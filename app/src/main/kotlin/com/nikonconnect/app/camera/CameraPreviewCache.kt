package com.nikonconnect.app.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.nikonconnect.ptp.PtpObjectInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import kotlin.math.max
import kotlin.math.roundToInt

class CameraPreviewCache(application: Application) {
    private val directory = File(application.cacheDir, "camera-previews")

    suspend fun load(
        session: PtpIpCameraSession,
        asset: PtpObjectInfo,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        cachedFile(asset)?.let { return@withContext it }

        val download = File.createTempFile("download-", ".part", directory)
        val encoded = File.createTempFile("preview-", ".part", directory)
        val target = File(directory, cacheFileName(asset))
        try {
            FileOutputStream(download).buffered().use { output ->
                session.downloadObject(asset, output, onProgress)
            }
            encodePreview(download, encoded)
            try {
                Files.move(
                    encoded.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    encoded.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            target.setLastModified(System.currentTimeMillis())
            trim()
            target
        } finally {
            download.delete()
            encoded.delete()
        }
    }

    private fun cachedFile(asset: PtpObjectInfo): File? =
        File(directory, cacheFileName(asset)).takeIf(File::isFile)?.also {
            it.setLastModified(System.currentTimeMillis())
        }

    private fun encodePreview(sourceFile: File, outputFile: File) {
        val source = ImageDecoder.createSource(sourceFile)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width.coerceAtLeast(1)
            val height = info.size.height.coerceAtLeast(1)
            val longestEdge = max(width, height)
            if (longestEdge > MAX_PREVIEW_EDGE) {
                val scale = MAX_PREVIEW_EDGE.toFloat() / longestEdge
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
        }
        try {
            FileOutputStream(outputFile).buffered().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, PREVIEW_JPEG_QUALITY, output)) {
                    "无法生成高清预览。"
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun trim() {
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
            .sortedByDescending(File::lastModified)
            .fold(0L) { retainedBytes, file ->
                val nextSize = retainedBytes + file.length()
                if (nextSize > MAX_CACHE_BYTES) {
                    file.delete()
                    retainedBytes
                } else {
                    nextSize
                }
            }
    }

    private fun cacheFileName(asset: PtpObjectInfo): String =
        "${asset.handle.toString(16)}-${asset.compressedSize.coerceAtLeast(0L)}.jpg"
}

private const val MAX_PREVIEW_EDGE = 3_840
private const val PREVIEW_JPEG_QUALITY = 92
private const val MAX_CACHE_BYTES = 200L * 1024L * 1024L
