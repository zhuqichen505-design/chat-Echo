package com.aiassistant.data.repository.helpers

import com.aiassistant.domain.model.ApiResponseText
import com.aiassistant.domain.model.Usage
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * OpenAI 流式 Chunk 解析结果数据载体
 */
data class OpenAiStreamChunkResult(
    val contentDelta: String? = null,
    val thinkingDelta: String? = null,
    val finishReason: String? = null,
    val isDone: Boolean = false,
    val usage: Usage? = null,
    val inlineErrorMessage: String? = null
)

/**
 * 健壮解析单行流式数据助手
 */
object OpenAiStreamChunkParser {

    /**
     * 健壮解析单行流式数据：
     * 1. 兼容响应 Content-Type 不是 text/event-stream 的情况
     * 2. 兼容没有 data: [DONE] 的自然 EOF 终止
     * 3. 兼容没有 finish_reason 的非标准 chunk
     * 4. 兼容行首 BOM (\uFEFF)、多余空白、空行与 SSE 注释行 (: ping / : keepalive)
     * 5. 兼容 NDJSON 格式 ({...}) 与非标准 choices[0].message
     */
    fun parseOpenAiStreamLine(rawLine: String, gson: Gson = Gson()): OpenAiStreamChunkResult? {
        var line = rawLine
        if (line.startsWith("\uFEFF")) {
            line = line.removePrefix("\uFEFF")
        }
        line = line.trim()
        if (line.isEmpty()) return null

        // 忽略 SSE 注释行（: keepalive, : ping）以及 event / id / retry 字段行
        if (line.startsWith(":")) return null
        if (line.startsWith("event:", ignoreCase = true) ||
            line.startsWith("id:", ignoreCase = true) ||
            line.startsWith("retry:", ignoreCase = true)) {
            return null
        }

        // 提取有效数据载荷：兼容 SSE "data: ..." 与 NDJSON "{...}"
        val data = when {
            line.startsWith("data:", ignoreCase = true) -> line.substring(5).trim()
            line.startsWith("{") && line.endsWith("}") -> line
            else -> return null
        }

        if (data.isBlank()) return null

        // 识别结束标记
        if (data == "[DONE]") {
            return OpenAiStreamChunkResult(isDone = true)
        }

        // 检测流式返回的内联错误（如 200 OK 建立连接后第一包返回 error）
        if (data.contains("\"error\"") && (data.contains("\"message\"") || data.contains("\"code\""))) {
            try {
                val errObj = JsonParser.parseString(data).asJsonObject
                if (errObj.has("error")) {
                    val err = errObj.get("error")
                    val msg = if (err.isJsonObject) err.asJsonObject.get("message")?.asString else err.asString
                    if (!msg.isNullOrBlank()) {
                        return OpenAiStreamChunkResult(inlineErrorMessage = msg)
                    }
                }
            } catch (_: Exception) {}
        }

        val root = runCatching { JsonParser.parseString(data) }.getOrNull()?.objectOrNull() ?: return null
        val choice = root.get("choices")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.firstOrNull()?.objectOrNull()
        val delta = choice?.get("delta")?.objectOrNull()
        val message = choice?.get("message")?.objectOrNull()

        // Decode fields independently. A structured/unknown content block must not erase
        // a valid finish reason or usage, and reasoning in delta must not hide message text.
        val contentDelta = ApiResponseText.content(delta?.get("content"))?.takeIf { it.isNotEmpty() }
            ?: ApiResponseText.content(message?.get("content"))
        val thinkingDelta = reasoningFrom(delta) ?: reasoningFrom(message)
        val usage = root.get("usage")?.takeUnless { it.isJsonNull }?.let {
            runCatching { gson.fromJson(it, Usage::class.java) }.getOrNull()
        }
        return OpenAiStreamChunkResult(
            contentDelta = contentDelta,
            thinkingDelta = thinkingDelta,
            finishReason = choice?.get("finish_reason").stringOrNull(),
            usage = usage
        )
    }

    private fun reasoningFrom(message: JsonObject?): String? {
        if (message == null) return null
        return listOf("reasoning_content", "reasoning_content_camel", "reasoningContent", "reasoning", "thinking", "thinking_content", "thought")
            .asSequence()
            .mapNotNull { ApiResponseText.reasoning(message.get(it))?.takeIf(String::isNotEmpty) }
            .firstOrNull()
            ?: ApiResponseText.embeddedReasoning(message.get("content"))
    }

    private fun JsonElement?.objectOrNull(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonElement?.stringOrNull(): String? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
