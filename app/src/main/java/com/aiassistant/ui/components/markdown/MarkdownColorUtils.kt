package com.aiassistant.ui.components.markdown

import androidx.compose.ui.graphics.Color

/**
 * Markdown 与富文本标签颜色解析与 HTML 实体解码工具
 */
object MarkdownColorUtils {

    fun parseHtmlTagColor(tag: String): Color? {
        val colorAttr = Regex("""color\s*=\s*["']?([^"'\s>]+)""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.getOrNull(1)
        val styleColor = Regex("""color\s*:\s*([^;"'>]+)""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.getOrNull(1)
        val parsed = parseInlineColor(colorAttr ?: styleColor)
        if (parsed != null) return parsed
        // If tag explicitly specified color="..." but it was non-standard/typo, fallback to stylish blue
        if (colorAttr != null || styleColor != null) {
            return Color(0xFF2B7DE0)
        }
        return null
    }

    fun parseInlineColor(raw: String?): Color? {
        val value = raw?.trim()?.trim('"', '\'')?.lowercase().orEmpty()
        if (value.isBlank()) return null
        if (value.startsWith("rgb")) {
            val parts = value.substringAfter("(").substringBefore(")").split(",").map { it.trim() }
            if (parts.size >= 3) {
                val r = parts[0].toIntOrNull()?.coerceIn(0, 255) ?: 0
                val g = parts[1].toIntOrNull()?.coerceIn(0, 255) ?: 0
                val b = parts[2].toIntOrNull()?.coerceIn(0, 255) ?: 0
                val a = if (parts.size >= 4) (parts[3].toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f) else 1f
                return Color(r, g, b, (a * 255).toInt().coerceIn(0, 255))
            }
        }
        val named = when (value) {
            "red" -> "#DC2626"
            "orange" -> "#EA580C"
            "yellow", "gold" -> "#CA8A04"
            "green" -> "#16A34A"
            "blue" -> "#2563EB"
            "purple" -> "#7C3AED"
            "pink" -> "#DB2777"
            "gray", "grey" -> "#64748B"
            "black" -> "#111827"
            "white" -> "#FFFFFF"
            "cyan" -> "#06B6D4"
            "teal" -> "#0D9488"
            "indigo" -> "#4F46E5"
            "violet" -> "#8B5CF6"
            else -> value
        }
        // Clean string (e.g. remove # or 0x)
        var hex = named.removePrefix("#").removePrefix("0x").lowercase()

        // Support non-standard hex like "2b7dep" -> map 'p' or other typos to valid hex digits
        if (hex.length == 6 || hex.length == 8) {
            hex = hex.map { c ->
                when (c) {
                    in '0'..'9', in 'a'..'f' -> c
                    'p', 'o' -> '0'
                    'l', 'i' -> '1'
                    else -> '0'
                }
            }.joinToString("")
        } else if (hex.length == 3) {
            if (hex.all { it in '0'..'9' || it in 'a'..'f' }) {
                hex = "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}"
            }
        }

        val hexValue = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            6 -> Color(hexValue or 0xFF000000L)
            8 -> Color(hexValue)
            else -> null
        }
    }

    /**
     * 解码 HTML 实体
     */
    fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&times;", "×")
    }
}
