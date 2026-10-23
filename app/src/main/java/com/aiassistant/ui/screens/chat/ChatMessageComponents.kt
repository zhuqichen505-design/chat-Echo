@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.aiassistant.ui.screens.chat

import android.net.Uri
import android.graphics.BitmapFactory
import com.aiassistant.ui.theme.rememberEchoSemanticColors
import com.aiassistant.ui.components.ImageCropEditDialog
import com.aiassistant.ui.components.CropShapeMode
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import com.aiassistant.domain.model.ToolCallRecord
import com.aiassistant.domain.model.QueuedMessage
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import com.aiassistant.ui.components.echoShapeClick
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiassistant.utils.TimelineMemoryHelper
import com.aiassistant.utils.TimelineReconcileResult
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.EchoModelMarkStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalUriHandler
import com.aiassistant.ui.components.EchoDoubleArcRing
import com.aiassistant.ui.components.EchoPulseRing
import com.aiassistant.ui.components.EchoTextToolbar
import com.aiassistant.ui.components.EchoStreamingIndicator
import com.aiassistant.ui.components.EchoModelMark
import com.aiassistant.ui.components.ModelMarkCompletionMotion
import com.aiassistant.ui.components.modelMarkColorFor
import com.aiassistant.ui.components.StreamingAnimationStyle
import com.aiassistant.ui.components.EchoTextToolbarHost
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Brush
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.aiassistant.AiAssistantApp
import com.aiassistant.R
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.Attachment
import com.aiassistant.domain.model.ChatModelOption
import com.aiassistant.domain.model.ConversationContextUsage
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.PromptTemplate
import com.aiassistant.ui.components.MarkdownText
import com.aiassistant.ui.components.markdown.MarkdownInlineParser
import com.aiassistant.ui.components.markdown.MarkdownColorUtils.decodeHtmlEntities
import com.aiassistant.ui.components.parseInlineMarkdown
import com.aiassistant.ui.components.SideAnchorItem
import com.aiassistant.ui.components.SideAnchorNavigator
import com.aiassistant.ui.components.TransientLazyListScrollbar
import com.aiassistant.ui.components.EchoPillSlider
import androidx.compose.runtime.CompositionLocalProvider
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.rememberSmoothReorderState
import com.aiassistant.ui.components.reorderItem
import com.aiassistant.ui.components.reorderDragHandle
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoSegmentedButtonBorder
import com.aiassistant.ui.components.echoSegmentedButtonColors
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoHazeSource
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.rememberReadableBackdropColors
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.components.rememberLazyListControlsVisible
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.BackgroundImageManager
import com.aiassistant.utils.FileUtils
import com.aiassistant.utils.EchoStoryReadingStyle
import com.aiassistant.utils.RoleplaySmartAnalyzer
import com.aiassistant.utils.RoleplaySmartParser
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.NarrativeMode
import com.aiassistant.domain.model.PlotAction
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoPrimaryButton
import com.aiassistant.ui.components.EchoGlassButton
import com.aiassistant.ui.screens.roleplay.ConflictAction
import com.aiassistant.ui.theme.EchoTokens
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.aiassistant.ui.theme.EchoThinkingColors


internal data class CapsuleTextViewport(val height: Int, val textTop: Int)

/** Expansion changes only clipping, never the text's width, wrapping or origin. */
internal fun capsuleTextViewport(headerHeight: Int, lineHeight: Int, textHeight: Int, expanded: Boolean): CapsuleTextViewport {
    val top = ((headerHeight - lineHeight) / 2).coerceAtLeast(0)
    return CapsuleTextViewport(if (expanded) maxOf(headerHeight, textHeight + top) else headerHeight, top)
}

fun formatThinkingCapsuleText(
    template: String,
    modelName: String,
    isThinkingActive: Boolean,
    responseTimeMs: Long,
    thinkingTokens: Int,
    totalTokens: Int
): String {
    val model = modelName.ifBlank { "AI" }
    val status = if (isThinkingActive) "思考中..." else "思考过程"
    val time = if (responseTimeMs > 0) formatTime(responseTimeMs) else ""
    val tokens = when {
        thinkingTokens > 0 -> "${thinkingTokens} token"
        totalTokens > 0 -> "${totalTokens} token"
        else -> ""
    }

    var result = template
        .replace("{model}", model)
        .replace("{status}", status)
        .replace("{time}", time)
        .replace("{tokens}", tokens)
        .replace("{token}", tokens)

    result = result.replace(Regex("\\s+"), " ").trim()
    return if (result.isBlank()) "$model $status" else result
}

fun formatNonThinkingCapsuleText(
    modelName: String,
    responseTimeMs: Long,
    tokenCount: Int,
    content: String = ""
): String {
    val model = modelName.ifBlank { "AI" }
    val effectiveTokens = if (tokenCount > 0) tokenCount else AiRepository.estimateTokenCount(content)
    val seconds = (responseTimeMs / 1000).toInt().coerceAtLeast(1)
    return when {
        responseTimeMs > 0 && effectiveTokens > 0 ->
            "${model}用${seconds}秒吃掉了你${effectiveTokens}token"
        responseTimeMs > 0 ->
            "${model}用${seconds}秒回复了你"
        effectiveTokens > 0 ->
            "${model}吃掉了你${effectiveTokens}token"
        else -> model
    }
}

fun parseQuotedMessage(content: String): ParsedQuotedMessage? {
    val trimmed = content.trimStart()
    if (!trimmed.startsWith(">")) return null
    val lines = content.lines()
    val quoteLines = mutableListOf<String>()
    var splitIndex = -1

    for (i in lines.indices) {
        val line = lines[i]
        if (line.startsWith(">")) {
            quoteLines.add(line.removePrefix(">").trimStart())
        } else if (line.isBlank() && quoteLines.isNotEmpty() && splitIndex == -1) {
            splitIndex = i + 1
            break
        } else {
            splitIndex = i
            break
        }
    }

    if (quoteLines.isEmpty()) return null
    val quoteText = quoteLines.joinToString("\n").trim()
    if (quoteText.isBlank()) return null

    val rawRemaining = if (splitIndex in lines.indices) {
        lines.subList(splitIndex, lines.size).joinToString("\n").trim()
    } else ""

    val replyText = rawRemaining
        .removePrefix("针对以上内容：\n")
        .removePrefix("针对以上内容：")
        .trim()

    return ParsedQuotedMessage(quoteText = quoteText, replyText = replyText)
}


internal fun buildDisplayMessages(
    messages: List<Message>,
    selections: Map<String, Int>
): List<DisplayMessageItem> {
    // 过滤掉无内容、无思考、无附件、无工具调用的无效空白异常消息，杜绝幽灵气泡残留
    val validMessages = messages.filter { message ->
        message.content.isNotBlank() ||
        !message.thinkingContent.isNullOrBlank() ||
        !message.attachments.isNullOrBlank() ||
        !message.toolCalls.isNullOrBlank()
    }
    val groups = validMessages
        .filter { !it.variantGroupId.isNullOrBlank() }
        .groupBy { it.variantGroupId!! }
    val consumedGroups = mutableSetOf<String>()
    val result = mutableListOf<DisplayMessageItem>()

    validMessages.forEach { message ->
        val groupId = message.variantGroupId
        if (groupId.isNullOrBlank()) {
            result += DisplayMessageItem(message = message, groupId = null)
            return@forEach
        }
        if (!consumedGroups.add(groupId)) return@forEach

        val rawVariants = groups[groupId].orEmpty()
        val distinctIndices = rawVariants.map { it.variantIndex }.distinct()
        val variants = if (rawVariants.size > 1 && distinctIndices.size < rawVariants.size) {
            rawVariants.sortedWith(compareBy<Message> { it.variantIndex }.thenBy { it.createdAt }.thenBy { it.id })
                .mapIndexed { idx, msg ->
                    if (msg.variantIndex != idx + 1) msg.copy(variantIndex = idx + 1) else msg
                }
        } else {
            rawVariants.sortedBy { it.variantIndex }
        }
        val indices = variants.map { it.variantIndex }.distinct().sorted()
        val selectedIndex = selections[groupId]
            ?.takeIf { it in indices }
            ?: indices.lastOrNull()
            ?: 1
        val selectedMessage = variants.lastOrNull { it.variantIndex == selectedIndex }
            ?: variants.last()
        result += DisplayMessageItem(
            message = selectedMessage,
            groupId = groupId,
            variantInfo = if (indices.size > 1) {
                VariantInfo(
                    groupId = groupId,
                    currentIndex = selectedIndex,
                    total = indices.size,
                    availableIndices = indices
                )
            } else null
        )
    }

    return result
}

internal fun pairedVariantGroupId(groupId: String): String? {
    return when {
        groupId.endsWith("_user") -> groupId.removeSuffix("_user") + "_assistant"
        groupId.endsWith("_assistant") -> groupId.removeSuffix("_assistant") + "_user"
        else -> null
    }
}

/**
 * 判定某条显示消息项是否可作为流式分支气泡的内联挂载点（挂载点 = 与流式分支配对的 user 消息项）。
 *
 * 约束：streamingBranchGroupId 为 "reply_*" 等非成对命名时 pairedVariantGroupId 返回 null，
 * 相等判定必须先确认 pairedId 非空——否则会与所有未分组消息项的 null groupId 相等，
 * 导致每个未分组消息后都多渲染一份相同的流式气泡（多份回复同时流式输出 + 位置错乱）。
 */
