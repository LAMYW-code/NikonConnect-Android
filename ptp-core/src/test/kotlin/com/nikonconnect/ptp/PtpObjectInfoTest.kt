package com.nikonconnect.ptp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PtpObjectInfoTest {
    @Test
    fun classifiesNikonFilesByExtensionBeforeFormatFallback() {
        assertEquals(CameraAssetKind.RAW, objectInfo("DSC_0001.NEF", 0x3000).assetKind)
        assertEquals(CameraAssetKind.JPEG, objectInfo("DSC_0001.JPG", 0x3000).assetKind)
        assertEquals(CameraAssetKind.MOVIE, objectInfo("NIKON001.MOV", 0x3000).assetKind)
        assertNull(objectInfo("FOLDER", 0x3001).assetKind)
    }

    private fun objectInfo(name: String, format: Int) = PtpObjectInfo(
        handle = 1u,
        storageId = 2u,
        objectFormat = format.toUShort(),
        compressedSize = 100,
        thumbnailFormat = 0u.toUShort(),
        thumbnailSize = 0,
        thumbnailWidth = 0,
        thumbnailHeight = 0,
        associationType = 0u.toUShort(),
        associationDescription = 0u,
        fileName = name,
        captureDate = null,
        modificationDate = null,
    )
}
