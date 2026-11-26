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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.aiassistant.ui.components.ImageCropEditDialog
import com.aiassistant.ui.components.CropShapeMode
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiassistant.AiAssistantApp
import com.aiassistant.R
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.data.repository.helpers.ApiEndpointHelper
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.NamedApiKey
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.domain.model.ModelCustomSettings
import com.aiassistant.ui.components.EchoGlassSubPanelShape
import com.aiassistant.ui.components.EchoScaffold
import com.aiassistant.ui.components.EchoSwitch
import com.aiassistant.ui.components.EchoSwitchSize
import com.aiassistant.ui.components.echoContentInset
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.rememberSmoothReorderState
import com.aiassistant.ui.components.reorderItem
import com.aiassistant.ui.components.reorderDragHandle
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.ui.theme.LocalEchoThemeStyle
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.BackgroundImageManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

internal data class ApiModelDiscoveryEntry(
    val model: String,
    val isAdded: Boolean,
    val isUnavailable: Boolean
)

/** 只有成功读取到当前接口清单后才能比较；未返回不等于已验证调用失败。 */
internal fun apiModelDiscoveryEntries(
    configuredModels: List<String>,
    fetchedModels: List<String>?,
    query: String = ""
): List<ApiModelDiscoveryEntry> {
    if (fetchedModels == null) return emptyList()
    val configured = cleanModelNames(configuredModels).toSet()
    val fetched = cleanModelNames(fetchedModels)
    val fetchedSet = fetched.toSet()
    val missing = configured.filter { it !in fetchedSet }
    return (missing + fetched).filter { it.contains(query.trim(), ignoreCase = true) }
        .map { ApiModelDiscoveryEntry(it, it in configured, it !in fetchedSet) }
}

internal enum class ApiConfigSection(val key: String, val label: String, val index: Int) {
    CONNECTION("api_section_connection", "连接", 1),
    KEYS("api_section_keys", "密钥", 2),
    MODELS("api_section_models", "模型", 3),
    OTHER("api_section_other", "其他", 4)
}

internal fun apiConfigSectionIndices(
    discoveredCount: Int,
    showDiscoveryEmpty: Boolean
): Map<ApiConfigSection, Int> {
    val safeCount = discoveredCount.coerceAtLeast(0)
    val otherIndex = 7 + safeCount + if (showDiscoveryEmpty) 1 else 0
    return mapOf(
        ApiConfigSection.CONNECTION to 0,
        ApiConfigSection.KEYS to 2,
        ApiConfigSection.MODELS to 4,
        ApiConfigSection.OTHER to otherIndex
    )
}

internal fun resolveActiveApiConfigSection(
    firstVisibleIndex: Int,
    canScrollForward: Boolean,
    sectionIndices: Map<ApiConfigSection, Int>
): ApiConfigSection {
    if (!canScrollForward && firstVisibleIndex > 0) return ApiConfigSection.OTHER
    return ApiConfigSection.entries
        .filter { (sectionIndices[it] ?: Int.MAX_VALUE) <= firstVisibleIndex }
        .maxByOrNull { sectionIndices[it] ?: 0 }
        ?: ApiConfigSection.CONNECTION
}

internal data class EditableApiKeyItem(
    val id: String = UUID.randomUUID().toString(),
    val namedKey: NamedApiKey = NamedApiKey()
)

internal fun moveApiKeyItem(
    items: List<EditableApiKeyItem>,
    fromIndex: Int,
    toIndex: Int
): List<EditableApiKeyItem> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    return items.toMutableList().apply {
        val moved = removeAt(fromIndex)
        add(toIndex, moved)
    }
}

internal fun swapApiKeyItems(
    items: List<EditableApiKeyItem>,
    indexA: Int,
    indexB: Int
): List<EditableApiKeyItem> {
    if (indexA !in items.indices || indexB !in items.indices || indexA == indexB) return items
    return items.toMutableList().apply {
        val tmp = this[indexA]
        this[indexA] = this[indexB]
        this[indexB] = tmp
    }
}

internal fun removeApiKeyItem(
    items: List<EditableApiKeyItem>,
    index: Int
): List<EditableApiKeyItem> {
    if (index !in items.indices) return items
    return items.toMutableList().apply { removeAt(index) }
        .ifEmpty { listOf(EditableApiKeyItem()) }
}

/**
 * 单页四段式的分区标题（连接 / 密钥 / 模型 / 其他）。
 *
 * 四段在同一 LazyColumn 里按顺序连续排布，标题只做层级标识与当前摘要，
 * 配合顶部快速跳转栏实现即点即达。
 */
@Composable
private fun ApiConfigSectionHeader(
    index: Int,
    title: String,
    summary: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = EchoTokens.Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.inlineGap)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp)
        )
    }
}

