package com.aiassistant

import com.aiassistant.tools.search.*
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
}
