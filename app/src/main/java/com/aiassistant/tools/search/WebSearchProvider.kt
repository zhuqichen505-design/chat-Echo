package com.aiassistant.tools.search

import com.aiassistant.utils.WebSearchBundle

enum class SearchEngineType(val displayName: String) {
    EXA("Exa"),
    TAVILY("Tavily"),
    MWMBL("Mwmbl"),
    SEARXNG("SearXNG");

    companion object {
        fun fromValue(value: String): SearchEngineType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EXA
        }
    }
}

object SearchLimits {
    const val MAX_REQUESTED = 100
    const val TAVILY_MAX = 20
}

interface WebSearchProvider {
    val engineType: SearchEngineType
    fun isReady(): Boolean
    fun search(query: String, maxResults: Int = 5): Result<WebSearchBundle>
}
