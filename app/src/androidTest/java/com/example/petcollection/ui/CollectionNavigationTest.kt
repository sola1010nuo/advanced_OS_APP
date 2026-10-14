package com.example.petcollection.ui

import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.*
import com.example.petcollection.ui.collection.CollectionUnlockState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class CollectionNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val resources = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
        setLocale(Locale.forLanguageTag("zh-Hant"))
    }).resources
    private val catalog = MockBreedRepository(resources)
    private val db = Room.inMemoryDatabaseBuilder(base, CollectionDatabase::class.java).build()
    private val state = CollectionUnlockState(EncounterRepository(db.encounters(), base))
    private var rootBacks = 0

    private fun mount() {
        runBlocking {
            db.encounters().createAlbum("navigation-album", "Navigation album")
            for (breed in listOf("british_shorthair", "corgi", "monkey", "other_cat", "other_dog", "unregistered")) {
                db.encounters().create(EncounterRecord(breed, "content://navigation/$breed", id = "navigation-$breed"))
            }
            db.encounters().create(EncounterRecord("unregistered", "content://navigation/custom", id = "navigation-custom"))
            db.encounters().movePhotos(listOf("navigation-custom"), "navigation-album")
        }
        compose.setContent {
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            // Catch only root-level back so the test activity remains available for assertions.
            DisposableEffect(owner) {
                val fallback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() { rootBacks++ }
                }
                owner.onBackPressedDispatcher.addCallback(fallback)
                onDispose { fallback.remove() }
            }
            PetCollectionApp(state)
        }
        compose.waitUntil(10000) { state.collection.value.loaded }
        compose.onNodeWithTag("navigation-COLLECTION").performClick()
        assertRoot()
    }

    @Test fun existingCoatRecordsCountOnceOnHomeAndCollectionWithoutRecognition() {
        runBlocking {
            for (id in MockBreedRepository.coatCategoryIds) {
                db.encounters().create(EncounterRecord(id, null, id = "saved-$id"))
            }
            db.encounters().create(EncounterRecord("coat_orange_tabby", null, id = "repeat-orange"))
        }
        mount()
        compose.waitUntil(10000) { state.collection.value.statistics(catalog.getCollectionEntries()).discovered == 9 }
        compose.onNodeWithText(resources.getString(R.string.collection_discovered_count, 7, 28)).assertExists()
        compose.onNodeWithTag("navigation-HOME").performClick()
        compose.onNodeWithText(resources.getString(R.string.collection_count, 9, 63)).assertExists()
        compose.onNodeWithText(resources.getString(R.string.category_count, 7, 28)).assertExists()
        compose.onNodeWithText(resources.getString(R.string.category_count, 1, 34)).assertExists()
        compose.onNodeWithTag("navigation-COLLECTION").performClick()
        compose.onNodeWithText(resources.getString(R.string.collection_discovered_count, 7, 28)).assertExists()
        compose.onNodeWithText(resources.getString(R.string.filter_dogs)).performClick()
        compose.onNodeWithText(resources.getString(R.string.collection_discovered_count, 1, 34)).assertExists()
    }

    private fun assertRoot() {
        compose.onNodeWithText(resources.getString(R.string.filter_cats)).assertIsSelected()
        compose.onNodeWithText(resources.getString(R.string.breed_back)).assertDoesNotExist()
        compose.onNodeWithText(resources.getString(R.string.encounter_edit)).assertDoesNotExist()
    }

    private fun openPhotos(breed: String) {
        val filter = when (breed) {
            "corgi", "other_dog" -> R.string.filter_dogs
            "monkey", "unregistered", "custom" -> R.string.filter_other
            else -> R.string.filter_cats
        }
        compose.onNodeWithText(resources.getString(filter)).performClick()
        if (breed == "custom") compose.onNodeWithTag("album_navigation-album").performClick()
        else if (breed != "unregistered") {
            val name = catalog.getBreed(breed)!!.name
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
            compose.onNode(hasText(name) and hasClickAction()).performClick()
        }
        compose.onNodeWithTag("album-photo-grid").performScrollToNode(hasTestTag("photo_navigation-$breed"))
        compose.onNodeWithTag("photo_navigation-$breed").performClick()
        compose.onNodeWithText(resources.getString(R.string.encounter_edit)).assertExists()
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        compose.waitForIdle()
    }

    private fun edgeBack() {
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val y = screenshot.height / 2
        val end = screenshot.width / 2
        screenshot.recycle()
        shell("input swipe 1 $y $end $y 350")
        // SystemUI finishes its gesture animation outside Compose's idling resources.
        compose.waitUntil(10000) {
            compose.onAllNodesWithText(resources.getString(R.string.encounter_edit)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun bottomCollectionReselectionClearsAllDetailsWithoutChangingHomeEntry() {
        mount()
        for (breed in listOf("british_shorthair", "corgi", "monkey", "other_cat", "other_dog", "unregistered", "custom")) {
            openPhotos(breed)
            compose.onNodeWithTag("navigation-COLLECTION").performClick()
            assertRoot()
            val previous = rootBacks
            shell("input keyevent 4")
            compose.runOnIdle { assertEquals(previous + 1, rootBacks) }
            assertRoot()
        }
        repeat(3) { compose.onNodeWithTag("navigation-COLLECTION").performClick(); assertRoot() }
        compose.onNodeWithTag("navigation-HOME").performClick()
        compose.onNodeWithTag("navigation-COLLECTION").performClick()
        assertRoot()
    }

    @Test fun nativeEdgeGestureAndSystemBackReturnOnlyOneLevel() {
        mount()
        for (breed in listOf("british_shorthair", "corgi", "monkey", "custom", "unregistered")) {
            openPhotos(breed)
            val before = rootBacks
            edgeBack()
            compose.onNodeWithText(resources.getString(R.string.encounter_edit)).assertDoesNotExist()
            compose.onNodeWithTag("album-photo-grid").assertExists()
            compose.runOnIdle { assertEquals(before, rootBacks) }
            if (breed != "unregistered") {
                shell("input keyevent 4")
                compose.onNode(hasScrollAction()).performScrollToIndex(0)
                compose.waitUntil(10000) {
                    compose.onAllNodesWithText(resources.getString(R.string.filter_cats)).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(resources.getString(R.string.breed_back)).assertDoesNotExist()
                compose.onNodeWithText(resources.getString(R.string.filter_cats)).assertExists()
                compose.runOnIdle { assertEquals(before, rootBacks) }
            }
            compose.onNodeWithTag("navigation-COLLECTION").performClick()
            assertRoot()
        }
    }

    @After fun close() {
        val store = ViewModelStore(); store.put("state", state); store.clear()
        db.close()
    }
}
