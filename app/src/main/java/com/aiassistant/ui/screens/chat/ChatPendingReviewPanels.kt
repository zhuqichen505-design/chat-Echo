@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.aiassistant.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiassistant.data.repository.AutoTimelineUpdateResult
import com.aiassistant.domain.model.PendingMemoryCandidate
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.echoCheckboxColors
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.utils.TimelineCategory

/** 先测量输入栏和它消耗的 IME，再分配审阅区；消息区始终保留可视空间。 */
@Composable
internal fun ChatReviewInputLayout(
    review: @Composable () -> Unit,
    input: @Composable () -> Unit
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Box { review() }
            Box { input() }
        }
    ) { measurables, constraints ->
        val inputPlaceable = measurables[1].measure(constraints.copy(minHeight = 0))
        val maximumReviewHeight = 440.dp.roundToPx()
        val availableReviewHeight = if (constraints.hasBoundedHeight) {
            (constraints.maxHeight - inputPlaceable.height - 120.dp.roundToPx()).coerceAtLeast(0)
                .coerceAtMost(maximumReviewHeight)
        } else maximumReviewHeight
        val reviewPlaceable = measurables[0].measure(
            constraints.copy(minHeight = 0, maxHeight = availableReviewHeight)
        )
        layout(constraints.maxWidth, reviewPlaceable.height + inputPlaceable.height) {
            reviewPlaceable.placeRelative(0, 0)
            inputPlaceable.placeRelative(0, reviewPlaceable.height)
        }
    }
}

/**
 * Keeps the review candidates attached to the input bar without turning a short review into a
 * fixed-height spacer. The pending content may consume the remaining bounded height, while the
 * status row always follows it immediately and stays adjacent to the input field.
 */
@Composable
internal fun ChatReviewPanelStack(
    pendingReview: @Composable () -> Unit,
    status: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            pendingReview()
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            status()
        }
    }
}

/** 同时出现两类候选时只展开一份正文；切换不销毁另一份编辑草稿。 */
@Composable
internal fun ChatPendingReviewPanels(
    memory: PendingMemoryCandidate?,
    timeline: AutoTimelineUpdateResult?,
    onDismissMemory: () -> Unit,
    onAcceptMemory: (String, String?) -> Unit,
    onDismissTimeline: (AutoTimelineUpdateResult) -> Unit,
    onApplyTimeline: (AutoTimelineUpdateResult) -> Unit,
    modifier: Modifier = Modifier,
    onReExtractTimeline: ((AutoTimelineUpdateResult) -> Unit)? = null
) {
    var selectedPanel by rememberSaveable { mutableStateOf("timeline") }
    val memoryExpanded = memory != null && (timeline == null || selectedPanel == "memory")
    val timelineExpanded = timeline != null && !memoryExpanded
    // 上层只提供最大可用高度；不要把这个上限变成所有候选的固定高度。
    Column(modifier = modifier.fillMaxWidth()) {
        memory?.let { candidate ->
            PendingMemoryReview(
                candidate = candidate,
                expanded = memoryExpanded,
                onExpand = { selectedPanel = "memory" },
                onDismiss = onDismissMemory,
                onAccept = onAcceptMemory,
                modifier = if (memoryExpanded) Modifier.weight(1f, fill = false) else Modifier
            )
        }
        timeline?.let { proposal ->
            PendingTimelineReview(
                proposal = proposal,
                expanded = timelineExpanded,
                onExpand = { selectedPanel = "timeline" },
                onDismiss = { onDismissTimeline(proposal) },
                onApply = onApplyTimeline,
                onReExtract = onReExtractTimeline?.let { callback -> { callback(proposal) } },
                modifier = if (timelineExpanded) Modifier.weight(1f, fill = false) else Modifier
            )
        }
    }
}

