package com.aiassistant.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiassistant.AiAssistantApp
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.utils.FavoriteMessage
import com.aiassistant.utils.FavoriteStore
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置 · 收藏：汇总所有对话的收藏消息，点击跳回对应对话与消息位置。
 *
 * 收藏快照只用于列表预览；跳转依赖 messageId，消息被删除时对话页会提示“该消息已不存在”。
 */
@Composable
fun FavoritesTab(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onNavigateToMessage: (conversationId: Long, messageId: Long) -> Unit
) {
    val favorites by FavoriteStore.favorites.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { FavoriteStore.ensureLoaded(context) }
    val conversations by AiAssistantApp.instance.repository.getAllConversations()
        .collectAsState(initial = emptyList())
    val titles = remember(conversations) { conversations.associate { it.id to it.title } }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (favorites.isEmpty()) {
            item(key = "favorites_empty") {
                EchoGlassCard(modifier = Modifier.fillMaxWidth(), shape = EchoTokens.Radius.shapeMd) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("还没有收藏", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "在对话中点击输入或回复下方的星标即可收藏；这里可以统一查看并跳回原位置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item(key = "favorites_count") {
                Text(
                    text = "共 ${favorites.size} 条收藏",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            items(items = favorites, key = { it.messageId }) { favorite ->
                FavoriteRow(
                    favorite = favorite,
                    conversationTitle = titles[favorite.conversationId].orEmpty(),
                    timeLabel = timeFormat.format(Date(favorite.messageCreatedAt)),
                    onClick = { onNavigateToMessage(favorite.conversationId, favorite.messageId) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    favorite: FavoriteMessage,
    conversationTitle: String,
    timeLabel: String,
    onClick: () -> Unit
) {
    EchoGlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = EchoTokens.Radius.shapeMd,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (favorite.role == "user") "我的输入" else "模型回复",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = conversationTitle.ifBlank { "对话 #${favorite.conversationId}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Text(
                text = favorite.content.replace('\n', ' ').trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "点击跳转到该消息",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
        }
    }
}
