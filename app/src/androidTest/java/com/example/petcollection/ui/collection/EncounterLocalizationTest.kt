package com.example.petcollection.ui.collection

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.Breed
import com.example.petcollection.data.BreedRepository
import com.example.petcollection.data.MockBreedRepository
import com.example.petcollection.ui.PetCollectionApp
import com.example.petcollection.ui.theme.PetCollectionTheme
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class EncounterLocalizationTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private fun context(language: String) = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
    )
    private val zh get() = context("zh-Hant").resources
    private val en get() = context("en").resources

    @Test fun languageSwitchUpdatesDialogsAndValidation() {
        var language by mutableStateOf("zh-Hant")
        val unlockState = CollectionUnlockState()
        val breed = MockBreedRepository(zh).getBreed("abyssinian")!!
        compose.setContent {
            val localized = remember(language) { context(language) }
            CompositionLocalProvider(LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration) {
                val repository = remember(localized) {
                    val breed = MockBreedRepository(localized.resources).getBreed("abyssinian")!!
                    object : BreedRepository { override fun getBreeds(): List<Breed> = listOf(breed) }
                }
                PetCollectionTheme { CollectionScreen(repository = repository, unlockState = unlockState) }
            }
        }
        compose.onNodeWithText(zh.getString(R.string.breed_abyssinian_name)).performClick()
        compose.runOnIdle { unlockState.onBreedRecognized(breed, photoUri = null) }
        for (id in listOf(R.string.unlock_title, R.string.unlock_added, R.string.unlock_record,
            R.string.unlock_later, R.string.breed_photo_placeholder)) {
            compose.onNodeWithText(zh.getString(id)).assertExists()
        }
        compose.runOnIdle { language = "en" }
        for (id in listOf(R.string.unlock_title, R.string.unlock_added, R.string.unlock_record,
            R.string.unlock_later, R.string.breed_photo_placeholder)) {
            compose.onNodeWithText(en.getString(id)).assertExists()
            compose.onNodeWithText(zh.getString(id)).assertDoesNotExist()
        }
        compose.onNodeWithText(en.getString(R.string.unlock_record)).performClick()
        compose.onNodeWithText(en.getString(R.string.encounter_title)).assertExists()
        compose.onNodeWithText(en.getString(R.string.encounter_cancel)).assertExists()
        for (id in listOf(R.string.encounter_name, R.string.encounter_date, R.string.encounter_time,
            R.string.encounter_location, R.string.encounter_note, R.string.encounter_date_hint,
            R.string.encounter_time_hint)) {
            compose.onNodeWithText(en.getString(id)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText(en.getString(R.string.encounter_name)).performScrollTo().performTextReplacement("Snow")
        compose.onNodeWithText(en.getString(R.string.encounter_date)).performScrollTo().performTextReplacement("invalid")
        compose.onNodeWithText(en.getString(R.string.encounter_date_error)).assertExists()
        compose.onNodeWithText(en.getString(R.string.encounter_time)).performScrollTo().performTextReplacement("99:99")
        compose.onNodeWithText(en.getString(R.string.encounter_time_error)).assertExists()
        compose.onNodeWithText(en.getString(R.string.encounter_save)).assertIsNotEnabled()
        compose.runOnIdle { language = "zh-Hant" }
        for (id in listOf(R.string.encounter_title, R.string.encounter_cancel, R.string.encounter_save,
            R.string.encounter_date_error, R.string.encounter_time_error)) {
            compose.onNodeWithText(zh.getString(id)).assertExists()
            compose.onNodeWithText(en.getString(id)).assertDoesNotExist()
        }
        for (id in listOf(R.string.encounter_name, R.string.encounter_date, R.string.encounter_time,
            R.string.encounter_location, R.string.encounter_note)) {
            compose.onNodeWithText(zh.getString(id)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Snow").assertExists()
        compose.onNodeWithText(zh.getString(R.string.encounter_date)).performScrollTo().performTextReplacement("2026-10-07")
        compose.onNodeWithText(zh.getString(R.string.encounter_time)).performScrollTo().performTextReplacement("15:30")
        compose.onNodeWithText(zh.getString(R.string.encounter_save)).performClick()
        compose.onNodeWithText(zh.getString(R.string.breed_back)).performScrollTo().performClick()
        compose.onNodeWithText(zh.getString(R.string.collection_discovered_count, 1, 1)).assertExists()
        compose.onNodeWithText(zh.getString(R.string.breed_abyssinian_name)).performClick()
        compose.runOnIdle { language = "en" }
        compose.runOnIdle { unlockState.onBreedRecognized(breed, photoUri = null) }
        compose.onNodeWithText(en.getString(R.string.encounter_title)).assertExists()
        compose.onNodeWithText(en.getString(R.string.unlock_title)).assertDoesNotExist()
    }

    @Test fun actualAppLanguageSettingChangesBreedStatusLabel() {
        compose.setContent { PetCollectionApp() }
        compose.onNodeWithText(zh.getString(R.string.settings)).performClick()
        compose.onNodeWithText(en.getString(R.string.language_en)).performClick()
        compose.onNodeWithText(en.getString(R.string.collection)).performClick()
        compose.onNodeWithText(en.getString(R.string.breed_abyssinian_name)).performClick()
        compose.onNodeWithText(en.getString(R.string.breed_undiscovered)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(en.getString(R.string.settings)).performClick()
        compose.onNodeWithText(zh.getString(R.string.language_zh)).performClick()
        compose.onNodeWithText(zh.getString(R.string.collection)).performClick()
        compose.onNodeWithText(zh.getString(R.string.breed_abyssinian_name)).performClick()
        compose.onNodeWithText(zh.getString(R.string.breed_undiscovered)).performScrollTo().assertIsDisplayed()
    }
}
