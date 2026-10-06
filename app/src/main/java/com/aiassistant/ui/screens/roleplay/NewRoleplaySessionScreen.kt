package com.aiassistant.ui.screens.roleplay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.components.EchoSettingRow
import com.aiassistant.domain.model.*
import com.aiassistant.ui.components.*
import com.aiassistant.ui.theme.EchoTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRoleplaySessionScreen(
    viewModel: RoleplayViewModel,
    apiConfigs: List<ApiConfig>,
    onStartSession: (List<Long>, Long?, Long, String?, NarrativeMode) -> Unit,
    onBack: () -> Unit
) {
    val hazeState = rememberEchoHazeState()
    val repository = remember { com.aiassistant.AiAssistantApp.instance.repository }
    val characters by viewModel.characters.collectAsState()
    val scenarios by viewModel.scenarios.collectAsState()
    var visibleModelOptions by remember { mutableStateOf<List<ChatModelOption>>(emptyList()) }
    LaunchedEffect(apiConfigs) {
        visibleModelOptions = repository.getAllVisibleChatModelOptions()
    }

    var selectedCharacterIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedScenario by remember { mutableStateOf<RoleplayScenario?>(null) }
    var selectedModelOption by remember { mutableStateOf<ChatModelOption?>(null) }
    var selectedNarrativeMode by remember { mutableStateOf(NarrativeMode.CHARACTER) }
    var showCharacterPicker by remember { mutableStateOf(false) }
    var showScenarioPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(visibleModelOptions, apiConfigs) {
        if (selectedModelOption == null && visibleModelOptions.isNotEmpty()) {
            selectedModelOption = visibleModelOptions.firstOrNull { opt ->
                apiConfigs.find { it.id == opt.apiConfigId }?.isDefault == true
            } ?: visibleModelOptions.firstOrNull()
        }
    }

    val selectedCharacters = remember(selectedCharacterIds, characters) {
        characters.filter { it.id in selectedCharacterIds }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("新建故事创作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("多角色登场 · 世界观设定 · 模式定制", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(EchoTokens.Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 登场角色选择
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "登场角色设定 (${selectedCharacterIds.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "可单选主角或多选群像角色参与演绎",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (selectedCharacterIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { selectedCharacterIds = emptySet() }) {
                            Text("清空选择", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                EchoGlassCard(
                    onClick = { showCharacterPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = EchoTokens.Radius.shapeLg
                ) {
                    EchoSettingRow(
                        title = if (selectedCharacters.isNotEmpty()) selectedCharacters.joinToString("、") { it.name }
                                else "选择登场角色（支持单选或多选群像）",
                        subtitle = if (selectedCharacters.isNotEmpty()) "已选 ${selectedCharacters.size} 位角色参与故事演绎" else null,
                        trailing = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                }
            }

            // 世界观选择
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "世界观与背景设定",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "注入宏观时代规则与现场氛围",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                EchoGlassCard(
                    onClick = { showScenarioPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = EchoTokens.Radius.shapeLg
                ) {
                    EchoSettingRow(
                        title = if (selectedScenario != null) selectedScenario!!.name
                                else "选择世界观（可选，注入时空背景与规则）",
                        subtitle = if (selectedScenario != null && selectedScenario!!.worldview.isNotBlank())
                            selectedScenario!!.worldview.take(60) + if (selectedScenario!!.worldview.length > 60) "..." else "" else null,
                        trailing = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                }
            }

            // API与模型配置 (与对话中可选模型列表保持一致)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "生成模型与 API 服务",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "与主对话可选模型池完全一致",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                EchoGlassCard(
                    onClick = { showModelPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = EchoTokens.Radius.shapeLg
                ) {
                    EchoSettingRow(
                        title = if (selectedModelOption != null) selectedModelOption!!.modelName else "选择生成模型",
                        subtitle = if (selectedModelOption != null) "${selectedModelOption!!.configName} · ${selectedModelOption!!.provider}" else null,
                        trailing = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                }
            }

            // 叙事模式
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "故事叙事模式",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "决定 AI 回应的角色定位与文风",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                NarrativeMode.values().forEach { mode ->
                    val isModeSelected = selectedNarrativeMode == mode
                    EchoGlassCard(
                        onClick = { selectedNarrativeMode = mode },
                        modifier = Modifier.fillMaxWidth(),
                        shape = EchoTokens.Radius.shapeMd,
                        containerColor = if (isModeSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Unspecified
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isModeSelected,
                                onClick = { selectedNarrativeMode = mode }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
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

            Spacer(modifier = Modifier.height(12.dp))

            EchoPrimaryButton(
                onClick = {
                    val configId = selectedModelOption?.apiConfigId ?: (apiConfigs.firstOrNull()?.id ?: 0L)
                    val modelName = selectedModelOption?.modelName
                    onStartSession(
                        selectedCharacterIds.toList(),
                        selectedScenario?.id,
                        configId,
                        modelName,
                        selectedNarrativeMode
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                enabled = selectedModelOption != null || apiConfigs.isNotEmpty()
            ) {
                Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("开启故事创作", style = MaterialTheme.typography.titleSmall)
            }
        }
    }

    // 角色多选弹窗
    if (showCharacterPicker) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showCharacterPicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择登场角色 (可多选)")
                }
            },
            text = {
                if (characters.isEmpty()) {
                    Text("暂无可用角色卡，请先在角色列表中创建。", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Box(modifier = Modifier.heightIn(max = 300.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(characters) { character ->
                                val isChecked = character.id in selectedCharacterIds
                                EchoGlassCard(
                                    onClick = {
                                        selectedCharacterIds = if (isChecked) selectedCharacterIds - character.id else selectedCharacterIds + character.id
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Unspecified
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedCharacterIds = if (checked) selectedCharacterIds + character.id else selectedCharacterIds - character.id
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(character.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                            if (character.identity.isNotBlank()) {
                                                Text("身份: ${character.identity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
                EchoPrimaryButton(onClick = { showCharacterPicker = false }) {
                    Text("确定 (${selectedCharacterIds.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCharacterPicker = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // 世界观单选弹窗
    if (showScenarioPicker) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showScenarioPicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Landscape, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择世界观背景")
                }
            },
            text = {
                Box(modifier = Modifier.heightIn(max = 300.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            EchoGlassCard(
                                onClick = {
                                    selectedScenario = null
                                    showScenarioPicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = if (selectedScenario == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Unspecified
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedScenario == null,
                                        onClick = {
                                            selectedScenario = null
                                            showScenarioPicker = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("不指定世界观 (自由发挥)", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        items(scenarios) { scenario ->
                            val isSelected = selectedScenario?.id == scenario.id
                            EchoGlassCard(
                                onClick = {
                                    selectedScenario = scenario
                                    showScenarioPicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Unspecified
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedScenario = scenario
                                            showScenarioPicker = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(scenario.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        if (scenario.worldview.isNotBlank()) {
                                            Text("世界观: ${scenario.worldview.take(40)}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                EchoPrimaryButton(onClick = { showScenarioPicker = false }) {
                    Text("完成")
                }
            }
        )
    }

    // 生成模型选择弹窗 (支持所有配置与全量模型)
    if (showModelPicker) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredOptions = remember(searchQuery, visibleModelOptions) {
            if (searchQuery.isBlank()) visibleModelOptions
            else visibleModelOptions.filter {
                it.modelName.contains(searchQuery, ignoreCase = true) ||
                it.configName.contains(searchQuery, ignoreCase = true) ||
                it.provider.contains(searchQuery, ignoreCase = true)
            }
        }

        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showModelPicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择生成模型")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索模型 / 渠道...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )

                    Box(modifier = Modifier.heightIn(max = 280.dp)) {
                        if (filteredOptions.isEmpty()) {
                            Text(
                                text = "暂无匹配模型，请在设置中添加或启用 API 配置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(filteredOptions) { option ->
                                    val isSelected = selectedModelOption?.apiConfigId == option.apiConfigId &&
                                        selectedModelOption?.modelName == option.modelName
                                    EchoGlassCard(
                                        onClick = {
                                            selectedModelOption = option
                                            showModelPicker = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Unspecified
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    selectedModelOption = option
                                                    showModelPicker = false
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(com.aiassistant.domain.model.ModelDisplayName.format(option.modelName), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                                Text("${option.configName} · ${option.provider}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
                EchoPrimaryButton(onClick = { showModelPicker = false }) {
                    Text("完成")
                }
            }
        )
    }

}
