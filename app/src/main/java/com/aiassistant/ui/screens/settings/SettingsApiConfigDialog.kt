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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
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
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.EchoSwitch
import com.aiassistant.ui.components.EchoSwitchSize
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


internal fun parseModelList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val parsed = try {
        val type = object : TypeToken<List<String>>() {}.type
        Gson().fromJson<List<String>>(raw, type).orEmpty()
    } catch (e: Exception) {
        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    return cleanModelNames(parsed)
}

internal fun cleanModelNames(models: List<String>): List<String> {
    return models.mapNotNull { cleanModelName(it) }.distinct()
}

internal fun cleanModelName(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    val blockedEdges = setOf('"', '“', '”', '\'', '`', ']', '[', '\\')
    if (value.first() in blockedEdges || value.last() in blockedEdges) return null
    if (value.any { it.isISOControl() }) return null
    return value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    config: ApiConfig?,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (ApiConfig, List<String>, Set<String>, Map<String, String>, Map<String, ModelCustomSettings>, android.net.Uri?, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = AiAssistantApp.instance.repository
    val context = androidx.compose.ui.platform.LocalContext.current
    val gson = remember { Gson() }

    var isConfigEnabled by remember { mutableStateOf(config?.isEnabled ?: true) }
    var name by remember { mutableStateOf(config?.name ?: "") }
    var provider by remember { mutableStateOf(config?.provider ?: "") }
    var baseUrl by remember { mutableStateOf(config?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(config?.apiKey ?: "") }
    var namedKeyList by remember {
        val initialList = if (!config?.apiKey.isNullOrBlank()) {
            AiRepository.parseNamedApiKeys(config!!.apiKey)
        } else emptyList()
        mutableStateOf(initialList.ifEmpty { listOf(NamedApiKey()) })
    }
    val keyIds = remember {
        mutableStateListOf<String>().apply {
            repeat(namedKeyList.size.coerceAtLeast(1)) {
                add(java.util.UUID.randomUUID().toString())
            }
        }
    }
    val keyReorderState = rememberSmoothReorderState()
    val modelReorderState = rememberSmoothReorderState()
    var keyVisibilityList by remember {
        mutableStateOf(List(16) { false })
    }
    var apiType by remember { mutableStateOf(config?.apiType ?: "openai") }
    var modelName by remember { mutableStateOf(cleanModelName(config?.modelName) ?: "") }
    var availableModels by remember {
        mutableStateOf(parseModelList(config?.availableModels).ifEmpty {
            cleanModelName(config?.modelName)?.let { listOf(it) } ?: emptyList()
        })
    }
    var enabledModelNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var modelCapabilities by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var modelCustomSettings by remember { mutableStateOf<Map<String, ModelCustomSettings>>(emptyMap()) }
    var keyModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var keyModelSearchQuery by remember { mutableStateOf("") }
    var customModelInput by remember { mutableStateOf("") }
    var modelSearchQuery by remember { mutableStateOf("") }
    var isConfiguredModelsExpanded by remember { mutableStateOf(true) }
    val filteredModels = remember(availableModels, modelSearchQuery) {
        if (modelSearchQuery.isBlank()) availableModels
        else availableModels.filter { it.contains(modelSearchQuery.trim(), ignoreCase = true) }
    }
    // 搜索过滤时禁用拖拽，避免 filtered 下标与完整列表错位
    val canReorderModels = modelSearchQuery.isBlank() && availableModels.size > 1
    var isLoadingModels by remember { mutableStateOf(false) }
    var selectedApiAvatarUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingCropAvatarUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingAvatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var clearApiAvatar by remember { mutableStateOf(false) }
    var avatarRevision by remember { mutableIntStateOf(0) }
    val currentApiAvatarBitmap = remember(context, config?.id, pendingAvatarBitmap, selectedApiAvatarUri, clearApiAvatar, avatarRevision) {
        when {
            pendingAvatarBitmap != null -> pendingAvatarBitmap
            clearApiAvatar -> null
            config?.id != null && config.id > 0L -> AvatarManager.getApiModelAvatarBitmap(context, config.id)
            else -> null
        }
    }
    val apiAvatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            pendingCropAvatarUri = it
        }
    }

    // 预设配置
    val presets = mapOf(
        "anthropic" to Triple("https://api.anthropic.com/v1", "claude-sonnet-4-6", "Anthropic"),
        "deepseek" to Triple("https://api.deepseek.com/v1", "deepseek-flash", "DeepSeek"),
        "openai" to Triple("https://api.openai.com/v1", "gpt-6-astra", "OpenAI")
    )

    LaunchedEffect(config?.id) {
        if (config != null) {
            repository.getDecryptedConfig(config.id)?.let { decrypted ->
                apiKey = decrypted.apiKey
                val parsed = AiRepository.parseNamedApiKeys(decrypted.apiKey)
                namedKeyList = parsed.ifEmpty { listOf(NamedApiKey()) }
                keyIds.clear()
                repeat(namedKeyList.size) {
                    keyIds.add(java.util.UUID.randomUUID().toString())
                }
            }
            val selectedModels = repository.getSelectedModels(config.id).first()
            if (selectedModels.isNotEmpty()) {
                val savedNames = selectedModels.map { it.modelName }
                availableModels = cleanModelNames(savedNames + availableModels + config.modelName)
                enabledModelNames = selectedModels
                    .filter { it.isEnabled }
                    .map { it.modelName }
                    .toSet()
                    .ifEmpty { savedNames.toSet() }
                modelCapabilities = selectedModels.associate { it.modelName to it.capability }
                modelCustomSettings = selectedModels.associate {
                    it.modelName to ModelCustomSettings(
                        contextWindowTokens = it.contextWindowTokens,
                        supportsTools = it.supportsTools,
                        supportsVision = it.supportsVision,
                        supportsThinking = it.supportsThinking,
                        supportsWebSearch = it.supportsWebSearch
                    )
                }
            } else {
                enabledModelNames = availableModels.toSet()
            }
        } else {
            enabledModelNames = availableModels.toSet()
        }
    }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = { Text(if (config == null) "添加API配置" else "编辑API配置") },
        text = {
            val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = (screenHeight * 0.78f).dp)
            ) {
                // 预设选择
                item {
                    Text("快速预设", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { (key, value) ->
                            AssistChip(
                                onClick = {
                                    provider = value.third
                                    baseUrl = value.first
                                    modelName = value.second
                                    if (name.isBlank()) name = value.third
                                    apiType = key
                                },
                                label = { Text(value.third, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // API类型
                item {
                    Text("API类型", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val openAiSelected = apiType == "openai"
                        FilterChip(
                            selected = openAiSelected,
                            onClick = { apiType = "openai" },
                            colors = echoFilterChipColors(),
                            border = echoFilterChipBorder(openAiSelected),
                            elevation = echoFilterChipElevation(),
                            label = { Text("OpenAI") }
                        )
                        val anthropicSelected = apiType == "anthropic"
                        FilterChip(
                            selected = anthropicSelected,
                            onClick = { apiType = "anthropic" },
                            colors = echoFilterChipColors(),
                            border = echoFilterChipBorder(anthropicSelected),
                            elevation = echoFilterChipElevation(),
                            label = { Text("Anthropic") }
                        )
                    }
                }

                // API 启用开关
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启用此 API 配置", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (isConfigEnabled) "已开启：允许使用此 API 并显示在模型选择列表中" else "已关闭：停用此 API 且模型不出现在选择列表中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        EchoSwitch(
                            checked = isConfigEnabled,
                            onCheckedChange = { isConfigEnabled = it },
                            size = EchoSwitchSize.Standard // §4.1 迁移表：弹窗主开关用标准尺寸
                        )
                    }
                }

                // 配置名称
                item {
                    SettingsInputField(
                        title = "配置名称",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "例如: 官方 OpenAI / 自定义中转"
                    )
                }

                // 提供商
                item {
                    SettingsInputField(
                        title = "提供商",
                        value = provider,
                        onValueChange = { provider = it },
                        placeholder = "例如: OpenAI / Anthropic / DeepSeek"
                    )
                }

                // Base URL
                item {
                    SettingsInputField(
                        title = "Base URL",
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        placeholder = "https://api.example.com/v1"
                    )
                }

                // API Key 独立输入框列表 (需求 1)
                item {
                    val validKeysCount = namedKeyList.count { it.key.isNotBlank() }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "API Key 密钥列表",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (validKeysCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                ) {
                                    Text(
                                        text = if (validKeysCount > 1) "已录入 $validKeysCount 个密钥 · 自动轮询故障转移" else "已录入 1 个密钥",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "提示：可为每个 Key 设置名称或备注，排在前面的 Key 拥有最高调用优先级，遇到异常自动切换后序 Key。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )

                        namedKeyList.forEachIndexed { index, currentKey ->
                            val isVisible = keyVisibilityList.getOrElse(index) { false }
                            val isKeyEnabled = currentKey.isEnabled
                            val keyLabel = if (isKeyEnabled) "Key ${index + 1}" else "Key ${index + 1} (停用)"
                            val itemId = keyIds.getOrElse(index) { "key_$index" }
                            val isActive = keyReorderState.isItemActive(index)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isKeyEnabled) 0.25f else 0.12f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isKeyEnabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .alpha(if (isKeyEnabled) 1f else 0.65f)
                                    .reorderItem(keyReorderState, index, itemId, shape = RoundedCornerShape(10.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (namedKeyList.size > 1) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent)
                                                    .reorderDragHandle(
                                                        state = keyReorderState,
                                                        index = { index },
                                                        key = { itemId },
                                                        keys = { keyIds.toList() },
                                                        listSize = { namedKeyList.size },
                                                        onMove = { fromIdx, toIdx ->
                                                            val updated = namedKeyList.toMutableList()
                                                            val temp = updated[fromIdx]
                                                            updated[fromIdx] = updated[toIdx]
                                                            updated[toIdx] = temp
                                                            namedKeyList = updated
                                                            apiKey = AiRepository.formatNamedApiKeys(updated)

                                                            if (fromIdx < keyIds.size && toIdx < keyIds.size) {
                                                                val tempId = keyIds[fromIdx]
                                                                keyIds[fromIdx] = keyIds[toIdx]
                                                                keyIds[toIdx] = tempId
                                                            }

                                                            val nextVis = keyVisibilityList.toMutableList()
                                                            if (fromIdx < nextVis.size && toIdx < nextVis.size) {
                                                                val tempV = nextVis[fromIdx]
                                                                nextVis[fromIdx] = nextVis[toIdx]
                                                                nextVis[toIdx] = tempV
                                                                keyVisibilityList = nextVis
                                                            }
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DragIndicator,
                                                    contentDescription = "按住上下拖动调整优先级",
                                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = if (!isKeyEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                else if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(
                                                0.8.dp,
                                                if (!isKeyEnabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                else if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                            )
                                        ) {
                                            Text(
                                                text = keyLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (!isKeyEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    else if (index == 0) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }

                                        BasicTextField(
                                            value = currentKey.name,
                                            onValueChange = { newName ->
                                                val updated = namedKeyList.toMutableList()
                                                updated[index] = currentKey.copy(name = newName)
                                                namedKeyList = updated
                                                apiKey = AiRepository.formatNamedApiKeys(updated)
                                            },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Normal),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                        .border(
                                                            BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (currentKey.name.isEmpty()) {
                                                        Text(
                                                            text = "备注名称 (可选)",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )

                                        EchoSwitch(
                                            checked = isKeyEnabled,
                                            onCheckedChange = { checked ->
                                                val updated = namedKeyList.toMutableList()
                                                updated[index] = currentKey.copy(isEnabled = checked)
                                                namedKeyList = updated
                                                apiKey = AiRepository.formatNamedApiKeys(updated)
                                            }
                                        )

                                        if (namedKeyList.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val fromKey = keyIds.getOrElse(index) { "key_$index" }
                                                        val toKey = keyIds.getOrElse(index - 1) { "key_${index - 1}" }
                                                        keyReorderState.onAnimateSwap(fromKey, toKey, index, index - 1)

                                                        val updated = namedKeyList.toMutableList()
                                                        val temp = updated[index]
                                                        updated[index] = updated[index - 1]
                                                        updated[index - 1] = temp
                                                        namedKeyList = updated
                                                        apiKey = AiRepository.formatNamedApiKeys(updated)

                                                        if (index < keyIds.size && index - 1 < keyIds.size) {
                                                            val tempId = keyIds[index]
                                                            keyIds[index - 1] = keyIds[index].also { keyIds[index] = keyIds[index - 1] }
                                                        }

                                                        val nextVis = keyVisibilityList.toMutableList()
                                                        if (index < nextVis.size && index - 1 < nextVis.size) {
                                                            val tempV = nextVis[index]
                                                            nextVis[index] = nextVis[index - 1]
                                                            nextVis[index - 1] = tempV
                                                            keyVisibilityList = nextVis
                                                        }
                                                    }
                                                },
                                                enabled = index > 0,
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "提升优先级",
                                                    tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (index < namedKeyList.size - 1) {
                                                        val fromKey = keyIds.getOrElse(index) { "key_$index" }
                                                        val toKey = keyIds.getOrElse(index + 1) { "key_${index + 1}" }
                                                        keyReorderState.onAnimateSwap(fromKey, toKey, index, index + 1)

                                                        val updated = namedKeyList.toMutableList()
                                                        val temp = updated[index]
                                                        updated[index] = updated[index + 1]
                                                        updated[index + 1] = temp
                                                        namedKeyList = updated
                                                        apiKey = AiRepository.formatNamedApiKeys(updated)

                                                        if (index < keyIds.size && index + 1 < keyIds.size) {
                                                            val tempId = keyIds[index]
                                                            keyIds[index + 1] = keyIds[index].also { keyIds[index] = keyIds[index + 1] }
                                                        }

                                                        val nextVis = keyVisibilityList.toMutableList()
                                                        if (index < nextVis.size && index + 1 < nextVis.size) {
                                                            val tempV = nextVis[index]
                                                            nextVis[index] = nextVis[index + 1]
                                                            nextVis[index + 1] = tempV
                                                            keyVisibilityList = nextVis
                                                        }
                                                    }
                                                },
                                                enabled = index < namedKeyList.size - 1,
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "降低优先级",
                                                    tint = if (index < namedKeyList.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val updated = namedKeyList.toMutableList()
                                                    updated.removeAt(index)
                                                    namedKeyList = updated.ifEmpty { listOf(NamedApiKey()) }
                                                    if (index < keyIds.size) keyIds.removeAt(index)
                                                    if (keyIds.isEmpty()) keyIds.add(java.util.UUID.randomUUID().toString())
                                                    apiKey = AiRepository.formatNamedApiKeys(namedKeyList)
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "删除此密钥",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = currentKey.key,
                                        onValueChange = { newVal ->
                                            val parsed = AiRepository.parseNamedApiKeys(newVal)
                                            if (parsed.size > 1) {
                                                val updated = namedKeyList.toMutableList()
                                                updated.removeAt(index)
                                                updated.addAll(index, parsed)
                                                namedKeyList = updated
                                                apiKey = AiRepository.formatNamedApiKeys(updated)
                                                if (index < keyIds.size) keyIds.removeAt(index)
                                                repeat(parsed.size) { offset ->
                                                    keyIds.add(index + offset, java.util.UUID.randomUUID().toString())
                                                }
                                            } else if (parsed.size == 1 && (parsed[0].name.isNotBlank() || parsed[0].key != newVal.trim())) {
                                                val updated = namedKeyList.toMutableList()
                                                val existingName = currentKey.name.ifBlank { parsed[0].name }
                                                updated[index] = NamedApiKey(name = existingName, key = parsed[0].key)
                                                namedKeyList = updated
                                                apiKey = AiRepository.formatNamedApiKeys(updated)
                                            } else {
                                                val updated = namedKeyList.toMutableList()
                                                updated[index] = currentKey.copy(key = newVal.trim())
                                                namedKeyList = updated
                                                apiKey = AiRepository.formatNamedApiKeys(updated)
                                            }
                                        },
                                        placeholder = { Text("填写密钥 (sk-...)", style = MaterialTheme.typography.bodyMedium) },
                                        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
                                        ),
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    val nextVis = keyVisibilityList.toMutableList()
                                                    while (nextVis.size <= index) nextVis.add(false)
                                                    nextVis[index] = !isVisible
                                                    keyVisibilityList = nextVis
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (isVisible) "隐藏密钥" else "显示密钥",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // "+ 添加 Key" 按钮
                        OutlinedButton(
                            onClick = {
                                namedKeyList = namedKeyList + NamedApiKey()
                                keyIds.add(java.util.UUID.randomUUID().toString())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加独立 Key 输入框 (Key ${namedKeyList.size + 1})", style = MaterialTheme.typography.labelMedium)
                        }

                        Text(
                            text = "💡 每个输入框填写一个独立 Key。亦可将多行或逗号分隔的密钥批量粘贴进任意输入框自动拆分。当请求超时或遇到连接/认证报错时，系统将自动切换至备用 Key 并透明重试。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                        )
                    }
                }

                // 模型选择
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingsInputField(
                            title = "此 API 的默认模型",
                            value = modelName,
                            onValueChange = { modelName = it },
                            placeholder = "例如: deepseek-v4-pro / gpt-6-astra"
                        )
                        Text(
                            text = "新对话会先使用标记为“新对话默认API”的配置，再使用这里设置的默认模型。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // 区域 1：已配置模型 (支持收起/展开、长按拖动排序与精细化自定义)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (availableModels.size > 1) {
                            Text(
                                text = if (modelSearchQuery.isBlank()) {
                                    "提示：按住模型左侧手柄上下拖动，可调整模型在列表中的顺序。"
                                } else {
                                    "搜索过滤时暂不可拖动排序，清空搜索后可调整顺序。"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { isConfiguredModelsExpanded = !isConfiguredModelsExpanded }
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "已配置模型 (${availableModels.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { isConfiguredModelsExpanded = !isConfiguredModelsExpanded },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isConfiguredModelsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isConfiguredModelsExpanded) "收起已配置模型" else "展开已配置模型",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (availableModels.isNotEmpty() && isConfiguredModelsExpanded) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = { enabledModelNames = availableModels.toSet() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("全选", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick = { enabledModelNames = emptySet() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("清空启用", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick = { modelCustomSettings = emptyMap() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("重置配置", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        // 手动输入并添加自定义模型 (紧凑型设计，大幅降低空间占用)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BasicTextField(
                                value = customModelInput,
                                onValueChange = { customModelInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                            .border(
                                                BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (customModelInput.isEmpty()) {
                                            Text(
                                                text = "手动添加模型 (如: qwen-max, claude-3-7-sonnet)...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val cleaned = cleanModelName(customModelInput)?.trim()
                                    if (!cleaned.isNullOrBlank() && cleaned !in availableModels) {
                                        availableModels = availableModels + cleaned
                                        enabledModelNames = enabledModelNames + cleaned
                                        if (modelName.isBlank()) modelName = cleaned
                                        val eval = ModelCapabilityEngine.evaluateModel(cleaned)
                                        modelCustomSettings = modelCustomSettings + (cleaned to ModelCustomSettings(
                                            contextWindowTokens = eval.contextWindowTokens,
                                            supportsTools = eval.supportsTools,
                                            supportsVision = eval.supportsVision,
                                            supportsThinking = eval.supportsReasoning,
                                            supportsWebSearch = true
                                        ))
                                        customModelInput = ""
                                    }
                                },
                                enabled = customModelInput.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("添加", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        AnimatedVisibility(
                            visible = isConfiguredModelsExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (availableModels.isEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ) {
                                        Text(
                                            text = "暂无已配置的模型。请点击下方“从Key中获取模型列表”或手动输入添加。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    if (availableModels.size > 5) {
                                        OutlinedTextField(
                                            value = modelSearchQuery,
                                            onValueChange = { modelSearchQuery = it },
                                            placeholder = { Text("搜索已配置模型...", style = MaterialTheme.typography.bodySmall) },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            trailingIcon = if (modelSearchQuery.isNotBlank()) {
                                                {
                                                    IconButton(onClick = { modelSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            } else null,
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp),
                                            textStyle = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    val modelsToShow = if (canReorderModels) availableModels else filteredModels
                                    modelsToShow.forEachIndexed { index, model ->
                                        val modelItemId = model
                                        val isModelDragActive = canReorderModels && modelReorderState.isItemActive(index)
                                        ModelCustomSettingCard(
                                            model = model,
                                            checked = enabledModelNames.contains(model),
                                            selected = modelName == model,
                                            capability = modelCapabilities[model] ?: "auto",
                                            customSettings = modelCustomSettings[model],
                                            reorderState = if (canReorderModels) modelReorderState else null,
                                            reorderIndex = index,
                                            reorderKey = modelItemId,
                                            reorderKeys = { availableModels.toList() },
                                            reorderListSize = { availableModels.size },
                                            onReorderMove = { fromIdx, toIdx ->
                                                if (fromIdx in availableModels.indices && toIdx in availableModels.indices && fromIdx != toIdx) {
                                                    val updated = availableModels.toMutableList()
                                                    val temp = updated[fromIdx]
                                                    updated[fromIdx] = updated[toIdx]
                                                    updated[toIdx] = temp
                                                    availableModels = updated
                                                }
                                            },
                                            isDragActive = isModelDragActive,
                                            onCheckedChange = { isChecked ->
                                                enabledModelNames = if (isChecked) enabledModelNames + model else enabledModelNames - model
                                            },
                                            onSelectAsDefault = {
                                                modelName = model
                                                enabledModelNames = enabledModelNames + model
                                            },
                                            onRemove = {
                                                availableModels = availableModels - model
                                                enabledModelNames = enabledModelNames - model
                                                if (modelName == model) {
                                                    modelName = availableModels.firstOrNull() ?: ""
                                                }
                                            },
                                            onResetCustomSettings = {
                                                modelCustomSettings = modelCustomSettings - model
                                            },
                                            onCustomSettingsChange = { newSettings ->
                                                modelCustomSettings = modelCustomSettings + (model to newSettings)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (!isConfiguredModelsExpanded && availableModels.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isConfiguredModelsExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "已折叠 (${availableModels.size} 个模型，已勾选 ${enabledModelNames.size} 个)，点击展开",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 区域 2：从Key中读取到的模型
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (keyModels.isNotEmpty()) "从Key中读取到的模型 (${keyModels.size})" else "从Key中读取到的模型",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (keyModels.isNotEmpty()) {
                                val unaddedCount = keyModels.count { it !in availableModels }
                                if (unaddedCount > 0) {
                                    TextButton(
                                        onClick = {
                                            val unadded = keyModels.filter { it !in availableModels }
                                            availableModels = availableModels + unadded
                                            enabledModelNames = enabledModelNames + unadded
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("一键添加全部 ($unaddedCount)", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoadingModels = true
                                    val result = repository.fetchAvailableModelsDirect(baseUrl, apiKey, apiType)
                                    result.onSuccess { models ->
                                        val cleaned = cleanModelNames(models)
                                        keyModels = cleaned
                                        val updated = modelCustomSettings.toMutableMap()
                                        cleaned.forEach { m ->
                                            if (!updated.containsKey(m)) {
                                                val eval = ModelCapabilityEngine.evaluateModel(m)
                                                updated[m] = ModelCustomSettings(
                                                    contextWindowTokens = eval.contextWindowTokens,
                                                    supportsTools = eval.supportsTools,
                                                    supportsVision = eval.supportsVision,
                                                    supportsThinking = eval.supportsReasoning,
                                                    supportsWebSearch = true
                                                )
                                            }
                                        }
                                        modelCustomSettings = updated
                                        if (modelName.isBlank() && cleaned.isNotEmpty()) {
                                            modelName = cleaned.first()
                                        }
                                    }
                                    isLoadingModels = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && !isLoadingModels
                        ) {
                            if (isLoadingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (keyModels.isEmpty()) "点击从 API Key 读取模型列表" else "重新读取 Key 模型列表")
                        }

                        if (keyModels.isNotEmpty()) {
                            OutlinedTextField(
                                value = keyModelSearchQuery,
                                onValueChange = { keyModelSearchQuery = it },
                                placeholder = { Text("在 Key 读取到的模型中搜索...", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = if (keyModelSearchQuery.isNotBlank()) {
                                    {
                                        IconButton(onClick = { keyModelSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )

                            val filteredKeyModels = if (keyModelSearchQuery.isBlank()) keyModels
                            else keyModels.filter { it.contains(keyModelSearchQuery.trim(), ignoreCase = true) }

                            filteredKeyModels.take(60).forEach { km ->
                                val isAdded = availableModels.contains(km)
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = km,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val eval = remember(km) { ModelCapabilityEngine.evaluateModel(km) }
                                            Row(
                                                modifier = Modifier.padding(top = 2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (eval.contextWindowDisplay.isNotBlank()) {
                                                    Text(eval.contextWindowDisplay, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                                if (eval.supportsVision) {
                                                    Text("视觉", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                                }
                                                if (eval.supportsTools) {
                                                    Text("工具", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                                }
                                                if (eval.supportsReasoning) {
                                                    Text("思考", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }

                                        if (isAdded) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "已添加",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    availableModels = availableModels + km
                                                    enabledModelNames = enabledModelNames + km
                                                    if (modelName.isBlank()) modelName = km
                                                    val eval = ModelCapabilityEngine.evaluateModel(km)
                                                    modelCustomSettings = modelCustomSettings + (km to ModelCustomSettings(
                                                        contextWindowTokens = eval.contextWindowTokens,
                                                        supportsTools = eval.supportsTools,
                                                        supportsVision = eval.supportsVision,
                                                        supportsThinking = eval.supportsReasoning,
                                                        supportsWebSearch = true
                                                    ))
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("添加", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    ApiModelAvatarSection(
                        currentBitmap = currentApiAvatarBitmap,
                        hasPendingAvatar = selectedApiAvatarUri != null || pendingAvatarBitmap != null,
                        clearAvatar = clearApiAvatar,
                        onPickAvatar = { apiAvatarPicker.launch("image/*") },
                        onClearAvatar = {
                            selectedApiAvatarUri = null
                            pendingAvatarBitmap = null
                            clearApiAvatar = true
                            avatarRevision++
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanedCurrentModel = cleanModelName(modelName).orEmpty()
                    val modelNames = cleanModelNames(availableModels + cleanedCurrentModel)
                    val enabledModels = (enabledModelNames + modelName)
                        .mapNotNull { cleanModelName(it) }
                        .toSet()
                    val newConfig = ApiConfig(
                        id = config?.id ?: 0,
                        name = name.ifBlank { provider },
                        provider = provider,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        apiType = apiType,
                        modelName = cleanedCurrentModel,
                        availableModels = modelNames.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                        temperature = 0.95f,
                        maxTokens = config?.maxTokens ?: 8192,
                        topP = 1.0f,
                        enableThinking = true,
                        thinkingEffort = "medium",
                        enableWebSearch = false,
                        isEnabled = isConfigEnabled,
                        isDefault = config?.isDefault ?: false,
                        createdAt = config?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(newConfig, modelNames, enabledModels, modelCapabilities, modelCustomSettings, selectedApiAvatarUri, clearApiAvatar)
                },
                enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && cleanModelName(modelName) != null && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    pendingCropAvatarUri?.let { cropUri ->
        ImageCropEditDialog(
            imageUri = cropUri,
            shapeMode = CropShapeMode.CIRCLE,
            title = "裁剪与编辑 API 模型头像",
            onDismiss = { pendingCropAvatarUri = null },
            onConfirm = { croppedBitmap ->
                val tempUri = AvatarManager.saveTempAvatarBitmap(context, croppedBitmap)
                selectedApiAvatarUri = tempUri
                pendingAvatarBitmap = croppedBitmap
                clearApiAvatar = false
                avatarRevision++
                pendingCropAvatarUri = null
            }
        )
    }
}

@Composable
private fun ModelCustomSettingCard(
    model: String,
    checked: Boolean,
    selected: Boolean,
    capability: String,
    customSettings: ModelCustomSettings?,
    onCheckedChange: (Boolean) -> Unit,
    onSelectAsDefault: () -> Unit,
    onRemove: () -> Unit,
    onResetCustomSettings: () -> Unit = {},
    onCustomSettingsChange: (ModelCustomSettings) -> Unit,
    reorderState: com.aiassistant.ui.components.SmoothReorderState? = null,
    reorderIndex: Int = 0,
    reorderKey: Any = model,
    reorderKeys: () -> List<Any> = { listOf(model) },
    reorderListSize: () -> Int = { 1 },
    onReorderMove: (Int, Int) -> Unit = { _, _ -> },
    isDragActive: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(16.dp)
    val glass = echoGlassPalette()

    val currentSettings = customSettings ?: run {
        val eval = ModelCapabilityEngine.evaluateModel(model)
        ModelCustomSettings(
            contextWindowTokens = eval.contextWindowTokens,
            supportsTools = eval.supportsTools,
            supportsVision = eval.supportsVision,
            supportsThinking = eval.supportsReasoning,
            supportsWebSearch = true
        )
    }

    val baseModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 3.dp)
    val dragModifier = if (reorderState != null) {
        baseModifier.reorderItem(reorderState, reorderIndex, reorderKey, shape = cardShape)
    } else {
        baseModifier
    }

    Surface(
        modifier = dragModifier,
        shape = cardShape,
        color = if (checked) glass.control.copy(alpha = 0.65f) else glass.control.copy(alpha = 0.32f),
        border = BorderStroke(
            if (selected) 1.2.dp else 0.8.dp,
            if (selected) glass.outlineSelected else glass.outline.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (reorderState != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isDragActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else Color.Transparent
                            )
                            .reorderDragHandle(
                                state = reorderState,
                                index = { reorderIndex },
                                key = { reorderKey },
                                keys = reorderKeys,
                                listSize = reorderListSize,
                                onMove = onReorderMove
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "按住上下拖动调整模型顺序",
                            tint = if (isDragActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCheckedChange(!checked) }
                ) {
                    Text(
                        text = com.aiassistant.domain.model.ModelDisplayName.format(model),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val ctxTokens = currentSettings.contextWindowTokens
                        val ctxLabel = if (ctxTokens != null && ctxTokens > 0) {
                            if (ctxTokens >= 1_000_000) "${ctxTokens / 1_000_000}M"
                            else "${ctxTokens / 1024}K"
                        } else "128K"

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = ctxLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        if (currentSettings.supportsVision == true) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "视觉",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (currentSettings.supportsTools == true) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "工具",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (currentSettings.supportsThinking == true) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "思考",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (currentSettings.supportsWebSearch == true) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = "联网",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                RadioButton(
                    selected = selected,
                    onClick = onSelectAsDefault,
                    modifier = Modifier.size(36.dp)
                )

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.Tune,
                        contentDescription = "自定义模型配置",
                        tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "移除模型",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = glass.outline.copy(alpha = 0.35f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "模型自定义配置",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            onClick = onResetCustomSettings,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "清空模型配置",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "清空配置",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 上下文大小配置 (支持快捷预设标签与自定义精确数值输入)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("上下文大小 (Tokens):", style = MaterialTheme.typography.labelSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                32 * 1024 to "32K",
                                64 * 1024 to "64K",
                                128 * 1024 to "128K",
                                200 * 1024 to "200K",
                                1024 * 1024 to "1M",
                                2048 * 1024 to "2M"
                            ).forEach { (tokens, label) ->
                                val isSelected = currentSettings.contextWindowTokens == tokens
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        onCustomSettingsChange(currentSettings.copy(contextWindowTokens = tokens))
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    colors = echoFilterChipColors(),
                                    border = echoFilterChipBorder(isSelected)
                                )
                            }
                        }

                        // 自定义上下文大小精确输入 (紧凑型设计，大幅降低空间占用)
                        var customTokensInput by remember(currentSettings.contextWindowTokens) {
                            val cur = currentSettings.contextWindowTokens
                            mutableStateOf(if (cur != null && cur > 0) cur.toString() else "")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BasicTextField(
                                value = customTokensInput,
                                onValueChange = { newVal ->
                                    val digits = newVal.filter { it.isDigit() }
                                    customTokensInput = digits
                                    val parsed = digits.toIntOrNull()
                                    if (parsed != null && parsed > 0) {
                                        onCustomSettingsChange(currentSettings.copy(contextWindowTokens = parsed))
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                            .border(
                                                BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (customTokensInput.isEmpty()) {
                                            Text(
                                                text = "自定义 Tokens (如 8192, 131072, 262144)...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (customTokensInput.isNotBlank()) {
                                val curInt = customTokensInput.toIntOrNull()
                                val displayK = if (curInt != null && curInt > 0) {
                                    if (curInt >= 1_000_000) "${curInt / 1_000_000}M" else "${curInt / 1024}K"
                                } else ""
                                if (displayK.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    ) {
                                        Text(
                                            text = "约 $displayK",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4 项特性开关
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("模型特性支持开关:", style = MaterialTheme.typography.labelSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val toolsActive = currentSettings.supportsTools == true
                            FilterChip(
                                selected = toolsActive,
                                onClick = {
                                    onCustomSettingsChange(currentSettings.copy(supportsTools = !toolsActive))
                                },
                                label = { Text(if (toolsActive) "✓ 支持工具" else "支持工具", style = MaterialTheme.typography.labelSmall) },
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(toolsActive)
                            )

                            val visionActive = currentSettings.supportsVision == true
                            FilterChip(
                                selected = visionActive,
                                onClick = {
                                    onCustomSettingsChange(currentSettings.copy(supportsVision = !visionActive))
                                },
                                label = { Text(if (visionActive) "✓ 支持视觉" else "支持视觉", style = MaterialTheme.typography.labelSmall) },
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(visionActive)
                            )

                            val thinkingActive = currentSettings.supportsThinking == true
                            FilterChip(
                                selected = thinkingActive,
                                onClick = {
                                    onCustomSettingsChange(currentSettings.copy(supportsThinking = !thinkingActive))
                                },
                                label = { Text(if (thinkingActive) "✓ 支持思考" else "支持思考", style = MaterialTheme.typography.labelSmall) },
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(thinkingActive)
                            )

                            val webSearchActive = currentSettings.supportsWebSearch == true
                            FilterChip(
                                selected = webSearchActive,
                                onClick = {
                                    onCustomSettingsChange(currentSettings.copy(supportsWebSearch = !webSearchActive))
                                },
                                label = { Text(if (webSearchActive) "✓ 支持联网搜索" else "支持联网搜索", style = MaterialTheme.typography.labelSmall) },
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(webSearchActive)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiModelAvatarSection(
    currentBitmap: android.graphics.Bitmap?,
    hasPendingAvatar: Boolean,
    clearAvatar: Boolean,
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("此 API 的模型对话头像", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when {
                    currentBitmap != null -> Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = "API模型头像",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    hasPendingAvatar -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    else -> Image(
                        painter = painterResource(id = R.drawable.deepseek),
                        contentDescription = "默认模型头像",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = when {
                        hasPendingAvatar -> "已选择新头像，保存后生效"
                        clearAvatar -> "保存后恢复默认头像"
                        currentBitmap != null -> "当前使用此 API 的自定义头像"
                        else -> "当前使用默认 deepseek 头像"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickAvatar, shape = RoundedCornerShape(999.dp)) {
                        Text("更换")
                    }
                    TextButton(onClick = onClearAvatar) {
                        Text("恢复默认")
                    }
                }
            }
        }
    }
}
