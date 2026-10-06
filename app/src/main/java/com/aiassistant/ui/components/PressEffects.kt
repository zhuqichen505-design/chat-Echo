package com.aiassistant.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.aiassistant.ui.theme.EchoMotion
import com.aiassistant.ui.theme.rememberReducedMotion

/**
 * 按压下沉缩放动画（P2-2）：按压瞬间 scale→0.97（80ms instant），松开 snappy 弹簧回弹。
 * 动画值仅在 graphicsLayer（绘制阶段）读取，零重组；reduced motion 时跳过缩放。
 * 供 echoShapeClick / echoShapeCombinedClick 内部共用，消费方零改动获得统一按压手感。
 */
@Composable
private fun rememberPressScale(pressed: Boolean): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val scale = remember { Animatable(1f) }
    val reducedMotion = rememberReducedMotion()
    LaunchedEffect(pressed) {
        if (reducedMotion) return@LaunchedEffect
        if (pressed) {
            // 检查报告 P3-1：tween 构造归口 EchoMotion.tweenSpec（禁止绕过令牌构造入口直接调 tween）
            scale.animateTo(0.97f, EchoMotion.tweenSpec<Float>(EchoMotion.Duration.instant, EchoMotion.Easing.standard))
        } else {
            scale.animateTo(1f, EchoMotion.Spring.snappy())
        }
    }
    return scale
}

@Composable
fun Modifier.echoShapeClick(
    shape: Shape,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = rememberPressScale(pressed)
    return this
        .graphicsLayer {
            val s = pressScale.value
            scaleX = s
            scaleY = s
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            if (pressed) {
                val overlay = Color.White.copy(alpha = 0.16f)
                when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Rectangle -> drawRect(overlay)
                    is Outline.Rounded -> {
                        val path = Path().apply { addRoundRect(outline.roundRect) }
                        drawPath(path, overlay)
                    }
                    is Outline.Generic -> drawPath(outline.path, overlay)
                }
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

@Composable
fun Modifier.echoPlainClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.echoShapeCombinedClick(
    shape: Shape,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = rememberPressScale(pressed)
    return this
        .graphicsLayer {
            val s = pressScale.value
            scaleX = s
            scaleY = s
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            if (pressed) {
                val overlay = Color.White.copy(alpha = 0.16f)
                when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Rectangle -> drawRect(overlay)
                    is Outline.Rounded -> {
                        val path = Path().apply { addRoundRect(outline.roundRect) }
                        drawPath(path, overlay)
                    }
                    is Outline.Generic -> drawPath(outline.path, overlay)
                }
            }
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
            onClick = onClick
        )
}
