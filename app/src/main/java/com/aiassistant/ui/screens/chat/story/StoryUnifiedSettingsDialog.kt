@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.aiassistant.ui.screens.chat.story

import com.aiassistant.ui.screens.chat.*

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
fun StoryUnifiedSettingsDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    session: RoleplaySession,
    characters: List<CharacterProfile>,
    allCharacters: List<CharacterProfile>,
    scenario: RoleplayScenario?,
    allScenarios: List<RoleplayScenario>,
    narrativeMode: NarrativeMode,
    currentOption: ChatModelOption?,
    fallbackModel: String,
    availableOptions: List<ChatModelOption>,
    tempSettings: TempChatSettings,
    currentPrompt: String?,
    templates: List<PromptTemplate>,
    onDismiss: () -> Unit,
    onSaveAll: (
        selectedCharIds: List<Long>,
        selectedScenarioId: Long?,
        mode: NarrativeMode,
        plotSummary: String,
        newSettings: TempChatSettings,
        newPrompt: String?
    ) -> Unit,
    onPlotAction: (PlotAction, String?) -> Unit,
    onSummarizeMemories: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onOpenSmartAppend: () -> Unit,
    onConvertToNormal: () -> Unit = {},
    onSaveLocalCharacter: (CharacterProfile) -> Unit = {},
    onDeleteLocalCharacter: (CharacterProfile) -> Unit = {},
    onSaveLocalScenario: (RoleplayScenario) -> Unit = {},
    onDeleteLocalScenario: () -> Unit = {},
    onModelSelected: (ChatModelOption) -> Unit,
    onSavePromptTemplate: (String, String) -> Unit,
    onModelAvatarChanged: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val dialogContentColor = if (isDark) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.9f)
    val dialogSecondaryColor = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.6f)

    var activeTab by remember { mutableIntStateOf(0) }
    var editingLocalCharacter by remember { mutableStateOf<CharacterProfile?>(null) }
    var editingLocalScenario by remember { mutableStateOf<RoleplayScenario?>(null) }

    // 故事与角色 Tab 状态
    val initialCharIds = remember(session, characters) {
        val ids = session.getEffectiveCharacterIds()
        if (ids.isNotEmpty()) ids.toSet() else characters.map { it.id }.toSet()
    }
    var selectedCharIds by remember { mutableStateOf(initialCharIds) }
    var selectedScenarioId by remember { mutableStateOf(session.scenarioId) }
    var selectedNarrativeMode by remember { mutableStateOf(narrativeMode) }
    var plotSummaryText by remember { mutableStateOf(session.currentPlotSummary) }
    var showCustomPlotDialog by remember { mutableStateOf(false) }
    var customInstructionText by remember { mutableStateOf("") }

    // 模型与参数 Tab 状态
    var temperature by remember { mutableFloatStateOf(tempSettings.temperature) }
    var maxTokens by remember {
        val currentMax = tempSettings.maxTokens.takeIf { it > 0 } ?: 8192
        mutableStateOf(currentMax.toString())
    }
    var topP by remember { mutableFloatStateOf(tempSettings.topP) }
    var showAdvancedMemory by remember { mutableStateOf(false) }
    var enableThinking by remember { mutableStateOf(tempSettings.enableThinking) }
    var thinkingEffort by remember { mutableStateOf(tempSettings.thinkingEffort) }
    var enableWebSearch by remember { mutableStateOf(tempSettings.enableWebSearch) }
    var enableExternalMemory by remember { mutableStateOf(session.enableExternalMemory) }
    var enableWorldBook by remember { mutableStateOf(session.enableWorldBook) }
    var contextWindowTokens by remember { mutableStateOf(tempSettings.contextWindowTokens) }
    var promptTextFieldValue by rememberSaveable(currentPrompt, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = currentPrompt.orEmpty(),
                selection = TextRange(0)
            )
        )
    }
    var isPromptExpanded by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var avatarRevision by remember { mutableIntStateOf(0) }
    val modelAvatarBitmap = remember(context, avatarRevision) {
        AvatarManager.getModelAvatarBitmap(context)
    }
    val modelAvatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingCropUri = it
        }
    }

    if (pendingCropUri != null) {
        ImageCropEditDialog(
            imageUri = pendingCropUri!!,
            shapeMode = CropShapeMode.CIRCLE,
            title = "裁剪与编辑故事角色头像",
            onDismiss = { pendingCropUri = null },
            onConfirm = { croppedBitmap ->
                pendingCropUri = null
                AvatarManager.saveModelAvatarBitmap(context, croppedBitmap)
                avatarRevision++
                onModelAvatarChanged()
            }
        )
    }

    val tuningProfile = remember(currentOption, fallbackModel, enableThinking) {
        chatTuningProfile(currentOption, fallbackModel, enableThinking)
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

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .widthIn(max = 430.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("故事创作与参数设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("世界观、登场角色与模型生成参数一站式管理", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ScrollableTabRow(
                    selectedTabIndex = activeTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("🎭 剧情导向", fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("👥 登场角色", fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = { Text("🌍 世界观", fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        text = { Text("📜 创作规范", fontWeight = if (activeTab == 3) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = activeTab == 4,
                        onClick = { activeTab = 4 },
                        text = { Text("⚙️ 模型参数", fontWeight = if (activeTab == 4) FontWeight.Bold else FontWeight.Normal) }
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 480.dp)) {
                    if (activeTab == 0) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 快捷 AI 追加
                            item {
                                EchoGlassCard(
                                    onClick = {
                                        onDismiss()
                                        onOpenSmartAppend()
                                    },
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
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("AI 智能识别、追加与融合", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                            Text("粘贴小说章节或人设，实时并入当前故事", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                                    }
                                }
                            }

                            // 转为普通对话操作
                            item {
                                EchoGlassCard(
                                    onClick = onConvertToNormal,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = EchoTokens.Radius.shapeMd,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("转为普通对话", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                            Text("将角色与世界观设定转译融合为普通系统提示词，降级为日常聊天", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                                    }
                                }
                            }

                            // 叙事模式选择
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("当前叙事模式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "点击即时切换导演/对话风格",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    NarrativeMode.values().forEach { mode ->
                                        val isSelected = selectedNarrativeMode == mode
                                        EchoGlassCard(
                                            onClick = { selectedNarrativeMode = mode },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = EchoTokens.Radius.shapeSm,
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Unspecified
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { selectedNarrativeMode = mode }
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(mode.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 剧情提示快捷动作
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("快捷剧情提示指令", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        listOf(
                                            PlotAction.CONTINUE to "继续剧情",
                                            PlotAction.BRANCH_CHOICES to "决策分支",
                                            PlotAction.SUMMARY to "剧情摘要",
                                            PlotAction.REWRITE to "改写上一段",
                                            PlotAction.EXTEND to "延长描写",
                                            PlotAction.SHORTEN to "精简对白",
                                            PlotAction.CHANGE_PERSPECTIVE to "切换视角",
                                            PlotAction.CUSTOM to "自定义指令..."
                                        ).forEach { (action, label) ->
                                            OutlinedButton(
                                                onClick = {
                                                    if (action == PlotAction.CUSTOM) {
                                                        showCustomPlotDialog = true
                                                    } else {
                                                        onDismiss()
                                                        onPlotAction(action, null)
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.defaultMinSize(minHeight = 28.dp)
                                            ) {
                                                Text(label, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }

                            // 剧情摘要输入
                            item {
                                OutlinedTextField(
                                    value = plotSummaryText,
                                    onValueChange = { plotSummaryText = it },
                                    label = { Text("当前剧情摘要 / 备忘录") },
                                    placeholder = { Text("记录当前故事线推进到的关键阶段或核心暗线...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    maxLines = 4
                                )
                            }

                            // 记忆管理
                            item {
                                OutlinedButton(
                                    onClick = onNavigateToMemory,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("管理长期记忆与关键事实库")
                                }
                            }
                        }
                    } else if (activeTab == 1) {
                        // activeTab == 1: 登场角色独立列表与管理
                        val charListToDisplay = (allCharacters + characters).distinctBy { it.id }
                        val validCharIds = charListToDisplay.map { it.id }.toSet()
                        val effectiveSelectedCharIds = selectedCharIds.filter { validCharIds.contains(it) }.toSet()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "登场角色 (${effectiveSelectedCharIds.size}/${charListToDisplay.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            editingLocalCharacter = CharacterProfile(id = 0, name = "")
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("添加新角色", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            if (charListToDisplay.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("暂无角色卡，点击上方「添加新角色」立即为故事编排登场人物。", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } else {
                                items(charListToDisplay.size) { idx ->
                                    val char = charListToDisplay[idx]
                                    val isChecked = effectiveSelectedCharIds.contains(char.id)
                                    EchoGlassCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    selectedCharIds = if (isChecked) {
                                                        effectiveSelectedCharIds - char.id
                                                    } else {
                                                        effectiveSelectedCharIds + char.id
                                                    }
                                                },
                                                onLongClick = {
                                                    editingLocalCharacter = char
                                                }
                                            ),
                                        shape = EchoTokens.Radius.shapeSm,
                                        containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Unspecified
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedCharIds = if (checked) effectiveSelectedCharIds + char.id else effectiveSelectedCharIds - char.id
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(char.name + if (char.identity.isNotBlank()) " · ${char.identity}" else "", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                if (char.personality.isNotBlank()) {
                                                    Text("性格: ${char.personality}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                }
                                            }
                                            IconButton(
                                                onClick = { editingLocalCharacter = char },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "编辑角色", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { onDeleteLocalCharacter(char) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "从故事移除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (activeTab == 2) {
                        // activeTab == 2: 世界观与场景独立列表
                        val scenarioListToDisplay = (allScenarios + listOfNotNull(scenario)).distinctBy { it.id }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "世界观与场景设定",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            editingLocalScenario = RoleplayScenario(id = 0, name = "")
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("添加新世界观", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            item {
                                EchoGlassCard(
                                    onClick = { selectedScenarioId = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = EchoTokens.Radius.shapeSm,
                                    containerColor = if (selectedScenarioId == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Unspecified
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = selectedScenarioId == null, onClick = { selectedScenarioId = null })
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("不指定世界观（自由开放背景）", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            items(scenarioListToDisplay.size) { idx ->
                                val sc = scenarioListToDisplay[idx]
                                val isSelected = selectedScenarioId == sc.id
                                EchoGlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { selectedScenarioId = sc.id },
                                            onLongClick = { editingLocalScenario = sc }
                                        ),
                                    shape = EchoTokens.Radius.shapeSm,
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Unspecified
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = isSelected, onClick = { selectedScenarioId = sc.id })
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sc.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            if (sc.worldview.isNotBlank()) {
                                                Text(sc.worldview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                            }
                                        }
                                        IconButton(
                                            onClick = { editingLocalScenario = sc },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑世界观", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { onDeleteLocalScenario() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "从故事移除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else if (activeTab == 3) {
                        // activeTab == 3: 创作规范与提示词 (教学模型如何创作)
                        val personalizationMgr = remember { AiAssistantApp.instance.personalizationManager }
                        val initialGlobalRpPrompt = remember { personalizationMgr.getSettings().globalRoleplayPrompt }
                        var globalRoleplayPromptText by remember { mutableStateOf(initialGlobalRpPrompt) }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 本故事系统提示词
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("本故事专属系统提示词", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = dialogContentColor)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = { isPromptExpanded = !isPromptExpanded },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPromptExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                                                    contentDescription = if (isPromptExpanded) "缩小" else "放大",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (templates.isNotEmpty()) {
                                                TextButton(
                                                    onClick = { showTemplates = true },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("模板", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                            if (promptTextFieldValue.text.isNotBlank()) {
                                                TextButton(
                                                    onClick = { showSaveDialog = true },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("存模板", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                    Text("仅作用于当前故事，指导本故事特定的叙事基调、伏笔暗线或风格限制", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedTextField(
                                        value = promptTextFieldValue,
                                        onValueChange = { promptTextFieldValue = it },
                                        placeholder = { Text("例如：采用冷硬派侦探小说笔触，多用客观白描，强化悬疑氛围...") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(
                                                min = if (isPromptExpanded) 160.dp else 80.dp,
                                                max = if (isPromptExpanded) 260.dp else 120.dp
                                            ),
                                        minLines = if (isPromptExpanded) 5 else 2,
                                        maxLines = if (isPromptExpanded) 10 else 4,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }

                            // 全局创作教学规范
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("全局角色创作规范与教学指引", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = dialogContentColor)
                                        TextButton(
                                            onClick = {
                                                globalRoleplayPromptText = com.aiassistant.data.repository.RoleplayRepository.DEFAULT_FICTION_TEACHING_GUIDELINES
                                                personalizationMgr.saveSettings(personalizationMgr.getSettings().copy(globalRoleplayPrompt = globalRoleplayPromptText))
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("恢复默认文学规范", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Text("作用于所有角色扮演故事，用于教学模型如何创作故事、行文规范与沉浸感", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedTextField(
                                        value = globalRoleplayPromptText,
                                        onValueChange = {
                                            globalRoleplayPromptText = it
                                            personalizationMgr.saveSettings(personalizationMgr.getSettings().copy(globalRoleplayPrompt = it))
                                        },
                                        placeholder = { Text("留空将使用内置文学创作铁律（以演代述、神态微表情描写、禁止出戏性格副词）...") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 100.dp, max = 180.dp),
                                        minLines = 3,
                                        maxLines = 8,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }

                            // 机制说明卡片
                            item {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("💡 创作提示词分层作用机制说明", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Text("1. 全局创作规范：向模型传授文学创作方法论（以演代述、避免性格副词、台词动作节奏），全局共用。", style = MaterialTheme.typography.bodySmall)
                                        Text("2. 本故事专属系统提示词：指导本故事特定的情节、题材与世界限制，优先级高于全局提示词。", style = MaterialTheme.typography.bodySmall)
                                        Text("3. 登场角色卡与场景卡：作为独立结构化卡片拼入上下文，与剧情提示词解耦，确保人设永不走样。", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    } else {
                        // activeTab == 4: 模型与参数
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                ChatSettingsModelSelector(
                                    currentOption = currentOption,
                                    fallbackModel = fallbackModel,
                                    availableOptions = availableOptions,
                                    contentColor = dialogContentColor,
                                    secondaryColor = dialogSecondaryColor,
                                    onModelSelected = onModelSelected
                                )
                            }

                            item {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("温度 (Temperature)", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                                        Text(String.format(Locale.getDefault(), "%.2f", temperature), style = MaterialTheme.typography.bodyMedium, color = dialogSecondaryColor)
                                    }
                                    Slider(
                                        value = temperature,
                                        onValueChange = { temperature = it },
                                        valueRange = 0f..tuningProfile.temperatureMax,
                                        steps = 20
                                    )
                                }
                            }

                            item {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("最大输出 (Max Tokens)", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                                        Text("默认 8192+", style = MaterialTheme.typography.bodySmall, color = dialogSecondaryColor)
                                    }
                                    OutlinedTextField(
                                        value = maxTokens,
                                        onValueChange = { maxTokens = it.filter { char -> char.isDigit() } },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }

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

                                    OutlinedTextField(
                                        value = contextWindowTokens?.toString().orEmpty(),
                                        onValueChange = { value ->
                                            val digits = value.filter { it.isDigit() }.take(7)
                                            contextWindowTokens = digits.toIntOrNull()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("自定义 Tokens，留空表示跟随模型默认") },
                                        singleLine = true
                                    )
                                    Text(
                                        "仅对当前对话生效，不影响该模型在其他会话的限制；超限自动降级保护也仅在此对话生效。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = dialogSecondaryColor
                                    )
                                }
                            }

                            item {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Top P (核采样)", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                                        Text(String.format(Locale.getDefault(), "%.2f", topP), style = MaterialTheme.typography.bodyMedium, color = dialogSecondaryColor)
                                    }
                                    Slider(
                                        value = topP,
                                        onValueChange = { topP = it },
                                        valueRange = 0f..1f,
                                        steps = 20
                                    )
                                }
                            }

                            item {
                                TextButton(onClick = { showAdvancedMemory = !showAdvancedMemory }) {
                                    Text(if (showAdvancedMemory) "收起更多高级选项" else "更多高级选项")
                                }
                            }
                            if (showAdvancedMemory) {
                                item {
                                    com.aiassistant.ui.screens.chat.ChatSettingsWorldBookAndExternalMemorySection(
                                        enableExternalMemory = enableExternalMemory,
                                        onEnableExternalMemoryChange = { enableExternalMemory = it },
                                        enableWorldBook = enableWorldBook,
                                        onEnableWorldBookChange = { enableWorldBook = it },
                                        contentColor = dialogContentColor,
                                        secondaryColor = dialogSecondaryColor,
                                        hazeState = hazeState
                                    )
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text("深度思考模式", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                                        Text(
                                            if (tuningProfile.noThinkingEffortReason != null) {
                                                tuningProfile.noThinkingEffortReason
                                            } else {
                                                "适合复杂情节构思与严谨逻辑推演"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = dialogSecondaryColor
                                        )
                                    }
                                    Switch(
                                        checked = currentOption?.reasoningCapability?.supportsThinking != false && (enableThinking || tuningProfile.forcedThinking),
                                        enabled = tuningProfile.thinkingToggleEnabled,
                                        onCheckedChange = { enableThinking = it },
                                        colors = echoSwitchColors(forcedThinking = tuningProfile.forcedThinking)
                                    )
                                }
                            }

                            if (enableThinking && tuningProfile.thinkingEfforts.isNotEmpty()) {
                                item {
                                    Column {
                                        Text("思考强度档位", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(tuningProfile.thinkingEfforts) { level ->
                                                val selected = thinkingEffort == level.value
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = { thinkingEffort = level.value },
                                                    colors = echoFilterChipColors(),
                                                    border = echoFilterChipBorder(selected),
                                                    elevation = echoFilterChipElevation(),
                                                    label = { Text(level.label) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text("联网搜索", style = MaterialTheme.typography.titleSmall, color = dialogContentColor)
                                        Text("仅对支持联网的 API 生效", style = MaterialTheme.typography.bodySmall, color = dialogSecondaryColor)
                                    }
                                    Switch(
                                        checked = enableWebSearch,
                                        onCheckedChange = { enableWebSearch = it },
                                        colors = echoSwitchColors()
                                    )
                                }
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
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (modelAvatarBitmap != null) {
                                                Image(
                                                    bitmap = modelAvatarBitmap.asImageBitmap(),
                                                    contentDescription = "模型头像",
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Image(
                                                    painter = painterResource(id = R.drawable.deepseek),
                                                    contentDescription = "默认模型头像",
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = { modelAvatarPicker.launch("image/*") },
                                                    shape = RoundedCornerShape(999.dp)
                                                ) {
                                                    Text("更换")
                                                }
                                                if (modelAvatarBitmap != null) {
                                                    TextButton(
                                                        onClick = {
                                                            AvatarManager.deleteModelAvatar(context)
                                                            avatarRevision++
                                                            onModelAvatarChanged()
                                                        }
                                                    ) {
                                                        Text("恢复默认")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
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
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val newSettings = TempChatSettings(
                            temperature = temperature.coerceIn(0f, tuningProfile.temperatureMax),
                            maxTokens = maxTokens.toIntOrNull() ?: 8192,
                            topP = topP,
                            enableThinking = enableThinking,
                            thinkingEffort = thinkingEffort,
                            enableWebSearch = enableWebSearch,
                            enableSessionMemory = tempSettings.enableSessionMemory,
                            enableExternalMemory = enableExternalMemory,
                            enableWorldBook = enableWorldBook,
                            activeWorldBookIds = tempSettings.activeWorldBookIds,
                            contextWindowTokens = contextWindowTokens
                        )
                        val charListToDisplay = (allCharacters + characters).distinctBy { it.id }
                        val validCharIds = charListToDisplay.map { it.id }.toSet()
                        val finalCharIds = selectedCharIds.filter { validCharIds.contains(it) }.toList()

                        onSaveAll(
                            finalCharIds,
                            selectedScenarioId,
                            selectedNarrativeMode,
                            plotSummaryText,
                            newSettings,
                            promptTextFieldValue.text.ifBlank { null }
                        )
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

    if (showCustomPlotDialog) {
        EchoGlassDialog(
            onDismissRequest = { showCustomPlotDialog = false },
            title = { Text("输入自定义剧情指令") },
            text = {
                OutlinedTextField(
                    value = customInstructionText,
                    onValueChange = { customInstructionText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("剧情提示 / 导演要求") },
                    placeholder = { Text("例如：接下来让他们在雨夜车站再次相遇...") },
                    maxLines = 4
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customInstructionText.isNotBlank()) {
                            onPlotAction(PlotAction.CUSTOM, customInstructionText)
                            showCustomPlotDialog = false
                        }
                    },
                    enabled = customInstructionText.isNotBlank()
                ) {
                    Text("发送指令")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPlotDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (editingLocalCharacter != null) {
        Dialog(
            onDismissRequest = { editingLocalCharacter = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                com.aiassistant.ui.screens.roleplay.CharacterEditorScreen(
                    character = if (editingLocalCharacter?.name?.isNotBlank() == true) editingLocalCharacter else null,
                    onSave = { updatedChar ->
                        onSaveLocalCharacter(updatedChar)
                        editingLocalCharacter = null
                    },
                    onDelete = {
                        if (editingLocalCharacter != null && editingLocalCharacter!!.name.isNotBlank()) {
                            onDeleteLocalCharacter(editingLocalCharacter!!)
                        }
                        editingLocalCharacter = null
                    },
                    onBack = { editingLocalCharacter = null }
                )
            }
        }
    }

    if (editingLocalScenario != null) {
        Dialog(
            onDismissRequest = { editingLocalScenario = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                com.aiassistant.ui.screens.roleplay.ScenarioEditorScreen(
                    scenario = if (editingLocalScenario?.name?.isNotBlank() == true) editingLocalScenario else null,
                    onSave = { updatedSc ->
                        onSaveLocalScenario(updatedSc)
                        editingLocalScenario = null
                    },
                    onDelete = {
                        onDeleteLocalScenario()
                        editingLocalScenario = null
                    },
                    onBack = { editingLocalScenario = null }
                )
            }
        }
    }
}
