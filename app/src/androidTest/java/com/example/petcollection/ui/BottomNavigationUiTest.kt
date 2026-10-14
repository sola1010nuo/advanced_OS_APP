package com.example.petcollection.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.MainActivity
import com.example.petcollection.data.*
import com.example.petcollection.ui.collection.CollectionUnlockState
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BottomNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val db = Room.inMemoryDatabaseBuilder(base, CollectionDatabase::class.java).build()
    private val state = CollectionUnlockState(EncounterRepository(db.encounters(), base))
    private var backDispatcher: OnBackPressedDispatcher? = null
    private val destinations = listOf("HOME", "COLLECTION", "CAMERA", "FOOTPRINTS", "ACHIEVEMENTS")
    private fun resources(language: String) = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
        setLocale(Locale.forLanguageTag(language))
    }).resources
    private fun mount() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
                SideEffect { backDispatcher = owner.onBackPressedDispatcher }
                PetCollectionApp(state)
            }
        }
        compose.waitUntil(10000) { state.collection.value.loaded }
    }
    private fun shell(command: String) {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(100, 3000)
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(base.getExternalFilesDir(null), name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shell("cp ${file.absolutePath} /data/local/tmp/$name")
    }

    @Test fun fiveEquallySpacedItemsCameraMessageAndAchievementNavigation() {
        mount()
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.recent_discoveries)).assertDoesNotExist()
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.no_discoveries)).assertDoesNotExist()
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.achievements)).assertDoesNotExist()
        compose.onNodeWithTag("navigation-SETTINGS").assertDoesNotExist()
        val bounds = destinations.map { compose.onNodeWithTag("navigation-$it").fetchSemanticsNode().boundsInRoot }
        val steps = bounds.zipWithNext { a, b -> b.center.x - a.center.x }
        assertTrue(steps.all { it > 0 })
        steps.forEach { assertEquals(steps.first(), it, 2f) }
        assertTrue(bounds[2].top < bounds[0].top)
        assertEquals(bounds[2].width, bounds[2].height, 1f)
        capture("navigation-home-zh-light.png")
        compose.onNodeWithTag("navigation-CAMERA").performClick()
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.camera_coming_soon)).assertExists()
        compose.onNodeWithTag("navigation-HOME").assertIsSelected()
        compose.onNodeWithTag("navigation-ACHIEVEMENTS").performClick()
        compose.onNodeWithTag("achievements-page").assertExists()
        compose.onNodeWithTag("navigation-ACHIEVEMENTS").assertIsSelected()
        capture("navigation-achievements-zh.png")
        compose.onNodeWithTag("navigation-FOOTPRINTS").performClick()
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.footprints_future)).assertExists()
        compose.onNodeWithTag("navigation-COLLECTION").performClick()
        compose.onNodeWithTag("navigation-CAMERA").performClick()
        compose.onNodeWithTag("navigation-COLLECTION").assertIsSelected()
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.filter_cats)).assertIsSelected()
        assertTrue(state.encounters.isEmpty())
    }

    @Test fun collectionBackStackAndReselectionKeepExistingBehavior() {
        runBlocking { db.encounters().create(EncounterRecord("corgi", null, id = "nav-corgi")) }
        mount()
        compose.onNodeWithTag("navigation-COLLECTION").performClick()
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.filter_dogs)).performClick()
        val name = requireNotNull(MockBreedRepository(resources("zh-Hant")).getBreed("corgi")).name
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
        compose.onNode(hasText(name) and hasClickAction()).performClick()
        compose.onNodeWithTag("album-photo-grid").performScrollToNode(hasTestTag("photo_nav-corgi"))
        compose.onNodeWithTag("photo_nav-corgi").performClick()
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.encounter_edit)).assertExists()
        compose.runOnIdle { requireNotNull(backDispatcher).onBackPressed() }
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.encounter_edit)).assertDoesNotExist()
        compose.onNodeWithTag("album-photo-grid").assertExists()
        compose.runOnIdle { requireNotNull(backDispatcher).onBackPressed() }
        compose.onNodeWithTag("album-photo-grid").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.filter_dogs)).assertIsSelected()
        compose.onNodeWithTag("navigation-COLLECTION").performClick()
        compose.onNodeWithText(resources("zh-Hant").getString(R.string.filter_cats)).assertIsSelected()
        assertEquals(1, state.collection.value.forBreed("corgi").size)
    }

    @Test fun homeSettingsRetainLanguageThemeAndReturnThroughButtonAndBackDispatcher() {
        mount()
        for (language in listOf("en", "zh-Hant")) for (dark in listOf(false, true)) {
            compose.onNodeWithTag("home-settings").performClick()
            val label = if (language == "en") R.string.language_en else R.string.language_zh
            compose.onNodeWithText(resources(language).getString(label)).performScrollTo().performClick()
            val mode = if (dark) R.string.dark_mode else R.string.light_mode
            compose.onNodeWithText(resources(language).getString(mode)).performScrollTo().performClick()
            compose.onNodeWithText(resources(language).getString(R.string.effects)).assertExists()
            if (language == "en" && !dark) {
                compose.onNodeWithTag("settings-back-home").performScrollTo().performClick()
            } else {
                compose.runOnIdle { requireNotNull(backDispatcher).onBackPressed() }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("home-settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("navigation-HOME").assertIsSelected()
            capture("navigation-home-$language-${if (dark) "dark" else "light"}.png")
            compose.onNodeWithTag("navigation-CAMERA").performClick()
            compose.onNodeWithText(resources(language).getString(R.string.camera_coming_soon)).assertExists()
        }
    }

    @After fun close() {
        val store = ViewModelStore(); store.put("navigation", state); store.clear()
        db.close()
    }
}