internal fun isStreamingBranchHostItem(
    itemGroupId: String?,
    streamingBranchGroupId: String,
    messageId: Long
): Boolean {
    val pairedId = pairedVariantGroupId(streamingBranchGroupId)
    return (pairedId != null && itemGroupId == pairedId) ||
        streamingBranchGroupId.startsWith("turn_${messageId}_")
}

/**
 * 生成锚点宿主判定（v2.6.5）：某条显示消息项是否为当前生成轮次的锚点用户消息项。
 * 流式回复气泡钉在触发本轮生成的用户消息之后——该锚点不随同一位置其他回复
 * （错误占位、旧 variant）的删除而移动，杜绝删除后流式回复跳到上方/下方变成额外回复。
 * id 直接命中未分组的用户消息；分组场景（编辑重发）按 user 分组 id 命中，
 * 保证锚点消息不在当前选中 variant 上时挂载位置依然正确。
 */
internal fun isGeneratingAnchorHostItem(
    itemGroupId: String?,
    itemMessageId: Long,
    anchorUserMessageId: Long?,
    anchorUserGroupId: String?
): Boolean {
    if (anchorUserMessageId == null || anchorUserMessageId <= 0L) return false
    if (itemMessageId == anchorUserMessageId) return true
    return anchorUserGroupId != null && itemGroupId == anchorUserGroupId
}

internal fun isErrorMessage(content: String): Boolean {
    val trimmed = content.trim()
    if (GenerationUiStateRules.isStoppedContent(trimmed)) return false
    return trimmed.startsWith("请求失败") ||
           trimmed.startsWith("[请求失败]") ||
           trimmed.startsWith("【请求失败】") ||
           trimmed.startsWith("Error:", ignoreCase = true) ||
           trimmed.startsWith("API错误") ||
           trimmed.startsWith("[API错误]") ||
           trimmed.startsWith("【API错误】") ||
           trimmed.startsWith("所有 API Key") ||
           trimmed.startsWith("连接失败") ||
           trimmed.startsWith("连接超时") ||
           trimmed.startsWith("网络异常") ||
           trimmed.startsWith("网络波动") ||
           trimmed.contains("[输出已被中断") ||
           trimmed.contains("【连接异常信息记录】") ||
           trimmed.contains("failed to stream request: empty response detected", ignoreCase = true) ||
           trimmed.contains("(思考已完成，但模型未输出正文内容") ||
           (trimmed.contains("回复已停止") && trimmed.contains("报错详情"))
}

// v2.7.3 流畅度：自定义头像（角色/会话专属，时间戳命名不可变文件）解码缓存，预算 8MB——
// 原先消息列表每行 ChatAvatar 各自 openInputStream 全量解码同一张角色头像
private val chatCustomAvatarCache = object : android.util.LruCache<String, android.graphics.Bitmap>(8 * 1024) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.byteCount / 1024
}

