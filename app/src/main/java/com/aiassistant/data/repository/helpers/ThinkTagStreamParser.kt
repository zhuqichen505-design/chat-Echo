package com.aiassistant.data.repository.helpers

data class ThinkTagDelta(
    val contentDelta: String = "",
    val thinkingDelta: String = ""
)

/**
 * 将正文中的 think 标签按流分离。只暂存可能构成标签的末尾片段，避免标签跨 chunk
 * 时泄漏到正文；普通小于号和其他 HTML 标签仍按原文输出。
 */
class ThinkTagStreamParser {
    private val pending = StringBuilder()
    private var inThinking = false

    fun feed(contentDelta: String): ThinkTagDelta {
        pending.append(contentDelta)
        val content = StringBuilder()
        val thinking = StringBuilder()

        fun appendText(text: String) {
            if (inThinking) thinking.append(text) else content.append(text)
        }

        while (pending.isNotEmpty()) {
            val tag = if (inThinking) "</think>" else "<think>"
            val text = pending.toString()
            val tagStart = text.indexOf(tag, ignoreCase = true)
            if (tagStart >= 0) {
                appendText(text.substring(0, tagStart))
                pending.delete(0, tagStart + tag.length)
                inThinking = !inThinking
                continue
            }

            // 最多保留 tag.length - 1 个字符；不可能成为当前标签的文字立即放行。
            val retainedLength = (minOf(text.length, tag.length - 1) downTo 1)
                .firstOrNull { size -> text.regionMatches(text.length - size, tag, 0, size, ignoreCase = true) }
                ?: 0
            val emittedLength = text.length - retainedLength
            appendText(text.substring(0, emittedLength))
            pending.delete(0, emittedLength)
            break
        }

        return ThinkTagDelta(content.toString(), thinking.toString())
    }

    /** 流结束时冲刷半标签；未闭合的 think 区间仍归入思考，不能混入正文。 */
    fun finish(): ThinkTagDelta {
        val tail = pending.toString()
        pending.clear()
        return if (inThinking) ThinkTagDelta(thinkingDelta = tail) else ThinkTagDelta(contentDelta = tail)
    }
}
