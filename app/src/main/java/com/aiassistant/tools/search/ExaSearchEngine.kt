package com.aiassistant.tools.search

import com.aiassistant.utils.WebSearchBundle
import com.aiassistant.utils.WebSearchDocument
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ExaSearchEngine(
    private var customApiKey: String = ""
) : WebSearchProvider {

    override val engineType: SearchEngineType = SearchEngineType.EXA

    private val httpClient = com.aiassistant.data.remote.HttpAccessPolicy.guard(OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun updateApiKey(key: String) {
        customApiKey = key.trim()
    }

    override fun isReady(): Boolean = true // Exa 免 Key 即可使用

    override fun search(query: String, maxResults: Int): Result<WebSearchBundle> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return Result.failure(IllegalArgumentException("搜索关键词为空"))
        }

        val limit = maxResults.coerceIn(1, SearchLimits.MAX_REQUESTED)
        val requestPayload = buildSearchPayload(cleanQuery, limit)

        val requestBuilder = Request.Builder()
            .url(EXA_MCP_URL)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
            .post(requestPayload.toString().toRequestBody("application/json".toMediaType()))

        if (customApiKey.isNotBlank()) requestBuilder.addHeader("x-api-key", customApiKey)

        return try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return Result.failure(Exception("Exa HTTP ${response.code}: $body"))
                val documents = parseExaResponse(body).take(limit)
                if (documents.isEmpty()) return Result.failure(Exception("Exa 未检索到相关结果"))
                Result.success(WebSearchBundle(cleanQuery, null, documents))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Exa 网络连接异常: ${e.message}", e))
        }
    }

    companion object {
        const val EXA_MCP_URL = "https://mcp.exa.ai/mcp"

        fun buildSearchPayload(query: String, maxResults: Int): JsonObject = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", "tools/call")
            add("params", JsonObject().apply {
                addProperty("name", "web_search_exa")
                add("arguments", JsonObject().apply {
                    addProperty("query", query.trim())
                    addProperty("numResults", maxResults.coerceIn(1, SearchLimits.MAX_REQUESTED))
                })
            })
            addProperty("id", 1)
        }

        fun parseExaResponse(rawBody: String): List<WebSearchDocument> {
            val trimmed = rawBody.trim()
            val jsonText = if (trimmed.contains("data:")) {
                trimmed.lines()
                    .firstOrNull { it.trim().startsWith("data:") }
                    ?.substringAfter("data:")
                    ?.trim()
                    ?: trimmed
            } else {
                trimmed
            }

            return try {
                val root = Gson().fromJson(jsonText, JsonObject::class.java)
                val contentArray = root.getAsJsonObject("result")?.getAsJsonArray("content")
                val textBuilder = StringBuilder()
                contentArray?.forEach { elem ->
                    val obj = elem.asJsonObject
                    if (obj.get("type")?.asString == "text") {
                        textBuilder.append(obj.get("text")?.asString.orEmpty()).append("\n")
                    }
                }
                parseExaResultText(textBuilder.toString())
            } catch (_: Exception) {
                // 回退：直接从文本中提取
                parseExaResultText(rawBody)
            }
        }

        fun parseExaResultText(rawText: String): List<WebSearchDocument> {
            val items = rawText.split(Regex("(?m)^---\\s*$"))
            val docs = mutableListOf<WebSearchDocument>()
            for (item in items) {
                val trimmed = item.trim()
                if (trimmed.isBlank()) continue
                var title = ""
                var url = ""
                val highlights = StringBuilder()
                var inHighlights = false

                for (line in trimmed.lines()) {
                    val lineTrim = line.trim()
                    when {
                        lineTrim.startsWith("Title:", ignoreCase = true) -> {
                            title = lineTrim.substringAfter(":").trim()
                            inHighlights = false
                        }
                        lineTrim.startsWith("URL:", ignoreCase = true) -> {
                            url = lineTrim.substringAfter(":").trim()
                            inHighlights = false
                        }
                        lineTrim.startsWith("Highlights:", ignoreCase = true) -> {
                            inHighlights = true
                            val after = lineTrim.substringAfter(":").trim()
                            if (after.isNotBlank()) highlights.append(after).append("\n")
                        }
                        lineTrim.startsWith("Author:", ignoreCase = true) || lineTrim.startsWith("Published:", ignoreCase = true) -> {
                            inHighlights = false
                        }
                        inHighlights -> {
                            highlights.append(line).append("\n")
                        }
                    }
                }
                val content = highlights.toString().trim().ifBlank { title }
                if (title.isNotBlank() || url.isNotBlank()) {
                    docs.add(
                        WebSearchDocument(
                            title = title.ifBlank { "网页参考资料" },
                            url = url,
                            content = content.take(1500)
                        )
                    )
                }
            }
            return docs
        }
    }
}
