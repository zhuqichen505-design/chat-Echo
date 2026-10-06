package com.aiassistant.tools.search

import com.aiassistant.utils.WebSearchBundle
import com.aiassistant.utils.WebSearchDocument
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Public, key-free independent index. Coverage and result counts depend on its index. */
class MwmblSearchEngine : WebSearchProvider {
    override val engineType = SearchEngineType.MWMBL
    override fun isReady() = true
    private val client = com.aiassistant.data.remote.HttpAccessPolicy.guard(OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    override fun search(query: String, maxResults: Int): Result<WebSearchBundle> = runCatching {
        require(query.isNotBlank()) { "搜索关键词为空" }
        val url = "https://api.mwmbl.org/search/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query.trim()).build()
        client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
            check(response.isSuccessful) { "Mwmbl 搜索失败 (HTTP ${response.code})" }
            val docs = parseResults(response.body?.string().orEmpty()).take(maxResults.coerceIn(1, SearchLimits.MAX_REQUESTED))
            check(docs.isNotEmpty()) { "Mwmbl 未检索到结果；其独立索引覆盖有限，可切换其他搜索引擎" }
            WebSearchBundle(query.trim(), null, docs)
        }
    }

    companion object {
        fun parseResults(body: String): List<WebSearchDocument> {
            fun text(parts: JsonArray?): String = parts?.joinToString("") {
                it.asJsonObject.get("value")?.asString.orEmpty()
            }.orEmpty()
            return JsonParser.parseString(body).asJsonArray.mapNotNull { item ->
                val obj = item.asJsonObject
                val url = obj.get("url")?.asString.orEmpty()
                if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapNotNull null
                val title = text(obj.getAsJsonArray("title")).ifBlank { url }
                WebSearchDocument(title, url, text(obj.getAsJsonArray("extract")).take(1500).ifBlank { title })
            }.distinctBy { it.url }
        }
    }
}
