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
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
internal fun SmartAnalyzeStudioDialog(
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
internal fun SmartAnalyzeResultDialog(
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
