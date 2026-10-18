package com.aiassistant.data.repository.helpers

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Shared normalization for saved API addresses and every HTTP transport. */
object ApiEndpointHelper {
    /**
     * A bare host keeps the historical /v1 default. An explicitly supplied path
     * belongs to the service and must not acquire another version suffix.
     * Full operation URLs stay recognizable here so normalization is idempotent,
     * including services whose operation lives directly below the host.
     */
    fun normalizeBaseUrl(rawBaseUrl: String, apiType: String = "openai"): String {
        val url = parse(rawBaseUrl)
        var path = url.encodedPath.trimEnd('/')
        val operations = operationPaths(apiType)
        while (path.endsWith("/v1/v1", ignoreCase = true)) path = path.dropLast(3)

        // Earlier versions appended /v1 to these already complete API roots or
        // operation URLs. Repair only recognizable cases; a custom /proxy/v1
        // can be intentional and must remain untouched.
        if (path.endsWith("/v1", ignoreCase = true)) {
            val withoutVersion = path.dropLast(3)
            if (withoutVersion.endsWith("/v1beta/openai", ignoreCase = true) ||
                withoutVersion.endsWith("/api/paas/v4", ignoreCase = true) ||
                operations.any { withoutVersion.endsWith(it, ignoreCase = true) }) {
                path = withoutVersion
            }
        }
        if (path.isEmpty()) path = "/v1"
        return url.newBuilder().encodedPath(path).build().toString().trimEnd('/')
    }

    /**
     * Strip an optional operation only at the transport boundary. The returned
     * trailing slash is suitable for Retrofit and for appending a relative path.
     */
    fun requestBaseUrl(rawBaseUrl: String, apiType: String = "openai"): String {
        val url = parse(normalizeBaseUrl(rawBaseUrl, apiType))
        var path = url.encodedPath.trimEnd('/')
        operationPaths(apiType).firstOrNull { path.endsWith(it, ignoreCase = true) }?.let {
            path = path.dropLast(it.length)
        }
        return url.newBuilder().encodedPath("${path.trimEnd('/')}/").build().toString()
    }

    /** AI version defaults must never change unrelated services such as /search. */
    fun resolveOperationUrl(rawBaseUrl: String, path: String, apiType: String = "openai"): String {
        val operation = path.trimStart('/').trimEnd('/')
        val isAiOperation = operationPaths(apiType).any { it.removePrefix("/").equals(operation, true) }
        val base = if (isAiOperation) parse(requestBaseUrl(rawBaseUrl, apiType)) else parse(rawBaseUrl)
        return base.newBuilder().encodedPath("${base.encodedPath.trimEnd('/')}/$operation").build().toString()
    }

    private fun operationPaths(apiType: String): List<String> = if (apiType.equals("anthropic", true)) {
        listOf("/messages", "/models", "/chat/completions")
    } else {
        listOf("/chat/completions", "/models", "/messages")
    }

    private fun parse(rawBaseUrl: String): HttpUrl {
        val trimmed = rawBaseUrl.trim()
        require(trimmed.isNotEmpty()) { "API 地址不能为空" }
        val value = if ("://" in trimmed) trimmed else "https://$trimmed"
        val url = value.toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入有效的 HTTP 或 HTTPS API 地址")
        require(url.encodedQuery == null && url.encodedFragment == null) {
            "API Base URL 不能包含查询参数或片段，请填写服务根地址或完整接口路径"
        }
        return url
    }
}
