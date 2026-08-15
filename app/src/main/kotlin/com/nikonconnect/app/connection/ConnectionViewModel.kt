package com.nikonconnect.app.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nikonconnect.app.AppContainer
import com.nikonconnect.app.camera.PtpIpCameraSession
import com.nikonconnect.app.camera.resolvePreviewAsset
import com.nikonconnect.app.notification.DownloadLiveUpdate
import com.nikonconnect.ptp.CameraAssetKind
import com.nikonconnect.ptp.PtpObjectInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConnectionUiState(
    val stage: ConnectionStage = ConnectionStage.IDLE,
    val title: String = "尚未连接相机",
    val detail: String = "准备好相机后，通过系统 Wi-Fi 选择框建立连接。",
    val connectedModel: String? = null,
    val connectedSsid: String? = null,
    val diagnosticCode: String? = null,
) {
    val isBusy get() = stage in setOf(
        ConnectionStage.SHOWING_SYSTEM_PICKER,
        ConnectionStage.BINDING_NETWORK,
        ConnectionStage.PTP_HANDSHAKE,
        ConnectionStage.DISCOVERING_CAPABILITIES,
    )
}

enum class ConnectionStage { IDLE, SHOWING_SYSTEM_PICKER, BINDING_NETWORK, PTP_HANDSHAKE,
    DISCOVERING_CAPABILITIES, CONNECTED, ERROR }

enum class GalleryFilter(val label: String) {
    ALL("全部"),
    JPEG("JPEG"),
    RAW("RAW"),
}

data class GalleryAssetUi(
    val info: PtpObjectInfo,
    val thumbnail: ByteArray? = null,
)

data class PreviewUiState(
    val requestedHandle: UInt? = null,
    val sourceHandle: UInt? = null,
    val filePath: String? = null,
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val usesPairedJpeg: Boolean = false,
    val error: String? = null,
)

data class GalleryUiState(
    val items: List<GalleryAssetUi> = emptyList(),
    val selected: Set<UInt> = emptySet(),
    val isLoading: Boolean = false,
    val isInitialScan: Boolean = false,
    val hasMore: Boolean = false,
    val jpegTarget: Int = INITIAL_JPEG_LIMIT,
    val rawTarget: Int = INITIAL_RAW_LIMIT,
    val scannedCandidates: Int = 0,
    val totalCandidates: Int = 0,
    val error: String? = null,
    val transferName: String? = null,
    val transferProgress: Float = 0f,
    val transferIndex: Int = 0,
    val transferTotal: Int = 0,
    val completedTransfers: Int = 0,
    val filter: GalleryFilter = GalleryFilter.ALL,
) {
    val isTransferring get() = transferName != null
    val jpegCount get() = items.count { it.info.assetKind == CameraAssetKind.JPEG }
    val rawCount get() = items.count { it.info.assetKind == CameraAssetKind.RAW }
    val visibleItems: List<GalleryAssetUi>
        get() = when (filter) {
            GalleryFilter.ALL -> items
            GalleryFilter.JPEG -> items.filter { it.info.assetKind == CameraAssetKind.JPEG }
            GalleryFilter.RAW -> items.filter { it.info.assetKind == CameraAssetKind.RAW }
        }
}

class ConnectionViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    private val _galleryState = MutableStateFlow(GalleryUiState())
    val galleryState: StateFlow<GalleryUiState> = _galleryState.asStateFlow()
    private val _previewState = MutableStateFlow(PreviewUiState())
    val previewState: StateFlow<PreviewUiState> = _previewState.asStateFlow()

    private var networkLease: SelectedNetworkLease? = null
    private var cameraSession: PtpIpCameraSession? = null
    private val thumbnailRequests = mutableSetOf<UInt>()
    private val previewJobs = mutableMapOf<UInt, Job>()

    fun connect() {
        if (_uiState.value.isBusy) return
        disconnectResources()
        viewModelScope.launch {
            _uiState.value = ConnectionUiState(ConnectionStage.SHOWING_SYSTEM_PICKER,
                "选择相机 Wi-Fi", "请在系统窗口中选择名称以 NIKON 开头的网络。")
            val lease = container.cameraNetworkSelector.requestNikonNetwork().getOrElse {
                showError("未连接相机 Wi-Fi", it.message.orEmpty(), "WIFI-001")
                return@launch
            }
            establishCameraSession(lease)
        }
    }

    private suspend fun establishCameraSession(lease: SelectedNetworkLease) {
        networkLease = lease
        _uiState.value = ConnectionUiState(ConnectionStage.BINDING_NETWORK,
            "已确认 Nikon 网络", "${lease.ssid} · 正在绑定相机通信通道")
        val session = PtpIpCameraSession(lease.network, container.initiatorGuid)
        cameraSession = session
        _uiState.value = ConnectionUiState(ConnectionStage.PTP_HANDSHAKE,
            "正在连接相机", "正在建立 PTP/IP 命令与事件通道。")
        runCatching { withContext(Dispatchers.IO) { session.connect() } }
            .onSuccess { camera ->
                _uiState.value = ConnectionUiState(
                    stage = ConnectionStage.CONNECTED,
                    title = camera.deviceInfo.model ?: camera.friendlyName,
                    detail = "已连接 · ${camera.deviceInfo.operationsSupported.size} 项相机能力可用",
                    connectedModel = camera.deviceInfo.model,
                    connectedSsid = lease.ssid,
                )
                loadAssets(reset = true)
            }.onFailure {
                disconnectResources()
                showError("无法建立相机会话", it.message ?: "请确认相机仍显示 Wi-Fi 连接页面后重试。", "PTP-001")
            }
    }

    fun loadAssets(reset: Boolean = false) {
        val session = cameraSession ?: return
        if (_galleryState.value.isLoading) return
        viewModelScope.launch {
            if (reset) thumbnailRequests.clear()
            _galleryState.value = if (reset) {
                GalleryUiState(
                    isLoading = true,
                    isInitialScan = true,
                    filter = _galleryState.value.filter,
                )
            } else {
                _galleryState.value.copy(isLoading = true, error = null)
            }
            runCatching {
                if (reset) {
                    session.loadInitialAssets(
                        jpegLimit = INITIAL_JPEG_LIMIT,
                        rawLimit = INITIAL_RAW_LIMIT,
                    ) { progress ->
                        _galleryState.value = _galleryState.value.copy(
                            items = mergeAssetInfo(progress.items),
                            scannedCandidates = progress.scannedCandidates,
                            totalCandidates = progress.totalCandidates,
                        )
                    }
                } else {
                    session.loadAssetsPage(limit = LOAD_MORE_PAGE_SIZE, reset = false)
                }
            }
                .onSuccess { page ->
                    val updatedItems = if (reset) {
                        mergeAssetInfo(page.items)
                    } else {
                        (_galleryState.value.items + page.items.map(::GalleryAssetUi))
                            .distinctBy { it.info.handle }
                    }
                    _galleryState.value = _galleryState.value.copy(
                        items = updatedItems,
                        isLoading = false,
                        isInitialScan = false,
                        hasMore = page.hasMore,
                    )
                }.onFailure {
                    _galleryState.value = _galleryState.value.copy(isLoading = false, isInitialScan = false,
                        error = it.message ?: "无法读取相机文件。")
                }
        }
    }

    fun requestThumbnail(handle: UInt) {
        val session = cameraSession ?: return
        if (_galleryState.value.items.none { it.info.handle == handle && it.thumbnail == null }) return
        if (!thumbnailRequests.add(handle)) return
        viewModelScope.launch {
            val bytes = session.loadThumbnail(handle) ?: return@launch
            if (cameraSession !== session) return@launch
            _galleryState.value = _galleryState.value.copy(
                items = _galleryState.value.items.map {
                    if (it.info.handle == handle) it.copy(thumbnail = bytes) else it
                },
            )
        }
    }

    fun requestPreview(handle: UInt) {
        val session = cameraSession ?: return
        val items = _galleryState.value.items
        val requested = items.firstOrNull { it.info.handle == handle }?.info ?: return
        val source = resolvePreviewAsset(requested, items.map(GalleryAssetUi::info))
        if (source == null) {
            _previewState.value = PreviewUiState(
                requestedHandle = handle,
                error = if (requested.assetKind == CameraAssetKind.RAW) {
                    "相机中没有同名 JPEG，暂时只能显示 RAW 缩略图。"
                } else {
                    "这种文件暂不支持高清预览。"
                },
            )
            return
        }

        _previewState.value = PreviewUiState(
            requestedHandle = handle,
            sourceHandle = source.source.handle,
            isLoading = true,
            usesPairedJpeg = source.usesPairedJpeg,
        )
        if (previewJobs[source.source.handle]?.isActive == true) return

        val job = viewModelScope.launch {
            var lastReportedPercent = -1
            runCatching {
                container.previewCache.load(session, source.source) { transferred, total ->
                    if (total <= 0L) return@load
                    val percent = ((transferred * 100L) / total).toInt().coerceIn(0, 100)
                    if (percent != 100 && percent < lastReportedPercent + PREVIEW_PROGRESS_STEP) {
                        return@load
                    }
                    lastReportedPercent = percent
                    val current = _previewState.value
                    if (current.sourceHandle == source.source.handle) {
                        _previewState.value = current.copy(
                            progress = percent / 100f,
                            isLoading = true,
                        )
                    }
                }
            }.onSuccess { file ->
                val current = _previewState.value
                if (cameraSession === session && current.sourceHandle == source.source.handle) {
                    _previewState.value = current.copy(
                        filePath = file.absolutePath,
                        isLoading = false,
                        progress = 1f,
                        error = null,
                    )
                }
            }.onFailure { error ->
                val current = _previewState.value
                if (current.sourceHandle == source.source.handle) {
                    _previewState.value = current.copy(
                        isLoading = false,
                        error = error.message ?: "高清预览加载失败，已继续显示缩略图。",
                    )
                }
            }
            previewJobs.remove(source.source.handle)
        }
        previewJobs[source.source.handle] = job
    }

    fun dismissPreview() {
        _previewState.value = PreviewUiState()
    }

    private fun mergeAssetInfo(infos: List<PtpObjectInfo>): List<GalleryAssetUi> {
        val existing = _galleryState.value.items.associateBy { it.info.handle }
        return infos.map { info -> existing[info.handle]?.copy(info = info) ?: GalleryAssetUi(info) }
    }

    fun toggleSelection(handle: UInt) {
        if (_galleryState.value.isTransferring) return
        val selected = _galleryState.value.selected.toMutableSet()
        if (!selected.add(handle)) selected.remove(handle)
        _galleryState.value = _galleryState.value.copy(selected = selected)
    }

    fun setGalleryFilter(filter: GalleryFilter) {
        if (_galleryState.value.isTransferring) return
        val visibleHandles = _galleryState.value.items.asSequence()
            .filter {
                when (filter) {
                    GalleryFilter.ALL -> true
                    GalleryFilter.JPEG -> it.info.assetKind == CameraAssetKind.JPEG
                    GalleryFilter.RAW -> it.info.assetKind == CameraAssetKind.RAW
                }
            }
            .map { it.info.handle }
            .toSet()
        _galleryState.value = _galleryState.value.copy(
            filter = filter,
            selected = _galleryState.value.selected.intersect(visibleHandles),
        )
    }

    fun downloadSelected() {
        val session = cameraSession ?: return
        val chosen = _galleryState.value.items.filter { it.info.handle in _galleryState.value.selected }
        if (chosen.isEmpty() || _galleryState.value.isTransferring) return
        viewModelScope.launch {
            var completed = 0
            var completedBytes = 0L
            val totalBytes = chosen.sumOf { it.info.compressedSize.coerceAtLeast(0L) }
            try {
                for ((index, item) in chosen.withIndex()) {
                    container.downloadLiveUpdateNotifier.update(
                        DownloadLiveUpdate(
                            fileName = item.info.fileName,
                            currentItem = index + 1,
                            totalItems = chosen.size,
                            transferredBytes = completedBytes,
                            totalBytes = totalBytes,
                        ),
                        force = true,
                    )
                    _galleryState.value = _galleryState.value.copy(
                        transferName = item.info.fileName,
                        transferProgress = 0f,
                        transferIndex = index + 1,
                        transferTotal = chosen.size,
                        completedTransfers = 0,
                        error = null,
                    )
                    val itemSize = item.info.compressedSize.coerceAtLeast(0L)
                    val result = runCatching {
                        container.assetExporter.export(session, item.info) { current, total ->
                            _galleryState.value = _galleryState.value.copy(
                                transferProgress = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else 0f)
                            container.downloadLiveUpdateNotifier.update(
                                DownloadLiveUpdate(
                                    fileName = item.info.fileName,
                                    currentItem = index + 1,
                                    totalItems = chosen.size,
                                    transferredBytes = completedBytes + current.coerceAtMost(itemSize),
                                    totalBytes = totalBytes,
                                ),
                            )
                        }
                    }
                    if (result.isFailure) {
                        _galleryState.value = _galleryState.value.copy(transferName = null,
                            transferIndex = 0, transferTotal = 0,
                            error = result.exceptionOrNull()?.message ?: "照片传输失败。")
                        return@launch
                    }
                    completed++
                    completedBytes += itemSize
                }
                _galleryState.value = _galleryState.value.copy(selected = emptySet(), transferName = null,
                    transferProgress = 1f, transferIndex = 0, transferTotal = 0,
                    completedTransfers = completed)
            } finally {
                container.downloadLiveUpdateNotifier.cancel()
            }
        }
    }

    fun disconnect() {
        disconnectResources()
        _uiState.value = ConnectionUiState()
        _galleryState.value = GalleryUiState()
        _previewState.value = PreviewUiState()
    }
    fun permissionDenied() = showError("需要附近 Wi-Fi 权限", "该权限只用于选择名称以 NIKON 开头的相机网络。", "PERM-001")
    private fun showError(title: String, detail: String, code: String) { _uiState.value = ConnectionUiState(ConnectionStage.ERROR, title, detail, diagnosticCode = code) }
    private fun disconnectResources() {
        previewJobs.values.forEach(Job::cancel)
        previewJobs.clear()
        cameraSession?.close()
        cameraSession = null
        networkLease?.close()
        networkLease = null
        thumbnailRequests.clear()
    }
    override fun onCleared() { disconnectResources(); super.onCleared() }
}

private const val INITIAL_JPEG_LIMIT = 100
private const val INITIAL_RAW_LIMIT = 100
private const val LOAD_MORE_PAGE_SIZE = 30
private const val PREVIEW_PROGRESS_STEP = 2

class ConnectionViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        if (modelClass.isAssignableFrom(ConnectionViewModel::class.java)) ConnectionViewModel(container) as T
        else throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
}
