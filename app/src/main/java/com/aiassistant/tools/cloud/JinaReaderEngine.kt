package com.aiassistant.tools.cloud

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class JinaReaderEngine(
    private var customApiKey: String = ""
) {

    private val httpClient = com.aiassistant.data.remote.HttpAccessPolicy.guard(OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun updateApiKey(key: String) {
        customApiKey = key.trim()
    }

    fun readUrl(url: String): Result<WebpageReadResult> {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("无效的网页网址，请以 http:// 或 https:// 开头"))
        }

        val jinaUrl = "https://r.jina.ai/"
        val requestBuilder = Request.Builder()
            .url(jinaUrl)
            .addHeader("Accept", "text/plain, */*")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")

        if (customApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ")
        }

        return try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful && body.isNotBlank()) {
                    val parsed = parseJinaContent(cleanUrl, body)
                    Result.success(parsed)
                } else {
                    // Jina 遇到 401 拦截或网络限流，自动回退到原生网页直连抓取
                    fallbackDirectScrape(cleanUrl)
                }
            }
        } catch (_: Exception) {
            // 网络超时或失败，回退到原生直连抓取
            fallbackDirectScrape(cleanUrl)
        }
    }

    private fun parseJinaContent(originalUrl: String, content: String): WebpageReadResult {
        var title = "网页提取正文"
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        if (firstLine.startsWith("Title:", ignoreCase = true)) {
            title = firstLine.substringAfter(":").trim()
        } else if (firstLine.startsWith("#")) {
            title = firstLine.trimStart('#', ' ')
        }

        return WebpageReadResult(
            url = originalUrl,
            title = title,
            markdownContent = content.take(6_000),
            isFallback = false
        )
    }

    private fun fallbackDirectScrape(url: String): Result<WebpageReadResult> {
        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || html.isBlank()) {
                    return Result.failure(Exception("无法访问目标网页 ()"))
                }

                val titleMatch = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
                val title = if (titleMatch.find()) titleMatch.group(1).orEmpty().trim() else "网页正文"

                // 移除 script 和 style 标签
                var text = html.replace(Regex("(?is)<script.*?</script>"), "")
                    .replace(Regex("(?is)<style.*?</style>"), "")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("&nbsp;"), " ")
                    .replace(Regex("&amp;"), "&")
                    .replace(Regex("&lt;"), "<")
                    .replace(Regex("&gt;"), ">")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                Result.success(
                    WebpageReadResult(
                        url = url,
                        title = title,
                        markdownContent = text.take(4_000),
                        isFallback = true
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception("网页内容抓取失败: ", e))
        }
    }

    data class WebpageReadResult(
        val url: String,
        val title: String,
        val markdownContent: String,
        val isFallback: Boolean
    ) {
        fun toPromptBlock(): String {
            return buildString {
                append("【网页正文长文提取】")
                append("\n网页标题：").append(title)
                append("\n源链接：").append(url)
                if (isFallback) {
                    append(" (Jina 调度未响应，由本地解析器提取)")
                } else {
                    append(" (由 Jina Reader 自动转换为精炼 Markdown)")
                }
                append("\n\n正文内容摘要：\n").append(markdownContent)
                append("\n\n【回答指导】请针对用户要求深度分析或总结上述网页正文内容，并注明信息出处。")
            }
        }
    }
}
