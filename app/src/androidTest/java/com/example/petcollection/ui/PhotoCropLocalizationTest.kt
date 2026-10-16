package com.example.petcollection.ui

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.PhotoCropTestFixtures
import com.example.petcollection.R
import com.example.petcollection.data.CollectionDatabase
import com.example.petcollection.data.EncounterRepository
import com.example.petcollection.data.PhotoCropIO
import com.example.petcollection.data.CropMode
import com.example.petcollection.data.CropSource
import com.example.petcollection.data.PhotoMetadata
import android.graphics.Bitmap
import android.graphics.Color
import com.example.petcollection.ui.collection.CollectionUnlockState
import com.example.petcollection.ui.home.PhotoCropHost
import com.example.petcollection.ui.home.PhotoCropScreen
import com.example.petcollection.ui.theme.PetCollectionTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhotoCropLocalizationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun resources(language: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
    ).resources

    private fun assertCropText(language: String, error: Boolean = false) {
        val strings = resources(language)
        for (id in listOf(R.string.crop_title, R.string.crop_cancel, R.string.crop_reset, R.string.crop_confirm)) {
            compose.onNodeWithText(strings.getString(id)).assertIsDisplayed()
        }
        for (id in listOf(R.string.crop_drag_hint, R.string.crop_pinch_hint, R.string.crop_hint, R.string.crop_zoom)) {
            compose.onNodeWithText(strings.getString(id)).assertDoesNotExist()
        }
        val other = resources(if (language == "en") "zh-Hant" else "en")
        compose.onNodeWithText(other.getString(R.string.crop_title)).assertDoesNotExist()
        if (error) {
            compose.onNodeWithText(strings.getString(R.string.crop_error)).assertIsDisplayed()
            compose.onNodeWithText(strings.getString(R.string.crop_retry)).assertIsDisplayed()
            compose.onNodeWithTag("photo-crop-confirm").assertIsNotEnabled()
        } else {
            compose.onNodeWithContentDescription(strings.getString(R.string.crop_zoom)).assertExists()
            compose.onNodeWithTag("photo-crop-confirm").assertIsEnabled()
        }
    }

    @Test fun freeAndCoverKeepMinimalBilingualUiAndVisibleHandlesOnLightAndDarkPhotos() {
        val light = Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val darkPhoto = Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        var language by mutableStateOf("en")
        var night by mutableStateOf(false)
        var darkImage by mutableStateOf(false)
        var mode by mutableStateOf(CropMode.FREE)
        val lightSource = CropSource(light, PhotoMetadata())
        val darkSource = CropSource(darkPhoto, PhotoMetadata())
        try {
            compose.setContent {
                val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
                CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration), LocalConfiguration provides configuration) {
                    PetCollectionTheme(darkTheme = night) { Surface {
                        PhotoCropScreen(if (darkImage) darkSource else lightSource, false, false, {}, {}, mode = mode)
                    } }
                }
            }
            for (lang in listOf("en", "zh-Hant")) for (dark in listOf(false, true))
                for (photoDark in listOf(false, true)) for (cropMode in CropMode.entries) {
                    compose.runOnIdle { language = lang; night = dark; darkImage = photoDark; mode = cropMode }
                    assertCropText(lang)
                    val label = compose.onNodeWithText(resources(lang).getString(R.string.crop_cover_ratio))
                    if (cropMode == CropMode.COVER) label.assertIsDisplayed() else label.assertDoesNotExist()
                    val bounds = compose.onNodeWithTag("photo-crop-frame").fetchSemanticsNode().boundsInRoot
                    assertEquals(if (cropMode == CropMode.COVER) 4f / 3f else 2f, bounds.width / bounds.height, .01f)
                    if (cropMode == CropMode.FREE) {
                        val handle = compose.onNodeWithTag("photo-crop-handle-0").captureToImage().asAndroidBitmap()
                        val pixels = IntArray(handle.width * handle.height)
                        handle.getPixels(pixels, 0, handle.width, 0, 0, handle.width, handle.height)
                        assertTrue(pixels.any { it == Color.BLACK })
                        assertTrue(pixels.any { it == Color.WHITE })
                        handle.recycle()
                    }
                }
        } finally { light.recycle(); darkPhoto.recycle() }
    }

    @Test fun loadingAndExportingUseLocalizedAccessibilityTextInBothThemes() {
        val original = PhotoCropTestFixtures.original(context)
        val source = PhotoCropIO.prepare(context, Uri.fromFile(original))
        var language by mutableStateOf("en")
        var dark by mutableStateOf(false)
        var exporting by mutableStateOf(false)
        try {
            compose.setContent {
                val configuration = Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                }
                CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration),
                    LocalConfiguration provides configuration) {
                    PetCollectionTheme(darkTheme = dark) { Surface {
                        PhotoCropScreen(if (exporting) source else null, true, false, {}, {})
                    } }
                }
            }
            for (lang in listOf("en", "zh-Hant")) for (night in listOf(false, true)) for (hasPhoto in listOf(false, true)) {
                compose.runOnIdle { language = lang; dark = night; exporting = hasPhoto }
                val strings = resources(lang)
                compose.onNodeWithText(strings.getString(R.string.crop_title)).assertIsDisplayed()
                compose.onNodeWithContentDescription(strings.getString(R.string.crop_loading)).assertIsDisplayed()
                compose.onNodeWithTag("photo-crop-confirm").assertIsNotEnabled()
                compose.onNodeWithTag("photo-crop-cancel").assertIsEnabled()
                if (hasPhoto) compose.onNodeWithTag("photo-crop-zoom").assertIsNotEnabled()
            }
        } finally { source.bitmap.recycle(); original.delete() }
    }

    @Test fun openDialogUpdatesAllTextAndErrorsInBothLanguagesAndThemesWithoutResettingCrop() {
        val original = PhotoCropTestFixtures.original(context)
        val db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        val state = CollectionUnlockState(EncounterRepository(db.encounters(), context))
        var language by mutableStateOf("en")
        var dark by mutableStateOf(false)
        var surface = 0
        try {
            compose.setContent {
                val configuration = Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                }
                val localizedContext = context.createConfigurationContext(configuration)
                CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration) {
                    PetCollectionTheme(darkTheme = dark) {
                        surface = MaterialTheme.colorScheme.surface.toArgb()
                        PhotoCropHost(state.photoInput)
                    }
                }
            }
            compose.waitUntil(10000) { state.collection.value.loaded }
            compose.runOnIdle { state.photoInput.begin(Uri.fromFile(original)) }
            compose.waitUntil(10000) { state.photoInput.source != null }
            compose.onNodeWithTag("photo-crop-zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
            for (lang in listOf("en", "zh-Hant")) for (night in listOf(false, true)) {
                compose.runOnIdle { language = lang; dark = night }
                assertCropText(lang)
                compose.onNodeWithTag("photo-crop-zoom").assertRangeInfoEquals(ProgressBarRangeInfo(4f, 1f..8f))
                val frame = compose.onNodeWithTag("photo-crop-frame").fetchSemanticsNode().boundsInRoot
                assertEquals(2f, frame.width / frame.height, .01f)
                val screenshot = compose.onNodeWithTag("photo-crop-page").captureToImage().asAndroidBitmap()
                assertEquals(surface, screenshot.getPixel(screenshot.width - 2, 2))
                screenshot.recycle()
            }
            compose.runOnIdle { state.photoInput.begin(Uri.fromFile(File(original.parentFile, "missing-${original.name}"))) }
            compose.waitUntil(10000) { state.photoInput.error && !state.photoInput.opening }
            for (lang in listOf("en", "zh-Hant")) for (night in listOf(false, true)) {
                compose.runOnIdle { language = lang; dark = night }
                assertCropText(lang, error = true)
                compose.onNodeWithText(resources(lang).getString(R.string.crop_retry)).performClick()
                compose.waitUntil(10000) { state.photoInput.error && !state.photoInput.opening }
            }
            compose.onNodeWithTag("photo-crop-cancel").performClick()
            compose.onNodeWithTag("photo-crop-page").assertDoesNotExist()
        } finally {
            compose.runOnIdle { ViewModelStore().apply { put("crop", state); clear() } }
            db.close()
            original.delete()
        }
    }

    @Test fun cropFollowsActualAppLanguageAndAppearanceSettings() {
        val original = PhotoCropTestFixtures.original(context)
        val db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        val state = CollectionUnlockState(EncounterRepository(db.encounters(), context))
        try {
            compose.setContent { PetCollectionApp(state) }
            compose.waitUntil(10000) { state.collection.value.loaded }
            for (language in listOf("en", "zh-Hant")) for (dark in listOf(false, true)) {
                val strings = resources(language)
                compose.onNodeWithTag("home-settings").performClick()
                val languageLabel = if (language == "en") R.string.language_en else R.string.language_zh
                compose.onNodeWithText(strings.getString(languageLabel)).performScrollTo().performClick()
                val mode = if (dark) R.string.dark_mode else R.string.light_mode
                compose.onNodeWithText(strings.getString(mode)).performScrollTo().performClick()
                compose.onNodeWithTag("settings-back-home").performScrollTo().performClick()
                compose.runOnIdle { state.photoInput.begin(Uri.fromFile(original)) }
                compose.waitUntil(10000) { state.photoInput.source != null }
                assertCropText(language)
                compose.onNodeWithTag("photo-crop-cancel").performClick()
                assertNull(state.pendingEncounter)
                assertTrue(state.collection.value.encounters.isEmpty())
            }
        } finally {
            compose.runOnIdle { ViewModelStore().apply { put("crop", state); clear() } }
            db.close()
            original.delete()
        }
    }
}