/** 标题与操作始终可见，只有正文滚动；滚动状态属于候选本身。 */
@Composable
private fun PendingReviewCard(
    reviewKey: String,
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onReExtract: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
    body: @Composable ColumnScope.() -> Unit,
    actions: @Composable FlowRowScope.() -> Unit
) {
    var fullScreen by remember(reviewKey) { mutableStateOf(false) }
    EchoGlassCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        containerColor = containerColor,
        shape = EchoTokens.Radius.shapeMd
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min = 40.dp).clickable(onClick = onExpand),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, null, Modifier.size(18.dp), tint = iconTint)
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!expanded) Icon(Icons.Default.ExpandMore, "展开待确认内容", Modifier.size(20.dp))
                }
                if (onReExtract != null) {
                    IconButton(onClick = { fullScreen = false; onReExtract() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Refresh, "重新提取", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { fullScreen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.OpenInFull, "全屏查看和编辑", Modifier.size(18.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "忽略此候选", Modifier.size(18.dp))
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                        .verticalScroll(scrollState).padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = body
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    content = actions
                )
            }
        }
    }
    if (fullScreen) {
        Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        if (onReExtract != null) {
                            IconButton(onClick = { fullScreen = false; onReExtract() }) {
                                Icon(Icons.Default.Refresh, "重新提取", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { fullScreen = false }) {
                            Icon(Icons.Default.CloseFullscreen, "返回聊天候选面板")
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
                    HorizontalDivider()
                    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp), content = actions)
                }
            }
        }
    }
}

@Composable
private fun PendingMemoryReview(
    candidate: PendingMemoryCandidate,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onAccept: (String, String?) -> Unit,
    modifier: Modifier
) {
    var editing by remember(candidate.id) { mutableStateOf(false) }
    var content by remember(candidate.id) { mutableStateOf(candidate.distilledContent) }
    val scrollState = remember(candidate.id) { ScrollState(0) }
    PendingReviewCard(
        reviewKey = candidate.id,
        title = if (editing) "编辑设定记忆" else "设定记忆待确认",
        icon = Icons.Default.AutoAwesome,
        expanded = expanded,
        onExpand = onExpand,
        onDismiss = onDismiss,
        scrollState = scrollState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        body = {
            if (editing) {
                SessionSettingChangeDetails(candidate.action, candidate.previousContent,
                    candidate.evidence, candidate.changeReason)
                OutlinedTextField(value = content, onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text("编辑设定/记忆内容") })
            } else {
                PendingStructuredItemCard(selected = true) {
                    ReviewActionBadgeRow(action = candidate.action)
                    Text(
                        text = candidate.distilledContent,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ReviewSettingMetadataBlock(
                        previousContent = candidate.previousContent,
                        evidence = candidate.evidence,
                        changeReason = candidate.changeReason
                    )
                }
            }
        },
        actions = {
            if (editing) {
                TextButton(onClick = { content = candidate.distilledContent; editing = false }) { Text("取消编辑") }
                Button(enabled = content.isNotBlank(), onClick = { onAccept("session", content) }) { Text("应用") }
            } else {
                TextButton(onClick = { editing = true }) { Text("编辑") }
                OutlinedButton(onClick = { onAccept("session", null) }) { Text("仅本会话生效") }
                Button(onClick = { onAccept("user", null) }) { Text("存为长期记忆") }
            }
        }
    )
}

