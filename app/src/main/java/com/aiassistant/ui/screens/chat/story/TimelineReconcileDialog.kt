@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.aiassistant.ui.screens.chat.story

import com.aiassistant.ui.screens.chat.*

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
import com.aiassistant.ui.screens.roleplay.ConflictAction
import com.aiassistant.ui.theme.EchoTokens


/**
 * 分类标签色安全解析：草稿/历史数据中的非法颜色值回落默认蓝，绝不闪退。
 */
private fun TimelineCategory.safeTagColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(tagColorHex))
    } catch (_: Exception) {
        Color(0xFF2196F3)
    }
}

@Composable
fun TimelineReconcileDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    initialResult: TimelineReconcileResult,
    onDismiss: () -> Unit,
    onApply: (String, List<TimelineEventItem>, List<AtemporalSettingItem>) -> Unit
) {
    var storyTime by remember(initialResult) { mutableStateOf(initialResult.currentStoryTime) }
    val events = remember(initialResult) {
        mutableStateListOf<TimelineEventItem>().apply {
            addAll(initialResult.events.map { it.copy() })
        }
    }
    val atemporalSettings = remember(initialResult) {
        mutableStateListOf<AtemporalSettingItem>().apply {
            addAll(initialResult.atemporalSettings.map { it.copy() })
        }
    }

    // 筛选状态
    var selectedTimeFilter by remember { mutableStateOf("全部") }
    var selectedCategoryFilter by remember { mutableStateOf<TimelineCategory?>(null) }

    // 提取所有出现过的独立时间标签集合
    val distinctTimeTags = remember(events.size, events.map { it.timeTag }) {
        val tags = mutableListOf("全部")
        events.map { it.timeTag.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { tag ->
                if (!tags.contains(tag)) tags.add(tag)
            }
        tags
    }

    // 过滤后的事件列表
    val filteredEvents = remember(events.toList(), selectedTimeFilter, selectedCategoryFilter) {
        events.filter { event ->
            val matchTime = (selectedTimeFilter == "全部") ||
                    (event.timeTag.trim() == selectedTimeFilter) ||
                    (event.timeTag.contains(selectedTimeFilter))
            val matchCat = (selectedCategoryFilter == null) || (event.category == selectedCategoryFilter)
            matchTime && matchCat
        }
    }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.98f)
            .widthIn(max = 620.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.HistoryEdu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "时间轴与多维设定工作台",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "已提炼 ${events.size} 条时间节点 · ${atemporalSettings.size} 条全局设定",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 0. 模型提炼来源与状态指示
                when (initialResult.extractionSource) {
                    "AI_MODEL" -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (initialResult.modelUsed.isNotBlank()) {
                                        "✨ AI 大模型智慧深度提炼完成（模型：${initialResult.modelUsed}）"
                                    } else {
                                        "✨ AI 大模型智慧深度提炼完成"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    "SAVED_DRAFT" -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "📁 已加载本地保存的时间线梳理草稿（支持编辑与确认入库，或稍后接着进度继续梳理）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    "LIVE_PROGRESS" -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "👁️ 实时梳理快照预览（后台仍可继续梳理，草稿已自动实时留存）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val modelHint = if (initialResult.modelUsed.isNotBlank()) "调用模型：${initialResult.modelUsed} | " else ""
                                val errorDetail = initialResult.extractionErrorMessage.orEmpty()
                                val timeoutHint = if (errorDetail.contains("timeout", ignoreCase = true) || errorDetail.contains("timed out", ignoreCase = true)) {
                                    "全文较长且模型推理响应超时，可再次点击重新梳理或在「设置 -> 辅助模型」选用推理更快的模型"
                                } else {
                                    errorDetail.ifBlank { "未检测到模型响应" }
                                }
                                Text(
                                    text = "⚠️ 模型响应未成功（$modelHint$timeoutHint），当前显示本地精纯扫描。可在「设置 -> 辅助模型」指定独立模型或检查当前网络。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // 1. 顶部当前故事时间编辑卡片
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "当前故事停留在：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        BasicTextField(
                            value = storyTime,
                            onValueChange = { storyTime = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (storyTime.isEmpty()) {
                                        Text(
                                            text = "例如：第5天·上午",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. 筛选控制栏 (时间过滤 Chips + 类别过滤 Chips)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 时间过滤 Chips (当筛选固有设定时隐藏时间过滤)
                    if (selectedCategoryFilter != TimelineCategory.ATEMPORAL_SETTING && distinctTimeTags.size > 2) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(distinctTimeTags) { tag ->
                                val isSelected = (selectedTimeFilter == tag)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedTimeFilter = tag },
                                    label = {
                                        Text(
                                            if (tag == "全部") "🌟 全部时间线" else tag,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }

                    // 类别过滤 Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = (selectedCategoryFilter == null),
                                onClick = { selectedCategoryFilter = null },
                                label = { Text("全部类别", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        items(TimelineCategory.values()) { cat ->
                            val isSelected = (selectedCategoryFilter == cat)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedCategoryFilter = if (isSelected) null else cat
                                },
                                label = {
                                    Text("${cat.emoji} ${cat.displayName}", style = MaterialTheme.typography.labelSmall)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = cat.safeTagColor().copy(alpha = 0.22f),
                                    selectedLabelColor = cat.safeTagColor()
                                )
                            )
                        }
                    }
                }

                // 3. 核心滚动区 (包含时间无关设定确认卡片 + 垂直时间轴事件流)
                val showAtemporalSection = (selectedCategoryFilter == null && selectedTimeFilter == "全部") ||
                        (selectedCategoryFilter == TimelineCategory.ATEMPORAL_SETTING)
                val showTimelineEvents = selectedCategoryFilter != TimelineCategory.ATEMPORAL_SETTING

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // A. 与时间无关设定确认卡片 (Atemporal Settings Section)
                    if (showAtemporalSection && (atemporalSettings.isNotEmpty() || selectedCategoryFilter == TimelineCategory.ATEMPORAL_SETTING)) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "💡 世界观与角色固有设定（时间无关）",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    for (i in atemporalSettings.indices) {
                                                        atemporalSettings[i] = atemporalSettings[i].copy(isSelected = true)
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("全选", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                                            }
                                            TextButton(
                                                onClick = {
                                                    for (i in atemporalSettings.indices) {
                                                        atemporalSettings[i] = atemporalSettings[i].copy(isSelected = false)
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("全不选", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                                            }
                                        }
                                    }

                                    Text(
                                        text = "模型通读识别出以下不随具体剧情天数变化的常驻设定与规则。请确认勾选需要同步保存的条目：",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    atemporalSettings.forEachIndexed { sIdx, setting ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // 第一行：方框、设定性质、应用范围和删除键放在同一行
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = setting.isSelected,
                                                        onCheckedChange = { checked ->
                                                            val idx = atemporalSettings.indexOfFirst { it.id == setting.id }
                                                            if (idx != -1) {
                                                                atemporalSettings[idx] = setting.copy(isSelected = checked)
                                                            }
                                                        },
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))

                                                    // 设定性质（类别切换胶囊）
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                                        modifier = Modifier.clickable {
                                                            val idx = atemporalSettings.indexOfFirst { it.id == setting.id }
                                                            if (idx != -1) {
                                                                atemporalSettings[idx] = setting.copy(category = setting.nextCategory())
                                                            }
                                                        }
                                                    ) {
                                                        Text(
                                                            text = setting.category,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.tertiary,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.weight(1f))

                                                    // 目标范围切换按钮
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                                        modifier = Modifier.clickable {
                                                            val idx = atemporalSettings.indexOfFirst { it.id == setting.id }
                                                            if (idx != -1) {
                                                                val nextScope = if (setting.targetScope == "global") "session" else "global"
                                                                atemporalSettings[idx] = setting.copy(targetScope = nextScope)
                                                            }
                                                        }
                                                    ) {
                                                        Text(
                                                            text = if (setting.targetScope == "global") "范围: 全局" else "范围: 会话",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(4.dp))

                                                    // 删除按钮
                                                    IconButton(
                                                        onClick = {
                                                            val idx = atemporalSettings.indexOfFirst { it.id == setting.id }
                                                            if (idx != -1) {
                                                                atemporalSettings.removeAt(idx)
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.DeleteOutline,
                                                            contentDescription = "删除",
                                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                // 第二行：文字放在下一行
                                                BasicTextField(
                                                    value = setting.content,
                                                    onValueChange = { newContent ->
                                                        val idx = atemporalSettings.indexOfFirst { it.id == setting.id }
                                                        if (idx != -1) {
                                                            atemporalSettings[idx] = setting.copy(content = newContent)
                                                        }
                                                    },
                                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.tertiary),
                                                    decorationBox = { innerTextField ->
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(
                                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                                                    RoundedCornerShape(6.dp)
                                                                )
                                                                .border(
                                                                    BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                                                    RoundedCornerShape(6.dp)
                                                                )
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            if (setting.content.isEmpty()) {
                                                                Text(
                                                                    text = "设定描述（如畏寒、不加糖黑咖啡）...",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                                )
                                                            }
                                                            innerTextField()
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    // 手动补充全局设定
                                    OutlinedButton(
                                        onClick = {
                                            atemporalSettings.add(
                                                AtemporalSettingItem(
                                                    category = "角色设定",
                                                    content = "",
                                                    isSelected = true,
                                                    targetScope = "session"
                                                )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("＋ 手动补充全局设定", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    if (showTimelineEvents) {
                        // B. 编年史事件列表标题
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "时间轴发展脉络 (${filteredEvents.size}/${events.size}条)：",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selectedTimeFilter != "全部" || selectedCategoryFilter != null) {
                                    TextButton(
                                        onClick = {
                                            selectedTimeFilter = "全部"
                                            selectedCategoryFilter = null
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("重置筛选", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        // 垂直时间轴事件流 (Timeline Events Stream)
                        itemsIndexed(filteredEvents) { idx, item ->
                            val isLast = (idx == filteredEvents.size - 1)
                            val catColor = item.category.safeTagColor()
                            val relativeTime = remember(item.timeTag, storyTime) {
                                TimelineMemoryHelper.calculateRelativeTime(item.timeTag, storyTime)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                // 垂直流线与发光节点列
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(26.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(catColor.copy(alpha = 0.22f), CircleShape)
                                            .border(2.dp, catColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(catColor, CircleShape)
                                        )
                                    }

                                    if (!isLast) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(72.dp)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            catColor.copy(alpha = 0.65f),
                                                            catColor.copy(alpha = 0.15f)
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // 事件编辑卡片
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                    border = BorderStroke(1.dp, catColor.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(bottom = 6.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // 头部操作行：类别切换胶囊 + 时间标签修改 + 相对时间距离徽章 + 删除
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 类别胶囊 (点击循环切换分类)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = catColor.copy(alpha = 0.16f),
                                                modifier = Modifier.clickable {
                                                    val all = TimelineCategory.values()
                                                    val nextIdx = (all.indexOf(item.category) + 1) % all.size
                                                    val idxInMaster = events.indexOfFirst { it.id == item.id }
                                                    if (idxInMaster != -1) {
                                                        events[idxInMaster] = item.copy(category = all[nextIdx])
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${item.category.emoji} ${item.category.displayName}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = catColor,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            // 时间标签直接编辑输入框
                                            BasicTextField(
                                                value = item.timeTag,
                                                onValueChange = { newTag ->
                                                    val idxInMaster = events.indexOfFirst { it.id == item.id }
                                                    if (idxInMaster != -1) {
                                                        events[idxInMaster] = item.copy(timeTag = newTag)
                                                    }
                                                },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.labelSmall.copy(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                decorationBox = { innerTextField ->
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                                RoundedCornerShape(6.dp)
                                                            )
                                                            .border(
                                                                BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                                                RoundedCornerShape(6.dp)
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        if (item.timeTag.isEmpty()) {
                                                            Text(
                                                                text = "如：第1天·傍晚",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )

                                            // 相对时间距离胶囊
                                            if (!relativeTime.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = relativeTime,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }

                                            // 删除按钮
                                            IconButton(
                                                onClick = {
                                                    val idx = events.indexOfFirst { it.id == item.id }
                                                    if (idx != -1) {
                                                        events.removeAt(idx)
                                                    }
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "删除该条",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        // 正文多行直接编辑输入框
                                        BasicTextField(
                                            value = item.content,
                                            onValueChange = { newContent ->
                                                val idx = events.indexOfFirst { it.id == item.id }
                                                if (idx != -1) {
                                                    events[idx] = item.copy(content = newContent)
                                                }
                                            },
                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .border(
                                                            BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    contentAlignment = Alignment.TopStart
                                                ) {
                                                    if (item.content.isEmpty()) {
                                                        Text(
                                                            text = "输入在此时间节点发生的客观剧情事实或确立的设定规则...",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 补充遗漏事件按钮
                        item {
                            OutlinedButton(
                                onClick = {
                                    events.add(
                                        TimelineEventItem(
                                            timeTag = if (selectedTimeFilter != "全部") selectedTimeFilter else (storyTime.ifBlank { "第 1 天" }),
                                            content = "",
                                            category = if (selectedCategoryFilter != null && selectedCategoryFilter != TimelineCategory.ATEMPORAL_SETTING) selectedCategoryFilter!! else TimelineCategory.PLOT_EVENT
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("＋ 手动添加时间节点事件", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        buttons = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 顶部统计胶囊指示条
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "时间节点 ${events.size} 条",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "全局设定 ${atemporalSettings.count { it.isSelected }}/${atemporalSettings.size} 条",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Text(
                            text = storyTime.ifBlank { "未指定时间" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 底部操作胶囊行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 放弃按钮 (轻量液态玻璃胶囊)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .height(44.dp)
                            .clickable { onDismiss() }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "放弃",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 保存并同步到记忆按钮 (高级 Echo 渐变流动光泽立体胶囊)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable {
                                val validEvents = events.filter { it.content.isNotBlank() }
                                val confirmedSettings = atemporalSettings.filter { it.isSelected && it.content.isNotBlank() }
                                onApply(storyTime, validEvents, confirmedSettings)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
                                        )
                                    )
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.BookmarkAdded,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "保存时间线与独立设定",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
