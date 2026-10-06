package com.aiassistant.domain.model

import com.aiassistant.data.repository.helpers.NetworkExceptionClassifier
import retrofit2.HttpException

enum class RetryErrorType(val label: String, val defaultEnabled: Boolean = false) {
    TIMEOUT("连接 / 读取超时", true),
    NETWORK("网络断开 / DNS / 连接失败", true),
    TLS("TLS / SSL 连接错误", true),
    BAD_REQUEST("400 请求参数错误"),
    AUTHENTICATION("401 鉴权失败"),
    PERMISSION("403 权限不足"),
    NOT_FOUND("404 模型 / 接口不存在"),
    REQUEST_TIMEOUT("408 服务端请求超时", true),
    CONFLICT("409 请求冲突"),
    TOO_LARGE("413 请求过大"),
    INVALID_PARAMETERS("422 参数校验失败"),
    RATE_LIMIT("429 限流 / 额度不足"),
    SERVER("500 服务端错误"),
    BAD_GATEWAY("502 网关错误"),
    UNAVAILABLE("503 服务暂不可用"),
    GATEWAY_TIMEOUT("504 网关超时", true),
    OTHER_HTTP("其他 HTTP 错误"),
    EMPTY_RESPONSE("空回复 / 无有效内容"),
    OTHER("其他错误");

    companion object {
        private val httpCode = Regex("(?i)\\bHTTP(?:\\s+error)?\\s*[:(]?\\s*(\\d{3})\\b")

        fun classify(error: Throwable): RetryErrorType {
            val chain = generateSequence(error) { it.cause }.take(16).toList()
            if (chain.any { it.message.orEmpty().contains("empty response", true) || it.message.orEmpty().contains("响应体为空") || it.message.orEmpty().contains("未返回有效") || it.message.orEmpty().contains("无有效内容") }) return EMPTY_RESPONSE
            val code = chain.filterIsInstance<com.aiassistant.data.repository.ApiException>().firstOrNull()?.statusCode
                ?: chain.filterIsInstance<HttpException>().firstOrNull()?.code()
                ?: chain.firstNotNullOfOrNull { httpCode.find(it.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() }
            if (code != null) return when (code) {
                400 -> BAD_REQUEST; 401 -> AUTHENTICATION; 403 -> PERMISSION; 404 -> NOT_FOUND
                408 -> REQUEST_TIMEOUT; 409 -> CONFLICT; 413 -> TOO_LARGE; 422 -> INVALID_PARAMETERS
                429 -> RATE_LIMIT; 500 -> SERVER; 502 -> BAD_GATEWAY; 503 -> UNAVAILABLE; 504 -> GATEWAY_TIMEOUT
                else -> OTHER_HTTP
            }
            if (NetworkExceptionClassifier.isTimeoutException(error)) return TIMEOUT
            if (chain.any { it is javax.net.ssl.SSLException || it.message.orEmpty().contains("ssl", true) || it.message.orEmpty().contains("handshake", true) }) return TLS
            if (NetworkExceptionClassifier.isNetworkFluctuationException(error)) return NETWORK
            return OTHER
        }
    }
}

data class RetryRule(val enabled: Boolean, val maxRetries: Int = 3)

data class RetryPolicy(val rules: Map<RetryErrorType, RetryRule> = emptyMap(), val enabled: Boolean = true) {
    fun rule(type: RetryErrorType): RetryRule = rules[type] ?: RetryRule(type.defaultEnabled)

    fun canRetry(error: Throwable, retriesUsed: Int, hasOutput: Boolean = false): Boolean {
        if (!enabled || hasOutput || NetworkExceptionClassifier.isRequestCancellation(error)) return false
        if (generateSequence(error) { it.cause }.take(16).any { it is com.aiassistant.data.remote.HttpAccessDeniedException }) return false
        val rule = rule(RetryErrorType.classify(error))
        return rule.enabled && retriesUsed < rule.maxRetries.coerceIn(0, MAX_RETRIES)
    }

    fun delayMillis(retriesUsed: Int): Long = when (retriesUsed) {
        0 -> 1_000L
        1 -> 2_000L
        else -> 5_000L
    }

    companion object { const val MAX_RETRIES = 20 }
}
