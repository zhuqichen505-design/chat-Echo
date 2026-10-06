@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.aiassistant.ui.screens.roleplay

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.aiassistant.AiAssistantApp
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.domain.model.NarrativeMode
import com.aiassistant.ui.components.EchoConfirmDialog
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoPrimaryButton
import com.aiassistant.ui.components.EchoGlassButton
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.utils.AnalyzedRoleplayBundle
import com.aiassistant.utils.RoleplaySmartAnalyzer
import com.aiassistant.utils.RoleplaySmartParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RoleplayStudioScreen(
    viewModel: RoleplayViewModel,
    onNavigateToCharacters: () -> Unit,
    onNavigateToScenarios: () -> Unit,
    onNavigateToCharacterEditor: (CharacterProfile?) -> Unit,
    onNavigateToScenarioEditor: (RoleplayScenario?) -> Unit,
    onNavigateToSession: (Long) -> Unit,
    onCreateNewSession: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AiAssistantApp.instance.repository }
    val hazeState = rememberEchoHazeState()
    val characters by viewModel.characters.collectAsState()
    val scenarios by viewModel.scenarios.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val currentTab = viewModel.studioTab
    var showGuide by remember { mutableStateOf(false) }
    var showSmartAnalyzeDialog by remember { mutableStateOf(false) }
    var analyzedBundle by remember { mutableStateOf<AnalyzedRoleplayBundle?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("角色与创作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("故事创作 · 多角色演绎 · 沉浸互动", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSmartAnalyzeDialog = true }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI智能识别与拆解",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showGuide = !showGuide }) {
                        Icon(
                            if (showGuide) Icons.Default.Info else Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "玩法说明"
                        )
                    }
                    IconButton(onClick = onCreateNewSession) {
                        Icon(Icons.Default.Add, contentDescription = "新建故事")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 可折叠玩法引导卡片
            AnimatedVisibility(
                visible = showGuide,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                StudioGuideCard(onDismiss = { showGuide = false })
            }

            // 标签页导航
            TabRow(
                selectedTabIndex = currentTab,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { viewModel.studioTab = 0 },
                    text = { Text("故事 (${sessions.size})") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { viewModel.studioTab = 1 },
                    text = { Text("角色 (${characters.size})") },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                Tab(
                    selected = currentTab == 2,
                    onClick = { viewModel.studioTab = 2 },
                    text = { Text("世界观 (${scenarios.size})") },
                    icon = { Icon(Icons.Default.Landscape, contentDescription = null) }
                )
            }

            // 内容区域
            when (currentTab) {
                0 -> SessionsTab(
                    sessions = sessions,
                    characters = characters,
                    scenarios = scenarios,
                    onSessionClick = { onNavigateToSession(it.conversationId) },
                    onDeleteSession = { viewModel.deleteSession(it) },
                    onUpdateSessionContext = { session, cIds, scId, mode, summary ->
                        viewModel.updateStorySessionContext(session, cIds, scId, mode, summary)
                    },
                    onSyncCharacterToDb = { char ->
                        viewModel.syncCharacterToDatabase(char) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    },
                    onSyncScenarioToDb = { sc ->
                        viewModel.syncScenarioToDatabase(sc) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    },
                    onSyncCharacterFromDb = { session, charId ->
                        viewModel.syncCharacterFromDatabase(session, charId) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    },
                    onSyncScenarioFromDb = { session, scId ->
                        viewModel.syncScenarioFromDatabase(session, scId) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    },
                    onCreateNewSession = onCreateNewSession,
                    onSmartAnalyze = { showSmartAnalyzeDialog = true }
                )
                1 -> CharactersTab(
                    characters = characters,
                    onCharacterClick = { onNavigateToCharacterEditor(it) },
                    onCreateClick = { onNavigateToCharacterEditor(null) },
                    onFavoriteClick = { id, isFavorite -> viewModel.setCharacterFavorite(id, isFavorite) },
                    onDeleteMultiple = { viewModel.deleteCharacters(it) },
                    onSmartAnalyze = { showSmartAnalyzeDialog = true },
                    onSearch = { viewModel.searchCharacters(it) }
                )
                2 -> ScenariosTab(
                    scenarios = scenarios,
                    onScenarioClick = { onNavigateToScenarioEditor(it) },
                    onCreateClick = { onNavigateToScenarioEditor(null) },
                    onFavoriteClick = { id, isFavorite -> viewModel.setScenarioFavorite(id, isFavorite) },
                    onDeleteMultiple = { viewModel.deleteScenarios(it) },
                    onSmartAnalyze = { showSmartAnalyzeDialog = true },
                    onSearch = { viewModel.searchScenarios(it) }
                )
            }
        }
    }

    // AI 剧本与人设一键智能提取弹窗
    if (showSmartAnalyzeDialog) {
        SmartAnalyzeStudioDialog(
            hazeState = hazeState,
            repository = repository,
            onDismiss = { showSmartAnalyzeDialog = false },
            onAnalyzed = { bundle ->
                showSmartAnalyzeDialog = false
                analyzedBundle = bundle
            }
        )
    }

    // 智能提取结果预览与确认入库弹窗
    analyzedBundle?.let { bundle ->
        SmartAnalyzeResultDialog(
            hazeState = hazeState,
            bundle = bundle,
            existingCharacters = characters,
            onDismiss = { analyzedBundle = null },
            onSave = { selectedChars, scenario, resolutionMap, narrativeMode, startSession ->
                analyzedBundle = null
                viewModel.importAnalyzedBundleWithConflictResolution(
                    characters = selectedChars,
                    scenario = scenario,
                    resolutionMap = resolutionMap,
                    narrativeMode = narrativeMode,
                    startSession = startSession,
                    onSessionCreated = { conversationId ->
                        if (conversationId > 0) {
                            onNavigateToSession(conversationId)
                        }
                    }
                )
            }
        )
    }

    // 处理 UI 状态 Toast
    when (val state = uiState) {
        is RoleplayUiState.SaveSuccess -> {
            LaunchedEffect(state) {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.clearUiState()
            }
        }
        is RoleplayUiState.Error -> {
            LaunchedEffect(state) {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.clearUiState()
            }
        }
        else -> {}
    }
}

