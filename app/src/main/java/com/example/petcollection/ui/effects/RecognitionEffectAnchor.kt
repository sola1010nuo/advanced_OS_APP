package com.example.petcollection.ui.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.petcollection.ui.settings.LocalEffectsEnabled

// Dialog windows may provide their own owner. Follow the result screen's lifecycle.
internal val LocalRecognitionEffectLifecycleOwner = staticCompositionLocalOf<LifecycleOwner?> { null }

/** Preserves the photo layout; plays the selected effect in a screen-sized overlay. */
@Composable
internal fun RecognitionEffectAnchor(
    state: RecognitionEffectsState?, resultId: String,
    modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit
) {
    val event = state?.event?.takeIf { it.resultId == resultId }
    val enabled = LocalEffectsEnabled.current
    val owner = LocalRecognitionEffectLifecycleOwner.current ?: LocalLifecycleOwner.current
    var foreground by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var playing by remember(resultId) { mutableStateOf<RecognitionEffectKind?>(null) }
    DisposableEffect(owner, state, resultId) {
        val observer = LifecycleEventObserver { _, lifecycleEvent ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            // addObserver replays ON_CREATE before ON_START, even for a resumed owner.
            // Only leaving the foreground consumes an event; initial attachment does not.
            if (lifecycleEvent == Lifecycle.Event.ON_STOP || lifecycleEvent == Lifecycle.Event.ON_DESTROY) {
                state?.finish(resultId)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            state?.finish(resultId)
        }
    }
    LaunchedEffect(state, event, enabled, foreground) {
        if (event == null || !foreground || enabled == false) {
            playing = null
            if (enabled == false) state?.claim(resultId, enabled = false)
        } else if (enabled == true && playing == null) {
            playing = state?.claim(resultId, enabled = true)
        }
    }
    Box(modifier.testTag("recognition-effect-anchor")) {
        content()
        if (event != null && enabled == true && foreground) {
            when (playing) {
                RecognitionEffectKind.BANANA_RAIN -> FullScreenRecognitionEffect(resultId) {
                    playing = null
                    state?.finish(resultId)
                }
                null -> Unit
            }
        }
    }
}
