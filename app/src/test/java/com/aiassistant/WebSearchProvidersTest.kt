package com.aiassistant

import com.aiassistant.tools.search.*
import com.aiassistant.domain.model.ToolCallRecord
import com.aiassistant.ui.components.markdown.MarkdownInlineParser
import com.aiassistant.ui.screens.chat.citationForIndex
import com.aiassistant.ui.screens.chat.extractCitationsFromContent
import com.aiassistant.utils.WebSearchBundle
import com.aiassistant.utils.WebSearchDocument
import org.junit.Assert.*
import org.junit.Test

class WebSearchProvidersTest {
    @Test fun exaCountUsesOfficialCamelCaseArgument() {
        val args = ExaSearchEngine.buildSearchPayload(" Kotlin ", 60).getAsJsonObject("params").getAsJsonObject("arguments")
        assertEquals("Kotlin", args.get("query").asString)
        assertEquals(60, args.get("numResults").asInt)
        assertFalse(args.has("num_results"))
        assertEquals(100, ExaSearchEngine.buildSearchPayload("x", 1000).getAsJsonObject("params").getAsJsonObject("arguments").get("numResults").asInt)
    }

    @Test fun mwmblParsesSegmentedTitlesAndDeduplicatesWithoutFakeResults() {
        val body = """[{"url":"https://kotlinlang.org","title":[{"value":"Kotlin","is_bold":true},{"value":" Docs","is_bold":false}],"extract":[{"value":"Official documentation"}]},{"url":"https://kotlinlang.org","title":[],"extract":[]},{"url":"javascript:bad","title":[],"extract":[]}]"""
        val docs = MwmblSearchEngine.parseResults(body)
        assertEquals(1, docs.size)
        assertEquals("Kotlin Docs", docs.single().title)
        assertEquals("Official documentation", docs.single().content)
        assertEquals(emptyList<Any>(), MwmblSearchEngine.parseResults("[]"))
    }

    @Test fun searxngParsesJsonAndRejectsInvalidSchemes() {
        val docs = SearxngSearchEngine.parseResults("""{"results":[{"url":"https://example.org","title":"Reference","content":"Summary"},{"url":"file:///bad"}]}""")
        assertEquals(1, docs.size)
        assertEquals("Summary", docs.single().content)
        assertFalse(SearxngSearchEngine { "" }.isReady())
        assertEquals(SearchEngineType.MWMBL, SearchEngineType.fromValue("mwmbl"))
    }

    @Test fun citationReferencesPreserveNumbersAndActualUrlsAcrossModelFormats() {
        val content = """
            资料见[1][2]与[^3]。
            [3] [第三项](https://example.org/three_(detail))
            [1]: https://example.org/one
            [^2]: 第二项 https://example.org/two。
            7. [第七项](https://example.org/seven)
            [9](https://example.org/nine)
            - [5] 第五项 https://example.org/find?a=1&amp;b=2
            [6] [同一来源另一编号](https://example.org/one)
        """.trimIndent()
        val byIndex = extractCitationsFromContent(content).associateBy { it.index }
        assertEquals(setOf(1, 2, 3, 5, 6, 7, 9), byIndex.keys)
        assertEquals("https://example.org/one", byIndex.getValue(1).url)
        assertEquals("https://example.org/two", byIndex.getValue(2).url)
        assertEquals("https://example.org/three_(detail)", byIndex.getValue(3).url)
        assertEquals("https://example.org/find?a=1&b=2", byIndex.getValue(5).url)
        assertEquals(byIndex.getValue(1).url, byIndex.getValue(6).url)
        assertEquals("https://example.org/seven", byIndex.getValue(7).url)
        assertEquals("https://example.org/nine", byIndex.getValue(9).url)
        assertEquals("第三项", byIndex.getValue(3).title)
    }

    @Test fun inlineCitationMarkersAndNumericLinksKeepSeparateClickableDestinations() {
        val parsed = MarkdownInlineParser.parseInlineMarkdown(
            "正文[1][^2]和[3, 4；5]，以及[7](https://example.org/page_(one)?a=1&amp;b=2 \"网页标题\")。"
        )
        val citations = parsed.getStringAnnotations("CITATION", 0, parsed.length)
        assertEquals(listOf("1", "2", "3", "4", "5"), citations.map { it.item })
        citations.forEach { annotation ->
            assertEquals("[${annotation.item}]", parsed.text.substring(annotation.start, annotation.end))
        }
        val url = parsed.getStringAnnotations("URL", 0, parsed.length).single()
        assertEquals("https://example.org/page_(one)?a=1&b=2", url.item)
        assertEquals("[7]", parsed.text.substring(url.start, url.end))
        assertFalse(parsed.text.contains("https://"))
        assertFalse(citations.any { it.item == "7" })
        val literalCode = MarkdownInlineParser.parseInlineMarkdown("代码 `[8]` 与正常引用[9]")
        assertEquals(listOf("9"), literalCode.getStringAnnotations("CITATION", 0, literalCode.length).map { it.item })
    }

