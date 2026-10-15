package com.aiassistant.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.ui.components.echoShapeCombinedClick

internal data class RecentHomeStory(val conversation: Conversation, val session: RoleplaySession)

/** Join only the visible home conversations; a roleplay session alone must not expose a hidden chat. */
internal fun recentHomeStory(
    visibleConversations: List<Conversation>,
    sessions: List<RoleplaySession>
): RecentHomeStory? {
    val sessionsByConversation = sessions.associateBy { it.conversationId }
    return visibleConversations.asSequence()
        .filter { it.tags?.contains("hidden", ignoreCase = true) != true &&
            it.tags?.contains("private", ignoreCase = true) != true }
        .filter { it.id in sessionsByConversation }
        .maxByOrNull { it.updatedAt }
        ?.let { RecentHomeStory(it, sessionsByConversation.getValue(it.id)) }
}

/** One LazyColumn item: the two compositions share exactly the same conversation operations below. */
@Composable
internal fun HomePresentationSection(
    workbench: Boolean,
    compact: Boolean,
    recentStory: RecentHomeStory?,
    showRecentStory: Boolean,
    onNewChat: () -> Unit,
    onConfigureChat: () -> Unit,
    onContinueStory: (Long) -> Unit,
    onOpenStudio: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)) {
        if (workbench) {
            if (showRecentStory) {
                HomeStorySpotlight(recentStory, prominent = true, onContinueStory, onOpenStudio)
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("创作资料", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenStudio, contentPadding = PaddingValues(vertical = 6.dp)) {
                        Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("角色、世界观与世界书", modifier = Modifier.weight(1f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            HomeNewChatAction(compact = true, onNewChat, onConfigureChat)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("今天想聊什么？", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text("继续一个想法，或开启新的对话。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HomeNewChatAction(compact, onNewChat, onConfigureChat)
            if (showRecentStory && recentStory != null) {
                HomeStorySpotlight(recentStory, prominent = false, onContinueStory, onOpenStudio)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = if (workbench) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (workbench) "会话列表" else "最近会话",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text("长按多选", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeNewChatAction(
    compact: Boolean,
    onNewChat: () -> Unit,
    onConfigureChat: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 50.dp else 58.dp)
                .echoShapeCombinedClick(
                    shape = MaterialTheme.shapes.medium,
                    onClick = onNewChat,
                    onLongClick = onConfigureChat,
                    onLongClickLabel = "配置新对话"
                ),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("开始新对话", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
        TextButton(onClick = onConfigureChat, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text("选择模型与对话设置",
                style = MaterialTheme.typography.labelMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeStorySpotlight(
    story: RecentHomeStory?,
    prominent: Boolean,
    onContinueStory: (Long) -> Unit,
    onOpenStudio: () -> Unit
) {
    val conversation = story?.conversation
    Surface(
        onClick = { conversation?.let { onContinueStory(it.id) } ?: onOpenStudio() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (prominent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (prominent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        if (!prominent) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("继续最近故事", style = MaterialTheme.typography.labelSmall)
                    Text(conversation?.title?.ifBlank { "未命名故事" } ?: "开始创作",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        } else {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (conversation == null) "开始创作" else "最近故事", style = MaterialTheme.typography.labelMedium)
                }
                Text(conversation?.title?.ifBlank { "未命名故事" } ?: "为下一个故事留一页",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = conversation?.currentStoryTime?.takeIf { it.isNotBlank() }
                    ?: story?.session?.currentPlotSummary?.takeIf { it.isNotBlank() }
                    ?: if (conversation != null) "${conversation.messageCount} 条消息" else "选择角色与世界观，开启新的创作。"
                Text(detail, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (conversation == null) "打开创作" else "继续故事", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
