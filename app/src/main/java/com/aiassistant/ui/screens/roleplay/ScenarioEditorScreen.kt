package com.aiassistant.ui.screens.roleplay

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.ui.components.EchoConfirmDialog
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.utils.RoleplaySmartParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioEditorScreen(
    scenario: RoleplayScenario? = null,
    onSave: (RoleplayScenario) -> Unit,
    onDelete: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val draftKey = "scenario:${scenario?.id ?: "new"}"
    retainEditorDraft(draftKey)
    var name by editorField(draftKey, "name", scenario?.name ?: "")
    var worldview by editorField(draftKey, "worldview", scenario?.worldview ?: "")
    var time by editorField(draftKey, "time", scenario?.time ?: "")
    var location by editorField(draftKey, "location", scenario?.location ?: "")
    var environment by editorField(draftKey, "environment", scenario?.environment ?: "")
    var premise by editorField(draftKey, "premise", scenario?.premise ?: "")
    var rules by editorField(draftKey, "rules", scenario?.rules ?: "")
    var relationshipState by editorField(draftKey, "relationshipState", scenario?.relationshipState ?: "")
    var conflict by editorField(draftKey, "conflict", scenario?.conflict ?: "")
    var plotGoal by editorField(draftKey, "plotGoal", scenario?.plotGoal ?: "")
    var atmosphere by editorField(draftKey, "atmosphere", scenario?.atmosphere ?: "")
    var narrativePerspective by editorField(draftKey, "narrativePerspective", scenario?.narrativePerspective ?: "")
    var outputFormat by editorField(draftKey, "outputFormat", scenario?.outputFormat ?: "")
    var contentRestrictions by editorField(draftKey, "contentRestrictions", scenario?.contentRestrictions ?: "")
    var openingPrompt by editorField(draftKey, "openingPrompt", scenario?.openingPrompt ?: "")
    var tags by editorField(draftKey, "tags", scenario?.tags ?: "")
    var isFavorite by editorField(draftKey, "isFavorite", scenario?.isFavorite ?: false)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showSmartReadDialog by remember { mutableStateOf(false) }
    var currentSection by editorField(draftKey, "section", 0)

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scenario == null) "创建场景" else "编辑场景") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSmartReadDialog = true }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "一键读取",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showPreview = true }) {
                        Icon(Icons.Default.Preview, contentDescription = "预览")
                    }
                    if (scenario != null) {
                        IconButton(onClick = { isFavorite = !isFavorite }) {
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                                tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 0.dp,
                modifier = Modifier.imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                // 保存按钮（水平内边距由外层 Row 统一提供，不再叠加）
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val newScenario = RoleplayScenario(
                                id = scenario?.id ?: 0,
                                name = name,
                                worldview = worldview,
                                time = time,
                                location = location,
                                environment = environment,
                                premise = premise,
                                rules = rules,
                                relationshipState = relationshipState,
                                conflict = conflict,
                                plotGoal = plotGoal,
                                atmosphere = atmosphere,
                                narrativePerspective = narrativePerspective,
                                outputFormat = outputFormat,
                                contentRestrictions = contentRestrictions,
                                openingPrompt = openingPrompt,
                                tags = tags.ifBlank { null },
                                isFavorite = isFavorite,
                                createdAt = scenario?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(newScenario)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存场景")
                }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(scrollState)
        ) {
            // 顶部一键读取提示条
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "支持一键读取长文本或 TXT 场景设定，自动拆解填入各设定项",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showSmartReadDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("一键读取")
                    }
                }
            }

            // 分区选择器
            ScrollableTabRow(
                selectedTabIndex = currentSection,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp
            ) {
                Tab(
                    selected = currentSection == 0,
                    onClick = { currentSection = 0 },
                    text = { Text("基本设定") }
                )
                Tab(
                    selected = currentSection == 1,
                    onClick = { currentSection = 1 },
                    text = { Text("世界观") }
                )
                Tab(
                    selected = currentSection == 2,
                    onClick = { currentSection = 2 },
                    text = { Text("剧情设定") }
                )
                Tab(
                    selected = currentSection == 3,
                    onClick = { currentSection = 3 },
                    text = { Text("叙事控制") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 内容区域
            when (currentSection) {
                0 -> BasicSettingSection(
                    name = name,
                    onNameChange = { name = it },
                    time = time,
                    onTimeChange = { time = it },
                    location = location,
                    onLocationChange = { location = it },
                    environment = environment,
                    onEnvironmentChange = { environment = it },
                    tags = tags,
                    onTagsChange = { tags = it }
                )
                1 -> WorldBuildingSection(
                    worldview = worldview,
                    onWorldviewChange = { worldview = it },
                    rules = rules,
                    onRulesChange = { rules = it },
                    relationshipState = relationshipState,
                    onRelationshipStateChange = { relationshipState = it }
                )
                2 -> PlotSettingSection(
                    premise = premise,
                    onPremiseChange = { premise = it },
                    conflict = conflict,
                    onConflictChange = { conflict = it },
                    plotGoal = plotGoal,
                    onPlotGoalChange = { plotGoal = it },
                    openingPrompt = openingPrompt,
                    onOpeningPromptChange = { openingPrompt = it }
                )
                3 -> NarrativeControlSection(
                    atmosphere = atmosphere,
                    onAtmosphereChange = { atmosphere = it },
                    narrativePerspective = narrativePerspective,
                    onNarrativePerspectiveChange = { narrativePerspective = it },
                    outputFormat = outputFormat,
                    onOutputFormatChange = { outputFormat = it },
                    contentRestrictions = contentRestrictions,
                    onContentRestrictionsChange = { contentRestrictions = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

        }
    }

    // 一键读取对话框
    if (showSmartReadDialog) {
        SmartReadScenarioDialog(
            currentScenario = RoleplayScenario(
                id = scenario?.id ?: 0,
                name = name,
                worldview = worldview,
                time = time,
                location = location,
                environment = environment,
                premise = premise,
                rules = rules,
                relationshipState = relationshipState,
                conflict = conflict,
                plotGoal = plotGoal,
                atmosphere = atmosphere,
                narrativePerspective = narrativePerspective,
                outputFormat = outputFormat,
                contentRestrictions = contentRestrictions,
                openingPrompt = openingPrompt,
                tags = tags,
                isFavorite = isFavorite
            ),
            onDismiss = { showSmartReadDialog = false },
            onApply = { parsed ->
                if (parsed.name.isNotBlank()) name = parsed.name
                if (parsed.worldview.isNotBlank()) worldview = parsed.worldview
                if (parsed.time.isNotBlank()) time = parsed.time
                if (parsed.location.isNotBlank()) location = parsed.location
                if (parsed.environment.isNotBlank()) environment = parsed.environment
                if (parsed.premise.isNotBlank()) premise = parsed.premise
                if (parsed.rules.isNotBlank()) rules = parsed.rules
                if (parsed.relationshipState.isNotBlank()) relationshipState = parsed.relationshipState
                if (parsed.conflict.isNotBlank()) conflict = parsed.conflict
                if (parsed.plotGoal.isNotBlank()) plotGoal = parsed.plotGoal
                if (parsed.atmosphere.isNotBlank()) atmosphere = parsed.atmosphere
                if (parsed.narrativePerspective.isNotBlank()) narrativePerspective = parsed.narrativePerspective
                if (parsed.outputFormat.isNotBlank()) outputFormat = parsed.outputFormat
                if (parsed.contentRestrictions.isNotBlank()) contentRestrictions = parsed.contentRestrictions
                if (parsed.openingPrompt.isNotBlank()) openingPrompt = parsed.openingPrompt
                parsed.tags?.takeIf { it.isNotBlank() }?.let { tags = it }
                showSmartReadDialog = false
                Toast.makeText(context, "场景设定已自动识别并填充", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        EchoConfirmDialog(
            title = "确认删除",
            text = "确定要删除场景\"${name}\"吗？此操作不可恢复。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                onDelete?.invoke()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // 预览对话框
    if (showPreview) {
        ScenarioPreviewDialog(
            scenario = RoleplayScenario(
                id = scenario?.id ?: 0,
                name = name,
                worldview = worldview,
                time = time,
                location = location,
                environment = environment,
                premise = premise,
                rules = rules,
                relationshipState = relationshipState,
                conflict = conflict,
                plotGoal = plotGoal,
                atmosphere = atmosphere,
                narrativePerspective = narrativePerspective,
                outputFormat = outputFormat,
                contentRestrictions = contentRestrictions,
                openingPrompt = openingPrompt,
                tags = tags,
                isFavorite = isFavorite
            ),
            onDismiss = { showPreview = false }
        )
    }
}

@Composable
private fun SmartReadScenarioDialog(
    currentScenario: RoleplayScenario,
    onDismiss: () -> Unit,
    onApply: (RoleplayScenario) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = com.aiassistant.AiAssistantApp.instance.repository
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

    var rawText by remember { mutableStateOf("") }
    var loadedFileName by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // v2.7.3 流畅度：TXT 可达数 MB，读取与双编码探测迁 IO 协程，回主线程更新状态
            scope.launch {
                val content = withContext(Dispatchers.IO) { RoleplaySmartParser.readTextFromUri(context, it) }
                if (content.isNotBlank()) {
                    rawText = content
                    loadedFileName = it.lastPathSegment?.substringAfterLast('/') ?: "已加载文件"
                    Toast.makeText(context, "已读取 TXT 文件内容 (${content.length}字)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "无法读取该文件，请检查格式", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    EchoGlassDialog(
        onDismissRequest = { if (!isAnalyzing) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 智能读取与场景拆解")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "支持粘贴小说片段、世界观设定、剧本大纲或导入 TXT，系统将调用大模型深度拆解世界观、时间、地点、现场环境、核心冲突与开场引导语。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // API 模型选择
                if (apiConfigs.isNotEmpty()) {
                    var selectorWidth by remember { mutableStateOf(0.dp) }
                    val density = LocalDensity.current
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { if (!isAnalyzing) configMenuExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { selectorWidth = with(density) { it.width.toDp() } }
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
                            modifier = if (selectorWidth > 0.dp) Modifier.width(selectorWidth) else Modifier
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("text/plain", "*/*")) },
                        enabled = !isAnalyzing
                    ) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(loadedFileName ?: "从 TXT 导入")
                    }

                    if (rawText.isNotBlank()) {
                        Text(
                            text = "已输入 ${rawText.length} 字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 140.dp, max = 220.dp),
                    placeholder = {
                        Text("在此粘贴场景设定长文本...\n例如：\n【场景名】赛博朋克夜雨街区\n【世界观】2077未来高科技低生活\n【时间】午夜2点\n【地点】霓虹闪烁的下城区巷道\n【剧情前提】追逐逃亡\n【当前冲突】被追捕者围堵\n【氛围】压抑紧张\n【标签】赛博,雨夜,战斗")
                    },
                    maxLines = 15,
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
                            text = progressStatus.ifBlank { "正在分析拆解中..." },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (rawText.isNotBlank()) {
                            val parsed = RoleplaySmartParser.parseScenario(rawText, currentScenario)
                            onApply(parsed)
                        }
                    },
                    enabled = rawText.isNotBlank() && !isAnalyzing
                ) {
                    Text("本地提取")
                }
                Button(
                    onClick = {
                        if (rawText.isNotBlank()) {
                            scope.launch {
                                isAnalyzing = true
                                progressStatus = "正在调用 AI 模型进行场景深度拆解..."
                                try {
                                    val parsed = com.aiassistant.utils.RoleplaySmartAnalyzer.analyzeScenario(
                                        context = context,
                                        rawText = rawText,
                                        repository = repository,
                                        apiConfig = selectedConfig,
                                        selectedModel = selectedModelName,
                                        onProgress = { progressStatus = it }
                                    )
                                    onApply(parsed)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "拆解异常: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        }
                    },
                    enabled = rawText.isNotBlank() && !isAnalyzing
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isAnalyzing) "拆解中..." else "AI 深度拆解")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAnalyzing) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun BasicSettingSection(
    name: String,
    onNameChange: (String) -> Unit,
    time: String,
    onTimeChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    environment: String,
    onEnvironmentChange: (String) -> Unit,
    tags: String,
    onTagsChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "基本设定",
            style = MaterialTheme.typography.titleMedium
        )

        var nameTouched by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = name,
            onValueChange = {
                nameTouched = true
                onNameChange(it)
            },
            label = { Text("场景名称 *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = nameTouched && name.isBlank()
        )

        OutlinedTextField(
            value = time,
            onValueChange = onTimeChange,
            label = { Text("时间背景") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("例如：黄昏时分、公元3024年、中世纪") }
        )

        OutlinedTextField(
            value = location,
            onValueChange = onLocationChange,
            label = { Text("主要地点") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("例如：废弃图书馆、空间站控制室、酒馆") }
        )

        OutlinedTextField(
            value = environment,
            onValueChange = onEnvironmentChange,
            label = { Text("环境描述") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("描述周围的环境细节、天气、光线等") }
        )

        OutlinedTextField(
            value = tags,
            onValueChange = onTagsChange,
            label = { Text("标签") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("用逗号分隔，例如：科幻,冒险,战斗") }
        )
    }
}

@Composable
private fun WorldBuildingSection(
    worldview: String,
    onWorldviewChange: (String) -> Unit,
    rules: String,
    onRulesChange: (String) -> Unit,
    relationshipState: String,
    onRelationshipStateChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "世界观与规则",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = worldview,
            onValueChange = onWorldviewChange,
            label = { Text("世界观设定") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = { Text("描述世界的宏观背景、历史、势力分布等") }
        )

        OutlinedTextField(
            value = rules,
            onValueChange = onRulesChange,
            label = { Text("世界规则/法则") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("魔法体系、科技水平、物理法则或特殊禁忌") }
        )

        OutlinedTextField(
            value = relationshipState,
            onValueChange = onRelationshipStateChange,
            label = { Text("初始关系状态") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("会话开始时各方角色的关系状态") }
        )
    }
}

@Composable
private fun PlotSettingSection(
    premise: String,
    onPremiseChange: (String) -> Unit,
    conflict: String,
    onConflictChange: (String) -> Unit,
    plotGoal: String,
    onPlotGoalChange: (String) -> Unit,
    openingPrompt: String,
    onOpeningPromptChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "剧情与目标",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = premise,
            onValueChange = onPremiseChange,
            label = { Text("剧情前提") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("发生这一场景的前情提要或起因") }
        )

        OutlinedTextField(
            value = conflict,
            onValueChange = onConflictChange,
            label = { Text("当前冲突") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("场景中正在发生或潜在的主要矛盾") }
        )

        OutlinedTextField(
            value = plotGoal,
            onValueChange = onPlotGoalChange,
            label = { Text("剧情目标") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("本次互动希望达成的剧情推进或结果") }
        )

        OutlinedTextField(
            value = openingPrompt,
            onValueChange = onOpeningPromptChange,
            label = { Text("开场引导/提示词") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("给 AI 的开场引导词，设定第一段描写的基调") }
        )
    }
}

@Composable
private fun NarrativeControlSection(
    atmosphere: String,
    onAtmosphereChange: (String) -> Unit,
    narrativePerspective: String,
    onNarrativePerspectiveChange: (String) -> Unit,
    outputFormat: String,
    onOutputFormatChange: (String) -> Unit,
    contentRestrictions: String,
    onContentRestrictionsChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "叙事控制与文风",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = atmosphere,
            onValueChange = onAtmosphereChange,
            label = { Text("叙事氛围") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("例如：紧张刺激、温馨治愈、黑暗压抑") }
        )

        OutlinedTextField(
            value = narrativePerspective,
            onValueChange = onNarrativePerspectiveChange,
            label = { Text("叙事视角") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("例如：第一人称、第三人称全知、第三人称有限") }
        )

        OutlinedTextField(
            value = outputFormat,
            onValueChange = onOutputFormatChange,
            label = { Text("输出格式") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("期望的输出格式，例如：包含动作描写和对话") }
        )

        OutlinedTextField(
            value = contentRestrictions,
            onValueChange = onContentRestrictionsChange,
            label = { Text("内容限制") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("内容的限制和禁忌") }
        )
    }
}

@Composable
private fun ScenarioPreviewDialog(
    scenario: RoleplayScenario,
    onDismiss: () -> Unit
) {
    EchoGlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("场景预览: ${scenario.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (scenario.worldview.isNotBlank()) {
                    PreviewItem("世界观", scenario.worldview)
                }
                if (scenario.time.isNotBlank()) {
                    PreviewItem("时间", scenario.time)
                }
                if (scenario.location.isNotBlank()) {
                    PreviewItem("地点", scenario.location)
                }
                if (scenario.environment.isNotBlank()) {
                    PreviewItem("环境描述", scenario.environment)
                }
                if (scenario.premise.isNotBlank()) {
                    PreviewItem("剧情前提", scenario.premise)
                }
                if (scenario.rules.isNotBlank()) {
                    PreviewItem("世界规则", scenario.rules)
                }
                if (scenario.relationshipState.isNotBlank()) {
                    PreviewItem("关系状态", scenario.relationshipState)
                }
                if (scenario.conflict.isNotBlank()) {
                    PreviewItem("当前冲突", scenario.conflict)
                }
                if (scenario.plotGoal.isNotBlank()) {
                    PreviewItem("剧情目标", scenario.plotGoal)
                }
                if (scenario.atmosphere.isNotBlank()) {
                    PreviewItem("叙事氛围", scenario.atmosphere)
                }
                if (scenario.narrativePerspective.isNotBlank()) {
                    PreviewItem("叙事视角", scenario.narrativePerspective)
                }
                if (scenario.outputFormat.isNotBlank()) {
                    PreviewItem("输出格式", scenario.outputFormat)
                }
                if (scenario.contentRestrictions.isNotBlank()) {
                    PreviewItem("内容限制", scenario.contentRestrictions)
                }
                if (scenario.openingPrompt.isNotBlank()) {
                    PreviewItem("开场提示", scenario.openingPrompt)
                }
                if (!scenario.tags.isNullOrBlank()) {
                    PreviewItem("标签", scenario.tags!!)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun PreviewItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
