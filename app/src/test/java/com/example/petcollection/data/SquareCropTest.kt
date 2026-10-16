package com.example.petcollection.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SquareCropTest {
    @Test fun fractionalDetailRatiosDoNotInvertClampingRangeAtMinimumZoom() {
        for (width in 100..2000) {
            val ratio = (width - 48f) / 280f
            val crop = SquareCrop(aspectRatio = ratio)
            val bounds = crop.bounds(480, 240)
            assertTrue(bounds.top >= 0f)
            assertTrue(bounds.top + bounds.height <= 240.001f)
            crop.gesture(480, 240, 400f, 200f, 200f / ratio, 0f, 0f, 1f)
        }
    }
    @Test fun rectangularCropFitsPortraitAndLandscapeSources() {
        assertEquals(CropBounds(0f, 150f, 200f, 100f), SquareCrop(aspectRatio = 2f).bounds(200, 400))
        assertEquals(CropBounds(150f, 0f, 100f, 200f), SquareCrop(aspectRatio = .5f).bounds(400, 200))
    }

    @Test fun rectangularPinchPreservesCentroidAndGesturesStayInsideSource() {
        for (ratio in listOf(.5f, 1.2f, 2f)) {
            var crop = SquareCrop(aspectRatio = ratio)
            val before = crop.bounds(640, 1200)
            crop = crop.gesture(640, 1200, 400f, 120f, 160f / ratio, 0f, 0f, 2f)
            val after = crop.bounds(640, 1200)
            assertEquals(before.left + .3f * before.side, after.left + .3f * after.side, .001f)
            assertEquals(before.top + .4f * before.height, after.top + .4f * after.height, .001f)
            val random = Random(100)
            repeat(1000) {
                crop = crop.gesture(640, 1200, 400f, random.nextFloat() * 400,
                    random.nextFloat() * 400 / ratio, (random.nextFloat() - .5f) * 1000,
                    (random.nextFloat() - .5f) * 1000, .1f + random.nextFloat() * 4)
                val bounds = crop.bounds(640, 1200)
                assertEquals(ratio, bounds.side / bounds.height, .001f)
                assertTrue(bounds.left >= -.001f && bounds.top >= -.001f)
                assertTrue(bounds.left + bounds.side <= 640.001f)
                assertTrue(bounds.top + bounds.height <= 1200.001f)
            }
        }
    }

    @Test fun defaultUsesCenteredSquareForPortraitLandscapeAndSquarePhotos() {
        assertEquals(CropBounds(100f, 0f, 200f), SquareCrop().bounds(400, 200))
        assertEquals(CropBounds(0f, 100f, 200f), SquareCrop().bounds(200, 400))
        assertEquals(CropBounds(0f, 0f, 200f), SquareCrop().bounds(200, 200))
    }
    @Test fun zoomAndDragMoveTheVisibleSourceWithoutChangingItsAspectRatio() {
        val zoomed = SquareCrop().gesture(400, 200, 200f, 100f, 100f, 0f, 0f, 2f)
        assertEquals(CropBounds(150f, 50f, 100f), zoomed.bounds(400, 200))
        val moved = zoomed.gesture(400, 200, 200f, 100f, 100f, -50f, -50f, 1f)
        assertEquals(CropBounds(175f, 75f, 100f), moved.bounds(400, 200))
    }
    @Test fun pinchKeepsThePointUnderTheFingersStable() {
        val before = SquareCrop().bounds(400, 400)
        val after = SquareCrop().gesture(400, 400, 200f, 50f, 80f, 0f, 0f, 2f).bounds(400, 400)
        assertEquals(before.left + .25f * before.side, after.left + .25f * after.side, .001f)
        assertEquals(before.top + .4f * before.side, after.top + .4f * after.side, .001f)
    }
    @Test fun viewportResizingDoesNotChangeTheChosenCrop() {
        val small = SquareCrop().gesture(1200, 800, 200f, 100f, 100f, -40f, 20f, 2f)
        val large = SquareCrop().gesture(1200, 800, 400f, 200f, 200f, -80f, 40f, 2f)
        assertEquals(small, large)
    }
    @Test fun randomGesturesNeverLeaveBlankMarginsOrExceedZoomLimits() {
        val random = Random(100)
        var crop = SquareCrop()
        repeat(1000) {
            crop = crop.gesture(640, 1200, 400f, random.nextFloat() * 400, random.nextFloat() * 400,
                (random.nextFloat() - .5f) * 1000, (random.nextFloat() - .5f) * 1000, .1f + random.nextFloat() * 4)
            val bounds = crop.bounds(640, 1200)
            assertTrue(crop.zoom in 1f..8f)
            assertTrue(bounds.left >= -.001f && bounds.top >= -.001f)
            assertTrue(bounds.left + bounds.side <= 640.001f && bounds.top + bounds.side <= 1200.001f)
        }
    }
}
