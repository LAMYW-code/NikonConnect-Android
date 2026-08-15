package com.nikonconnect.app.camera

import com.nikonconnect.ptp.CameraAssetKind
import com.nikonconnect.ptp.PtpObjectInfo

internal data class PreviewAssetSource(
    val requested: PtpObjectInfo,
    val source: PtpObjectInfo,
    val usesPairedJpeg: Boolean,
)

internal fun resolvePreviewAsset(
    requested: PtpObjectInfo,
    available: List<PtpObjectInfo>,
): PreviewAssetSource? = when (requested.assetKind) {
    CameraAssetKind.JPEG, CameraAssetKind.PNG -> PreviewAssetSource(
        requested = requested,
        source = requested,
        usesPairedJpeg = false,
    )
    CameraAssetKind.RAW -> available
        .asSequence()
        .filter { it.assetKind == CameraAssetKind.JPEG }
        .filter { it.previewBaseName().equals(requested.previewBaseName(), ignoreCase = true) }
        .minByOrNull { candidate ->
            if (candidate.storageId == requested.storageId) 0 else 1
        }
        ?.let { paired ->
            PreviewAssetSource(
                requested = requested,
                source = paired,
                usesPairedJpeg = true,
            )
        }
    CameraAssetKind.MOVIE, null -> null
}

private fun PtpObjectInfo.previewBaseName(): String =
    fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
