@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.aiassistant.ui.screens.chat

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiassistant.data.repository.AutoTimelineUpdateResult
import com.aiassistant.domain.model.PendingMemoryCandidate
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.theme.EchoTokens

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

/** 同时出现两类候选时只展开一份正文；切换不销毁另一份编辑草稿。 */
@Composable
internal fun ChatPendingReviewPanels(
    memory: PendingMemoryCandidate?,
    timeline: AutoTimelineUpdateResult?,
    onDismissMemory: () -> Unit,
    onAcceptMemory: (String, String?) -> Unit,
    onDismissTimeline: (AutoTimelineUpdateResult) -> Unit,
    onApplyTimeline: (AutoTimelineUpdateResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPanel by rememberSaveable { mutableStateOf("timeline") }
    val memoryExpanded = memory != null && (timeline == null || selectedPanel == "memory")
    val timelineExpanded = timeline != null && !memoryExpanded
    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        memory?.let { candidate ->
            PendingMemoryReview(
                candidate = candidate,
                expanded = memoryExpanded,
                onExpand = { selectedPanel = "memory" },
                onDismiss = onDismissMemory,
                onAccept = onAcceptMemory,
                modifier = if (memoryExpanded) Modifier.weight(1f) else Modifier
            )
        }
        timeline?.let { proposal ->
            PendingTimelineReview(
                proposal = proposal,
                expanded = timelineExpanded,
                onExpand = { selectedPanel = "timeline" },
                onDismiss = { onDismissTimeline(proposal) },
                onApply = onApplyTimeline,
                modifier = if (timelineExpanded) Modifier.weight(1f) else Modifier
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
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
    body: @Composable ColumnScope.() -> Unit,
    actions: @Composable FlowRowScope.() -> Unit
) {
    var fullScreen by remember(reviewKey) { mutableStateOf(false) }
    EchoGlassCard(
        modifier = modifier.fillMaxWidth().then(if (expanded) Modifier.fillMaxHeight() else Modifier)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        containerColor = containerColor,
        shape = EchoTokens.Radius.shapeMd
    ) {
        Column(Modifier.fillMaxWidth().then(if (expanded) Modifier.fillMaxHeight() else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min = 40.dp).clickable(onClick = onExpand),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!expanded) Icon(Icons.Default.ExpandMore, "展开待确认内容", Modifier.size(20.dp))
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
                    modifier = Modifier.weight(1f).fillMaxWidth()
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
            SessionSettingChangeDetails(candidate.action, candidate.previousContent,
                candidate.evidence, candidate.changeReason)
            if (editing) {
                OutlinedTextField(value = content, onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text("编辑设定/记忆内容") })
            } else {
                Text("「${candidate.distilledContent}」", style = MaterialTheme.typography.bodySmall)
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
    modifier: Modifier
) {
    var editing by remember(proposal.proposalId) { mutableStateOf(false) }
    var storyTime by remember(proposal.proposalId) { mutableStateOf(proposal.updatedStoryTime.orEmpty()) }
    var eventTime by remember(proposal.proposalId) { mutableStateOf(proposal.newEvent?.timeTag.orEmpty()) }
    var eventContent by remember(proposal.proposalId) { mutableStateOf(proposal.newEvent?.content.orEmpty()) }
    var settings by remember(proposal.proposalId) { mutableStateOf(proposal.atemporalSettings.map { it.copy() }) }
    val scrollState = remember(proposal.proposalId) { ScrollState(0) }
    PendingReviewCard(
        reviewKey = proposal.proposalId,
        title = if (editing) "编辑本轮时间线与设定" else "本轮时间线与设定待确认",
        icon = Icons.Default.HistoryEdu,
        expanded = expanded,
        onExpand = onExpand,
        onDismiss = onDismiss,
        scrollState = scrollState,
        modifier = modifier,
        body = {
            proposal.extractionErrorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                if (!proposal.updatedStoryTime.isNullOrBlank()) {
                    Text("推进时空：${proposal.updatedStoryTime}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                proposal.newEvent?.let { event ->
                    if (proposal.action == "UPDATE" && !proposal.previousEventContent.isNullOrBlank()) {
                        Text("原事件：${proposal.previousEventContent}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${if (proposal.action == "UPDATE") "更新事件" else "新增事件"}：[${event.timeTag}]【${event.category.displayName}】${event.content}",
                        style = MaterialTheme.typography.bodySmall)
                }
                settings.forEachIndexed { index, setting ->
                    SessionSettingChangeDetails(setting.action, setting.previousContent, setting.evidence, setting.changeReason)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = setting.isSelected, onCheckedChange = { selected ->
                            settings = settings.mapIndexed { i, item -> if (i == index) item.copy(isSelected = selected) else item }
                        })
                        Text("【${setting.category}】${setting.content}", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).clickable {
                                settings = settings.mapIndexed { i, item -> if (i == index) item.copy(isSelected = !item.isSelected) else item }
                            })
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
