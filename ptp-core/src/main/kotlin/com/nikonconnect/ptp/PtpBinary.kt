package com.nikonconnect.ptp

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID

class PtpReader(private val data: ByteArray) {
    private var offset = 0

    val remaining: Int get() = data.size - offset

    fun readUByte(): UByte {
        requireRemaining(1)
        return data[offset++].toUByte()
    }

    fun readUShort(): UShort {
        requireRemaining(2)
        val value = data[offset].toUByte().toUInt() or
            (data[offset + 1].toUByte().toUInt() shl 8)
        offset += 2
        return value.toUShort()
    }

    fun readUInt(): UInt {
        requireRemaining(4)
        var value = 0u
        repeat(4) { index ->
            value = value or (data[offset + index].toUByte().toUInt() shl (index * 8))
        }
        offset += 4
        return value
    }

    fun readULong(): ULong {
        requireRemaining(8)
        var value = 0uL
        repeat(8) { index ->
            value = value or (data[offset + index].toUByte().toULong() shl (index * 8))
        }
        offset += 8
        return value
    }

    fun readBytes(count: Int): ByteArray {
        if (count < 0) throw PtpProtocolException("负数字节长度：$count")
        requireRemaining(count)
        return data.copyOfRange(offset, offset + count).also { offset += count }
    }

    fun readUShortArray(maxCount: Int = 65_536): List<UShort> {
        val count = readUInt().toLong()
        if (count > maxCount) throw PtpProtocolException("PTP UInt16 数组过大：$count")
        return List(count.toInt()) { readUShort() }
    }

    fun readUIntArray(maxCount: Int = 1_000_000): List<UInt> {
        val count = readUInt().toLong()
        if (count > maxCount) throw PtpProtocolException("PTP UInt32 数组过大：$count")
        return List(count.toInt()) { readUInt() }
    }

    fun readPtpString(maxCharacters: Int = 255): String {
        val characterCount = readUByte().toInt()
        if (characterCount == 0) return ""
        if (characterCount > maxCharacters) {
            throw PtpProtocolException("PTP 字符串过长：$characterCount")
        }
        return decodeUtf16NullTerminated(readBytes(characterCount * 2))
    }

    fun readUtf16NullTerminated(maxCharacters: Int = 255): String {
        val output = ByteArrayOutputStream()
        repeat(maxCharacters + 1) {
            val low = readUByte().toByte()
            val high = readUByte().toByte()
            if (low == 0.toByte() && high == 0.toByte()) {
                return output.toByteArray().toString(Charsets.UTF_16LE)
            }
            output.write(low.toInt())
            output.write(high.toInt())
        }
        throw PtpProtocolException("UTF-16 字符串缺少终止符")
    }

    private fun requireRemaining(count: Int) {
        if (count > remaining) {
            throw PtpProtocolException("PTP 数据不足：需要 $count 字节，剩余 $remaining 字节")
        }
    }

    private fun decodeUtf16NullTerminated(bytes: ByteArray): String {
        val terminator = bytes.indices
            .step(2)
            .firstOrNull { it + 1 < bytes.size && bytes[it] == 0.toByte() && bytes[it + 1] == 0.toByte() }
            ?: bytes.size
        return bytes.copyOfRange(0, terminator).toString(Charsets.UTF_16LE)
    }
}

class PtpWriter {
    private val output = ByteArrayOutputStream()

    fun writeUShort(value: UShort): PtpWriter = apply {
        repeat(2) { output.write((value.toUInt() shr (it * 8)).toInt() and 0xFF) }
    }

    fun writeUInt(value: UInt): PtpWriter = apply {
        repeat(4) { output.write((value shr (it * 8)).toInt() and 0xFF) }
    }

    fun writeULong(value: ULong): PtpWriter = apply {
        repeat(8) { output.write((value shr (it * 8)).toInt() and 0xFF) }
    }

    fun writeBytes(value: ByteArray): PtpWriter = apply { output.write(value) }

    fun writeUtf16NullTerminated(value: String, maxCharacters: Int = 39): PtpWriter = apply {
        val bytes = value.take(maxCharacters).toByteArray(Charsets.UTF_16LE)
        output.write(bytes)
        output.write(0)
        output.write(0)
    }

    fun toByteArray(): ByteArray = output.toByteArray()
}

object PtpIpCodec {
    const val PROTOCOL_VERSION: UInt = 0x0001_0000u
    const val MAX_PACKET_LENGTH = 64 * 1024 * 1024

    fun encodePacket(type: PtpIpPacketType, payload: ByteArray = byteArrayOf()): ByteArray {
        val totalLength = payload.size + 8
        if (totalLength > MAX_PACKET_LENGTH) throw PtpProtocolException("PTP/IP 包过大：$totalLength")
        return PtpWriter()
            .writeUInt(totalLength.toUInt())
            .writeUInt(type.value)
            .writeBytes(payload)
            .toByteArray()
    }

    fun encodeInitCommandRequest(guid: ByteArray, friendlyName: String): ByteArray {
        if (guid.size != 16) throw PtpProtocolException("PTP/IP GUID 必须为 16 字节")
        val payload = PtpWriter()
            .writeBytes(guid)
            .writeUtf16NullTerminated(friendlyName)
            .writeUInt(PROTOCOL_VERSION)
            .toByteArray()
        return encodePacket(PtpIpPacketType.INIT_COMMAND_REQUEST, payload)
    }

