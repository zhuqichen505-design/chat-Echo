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
import androidx.compose.foundation.text.selection.SelectionContainer
import com.aiassistant.domain.model.ToolCallRecord
import com.aiassistant.domain.model.QueuedMessage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiassistant.utils.TimelineMemoryHelper
import com.aiassistant.utils.TimelineReconcileResult
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.AtemporalSettingItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.aiassistant.ui.components.EchoTextToolbar
import com.aiassistant.ui.components.EchoTextToolbarHost
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.aiassistant.ui.components.SideAnchorItem
import com.aiassistant.ui.components.SideAnchorNavigator
import com.aiassistant.ui.components.TransientLazyListScrollbar
import com.aiassistant.ui.components.EchoScrollableTextEditor
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
import com.aiassistant.utils.RoleplaySmartAnalyzer
import com.aiassistant.utils.RoleplaySmartParser
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
import androidx.compose.runtime.produceState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit = {},
    onNavigateToRoleplayMemory: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val chatBackgroundBitmap = remember(context) {
        BackgroundImageManager.getChatBackgroundBitmap(context)
    }
    val scope = rememberCoroutineScope()
    val viewModel: ChatViewModel = viewModel(
        key = "chat_$conversationId",
        factory = ChatViewModel.factory(conversationId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    // 实时 Key 尝试报错明细：生成过程中无需手动暂停即可直接看到各次连接失败的具体原因
    val liveKeyErrors by viewModel.keyAttemptErrors.collectAsState()
    // 生成锚点（v2.6.5）：正在连接/输出的回复钉在触发本轮的用户消息之后，
    // 删除同一位置的其他回复（错误占位、旧 variant）不会移动其位置
    val generatingAnchor by viewModel.generatingAnchor.collectAsState()
    // P0-2① 合帧消费（R-1 红线）：流式 token 经 33ms 窗口合并（≈30fps 上限），
    // 杜绝逐 token 全屏重组；长度收缩（重置/重发清空）时立即发射，避免旧文本滞留。
    // StateFlow conflate 特性天然丢弃中间态，无积压风险。
    var currentResponse by remember(viewModel) { mutableStateOf("") }
    LaunchedEffect(viewModel) {
        viewModel.currentResponse.collect { full ->
            currentResponse = full
            kotlinx.coroutines.delay(com.aiassistant.ui.theme.EchoMotion.Typewriter.frameBudgetMs)
        }
    }
    // 与 currentResponse 同一合帧策略，思考块流式同样 30fps 上限
    var currentThinking by remember(viewModel) { mutableStateOf("") }
    LaunchedEffect(viewModel) {
        viewModel.currentThinking.collect { full ->
            currentThinking = full
            kotlinx.coroutines.delay(com.aiassistant.ui.theme.EchoMotion.Typewriter.frameBudgetMs)
        }
    }
    val error by viewModel.error.collectAsState()
    val availableModelOptions by viewModel.availableModelOptions.collectAsState()
    val currentModel by viewModel.currentModel.collectAsState()
    val currentModelOption by viewModel.currentModelOption.collectAsState()
    val tempSettings by viewModel.tempSettings.collectAsState()
    val contextUsage by viewModel.contextUsage.collectAsState()
    val messageModelMap by viewModel.messageModelMap.collectAsState()
    val pendingMemoryCandidate by viewModel.pendingMemoryCandidate.collectAsState()
    val pendingContextFallbackPrompt by viewModel.pendingContextFallbackPrompt.collectAsState()
    val pendingReplyDirection by viewModel.pendingReplyDirection.collectAsState()
    var replyDirectionDismissed by remember(pendingReplyDirection) { mutableStateOf(false) }
    val reconnectStatus by viewModel.reconnectStatus.collectAsState()

    val hazeState = rememberEchoHazeState()
    val readableBackdrops = rememberReadableBackdropColors(chatBackgroundBitmap)
    val listState = rememberLazyListState()
    val scrollControlsVisibilityState = rememberLazyListControlsVisible(listState)
    val showScrollControls by scrollControlsVisibilityState
    val clipboardManager = LocalClipboardManager.current
    val promptTemplates by viewModel.promptTemplates.collectAsState()
    val translatingMessageIds by viewModel.translatingMessageIds.collectAsState()
    val sessionMemories by viewModel.sessionMemories.collectAsState()
    val timelineNodes by viewModel.timelineNodes.collectAsState()
    val isReconcilingTimeline by viewModel.isReconcilingTimeline.collectAsState()
    val timelineReconcileProgress by viewModel.timelineReconcileProgress.collectAsState()
    val timelineReconcileResult by viewModel.timelineReconcileResult.collectAsState()
    val showTimelineReconcileDialog by viewModel.showTimelineReconcileDialog.collectAsState()
    val timelineUpdateNotice by viewModel.timelineUpdateNotice.collectAsState()
    val pendingTimelineProposal by viewModel.pendingTimelineProposal.collectAsState()
    val timelineExtractionCount by viewModel.timelineExtractionCount.collectAsState()
    val liveReconcileDraft by viewModel.liveReconcileDraft.collectAsState()
    val timelineCheckpoint by viewModel.timelineCheckpoint.collectAsState()

    val roleplayRepo = remember { com.aiassistant.AiAssistantApp.instance.roleplayRepository }
    val allAvailableCharacters by roleplayRepo.getAllCharacters().collectAsState(initial = emptyList())
    val allAvailableScenarios by roleplayRepo.getAllScenarios().collectAsState(initial = emptyList())

    var inputText by remember(conversationId) { mutableStateOf(ChatViewModel.getDraft(conversationId)) }
    var activeQuotedText by remember { mutableStateOf<String?>(null) }
    var messagePendingDelete by remember { mutableStateOf<Message?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(uiState.conversationTitle) { mutableStateOf(uiState.conversationTitle) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showConvertToRoleplayDialog by remember { mutableStateOf(false) }
    var showContextUsageDialog by remember { mutableStateOf(false) }
    var showRollingSummaryDialog by remember { mutableStateOf(false) }
    var showStoryManagerDialog by remember { mutableStateOf(false) }
    var showStorySmartAnalyzeDialog by remember { mutableStateOf(false) }
    var showPlotActionDialog by remember { mutableStateOf(false) }
    var showThinkingPopover by remember { mutableStateOf(false) }
    var selectedAttachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var isProcessingAttachments by remember { mutableStateOf(false) }
    var attachmentStatus by remember { mutableStateOf<String?>(null) }
    var modelAvatarRevision by remember { mutableIntStateOf(0) }
    var pendingEditSource by remember { mutableStateOf<Message?>(null) }
    var preserveScrollForBranchGeneration by remember { mutableStateOf(false) }
    var streamingBranchGroupId by remember { mutableStateOf<String?>(null) }
    var autoFollowOutput by remember { mutableStateOf(true) }
    var isBarsHidden by remember { mutableStateOf(false) }
    var branchSuccessDialog by remember { mutableStateOf<BranchSuccessDialogState?>(null) }
    val messageQueue by viewModel.messageQueue.collectAsState()
    val isQueuePaused by viewModel.isQueuePaused.collectAsState()
    var editingQueueItem by remember { mutableStateOf<QueuedMessage?>(null) }
    var editingAssistantMessage by remember { mutableStateOf<Message?>(null) }
    // v2.7.2 需求 1：TextFieldValue 承载光标位置，「跳转末尾」按钮据此把光标锁定到文字末尾
    var editingAssistantContent by remember { mutableStateOf(TextFieldValue("")) }
    // 用户消息“仅编辑”：只改显示内容，不重新发送、不重新生成
    var editingUserMessage by remember { mutableStateOf<Message?>(null) }
    var editingUserContent by remember { mutableStateOf(TextFieldValue("")) }

    var lastStreamScrollAt by remember { mutableLongStateOf(0L) }
    val variantSelections = remember { mutableStateMapOf<String, Int>() }
    val variantSelectionSnapshot = variantSelections.toMap()
    val displayMessages = remember(messages, variantSelectionSnapshot) {
        buildDisplayMessages(messages, variantSelectionSnapshot)
    }
    val chatNavItems = remember(displayMessages) {
        buildChatAnchorItems(displayMessages)
    }

    DisposableEffect(conversationId) {
        onDispose {
            ChatViewModel.saveDraft(conversationId, inputText)
        }
    }

    BackHandler {
        when {
            // N-1：子弹窗优先关闭，避免弹窗未关时返回键直接退出会话
            showRenameDialog -> showRenameDialog = false
            showSettingsDialog -> showSettingsDialog = false
            showConvertToRoleplayDialog -> showConvertToRoleplayDialog = false
            showContextUsageDialog -> showContextUsageDialog = false
            showRollingSummaryDialog -> showRollingSummaryDialog = false
            showStoryManagerDialog -> showStoryManagerDialog = false
            showStorySmartAnalyzeDialog -> showStorySmartAnalyzeDialog = false
            showPlotActionDialog -> showPlotActionDialog = false
            isBarsHidden -> {
                isBarsHidden = false
            }
            showThinkingPopover -> {
                showThinkingPopover = false
            }
            else -> {
                viewModel.leaveConversation(onNavigateBack)
            }
        }
    }

    fun addAttachments(uris: List<Uri>, forceOcr: Boolean = false) {
        if (uris.isEmpty() || isProcessingAttachments) return
        scope.launch {
            isProcessingAttachments = true
            attachmentStatus = "正在处理附件..."
            val modelName = currentModel ?: uiState.modelName
            val supportsImageOverride = currentModelOption?.capability?.imageSupportOverride()
            val newAttachments = mutableListOf<Attachment>()
            var totalChars = selectedAttachments.sumOf { (it.base64Data?.length ?: 0).toLong() + (it.textContent?.length ?: 0) }
            try {
            require(selectedAttachments.size + uris.size <= 8) { "每次最多添加 8 个附件" }
            uris.forEach { uri ->
                val prepared = FileUtils.prepareAttachment(
                    context = context,
                    uri = uri,
                    modelName = modelName,
                    forceOcr = forceOcr,
                    supportsImageInputOverride = supportsImageOverride
                ) ?: error("附件读取失败或超过 16 MB 限制")
                totalChars += (prepared.base64Data?.length ?: 0) + (prepared.textContent?.length ?: 0)
                require(totalChars <= com.aiassistant.utils.BoundedInput.MAX_TOTAL_CHARS) { "附件总内容超过限制，请减少附件数量" }
                newAttachments.add(prepared)
            }
            selectedAttachments = selectedAttachments + newAttachments
            attachmentStatus = when {
                newAttachments.isEmpty() -> "附件处理失败"
                newAttachments.any { it.processingNote?.contains("OCR") == true } -> "已添加 ${newAttachments.size} 个附件，图片已OCR"
                else -> "已添加 ${newAttachments.size} 个附件"
            }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                attachmentStatus = e.message ?: "附件处理失败"
            } finally {
                isProcessingAttachments = false
            }
        }
    }

    // 通用文件选择器（支持所有类型）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        addAttachments(uris)
    }

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        addAttachments(uris)
    }

    val ocrImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        addAttachments(uris, forceOcr = true)
    }

    var prevMessagesCount by remember { mutableIntStateOf(messages.size) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val total = layoutInfo.totalItemsCount
            val atBottom = if (total == 0 || visibleItems.isEmpty()) {
                true
            } else {
                val lastItem = visibleItems.last()
                lastItem.index >= total - 1
            }
            Pair(listState.isScrollInProgress, atBottom)
        }.collect { (isScrolling, atBottom) ->
            if (isScrolling) {
                autoFollowOutput = atBottom
            } else if (atBottom) {
                autoFollowOutput = true
            }
        }
    }

    var hasInitialScrolledToBottom by remember(conversationId) { mutableStateOf(false) }
    LaunchedEffect(conversationId, displayMessages.size) {
        if (!hasInitialScrolledToBottom && displayMessages.isNotEmpty()) {
            hasInitialScrolledToBottom = true
            androidx.compose.runtime.withFrameNanos { }
            try {
                listState.followMeasuredBottom()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        }
    }

    LaunchedEffect(messages.size) {
        val prev = prevMessagesCount
        prevMessagesCount = messages.size
        if (messages.size <= prev) {
            return@LaunchedEffect
        }
        if (autoFollowOutput && !preserveScrollForBranchGeneration && !listState.isScrollInProgress) {
            androidx.compose.runtime.withFrameNanos { }
            val totalCount = listState.layoutInfo.totalItemsCount
            if (totalCount > 0) {
                try {
                    listState.followMeasuredBottom()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            }
        }
    }

    var lastStreamScrollTime by remember { mutableLongStateOf(0L) }
    // v2.7.3 流畅度：原先以 (currentResponse.length, currentThinking.length, isGenerating) 为 key 的
    // LaunchedEffect 在 ChatScreen 顶层组合作用域读取每 token 变化的流式 state，导致整个聊天页
    // 组合体（含 Scaffold、全部对话框分支与 remember）以每 token 一帧的频率全量重组。
    // 改为 LaunchedEffect(isGenerating) + snapshotFlow 在协程内读取长度：组合期零读取，
    // 70ms 节流与钉底条件保持原样，滚动跟随行为不变
    LaunchedEffect(isGenerating) {
        snapshotFlow { currentResponse.length to currentThinking.length }.collect {
            if (preserveScrollForBranchGeneration || !autoFollowOutput || listState.isScrollInProgress) {
                return@collect
            }
            val isStreaming = isGenerating && (currentResponse.isNotEmpty() || currentThinking.isNotEmpty())
            if (isStreaming) {
                val now = System.currentTimeMillis()
                if (now - lastStreamScrollTime < 70L) {
                    return@collect
                }
                lastStreamScrollTime = now
                val totalCount = listState.layoutInfo.totalItemsCount
                if (totalCount > 0) {
                    androidx.compose.runtime.withFrameNanos { }
                    try {
                        listState.followMeasuredBottom()
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                }
            }
        }
    }

    LaunchedEffect(isGenerating) {
        if (!isGenerating) {
            streamingBranchGroupId = null
            if (autoFollowOutput && !preserveScrollForBranchGeneration && !listState.isScrollInProgress) {
                kotlinx.coroutines.delay(40)
                val totalCount = listState.layoutInfo.totalItemsCount
                if (totalCount > 0) {
                    try {
                        listState.followMeasuredBottom()
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                }
            }
        }
    }

    // Connect once after the waiting item is measured. Do not observe our own scrolling/layout
    // and feed it back into another scrollToItem: that loop caused transient overshoot/rebound.
    LaunchedEffect(isGenerating) {
        if (!isGenerating) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        if (autoFollowOutput && !preserveScrollForBranchGeneration && !listState.isScrollInProgress) {
            listState.followMeasuredBottom()
        }
    }

    // 审核 A3：落定信号接线——生成结束后捕获最新落库的 assistant 消息 id，
    // 对应持久化气泡以 settleSignal 入场播放收尾序列（光标淡出+落定脉冲）；
    // 1.6s 后撤销信号（防滚动回看重播）
    var settledMessageId by remember { mutableStateOf(0L) }
    val wasGeneratingForSettle = remember { mutableStateOf(isGenerating) }
    LaunchedEffect(isGenerating) {
        if (!isGenerating && wasGeneratingForSettle.value) {
            kotlinx.coroutines.delay(150) // 等待落库消息经 Room 流回 UI
            val lastAssistant = messages.lastOrNull { it.role == "assistant" && it.id > 0 }
            if (lastAssistant != null) {
                settledMessageId = lastAssistant.id
                kotlinx.coroutines.delay(1600)
                settledMessageId = 0L
            }
        }
        wasGeneratingForSettle.value = isGenerating
    }

    LaunchedEffect(conversationId) {
        streamingBranchGroupId = null
        pendingEditSource = null
    }

    val systemClipboard = LocalClipboardManager.current
    val inAppClipboard = remember(systemClipboard) {
        com.aiassistant.ui.components.InAppSelectionClipboardManager(systemClipboard)
    }
    val textToolbar = remember { EchoTextToolbar() }
    CompositionLocalProvider(
        LocalTextToolbar provides textToolbar,
        LocalClipboardManager provides inAppClipboard
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 全屏背景与全屏毛玻璃源 (涵盖从顶到底全部区域，包括 bottomBar 与 topBar 背后)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.background)
                    .echoHazeSource(hazeState)
            ) {
                chatBackgroundBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                topBar = {},
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = pendingMemoryCandidate != null,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                ) {
                    pendingMemoryCandidate?.let { candidate ->
                        var isEditingMemory by remember(candidate.sourceMessageId, candidate.distilledContent) {
                            mutableStateOf(false)
                        }
                        var editedMemoryContent by remember(candidate.sourceMessageId, candidate.distilledContent) {
                            mutableStateOf(candidate.distilledContent)
                        }

                        EchoGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                            shape = EchoTokens.Radius.shapeMd
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (isEditingMemory) "编辑设定记忆" else "智能识别记忆候选 (长按文字可编辑)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissPendingMemory() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "忽略",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                if (isEditingMemory) {
                                    OutlinedTextField(
                                        value = editedMemoryContent,
                                        onValueChange = { editedMemoryContent = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        minLines = 2,
                                        maxLines = 5,
                                        label = { Text("编辑设定/记忆内容") }
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                editedMemoryContent = candidate.distilledContent
                                                isEditingMemory = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Text("取消", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.acceptPendingMemory("session", editedMemoryContent)
                                                isEditingMemory = false
                                            },
                                            enabled = editedMemoryContent.isNotBlank(),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                        ) {
                                            Text("应用", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "「${candidate.distilledContent}」",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(EchoTokens.Radius.shapeSm)
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    editedMemoryContent = candidate.distilledContent
                                                    isEditingMemory = true
                                                }
                                            )
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        TextButton(
                                            onClick = { viewModel.dismissPendingMemory() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                                        ) {
                                            Text("忽略", style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.acceptPendingMemory("session") },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                                        ) {
                                            Text("仅本会话生效", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Button(
                                            onClick = { viewModel.acceptPendingMemory("user") },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                                        ) {
                                            Text("存为跨会话长期记忆", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 需求 4：时间线实时梳理进度卡片（实时可见已梳理事件数、随时查看与暂停/保存）
                AnimatedVisibility(
                    visible = isReconcilingTimeline,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    EchoGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                        shape = EchoTokens.Radius.shapeMd
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "时间线正在全量梳理...",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                    )
                                    Text(
                                        text = timelineReconcileProgress ?: "已梳理 ${liveReconcileDraft?.events?.size ?: 0} 条事件",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { viewModel.openLiveDraftForReview() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("实时查看", style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(
                                    onClick = { viewModel.cancelTimelineReconciliation() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("暂停/保存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                if (timelineExtractionCount > 0) {
                    Text("正在提取本轮时间线与设定…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
                }

                // 需求 1 & 2：时间线自动识别事件与时间变动待确认卡片
                AnimatedVisibility(
                    visible = pendingTimelineProposal != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    pendingTimelineProposal?.let { proposal ->
                        var editedSettings by remember(proposal) { mutableStateOf(proposal.atemporalSettings.map { it.copy() }) }
                        var isEditingTimeline by remember(proposal) {
                            mutableStateOf(false)
                        }
                        var editedStoryTime by remember(proposal.updatedStoryTime) {
                            mutableStateOf(proposal.updatedStoryTime.orEmpty())
                        }
                        var editedEventTimeTag by remember(proposal.newEvent?.timeTag) {
                            mutableStateOf(proposal.newEvent?.timeTag.orEmpty())
                        }
                        var editedEventContent by remember(proposal.newEvent?.content) {
                            mutableStateOf(proposal.newEvent?.content.orEmpty())
                        }

                        EchoGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                            shape = EchoTokens.Radius.shapeMd
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.HistoryEdu,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isEditingTimeline) {
                                                "编辑本轮时间线与设定"
                                            } else if (proposal.action == "UPDATE") {
                                                "时间线更新待确认 (长按可编辑)"
                                            } else {
                                                "本轮时间线与设定待确认"
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissTimelineProposal() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "忽略",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                proposal.extractionErrorMessage?.let { message ->
                                    Text(message, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                }

                                if (isEditingTimeline) {
                                    if (!proposal.updatedStoryTime.isNullOrBlank()) {
                                        OutlinedTextField(
                                            value = editedStoryTime,
                                            onValueChange = { editedStoryTime = it },
                                            label = { Text("推进时空") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (proposal.newEvent != null) {
                                        OutlinedTextField(
                                            value = editedEventTimeTag,
                                            onValueChange = { editedEventTimeTag = it },
                                            label = { Text("事件时间节点") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodySmall
                                        )
                                        OutlinedTextField(
                                            value = editedEventContent,
                                            onValueChange = { editedEventContent = it },
                                            label = { Text("事件内容") },
                                            minLines = 2,
                                            maxLines = 4,
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    editedSettings.forEachIndexed { index, setting ->
                                        OutlinedTextField(
                                            value = setting.content,
                                            onValueChange = { value -> editedSettings = editedSettings.mapIndexed { i, item ->
                                                if (i == index) item.copy(content = value) else item
                                            } },
                                            label = { Text("设定 · ${setting.category}") },
                                            modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                editedStoryTime = proposal.updatedStoryTime.orEmpty()
                                                editedEventTimeTag = proposal.newEvent?.timeTag.orEmpty()
                                                editedEventContent = proposal.newEvent?.content.orEmpty()
                                                editedSettings = proposal.atemporalSettings.map { it.copy() }
                                                isEditingTimeline = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Text("取消", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Button(
                                            onClick = {
                                                val modifiedProposal = proposal.copy(
                                                    updatedStoryTime = editedStoryTime.trim().ifBlank { null },
                                                    newEvent = proposal.newEvent?.copy(
                                                        timeTag = editedEventTimeTag.trim(),
                                                        content = editedEventContent.trim()
                                                    ),
                                                    atemporalSettings = editedSettings
                                                )
                                                viewModel.applyTimelineProposal(modifiedProposal)
                                                isEditingTimeline = false
                                            },
                                            enabled = (proposal.newEvent == null || editedEventContent.isNotBlank()) &&
                                                (editedStoryTime.isNotBlank() || editedEventContent.isNotBlank() || editedSettings.any { it.isSelected && it.content.isNotBlank() }),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                            shape = EchoTokens.Radius.shapePill
                                        ) {
                                            Text("应用", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(EchoTokens.Radius.shapeSm)
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    editedStoryTime = proposal.updatedStoryTime.orEmpty()
                                                    editedEventTimeTag = proposal.newEvent?.timeTag.orEmpty()
                                                    editedEventContent = proposal.newEvent?.content.orEmpty()
                                                    isEditingTimeline = true
                                                }
                                            ),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (!proposal.updatedStoryTime.isNullOrBlank()) {
                                            Text(
                                                text = "🕒 推进时空：${proposal.updatedStoryTime}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                            )
                                        }

                                        if (proposal.newEvent != null) {
                                            if (proposal.action == "UPDATE" && !proposal.previousEventContent.isNullOrBlank()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                            shape = EchoTokens.Radius.shapeSm
                                                        )
                                                        .padding(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = "原事件：${proposal.previousEventContent}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "充实更新：[${proposal.newEvent.timeTag}] ${proposal.newEvent.content}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = "📌 新增事件：[${proposal.newEvent.timeTag}] 【${proposal.newEvent.category.displayName}】${proposal.newEvent.content}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    editedSettings.forEachIndexed { index, setting ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = setting.isSelected, onCheckedChange = { selected ->
                                                editedSettings = editedSettings.mapIndexed { i, item ->
                                                    if (i == index) item.copy(isSelected = selected) else item
                                                }
                                            })
                                            Text("【${setting.category}】${setting.content}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f).clickable {
                                                    editedSettings = editedSettings.mapIndexed { i, item ->
                                                        if (i == index) item.copy(isSelected = !item.isSelected) else item
                                                    }
                                                })
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { isEditingTimeline = true },
                                            enabled = proposal.newEvent != null || !proposal.updatedStoryTime.isNullOrBlank() || editedSettings.isNotEmpty(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) { Text("编辑", style = MaterialTheme.typography.labelSmall) }
                                        TextButton(
                                            onClick = { viewModel.dismissTimelineProposal() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("忽略", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Button(
                                            onClick = { viewModel.applyTimelineProposal(proposal.copy(atemporalSettings = editedSettings)) },
                                            enabled = proposal.newEvent != null || !proposal.updatedStoryTime.isNullOrBlank() || editedSettings.any { it.isSelected && it.content.isNotBlank() },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                            shape = EchoTokens.Radius.shapePill
                                        ) {
                                            Text("确认应用", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 时间线变动提示显示数秒后自动消失（退场由下方 AnimatedVisibility 平滑收起），
                // 手动「查看/关闭」仍即时生效
                LaunchedEffect(timelineUpdateNotice) {
                    if (timelineUpdateNotice != null) {
                        kotlinx.coroutines.delay(5000)
                        viewModel.dismissTimelineUpdateNotice()
                    }
                }

                // 时间线自动增量更新提醒胶囊
                AnimatedVisibility(
                    visible = timelineUpdateNotice != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    timelineUpdateNotice?.let { notice ->
                        EchoGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            shape = EchoTokens.Radius.shapeMd
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.HistoryEdu,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = notice,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            viewModel.dismissTimelineUpdateNotice()
                                            showSettingsDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("查看", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissTimelineUpdateNotice() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "关闭",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 需求 6：模型回复时排队消息悬浮卡片 (UI 严格按照 media_1789390149204.png 设计落地)
                AnimatedVisibility(
                    visible = messageQueue.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    MessageQueueCard(
                        queue = messageQueue,
                        isPaused = isQueuePaused,
                        onTogglePause = { viewModel.toggleQueuePause() },
                        onRecall = { id ->
                            val recalled = viewModel.recallQueuedMessage(id)
                            if (recalled != null) {
                                inputText = if (inputText.isBlank()) recalled.content else "$inputText\n${recalled.content}"
                                if (recalled.attachments.isNotEmpty()) {
                                    selectedAttachments = selectedAttachments + recalled.attachments
                                }
                            }
                        },
                        onEdit = { msg -> editingQueueItem = msg },
                        onRemove = { id -> viewModel.removeQueuedMessage(id) },
                        onMove = { from, to -> viewModel.moveQueuedMessage(from, to) }
                    )
                }

                ChatInputBar(
                    hazeState = hazeState,
                    inputText = inputText,
                    onInputChange = {
                        inputText = it
                        ChatViewModel.saveDraft(conversationId, it)
                    },
                    quotedText = activeQuotedText,
                    onClearQuote = { activeQuotedText = null },
                    onSend = {
                        val trimmedInput = inputText.trim()
                        val hasContent = trimmedInput.isNotBlank() || selectedAttachments.isNotEmpty() || !activeQuotedText.isNullOrBlank()
                        if (hasContent && !isProcessingAttachments) {
                            val finalPrompt = if (!activeQuotedText.isNullOrBlank()) {
                                val quoteBlock = activeQuotedText!!.trim().lines().joinToString("\n") { "> $it" }
                                if (trimmedInput.isNotBlank()) {
                                    "$quoteBlock\n\n针对以上内容：\n$trimmedInput"
                                } else {
                                    "$quoteBlock\n\n针对以上内容："
                                }
                            } else {
                                inputText
                            }
                            val editSource = pendingEditSource
                            if (editSource != null) {
                                preserveScrollForBranchGeneration = true
                                val targetAssistantGroupId = editSource.variantGroupId
                                    ?.let { pairedVariantGroupId(it) }
                                    ?: "turn_${editSource.id}_assistant"
                                streamingBranchGroupId = targetAssistantGroupId
                                val userGroupId = editSource.variantGroupId ?: "turn_${editSource.id}_user"
                                val targetIndex = displayMessages.indexOfFirst {
                                    it.message.id == editSource.id || it.groupId == userGroupId
                                }
                                if (targetIndex >= 0) {
                                    scope.launch {
                                        try {
                                            listState.animateScrollToItem(targetIndex)
                                        } catch (_: Exception) {}
                                    }
                                }
                                viewModel.sendEditedMessage(editSource, finalPrompt, selectedAttachments)
                            } else {
                                preserveScrollForBranchGeneration = false
                                streamingBranchGroupId = null
                                autoFollowOutput = true
                                viewModel.sendMessage(finalPrompt, selectedAttachments)
                            }
                            inputText = ""
                            activeQuotedText = null
                            ChatViewModel.saveDraft(conversationId, "")
                            pendingEditSource = null
                            selectedAttachments = emptyList()
                            attachmentStatus = null
                        }
                    },
                    isGenerating = isGenerating,
                    onStopGeneration = { viewModel.stopGeneration() },
                    attachments = selectedAttachments,
                    onRemoveAttachment = { attachment ->
                        selectedAttachments = selectedAttachments.filter { it != attachment }
                    },
                    isProcessingAttachments = isProcessingAttachments,
                    attachmentStatus = attachmentStatus,
                    onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onPickImage = { imagePickerLauncher.launch(arrayOf("image/*")) },
                    onOcrImages = {
                        attachmentStatus = "请选择需要OCR的图片"
                        ocrImagePickerLauncher.launch(arrayOf("image/*"))
                    },
                    enableWebSearch = tempSettings.enableWebSearch,
                    onWebSearchChange = { enabled ->
                        viewModel.updateTempSettings(tempSettings.copy(enableWebSearch = enabled))
                    },
                    enableThinking = tempSettings.enableThinking,
                    thinkingEffort = tempSettings.thinkingEffort,
                    onThinkingChange = { enabled, effort ->
                        viewModel.updateTempSettings(tempSettings.copy(enableThinking = enabled, thinkingEffort = effort))
                    },
                    showThinkingPopover = showThinkingPopover,
                    onThinkingPopoverChange = { showThinkingPopover = it },
                    isRoleplay = uiState.isRoleplay,
                    onPlotActionClick = { showPlotActionDialog = true },
                    readableBackdrop = readableBackdrops.bottom,
                    modelName = currentModelOption?.modelName ?: currentModel ?: uiState.modelName,
                    apiType = currentModelOption?.apiType ?: "openai",
                    reasoningCapability = currentModelOption?.reasoningCapability,
                    isBarsHidden = isBarsHidden,
                    onBarsHiddenChange = { isBarsHidden = it }
                )
            }
        }
) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // N-2：悬浮跳转按钮保持原有底部偏移；v2.7.7 需求 6：消息列表底部间距单独收窄——
            // 滑到最底部时最后一条回复与输入栏顶部的距离由 16dp 收窄为 4dp
            val bottomBarOverlay = paddingValues.calculateBottomPadding() + EchoTokens.Spacing.lg
            val listBottomPadding = paddingValues.calculateBottomPadding() + 4.dp
            val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val topFloatingBarHeight = 68.dp + (if (error != null) 60.dp else 0.dp)

            // 1. 底层：全屏贯通的消息列表，向上滚动时平滑穿透悬浮顶栏与错误提示
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // 消息列表 (全屏延伸，向上滚动时平滑穿透悬浮工具栏和报错弹窗，被毛玻璃实时模糊)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    // v2.7.4 需求 1：水平 contentPadding 与底部输入栏/顶部工具栏统一为 12dp——
                    // 状态胶囊右缘与输入气泡右缘精确对齐（原先列表 14dp 与界面骨架 12dp 不一致）
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = statusBarTopPadding + topFloatingBarHeight,
                        bottom = listBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 空状态
                    if (displayMessages.isEmpty() && currentResponse.isEmpty() && currentThinking.isEmpty()) {
                        item {
                            EmptyChatPlaceholder(
                                isRoleplay = uiState.isRoleplay,
                                characterName = uiState.roleplayCharacter?.name,
                                scenarioTitle = uiState.roleplayScenario?.name,
                                characterAvatarUri = uiState.roleplayCharacter?.avatarUri,
                                onTriggerOpening = { viewModel.triggerCharacterOpening() }
                            )
                        }
                    }

                    // 消息列表
                    val currentAssistantModelName = currentModelOption?.modelName ?: currentModel ?: uiState.modelName ?: "AI"
                    itemsIndexed(
                        items = displayMessages,
                        key = { _, item -> item.groupId ?: "${item.message.id}_${item.message.createdAt}_${item.message.role}" }
                    ) { index, displayItem ->
                        val message = displayItem.message
                        val isUser = message.role == "user"
                        val prevItem = if (index > 0) displayMessages.getOrNull(index - 1) else null
                        val isPrevAssistant = prevItem?.message?.role == "assistant"
                        // 加大用户输入气泡和模型上一次输出之间的距离
                        val extraTopSpacing = if (isUser && isPrevAssistant) 18.dp else 0.dp

                        val resolvedAssistantModelName = message.modelName?.ifBlank { null }
                            ?: messageModelMap[message.id]
                            ?: messageModelMap[message.createdAt]
                            ?: uiState.modelName?.ifBlank { null }
                            ?: currentAssistantModelName
                        val isBranchStreamingHere = streamingBranchGroupId != null &&
                            displayItem.groupId == streamingBranchGroupId &&
                            (isGenerating || currentResponse.isNotEmpty() || currentThinking.isNotEmpty())

                        val totalVariantsWithStreaming = if (isBranchStreamingHere) {
                            (displayItem.variantInfo?.total ?: 1) + 1
                        } else {
                            displayItem.variantInfo?.total ?: 1
                        }
                        val selectedVariantIndex = if (isBranchStreamingHere) {
                            variantSelections[streamingBranchGroupId!!] ?: totalVariantsWithStreaming
                        } else {
                            displayItem.variantInfo?.currentIndex ?: 1
                        }
                        val showStreamingBubbleHere = isBranchStreamingHere && selectedVariantIndex == totalVariantsWithStreaming

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = extraTopSpacing)
                        ) {
                            if (showStreamingBubbleHere) {
                                // 在原位以正在生成的最新版本渲染
                                MessageBubble(
                                    message = Message(
                                        conversationId = conversationId,
                                        role = "assistant",
                                        content = currentResponse,
                                        thinkingContent = currentThinking.ifEmpty { null },
                                        variantGroupId = streamingBranchGroupId,
                                        variantIndex = totalVariantsWithStreaming
                                    ),
                                    hazeState = hazeState,
                                    readableBackdrop = readableBackdrops.content,
                                    isGenerating = true,
                                    thinkingEffort = tempSettings.thinkingEffort,
                                    assistantAvatarRevision = modelAvatarRevision,
                                    assistantApiConfigId = currentModelOption?.apiConfigId,
                                    assistantModelName = currentAssistantModelName,
                                    reconnectStatus = reconnectStatus,
                                    liveKeyErrors = liveKeyErrors,
                                    variantInfo = VariantInfo(
                                        groupId = streamingBranchGroupId!!,
                                        currentIndex = totalVariantsWithStreaming,
                                        total = totalVariantsWithStreaming,
                                        availableIndices = (displayItem.variantInfo?.availableIndices ?: listOf(1)) + totalVariantsWithStreaming
                                    ),
                                    onVariantSelected = { groupId, index ->
                                        preserveScrollForBranchGeneration = true
                                        autoFollowOutput = false
                                        variantSelections[groupId] = index
                                        pairedVariantGroupId(groupId)?.let { pairedGroup ->
                                            variantSelections[pairedGroup] = index
                                        }
                                    },
                                    translatingThinking = false,
                                    onTranslateThinking = null,
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(currentResponse))
                                    },
                                    onCopyThinking = {
                                        clipboardManager.setText(AnnotatedString(currentThinking))
                                    },
                                    onQuote = if (currentResponse.isNotBlank()) {
                                        {
                                            val quoteBlock = currentResponse.lines().joinToString("\n") { line -> "> $line" } + "\n针对以上内容：\n"
                                            inputText = if (inputText.isBlank()) quoteBlock else "$inputText\n\n$quoteBlock"
                                        }
                                    } else null,
                                    customAvatarUri = uiState.roleplayCharacter?.avatarUri ?: uiState.modelAvatarUri
                                )
                            } else {
                                val dynamicVariantInfo = if (isBranchStreamingHere) {
                                    VariantInfo(
                                        groupId = streamingBranchGroupId!!,
                                        currentIndex = selectedVariantIndex,
                                        total = totalVariantsWithStreaming,
                                        availableIndices = (displayItem.variantInfo?.availableIndices ?: listOf(1)) + totalVariantsWithStreaming
                                    )
                                } else {
                                    displayItem.variantInfo
                                }
                                val isLastAssistantTurn = !isGenerating && message.role == "assistant" &&
                                    displayItem == displayMessages.lastOrNull { it.message.role == "assistant" }
                                MessageBubble(
                                    message = message,
                                    hazeState = hazeState,
                                    readableBackdrop = readableBackdrops.content,
                                    assistantAvatarRevision = modelAvatarRevision,
                                    assistantApiConfigId = currentModelOption?.apiConfigId,
                                    assistantModelName = resolvedAssistantModelName,
                                    variantInfo = dynamicVariantInfo,
                                    onVariantSelected = { groupId, index ->
                                        preserveScrollForBranchGeneration = true
                                        autoFollowOutput = false
                                        variantSelections[groupId] = index
                                        pairedVariantGroupId(groupId)?.let { pairedGroup ->
                                            if (messages.any { it.variantGroupId == pairedGroup && it.variantIndex == index }) {
                                                variantSelections[pairedGroup] = index
                                            }
                                        }
                                    },
                                    translatingThinking = translatingMessageIds.contains(message.id),
                                    onTranslateThinking = { msg -> viewModel.translateMessageThinking(msg) },
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(message.content))
                                    },
                                    onCopyThinking = {
                                        message.thinkingContent?.let {
                                            clipboardManager.setText(AnnotatedString(it))
                                        }
                                    },
                                    onQuote = if (message.role == "user" && message.content.isNotBlank()) {
                                        {
                                            activeQuotedText = message.content.trim()
                                        }
                                    } else null,
                                    onBranch = if (!isGenerating && message.role == "assistant" && message.id > 0) {
                                        {
                                            val targetIdx = displayMessages.indexOfFirst { it.message.id == message.id }
                                            val messagesToBranch = if (targetIdx >= 0) {
                                                displayMessages.take(targetIdx + 1).map { it.message }
                                            } else {
                                                null
                                            }
                                            viewModel.createBranch(message.id, messagesToBranch) { newId, branchTitle ->
                                                branchSuccessDialog = BranchSuccessDialogState(newId, branchTitle)
                                            }
                                        }
                                    } else null,
                                    onRegenerate = if (isLastAssistantTurn) {
                                        {
                                            preserveScrollForBranchGeneration = true
                                            autoFollowOutput = false
                                            val targetGroupId = displayItem.groupId ?: message.variantGroupId ?: "reply_${message.id}"
                                            streamingBranchGroupId = targetGroupId
                                            variantSelections.remove(targetGroupId)
                                            pairedVariantGroupId(targetGroupId)?.let { variantSelections.remove(it) }
                                            viewModel.regenerateLastMessage(message)
                                        }
                                    } else null,
                                    onEdit = {
                                        if (message.role == "user") {
                                            val parsed = parseQuotedMessage(message.content)
                                            if (parsed != null) {
                                                activeQuotedText = parsed.quoteText
                                                inputText = parsed.replyText
                                            } else {
                                                inputText = message.content
                                                activeQuotedText = null
                                            }
                                            pendingEditSource = message
                                            autoFollowOutput = false
                                            selectedAttachments = emptyList()
                                            attachmentStatus = null
                                        } else {
                                            editingAssistantMessage = message
                                            editingAssistantContent = TextFieldValue(message.content)
                                        }
                                    },
                                    onEditInPlace = if (message.role == "user") {
                                        {
                                            editingUserMessage = message
                                            editingUserContent = TextFieldValue(message.content)
                                        }
                                    } else null,
                                    onDelete = {
                                        messagePendingDelete = message
                                    },
                                    customAvatarUri = uiState.roleplayCharacter?.avatarUri ?: uiState.modelAvatarUri,
                                    onTogglePin = { msg -> viewModel.togglePinMessage(msg) },
                                    onToggleExclude = { msg -> viewModel.toggleExcludeMessage(msg) },
                                    settleSignal = message.role == "assistant" && message.id == settledMessageId
                                )
                            }

                            // 如果该轮还没有已入库的 assistant 消息，但在对应 user 消息后正在流式生成
                            // v2.6.5：挂载点除配对 user 分组/turn_ 前缀外，新增生成锚点（触发本轮的
                            // 用户消息）——删除同一位置的其他回复时，流式气泡钉在锚点后不跳位
                            val hasAssistantItemForThisTurn = streamingBranchGroupId != null &&
                                displayMessages.any { it.groupId == streamingBranchGroupId }
                            val isAnchorHostHere = isGeneratingAnchorHostItem(
                                itemGroupId = displayItem.groupId,
                                itemMessageId = displayItem.message.id,
                                anchorUserMessageId = generatingAnchor?.userMessageId,
                                anchorUserGroupId = generatingAnchor?.userGroupId
                            )
                            if (
                                streamingBranchGroupId != null && !hasAssistantItemForThisTurn &&
                                (isAnchorHostHere ||
                                    (streamingBranchGroupId != null &&
                                        isStreamingBranchHostItem(displayItem.groupId, streamingBranchGroupId!!, displayItem.message.id))) &&
                                (isGenerating || currentResponse.isNotEmpty() || currentThinking.isNotEmpty())
                            ) {
                                Spacer(modifier = Modifier.height(14.dp))
                                MessageBubble(
                                    message = Message(
                                        conversationId = conversationId,
                                        role = "assistant",
                                        content = currentResponse,
                                        thinkingContent = currentThinking.ifEmpty { null },
                                        variantGroupId = streamingBranchGroupId
                                    ),
                                    hazeState = hazeState,
                                    readableBackdrop = readableBackdrops.content,
                                    isGenerating = true,
                                    thinkingEffort = tempSettings.thinkingEffort,
                                    assistantAvatarRevision = modelAvatarRevision,
                                    assistantApiConfigId = currentModelOption?.apiConfigId,
                                    assistantModelName = currentAssistantModelName,
                                    reconnectStatus = reconnectStatus,
                                    liveKeyErrors = liveKeyErrors,
                                    translatingThinking = false,
                                    onTranslateThinking = null,
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(currentResponse))
                                    },
                                    onCopyThinking = {
                                        clipboardManager.setText(AnnotatedString(currentThinking))
                                    },
                                    customAvatarUri = uiState.roleplayCharacter?.avatarUri ?: uiState.modelAvatarUri
                                )
                            }
                        }
                    }

                    // 检查当前流式分支是否在消息列表中成功挂载
                    // v2.6.5：生成锚点命中同样视为已挂载——锚点（触发本轮的用户消息）存在时，
                    // 内联挂载已承接流式气泡，禁用底部兜底，避免同一回复出现两份
                    val isAnchorHostMounted = streamingBranchGroupId != null && generatingAnchor != null && displayMessages.any { item ->
                        isGeneratingAnchorHostItem(
                            itemGroupId = item.groupId,
                            itemMessageId = item.message.id,
                            anchorUserMessageId = generatingAnchor?.userMessageId,
                            anchorUserGroupId = generatingAnchor?.userGroupId
                        )
                    }
                    val isBranchStreamingMounted = (streamingBranchGroupId != null && displayMessages.any { item ->
                        item.groupId == streamingBranchGroupId ||
                        isStreamingBranchHostItem(item.groupId, streamingBranchGroupId!!, item.message.id)
                    }) || isAnchorHostMounted

                    // 当前正在生成的内容（若为常规生成，或分支宿主/生成锚点不存在/被删除时，在底部稳妥兜底渲染，绝不丢失流式气泡）
                    if (!isBranchStreamingMounted && (currentThinking.isNotEmpty() || currentResponse.isNotEmpty() || isGenerating)) {
                        item(key = "streaming_assistant_message") {
                            MessageBubble(
                                message = Message(
                                    conversationId = conversationId,
                                    role = "assistant",
                                    content = currentResponse,
                                    thinkingContent = currentThinking.ifEmpty { null }
                                ),
                                hazeState = hazeState,
                                readableBackdrop = readableBackdrops.content,
                                isGenerating = true,
                                thinkingEffort = tempSettings.thinkingEffort,
                                assistantAvatarRevision = modelAvatarRevision,
                                assistantApiConfigId = currentModelOption?.apiConfigId,
                                assistantModelName = currentAssistantModelName,
                                reconnectStatus = reconnectStatus,
                                liveKeyErrors = liveKeyErrors,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(currentResponse))
                                },
                                onCopyThinking = {
                                    clipboardManager.setText(AnnotatedString(currentThinking))
                                },
                                customAvatarUri = uiState.roleplayCharacter?.avatarUri ?: uiState.modelAvatarUri
                            )
                        }
                    }

                    item(key = "chat_bottom_anchor") {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

            // 2. 顶部悬浮工具栏与错误提示：悬浮在最顶层，直接复用输入框相同 Surface + echoHazePanel 结构，无边缘包裹，半透明透字
            val toolbarShape = RoundedCornerShape(22.dp)
            val glass = echoGlassPalette()
            val toolbarTint = glass.input
            val toolbarContentColor = readableTextColorFor(
                background = toolbarTint,
                fallbackSurface = readableBackdrops.top
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                AnimatedContent(
                    targetState = isBarsHidden,
                    transitionSpec = {
                        // 局部变量先求值，规避 K2 对表达式内显式泛型的解析歧义
                        val topBarEnterSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec<Float>(com.aiassistant.ui.theme.EchoMotion.Duration.standard)
                        val topBarExitSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec<Float>(com.aiassistant.ui.theme.EchoMotion.Duration.fast)
                        (fadeIn(topBarEnterSpec) + scaleIn(initialScale = 0.8f, transformOrigin = TransformOrigin(0f, 0f), animationSpec = topBarEnterSpec))
                            .togetherWith(fadeOut(topBarExitSpec) + scaleOut(targetScale = 0.8f, transformOrigin = TransformOrigin(0f, 0f), animationSpec = topBarExitSpec))
                    },
                    label = "topBarHiddenAnim"
                ) { hidden ->
                    if (hidden) {
                        val topPulseTransition = rememberInfiniteTransition(label = "topPulse")
                        val topPulseScale by topPulseTransition.animateFloat(
                            initialValue = 1.0f,
                            targetValue = 1.15f,
                            animationSpec = com.aiassistant.ui.theme.EchoMotion.reverseCycleSpec<Float>(com.aiassistant.ui.theme.EchoMotion.Cycle.pulse),
                            label = "topPulseScale"
                        )
                        val topPulseAlpha by topPulseTransition.animateFloat(
                            initialValue = 0.55f,
                            targetValue = 0.15f,
                            animationSpec = com.aiassistant.ui.theme.EchoMotion.reverseCycleSpec<Float>(com.aiassistant.ui.theme.EchoMotion.Cycle.pulse),
                            label = "topPulseAlpha"
                        )
                        val topPulseColor = MaterialTheme.colorScheme.primary
                        Box(
                            modifier = Modifier
                                .padding(start = 2.dp, top = 2.dp)
                                .size(34.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val strokeWidth = 1.2.dp.toPx()
                                val baseRadius = (size.minDimension - strokeWidth) / 2f
                                val currentRadius = baseRadius * topPulseScale
                                drawCircle(
                                    color = topPulseColor.copy(alpha = topPulseAlpha),
                                    radius = currentRadius,
                                    center = center,
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                            Surface(
                                onClick = { isBarsHidden = false },
                                shape = CircleShape,
                                color = glass.control,
                                contentColor = MaterialTheme.colorScheme.primary,
                                border = BorderStroke(1.2.dp, glass.outlineSelected),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "取消隐藏并恢复顶部栏",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .echoHazePanel(
                                    hazeState = hazeState,
                                    shape = toolbarShape,
                                    tint = toolbarTint,
                                    blurRadius = 16.dp,
                                    highlightAlpha = 0.025f
                                ),
                            shape = toolbarShape,
                            color = Color.Transparent,
                            contentColor = toolbarContentColor,
                            border = BorderStroke(1.dp, glass.outline),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.leaveConversation(onNavigateBack) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                                ChatHeaderTitle(
                                    title = uiState.conversationTitle.ifBlank { "新对话" },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    onLongClick = {
                                        renameText = uiState.conversationTitle
                                        showRenameDialog = true
                                    }
                                )
                                ContextUsageButton(
                                    usage = contextUsage.usage,
                                    canCompress = contextUsage.usage?.canCompress == true,
                                    onClick = {
                                        viewModel.refreshContextUsage()
                                        showContextUsageDialog = true
                                    }
                                )
                                if (uiState.isRoleplay) {
                                    IconButton(
                                        onClick = { showStoryManagerDialog = true },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AutoStories,
                                            contentDescription = "故事创作与参数设置",
                                            tint = toolbarContentColor
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { showSettingsDialog = true },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Tune,
                                            contentDescription = "对话设置",
                                            tint = toolbarContentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                error?.let { errorMsg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    // P1-2③ 横幅登场：MutableTransitionState 初值 false→true，首次组合即播滑入+淡入
                    AnimatedVisibility(
                        visibleState = remember {
                            androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
                        },
                        // A5：reduced motion 时横幅直接呈现（功能信息保留，装饰动画短路）
                        enter = if (com.aiassistant.ui.theme.rememberReducedMotion()) {
                            androidx.compose.animation.EnterTransition.None
                        } else {
                            slideInVertically(
                                animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec<androidx.compose.ui.unit.IntOffset>(com.aiassistant.ui.theme.EchoMotion.Duration.standard),
                                initialOffsetY = { -it }
                            ) + fadeIn(com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.standard))
                        },
                        exit = if (com.aiassistant.ui.theme.rememberReducedMotion()) {
                            androidx.compose.animation.ExitTransition.None
                        } else {
                            shrinkVertically(
                                animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast)
                            ) + fadeOut(com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast))
                        }
                    ) {
                        val errorShape = RoundedCornerShape(22.dp)
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        val errorSemantic = rememberEchoSemanticColors().error
                        val errorTint = errorSemantic.container.copy(alpha = if (isDark) 0.88f else 0.92f)
                        val errorBorder = errorSemantic.border.copy(alpha = if (isDark) 0.35f else 0.65f)
                        val errorContentColor = errorSemantic.onContainer

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .echoHazePanel(
                                    hazeState = hazeState,
                                    shape = errorShape,
                                    tint = errorTint,
                                    blurRadius = 16.dp,
                                    highlightAlpha = 0.025f
                                ),
                            shape = errorShape,
                            color = Color.Transparent,
                            contentColor = errorContentColor,
                            border = BorderStroke(1.dp, errorBorder),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = errorSemantic.main,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = errorContentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearError() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "关闭",
                                        tint = errorContentColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                contextUsage.statusMessage?.let { statusMsg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    // P1-2③ 横幅登场：MutableTransitionState 初值 false→true，首次组合即播滑入+淡入
                    AnimatedVisibility(
                        visibleState = remember {
                            androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
                        },
                        // A5：reduced motion 时横幅直接呈现（功能信息保留，装饰动画短路）
                        enter = if (com.aiassistant.ui.theme.rememberReducedMotion()) {
                            androidx.compose.animation.EnterTransition.None
                        } else {
                            slideInVertically(
                                animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec<androidx.compose.ui.unit.IntOffset>(com.aiassistant.ui.theme.EchoMotion.Duration.standard),
                                initialOffsetY = { -it }
                            ) + fadeIn(com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.standard))
                        },
                        exit = if (com.aiassistant.ui.theme.rememberReducedMotion()) {
                            androidx.compose.animation.ExitTransition.None
                        } else {
                            shrinkVertically(
                                animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast)
                            ) + fadeOut(com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast))
                        }
                    ) {
                        val isWarning = statusMsg.startsWith("⚠️")
                        val isSuccess = statusMsg.startsWith("✅")
                        val infoShape = RoundedCornerShape(22.dp)
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        val semantic = rememberEchoSemanticColors()
                        val statusGroup = when {
                            isWarning -> semantic.warning
                            isSuccess -> semantic.success
                            else -> semantic.info
                        }
                        val tintColor = statusGroup.container.copy(alpha = if (isDark) 0.89f else 0.92f)
                        val borderColor = statusGroup.border.copy(alpha = if (isDark) 0.40f else 0.70f)
                        val bannerContentColor = statusGroup.onContainer

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .echoHazePanel(
                                    hazeState = hazeState,
                                    shape = infoShape,
                                    tint = tintColor,
                                    blurRadius = 16.dp,
                                    highlightAlpha = 0.025f
                                ),
                            shape = infoShape,
                            color = Color.Transparent,
                            contentColor = bannerContentColor,
                            border = BorderStroke(1.dp, borderColor),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (contextUsage.isCompressing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = bannerContentColor
                                    )
                                } else {
                                    Icon(
                                        if (isWarning) Icons.Default.Warning else if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = bannerContentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = statusMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = bannerContentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                if (contextUsage.pendingAutoTier != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.applyPendingAutoCompression() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Text("立即压缩", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TextButton(
                                        onClick = { viewModel.dismissPendingAutoCompression() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Text("忽略", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.clearContextStatusMessage() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "关闭",
                                        tint = bannerContentColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            TransientLazyListScrollbar(
                listState = listState,
                visible = showScrollControls,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
            )

            SideAnchorNavigator(
                items = chatNavItems,
                listState = listState,
                visible = showScrollControls,
                hazeState = hazeState,
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 4.dp)
            )

            // v2.7.3 流畅度：组合期直读 listState.layoutInfo 会随列表每次测量/滚动更新而失效本作用域，
            // 改经 derivedStateOf 只在「是否多于一页」布尔翻转时重组（流式期间列表每帧重排不再扩散）
            val hasMultipleChatItems by remember {
                derivedStateOf { listState.layoutInfo.totalItemsCount > 1 }
            }
            ChatScrollJumpButtons(
                visible = showScrollControls && hasMultipleChatItems,
                onJumpToTop = {
                    autoFollowOutput = false
                    scrollControlsVisibilityState.extendVisibility(2800L)
                    scope.launch {
                        val firstVisible = listState.firstVisibleItemIndex
                        if (firstVisible > 8) {
                            listState.scrollToItem(6)
                        }
                        listState.animateScrollToItem(0)
                    }
                },
                onJumpToPrevInput = {
                    autoFollowOutput = false
                    scrollControlsVisibilityState.extendVisibility(2800L)
                    scope.launch {
                        val currentFirst = listState.firstVisibleItemIndex
                        val target = displayMessages.indices.reversed().firstOrNull { idx ->
                            idx < currentFirst && displayMessages[idx].message.role == "user"
                        } ?: 0
                        listState.animateScrollToItem(target)
                    }
                },
                onJumpToNextInput = {
                    autoFollowOutput = false
                    scrollControlsVisibilityState.extendVisibility(2800L)
                    scope.launch {
                        val currentFirst = listState.firstVisibleItemIndex
                        val target = displayMessages.indices.firstOrNull { idx ->
                            idx > currentFirst && displayMessages[idx].message.role == "user"
                        } ?: (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                        listState.animateScrollToItem(target)
                    }
                },
                onJumpToBottom = {
                    autoFollowOutput = true
                    scrollControlsVisibilityState.extendVisibility(2800L)
                    scope.launch {
                        val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                        val firstVisible = listState.firstVisibleItemIndex
                        if (lastIndex - firstVisible > 8) {
                            listState.scrollToItem((lastIndex - 6).coerceAtLeast(0))
                        }
                        listState.animateScrollToItem(lastIndex)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 18.dp,
                        bottom = bottomBarOverlay
                    )
            )

            if (showThinkingPopover) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            showThinkingPopover = false
                        }
                )
            }

            EchoTextToolbarHost(toolbar = textToolbar) { quotedText ->
                val clean = quotedText.trim()
                if (clean.isNotBlank()) {
                    activeQuotedText = clean
                }
            }
        }
    }
}
}

    // 删除消息二次确认对话框
    messagePendingDelete?.let { targetMsg ->
        val isUserMsg = targetMsg.role == "user"
        AlertDialog(
            onDismissRequest = { messagePendingDelete = null },
            title = {
                Text(
                    text = if (isUserMsg) "删除提问消息" else "删除 AI 回复",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("确定要删除此条${if (isUserMsg) "提问消息" else "AI 回复"}吗？删除后不可恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 若被删除的消息与正在进行的流式分支关联，解绑分支并恢复常规底部生成展示
                        val isAssociatedWithStreaming = isGenerating && streamingBranchGroupId != null && (
                            targetMsg.variantGroupId == streamingBranchGroupId ||
                            targetMsg.variantGroupId?.let { pairedVariantGroupId(it) } == streamingBranchGroupId ||
                            streamingBranchGroupId!!.startsWith("turn_${targetMsg.id}_") ||
                            streamingBranchGroupId == "reply_${targetMsg.id}"
                        )
                        if (isAssociatedWithStreaming) {
                            streamingBranchGroupId = null
                            preserveScrollForBranchGeneration = false
                        }

                        // 锁定当前可见视口锚点：若被删除的消息在视口上方，在删除后平移 1 个索引避免视觉跳跃
                        val targetIdx = displayMessages.indexOfFirst { it.message.id == targetMsg.id }
                        val firstVisible = listState.firstVisibleItemIndex
                        val currentOffset = listState.firstVisibleItemScrollOffset
                        if (targetIdx >= 0 && firstVisible > targetIdx) {
                            scope.launch {
                                try {
                                    listState.scrollToItem((firstVisible - 1).coerceAtLeast(0), scrollOffset = currentOffset)
                                } catch (_: Exception) {}
                            }
                        }
                        viewModel.deleteMessage(targetMsg)
                        messagePendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { messagePendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 设置对话框
    if (showSettingsDialog) {
        ChatSettingsDialog(
            hazeState = hazeState,
            tempSettings = tempSettings,
            currentPrompt = uiState.systemPrompt,
            currentOption = currentModelOption,
            fallbackModel = currentModel ?: uiState.modelName,
            availableOptions = availableModelOptions,
            templates = promptTemplates,
            sessionMemories = sessionMemories,
            timelineNodes = timelineNodes,
            currentStoryTime = uiState.currentStoryTime,
            onUpdateCurrentStoryTime = { viewModel.updateCurrentStoryTime(it) },
            onAddTimelineNode = { timeTag, event, category -> viewModel.addTimelineNode(timeTag, event, category) },
            onUpdateTimelineNode = { viewModel.updateTimelineNode(it) },
            onDeleteTimelineNode = { viewModel.deleteTimelineNode(it) },
            onClearTimeline = { viewModel.clearTimeline() },
            isReconcilingTimeline = isReconcilingTimeline,
            reconcileTimelineProgress = timelineReconcileProgress,
            hasSavedTimelineDraft = liveReconcileDraft != null || viewModel.hasTimelineDraft(),
            checkpoint = timelineCheckpoint,
            newMessagesCountSinceCheckpoint = viewModel.getNewMessagesCountSinceCheckpoint(),
            onStartTimelineReconciliation = { viewModel.startTimelineReconciliation(startFromDraft = false, fromCheckpoint = false) },
            onContinueTimelineReconciliation = { viewModel.startTimelineReconciliation(startFromDraft = true, fromCheckpoint = false) },
            onReconcileFromCheckpoint = { viewModel.startTimelineReconciliation(startFromDraft = false, fromCheckpoint = true) },
            onOpenTimelineDraft = {
                if (isReconcilingTimeline) {
                    viewModel.openLiveDraftForReview()
                } else {
                    viewModel.openSavedDraftForReview()
                }
            },
            onClearTimelineDraft = { viewModel.clearTimelineDraft() },
            onCancelTimelineReconciliation = { viewModel.cancelTimelineReconciliation() },
            onAddSessionMemory = { viewModel.addSessionMemory(it) },
            onUpdateSessionMemory = { viewModel.updateSessionMemory(it) },
            onToggleSessionMemory = { id, enabled -> viewModel.toggleSessionMemory(id, enabled) },
            onDeleteSessionMemory = { viewModel.deleteSessionMemory(it) },
            onClearSessionMemories = { viewModel.clearSessionMemories() },
            onTempSettingsChange = { viewModel.updateTempSettings(it) },
            onDismiss = { showSettingsDialog = false },
            onSave = { settings, prompt ->
                viewModel.updateChatSettings(settings, prompt)
                showSettingsDialog = false
            },
            onModelSelected = { viewModel.switchModel(it) },
            onSavePromptTemplate = { name, content ->
                viewModel.savePromptTemplate(name, content)
            },
            onModelAvatarChanged = { modelAvatarRevision++ },
            onConvertToRoleplay = {
                showSettingsDialog = false
                showConvertToRoleplayDialog = true
            },
            conversationId = conversationId,
            currentConversationModelAvatarUri = uiState.modelAvatarUri,
            onUpdateConversationModelAvatar = { uri ->
                viewModel.updateConversationModelAvatar(uri)
                modelAvatarRevision++
            }
        )
    }

    // 全量历史时间轴梳理与校对审核弹窗
    if (showTimelineReconcileDialog && timelineReconcileResult != null) {
        TimelineReconcileDialog(
            hazeState = hazeState,
            initialResult = timelineReconcileResult!!,
            onDismiss = { viewModel.dismissTimelineReconcileDialog() },
            onApply = { currentTime, events, confirmedSettings ->
                viewModel.applyReconciledTimeline(currentTime, events, confirmedSettings)
            }
        )
    }

    if (showConvertToRoleplayDialog) {
        var charName by remember { mutableStateOf((uiState.conversationTitle.ifBlank { "故事主角" }).take(10)) }
        var charIdentity by remember { mutableStateOf("核心探索者") }
        var charPersonality by remember { mutableStateOf("沉着、机智、性格鲜明") }
        var scenarioName by remember { mutableStateOf("${uiState.conversationTitle.take(8)} · 世界观") }

        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showConvertToRoleplayDialog = false },
            title = { Text("转为角色扮演 / 故事创作") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "将当前对话平滑升级为故事创作会话。自动建立独立角色卡与世界观，完整保留全部聊天历史，解锁剧情推进动作与独立记忆体系。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = charName,
                        onValueChange = { charName = it },
                        label = { Text("主角名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = charIdentity,
                        onValueChange = { charIdentity = it },
                        label = { Text("主角身份/职业") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = charPersonality,
                        onValueChange = { charPersonality = it },
                        label = { Text("主角性格特征") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = scenarioName,
                        onValueChange = { scenarioName = it },
                        label = { Text("舞台/世界观名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.convertToRoleplay(
                        charName = charName,
                        charIdentity = charIdentity,
                        charPersonality = charPersonality,
                        scenarioName = scenarioName
                    ) {
                        showConvertToRoleplayDialog = false
                        Toast.makeText(context, "已成功升级为角色扮演故事", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("立即转换")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConvertToRoleplayDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (editingAssistantMessage != null) {
        // v2.7.2 需求 1/2：「跳转末尾」按钮与编辑器共用滚动状态，点击后光标锁定文字末尾并滚动到底
        val editScrollState = androidx.compose.foundation.rememberScrollState()
        val editScope = rememberCoroutineScope()
        EchoGlassDialog(
            onDismissRequest = { editingAssistantMessage = null },
            shape = EchoTokens.Radius.shapeXl,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("编辑模型回复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "直接修润、增删或调整模型的历史回复内容，修改后将直接持久化保存至当前会话历史。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    EchoScrollableTextEditor(
                        value = editingAssistantContent,
                        onValueChange = { editingAssistantContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        scrollState = editScrollState,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
                }
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 需求 1：左下角「向下」按钮——光标锁定到文字末尾并滚动到可视区底部
                    IconButton(
                        onClick = {
                            editingAssistantContent = editingAssistantContent.copy(
                                selection = TextRange(editingAssistantContent.text.length)
                            )
                            editScope.launch {
                                editScrollState.animateScrollTo(editScrollState.maxValue)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "光标跳转到文字末尾",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { editingAssistantMessage = null }) {
                        Text("取消", maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val msg = editingAssistantMessage ?: return@Button
                            viewModel.updateMessageContent(msg.id, editingAssistantContent.text)
                            editingAssistantMessage = null
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("保存修改", maxLines = 1)
                    }
                }
            }
        )
    }

    // 用户消息“仅编辑”对话框：保存后只更新该消息显示的内容，不重新发送、不触发重新生成
    if (editingUserMessage != null) {
        EchoGlassDialog(
            onDismissRequest = { editingUserMessage = null },
            shape = EchoTokens.Radius.shapeXl,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("仅修改消息内容", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "只修改这条消息显示的内容：不会重新发送提问，不会删除或重新生成后续回复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    EchoScrollableTextEditor(
                        value = editingUserContent,
                        onValueChange = { editingUserContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
                }
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { editingUserMessage = null }) {
                        Text("取消", maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val msg = editingUserMessage ?: return@Button
                            viewModel.updateMessageContent(msg.id, editingUserContent.text)
                            editingUserMessage = null
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("保存内容", maxLines = 1)
                    }
                }
            }
        )
    }

    if (showContextUsageDialog) {
        ContextUsageDialog(
            hazeState = hazeState,
            state = contextUsage,
            customContextLimit = tempSettings.contextWindowTokens,
            onUpdateContextLimit = { limit -> viewModel.updateConversationContextLimit(limit) },
            onDismiss = { showContextUsageDialog = false },
            onRefresh = { viewModel.refreshContextUsage() },
            onCompress = { viewModel.compressContextNow() },
            onGenerateRollingSummary = { viewModel.generateRollingSummaryNow() },
            onEditRollingSummary = { showRollingSummaryDialog = true },
            onSelectCompressionTier = { tier, rounds, percent ->
                viewModel.setCompressionTier(tier, rounds, percent)
            },
            onPreviewCompressionSettings = { rounds, percent ->
                viewModel.previewCompressionSettings(rounds, percent)
            }
        )
    }

    if (showRollingSummaryDialog) {
        val currentSummary = viewModel.getCurrentRollingSummary()
        RollingSummaryEditDialog(
            hazeState = hazeState,
            initialSummary = currentSummary,
            onDismiss = { showRollingSummaryDialog = false },
            onSave = { updatedSummary ->
                viewModel.updateRollingSummary(updatedSummary)
            },
            onClear = {
                viewModel.clearRollingSummary()
            }
        )
    }

    if (showStoryManagerDialog && uiState.roleplaySession != null) {
        StoryUnifiedSettingsDialog(
            hazeState = hazeState,
            session = uiState.roleplaySession!!,
            characters = uiState.roleplayCharacters,
            allCharacters = allAvailableCharacters,
            scenario = uiState.roleplayScenario,
            allScenarios = allAvailableScenarios,
            narrativeMode = uiState.narrativeMode,
            currentOption = currentModelOption,
            fallbackModel = currentModel ?: uiState.modelName,
            availableOptions = availableModelOptions,
            tempSettings = tempSettings,
            currentPrompt = uiState.systemPrompt,
            templates = promptTemplates,
            onDismiss = { showStoryManagerDialog = false },
            onSaveAll = { charIds, scenarioId, mode, plotSummary, settings, prompt ->
                viewModel.updateStorySessionContext(charIds, scenarioId, mode, plotSummary)
                viewModel.updateChatSettings(settings, prompt)
                showStoryManagerDialog = false
            },
            onPlotAction = { action, custom ->
                viewModel.sendPlotAction(action, custom)
                showStoryManagerDialog = false
            },
            onSummarizeMemories = {
                Toast.makeText(context, "正在提炼剧情摘要与关键事实...", Toast.LENGTH_SHORT).show()
                viewModel.summarizeAndExtractMemories(
                    onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                )
            },
            onNavigateToMemory = {
                showStoryManagerDialog = false
                onNavigateToRoleplayMemory(uiState.roleplaySession!!.id)
            },
            onOpenSmartAppend = {
                showStorySmartAnalyzeDialog = true
            },
            onConvertToNormal = {
                viewModel.convertToNormal {
                    showStoryManagerDialog = false
                    Toast.makeText(context, "已转为普通对话（设定已合并为系统提示词）", Toast.LENGTH_SHORT).show()
                }
            },
            onSaveLocalCharacter = { updatedChar ->
                viewModel.addNewLocalCharacter(updatedChar)
                Toast.makeText(context, "已保存「${updatedChar.name}」故事设定", Toast.LENGTH_SHORT).show()
            },
            onDeleteLocalCharacter = { char ->
                viewModel.deleteLocalCharacter(char)
                Toast.makeText(context, "已从故事中移除角色「${char.name}」", Toast.LENGTH_SHORT).show()
            },
            onSaveLocalScenario = { updatedSc ->
                viewModel.saveLocalScenario(updatedSc)
                Toast.makeText(context, "已保存「${updatedSc.name}」故事世界观设定", Toast.LENGTH_SHORT).show()
            },
            onDeleteLocalScenario = {
                viewModel.deleteLocalScenario()
                Toast.makeText(context, "已从故事中移除世界观设定", Toast.LENGTH_SHORT).show()
            },
            onModelSelected = { viewModel.switchModel(it) },
            onSavePromptTemplate = { name, content ->
                viewModel.savePromptTemplate(name, content)
            },
            onModelAvatarChanged = { modelAvatarRevision++ }
        )
    }

    if (showStorySmartAnalyzeDialog) {
        SmartAppendStoryDialog(
            hazeState = hazeState,
            onDismiss = { showStorySmartAnalyzeDialog = false },
            onAppendAndMerge = { chars, scenario, resMap ->
                showStorySmartAnalyzeDialog = false
                viewModel.appendAndMergeStoryBundle(chars, scenario, resMap) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (editingQueueItem != null) {
        var draftText by remember(editingQueueItem) { mutableStateOf(editingQueueItem!!.content) }
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { editingQueueItem = null },
            title = { Text("编辑排队消息") },
            text = {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    label = { Text("消息内容") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 240.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingQueueItem?.let {
                            viewModel.editQueuedMessage(it.id, draftText)
                        }
                        editingQueueItem = null
                    },
                    enabled = draftText.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingQueueItem = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (uiState.suggestedProposal != null) {
        EditableSettingProposalDialog(
            hazeState = hazeState,
            proposal = uiState.suggestedProposal!!,
            onDismiss = { viewModel.dismissProposedSetting() },
            onApply = { chars, sc ->
                viewModel.applyProposedSetting(chars, sc,
                    onSaved = { Toast.makeText(context, "已保存到当前故事专属设定", Toast.LENGTH_SHORT).show() },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() })
            }
        )
    }

    pendingReplyDirection?.let { prompt ->
        if (!replyDirectionDismissed) {
            ReplyDirectionDialog(prompt, hazeState,
                onDecision = { viewModel.answerReplyDirection(prompt, it) },
                onDismiss = { replyDirectionDismissed = true })
        } else {
            androidx.compose.ui.window.Popup(alignment = Alignment.BottomCenter) {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, modifier = Modifier.padding(16.dp)) {
                    TextButton(onClick = { replyDirectionDismissed = false }) { Text("等待回复方向 · 继续选择") }
                }
            }
        }
    }

    pendingContextFallbackPrompt?.let { promptState ->
        AlertDialog(
            onDismissRequest = {
                viewModel.handleContextFallbackDecision(com.aiassistant.domain.model.ContextFallbackChoice.IGNORE)
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "网络连接受阻与上下文回退",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "由于网络连接不畅或模型连接中断，请求未能正常完成。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "请选择如何处理本次历史上下文：\n• 回退：裁剪部分早期历史上下文并轻量化重试\n• 忽略：保持当前完整上下文，不执行自动回退\n• （当前对话）永久忽略：当前对话后续不再尝试回退，始终保持完整上下文",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.handleContextFallbackDecision(com.aiassistant.domain.model.ContextFallbackChoice.FALLBACK)
                    }
                ) {
                    Text("回退")
                }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.handleContextFallbackDecision(com.aiassistant.domain.model.ContextFallbackChoice.IGNORE)
                        }
                    ) {
                        Text("忽略")
                    }
                    TextButton(
                        onClick = {
                            viewModel.handleContextFallbackDecision(com.aiassistant.domain.model.ContextFallbackChoice.PERMANENTLY_IGNORE)
                        }
                    ) {
                        Text("（当前对话）永久忽略")
                    }
                }
            }
        )
    }

    if (showPlotActionDialog) {
        com.aiassistant.ui.screens.roleplay.PlotActionDialog(
            hazeState = hazeState,
            onAction = { action, custom ->
                viewModel.sendPlotAction(action, custom)
            },
            onProposeSetting = {
                val textToAnalyze = if (inputText.isNotBlank()) inputText else {
                    messages.takeLast(4).joinToString("\n") { "${it.role}: ${it.content}" }
                }
                if (textToAnalyze.isNotBlank()) {
                    Toast.makeText(context, "正在结合故事已有设定进行精准分析...", Toast.LENGTH_SHORT).show()
                    viewModel.analyzeAndProposeSettingFromInput(
                        text = textToAnalyze,
                        onProgress = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                        onNoProposal = { Toast.makeText(context, "未能从当前输入中提取到实质性设定变动", Toast.LENGTH_SHORT).show() },
                        onError = { err -> Toast.makeText(context, "识别失败: $err", Toast.LENGTH_SHORT).show() }
                    )
                } else {
                    Toast.makeText(context, "请先在输入框输入设定或剧情文本", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showPlotActionDialog = false }
        )
    }

    if (showRenameDialog) {
        EchoGlassDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(if (uiState.isRoleplay) "重命名故事" else "重命名对话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameConversation(renameText)
                        showRenameDialog = false
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    branchSuccessDialog?.let { dialogState ->
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { branchSuccessDialog = null },
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "分支创建成功",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "已为您生成包含当前回复及之前完整上下文的新分支：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = dialogState.branchTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "您可以留在当前对话继续探索，或立即跳转到新分支对话。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newId = dialogState.newConversationId
                        branchSuccessDialog = null
                        onNavigateToChat(newId)
                    }
                ) {
                    Text("跳转到新对话")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { branchSuccessDialog = null }
                ) {
                    Text("确定")
                }
            }
        )
    }
}
