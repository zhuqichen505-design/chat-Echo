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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut


internal fun buildChatAnchorItems(displayMessages: List<DisplayMessageItem>): List<SideAnchorItem> {
    return displayMessages.mapIndexedNotNull { index, item ->
        val message = item.message
        if (message.role != "user") return@mapIndexedNotNull null
        SideAnchorItem(
            title = anchorTitle(message.content),
            itemIndex = index
        )
    }
}

internal fun anchorTitle(value: String): String {
    return value
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(28)
        ?: "我的提问"
}

@Composable
internal fun ChatScrollJumpButtons(
    visible: Boolean,
    onJumpToTop: () -> Unit,
    onJumpToPrevInput: () -> Unit,
    onJumpToNextInput: () -> Unit,
    onJumpToBottom: () -> Unit,
    modifier: Modifier = Modifier
) {
    // P2-6 滚动辅助件：悬浮跳转钮弹入（snappy 弹簧缩放）+淡入，退场快收，统一 EchoMotion 曲线；
    // A5：reduced motion 时直接呈现/消失
    val jumpMotionReduced = com.aiassistant.ui.theme.rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        enter = if (jumpMotionReduced) {
            androidx.compose.animation.EnterTransition.None
        } else {
            fadeIn(com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast)) +
                scaleIn(
                    initialScale = 0.8f,
                    animationSpec = com.aiassistant.ui.theme.EchoMotion.Spring.snappy()
                )
        },
        exit = if (jumpMotionReduced) {
            androidx.compose.animation.ExitTransition.None
        } else {
            fadeOut(com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast)) +
                scaleOut(
                    targetScale = 0.8f,
                    animationSpec = com.aiassistant.ui.theme.EchoMotion.tweenSpec(com.aiassistant.ui.theme.EchoMotion.Duration.fast)
                )
        },
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 1. 滑动到顶：双条线向上箭头，极浅天蓝半透明
            Surface(
                onClick = onJumpToTop,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                shadowElevation = 0.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.KeyboardDoubleArrowUp,
                        contentDescription = "滑动到顶",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 2. 滑动到上一条输入：单条线向上箭头，柔和浅蓝半透明
            Surface(
                onClick = onJumpToPrevInput,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                shadowElevation = 0.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "滑动到上一条输入",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 3. 滑动到下一条输入：单条线向下箭头，纯正天蓝半透明
            Surface(
                onClick = onJumpToNextInput,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                shadowElevation = 0.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "滑动到下一条输入",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 4. 滑动到底：双条线向下箭头，蔚蓝半透明
            Surface(
                onClick = onJumpToBottom,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                shadowElevation = 0.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.KeyboardDoubleArrowDown,
                        contentDescription = "滑动到底",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ContextUsageButton(
    usage: ConversationContextUsage?,
    canCompress: Boolean,
    onClick: () -> Unit
) {
    val usagePercent = usage?.usagePercent ?: 0f
    val accent = contextUsageColor(usagePercent)
    val buttonShape = CircleShape

    Surface(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(40.dp)
            .echoShapeClick(buttonShape, onClick = onClick),
        shape = buttonShape,
        color = Color.Transparent,
        contentColor = accent
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ContextUsageRing(
                progress = usagePercent,
                color = accent,
                modifier = Modifier.size(24.dp)
            )
            if (canCompress && usagePercent >= 0.60f) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 6.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                )
            }
        }
    }
}

@Composable
internal fun ContextUsageRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
    Canvas(modifier = modifier) {
        val strokeWidth = 3.5.dp.toPx()
        drawCircle(
            color = trackColor,
            style = Stroke(width = strokeWidth)
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = progress.coerceIn(0f, 1f) * 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
internal fun ContextUsageDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    state: ContextUsageUiState,
    customContextLimit: Int? = null,
    onUpdateContextLimit: ((Int?) -> Unit)? = null,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onCompress: () -> Unit,
    onGenerateRollingSummary: () -> Unit = {},
    onEditRollingSummary: (() -> Unit)? = null,
    onSelectCompressionTier: ((com.aiassistant.domain.model.CompressionTier, Int, Int) -> Unit)? = null,
    onPreviewCompressionSettings: ((Int, Int) -> Unit)? = null
) {
    val usage = state.usage
    // 压缩档位卡片有待确认变更时注册的"确认并生效"动作；右下角"完成"键视同确认
    var pendingTierApply by remember { mutableStateOf<(() -> Unit)?>(null) }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("上下文使用情况", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "模型窗口预算、时间线梳理与上下文压缩管理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        content = {
            if (usage == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        ContextUsageOverview(usage = usage, customLimit = customContextLimit)
                    }
                    item {
                        ContextCompressionTierSelectorCard(
                            usage = usage,
                            onApplyTier = onSelectCompressionTier,
                            onPreviewSettingsChanged = onPreviewCompressionSettings,
                            onRegisterApplyPending = { action -> pendingTierApply = action }
                        )
                    }
                    if (onUpdateContextLimit != null) {
                        item {
                            ContextLimitSettingsCard(
                                currentCustomLimit = customContextLimit,
                                modelContextTokens = usage.modelDefaultContextTokens.takeIf { it > 0 }
                                    ?: usage.contextWindowTokens.takeIf { it > 0 }
                                    ?: 256_000,
                                onUpdateLimit = onUpdateContextLimit
                            )
                        }
                    }
                    item {
                        ContextOptimizationActions(
                            usage = usage,
                            state = state,
                            onGenerateRollingSummary = onGenerateRollingSummary,
                            onEditRollingSummary = onEditRollingSummary,
                            onCompress = onCompress
                        )
                    }
                    item {
                        ContextUsageDetails(
                            usage = usage,
                            onGenerateRollingSummary = onGenerateRollingSummary,
                            onEditRollingSummary = onEditRollingSummary
                        )
                    }
                    item {
                        ContextUsageStatus(
                            usage = usage,
                            statusMessage = state.statusMessage
                        )
                    }
                }
            }
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRefresh,
                    enabled = !state.isCompressing && !state.isGeneratingSummary
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("刷新状态")
                }
                Button(onClick = {
                    // 直接点"完成"：若有待确认的压缩档位/参数变更，视同已确认并生效
                    pendingTierApply?.invoke()
                    pendingTierApply = null
                    onDismiss()
                }) {
                    Text("完成")
                }
            }
        }
    )
}

