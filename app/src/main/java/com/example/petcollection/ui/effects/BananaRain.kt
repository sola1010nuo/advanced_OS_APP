package com.example.petcollection.ui.effects

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

internal const val BANANA_RAIN_DURATION_MS = 4000
// Leave enough drain time for the slower particles within the original four seconds.
internal const val BANANA_RAIN_EMISSION_MS = 1850
internal const val BANANA_PARTICLE_COUNT = 48

internal data class BananaParticle(
    val x: Float, val delayMs: Int, val fallMs: Int, val sizeDp: Float,
    val rotation: Float, val spin: Float, val sway: Float, val phase: Float
)

internal fun bananaParticles(seed: Int): List<BananaParticle> {
    val random = Random(seed)
    val lanes = List(BANANA_PARTICLE_COUNT / 8) { (0 until 8).shuffled(random) }
    return List(BANANA_PARTICLE_COUNT) { index ->
        val emissionStart = index * BANANA_RAIN_EMISSION_MS / BANANA_PARTICLE_COUNT
        BananaParticle(
            // Six independently shuffled-width waves, staggered throughout emission.
            x = (lanes[index / 8][index % 8] + random.nextFloat()) / 8f,
            delayMs = emissionStart + random.nextInt(0, minOf(40, BANANA_RAIN_EMISSION_MS - emissionStart)),
            // Same random speed distribution, with 25% longer travel (20% slower).
            fallMs = (random.nextInt(1200, 1700) * 1.25f).toInt(),
            sizeDp = random.nextInt(24, 43).toFloat(),
            rotation = random.nextFloat() * 80f - 40f,
            spin = random.nextFloat() * 100f - 50f,
            sway = .015f + random.nextFloat() * .025f,
            phase = random.nextFloat() * (2f * PI.toFloat())
        )
    }
}

/** Finite continuous rain, then a drain interval. Per-frame reads stay in drawing. */
@Composable
internal fun BananaRain(resultId: String, modifier: Modifier, onFinished: () -> Unit) {
    val elapsed = remember(resultId) { Animatable(0f) }
    val particles = remember(resultId) { bananaParticles(resultId.hashCode()) }
    // Android's default font fallback renders the system color emoji. Cache its
    // metrics once and scale uniformly; the Polaroid's Banana artwork is separate.
    val emojiPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 100f } }
    val emoji = "\uD83C\uDF4C"
    val emojiWidth = remember { emojiPaint.measureText(emoji) }
    val emojiMetrics = remember { emojiPaint.fontMetrics }
    val emojiExtent = remember { maxOf(emojiWidth, emojiMetrics.descent - emojiMetrics.ascent) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(resultId) {
        elapsed.animateTo(BANANA_RAIN_DURATION_MS.toFloat(), tween(BANANA_RAIN_DURATION_MS, easing = LinearEasing))
        finished()
    }
    // Canvas has no pointerInput/clickable modifier, so it cannot intercept gestures.
    Canvas(modifier.testTag("banana-rain")) {
        val time = elapsed.value
        particles.forEach { particle ->
            val progress = (time - particle.delayMs) / particle.fallMs
            if (progress > 0f && progress < 1f) {
                val side = particle.sizeDp.dp.toPx().coerceAtMost(size.minDimension * .11f)
                val x = size.width * (particle.x + particle.sway * sin(progress * 2f * PI.toFloat() + particle.phase)) - side / 2f
                // Start completely above the viewport and exit completely below it,
                // including the extra extent introduced by rotation.
                val y = -side * 1.5f + progress * (size.height + side * 3f)
                withTransform({
                    translate(x, y)
                    rotate(particle.rotation + particle.spin * progress, pivot = Offset(side / 2f, side / 2f))
                    scale(side / emojiExtent, side / emojiExtent, pivot = Offset.Zero)
                }) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            emoji, (emojiExtent - emojiWidth) / 2f,
                            (emojiExtent - (emojiMetrics.descent - emojiMetrics.ascent)) / 2f - emojiMetrics.ascent,
                            emojiPaint
                        )
                    }
                }
            }
        }
    }
}
