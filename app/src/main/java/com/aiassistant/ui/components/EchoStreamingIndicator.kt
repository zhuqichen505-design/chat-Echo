package com.aiassistant.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import com.aiassistant.ui.theme.EchoMotion
import com.aiassistant.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos

/** User-selectable streaming output styles. Persist these exact values. */
object StreamingAnimationStyle {
    const val THIN_CURSOR = com.aiassistant.utils.EchoStreamingAnimationStyle.THIN_CURSOR
    const val SOUND_WAVE = com.aiassistant.utils.EchoStreamingAnimationStyle.SOUND_WAVE
    const val INK_RING = com.aiassistant.utils.EchoStreamingAnimationStyle.INK_RING

    // Source-compatible aliases for callers that used the earlier draft names.
    const val CURSOR = THIN_CURSOR
    const val WAVEFORM = SOUND_WAVE

    fun normalize(value: String?): String = when (value) {
        SOUND_WAVE, "waveform" -> SOUND_WAVE
        INK_RING -> INK_RING
        THIN_CURSOR, "cursor" -> THIN_CURSOR
        else -> THIN_CURSOR
    }
}

/**
 * Small, layout-neutral streaming status mark.
 *
 * The same component is used at the end of streamed Markdown and in the empty
 * connection slot. It draws only on Canvas, so animation frames never trigger
 * text measurement or LazyColumn layout. Reduced motion keeps a static state.
 */
@Composable
fun EchoStreamingIndicator(
    color: Color,
    style: String = StreamingAnimationStyle.CURSOR,
    modifier: Modifier = Modifier,
    fading: Boolean = false,
    compact: Boolean = false
) {
    val resolvedStyle = StreamingAnimationStyle.normalize(style)
    val reduced = rememberReducedMotion()
    val fadeTarget = if (fading) 0f else 1f
    val fade = androidx.compose.animation.core.animateFloatAsState(
        targetValue = fadeTarget,
        animationSpec = if (reduced) androidx.compose.animation.core.snap() else
            EchoMotion.tweenSpec<Float>(EchoMotion.Typewriter.cursorFadeMs),
        label = "streamingIndicatorFade"
    )
    val phase: State<Float>? = if (reduced || fading) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "streamingIndicator")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = EchoMotion.linearCycleSpec(
                when (resolvedStyle) {
                    StreamingAnimationStyle.WAVEFORM -> EchoMotion.Cycle.reconnectArc
                    StreamingAnimationStyle.INK_RING -> EchoMotion.Cycle.pulse
                    else -> EchoMotion.Typewriter.cursorBlinkMs
                }
            ),
            label = "streamingIndicatorPhase"
        )
    }
    val width = when (resolvedStyle) {
        StreamingAnimationStyle.WAVEFORM -> if (compact) 28.dp else 18.dp
        StreamingAnimationStyle.INK_RING -> 18.dp
        else -> 2.dp
    }
    Canvas(modifier.then(Modifier.size(width, 18.dp))) {
        val alpha = fade.value.coerceIn(0f, 1f)
        val progress = phase?.value ?: 0f
        when (resolvedStyle) {
            StreamingAnimationStyle.WAVEFORM -> {
                val center = size.width / 2f
                val gap = (size.width * 0.26f).coerceAtLeast(3.dp.toPx())
                val barWidth = (size.width * 0.10f).coerceAtLeast(1.5.dp.toPx())
                repeat(3) { index ->
                    val wave = if (reduced) 0f else {
                        val shifted = (progress - index / 3f).mod(1f)
                        0.5f - 0.5f * cos((shifted * 2.0 * PI).toFloat())
                    }
                    val barHeight = size.height * (0.34f + 0.50f * wave)
                    drawRoundRect(
                        color = color.copy(alpha = alpha * (0.52f + 0.48f * wave)),
                        topLeft = Offset(center + (index - 1) * gap - barWidth / 2f, (size.height - barHeight) / 2f),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                    )
                }
            }
            StreamingAnimationStyle.INK_RING -> {
                val pulse = if (reduced) 0.84f else 0.78f + 0.22f *
                    (0.5f - 0.5f * cos((progress * 2.0 * PI).toFloat()))
                val stroke = 1.7.dp.toPx()
                val radius = (minOf(size.width, size.height) / 2f - stroke) * pulse
                drawCircle(
                    color = color.copy(alpha = alpha * (if (reduced) 0.82f else 0.60f + pulse * 0.40f)),
                    radius = radius,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            else -> {
                val cursorAlpha = if (reduced || fading) 1f else 0.25f + 0.75f *
                    (0.5f - 0.5f * cos((progress * 2.0 * PI).toFloat()))
                drawRoundRect(
                    color = color.copy(alpha = alpha * cursorAlpha),
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(size.width, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f)
                )
            }
        }
    }
}
