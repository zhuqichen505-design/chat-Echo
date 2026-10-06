@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aiassistant.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.aiassistant.domain.model.ChatModelOption
import com.aiassistant.ui.components.ImageCropEditDialog
import com.aiassistant.ui.components.CropShapeMode
import com.aiassistant.ui.components.ExpandableText
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.AiAssistantApp
import com.aiassistant.BuildConfig
import com.aiassistant.R
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.NamedApiKey
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.EnvironmentVariable
import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.WorldBook
import com.aiassistant.domain.model.WorldBookEntry
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.domain.model.ModelCustomSettings
import com.aiassistant.domain.model.PromptTemplate
import com.aiassistant.ui.components.EchoBadge
import com.aiassistant.ui.components.EchoBadgeType
import com.aiassistant.ui.components.EchoSettingRow
import com.aiassistant.ui.components.EchoSwitch
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.rememberReadableBackdropColors
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoSegmentedButtonBorder
import com.aiassistant.ui.components.echoSegmentedButtonColors
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoHazeSource
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.components.rememberSmoothReorderState
import com.aiassistant.ui.components.reorderItem
import com.aiassistant.ui.components.reorderDragHandle
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.BackgroundImageManager
import com.aiassistant.utils.BackupManager
import com.aiassistant.utils.HiddenConversationLock
import com.aiassistant.utils.TavilySearchSettings
import com.aiassistant.utils.AppThemeMode
import com.aiassistant.tools.search.SearchEngineType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aiassistant.tools.cloud.OpenMeteoWeatherEngine
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PromptsMemoryTab(
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val manager = AiAssistantApp.instance.personalizationManager
    val repository = AiAssistantApp.instance.repository
    val coroutineScope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(manager.getSettings()) }
    var globalPrompt by remember(settings) { mutableStateOf(settings.globalSystemPrompt) }
    var globalRoleplayPrompt by remember(settings) { mutableStateOf(settings.globalRoleplayPrompt) }
    var instruction by remember(settings) {
        mutableStateOf(
            listOf(
                settings.aboutUser,
                settings.responseStyle,
                settings.preferences,
                settings.avoid
            )
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
        )
    }
    var autoMemoryEnabled by remember(settings) { mutableStateOf(settings.autoMemoryEnabled) }

    var savedMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(savedMessage) {
        savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            savedMessage = null
        }
    }

    fun persistPromptSettings(
        newGlobalPrompt: String = globalPrompt,
        newGlobalRoleplayPrompt: String = globalRoleplayPrompt,
        newInstruction: String = instruction,
        newAutoMemoryEnabled: Boolean = autoMemoryEnabled
    ) {
        manager.saveSettings(
            settings.copy(
                globalSystemPrompt = newGlobalPrompt.trim(),
                globalRoleplayPrompt = newGlobalRoleplayPrompt.trim(),
                aboutUser = newInstruction.trim(),
                responseStyle = "",
                preferences = "",
                avoid = "",
                autoMemoryEnabled = newAutoMemoryEnabled
            )
        )
        settings = manager.getSettings()
    }

    LaunchedEffect(globalPrompt) {
        if (globalPrompt.trim() != settings.globalSystemPrompt.trim()) {
            kotlinx.coroutines.delay(400)
            persistPromptSettings(newGlobalPrompt = globalPrompt)
        }
    }

    LaunchedEffect(globalRoleplayPrompt) {
        if (globalRoleplayPrompt.trim() != settings.globalRoleplayPrompt.trim()) {
            kotlinx.coroutines.delay(400)
            persistPromptSettings(newGlobalRoleplayPrompt = globalRoleplayPrompt)
        }
    }

    LaunchedEffect(instruction) {
        if (instruction.trim() != settings.aboutUser.trim()) {
            kotlinx.coroutines.delay(400)
            persistPromptSettings(newInstruction = instruction)
        }
    }

    // 跨会话长期记忆管理状态（仅维护全局偏好，彻底与对话专属偏好解耦独立，需求 3）
    var isMemoriesExpanded by remember { mutableStateOf(false) }
    var memorySearchQuery by remember { mutableStateOf("") }
    val globalMemories by remember(memorySearchQuery) {
        if (memorySearchQuery.isBlank()) repository.getGlobalMemories() else repository.searchGlobalMemories(memorySearchQuery.trim())
    }.collectAsState(initial = emptyList())

    var memoryToEdit by remember { mutableStateOf<MemoryItem?>(null) }
    var isAddingMemory by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }


    // 世界书 (Lorebook) 状态
    var isWorldBooksExpanded by remember { mutableStateOf(false) }
    var worldBookSearchQuery by remember { mutableStateOf("") }
    val allWorldBooks by repository.getAllWorldBooks().collectAsState(initial = emptyList())
    val filteredWorldBooks = remember(allWorldBooks, worldBookSearchQuery) {
        if (worldBookSearchQuery.isBlank()) allWorldBooks
        else allWorldBooks.filter {
            it.name.contains(worldBookSearchQuery.trim(), ignoreCase = true) ||
            it.description.contains(worldBookSearchQuery.trim(), ignoreCase = true) ||
            (it.tags?.contains(worldBookSearchQuery.trim(), ignoreCase = true) == true)
        }
    }
    var isAddingWorldBook by remember { mutableStateOf(false) }
    var worldBookToEdit by remember { mutableStateOf<WorldBook?>(null) }
    var worldBookToDelete by remember { mutableStateOf<WorldBook?>(null) }
    var selectedWorldBookForEntries by remember { mutableStateOf<WorldBook?>(null) }
    var isAddingEntryForBookId by remember { mutableStateOf<Long?>(null) }
    var entryToEdit by remember { mutableStateOf<WorldBookEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<WorldBookEntry?>(null) }

    // 提示词模板状态
    var isTemplatesExpanded by remember { mutableStateOf(false) }
    var templateSearchQuery by remember { mutableStateOf("") }
    val allTemplates by repository.getAllPromptTemplates().collectAsState(initial = emptyList())
    val filteredTemplates = remember(allTemplates, templateSearchQuery) {
        if (templateSearchQuery.isBlank()) allTemplates
        else allTemplates.filter { it.name.contains(templateSearchQuery.trim(), ignoreCase = true) || it.content.contains(templateSearchQuery.trim(), ignoreCase = true) }
    }
    var templateToEdit by remember { mutableStateOf<PromptTemplate?>(null) }
    var isAddingTemplate by remember { mutableStateOf(false) }

    // 环境变量状态
    var isEnvVarsExpanded by remember { mutableStateOf(false) }
    val allEnvVars by repository.getAllEnvironmentVariables().collectAsState(initial = emptyList())
    var envVarToEdit by remember { mutableStateOf<EnvironmentVariable?>(null) }
    var isAddingEnvVar by remember { mutableStateOf(false) }

    // 机制与优先级说明折叠状态
    var showPriorityDetails by remember { mutableStateOf(false) }

    val glass = echoGlassPalette()

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.cardGap)
        ) {
            // 1. 提示词与记忆生效机制与优先级说明
            item {
                SettingsGlassCard(hazeState = hazeState) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .echoShapeClick(SettingsInnerShape) { showPriorityDetails = !showPriorityDetails },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("提示词与记忆生效机制与优先级说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "系统提示词 / 全局提示词 / 个性化偏好 / 长期记忆规则",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { showPriorityDetails = !showPriorityDetails }) {
                            Icon(
                                if (showPriorityDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showPriorityDetails,
                        enter = expandVertically(
                            animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.standard),
                            expandFrom = Alignment.Top
                        ) + fadeIn(
                            animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast)
                        ),
                        exit = shrinkVertically(
                            animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast),
                            shrinkTowards = Alignment.Top
                        ) + fadeOut(
                            animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PriorityRuleRow(
                                badge = "最高优先级",
                                title = "会话专属系统提示词 (100% 独占)",
                                description = "在单个对话设置中填写的系统提示词拥有最高优先级。当其存在时，全局系统提示词将被 100% 覆盖，0 作用生效。",
                                badgeColor = MaterialTheme.colorScheme.primary
                            )
                            PriorityRuleRow(
                                badge = "默认兜底",
                                title = "全局系统提示词 (全局兜底)",
                                description = "仅在对话未设置任何专属系统提示词时自动继承；一旦对话设置了专属提示词即刻失效。",
                                badgeColor = MaterialTheme.colorScheme.secondary
                            )
                            PriorityRuleRow(
                                badge = "全局引导",
                                title = "个性化偏好 (全局引导)",
                                description = "对所有普通对话起全局引导与输出润色效果；若偏好内容与提示词规则发生冲突，严格以提示词为准。",
                                badgeColor = MaterialTheme.colorScheme.tertiary
                            )
                            PriorityRuleRow(
                                badge = "弹窗确认",
                                title = "长期记忆 vs 会话记忆 (严禁静默入库)",
                                description = "模型识别到重要偏好或事实后，必须在聊天输入框上方弹出确认浮条，由您主动点击【存为跨会话长期记忆】或【仅本会话生效】或【忽略】，杜绝静默污染记忆库。",
                                badgeColor = MaterialTheme.colorScheme.primary
                            )
                            PriorityRuleRow(
                                badge = "物理隔离",
                                title = "角色扮演与故事创作 (物理严格隔离)",
                                description = "角色与故事创作拥有独立角色卡、世界观与剧情备忘录，绝对不读取也不污染普通对话的提示词、偏好与日常记忆。",
                                badgeColor = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 2. 全局系统提示词模块
            item {
                SettingsGlassCard(hazeState = hazeState) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("全局系统提示词", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "设置的系统提示词将作为默认提示词应用于普通新对话（角色扮演/故事创作模式使用专属设定）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                val defaultPrompt = "你是一个专业、严谨、有深度思考能力的 AI 助手。回答问题时逻辑清晰、论证充分，遇到专业问题主动给出高质量的代码或技术解释，语言自然流畅。"
                                globalPrompt = defaultPrompt
                                persistPromptSettings(newGlobalPrompt = defaultPrompt)
                                savedMessage = "已填入并保存默认提示词"
                            }
                        ) {
                            Text("填入默认预设", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    OutlinedTextField(
                        value = globalPrompt,
                        onValueChange = {
                            globalPrompt = it
                            savedMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        placeholder = { Text("例如：你是一个专业、富有同理心的全能AI助手...") },
                        minLines = 4,
                        maxLines = 14,
                        shape = SettingsInnerShape
                    )
                }
            }

            // 3. 故事创作与角色扮演全局教学指引
            item {
                SettingsGlassCard(hazeState = hazeState) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("故事创作与角色扮演全局教学指引", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "用于教学模型如何创作故事、行文规范与沉浸感（如以演代述、避免性格副词、维持角色独立性）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                val defaultGuidelines = com.aiassistant.data.repository.RoleplayRepository.DEFAULT_FICTION_TEACHING_GUIDELINES
                                globalRoleplayPrompt = defaultGuidelines
                                persistPromptSettings(newGlobalRoleplayPrompt = defaultGuidelines)
                                savedMessage = "已填入并保存创作规范"
                            }
                        ) {
                            Text("填入默认规范", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    OutlinedTextField(
                        value = globalRoleplayPrompt,
                        onValueChange = {
                            globalRoleplayPrompt = it
                            savedMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        placeholder = { Text("留空将使用内置文学创作铁律（Show Don't Tell、真实对白与微动作交融）...") },
                        minLines = 4,
                        maxLines = 14,
                        shape = SettingsInnerShape
                    )
                }
            }

            // 4. 自定义偏好与关于我画像
            item {
                SettingsGlassCard(hazeState = hazeState) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("“关于我”与自定义偏好", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "模型在所有普通对话中都会参考这些背景信息，让回答更贴合您的喜好与需求。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    PersonalizationTextField(
                        title = "用户画像与偏好指令",
                        value = instruction,
                        placeholder = "例如：\n- 我是一名全栈工程师，主要使用 Kotlin 和 Python\n- 回答请直接切入重点，少说客套话\n- 代码请附带关键行注释",
                        onValueChange = {
                            instruction = it
                            savedMessage = null
                        }
                    )
                }
            }

            // 5. 跨会话长期记忆
            item {
                SettingsGlassCard(hazeState = hazeState) {
                    EchoSettingRow(
                        title = "跨会话长期记忆",
                        subtitle = "管理跨所有对话共通参考的全局偏好。开关仅影响全局偏好；各对话专属偏好和记忆完全独立运作。",
                        icon = Icons.Default.Memory,
                        contentPaddingHorizontal = 0.dp
                    ) {
                        EchoSwitch(
                            checked = autoMemoryEnabled,
                            onCheckedChange = {
                                autoMemoryEnabled = it
                                persistPromptSettings(newAutoMemoryEnabled = it)
                                savedMessage = if (it) "已开启跨会话长期记忆" else "已关闭跨会话长期记忆"
                            }
                        )
                    }

                    if (autoMemoryEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .echoShapeClick(SettingsInnerShape) { isMemoriesExpanded = !isMemoriesExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "全局偏好库管理",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${globalMemories.size} 条",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { isAddingMemory = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "添加偏好", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { isMemoriesExpanded = !isMemoriesExpanded }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        if (isMemoriesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = isMemoriesExpanded) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = memorySearchQuery,
                                    onValueChange = { memorySearchQuery = it },
                                    placeholder = { Text("搜索全局偏好内容或关键词...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (memorySearchQuery.isNotBlank()) {
                                            IconButton(onClick = { memorySearchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(999.dp)
                                )

                                if (globalMemories.isEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = SettingsInnerShape,
                                        color = glass.control
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Default.Lightbulb,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                if (memorySearchQuery.isBlank()) "暂无全局偏好条目\n当与 AI 对话提及个人习惯或点击右上角「+」时将在此处列出。"
                                                else "没有搜索到符合条件的全局偏好条目",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        globalMemories.forEach { memory ->
                                            MemoryItemCard(
                                                memory = memory,
                                                onToggleEnabled = { enabled ->
                                                    coroutineScope.launch {
                                                        repository.saveMemory(memory.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis()))
                                                    }
                                                },
                                                onEdit = { memoryToEdit = memory },
                                                onDelete = {
                                                    coroutineScope.launch {
                                                        repository.deleteMemory(memory)
                                                    }
                                                }
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = { showClearAllConfirm = true }) {
                                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("清空全局偏好", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // 6. 世界书与设定库 (Lorebook)
            item {
                SettingsGlassCard(hazeState = hazeState) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("世界书与设定库 (Lorebook)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "基于关键词自动唤醒设定或常驻注入世界观，普通对话与故事模式均可自由生效",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .echoShapeClick(SettingsInnerShape) { isWorldBooksExpanded = !isWorldBooksExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("世界书列表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            EchoBadge(text = "${allWorldBooks.size} 本", type = EchoBadgeType.Primary)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val sampleBook = WorldBook(
                                            name = "奇幻与机械纪元",
                                            description = "以太魔法与重工业机械神谕并存的世界观设定集",
                                            isEnabled = true,
                                            tags = "奇幻,机械,设定集"
                                        )
                                        val bookId = repository.insertWorldBook(sampleBook)
                                        if (bookId > 0L) {
                                            repository.insertWorldBookEntry(
                                                WorldBookEntry(
                                                    bookId = bookId,
                                                    name = "以太灵素",
                                                    keys = "以太, 灵素, 魔法, 施法",
                                                    content = "以太是构筑天地万物的灵性能量，过度抽取会导致现实空间裂隙与灵力枯竭。",
                                                    isEnabled = true,
                                                    isConstant = false,
                                                    priority = 20
                                                )
                                            )
                                            repository.insertWorldBookEntry(
                                                WorldBookEntry(
                                                    bookId = bookId,
                                                    name = "铁心重工神谕",
                                                    keys = "铁心, 机械, 齿轮, 蒸汽, 议会",
                                                    content = "掌控大陆重工科技的机械神殿，崇尚秩序与严酷法典，对野生法师保持警惕。",
                                                    isEnabled = true,
                                                    isConstant = false,
                                                    priority = 15
                                                )
                                            )
                                            repository.insertWorldBookEntry(
                                                WorldBookEntry(
                                                    bookId = bookId,
                                                    name = "世界基底铁律 (常驻)",
                                                    keys = "",
                                                    content = "大陆的一切能量转换严格遵循等价守恒，严禁任何形式的亡者逆转复活。",
                                                    isEnabled = true,
                                                    isConstant = true,
                                                    priority = 30
                                                )
                                            )
                                            savedMessage = "已成功载入示例世界书《奇幻与机械纪元》"
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("示例载入", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { isAddingWorldBook = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "新建世界书", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { isWorldBooksExpanded = !isWorldBooksExpanded }, modifier = Modifier.size(32.dp)) {
                                Icon(if (isWorldBooksExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    AnimatedVisibility(visible = isWorldBooksExpanded) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = worldBookSearchQuery,
                                onValueChange = { worldBookSearchQuery = it },
                                placeholder = { Text("搜索世界书名称或标签...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (worldBookSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { worldBookSearchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(999.dp)
                            )

                            if (allWorldBooks.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = SettingsInnerShape,
                                    color = glass.control
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "暂无世界书设定\n点击右上角「示例载入」可一键生成《奇幻与机械纪元》，或点击「+」新建专属设定集。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filteredWorldBooks.forEach { book ->
                                        WorldBookCardItem(
                                            book = book,
                                            onToggleEnabled = { enabled ->
                                                coroutineScope.launch {
                                                    repository.setWorldBookEnabled(book.id, enabled)
                                                }
                                            },
                                            onManageEntries = { selectedWorldBookForEntries = book },
                                            onEdit = { worldBookToEdit = book },
                                            onDelete = { worldBookToDelete = book }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. 提示词模板系统
            item {
                SettingsGlassCard(hazeState = hazeState) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("提示词模板工作流", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "预设结构化指令与 {{变量}} 占位符，支持聊天输入框快速调用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .echoShapeClick(SettingsInnerShape) { isTemplatesExpanded = !isTemplatesExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("模板库列表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            EchoBadge(text = "${allTemplates.size} 个", type = EchoBadgeType.Primary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isAddingTemplate = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "新建模板", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { isTemplatesExpanded = !isTemplatesExpanded }, modifier = Modifier.size(32.dp)) {
                                Icon(if (isTemplatesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    AnimatedVisibility(visible = isTemplatesExpanded) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = templateSearchQuery,
                                onValueChange = { templateSearchQuery = it },
                                placeholder = { Text("搜索模板名称或内容...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (templateSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { templateSearchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(999.dp)
                            )

                            if (filteredTemplates.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = SettingsInnerShape,
                                    color = glass.control
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "暂无匹配的提示词模板\n点击右上角「+」新建专属的高效生产力模板。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filteredTemplates.forEach { tpl ->
                                        PromptTemplateItemCard(
                                            template = tpl,
                                            onEdit = { templateToEdit = tpl },
                                            onDelete = {
                                                coroutineScope.launch {
                                                    repository.deletePromptTemplate(tpl)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. 环境变量与安全密钥注入
            item {
                SettingsGlassCard(hazeState = hazeState) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DataObject,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("提示词环境变量注入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "配置自定义环境变量（如 {{PROJECT_NAME}}），发送时自动解密并动态替换",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .echoShapeClick(SettingsInnerShape) { isEnvVarsExpanded = !isEnvVarsExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("环境变量列表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            EchoBadge(text = "${allEnvVars.size} 个", type = EchoBadgeType.Info)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isAddingEnvVar = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "添加变量", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { isEnvVarsExpanded = !isEnvVarsExpanded }, modifier = Modifier.size(32.dp)) {
                                Icon(if (isEnvVarsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    AnimatedVisibility(visible = isEnvVarsExpanded) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (allEnvVars.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = SettingsInnerShape,
                                    color = glass.control
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Code,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "暂无自定义环境变量\n点击右上角「添加变量」创建可动态替换的变量。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    allEnvVars.forEach { variable ->
                                        EnvironmentVariableItemCard(
                                            variable = variable,
                                            onEdit = { envVarToEdit = variable },
                                            onDelete = {
                                                coroutineScope.launch {
                                                    repository.deleteEnvironmentVariable(variable)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // 记忆编辑弹窗
    if (isAddingMemory || memoryToEdit != null) {
        MemoryEditDialog(
            hazeState = hazeState,
            memory = memoryToEdit,
            onDismiss = {
                isAddingMemory = false
                memoryToEdit = null
            },
            onConfirm = { content, _, keywords ->
                coroutineScope.launch {
                    val target = memoryToEdit?.copy(
                        content = content,
                        scope = "user",
                        keywords = keywords,
                        updatedAt = System.currentTimeMillis()
                    ) ?: MemoryItem(
                        content = content,
                        scope = "user",
                        keywords = keywords
                    )
                    repository.saveMemory(target)
                    isAddingMemory = false
                    memoryToEdit = null
                    savedMessage = "全局偏好已保存"
                }
            }
        )
    }

    // 清空全局偏好确认弹窗（严格与对话专属偏好和记忆隔离，需求 3）
    if (showClearAllConfirm) {
        EchoGlassDialog(
            hazeState = hazeState,
            title = { Text("清空全局偏好记忆") },
            text = { Text("确定要清空全部跨会话全局偏好吗？此操作仅清除全局通用偏好，各对话内部的专属记忆与时间线设定将完全保留不受任何影响。") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.clearGlobalMemories()
                            showClearAllConfirm = false
                            savedMessage = "已清空跨会话全局偏好"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("取消")
                }
            },
            onDismissRequest = { showClearAllConfirm = false }
        )
    }

    // 提示词模板新建/编辑弹窗
    if (isAddingTemplate || templateToEdit != null) {
        PromptTemplateEditDialog(
            hazeState = hazeState,
            template = templateToEdit,
            onDismiss = {
                isAddingTemplate = false
                templateToEdit = null
            },
            onConfirm = { name, content, description, category ->
                coroutineScope.launch {
                    val target = templateToEdit?.copy(
                        name = name,
                        content = content,
                        description = description,
                        category = category,
                        updatedAt = System.currentTimeMillis()
                    ) ?: PromptTemplate(
                        name = name,
                        content = content,
                        description = description,
                        category = category
                    )
                    repository.savePromptTemplate(target)
                    isAddingTemplate = false
                    templateToEdit = null
                    savedMessage = "模板已保存"
                }
            }
        )
    }

    // 环境变量新建/编辑弹窗
    if (isAddingEnvVar || envVarToEdit != null) {
        EnvironmentVariableEditDialog(
            hazeState = hazeState,
            variable = envVarToEdit,
            onDismiss = {
                isAddingEnvVar = false
                envVarToEdit = null
            },
            onConfirm = { name, value, description ->
                coroutineScope.launch {
                    val target = envVarToEdit?.copy(
                        name = name,
                        value = value,
                        description = description,
                        updatedAt = System.currentTimeMillis()
                    ) ?: EnvironmentVariable(
                        name = name,
                        value = value,
                        description = description
                    )
                    repository.saveEnvironmentVariable(target)
                    isAddingEnvVar = false
                    envVarToEdit = null
                    savedMessage = "环境变量已保存"
                }
            }
        )
    }


    // 世界书新建/编辑弹窗
    if (isAddingWorldBook || worldBookToEdit != null) {
        WorldBookEditDialog(
            hazeState = hazeState,
            book = worldBookToEdit,
            onDismiss = {
                isAddingWorldBook = false
                worldBookToEdit = null
            },
            onConfirm = { name, description, tags ->
                coroutineScope.launch {
                    val target = worldBookToEdit?.copy(
                        name = name,
                        description = description,
                        tags = tags,
                        updatedAt = System.currentTimeMillis()
                    ) ?: WorldBook(
                        name = name,
                        description = description,
                        tags = tags
                    )
                    if (worldBookToEdit != null) {
                        repository.updateWorldBook(target)
                    } else {
                        repository.insertWorldBook(target)
                    }
                    isAddingWorldBook = false
                    worldBookToEdit = null
                    savedMessage = "世界书已保存"
                }
            }
        )
    }

    // 世界书删除确认弹窗
    worldBookToDelete?.let { book ->
        EchoGlassDialog(
            hazeState = hazeState,
            title = { Text("删除世界书") },
            text = { Text("确定要删除世界书《${book.name}》及其所有包含的词条设定吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteWorldBook(book.id)
                            worldBookToDelete = null
                            savedMessage = "已删除世界书《${book.name}》"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { worldBookToDelete = null }) {
                    Text("取消")
                }
            },
            onDismissRequest = { worldBookToDelete = null }
        )
    }

    // 世界书词条管理弹窗
    selectedWorldBookForEntries?.let { book ->
        WorldBookEntriesManageDialog(
            hazeState = hazeState,
            book = book,
            onDismiss = { selectedWorldBookForEntries = null },
            onAddEntry = { isAddingEntryForBookId = book.id },
            onEditEntry = { entryToEdit = it },
            onDeleteEntry = { entryToDelete = it }
        )
    }

    // 词条新建/编辑弹窗
    val currentBookIdForEntry = isAddingEntryForBookId ?: entryToEdit?.bookId
    if (currentBookIdForEntry != null && (isAddingEntryForBookId != null || entryToEdit != null)) {
        WorldBookEntryEditDialog(
            hazeState = hazeState,
            entry = entryToEdit,
            bookId = currentBookIdForEntry,
            onDismiss = {
                isAddingEntryForBookId = null
                entryToEdit = null
            },
            onConfirm = { name, keys, content, isConstant, priority ->
                coroutineScope.launch {
                    val target = entryToEdit?.copy(
                        name = name,
                        keys = keys,
                        content = content,
                        isConstant = isConstant,
                        priority = priority,
                        updatedAt = System.currentTimeMillis()
                    ) ?: WorldBookEntry(
                        bookId = currentBookIdForEntry,
                        name = name,
                        keys = keys,
                        content = content,
                        isConstant = isConstant,
                        priority = priority
                    )
                    if (entryToEdit != null) {
                        repository.updateWorldBookEntry(target)
                    } else {
                        repository.insertWorldBookEntry(target)
                    }
                    isAddingEntryForBookId = null
                    entryToEdit = null
                    savedMessage = "设定词条已保存"
                }
            }
        )
    }

    // 词条删除确认弹窗
    entryToDelete?.let { entry ->
        EchoGlassDialog(
            hazeState = hazeState,
            title = { Text("删除设定词条") },
            text = { Text("确定要删除词条【${entry.name}】吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteWorldBookEntry(entry.id)
                            entryToDelete = null
                            savedMessage = "已删除词条【${entry.name}】"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("取消")
                }
            },
            onDismissRequest = { entryToDelete = null }
        )
    }
}

