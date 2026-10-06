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
import com.aiassistant.ui.screens.roleplay.ConflictAction
import com.aiassistant.ui.theme.EchoTokens


@Composable
fun ChatSettingsTimelineSection(
    timelineNodes: List<TimelineNode>,
    currentStoryTime: String?,
    contentColor: Color,
    secondaryColor: Color,
    enableTimeline: Boolean = true,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    isReconcilingTimeline: Boolean = false,
    reconcileTimelineProgress: String? = null,
    hasSavedTimelineDraft: Boolean = false,
    checkpoint: TimelineReconcileCheckpoint? = null,
    newMessagesCountSinceCheckpoint: Int = 0,
    onStartTimelineReconciliation: () -> Unit = {},
    onContinueTimelineReconciliation: () -> Unit = {},
    onReconcileFromCheckpoint: () -> Unit = {},
    onOpenTimelineDraft: () -> Unit = {},
    onClearTimelineDraft: () -> Unit = {},
    onCancelTimelineReconciliation: () -> Unit = {},
    onUpdateCurrentStoryTime: (String?) -> Unit,
    onAddTimelineNode: (timeTag: String, event: String, category: String) -> Unit,
    onUpdateTimelineNode: (TimelineNode) -> Unit,
    onDeleteTimelineNode: (Long) -> Unit,
    onClearTimeline: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var nodeToEdit by remember { mutableStateOf<TimelineNode?>(null) }
    var showStoryTimeEditDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    var addTimeTag by remember { mutableStateOf("") }
    var addEventText by remember { mutableStateOf("") }
    var addCategory by remember { mutableStateOf(TimelineCategory.PLOT_EVENT) }

    var editTimeTag by remember { mutableStateOf("") }
    var editEventText by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf(TimelineCategory.PLOT_EVENT) }

    var storyTimeInput by remember { mutableStateOf("") }
    var isTimelineExpanded by remember { mutableStateOf(true) }

    val glass = echoGlassPalette()
    val primaryColor = MaterialTheme.colorScheme.primary
    val sortedNodes = remember(timelineNodes) {
        timelineNodes.sortedWith(compareBy<TimelineNode> { it.orderIndex }.thenBy { it.createdAt })
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
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.HistoryEdu,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            "🕒 故事时间线 (Timeline)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Text(
                            "独立时序存放 · 关键事件与时间节点 · 支持编辑",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryColor
                        )
                    }
                }
            }

            // 当前故事推进节点展示与就地编辑（满足需求 1）
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = primaryColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (!currentStoryTime.isNullOrBlank()) "当前时空节点：$currentStoryTime" else "当前时空节点：未设定",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = {
                            storyTimeInput = currentStoryTime.orEmpty()
                            showStoryTimeEditDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("修改节点", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 需求 4：存在先前梳理草稿提示卡片（可查看/应用或断点续梳）
            if (hasSavedTimelineDraft && !isReconcilingTimeline) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "存在先前梳理草稿",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            TextButton(
                                onClick = onClearTimelineDraft,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("丢弃", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            text = "可直接查看并应用已保存草稿，或接着上次进度继续向下梳理。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenTimelineDraft,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("查看草稿", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = onContinueTimelineReconciliation,
                                modifier = Modifier.weight(1.2f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("继续向下梳理", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // 梳理检查点水线（满足需求 1：记录上次梳理到的对话节点并支持结合原有时间线梳理后续）
            if (checkpoint != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "上次梳理断点",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "已梳理至第 ${checkpoint.lastReconciledMessageIndex} 条对话",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (newMessagesCountSinceCheckpoint > 0) {
                            Text(
                                text = "断点后产生 $newMessagesCountSinceCheckpoint 条后续新增对话（包含期间自动记录的增量节点），推荐结合已确认的基准时间线进行整体优化梳理：",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                            Button(
                                onClick = onReconcileFromCheckpoint,
                                enabled = enableTimeline && !isReconcilingTimeline,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("结合原有时间线梳理后续 (推荐)", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Text(
                                text = "时间线已与全部历史对话完全同步（暂无断点后新增对话）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }

            // 操作栏：梳理全量时间线、添加节点、清空时间线
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isReconcilingTimeline) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenTimelineDraft,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("实时查看", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onCancelTimelineReconciliation,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                reconcileTimelineProgress ?: "暂停/保存",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onStartTimelineReconciliation,
                        enabled = enableTimeline,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (checkpoint != null || hasSavedTimelineDraft) "全量重新梳理" else "全量梳理与校对", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            addTimeTag = currentStoryTime?.takeIf { it.isNotBlank() } ?: "第 1 天"
                            addEventText = ""
                            addCategory = TimelineCategory.PLOT_EVENT
                            showAddDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        enabled = enableTimeline
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("＋ 节点", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    if (sortedNodes.isNotEmpty() || !currentStoryTime.isNullOrBlank()) {
                        TextButton(
                            onClick = { showClearConfirmDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            enabled = enableTimeline,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("清空", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }

            // 节点列表展示与折叠
            if (sortedNodes.isEmpty()) {
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
                            text = "时间线暂无节点数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryColor
                        )
                        OutlinedButton(
                            onClick = {
                                addTimeTag = currentStoryTime?.takeIf { it.isNotBlank() } ?: "第 1 天"
                                addEventText = ""
                                addCategory = TimelineCategory.PLOT_EVENT
                                showAddDialog = true
                            },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.6f)),
                            enabled = enableTimeline
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ 手动添加首个时间节点", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isTimelineExpanded = !isTimelineExpanded }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "时间节点编年表 (${sortedNodes.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isTimelineExpanded) "收起" else "展开",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = if (isTimelineExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isTimelineExpanded) "收起" else "展开",
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isTimelineExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sortedNodes.forEachIndexed { index, node ->
                            val cat = TimelineCategory.fromKey(node.category)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enableTimeline) 0.45f else 0.22f),
                                border = BorderStroke(1.dp, glass.outline.copy(alpha = if (enableTimeline) 0.5f else 0.25f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // 顶部行：序号、时间点与事件性质（上下排布）、编辑与删除键放在同一行
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. 左侧序号
                                        Surface(
                                            shape = CircleShape,
                                            color = primaryColor.copy(alpha = 0.2f),
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${index + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = primaryColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // 2. 时间点与事件性质上下排布
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = primaryColor.copy(alpha = 0.15f),
                                                border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = node.timeTag.ifBlank { "未知时段" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = primaryColor,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = "${cat.emoji} ${cat.displayName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        // 3. 右侧编辑与删除按键在同一行
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    editTimeTag = node.timeTag
                                                    editEventText = node.event
                                                    editCategory = cat
                                                    nodeToEdit = node
                                                },
                                                enabled = enableTimeline,
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
                                                onClick = { onDeleteTimelineNode(node.id) },
                                                enabled = enableTimeline,
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

                                    // 下方展示完整的事件描述
                                    Text(
                                        text = node.event,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (enableTimeline) contentColor else secondaryColor,
                                        modifier = Modifier.fillMaxWidth().padding(start = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 修改当前故事时间弹窗
    if (showStoryTimeEditDialog) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showStoryTimeEditDialog = false },
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp),
            title = {
                Text("修改故事时间推进节点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "设定当前故事推进到的时间点（如：第 1 天·清晨、三天后·黄昏、暑假开始等）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = storyTimeInput,
                        onValueChange = { storyTimeInput = it },
                        placeholder = { Text("输入故事时间节点...", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onUpdateCurrentStoryTime(null)
                            showStoryTimeEditDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("清除设定")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showStoryTimeEditDialog = false }) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                onUpdateCurrentStoryTime(storyTimeInput.trim().takeIf { it.isNotBlank() })
                                showStoryTimeEditDialog = false
                            }
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        )
    }

    // 添加时间节点弹窗
    if (showAddDialog) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showAddDialog = false },
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp),
            title = {
                Text("添加时间线节点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = addTimeTag,
                        onValueChange = { addTimeTag = it },
                        label = { Text("时间标签", style = MaterialTheme.typography.bodySmall) },
                        placeholder = { Text("如：第 1 天·傍晚、次日拂晓", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // 分类选择器
                    Text("事件分类：", style = MaterialTheme.typography.labelSmall, color = secondaryColor)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val categories = listOf(
                            TimelineCategory.PLOT_EVENT,
                            TimelineCategory.CHARACTER_BOND,
                            TimelineCategory.RELATIONSHIP,
                            TimelineCategory.KEY_FACT,
                            TimelineCategory.TURNING_POINT
                        )
                        items(categories) { cat ->
                            FilterChip(
                                selected = addCategory == cat,
                                onClick = { addCategory = cat },
                                label = { Text("${cat.emoji} ${cat.displayName}", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = addEventText,
                        onValueChange = { addEventText = it },
                        label = { Text("关键事件事实（15~40字）", style = MaterialTheme.typography.bodySmall) },
                        placeholder = { Text("主体在何处完成了什么事实...", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp, max = 130.dp),
                        maxLines = 4,
                        shape = RoundedCornerShape(10.dp)
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
                            if (addEventText.isNotBlank()) {
                                onAddTimelineNode(addTimeTag.trim(), addEventText.trim(), addCategory.key)
                                showAddDialog = false
                            }
                        },
                        enabled = addEventText.isNotBlank()
                    ) {
                        Text("添加")
                    }
                }
            }
        )
    }

    // 编辑时间节点弹窗
    nodeToEdit?.let { node ->
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { nodeToEdit = null },
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp),
            title = {
                Text("编辑时间节点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTimeTag,
                        onValueChange = { editTimeTag = it },
                        label = { Text("时间标签", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text("事件分类：", style = MaterialTheme.typography.labelSmall, color = secondaryColor)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val categories = listOf(
                            TimelineCategory.PLOT_EVENT,
                            TimelineCategory.CHARACTER_BOND,
                            TimelineCategory.RELATIONSHIP,
                            TimelineCategory.KEY_FACT,
                            TimelineCategory.TURNING_POINT
                        )
                        items(categories) { cat ->
                            FilterChip(
                                selected = editCategory == cat,
                                onClick = { editCategory = cat },
                                label = { Text("${cat.emoji} ${cat.displayName}", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editEventText,
                        onValueChange = { editEventText = it },
                        label = { Text("关键事件事实", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp, max = 130.dp),
                        maxLines = 4,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { nodeToEdit = null }) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (editEventText.isNotBlank()) {
                                onUpdateTimelineNode(
                                    node.copy(
                                        timeTag = editTimeTag.trim(),
                                        event = editEventText.trim(),
                                        category = editCategory.key,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                nodeToEdit = null
                            }
                        },
                        enabled = editEventText.isNotBlank()
                    ) {
                        Text("保存更新")
                    }
                }
            }
        )
    }

    // 清空时间线确认弹窗
    if (showClearConfirmDialog) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showClearConfirmDialog = false },
            modifier = Modifier.fillMaxWidth(0.88f).widthIn(max = 380.dp),
            title = {
                Text("清空故事时间线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            content = {
                Text("确定要清空当前会话的时间线吗？包括故事推进时间节点和所有历史时间事件记录。此操作无法撤销。", style = MaterialTheme.typography.bodyMedium)
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
                            onClearTimeline()
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

