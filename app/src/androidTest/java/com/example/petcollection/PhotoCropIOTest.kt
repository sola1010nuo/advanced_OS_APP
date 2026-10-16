package com.example.petcollection

import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class PhotoCropIOTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun rectangularExportMatchesPreviewAndRequestedRatio() {
        val original = PhotoCropTestFixtures.original(context)
        val directory = PhotoCropIO.sessionDirectory(context)
        try {
            val source = PhotoCropIO.prepare(context, Uri.fromFile(original))
            for (ratio in listOf(.5f, 1.2f, 4f / 3f, 2f)) {
                val crop = SquareCrop(.6f, .5f, 2f, ratio)
                val output = PhotoCropIO.export(source, crop, directory)
                val exported = BitmapFactory.decodeFile(output.file.path)
                assertEquals(ratio, exported.width.toFloat() / exported.height, .02f)
                if (ratio == 4f / 3f) assertEquals(exported.width * 3, exported.height * 4)
                val preview = Bitmap.createBitmap(exported.width, exported.height, Bitmap.Config.ARGB_8888)
                PhotoCropIO.draw(Canvas(preview), source.bitmap, crop, preview.width.toFloat())
                assertTrue(exported.sameAs(preview))
                preview.recycle()
                exported.recycle()
            }
        } finally { PhotoCropIO.deleteSession(directory); original.delete() }
    }

    @Test fun failedExportDoesNotLeavePartialsOrChangeTheOriginal() {
        val original = PhotoCropTestFixtures.original(context)
        val bytes = original.readBytes()
        val directory = PhotoCropIO.sessionDirectory(context)
        try {
            val source = PhotoCropIO.prepare(context, Uri.fromFile(original))
            val missingDirectory = java.io.File(directory, "missing-directory")
            assertTrue(runCatching { PhotoCropIO.export(source, SquareCrop(), missingDirectory) }.isFailure)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertArrayEquals(bytes, original.readBytes())
        } finally { PhotoCropIO.deleteSession(directory); original.delete() }
    }

    @Test fun previewAndLosslessExportUseExactlyTheSameSquareAndNeverModifyOriginal() {
        val original = PhotoCropTestFixtures.original(context)
        val bytes = original.readBytes()
        val directory = PhotoCropIO.sessionDirectory(context)
        try {
            val source = PhotoCropIO.prepare(context, Uri.fromFile(original))
            assertEquals(LocalDateTime.of(2021, 6, 7, 8, 9, 10), source.metadata.capturedAt)
            assertEquals("+08:00", source.metadata.timeZoneId)
            assertEquals(25.0416667, requireNotNull(source.metadata.latitude), .00001)
            assertEquals(121.5, requireNotNull(source.metadata.longitude), .00001)
            val crop = SquareCrop(.75f, .5f, 2f)
            val output = PhotoCropIO.export(source, crop, directory)
            val exported = BitmapFactory.decodeFile(output.file.path)
            assertEquals(120, exported.width)
            assertEquals(exported.width, exported.height)
            val preview = Bitmap.createBitmap(exported.width, exported.height, Bitmap.Config.ARGB_8888)
            PhotoCropIO.draw(Canvas(preview), source.bitmap, crop, preview.width.toFloat())
            assertTrue(exported.sameAs(preview))
            assertTrue(Color.blue(exported.getPixel(60, 60)) > 240)
            assertTrue(Color.red(exported.getPixel(60, 60)) < 20)
            assertEquals(source.metadata, output.metadata)
            assertArrayEquals(bytes, original.readBytes())
            assertEquals(1, directory.listFiles()?.size)
            preview.recycle(); exported.recycle()
        } finally { PhotoCropIO.deleteSession(directory); original.delete() }
    }

    @Test fun exifOrientationIsAppliedBeforeTheUserChoosesCropCoordinates() {
        val original = PhotoCropTestFixtures.original(context, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        try {
            val source = PhotoCropIO.prepare(context, Uri.fromFile(original))
            assertEquals(240, source.bitmap.width)
            assertEquals(480, source.bitmap.height)
            assertTrue(Color.red(source.bitmap.getPixel(120, 60)) > 240)
            assertTrue(Color.blue(source.bitmap.getPixel(120, 400)) > 240)
        } finally { original.delete() }
    }

    @Test fun missingExifRemainsMissingInsteadOfUsingTheNewFileTimestamp() {
        val original = PhotoCropTestFixtures.original(context, exif = false)
        try {
            val source = PhotoCropIO.prepare(context, Uri.fromFile(original))
            assertNull(source.metadata.capturedAt)
            assertNull(source.metadata.timeZoneId)
            assertNull(source.metadata.latitude)
            assertNull(source.metadata.longitude)
        } finally { original.delete() }
    }

    @Test fun everyNativeModelAcceptsTheCroppedRgbFloatPixelsWithoutRepeatedNormalization() {
        val original = PhotoCropTestFixtures.original(context)
        val directory = PhotoCropIO.sessionDirectory(context)
        try {
            val source = PhotoCropIO.prepare(context, Uri.fromFile(original))
            for (region in listOf(CropRegion(.55f, .1f, .25f, .8f), CropRegion(.55f, .2f, .4f, .4f))) {
                val output = PhotoCropIO.export(source, SquareCrop.fromRegion(region, source.bitmap.width, source.bitmap.height), directory)
                val crop = BitmapFactory.decodeFile(output.file.path)
                val input = BreedClassifier.rgbInput(crop)
                assertEquals(224 * 224 * 3 * 4, input.remaining())
                repeat(224 * 224) {
                    assertTrue(input.float < 20f)
                    assertTrue(input.float < 20f)
                    assertTrue(input.float > 240f) // RGB [0,255]; original has red pixels outside the selected crop.
                }
                AnimalClassifier(context).use { assertTrue(it.classify(crop).confidence in 0f..1f) }
                for (type in BreedClassifier.AnimalType.entries) BreedClassifier(context, type).use {
                    assertTrue(it.classify(crop).confidence in 0f..1f)
                }
                CatColorClassifier(context).use { assertTrue(it.classify(crop).second in 0f..1f) }
                crop.recycle()
            }
        } finally { PhotoCropIO.deleteSession(directory); original.delete() }
    }
}
