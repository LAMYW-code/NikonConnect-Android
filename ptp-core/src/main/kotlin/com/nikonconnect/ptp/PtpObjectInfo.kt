package com.nikonconnect.ptp

enum class CameraAssetKind(val badge: String) {
    JPEG("JPEG"),
    PNG("PNG"),
    RAW("RAW"),
    MOVIE("MOV"),
}

data class PtpObjectInfo(
    val handle: UInt,
    val storageId: UInt,
    val objectFormat: UShort,
    val compressedSize: Long,
    val thumbnailFormat: UShort,
    val thumbnailSize: Long,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
    val associationType: UShort,
    val associationDescription: UInt,
    val fileName: String,
    val captureDate: String?,
    val modificationDate: String?,
) {
    val isDirectory: Boolean get() = objectFormat == 0x3001.toUShort()

    val assetKind: CameraAssetKind?
        get() {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return when (extension) {
                "jpg", "jpeg", "heic", "heif" -> CameraAssetKind.JPEG
                "png" -> CameraAssetKind.PNG
                "nef", "nrw", "raw", "dng", "tif", "tiff" -> CameraAssetKind.RAW
                "mov", "mp4", "avi", "mpeg", "mpg" -> CameraAssetKind.MOVIE
                else -> when (objectFormat.toInt()) {
                    0x3000, 0x3802, 0x380D, 0x3810, 0x3811 -> CameraAssetKind.RAW
                    0x3801, 0x3808, 0xB200 -> CameraAssetKind.JPEG
                    0x380B -> CameraAssetKind.PNG
                    0x300A, 0x300B, 0x300D, 0xB97E -> CameraAssetKind.MOVIE
                    else -> null
                }
            }
        }

    val mimeType: String
        get() = when (assetKind) {
            CameraAssetKind.JPEG -> if (fileName.endsWith(".heif", true) || fileName.endsWith(".heic", true)) {
                "image/heif"
            } else {
                "image/jpeg"
            }
            CameraAssetKind.PNG -> "image/png"
            CameraAssetKind.RAW -> when {
                fileName.endsWith(".nef", true) -> "image/x-nikon-nef"
                fileName.endsWith(".nrw", true) -> "image/x-nikon-nrw"
                fileName.endsWith(".dng", true) -> "image/x-adobe-dng"
                else -> "application/octet-stream"
            }
            CameraAssetKind.MOVIE -> if (fileName.endsWith(".mov", true)) "video/quicktime" else "video/mp4"
            null -> "application/octet-stream"
        }
}
