package com.aiassistant

import android.app.Application
import com.aiassistant.ui.search.buildSearchSnippet
import com.aiassistant.ui.search.highlightRanges
import com.aiassistant.ui.search.matchesAllTerms
import com.aiassistant.ui.search.searchTerms
import com.aiassistant.ui.search.titleMatchScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 对话搜索策略回归：
 * 1. 查询词必须去空白、拆词、去重（旧实现把整串交给 SQL，尾随空格与多词输入直接 0 结果）；
 * 2. 片段必须在没有命中时返回 null（旧实现 indexOf=-1 时从 0 切片，展示与关键词无关的内容）；
 * 3. 高亮区间必须与片段文本对应，且不截断 emoji。
 */
class ConversationSearchTest {

    @Test
    fun searchTermsTrimsCollapsesAndSplits() {
        assertEquals(listOf("项目", "需求"), searchTerms("  项目   需求  "))
    }

    @Test
    fun searchTermsIsCaseInsensitiveAndDistinct() {
        assertEquals(listOf("ab"), searchTerms("Ab aB ab"))
    }

    @Test
    fun searchTermsIsCappedAtDaoParameterCount() {
        assertEquals(listOf("a", "b", "c", "d"), searchTerms("a b c d e"))
    }

    @Test
    fun blankQueryProducesNoTerms() {
        assertTrue(searchTerms("   ").isEmpty())
    }

    @Test
    fun allTermsMustMatch() {
        assertTrue(matchesAllTerms("项目需求文档", listOf("项目", "需求")))
        assertFalse(matchesAllTerms("项目需求文档", listOf("项目", "设计")))
        assertTrue(matchesAllTerms("任意文本", emptyList()))
    }

    @Test
    fun snippetIsNullWhenNoTermMatches() {
        assertNull(buildSearchSnippet("这是一段完全没有关键词的正文", listOf("需求")))
    }

    @Test
    fun snippetIsNullForBlankContent() {
        assertNull(buildSearchSnippet("   \n  ", listOf("需求")))
    }

    @Test
    fun snippetCentersOnFirstMatchAndMarksIt() {
        val content = "前文".repeat(50) + "项目需求" + "后文".repeat(50)
        val snippet = buildSearchSnippet(content, listOf("需求"), contextChars = 12)
        requireNotNull(snippet)
        assertTrue(snippet.text.contains("需求"))
        assertTrue(snippet.truncatedStart)
        assertTrue(snippet.truncatedEnd)
        val highlighted = snippet.highlights.joinToString("") { snippet.text.substring(it.first, it.last + 1) }
        assertEquals("需求", highlighted)
    }

    @Test
    fun snippetHighlightsEveryOccurrenceIgnoringCase() {
        val snippet = buildSearchSnippet("Alpha beta ALPHA", listOf("alpha"), contextChars = 50)
        requireNotNull(snippet)
        assertEquals(2, snippet.highlights.size)
        assertFalse(snippet.truncatedStart)
        assertFalse(snippet.truncatedEnd)
    }

    @Test
    fun snippetDoesNotSplitSurrogatePairs() {
        val content = "\uD83D\uDE00".repeat(40) + "关键词" + "\uD83D\uDE00".repeat(40)
        val snippet = buildSearchSnippet(content, listOf("关键词"), contextChars = 10)
        requireNotNull(snippet)
        assertFalse(snippet.text.first().isLowSurrogate())
        assertFalse(snippet.text.last().isHighSurrogate())
        assertTrue(snippet.text.contains("关键词"))
    }

    @Test
    fun highlightRangesMergeOverlaps() {
        assertEquals(listOf(0..2), highlightRanges("aaa", listOf("aa", "a")))
    }

    @Test
    fun titleScoreRanksExactAbovePrefixAndContains() {
        val terms = listOf("需求")
        val exact = titleMatchScore("需求", terms)
        val prefix = titleMatchScore("需求评审记录", terms)
        val contains = titleMatchScore("本周需求评审记录", terms)
        assertTrue(exact > prefix)
        assertTrue(prefix > contains)
        assertTrue(contains > 0)
    }

    @Test
    fun titleScoreIgnoresEmptyTerms() {
        assertEquals(0, titleMatchScore("任意标题", emptyList()))
    }
}

/** 历史页路由必须编码查询词，否则含 & 或 # 的关键词会被截断。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class HistoryRouteTest {

    @Test
    fun blankQueryUsesPlainHistoryRoute() {
        assertEquals("history", historyRoute("   "))
    }

    @Test
    fun queryIsEncoded() {
        val route = historyRoute("项目 & 需求")
        assertTrue(route.startsWith("history?query="))
        assertTrue(route.contains("%26"))
        assertFalse(route.contains("&需求"))
    }

    @Test
    fun plainQueryIsEncodedForNavigation() {
        assertEquals("history?query=%E9%9C%80%E6%B1%82", historyRoute("  需求  "))
    }
}
