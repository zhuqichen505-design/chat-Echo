package com.aiassistant.ui.search

import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Message

/**
 * 与 MessageDao.searchMessagesIn 的查询参数个数保持一致。
 * 超过该数量的关键词只在内存里参与匹配，不再进入 SQL 粗筛。
 */
internal const val SearchTermLimit = 4

/** 一次消息搜索最多展示的命中条数；DAO 多取一条用于判断是否截断。 */
internal const val SearchMessageHitLimit = 200

private val WhitespaceRun = Regex("\\s+")

/**
 * 搜索片段：已完成空白折叠的正文窗口 + 相对窗口的高亮区间。
 *
 * 片段不包含省略号，由 UI 依据 [truncatedStart] / [truncatedEnd] 自行拼接，
 * 这样高亮区间始终基于 [text] 的真实下标。
 */
data class SearchSnippet(
    val text: String,
    val highlights: List<IntRange>,
    val truncatedStart: Boolean,
    val truncatedEnd: Boolean
)

/** 一条消息命中：对话用于展示归属，片段用于高亮预览。 */
data class MessageSearchHit(
    val conversation: Conversation,
    val message: Message,
    val snippet: SearchSnippet
)

/** 折叠换行与连续空白，避免预览被 Markdown 换行撑散。 */
internal fun normalizeSearchText(text: String): String =
    text.replace(WhitespaceRun, " ").trim()

/**
 * 拆分查询词：去首尾空白、折叠空白、忽略大小写、去重，最多保留 [SearchTermLimit] 个。
 *
 * 关键字搜索的旧实现把整串直接交给 SQL，导致“项目 需求”这类多词输入必然搜不到；
 * 拆词后各词按 AND 语义匹配，符合用户对搜索框的常规预期。
 */
internal fun searchTerms(raw: String): List<String> =
    normalizeSearchText(raw)
        .lowercase()
        .split(' ')
        .filter { it.isNotEmpty() }
        .distinct()
        .take(SearchTermLimit)

/** 全部关键词都出现在文本中才算命中；空关键词视为命中（不做过滤）。 */
internal fun matchesAllTerms(text: String, terms: List<String>): Boolean =
    terms.all { term -> text.contains(term, ignoreCase = true) }

/**
 * 对话标题相关度：完全相等 > 前缀 > 全部包含 > 命中词数。
 * 用于把“标题就是关键词”的对话排到搜索列表前面。
 */
internal fun titleMatchScore(title: String, terms: List<String>): Int {
    if (terms.isEmpty()) return 0
    val normalized = normalizeSearchText(title).lowercase()
    if (normalized.isEmpty()) return 0
    var score = 0
    if (normalized == terms.joinToString(" ")) score += 100
    if (normalized.startsWith(terms.first())) score += 30
    if (matchesAllTerms(normalized, terms)) score += 20
    terms.forEach { term -> if (normalized.contains(term)) score += 5 }
    return score
}

/**
 * 围绕最早命中的关键词截取预览窗口。
 *
 * 返回 null 表示正文里没有任何关键词——调用方必须丢弃该条命中，
 * 不能像旧实现那样在 `indexOf == -1` 时从字符串开头切片，否则会展示与关键词无关的内容。
 */
internal fun buildSearchSnippet(
    content: String,
    terms: List<String>,
    contextChars: Int = 36
): SearchSnippet? {
    if (terms.isEmpty()) return null
    val normalized = normalizeSearchText(content)
    if (normalized.isEmpty()) return null

    val first = terms
        .mapNotNull { term -> normalized.indexOf(term, ignoreCase = true).takeIf { it >= 0 } }
        .minOrNull()
        ?: return null

    var start = (first - contextChars).coerceAtLeast(0)
    var end = (first + contextChars * 2).coerceAtMost(normalized.length)
    // 保证窗口至少覆盖第一个命中词的完整长度
    end = end.coerceAtLeast((first + terms.first().length).coerceAtMost(normalized.length))
    // 不要从代理对（emoji 等）中间截断，否则预览会出现半个字符
    if (start > 0 && normalized[start].isLowSurrogate()) start += 1
    if (end < normalized.length && normalized[end - 1].isHighSurrogate()) end -= 1
    if (start >= end) return null

    val window = normalized.substring(start, end)
    val highlights = highlightRanges(window, terms)
    if (highlights.isEmpty()) return null
    return SearchSnippet(
        text = window,
        highlights = highlights,
        truncatedStart = start > 0,
        truncatedEnd = end < normalized.length
    )
}

/** 找出窗口内全部关键词出现位置，合并重叠或相邻区间，保证高亮不重复叠加。 */
internal fun highlightRanges(text: String, terms: List<String>): List<IntRange> {
    if (text.isEmpty() || terms.isEmpty()) return emptyList()
    val raw = mutableListOf<IntRange>()
    terms.forEach { term ->
        if (term.isEmpty()) return@forEach
        var from = 0
        while (from <= text.length - term.length) {
            val at = text.indexOf(term, from, ignoreCase = true)
            if (at < 0) break
            raw += at until (at + term.length)
            from = at + term.length
        }
    }
    if (raw.isEmpty()) return emptyList()
    raw.sortBy { it.first }
    val merged = mutableListOf<IntRange>()
    raw.forEach { range ->
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
    }
    return merged
}
