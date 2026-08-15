package com.nikonconnect.app.camera

import com.nikonconnect.ptp.PtpObjectInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetSelectionTest {
    @Test
    fun keepsIndependentJpegAndRawQuotas() {
        val quota = InitialAssetQuota(jpegLimit = 2, rawLimit = 1)

        assertTrue(quota.offer(asset(1u, "DSC_0001.JPG", 0x3801)))
        assertTrue(quota.offer(asset(2u, "DSC_0001.NEF", 0x3000)))
        assertFalse(quota.offer(asset(3u, "NIKON001.MOV", 0x300D)))
        assertTrue(quota.offer(asset(4u, "DSC_0002.JPG", 0x3801)))
        assertFalse(quota.offer(asset(5u, "DSC_0003.JPG", 0x3801)))

        assertEquals(2, quota.jpegCount)
        assertEquals(1, quota.rawCount)
        assertEquals(listOf(1u, 2u, 4u), quota.items.map { it.handle })
        assertTrue(quota.isComplete)
    }

    @Test
    fun interleavesCardsNewestFirstAndDeduplicatesHandles() {
        val handles = interleaveNewestHandles(
            listOf(
                listOf(1u, 2u, 3u),
                listOf(10u, 11u, 3u),
            ),
        )

        assertEquals(listOf(3u, 2u, 11u, 1u, 10u), handles)
    }

    private fun asset(handle: UInt, name: String, format: Int) = PtpObjectInfo(
        handle = handle,
        storageId = 1u,
        objectFormat = format.toUShort(),
        compressedSize = 1024,
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
