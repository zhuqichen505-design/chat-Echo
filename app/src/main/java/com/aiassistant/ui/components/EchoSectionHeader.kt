package com.aiassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.ui.theme.EchoHeaderStyle
import com.aiassistant.ui.theme.EchoThemedDivider
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.ui.theme.LocalEchoThemeStyle

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
        val headerStyle = LocalEchoThemeStyle.current.headerStyle
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (headerStyle) {
                EchoHeaderStyle.BauhausBar, EchoHeaderStyle.SwissEditorial -> {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(width = 4.dp, height = 14.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                EchoHeaderStyle.BlueprintSpec, EchoHeaderStyle.TelemetryHud -> {
                    Text(
                        text = if (headerStyle == EchoHeaderStyle.BlueprintSpec) "// " else ":: ",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                EchoHeaderStyle.FolioChapter, EchoHeaderStyle.NewspaperMasthead, EchoHeaderStyle.CatalogueBar -> {
                    Text(
                        text = if (headerStyle == EchoHeaderStyle.FolioChapter) "§ " else "▪ ",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                EchoHeaderStyle.CinemaSlate -> {
                    Text(
                        text = "▸ ",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                EchoHeaderStyle.NauticalLog -> {
                    Text(
                        text = "◇ ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                EchoHeaderStyle.CeladonSeal -> {
                    Box(
                        modifier = Modifier
                            .padding(end = 7.dp)
                            .size(12.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                    }
                }
                EchoHeaderStyle.LacquerPlaque -> {
                    Text(
                        text = "◆ ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                EchoHeaderStyle.BookPlate -> {
                    Text(
                        text = "— ",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                EchoHeaderStyle.VerseSlip -> {
                    Box(
                        modifier = Modifier
                            .padding(end = 7.dp)
                            .size(width = 2.dp, height = 13.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                    )
                }
                EchoHeaderStyle.AiryHorizon -> {
                    Box(
                        modifier = Modifier
                            .padding(end = 7.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                }
                EchoHeaderStyle.Default -> Unit
            }
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
            EchoThemedDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )
        }
    }
}
