@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aiassistant.ui.screens.chat.story

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiassistant.domain.model.*
import com.aiassistant.ui.components.EchoConfirmDialog
import com.aiassistant.ui.screens.chat.*
import com.aiassistant.ui.screens.roleplay.CharacterEditorScreen
import com.aiassistant.ui.screens.roleplay.ScenarioEditorScreen
import com.aiassistant.ui.screens.roleplay.WorldBookImportButton
import dev.chrisbanes.haze.HazeState

/**
 * 故事工作台使用 Conversation 的设定/时间线和同一套生成配置。
 * 角色、世界观属于可复用资料；本故事的副本由 RoleplaySession 关联。
 * 全屏工作区代替旧版五层弹窗，不再单独维护一套模型/记忆设置。
 */
@Composable
fun StoryUnifiedSettingsDialog(
    hazeState: HazeState,
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
    onSaveAll: (List<Long>, Long?, NarrativeMode, String, TempChatSettings, String?, (Boolean) -> Unit) -> Unit,
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
    onModelAvatarChanged: () -> Unit,
    knowledge: StoryKnowledgeState,
    knowledgeActions: StoryKnowledgeActions,
    onOpenSharedSettings: () -> Unit,
    onOpenContext: () -> Unit,
    title: String,
    initialSection: String = "writing"
) {
    val incoming = remember(session, currentPrompt, tempSettings) {
        StoryWorkspaceDraft.from(session, currentPrompt, tempSettings)
    }
    var baseline by remember(session.id) { mutableStateOf(incoming) }
    var draft by rememberSaveable(session.id, stateSaver = androidx.compose.runtime.saveable.Saver<StoryWorkspaceDraft, String>(
        save = { com.google.gson.Gson().toJson(it) },
        restore = { runCatching { com.google.gson.Gson().fromJson(it, StoryWorkspaceDraft::class.java) }.getOrNull() }
    )) { mutableStateOf(incoming) }
    LaunchedEffect(incoming) {
        draft = mergeStoryWorkspaceDraft(draft, baseline, incoming)
        baseline = incoming
    }
    var section by rememberSaveable(session.id) { mutableStateOf(initialSection) }
    var discardChanges by remember { mutableStateOf(false) }
    var confirmConvert by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<CharacterProfile?>(null) }
    var editingScenario by remember { mutableStateOf<RoleplayScenario?>(null) }
    var showLoreLibrary by remember { mutableStateOf(false) }
    var customPlotAction by remember { mutableStateOf<PlotAction?>(null) }
    var customPlotInstruction by remember { mutableStateOf("") }
    var templateMenu by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf<String?>(null) }
    var timelineExpanded by rememberSaveable(session.id) { mutableStateOf(true) }
    var memoryExpanded by rememberSaveable(session.id) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val changed = draft != baseline
    val close = { if (!saving) { if (changed) discardChanges = true else onDismiss() } }
    fun save(afterSave: () -> Unit = {}) {
        if (saving) return
        saving = true
        saveError = false
        onSaveAll(draft.characterIds.toList(), draft.scenarioId, draft.mode, draft.summary,
            draft.settings, draft.instructions.trim().ifBlank { null }) { success ->
            saving = false
            if (success) afterSave() else saveError = true
        }
    }
    val tabs = listOf("writing" to "写作", "cast" to "人物与世界", "facts" to "会话设定",
        "timeline" to "时间线", "lore" to "世界书")

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = close)
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground) {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Column {
                                Text("故事工作台", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } },
                            navigationIcon = { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回正文") } },
                            actions = { TextButton(enabled = !saving, onClick = { if (changed) save() else onDismiss() }) { Text(if (saving) "保存中…" else if (changed) "保存修改" else "返回正文") } }
                        )
                        ScrollableTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == section }.coerceAtLeast(0),
                            edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                            tabs.forEach { (key, label) ->
                                Tab(selected = section == key, onClick = { section = key }, text = { Text(label) })
                            }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (saveError) Text("保存未完成，修改仍保留在此页，请重试。", color = MaterialTheme.colorScheme.error)
                    when (section) {
                        "writing" -> {
                            WorkspaceHeading("本故事的写作要求", "只影响当前故事；本轮临时要求可直接在输入框发送。")
                            OutlinedTextField(value = draft.instructions, onValueChange = { draft = draft.copy(instructions = it) },
                                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
                                label = { Text("视角、文风、边界与当前写作目标") },
                                placeholder = { Text("例如：使用第三人称限知视角；保留我的行动决定；本章写到调查队抵达旧车站。") })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box {
                                    TextButton(onClick = { templateMenu = true }) { Text("使用提示词模板") }
                                    DropdownMenu(expanded = templateMenu, onDismissRequest = { templateMenu = false }) {
                                        if (templates.isEmpty()) DropdownMenuItem(text = { Text("暂无模板") }, onClick = {}, enabled = false)
                                        templates.forEach { template ->
                                            DropdownMenuItem(text = { Text(template.name) }, onClick = {
                                                draft = draft.copy(instructions = template.content); templateMenu = false
                                            })
                                        }
                                    }
                                }
                                TextButton(enabled = draft.instructions.isNotBlank(), onClick = { templateName = "" }) { Text("存为模板") }
                            }
                            WorkspaceHeading("叙事方式", "决定模型承担的角色，不改变你对故事内容的控制。")
                            NarrativeMode.entries.forEach { mode ->
                                Row(Modifier.fillMaxWidth().clickable { draft = draft.copy(mode = mode) }.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = draft.mode == mode, onClick = { draft = draft.copy(mode = mode) })
                                    Column(Modifier.weight(1f)) {
                                        Text(mode.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            HorizontalDivider()
                            WorkspaceHeading("已发生的剧情摘要", "摘要帮助续写，规则放在会话设定，事件放在时间线。")
                            OutlinedTextField(value = draft.summary, onValueChange = { draft = draft.copy(summary = it) },
                                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 7, label = { Text("当前剧情摘要") })
                            WorkspaceAction("提炼剧情与关键事实", "从已保存的故事内容生成摘要及事实", Icons.Default.AutoAwesome) { save { onSummarizeMemories() } }
                            WorkspaceAction("上下文与压缩", "检查实际预算、压缩档位和滚动摘要", Icons.Default.DataUsage) { save { onOpenContext() } }
                            WorkspaceAction("模型与生成", "${currentOption?.modelName ?: fallbackModel} · ${if (draft.settings.enableReplyDirections) "${draft.settings.replyDirectionCount} 个回复方向" else "直接回复"}", Icons.Default.Tune) {
                                save { onOpenSharedSettings() }
                            }
                            WorkspaceAction("智能导入人物与场景", "分析粘贴内容，逐项确认后加入本故事", Icons.Default.Add) { save { onOpenSmartAppend() } }
                            WorkspaceHeading("推进剧情", "选择操作后确认本轮指令，再交给当前模型生成。")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(PlotAction.CONTINUE, PlotAction.REWRITE, PlotAction.EXTEND, PlotAction.SHORTEN, PlotAction.CHANGE_PERSPECTIVE, PlotAction.CHANGE_TONE, PlotAction.BRANCH_CHOICES, PlotAction.DIALOGUE_ONLY, PlotAction.NARRATION_ONLY, PlotAction.CUSTOM).forEach { action ->
                                    AssistChip(onClick = { customPlotAction = action; customPlotInstruction = "" }, label = { Text(action.displayName) })
                                }
                            }
                            TextButton(onClick = { confirmConvert = true }) { Text("转为普通对话") }
                        }
                        "cast" -> {
                            WorkspaceHeading("本故事的人物", "勾选决定谁参与；编辑保存为本故事副本，不改其他故事。")
                            val effective = remember(session, allCharacters, characters) {
                                (session.getCustomizedCharacters(characters) + allCharacters).distinctBy { it.id }
                            }
                            if (effective.isEmpty()) Text("还没有人物。可以新建，或从创作资料库导入。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            effective.forEach { character ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = character.id in draft.characterIds, onCheckedChange = { checked ->
                                        draft = draft.copy(characterIds = if (checked) draft.characterIds + character.id else draft.characterIds - character.id)
                                    })
                                    Column(Modifier.weight(1f)) {
                                        Text(character.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                        Text(character.identity.ifBlank { character.personality }.ifBlank { "未填写身份" }, maxLines = 2,
                                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { editingCharacter = character }) { Icon(Icons.Default.Edit, "编辑 ${character.name} 的故事副本") }
                                }
                            }
                            OutlinedButton(onClick = { editingCharacter = CharacterProfile(name = "") }) { Icon(Icons.Default.Add, null); Text("添加新人物") }
                            HorizontalDivider()
                            WorkspaceHeading("世界与场景", "选择背景资料；故事中的修改与资料库分开保存。")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = draft.scenarioId == null, onClick = { draft = draft.copy(scenarioId = null) })
                                Text("自由世界（不绑定资料库场景）", style = MaterialTheme.typography.bodyMedium)
                            }
                            val availableScenarios = remember(allScenarios, scenario) { (listOfNotNull(scenario) + allScenarios).distinctBy { it.id } }
                            availableScenarios.forEach { item ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = draft.scenarioId == item.id, onClick = { draft = draft.copy(scenarioId = item.id) })
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                        Text(item.premise.ifBlank { item.worldview }.ifBlank { "未填写背景" }, maxLines = 2,
                                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { editingScenario = item }) { Icon(Icons.Default.Edit, "编辑 ${item.name} 的故事副本") }
                                }
                            }
                            OutlinedButton(onClick = { editingScenario = RoleplayScenario(name = "") }) { Icon(Icons.Default.Add, null); Text("创建本故事世界观") }
                        }
                        "facts" -> {
                            WorkspaceHeading("会话设定与规则", "与普通对话共用同一份数据。已确认的事实、人物约束和写作边界放在这里；修改即时保存。")
                            ChatSettingsSessionMemorySection(
                                sessionMemories = knowledge.memories,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                enableSessionMemory = draft.settings.enableSessionMemory,
                                onEnableSessionMemoryChange = { draft = draft.copy(settings = draft.settings.copy(enableSessionMemory = it)) },
                                hazeState = hazeState, onAddMemory = knowledgeActions.addMemory,
                                onUpdateMemory = knowledgeActions.updateMemory, onToggleMemory = knowledgeActions.toggleMemory,
                                onDeleteMemory = knowledgeActions.deleteMemory, onClearMemories = knowledgeActions.clearMemories,
                                isExpanded = memoryExpanded, onExpandedChange = { memoryExpanded = it }
                            )
                            WorkspaceAction("梳理历史中的事件与设定", "先提取草稿，再审核；不会把候选结果直接当成事实", Icons.Default.FactCheck) { section = "timeline" }
                            WorkspaceAction("故事记忆档案", "管理旧版故事事实与关系记录", Icons.Default.Inventory2) { save { onNavigateToMemory() } }
                        }
                        "timeline" -> {
                            WorkspaceHeading("故事时间线", "记录已发生事件与当前故事时间。支持从历史提取、增量续提、断点恢复和逐条审核；手动修改即时保存。")
                            ChatSettingsTimelineSection(
                                timelineNodes = knowledge.timeline, currentStoryTime = knowledge.currentTime,
                                contentColor = MaterialTheme.colorScheme.onSurface, secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                hazeState = hazeState, isReconcilingTimeline = knowledge.isExtracting,
                                reconcileTimelineProgress = knowledge.extractionProgress, hasSavedTimelineDraft = knowledge.hasDraft,
                                checkpoint = knowledge.checkpoint, newMessagesCountSinceCheckpoint = knowledge.newMessageCount,
                                onStartTimelineReconciliation = knowledgeActions.extractAll,
                                onContinueTimelineReconciliation = knowledgeActions.continueExtraction,
                                onReconcileFromCheckpoint = knowledgeActions.extractNew,
                                onOpenTimelineDraft = { save { knowledgeActions.reviewDraft() } },
                                onClearTimelineDraft = knowledgeActions.clearDraft,
                                onCancelTimelineReconciliation = knowledgeActions.cancelExtraction,
                                onUpdateCurrentStoryTime = knowledgeActions.updateTime,
                                onAddTimelineNode = knowledgeActions.addEvent, onUpdateTimelineNode = knowledgeActions.updateEvent,
                                onDeleteTimelineNode = knowledgeActions.deleteEvent, onClearTimeline = knowledgeActions.clearTimeline,
                                isExpanded = timelineExpanded, onExpandedChange = { timelineExpanded = it }
                            )
                        }
                        "lore" -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { WorkspaceHeading("本故事的世界书", "导入、启用与绑定是三个独立步骤。") }
                                WorldBookImportButton()
                            }
                            OutlinedButton(onClick = { showLoreLibrary = true }) { Text("打开资料库：启用书目与编辑词条") }
                            ChatSettingsWorldBookAndExternalMemorySection(
                                enableExternalMemory = draft.settings.enableExternalMemory,
                                onEnableExternalMemoryChange = { draft = draft.copy(settings = draft.settings.copy(enableExternalMemory = it)) },
                                enableWorldBook = draft.settings.enableWorldBook,
                                onEnableWorldBookChange = { draft = draft.copy(settings = draft.settings.copy(enableWorldBook = it)) },
                                activeWorldBookIds = draft.settings.activeWorldBookIds,
                                onActiveWorldBookIdsChange = { draft = draft.copy(settings = draft.settings.copy(activeWorldBookIds = it)) },
                                contentColor = MaterialTheme.colorScheme.onSurface, secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                hazeState = hazeState
                            )
                            Text("人物与世界观定义背景；会话设定记录已确认约束；时间线记录已发生事件；世界书按关键词提供相关资料；摘要用于回顾。条目数字只影响预算与排序，不代表更高模型权限。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("导入后打开资料库检查并启用书目和词条，再在这里绑定。选择“仅选中”后不选书目表示不注入；“全部已启用”会随资料库变化。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
    if (showLoreLibrary) Dialog(onDismissRequest = { showLoreLibrary = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                TopAppBar(title = { Text("世界书资料库") }, navigationIcon = { IconButton(onClick = { showLoreLibrary = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回故事") } })
                com.aiassistant.ui.screens.roleplay.StudioLoreLibrary(modifier = Modifier.fillMaxSize())
            }
        }
    }
    customPlotAction?.let { action -> AlertDialog(onDismissRequest = { customPlotAction = null }, title = { Text(action.displayName) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("将按当前故事设定发送此操作，生成一轮回复。可补充具体要求。")
            OutlinedTextField(value = customPlotInstruction, onValueChange = { customPlotInstruction = it }, label = { Text("本轮补充要求") }, minLines = 2)
        } }, confirmButton = { TextButton(onClick = { save { onPlotAction(action, customPlotInstruction.trim().ifBlank { null }) }; customPlotAction = null }) { Text("开始生成") } },
        dismissButton = { TextButton(onClick = { customPlotAction = null }) { Text("取消") } }) }
    if (discardChanges) EchoConfirmDialog(title = "放弃未保存的写作设置？", text = "人物选择、写作要求和开关尚未保存。已经保存的会话设定与时间线不会撤销。",
        confirmText = "放弃修改", onConfirm = { discardChanges = false; onDismiss() }, onDismiss = { discardChanges = false })
    if (confirmConvert) EchoConfirmDialog(title = "转为普通对话？", text = "将先保存本页修改，再把故事设定合并进系统提示词，消息继续保留。保存失败时会留在工作台供重试。",
        confirmText = "保存并转换", onConfirm = { confirmConvert = false; save { onConvertToNormal() } }, onDismiss = { confirmConvert = false })
    if (templateName != null) AlertDialog(onDismissRequest = { templateName = null }, title = { Text("保存提示词模板") },
        text = { OutlinedTextField(value = templateName.orEmpty(), onValueChange = { templateName = it }, label = { Text("模板名称") }) },
        confirmButton = { TextButton(enabled = !templateName.isNullOrBlank(), onClick = { onSavePromptTemplate(templateName.orEmpty(), draft.instructions); templateName = null }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { templateName = null }) { Text("取消") } })
    editingCharacter?.let { character ->
        Dialog(onDismissRequest = { editingCharacter = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) { CharacterEditorScreen(character = character.takeIf { it.name.isNotBlank() },
                onSave = { onSaveLocalCharacter(it); editingCharacter = null },
                onDelete = { onDeleteLocalCharacter(character); editingCharacter = null }, onBack = { editingCharacter = null }) }
        }
    }
    editingScenario?.let { item ->
        Dialog(onDismissRequest = { editingScenario = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) { ScenarioEditorScreen(scenario = item.takeIf { it.name.isNotBlank() },
                onSave = { onSaveLocalScenario(it); editingScenario = null },
                onDelete = if (item.id == scenario?.id) ({ onDeleteLocalScenario(); editingScenario = null }) else null, onBack = { editingScenario = null }) }
        }
    }
}

@Composable
private fun WorkspaceHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkspaceAction(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
