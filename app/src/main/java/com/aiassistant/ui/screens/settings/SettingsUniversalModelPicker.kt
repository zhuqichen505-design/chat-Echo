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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.RectangleShape
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

@Composable
fun UniversalModelPickerCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    title: String,
    subtitle: String,
    selectedConfigId: Long,
    selectedModel: String,
    allConfigs: List<ApiConfig>,
    onSelect: (configId: Long, model: String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var manualInputMode by remember { mutableStateOf(false) }
    var manualModelName by remember { mutableStateOf(selectedModel) }
    var manualConfigId by remember { mutableLongStateOf(if (selectedConfigId > 0L) selectedConfigId else allConfigs.firstOrNull()?.id ?: 0L) }
    val repository = AiAssistantApp.instance.repository
    val glass = echoGlassPalette()

    // 读取与聊天对话页完全一致的全部已启用模型列表 (跨服务商)
    var visibleChatOptions by remember { mutableStateOf<List<ChatModelOption>>(emptyList()) }
    LaunchedEffect(allConfigs) {
        visibleChatOptions = repository.getAllVisibleChatModelOptions()
    }

    val currentConfig = remember(selectedConfigId, allConfigs) {
        if (selectedConfigId > 0L) allConfigs.find { it.id == selectedConfigId }
        else allConfigs.find { it.isDefault } ?: allConfigs.firstOrNull()
    }

    val displayTitle = remember(selectedConfigId, selectedModel, currentConfig) {
        if (selectedConfigId == 0L && selectedModel.isBlank()) {
            "跟随当前会话模型（自动继承）"
        } else if (selectedModel.isNotBlank()) {
            com.aiassistant.domain.model.ModelDisplayName.format(selectedModel)
        } else {
            currentConfig?.modelName?.let(com.aiassistant.domain.model.ModelDisplayName::format)?.ifBlank { "默认模型" } ?: "未指定模型"
        }
    }

    val displayBadge = remember(selectedConfigId, selectedModel, currentConfig) {
        if (selectedConfigId == 0L && selectedModel.isBlank()) {
            "默认推荐"
        } else {
            currentConfig?.let { it.name.ifBlank { it.provider } } ?: "未绑定"
        }
    }

    val allChoices = remember(allConfigs, visibleChatOptions) {
        val list = mutableListOf<FullModelChoice>()
        // 1. 优先加入所有在 API 配置中启用的模型 (和对话页完全一致)
        visibleChatOptions.forEach { opt ->
            list.add(
                FullModelChoice(
                    configId = opt.apiConfigId,
                    configName = opt.configName.ifBlank { opt.provider },
                    provider = opt.provider,
                    modelName = opt.modelName,
                    isDefault = false
                )
            )
        }
        // 2. 补充服务商默认模型和 availableModels 中尚未添加的模型
        allConfigs.forEach { cfg ->
            val cfgName = cfg.name.ifBlank { cfg.provider }
            val defaultModel = cleanModelName(cfg.modelName)
            if (!defaultModel.isNullOrBlank() && list.none { it.configId == cfg.id && it.modelName == defaultModel }) {
                list.add(FullModelChoice(cfg.id, cfgName, cfg.provider, defaultModel, isDefault = true))
            }
            val parsed = parseModelList(cfg.availableModels)
            parsed.forEach { m ->
                if (m.isNotBlank() && list.none { it.configId == cfg.id && it.modelName == m }) {
                    list.add(FullModelChoice(cfg.id, cfgName, cfg.provider, m, isDefault = false))
                }
            }
        }
        list.distinctBy { "${it.configId}:${it.modelName}" }
    }

    val filteredChoices = remember(allChoices, searchQuery) {
        if (searchQuery.isBlank()) allChoices
        else allChoices.filter {
            it.modelName.contains(searchQuery.trim(), ignoreCase = true) ||
            it.configName.contains(searchQuery.trim(), ignoreCase = true) ||
            it.provider.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    val groupedChoices = remember(filteredChoices) {
        filteredChoices.groupBy { "${it.configName} (${it.provider})" }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsInnerShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, glass.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = displayBadge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 主下拉按键：向下展开
            OutlinedButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth(),
                shape = SettingsInnerShape,
                border = BorderStroke(
                    1.dp,
                    if (isExpanded) MaterialTheme.colorScheme.primary else glass.outline
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起模型列表" else "展开模型列表",
                        tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 向下直接展开列表
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = glass.control,
                    border = BorderStroke(1.dp, glass.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (manualInputMode) {
                            // 手动输入模式
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("自定义手动输入模型名称：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                BasicTextField(
                                    value = manualModelName,
                                    onValueChange = { manualModelName = it },
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
                                            if (manualModelName.isEmpty()) {
                                                Text(
                                                    text = "例如：gpt-4o-mini 或 deepseek-chat",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("绑定服务商：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                allConfigs.forEach { cfg ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { manualConfigId = cfg.id }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = manualConfigId == cfg.id,
                                            onClick = { manualConfigId = cfg.id }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${cfg.name.ifBlank { cfg.provider }} (${cfg.provider})",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { manualInputMode = false }) {
                                        Text("返回列表", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(
                                        onClick = {
                                            if (manualModelName.isNotBlank()) {
                                                onSelect(manualConfigId, manualModelName.trim())
                                                isExpanded = false
                                                manualInputMode = false
                                            }
                                        },
                                        enabled = manualModelName.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                    ) {
                                        Text("确认选择", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        } else {
                            // 搜索栏 (高度 40dp，匀称美观)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, glass.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    "搜索模型名称或服务商...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "清除",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // 滚动模型列表
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 1. 跟随当前会话模型
                                item {
                                    val isFollowSelected = selectedConfigId == 0L && selectedModel.isBlank()
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                onSelect(0L, "")
                                                isExpanded = false
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isFollowSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                        border = if (isFollowSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.5.dp, glass.outline.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "跟随当前会话模型（自动继承）",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isFollowSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isFollowSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    "按每个会话各自绑定的模型与配置自动调用",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isFollowSelected) {
                                                Icon(Icons.Default.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                // 2. 分组显示所有服务商与模型
                                groupedChoices.forEach { (groupTitle, choices) ->
                                    item {
                                        Text(
                                            text = groupTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
                                        )
                                    }
                                    items(choices) { choice ->
                                        val isSelected = selectedConfigId == choice.configId &&
                                            (selectedModel == choice.modelName || (selectedModel.isBlank() && choice.isDefault))
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onSelect(choice.configId, choice.modelName)
                                                    isExpanded = false
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = com.aiassistant.domain.model.ModelDisplayName.format(choice.modelName),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (choice.isDefault) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                                            ) {
                                                                Text(
                                                                    "默认",
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.secondary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "已选择",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (allChoices.isEmpty()) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                            Text("未检测到已配置的服务商模型，请先在「API配置」中添加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            // 底部操作区
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { manualInputMode = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("自定义输入模型", style = MaterialTheme.typography.labelSmall)
                                }

                                TextButton(
                                    onClick = { isExpanded = false },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("收起", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
