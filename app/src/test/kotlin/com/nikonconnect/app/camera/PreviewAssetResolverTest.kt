package com.nikonconnect.app.camera

import com.nikonconnect.ptp.PtpObjectInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewAssetResolverTest {
    @Test
    fun jpegUsesItsOwnObject() {
        val jpeg = asset(1u, "DSC_1001.JPG")

        val source = resolvePreviewAsset(jpeg, listOf(jpeg))

        assertEquals(jpeg, source?.source)
        assertFalse(source?.usesPairedJpeg ?: true)
    }

    @Test
    fun rawUsesSameNamedJpegPreferablyFromSameStorage() {
        val raw = asset(3u, "DSC_1002.NEF", storageId = 8u)
        val otherCardJpeg = asset(1u, "dsc_1002.jpg", storageId = 4u)
        val sameCardJpeg = asset(2u, "DSC_1002.JPG", storageId = 8u)

        val source = resolvePreviewAsset(raw, listOf(otherCardJpeg, sameCardJpeg, raw))

        assertEquals(sameCardJpeg, source?.source)
        assertTrue(source?.usesPairedJpeg == true)
    }

    @Test
    fun rawWithoutPairedJpegDoesNotDownloadRawAsAnImage() {
        val raw = asset(1u, "DSC_1003.NEF")

        assertNull(resolvePreviewAsset(raw, listOf(raw)))
    }

    private fun asset(
        handle: UInt,
        fileName: String,
        storageId: UInt = 1u,
    ) = PtpObjectInfo(
        handle = handle,
        storageId = storageId,
        objectFormat = 0u.toUShort(),
        compressedSize = 1_024L,
        thumbnailFormat = 0u.toUShort(),
        thumbnailSize = 0L,
        thumbnailWidth = 0,
        thumbnailHeight = 0,
        associationType = 0u.toUShort(),
        associationDescription = 0u,
        fileName = fileName,
        captureDate = null,
        modificationDate = null,
    )
}