    @Test fun missingReferenceUrlsUseThisMessagesSuccessfulSearchSources() {
        val record = searchRecord(
            WebSearchDocument("第一项", "https://example.org/first", "第一份合成摘要"),
            WebSearchDocument("第二项", "https://example.org/second", "第二份合成摘要")
        )
        val citations = extractCitationsFromContent("模型只给出了角标[2]。", listOf(record))
        assertEquals("https://example.org/second", citationForIndex(citations, 2).url)
        assertEquals("第二项", citationForIndex(citations, 2).title)
        assertTrue(extractCitationsFromContent("只有[2]。", listOf(record.copy(isSuccess = false))).isEmpty())
        assertTrue(extractCitationsFromContent("只有[2]。", listOf(record.copy(toolType = "JINA_READER"))).isEmpty())
    }

    @Test fun explicitReplyReferencesTakePriorityOverSearchNumbering() {
        val record = searchRecord(WebSearchDocument("搜索来源", "https://example.org/search", "合成摘要"))
        val citations = extractCitationsFromContent("正文[1]\n[1]: https://example.org/reply", listOf(record))
        assertEquals("https://example.org/reply", citationForIndex(citations, 1).url)
    }

    @Test fun conflictingSearchNumbersAndUnknownCitationsNeverInventUrls() {
        val first = searchRecord(WebSearchDocument("第一轮", "https://example.org/first", "合成摘要"))
        val second = searchRecord(WebSearchDocument("第二轮", "https://example.org/second", "合成摘要"))
        val citations = extractCitationsFromContent("只有[1]。", listOf(first, second))
        assertTrue(citations.isEmpty())
        val missing = citationForIndex(citations, 1)
        assertEquals(1, missing.index)
        assertEquals("", missing.url)
        assertEquals("", citationForIndex(emptyList(), 99).url)
    }

    @Test fun unnumberedLinksDoNotDiscardOrRenumberExplicitReferences() {
        val citations = extractCitationsFromContent(
            "[补充资料](https://example.org/extra)\n[4] [明确编号](https://example.org/four)"
        )
        assertEquals(listOf(1, 4), citations.map { it.index })
        assertEquals("https://example.org/four", citationForIndex(citations, 4).url)
        assertEquals("https://example.org/extra", citationForIndex(citations, 1).url)
    }

    @Test fun referencesIgnoreCodeImagesAndUnsupportedSchemes() {
        val citations = extractCitationsFromContent(
            """
                ```markdown
                [1] [代码示例](https://example.org/code)
                ```
                ![配图](https://example.org/image.png)
                [2](javascript:alert(1))
                [文件](file:///example.txt)
                [3]:
                    <https://example.org/valid>
            """.trimIndent()
        )
        assertEquals(listOf(3), citations.map { it.index })
        assertEquals("https://example.org/valid", citations.single().url)
    }

    @Test fun markdownDestinationSupportsAnglesTitlesAndLiteralUrlPunctuation() {
        val parsed = MarkdownInlineParser.parseInlineMarkdown("[资料](<https://example.org/path!> '说明')")
        assertEquals("资料", parsed.text)
        assertEquals("https://example.org/path!", parsed.getStringAnnotations("URL", 0, parsed.length).single().item)
        assertEquals("https://example.org/path!", extractCitationsFromContent("[1] [资料](https://example.org/path!)").single().url)
    }

    @Test fun citationRegexesRemainCompatibleWithAndroidIcuCharacterClasses() {
        val fields = Class.forName("com.aiassistant.ui.screens.chat.ChatMessageComponentsKt").declaredFields
            .filter { it.name.startsWith("CITATION_") && it.type == Regex::class.java }
        assertFalse(fields.isEmpty())
        fields.forEach { field ->
            field.isAccessible = true
            val regex = field.get(null) as Regex
            java.util.regex.Pattern.compile(com.ibm.icu.impl.UnicodeRegex.fix(regex.pattern))
        }
    }

    private fun searchRecord(vararg documents: WebSearchDocument): ToolCallRecord = ToolCallRecord(
        toolType = "WEB_SEARCH",
        toolName = "合成搜索",
        summary = "合成测试资料",
        detailContent = WebSearchBundle("合成查询", null, documents.toList()).toPromptBlock()
    )
}
