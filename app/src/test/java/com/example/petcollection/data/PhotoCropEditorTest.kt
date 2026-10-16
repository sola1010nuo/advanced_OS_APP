package com.example.petcollection.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PhotoCropEditorTest {
    @Test fun freeStartsAndResetsToTheEntirePortraitOrLandscape() {
        for ((w, h) in listOf(400 to 1200, 1200 to 400, 500 to 500)) {
            val initial = PhotoCropEditor.initial(w, h, CropMode.FREE, 4f / 3f)
            assertEquals(CropBounds(0f, 0f, w.toFloat(), h.toFloat()), initial.crop(w, h).bounds(w, h))
            val changed = initial.resize(w, h, CropCorner.BOTTOM_RIGHT, -w / 2f, -h / 3f)
            assertNotEquals(initial, changed)
            assertEquals(initial, PhotoCropEditor.initial(w, h, CropMode.FREE, 4f / 3f))
        }
    }

    @Test fun eachCornerChangesBothDimensionsAndKeepsTheOppositeCornerAnchored() {
        val selection = CropBounds(100f, 100f, 600f, 400f)
        for (corner in CropCorner.entries) {
            val next = PhotoCropEditor(selection).resize(1000, 800, corner, 50f, 25f).selection
            assertNotEquals(selection.side, next.side)
            assertNotEquals(selection.height, next.height)
            val isLeft = corner == CropCorner.TOP_LEFT || corner == CropCorner.BOTTOM_LEFT
            val isTop = corner == CropCorner.TOP_LEFT || corner == CropCorner.TOP_RIGHT
            assertEquals(if (isLeft) 700f else 100f, if (isLeft) next.left + next.side else next.left, .001f)
            assertEquals(if (isTop) 500f else 100f, if (isTop) next.top + next.height else next.top, .001f)
        }
    }

    @Test fun randomResizingMovingAndZoomingCannotLeaveTheSourceOrExposeBlankPixels() {
        val random = Random(17)
        for (mode in CropMode.entries) {
            var editor = PhotoCropEditor.initial(640, 1200, mode, 4f / 3f)
            repeat(1000) {
                if (mode == CropMode.FREE) editor = editor.resize(640, 1200, CropCorner.entries[it % 4],
                    random.nextFloat() * 400 - 200, random.nextFloat() * 400 - 200)
                editor = editor.move(640, 1200, random.nextFloat() * 800 - 400, random.nextFloat() * 800 - 400)
                editor = editor.transform(640, 1200, 400f, 600f, 200f, 300f,
                    random.nextFloat() * 500 - 250, random.nextFloat() * 500 - 250, .5f + random.nextFloat() * 2)
                val bounds = editor.crop(640, 1200).bounds(640, 1200)
                assertTrue(bounds.left >= -.001f && bounds.top >= -.001f)
                assertTrue(bounds.side > 0f && bounds.height > 0f)
                assertTrue(bounds.left + bounds.side <= 640.001f)
                assertTrue(bounds.top + bounds.height <= 1200.001f)
                if (mode == CropMode.COVER) assertEquals(4f / 3f, bounds.side / bounds.height, .001f)
            }
        }
    }

    @Test fun zoomingPhotoKeepsTheOnscreenFrameStationary() {
        val editor = PhotoCropEditor.initial(600, 800, CropMode.COVER, 4f / 3f)
        val next = editor.transform(600, 800, 400f, 600f, 100f, 200f, 20f, -30f, 2f)
        val beforeViewport = editor.viewport(600, 800, 400f, 600f)
        val afterViewport = next.viewport(600, 800, 400f, 600f)
        assertEquals(beforeViewport.x + editor.selection.left * beforeViewport.scale,
            afterViewport.x + next.selection.left * afterViewport.scale, .001f)
        assertEquals(beforeViewport.y + editor.selection.top * beforeViewport.scale,
            afterViewport.y + next.selection.top * afterViewport.scale, .001f)
        assertEquals(editor.selection.side * beforeViewport.scale, next.selection.side * afterViewport.scale, .001f)
    }
}
