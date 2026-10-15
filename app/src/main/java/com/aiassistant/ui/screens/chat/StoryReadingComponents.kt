package com.aiassistant.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import com.aiassistant.R
import androidx.compose.ui.unit.dp
import com.aiassistant.utils.EchoStoryFont
import com.aiassistant.utils.EchoStoryReadingStyle

/** 正文阅读设置。排版和字体可独立选择作用范围，操作控件不继承此层。 */
@Composable
internal fun StoryReadingTypography(
    style: String?,
    font: String? = EchoStoryFont.LITERARY,
    content: @Composable () -> Unit
) {
    if (style == null && font == null) {
        content()
        return
    }
    val typography = MaterialTheme.typography
    val immersive = style == EchoStoryReadingStyle.IMMERSIVE
    val family = font?.let(::readingFontFamily)
    val lineMultiplier = if (immersive) 1.95f else 1.65f
    MaterialTheme(
        typography = typography.copy(
            bodyLarge = typography.bodyLarge.copy(
                fontFamily = family ?: typography.bodyLarge.fontFamily,
                lineHeight = if (style == null) typography.bodyLarge.lineHeight else typography.bodyLarge.fontSize * lineMultiplier
            ),
            bodyMedium = typography.bodyMedium.copy(
                fontFamily = family ?: typography.bodyMedium.fontFamily,
                lineHeight = if (style == null) typography.bodyMedium.lineHeight else typography.bodyMedium.fontSize * lineMultiplier
            ),
            bodySmall = typography.bodySmall.copy(
                fontFamily = family ?: typography.bodySmall.fontFamily,
                lineHeight = if (style == null) typography.bodySmall.lineHeight else typography.bodySmall.fontSize * lineMultiplier
            ),
            titleLarge = typography.titleLarge.copy(fontFamily = family ?: typography.titleLarge.fontFamily),
            titleMedium = typography.titleMedium.copy(fontFamily = family ?: typography.titleMedium.fontFamily),
            titleSmall = typography.titleSmall.copy(fontFamily = family ?: typography.titleSmall.fontFamily)
        ),
        content = content
    )
}

internal fun readingFontFamily(font: String): FontFamily = when (EchoStoryFont.sanitize(font)) {
    EchoStoryFont.TYPEWRITER -> FontFamily.Monospace
    EchoStoryFont.CLEAR -> FontFamily.SansSerif
    else -> EchoLiteraryFont
}

internal val EchoLiteraryFont = FontFamily(Font(R.font.echo_literary_regular))

/** 分栏展示同一 Conversation 的资料，普通对话与故事都可使用。 */
@Composable
internal fun StoryContextPanel(
    uiState: ChatUiState,
    settings: TempChatSettings,
    expandedByDefault: Boolean,
    onOpenSettings: () -> Unit,
    memories: List<com.aiassistant.domain.model.MemoryItem> = emptyList(),
    timeline: List<com.aiassistant.domain.model.TimelineNode> = emptyList(),
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(expandedByDefault) { mutableStateOf(expandedByDefault) }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text("${if (uiState.isRoleplay) "故事" else "会话"}资料 · ${if (expanded) "收起" else "展开"}")
            }
            if (expanded) {
                if (uiState.isRoleplay) {
                    val names = uiState.roleplayCharacters.map { it.name }.filter { it.isNotBlank() }
                        .ifEmpty { listOfNotNull(uiState.roleplayCharacter?.name) }
                    Text("人物与世界", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text((names + listOfNotNull(uiState.roleplayScenario?.name)).joinToString(" · ").ifBlank { "自由写作" }, style = MaterialTheme.typography.bodyMedium)
                }
                val enabled = memories.filter { it.isEnabled }
                Text("会话设定 ${enabled.size} 条 · ${if (settings.enableSessionMemory) "参与上下文" else "已停用"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                enabled.take(3).forEach { Text(it.content, style = MaterialTheme.typography.bodySmall, maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                if (enabled.isEmpty()) Text("尚未保存会话规则", style = MaterialTheme.typography.bodySmall)
                Text("时间线 ${timeline.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                uiState.currentStoryTime?.takeIf { it.isNotBlank() }?.let { Text("当前：$it", style = MaterialTheme.typography.bodySmall) }
                timeline.sortedWith(compareBy<com.aiassistant.domain.model.TimelineNode> { it.orderIndex }.thenBy { it.createdAt }).takeLast(2).forEach {
                    Text("${it.timeTag} · ${it.event}", style = MaterialTheme.typography.bodySmall, maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                if (timeline.isEmpty()) Text("尚未记录事件", style = MaterialTheme.typography.bodySmall)
                Text("世界书", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(storyWorldBookSelectionLabel(settings.enableWorldBook, settings.activeWorldBookIds), style = MaterialTheme.typography.bodySmall)
                uiState.roleplaySession?.currentPlotSummary?.takeIf { it.isNotBlank() }?.let {
                    Text("剧情摘要", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(0.dp)) { Text(if (uiState.isRoleplay) "打开故事工作台" else "管理会话资料") }
            }
        }
    }
}

internal fun storyWorldBookSelectionLabel(enabled: Boolean, selectedIds: String?): String = when {
    !enabled -> "已关闭"
    selectedIds == null -> "跟随所有已启用世界书"
    else -> selectedIds.split(',').mapNotNull { it.trim().toLongOrNull() }.distinct().size.let { count ->
        if (count == 0) "未选择世界书" else "指定 $count 本 · 仅启用书参与触发"
    }
}
