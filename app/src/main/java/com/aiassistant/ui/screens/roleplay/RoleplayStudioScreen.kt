@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aiassistant.ui.screens.roleplay

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiassistant.AiAssistantApp
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.NarrativeMode
import com.aiassistant.ui.components.EchoConfirmDialog
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.utils.AnalyzedRoleplayBundle

/** 作品为主入口，人物/世界观为可复用资料；每部作品直接进入自己的写作与资料工作区。 */
@Composable
fun RoleplayStudioScreen(
    viewModel: RoleplayViewModel,
    onNavigateToCharacters: () -> Unit,
    onNavigateToScenarios: () -> Unit,
    onNavigateToCharacterEditor: (CharacterProfile?) -> Unit,
    onNavigateToScenarioEditor: (RoleplayScenario?) -> Unit,
    onNavigateToSession: (Long) -> Unit,
    onCreateNewSession: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    onNavigateToWorkspace: (Long, String) -> Unit = { id, _ -> onNavigateToSession(id) }
) {
    val context = LocalContext.current
    val characters by viewModel.allCharacters.collectAsState()
    val scenarios by viewModel.allScenarios.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val hazeState = rememberEchoHazeState()
    val currentTab = viewModel.studioTab.coerceIn(0, 2)
    var query by rememberSaveable(currentTab) { mutableStateOf("") }
    var onlyPinned by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var deleteSelected by remember { mutableStateOf(false) }
    var showAnalyze by remember { mutableStateOf(false) }
    var analyzed by remember { mutableStateOf<AnalyzedRoleplayBundle?>(null) }
    var libraryKind by rememberSaveable { mutableStateOf(0) }
    var storyMenu by remember { mutableStateOf<StudioStory?>(null) }
    var rename by remember { mutableStateOf<StudioStory?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var characterToDelete by remember { mutableStateOf<CharacterProfile?>(null) }
    var scenarioToDelete by remember { mutableStateOf<RoleplayScenario?>(null) }
    val stories = remember(sessions, conversations, characters, scenarios, query, onlyPinned, mode) {
        buildStudioStories(sessions, conversations, characters, scenarios, if (currentTab == 0) query else "", onlyPinned, mode)
    }
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        Column {
            TopAppBar(title = { Text("创作", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = { if (showBackButton) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { showAnalyze = true }) { Icon(Icons.Default.FileUpload, "导入素材") }
                    FilledTonalButton(onClick = onCreateNewSession, modifier = Modifier.padding(end = 12.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("新作品") }
                })
            ScrollableTabRow(selectedTabIndex = currentTab, edgePadding = 16.dp, containerColor = MaterialTheme.colorScheme.background) {
                listOf("作品", "人物与世界", "世界书").forEachIndexed { index, name ->
                    Tab(selected = currentTab == index, onClick = { viewModel.studioTab = index; selected = emptySet() }, text = { Text(name) })
                }
            }
        }
    }) { padding ->
        if (currentTab == 2) {
            StudioLoreLibrary(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text(if (currentTab == 0) "搜索作品、人物、世界或剧情" else "搜索资料名称与内容") },
                        trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清空搜索") } })
                }
                if (currentTab == 0) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = onlyPinned, onClick = { onlyPinned = !onlyPinned }, label = { Text("置顶") })
                            FilterChip(selected = mode == null, onClick = { mode = null }, label = { Text("全部模式") })
                            FilterChip(selected = mode == NarrativeMode.CHARACTER.value, onClick = { mode = NarrativeMode.CHARACTER.value }, label = { Text("角色互动") })
                            FilterChip(selected = mode == NarrativeMode.AUTHOR.value, onClick = { mode = NarrativeMode.AUTHOR.value }, label = { Text("小说写作") })
                        }
                    }
                    if (selected.isNotEmpty()) item {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("已选 ${selected.size}", Modifier.weight(1f))
                                IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, "退出多选") }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { selected = stories.map { it.conversation.id }.toSet() }) { Text("全选") }
                                TextButton(onClick = { viewModel.setStoryPinned(selected, true); selected = emptySet() }) { Text("置顶") }
                                TextButton(onClick = { viewModel.hideStories(selected); selected = emptySet() }) { Text("隐藏") }
                                TextButton(onClick = { deleteSelected = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    if (stories.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (query.isBlank() && !onlyPinned && mode == null) "从一部作品开始" else "没有匹配的作品", style = MaterialTheme.typography.titleLarge)
                            Text("直接写作，或先准备人物与世界。每部作品都有独立的会话设定、时间线和世界书绑定。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onCreateNewSession) { Text("创建作品") }
                        }
                    }
                    items(stories, key = { it.conversation.id }) { story ->
                        val id = story.conversation.id
                        Surface(color = if (id in selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (selected.isNotEmpty()) Checkbox(checked = id in selected, onCheckedChange = { checked -> selected = if (checked) selected + id else selected - id })
                                    Column(Modifier.weight(1f).clickable { onNavigateToSession(id) }) {
                                        Text((if (story.conversation.isPinned) "置顶 · " else "") + story.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(listOf(story.characterNames, story.scenarioName).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "自由写作 · 未绑定人物或世界" },
                                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { storyMenu = story }) { Icon(Icons.Default.MoreVert, "管理 ${story.title}") }
                                }
                                Text(story.session.currentPlotSummary.ifBlank { "尚未保存剧情摘要，进入工作台可补充写作目标与设定。" },
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${NarrativeMode.fromValue(story.session.narrativeMode).displayName} · ${story.conversation.messageCount} 条消息", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                    FilledTonalButton(onClick = { onNavigateToSession(id) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("继续写作") }
                                    TextButton(onClick = { onNavigateToWorkspace(id, "writing") }) { Text("工作台") }
                                    TextButton(onClick = { onNavigateToWorkspace(id, "facts") }) { Text("会话设定") }
                                    TextButton(onClick = { onNavigateToWorkspace(id, "timeline") }) { Text("时间线") }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = libraryKind == 0, onClick = { libraryKind = 0 }, label = { Text("人物 ${characters.size}") })
                            FilterChip(selected = libraryKind == 1, onClick = { libraryKind = 1 }, label = { Text("世界 ${scenarios.size}") })
                            TextButton(onClick = { if (libraryKind == 0) onNavigateToCharacterEditor(null) else onNavigateToScenarioEditor(null) }) { Text("新建") }
                        }
                    }
                    item { Text("资料库可用于多部作品。作品内编辑会生成专属副本，避免把剧情变化写回其他故事。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (libraryKind == 0) {
                        val filtered = characters.filter { listOf(it.name, it.identity, it.personality, it.background, it.tags.orEmpty()).any { value -> value.contains(query, true) } }
                        if (filtered.isEmpty()) item { Text("没有匹配的人物，可以新建或导入素材。", modifier = Modifier.padding(vertical = 20.dp)) }
                        items(filtered, key = { it.id }) { character ->
                            LibraryResourceRow(character.name, character.identity.ifBlank { character.personality }, character.isFavorite,
                                onClick = { onNavigateToCharacterEditor(character) }, onFavorite = { viewModel.setCharacterFavorite(character.id, !character.isFavorite) },
                                onDelete = { characterToDelete = character })
                        }
                    } else {
                        val filtered = scenarios.filter { listOf(it.name, it.worldview, it.premise, it.tags.orEmpty()).any { value -> value.contains(query, true) } }
                        if (filtered.isEmpty()) item { Text("没有匹配的世界，可以新建或导入素材。", modifier = Modifier.padding(vertical = 20.dp)) }
                        items(filtered, key = { it.id }) { scenario ->
                            LibraryResourceRow(scenario.name, scenario.worldview.ifBlank { scenario.premise }, scenario.isFavorite,
                                onClick = { onNavigateToScenarioEditor(scenario) }, onFavorite = { viewModel.setScenarioFavorite(scenario.id, !scenario.isFavorite) },
                                onDelete = { scenarioToDelete = scenario })
                        }
                    }
                }
            }
        }
    }
    storyMenu?.let { story ->
        AlertDialog(onDismissRequest = { storyMenu = null }, title = { Text(story.title) }, text = {
            Column {
                StudioMenuRow("重命名") { rename = story; renameValue = story.title; storyMenu = null }
                StudioMenuRow(if (story.conversation.isPinned) "取消置顶" else "置顶") { viewModel.setStoryPinned(listOf(story.conversation.id), !story.conversation.isPinned); storyMenu = null }
                StudioMenuRow("复制作品（含消息与设定）") { viewModel.duplicateStory(story.conversation.id, onNavigateToSession); storyMenu = null }
                StudioMenuRow("批量管理") { selected = setOf(story.conversation.id); storyMenu = null }
                StudioMenuRow("隐藏作品") { viewModel.hideStories(listOf(story.conversation.id)); storyMenu = null }
                StudioMenuRow("删除作品") { selected = setOf(story.conversation.id); deleteSelected = true; storyMenu = null }
            }
        }, confirmButton = { TextButton(onClick = { storyMenu = null }) { Text("关闭") } })
    }
    if (rename != null) AlertDialog(onDismissRequest = { rename = null }, title = { Text("作品名称") },
        text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = renameValue.isNotBlank(), onClick = { rename?.let { viewModel.renameStory(it.conversation.id, renameValue) }; rename = null }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { rename = null }) { Text("取消") } })
    if (deleteSelected) EchoConfirmDialog(title = "删除 ${selected.size} 部作品？", text = "消息、会话设定与时间线将一起删除，无法撤销。资料库人物和世界仍然保留。", confirmText = "删除", isDestructive = true,
        onConfirm = { viewModel.deleteStories(selected); selected = emptySet(); deleteSelected = false }, onDismiss = { deleteSelected = false })
    characterToDelete?.let { character -> EchoConfirmDialog(title = "删除人物资料？", text = "删除“${character.name}”的资料库记录。已经保存的故事专属副本仍会保留。", confirmText = "删除", isDestructive = true,
        onConfirm = { viewModel.deleteCharacter(character); characterToDelete = null }, onDismiss = { characterToDelete = null }) }
    scenarioToDelete?.let { scenario -> EchoConfirmDialog(title = "删除世界资料？", text = "删除“${scenario.name}”的资料库记录。已经保存的故事专属副本仍会保留。", confirmText = "删除", isDestructive = true,
        onConfirm = { viewModel.deleteScenario(scenario); scenarioToDelete = null }, onDismiss = { scenarioToDelete = null }) }
    if (showAnalyze) SmartAnalyzeStudioDialog(hazeState = hazeState, repository = AiAssistantApp.instance.repository,
        onDismiss = { showAnalyze = false }, onAnalyzed = { analyzed = it; showAnalyze = false })
    analyzed?.let { bundle -> SmartAnalyzeResultDialog(hazeState = hazeState, bundle = bundle, existingCharacters = characters,
        onDismiss = { analyzed = null }, onSave = { chars, world, resolution, narrative, start ->
            analyzed = null
            viewModel.importAnalyzedBundleWithConflictResolution(chars, world, resolution, narrative, start,
                onSessionCreated = { if (it > 0) onNavigateToWorkspace(it, "writing") })
        }) }
    LaunchedEffect(uiState) {
        val text = when (val state = uiState) { is RoleplayUiState.SaveSuccess -> state.message; is RoleplayUiState.Error -> state.message; else -> null }
        text?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearUiState() }
    }
}

@Composable
private fun StudioMenuRow(text: String, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun LibraryResourceRow(name: String, detail: String, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, onDelete: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 8.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(detail.ifBlank { "点击完善资料" }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, "收藏", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "删除 $name", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        HorizontalDivider()
    }
}
