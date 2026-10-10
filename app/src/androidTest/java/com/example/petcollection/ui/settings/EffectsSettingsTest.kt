package com.example.petcollection.ui.settings

import android.content.res.Configuration
import androidx.compose.material3.Card
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.R
import com.example.petcollection.data.EffectsSettingsRepository
import com.example.petcollection.ui.theme.PetCollectionTheme
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EffectsSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun defaultIsOnAndBothValuesSurviveNewDataStoreInstances() = runBlocking {
        val file = File(base.cacheDir, "effects-test-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun repository() = EffectsSettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        try {
            var store = repository()
            assertTrue(store.enabled.first())
            for (enabled in listOf(false, true)) {
                store.setEnabled(enabled)
                scope.coroutineContext[Job]!!.cancelAndJoin()
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                store = repository()
                assertEquals(enabled, store.enabled.first())
            }
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun switchWorksInBothLanguagesAndBothThemes() {
        var language by mutableStateOf("zh-Hant")
        var dark by mutableStateOf(false)
        var state by mutableStateOf(EffectsSettingsUiState(loaded = true))
        compose.setContent {
            val context = remember(language) { base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }) }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration) {
                PetCollectionTheme(darkTheme = dark) {
                    Card { EffectsSettingRow(state) { state = state.copy(enabled = it) } }
                }
            }
        }
        for (lang in listOf("zh-Hant", "en")) {
            for (night in listOf(false, true)) {
                compose.runOnIdle { language = lang; dark = night; state = state.copy(enabled = true) }
                val label = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(lang))
                }).getString(R.string.effects_enabled)
                compose.onNodeWithText(label).assertExists()
                compose.onNodeWithTag("effects-switch").assertIsOn().performClick().assertIsOff()
                compose.onNodeWithTag("effects-switch").performClick().assertIsOn()
            }
        }
    }
}
