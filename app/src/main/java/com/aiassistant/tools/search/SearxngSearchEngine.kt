package com.aiassistant.tools.search

import com.aiassistant.utils.WebSearchBundle
import com.aiassistant.utils.WebSearchDocument
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Uses a user-selected JSON-enabled instance; never sends queries to random public hosts. */
class SearxngSearchEngine(private val instanceUrl: () -> String) : WebSearchProvider {
    override val engineType = SearchEngineType.SEARXNG
    override fun isReady() = instanceUrl().isNotBlank()
    private val client = com.aiassistant.data.remote.HttpAccessPolicy.guard(OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    override fun search(query: String, maxResults: Int): Result<WebSearchBundle> = runCatching {
        require(query.isNotBlank()) { "搜索关键词为空" }
        val base = instanceUrl().trim().trimEnd('/').toHttpUrl()
        require(base.isHttps) { "SearXNG 实例必须使用 HTTPS" }
        val limit = maxResults.coerceIn(1, SearchLimits.MAX_REQUESTED)
        val results = linkedMapOf<String, WebSearchDocument>()
        for (page in 1..10) {
            val url = base.newBuilder().addPathSegment("search")
                .addQueryParameter("q", query.trim()).addQueryParameter("format", "json")
                .addQueryParameter("pageno", page.toString()).build()
            val docs = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
                check(response.isSuccessful) { "SearXNG 请求失败 (HTTP ${response.code})；请检查实例是否允许 JSON 搜索" }
                parseResults(response.body?.string().orEmpty())
            }
            val before = results.size
            docs.forEach { results.putIfAbsent(it.url, it) }
            if (results.size >= limit || results.size == before) break
        }
        check(results.isNotEmpty()) { "SearXNG 未检索到结果" }
        WebSearchBundle(query.trim(), null, results.values.take(limit))
    }

    companion object {
        fun parseResults(body: String): List<WebSearchDocument> =
            JsonParser.parseString(body).asJsonObject.getAsJsonArray("results")?.mapNotNull { item ->
                val obj = item.asJsonObject
                val url = obj.get("url")?.asString.orEmpty()
                if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapNotNull null
                WebSearchDocument(obj.get("title")?.asString.orEmpty().ifBlank { url }, url, obj.get("content")?.asString.orEmpty().take(1500))
            }.orEmpty()
    }
}
