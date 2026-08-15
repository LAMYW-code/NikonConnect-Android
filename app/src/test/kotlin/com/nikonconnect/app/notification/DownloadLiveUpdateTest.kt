package com.nikonconnect.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadLiveUpdateTest {
    @Test
    fun calculatesAndClampsOverallBatchPercentage() {
        assertEquals(25, update(transferred = 250, total = 1_000).percent)
        assertEquals(100, update(transferred = 1_500, total = 1_000).percent)
        assertEquals(0, update(transferred = -10, total = 1_000).percent)
        assertEquals(0, update(transferred = 10, total = 0).percent)
    }

    private fun update(transferred: Long, total: Long) = DownloadLiveUpdate(
        fileName = "DSC_0001.NEF",
        currentItem = 1,
        totalItems = 2,
        transferredBytes = transferred,
        totalBytes = total,
    )
}
