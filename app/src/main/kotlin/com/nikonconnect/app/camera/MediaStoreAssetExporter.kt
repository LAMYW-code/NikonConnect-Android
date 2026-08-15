package com.nikonconnect.app.camera

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.nikonconnect.ptp.CameraAssetKind
import com.nikonconnect.ptp.PtpObjectInfo

class MediaStoreAssetExporter(private val context: Context) {
    suspend fun export(
        session: PtpIpCameraSession,
        asset: PtpObjectInfo,
        onProgress: (Long, Long) -> Unit,
    ) {
        val resolver = context.contentResolver
        val (collection, relativePath) = when (asset.assetKind) {
            CameraAssetKind.MOVIE -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "DCIM/Nikon Connect"
            CameraAssetKind.JPEG, CameraAssetKind.PNG -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "DCIM/Nikon Connect"
            CameraAssetKind.RAW, null -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Download/Nikon Connect"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, asset.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, asset.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("无法在系统相册中创建文件。")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                session.downloadObject(asset, output, onProgress)
            } ?: error("无法打开系统相册文件。")
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