@Composable
internal fun MessageBubble(
    message: Message,
    isStory: Boolean = false,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    readableBackdrop: Color = Color.Unspecified,
    isGenerating: Boolean = false,
    assistantAvatarRevision: Int = 0,
    assistantApiConfigId: Long? = null,
    assistantModelName: String = "AI",
    reconnectStatus: String? = null,
    /** 实时 Key 尝试报错明细（仅流式气泡传入）：生成过程中直接展示每次连接失败的具体原因，无需手动暂停 */
    liveKeyErrors: List<String> = emptyList(),
    variantInfo: VariantInfo? = null,
    onVariantSelected: (String, Int) -> Unit = { _, _ -> },
    translatingThinking: Boolean = false,
    onTranslateThinking: ((Message) -> Unit)? = null,
    onCopy: () -> Unit,
    onCopyThinking: () -> Unit,
    onQuote: (() -> Unit)? = null,
    onBranch: (() -> Unit)? = null,
    isCreatingBranch: Boolean = false,
    branchEnabled: Boolean = true,
    onRegenerate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    /** 仅修改消息内容（不重新发送/不重新生成），用户消息的“仅编辑”入口 */
    onEditInPlace: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    customAvatarUri: String? = null,
    onTogglePin: ((Message) -> Unit)? = null,
    onToggleExclude: ((Message) -> Unit)? = null,
    thinkingEffort: String? = null,
    /** P0-3 落定信号：生成结束落库后的持久化气泡置 true，首次组合播放一次光标淡出+落定脉冲 */
    settleSignal: Boolean = false,
    latestAssistantIdentity: Boolean = false
) {
    val isUser = message.role == "user"
    val personalizationSettings = rememberChatPersonalizationSettings()
    val readingStyle = personalizationSettings.storyReadingStyle.takeIf { isStory || personalizationSettings.applyReadingStyleToChats }
    val readingFont = personalizationSettings.storyFont.takeIf { isStory || personalizationSettings.applyReadingFontToChats }
    val immersiveUserMessage = isUser && readingStyle == EchoStoryReadingStyle.IMMERSIVE
    val showThinkingBubbles = personalizationSettings.showThinkingBubbles
    val resolvedReadableBackdrop = readableBackdrop.takeOrElse {
        MaterialTheme.colorScheme.background
    }
    val glass = echoGlassPalette()
    val userBubbleTint = glass.userBubble
    val bubbleColor = if (isUser) glass.userBubble else glass.assistantBubble
    val textBackground = if (isUser && !immersiveUserMessage) bubbleColor else resolvedReadableBackdrop
    val textColor = readableTextColorFor(
        background = textBackground,
        fallbackSurface = resolvedReadableBackdrop
    )
    val bubbleShape = if (isUser) {
        RoundedCornerShape(18.dp, 6.dp, 18.dp, 18.dp)
    } else {
        RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp)
    }
    val hasThinkingContent = !message.thinkingContent.isNullOrBlank()
    val hasThinking = hasThinkingContent || message.thinkingTokens > 0
    var showThinking by remember(message.id) {
        mutableStateOf(isGenerating && hasThinkingContent && message.content.isBlank())
    }
    val hasTranslation = !message.translatedThinking.isNullOrBlank()
    var showTranslated by remember(message.id, message.translatedThinking) {
        mutableStateOf(hasTranslation)
    }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // 解析附件
    val attachments = remember(message.attachments) {
        if (message.attachments.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val gson = com.google.gson.Gson()
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    List::class.java, Attachment::class.java
                ).type
                gson.fromJson<List<Attachment>>(message.attachments, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val toolCallsList = remember(message.toolCalls) {
        if (message.toolCalls.isNullOrBlank()) {
            emptyList<ToolCallRecord>()
        } else {
            runCatching {
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    List::class.java, ToolCallRecord::class.java
                ).type
                com.google.gson.Gson().fromJson<List<ToolCallRecord>>(message.toolCalls, type).orEmpty()
            }.getOrDefault(emptyList())
        }
    }
    var activeCitation by remember(message.id) { mutableStateOf<CitationInfo?>(null) }
    val searchCitations = remember(toolCallsList, isUser) {
        if (!isUser) extractCitationsFromContent("", toolCallsList) else emptyList()
    }
    val citations = remember(message.content, searchCitations, isUser) {
        if (!isUser) extractCitationSources(message.content, searchCitations) else emptyList()
    }

    // ===== 动效轮：生成状态机（P0-4）与落定/光标生命周期（P0-3）=====
    // v2.7.3 流畅度：isErrorMessage 对全文做 15+ 次前缀/包含扫描，原先在流式期间每帧于
    // 三处重复执行；这里记忆化一次供全气泡复用
    val contentIsErrorMessage = remember(message.content, isUser) { !isUser && isErrorMessage(message.content) }
    val generationState = rememberGenerationUiState(
        isGenerating = isGenerating,
        content = message.content,
        hasThinking = hasThinking,
        reconnectStatus = reconnectStatus,
        contentIsError = contentIsErrorMessage
    )
    val displayContent = remember(message.content, isUser, generationState) {
        if (!isUser && generationState == GenerationUiState.Cancelled) {
            GenerationUiStateRules.stoppedReplyDisplayBody(message.content)
        } else {
            message.content
        }
    }
    val stoppedErrorReport = remember(message.content, generationState) {
        if (generationState == GenerationUiState.Cancelled) GenerationUiStateRules.stoppedReplyErrorReport(message.content) else null
    }
    // 光标生命周期：流式期间可见；生成结束后保留 300ms 供淡出，再摘除；
    // settleSignal（审核 A3）：常规路径下流式气泡随 isGenerating=false 同帧卸载（ViewModel 同帧清空
    // currentResponse），落库后的持久化气泡以 settleSignal=true 入场，播放同一段收尾序列
    val reducedMotion = com.aiassistant.ui.theme.rememberReducedMotion()
    val settleEligible = settleSignal && generationState != GenerationUiState.Failed
    var modelMarkCompleted by remember(message.id) { mutableStateOf(false) }
    // The message owns consumption: changing the avatar/thinking layout cannot replay a completed identity.
    val modelMarkCompletionOwner = remember(message.id) { ModelMarkCompletionMotion() }
    var previousModelMarkGenerationState by remember(message.id) { mutableStateOf<GenerationUiState?>(null) }
    val modelMarkCompletionNow = remember(previousModelMarkGenerationState, generationState, message.content, settleEligible) {
        GenerationUiStateRules.modelMarkCompletionObserved(
            previousModelMarkGenerationState, generationState, message.content, settleEligible)
    }
    LaunchedEffect(generationState, settleEligible, message.id, message.content) {
        if (GenerationUiStateRules.animateModelMark(generationState)) modelMarkCompleted = false
        else if (modelMarkCompletionNow) modelMarkCompleted = true
        // Idle 可先于正文落库到达；保留已经观察到的输出前态，等正文到达后再判断一次。
        if (generationState != GenerationUiState.Idle || message.content.isNotBlank()) {
            previousModelMarkGenerationState = generationState
        }
    }
    val modelMarkState = GenerationUiStateRules.modelMarkState(generationState,
        completed = (modelMarkCompleted || modelMarkCompletionNow) &&
            remember(message.content) { GenerationUiStateRules.modelMarkHasSuccessfulBody(message.content) })
    val modelMarkIdleEffects = GenerationUiStateRules.modelMarkAllowsIdle(
        generationState, !isUser && latestAssistantIdentity, message.content)
    LaunchedEffect(modelMarkState, modelMarkCompleted, modelMarkCompletionNow, personalizationSettings.showModelAvatar) {
        if (personalizationSettings.showModelAvatar) {
            // Keep hidden identities up to date, so restoring a mark never celebrates an unseen old completion.
            modelMarkCompletionOwner.update(modelMarkState,
                if (modelMarkCompleted || modelMarkCompletionNow) message.id else 0L)
        }
    }
    var cursorAlive by remember { mutableStateOf(isGenerating || settleEligible) }
    val wasGenerating = remember { mutableStateOf(isGenerating || settleEligible) }
    // P0-3② 落定脉冲：scale 1.0→0.995→1.0（200ms，graphicsLayer 绘制层，一次性）
    val settleScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(isGenerating, settleEligible) {
        if (isGenerating) {
            cursorAlive = true
        } else {
            val wasGeneratingBefore = wasGenerating.value
            if (wasGeneratingBefore && message.content.isNotBlank() &&
                generationState != GenerationUiState.Failed && !reducedMotion
            ) {
                runCatching {
                    settleScale.animateTo(
                        0.995f,
                        com.aiassistant.ui.theme.EchoMotion.tweenSpec<Float>(com.aiassistant.ui.theme.EchoMotion.Duration.fast)
                    )
                    settleScale.animateTo(
                        1f,
                        com.aiassistant.ui.theme.EchoMotion.tweenSpec<Float>(com.aiassistant.ui.theme.EchoMotion.Duration.fast)
                    )
                }
            }
            kotlinx.coroutines.delay(com.aiassistant.ui.theme.EchoMotion.Typewriter.cursorFadeMs.toLong())
            cursorAlive = false
        }
        wasGenerating.value = isGenerating
    }
    // 光标/脉冲环同源配色：思考模型取思考档位色，非思考模型用 primary（P0-1②/P0-2②）
    val generationAccentColor = if (hasThinking) {
        com.aiassistant.ui.theme.EchoThinkingColors.forEffort(thinkingEffort)
    } else {
        MaterialTheme.colorScheme.primary
    }

    // 连接等待计时（气泡级）：以"尝试阶段"为计时窗口——生成发起或重试状态切换（换 Key/重连，
    // 表现为 reconnectStatus 文案变化）时重新计时；计时器常驻气泡组合内，
    // 不会因状态块离场而意外归零后"消失又重现"
    var connectElapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(isGenerating, reconnectStatus) {
        if (!isGenerating) {
            connectElapsedSec = 0
            return@LaunchedEffect
        }
        connectElapsedSec = 0
        val startedAt = System.currentTimeMillis()
        while (isActive) {
            kotlinx.coroutines.delay(1000)
            connectElapsedSec = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
        }
    }

    activeCitation?.let { citation ->
        CitationDetailDialog(
            citation = citation,
            onDismiss = { activeCitation = null }
        )
    }

    @Composable
    fun MessageContent(contentColor: Color) {
        // v2.7.7 需求 3：无正文生成期（连接/思考）内容区不渲染空白占位——动画已上移至
        // 胶囊下方与提醒同行，此处的 8dp 纵向空距只会拉大动画与底部时间戳的距离
        if (!isUser && displayContent.isBlank() &&
            (isGenerating || generationState == GenerationUiState.Cancelled)) {
            return
        }
        StoryReadingTypography(readingStyle, readingFont) {
        SelectionContainer {
            Column(
                modifier = if (immersiveUserMessage) {
                    Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
                } else if (isUser) {
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                }
            ) {
                if (displayContent.isNotBlank()) {
                    if (isUser) {
                        val parsedQuote = remember(message.content) { parseQuotedMessage(message.content) }
                        if (parsedQuote != null) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                var isQuoteExpanded by remember { mutableStateOf(false) }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = contentColor.copy(alpha = 0.09f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clickable { isQuoteExpanded = !isQuoteExpanded }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .fillMaxHeight()
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                                    ),
                                                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                                )
                                        )
                                        Column(
                                            modifier = Modifier
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                                .fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FormatQuote,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "引用内容",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = parseInlineMarkdown(parsedQuote.quoteText),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = contentColor.copy(alpha = 0.82f),
                                                maxLines = if (isQuoteExpanded) Int.MAX_VALUE else 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                if (parsedQuote.replyText.isNotBlank()) {
                                    Text(
                                        text = parseInlineMarkdown(parsedQuote.replyText),
                                        color = contentColor,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = parseInlineMarkdown(message.content),
                                color = contentColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        MarkdownText(
                            content = displayContent,
                            color = contentColor,
                            onCitationClick = { id ->
                                activeCitation = citationForIndex(citations, id)
                            },
                            streaming = isGenerating || cursorAlive,
                            cursor = if (cursorAlive) generationAccentColor else null,
                            cursorFading = !isGenerating,
                            streamingAnimationStyle = personalizationSettings.streamingAnimationStyle
                        )
                        if (citations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            CitationsCardsRow(
                                citations = citations,
                                onCitationClick = { activeCitation = it }
                            )
                        }
                    }
                } else if (!isGenerating && !hasThinking && attachments.isEmpty() && (isUser || showThinkingBubbles)) {
                    Text(
                        text = "空消息",
                        color = contentColor.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Markdown owns the single output marker; do not add a second waiting animation.
            }
        }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (message.isExcluded) 0.52f else 1f
            }
    ) {
        val bubbleMaxWidth = if (immersiveUserMessage) maxWidth.coerceAtMost(720.dp)
            else (maxWidth - 52.dp).coerceAtLeast(160.dp).coerceAtMost(360.dp)

        if (isUser) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (immersiveUserMessage) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.widthIn(max = bubbleMaxWidth).then(
                        if (immersiveUserMessage) Modifier.fillMaxWidth().padding(start = 22.dp, top = 8.dp, bottom = 8.dp)
                        else Modifier
                    ),
                    horizontalAlignment = if (immersiveUserMessage) Alignment.Start else Alignment.End
                ) {
                    if (attachments.isNotEmpty()) {
                        AttachmentGroupBubble(
                            attachments = attachments,
                            modifier = Modifier
                                .widthIn(max = bubbleMaxWidth)
                                .padding(bottom = if (message.content.isNotBlank() || isGenerating) 8.dp else 0.dp)
                        )
                    }

                    if (message.content.isNotBlank() || isGenerating) {
                        if (immersiveUserMessage) {
                            val quoteLineColor = MaterialTheme.colorScheme.primary
                            Box(Modifier.fillMaxWidth().drawBehind {
                                val stroke = 1.dp.toPx()
                                drawLine(quoteLineColor, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), stroke)
                            }) {
                                MessageContent(textColor)
                            }
                        } else {
                        Surface(
                            modifier = Modifier,
                            color = bubbleColor,
                            contentColor = textColor,
                            shape = bubbleShape,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = BorderStroke(
                                width = 0.8.dp,
                                color = glass.outlineSelected.copy(alpha = 0.35f)
                            )
                        ) {
                            MessageContent(textColor)
                        }
                        }
                    }

                    MessageFooter(
                        isUser = true,
                        message = message,
                        backdropContentColor = readableTextColorFor(resolvedReadableBackdrop),
                        variantInfo = variantInfo,
                        onVariantSelected = onVariantSelected,
                        onCopy = onCopy,
                        onQuote = onQuote,
                        onBranch = null,
                        onRegenerate = onRegenerate,
                        onEdit = onEdit,
                        onEditInPlace = onEditInPlace,
                        onDelete = onDelete,
                        onTogglePin = onTogglePin?.let { cb -> { cb(message) } },
                        onToggleExclude = onToggleExclude?.let { cb -> { cb(message) } },
                        endAlignMetadata = !immersiveUserMessage,
                        modifier = Modifier
                            .widthIn(max = bubbleMaxWidth)
                            .fillMaxWidth()
                            .then(if (immersiveUserMessage) Modifier.padding(start = 14.dp) else Modifier)
                    )
                }

                if (!immersiveUserMessage) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ChatAvatar(isUser = true)
                }
            }
        } else {
            // 模型回复：头像与身份置顶对齐，正文与思考全宽居中展开，左右对称无空白浪费
            // v2.7.4 需求 1：外层仅保留左侧 4dp 内边距（右侧 0）——状态胶囊右缘需与底部
            // 输入气泡右缘（消息列表 contentPadding 已统一为 12dp）精确对齐
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 2.dp, end = 0.dp, bottom = 2.dp)
                    .graphicsLayer {
                        val s = settleScale.value
                        scaleX = s
                        scaleY = s
                    },
                horizontalAlignment = Alignment.Start
            ) {
                val thinkingBubbleColor = glass.controlSelected
                val thinkingHeaderColor = MaterialTheme.colorScheme.onPrimaryContainer
                val thinkingContentColor = glass.textPrimary
                var isErrorReportExpanded by remember(message.id) { mutableStateOf(true) }

                // 头像与胶囊顶对齐同行，连接等待提示在行下方独立渲染：
                // 提示出现或胶囊因文字变高时只向下延展，头像与胶囊的相对位置固定不漂移
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    val isMessageContentError = contentIsErrorMessage
                    val isConnecting = isGenerating && message.content.isBlank() && !hasThinking
                    val isThinkingActive = isGenerating && hasThinking && message.content.isBlank()
                    // 失败信号仅在连接阶段（尚无正文）生效：Key 重试失败后恢复成功时，
                    // 残留的 reconnectStatus 不得让已开始流式输出的气泡继续显示失败态
                    val isConnectionFailed = isMessageContentError || generationState == GenerationUiState.Failed
                    val capsuleText = remember(
                        assistantModelName,
                        hasThinking,
                        isGenerating,
                        isConnecting,
                        isThinkingActive,
                        isConnectionFailed,
                        isMessageContentError,
                        generationState,
                        message.content,
                        message.responseTime,
                        message.thinkingTokens,
                        message.tokenCount,
                        personalizationSettings.thinkingCapsuleTemplate,
                        reconnectStatus
                    ) {
                        if (isMessageContentError) {
                            // 已落库错误消息：保持简洁标题，具体原因由下方红框报告承载
                            "模型连接失败"
                        } else if (isConnectionFailed) {
                            // 生成中连接失败：直接展示具体原因（Key 报错/重试状态），不再隐藏为无原因标题
                            reconnectStatus?.takeIf { it.isNotBlank() } ?: "模型连接失败"
                        } else {
                            val rawModel = assistantModelName.ifBlank { "AI" }
                            val model = rawModel.displayModelShortName()
                            when {
                                generationState == GenerationUiState.Cancelled -> "生成已停止"
                                generationState == GenerationUiState.ChoosingDirection -> reconnectStatus ?: "等待选择回复方向"
                                isConnecting -> {
                                    if (!reconnectStatus.isNullOrBlank()) {
                                        reconnectStatus
                                    } else {
                                        personalizationSettings.connectingTextTemplate.replace("{model}", model).ifBlank { "正在连接 $model..." }
                                    }
                                }
                                isThinkingActive -> personalizationSettings.thinkingTextTemplate.replace("{model}", model).ifBlank { "$model 正在思考中..." }
                                hasThinking -> formatThinkingCapsuleText(
                                    template = personalizationSettings.thinkingCapsuleTemplate.ifBlank { "{model} {status} {time} {tokens}" },
                                    modelName = model,
                                    isThinkingActive = isGenerating && message.content.isBlank(),
                                    responseTimeMs = message.responseTime,
                                    thinkingTokens = message.thinkingTokens,
                                    totalTokens = message.tokenCount
                                )
                                isGenerating -> personalizationSettings.thinkingTextTemplate.replace("{model}", model).ifBlank { "$model 正在思考回复中..." }
                                else -> formatNonThinkingCapsuleText(
                                    modelName = model,
                                    responseTimeMs = message.responseTime,
                                    tokenCount = message.tokenCount,
                                    content = message.content
                                )
                            }
                        }
                    }

                    val isStatusError = isConnectionFailed ||
                        ((generationState == GenerationUiState.Connecting || generationState == GenerationUiState.Reconnecting) &&
                            !reconnectStatus.isNullOrBlank() && GenerationUiStateRules.isErrorText(capsuleText))
                    // 连接/重连等待期（尚无正文）携带重试原因：默认多行展示，保证重试原因完整可读（不横向滚动截断）
                    val isWaitingWithReason = isConnecting && !reconnectStatus.isNullOrBlank()
                    // v2.6.8 需求 6：展开态改为「默认策略 + 用户显式覆盖」两级。
                    // 默认策略：报错态或等待期携带重试原因 → 多行；其余 → 单行。
                    // 点击胶囊后由 statusExpandOverride 接管，折叠必然生效——此前 maxLinesCount 中
                    // isWaitingWithReason/isStatusError 优先级高于 isStatusExpanded，点击「收起」不改变行数，
                    // 表现为「胶囊无法正确收缩」。
                    var statusExpandOverride by remember(message.id) { mutableStateOf<Boolean?>(null) }
                    val isStatusExpanded = statusExpandOverride ?: (isStatusError || isWaitingWithReason)
                    // 状态文本包含多行、超长详细报错/URL信息或报错状态时提供展开功能，无多余内容不给展开键
                    val hasDetailedExpandableContent = isMessageContentError || (!hasThinkingContent && (capsuleText.contains("\n") || capsuleText.length > 36 || isStatusError))
                    // 携带重试原因的等待胶囊即使文案较短也处于多行态，必须给出收起/展开键
                    val showStatusToggle = hasDetailedExpandableContent || isWaitingWithReason
                    val canExpandStatus = showStatusToggle || isStatusExpanded
                    val capsuleShape = RoundedCornerShape(16.dp)
                    val maxLinesCount = when {
                        isStatusError -> 4
                        isWaitingWithReason -> 3
                        else -> 16
                    }
                    val capsuleTextStyle = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    )
                    val textMeasurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val lineHeightPx = textMeasurer.measure("Ag", style = capsuleTextStyle, maxLines = 1).size.height
                    val headerHeightPx = maxOf(lineHeightPx, with(density) { 20.dp.roundToPx() })
                    val headerHeight = with(density) { headerHeightPx.toDp() }
                    // 关闭思考展示时不保留空头像行；连接、重试和选方向仍有独立提示。
                    if (showThinkingBubbles) {
                    // 头像与胶囊顶对齐同行：胶囊因文字变高时向下延展，头像相对胶囊位置固定
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (personalizationSettings.showModelAvatar) {
                            ChatAvatar(
                                isUser = false,
                                avatarRevision = assistantAvatarRevision,
                                apiConfigId = assistantApiConfigId,
                                customAvatarUri = customAvatarUri,
                                modifier = Modifier.offset(y = (-1).dp)
                            )
                        } else {
                            EchoModelMark(
                                color = modelMarkColorFor(MaterialTheme.colorScheme.primary, resolvedReadableBackdrop),
                                style = personalizationSettings.modelMarkStyle,
                                animated = GenerationUiStateRules.animateModelMark(generationState),
                                state = modelMarkState,
                                completionEvent = if (modelMarkCompleted || modelMarkCompletionNow) message.id else 0L,
                                completionOwner = modelMarkCompletionOwner,
                                idleEffects = modelMarkIdleEffects,
                                // Keep the bot and its upper-right status symbol readable beside
                                // the taller thinking/status capsule.
                                modifier = Modifier.padding(top = 3.dp).size(34.dp),
                                reducedMotion = reducedMotion,
                                description = "模型标识"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                        modifier = Modifier
                            // v2.7.7 需求 4：胶囊宽度随实际内容自适应（weight fill=false）——文字未超过
                            // 显示范围时收缩到内容宽度；文字超限时仍占满剩余宽度（横向滚动/换行逻辑
                            // 不变）。右缩 44dp（头像36+间距8）：撑满时右缘仍与用户输入气泡右缘对齐
                            .weight(1f, fill = false)
                            .padding(end = 44.dp)
                            .defaultMinSize(minHeight = 34.dp)
                            .animateContentSize(com.aiassistant.ui.theme.EchoMotion.Spring.gentle())
                            .clip(capsuleShape)
                            .then(
                                if (isMessageContentError) {
                                    Modifier.echoShapeClick(shape = capsuleShape) {
                                        isErrorReportExpanded = !isErrorReportExpanded
                                        statusExpandOverride = isErrorReportExpanded
                                    }
                                } else if (hasThinking && hasThinkingContent) {
                                    Modifier.echoShapeClick(shape = capsuleShape) {
                                        showThinking = !showThinking
                                    }
                                } else if (canExpandStatus) {
                                    Modifier.echoShapeClick(shape = capsuleShape) {
                                        // v2.6.8 需求 6：折叠/展开写回显式覆盖，确保「收起」一定把胶囊收成单行
                                        statusExpandOverride = !isStatusExpanded
                                    }
                                } else Modifier
                            ),
                        color = if (isStatusError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f) else thinkingBubbleColor,
                        contentColor = if (isStatusError) MaterialTheme.colorScheme.error else thinkingHeaderColor,
                        shape = capsuleShape,
                        border = BorderStroke(
                            1.dp,
                            if (isStatusError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else glass.outlineSelected.copy(alpha = 0.72f)
                        )
                    ) {
                        // Both states share the SAME wrapped text layout and first-line slot.
                        // Expansion only reveals the portion below it; alignment never changes.
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier.width(16.dp).height(headerHeight),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isStatusError) {
                                    Icon(
                                        Icons.Default.WarningAmber,
                                        contentDescription = "连接报错",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                } else if (generationState == GenerationUiState.Reconnecting) {
                                    // P0-1④ 重连：双弧追逐旋转（error 语义色）
                                    EchoDoubleArcRing(
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else if (generationState == GenerationUiState.Connecting ||
                                    generationState == GenerationUiState.Thinking ||
                                    generationState == GenerationUiState.Streaming
                                ) {
                                    // v2.7.2 需求 4：连接→思考→流式全程圆环动效（原先正文开始后回落为静态 Psychology 图标），
                                    // 阶段切换时 primary 平滑过渡到思考档位色
                                    val ringColor by animateColorAsState(
                                        targetValue = if (generationState == GenerationUiState.Thinking ||
                                            generationState == GenerationUiState.Streaming
                                        ) {
                                             generationAccentColor
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec(
                                            com.aiassistant.ui.theme.EchoMotion.Duration.fast
                                        ),
                                        label = "pulseRingColor"
                                    )
                                    EchoPulseRing(
                                        color = ringColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    // v2.7.2 需求 4：回复完毕后以静态圆环落定（替换原 Psychology 图标），
                                    // 颜色与生成期圆环同源（思考模型取思考档位色，否则 primary），视觉连续
                                    EchoPulseRing(
                                        color = if (hasThinking) generationAccentColor else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                        animated = false
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    // v2.7.8 需求 3：文本区 fill=false——v2.7.7 只改了外层胶囊
                                    // weight(fill=false)，但本区 weight(1f)（fill 默认 true）仍把
                                    // 文本区撑满剩余宽度，胶囊因此永远收缩不到内容宽度；
                                    // 文字超限时本区仍顶满剩余宽度（横向滚动/换行逻辑不变）
                                    .weight(1f, fill = false),
                                contentAlignment = Alignment.TopStart
                            ) {
                                // P0-1② 状态文案交叉淡换：以状态枚举为 key（150ms 淡出+淡入），
                                // 同状态下文案变化不触发动画（避免逐字符抖动）
                                // A5：reduced motion 时 snap 硬切（保留状态变化本身），正常时 150ms 交叉淡换
                                val capsuleSpec = if (reducedMotion) {
                                    androidx.compose.animation.core.snap<Float>()
                                } else {
                                    com.aiassistant.ui.theme.EchoMotion.tweenSpec<Float>(com.aiassistant.ui.theme.EchoMotion.Duration.fast)
                                }
                                AnimatedContent(
                                    targetState = capsuleText to isStatusError,
                                    transitionSpec = {
                                        // 尺寸过渡只由外层 Surface.animateContentSize 单一驱动：
                                        // 内层若用默认 SizeTransform 会与外层弹簧叠加，状态切换时
                                        // 胶囊先被出入场内容的最大值撑大再回缩，表现为突然变大又变小
                                        (fadeIn(capsuleSpec) togetherWith fadeOut(capsuleSpec))
                                            .using(SizeTransform { _, _ -> snap() })
                                    },
                                    contentAlignment = Alignment.TopStart,
                                    label = "capsulePhase"
                                ) { (phaseText, phaseError) ->
                                    Layout(
                                        modifier = Modifier.clipToBounds().drawWithContent {
                                            val bottom = if (isStatusExpanded) size.height else
                                                lineHeightPx + ((headerHeightPx - lineHeightPx) / 2).toFloat()
                                            clipRect(bottom = bottom) { this@drawWithContent.drawContent() }
                                        },
                                        content = {
                                            Text(
                                                text = phaseText,
                                                style = capsuleTextStyle,
                                                color = if (phaseError) MaterialTheme.colorScheme.error else thinkingHeaderColor,
                                                maxLines = maxLinesCount,
                                                softWrap = true,
                                                overflow = TextOverflow.Clip
                                            )
                                        }
                                    ) { measurables, constraints ->
                                        val text = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                                        val viewport = capsuleTextViewport(headerHeightPx, lineHeightPx, text.height, isStatusExpanded)
                                        layout(text.width, viewport.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                                            text.placeRelative(0, viewport.textTop)
                                        }
                                    }
                                }
                            }
                            if (isMessageContentError) {
                                Icon(
                                    imageVector = if (isErrorReportExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isErrorReportExpanded) "收起错误提示报告" else "展开错误提示报告",
                                    modifier = Modifier.padding(top = (headerHeight - 16.dp) / 2).size(16.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                )
                            } else if (hasThinking && hasThinkingContent) {
                                Icon(
                                    imageVector = if (showThinking) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showThinking) "收起" else "展开",
                                    modifier = Modifier.padding(top = (headerHeight - 16.dp) / 2).size(16.dp),
                                    tint = thinkingHeaderColor.copy(alpha = 0.78f)
                                )
                            } else if (canExpandStatus && showStatusToggle) {
                                Icon(
                                    imageVector = if (isStatusExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isStatusExpanded) "收起完整信息" else "展开完整信息",
                                    modifier = Modifier.padding(top = (headerHeight - 16.dp) / 2).size(16.dp),
                                    tint = (if (isStatusError) MaterialTheme.colorScheme.error else thinkingHeaderColor).copy(alpha = 0.78f)
                                )
                            }
                        }
                    }

                }

                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (personalizationSettings.showModelAvatar) {
                                ChatAvatar(
                                    isUser = false,
                                    avatarRevision = assistantAvatarRevision,
                                    apiConfigId = assistantApiConfigId,
                                    customAvatarUri = customAvatarUri,
                                    avatarSize = 22.dp
                                )
                            } else {
                                EchoModelMark(
                                    color = modelMarkColorFor(MaterialTheme.colorScheme.primary, resolvedReadableBackdrop),
                                    style = personalizationSettings.modelMarkStyle,
                                    animated = GenerationUiStateRules.animateModelMark(generationState),
                                    state = modelMarkState,
                                    completionEvent = if (modelMarkCompleted || modelMarkCompletionNow) message.id else 0L,
                                    completionOwner = modelMarkCompletionOwner,
                                    idleEffects = modelMarkIdleEffects,
                                    modifier = Modifier.size(if (personalizationSettings.modelMarkStyle in EchoModelMarkStyle.botStyles) 32.dp else 22.dp),
                                    reducedMotion = reducedMotion
                                )
                            }
                            Text(
                                text = assistantModelName.ifBlank { "AI" }.displayModelShortName(),
                                style = MaterialTheme.typography.labelMedium,
                                color = readableTextColorFor(resolvedReadableBackdrop)
                            )
                        }
                        GenerationUiStateRules.statusWithoutThinking(generationState, reconnectStatus, message.content.isBlank())?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isConnectionFailed) MaterialTheme.colorScheme.error else readableTextColorFor(resolvedReadableBackdrop),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // P0-1③ 连接等待计时：>30s 弱提示，>60s 升级 error 语义色（仅连接态；重连态由 reconnectStatus 文案承载）。
                    // 提示置于头像+胶囊行正下方：头像与胶囊对齐关系不受影响。
                    // 计时来自气泡级 connectElapsedSec（按生成会话累计），状态闪断不重置、提示不再消失重现。
                    //
                    // v2.6.8 需求 1：提示改为「固定槽位 + 透明度渐变」——文案出现、消失与「已等待 Ns」秒数
                    // 增长都不再改变末项尺寸。现在布局零抖动，只做淡入淡出。
                    //
                    // v2.7.7 需求 2/3：动画移到行**右端**（右缘与用户气泡右缘基准线一致），提示文字单行
                    // 占位（秒数增长/文案出现均不改变高度，槽位高度恒为一行）——无正文阶段末项更紧凑，
                    // 动画与底部时间戳的距离显著缩小。
                    val isConnectHintPhase = generationState == GenerationUiState.Connecting ||
                        generationState == GenerationUiState.Reconnecting
                    val hintVisible = isConnectHintPhase && connectElapsedSec >= 30
                    val hintAlpha by animateFloatAsState(
                        targetValue = if (hintVisible) 1f else 0f,
                        animationSpec = if (reducedMotion) {
                            snap()
                        } else {
                            com.aiassistant.ui.theme.EchoMotion.tweenSpec<Float>(com.aiassistant.ui.theme.EchoMotion.Duration.fast)
                        },
                        label = "connectHintAlpha"
                    )
                    val hintBodySmall = MaterialTheme.typography.bodySmall
                    // 相位切换（连接→思考→流式）时槽位不再一帧内整体移除：改用 fade+expand/shrink 平滑出入场，
                    // 否则末项高度突降会被钉底逻辑追平，表现为屏幕错误滑动一小段
                    val hintFadeSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec<Float>(
                        com.aiassistant.ui.theme.EchoMotion.Duration.fast
                    )
                    val hintSizeSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec<androidx.compose.ui.unit.IntSize>(
                        com.aiassistant.ui.theme.EchoMotion.Duration.fast
                    )
                    // v2.7.8 需求 2：动画改回**最左侧**（内容区左缘 x=4dp，与历史版本位置一致），
                    // 提示文字在其右侧；保持单行紧凑槽位
                    AnimatedVisibility(
                        visible = isGenerating && message.content.isBlank(),
                        enter = if (reducedMotion) {
                            fadeIn(snap()) + expandVertically(snap())
                        } else {
                            fadeIn(hintFadeSpec) + expandVertically(hintSizeSpec)
                        },
                        exit = if (reducedMotion) {
                            fadeOut(snap()) + shrinkVertically(snap())
                        } else {
                            fadeOut(hintFadeSpec) + shrinkVertically(hintSizeSpec)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(start = 4.dp, top = 4.dp, end = 44.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TypingIndicator(
                                accentColor = generationAccentColor,
                                style = personalizationSettings.streamingAnimationStyle
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (generationState == GenerationUiState.Reconnecting && !reconnectStatus.isNullOrBlank()) {
                                    "连接重试中，已等待 ${connectElapsedSec}s…"
                                } else {
                                    "连接时间较长，已等待 ${connectElapsedSec}s…"
                                },
                                style = hintBodySmall,
                                color = if (connectElapsedSec >= 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(hintAlpha)
                            )
                        }
                    }
                }

                // 实时连接异常明细：每个 Key 尝试失败即刻展示（v2.7.0 需求 3，无需手动暂停）
                if (liveKeyErrors.isNotEmpty()) {
                    var isLiveErrorsExpanded by remember(message.id) { mutableStateOf(true) }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .echoShapeClick(RoundedCornerShape(12.dp)) {
                                isLiveErrorsExpanded = !isLiveErrorsExpanded
                            },
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        contentColor = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "连接异常 · 实时明细（${liveKeyErrors.size} 次尝试失败）",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isLiveErrorsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isLiveErrorsExpanded) "收起实时报错明细" else "展开实时报错明细",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            androidx.compose.animation.AnimatedVisibility(visible = isLiveErrorsExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 5.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    liveKeyErrors.forEach { errorLine ->
                                        Text(
                                            text = "• $errorLine",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "正在按 Key 顺序自动重试，可继续等待或点击停止按钮结束本次生成。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (showThinkingBubbles && hasThinking) {
                    AnimatedVisibility(visible = showThinking && hasThinkingContent) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 4.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = { showThinking = !showThinking }
                                    )
                                },
                            color = glass.controlSelected.copy(alpha = 0.6f),
                            contentColor = thinkingContentColor,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, glass.outlineSelected.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "思考内容详情",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = thinkingHeaderColor
                                        )

                                        if (hasTranslation) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(glass.control.copy(alpha = 0.7f))
                                                    .padding(2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (showTranslated) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    modifier = Modifier.clickable { showTranslated = true }
                                                ) {
                                                    Text(
                                                        text = "译文",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (showTranslated) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (showTranslated) MaterialTheme.colorScheme.onPrimary else thinkingHeaderColor.copy(alpha = 0.8f),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (!showTranslated) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    modifier = Modifier.clickable { showTranslated = false }
                                                ) {
                                                    Text(
                                                        text = "原文",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (!showTranslated) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (!showTranslated) MaterialTheme.colorScheme.onPrimary else thinkingHeaderColor.copy(alpha = 0.8f),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (!isGenerating) {
                                            if (translatingThinking) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(end = 4.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(13.dp),
                                                        strokeWidth = 1.6.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        "翻译中...",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { onTranslateThinking?.invoke(message) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Translate,
                                                        contentDescription = if (hasTranslation) "重新翻译思考" else "翻译思考为中文",
                                                        modifier = Modifier.size(15.dp),
                                                        tint = if (hasTranslation) MaterialTheme.colorScheme.primary else thinkingHeaderColor.copy(alpha = 0.78f)
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                val textToCopy = if (hasTranslation && showTranslated) message.translatedThinking ?: "" else message.thinkingContent ?: ""
                                                clipboardManager.setText(AnnotatedString(textToCopy))
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "复制思考",
                                                modifier = Modifier.size(14.dp),
                                                tint = thinkingHeaderColor.copy(alpha = 0.78f)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val displayThinking = if (hasTranslation && showTranslated) message.translatedThinking ?: "" else (message.thinkingContent ?: "")
                                StoryReadingTypography(style = null, font = readingFont) {
                                    MarkdownText(content = displayThinking, color = thinkingContentColor)
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    val isErrorOutput = contentIsErrorMessage || stoppedErrorReport != null

                    if (isErrorOutput) {
                        val interruptMarker = "[输出已被中断"
                        val beforeInterrupt = if (stoppedErrorReport != null) displayContent else if (message.content.contains(interruptMarker)) {
                            message.content.substringBefore(interruptMarker).trimEnd()
                        } else ""
                        val errorBody = if (stoppedErrorReport != null) stoppedErrorReport else if (beforeInterrupt.isNotBlank()) {
                            message.content.substring(message.content.indexOf(interruptMarker)).trim()
                        } else {
                            message.content.trim()
                        }

                        // 如果前面已有部分输出内容，先以正常气泡展示正文
                        if (beforeInterrupt.isNotBlank()) {
                            if (stoppedErrorReport != null) {
                                MessageContent(textColor)
                            } else {
                                StoryReadingTypography(readingStyle, readingFont) {
                                    MarkdownText(content = beforeInterrupt, color = textColor)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val semanticColors = rememberEchoSemanticColors()
                        val errorSemantic = semanticColors.error
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

                        AnimatedVisibility(
                            visible = isErrorReportExpanded || !showThinkingBubbles,
                            enter = fadeIn(com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast)) + expandVertically(),
                            exit = fadeOut(com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast)) + shrinkVertically()
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = errorSemantic.container.copy(alpha = if (isDark) 0.38f else 0.22f),
                                border = BorderStroke(1.2.dp, errorSemantic.border.copy(alpha = if (isDark) 0.55f else 0.70f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ErrorOutline,
                                            contentDescription = "错误提示",
                                            modifier = Modifier.size(20.dp),
                                            tint = errorSemantic.main
                                        )
                                        Text(
                                            text = if (stoppedErrorReport != null) "暂停前的连接异常" else if (beforeInterrupt.isNotBlank()) "生成已被中断" else "模型请求异常",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = errorSemantic.main,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(errorBody))
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "复制报错信息",
                                                modifier = Modifier.size(16.dp),
                                                tint = errorSemantic.main.copy(alpha = 0.85f)
                                            )
                                        }
                                    }

                                    HorizontalDivider(
                                        color = errorSemantic.border.copy(alpha = 0.25f),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )

                                    MarkdownText(
                                        content = errorBody,
                                        color = errorSemantic.onContainer
                                    )
                                }
                            }
                        }
                    } else {
                        MessageContent(textColor)
                    }

                    // 工具调用留痕展示 (支持展开查看执行摘要与注入的上下文详情)
                    if (toolCallsList.isNotEmpty()) {
                        ToolCallsFooter(toolCalls = toolCallsList)
                    }

                    MessageFooter(
                        isUser = false,
                        thinkingBubbleVisible = showThinkingBubbles,
                        message = message,
                        backdropContentColor = readableTextColorFor(resolvedReadableBackdrop),
                        variantInfo = variantInfo,
                        onVariantSelected = onVariantSelected,
                        onCopy = onCopy,
                        onQuote = onQuote,
                        onBranch = onBranch,
                        isCreatingBranch = isCreatingBranch,
                        branchEnabled = branchEnabled,
                        onRegenerate = onRegenerate,
                        onEdit = onEdit,
                        onEditInPlace = onEditInPlace,
                        onDelete = onDelete,
                        onTogglePin = onTogglePin?.let { cb -> { cb(message) } },
                        onToggleExclude = onToggleExclude?.let { cb -> { cb(message) } },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!isGenerating && (message.content.isNotBlank() || hasThinking)) {
                        val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        val dividerColor = if (isDarkTheme) {
                            Color.White.copy(alpha = 0.16f)
                        } else {
                            Color.Black.copy(alpha = 0.12f)
                        }
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 4.dp),
                            thickness = 1.dp,
                            color = dividerColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageFooter(
    isUser: Boolean,
    message: Message,
    backdropContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    variantInfo: VariantInfo? = null,
    onVariantSelected: ((String, Int) -> Unit)? = null,
    onCopy: () -> Unit,
    onQuote: (() -> Unit)? = null,
    onBranch: (() -> Unit)? = null,
    isCreatingBranch: Boolean = false,
    branchEnabled: Boolean = true,
    onRegenerate: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    /** 仅修改消息内容（用户消息“仅编辑”，不重新发送/不重新生成） */
    onEditInPlace: (() -> Unit)? = null,
    onDelete: (() -> Unit)?,
    onTogglePin: (() -> Unit)? = null,
    onToggleExclude: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    endAlignMetadata: Boolean = isUser,
    thinkingBubbleVisible: Boolean = true
) {
    val thinkingTokensInThinkingBubble = thinkingBubbleVisible && message.role == "assistant" &&
        (!message.thinkingContent.isNullOrBlank() || message.thinkingTokens > 0)
    val responseTimeInThinkingBubble = thinkingTokensInThinkingBubble

    Row(
        // v2.7.7 需求 3：顶部间距 5dp→3dp，收窄回复内容/动画与底部时间戳之间的距离
        modifier = modifier.padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧元数据：时间戳、Token 等支持水平横向滚动
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (endAlignMetadata) Arrangement.End else Arrangement.Start
        ) {
            if (message.isPinned) {
                MessageMetaText(
                    text = "📌已固定",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (message.isExcluded) {
                MessageMetaText(
                    text = "🚫已排除",
                    color = MaterialTheme.colorScheme.error
                )
            }

            MessageMetaText(
                text = formatMessageClock(message.createdAt),
                color = backdropContentColor.copy(alpha = 0.84f)
            )

            if (message.responseTime > 0 && !responseTimeInThinkingBubble) {
                MessageMetaText(
                    text = formatTime(message.responseTime),
                    color = backdropContentColor.copy(alpha = 0.84f)
                )
            }

            if (message.tokenCount > 0) {
                MessageMetaText(
                    text = "${message.tokenCount} tokens",
                    color = backdropContentColor.copy(alpha = 0.84f)
                )

                if (message.responseTime > 0) {
                    val seconds = message.responseTime / 1000.0
                    // v2.7.7 需求 12：速度只按模型实际产出计——此前用 tokenCount（含输入+思考的
                    // 总消耗）除以耗时，输入越大 TPS 越虚高（常见 2000+，明显违背常识）；
                    // 改为按回复正文估算产出 token，且仅当计时窗口 ≥1s（速率才稳定可信）时展示
                    val outputTokens = remember(message.content) { AiRepository.estimateTokenCount(message.content) }
                    if (seconds >= 1.0 && outputTokens > 0) {
                        val speed = outputTokens / seconds
                        MessageMetaText(
                            text = String.format(Locale.US, "%.1f tokens/s", speed),
                            color = backdropContentColor.copy(alpha = 0.84f)
                        )
                    }
                }
            }

            if (message.thinkingTokens > 0 && !thinkingTokensInThinkingBubble) {
                MessageMetaText(
                    text = "思考: ${message.thinkingTokens} tokens",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 右侧操作栏：版本切换器与操作按钮同一行排布
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (variantInfo != null && variantInfo.total > 1 && onVariantSelected != null) {
                VariantSwitcher(
                    info = variantInfo,
                    contentColor = backdropContentColor,
                    onSelect = { index -> onVariantSelected(variantInfo.groupId, index) }
                )
                Spacer(modifier = Modifier.width(2.dp))
            }

            FooterIconButton(
                icon = Icons.Default.ContentCopy,
                contentDescription = "复制",
                tint = backdropContentColor,
                onClick = onCopy
            )

            if (!isUser && onBranch != null) {
                IconButton(onClick = onBranch, enabled = branchEnabled && !isCreatingBranch,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (isCreatingBranch) "正在创建分支" else "分支对话"
                    }) {
                    if (isCreatingBranch) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null,
                            tint = backdropContentColor.copy(alpha = if (branchEnabled) 1f else 0.38f),
                            modifier = Modifier.size(17.dp))
                    }
                }
            } else if (onQuote != null) {
                FooterIconButton(
                    icon = Icons.Default.FormatQuote,
                    contentDescription = "引用",
                    tint = backdropContentColor,
                    onClick = onQuote
                )
            }

            if (!isUser && onRegenerate != null) {
                FooterIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "重新生成",
                    tint = backdropContentColor,
                    onClick = onRegenerate
                )
            }

            // 需求 3：将编辑、固定、排除功能收纳进二级菜单（同时整合删除操作，使工具栏紧凑优雅）
            var showMoreMenu by remember { mutableStateOf(false) }
            val hasSecondaryActions = onEdit != null || onEditInPlace != null || onTogglePin != null || onToggleExclude != null || onDelete != null

            if (hasSecondaryActions) {
                Box {
                    FooterIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                        tint = backdropContentColor,
                        onClick = { showMoreMenu = true }
                    )

                    EchoGlassDropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        if (onEdit != null) {
                            DropdownMenuItem(
                                text = { Text(if (isUser) "重新编辑" else "编辑回复", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onEdit()
                                }
                            )
                        }

                        // 用户消息“仅编辑”：只改显示内容，不重新发送、不触发重新生成
                        if (isUser && onEditInPlace != null) {
                            DropdownMenuItem(
                                text = { Text("仅修改内容", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onEditInPlace()
                                }
                            )
                        }

                        if (onTogglePin != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (message.isPinned) "取消固定" else "固定到上下文",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (message.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = if (message.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onTogglePin()
                                }
                            )
                        }

                        if (onToggleExclude != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (message.isExcluded) "恢复参与上下文" else "从上下文中排除",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (message.isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (message.isExcluded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = if (message.isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onToggleExclude()
                                }
                            )
                        }

                        if (onDelete != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            DropdownMenuItem(
                                text = { Text("删除本条", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageMetaText(
    text: String,
    color: Color
) {
    Text(
        text = text,
        modifier = Modifier.padding(end = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
internal fun FooterIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(15.dp),
            tint = tint
        )
    }
}

@Composable
internal fun VariantSwitcher(
    info: VariantInfo,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val currentPosition = info.availableIndices.indexOf(info.currentIndex).coerceAtLeast(0)
    val canGoPrevious = currentPosition > 0
    val canGoNext = currentPosition < info.availableIndices.lastIndex

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = {
                if (canGoPrevious) onSelect(info.availableIndices[currentPosition - 1])
            },
            enabled = canGoPrevious,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = "上一版",
                modifier = Modifier.size(20.dp),
                tint = if (canGoPrevious) contentColor else contentColor.copy(alpha = 0.38f)
            )
        }
        Text(
            text = "${currentPosition + 1}/${info.total}",
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
        IconButton(
            onClick = {
                if (canGoNext) onSelect(info.availableIndices[currentPosition + 1])
            },
            enabled = canGoNext,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "下一版",
                modifier = Modifier.size(20.dp),
                tint = if (canGoNext) contentColor else contentColor.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
internal fun ChatAvatar(
    isUser: Boolean,
    avatarRevision: Int = 0,
    apiConfigId: Long? = null,
    customAvatarUri: String? = null,
    modifier: Modifier = Modifier,
    avatarSize: androidx.compose.ui.unit.Dp = 36.dp
) {
    val context = LocalContext.current
    val userAvatarBitmap = if (isUser) remember(context) { AvatarManager.getAvatarBitmap(context) } else null
    val customAvatarBitmap = if (!isUser && !customAvatarUri.isNullOrBlank()) {
        remember(customAvatarUri) {
            // v2.7.3 流畅度：命中全局缓存避免每行重复解码；未命中再读盘并回填
            chatCustomAvatarCache.get(customAvatarUri) ?: runCatching {
                val uri = Uri.parse(customAvatarUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()?.also { chatCustomAvatarCache.put(customAvatarUri, it) }
        }
    } else null
    val modelAvatarBitmap = if (!isUser && customAvatarBitmap == null) {
        remember(context, avatarRevision, apiConfigId) {
            AvatarManager.getPreferredModelAvatarBitmap(context, apiConfigId)
        }
    } else null
    val background = if (isUser) Color.White else MaterialTheme.colorScheme.surface
    val foreground = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier
            .requiredSize(avatarSize)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (isUser && userAvatarBitmap != null) {
            Image(
                bitmap = userAvatarBitmap.asImageBitmap(),
                contentDescription = "用户头像",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (!isUser) {
            if (customAvatarBitmap != null) {
                Image(
                    bitmap = customAvatarBitmap.asImageBitmap(),
                    contentDescription = "角色头像",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (modelAvatarBitmap != null) {
                Image(
                    bitmap = modelAvatarBitmap.asImageBitmap(),
                    contentDescription = "模型头像",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.deepseek),
                    contentDescription = "模型头像",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
fun TypingIndicator(
    accentColor: androidx.compose.ui.graphics.Color,
    style: String = StreamingAnimationStyle.THIN_CURSOR
) {
    Box(Modifier.width(28.dp).height(20.dp), contentAlignment = Alignment.CenterStart) {
        EchoStreamingIndicator(color = accentColor, style = style, compact = true)
    }
}

/** Update existing bubbles when the user changes an appearance setting. */
@Composable
internal fun rememberChatPersonalizationSettings(): com.aiassistant.utils.PersonalizationSettings {
    val context = LocalContext.current.applicationContext
    val manager = AiAssistantApp.instance.personalizationManager
    var settings by remember(manager) { mutableStateOf(manager.getSettings()) }
    DisposableEffect(context, manager) {
        val prefs = context.getSharedPreferences("personalization_settings", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            settings = manager.getSettings()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        settings = manager.getSettings()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}

// 格式化时间
internal fun formatTime(ms: Long): String {
    return when {
        ms < 1000 -> "${ms}ms"
        ms < 60000 -> "${ms / 1000}s"
        else -> "${ms / 60000}m${(ms % 60000) / 1000}s"
    }
}

internal fun formatMessageClock(timestamp: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
fun AttachmentChip(attachment: Attachment) {
    val isImage = FileUtils.isImage(attachment.mimeType, attachment.name)
    val hasOcr = !attachment.ocrText.isNullOrBlank() || attachment.processingNote?.contains("OCR") == true

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when {
                    hasOcr -> Icons.Default.DocumentScanner
                    isImage -> Icons.Default.Image
                    else -> Icons.Default.AttachFile
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun AttachmentGroupBubble(
    attachments: List<Attachment>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(12.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(10.dp)
        ) {
            items(attachments) { attachment ->
                AttachmentChip(attachment = attachment)
            }
        }
    }
}

private val CITATION_NUMBERED_LINE_REGEX = Regex("""^\s*(?:[-*+]\s+)?(?:\[\^?(\d+)]|(\d+)[.)])\s*:?\s*(.*)$""")
private val CITATION_BARE_URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
private val CITATION_NUMERIC_LABEL_REGEX = Regex("""\^?\d+""")

private fun citationWebUrl(raw: String): String? {
    val url = decodeHtmlEntities(raw.trim())
    return url.takeIf {
        runCatching {
            val uri = java.net.URI(it)
            uri.scheme.lowercase() in setOf("http", "https") && !uri.rawAuthority.isNullOrBlank()
        }.getOrDefault(false)
    }
}

private fun trimBareCitationUrl(raw: String): String {
    var url = raw.trimEnd('.', ',', ';', '!', '?', '，', '。', '；', '！', '？', ']', '}')
    while (url.endsWith(')') && url.count { it == ')' } > url.count { it == '(' }) url = url.dropLast(1)
    return url
}

/** 保留资料自身的编号；模型省略网址时，从本轮成功的联网搜索留痕找回真实来源。 */
fun extractCitationsFromContent(content: String, toolCalls: List<ToolCallRecord> = emptyList()): List<CitationInfo> {
    val searchSources = toolCalls.filter { it.isSuccess && it.toolType == "WEB_SEARCH" }
        .flatMap { extractCitationSources(it.detailContent) }
        .groupBy { it.index }
        .mapNotNull { (_, sources) ->
            // 多次搜索可能各自从 1 编号；冲突时不得猜测正文指向哪轮搜索。
            sources.distinctBy { it.url }.singleOrNull()
        }
    return extractCitationSources(content, searchSources)
}

private fun extractCitationSources(content: String, searchSources: List<CitationInfo> = emptyList()): List<CitationInfo> {
    val explicit = linkedMapOf<Int, CitationInfo>()
    val general = mutableListOf<MarkdownInlineParser.MarkdownLink>()

    fun add(index: Int, title: String, rawUrl: String) {
        if (index <= 0 || explicit.containsKey(index)) return
        val url = citationWebUrl(rawUrl) ?: return
        val plainTitle = parseInlineMarkdown(title).text.trim().trim(':', '：', '-', '(', ')', '<', '>')
            .takeUnless { it.matches(CITATION_NUMERIC_LABEL_REGEX) }
        explicit[index] = CitationInfo(index, plainTitle?.takeIf { it.isNotBlank() } ?: "参考网页 $index", url)
    }

    fun collect(source: String, includeGeneral: Boolean) {
        val decoded = decodeHtmlEntities(source)
        val lines = decoded.lines()
        var inFence = false
        lines.forEachIndexed { lineIndex, line ->
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                inFence = !inFence
                return@forEachIndexed
            }
            if (inFence) return@forEachIndexed
            val numbered = CITATION_NUMBERED_LINE_REGEX.matchEntire(line)
            if (numbered != null) {
                val index = (numbered.groupValues[1].ifBlank { numbered.groupValues[2] }).toIntOrNull()
                val tail = numbered.groupValues[3]
                // 标准脚注定义允许把目的地址放到下一缩进行。
                val sourceTail = if (!tail.contains("http", ignoreCase = true) &&
                    lines.getOrNull(lineIndex + 1)?.firstOrNull()?.isWhitespace() == true) {
                    tail + " " + lines[lineIndex + 1].trim()
                } else tail
                val linkStart = sourceTail.indexOf('[')
                val link = if (linkStart >= 0) MarkdownInlineParser.readMarkdownLink(sourceTail, linkStart) else null
                val rawUrl = link?.destination ?: CITATION_BARE_URL_REGEX.find(sourceTail)?.value?.let(::trimBareCitationUrl)
                if (index != null && rawUrl != null) {
                    val title = link?.label ?: sourceTail.substringBefore(rawUrl).trim()
                    add(index, title, rawUrl)
                }
            }
            var start = line.indexOf('[')
            while (start >= 0) {
                val link = MarkdownInlineParser.readMarkdownLink(line, start)
                if (link != null && line.getOrNull(start - 1) != '!') {
                    if (link.label.matches(CITATION_NUMERIC_LABEL_REGEX)) {
                        link.label.removePrefix("^").toIntOrNull()?.let { add(it, "", link.destination) }
                    } else if (includeGeneral) {
                        general += link
                    }
                }
                start = line.indexOf('[', if (link != null) link.endExclusive else start + 1)
            }
        }
    }

    collect(content, includeGeneral = true)
    searchSources.forEach { source -> if (!explicit.containsKey(source.index)) explicit[source.index] = source }
    var nextIndex = 1
    general.forEach { link ->
        val url = citationWebUrl(link.destination) ?: return@forEach
        if (explicit.values.any { it.url == url }) return@forEach
        while (explicit.containsKey(nextIndex)) nextIndex++
        add(nextIndex, link.label, url)
    }
    return explicit.values.sortedBy { it.index }
}

internal fun citationForIndex(citations: List<CitationInfo>, index: Int): CitationInfo =
    citations.firstOrNull { it.index == index } ?: CitationInfo(index, "参考资料 $index", "")

@Composable
fun CitationsCardsRow(
    citations: List<CitationInfo>,
    onCitationClick: (CitationInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = echoGlassPalette()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "参考资料来源 (${citations.size})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            citations.forEach { citation ->
                val cardShape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .clip(cardShape)
                        .background(glass.control.copy(alpha = 0.65f))
                        .border(BorderStroke(0.8.dp, glass.outline.copy(alpha = 0.5f)), cardShape)
                        .echoShapeClick(cardShape) { onCitationClick(citation) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "[${citation.index}]",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = citation.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CitationDetailDialog(
    citation: CitationInfo,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val hasUrl = citationWebUrl(citation.url) != null
    var copied by remember(citation.index, citation.url) { mutableStateOf(false) }

    EchoGlassDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 440.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[${citation.index}]",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = citation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (hasUrl) "参考网址来源（可长按文本选中）：" else "该引用未提供可打开的网址。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasUrl) androidx.compose.foundation.text.selection.SelectionContainer {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.08f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = citation.url,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
                if (!hasUrl) {
                    Text(
                        text = "无法从回复或本轮搜索记录中确定编号 [${citation.index}] 对应的真实链接。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        buttons = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasUrl) Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        runCatching<Unit> { uriHandler.openUri(citation.url) }
                            .onSuccess { onDismiss() }
                            .onFailure { Toast.makeText(context, "无法打开网页，可复制网址后在浏览器打开", Toast.LENGTH_SHORT).show() }
                    }
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("打开网页", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // 主操作独占一行，次操作均分宽度；复制状态变化不会挤压“打开网页”。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("关闭", maxLines = 1)
                    }
                    if (hasUrl) OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            clipboardManager.setText(AnnotatedString(citation.url))
                            copied = true
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copied) "已复制" else "复制网址", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    )
}

@Composable
fun ToolCallsFooter(
    toolCalls: List<ToolCallRecord>,
    modifier: Modifier = Modifier
) {
    if (toolCalls.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }
    var selectedRecordForDialog by remember { mutableStateOf<ToolCallRecord?>(null) }
    val glass = echoGlassPalette()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = glass.control.copy(alpha = if (isDarkTheme) 0.5f else 0.7f),
        border = BorderStroke(0.8.dp, glass.outline.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // 头部摘要栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = "成功调用 ${toolCalls.size} 项系统与联网工具",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isExpanded) "收起留痕" else "查看留痕",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 展开的工具调用记录列表
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    toolCalls.forEach { record ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedRecordForDialog = record },
                            shape = RoundedCornerShape(10.dp),
                            color = glass.controlSelected.copy(alpha = 0.4f),
                            border = BorderStroke(0.6.dp, glass.outlineSelected.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val icon = when (record.toolType) {
                                    "WEATHER" -> Icons.Default.Cloud
                                    "TIME_CALENDAR" -> Icons.Default.Schedule
                                    "HEALTH" -> Icons.Default.DirectionsWalk
                                    "DEVICE_HARDWARE" -> Icons.Default.Smartphone
                                    "LOCATION" -> Icons.Default.LocationOn
                                    "JINA_READER" -> Icons.Default.MenuBook
                                    else -> Icons.Default.Search
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = record.toolName,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = record.toolName,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = "执行成功",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (record.summary.isNotBlank()) {
                                        Text(
                                            text = record.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { selectedRecordForDialog = record },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 28.dp)
                                ) {
                                    Text("详情", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedRecordForDialog != null) {
        ToolCallDetailDialog(
            record = selectedRecordForDialog!!,
            onDismiss = { selectedRecordForDialog = null }
        )
    }
}

@Composable
fun ToolCallDetailDialog(
    record: ToolCallRecord,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    EchoGlassDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 440.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(record.toolName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("执行摘要：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(record.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text("工具获取并注入模型的完整上下文数据：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = record.detailContent,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(record.detailContent))
                        android.widget.Toast.makeText(context, "已复制工具数据到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("复制数据")
                }
            }
        }
    )
}

// 需求 6：模型回复时排队消息悬浮卡片（按设计稿实现）
@Composable
internal fun MessageQueueCard(
    queue: List<QueuedMessage>,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onRecall: (String) -> Unit,
    onEdit: (QueuedMessage) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val glass = echoGlassPalette()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, glass.outline.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶部标题行: 排队中 (N) + 暂停/开启图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "排队中 (${queue.size})${if (isPaused) " · 已暂停" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onTogglePause,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "开启自动发送" else "暂停自动发送（只排队）",
                        tint = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val queueReorderState = rememberSmoothReorderState()

            // 队列列表项
            queue.forEachIndexed { index, msg ->
                val isActive = queueReorderState.isItemActive(index)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isActive) MaterialTheme.colorScheme.surface.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    border = BorderStroke(
                        if (isActive) 1.5.dp else 0.8.dp,
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else glass.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .reorderItem(queueReorderState, index, msg.id)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 左侧拖动手柄 (按住六个点上下拖拽调序，带平滑动画)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent)
                                .reorderDragHandle(
                                    state = queueReorderState,
                                    index = { index },
                                    key = { msg.id },
                                    keys = { queue.map { it.id } },
                                    listSize = { queue.size },
                                    onMove = onMove
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = "按住上下拖动调整顺序",
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // 中间文本内容预览
                        Text(
                            text = msg.content.ifBlank { "[附件消息]" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // 右侧操作区：编辑(铅笔)、撤回回填输入框(✕)（需求 4：删除无用发送键，仅保留编辑与撤回）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { onEdit(msg) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "编辑排队消息",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRecall(msg.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "撤回排队消息至输入框",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