@Composable
internal fun ContextOptimizationActions(
    usage: ConversationContextUsage,
    state: ContextUsageUiState,
    onGenerateRollingSummary: () -> Unit,
    onEditRollingSummary: (() -> Unit)?,
    onCompress: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 卡片 1：时间线梳理与记忆沉淀
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
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
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "时间线与会话记忆 (Timeline & Memory)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "已沉淀 · ${usage.memoryItemCount} 条记忆",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "过往超出最近未压缩窗口的历史对话，已自动梳理沉淀为时间线节点与会话专属记忆。最近十几次对话（16+ 条）无条件无损保留，剧情自然向前推移，绝不在陈旧过去时间点原地踏步。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onGenerateRollingSummary,
                        enabled = !state.isGeneratingSummary,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (state.isGeneratingSummary) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("梳理中...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("梳理时间线与提炼记忆", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 卡片 2：主动上下文压缩
        val isCompressed = usage.compressedThroughMessageId != null
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
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
                            Icons.Default.Compress,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "主动上下文压缩 (Compression)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isCompressed) "已裁剪至 #${usage.compressedThroughMessageId}" else "尚未压缩",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "【释放 Token 空间】基于时间线梳理与会话专属记忆，将早期历史对话沉淀归档，无条件完整保留最近十几次活跃对话（16+ 条），释放窗口空间同时确保剧情连贯推动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCompress,
                        enabled = !state.isCompressing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (state.isCompressing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("正在压缩...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isCompressed) "重新压缩 / 更新水线" else "立即压缩释放空间", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContextUsageOverview(
    usage: ConversationContextUsage,
    customLimit: Int? = null
) {
    val progress = usage.usagePercent.coerceIn(0f, 1f)
    val accent = contextUsageColor(progress)
    val percentText = "${(progress * 100).toInt().coerceIn(0, 100)}%"
    val contextLimit = usage.contextWindowTokens.takeIf { it > 0 } ?: usage.promptBudgetTokens
    val isDegraded = (customLimit in listOf(32_000, 32_768, 200_000)) && usage.modelDefaultContextTokens > (customLimit ?: 0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "最大上下文限制",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            isDegraded -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            customLimit != null -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        }
                    ) {
                        Text(
                            text = when {
                                isDegraded -> "已降级保护 (${formatTokenCount(customLimit ?: 200_000)})"
                                customLimit != null -> "本会话自定义"
                                else -> "跟随模型默认"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isDegraded -> MaterialTheme.colorScheme.error
                                customLimit != null -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${formatTokenCount(usage.estimatedInputTokens)} / ${formatTokenCount(contextLimit)} tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = percentText,
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
internal fun ContextLimitSettingsCard(
    currentCustomLimit: Int?,
    modelContextTokens: Int,
    onUpdateLimit: (Int?) -> Unit
) {
    var customInput by remember(currentCustomLimit) {
        mutableStateOf(currentCustomLimit?.toString() ?: "")
    }

    val isDegraded = (currentCustomLimit in listOf(32_000, 32_768, 200_000)) && modelContextTokens > (currentCustomLimit ?: 0)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDegraded) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (isDegraded) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        if (isDegraded) Icons.Default.WarningAmber else Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (isDegraded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isDegraded) "会话上下文已降级保护 (${formatTokenCount(currentCustomLimit ?: 200_000)})" else "本会话上下文上限限制",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDegraded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isDegraded -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        currentCustomLimit != null -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    }
                ) {
                    Text(
                        text = when {
                            isDegraded -> "已降级 (${formatTokenCount(currentCustomLimit ?: 200_000)})"
                            currentCustomLimit != null -> "已自定义: ${formatTokenCount(currentCustomLimit)}"
                            else -> "跟随模型 (${formatTokenCount(modelContextTokens)})"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isDegraded -> MaterialTheme.colorScheme.error
                            currentCustomLimit != null -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (isDegraded) {
                Text(
                    text = "提示：由于先前请求超出模型窗口或遇到服务限制，系统已自动将本会话临时降级至 ${formatTokenCount(currentCustomLimit ?: 200_000)} 保护以恢复生成。当前模型原生支持 ${formatTokenCount(modelContextTokens)}。您可以随时点击下方按钮一键恢复模型默认，或手动选择更高规格。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        customInput = ""
                        onUpdateLimit(null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("恢复跟随模型默认 (${formatTokenCount(modelContextTokens)})")
                }
            } else {
                Text(
                    text = "允许在对话内独立限制上下文窗口，不影响模型在其他会话的配置；自动降级保护也仅对本会话生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 快捷芯片选择
            val presets = listOf(
                Pair("跟随模型", null),
                Pair("32K", 32_768),
                Pair("64K", 65_536),
                Pair("128K", 131_072),
                Pair("200K", 200_000),
                Pair("1M", 1_000_000),
                Pair("2M", 2_000_000)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { (label, tokens) ->
                    val isSelected = currentCustomLimit == tokens
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            customInput = tokens?.toString() ?: ""
                            onUpdateLimit(tokens)
                        },
                        label = {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }

            // 自定义输入框
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it.filter { char -> char.isDigit() }.take(7) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("自定义 Tokens，如 65536") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(10.dp)
                )
                Button(
                    onClick = {
                        val parsed = customInput.toIntOrNull()?.coerceIn(4_000, 2_000_000)
                        if (parsed != null) {
                            onUpdateLimit(parsed)
                        } else {
                            onUpdateLimit(null)
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("应用")
                }
                if (currentCustomLimit != null) {
                    TextButton(
                        onClick = {
                            customInput = ""
                            onUpdateLimit(null)
                        }
                    ) {
                        Text("重置")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContextUsageDetails(
    usage: ConversationContextUsage,
    onGenerateRollingSummary: (() -> Unit)? = null,
    onEditRollingSummary: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ContextUsageRow(
                label = "可用输入预算",
                value = "${formatTokenCount(usage.promptBudgetTokens)} tokens"
            )
            ContextUsageRow(
                label = "近期原文",
                value = "${usage.recentMessageCount} 条 · ${formatTokenCount(usage.recentTokens)} tokens"
            )
            ContextUsageRow(
                label = "较早历史消息",
                value = "${usage.olderMessageCount} 条"
            )
            ContextUsageRow(
                label = "时间线/记忆成果",
                value = "${usage.memoryItemCount} 条记忆 · ${formatTokenCount(usage.memoryTokens)} tokens"
            )
            ContextUsageRow(
                label = "压缩沉淀水线",
                value = usage.compressedThroughMessageId?.let { "已沉淀至 #$it" } ?: "未触发压缩"
            )
            ContextUsageRow(
                label = "沉淀梳理时间",
                value = usage.summaryUpdatedAt?.let(::formatContextTimestamp) ?: "暂无"
            )
        }
    }
}

@Composable
internal fun ContextUsageStatus(
    usage: ConversationContextUsage,
    statusMessage: String?
) {
    val isHighPressure = usage.usagePercent >= 0.60f
    val message = statusMessage ?: if (usage.canCompress && isHighPressure) {
        "上下文负载较高，可主动梳理时间线与提炼记忆以释放容量。"
    } else if (usage.canCompress) {
        "检测到较多早期历史对话，可按需梳理时间线与提炼记忆。"
    } else {
        "当前上下文容量充裕，最近十几次历史对话完整保留。"
    }
    val icon = if (isHighPressure) Icons.Default.Warning else Icons.Default.CheckCircle
    val color = if (isHighPressure) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.10f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun ContextUsageRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun contextUsageColor(usagePercent: Float): Color {
    return when {
        usagePercent >= 0.85f -> MaterialTheme.colorScheme.error
        usagePercent >= 0.65f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
}

internal fun formatTokenCount(value: Int): String {
    return if (value >= 1000) {
        String.format(Locale.getDefault(), "%.1fk", value / 1000f)
    } else {
        value.toString()
    }
}

internal fun formatContextTimestamp(timestamp: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatHeaderTitle(
    title: String,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun RollingSummaryEditDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    initialSummary: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var text by remember(initialSummary) { mutableStateOf(initialSummary) }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text("会话历史梳理与记忆", style = MaterialTheme.typography.titleLarge)
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "过往历史对话已梳理沉淀为时间线节点与会话专属记忆。您可以直接审阅或按需微调修改：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 460.dp),
                    maxLines = 30,
                    placeholder = { Text("暂无历史梳理内容...") },
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
                        onClear()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("清除摘要")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            onSave(text)
                            onDismiss()
                        }
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    )
}

@Composable
internal fun ContextCompressionTierSelectorCard(
    usage: com.aiassistant.domain.model.ConversationContextUsage,
    onApplyTier: ((com.aiassistant.domain.model.CompressionTier, Int, Int) -> Unit)?,
    onPreviewSettingsChanged: ((Int, Int) -> Unit)? = null,
    onRegisterApplyPending: ((() -> Unit)?) -> Unit = {}
) {
    // 注意：轮数/百分比及其 confirmed 基线不得以 usage 字段作 remember key——
    // 滑动条松手后 ViewModel 异步重算 usage 回流会改写这些字段，若作为 key 会把
    // confirmed 基线重置成新值，hasPendingChange 瞬间变 false，导致"前后对比预览"
    // 刚弹出即缩回（重算快时根本来不及显示）。仅在首次组合取初值。
    var selectedTier by remember(usage.compressionTier) { mutableStateOf(usage.compressionTier) }
    var l2Rounds by remember { mutableIntStateOf(usage.compressionRecentRounds) }
    var customPercent by remember {
        mutableIntStateOf(
            usage.compressionCustomPercent.coerceIn(
                com.aiassistant.domain.model.CompressionTierPolicy.MIN_CUSTOM_RETAIN_PERCENT,
                com.aiassistant.domain.model.CompressionTierPolicy.MAX_CUSTOM_RETAIN_PERCENT
            )
        )
    }
    var confirmedTier by remember(usage.compressionTier) { mutableStateOf(usage.compressionTier) }
    var confirmedRounds by remember { mutableIntStateOf(usage.compressionRecentRounds) }
    var confirmedPercent by remember { mutableIntStateOf(usage.compressionCustomPercent) }

    val hasPendingChange = selectedTier != confirmedTier ||
        (selectedTier == com.aiassistant.domain.model.CompressionTier.L2 && l2Rounds != confirmedRounds) ||
        (selectedTier == com.aiassistant.domain.model.CompressionTier.LC && customPercent != confirmedPercent)
    val currentPreview = usage.tierPreviews.firstOrNull { it.tier == selectedTier }

    // 应用当前待确认的档位/参数变更（胶囊内"确认应用并生效"与宿主对话框"完成"键共用）
    fun applyPendingChange() {
        val pending = selectedTier != confirmedTier ||
            (selectedTier == com.aiassistant.domain.model.CompressionTier.L2 && l2Rounds != confirmedRounds) ||
            (selectedTier == com.aiassistant.domain.model.CompressionTier.LC && customPercent != confirmedPercent)
        if (!pending) return
        onApplyTier?.invoke(selectedTier, l2Rounds, customPercent)
        confirmedTier = selectedTier
        confirmedRounds = l2Rounds
        confirmedPercent = customPercent
    }
    // 有待确认变更时向宿主注册"完成=确认并生效"动作；变更被确认或还原后注销
    LaunchedEffect(hasPendingChange) {
        onRegisterApplyPending(if (hasPendingChange) { { applyPendingChange() } } else null)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        Icons.Default.Compress,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "请求组装压缩档位",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "当前: ${usage.compressionTier.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "压缩仅在组装发送至大模型的请求报文时生效，绝不物理改写或删除数据库中的聊天记录。每会话独立记忆。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 档位单选列表
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.aiassistant.domain.model.CompressionTier.values().forEach { tier ->
                    val isSelected = tier == selectedTier
                    val isCurrentSaved = tier == usage.compressionTier
                    val preview = usage.tierPreviews.firstOrNull { it.tier == tier }
                    val estTokens = preview?.estimatedTokens ?: 0
                    val savedTokens = preview?.tokensSaved ?: 0
                    val savedPercent = ((preview?.savingsPercent ?: 0f) * 100).toInt()

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTier = tier
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedTier = tier },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "${tier.name} · ${tier.displayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCurrentSaved) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "生效中",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "预估 ~$estTokens T",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (savedTokens > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "-$savedPercent%",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = tier.detailLossNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // L2 档位提供 N 轮滑动选择 (4~32)
                            if (tier == com.aiassistant.domain.model.CompressionTier.L2 && isSelected) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "保留最近轮数 N：",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "最近 $l2Rounds 轮（${l2Rounds * 2} 条原文）",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Slider(
                                        value = l2Rounds.toFloat(),
                                        onValueChange = { l2Rounds = it.toInt() },
                                        onValueChangeFinished = {
                                            onPreviewSettingsChanged?.invoke(l2Rounds, customPercent)
                                        },
                                        valueRange = 4f..32f,
                                        steps = 27,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                                    )
                                }
                            }

                            // LC 自定义比例档：保留原文的百分比滑动选择 (10~90，步长 5)
                            if (tier == com.aiassistant.domain.model.CompressionTier.LC && isSelected) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "保留原文百分比：",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "保留 $customPercent% 原文（约压缩 ${100 - customPercent}%）",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Slider(
                                        value = customPercent.toFloat(),
                                        onValueChange = { value ->
                                            customPercent = (Math.round(value / com.aiassistant.domain.model.CompressionTierPolicy.STEP_CUSTOM_RETAIN_PERCENT) *
                                                com.aiassistant.domain.model.CompressionTierPolicy.STEP_CUSTOM_RETAIN_PERCENT)
                                                .coerceIn(
                                                    com.aiassistant.domain.model.CompressionTierPolicy.MIN_CUSTOM_RETAIN_PERCENT,
                                                    com.aiassistant.domain.model.CompressionTierPolicy.MAX_CUSTOM_RETAIN_PERCENT
                                                )
                                        },
                                        onValueChangeFinished = {
                                            onPreviewSettingsChanged?.invoke(l2Rounds, customPercent)
                                        },
                                        valueRange = com.aiassistant.domain.model.CompressionTierPolicy.MIN_CUSTOM_RETAIN_PERCENT.toFloat()
                                            ..com.aiassistant.domain.model.CompressionTierPolicy.MAX_CUSTOM_RETAIN_PERCENT.toFloat(),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 前后对比预览卡片与确认生效
            AnimatedVisibility(
                visible = hasPendingChange,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "前后对比预览（确认后生效）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if ((currentPreview?.tokensSaved ?: 0) > 0) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "预计释放 ~${currentPreview?.tokensSaved} T (-${((currentPreview?.savingsPercent ?: 0f) * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // 双条形对比：当前档位 vs 新档位（长度按基线归一化，直观呈现压缩幅度）
                        val baselineTokens = usage.estimatedInputTokens.coerceAtLeast(1)
                        ComparisonBarRow(
                            label = "当前 · ${usage.compressionTier.displayName}",
                            tokens = usage.estimatedInputTokens,
                            fraction = 1f,
                            barColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                            valueColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        ComparisonBarRow(
                            label = "新档 · ${selectedTier.displayName}",
                            tokens = currentPreview?.estimatedTokens ?: 0,
                            fraction = ((currentPreview?.estimatedTokens ?: 0).toFloat() / baselineTokens)
                                .coerceIn(0.03f, 1f),
                            barColor = MaterialTheme.colorScheme.primary,
                            valueColor = MaterialTheme.colorScheme.primary,
                            highlight = true
                        )

                        Text(
                            text = "保留策略：${currentPreview?.retainedRoundsDesc ?: selectedTier.shortDesc}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "修剪策略：${selectedTier.detailLossNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { applyPendingChange() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("确认应用并生效", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 前后对比预览的单行对比条：标签 + 归一化长度条 + Token 数 */
@Composable
private fun ComparisonBarRow(
    label: String,
    tokens: Int,
    fraction: Float,
    barColor: Color,
    valueColor: Color,
    highlight: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "~$tokens T",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                color = valueColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(3.5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.5.dp))
                    .background(barColor)
            )
        }
    }
}

