package com.aiassistant.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.theme.EchoTokens

/**
 * Echo 图标按钮 (EchoIconButton)
 * 统一全应用图标按钮的视觉规范：触控目标 48dp，图标 24dp。
 *
 * 设计决策：
 * - 触控目标使用 TouchTarget.minimum (48dp)，符合无障碍规范
 * - 图标统一使用 IconSize.action (24dp)
 * - 支持紧凑模式（工具条/气泡尾部），触控目标 36dp + 外间距补偿
 */
@Composable
fun EchoIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    colors: IconButtonColors = echoIconButtonColors()
) {
    val touchTarget = if (compact) {
        EchoTokens.TouchTarget.compact
    } else {
        EchoTokens.TouchTarget.minimum
    }

    val iconSize = if (compact) {
        EchoTokens.IconSize.inline
    } else {
        EchoTokens.IconSize.action
    }

    Box(
        modifier = modifier.size(touchTarget),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = colors,
            modifier = Modifier.size(touchTarget)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Echo 图标按钮配色方案
 */
@Composable
fun echoIconButtonColors(): IconButtonColors {
    val colorScheme = MaterialTheme.colorScheme

    return IconButtonDefaults.iconButtonColors(
        containerColor = Color.Transparent,
        contentColor = colorScheme.onSurfaceVariant,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
    )
}

/**
 * Echo 主要操作图标按钮（带背景色）
 * 用于需要强调的操作，如发送、确认等
 */
@Composable
fun EchoPrimaryIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier.size(EchoTokens.TouchTarget.minimum),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            modifier = Modifier.size(EchoTokens.TouchTarget.minimum)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(EchoTokens.IconSize.action)
            )
        }
    }
}
