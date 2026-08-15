package com.nikonconnect.app

import android.app.Application
import android.util.Base64
import com.nikonconnect.app.connection.CameraNetworkSelector
import com.nikonconnect.app.camera.CameraPreviewCache
import com.nikonconnect.app.camera.MediaStoreAssetExporter
import com.nikonconnect.app.notification.DownloadLiveUpdateNotifier
import com.nikonconnect.ptp.PtpIpCodec

class NikonConnectApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val cameraNetworkSelector = CameraNetworkSelector(application)
    val assetExporter = MediaStoreAssetExporter(application)
    val previewCache = CameraPreviewCache(application)
    val downloadLiveUpdateNotifier = DownloadLiveUpdateNotifier(application)
    val initiatorGuid: ByteArray by lazy {
        val preferences = application.getSharedPreferences("ptpip", Application.MODE_PRIVATE)
        preferences.getString("initiator_guid", null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?.takeIf { it.size == 16 }
            ?: PtpIpCodec.randomGuid().also { guid ->
                preferences.edit()
                    .putString("initiator_guid", Base64.encodeToString(guid, Base64.NO_WRAP))
                    .apply()
            }
    }
}
