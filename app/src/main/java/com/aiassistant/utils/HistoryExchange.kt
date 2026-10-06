package com.aiassistant.utils

import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Message
import com.google.gson.JsonParser
import java.time.Instant

/** Parse completely before starting the database transaction. */
object HistoryExchange {
    data class Import(val title: String, val model: String, val messages: List<Message>)

    fun parse(json: String, fallbackModel: String, now: Long = System.currentTimeMillis()): Import {
        val root = JsonParser.parseString(json).asJsonObject
        val title = root.get("title")?.asString?.takeIf { it.isNotBlank() } ?: "导入对话"
        val model = root.get("model")?.asString?.takeIf { it.isNotBlank() } ?: fallbackModel
        val messages = requireNotNull(root.getAsJsonArray("messages")) { "缺少消息列表" }.mapIndexed { index, element ->
            val item = element.asJsonObject
            val role = requireNotNull(item.get("role")) { "缺少消息角色" }.asString
            require(role in setOf("user", "assistant", "system", "tool")) { "无效消息角色" }
            val timestamp = item.get("timestamp")?.takeUnless { it.isJsonNull }?.let {
                if (it.asJsonPrimitive.isNumber) it.asLong else Instant.parse(it.asString).toEpochMilli()
            } ?: now + index
            Message(conversationId = 0, role = role,
                content = requireNotNull(item.get("content")) { "缺少消息内容" }.asString,
                thinkingContent = item.get("thinkingContent")?.takeUnless { it.isJsonNull }?.asString,
                attachments = item.get("attachments")?.takeUnless { it.isJsonNull }?.asString,
                createdAt = timestamp)
        }
        return Import(title, model, messages)
    }
}
