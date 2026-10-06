package com.aiassistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.aiassistant.ui.theme.EchoTokens

/**
 * Echo 分区标题 (EchoSectionHeader)
 * 统一设置页、侧边栏、弹窗内分区标题的视觉规范。
 *
 * 设计决策：
 * - 使用 titleSmall + SemiBold + 15sp，保证分区标题可读性（此前 labelMedium 12sp 过小）
 * - 支持可选的副标题说明
 * - 上间距使用 sectionGap (20dp)，与卡片组形成呼吸感
 */
@Composable
fun EchoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = EchoTokens.Spacing.sectionGap,
                bottom = EchoTokens.Spacing.sm
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.2.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.width(EchoTokens.Spacing.sm))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showDivider) {
            Spacer(modifier = Modifier.height(EchoTokens.Spacing.sm))
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}