@Composable
private fun ApiConfigSectionNavBar(
    activeSection: ApiConfigSection,
    onSelectSection: (ApiConfigSection) -> Unit
) {
    val glass = echoGlassPalette()
    val themeStyle = LocalEchoThemeStyle.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = EchoGlassSubPanelShape,
        color = glass.panel,
        border = BorderStroke(if (themeStyle.strongOutline) 1.6.dp else 0.8.dp, glass.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EchoTokens.Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.xs)
        ) {
            ApiConfigSection.entries.forEach { section ->
                val selected = section == activeSection
                Surface(
                    onClick = { onSelectSection(section) },
                    shape = EchoGlassSubPanelShape,
                    color = if (selected) glass.controlSelected else Color.Transparent,
                    border = if (selected) BorderStroke(1.dp, glass.outlineSelected) else null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = EchoTokens.TouchTarget.compact)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else glass.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiDiscoveredModelRow(
    entry: ApiModelDiscoveryEntry,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (entry.isUnavailable) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.model,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.isUnavailable) {
                    Text(
                        "可能已失效 · 本次列表未返回",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    val capability = remember(entry.model) { ModelCapabilityEngine.evaluateModel(entry.model) }
                    val details = listOfNotNull(
                        capability.contextWindowDisplay.takeIf { it.isNotBlank() },
                        "视觉".takeIf { capability.supportsVision },
                        "工具".takeIf { capability.supportsTools },
                        "思考".takeIf { capability.supportsReasoning }
                    ).joinToString(" · ")
                    if (details.isNotBlank()) {
                        Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (entry.isAdded) {
                Text("已添加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onRemove, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("移除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                TextButton(onClick = onAdd, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("添加", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * API 配置编辑页入口：configId > 0 编辑已有配置，0 新建。
 * 页面自行加载配置并负责落库，避免长表单被弹窗高度压缩。
 */
@Composable
fun ApiConfigEditorScreen(
    configId: Long,
    onBack: () -> Unit
) {
    val repository = AiAssistantApp.instance.repository
    var config by remember(configId) { mutableStateOf<ApiConfig?>(null) }
    var loading by remember(configId) { mutableStateOf(configId > 0L) }
    var missing by remember(configId) { mutableStateOf(false) }
    LaunchedEffect(configId) {
        if (configId > 0L) {
            val loaded = repository.getApiConfigById(configId)
            config = loaded
            missing = loaded == null
        }
        loading = false
    }
    when {
        loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        missing -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("API 配置不存在或已被删除", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onBack) { Text("返回") }
        }
        else -> ApiConfigEditorContent(config = config, initialConfigId = configId, onBack = onBack)
    }
}

@Composable
private fun ApiConfigEditorContent(
    config: ApiConfig?,
    initialConfigId: Long,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = AiAssistantApp.instance.repository
    val context = androidx.compose.ui.platform.LocalContext.current
    val gson = remember { Gson() }
    var isSaving by remember { mutableStateOf(false) }
    var persistedConfigId by remember { mutableLongStateOf(initialConfigId) }
    // 连接 / 密钥 / 模型三段在同一条纵向滚动里占据连续的层级，共用同一个滚动位置。
    val pageListState = androidx.compose.foundation.lazy.rememberLazyListState()

    var isConfigEnabled by remember { mutableStateOf(config?.isEnabled ?: true) }
    var name by remember { mutableStateOf(config?.name ?: "") }
    var provider by remember { mutableStateOf(config?.provider ?: "") }
    var baseUrl by remember { mutableStateOf(config?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(config?.apiKey ?: "") }
    var editableKeys by remember {
        val initialList = if (!config?.apiKey.isNullOrBlank()) {
            AiRepository.parseNamedApiKeys(config!!.apiKey)
        } else emptyList()
        mutableStateOf((initialList.ifEmpty { listOf(NamedApiKey()) }).map { EditableApiKeyItem(namedKey = it) })
    }
    var visibleKeyIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val applyEditableKeys: (List<EditableApiKeyItem>) -> Unit = { updated ->
        editableKeys = updated
        apiKey = AiRepository.formatNamedApiKeys(updated.map { it.namedKey })
    }
    val keyReorderState = rememberSmoothReorderState()
    val modelReorderState = rememberSmoothReorderState()
    var apiType by remember { mutableStateOf(config?.apiType ?: "openai") }
    val rotationManager = AiAssistantApp.instance.personalizationManager.apiKeyRotationManager
    val rotationAddress = remember(baseUrl, apiType) { runCatching { ApiEndpointHelper.normalizeBaseUrl(baseUrl, apiType) } }
    val rotationScopeKey = "${apiType.lowercase(java.util.Locale.ROOT)}|${rotationAddress.getOrNull() ?: baseUrl.trim()}"
    val loadedRotationSettings = remember(config?.id, rotationScopeKey) {
        runCatching { rotationManager.getSettings(baseUrl, apiType) }
    }
    // 该地址与协议是否已有用户保存过的策略：策略按地址共享，已有策略必须原样载入。
    val hasStoredRotationSettings = remember(config?.id, rotationScopeKey) {
        runCatching { rotationManager.hasSettings(baseUrl, apiType) }.getOrDefault(false)
    }
    var rotationDraft by remember(config?.id, rotationScopeKey) {
        mutableStateOf(
            ApiKeyRotationDraft(
                initialRotationSettings(
                    loaded = if (hasStoredRotationSettings) loadedRotationSettings.getOrNull() else null,
                    isNewConfig = initialConfigId <= 0L
                )
            )
        )
    }
    var resettingKeyHealth by remember(rotationScopeKey) { mutableStateOf(false) }
    var keyHealthResetFeedback by remember(rotationScopeKey) { mutableStateOf<Pair<Boolean, String>?>(null) }
    var modelName by remember { mutableStateOf(cleanModelName(config?.modelName) ?: "") }
    var availableModels by remember {
        mutableStateOf(parseModelList(config?.availableModels).ifEmpty {
            cleanModelName(config?.modelName)?.let { listOf(it) } ?: emptyList()
        })
    }
    var enabledModelNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var modelCapabilities by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var modelCustomSettings by remember { mutableStateOf<Map<String, ModelCustomSettings>>(emptyMap()) }
    // 地址、协议或 Key 改变后丢弃旧清单，避免把另一个接口的模型误标为失效。
    var keyModels by remember(baseUrl, apiType, apiKey) { mutableStateOf<List<String>?>(null) }
    var keyModelFetchFailed by remember(baseUrl, apiType, apiKey) { mutableStateOf(false) }
    var keyModelSearchQuery by remember { mutableStateOf("") }
    var customModelInput by remember { mutableStateOf("") }
    var modelSearchQuery by remember { mutableStateOf("") }
    var isConfiguredModelsExpanded by rememberSaveable { mutableStateOf(true) }
    val filteredModels = remember(availableModels, modelSearchQuery) {
        if (modelSearchQuery.isBlank()) availableModels
        else availableModels.filter { it.contains(modelSearchQuery.trim(), ignoreCase = true) }
    }
    // 搜索过滤时禁用拖拽，避免 filtered 下标与完整列表错位
    val canReorderModels = modelSearchQuery.isBlank() && availableModels.size > 1
    var isLoadingModels by remember(baseUrl, apiType, apiKey) { mutableStateOf(false) }
    val configuredModelNames = cleanModelNames(availableModels + modelName)
    val discoveredModels = remember(configuredModelNames, keyModels, keyModelSearchQuery) {
        apiModelDiscoveryEntries(configuredModelNames, keyModels, keyModelSearchQuery)
    }
    val removeConfiguredModel: (String) -> Unit = { removed ->
        availableModels = availableModels - removed
        enabledModelNames = enabledModelNames - removed
        modelCapabilities = modelCapabilities - removed
        modelCustomSettings = modelCustomSettings - removed
        if (modelName == removed) {
            modelName = availableModels.firstOrNull { it in enabledModelNames }
                ?: availableModels.firstOrNull().orEmpty()
        }
    }
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
                editableKeys = (parsed.ifEmpty { listOf(NamedApiKey()) }).map { EditableApiKeyItem(namedKey = it) }
                visibleKeyIds = emptySet()
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

    val canSave = rotationAddress.isSuccess && loadedRotationSettings.isSuccess &&
        apiKey.isNotBlank() && cleanModelName(modelName) != null &&
        rotationDraft.validationMessage == null && !isSaving && !resettingKeyHealth

    // 页面自己完成落库：先保证配置本体保存，再同步模型清单、头像与轮询设置。
    val performSave: () -> Unit = {
        val rotationSettings = rotationDraft.toSettingsOrNull()
        if (rotationSettings != null && canSave) {
            isSaving = true
            scope.launch {
                try {
                    val cleanedCurrentModel = cleanModelName(modelName).orEmpty()
                    val modelNames = cleanModelNames(availableModels + cleanedCurrentModel)
                    val enabledModels = (enabledModelNames + modelName)
                        .mapNotNull { cleanModelName(it) }
                        .toSet()
                    // 重试保存时沿用已落库配置，编辑既有配置时完整保留采样/思考/联网/停止词等未在本页修改的参数。
                    val persisted = if (persistedConfigId > 0L) repository.getApiConfigById(persistedConfigId) else null
                    val baseConfig = persisted ?: config
                    val now = System.currentTimeMillis()
                    val configToSave = (baseConfig ?: ApiConfig(
                        name = name.ifBlank { provider },
                        provider = provider,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        apiType = apiType,
                        modelName = cleanedCurrentModel,
                        maxTokens = 8192,
                        temperature = 0.95f,
                        topP = 1.0f,
                        enableThinking = true,
                        thinkingEffort = "medium",
                        enableWebSearch = false,
                        createdAt = now,
                        updatedAt = now
                    )).copy(
                        id = persistedConfigId,
                        name = name.ifBlank { provider },
                        provider = provider,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        apiType = apiType,
                        modelName = cleanedCurrentModel,
                        availableModels = modelNames.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
                        isEnabled = isConfigEnabled,
                        updatedAt = now
                    )
                    val savedId = repository.saveApiConfig(configToSave)
                    persistedConfigId = savedId
                    if (modelNames.isNotEmpty()) {
                        repository.replaceSelectedModels(
                            apiConfigId = savedId,
                            modelNames = modelNames,
                            enabledModelNames = enabledModels,
                            modelCapabilities = modelCapabilities,
                            modelSettings = modelCustomSettings
                        )
                    }
                    if (clearApiAvatar) {
                        AvatarManager.deleteApiModelAvatar(context, savedId)
                    }
                    selectedApiAvatarUri?.let { uri ->
                        AvatarManager.saveApiModelAvatarFromUri(context, savedId, uri)
                    }
                    val rotationSaved = withContext(Dispatchers.IO) {
                        runCatching {
                            AiAssistantApp.instance.personalizationManager.apiKeyRotationManager.saveSettings(
                                baseUrl, apiType, rotationSettings
                            )
                        }.getOrDefault(false)
                    }
                    if (rotationSaved) {
                        onBack()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "API 配置已保存，但轮询设置保存失败。请点击“保存”重试。",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "保存失败: ${e.message ?: "未知异常"}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    isSaving = false
                }
            }
        }
    }

    // 保存中禁止返回，避免落库与页面销毁竞争。
    BackHandler(enabled = !isSaving) { onBack() }

    // 全屏页沿用设置页的壁纸、纸纹与悬浮玻璃顶栏，避免“弹窗放大”感。
    val pageBackgroundBitmap = remember(context) { BackgroundImageManager.getHomeBackgroundBitmap(context) }
    var isDiscoveredModelsExpanded by rememberSaveable(config?.id ?: 0L) { mutableStateOf(true) }
    val showDiscoveryEmpty = keyModels != null && discoveredModels.isEmpty()
    val visibleDiscoveredModels = if (isDiscoveredModelsExpanded) discoveredModels else emptyList()
    val showDiscoveryEmptyVisible = isDiscoveredModelsExpanded && showDiscoveryEmpty
    val sectionIndices = remember(visibleDiscoveredModels.size, showDiscoveryEmptyVisible) {
        apiConfigSectionIndices(visibleDiscoveredModels.size, showDiscoveryEmptyVisible)
    }
    var pendingJumpSection by remember { mutableStateOf<ApiConfigSection?>(null) }
    val scrollActiveSection by remember(sectionIndices) {
        derivedStateOf {
            resolveActiveApiConfigSection(
                firstVisibleIndex = pageListState.firstVisibleItemIndex,
                canScrollForward = pageListState.canScrollForward,
                sectionIndices = sectionIndices
            )
        }
    }
    val activeSection = pendingJumpSection ?: scrollActiveSection

    EchoScaffold(
        title = if (config == null) "添加 API 配置" else "编辑 API 配置",
        subtitle = "连接、密钥、模型与其他",
        backgroundBitmap = pageBackgroundBitmap,
        navigationIcon = {
            IconButton(onClick = { if (!isSaving) onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = performSave, enabled = canSave) {
                    Text("保存")
                }
            }
        }
    ) { pagePadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pagePadding)
                .padding(echoContentInset(base = 16.dp))
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.sm)
        ) {
            ApiConfigSectionNavBar(
                activeSection = activeSection,
                onSelectSection = { section ->
                    val targetIndex = sectionIndices[section] ?: 0
                    pendingJumpSection = section
                    scope.launch {
                        try {
                            pageListState.animateScrollToItem(targetIndex)
                        } finally {
                            pendingJumpSection = null
                        }
                    }
                }
            )
            LazyColumn(
                state = pageListState,
                verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.cardGap),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    top = EchoTokens.Spacing.xs,
                    bottom = EchoTokens.Spacing.sectionGap
                )
            ) {
                // ========== 1. 连接 ==========
                item(key = ApiConfigSection.CONNECTION.key) {
                    ApiConfigSectionHeader(
                        index = ApiConfigSection.CONNECTION.index,
                        title = ApiConfigSection.CONNECTION.label,
                        summary = "启用状态、接口类型与服务地址"
                    )
                }
                item(key = "api_connection_card") {
                    SettingsGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("启用此 API 配置", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (isConfigEnabled) "已开启：允许使用此配置并在模型列表展示" else "已关闭：停用此配置且不在模型列表展示",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            EchoSwitch(
                                checked = isConfigEnabled,
                                onCheckedChange = { isConfigEnabled = it },
                                size = EchoSwitchSize.Standard
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Text("接口预设与协议", style = MaterialTheme.typography.titleMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "快速预设",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                presets.forEach { (key, value) ->
                                    AssistChip(
                                        onClick = {
                                            provider = value.third
                                            baseUrl = value.first
                                            modelName = value.second
                                            if (name.isBlank()) name = value.third
                                            apiType = if (key == "anthropic") "anthropic" else "openai"
                                        },
                                        label = { Text(value.third, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "API 类型",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Text("连接信息", style = MaterialTheme.typography.titleMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val fontScale = LocalDensity.current.fontScale
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    maxItemsInEachRow = if (maxWidth < (360 * fontScale).dp) 1 else 2
                                ) {
                                    SettingsInputField(
                                        title = "配置名称",
                                        value = name,
                                        onValueChange = { name = it },
                                        placeholder = "如：主用节点",
                                        modifier = Modifier.weight(1f)
                                    )
                                    SettingsInputField(
                                        title = "提供商",
                                        value = provider,
                                        onValueChange = { provider = it },
                                        placeholder = "OpenAI / DeepSeek",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            SettingsInputField(
                                title = "Base URL",
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                placeholder = "https://api.example.com/v1"
                            )
                        }
                    }
                }

                // ========== 2. 密钥 ==========
                val validKeysCount = editableKeys.count { it.namedKey.key.isNotBlank() }
                item(key = ApiConfigSection.KEYS.key) {
                    ApiConfigSectionHeader(
                        index = ApiConfigSection.KEYS.index,
                        title = ApiConfigSection.KEYS.label,
                        summary = "已录入 $validKeysCount 个密钥 · 拖动左侧手柄或点按箭头排序"
                    )
                }
                item(key = "api_keys_card") {
                    val keySpacingPx = with(LocalDensity.current) { 10.dp.toPx() }
                    SettingsGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "API Key 列表",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (validKeysCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    val enabledCount = editableKeys.count { it.namedKey.key.isNotBlank() && it.namedKey.isEnabled }
                                    Text(
                                        text = if (enabledCount < validKeysCount) "$enabledCount/$validKeysCount 启用" else "$validKeysCount 个可用",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (rotationDraft.settings.enabled)
                                "已开启多 Key 轮询（可在“其他”调整策略）。每个输入框填写一个 Key，支持拖动或箭头调整基础顺序。"
                            else "轮询已关闭：调用时固定使用列表中第一个已启用的 Key。如需多 Key 轮询，请在底部“其他”中开启。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            editableKeys.forEachIndexed { index, item ->
                                key(item.id) {
                                    val currentKey = item.namedKey
                                    val isVisible = item.id in visibleKeyIds
                                    val isKeyEnabled = currentKey.isEnabled
                                    val isActive = keyReorderState.isItemActive(item.id)

                                    Surface(
                                        shape = RoundedCornerShape(EchoTokens.Radius.md),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isKeyEnabled) 0.28f else 0.14f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            else if (isKeyEnabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .alpha(if (isKeyEnabled) 1f else 0.65f)
                                            .reorderItem(
                                                keyReorderState,
                                                item.id,
                                                shape = RoundedCornerShape(EchoTokens.Radius.md),
                                                spacingPx = keySpacingPx
                                            )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (editableKeys.size > 1) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent)
                                                            .reorderDragHandle(
                                                                state = keyReorderState,
                                                                key = { item.id },
                                                                keys = { editableKeys.map { it.id } },
                                                                onMove = { fromIdx, toIdx ->
                                                                    applyEditableKeys(moveApiKeyItem(editableKeys, fromIdx, toIdx))
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
                                                        text = "Key ${index + 1}",
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
                                                        val currentIdx = editableKeys.indexOfFirst { it.id == item.id }
                                                        if (currentIdx >= 0) {
                                                            val updated = editableKeys.toMutableList()
                                                            updated[currentIdx] = updated[currentIdx].copy(
                                                                namedKey = updated[currentIdx].namedKey.copy(name = newName)
                                                            )
                                                            applyEditableKeys(updated)
                                                        }
                                                    },
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Normal
                                                    ),
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
                                                                    text = "备注 (可选)",
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
                                                        val currentIdx = editableKeys.indexOfFirst { it.id == item.id }
                                                        if (currentIdx >= 0) {
                                                            val updated = editableKeys.toMutableList()
                                                            updated[currentIdx] = updated[currentIdx].copy(
                                                                namedKey = updated[currentIdx].namedKey.copy(isEnabled = checked)
                                                            )
                                                            applyEditableKeys(updated)
                                                        }
                                                    }
                                                )

                                                if (editableKeys.size > 1) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                val currentIdx = editableKeys.indexOfFirst { it.id == item.id }
                                                                if (currentIdx > 0) {
                                                                    val targetId = editableKeys[currentIdx - 1].id
                                                                    keyReorderState.onAnimateSwap(item.id, targetId, currentIdx, currentIdx - 1)
                                                                    applyEditableKeys(swapApiKeyItems(editableKeys, currentIdx, currentIdx - 1))
                                                                }
                                                            },
                                                            enabled = index > 0,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowUpward,
                                                                contentDescription = "提升优先级",
                                                                tint = if (index > 0) MaterialTheme.colorScheme.primary
                                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                val currentIdx = editableKeys.indexOfFirst { it.id == item.id }
                                                                if (currentIdx in 0 until editableKeys.lastIndex) {
                                                                    val targetId = editableKeys[currentIdx + 1].id
                                                                    keyReorderState.onAnimateSwap(item.id, targetId, currentIdx, currentIdx + 1)
                                                                    applyEditableKeys(swapApiKeyItems(editableKeys, currentIdx, currentIdx + 1))
                                                                }
                                                            },
                                                            enabled = index < editableKeys.lastIndex,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowDownward,
                                                                contentDescription = "降低优先级",
                                                                tint = if (index < editableKeys.lastIndex) MaterialTheme.colorScheme.primary
                                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                val currentIdx = editableKeys.indexOfFirst { it.id == item.id }
                                                                if (currentIdx >= 0) {
                                                                    visibleKeyIds = visibleKeyIds - item.id
                                                                    applyEditableKeys(removeApiKeyItem(editableKeys, currentIdx))
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp)
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
                                            }

                                            OutlinedTextField(
                                                value = currentKey.key,
                                                onValueChange = { newVal ->
                                                    val currentIdx = editableKeys.indexOfFirst { it.id == item.id }
                                                    if (currentIdx < 0) return@OutlinedTextField
                                                    val parsed = AiRepository.parseNamedApiKeys(newVal)
                                                    val updated = editableKeys.toMutableList()
                                                    if (parsed.size > 1) {
                                                        updated.removeAt(currentIdx)
                                                        updated.addAll(currentIdx, parsed.map { EditableApiKeyItem(namedKey = it) })
                                                    } else if (parsed.size == 1 && (parsed[0].name.isNotBlank() || parsed[0].key != newVal.trim())) {
                                                        val existingName = updated[currentIdx].namedKey.name.ifBlank { parsed[0].name }
                                                        updated[currentIdx] = updated[currentIdx].copy(
                                                            namedKey = NamedApiKey(
                                                                name = existingName,
                                                                key = parsed[0].key,
                                                                isEnabled = updated[currentIdx].namedKey.isEnabled
                                                            )
                                                        )
                                                    } else {
                                                        updated[currentIdx] = updated[currentIdx].copy(
                                                            namedKey = updated[currentIdx].namedKey.copy(key = newVal.trim())
                                                        )
                                                    }
                                                    applyEditableKeys(updated)
                                                },
                                                placeholder = { Text("填写密钥 (sk-...)", style = MaterialTheme.typography.bodyMedium) },
                                                visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(EchoTokens.Radius.md),
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
                                                            visibleKeyIds = if (isVisible) visibleKeyIds - item.id else visibleKeyIds + item.id
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
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                applyEditableKeys(editableKeys + EditableApiKeyItem())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(EchoTokens.Radius.md),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("添加独立密钥 (Key ${editableKeys.size + 1})", style = MaterialTheme.typography.labelMedium)
                        }

                        Text(
                            text = "多行或逗号分隔的密钥粘贴后会自动拆分为独立条目。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ========== 3. 模型 ==========
                item(key = ApiConfigSection.MODELS.key) {
                    ApiConfigSectionHeader(
                        index = ApiConfigSection.MODELS.index,
                        title = ApiConfigSection.MODELS.label,
                        summary = "从 Key 拉取模型、默认模型与已配置清单（已添加 ${availableModels.size} 个）"
                    )
                }
                item(key = "model_discovery_header") {
                    SettingsGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = keyModels?.let { "从 Key 读取的模型 (${it.size})" } ?: "从 Key 读取模型",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                val unadded = keyModels.orEmpty().filter { it !in configuredModelNames }
                                if (unadded.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            availableModels = cleanModelNames(availableModels + unadded)
                                            enabledModelNames = enabledModelNames + unadded
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("一键添加全部 (${unadded.size})", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            if (keyModels != null) {
                                IconButton(
                                    onClick = { isDiscoveredModelsExpanded = !isDiscoveredModelsExpanded },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDiscoveredModelsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isDiscoveredModelsExpanded) "收起读取结果" else "展开读取结果",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val requestedUrl = baseUrl
                                val requestedKey = apiKey
                                val requestedType = apiType
                                scope.launch {
                                    isLoadingModels = true
                                    keyModelFetchFailed = false
                                    keyModels = null
                                    try {
                                        val result = repository.fetchAvailableModelsDirect(requestedUrl, requestedKey, requestedType)
                                        if (baseUrl == requestedUrl && apiKey == requestedKey && apiType == requestedType) {
                                            result.onSuccess { models ->
                                                val cleaned = cleanModelNames(models)
                                                keyModels = cleaned
                                                isDiscoveredModelsExpanded = true
                                                val updated = modelCustomSettings.toMutableMap()
                                                cleaned.forEach { model ->
                                                    if (!updated.containsKey(model)) {
                                                        val eval = ModelCapabilityEngine.evaluateModel(model)
                                                        updated[model] = ModelCustomSettings(
                                                            contextWindowTokens = eval.contextWindowTokens,
                                                            supportsTools = eval.supportsTools,
                                                            supportsVision = eval.supportsVision,
                                                            supportsThinking = eval.supportsReasoning,
                                                            supportsWebSearch = true
                                                        )
                                                    }
                                                }
                                                modelCustomSettings = updated
                                                if (modelName.isBlank() && cleaned.isNotEmpty()) modelName = cleaned.first()
                                            }.onFailure { keyModelFetchFailed = true }
                                        }
                                    } finally {
                                        isLoadingModels = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && !isLoadingModels && !isSaving
                        ) {
                            if (isLoadingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (keyModels == null) "点击从 API Key 读取模型列表" else "重新读取 Key 模型列表")
                        }
                        if (keyModelFetchFailed) {
                            Text(
                                "读取失败，请检查地址、Key 和网络后重试；已有模型不会被判为失效。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (keyModels != null && isDiscoveredModelsExpanded) {
                            val missingCount = apiModelDiscoveryEntries(configuredModelNames, keyModels).count { it.isUnavailable }
                            if (missingCount > 0) {
                                Text(
                                    "可能已失效的已添加模型 ($missingCount) 已置顶。本次列表未返回这些模型，手动模型也可能不在接口清单中；可移除或保留继续使用。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedTextField(
                                value = keyModelSearchQuery,
                                onValueChange = { keyModelSearchQuery = it },
                                placeholder = { Text("搜索已添加及本次读取的模型…", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = if (keyModelSearchQuery.isNotBlank()) {
                                    {
                                        IconButton(onClick = { keyModelSearchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "清除模型搜索", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                shape = RoundedCornerShape(EchoTokens.Radius.md),
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (keyModels != null && !isDiscoveredModelsExpanded) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDiscoveredModelsExpanded = true },
                                shape = RoundedCornerShape(EchoTokens.Radius.md),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "已折叠 (${discoveredModels.size} 个读取条目)，点击展开",
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
                items(visibleDiscoveredModels, key = { "discovered_model:${it.model}" }) { entry ->
                    ApiDiscoveredModelRow(
                        entry = entry,
                        enabled = !isSaving,
                        onRemove = { removeConfiguredModel(entry.model) },
                        onAdd = {
                            availableModels = cleanModelNames(availableModels + entry.model)
                            enabledModelNames = enabledModelNames + entry.model
                            if (modelName.isBlank()) modelName = entry.model
                        }
                    )
                }
                if (showDiscoveryEmptyVisible) {
                    item(key = "model_discovery_empty") {
                        Text(
                            if (keyModelSearchQuery.isBlank()) "本次未返回可添加的模型。" else "没有匹配的模型，试试其他关键词。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item(key = "api_default_and_configured_models") {
                    val modelSpacingPx = with(LocalDensity.current) { 8.dp.toPx() }
                    SettingsGlassCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsInputField(
                                title = "默认模型",
                                value = modelName,
                                onValueChange = { modelName = it },
                                placeholder = "例如: deepseek-v4-pro / gpt-6-astra"
                            )
                            Text(
                                text = "新对话选中此 API 时默认优先使用该模型；可从下方列表点选切换。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isConfiguredModelsExpanded = !isConfiguredModelsExpanded }
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
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
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
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    TextButton(
                                        onClick = { enabledModelNames = availableModels.toSet() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("全选", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick = { enabledModelNames = emptySet() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("清空启用", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick = { modelCustomSettings = emptyMap() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("重置能力", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

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
                                                text = "暂无已配置模型。可在上方从 Key 读取模型，或在此手动添加。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    } else {
                                        if (availableModels.size > 1) {
                                            Text(
                                                text = if (modelSearchQuery.isBlank()) {
                                                    "按住模型左侧手柄上下拖动可调整展示顺序。"
                                                } else {
                                                    "搜索过滤时暂不可拖动排序，清空搜索后可调整顺序。"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
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
                                                shape = RoundedCornerShape(EchoTokens.Radius.md),
                                                textStyle = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        val modelsToShow = if (canReorderModels) availableModels else filteredModels
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            modelsToShow.forEach { model ->
                                                key(model) {
                                                    val isModelDragActive = canReorderModels && modelReorderState.isItemActive(model)
                                                    ModelCustomSettingCard(
                                                        model = model,
                                                        checked = enabledModelNames.contains(model),
                                                        selected = modelName == model,
                                                        capability = modelCapabilities[model] ?: "auto",
                                                        customSettings = modelCustomSettings[model],
                                                        reorderState = if (canReorderModels) modelReorderState else null,
                                                        reorderKey = model,
                                                        reorderKeys = { availableModels.toList() },
                                                        reorderSpacingPx = modelSpacingPx,
                                                        onReorderMove = { fromIdx, toIdx ->
                                                            if (fromIdx in availableModels.indices && toIdx in availableModels.indices && fromIdx != toIdx) {
                                                                val updated = availableModels.toMutableList()
                                                                val movedModel = updated.removeAt(fromIdx)
                                                                updated.add(toIdx, movedModel)
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
                                                            removeConfiguredModel(model)
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
                                }
                            }
                            if (!isConfiguredModelsExpanded && availableModels.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isConfiguredModelsExpanded = true },
                                    shape = RoundedCornerShape(EchoTokens.Radius.md),
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
                }

                // ========== 4. 其他 ==========
                item(key = ApiConfigSection.OTHER.key) {
                    ApiConfigSectionHeader(
                        index = ApiConfigSection.OTHER.index,
                        title = ApiConfigSection.OTHER.label,
                        summary = "模型对话头像与多 Key 轮询策略"
                    )
                }
                item(key = "api_other_avatar_card") {
                    SettingsGlassCard {
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
                item(key = "api_other_rotation_card") {
                    SettingsGlassCard {
                        ApiKeyRotationSettingsSection(
                            scopeKey = rotationScopeKey,
                            draft = rotationDraft,
                            onDraftChange = { rotationDraft = it },
                            enabled = !isSaving && loadedRotationSettings.isSuccess,
                            canResetHealth = rotationAddress.isSuccess,
                            resettingHealth = resettingKeyHealth,
                            resetFeedback = keyHealthResetFeedback,
                            embeddedInCard = true,
                            onResetHealth = {
                                val targetBaseUrl = baseUrl
                                val targetApiType = apiType
                                resettingKeyHealth = true
                                keyHealthResetFeedback = null
                                scope.launch {
                                    val success = withContext(Dispatchers.IO) {
                                        runCatching { rotationManager.resetHealth(targetBaseUrl, targetApiType) }.getOrDefault(false)
                                    }
                                    keyHealthResetFeedback = success to if (success) "已清除该地址的 Key 健康记录。" else "重置失败，请重试。"
                                    resettingKeyHealth = false
                                }
                            }
                        )
                    }
                }
            }
            if (rotationDraft.validationMessage != null) {
                Text(
                    text = "轮询设置有无效数字，请到“其他”分区修正后保存：${rotationDraft.validationMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (baseUrl.isNotBlank() && rotationAddress.isFailure) {
                Text(
                    text = rotationAddress.exceptionOrNull()?.message ?: "请填写有效的 API 地址。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (rotationAddress.isSuccess && loadedRotationSettings.isFailure) {
                Text(
                    text = "轮询设置读取失败，请重新打开此 API 配置后重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

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
    reorderKey: Any = model,
    reorderKeys: () -> List<Any> = { listOf(model) },
    reorderSpacingPx: Float = 0f,
    onReorderMove: (Int, Int) -> Unit = { _, _ -> },
    isDragActive: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val cardShape = SettingsInnerShape
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

    val baseModifier = Modifier.fillMaxWidth()
    val dragModifier = if (reorderState != null) {
        baseModifier.reorderItem(
            reorderState,
            reorderKey,
            shape = cardShape,
            spacingPx = reorderSpacingPx
        )
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
                                key = { reorderKey },
                                keys = reorderKeys,
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
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
