package com.aiassistant.ui.screens.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import com.aiassistant.domain.model.Message
import com.aiassistant.data.repository.ChatGenerationManager

/** The application owns in-flight variants; screen recreation must recover their original host. */
internal fun activeStreamingBranchGroupId(conversationId: Long): String? =
    ChatGenerationManager.getSession(conversationId)?.takeIf { it.isGenerating.value }?.assistantVariantGroupId

internal data class ChatReplyRowKeys(
    val messageKeys: List<String>,
    val streamingKey: String,
    val hasPersistedReply: Boolean
)

/** A turn's first reply keeps its LazyColumn identity when streaming becomes a saved message. */
internal fun chatReplyRowKeys(
    items: List<DisplayMessageItem>,
    anchorUserMessageId: Long? = null,
    anchorUserGroupId: String? = null
): ChatReplyRowKeys {
    fun messageKey(item: DisplayMessageItem): String = item.groupId?.let { "chat_group:$it" }
        ?: "chat_message:${item.message.id}_${item.message.createdAt}_${item.message.role}"
    val anchor = items.lastOrNull {
        it.message.role == "user" &&
            (it.message.id == anchorUserMessageId || (anchorUserGroupId != null && it.groupId == anchorUserGroupId))
    } ?: if (anchorUserMessageId == null && anchorUserGroupId == null) items.lastOrNull { it.message.role == "user" } else null
    val anchorKey = anchor?.let(::messageKey)
        ?: anchorUserGroupId?.let { "chat_group:$it" }
        ?: anchorUserMessageId?.let { "deleted_user:$it" }
        ?: "pending"
    val streamingKey = "chat_reply:$anchorKey"
    var precedingUserKey: String? = null
    var hasReplyForUser = false
    val keys = items.map { item ->
        when (item.message.role) {
            "user" -> messageKey(item).also {
                precedingUserKey = it
                hasReplyForUser = false
            }
            "assistant" -> if (precedingUserKey != null && !hasReplyForUser) {
                hasReplyForUser = true
                "chat_reply:$precedingUserKey"
            } else messageKey(item)
            else -> messageKey(item)
        }
    }
    return ChatReplyRowKeys(keys, streamingKey, streamingKey in keys)
}

/** Saved assistant replies are a rendering handoff, not a request to move the reader. */
internal fun hasNewUserMessage(previousIds: Set<Long>, messages: List<Message>): Boolean =
    messages.any { it.role == "user" && it.id !in previousIds }

/** Only consume actual bottom overflow; never overshoot and rely on a later layout to undo it. */
internal fun bottomFollowDistance(itemOffset: Int, itemSize: Int, viewportEnd: Int): Int =
    (itemOffset + itemSize - viewportEnd).coerceAtLeast(0)

internal suspend fun LazyListState.followMeasuredBottom() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == lastIndex }) {
        scrollToItem(lastIndex)
        withFrameNanos { }
    }
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull { it.index == layout.totalItemsCount - 1 } ?: return
    val distance = bottomFollowDistance(last.offset, last.size, layout.viewportEndOffset)
    if (distance > 0) scrollBy(distance.toFloat())
}
