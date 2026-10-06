package com.aiassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.ui.theme.rememberEchoSemanticColors

/**
 * Echo 徽章 (EchoBadge)
 * 统一全应用状态徽章、计数徽章、标签徽章的视觉规范。
 *
 * 设计决策：
 * - 使用 badgeLabel 排版令牌（12sp），确保一致性
 * - 支持三种类型：状态点、计数、标签
 * - 颜色语义化：primary/success/warning/error
 */
@Composable
fun EchoBadge(
    text: String,
    modifier: Modifier = Modifier,
    type: EchoBadgeType = EchoBadgeType.Primary,
    icon: ImageVector? = null
) {
    val semanticColors = rememberEchoSemanticColors()
    val (backgroundColor, contentColor) = when (type) {
        EchoBadgeType.Primary -> {
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
        EchoBadgeType.Success -> {
            semanticColors.success.container to semanticColors.success.onContainer
        }
        EchoBadgeType.Warning -> {
            semanticColors.warning.container to semanticColors.warning.onContainer
        }
        EchoBadgeType.Error -> {
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }
        EchoBadgeType.Info -> {
            semanticColors.info.container to semanticColors.info.onContainer
        }
        EchoBadgeType.Neutral -> {
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Box(
        modifier = modifier
            .clip(EchoTokens.Radius.shapePill)
            .background(backgroundColor)
            .padding(
                horizontal = EchoTokens.Spacing.sm,
                vertical = EchoTokens.Spacing.xxs
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(EchoTokens.IconSize.badge),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(EchoTokens.Spacing.xxs))
            }
            Text(
                text = text,
                style = EchoTokens.Type.badgeLabel,
                color = contentColor
            )
        }
    }
}

/**
 * Echo 徽章类型
 */
enum class EchoBadgeType {
    Primary,    // 主要状态
    Success,    // 成功状态
    Warning,    // 警告状态
    Error,      // 错误状态
    Info,       // 信息提示
    Neutral     // 中性标签
}

/**
 * Echo 状态点徽章
 * 用于表示在线状态、未读计数等场景
 */
@Composable
fun EchoDotBadge(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    count: Int? = null
) {
    Box(
        modifier = modifier
            .size(if (count != null) 18.dp else 8.dp)
            .clip(EchoTokens.Radius.shapePill)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (count != null && count > 0) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
