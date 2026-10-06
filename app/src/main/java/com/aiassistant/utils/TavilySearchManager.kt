package com.aiassistant.utils

import android.content.Context
import com.aiassistant.data.remote.RetrofitClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class TavilySearchSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val searchDepth: String = "basic",
    val maxResults: Int = 8,
    val includeAnswer: Boolean = true
)

data class WebSearchDocument(
    val title: String,
    val url: String,
    val content: String
)

data class WebSearchBundle(
    val query: String,
    val answer: String?,
    val results: List<WebSearchDocument>
) {
    fun toPromptBlock(): String {
        val sources = results.mapIndexed { index, result ->
            """
            [${index + 1}] [${result.title}](${result.url})
            摘要：${result.content}
            """.trimIndent()
        }.joinToString("\n\n")

        return buildString {
            append("以下是联网搜索得到的实时参考资料。")
            append("\n搜索词：").append(query)
            if (!answer.isNullOrBlank()) {
                append("\n\n搜索摘要：").append(answer.trim())
            }
            if (sources.isNotBlank()) {
                append("\n\n参考来源：\n").append(sources)
            }
            append("\n\n【回答规范】")
            append("\n1. 优先依据上述参考资料回答，确保信息客观真实；")
            append("\n2. 正文引用了哪一部分资料时，必须在对应句子或短语右侧标注序号角标，例如「根据报道[1]」或「[2]」；")
            append("\n3. 回答末尾可附带简洁的「参考资料」列表。")
        }
    }
}

class TavilySearchManager(
    context: Context,
    private val cryptoManager: CryptoManager
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    fun getSettings(): TavilySearchSettings {
        val encryptedKey = prefs.getString(KEY_API_KEY, "").orEmpty()
        val apiKey = cryptoManager.decrypt(encryptedKey)
        return TavilySearchSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, apiKey.isNotBlank()),
            apiKey = apiKey,
            searchDepth = prefs.getString(KEY_SEARCH_DEPTH, "basic") ?: "basic",
            maxResults = prefs.getInt(KEY_MAX_RESULTS, 8).coerceIn(1, MAX_SEARCH_RESULTS),
            includeAnswer = prefs.getBoolean(KEY_INCLUDE_ANSWER, true)
        )
    }

    fun saveSettings(settings: TavilySearchSettings): Boolean {
        val cleanKey = settings.apiKey.trim()
        return prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled || cleanKey.isNotBlank())
            .putString(KEY_API_KEY, cryptoManager.encrypt(cleanKey))
            .putString(KEY_SEARCH_DEPTH, settings.searchDepth.ifBlank { "basic" })
            .putInt(KEY_MAX_RESULTS, settings.maxResults.coerceIn(1, MAX_SEARCH_RESULTS))
            .putBoolean(KEY_INCLUDE_ANSWER, settings.includeAnswer)
            .commit()
    }

    fun hasUsableKey(): Boolean = getSettings().apiKey.isNotBlank()

    fun isReady(): Boolean {
        val settings = getSettings()
        return settings.enabled && settings.apiKey.isNotBlank()
    }

    fun search(query: String): Result<WebSearchBundle> {
        return search(query, getSettings().maxResults)
    }

    fun search(query: String, maxResults: Int): Result<WebSearchBundle> {
        val baseSettings = getSettings()
        val settings = baseSettings.copy(maxResults = maxResults.coerceIn(1, MAX_SEARCH_RESULTS))
        if (!settings.enabled) {
            return Result.failure(IllegalStateException("Tavily 联网搜索未启用"))
        }
        if (settings.apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Tavily API Key 为空，请先在设置中填写"))
        }
        if (query.isBlank()) {
            return Result.failure(IllegalArgumentException("搜索问题为空"))
        }

        return try {
            val requestJson = buildSearchRequestJson(query.trim(), settings)
            RetrofitClient.postJson(
                baseUrl = TAVILY_BASE_URL,
                path = "search",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer ${settings.apiKey}"
                ),
                json = requestJson
            ).use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Tavily 搜索失败 (${response.code}): ${body.ifBlank { response.message }}"))
                }

                val parsed = gson.fromJson(body, TavilySearchResponse::class.java)
                val documents = parsed.results.orEmpty()
                    .filter { !it.url.isNullOrBlank() || !it.content.isNullOrBlank() }
                    .map {
                        WebSearchDocument(
                            title = it.title?.ifBlank { "未命名网页" } ?: "未命名网页",
                            url = it.url.orEmpty(),
                            content = it.content.orEmpty().trim().take(1_200)
                        )
                    }

                Result.success(
                    WebSearchBundle(
                        query = query.trim(),
                        answer = parsed.answer,
                        results = documents
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun buildSearchRequestJson(query: String, settings: TavilySearchSettings): String {
        return JsonObject().apply {
            addProperty("query", query)
            addProperty("search_depth", settings.searchDepth.ifBlank { "basic" })
            addProperty("include_answer", settings.includeAnswer)
            addProperty("include_raw_content", false)
            addProperty("max_results", settings.maxResults.coerceIn(1, MAX_SEARCH_RESULTS))
            // Tavily 新版使用 Authorization；保留 body key 兼容旧网关，字段名手写避免 release 混淆。
            addProperty("api_key", settings.apiKey)
        }.toString()
    }

    private data class TavilySearchResponse(
        @SerializedName("answer")
        val answer: String?,

        @SerializedName("results")
        val results: List<TavilyResult>?
    )

    private data class TavilyResult(
        @SerializedName("title")
        val title: String?,

        @SerializedName("url")
        val url: String?,

        @SerializedName("content")
        val content: String?
    )

    companion object {
        private const val PREFS_NAME = "tavily_search_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SEARCH_DEPTH = "search_depth"
        private const val KEY_MAX_RESULTS = "max_results"
        private const val KEY_INCLUDE_ANSWER = "include_answer"
        private const val TAVILY_BASE_URL = "https://api.tavily.com"
        private const val MAX_SEARCH_RESULTS = 20
    }
}
