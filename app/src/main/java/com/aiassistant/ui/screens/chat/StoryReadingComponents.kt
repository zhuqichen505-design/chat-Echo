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

/** 仅调整故事正文；普通问答、思考过程和消息操作保持原有样式。 */
@Composable
internal fun StoryReadingTypography(
    style: String?,
    font: String = EchoStoryFont.LITERARY,
    content: @Composable () -> Unit
) {
    if (style == null) {
        content()
        return
    }
    val typography = MaterialTheme.typography
    val immersive = style == EchoStoryReadingStyle.IMMERSIVE
    val family = when (EchoStoryFont.sanitize(font)) {
        EchoStoryFont.TYPEWRITER -> FontFamily.Monospace
        EchoStoryFont.CLEAR -> FontFamily.SansSerif
        else -> EchoLiteraryFont
    }
    val lineMultiplier = if (immersive) 1.95f else 1.65f
    MaterialTheme(
        typography = typography.copy(
            bodyLarge = typography.bodyLarge.copy(
                fontFamily = family,
                lineHeight = typography.bodyLarge.fontSize * lineMultiplier
            ),
            bodyMedium = typography.bodyMedium.copy(
                fontFamily = family,
                lineHeight = typography.bodyMedium.fontSize * lineMultiplier
            )
        ),
        content = content
    )
}

internal val EchoLiteraryFont = FontFamily(Font(R.font.echo_literary_regular))

/** 分栏模式只显示已保存的故事资料，不冒充本轮模型实际触发的上下文。 */
@Composable
internal fun StoryContextPanel(
    uiState: ChatUiState,
    settings: TempChatSettings,
    expandedByDefault: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(expandedByDefault) { mutableStateOf(expandedByDefault) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "故事资料 · 收起" else "故事资料 · 展开")
            }
            if (expanded) {
                val characters = uiState.roleplayCharacters.map { it.name }.filter { it.isNotBlank() }
                    .ifEmpty { listOfNotNull(uiState.roleplayCharacter?.name) }
                Text("人物", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(characters.joinToString("、").ifBlank { "尚未选择角色" }, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("世界观", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(uiState.roleplayScenario?.name?.takeIf { it.isNotBlank() } ?: "尚未选择世界观", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("故事时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(uiState.currentStoryTime?.takeIf { it.isNotBlank() } ?: "尚未记录", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("世界书", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(storyWorldBookSelectionLabel(settings.enableWorldBook, settings.activeWorldBookIds), style = MaterialTheme.typography.bodyMedium)
                uiState.roleplaySession?.currentPlotSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                    Spacer(Modifier.height(12.dp))
                    Text("已保存摘要", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(top = 12.dp)) { Text("管理故事设定") }
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
