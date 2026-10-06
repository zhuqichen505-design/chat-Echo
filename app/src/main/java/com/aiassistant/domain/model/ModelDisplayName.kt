package com.aiassistant.domain.model

import java.util.Locale

/** Display-only transformation. Never use this value for persistence, matching or API requests. */
object ModelDisplayName {
    private val words = Regex("[A-Za-z]+")
    private val acronyms = setOf("gpt", "glm", "api", "llm", "vl", "vlm", "it", "fp", "bf", "gguf", "a")
    private val brands = mapOf("deepseek" to "DeepSeek", "minimax" to "MiniMax", "openai" to "OpenAI", "chatgpt" to "ChatGPT")
    private val compounds = Regex("(?i)^(gemini|gpt|claude|deepseek|qwen|kimi|glm|minimax)?(flash|pro|lite|preview|thinking|instruct|coder|code|chat|latest|mini|nano|sol|luna)+$")
    private val pieces = Regex("(?i)gemini|deepseek|minimax|claude|qwen|kimi|gpt|glm|instruct|thinking|preview|latest|flash|coder|luna|nano|mini|lite|code|chat|sol|pro")

    fun format(rawId: String): String = words.replace(rawId.trim()) { match ->
        val word = match.value
        if (compounds.matches(word)) pieces.findAll(word).joinToString("") { title(it.value) }
        else title(word)
    }

    private fun title(word: String): String {
        val lower = word.lowercase(Locale.ROOT)
        return brands[lower] ?: if (lower in acronyms) lower.uppercase(Locale.ROOT)
        else word.replaceFirstChar { it.uppercaseChar() }
    }
}
