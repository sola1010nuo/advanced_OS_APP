package com.example.petcollection.ui.collection

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.*
import com.example.petcollection.ui.theme.PetCollectionTheme
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BreedRarityUiTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(base, CollectionDatabase::class.java).build()
    private val state = CollectionUnlockState(EncounterRepository(db.encounters(), base))
    private fun context(language: String) = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
        setLocale(Locale.forLanguageTag(language))
    })
    private fun capture(name: String, dialog: Boolean = false) {
        val node = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val file = File(base.getExternalFilesDir(null), name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Test package cleanup removes its own storage; retain QA captures in shell-owned temp storage.
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${file.absolutePath} /data/local/tmp/$name")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    @Test fun rarityAppearsOnCardsDetailsAndPolaroidsInBothLanguagesAndThemes() {
        var language by mutableStateOf("en")
        var dark by mutableStateOf(false)
        compose.setContent {
            val owner = requireNotNull(LocalActivityResultRegistryOwner.current)
            val local = remember(language) { context(language) }
            val catalog = remember(local) { MockBreedRepository(local.resources) }
            CompositionLocalProvider(LocalContext provides local, LocalConfiguration provides local.resources.configuration,
                LocalActivityResultRegistryOwner provides owner) {
                PetCollectionTheme(darkTheme = dark) { CollectionScreen(repository = catalog, unlockState = state, initialSpecies = Species.DOG) }
            }
        }
        compose.waitUntil(10000) { state.collection.value.loaded }
        for (lang in listOf("en", "zh-Hant")) for (night in listOf(false, true)) {
            compose.runOnIdle { language = lang; dark = night }
            val local = context(lang)
            val catalog = MockBreedRepository(local.resources).getCollectionEntries()
            val boxer = catalog.first { it.id == "boxer" }
            val description = local.getString(R.string.rarity_description, 4, local.getString(R.string.rarity_epic))
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(boxer.name))
            compose.onNode(hasText(boxer.name) and hasClickAction()).assertExists()
            compose.onNode(hasText(boxer.name) and hasClickAction()).assert(hasContentDescription(description))
            if (lang == "en" && !night) capture("dog-rarity-cards-en.png")
            compose.onNode(hasText(boxer.name) and hasClickAction()).performClick()
            compose.onNodeWithContentDescription(description).assertExists()
            if (lang == "en" && !night) capture("dog-rarity-detail-en.png")
            compose.runOnIdle {
                assertTrue(state.onClassificationResult(boxer.id, catalog, "file:///preview-only"))
                assertTrue(state.isFirstDiscovery)
                assertFalse(state.applyTo(boxer).isDiscovered)
            }
            compose.onNode(hasContentDescription(description) and hasAnyAncestor(isDialog())).assertExists()
            capture("dog-rarity-polaroid-$lang-${if (night) "dark" else "light"}.png", dialog = true)
            compose.onNodeWithText(local.getString(R.string.encounter_cancel)).performClick()
            compose.onNodeWithText(local.getString(R.string.breed_back)).performClick()
        }
    }

    @Test fun retiredBreedAlbumRemainsAccessibleWithoutCatalogProgress() {
        runBlocking { db.encounters().create(EncounterRecord("english_setter", null, id = "legacy-record")) }
        val local = context("en")
        val catalog = MockBreedRepository(local.resources)
        compose.setContent {
            val owner = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(LocalContext provides local, LocalConfiguration provides local.resources.configuration,
                LocalActivityResultRegistryOwner provides owner) {
                PetCollectionTheme { CollectionScreen(repository = catalog, unlockState = state, initialSpecies = Species.DOG) }
            }
        }
        compose.waitUntil(10000) { state.collection.value.encounters.size == 1 }
        assertEquals(CollectionStatistics(0, 36, 1), state.collection.value.statistics(catalog.getCollectionEntries(), Species.DOG))
        val name = requireNotNull(catalog.getBreed("english_setter")).name
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
        compose.onNode(hasText(name) and hasClickAction()).performClick()
        compose.onNodeWithText(local.getString(R.string.legacy_breed_note)).assertExists()
        compose.onNodeWithTag("album-photo-grid").performScrollToNode(hasTestTag("photo_legacy-record"))
        compose.onNodeWithTag("photo_legacy-record").performClick()
        compose.onNodeWithText(local.getString(R.string.encounter_edit)).assertExists()
    }

    @After fun close() {
        val store = ViewModelStore()
        store.put("collection", state)
        store.clear()
        db.close()
    }
}
