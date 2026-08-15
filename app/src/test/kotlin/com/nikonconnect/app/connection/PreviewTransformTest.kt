package com.nikonconnect.app.connection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewTransformTest {
    @Test
    fun scaleOneAlwaysCentersTheImage() {
        val result = clampPreviewPan(
            proposed = Offset(320f, -180f),
            scale = 1f,
            viewport = IntSize(1_000, 1_000),
            image = IntSize(2_000, 1_000),
        )

        assertEquals(Offset.Zero, result)
    }

    @Test
    fun panIsClampedToVisibleImageEdges() {
        val result = clampPreviewPan(
            proposed = Offset(800f, 400f),
            scale = 2f,
            viewport = IntSize(1_000, 1_000),
            image = IntSize(2_000, 1_000),
        )

        assertEquals(500f, result.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
    }
}
