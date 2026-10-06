package com.aiassistant.data.repository.helpers

import com.aiassistant.domain.model.ChatCompletionChunk
import com.aiassistant.domain.model.Usage
import com.google.gson.Gson
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

        return try {
            val chunk = gson.fromJson(data, ChatCompletionChunk::class.java)
            val choice = chunk.choices?.firstOrNull()
            val finishReason = choice?.finish_reason

            var contentDelta = choice?.delta?.content
            var thinkingDelta = choice?.delta?.reasoning_content
                ?: choice?.delta?.reasoning_content_camel
                ?: choice?.delta?.reasoningContent
                ?: choice?.delta?.reasoning
                ?: choice?.delta?.thinking
                ?: choice?.delta?.thinking_content
                ?: choice?.delta?.thought

            // 兼容非标准将输出置于 choices[0].message 的中转网关
            if (contentDelta == null && thinkingDelta == null && data.contains("\"message\"")) {
                try {
                    val jsonObj = JsonParser.parseString(data).asJsonObject
                    val firstChoice = jsonObj.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    val msgObj = firstChoice?.getAsJsonObject("message")
                    if (msgObj != null) {
                        if (msgObj.has("content") && !msgObj.get("content").isJsonNull) {
                            contentDelta = msgObj.get("content").asString
                        }
                        if (msgObj.has("reasoning_content") && !msgObj.get("reasoning_content").isJsonNull) {
                            thinkingDelta = msgObj.get("reasoning_content").asString
                        }
                    }
                } catch (_: Exception) {}
            }

            OpenAiStreamChunkResult(
                contentDelta = contentDelta,
                thinkingDelta = thinkingDelta,
                finishReason = finishReason,
                usage = chunk.usage
            )
        } catch (_: Exception) {
            null
        }
    }
}
