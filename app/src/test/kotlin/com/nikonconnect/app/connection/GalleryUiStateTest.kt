package com.nikonconnect.app.connection

import com.nikonconnect.ptp.PtpObjectInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryUiStateTest {
    @Test
    fun filtersLoadedAssetsWithoutChangingSourceItems() {
        val jpeg = asset(1u, "DSC_0001.JPG", 0x3801)
        val raw = asset(2u, "DSC_0001.NEF", 0x3000)
        val source = listOf(GalleryAssetUi(jpeg), GalleryAssetUi(raw))

        assertEquals(listOf(jpeg), GalleryUiState(items = source, filter = GalleryFilter.JPEG).visibleItems.map { it.info })
        assertEquals(listOf(raw), GalleryUiState(items = source, filter = GalleryFilter.RAW).visibleItems.map { it.info })
        assertEquals(2, GalleryUiState(items = source).visibleItems.size)
        assertEquals(1, GalleryUiState(items = source).jpegCount)
        assertEquals(1, GalleryUiState(items = source).rawCount)
    }

    @Test
    fun transferStateCarriesPinnedProgressMetadata() {
        val active = GalleryUiState(
            transferName = "DSC_0001.NEF",
            transferProgress = 0f,
            transferIndex = 1,
            transferTotal = 3,
        )

        assertTrue(active.isTransferring)
        assertEquals(1, active.transferIndex)
        assertEquals(3, active.transferTotal)
        assertFalse(active.copy(transferName = null).isTransferring)
        assertEquals(
            3,
            active.copy(
                transferName = null,
                transferIndex = 0,
                transferTotal = 0,
                completedTransfers = 3,
            ).completedTransfers,
        )
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
