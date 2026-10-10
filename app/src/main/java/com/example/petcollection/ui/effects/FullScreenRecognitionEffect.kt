package com.example.petcollection.ui.effects

import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** App-owned subwindow above the result Dialog, with no touch or focus interception. */
@Composable
internal fun FullScreenRecognitionEffect(resultId: String, onFinished: () -> Unit) {
    val host = LocalView.current
    val parentComposition = rememberCompositionContext()
    val finished by rememberUpdatedState(onFinished)
    DisposableEffect(host, resultId, parentComposition) {
        val manager = host.context.getSystemService(WindowManager::class.java)
        val overlay = ComposeView(host.context).apply {
            setViewTreeLifecycleOwner(host.findViewTreeLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(host.findViewTreeSavedStateRegistryOwner())
            setParentCompositionContext(parentComposition)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setContent { BananaRain(resultId, Modifier.fillMaxSize()) { finished() } }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            token = host.windowToken
            gravity = Gravity.TOP or Gravity.LEFT
            setTitle("PetCollection recognition effect")
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        manager.addView(overlay, params)
        onDispose {
            manager.removeViewImmediate(overlay)
            overlay.disposeComposition()
        }
    }
}
