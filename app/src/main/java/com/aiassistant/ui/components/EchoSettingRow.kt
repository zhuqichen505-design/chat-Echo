package com.aiassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.theme.EchoTokens

/**
 * Echo 标准设置行 (EchoSettingRow)
 * 统一设置页、弹窗内设置项的视觉规范：图标底色块 + 标题/徽章/副标题 + 尾部控件。
 *
 * 设计决策（UI-Kimi.md §4.2）：
 * - 最小高度 56dp（无副标题）/ 64dp（有副标题），确保触控目标充足
 * - 图标统一置于 42dp 圆角底色块（primary 12%）内，图标 22dp，
 *   替代设置页 20/22/24/28 图标与"有底色块/无底色块"混用
 * - 标题使用 cardTitle 排版令牌，消灭 16/16.5/17sp 混用
 * - 支持标题右侧状态徽章（如"未配置""已启用"），进入子页前即可感知状态
 * - 支持尾部任意控件：Switch、IconButton、Text 等
 * - 默认自带 cardPadding 水平内边距（整卡通栏行使用）；
 *   嵌入已有内边距容器（如 SettingsGlassCard）时传 0.dp 避免双重缩进
 */
@Composable
fun EchoSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    badgeText: String? = null,
    badgeType: EchoBadgeType = EchoBadgeType.Neutral,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPaddingHorizontal: Dp = EchoTokens.Spacing.cardPadding,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    val minHeight = if (subtitle != null) {
        EchoTokens.Component.settingRowMinHeightWithSubtitle
    } else {
        EchoTokens.Component.settingRowMinHeight
    }

    val rowModifier = if (onClick != null && enabled) {
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
    }

    Row(
        modifier = rowModifier
            .padding(horizontal = contentPaddingHorizontal)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // 图标底色块：42dp 圆角块 + 22dp 图标
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(EchoTokens.Component.settingIconBackdrop)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(EchoTokens.IconSize.header),
                    tint = iconTint
                )
            }
            Spacer(modifier = Modifier.width(EchoTokens.Spacing.inlineGap))
        }

        // 标题 + 徽章 + 副标题
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = EchoTokens.Type.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (badgeText != null) {
                    Spacer(modifier = Modifier.width(EchoTokens.Spacing.inlineGapTight))
                    EchoBadge(text = badgeText, type = badgeType)
                }
            }
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(EchoTokens.Spacing.inlineGapTight))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 尾部控件
        if (trailing != null) {
            Spacer(modifier = Modifier.width(EchoTokens.Spacing.inlineGap))
            trailing()
        }
    }
}
