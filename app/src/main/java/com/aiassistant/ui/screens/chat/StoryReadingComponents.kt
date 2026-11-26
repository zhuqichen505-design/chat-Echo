package com.aiassistant.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import com.aiassistant.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aiassistant.utils.EchoStoryFont
import com.aiassistant.utils.EchoStoryReadingStyle

internal enum class StoryUserMessageStyle {
    Bubble,
    QuoteLine,
    BookIndent,
    ScriptCue
}

internal data class StoryReadingMetrics(
    val lineMultiplier: Float,
    val paragraphIndentEm: Float,
    val justifyParagraphs: Boolean,
    val paragraphVerticalPaddingDp: Float,
    val messageSpacingDp: Float,
    val horizontalExtraPaddingDp: Float,
    val userStyle: StoryUserMessageStyle
)

internal fun storyReadingMetricsFor(style: String?): StoryReadingMetrics {
    if (style == null) {
        return StoryReadingMetrics(
            lineMultiplier = 1.65f,
            paragraphIndentEm = 0f,
            justifyParagraphs = false,
            paragraphVerticalPaddingDp = 2f,
            messageSpacingDp = 14f,
            horizontalExtraPaddingDp = 0f,
            userStyle = StoryUserMessageStyle.Bubble
        )
    }
    return when (EchoStoryReadingStyle.sanitize(style)) {
        EchoStoryReadingStyle.IMMERSIVE -> StoryReadingMetrics(
            lineMultiplier = 1.95f,
            paragraphIndentEm = 0f,
            justifyParagraphs = false,
            paragraphVerticalPaddingDp = 2f,
            messageSpacingDp = 14f,
            horizontalExtraPaddingDp = 0f,
            userStyle = StoryUserMessageStyle.QuoteLine
        )
        EchoStoryReadingStyle.PLAIN, EchoStoryReadingStyle.SPLIT -> StoryReadingMetrics(
            lineMultiplier = 1.65f,
            paragraphIndentEm = 0f,
            justifyParagraphs = false,
            paragraphVerticalPaddingDp = 2f,
            messageSpacingDp = 14f,
            horizontalExtraPaddingDp = 0f,
            userStyle = StoryUserMessageStyle.Bubble
        )
        EchoStoryReadingStyle.BOOK -> StoryReadingMetrics(
            lineMultiplier = 1.85f,
            paragraphIndentEm = 2.0f,
            justifyParagraphs = true,
            paragraphVerticalPaddingDp = 5f,
            messageSpacingDp = 16f,
            horizontalExtraPaddingDp = 6f,
            userStyle = StoryUserMessageStyle.BookIndent
        )
        EchoStoryReadingStyle.COMPACT -> StoryReadingMetrics(
            lineMultiplier = 1.48f,
            paragraphIndentEm = 0f,
            justifyParagraphs = false,
            paragraphVerticalPaddingDp = 1f,
            messageSpacingDp = 8f,
            horizontalExtraPaddingDp = 0f,
            userStyle = StoryUserMessageStyle.Bubble
        )
        EchoStoryReadingStyle.SCRIPT -> StoryReadingMetrics(
            lineMultiplier = 1.72f,
            paragraphIndentEm = 0f,
            justifyParagraphs = false,
            paragraphVerticalPaddingDp = 3f,
            messageSpacingDp = 16f,
            horizontalExtraPaddingDp = 4f,
            userStyle = StoryUserMessageStyle.ScriptCue
        )
        else -> StoryReadingMetrics(
            lineMultiplier = 1.95f,
            paragraphIndentEm = 0f,
            justifyParagraphs = false,
            paragraphVerticalPaddingDp = 2f,
            messageSpacingDp = 14f,
            horizontalExtraPaddingDp = 0f,
            userStyle = StoryUserMessageStyle.QuoteLine
        )
    }
}

internal data class StoryFontSpec(
    val family: FontFamily,
    val weight: FontWeight? = null,
    val letterSpacingBonusSp: Float = 0f
)

