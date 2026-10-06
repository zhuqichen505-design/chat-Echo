package com.aiassistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple // 工具链约束：BOM 2024.06（foundation 1.6.x）尚无 ripple() 新 API（需 1.7+），保留弃用 API 仅警告
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.theme.EchoMotion
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.ui.theme.rememberReducedMotion
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/**
 * Echo 开关规格
 */
enum class EchoSwitchSize {
    /** M3 原生默认开关（52×32 轨道，48dp 触控目标），用于弹窗主开关等显著位置 */
    Standard,
    /** 紧凑自绘开关（44×26 轨道，48dp 触控目标），用于列表行/卡片行 */
    Compact
}

/**
 * Echo 标准开关 (EchoSwitch)
 * 统一全应用开关规格与配色，杜绝缩放修饰符导致的绘制/布局错位。
 *
 * 设计决策（UI-Kimi.md §4.1）：
 * - Compact 为自绘 44×26 轨道（M3 1.2 原生 Switch 不支持自定义尺寸）：
 *   布局占位与绘制尺寸一致，外层保留 48dp 触控目标（minimumInteractiveComponentSize 语义），
 *   并通过 toggleable(Role.Switch) 保证 TalkBack 正确朗读
 * - Standard 直接使用 M3 原生 Switch
 * - isLoading 时以微型进度指示器替代滑块并禁用交互，避免异步操作期间的"假死"错觉（S-6）
 */
@Composable
fun EchoSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    size: EchoSwitchSize = EchoSwitchSize.Compact,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    colors: SwitchColors = echoSwitchColors()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val effectiveEnabled = enabled && !isLoading

    if (size == EchoSwitchSize.Standard) {
        Switch(
            checked = checked,
            onCheckedChange = if (isLoading) null else onCheckedChange,
            modifier = modifier,
            enabled = effectiveEnabled,
            colors = colors,
            interactionSource = interactionSource,
            thumbContent = if (isLoading) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else null
        )
    } else {
        Box(
            modifier = modifier
                .size(EchoTokens.TouchTarget.minimum)
                .toggleable(
                    value = checked,
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = false),
                    enabled = effectiveEnabled,
                    role = Role.Switch,
                    onValueChange = { onCheckedChange?.invoke(it) }
                ),
            contentAlignment = Alignment.Center
        ) {
            EchoCompactSwitchTrack(
                checked = checked,
                enabled = effectiveEnabled,
                isLoading = isLoading,
                colors = colors
            )
        }
    }
}

@Composable
private fun EchoCompactSwitchTrack(
    checked: Boolean,
    enabled: Boolean,
    isLoading: Boolean,
    colors: SwitchColors
) {
    val trackWidth = EchoTokens.Component.switchTrackWidth   // 44dp
    val trackHeight = EchoTokens.Component.switchTrackHeight // 26dp
    val thumbSize = 20.dp
    val thumbMargin = 3.dp
    val pillShape = RoundedCornerShape(trackHeight / 2)

    // A5：reduced motion 时颜色/位移瞬切（snap），否则弹簧节奏动画
    val reduced = rememberReducedMotion()
    val colorSpec = if (reduced) {
        androidx.compose.animation.core.snap<androidx.compose.ui.graphics.Color>()
    } else {
        androidx.compose.animation.core.tween<androidx.compose.ui.graphics.Color>(EchoMotion.Duration.fast)
    }
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.checkedTrackColor else colors.uncheckedTrackColor,
        animationSpec = colorSpec,
        label = "echoSwitchTrackColor"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) colors.checkedThumbColor else colors.uncheckedThumbColor,
        animationSpec = colorSpec,
        label = "echoSwitchThumbColor"
    )
    val borderColor = if (checked) Color.Transparent else colors.uncheckedBorderColor
    val offsetSpec = if (reduced) {
        androidx.compose.animation.core.snap<androidx.compose.ui.unit.Dp>()
    } else {
        androidx.compose.animation.core.tween<androidx.compose.ui.unit.Dp>(EchoMotion.Duration.fast)
    }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - thumbMargin else thumbMargin,
        animationSpec = offsetSpec,
        label = "echoSwitchThumbOffset"
    )

    Box(
        modifier = Modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(pillShape)
            .background(
                if (enabled) trackColor else if (checked) colors.disabledCheckedTrackColor else colors.disabledUncheckedTrackColor
            )
            .border(
                width = 1.dp,
                color = if (enabled) borderColor else if (checked) colors.disabledCheckedBorderColor else colors.disabledUncheckedBorderColor,
                shape = pillShape
            )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(12.dp),
                strokeWidth = 2.dp,
                color = thumbColor
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart) // 垂直居中于 26dp 轨道（缺省会贴顶，底部悬空 6dp）
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(
                        if (enabled) thumbColor else if (checked) colors.disabledCheckedThumbColor else colors.disabledUncheckedThumbColor
                    )
            )
        }
    }
}

/**
 * Echo 开关配色方案
 * 统一全应用开关颜色，确保深浅色主题下均有良好对比度
 */
@Composable
fun echoSwitchColors(forcedThinking: Boolean = false): SwitchColors {
    val colorScheme = MaterialTheme.colorScheme

    return SwitchDefaults.colors(
        // 开启状态
        checkedThumbColor = colorScheme.onPrimary,
        checkedTrackColor = if (forcedThinking) Color(0xFFB9DEFF) else colorScheme.primary,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = colorScheme.primary,

        // 关闭状态
        uncheckedThumbColor = colorScheme.onSurfaceVariant,
        uncheckedTrackColor = colorScheme.surfaceContainerHighest, // §4.1：比 surfaceVariant 深一档，玻璃卡上可见（S-3）
        uncheckedBorderColor = colorScheme.outline,
        uncheckedIconColor = colorScheme.surfaceContainerHighest,

        // 禁用状态
        disabledCheckedThumbColor = colorScheme.onSurface.copy(alpha = 0.38f),
        disabledCheckedTrackColor = if (forcedThinking) Color(0xFFB9DEFF) else colorScheme.onSurface.copy(alpha = 0.12f),
        disabledCheckedBorderColor = Color.Transparent,
        disabledUncheckedThumbColor = colorScheme.onSurface.copy(alpha = 0.38f),
        disabledUncheckedTrackColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.38f),
        disabledUncheckedBorderColor = colorScheme.outline.copy(alpha = 0.12f)
    )
}
