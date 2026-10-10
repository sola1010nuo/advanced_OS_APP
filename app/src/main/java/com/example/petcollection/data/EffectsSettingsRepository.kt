package com.example.petcollection.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One application-scoped DataStore instance, including across localized Contexts.
private val Context.effectsDataStore by preferencesDataStore(name = "effects_settings")

class EffectsSettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.effectsDataStore)

    val enabled: Flow<Boolean> = store.data.map { it[EFFECTS_ENABLED] ?: true }

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[EFFECTS_ENABLED] = enabled }
    }

    private companion object {
        val EFFECTS_ENABLED = booleanPreferencesKey("effects_enabled")
    }
}
