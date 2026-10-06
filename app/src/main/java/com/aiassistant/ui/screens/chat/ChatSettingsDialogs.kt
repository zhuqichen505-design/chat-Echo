@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.aiassistant.ui.screens.chat

import android.net.Uri
import android.graphics.BitmapFactory
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
import com.aiassistant.domain.model.TimelineNode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiassistant.utils.TimelineMemoryHelper
import com.aiassistant.utils.TimelineReconcileResult
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineReconcileCheckpoint
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
import com.aiassistant.ui.components.echoSwitchColors
import com.aiassistant.ui.screens.roleplay.ConflictAction
import com.aiassistant.ui.theme.EchoTokens


@Composable
fun ChatSettingsDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    tempSettings: TempChatSettings,
    currentPrompt: String?,
    currentOption: ChatModelOption?,
    fallbackModel: String,
    availableOptions: List<ChatModelOption>,
    templates: List<PromptTemplate>,
    sessionMemories: List<MemoryItem> = emptyList(),
    timelineNodes: List<TimelineNode> = emptyList(),
    currentStoryTime: String? = null,
    onUpdateCurrentStoryTime: (String?) -> Unit = {},
    onAddTimelineNode: (timeTag: String, event: String, category: String) -> Unit = { _, _, _ -> },
    onUpdateTimelineNode: (TimelineNode) -> Unit = {},
    onDeleteTimelineNode: (Long) -> Unit = {},
    onClearTimeline: () -> Unit = {},
    isReconcilingTimeline: Boolean = false,
    reconcileTimelineProgress: String? = null,
    hasSavedTimelineDraft: Boolean = false,
    checkpoint: TimelineReconcileCheckpoint? = null,
    newMessagesCountSinceCheckpoint: Int = 0,
    onStartTimelineReconciliation: () -> Unit = {},
    onContinueTimelineReconciliation: () -> Unit = {},
    onReconcileFromCheckpoint: () -> Unit = {},
    onOpenTimelineDraft: () -> Unit = {},
    onClearTimelineDraft: () -> Unit = {},
    onCancelTimelineReconciliation: () -> Unit = {},
    onAddSessionMemory: (String) -> Unit = {},
    onUpdateSessionMemory: (MemoryItem) -> Unit = {},
    onToggleSessionMemory: (Long, Boolean) -> Unit = { _, _ -> },
    onDeleteSessionMemory: (Long) -> Unit = {},
    onClearSessionMemories: () -> Unit = {},
    onTempSettingsChange: ((TempChatSettings) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (TempChatSettings, String?) -> Unit,
    onModelSelected: (ChatModelOption) -> Unit,
    onSavePromptTemplate: (String, String) -> Unit,
    onModelAvatarChanged: () -> Unit,
    onConvertToRoleplay: () -> Unit = {},
    conversationId: Long = 0L,
    currentConversationModelAvatarUri: String? = null,
    onUpdateConversationModelAvatar: ((String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val dialogContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    val dialogContentColor = readableTextColorFor(
        background = dialogContainerColor,
        fallbackSurface = MaterialTheme.colorScheme.background
    )
    val dialogSecondaryColor = dialogContentColor.copy(alpha = 0.72f)
    var maxTokens by remember { mutableStateOf(tempSettings.maxTokens.toString()) }
    var topP by remember { mutableStateOf(tempSettings.topP) }
    var enableThinking by remember { mutableStateOf(tempSettings.enableThinking) }
    var thinkingEffort by remember { mutableStateOf(tempSettings.thinkingEffort) }
    var enableWebSearch by remember { mutableStateOf(tempSettings.enableWebSearch) }
    var enableSessionMemory by remember { mutableStateOf(tempSettings.enableSessionMemory) }
    var enableExternalMemory by remember { mutableStateOf(tempSettings.enableExternalMemory) }
    var enableWorldBook by remember { mutableStateOf(tempSettings.enableWorldBook) }
    var enableReplyDirections by remember { mutableStateOf(tempSettings.enableReplyDirections) }
    var replyDirectionCount by remember { mutableIntStateOf(tempSettings.replyDirectionCount.coerceIn(2, 4)) }
    var contextWindowTokens by remember { mutableStateOf(tempSettings.contextWindowTokens) }
    var promptTextFieldValue by rememberSaveable(currentPrompt, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = currentPrompt.orEmpty(),
                selection = TextRange(0)
            )
        )
    }
    var showTemplates by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var avatarRevision by remember { mutableIntStateOf(0) }
    val dialogListState = rememberLazyListState()
    val modelOptions = remember(currentOption, fallbackModel, availableOptions) {
        val fallback = currentOption ?: ChatModelOption(
            apiConfigId = 0,
            configName = "当前对话",
            provider = "",
            apiType = "",
            modelName = fallbackModel
        )
        (availableOptions + fallback)
            .filter { it.modelName.isNotBlank() }
            .distinctBy { "${it.apiConfigId}:${it.modelName}" }
    }
    val tuningProfile = remember(currentOption, fallbackModel, enableThinking) {
        chatTuningProfile(currentOption, fallbackModel, enableThinking)
    }
    var temperature by remember(tuningProfile.temperatureMax) {
        mutableStateOf(tempSettings.temperature.coerceIn(0f, tuningProfile.temperatureMax))
    }

    LaunchedEffect(tempSettings) {
        maxTokens = tempSettings.maxTokens.toString()
        topP = tempSettings.topP
        enableThinking = tempSettings.enableThinking
        thinkingEffort = tempSettings.thinkingEffort
        enableWebSearch = tempSettings.enableWebSearch
        enableSessionMemory = tempSettings.enableSessionMemory
        enableExternalMemory = tempSettings.enableExternalMemory
        enableWorldBook = tempSettings.enableWorldBook
        enableReplyDirections = tempSettings.enableReplyDirections
        replyDirectionCount = tempSettings.replyDirectionCount.coerceIn(2, 4)
        contextWindowTokens = tempSettings.contextWindowTokens
    }

    fun notifyTempSettingsChange() {
        val updated = TempChatSettings(
            temperature = temperature.coerceIn(0f, tuningProfile.temperatureMax),
            maxTokens = maxTokens.toIntOrNull() ?: 4096,
            topP = topP,
            enableThinking = enableThinking,
            thinkingEffort = thinkingEffort,
            enableWebSearch = enableWebSearch,
            enableSessionMemory = enableSessionMemory,
            enableExternalMemory = enableExternalMemory,
            enableWorldBook = enableWorldBook,
            enableReplyDirections = enableReplyDirections,
            replyDirectionCount = replyDirectionCount,
            activeWorldBookIds = tempSettings.activeWorldBookIds,
            contextWindowTokens = contextWindowTokens
        )
        onTempSettingsChange?.invoke(updated)
    }

    LaunchedEffect(tuningProfile.temperatureMax) {
        temperature = temperature.coerceIn(0f, tuningProfile.temperatureMax)
    }
    LaunchedEffect(enableThinking, tuningProfile) {
        if (tuningProfile.forcedThinking) enableThinking = true
        val options = tuningProfile.thinkingEfforts
        if (enableThinking && options.isNotEmpty() && options.none { it.value.equals(thinkingEffort, ignoreCase = true) }) {
            thinkingEffort = com.aiassistant.domain.model.ReasoningControls.selected(
                currentOption?.modelName ?: fallbackModel, currentOption?.apiType ?: "openai", enableThinking, thinkingEffort, currentOption?.reasoningCapability
            ).value
        }
    }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    val modelAvatarBitmap = remember(context, avatarRevision, currentConversationModelAvatarUri) {
        if (!currentConversationModelAvatarUri.isNullOrBlank()) {
            runCatching {
                val uri = Uri.parse(currentConversationModelAvatarUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        } else {
            AvatarManager.getModelAvatarBitmap(context)
        }
    }
    val modelAvatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            pendingCropUri = it
        }
    }

    if (pendingCropUri != null) {
        ImageCropEditDialog(
            imageUri = pendingCropUri!!,
            shapeMode = CropShapeMode.CIRCLE,
            title = "裁剪与编辑会话模型头像",
            onDismiss = { pendingCropUri = null },
            onConfirm = { croppedBitmap ->
                pendingCropUri = null
                if (conversationId > 0L && onUpdateConversationModelAvatar != null) {
                    val savedUri = AvatarManager.saveConversationModelAvatarBitmap(context, conversationId, croppedBitmap)
                    onUpdateConversationModelAvatar(savedUri)
                } else {
                    AvatarManager.saveModelAvatarBitmap(context, croppedBitmap)
                    onModelAvatarChanged()
                }
                avatarRevision++
            }
        )
    }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .widthIn(max = 430.dp),
        tint = dialogContainerColor,
        containerColor = dialogContainerColor,
        contentColor = dialogContentColor,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("对话设置", style = MaterialTheme.typography.titleLarge, color = dialogContentColor)
                    Text(
                        text = "当前对话配置会直接生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogSecondaryColor
                    )
                }
            }
        },
        content = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                state = dialogListState,
                contentPadding = PaddingValues(end = 2.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ChatSettingsModelSelector(
                        currentOption = currentOption,
                        fallbackModel = fallbackModel,
                        availableOptions = modelOptions,
                        contentColor = dialogContentColor,
                        secondaryColor = dialogSecondaryColor,
                        onModelSelected = onModelSelected
                    )
                }

                item {
                    ChatSettingsSystemPromptSection(
                        promptTextFieldValue = promptTextFieldValue,
                        onPromptChange = { promptTextFieldValue = it },
                        hasTemplates = templates.isNotEmpty(),
                        contentColor = dialogContentColor,
                        secondaryColor = dialogSecondaryColor,
                        onChooseTemplate = { showTemplates = true },
                        onSaveTemplate = { showSaveDialog = true }
                    )
                }


                // 思考模式
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text("思考模式", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                            Text(
                                "支持时按厂商协议传入 reasoning_effort，档位与采样参数随模型自动适配",
                                style = MaterialTheme.typography.bodySmall,
                                color = dialogSecondaryColor
                            )
                        }
                        Switch(
                            checked = currentOption?.reasoningCapability?.supportsThinking != false && (enableThinking || tuningProfile.forcedThinking),
                            enabled = tuningProfile.thinkingToggleEnabled,
                            onCheckedChange = {
                                enableThinking = it
                                notifyTempSettingsChange()
                            },
                            colors = echoSwitchColors(forcedThinking = tuningProfile.forcedThinking)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text("联网", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                            Text(
                                "仅对支持联网的API或模型生效",
                                style = MaterialTheme.typography.bodySmall,
                                color = dialogSecondaryColor
                            )
                        }
                        Switch(
                            checked = enableWebSearch,
                            onCheckedChange = {
                                enableWebSearch = it
                                notifyTempSettingsChange()
                            },
                            colors = echoSwitchColors()
                        )
                    }
                }

                // 思考强度
                if (enableThinking && tuningProfile.thinkingEfforts.isNotEmpty()) {
                    item {
                        Column {
                            Text("思考强度", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(tuningProfile.thinkingEfforts) { level ->
                                    val selected = thinkingEffort == level.value
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            thinkingEffort = level.value
                                            notifyTempSettingsChange()
                                        },
                                        colors = echoFilterChipColors(),
                                        border = echoFilterChipBorder(selected),
                                        elevation = echoFilterChipElevation(),
                                        label = {
                                            Text(level.label)
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else if (enableThinking && tuningProfile.noThinkingEffortReason != null) {
                    item {
                        Text(
                            text = tuningProfile.noThinkingEffortReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = dialogSecondaryColor
                        )
                    }
                }

                item {
                    ChatSettingsTimelineSection(
                        timelineNodes = timelineNodes,
                        currentStoryTime = currentStoryTime,
                        contentColor = dialogContentColor,
                        secondaryColor = dialogSecondaryColor,
                        enableTimeline = true,
                        hazeState = hazeState,
                        isReconcilingTimeline = isReconcilingTimeline,
                        reconcileTimelineProgress = reconcileTimelineProgress,
                        hasSavedTimelineDraft = hasSavedTimelineDraft,
                        checkpoint = checkpoint,
                        newMessagesCountSinceCheckpoint = newMessagesCountSinceCheckpoint,
                        onStartTimelineReconciliation = onStartTimelineReconciliation,
                        onContinueTimelineReconciliation = onContinueTimelineReconciliation,
                        onReconcileFromCheckpoint = onReconcileFromCheckpoint,
                        onOpenTimelineDraft = onOpenTimelineDraft,
                        onClearTimelineDraft = onClearTimelineDraft,
                        onCancelTimelineReconciliation = onCancelTimelineReconciliation,
                        onUpdateCurrentStoryTime = onUpdateCurrentStoryTime,
                        onAddTimelineNode = onAddTimelineNode,
                        onUpdateTimelineNode = onUpdateTimelineNode,
                        onDeleteTimelineNode = onDeleteTimelineNode,
                        onClearTimeline = onClearTimeline
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("回复前选择方向", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                                Text("仅当前对话；开启后增加一次模型请求与费用", style = MaterialTheme.typography.bodySmall, color = dialogSecondaryColor)
                            }
                            Switch(checked = enableReplyDirections, onCheckedChange = {
                                enableReplyDirections = it
                                notifyTempSettingsChange()
                            })
                        }
                        if (enableReplyDirections) {
                            Text("模型方向数量（另有“自行决定”和“其他”）", style = MaterialTheme.typography.bodySmall, color = dialogSecondaryColor)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (2..4).forEach { count ->
                                    FilterChip(selected = replyDirectionCount == count, onClick = {
                                        replyDirectionCount = count
                                        notifyTempSettingsChange()
                                    }, label = { Text("$count 个") })
                                }
                            }
                        }
                    }
                }

                item {
                    ChatSettingsSessionMemorySection(
                        sessionMemories = sessionMemories,
                        contentColor = dialogContentColor,
                        secondaryColor = dialogSecondaryColor,
                        enableSessionMemory = enableSessionMemory,
                        onEnableSessionMemoryChange = {
                            enableSessionMemory = it
                            notifyTempSettingsChange()
                        },
                        hazeState = hazeState,
                        onAddMemory = onAddSessionMemory,
                        onUpdateMemory = onUpdateSessionMemory,
                        onToggleMemory = onToggleSessionMemory,
                        onDeleteMemory = onDeleteSessionMemory,
                        onClearMemories = onClearSessionMemories
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("模型头像", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (modelAvatarBitmap != null) {
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
                                        contentDescription = "默认模型头像",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = when {
                                        !currentConversationModelAvatarUri.isNullOrBlank() -> "当前使用本会话专属自定义头像（仅限本会话）"
                                        modelAvatarBitmap != null -> "当前使用全局自定义模型头像"
                                        else -> "当前使用 deepseek 默认头像"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = dialogSecondaryColor
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { modelAvatarPicker.launch("image/*") },
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text("更换", maxLines = 1)
                                    }
                                    if (modelAvatarBitmap != null || !currentConversationModelAvatarUri.isNullOrBlank()) {
                                        TextButton(
                                            onClick = {
                                                if (conversationId > 0L && onUpdateConversationModelAvatar != null) {
                                                    AvatarManager.deleteConversationModelAvatar(context, currentConversationModelAvatarUri)
                                                    onUpdateConversationModelAvatar(null)
                                                } else {
                                                    AvatarManager.deleteModelAvatar(context)
                                                    onModelAvatarChanged()
                                                }
                                                avatarRevision++
                                            }
                                        ) {
                                            Text("恢复默认", maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 更多高级选项 (折叠区域)
                item {
                    EchoGlassCard(
                        onClick = { showAdvancedOptions = !showAdvancedOptions },
                        modifier = Modifier.fillMaxWidth(),
                        shape = EchoTokens.Radius.shapeMd,
                        containerColor = echoGlassPalette().control.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = dialogContentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "更多高级选项",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = dialogContentColor
                                    )
                                    Text(
                                        text = if (showAdvancedOptions) "点击收起参数调节与扩展功能" else "温度、最大Token、上下文上限、TopP、角色扮演",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = dialogSecondaryColor
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showAdvancedOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showAdvancedOptions) "收起" else "展开",
                                tint = dialogSecondaryColor
                            )
                        }
                    }
                }

                if (showAdvancedOptions) {
                    // 温度
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (tuningProfile.temperatureEnabled) {
                                        "温度: ${String.format("%.2f", temperature)}"
                                    } else {
                                        "温度: 思考模式下不可调"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = dialogContentColor
                                )
                                Text(
                                    text = "越低严谨，越高富有想象力",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = dialogSecondaryColor
                                )
                            }
                            Slider(
                                value = temperature,
                                onValueChange = { newValue ->
                                    // 精度为0.05
                                    temperature = (newValue * 20).toInt() / 20f
                                },
                                valueRange = 0f..tuningProfile.temperatureMax,
                                steps = (tuningProfile.temperatureMax * 20).toInt().coerceAtLeast(1) - 1,
                                enabled = tuningProfile.temperatureEnabled
                            )
                            if (!tuningProfile.temperatureEnabled) {
                                Text(
                                    text = "${tuningProfile.modelLabel} 的思考模式不支持调整温度，发送请求时会自动省略 temperature。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = dialogSecondaryColor
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("精确", style = MaterialTheme.typography.labelSmall, color = dialogSecondaryColor)
                                Text("平衡", style = MaterialTheme.typography.labelSmall, color = dialogSecondaryColor)
                                Text("发散", style = MaterialTheme.typography.labelSmall, color = dialogSecondaryColor)
                            }
                        }
                    }

                    // 最大Token
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("最大 Token 数", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                                Text("限制单次回复的最大生成长度", style = MaterialTheme.typography.labelSmall, color = dialogSecondaryColor)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = maxTokens,
                                    onValueChange = { value -> maxTokens = value.filter { it.isDigit() }.take(6) },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("默认 4096") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = glassTextFieldColors(dialogContentColor, dialogSecondaryColor, dialogContainerColor)
                                )
                                Surface(
                                    modifier = Modifier.height(54.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = echoGlassPalette().control,
                                    contentColor = dialogSecondaryColor,
                                    border = BorderStroke(1.dp, echoGlassPalette().outline),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("tokens", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            Text(
                                "默认 4096 tokens。留空会使用模型或全局配置的默认值。",
                                style = MaterialTheme.typography.labelSmall,
                                color = dialogSecondaryColor
                            )
                        }
                    }

                    // 上下文上限
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "当前会话上下文上限 (Context Window)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = dialogContentColor
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (contextWindowTokens != null) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                           else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (contextWindowTokens != null) "已自定义" else "跟随模型",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (contextWindowTokens != null) MaterialTheme.colorScheme.tertiary
                                               else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            val presets = listOf(
                                Pair("跟随模型", null),
                                Pair("32K", 32_768),
                                Pair("64K", 65_536),
                                Pair("128K", 131_072),
                                Pair("200K", 200_000),
                                Pair("1M", 1_000_000),
                                Pair("2M", 2_000_000)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(presets) { (label, tokens) ->
                                    val isSelected = contextWindowTokens == tokens
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            contextWindowTokens = tokens
                                            notifyTempSettingsChange()
                                        },
                                        label = {
                                            Text(label, style = MaterialTheme.typography.labelSmall)
                                        },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                        } else null
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = contextWindowTokens?.toString().orEmpty(),
                                    onValueChange = { value ->
                                        val digits = value.filter { it.isDigit() }.take(7)
                                        contextWindowTokens = digits.toIntOrNull()
                                        notifyTempSettingsChange()
                                    },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("留空表示跟随模型默认") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = glassTextFieldColors(dialogContentColor, dialogSecondaryColor, dialogContainerColor)
                                )
                                Surface(
                                    modifier = Modifier.height(54.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = echoGlassPalette().control,
                                    contentColor = dialogSecondaryColor,
                                    border = BorderStroke(1.dp, echoGlassPalette().outline),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("tokens", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            Text(
                                "仅对当前对话生效，不影响该模型在其他会话的限制；超限自动降级保护也仅在此对话生效。",
                                style = MaterialTheme.typography.labelSmall,
                                color = dialogSecondaryColor
                            )
                        }
                    }

                    // Top P
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Top P: ${String.format("%.2f", topP)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = dialogContentColor
                                )
                                Text("核采样概率阈值，控制用词发散程度", style = MaterialTheme.typography.labelSmall, color = dialogSecondaryColor)
                            }
                            Slider(
                                value = topP,
                                onValueChange = { newValue ->
                                    topP = (newValue * 20).toInt() / 20f
                                },
                                valueRange = 0f..1f,
                                steps = 19
                            )
                        }
                    }

                    // 转为角色扮演
                    item {
                        ChatSettingsWorldBookAndExternalMemorySection(
                            enableExternalMemory = enableExternalMemory,
                            onEnableExternalMemoryChange = {
                                enableExternalMemory = it
                                notifyTempSettingsChange()
                            },
                            enableWorldBook = enableWorldBook,
                            onEnableWorldBookChange = {
                                enableWorldBook = it
                                notifyTempSettingsChange()
                            },
                            contentColor = dialogContentColor,
                            secondaryColor = dialogSecondaryColor,
                            hazeState = hazeState
                        )
                    }

                    item {
                        EchoGlassCard(
                            onClick = onConvertToRoleplay,
                            modifier = Modifier.fillMaxWidth(),
                            shape = EchoTokens.Radius.shapeMd,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("转为角色扮演 / 故事创作", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                                    Text("平滑升级为故事会话，解锁角色卡、世界观与剧情推进指令", style = MaterialTheme.typography.bodySmall, color = dialogSecondaryColor)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = dialogSecondaryColor)
                            }
                        }
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
                    Text("取消")
                }
                Button(
                    onClick = {
                        val settings = TempChatSettings(
                            temperature = temperature.coerceIn(0f, tuningProfile.temperatureMax),
                            maxTokens = maxTokens.toIntOrNull() ?: 4096,
                            topP = topP,
                            enableThinking = enableThinking,
                            thinkingEffort = thinkingEffort,
                            enableWebSearch = enableWebSearch,
                            enableSessionMemory = enableSessionMemory,
                            enableExternalMemory = enableExternalMemory,
                            enableWorldBook = enableWorldBook,
                            enableReplyDirections = enableReplyDirections,
                            replyDirectionCount = replyDirectionCount,
                            activeWorldBookIds = tempSettings.activeWorldBookIds,
                            contextWindowTokens = contextWindowTokens
                        )
                        onSave(settings, promptTextFieldValue.text.ifBlank { null })
                    }
                ) {
                    Text("保存")
                }
            }
        }
    )

    if (showTemplates) {
        TemplateListDialog(
            hazeState = hazeState,
            templates = templates,
            onDismiss = { showTemplates = false },
            onSelect = { template ->
                promptTextFieldValue = TextFieldValue(
                    text = template.content,
                    selection = TextRange(template.content.length)
                )
                showTemplates = false
            }
        )
    }

    if (showSaveDialog) {
        SaveTemplateDialog(
            hazeState = hazeState,
            content = promptTextFieldValue.text,
            onDismiss = { showSaveDialog = false },
            onSave = { name, content ->
                onSavePromptTemplate(name, content)
                showSaveDialog = false
            }
        )
    }
}
