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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.ui.components.EchoGlassPagePanelShape
import com.aiassistant.ui.components.echoShapeCombinedClick
import com.aiassistant.ui.components.echoThemeHardShadow
import com.aiassistant.ui.theme.LocalEchoThemeStyle
import com.aiassistant.ui.theme.echoThemeFrameOrnament

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
    backdropContentColor: Color,
    recentStory: RecentHomeStory?,
    showRecentStory: Boolean,
    onNewChat: () -> Unit,
    onConfigureChat: () -> Unit,
    onContinueStory: (Long) -> Unit,
    onOpenStudio: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
        if (workbench) {
            HomeNewChatAction(compact = true, onNewChat, onConfigureChat)
            if (showRecentStory) {
                HomeStorySpotlight(recentStory, prominent = true, onContinueStory, onOpenStudio)
            }
        } else {
            HomeNewChatAction(compact, onNewChat, onConfigureChat)
            if (showRecentStory && recentStory != null) {
                HomeStorySpotlight(recentStory, prominent = false, onContinueStory, onOpenStudio)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = if (workbench) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (workbench) "会话列表" else "最近会话",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = backdropContentColor,
                modifier = Modifier.weight(1f))
            Text("长按多选", style = MaterialTheme.typography.labelSmall,
                color = backdropContentColor.copy(alpha = 0.84f))
        }
    }
}

@Composable
private fun HomeNewChatAction(
    compact: Boolean,
    onNewChat: () -> Unit,
    onConfigureChat: () -> Unit
) {
    val themeStyle = LocalEchoThemeStyle.current
    val actionShape = EchoGlassPagePanelShape
    val strokeWidth = if (themeStyle.strongOutline) 1.5.dp else 1.dp
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .echoThemeHardShadow(offset = 3.dp, shape = actionShape)
                .echoShapeCombinedClick(
                    shape = actionShape,
                    onClick = onNewChat,
                    onLongClick = onConfigureChat,
                    onLongClickLabel = "配置新对话"
                )
                .echoThemeFrameOrnament(cornerRadius = themeStyle.panelCornerRadius, compact = true),
            shape = actionShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            border = if (themeStyle.strongOutline) BorderStroke(strokeWidth, MaterialTheme.colorScheme.outline) else null
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("新对话", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
        OutlinedIconButton(
            onClick = onConfigureChat,
            modifier = Modifier
                .size(48.dp)
                .echoThemeHardShadow(offset = 3.dp, shape = actionShape),
            shape = actionShape,
            border = BorderStroke(
                strokeWidth,
                if (themeStyle.strongOutline) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
            ),
            colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Icon(Icons.Default.Tune, contentDescription = "选择模型与新对话设置", modifier = Modifier.size(20.dp))
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
    val themeStyle = LocalEchoThemeStyle.current
    val spotlightShape = EchoGlassPagePanelShape
    Surface(
        onClick = { conversation?.let { onContinueStory(it.id) } ?: onOpenStudio() },
        modifier = Modifier
            .fillMaxWidth()
            .echoThemeHardShadow(shape = spotlightShape)
            .echoThemeFrameOrnament(cornerRadius = themeStyle.panelCornerRadius, compact = true),
        shape = spotlightShape,
        border = BorderStroke(
            if (themeStyle.strongOutline) 1.6.dp else 1.dp,
            if (themeStyle.strongOutline) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
        ),
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
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (conversation == null) "开始创作" else "最近故事", style = MaterialTheme.typography.labelMedium)
                }
                Text(conversation?.title?.ifBlank { "未命名故事" } ?: "为下一个故事留一页",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = conversation?.currentStoryTime?.takeIf { it.isNotBlank() }
                    ?: story?.session?.currentPlotSummary?.takeIf { it.isNotBlank() }
                    ?: if (conversation != null) "${conversation.messageCount} 条消息" else "选择角色与世界观，开启新的创作。"
                Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
