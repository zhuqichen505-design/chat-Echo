package com.aiassistant.data.repository.helpers

import okhttp3.Call
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Headers and heartbeats are not output; continuous generation has no total deadline. */
internal class FirstTokenDeadline(private val timeoutMillis: Long) : AutoCloseable {
    private var timer: ScheduledFuture<*>? = null
    private var finished = false
    private var expired = false
    @Synchronized fun start(call: Call) {
        if (finished || timer != null) return
        timer = scheduler.schedule({ synchronized(this) {
            if (!finished && !call.isCanceled()) {
                expired = true
                finished = true
                call.cancel()
            }
        } }, timeoutMillis, TimeUnit.MILLISECONDS)
    }
    @Synchronized fun received(delta: String) {
        if (expired) throw failure(SocketTimeoutException())
        if (delta.isNotEmpty()) close()
    }
    @Synchronized fun failure(error: Exception): Exception {
        close()
        return if (expired) {
            // Don't chain OkHttp Canceled: this is a timeout, not a user's stop.
            SocketTimeoutException("首个有效回复等待超时（${timeoutMillis / 1000}s）：未收到正文或思考内容，心跳不计为回复")
        } else error
    }
    @Synchronized override fun close() { finished = true; timer?.cancel(false) }
    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 120_000L
        private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "echo-first-token-deadline").apply { isDaemon = true }
        }
    }
}
