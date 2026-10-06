@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.aiassistant.ui.screens.chat

import android.net.Uri
import android.graphics.BitmapFactory
import com.aiassistant.ui.components.ImageCropEditDialog
import com.aiassistant.ui.components.CropShapeMode
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import com.aiassistant.domain.model.ToolCallRecord
import com.aiassistant.domain.model.QueuedMessage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.aiassistant.domain.model.TimelineNode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiassistant.utils.TimelineMemoryHelper
import com.aiassistant.utils.TimelineReconcileResult
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineReconcileCheckpoint
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalUriHandler
import com.aiassistant.ui.components.EchoTextToolbar
import com.aiassistant.ui.components.EchoTextToolbarHost
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Brush
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.aiassistant.AiAssistantApp
import com.aiassistant.R
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.Attachment
import com.aiassistant.domain.model.ChatModelOption
import com.aiassistant.domain.model.ConversationContextUsage
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.PromptTemplate
import com.aiassistant.ui.components.MarkdownText
import com.aiassistant.ui.components.SideAnchorItem
import com.aiassistant.ui.components.SideAnchorNavigator
import com.aiassistant.ui.components.TransientLazyListScrollbar
import com.aiassistant.ui.components.EchoPillSlider
import androidx.compose.runtime.CompositionLocalProvider
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.rememberSmoothReorderState
import com.aiassistant.ui.components.reorderItem
import com.aiassistant.ui.components.reorderDragHandle
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoSegmentedButtonBorder
import com.aiassistant.ui.components.echoSegmentedButtonColors
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoHazeSource
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.rememberReadableBackdropColors
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.components.rememberLazyListControlsVisible
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.BackgroundImageManager
import com.aiassistant.utils.FileUtils
import com.aiassistant.utils.RoleplaySmartAnalyzer
import com.aiassistant.utils.RoleplaySmartParser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.NarrativeMode
import com.aiassistant.domain.model.PlotAction
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoPrimaryButton
import com.aiassistant.ui.components.EchoGlassButton
import com.aiassistant.ui.components.EchoSwitch
import com.aiassistant.ui.screens.roleplay.ConflictAction
import com.aiassistant.ui.theme.EchoTokens


@Composable
internal fun ChatSettingsSystemPromptSection(
    promptTextFieldValue: TextFieldValue,
    onPromptChange: (TextFieldValue) -> Unit,
    hasTemplates: Boolean,
    contentColor: Color,
    secondaryColor: Color,
    onChooseTemplate: () -> Unit,
    onSaveTemplate: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showPriorityTip by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showPriorityTip = !showPriorityTip },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "系统提示词优先级说明",
                        tint = if (showPriorityTip) MaterialTheme.colorScheme.primary else secondaryColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("系统提示词", style = MaterialTheme.typography.titleSmall, color = contentColor)
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = if (isExpanded) "缩小输入框" else "放大输入框",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hasTemplates) {
                    TextButton(
                        onClick = onChooseTemplate,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("模板", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (promptTextFieldValue.text.isNotBlank()) {
                    TextButton(
                        onClick = onSaveTemplate,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("存为模板", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPriorityTip,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "【优先级说明】若设置了当前对话的系统提示词，将 100% 覆盖全局系统提示词；若留空则自动继承全局系统提示词。",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor
                    )
                }
            }
        }
        OutlinedTextField(
            value = promptTextFieldValue,
            onValueChange = onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    min = if (isExpanded) 220.dp else 118.dp,
                    max = if (isExpanded) 360.dp else 160.dp
                ),
            placeholder = { Text("例如：你是一个专业、简洁、可靠的助手。") },
            maxLines = if (isExpanded) 16 else 8,
            shape = RoundedCornerShape(14.dp),
            colors = glassTextFieldColors(
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
            )
        )
    }
}