internal fun readingFontSpec(font: String): StoryFontSpec = when (EchoStoryFont.sanitize(font)) {
    EchoStoryFont.TYPEWRITER -> StoryFontSpec(FontFamily.Monospace)
    EchoStoryFont.CLEAR -> StoryFontSpec(FontFamily.SansSerif)
    EchoStoryFont.SYSTEM -> StoryFontSpec(FontFamily.Default)
    EchoStoryFont.SYSTEM_SERIF -> StoryFontSpec(FontFamily.Serif, letterSpacingBonusSp = 0.15f)
    EchoStoryFont.PRINT_BOOK -> StoryFontSpec(EchoLiteraryFont, weight = FontWeight.Medium, letterSpacingBonusSp = 0.45f)
    EchoStoryFont.ELEGANT_LIGHT -> StoryFontSpec(FontFamily.SansSerif, weight = FontWeight.Light, letterSpacingBonusSp = 0.35f)
    EchoStoryFont.CALLIGRAPHY -> StoryFontSpec(FontFamily.Cursive, letterSpacingBonusSp = 0.4f)
    else -> StoryFontSpec(EchoLiteraryFont)
}

internal fun readingFontFamily(font: String): FontFamily = readingFontSpec(font).family

internal val LocalEchoStoryReadingStyle = compositionLocalOf<String?> { null }

private fun applyStoryTextStyle(
    base: TextStyle,
    spec: StoryFontSpec?,
    lineMultiplier: Float?,
    applyWeightAndSpacing: Boolean
): TextStyle {
    val resolvedFamily = spec?.family ?: base.fontFamily
    val resolvedLineHeight = if (lineMultiplier != null) base.fontSize * lineMultiplier else base.lineHeight
    val resolvedWeight = if (applyWeightAndSpacing && spec?.weight != null) spec.weight else base.fontWeight
    val resolvedLetterSpacing = if (applyWeightAndSpacing && spec != null && spec.letterSpacingBonusSp != 0f) {
        val currentSp = if (base.letterSpacing.isSpecified) base.letterSpacing.value else 0f
        (currentSp + spec.letterSpacingBonusSp).sp
    } else {
        base.letterSpacing
    }
    return base.copy(
        fontFamily = resolvedFamily,
        fontWeight = resolvedWeight,
        lineHeight = resolvedLineHeight,
        letterSpacing = resolvedLetterSpacing
    )
}

/** 正文阅读设置。排版和字体可独立选择作用范围，操作控件不继承此层。 */
@Composable
internal fun StoryReadingTypography(
    style: String?,
    font: String? = EchoStoryFont.LITERARY,
    content: @Composable () -> Unit
) {
    if (style == null && font == null) {
        CompositionLocalProvider(LocalEchoStoryReadingStyle provides null, content = content)
        return
    }
    val typography = MaterialTheme.typography
    val spec = font?.let(::readingFontSpec)
    val lineMultiplier = if (style == null) null else storyReadingMetricsFor(style).lineMultiplier
    CompositionLocalProvider(LocalEchoStoryReadingStyle provides style) {
        MaterialTheme(
            typography = typography.copy(
                bodyLarge = applyStoryTextStyle(typography.bodyLarge, spec, lineMultiplier, applyWeightAndSpacing = true),
                bodyMedium = applyStoryTextStyle(typography.bodyMedium, spec, lineMultiplier, applyWeightAndSpacing = true),
                bodySmall = applyStoryTextStyle(typography.bodySmall, spec, lineMultiplier, applyWeightAndSpacing = true),
                titleLarge = applyStoryTextStyle(typography.titleLarge, spec, lineMultiplier = null, applyWeightAndSpacing = false),
                titleMedium = applyStoryTextStyle(typography.titleMedium, spec, lineMultiplier = null, applyWeightAndSpacing = false),
                titleSmall = applyStoryTextStyle(typography.titleSmall, spec, lineMultiplier = null, applyWeightAndSpacing = false)
            ),
            content = content
        )
    }
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