@Composable
private fun StudioGuideCard(onDismiss: () -> Unit) {
    EchoGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EchoTokens.Spacing.screenHorizontal, vertical = 6.dp),
        shape = EchoTokens.Radius.shapeLg,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "角色扮演工作室 · 核心玩法指南",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            GuideStepRow(
                step = "1",
                title = "一键智能提取 / 自定义创建角色",
                desc = "设定角色姓名、身份、性格与口吻习惯，或直接粘贴长篇小说由 AI 自动提炼。"
            )
            Spacer(modifier = Modifier.height(6.dp))
            GuideStepRow(
                step = "2",
                title = "构筑世界观与场景氛围",
                desc = "配置宏观时代背景、现场环境与当前剧情冲突，为互动设定规则与基调。"
            )
            Spacer(modifier = Modifier.height(6.dp))
            GuideStepRow(
                step = "3",
                title = "多轮沉浸式演绎与剧情推进",
                desc = "支持第一人称角色交互、旁白叙事模式与剧情分支，AI 将严格遵循人设法则。"
            )
        }
    }
}

@Composable
private fun GuideStepRow(step: String, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(step, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionsTab(
    sessions: List<RoleplaySession>,
    characters: List<CharacterProfile>,
    scenarios: List<RoleplayScenario>,
    onSessionClick: (RoleplaySession) -> Unit,
    onDeleteSession: (RoleplaySession) -> Unit,
    onUpdateSessionContext: (RoleplaySession, List<Long>, Long?, NarrativeMode?, String?) -> Unit,
    onSyncCharacterToDb: (CharacterProfile) -> Unit,
    onSyncScenarioToDb: (RoleplayScenario) -> Unit,
    onSyncCharacterFromDb: (RoleplaySession, Long) -> Unit,
    onSyncScenarioFromDb: (RoleplaySession, Long) -> Unit,
    onCreateNewSession: () -> Unit,
    onSmartAnalyze: () -> Unit
) {
    if (sessions.isEmpty()) {
        StudioEmptyState(
            icon = Icons.AutoMirrored.Filled.Chat,
            title = "暂无故事创作",
            description = "您可以点击右上角开启故事创作，或使用 AI 智能提取剧本一键开局",
            actionText = "新建故事",
            onAction = onCreateNewSession,
            secondaryActionText = "AI 智能提取剧本",
            onSecondaryAction = onSmartAnalyze
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(EchoTokens.Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.itemSpacing)
        ) {
            items(sessions) { session ->
                SessionCard(
                    session = session,
                    characters = characters,
                    scenarios = scenarios,
                    onClick = { onSessionClick(session) },
                    onDelete = { onDeleteSession(session) },
                    onUpdateContext = { cIds, scId, mode, summary ->
                        onUpdateSessionContext(session, cIds, scId, mode, summary)
                    },
                    onSyncCharacterToDb = onSyncCharacterToDb,
                    onSyncScenarioToDb = onSyncScenarioToDb,
                    onSyncCharacterFromDb = { charId -> onSyncCharacterFromDb(session, charId) },
                    onSyncScenarioFromDb = { scId -> onSyncScenarioFromDb(session, scId) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: RoleplaySession,
    characters: List<CharacterProfile>,
    scenarios: List<RoleplayScenario>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onUpdateContext: (List<Long>, Long?, NarrativeMode?, String?) -> Unit,
    onSyncCharacterToDb: (CharacterProfile) -> Unit,
    onSyncScenarioToDb: (RoleplayScenario) -> Unit,
    onSyncCharacterFromDb: (Long) -> Unit,
    onSyncScenarioFromDb: (Long) -> Unit
) {
    val hazeState = rememberEchoHazeState()
    var showActionMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditContextDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }

    val boundCharNames = remember(session.characterIds, session.characterId, characters) {
        val ids = session.getEffectiveCharacterIds()
        characters.filter { it.id in ids }.map { it.name }
    }
    val boundScenario = remember(session.scenarioId, scenarios) {
        scenarios.firstOrNull { it.id == session.scenarioId }
    }

    val storyTitle = remember(boundCharNames, boundScenario, session.id) {
        when {
            boundCharNames.isNotEmpty() && boundScenario != null -> "${boundCharNames.joinToString("、")} · ${boundScenario.name}"
            boundCharNames.isNotEmpty() -> "故事：" + boundCharNames.joinToString("、")
            boundScenario != null -> "世界观：${boundScenario.name}"
            else -> "故事创作会话 #${session.id}"
        }
    }

    EchoGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = { showActionMenu = true },
        shape = EchoTokens.Radius.shapeLg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = storyTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold)
                )
                if (boundCharNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "登场角色：${boundCharNames.joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (session.currentPlotSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = session.currentPlotSummary.take(60) + if (session.currentPlotSummary.length > 60) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SuggestionChip(
                        onClick = { showEditContextDialog = true },
                        label = { Text("叙事模式: ${NarrativeMode.fromValue(session.narrativeMode).displayName}") },
                        modifier = Modifier.defaultMinSize(minHeight = 26.dp)
                    )
                    Text(
                        text = "长按故事可同步或修改设定",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { showActionMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "操作", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showActionMenu) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showActionMenu = false },
            title = {
                Text(storyTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EchoGlassCard(
                        onClick = {
                            showActionMenu = false
                            showSyncDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = EchoTokens.Radius.shapeSm,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("🔄 双向设定同步 (Sync)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("按故事更新主库，或从主库拉取最新设定", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    EchoGlassCard(
                        onClick = {
                            showActionMenu = false
                            showEditContextDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = EchoTokens.Radius.shapeSm
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("✏️ 修改故事设定与模式", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("调整绑定世界观、登场角色与叙事模式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    EchoGlassCard(
                        onClick = {
                            showActionMenu = false
                            showDeleteDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = EchoTokens.Radius.shapeSm,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("🗑️ 删除故事会话", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                Text("删除本故事及其对话历史", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showActionMenu = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }

    if (showSyncDialog) {
        StorySyncDialog(
            hazeState = hazeState,
            session = session,
            allCharacters = characters,
            allScenarios = scenarios,
            onDismiss = { showSyncDialog = false },
            onSyncCharacterToDb = onSyncCharacterToDb,
            onSyncScenarioToDb = onSyncScenarioToDb,
            onSyncCharacterFromDb = onSyncCharacterFromDb,
            onSyncScenarioFromDb = onSyncScenarioFromDb
        )
    }

    if (showEditContextDialog) {
        EditStorySessionContextDialog(
            hazeState = hazeState,
            session = session,
            allCharacters = characters,
            allScenarios = scenarios,
            onDismiss = { showEditContextDialog = false },
            onSave = { newCharIds, newScenarioId, newMode, newSummary ->
                showEditContextDialog = false
                onUpdateContext(newCharIds, newScenarioId, newMode, newSummary)
            }
        )
    }

    if (showDeleteDialog) {
        EchoConfirmDialog(
            title = "确认删除故事",
            text = "确定要删除此故事会话吗？对话历史记录将一并清除。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                onDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
fun StorySyncDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    session: RoleplaySession,
    allCharacters: List<CharacterProfile>,
    allScenarios: List<RoleplayScenario>,
    onDismiss: () -> Unit,
    onSyncCharacterToDb: (CharacterProfile) -> Unit,
    onSyncScenarioToDb: (RoleplayScenario) -> Unit,
    onSyncCharacterFromDb: (Long) -> Unit,
    onSyncScenarioFromDb: (Long) -> Unit
) {
    val boundChars = remember(session, allCharacters) {
        session.getCustomizedCharacters(allCharacters.filter { it.id in session.getEffectiveCharacterIds() })
    }
    val boundScenario = remember(session, allScenarios) {
        val base = allScenarios.firstOrNull { it.id == session.scenarioId }
        session.getCustomizedScenario(base)
    }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("故事设定双向同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("支持将故事专属设定写入全局库，或拉取主库最新设定", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("🎭 本故事包含的角色 (${boundChars.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                if (boundChars.isEmpty()) {
                    item {
                        Text("当前故事暂无登场角色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(boundChars) { char ->
                        EchoGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = EchoTokens.Radius.shapeMd
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = char.name + if (char.identity.isNotBlank()) " · ${char.identity}" else "",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    val isCustomized = session.customCharacterData?.contains(char.name) == true
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(if (isCustomized) "故事内定制" else "主库关联") },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                                if (char.background.isNotBlank()) {
                                    Text(
                                        text = char.background.take(80) + if (char.background.length > 80) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onSyncCharacterToDb(char)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("按故事更新库", style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (char.id > 0) {
                                        OutlinedButton(
                                            onClick = {
                                                onSyncCharacterFromDb(char.id)
                                            },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("按主库更新故事", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("🌍 本故事的世界观设定", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                if (boundScenario == null) {
                    item {
                        Text("当前故事未绑定世界观（自由背景）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    item {
                        val sc = boundScenario
                        EchoGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = EchoTokens.Radius.shapeMd
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(sc.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    val isCustomized = session.customScenarioData != null
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(if (isCustomized) "故事内定制" else "主库关联") },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                                if (sc.worldview.isNotBlank()) {
                                    Text(
                                        text = sc.worldview.take(80) + if (sc.worldview.length > 80) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onSyncScenarioToDb(sc)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("按故事更新库", style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (sc.id > 0) {
                                        OutlinedButton(
                                            onClick = {
                                                onSyncScenarioFromDb(sc.id)
                                            },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("按主库更新故事", style = MaterialTheme.typography.labelMedium)
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
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onDismiss) {
                    Text("完成")
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharactersTab(
    characters: List<CharacterProfile>,
    onCharacterClick: (CharacterProfile) -> Unit,
    onCreateClick: () -> Unit,
    onFavoriteClick: (Long, Boolean) -> Unit,
    onDeleteMultiple: (List<Long>) -> Unit,
    onSmartAnalyze: () -> Unit,
    onSearch: (String) -> Unit = {}
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSelectionMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EchoTokens.Spacing.screenHorizontal, vertical = 6.dp),
                shape = EchoTokens.Radius.shapeMd,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "已选 ${selectedIds.size} / ${characters.size} 项",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                selectedIds = if (selectedIds.size == characters.size) emptySet() else characters.map { it.id }.toSet()
                            }
                        ) {
                            Text(if (selectedIds.size == characters.size) "取消全选" else "全选")
                        }
                        Button(
                            onClick = { showDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除 (${selectedIds.size})")
                        }
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "退出选择")
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearch(it)
                },
                placeholder = { Text("搜索角色名称、职业或性格...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            onSearch("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "清除搜索", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EchoTokens.Spacing.screenHorizontal, vertical = 4.dp),
                shape = EchoTokens.Radius.shapeMd,
                singleLine = true
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EchoTokens.Spacing.screenHorizontal, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EchoPrimaryButton(
                    onClick = onCreateClick,
                    modifier = Modifier.weight(1f),
                    shape = EchoTokens.Radius.shapeMd
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("手动创建角色")
                }
                EchoGlassButton(
                    onClick = onSmartAnalyze,
                    modifier = Modifier.weight(1f),
                    shape = EchoTokens.Radius.shapeMd
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI 智能提取")
                }
            }
        }

        if (characters.isEmpty()) {
            StudioEmptyState(
                icon = Icons.Default.Person,
                title = "暂无角色人设",
                description = "点击上方按钮创建新角色，或粘贴小说由 AI 提炼角色卡",
                actionText = "创建角色",
                onAction = onCreateClick,
                secondaryActionText = "从文本/小说提取",
                onSecondaryAction = onSmartAnalyze
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = EchoTokens.Spacing.screenHorizontal, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.itemSpacing)
            ) {
                items(characters) { character ->
                    val isSelected = character.id in selectedIds
                    CharacterCard(
                        character = character,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                selectedIds = if (isSelected) selectedIds - character.id else selectedIds + character.id
                            } else {
                                onCharacterClick(character)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedIds = setOf(character.id)
                            }
                        },
                        onFavoriteClick = { onFavoriteClick(character.id, !character.isFavorite) }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        EchoConfirmDialog(
            title = "确认批量删除",
            text = "确定要删除选中的 ${selectedIds.size} 位角色吗？此操作无法撤销。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                onDeleteMultiple(selectedIds.toList())
                isSelectionMode = false
                selectedIds = emptySet()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharacterCard(
    character: CharacterProfile,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onFavoriteClick: () -> Unit
) {
    EchoGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
        shape = EchoTokens.Radius.shapeLg,
        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Unspecified
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (character.identity.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "· ${character.identity}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                if (character.personality.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "性格：${character.personality}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (!character.tags.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "标签：${character.tags}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (!isSelectionMode) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        if (character.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (character.isFavorite) "取消收藏" else "收藏",
                        tint = if (character.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScenariosTab(
    scenarios: List<RoleplayScenario>,
    onScenarioClick: (RoleplayScenario) -> Unit,
    onCreateClick: () -> Unit,
    onFavoriteClick: (Long, Boolean) -> Unit,
    onDeleteMultiple: (List<Long>) -> Unit,
    onSmartAnalyze: () -> Unit,
    onSearch: (String) -> Unit = {}
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSelectionMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EchoTokens.Spacing.screenHorizontal, vertical = 6.dp),
                shape = EchoTokens.Radius.shapeMd,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "已选 ${selectedIds.size} / ${scenarios.size} 项",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                selectedIds = if (selectedIds.size == scenarios.size) emptySet() else scenarios.map { it.id }.toSet()
                            }
                        ) {
                            Text(if (selectedIds.size == scenarios.size) "取消全选" else "全选")
                        }
                        Button(
                            onClick = { showDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除 (${selectedIds.size})")
                        }
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "退出选择")
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearch(it)
                },
                placeholder = { Text("搜索世界观、背景或设定...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            onSearch("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "清除搜索", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EchoTokens.Spacing.screenHorizontal, vertical = 4.dp),
                shape = EchoTokens.Radius.shapeMd,
                singleLine = true
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EchoTokens.Spacing.screenHorizontal, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EchoPrimaryButton(
                    onClick = onCreateClick,
                    modifier = Modifier.weight(1f),
                    shape = EchoTokens.Radius.shapeMd
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("手动创建世界观")
                }
                EchoGlassButton(
                    onClick = onSmartAnalyze,
                    modifier = Modifier.weight(1f),
                    shape = EchoTokens.Radius.shapeMd
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI 智能提取")
                }
            }
        }

        if (scenarios.isEmpty()) {
            StudioEmptyState(
                icon = Icons.Default.Landscape,
                title = "暂无世界观设定",
                description = "点击上方按钮创建新世界观，或粘贴小说由 AI 提炼剧情大纲与时空背景",
                actionText = "创建世界观",
                onAction = onCreateClick,
                secondaryActionText = "从文本/小说提取",
                onSecondaryAction = onSmartAnalyze
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = EchoTokens.Spacing.screenHorizontal, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.itemSpacing)
            ) {
                items(scenarios) { scenario ->
                    val isSelected = scenario.id in selectedIds
                    ScenarioCard(
                        scenario = scenario,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                selectedIds = if (isSelected) selectedIds - scenario.id else selectedIds + scenario.id
                            } else {
                                onScenarioClick(scenario)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedIds = setOf(scenario.id)
                            }
                        },
                        onFavoriteClick = { onFavoriteClick(scenario.id, !scenario.isFavorite) }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        EchoConfirmDialog(
            title = "确认批量删除",
            text = "确定要删除选中的 ${selectedIds.size} 个世界观吗？此操作无法撤销。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                onDeleteMultiple(selectedIds.toList())
                isSelectionMode = false
                selectedIds = emptySet()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScenarioCard(
    scenario: RoleplayScenario,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onFavoriteClick: () -> Unit
) {
    EchoGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
        shape = EchoTokens.Radius.shapeLg,
        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Unspecified
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scenario.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (scenario.worldview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "世界观：${scenario.worldview}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (scenario.location.isNotBlank() || scenario.time.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = listOf(scenario.time, scenario.location).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (!isSelectionMode) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        if (scenario.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (scenario.isFavorite) "取消收藏" else "收藏",
                        tint = if (scenario.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ScenarioCard(
    scenario: RoleplayScenario,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    EchoGlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = EchoTokens.Radius.shapeLg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scenario.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (scenario.worldview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = scenario.worldview.take(70) + if (scenario.worldview.length > 70) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!scenario.tags.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "标签：${scenario.tags}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    if (scenario.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (scenario.isFavorite) "取消收藏" else "收藏",
                    tint = if (scenario.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StudioEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        EchoPrimaryButton(
            onClick = onAction,
            shape = EchoTokens.Radius.shapeMd
        ) {
            Text(actionText)
        }
        if (secondaryActionText != null && onSecondaryAction != null) {
            Spacer(modifier = Modifier.height(6.dp))
            EchoGlassButton(
                onClick = onSecondaryAction,
                shape = EchoTokens.Radius.shapeMd
            ) {
                Text(secondaryActionText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartAnalyzeStudioDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    repository: com.aiassistant.data.repository.AiRepository,
    onDismiss: () -> Unit,
    onAnalyzed: (AnalyzedRoleplayBundle) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var analyzeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val apiConfigs by repository.getAllApiConfigs().collectAsState(initial = emptyList())
    val visibleOptions = remember { mutableStateOf<List<com.aiassistant.domain.model.ChatModelOption>>(emptyList()) }
    LaunchedEffect(Unit) {
        visibleOptions.value = repository.getAllVisibleChatModelOptions()
    }
    var selectedConfig by remember { mutableStateOf<com.aiassistant.domain.model.ApiConfig?>(null) }
    var selectedModelName by remember(selectedConfig) {
        mutableStateOf(selectedConfig?.modelName.orEmpty())
    }
    var configMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(apiConfigs) {
        if (selectedConfig == null && apiConfigs.isNotEmpty()) {
            val cfg = apiConfigs.firstOrNull { it.isDefault } ?: apiConfigs.firstOrNull()
            selectedConfig = cfg
            selectedModelName = cfg?.modelName.orEmpty()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // v2.7.3 流畅度：文件读取迁 IO 协程，回主线程更新状态
            scope.launch {
                val content = withContext(Dispatchers.IO) { RoleplaySmartParser.readTextFromUri(context, it) }
                if (content.isNotBlank()) {
                    inputText = content
                    Toast.makeText(context, "已读取文件，共 ${content.length} 字", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "文件读取为空或编码不支持", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = {
            if (isAnalyzing) {
                analyzeJob?.cancel()
                isAnalyzing = false
            }
            onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 智能识别与深度拆解")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "支持粘贴人设设定、大纲、小说章节或长篇文本，系统将调用指定的大模型深度拆解主角/配角设定、世界观、现场环境、文风与剧情目标。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // API 模型选择
                if (apiConfigs.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { if (!isAnalyzing) configMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedConfig?.let { "${it.name} (${com.aiassistant.domain.model.ModelDisplayName.format(selectedModelName.ifBlank { it.modelName })})" } ?: "选择分析模型",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        EchoGlassDropdownMenu(
                            expanded = configMenuExpanded,
                            onDismissRequest = { configMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            apiConfigs.forEach { cfg ->
                                val modelsForCfg = visibleOptions.value.filter { it.apiConfigId == cfg.id }.map { it.modelName }.ifEmpty { listOf(cfg.modelName) }
                                modelsForCfg.distinct().forEach { mName ->
                                    DropdownMenuItem(
                                        text = { Text("${cfg.name} · $mName") },
                                        onClick = {
                                            selectedConfig = cfg
                                            selectedModelName = mName
                                            configMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (selectedConfig?.id == cfg.id && selectedModelName == mName) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { filePicker.launch("text/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAnalyzing
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("从 TXT 文件导入 (支持 UTF-8 / GBK)")
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    label = { Text("粘贴小说章节 / 设定文本") },
                    placeholder = { Text("在此粘贴故事正文、人设卡或世界观设定...") },
                    enabled = !isAnalyzing
                )

                if (isAnalyzing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = progressStatus.ifBlank { "正在深度拆解中..." },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = {
                                analyzeJob?.cancel()
                                isAnalyzing = false
                                Toast.makeText(context, "已终止解析", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("停止", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            EchoPrimaryButton(
                onClick = {
                    analyzeJob = scope.launch {
                        isAnalyzing = true
                        progressStatus = "正在初始化分析引擎..."
                        try {
                            val bundle = RoleplaySmartAnalyzer.analyzeTextOrNovel(
                                context = context,
                                rawText = inputText,
                                repository = repository,
                                preferredConfig = selectedConfig,
                                selectedModel = selectedModelName,
                                onProgress = { progressStatus = it }
                            )
                            onAnalyzed(bundle)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            Toast.makeText(context, "解析已取消", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "分析异常: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isAnalyzing = false
                        }
                    }
                },
                enabled = inputText.isNotBlank() && !isAnalyzing
            ) {
                Text(if (isAnalyzing) "解析中..." else "开始智能拆解")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isAnalyzing) {
                        analyzeJob?.cancel()
                        isAnalyzing = false
                    }
                    onDismiss()
                }
            ) {
                Text(if (isAnalyzing) "终止" else "取消")
            }
        }
    )
}

@Composable
private fun SmartAnalyzeResultDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    bundle: AnalyzedRoleplayBundle,
    existingCharacters: List<CharacterProfile>,
    onDismiss: () -> Unit,
    onSave: (List<CharacterProfile>, RoleplayScenario?, Map<String, ConflictAction>, NarrativeMode, Boolean) -> Unit
) {
    var resultTab by remember { mutableIntStateOf(0) }
    var selectedCharacterIndices by remember {
        mutableStateOf(bundle.characters.indices.toSet())
    }
    var includeScenario by remember { mutableStateOf(bundle.scenario != null) }
    var selectedNarrativeMode by remember { mutableStateOf(NarrativeMode.CHARACTER) }

    val resolutionMap = remember {
        mutableStateMapOf<String, ConflictAction>().apply {
            bundle.characters.forEach { char ->
                val hasDuplicate = existingCharacters.any { it.name.trim() == char.name.trim() }
                if (hasDuplicate) {
                    put(char.name.trim(), ConflictAction.MERGE)
                }
            }
        }
    }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("智能拆解结果与入库确认")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${if (bundle.isAiAnalyzed) "✦ AI 深度分析完成" else "✦ 本地规则识别完成"}：${bundle.summaryReport}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                TabRow(
                    selectedTabIndex = resultTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = resultTab == 0,
                        onClick = { resultTab = 0 },
                        text = { Text("提取角色 (${bundle.characters.size})") }
                    )
                    Tab(
                        selected = resultTab == 1,
                        onClick = { resultTab = 1 },
                        text = { Text("世界观 (${if (bundle.scenario != null) 1 else 0})") }
                    )
                    Tab(
                        selected = resultTab == 2,
                        onClick = { resultTab = 2 },
                        text = { Text("故事模式") }
                    )
                }

                Box(modifier = Modifier.heightIn(min = 180.dp, max = 300.dp)) {
                    when (resultTab) {
                        0 -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(bundle.characters.size) { index ->
                                    val char = bundle.characters[index]
                                    val isSelected = index in selectedCharacterIndices
                                    val isDuplicate = existingCharacters.any { it.name.trim() == char.name.trim() }

                                    EchoGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        selectedCharacterIndices = if (checked) {
                                                            selectedCharacterIndices + index
                                                        } else {
                                                            selectedCharacterIndices - index
                                                        }
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(char.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                        if (isDuplicate) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            SuggestionChip(
                                                                onClick = {},
                                                                label = { Text("已存在同名角色", style = MaterialTheme.typography.labelSmall) },
                                                                modifier = Modifier.height(22.dp)
                                                            )
                                                        }
                                                    }
                                                    if (char.identity.isNotBlank()) {
                                                        Text("身份: ${char.identity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    if (char.personality.isNotBlank()) {
                                                        Text("性格: ${char.personality}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                                    }
                                                }
                                            }

                                            if (isDuplicate && isSelected) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "检测到已有同名角色，请选择入库策略：",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                FlowRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    ConflictAction.values().forEach { action ->
                                                        val currentAction = resolutionMap[char.name.trim()] ?: ConflictAction.MERGE
                                                        val isActionSelected = currentAction == action
                                                        FilterChip(
                                                            selected = isActionSelected,
                                                            onClick = { resolutionMap[char.name.trim()] = action },
                                                            label = { Text(action.displayName, style = MaterialTheme.typography.labelSmall) },
                                                            modifier = Modifier.defaultMinSize(minHeight = 28.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            bundle.scenario?.let { sc ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = includeScenario,
                                            onCheckedChange = { includeScenario = it }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(sc.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                    if (sc.worldview.isNotBlank()) {
                                        Text("世界观: ${sc.worldview}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (sc.conflict.isNotBlank()) {
                                        Text("核心冲突: ${sc.conflict}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (sc.atmosphere.isNotBlank()) {
                                        Text("氛围: ${sc.atmosphere}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } ?: run {
                                Text("未提取到独立世界观，可从角色直接开启创作。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        2 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("选择故事对话的叙事模式：", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                NarrativeMode.values().forEach { mode ->
                                    val isModeSelected = selectedNarrativeMode == mode
                                    EchoGlassCard(
                                        onClick = { selectedNarrativeMode = mode },
                                        modifier = Modifier.fillMaxWidth(),
                                        containerColor = if (isModeSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Unspecified
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isModeSelected,
                                                onClick = { selectedNarrativeMode = mode }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(mode.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    text = mode.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
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
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EchoGlassButton(
                    onClick = {
                        val charsToSave = bundle.characters.filterIndexed { index, _ -> index in selectedCharacterIndices }
                        val scenarioToSave = if (includeScenario) bundle.scenario else null
                        onSave(charsToSave, scenarioToSave, resolutionMap.toMap(), selectedNarrativeMode, false)
                    }
                ) {
                    Text("仅保存入库")
                }
                EchoPrimaryButton(
                    onClick = {
                        val charsToSave = bundle.characters.filterIndexed { index, _ -> index in selectedCharacterIndices }
                        val scenarioToSave = if (includeScenario) bundle.scenario else null
                        onSave(charsToSave, scenarioToSave, resolutionMap.toMap(), selectedNarrativeMode, true)
                    }
                ) {
                    Text("保存并开启故事")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("放弃")
            }
        }
    )
}

@Composable
private fun EditStorySessionContextDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    session: RoleplaySession,
    allCharacters: List<CharacterProfile>,
    allScenarios: List<RoleplayScenario>,
    onDismiss: () -> Unit,
    onSave: (List<Long>, Long?, NarrativeMode, String) -> Unit
) {
    val initialIds = remember(session) { session.getEffectiveCharacterIds().toSet() }
    var selectedCharacterIds by remember { mutableStateOf(initialIds) }
    var selectedScenarioId by remember { mutableStateOf(session.scenarioId) }
    var selectedNarrativeMode by remember {
        mutableStateOf(NarrativeMode.fromValue(session.narrativeMode))
    }
    var plotSummary by remember { mutableStateOf(session.currentPlotSummary) }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("修改故事设定与登场角色")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 登场角色多选
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("登场角色 (${selectedCharacterIds.size} 位)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("勾选或取消勾选参与本故事的角色：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (allCharacters.isEmpty()) {
                        Text("暂无可选角色", style = MaterialTheme.typography.bodySmall)
                    } else {
                        allCharacters.forEach { char ->
                            val isChecked = char.id in selectedCharacterIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCharacterIds = if (isChecked) selectedCharacterIds - char.id else selectedCharacterIds + char.id
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedCharacterIds = if (isChecked) selectedCharacterIds - char.id else selectedCharacterIds + char.id
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(char.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    if (char.identity.isNotBlank()) {
                                        Text(char.identity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 世界观单选
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("绑定世界观", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScenarioId = null }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedScenarioId == null,
                            onClick = { selectedScenarioId = null }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("不指定世界观 (自由背景)", style = MaterialTheme.typography.bodyMedium)
                    }
                    allScenarios.forEach { sc ->
                        val isSelected = selectedScenarioId == sc.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedScenarioId = sc.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedScenarioId = sc.id }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(sc.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                if (sc.worldview.isNotBlank()) {
                                    Text(sc.worldview.take(30) + "...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 叙事模式
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("叙事模式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    NarrativeMode.values().forEach { mode ->
                        val isModeSelected = selectedNarrativeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedNarrativeMode = mode }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isModeSelected,
                                onClick = { selectedNarrativeMode = mode }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(mode.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 剧情摘要
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("当前剧情摘要 / 备忘", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = plotSummary,
                        onValueChange = { plotSummary = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("记录当前故事发展进度、重要转折点...") },
                        maxLines = 4
                    )
                }
            }
        },
        confirmButton = {
            EchoPrimaryButton(
                onClick = {
                    onSave(
                        selectedCharacterIds.toList(),
                        selectedScenarioId,
                        selectedNarrativeMode,
                        plotSummary
                    )
                }
            ) {
                Text("保存修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
