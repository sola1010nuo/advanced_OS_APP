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
        val message = when (id) {
            "monkey" -> if (normalFirst) R.string.monkey_first else R.string.monkey_again
            "other_cat" -> R.string.recognition_other_cat
            "other_dog" -> R.string.recognition_other_dog
            "unregistered" -> R.string.recognition_unregistered
            else -> if (normalFirst) R.string.recognition_first else R.string.recognition_again
        }
        compose.onNodeWithText(text(message)).assertExists()
        compose.onNodeWithText(text(R.string.encounter_title)).assertDoesNotExist()
        click(R.string.recognition_continue)
        compose.onNode(hasText(text(R.string.encounter_name)) and hasSetTextAction()).performScrollTo().performTextReplacement(name)
        click(R.string.encounter_save)
        compose.waitUntil(10000) { !state.isWorking && state.encounters.any { it.petName == name } }
        compose.onNodeWithText(text(R.string.recognition_result)).assertDoesNotExist()
    }
    private fun openAlbum(id: String, filter: Int?) {
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        filter?.let { compose.onNodeWithText(text(it)).performClick() }
        if (id == "unregistered") return
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

    @Test fun coatAlbumsSupportIndependentPhotosAndPreserveBreedProgress() {
        mount()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text(R.string.coat_categories)))
        compose.onNodeWithText(text(R.string.coat_categories)).assertIsDisplayed()
        for (id in MockBreedRepository.coatCategoryIds) {
            openAlbum(id, R.string.filter_cats)
            compose.onNodeWithText(catalog.getBreed(id)!!.name).assertExists()
            compose.runOnIdle {
                assertTrue(state.onClassificationResult(id, catalog.getCollectionEntries(), Uri.fromFile(fixture).toString()))
                assertTrue(state.isFirstDiscovery)
                assertFalse(state.applyTo(catalog.getBreed(id)!!).isDiscovered)
                assertTrue(state.collection.value.forBreed(id).isEmpty())
            }
            compose.onNodeWithText(text(R.string.recognition_first)).assertExists()
            click(R.string.encounter_cancel)
            assertTrue(state.collection.value.forBreed(id).isEmpty())
            add(id, "Coat $id", normalFirst = true)
            compose.waitUntil(10000) { state.collection.value.forBreed(id).size == 1 }
            assertTrue(state.applyTo(catalog.getBreed(id)!!).isDiscovered)
            add(id, "Second $id")
            assertEquals(1, state.collection.value.statistics(catalog.getCollectionEntries(), Species.CAT).discovered)
            assertEquals(28, state.collection.value.statistics(catalog.getCollectionEntries(), Species.CAT).totalBreeds)
            openPhoto("Second $id")
            click(R.string.encounter_set_cover)
            compose.waitUntil(10000) {
                state.applyTo(catalog.getBreed(id)!!).coverPhoto?.id == state.encounters.first { it.petName == "Second $id" }.id
            }
            compose.onNodeWithText(text(R.string.encounter_current_cover)).assertExists()
            deleteConfirmed("Second $id")
            assertTrue(state.applyTo(catalog.getBreed(id)!!).isDiscovered)
            openPhoto("Coat $id")
            deleteConfirmed("Coat $id")
            assertFalse(state.applyTo(catalog.getBreed(id)!!).isDiscovered)
            click(R.string.breed_back)
        }
    }

    @Test fun otherCatsRemainPhotoAlbumWithoutDiscoveryState() {
        mount()
        val other = catalog.getBreed("other_cat")!!
        assertFalse(other.isCoatCategory)
        assertFalse(other.showsDiscoveryState)
        openAlbum(other.id, R.string.filter_cats)
        compose.onAllNodesWithText(text(R.string.breed_undiscovered)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.breed_discovered)).assertCountEquals(0)
        compose.runOnIdle {
            assertTrue(state.onClassificationResult(other.id, catalog.getCollectionEntries(), Uri.fromFile(fixture).toString()))
            assertFalse(state.isFirstDiscovery)
        }
        compose.onNodeWithText(text(R.string.recognition_other_cat)).assertExists()
        click(R.string.encounter_cancel)
        add(other.id, "Other cat")
        assertEquals(1, state.collection.value.forBreed(other.id).size)
        add(other.id, "Second other cat")
        openPhoto("Second other cat")
        click(R.string.encounter_set_cover)
        compose.waitUntil(10000) {
            state.applyTo(other).coverPhoto?.id == state.encounters.first { it.petName == "Second other cat" }.id
        }
        compose.onNodeWithText(text(R.string.encounter_current_cover)).assertExists()
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

    @Test fun customAlbumsStartAtFirstCellAndMonkeyStaysBeforeThem() {
        runBlocking { repository.createAlbum("First custom"); repository.createAlbum("Second custom") }
        mount()
        click(R.string.filter_other)
        compose.onNodeWithText(catalog.getBreed("monkey")!!.name).assertDoesNotExist()
        val first = compose.onNodeWithText("First custom").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Second custom").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 2f)
        assertTrue(first.left < second.left)
        runBlocking { db.encounters().create(EncounterRecord("monkey", null)) }
        compose.waitUntil(10000) { state.collection.value.forBreed("monkey").isNotEmpty() }
        val monkey = compose.onNodeWithText(catalog.getBreed("monkey")!!.name).fetchSemanticsNode().boundsInRoot
        val custom = compose.onNodeWithText("First custom").fetchSemanticsNode().boundsInRoot
        assertTrue(monkey.left < custom.left)
        val source = compose.onNodeWithText("Second custom").fetchSemanticsNode().boundsInRoot
        val destination = compose.onNodeWithText("First custom").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithText("Second custom").performTouchInput {
            down(center); advanceEventTime(700); moveTo(center)
            moveTo(destination - source.topLeft, delayMillis = 32); up()
        }
        compose.waitUntil(10000) { !state.isWorking && state.collection.value.albums.first().name == "Second custom" }
        assertTrue(compose.onNodeWithText(catalog.getBreed("monkey")!!.name).fetchSemanticsNode().boundsInRoot.left <
            compose.onNodeWithText("Second custom").fetchSemanticsNode().boundsInRoot.left)
        val lockedOrder = state.collection.value.albums.map { it.id }
        val customBounds = compose.onNodeWithText("Second custom").fetchSemanticsNode().boundsInRoot
        val monkeyCenter = compose.onNodeWithText(catalog.getBreed("monkey")!!.name).fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithText("Second custom").performTouchInput {
            down(center); advanceEventTime(700); moveTo(center)
            moveTo(monkeyCenter - customBounds.topLeft, delayMillis = 32); up()
        }
        compose.runOnIdle { assertEquals(lockedOrder, state.collection.value.albums.map { it.id }) }
        compose.onNodeWithText(text(R.string.other_earlier)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.other_later)).assertDoesNotExist()
    }

    @Test fun otherPhotosCanMoveIntoAlbumAndReturnAfterAlbumOnlyDeletion() {
        runBlocking {
            repository.createAlbum("My album")
            db.encounters().create(EncounterRecord("unregistered", "content://fixture/other", petName = "Loose photo"))
        }
        mount()
        click(R.string.filter_other)
        compose.onNodeWithText(text(R.string.collection_add_encounter)).assertDoesNotExist()
        click(R.string.other_organize)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Loose photo"))
        compose.onNode(isToggleable()).performClick()
        click(R.string.other_move)
        compose.onNode(hasText("My album") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { !state.isWorking && state.collection.value.members.size == 1 }
        compose.onNodeWithText("Loose photo").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithText("My album").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Loose photo"))
        compose.onNodeWithText("Loose photo").performClick()
        compose.onNodeWithText(text(R.string.encounter_current_cover)).performScrollTo().assertIsDisplayed()
        click(R.string.encounter_back_album)
        click(R.string.other_delete_album)
        compose.onNodeWithText(text(R.string.other_cancel)).performClick()
        assertEquals(1, state.collection.value.albums.size)
        click(R.string.other_delete_album)
        compose.onNodeWithText(text(R.string.other_keep_photos)).performClick()
        compose.waitUntil(10000) { !state.isWorking && state.collection.value.albums.isEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Loose photo"))
        compose.onNodeWithText("Loose photo").assertIsDisplayed()
        assertEquals(1, state.encounters.size)
        assertTrue(state.collection.value.members.isEmpty())
    }

    @Test fun newAlbumImmediatelySelectsPhotosAllowsEmptyAndRenamesFromTitle() {
        runBlocking {
            db.encounters().create(EncounterRecord("unregistered", "content://fixture/one", petName = "One"))
            db.encounters().create(EncounterRecord("unregistered", "content://fixture/two", petName = "Two"))
        }
        mount()
        click(R.string.filter_other)
        click(R.string.other_add_album)
        compose.onNode(hasSetTextAction()).performTextReplacement("New album")
        compose.onNode(hasText(text(R.string.collection_done)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { !state.isWorking && state.collection.value.albums.size == 1 }
        compose.onNodeWithText(text(R.string.other_choose_photos)).assertExists()
        compose.onNodeWithText("One").performClick()
        compose.onNodeWithText("Two").performClick()
        compose.onNodeWithText(context.getString(R.string.other_selected_count, 2)).assertExists()
        click(R.string.collection_done)
        compose.waitUntil(10000) { !state.isWorking && state.collection.value.members.size == 2 }
        compose.onNodeWithTag("album_title").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("   ")
        compose.onNode(hasText(text(R.string.collection_done)) and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("Renamed album")
        compose.onNode(hasText(text(R.string.collection_done)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { !state.isWorking && state.collection.value.albums.single().name == "Renamed album" }
        click(R.string.breed_back)
        compose.onNodeWithText(text(R.string.other_delete_album)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.other_rename)).assertDoesNotExist()
        click(R.string.other_add_album)
        compose.onNode(hasSetTextAction()).performTextReplacement("Empty album")
        compose.onNode(hasText(text(R.string.collection_done)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { !state.isWorking && state.collection.value.albums.size == 2 }
        click(R.string.collection_done)
        compose.onNodeWithTag("album_title").assertTextEquals("Empty album")
        assertEquals(2, state.collection.value.members.size)
        click(R.string.breed_back)
        compose.onNodeWithText("Renamed album").performClick()
        click(R.string.other_delete_album)
        compose.onNodeWithText(text(R.string.other_delete_photos)).performClick()
        compose.onNodeWithText(text(R.string.other_irreversible)).assertExists()
        compose.onNodeWithText(text(R.string.other_delete_photos)).performClick()
        compose.waitUntil(10000) { !state.isWorking && state.encounters.isEmpty() }
        assertEquals("Empty album", state.collection.value.albums.single().name)
    }

    @Test fun albumSelectionCanReturnMoveAndDeletePhotosWithConfirmation() {
        runBlocking {
            val album = repository.createAlbum("Source")
            repository.createAlbum("Destination")
            val records = (1..3).map { EncounterRecord("unregistered", "content://fixture/$it", petName = "Photo $it") }
            records.forEach { db.encounters().create(it) }
            repository.movePhotos(records.map { it.id }, album)
        }
        mount()
        click(R.string.filter_other)
        compose.onNodeWithText("Source").performClick()
        click(R.string.other_organize)
        compose.onNodeWithText("Photo 1").performClick()
        click(R.string.other_return_photos)
        compose.waitUntil(10000) { !state.isWorking && state.collection.value.members.size == 2 }
        click(R.string.other_organize)
        compose.onNodeWithText("Photo 2").performClick()
        click(R.string.other_move)
        compose.onNode(hasText("Destination") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { !state.isWorking && state.collection.value.members.map { it.albumId }.toSet().size == 2 }
        click(R.string.other_organize)
        compose.onNodeWithText("Photo 3").performClick()
        click(R.string.other_delete_selected)
        compose.onNode(hasText(text(R.string.other_cancel)) and hasAnyAncestor(isDialog())).performClick()
        assertEquals(3, state.encounters.size)
        click(R.string.other_delete_selected)
        compose.onNode(hasText(text(R.string.encounter_delete)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { !state.isWorking && state.encounters.size == 2 }
        assertEquals(setOf("Photo 1", "Photo 2"), state.encounters.map { it.petName }.toSet())
    }

    @Test fun allSpecialAlbumsSupportCrudWithoutUnlockOrProgress() {
        mount()
        compose.onNodeWithText(text(R.string.filter_cats)).assertIsSelected()
        compose.onNodeWithText(text(R.string.filter_dogs)).assertExists()
        compose.onNodeWithText(text(R.string.filter_other)).assertExists()
        compose.onNodeWithText(text(R.string.unregistered)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.filter_other)).performClick()
        compose.onNodeWithText(text(R.string.filter_other)).assertIsSelected()
        compose.onNodeWithText(text(R.string.other_photos)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.other_cats)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.other_dogs)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.collection_discovered_count, 0, 28)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.collection_discovered_count, 0, 34)).assertDoesNotExist()
        for ((id, filter) in listOf("other_cat" to R.string.filter_cats, "other_dog" to R.string.filter_dogs,
            "unregistered" to R.string.filter_other)) {
            openAlbum(id, filter)
            add(id, "Special $id")
            openPhoto("Special $id")
            click(R.string.encounter_edit)
            compose.onNode(hasText(text(R.string.encounter_name)) and hasSetTextAction()).performScrollTo().performTextReplacement("Edited $id")
            click(R.string.encounter_save)
            compose.waitUntil(10000) { !state.isWorking && state.encounters.any { it.petName == "Edited $id" } }
            deleteConfirmed("Edited $id")
            assertEquals(0, state.collection.value.discoveredCount(MockBreedRepository.normalBreedIds))
            if (id != "unregistered") click(R.string.breed_back)
        }
    }

    @Test fun cancellingResultOrEditorNeverWritesOrUnlocks() {
        mount()
        val breed = catalog.getBreed("corgi")!!
        for (continueToEditor in listOf(false, true)) {
            compose.runOnIdle {
                assertTrue(state.onBreedRecognized(breed, Uri.fromFile(fixture).toString()))
                assertTrue(state.isFirstDiscovery)
                // Saving cannot skip the result.
                state.saveEncounter(state.pendingEncounter!!)
                assertFalse(state.isWorking)
            }
            compose.onNodeWithText(text(R.string.recognition_first)).assertExists()
            if (continueToEditor) click(R.string.recognition_continue)
            click(R.string.encounter_cancel)
            compose.runOnIdle {
                assertNull(state.pendingEncounter)
                assertFalse(state.applyTo(breed).isDiscovered)
                assertEquals(0, state.collection.value.statistics(catalog.getCollectionEntries()).encounterCount)
            }
            assertEquals(0, runBlocking { db.encounters().count(breed.id) })
        }
    }
    @Test fun secretMonkeyPreviewCancelSaveRepeatCoverAndLastDeleteUseExistingFlow() {
        mount()
        val id = MockBreedRepository.MONKEY_ID
        val breed = catalog.getBreed(id)!!
        val total = state.collection.value.statistics(catalog.getCollectionEntries()).totalBreeds
        assertEquals(catalog.getBreeds().size, total)
        assertEquals(Species.entries.sumOf { MockBreedRepository.total(it) } + MockBreedRepository.coatCategoryIds.size + 1, total)
        click(R.string.filter_other)
        compose.onNodeWithText(breed.name).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.monkey_rarity)).assertDoesNotExist()
        for (continueToEditor in listOf(false, true)) {
            compose.runOnIdle {
                assertTrue(state.onClassificationResult(id, catalog.getCollectionEntries(), Uri.fromFile(fixture).toString()))
                assertTrue(state.isFirstDiscovery)
                assertEquals(Uri.fromFile(fixture).toString(), state.pendingEncounter!!.photoUri)
            }
            compose.onNodeWithText(text(R.string.monkey_first)).assertExists()
            compose.onNodeWithText(text(R.string.monkey_discovery)).assertExists()
            assertEquals(0, runBlocking { db.encounters().count(id) })
            if (continueToEditor) click(R.string.recognition_continue)
            click(R.string.encounter_cancel)
            compose.onNodeWithText(breed.name).assertDoesNotExist()
            assertEquals(0, state.collection.value.statistics(catalog.getCollectionEntries()).discovered)
        }
        add(id, "Senior first", normalFirst = true)
        val first = state.encounters.single()
        assertEquals(first.id, state.collection.value.applyTo(breed).coverPhoto!!.id)
        compose.onNodeWithText(breed.name).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.other_photos)).assertIsDisplayed()
        assertTrue(compose.onNodeWithText(breed.name).fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText(text(R.string.other_photos)).fetchSemanticsNode().boundsInRoot.top)
        add(id, "Senior second")
        assertEquals(1, state.collection.value.statistics(catalog.getCollectionEntries()).discovered)
        assertEquals(0, state.collection.value.statistics(catalog.getCollectionEntries(), Species.CAT).discovered)
        assertEquals(0, state.collection.value.statistics(catalog.getCollectionEntries(), Species.DOG).discovered)
        openAlbum(id, null)
        compose.onNodeWithText(text(R.string.monkey_legend)).assertExists()
        compose.onNodeWithText(text(R.string.monkey_inspiration)).assertExists()
        openPhoto("Senior second")
        click(R.string.encounter_set_cover)
        compose.waitUntil(10000) {
            state.collection.value.applyTo(breed).coverPhoto?.id == state.encounters.first { it.petName == "Senior second" }.id
        }
        deleteConfirmed("Senior second")
        assertEquals(first.id, state.collection.value.applyTo(breed).coverPhoto!!.id)
        openPhoto("Senior first")
        deleteConfirmed("Senior first")
        compose.waitUntil(10000) { !state.collection.value.applyTo(breed).isVisibleInCollection }
        compose.onNodeWithText(breed.name).assertDoesNotExist()
        assertEquals(0, state.collection.value.statistics(catalog.getCollectionEntries()).discovered)
        assertEquals(total, state.collection.value.statistics(catalog.getCollectionEntries()).totalBreeds)
        compose.onNodeWithText(text(R.string.other_photos)).assertExists()
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