@Composable
fun ChatSettingsSessionMemorySection(
    sessionMemories: List<MemoryItem>,
    contentColor: Color,
    secondaryColor: Color,
    enableSessionMemory: Boolean = true,
    onEnableSessionMemoryChange: (Boolean) -> Unit = {},
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    onAddMemory: (String) -> Unit,
    onUpdateMemory: (MemoryItem) -> Unit,
    onToggleMemory: (Long, Boolean) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearMemories: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var memoryToEdit by remember { mutableStateOf<MemoryItem?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var addMemoryText by remember { mutableStateOf("") }
    var editMemoryText by remember { mutableStateOf("") }
    var isMemoriesExpanded by remember { mutableStateOf(true) }

    val glass = echoGlassPalette()
    val primaryColor = MaterialTheme.colorScheme.primary

    // 彻底过滤掉时间线相关数据，专属记忆卡片仅保留世界观规则与角色常驻设定
    val displayMemories = remember(sessionMemories) {
        sessionMemories.filter {
            !it.content.startsWith("【当前故事时间】：") &&
            !it.content.startsWith("当前故事时间：") &&
            !TimelineMemoryHelper.isExplicitTimelineEvent(it.content)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = glass.control,
        border = BorderStroke(1.dp, glass.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            "会话专属设定与规则",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Text(
                            "角色特征 · 世界观规则 · 行为约束（与时间线物理隔离）",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryColor
                        )
                    }
                }
                EchoSwitch(
                    checked = enableSessionMemory,
                    onCheckedChange = onEnableSessionMemoryChange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (enableSessionMemory) "已生效：发送消息时将附带上述规则约束" else "已停用：发送消息时不附带设定",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            addMemoryText = ""
                            showAddDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        enabled = enableSessionMemory
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("添加设定", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    if (displayMemories.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirmDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            enabled = enableSessionMemory,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("清空", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }

            if (displayMemories.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                    border = BorderStroke(1.dp, glass.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "当前会话暂无自定义设定与规则",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryColor
                        )
                        OutlinedButton(
                            onClick = {
                                addMemoryText = ""
                                showAddDialog = true
                            },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.6f)),
                            enabled = enableSessionMemory
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ 添加第一条专属设定", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isMemoriesExpanded = !isMemoriesExpanded }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "专属设定清单 (${displayMemories.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isMemoriesExpanded) "收起" else "展开",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = if (isMemoriesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isMemoriesExpanded) "收起" else "展开",
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isMemoriesExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        displayMemories.forEach { memory ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enableSessionMemory) 0.45f else 0.22f),
                                border = BorderStroke(1.dp, glass.outline.copy(alpha = if (enableSessionMemory) 0.5f else 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = memory.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (memory.isEnabled && enableSessionMemory) contentColor else secondaryColor,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.padding(start = 2.dp)
                                    ) {
                                        EchoSwitch(
                                            checked = memory.isEnabled && enableSessionMemory,
                                            onCheckedChange = { onToggleMemory(memory.id, it) },
                                            enabled = enableSessionMemory
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    memoryToEdit = memory
                                                    editMemoryText = memory.content
                                                },
                                                enabled = enableSessionMemory,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "编辑",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = secondaryColor
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteMemory(memory.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "删除",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
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
        }
    }

    // 添加设定弹窗
    if (showAddDialog) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showAddDialog = false },
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp),
            title = {
                Text("添加会话专属设定与规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "例如：主角对冰系法术有天然抗性；所有回答保持克制冷峻风格。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = addMemoryText,
                        onValueChange = { addMemoryText = it },
                        placeholder = { Text("输入此会话的专属设定或约束...", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp, max = 140.dp),
                        maxLines = 4,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showAddDialog = false }) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (addMemoryText.isNotBlank()) {
                                onAddMemory(addMemoryText.trim())
                                showAddDialog = false
                            }
                        },
                        enabled = addMemoryText.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            }
        )
    }

    // 编辑设定弹窗
    memoryToEdit?.let { memory ->
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { memoryToEdit = null },
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp),
            title = {
                Text("编辑会话专属设定与规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            content = {
                OutlinedTextField(
                    value = editMemoryText,
                    onValueChange = { editMemoryText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp, max = 140.dp),
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { memoryToEdit = null }) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (editMemoryText.isNotBlank()) {
                                onUpdateMemory(memory.copy(content = editMemoryText.trim()))
                                memoryToEdit = null
                            }
                        },
                        enabled = editMemoryText.isNotBlank()
                    ) {
                        Text("更新")
                    }
                }
            }
        )
    }

    // 清空设定确认弹窗
    if (showClearConfirmDialog) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showClearConfirmDialog = false },
            modifier = Modifier.fillMaxWidth(0.88f).widthIn(max = 380.dp),
            title = {
                Text("清空本会话设定", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            content = {
                Text("确定要清空当前会话的所有专属设定与规则吗？（时间线数据不受影响）", style = MaterialTheme.typography.bodyMedium)
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showClearConfirmDialog = false }) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onClearMemories()
                            showClearConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("确认清空")
                    }
                }
            }
        )
    }
}

@Composable
internal fun ChatSettingsWorldBookAndExternalMemorySection(
    enableExternalMemory: Boolean,
    onEnableExternalMemoryChange: (Boolean) -> Unit,
    enableWorldBook: Boolean,
    onEnableWorldBookChange: (Boolean) -> Unit,
    contentColor: Color,
    secondaryColor: Color,
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "跨会话记忆与世界书 (Lorebook)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // 跨会话长期记忆
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "跨会话长期记忆",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                    Text(
                        text = "跨会话的全局偏好与用户画像，按输入意图和关键词动态检索注入，角色扮演默认严格隔离",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor
                    )
                }
                EchoSwitch(
                    checked = enableExternalMemory,
                    onCheckedChange = onEnableExternalMemoryChange
                )
            }

            // 世界书设定
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "世界书设定 (Lorebook)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                    Text(
                        text = "根据关键词动态唤醒世界观设定与专有名词知识，可在系统设置中管理词条",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor
                    )
                }
                EchoSwitch(
                    checked = enableWorldBook,
                    onCheckedChange = onEnableWorldBookChange
                )
            }
        }
    }
}

@Composable
internal fun glassTextFieldColors(
    contentColor: Color,
    secondaryColor: Color,
    containerColor: Color
) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = contentColor,
    unfocusedTextColor = contentColor,
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    disabledContainerColor = containerColor,
    cursorColor = contentColor,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = secondaryColor,
    focusedPlaceholderColor = secondaryColor,
    unfocusedPlaceholderColor = secondaryColor,
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
    unfocusedBorderColor = secondaryColor.copy(alpha = 0.28f)
)

