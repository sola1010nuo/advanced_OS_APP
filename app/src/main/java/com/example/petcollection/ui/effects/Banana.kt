package com.example.petcollection.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Reuses the project's original banana artwork; coordinates are normalized and cached. */
internal fun bananaPath() = Path().apply {
    moveTo(.2f, .08f)
    cubicTo(.12f, .6f, .52f, .82f, .92f, .35f)
    cubicTo(.68f, 1.03f, .07f, .98f, .2f, .08f)
    close()
}

internal fun DrawScope.drawBanana(path: Path, side: Float, alpha: Float = 1f) {
    withTransform({ scale(side, side, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
        drawPath(path, Color(0xFFFFD458), alpha = alpha)
        drawPath(path, Color(0xFF90620C), alpha = alpha, style = Stroke(width = .055f))
    }
}

@Composable
internal fun Banana(modifier: Modifier) {
    val path = remember { bananaPath() }
    Canvas(modifier.clearAndSetSemantics { }) {
        drawBanana(path, size.minDimension)
    }
}