    fun encodeInitEventRequest(connectionNumber: UInt): ByteArray = encodePacket(
        PtpIpPacketType.INIT_EVENT_REQUEST,
        PtpWriter().writeUInt(connectionNumber).toByteArray(),
    )

    fun encodeOperationRequest(
        operation: PtpOperationCode,
        transactionId: UInt,
        parameters: List<UInt> = emptyList(),
        dataPhase: UInt = 1u,
    ): ByteArray {
        val writer = PtpWriter()
            .writeUInt(dataPhase)
            .writeUShort(operation.value)
            .writeUInt(transactionId)
        parameters.take(5).forEach(writer::writeUInt)
        return encodePacket(PtpIpPacketType.OPERATION_REQUEST, writer.toByteArray())
    }

    fun parseHeader(header: ByteArray): Pair<Int, PtpIpPacketType> {
        if (header.size != 8) throw PtpProtocolException("PTP/IP 包头必须为 8 字节")
        val reader = PtpReader(header)
        val length = reader.readUInt().toLong()
        if (length !in 8..MAX_PACKET_LENGTH.toLong()) {
            throw PtpProtocolException("无效 PTP/IP 包长：$length")
        }
        return length.toInt() to PtpIpPacketType.fromValue(reader.readUInt())
    }

    fun parseInitCommandAck(payload: ByteArray): PtpInitCommandAck {
        val reader = PtpReader(payload)
        val connectionNumber = reader.readUInt()
        reader.readBytes(16)
        val name = reader.readUtf16NullTerminated()
        val version = reader.readUInt()
        return PtpInitCommandAck(connectionNumber, name, version)
    }

    fun parseResponse(payload: ByteArray): PtpResponse {
        val reader = PtpReader(payload)
        val code = reader.readUShort()
        val transactionId = reader.readUInt()
        val parameters = buildList {
            while (reader.remaining >= 4) add(reader.readUInt())
        }
        return PtpResponse(code, transactionId, parameters)
    }

    fun parseDeviceInfo(payload: ByteArray): PtpDeviceInfo {
        val reader = PtpReader(payload)
        reader.readUShort()
        reader.readUInt()
        reader.readUShort()
        reader.readPtpString()
        reader.readUShort()
        val operations = reader.readUShortArray().toSet()
        repeat(4) { reader.readUShortArray() }
        val manufacturer = reader.readPtpString().ifBlank { null }
        val model = reader.readPtpString().ifBlank { null }
        val version = reader.readPtpString().ifBlank { null }
        val serial = reader.readPtpString().ifBlank { null }
        return PtpDeviceInfo(manufacturer, model, version, serial, operations)
    }

    fun randomGuid(): ByteArray {
        val uuid = UUID.randomUUID()
        return PtpWriter().writeULong(uuid.mostSignificantBits.toULong())
            .writeULong(uuid.leastSignificantBits.toULong())
            .toByteArray()
    }

    fun parseUIntArray(payload: ByteArray): List<UInt> = PtpReader(payload).readUIntArray()

    fun parseObjectInfo(payload: ByteArray, handle: UInt): PtpObjectInfo {
        val reader = PtpReader(payload)
        val storageId = reader.readUInt()
        val format = reader.readUShort()
        reader.readUShort()
        val compressedSize = reader.readUInt()
        val thumbnailFormat = reader.readUShort()
        val thumbnailSize = reader.readUInt()
        val thumbnailWidth = reader.readUInt()
        val thumbnailHeight = reader.readUInt()
        reader.readUInt()
        reader.readUInt()
        reader.readUInt()
        reader.readUInt()
        val associationType = reader.readUShort()
        val associationDescription = reader.readUInt()
        reader.readUInt()
        val fileName = reader.readPtpString()
        val captureDate = reader.readPtpString().ifBlank { null }
        val modificationDate = reader.readPtpString().ifBlank { null }
        if (reader.remaining > 0) runCatching { reader.readPtpString() }

        return PtpObjectInfo(
            handle = handle,
            storageId = storageId,
            objectFormat = format,
            compressedSize = compressedSize.toLong(),
            thumbnailFormat = thumbnailFormat,
            thumbnailSize = thumbnailSize.toLong(),
            thumbnailWidth = thumbnailWidth.toInt(),
            thumbnailHeight = thumbnailHeight.toInt(),
            associationType = associationType,
            associationDescription = associationDescription,
            fileName = fileName.ifBlank { "NIKON_${handle}.BIN" },
            captureDate = captureDate,
            modificationDate = modificationDate,
        )
    }
}

object NikonSsidPolicy {
    fun normalize(rawSsid: String?): String? {
        val value = rawSsid?.trim()?.removeSurrounding("\"")?.trim().orEmpty()
        if (value.isBlank() || value.equals("<unknown ssid>", ignoreCase = true)) return null
        return value.uppercase(Locale.ROOT)
    }

    fun isNikon(rawSsid: String?): Boolean = normalize(rawSsid)?.startsWith("NIKON") == true
}
