package com.example.petcollection.ui.effects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.petcollection.data.Breed

enum class RecognitionEffectKind { BANANA_RAIN }

/** Receives a final catalog entry, never a raw model label or an unconfirmed prediction. */
object RecognitionEffectRegistry {
    fun effectFor(breed: Breed): RecognitionEffectKind? = when {
        breed.isSecretMonkey -> RecognitionEffectKind.BANANA_RAIN
        else -> null // Add species/category mappings here when another effect is implemented.
    }
}

data class RecognitionEffectEvent(val resultId: String, val kind: RecognitionEffectKind)

/** Held by the encounter ViewModel: remounting a Dialog or rotating cannot replay a result. */
class RecognitionEffectsState {
    var event by mutableStateOf<RecognitionEffectEvent?>(null)
        private set
    private var consumed = false

    fun onClassificationCompleted(resultId: String, breed: Breed) {
        if (event?.resultId == resultId) return
        event = RecognitionEffectRegistry.effectFor(breed)?.let { RecognitionEffectEvent(resultId, it) }
        consumed = false
    }

    fun claim(resultId: String, enabled: Boolean): RecognitionEffectKind? {
        val current = event ?: return null
        if (current.resultId != resultId || consumed) return null
        consumed = true // Disabled results are consumed too; enabling later does not replay them.
        return current.kind.takeIf { enabled }
    }

    fun finish(resultId: String) {
        if (event?.resultId == resultId) consumed = true
    }

    fun clear() {
        event = null
        consumed = false
    }
}
