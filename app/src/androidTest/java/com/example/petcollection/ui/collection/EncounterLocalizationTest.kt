package com.example.petcollection.ui.collection

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.*
import com.example.petcollection.ui.PetCollectionApp
import com.example.petcollection.ui.theme.PetCollectionTheme
import java.util.Locale
import org.junit.After
import org.junit.Rule
import org.junit.Test

class EncounterLocalizationTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = Room.inMemoryDatabaseBuilder(base, CollectionDatabase::class.java).build()
    private val state = CollectionUnlockState(EncounterRepository(database.encounters(), base))
    private fun context(language: String) = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
    )
    private val zh get() = context("zh-Hant").resources
    private val en get() = context("en").resources
    @After fun closeDatabase() {
        val store = androidx.lifecycle.ViewModelStore()
        store.put("collection", state)
        store.clear()
        database.close()
    }

    @Test fun languageSwitchUpdatesRecognitionThenEditorWithoutSavedUnlockDialog() {
        var language by mutableStateOf("zh-Hant")
        val breed = MockBreedRepository(zh).getBreed("abyssinian")!!
        compose.setContent {
            val activityResultOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            val localized = remember(language) { context(language) }
            CompositionLocalProvider(LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalActivityResultRegistryOwner provides activityResultOwner) {
                val repository = remember(localized) { MockBreedRepository(localized.resources) }
                PetCollectionTheme { CollectionScreen(repository = repository, unlockState = state) }
            }
        }
        compose.waitUntil(10000) { state.collection.value.loaded }
        compose.onNodeWithText(breed.name).performClick()
        compose.runOnIdle { state.onBreedRecognized(breed, null) }
        compose.onNodeWithText(zh.getString(R.string.recognition_first)).assertExists()
        compose.onNodeWithText(zh.getString(R.string.encounter_title)).assertDoesNotExist()
        compose.runOnIdle { language = "en" }
        compose.onNodeWithText(en.getString(R.string.recognition_first)).assertExists()
        compose.onNode(hasText(en.getString(R.string.breed_abyssinian_name)) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithText(en.getString(R.string.recognition_continue)).performClick()
        compose.onNodeWithText(en.getString(R.string.encounter_title)).assertExists()
        compose.onNodeWithText(en.getString(R.string.encounter_name)).performScrollTo().performTextReplacement("Snow")
        compose.onNodeWithText(en.getString(R.string.encounter_date)).performScrollTo().performTextReplacement("invalid")
        compose.onNodeWithText(en.getString(R.string.encounter_date_error)).assertExists()
        compose.onNodeWithText(en.getString(R.string.encounter_time)).performScrollTo().performTextReplacement("99:99")
        compose.onNodeWithText(en.getString(R.string.encounter_time_error)).assertExists()
        compose.onNodeWithText(en.getString(R.string.encounter_save)).assertIsNotEnabled()
        compose.runOnIdle { language = "zh-Hant" }
        compose.onNodeWithText(zh.getString(R.string.encounter_date_error)).assertExists()
        compose.onNodeWithText(zh.getString(R.string.encounter_time_error)).assertExists()
        compose.onNodeWithText("Snow").assertExists()
        compose.onNodeWithText(zh.getString(R.string.encounter_date)).performScrollTo().performTextReplacement("2026-10-07")
        compose.onNodeWithText(zh.getString(R.string.encounter_time)).performScrollTo().performTextReplacement("15:30")
        compose.onNodeWithText(zh.getString(R.string.encounter_save)).performClick()
        compose.waitUntil(10000) { !state.isWorking && state.encounters.size == 1 }
        compose.onNodeWithText(zh.getString(R.string.recognition_result)).assertDoesNotExist()
        compose.runOnIdle { language = "en" }
        compose.runOnIdle { state.onBreedRecognized(breed, null) }
        compose.onNodeWithText(en.getString(R.string.recognition_again)).assertExists()
        compose.onNodeWithText(en.getString(R.string.encounter_title)).assertDoesNotExist()
        compose.onNodeWithText(en.getString(R.string.recognition_continue)).performClick()
        compose.onNodeWithText(en.getString(R.string.encounter_title)).assertExists()
    }

    @Test fun actualAppLanguageSettingChangesBreedStatusLabel() {
        compose.setContent { PetCollectionApp() }
        compose.onNodeWithText(zh.getString(R.string.settings)).performClick()
        compose.onNodeWithText(en.getString(R.string.language_en)).performClick()
        compose.onNodeWithText(en.getString(R.string.collection)).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(en.getString(R.string.breed_abyssinian_name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(en.getString(R.string.breed_abyssinian_name)).performClick()
        compose.onNodeWithText(en.getString(R.string.breed_locked_hint)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(en.getString(R.string.settings)).performClick()
        compose.onNodeWithText(zh.getString(R.string.language_zh)).performClick()
        compose.onNodeWithText(zh.getString(R.string.collection)).performClick()
        compose.onNodeWithText(zh.getString(R.string.breed_abyssinian_name)).performClick()
        compose.onNodeWithText(zh.getString(R.string.breed_locked_hint)).performScrollTo().assertIsDisplayed()
    }
}
