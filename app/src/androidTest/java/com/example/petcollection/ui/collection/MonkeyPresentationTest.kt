package com.example.petcollection.ui.collection

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.*
import com.example.petcollection.ui.home.HomeScreen
import com.example.petcollection.ui.theme.PetCollectionTheme
import java.util.Locale
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MonkeyPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(base, CollectionDatabase::class.java).build()
    private val state = CollectionUnlockState(EncounterRepository(db.encounters(), base))
    private fun localized(language: String) = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })

    @Test fun homeTotalAndMonkeyPreviewFollowCatalogLanguageAndTheme() {
        var language by mutableStateOf("zh-Hant")
        var dark by mutableStateOf(false)
        compose.setContent {
            val context = remember(language) { localized(language) }
            val catalog = remember(context) { MockBreedRepository(context.resources).getCollectionEntries() }
            val data by state.collection.collectAsState()
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration) {
                PetCollectionTheme(darkTheme = dark) {
                    HomeScreen(collectionStatistics = data.statistics(catalog))
                    CollectionEncounterHost(state, catalog.map { data.applyTo(it) })
                }
            }
        }
        compose.waitUntil(10000) { state.collection.value.loaded }
        for (lang in listOf("zh-Hant", "en")) {
            for (night in listOf(false, true)) {
                compose.runOnIdle { language = lang; dark = night }
                val resources = localized(lang).resources
                val catalog = MockBreedRepository(resources).getCollectionEntries()
                val total = catalog.count { it.isCollectible }
                compose.onNodeWithText(resources.getString(R.string.collection_count, 0, total)).assertExists()
                compose.runOnIdle {
                    assertTrue(state.onClassificationResult(MockBreedRepository.MONKEY_ID, catalog, "file:///preview-only"))
                }
                compose.onNodeWithText(resources.getString(R.string.monkey_first)).assertExists()
                compose.onNodeWithText(resources.getString(R.string.monkey_name)).assertExists()
                compose.onNodeWithText(resources.getString(R.string.monkey_legend)).assertExists()
                compose.onNodeWithText(resources.getString(R.string.recognition_continue)).assertIsDisplayed()
                compose.onNodeWithText(resources.getString(R.string.encounter_cancel)).performClick()
                assertEquals(0, state.collection.value.statistics(catalog).discovered)
            }
        }
    }

    @After fun close() {
        val store = androidx.lifecycle.ViewModelStore()
        store.put("collection", state)
        store.clear()
        db.close()
    }
}