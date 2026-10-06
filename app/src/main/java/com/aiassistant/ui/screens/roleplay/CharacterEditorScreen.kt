package com.aiassistant.ui.screens.roleplay

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.ui.components.EchoConfirmDialog
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.ImageCropEditDialog
import com.aiassistant.ui.components.CropShapeMode
import com.aiassistant.ui.components.echoSwitchColors
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.RoleplaySmartParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditorScreen(
    character: CharacterProfile? = null,
    onSave: (CharacterProfile) -> Unit,
    onDelete: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val draftKey = "character:${character?.id ?: "new"}"
    retainEditorDraft(draftKey)
    var avatarUri by editorField(draftKey, "avatarUri", character?.avatarUri)
    var name by editorField(draftKey, "name", character?.name ?: "")
    var identity by editorField(draftKey, "identity", character?.identity ?: "")
    var personality by editorField(draftKey, "personality", character?.personality ?: "")
    var background by editorField(draftKey, "background", character?.background ?: "")
    var speakingStyle by editorField(draftKey, "speakingStyle", character?.speakingStyle ?: "")
    var goals by editorField(draftKey, "goals", character?.goals ?: "")
    var relationships by editorField(draftKey, "relationships", character?.relationships ?: "")
    var knowledge by editorField(draftKey, "knowledge", character?.knowledge ?: "")
    var constraints by editorField(draftKey, "constraints", character?.constraints ?: "")
    var behaviorRules by editorField(draftKey, "behaviorRules", character?.behaviorRules ?: "")
    var greeting by editorField(draftKey, "greeting", character?.greeting ?: "")
    var exampleDialogue by editorField(draftKey, "exampleDialogue", character?.exampleDialogue ?: "")
    var tags by editorField(draftKey, "tags", character?.tags ?: "")
    var isFavorite by editorField(draftKey, "isFavorite", character?.isFavorite ?: false)
    var isDefault by editorField(draftKey, "isDefault", character?.isDefault ?: false)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showSmartReadDialog by remember { mutableStateOf(false) }
    var currentSection by editorField(draftKey, "section", 0)

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (character == null) "创建角色" else "编辑角色") },
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
                    if (character != null) {
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
                            val newCharacter = CharacterProfile(
                                id = character?.id ?: 0,
                                name = name,
                                avatarUri = avatarUri,
                                identity = identity,
                                personality = personality,
                                background = background,
                                speakingStyle = speakingStyle,
                                goals = goals,
                                relationships = relationships,
                                knowledge = knowledge,
                                constraints = constraints,
                                behaviorRules = behaviorRules,
                                greeting = greeting,
                                exampleDialogue = exampleDialogue,
                                tags = tags.ifBlank { null },
                                isFavorite = isFavorite,
                                isDefault = isDefault,
                                createdAt = character?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(newCharacter)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存角色")
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
                        text = "支持一键读取长文本或 TXT 角色卡，自动填充设定",
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
                    text = { Text("基本信息") }
                )
                Tab(
                    selected = currentSection == 1,
                    onClick = { currentSection = 1 },
                    text = { Text("性格背景") }
                )
                Tab(
                    selected = currentSection == 2,
                    onClick = { currentSection = 2 },
                    text = { Text("行为设定") }
                )
                Tab(
                    selected = currentSection == 3,
                    onClick = { currentSection = 3 },
                    text = { Text("对话示例") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 内容区域
            when (currentSection) {
                0 -> BasicInfoSection(
                    avatarUri = avatarUri,
                    onAvatarUriChange = { avatarUri = it },
                    name = name,
                    onNameChange = { name = it },
                    identity = identity,
                    onIdentityChange = { identity = it },
                    tags = tags,
                    onTagsChange = { tags = it },
                    isDefault = isDefault,
                    onIsDefaultChange = { isDefault = it }
                )
                1 -> PersonalitySection(
                    personality = personality,
                    onPersonalityChange = { personality = it },
                    background = background,
                    onBackgroundChange = { background = it },
                    speakingStyle = speakingStyle,
                    onSpeakingStyleChange = { speakingStyle = it },
                    goals = goals,
                    onGoalsChange = { goals = it }
                )
                2 -> BehaviorSection(
                    relationships = relationships,
                    onRelationshipsChange = { relationships = it },
                    knowledge = knowledge,
                    onKnowledgeChange = { knowledge = it },
                    constraints = constraints,
                    onConstraintsChange = { constraints = it },
                    behaviorRules = behaviorRules,
                    onBehaviorRulesChange = { behaviorRules = it }
                )
                3 -> DialogueSection(
                    greeting = greeting,
                    onGreetingChange = { greeting = it },
                    exampleDialogue = exampleDialogue,
                    onExampleDialogueChange = { exampleDialogue = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

        }
    }

    // 一键读取对话框
    if (showSmartReadDialog) {
        SmartReadCharacterDialog(
            currentProfile = CharacterProfile(
                id = character?.id ?: 0,
                name = name,
                avatarUri = avatarUri,
                identity = identity,
                personality = personality,
                background = background,
                speakingStyle = speakingStyle,
                goals = goals,
                relationships = relationships,
                knowledge = knowledge,
                constraints = constraints,
                behaviorRules = behaviorRules,
                greeting = greeting,
                exampleDialogue = exampleDialogue,
                tags = tags,
                isFavorite = isFavorite,
                isDefault = isDefault
            ),
            onDismiss = { showSmartReadDialog = false },
            onApply = { parsed ->
                if (!parsed.avatarUri.isNullOrBlank()) avatarUri = parsed.avatarUri
                if (parsed.name.isNotBlank()) name = parsed.name
                if (parsed.identity.isNotBlank()) identity = parsed.identity
                if (parsed.personality.isNotBlank()) personality = parsed.personality
                if (parsed.background.isNotBlank()) background = parsed.background
                if (parsed.speakingStyle.isNotBlank()) speakingStyle = parsed.speakingStyle
                if (parsed.goals.isNotBlank()) goals = parsed.goals
                if (parsed.relationships.isNotBlank()) relationships = parsed.relationships
                if (parsed.knowledge.isNotBlank()) knowledge = parsed.knowledge
                if (parsed.constraints.isNotBlank()) constraints = parsed.constraints
                if (parsed.behaviorRules.isNotBlank()) behaviorRules = parsed.behaviorRules
                if (parsed.greeting.isNotBlank()) greeting = parsed.greeting
                if (parsed.exampleDialogue.isNotBlank()) exampleDialogue = parsed.exampleDialogue
                parsed.tags?.takeIf { it.isNotBlank() }?.let { tags = it }
                showSmartReadDialog = false
                Toast.makeText(context, "角色设定已自动识别并填充", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        EchoConfirmDialog(
            title = "确认删除",
            text = "确定要删除角色\"${name}\"吗？此操作不可恢复。",
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
        CharacterPreviewDialog(
            character = CharacterProfile(
                id = character?.id ?: 0,
                name = name,
                avatarUri = avatarUri,
                identity = identity,
                personality = personality,
                background = background,
                speakingStyle = speakingStyle,
                goals = goals,
                relationships = relationships,
                knowledge = knowledge,
                constraints = constraints,
                behaviorRules = behaviorRules,
                greeting = greeting,
                exampleDialogue = exampleDialogue,
                tags = tags,
                isFavorite = isFavorite,
                isDefault = isDefault
            ),
            onDismiss = { showPreview = false }
        )
    }
}

@Composable
private fun SmartReadCharacterDialog(
    currentProfile: CharacterProfile,
    onDismiss: () -> Unit,
    onApply: (CharacterProfile) -> Unit
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
                Text("AI 智能读取与人设拆解")
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
                    text = "支持粘贴小说正文、人物小传、设定长文本或导入 TXT，系统将调用大模型深度拆解主角/配角性格、生平经历、口吻文风、动机与开场白，绝不无脑照抄原文。",
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
                        Text("在此粘贴小说章节、人物小传或人设长文本...\n例如：爱莉希雅是十三英桀第二位，开朗温柔带有一丝神秘优雅...")
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
                            val parsed = RoleplaySmartParser.parseCharacter(rawText, currentProfile)
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
                                progressStatus = "正在调用 AI 模型进行人设深度拆解..."
                                try {
                                    val parsed = com.aiassistant.utils.RoleplaySmartAnalyzer.analyzeCharacter(
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
private fun BasicInfoSection(
    avatarUri: String?,
    onAvatarUriChange: (String?) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    identity: String,
    onIdentityChange: (String) -> Unit,
    tags: String,
    onTagsChange: (String) -> Unit,
    isDefault: Boolean,
    onIsDefaultChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingCropUri = uri
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
                val savedUri = AvatarManager.saveCharacterAvatarBitmap(context, croppedBitmap)
                if (savedUri != null) {
                    onAvatarUriChange(savedUri)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "基本信息",
            style = MaterialTheme.typography.titleMedium
        )

        // 角色头像选择器
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val avatarBitmap = remember(avatarUri) {
                    avatarUri?.let { uriStr ->
                        runCatching {
                            val uri = Uri.parse(uriStr)
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }.getOrNull()
                    }
                }
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap.asImageBitmap(),
                        contentDescription = "角色头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "添加角色头像",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "选择头像",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (avatarUri != null) {
                IconButton(
                    onClick = { onAvatarUriChange(null) },
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "清除头像",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }

        var nameTouched by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = name,
            onValueChange = {
                nameTouched = true
                onNameChange(it)
            },
            label = { Text("角色名称 *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = nameTouched && name.isBlank()
        )

        OutlinedTextField(
            value = identity,
            onValueChange = onIdentityChange,
            label = { Text("身份/职业") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("例如：魔法师、侦探、学生") }
        )

        OutlinedTextField(
            value = tags,
            onValueChange = onTagsChange,
            label = { Text("标签") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("用逗号分隔，例如：奇幻,主角,女性") }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设为默认角色")
            Switch(
                checked = isDefault,
                onCheckedChange = onIsDefaultChange,
                colors = echoSwitchColors()
            )
        }
    }
}

@Composable
private fun PersonalitySection(
    personality: String,
    onPersonalityChange: (String) -> Unit,
    background: String,
    onBackgroundChange: (String) -> Unit,
    speakingStyle: String,
    onSpeakingStyleChange: (String) -> Unit,
    goals: String,
    onGoalsChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "性格背景",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = personality,
            onValueChange = onPersonalityChange,
            label = { Text("性格特征") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("描述角色的性格特点，例如：开朗乐观，但在关键时刻非常冷静果断") }
        )

        OutlinedTextField(
            value = background,
            onValueChange = onBackgroundChange,
            label = { Text("背景故事") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = { Text("角色的出身、经历、重要过往等") }
        )

        OutlinedTextField(
            value = speakingStyle,
            onValueChange = onSpeakingStyleChange,
            label = { Text("说话方式/文风") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("角色的语气、口头禅、用词习惯等，例如：喜欢用反问句，口头禅是'真是麻烦啊'") }
        )

        OutlinedTextField(
            value = goals,
            onValueChange = onGoalsChange,
            label = { Text("目标与动机") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("角色当前追求的目标或行动动机") }
        )
    }
}

@Composable
private fun BehaviorSection(
    relationships: String,
    onRelationshipsChange: (String) -> Unit,
    knowledge: String,
    onKnowledgeChange: (String) -> Unit,
    constraints: String,
    onConstraintsChange: (String) -> Unit,
    behaviorRules: String,
    onBehaviorRulesChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "行为设定",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = relationships,
            onValueChange = onRelationshipsChange,
            label = { Text("人际关系") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("与其他角色的关系，对用户的态度等") }
        )

        OutlinedTextField(
            value = knowledge,
            onValueChange = onKnowledgeChange,
            label = { Text("知识边界") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("角色了解和不了解的知识范围") }
        )

        OutlinedTextField(
            value = constraints,
            onValueChange = onConstraintsChange,
            label = { Text("绝对禁止") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("角色绝不能做或说的事情") }
        )

        OutlinedTextField(
            value = behaviorRules,
            onValueChange = onBehaviorRulesChange,
            label = { Text("行为约束") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("角色的特殊行为模式或反应规则") }
        )
    }
}

@Composable
private fun DialogueSection(
    greeting: String,
    onGreetingChange: (String) -> Unit,
    exampleDialogue: String,
    onExampleDialogueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "对话示例",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = greeting,
            onGreetingChange,
            label = { Text("初始问候语") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("角色在会话开始时的第一句话") }
        )

        OutlinedTextField(
            value = exampleDialogue,
            onValueChange = onExampleDialogueChange,
            label = { Text("示例对话") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            placeholder = { Text("提供一些示例对话，展示角色的说话风格") }
        )
    }
}

@Composable
private fun CharacterPreviewDialog(
    character: CharacterProfile,
    onDismiss: () -> Unit
) {
    EchoGlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("角色预览: ${character.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (character.identity.isNotBlank()) {
                    PreviewItem("身份/职业", character.identity)
                }
                if (character.personality.isNotBlank()) {
                    PreviewItem("性格特征", character.personality)
                }
                if (character.background.isNotBlank()) {
                    PreviewItem("背景故事", character.background)
                }
                if (character.speakingStyle.isNotBlank()) {
                    PreviewItem("说话方式", character.speakingStyle)
                }
                if (character.goals.isNotBlank()) {
                    PreviewItem("目标动机", character.goals)
                }
                if (character.relationships.isNotBlank()) {
                    PreviewItem("关系设定", character.relationships)
                }
                if (character.knowledge.isNotBlank()) {
                    PreviewItem("知识边界", character.knowledge)
                }
                if (character.constraints.isNotBlank()) {
                    PreviewItem("禁止违背", character.constraints)
                }
                if (character.behaviorRules.isNotBlank()) {
                    PreviewItem("行为约束", character.behaviorRules)
                }
                if (character.greeting.isNotBlank()) {
                    PreviewItem("初始问候", character.greeting)
                }
                if (character.exampleDialogue.isNotBlank()) {
                    PreviewItem("示例对话", character.exampleDialogue)
                }
                if (!character.tags.isNullOrBlank()) {
                    PreviewItem("标签", character.tags!!)
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
