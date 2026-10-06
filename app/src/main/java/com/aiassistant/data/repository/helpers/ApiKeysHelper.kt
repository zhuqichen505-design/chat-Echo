package com.aiassistant.data.repository.helpers

import com.aiassistant.domain.model.NamedApiKey

/**
 * API Key 解析、格式化与状态提取助手
 */
object ApiKeysHelper {

    /**
     * 解析具名或纯文本 API Key 列表（支持 [名称] sk-xxx 与 名称:::sk-xxx 格式，以及 [已禁用] 状态标签）
     */
    fun parseNamedApiKeys(rawKey: String?): List<NamedApiKey> {
        if (rawKey.isNullOrBlank()) return emptyList()
        val lines = rawKey.split(Regex("[\\n,;]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<NamedApiKey>()
        for (line in lines) {
            var isEnabled = true
            var workingLine = line
            val disableTagMatch = Regex("""^[\[【](?:已禁用|禁用|off|disabled|关闭)[\]】]\s*""", RegexOption.IGNORE_CASE).find(workingLine)
            if (disableTagMatch != null) {
                isEnabled = false
                workingLine = workingLine.substring(disableTagMatch.range.last + 1).trim()
            }

            // 格式 1: [名称] key
            val bracketMatch = Regex("""^[\[【]([^\[\]【】]+)[\]】]\s*(.+)$""").find(workingLine)
            if (bracketMatch != null) {
                val name = bracketMatch.groupValues[1].trim()
                val key = bracketMatch.groupValues[2].trim()
                if (key.isNotEmpty()) {
                    result.add(NamedApiKey(name = name, key = key, isEnabled = isEnabled))
                    continue
                }
            }
            // 格式 2: 名称:::key
            val colonMatch = Regex("""^([^:]+):::\s*(.+)$""").find(workingLine)
            if (colonMatch != null) {
                val name = colonMatch.groupValues[1].trim()
                val key = colonMatch.groupValues[2].trim()
                if (key.isNotEmpty()) {
                    result.add(NamedApiKey(name = name, key = key, isEnabled = isEnabled))
                    continue
                }
            }
            // 格式 3: 纯 key
            if (workingLine.isNotEmpty()) {
                result.add(NamedApiKey(name = "", key = workingLine, isEnabled = isEnabled))
            }
        }
        return result
    }

    /**
     * 格式化具名 API Key 为多行持久化文本（支持保存停用状态）
     */
    fun formatNamedApiKeys(keys: List<NamedApiKey>): String {
        return keys.filter { it.key.isNotBlank() }.joinToString("\n") { item ->
            val cleanKey = item.key.trim()
            val cleanName = item.name.trim()
            val disablePrefix = if (!item.isEnabled) "[已禁用] " else ""
            if (cleanName.isNotBlank()) {
                "$disablePrefix[$cleanName] $cleanKey"
            } else {
                "$disablePrefix$cleanKey"
            }
        }
    }

    /**
     * 提取纯净 API Key 列表（自动剥离名称前缀，仅返回处于启用状态的有效 Key，杜绝网络层脏标头）
     */
    fun parseApiKeys(rawKey: String?): List<String> {
        if (rawKey.isNullOrBlank()) return emptyList()
        return parseNamedApiKeys(rawKey)
            .filter { it.isEnabled }
            .map { it.key.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
