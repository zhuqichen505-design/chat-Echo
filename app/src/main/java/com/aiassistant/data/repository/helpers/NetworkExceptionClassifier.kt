package com.aiassistant.data.repository.helpers

import kotlinx.coroutines.CancellationException

/**
 * 网络异常与请求取消类型识别助手
 */
object NetworkExceptionClassifier {

    fun isTimeoutException(e: Throwable): Boolean {
        if (e is java.net.SocketTimeoutException) return true
        var current: Throwable? = e
        while (current != null) {
            if (current is java.net.SocketTimeoutException) return true
            val msg = current.message?.lowercase().orEmpty()
            if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("time out")) return true
            current = current.cause
        }
        return false
    }

    fun isRequestCancellation(error: Throwable): Boolean {
        if (error is CancellationException) return true
        if (error.message.equals("Canceled", ignoreCase = true)) return true
        return error.cause?.let(::isRequestCancellation) == true
    }

    fun isNetworkFluctuationException(e: Throwable): Boolean {
        if (isTimeoutException(e)) return true
        var current: Throwable? = e
        while (current != null) {
            if (current is java.net.UnknownHostException ||
                current is java.net.ConnectException ||
                current is java.net.NoRouteToHostException ||
                current is java.net.SocketException ||
                current is javax.net.ssl.SSLException ||
                current is java.net.SocketTimeoutException ||
                current is java.net.ProtocolException ||
                (current is java.io.InterruptedIOException && !isRequestCancellation(current))
            ) {
                return true
            }
            val msg = current.message?.lowercase().orEmpty()
            if (msg.contains("connection abort") ||
                msg.contains("unexpected end of stream") ||
                msg.contains("stream was reset") ||
                msg.contains("stream reset") ||
                msg.contains("broken pipe") ||
                msg.contains("network is unreachable") ||
                msg.contains("connection reset") ||
                msg.contains("connection closed") ||
                msg.contains("unable to resolve host") ||
                msg.contains("failed to connect") ||
                msg.contains("route to host") ||
                msg.contains("end of stream") ||
                msg.contains("timed out") ||
                msg.contains("timeout") ||
                msg.contains("http2") ||
                msg.contains("connection refused") ||
                msg.contains("handshake failed") ||
                msg.contains("ssl handshake") ||
                msg.contains("clean shutdown")
            ) {
                return true
            }
            if (current is java.io.EOFException) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
