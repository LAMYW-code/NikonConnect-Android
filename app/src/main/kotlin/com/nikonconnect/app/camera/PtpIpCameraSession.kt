package com.nikonconnect.app.camera

import android.net.Network
import com.nikonconnect.ptp.PtpDeviceInfo
import com.nikonconnect.ptp.PtpIpCodec
import com.nikonconnect.ptp.PtpIpPacket
import com.nikonconnect.ptp.PtpIpPacketType
import com.nikonconnect.ptp.PtpObjectInfo
import com.nikonconnect.ptp.PtpOperationCode
import com.nikonconnect.ptp.PtpProtocolException
import com.nikonconnect.ptp.PtpReader
import com.nikonconnect.ptp.PtpResponseCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

data class ConnectedCamera(
    val friendlyName: String,
    val deviceInfo: PtpDeviceInfo,
)

data class AssetPage(
    val items: List<PtpObjectInfo>,
    val hasMore: Boolean,
)

data class InitialAssetScanProgress(
    val items: List<PtpObjectInfo>,
    val jpegCount: Int,
    val rawCount: Int,
    val scannedCandidates: Int,
    val totalCandidates: Int,
)

class PtpIpCameraSession(
    private val network: Network,
    private val initiatorGuid: ByteArray,
    private val host: String = "192.168.1.1",
    private val port: Int = 15_740,
) : AutoCloseable {
    private var commandChannel: PacketChannel? = null
    private var eventChannel: PacketChannel? = null
    private var eventJob: Job? = null
    private var nextTransactionId = 1u
    private val closed = AtomicBoolean(false)
    private val commandMutex = Mutex()
    private var assetHandles: List<UInt>? = null
    private var nextAssetIndex = 0

    suspend fun connect(): ConnectedCamera = withContext(Dispatchers.IO) {
        try {
            val command = openChannel()
            commandChannel = command
            command.send(
                PtpIpCodec.encodeInitCommandRequest(
                    guid = initiatorGuid,
                    friendlyName = "NikonConnectAndroid",
                ),
            )
            val ackPacket = command.receive()
            if (ackPacket.type == PtpIpPacketType.INIT_FAIL) {
                throw PtpProtocolException("相机拒绝了 PTP/IP 连接。")
            }
            if (ackPacket.type != PtpIpPacketType.INIT_COMMAND_ACK) {
                throw PtpProtocolException("相机未返回 InitCommandAck。")
            }
            val ack = PtpIpCodec.parseInitCommandAck(ackPacket.payload)
            if ((ack.protocolVersion shr 16) != 1u) {
                throw PtpProtocolException("不兼容的 PTP/IP 协议版本：${ack.protocolVersion}")
            }

            val event = openChannel()
            eventChannel = event
            event.send(PtpIpCodec.encodeInitEventRequest(ack.connectionNumber))
            val eventAck = event.receive()
            if (eventAck.type != PtpIpPacketType.INIT_EVENT_ACK) {
                throw PtpProtocolException("相机未返回 InitEventAck。")
            }
            startEventLoop(event)

            val deviceInfoPayload = requestDataUnlocked(PtpOperationCode.GET_DEVICE_INFO)
            val deviceInfo = PtpIpCodec.parseDeviceInfo(deviceInfoPayload)
            requestResponseUnlocked(PtpOperationCode.OPEN_SESSION, parameters = listOf(1u))

            ConnectedCamera(
                friendlyName = ack.responderFriendlyName.ifBlank { "Nikon 相机" },
                deviceInfo = deviceInfo,
            )
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    suspend fun loadAssetsPage(limit: Int, reset: Boolean): AssetPage = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            ensureOpen()
            if (reset || assetHandles == null) {
                resetAssetHandlesUnlocked()
            }

            val handles = assetHandles.orEmpty()
            val items = mutableListOf<PtpObjectInfo>()
            while (items.size < limit && nextAssetIndex < handles.size) {
                val handle = handles[nextAssetIndex++]
                val info = PtpIpCodec.parseObjectInfo(
                    requestDataUnlocked(
                        operation = PtpOperationCode.GET_OBJECT_INFO,
                        parameters = listOf(handle),
                        maxBytes = 1_048_576,
                    ),
                    handle,
                )
                if (!info.isDirectory && info.assetKind != null) items += info
            }
            AssetPage(items, nextAssetIndex < handles.size)
        }
    }

    suspend fun loadInitialAssets(
        jpegLimit: Int = 100,
        rawLimit: Int = 100,
        onProgress: (InitialAssetScanProgress) -> Unit = {},
    ): AssetPage = withContext(Dispatchers.IO) {
        require(jpegLimit >= 0) { "jpegLimit must not be negative" }
        require(rawLimit >= 0) { "rawLimit must not be negative" }

        commandMutex.withLock {
            ensureOpen()
            resetAssetHandlesUnlocked()
        }

        val quota = InitialAssetQuota(jpegLimit, rawLimit)
        var scannedCandidates = 0
        var acceptedSinceProgress = 0
        var scannedSinceProgress = 0
        var consecutiveFailures = 0
        val totalCandidates = commandMutex.withLock { assetHandles.orEmpty().size }

        while (!quota.isComplete) {
            val handle = commandMutex.withLock {
                ensureOpen()
                val handles = assetHandles.orEmpty()
                if (nextAssetIndex >= handles.size) null else handles[nextAssetIndex++]
            } ?: break

            scannedCandidates++
            scannedSinceProgress++
            val info = try {
                commandMutex.withLock {
                    ensureOpen()
                    PtpIpCodec.parseObjectInfo(
                        requestDataUnlocked(
                            operation = PtpOperationCode.GET_OBJECT_INFO,
                            parameters = listOf(handle),
                            maxBytes = 1_048_576,
                        ),
                        handle,
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                consecutiveFailures++
                if (closed.get() || consecutiveFailures >= MAX_CONSECUTIVE_OBJECT_INFO_FAILURES) throw error
                null
            }

            if (info != null) {
                consecutiveFailures = 0
                if (quota.offer(info)) acceptedSinceProgress++
            }
            if (acceptedSinceProgress >= INITIAL_SCAN_PROGRESS_BATCH ||
                scannedSinceProgress >= INITIAL_SCAN_CANDIDATE_PROGRESS_BATCH
            ) {
                onProgress(quota.toProgress(scannedCandidates, totalCandidates))
                acceptedSinceProgress = 0
                scannedSinceProgress = 0
            }
        }

        onProgress(quota.toProgress(scannedCandidates, totalCandidates))
        val hasMore = commandMutex.withLock { nextAssetIndex < assetHandles.orEmpty().size }
        AssetPage(quota.items.toList(), hasMore)
    }

    suspend fun loadThumbnail(handle: UInt): ByteArray? = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            ensureOpen()
            runCatching {
                requestDataUnlocked(
                    operation = PtpOperationCode.GET_THUMB,
                    parameters = listOf(handle),
                    maxBytes = 16 * 1024 * 1024,
                ).takeIf(ByteArray::isNotEmpty)
            }.getOrNull()
        }
    }

    suspend fun downloadObject(
        asset: PtpObjectInfo,
        output: OutputStream,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            ensureOpen()
            requestDataToUnlocked(
                operation = PtpOperationCode.GET_OBJECT,
                parameters = listOf(asset.handle),
                output = output,
                maxBytes = Long.MAX_VALUE,
                onProgress = { transferred, reportedTotal ->
                    onProgress(transferred, maxOf(asset.compressedSize, reportedTotal))
                },
            )
        }
    }

    private fun openChannel(): PacketChannel {
        val socket = network.socketFactory.createSocket()
        socket.connect(InetSocketAddress(host, port), 8_000)
        socket.soTimeout = 15_000
        socket.tcpNoDelay = true
        return PacketChannel(socket)
    }

    private fun resetAssetHandlesUnlocked() {
        val storageIds = PtpIpCodec.parseUIntArray(
            requestDataUnlocked(PtpOperationCode.GET_STORAGE_IDS, maxBytes = 1_048_576),
        )
        val candidates = storageIds.ifEmpty { listOf(UInt.MAX_VALUE) }
        val handlesByStorage = candidates.map { storageId ->
            PtpIpCodec.parseUIntArray(
                requestDataUnlocked(
                    operation = PtpOperationCode.GET_OBJECT_HANDLES,
                    parameters = listOf(storageId, 0u, 0u),
                    maxBytes = 16 * 1024 * 1024,
                ),
            )
        }
        assetHandles = interleaveNewestHandles(handlesByStorage)
        nextAssetIndex = 0
    }

    private fun startEventLoop(channel: PacketChannel) {
        eventJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && !closed.get()) {
                val packet = runCatching { channel.receive() }.getOrNull() ?: break
                if (packet.type == PtpIpPacketType.PROBE_REQUEST) {
                    runCatching { channel.send(PtpIpCodec.encodePacket(PtpIpPacketType.PROBE_RESPONSE)) }
                }
            }
        }
    }

    private fun nextTransaction(): UInt = nextTransactionId++

    private fun requestResponseUnlocked(
        operation: PtpOperationCode,
        parameters: List<UInt> = emptyList(),
    ) {
        val transactionId = nextTransaction()
        val command = commandChannel ?: throw PtpProtocolException("命令通道未连接。")
        command.send(PtpIpCodec.encodeOperationRequest(operation, transactionId, parameters))
        validateResponse(command.receive(), transactionId)
    }

    private fun requestDataUnlocked(
        operation: PtpOperationCode,
        parameters: List<UInt> = emptyList(),
        maxBytes: Long = 16 * 1024 * 1024,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        requestDataToUnlocked(operation, parameters, output, maxBytes)
        return output.toByteArray()
    }

    private fun requestDataToUnlocked(
        operation: PtpOperationCode,
        parameters: List<UInt>,
        output: OutputStream,
        maxBytes: Long,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> },
    ): Long {
        val transactionId = nextTransaction()
        val command = commandChannel ?: throw PtpProtocolException("命令通道未连接。")
        command.send(PtpIpCodec.encodeOperationRequest(operation, transactionId, parameters))

        val first = command.receive()
        if (first.type == PtpIpPacketType.OPERATION_RESPONSE) {
            validateResponse(first, transactionId)
            return 0
        }
        if (first.type != PtpIpPacketType.START_DATA) {
            throw PtpProtocolException("数据请求收到意外包：${first.type}")
        }
        val start = PtpReader(first.payload)
        val startTransaction = start.readUInt()
        val totalLength = start.readULong()
        if (startTransaction != transactionId) throw PtpProtocolException("StartData 事务号不匹配。")
        if (totalLength > maxBytes.toULong()) {
            throw PtpProtocolException("相机返回的数据超过允许上限：$totalLength")
        }

        var transferred = 0L
        while (true) {
            val packet = command.receive()
            when (packet.type) {
                PtpIpPacketType.DATA, PtpIpPacketType.END_DATA -> {
                    val reader = PtpReader(packet.payload)
                    if (reader.readUInt() != transactionId) {
                        throw PtpProtocolException("数据包事务号不匹配。")
                    }
                    val bytes = reader.readBytes(reader.remaining)
                    output.write(bytes)
                    transferred += bytes.size
                    if (transferred > maxBytes) throw PtpProtocolException("接收数据超过允许上限。")
                    val safeTotal = totalLength.coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
                    onProgress(transferred, safeTotal)
                    if (packet.type == PtpIpPacketType.END_DATA) break
                }
                else -> throw PtpProtocolException("数据阶段收到意外包：${packet.type}")
            }
        }
        validateResponse(command.receive(), transactionId)
        output.flush()
        return transferred
    }

    private fun validateResponse(packet: PtpIpPacket, transactionId: UInt) {
        if (packet.type != PtpIpPacketType.OPERATION_RESPONSE) {
            throw PtpProtocolException("未收到 OperationResponse。")
        }
        val response = PtpIpCodec.parseResponse(packet.payload)
        if (response.transactionId != transactionId) {
            throw PtpProtocolException("响应事务号不匹配。")
        }
        if (response.code != PtpResponseCode.OK) {
            throw PtpProtocolException("相机返回错误：0x${response.code.toString(16)}")
        }
    }

    private fun ensureOpen() {
        if (closed.get() || commandChannel == null || eventChannel == null) {
            throw PtpProtocolException("PTP/IP 会话未连接。")
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        eventJob?.cancel()
        eventJob = null
        commandChannel?.close()
        eventChannel?.close()
        commandChannel = null
        eventChannel = null
        assetHandles = null
        nextAssetIndex = 0
    }
}

private fun InitialAssetQuota.toProgress(
    scannedCandidates: Int,
    totalCandidates: Int,
) = InitialAssetScanProgress(
    items = items.toList(),
    jpegCount = jpegCount,
    rawCount = rawCount,
    scannedCandidates = scannedCandidates,
    totalCandidates = totalCandidates,
)

private const val INITIAL_SCAN_PROGRESS_BATCH = 10
private const val INITIAL_SCAN_CANDIDATE_PROGRESS_BATCH = 25
private const val MAX_CONSECUTIVE_OBJECT_INFO_FAILURES = 3

private class PacketChannel(private val socket: Socket) : AutoCloseable {
    private val input = BufferedInputStream(socket.getInputStream())
    private val output = BufferedOutputStream(socket.getOutputStream())

    @Synchronized
    fun send(bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }

    fun receive(): PtpIpPacket {
        val header = input.readExactly(8)
        val (length, type) = PtpIpCodec.parseHeader(header)
        return PtpIpPacket(type, input.readExactly(length - 8))
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

private fun BufferedInputStream.readExactly(count: Int): ByteArray {
    val result = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = read(result, offset, count - offset)
        if (read < 0) throw EOFException("PTP/IP 连接提前关闭。")
        offset += read
    }
    return result
}
