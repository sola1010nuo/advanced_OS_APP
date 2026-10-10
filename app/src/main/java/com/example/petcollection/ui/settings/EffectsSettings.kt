package com.example.petcollection.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.petcollection.R
import com.example.petcollection.data.EffectsSettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EffectsSettingsUiState(
    val enabled: Boolean = true,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: Boolean = false
)

/** Null means disk settings are still loading; no effect may start yet. */
val LocalEffectsEnabled = staticCompositionLocalOf<Boolean?> { null }

class EffectsSettingsState(private val repository: EffectsSettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(EffectsSettingsUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.enabled.collect { enabled ->
                    mutableState.update { it.copy(enabled = enabled, loaded = true, error = false) }
                }
            } catch (_: IOException) {
                mutableState.update { it.copy(enabled = false, loaded = true, error = true) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val previous = mutableState.value
        if (!previous.loaded || previous.saving) return
        // Cancel an active effect immediately, before the asynchronous disk write finishes.
        mutableState.value = previous.copy(enabled = enabled, saving = true, error = false)
        viewModelScope.launch {
            try {
                repository.setEnabled(enabled)
                mutableState.update { it.copy(enabled = enabled, error = false) }
            } catch (_: IOException) {
                mutableState.update { it.copy(enabled = previous.enabled, error = true) }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }
}

@Composable
fun rememberEffectsSettingsState(): EffectsSettingsState {
    val context = LocalContext.current.applicationContext
    val factory = remember(context) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EffectsSettingsState(EffectsSettingsRepository(context)) as T
        }
    }
    return viewModel(factory = factory)
}

@Composable
internal fun EffectsSettingRow(state: EffectsSettingsUiState, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("effects-switch")
            .toggleable(state.enabled, enabled = state.loaded && !state.saving,
                role = Role.Switch, onValueChange = onToggle)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.effects_enabled), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.effects_description), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = state.enabled, onCheckedChange = null, enabled = state.loaded && !state.saving)
    }
    if (state.error) Text(stringResource(R.string.effects_settings_error),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