@Composable
private fun PendingTimelineReview(
    proposal: AutoTimelineUpdateResult,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onApply: (AutoTimelineUpdateResult) -> Unit,
    onReExtract: (() -> Unit)?,
    modifier: Modifier
) {
    var editing by remember(proposal.proposalId) { mutableStateOf(false) }
    var storyTime by remember(proposal.proposalId) { mutableStateOf(proposal.updatedStoryTime.orEmpty()) }
    var eventTime by remember(proposal.proposalId) { mutableStateOf(proposal.newEvent?.timeTag.orEmpty()) }
    var eventContent by remember(proposal.proposalId) { mutableStateOf(proposal.newEvent?.content.orEmpty()) }
    var settings by remember(proposal.proposalId) { mutableStateOf(proposal.atemporalSettings.map { it.copy() }) }
    val scrollState = remember(proposal.proposalId) { ScrollState(0) }
    val hasExtractedContent = proposal.newEvent != null || !proposal.updatedStoryTime.isNullOrBlank() || settings.isNotEmpty()
    val isFailureOnly = !proposal.extractionErrorMessage.isNullOrBlank() && !hasExtractedContent
    PendingReviewCard(
        reviewKey = proposal.proposalId,
        title = when {
            editing -> "编辑本轮时间线与设定"
            isFailureOnly -> "本轮时间线与设定提取失败"
            else -> "本轮时间线与设定待确认"
        },
        icon = if (isFailureOnly) Icons.Default.WarningAmber else Icons.Default.HistoryEdu,
        iconTint = if (isFailureOnly) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        expanded = expanded,
        onExpand = onExpand,
        onDismiss = onDismiss,
        onReExtract = onReExtract,
        scrollState = scrollState,
        modifier = modifier,
        body = {
            proposal.extractionErrorMessage?.takeIf { it.isNotBlank() }?.let { errMsg ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 1.dp).size(15.dp)
                        )
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (editing) {
                if (!proposal.updatedStoryTime.isNullOrBlank()) {
                    OutlinedTextField(value = storyTime, onValueChange = { storyTime = it },
                        label = { Text("推进时空") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (proposal.newEvent != null) {
                    OutlinedTextField(value = eventTime, onValueChange = { eventTime = it },
                        label = { Text("事件时间节点") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = eventContent, onValueChange = { eventContent = it },
                        label = { Text("事件内容") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                }
                settings.forEachIndexed { index, setting ->
                    OutlinedTextField(value = setting.content, onValueChange = { value ->
                        settings = settings.mapIndexed { i, item -> if (i == index) item.copy(content = value) else item }
                    }, label = { Text("设定 · ${setting.category}") }, modifier = Modifier.fillMaxWidth(),
                        minLines = 1, maxLines = 4)
                }
            } else {
                // 1. 推进时空：顶部醒目紧凑条
                proposal.updatedStoryTime?.takeIf { it.isNotBlank() }?.let { updatedTime ->
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "推进时空",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = updatedTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. 时间线事件：胶囊标签行 + 原事件对照 + 清晰正文
                proposal.newEvent?.let { event ->
                    val isUpdate = proposal.action == "UPDATE"
                    val categoryColor = remember(event.category) { parseTimelineCategoryColor(event.category) }
                    PendingStructuredItemCard(selected = true) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ReviewTagBadge(
                                text = if (isUpdate) "更新事件" else "新增事件",
                                containerColor = if (isUpdate) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                },
                                contentColor = if (isUpdate) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                            if (event.timeTag.isNotBlank()) {
                                ReviewTagBadge(
                                    text = event.timeTag,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ReviewTagBadge(
                                text = event.category.displayName,
                                containerColor = categoryColor.copy(alpha = 0.14f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                leadingDotColor = categoryColor
                            )
                        }
                        if (isUpdate && !proposal.previousEventContent.isNullOrBlank()) {
                            ReviewPreviousQuoteBlock(
                                label = "原记录",
                                content = proposal.previousEventContent
                            )
                        }
                        Text(
                            text = event.content,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 3. 会话设定：分节计数 + 每条设定自成卡片（操作/类别/正文/变更依据同卡聚合）
                if (settings.isNotEmpty()) {
                    val selectedCount = settings.count { it.isSelected }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 2.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "提取设定",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "已选 $selectedCount/${settings.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    settings.forEachIndexed { index, setting ->
                        val toggleSelection = {
                            settings = settings.mapIndexed { i, item ->
                                if (i == index) item.copy(isSelected = !item.isSelected) else item
                            }
                        }
                        PendingStructuredItemCard(
                            selected = setting.isSelected,
                            onClick = toggleSelection
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = setting.isSelected,
                                    onCheckedChange = null,
                                    colors = echoCheckboxColors(),
                                    modifier = Modifier.padding(top = 2.dp).size(20.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (setting.category.isNotBlank()) {
                                            ReviewTagBadge(
                                                text = setting.category,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        ReviewActionBadge(action = setting.action, compact = true)
                                    }
                                    Text(
                                        text = setting.content,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                        color = if (setting.isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                        }
                                    )
                                }
                            }
                            ReviewSettingMetadataBlock(
                                previousContent = setting.previousContent,
                                evidence = setting.evidence,
                                changeReason = setting.changeReason
                            )
                        }
                    }
                }
            }
        },
        actions = {
            if (editing) {
                TextButton(onClick = {
                    storyTime = proposal.updatedStoryTime.orEmpty()
                    eventTime = proposal.newEvent?.timeTag.orEmpty()
                    eventContent = proposal.newEvent?.content.orEmpty()
                    settings = proposal.atemporalSettings.map { it.copy() }
                    editing = false
                }) { Text("取消编辑") }
                Button(
                    enabled = (proposal.newEvent == null || eventContent.isNotBlank()) &&
                        (storyTime.isNotBlank() || eventContent.isNotBlank() || settings.any { it.isSelected && it.content.isNotBlank() }),
                    onClick = { onApply(proposal.copy(updatedStoryTime = storyTime.trim().ifBlank { null },
                        newEvent = proposal.newEvent?.copy(timeTag = eventTime.trim(), content = eventContent.trim()),
                        atemporalSettings = settings)) }
                ) { Text("应用") }
            } else {
                TextButton(enabled = proposal.newEvent != null || !proposal.updatedStoryTime.isNullOrBlank() || settings.isNotEmpty(),
                    onClick = { editing = true }) { Text("编辑") }
                Button(enabled = proposal.newEvent != null || !proposal.updatedStoryTime.isNullOrBlank() || settings.any { it.isSelected && it.content.isNotBlank() },
                    onClick = { onApply(proposal.copy(atemporalSettings = settings)) }) { Text("确认应用") }
            }
        }
    )
}

@Composable
private fun PendingStructuredItemCard(
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (selected) 0.80f else 0.44f)
    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(0.75.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun ReviewTagBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    leadingDotColor: Color? = null
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leadingDotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(leadingDotColor)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReviewActionBadge(action: String, compact: Boolean = false) {
    val (label, container, content) = when (action) {
        "SUPPLEMENT" -> Triple(
            if (compact) "补充" else "补充已有设定 · 保留旧信息",
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        "REVISE" -> Triple(
            if (compact) "修订 · 核对" else "修订已有设定 · 请核对后确认",
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Triple(
            if (compact) "新增" else "新增设定",
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
    ReviewTagBadge(text = label, containerColor = container, contentColor = content)
}

@Composable
private fun ReviewActionBadgeRow(action: String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ReviewActionBadge(action = action, compact = false)
    }
}

@Composable
private fun ReviewPreviousQuoteBlock(label: String, content: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .width(2.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReviewSettingMetadataBlock(
    previousContent: String?,
    evidence: String,
    changeReason: String
) {
    val hasPrev = !previousContent.isNullOrBlank()
    val hasEvidence = evidence.isNotBlank()
    val hasReason = changeReason.isNotBlank()
    if (!hasPrev && !hasEvidence && !hasReason) return

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        thickness = 0.5.dp
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (hasPrev) {
            ReviewPreviousQuoteBlock(label = "原设定", content = previousContent!!)
        }
        if (hasEvidence) {
            ReviewMetadataLine(label = "依据", value = evidence)
        }
        if (hasReason) {
            ReviewMetadataLine(label = "说明", value = changeReason)
        }
    }
}

@Composable
private fun ReviewMetadataLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun parseTimelineCategoryColor(category: TimelineCategory): Color = try {
    Color(android.graphics.Color.parseColor(category.tagColorHex))
} catch (_: Exception) {
    Color(0xFF2196F3)
}
