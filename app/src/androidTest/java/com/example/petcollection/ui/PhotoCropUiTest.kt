package com.example.petcollection.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.PhotoCropTestFixtures
import com.example.petcollection.R
import com.example.petcollection.data.*
import com.example.petcollection.ui.collection.CollectionUnlockState
import com.example.petcollection.ui.collection.EncounterDetailScreen
import com.example.petcollection.ui.collection.BreedImageArea
import com.example.petcollection.ui.home.PhotoCropScreen
import com.example.petcollection.ui.theme.PetCollectionTheme
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhotoCropUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val expectedRatio = 4f / 3f

    @Test fun sharedPhotosKeepAllFourEdgesInThumbnailsAndMismatchedLegacyContainers() {
        val directory = PhotoCropIO.sessionDirectory(context)
        val breed = MockBreedRepository(context.resources).getCollectionEntries().first()
        var reference by mutableStateOf<String?>(null)
        var containerRatio by mutableFloatStateOf(4f / 3f)
        try {
            compose.setContent { PetCollectionTheme { Surface {
                key(reference) {
                    BreedImageArea(breed, BreedPhoto("edges", reference), false,
                        Modifier.size(240.dp, 240.dp / containerRatio).testTag("edge-photo"))
                }
            } } }
            for ((width, height) in listOf(240 to 180, 180 to 240, 360 to 120)) {
                val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                image.setPixels(IntArray(width * height) { index ->
                    val x = index % width
                    val y = index / width
                    when {
                        y < height / 10 -> Color.RED
                        y >= height * 9 / 10 -> Color.BLUE
                        x < width / 10 -> Color.GREEN
                        x >= width * 9 / 10 -> Color.MAGENTA
                        else -> Color.YELLOW
                    }
                }, 0, width, 0, 0, width, height)
                val file = File(directory, "edges-$width-$height.png")
                file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
                compose.runOnIdle { reference = Uri.fromFile(file).toString() }
                compose.waitUntil(10000) {
                    compose.onAllNodesWithContentDescription(context.getString(R.string.breed_photo_description, breed.name))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                for (ratio in listOf(.75f, 1f, 4f / 3f, 2f)) {
                    compose.runOnIdle { containerRatio = ratio }
                    val shown = compose.onNodeWithTag("edge-photo").captureToImage().asAndroidBitmap()
                    val imageWidth = minOf(shown.width.toFloat(), shown.height * width.toFloat() / height)
                    val imageHeight = imageWidth * height / width
                    val left = (shown.width - imageWidth) / 2
                    val top = (shown.height - imageHeight) / 2
                    for ((point, color) in listOf(Offset(.5f, .05f) to Color.RED,
                        Offset(.5f, .95f) to Color.BLUE, Offset(.05f, .5f) to Color.GREEN,
                        Offset(.95f, .5f) to Color.MAGENTA)) {
                        val actual = shown.getPixel((left + imageWidth * point.x).toInt(),
                            (top + imageHeight * point.y).toInt())
                        assertEquals("Missing photo edge at $width x $height in $ratio container", color, actual)
                    }
                    shown.recycle()
                }
            }
        } finally { PhotoCropIO.deleteSession(directory) }
    }

    @Test fun collectedDetailShowsTheSameAreaAsTheFourByThreeCropAtDifferentWidths() {
        // A gradient in both axes makes any second crop visible in the pixel comparison.
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(IntArray(640 * 640) { index ->
            Color.rgb((index % 640) * 255 / 639, (index / 640) * 255 / 639, 80)
        }, 0, 640, 0, 0, 640, 640)
        val source = CropSource(bitmap, PhotoMetadata())
        val directory = PhotoCropIO.sessionDirectory(context)
        val db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        val repo = EncounterRepository(db.encounters(), context)
        val breed = MockBreedRepository(context.resources).getCollectionEntries().first { it.isCollectible }
        var record by mutableStateOf<EncounterRecord?>(null)
        var width by mutableStateOf(320.dp)
        var chosen: SquareCrop? = null
        var savedFile: File? = null
        try {
            compose.setContent { PetCollectionTheme { Surface(Modifier.width(width)) {
                val saved = record
                if (saved == null) PhotoCropScreen(source, false, false, {}, { chosen = it })
                else EncounterDetailScreen(breed, saved, {}, {}, {}, {}, false)
            } } }
            compose.onNodeWithTag("photo-crop-zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            compose.onNodeWithTag("photo-crop-frame").performTouchInput {
                swipe(center, center + Offset(-35f, 25f), 300)
            }
            val preview = compose.onNodeWithTag("photo-crop-frame").captureToImage().asAndroidBitmap()
            compose.onNodeWithTag("photo-crop-confirm").performClick()
            val crop = requireNotNull(chosen)
            assertEquals(4f / 3f, crop.aspectRatio, 0f)
            val exported = PhotoCropIO.export(source, crop, directory)
            val saved = runBlocking { repo.create(EncounterRecord(breed.id, exported.uri.toString())).first }
            savedFile = File(requireNotNull(Uri.parse(saved.photoUri).path))
            assertArrayEquals(exported.file.readBytes(), savedFile.readBytes())
            compose.runOnIdle { record = saved }
            for (displayWidth in listOf(320.dp, 280.dp)) {
                compose.runOnIdle { width = displayWidth }
                compose.waitUntil(10000) {
                    compose.onAllNodesWithContentDescription(context.getString(R.string.breed_photo_description, breed.name))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                val detail = compose.onNodeWithTag("encounter-detail-photo")
                val bounds = detail.fetchSemanticsNode().boundsInRoot
                assertEquals(4f / 3f, bounds.width / bounds.height, .01f)
                val shown = detail.captureToImage().asAndroidBitmap()
                for (x in listOf(.1f, .5f, .9f)) for (y in listOf(.1f, .5f, .9f)) {
                    val expected = preview.getPixel((preview.width * x).toInt(), (preview.height * y).toInt())
                    val actual = shown.getPixel((shown.width * x).toInt(), (shown.height * y).toInt())
                    assertEquals(Color.red(expected).toFloat(), Color.red(actual).toFloat(), 3f)
                    assertEquals(Color.green(expected).toFloat(), Color.green(actual).toFloat(), 3f)
                    assertEquals(Color.blue(expected).toFloat(), Color.blue(actual).toFloat(), 3f)
                }
                shown.recycle()
            }
            preview.recycle()
        } finally {
            db.close()
            savedFile?.delete()
            PhotoCropIO.deleteSession(directory)
            bitmap.recycle()
        }
    }
    private fun capture(name: String) {
        val file = File(context.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name")).use { it.readBytes() }
    }

    @Test fun galleryResultOpensCropAndCancelNeverRecognizesOrCollectsOriginal() {
        val original = PhotoCropTestFixtures.original(context)
        val bytes = original.readBytes()
        val db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        val state = CollectionUnlockState(EncounterRepository(db.encounters(), context))
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(code, Activity.RESULT_OK, Intent().setData(Uri.fromFile(original)))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        try {
            compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { PetCollectionApp(state) } }
            compose.waitUntil(10000) { state.collection.value.loaded }
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("zh-Hant")) }).resources
            compose.onNodeWithText(resources.getString(R.string.add_photo)).performScrollTo().performClick()
            compose.waitUntil(10000) { state.photoInput.source != null }
            compose.onNodeWithTag("photo-crop-page").assertExists()
            val frame = compose.onNodeWithTag("photo-crop-frame").fetchSemanticsNode().boundsInRoot
            assertEquals(expectedRatio, frame.width / frame.height, .01f)
            assertNull(state.photoInput.recognition.bitmap)
            assertNull(state.pendingEncounter)
            compose.onNodeWithTag("photo-crop-cancel").performClick()
            compose.onNodeWithTag("photo-crop-page").assertDoesNotExist()
            assertNull(state.pendingEncounter)
            assertTrue(state.collection.value.encounters.isEmpty())
            assertFalse(state.photoInput.recognition.processing)
            assertArrayEquals(bytes, original.readBytes())
        } finally {
            val store = ViewModelStore(); store.put("crop", state); store.clear()
            db.close(); original.delete()
        }
    }

    @Test fun dragPinchAndZoomControlsUseDetailRatioInBothLanguagesAndThemes() {
        val original = PhotoCropTestFixtures.original(context)
        val source = PhotoCropIO.prepare(context, Uri.fromFile(original))
        var language by mutableStateOf("zh-Hant")
        var dark by mutableStateOf(false)
        var chosen: SquareCrop? = null
        try {
            compose.setContent {
                val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
                CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration), LocalConfiguration provides configuration) {
                    PetCollectionTheme(darkTheme = dark) { Surface {
                        PhotoCropScreen(source, false, false, {}, { chosen = it })
                    } }
                }
            }
            val frame = compose.onNodeWithTag("photo-crop-frame")
            frame.performTouchInput {
                pinch(center - Offset(30f, 0f), center - Offset(90f, 0f),
                    center + Offset(30f, 0f), center + Offset(90f, 0f), 500)
            }
            compose.onNodeWithTag("photo-crop-confirm").performClick()
            assertTrue(requireNotNull(chosen).zoom > 1.5f)
            val before = requireNotNull(chosen)
            frame.performTouchInput { swipe(center, center + Offset(-70f, -35f), 500) }
            compose.onNodeWithTag("photo-crop-confirm").performClick()
            assertNotEquals(before.centerX, requireNotNull(chosen).centerX)
            assertNotEquals(before.centerY, requireNotNull(chosen).centerY)
            compose.onNodeWithTag("photo-crop-zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
            compose.onNodeWithTag("photo-crop-confirm").performClick()
            assertEquals(4f, requireNotNull(chosen).zoom, .01f)
            for (lang in listOf("zh-Hant", "en")) for (night in listOf(false, true)) {
                compose.runOnIdle { language = lang; dark = night }
                val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }).resources
                compose.onNodeWithText(resources.getString(R.string.crop_confirm)).assertExists()
                compose.onNodeWithText(resources.getString(R.string.crop_cancel)).assertExists()
                val bounds = frame.fetchSemanticsNode().boundsInRoot
                assertEquals(expectedRatio, bounds.width / bounds.height, .01f)
                capture("photo-crop-$lang-${if (night) "dark" else "light"}.png")
            }
        } finally { original.delete() }
    }
}
