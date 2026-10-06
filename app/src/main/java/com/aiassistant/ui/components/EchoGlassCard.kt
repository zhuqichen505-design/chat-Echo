package com.aiassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.theme.EchoTokens
import dev.chrisbanes.haze.HazeState

/**
 * 卡片视觉强调级别 (CardEmphasis)
 * - Standard：完整四层玻璃效果（高光 + 顶部微光线 + 渐变边框 + 阴影），用于视觉焦点区
 * - Subdued：仅保留"边框 + 轻阴影"，用于设置/历史等列表密集场景，降低视觉噪点（§3.3 E-6）
 */
enum class CardEmphasis { Standard, Subdued }

/**
 * Echo 标准玻璃卡片 (EchoGlassCard)
 * 全应用统一的高端液态玻璃容器，具备纯净半透明底色、45°环境光折射高光、平滑微阴影。
 * 杜绝多次模糊计算导致的错位、拖影与重叠色块 Bug。
 */
@Composable
fun EchoGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = EchoTokens.Radius.shapeLg,
    containerColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = EchoTokens.Glass.borderWidth,
    elevation: Dp = EchoTokens.Elevation.subtle,
    hazeState: HazeState? = null,
    highlight: Boolean = false,
    showBorder: Boolean = true,
    emphasis: CardEmphasis = CardEmphasis.Standard,
    content: @Composable BoxScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f

    val defaultBg = containerColor ?: if (isDark) {
        colorScheme.surface.copy(alpha = EchoTokens.Glass.cardAlphaDark)
    } else {
        colorScheme.surface.copy(alpha = EchoTokens.Glass.cardAlphaLight)
    }

    val defaultBorderBrush = borderColor?.let {
        Brush.linearGradient(listOf(it, it))
    } ?: Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to (if (isDark) colorScheme.outline.copy(alpha = if (highlight) 0.60f else 0.50f) else colorScheme.outline.copy(alpha = if (highlight) 0.58f else 0.48f)),
            0.40f to (if (highlight) colorScheme.primary.copy(alpha = 0.30f) else colorScheme.outlineVariant.copy(alpha = 0.18f)),
            1.00f to colorScheme.primary.copy(alpha = if (highlight) 0.38f else 0.18f)
        ),
        start = Offset(0f, 0f),
        end = Offset.Infinite
    )

    val shadowRecipe = EchoTokens.Elevation.card(isDark)
    var baseModifier = modifier
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = shadowRecipe.ambient,
            spotColor = shadowRecipe.spot
        )
        .clip(shape)
        .background(defaultBg, shape)
        .then(
            if (emphasis == CardEmphasis.Subdued) Modifier else Modifier.drawBehind {
            // 45° 柔和环境漫反射高光 (模拟真实液态玻璃光学折射)
            val specularAlpha = if (isDark) 0.05f else 0.09f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = specularAlpha),
                        Color.Transparent
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.7f, size.height * 0.7f)
                ),
                size = size,
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
            )
            // 顶部微光边缘线
            drawRoundRect(
                color = Color.White.copy(alpha = if (isDark) 0.06f else 0.10f),
                topLeft = Offset(size.width * 0.08f, 1.dp.toPx()),
                size = Size(size.width * 0.60f, 1.dp.toPx()),
                cornerRadius = CornerRadius(999.dp.toPx(), 999.dp.toPx())
            )
            }
        )

    if (showBorder && borderWidth > 0.dp) {
        baseModifier = baseModifier.border(
            border = BorderStroke(borderWidth, defaultBorderBrush),
            shape = shape
        )
    }

    Box(
        modifier = baseModifier,
        content = content
    )
}

/**
 * 可点击或长按交互的 Echo 标准玻璃卡片
 */
@Composable
fun EchoGlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    enabled: Boolean = true,
    shape: Shape = EchoTokens.Radius.shapeLg,
    containerColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = EchoTokens.Glass.borderWidth,
    elevation: Dp = EchoTokens.Elevation.subtle,
    hazeState: HazeState? = null,
    highlight: Boolean = false,
    showBorder: Boolean = true,
    emphasis: CardEmphasis = CardEmphasis.Standard,
    content: @Composable BoxScope.() -> Unit
) {
    val clickModifier = if (onLongClick != null) {
        modifier.echoShapeCombinedClick(
            shape = shape,
            enabled = enabled,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
            onClick = onClick
        )
    } else {
        modifier.echoShapeClick(shape = shape, enabled = enabled, onClick = onClick)
    }
    EchoGlassCard(
        modifier = clickModifier,
        shape = shape,
        containerColor = containerColor,
        borderColor = borderColor,
        borderWidth = borderWidth,
        elevation = elevation,
        hazeState = hazeState,
        highlight = highlight,
        showBorder = showBorder,
        emphasis = emphasis,
        content = content
    )
}
