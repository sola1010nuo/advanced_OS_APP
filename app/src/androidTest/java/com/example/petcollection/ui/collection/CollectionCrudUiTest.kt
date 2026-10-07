package com.example.petcollection.ui.collection

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.*
import com.example.petcollection.ui.theme.PetCollectionTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.UUID

class CollectionCrudUiTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) })
    private val db = Room.inMemoryDatabaseBuilder(base, CollectionDatabase::class.java).build()
    private val repository = EncounterRepository(db.encounters(), base)
    private val state = CollectionUnlockState(repository)
    private val catalog = MockBreedRepository(context.resources)
    private val fixture = File(base.cacheDir, "collection-ui-${UUID.randomUUID()}.png")

    private fun mount() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.setContent {
            val owner = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
                LocalActivityResultRegistryOwner provides owner) {
                PetCollectionTheme { CollectionScreen(repository = catalog, unlockState = state) }
            }
        }
        compose.waitUntil(10000) { state.collection.value.loaded }
    }
    private fun text(id: Int) = context.getString(id)
    private fun click(id: Int) {
        if (id == R.string.breed_back) compose.onNode(hasScrollAction()).performScrollToIndex(0)
        val node = compose.onNodeWithText(text(id))
        try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
        node.performClick()
    }
    private fun add(id: String, name: String, normalFirst: Boolean = false) {
        compose.runOnIdle { assertTrue(state.onBreedRecognized(catalog.getBreed(id)!!, Uri.fromFile(fixture).toString())) }
        compose.onNode(hasText(text(R.string.encounter_name)) and hasSetTextAction()).performScrollTo().performTextReplacement(name)
        click(R.string.encounter_save)
        compose.waitUntil(10000) { !state.isWorking && state.encounters.any { it.petName == name } }
        if (normalFirst) {
            compose.onNodeWithText(text(R.string.unlock_title)).assertExists()
            compose.onNodeWithText(text(R.string.collection_done)).performClick()
        } else compose.onNodeWithText(text(R.string.unlock_title)).assertDoesNotExist()
    }
    private fun openAlbum(id: String, filter: Int?) {
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        filter?.let { compose.onNodeWithText(text(it)).performClick() }
        val name = catalog.getBreed(id)!!.name
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
    }
    private fun openPhoto(name: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
    }
    private fun deleteConfirmed(name: String) {
        click(R.string.encounter_delete)
        compose.onNodeWithText(text(R.string.encounter_delete_question)).assertExists()
        compose.onNode(hasText(text(R.string.encounter_delete)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { !state.isWorking && state.encounters.none { it.petName == name } }
    }

    @Test fun corgiAlbumDetailEditDeleteAndCoverUseRoomRecords() {
        mount()
        openAlbum("corgi", R.string.filter_dogs)
        add("corgi", "First", normalFirst = true)
        add("corgi", "Second")
        assertEquals(1, state.collection.value.discoveredCount(MockBreedRepository.normalBreedIds))
        openPhoto("Second")
        click(R.string.encounter_set_cover)
        compose.waitUntil(10000) {
            val second = state.encounters.first { it.petName == "Second" }
            state.collection.value.covers.any { it.encounterId == second.id }
        }
        compose.onNodeWithText(text(R.string.encounter_current_cover)).assertExists()
        click(R.string.encounter_back_album)
        openPhoto("First")
        click(R.string.encounter_edit)
        for ((label, value) in listOf(R.string.encounter_name to "Edited", R.string.encounter_date to "2026-10-08",
            R.string.encounter_time to "09:42", R.string.encounter_location to "Park", R.string.encounter_note to "Friendly")) {
            compose.onNode(hasText(text(label)) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
        }
        click(R.string.encounter_save)
        compose.waitUntil(10000) { !state.isWorking && state.encounters.any { it.petName == "Edited" } }
        for (value in listOf("Edited", "2026-10-08", "09:42", "Park", "Friendly")) {
            compose.onNodeWithText(value).performScrollTo().assertIsDisplayed()
        }
        click(R.string.encounter_delete)
        compose.onNodeWithText(text(R.string.encounter_cancel)).performClick()
        assertEquals(2, state.encounters.size)
        deleteConfirmed("Edited")
        assertEquals(1, state.collection.value.discoveredCount(MockBreedRepository.normalBreedIds))
        openPhoto("Second")
        deleteConfirmed("Second")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text(R.string.collection_album_empty)))
        compose.onNodeWithText(text(R.string.collection_album_empty)).assertIsDisplayed()
        assertEquals(0, state.collection.value.discoveredCount(MockBreedRepository.normalBreedIds))
        assertTrue(state.collection.value.covers.isEmpty())
        click(R.string.breed_back)
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithText(context.getString(R.string.collection_discovered_count, 0, 34)).assertExists()
    }

    @Test fun allSpecialAlbumsSupportCrudWithoutUnlockOrProgress() {
        mount()
        for ((id, filter) in listOf("other_cat" to R.string.filter_cats, "other_dog" to R.string.filter_dogs,
            "unregistered" to null)) {
            openAlbum(id, filter)
            add(id, "Special $id")
            openPhoto("Special $id")
            click(R.string.encounter_edit)
            compose.onNode(hasText(text(R.string.encounter_name)) and hasSetTextAction()).performScrollTo().performTextReplacement("Edited $id")
            click(R.string.encounter_save)
            compose.waitUntil(10000) { !state.isWorking && state.encounters.any { it.petName == "Edited $id" } }
            deleteConfirmed("Edited $id")
            assertEquals(0, state.collection.value.discoveredCount(MockBreedRepository.normalBreedIds))
            click(R.string.breed_back)
        }
    }

    @After fun close() = runBlocking {
        val store = androidx.lifecycle.ViewModelStore()
        store.put("collection", state)
        store.clear()
        db.encounters().observeAll().first().forEach { repository.delete(it.id) }
        db.close()
        fixture.delete()
        Unit
    }
}
