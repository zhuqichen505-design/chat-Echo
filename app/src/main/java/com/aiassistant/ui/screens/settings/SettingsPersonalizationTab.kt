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
import com.aiassistant.ui.components.EchoSwitch
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

// 说明：本文件是设置模块的共享组件库（MemoryItemCard/WorldBook*/PromptTemplate*/BackgroundPickerRow 等），
// 被 SettingsPromptsMemoryTab / SettingsAppearanceTab / SettingsModelFeaturesTab 复用。
// 原 PersonalizationTab 包装函数已随 personalization 死路由一并删除（T-6）。

@Composable
fun MemoryItemCard(
    memory: MemoryItem,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val glass = echoGlassPalette()
    val scopeLabel = when (memory.scope) {
        "user", "global" -> "全局偏好"
        "conversation" -> "会话专属"
        else -> memory.scope
    }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val timeText = remember(memory.updatedAt) { dateFormat.format(Date(memory.updatedAt)) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsInnerShape,
        color = if (memory.isEnabled) glass.control else glass.control.copy(alpha = 0.4f),
        contentColor = if (memory.isEnabled) glass.textPrimary else glass.textPrimary.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, glass.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = scopeLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑记忆",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除记忆",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))
                EchoSwitch(
                    checked = memory.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (memory.isEnabled) glass.textPrimary else glass.textPrimary.copy(alpha = 0.6f)
            )

            if (!memory.keywords.isNullOrBlank()) {
                Text(
                    text = "关键词: ${memory.keywords}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun WorldBookCardItem(
    book: WorldBook,
    onToggleEnabled: (Boolean) -> Unit,
    onManageEntries: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val glass = echoGlassPalette()
    val repository = AiAssistantApp.instance.repository
    val entries by repository.getWorldBookEntries(book.id).collectAsState(initial = emptyList())

    Surface(
        shape = SettingsInnerShape,
        color = if (book.isEnabled) glass.control else glass.control.copy(alpha = 0.4f),
        contentColor = if (book.isEnabled) glass.textPrimary else glass.textPrimary.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, glass.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (book.isEnabled) glass.textPrimary else glass.textPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${entries.size} 条设定",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    EchoSwitch(
                        checked = book.isEnabled,
                        onCheckedChange = onToggleEnabled
                    )
                }
            }

            if (book.description.isNotBlank()) {
                Text(
                    text = book.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!book.tags.isNullOrBlank()) {
                    Text(
                        text = "标签: ${book.tags}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                FilledTonalButton(
                    onClick = onManageEntries,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("管理词条 (${entries.size})", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun WorldBookEditDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    book: WorldBook?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, tags: String) -> Unit
) {
    var name by remember { mutableStateOf(book?.name.orEmpty()) }
    var description by remember { mutableStateOf(book?.description.orEmpty()) }
    var tags by remember { mutableStateOf(book?.tags.orEmpty()) }

    EchoGlassDialog(
        hazeState = hazeState,
        title = { Text(if (book == null) "新建世界书" else "编辑世界书") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("世界书名称", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("例如：赛博朋克 2077、奇幻大陆编年史...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsInnerShape
                )

                Text("世界观简述 (可选)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("概括世界观核心冲突、时代背景或核心规则...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsInnerShape
                )

                Text("标签分类 (可选)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    placeholder = { Text("逗号分隔，例如：科幻, 赛博朋克, 设定集") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsInnerShape
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description, tags) },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        onDismissRequest = onDismiss
    )
}

@Composable
fun WorldBookEntriesManageDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    book: WorldBook,
    onDismiss: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (WorldBookEntry) -> Unit,
    onDeleteEntry: (WorldBookEntry) -> Unit
) {
    val repository = AiAssistantApp.instance.repository
    val entries by repository.getWorldBookEntries(book.id).collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val glass = echoGlassPalette()

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("《${book.name}》词条管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("命中关键词即刻将设定动态注入上下文", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAddEntry) {
                    Icon(Icons.Default.Add, contentDescription = "添加词条", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 450.dp)) {
                if (entries.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("当前世界书暂无词条设定", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onAddEntry) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加首个设定词条")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries) { entry ->
                            Surface(
                                shape = SettingsInnerShape,
                                color = if (entry.isEnabled) glass.control else glass.control.copy(alpha = 0.35f),
                                contentColor = if (entry.isEnabled) glass.textPrimary else glass.textPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            if (entry.isConstant) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        "常驻",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    "优先级:${entry.priority}",
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { onEditEntry(entry) }, modifier = Modifier.size(26.dp)) {
                                                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(14.dp))
                                            }
                                            IconButton(onClick = { onDeleteEntry(entry) }, modifier = Modifier.size(26.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                            }
                                            EchoSwitch(
                                                checked = entry.isEnabled,
                                                onCheckedChange = { en ->
                                                    coroutineScope.launch {
                                                        repository.setWorldBookEntryEnabled(entry.id, en)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    if (entry.keys.isNotBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            entry.getKeyList().forEach { key ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = key,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = entry.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (entry.isEnabled) glass.textPrimary else glass.textPrimary.copy(alpha = 0.6f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun WorldBookEntryEditDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    entry: WorldBookEntry?,
    bookId: Long,
    onDismiss: () -> Unit,
    onConfirm: (name: String, keys: String, content: String, isConstant: Boolean, priority: Int) -> Unit
) {
    var name by remember { mutableStateOf(entry?.name.orEmpty()) }
    var keys by remember { mutableStateOf(entry?.keys.orEmpty()) }
    var content by remember { mutableStateOf(entry?.content.orEmpty()) }
    var isConstant by remember { mutableStateOf(entry?.isConstant ?: false) }
    var priorityText by remember { mutableStateOf((entry?.priority ?: 10).toString()) }

    EchoGlassDialog(
        hazeState = hazeState,
        title = { Text(if (entry == null) "添加设定词条" else "编辑设定词条") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("词条名称", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("例如：以太灵素、深渊裂隙、铁心重工...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsInnerShape
                )

                Text("触发关键词", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = keys,
                    onValueChange = { keys = it },
                    placeholder = { Text("多个关键词用逗号分隔，例如：以太, 灵素, 魔法") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsInnerShape
                )

                Text("词条设定内容", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("详细阐述该名词的背景、规则、属性或设定细节...") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsInnerShape
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("常驻词条设定", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("开启后无需命中关键词，也会默认注入会话上下文", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    EchoSwitch(checked = isConstant, onCheckedChange = { isConstant = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("优先级权重 (数值越大越优先注入)", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = priorityText,
                        onValueChange = { priorityText = it.filter { ch -> ch.isDigit() }.take(3) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                        shape = SettingsInnerShape
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prio = priorityText.toIntOrNull() ?: 10
                    onConfirm(name, keys, content, isConstant, prio)
                },
                enabled = name.isNotBlank() && content.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        onDismissRequest = onDismiss
    )
}

@Composable
fun AuxiliaryMemoryTestDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    testInput: String,
    onInputChange: (String) -> Unit,
    isTesting: Boolean,
    testResult: String?,
    onRunTest: () -> Unit,
    onDismiss: () -> Unit
) {
    EchoGlassDialog(
        hazeState = hazeState,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("辅助模型提炼与平滑降级验证", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("测试对话文本", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = testInput,
                    onValueChange = onInputChange,
                    placeholder = { Text("输入包含个人偏好、重要事实或剧情背景的测试对话...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SettingsInnerShape
                )

                if (isTesting) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在调用辅助模型识别提炼（若失败将自动触发本地规则兜底）...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                testResult?.let { result ->
                    val isFallback = result.contains("降级") || result.contains("本地规则")
                    Surface(
                        shape = SettingsInnerShape,
                        color = if (isFallback) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isFallback) Icons.Default.Info else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isFallback) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isFallback) "【已触发本地规则兜底保障】" else "【辅助模型识别成功】",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFallback) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isFallback) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRunTest,
                enabled = !isTesting && testInput.isNotBlank()
            ) {
                Text(if (isTesting) "正在识别..." else "开始测试识别")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        onDismissRequest = onDismiss
    )
}

@Composable
fun MemoryEditDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    memory: MemoryItem?,
    onDismiss: () -> Unit,
    onConfirm: (content: String, scope: String, keywords: String) -> Unit
) {
    var content by remember { mutableStateOf(memory?.content.orEmpty()) }
    var scope by remember { mutableStateOf(memory?.scope ?: "user") }
    var keywords by remember { mutableStateOf(memory?.keywords.orEmpty()) }

    val isUserScope = scope == "user" || scope == "global"
    val isConvScope = scope == "conversation"

    EchoGlassDialog(
        hazeState = hazeState,
        title = { Text(if (memory == null) "添加长期记忆" else "编辑长期记忆") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("记忆内容", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    placeholder = { Text("例如：用户习惯用 Kotlin 编写 Android 应用...") },
                    minLines = 3,
                    maxLines = 8,
                    shape = SettingsInnerShape
                )

                Text("作用域范围", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isUserScope,
                        onClick = { scope = "user" },
                        label = { Text("全局偏好 (user)") },
                        colors = echoFilterChipColors(),
                        border = echoFilterChipBorder(isUserScope)
                    )
                    FilterChip(
                        selected = isConvScope,
                        onClick = { scope = "conversation" },
                        label = { Text("会话专属 (conversation)") },
                        colors = echoFilterChipColors(),
                        border = echoFilterChipBorder(isConvScope)
                    )
                }

                Text("关联关键词 (可选)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("以逗号分隔，例如：kotlin, android, 开发") },
                    singleLine = true,
                    shape = SettingsInnerShape
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(content, scope, keywords) },
                enabled = content.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        onDismissRequest = onDismiss
    )
}

@Composable
fun PriorityRuleRow(
    badge: String,
    title: String,
    description: String,
    badgeColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsInnerShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PromptTemplateItemCard(
    template: PromptTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val glass = echoGlassPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsInnerShape,
        color = glass.control,
        border = BorderStroke(1.dp, glass.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = template.category.ifBlank { "general" },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
            if (!template.description.isNullOrBlank()) {
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = template.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun PromptTemplateEditDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    template: PromptTemplate?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, content: String, description: String?, category: String) -> Unit
) {
    var name by remember { mutableStateOf(template?.name.orEmpty()) }
    var category by remember { mutableStateOf(template?.category.orEmpty().ifBlank { "general" }) }
    var description by remember { mutableStateOf(template?.description.orEmpty()) }
    var content by remember { mutableStateOf(template?.content.orEmpty()) }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = { Text(if (template == null) "新建提示词模板" else "编辑提示词模板") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("模板名称") },
                    placeholder = { Text("例如: 代码重构专家 / 故事剧情续写") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类 (可选)") },
                    placeholder = { Text("例如: 创作 / 编程 / 翻译 / 效率") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("简介说明 (可选)") },
                    placeholder = { Text("简述模板用途与触发时机") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("模板正文 (支持 {{变量}} 占位符)") },
                    placeholder = { Text("输入提示词内容，使用 {{变量}} 作为插值占位符...") },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), content.trim(), description.trim().takeIf { it.isNotBlank() }, category.trim().ifBlank { "general" }) },
                enabled = name.isNotBlank() && content.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun EnvironmentVariableItemCard(
    variable: EnvironmentVariable,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val glass = echoGlassPalette()
    var isRevealed by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsInnerShape,
        color = glass.control,
        border = BorderStroke(1.dp, glass.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "{{${variable.name}}}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { isRevealed = !isRevealed }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isRevealed) "隐藏" else "查看",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
            if (!variable.description.isNullOrBlank()) {
                Text(
                    text = variable.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (isRevealed) variable.value else "••••••••••••",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun EnvironmentVariableEditDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    variable: EnvironmentVariable?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, value: String, description: String?) -> Unit
) {
    var name by remember { mutableStateOf(variable?.name.orEmpty()) }
    var value by remember { mutableStateOf(variable?.value.orEmpty()) }
    var description by remember { mutableStateOf(variable?.description.orEmpty()) }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = { Text(if (variable == null) "添加环境变量" else "编辑环境变量") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("变量名 (KEY)") },
                    placeholder = { Text("例如: USER_NAME / PROJECT_NAME") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("变量值 (VALUE)") },
                    placeholder = { Text("实际注入的变量文本或密钥") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述说明 (可选)") },
                    placeholder = { Text("说明该变量在何处使用") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), value.trim(), description.trim().takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank() && value.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun BackgroundPickerRow(
    title: String,
    hasImage: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    val glass = echoGlassPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsInnerShape,
        color = glass.control,
        contentColor = glass.textPrimary,
        border = androidx.compose.foundation.BorderStroke(1.dp, glass.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (hasImage) Icons.Default.CheckCircle else Icons.Default.Wallpaper,
                contentDescription = null,
                tint = if (hasImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (hasImage) "已使用自定义图片" else "使用默认纯色背景",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onPick) {
                Text(if (hasImage) "更换" else "选择")
            }
            if (hasImage) {
                TextButton(onClick = onClear) {
                    Text("恢复")
                }
            }
        }
    }
}

@Composable
fun PersonalizationTextField(
    title: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp),
            placeholder = { Text(placeholder) },
            minLines = 3,
            maxLines = 8,
            shape = SettingsInnerShape
        )
    }
}

