package com.aiassistant.data.repository.helpers

import com.aiassistant.domain.model.AnthropicContent
import com.aiassistant.domain.model.ContentPart

/**
 * Token 估算、语言判定与思考深度参数处理助手
 */
object TokenEstimationHelper {

    fun isMainlyEnglish(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val nonWhitespace = text.filterNot { it.isWhitespace() }
        if (nonWhitespace.length < 15) return false

        var chineseCount = 0
        var latinCount = 0
        for (ch in nonWhitespace) {
            if (ch in '\u4e00'..'\u9fa5') {
                chineseCount++
            } else if ((ch in 'a'..'z') || (ch in 'A'..'Z')) {
                latinCount++
            }
        }
        return latinCount >= 30 && chineseCount < (latinCount * 0.15)
    }

    fun normalizeThinkingEffort(effort: String?, providerType: String = "openai", modelName: String? = null): String {
        if (effort?.startsWith("budget:") == true) return effort
        // 未指定模型名时保持历史安全映射（max/ultra→high），避免向未知网关发送过高档位
        if (modelName.isNullOrBlank()) {
            return when (effort?.lowercase()?.trim()) {
                "low", "fast" -> "low"
                "medium", "balanced" -> "medium"
                "high", "deep" -> "high"
                "max", "ultra", "xhigh", "ultra_high", "highest" -> "high"
                else -> "medium"
            }
        }
        // 按厂商真实支持档位映射：Kimi/GLM 仅 low/high/max，GPT-6 含 xhigh/max
        val policy = com.aiassistant.domain.model.ModelVendorProfiles.policyFor(modelName)
        return com.aiassistant.domain.model.ModelVendorProfiles.mapThinkingGear(effort, policy)
    }

    fun thinkingBudgetForEffort(effort: String?, configuredBudget: Int): Int {
        effort?.removePrefix("budget:")?.toIntOrNull()?.let { return it.coerceIn(1024, 60000) }
        val base = configuredBudget.coerceIn(1024, 128_000)
        return when (effort?.lowercase()?.trim()) {
            "low", "fast" -> (base / 2).coerceIn(1024, 128_000)
            "high", "deep" -> (base * 2).coerceIn(1024, 128_000)
            "xhigh" -> (base * 3).coerceIn(8_192, 128_000)
            "max", "ultra" -> 32_768.coerceAtLeast(base * 4).coerceAtMost(128_000)
            else -> base
        }
    }

    fun estimateTokenCount(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        val cjkCount = trimmed.count { Character.UnicodeScript.of(it.code) in setOf(
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL
        ) }
        val asciiCount = trimmed.length - cjkCount
        return (cjkCount / 1.7f + asciiCount / 4.0f).toInt().coerceAtLeast(1)
    }

    fun estimateContentTokenCount(content: Any?): Int {
        return when (content) {
            null -> 0
            is String -> estimateTokenCount(content)
            is List<*> -> {
                content.sumOf { part ->
                    when (part) {
                        is ContentPart -> estimateTokenCount(part.text.orEmpty())
                        is AnthropicContent -> estimateTokenCount(part.text.orEmpty())
                        else -> estimateTokenCount(part?.toString().orEmpty())
                    }
                }
            }
            else -> estimateTokenCount(content.toString())
        }
    }

    fun extractContextWindowFromError(message: String): Int? {
        val lower = message.lowercase()
        val explicitPatterns = listOf(
            Regex("""(?:maximum|max)\s+context(?:\s+length)?\s*(?:is|=|:|of)?\s*([1-9]\d{3,6})"""),
            Regex("""context[-_ ]?window\s*(?:is|=|:|of)?\s*([1-9]\d{3,6})"""),
            Regex("""limit\s*(?:of|is)?\s*([1-9]\d{3,6})\s*(?:tokens?|token)"""),
            Regex("""([1-9]\d{3,6})\s*(?:tokens?|token)?\s*(?:max(?:imum)?\s+context|context\s+limit)"""),
            Regex("""(?:context_length_exceeded|model_context_window_exceeded)[^\d]*([1-9]\d{3,6})""")
        )
        for (pattern in explicitPatterns) {
            pattern.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (it in 4_000..2_000_000) return it
            }
        }
        return Regex("""(?<!\d)([1-9]\d{3,6})(?!\d)\s*(?:tokens?|token|上下文|长度)?""")
            .findAll(lower)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 4_000..2_000_000 }
            .minOrNull()
    }

    fun compactMessageForHistory(content: String, limit: Int = Int.MAX_VALUE): String {
        val normalized = content
            .lineSequence()
            .map { it.trimEnd() }
            .joinToString("\n")
            .trim()
        return if (normalized.length <= limit) {
            normalized
        } else {
            normalized.take(limit) + "\n...[内容过长，已截断]"
        }
    }
}
