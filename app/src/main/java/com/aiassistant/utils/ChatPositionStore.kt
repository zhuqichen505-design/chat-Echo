package com.aiassistant.utils

import android.content.Context
import androidx.core.content.edit

/**
 * 对话阅读位置：记录首个可见消息行的身份与像素偏移。
 *
 * [groupId] 优先（变体组切换后仍能定位到同一轮），组内消息被删除时回退 [messageId]。
 */
data class ChatScrollAnchor(
    val groupId: String?,
    val messageId: Long,
    val offset: Int
)

/**
 * 按会话 id 持久化阅读位置。列表停在底部时不保存——下次打开继续贴底，
 * 只有用户主动上翻阅读的位置才需要恢复。
 */
object ChatPositionStore {
    private const val PREFS_NAME = "echo_chat_positions"

    fun load(context: Context, conversationId: Long): ChatScrollAnchor? {
        if (conversationId <= 0L) return null
        val raw = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(keyFor(conversationId), null)
        }.getOrNull() ?: return null
        return decodeChatScrollAnchor(raw)
    }

    fun save(context: Context, conversationId: Long, anchor: ChatScrollAnchor) {
        if (conversationId <= 0L || anchor.messageId <= 0L) return
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit { putString(keyFor(conversationId), encodeChatScrollAnchor(anchor)) }
        }
    }

    fun clear(context: Context, conversationId: Long) {
        if (conversationId <= 0L) return
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit { remove(keyFor(conversationId)) }
        }
    }

    private fun keyFor(conversationId: Long): String = "position_$conversationId"
}

internal fun encodeChatScrollAnchor(anchor: ChatScrollAnchor): String {
    val group = anchor.groupId?.let { escapeAnchorGroup(it) } ?: "-"
    return "v1|g=$group|m=${anchor.messageId}|o=${anchor.offset}"
}

internal fun decodeChatScrollAnchor(raw: String): ChatScrollAnchor? {
    if (!raw.startsWith("v1|")) return null
    val parts = raw.split('|')
    if (parts.size != 4) return null
    val group = parts[1].removePrefix("g=").takeIf { it.isNotBlank() && it != "-" }
        ?.let { unescapeAnchorGroup(it) }
    val messageId = parts[2].removePrefix("m=").toLongOrNull() ?: return null
    val offset = parts[3].removePrefix("o=").toIntOrNull() ?: 0
    if (messageId <= 0L) return null
    return ChatScrollAnchor(groupId = group, messageId = messageId, offset = offset.coerceAtLeast(0))
}

/**
 * 分组 id 只用在自己的 SharedPreferences 值里，手工转义分隔符即可；
 * 不依赖 android.net.Uri，纯 JVM 单元测试可直接验证往返。
 */
private fun escapeAnchorGroup(raw: String): String = raw
    .replace("%", "%25")
    .replace("|", "%7C")
    .replace("=", "%3D")

private fun unescapeAnchorGroup(raw: String): String = raw
    .replace("%7C", "|")
    .replace("%3D", "=")
    .replace("%25", "%")
