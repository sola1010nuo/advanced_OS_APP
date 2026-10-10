package com.example.petcollection.ui.effects

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.*
import com.example.petcollection.ui.collection.CollectionEncounterHost
import com.example.petcollection.ui.collection.CollectionUnlockState
import com.example.petcollection.ui.settings.LocalEffectsEnabled
import com.example.petcollection.ui.theme.PetCollectionTheme
import java.util.Locale
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Injects the final classification callback; does not replace or claim to test AI accuracy. */
class RecognitionEffectsUiTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(base, CollectionDatabase::class.java).build()
    private val state = CollectionUnlockState(EncounterRepository(db.encounters(), base))
    private var enabled by mutableStateOf<Boolean?>(true)
    private var language by mutableStateOf("zh-Hant")
    private var dark by mutableStateOf(false)
    private val catalog get() = MockBreedRepository(base.resources).getCollectionEntries()
    private val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun mount() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val context = remember(language) {
                base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                })
            }
            val entries = remember(context) { MockBreedRepository(context.resources).getCollectionEntries() }
            CompositionLocalProvider(LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalEffectsEnabled provides enabled, LocalLifecycleOwner provides lifecycleOwner) {
                PetCollectionTheme(darkTheme = dark) { CollectionEncounterHost(state, entries) }
            }
        }
        compose.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(10000) { state.collection.value.loaded }
    }

    private fun recognize(id: String = MockBreedRepository.MONKEY_ID) {
        compose.runOnIdle { assertTrue(state.onClassificationResult(id, catalog, "file:///effect-preview-only")) }
        compose.mainClock.advanceTimeBy(400)
        // Dialog composition can attach after the activity's frame; allow its event
        // coroutine to claim the result, then render that state on a separate frame.
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun monkeyPlaysOnceSurvivesRecompositionAndAnotherPhotoPlaysAgain() {
        mount()
        recognize()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertExists()
        val firstId = state.pendingEncounter!!.id
        val anchor = compose.onNodeWithTag("recognition-effect-anchor", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val rain = compose.onNodeWithTag("banana-rain", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val display = base.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
        assertTrue(rain.width > anchor.width)
        assertTrue(rain.height > anchor.height)
        assertEquals(display.width().toFloat(), rain.width, 1f)
        assertEquals(display.height().toFloat(), rain.height, 1f)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).captureToImage()
        // Allow SurfaceFlinger to present the captured frame before device capture.
        android.os.SystemClock.sleep(100)
        // Full device screenshot includes the transparent overlay and the dialog
        // beneath it, so its visual placement can also be inspected after the test.
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(base.externalCacheDir, "banana-rain-preview.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        // Both locale/theme changes recompose the active Polaroid without creating another event.
        compose.runOnIdle { language = "en"; dark = true }
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { language = "zh-Hant"; dark = false }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { state.dismissEncounter() }
        recognize()
        assertNotEquals(firstId, state.pendingEncounter!!.id)
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertExists()
    }

    @Test fun disabledOtherAndManualEncountersNeverPlay() {
        mount()
        compose.runOnIdle { enabled = false }
        recognize()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        for (id in listOf("abyssinian", "corgi", "other_cat", "other_dog", "unregistered")) {
            compose.runOnIdle { state.dismissEncounter() }
            recognize(id)
            compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        }
        compose.runOnIdle {
            state.dismissEncounter()
            assertTrue(state.onBreedRecognized(catalog.first { it.isSecretMonkey }, "file:///manual"))
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun switchingOffOrBackgroundingStopsWithoutReplayingOnReturn() {
        mount()
        recognize()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertExists()
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { state.dismissEncounter() }
        recognize()
        compose.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun newRecognitionCancelsAndRainDoesNotBlockContinue() {
        mount()
        recognize()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertExists()
        // This is the same clear called at the beginning of PhotoRecognitionState.recognize.
        compose.runOnIdle { state.recognitionEffects.clear() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { state.dismissEncounter() }
        recognize()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertExists()
        val label = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("zh-Hant"))
        }).getString(R.string.recognition_continue)
        compose.onNodeWithText(label).assertIsDisplayed()
        // Inject through Android's input dispatcher, rather than directly into a
        // Compose root, to verify that the overlay window really passes touches.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val coordinates = compose.onNodeWithText(label).fetchSemanticsNode().layoutInfo.coordinates
        val center = compose.runOnIdle {
            coordinates.localToScreen(androidx.compose.ui.geometry.Offset(
                coordinates.size.width / 2f, coordinates.size.height / 2f))
        }
        val downTime = android.os.SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(),
                action, center.x, center.y, 0).apply {
                source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            }
            assertTrue(automation.injectInputEvent(event, true))
            event.recycle()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertFalse(state.showRecognitionResult); assertNotNull(state.pendingEncounter) }
    }

    @Test fun savedSettingsMustLoadBeforeAnEffectCanStart() {
        mount()
        compose.runOnIdle { enabled = null }
        recognize()
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("banana-rain", useUnmergedTree = true).assertDoesNotExist()
    }

    @After fun close() {
        val store = ViewModelStore()
        store.put("collection", state)
        store.clear()
        db.close()
    }
}
