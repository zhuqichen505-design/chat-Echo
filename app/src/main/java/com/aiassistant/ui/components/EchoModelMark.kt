package com.aiassistant.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.theme.EchoMotion
import com.aiassistant.ui.theme.rememberReducedMotion
import com.aiassistant.utils.EchoModelMarkStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 保留主题色倾向，必要时向黑/白收敛，避免自定义背景吞掉细线。 */
fun modelMarkColorFor(accent: Color, backdrop: Color): Color {
    val surface = backdrop.compositeOver(Color.White)
    fun contrast(color: Color): Float {
        val first = color.compositeOver(surface).luminance()
        val second = surface.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
    if (contrast(accent) >= 4.5f) return accent
    val target = readableTextColorFor(surface)
    for (step in 1..20) {
        val candidate = lerp(accent, target, step / 20f)
        if (contrast(candidate) >= 4.5f) return candidate
    }
    return target
}

/**
 * 头像关闭时的模型标识。默认双描边同心圆对应阅读预览中的 mark。
 * 相位只在 Canvas 绘制时读取，不使流式正文重新组合或测量；结束后卸载动画。
 */
@Composable
fun EchoModelMark(
    color: Color,
    style: String = EchoModelMarkStyle.CONCENTRIC,
    animated: Boolean = false,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    description: String? = null
) {
    val resolvedStyle = EchoModelMarkStyle.sanitize(style)
    val phase: State<Float>? = if (animated && !reducedMotion) {
        rememberInfiniteTransition(label = "modelMark").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = EchoMotion.linearCycleSpec(EchoMotion.Cycle.pulse),
            label = "modelMarkPhase"
        )
    } else null
    Canvas(modifier.size(26.dp).semantics {
        description?.let { contentDescription = it }
    }) {
        val progress = phase?.value ?: 0f
        val pulse = if (phase == null) 0f else sin((progress * 2f * PI).toFloat())
        val radius = size.minDimension * 0.43f
        val stroke = (size.minDimension * 0.065f).coerceAtLeast(1.dp.toPx())
        when (resolvedStyle) {
            EchoModelMarkStyle.ORBIT -> {
                drawCircle(color.copy(alpha = color.alpha * 0.48f), radius, style = Stroke(stroke * 0.8f))
                drawCircle(color, radius * 0.24f)
                val angle = progress * 2f * PI.toFloat() - PI.toFloat() / 2f
                val orbitRadius = radius - stroke * 0.3f
                drawCircle(color, stroke * 1.15f,
                    center + Offset(cos(angle) * orbitRadius, sin(angle) * orbitRadius))
                drawCircle(color.copy(alpha = color.alpha * 0.65f), stroke * 0.85f,
                    center - Offset(cos(angle) * orbitRadius, sin(angle) * orbitRadius))
            }
            EchoModelMarkStyle.PRISM -> {
                // 小幅摇转保持菱形辨识度；不是高速、连续翻转的加载图标。
                rotate(pulse * 18f) {
                    val corners = listOf(
                        center + Offset(0f, -radius), center + Offset(radius, 0f),
                        center + Offset(0f, radius), center + Offset(-radius, 0f)
                    )
                    corners.indices.forEach { index ->
                        drawLine(color, corners[index], corners[(index + 1) % corners.size], stroke, StrokeCap.Round)
                    }
                    val inner = radius * (0.44f + 0.04f * pulse)
                    drawLine(color.copy(alpha = color.alpha * 0.6f), center - Offset(inner, 0f), center - Offset(0f, inner), stroke)
                    drawLine(color.copy(alpha = color.alpha * 0.6f), center - Offset(0f, inner), center + Offset(inner, 0f), stroke)
                    drawLine(color.copy(alpha = color.alpha * 0.6f), center + Offset(inner, 0f), center + Offset(0f, inner), stroke)
                    drawLine(color.copy(alpha = color.alpha * 0.6f), center + Offset(0f, inner), center - Offset(inner, 0f), stroke)
                }
            }
            EchoModelMarkStyle.WAVEFORM -> {
                repeat(5) { index ->
                    val restingHeight = if (index == 2) 0.78f else if (index % 2 == 1) 0.5f else 0.28f
                    val variation = if (phase == null) 0f else
                        0.18f * sin((progress * 2f * PI + index * 0.8f).toFloat())
                    val halfHeight = radius * (restingHeight + variation)
                    val x = center.x + (index - 2) * radius * 0.4f
                    drawLine(color, Offset(x, center.y - halfHeight), Offset(x, center.y + halfHeight),
                        stroke * 1.15f, StrokeCap.Round)
                }
            }
            else -> {
                // 两条完整圆线保持预览的双环结构，仅轻微相向舒展，无额外加载点。
                drawCircle(color, radius * (1f + 0.025f * pulse), style = Stroke(stroke))
                drawCircle(color.copy(alpha = color.alpha * (0.86f + 0.10f * pulse)),
                    radius * (0.60f - 0.045f * pulse), style = Stroke(stroke))
            }
        }
    }
}
