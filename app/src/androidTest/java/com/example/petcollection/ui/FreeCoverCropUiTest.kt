package com.example.petcollection.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.*
import com.example.petcollection.ui.collection.BreedImageArea
import com.example.petcollection.ui.collection.CollectionUnlockState
import com.example.petcollection.ui.home.PhotoCropHost
import com.example.petcollection.ui.home.PhotoCropScreen
import com.example.petcollection.ui.theme.PetCollectionTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class FreeCoverCropUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun bitmap(width: Int, height: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(IntArray(width * height) { index ->
            Color.rgb(index % width * 255 / (width - 1), index / width * 255 / (height - 1), 80)
        }, 0, width, 0, 0, width, height)
    }

    @Test fun portraitAndLandscapeAllowAllFourCornersFrameMovePinchAndReset() {
        var source by mutableStateOf(CropSource(bitmap(300, 600), PhotoMetadata()))
        var chosen: SquareCrop? = null
        compose.setContent { PetCollectionTheme { Surface {
            PhotoCropScreen(source, false, false, {}, { chosen = it })
        } } }
        for ((w, h) in listOf(300 to 600, 600 to 300)) {
            compose.runOnIdle { source = CropSource(bitmap(w, h), PhotoMetadata()) }
            compose.onNodeWithTag("photo-crop-confirm").performClick()
            assertEquals(CropRegion(0f, 0f, 1f, 1f), chosen!!.region)
            for (corner in 0..3) {
                val before = chosen!!.bounds(w, h)
                compose.onNodeWithTag("photo-crop-handle-$corner").performTouchInput {
                    swipe(center, center + Offset(if (corner % 2 == 0) 25f else -25f,
                        if (corner < 2) 25f else -25f), 250)
                }
                compose.onNodeWithTag("photo-crop-confirm").performClick()
                val after = chosen!!.bounds(w, h)
                assertTrue("Corner $corner must resize width", after.side < before.side)
                assertTrue("Corner $corner must resize height", after.height < before.height)
            }
            val beforeMove = chosen!!.bounds(w, h)
            compose.onNodeWithTag("photo-crop-frame").performTouchInput { swipe(center, center + Offset(15f, 10f), 250) }
            compose.onNodeWithTag("photo-crop-confirm").performClick()
            val moved = chosen!!.bounds(w, h)
            assertNotEquals(beforeMove.left, moved.left)
            assertEquals(beforeMove.side, moved.side, .01f)
            compose.onNodeWithTag("photo-crop-frame").performTouchInput {
                pinch(center - Offset(20f, 0f), center - Offset(55f, 0f),
                    center + Offset(20f, 0f), center + Offset(55f, 0f), 400)
            }
            compose.onNodeWithTag("photo-crop-confirm").performClick()
            assertTrue(chosen!!.bounds(w, h).side < moved.side)
            compose.onNodeWithTag("photo-crop-reset").performClick()
            compose.onNodeWithTag("photo-crop-confirm").performClick()
            assertEquals(CropRegion(0f, 0f, 1f, 1f), chosen!!.region)
        }
    }

    @Test fun coverPreviewPersistsSeparatelyAndNeverRecognizesOrChangesCollectedPhoto() {
        val name = "cover-crop-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, CollectionDatabase::class.java, name).build()
        var db = open()
        val repository = EncounterRepository(db.encounters(), context)
        val breed = MockBreedRepository(context.resources).getCollectionEntries().first { it.isCollectible }
        val original = File(context.cacheDir, "$name.png")
        val photo = bitmap(600, 800)
        original.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }
        photo.recycle()
        val saved = runBlocking { repository.create(EncounterRecord(breed.id, Uri.fromFile(original).toString())).first }
        val file = File(requireNotNull(Uri.parse(saved.photoUri).path))
        val bytes = file.readBytes()
        val state = CollectionUnlockState(repository)
        var display by mutableStateOf<Breed?>(null)
        try {
            compose.setContent { PetCollectionTheme { Surface(Modifier.width(320.dp)) {
                val cover = display
                if (cover == null) PhotoCropHost(state.photoInput)
                else BreedImageArea(cover, cover.coverPhoto, false,
                    Modifier.width(300.dp).aspectRatio(4f / 3f).testTag("saved-cover"))
            } } }
            compose.waitUntil(10000) { state.collection.value.loaded }
            compose.runOnIdle { state.photoInput.beginCover(breed.id, saved) }
            compose.waitUntil(10000) { state.photoInput.source != null }
            assertEquals(CropMode.COVER, state.photoInput.mode)
            compose.onNodeWithTag("photo-crop-handle-0").assertDoesNotExist()
            compose.onNodeWithTag("photo-crop-zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            compose.onNodeWithTag("photo-crop-frame").performTouchInput { swipe(center, center + Offset(30f, -20f), 300) }
            val frame = compose.onNodeWithTag("photo-crop-frame")
            val bounds = frame.fetchSemanticsNode().boundsInRoot
            assertEquals(4f / 3f, bounds.width / bounds.height, .01f)
            val preview = frame.captureToImage().asAndroidBitmap()
            compose.onNodeWithTag("photo-crop-confirm").performClick()
            compose.waitUntil(10000) { state.photoInput.source == null && !state.photoInput.exporting }
            assertNull(state.pendingEncounter)
            assertNull(state.photoInput.recognition.bitmap)
            assertFalse(state.photoInput.recognition.processing)
            assertArrayEquals(bytes, file.readBytes())
            val record = runBlocking { db.encounters().get(saved.id) }
            assertEquals(saved, record)
            val region = state.collection.value.covers.single().cropRegion
            assertNotNull(region)
            compose.runOnIdle { ViewModelStore().apply { put("cover", state); clear() } }
            db.close()
            db = open()
            val restored = runBlocking { EncounterRepository(db.encounters(), context).collection.first { it.loaded } }
            assertEquals(region, restored.covers.single().cropRegion)
            val updated = restored.applyTo(breed)
            assertNull(updated.collectedPhotos.single().cropRegion)
            compose.runOnIdle { display = updated }
            compose.waitUntil(10000) {
                compose.onAllNodesWithContentDescription(context.getString(R.string.breed_photo_description, breed.name))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val shown = compose.onNodeWithTag("saved-cover").captureToImage().asAndroidBitmap()
            for (x in listOf(.1f, .5f, .9f)) for (y in listOf(.1f, .5f, .9f)) {
                val expected = preview.getPixel((preview.width * x).toInt(), (preview.height * y).toInt())
                val actual = shown.getPixel((shown.width * x).toInt(), (shown.height * y).toInt())
                assertEquals(Color.red(expected).toFloat(), Color.red(actual).toFloat(), 3f)
                assertEquals(Color.green(expected).toFloat(), Color.green(actual).toFloat(), 3f)
            }
            preview.recycle(); shown.recycle()
        } finally {
            compose.runOnIdle { ViewModelStore().apply { put("cover", state); clear() } }
            db.close(); context.deleteDatabase(name); original.delete(); file.delete()
        }
    }
}
