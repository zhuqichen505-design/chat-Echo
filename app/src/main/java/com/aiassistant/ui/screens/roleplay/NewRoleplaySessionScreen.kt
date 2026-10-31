@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aiassistant.ui.screens.roleplay

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
import androidx.compose.ui.unit.dp
import com.aiassistant.AiAssistantApp
import com.aiassistant.domain.model.*
import com.aiassistant.ui.screens.chat.ChatSettingsModelSelector

/** 创作不要求先完成角色卡：名称、写作方式、模型即可开始，其余资料按需绑定。 */
@Composable
fun NewRoleplaySessionScreen(
    viewModel: RoleplayViewModel,
    apiConfigs: List<ApiConfig>,
    onStartSession: (List<Long>, Long?, Long, String?, NarrativeMode, String, String) -> Unit,
    onBack: () -> Unit
) {
    val characters by viewModel.allCharacters.collectAsState()
    val scenarios by viewModel.allScenarios.collectAsState()
    val state by viewModel.uiState.collectAsState()
    var models by remember { mutableStateOf<List<ChatModelOption>>(emptyList()) }
    var model by remember { mutableStateOf<ChatModelOption?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var modeValue by rememberSaveable { mutableStateOf(NarrativeMode.AUTHOR.value) }
    var selectedCharacters by remember { mutableStateOf(emptySet<Long>()) }
    var scenarioId by remember { mutableStateOf<Long?>(null) }
    var showResources by rememberSaveable { mutableStateOf(false) }
    var resourceQuery by rememberSaveable { mutableStateOf("") }
    val saving = state is RoleplayUiState.Saving
    LaunchedEffect(apiConfigs) {
        models = AiAssistantApp.instance.repository.getAllVisibleChatModelOptions()
        if (model == null || models.none { it.apiConfigId == model?.apiConfigId && it.modelName == model?.modelName }) {
            model = models.firstOrNull { option -> apiConfigs.any { it.id == option.apiConfigId && it.isDefault } } ?: models.firstOrNull()
        }
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { TopAppBar(title = { Text("创建作品", style = MaterialTheme.typography.titleMedium) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Button(enabled = model != null && !saving, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    onClick = { model?.let { onStartSession(selectedCharacters.toList(), scenarioId, it.apiConfigId, it.modelName, NarrativeMode.fromValue(modeValue), name.trim(), instructions.trim()) } }) {
                    Text(if (saving) "正在创建…" else "创建并打开工作台")
                }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("先写起来，资料随时补充", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("作品独立保存人物副本、会话设定、时间线与世界书选择。无需先填完人物卡。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("作品名称（可稍后修改）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("写作方式", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(NarrativeMode.AUTHOR to "小说写作", NarrativeMode.CHARACTER to "角色互动", NarrativeMode.MULTI to "群像", NarrativeMode.NARRATOR to "旁白").forEach { (mode, label) ->
                    FilterChip(selected = modeValue == mode.value, onClick = { modeValue = mode.value }, label = { Text(label) })
                }
            }
            Text(NarrativeMode.fromValue(modeValue).description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text("写作目标与要求（可选）") },
                placeholder = { Text("描述本故事的视角、文风、角色边界或第一章目标。") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            ChatSettingsModelSelector(currentOption = model, fallbackModel = "选择可用模型", availableOptions = models,
                onModelSelected = { model = it }, contentColor = MaterialTheme.colorScheme.onSurface, secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant)
            if (models.isEmpty()) Text("尚无可用模型，请先在设置中添加并启用 API 配置。", color = MaterialTheme.colorScheme.error)
            HorizontalDivider()
            TextButton(onClick = { showResources = !showResources }) { Icon(if (showResources) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null); Text("使用已有资料 · ${selectedCharacters.size} 位人物${if (scenarioId != null) " · 已选世界" else ""}") }
            if (showResources) {
                OutlinedTextField(value = resourceQuery, onValueChange = { resourceQuery = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("筛选人物或世界") })
                Text("人物", style = MaterialTheme.typography.titleSmall)
                if (characters.isEmpty()) Text("暂无人物资料，创建作品后可直接添加。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                characters.filter { it.name.contains(resourceQuery, true) }.forEach { character ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = character.id in selectedCharacters, onCheckedChange = { checked -> selectedCharacters = if (checked) selectedCharacters + character.id else selectedCharacters - character.id })
                        Text(character.name, Modifier.weight(1f))
                    }
                }
                Text("世界", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = scenarioId == null, onClick = { scenarioId = null }); Text("自由世界") }
                scenarios.filter { it.name.contains(resourceQuery, true) }.forEach { scenario ->
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = scenarioId == scenario.id, onClick = { scenarioId = scenario.id }); Text(scenario.name, Modifier.weight(1f)) }
                }
            }
            (state as? RoleplayUiState.Error)?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        }
    }
}
