package com.aiassistant.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException

data class HttpAccessRules(val enabled: Boolean = false, val addresses: List<String> = emptyList())

class HttpAccessDeniedException(message: String) : IOException(message)

/** Dynamic, fail-closed application policy. Android's static config only enables transport. */
object HttpAccessPolicy {
    @Volatile private var rulesProvider: () -> HttpAccessRules = { HttpAccessRules() }

    fun initialize(provider: () -> HttpAccessRules) { rulesProvider = provider }

    fun normalizeAddress(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty() && value.none { it.isWhitespace() } && value.none { it in "*\\?#@" }) {
            "地址不能包含空格、通配符、账号、查询参数或片段"
        }
        val url = (if ("://" in value) value else "http://$value").toHttpUrlOrNull()
            ?: throw IllegalArgumentException("请输入有效的 HTTP 地址和端口")
        require(url.scheme == "http") { "白名单只填写 http:// 地址，HTTPS 无需放行" }
        require(url.username.isEmpty() && url.password.isEmpty()) { "地址不能包含账号或密码" }
        require(url.pathSegments.none { '/' in it || '\\' in it }) { "路径不能包含编码后的斜杠" }
        return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/').ifEmpty { "/" }).build().toString().trimEnd('/')
    }

    fun permits(url: HttpUrl, rules: HttpAccessRules): Boolean {
        if (url.isHttps) return true
        if (!rules.enabled || url.scheme != "http") return false
        if (url.pathSegments.any { '/' in it || '\\' in it }) return false
        return rules.addresses.any { address ->
            val allowed = runCatching { normalizeAddress(address).toHttpUrlOrNull() }.getOrNull() ?: return@any false
            val prefix = allowed.pathSegments.dropLastWhile { it.isEmpty() }
            url.host == allowed.host && url.port == allowed.port &&
                url.pathSegments.take(prefix.size) == prefix
        }
    }

    fun check(url: HttpUrl) {
        val rules = rulesProvider()
        if (permits(url, rules)) return
        // Do not expose query strings, userinfo or API keys in errors.
        val destination = url.newBuilder().username("").password("").query(null).fragment(null).build()
        throw HttpAccessDeniedException(if (!rules.enabled)
            "HTTP 访问已关闭。请在设置 → HTTP 访问中添加允许地址并开启开关（$destination）"
        else "HTTP 地址不在允许列表中。请检查主机、端口与路径范围（$destination）")
    }

    private val initialGuard = Interceptor { chain ->
        check(chain.request().url)
        chain.proceed(chain.request())
    }
    internal fun checkRedirect(from: HttpUrl, location: String?) {
        val next = location?.let { from.resolve(it) } ?: return
        if (next.scheme != "http") return
        if (from.isHttps) throw HttpAccessDeniedException("安全限制：不允许 HTTPS 自动重定向到 HTTP，请直接配置并确认 HTTP 地址")
        check(next)
    }
    private val exchangeGuard = Interceptor { chain ->
        val request = chain.request()
        check(request.url) // Also covers redirect follow-ups and policy changes before sending.
        val response = chain.proceed(request)
        try {
            if (response.code in setOf(300, 301, 302, 303, 307, 308)) {
                checkRedirect(request.url, response.header("Location")) // Before the next DNS/connect/headers.
            }
            response
        } catch (error: Exception) {
            response.close()
            throw error
        }
    }

    fun guard(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .addInterceptor(initialGuard)
        .addNetworkInterceptor(exchangeGuard)
}
