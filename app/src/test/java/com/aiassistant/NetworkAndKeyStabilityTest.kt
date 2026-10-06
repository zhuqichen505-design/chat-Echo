package com.aiassistant

import com.aiassistant.data.remote.RetrofitClient
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.data.repository.KeyAttemptFailure
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.SmartMemoryExtractor
import org.junit.Assert.*
import org.junit.Test
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class NetworkAndKeyStabilityTest {
    @org.junit.Before fun allowSyntheticHttpEndpoints() { HttpTestAccess.start() }
    @org.junit.After fun restoreClosedHttpPolicy() { HttpTestAccess.stop() }
    @Test fun httpAddressRulesMatchExactOriginAndPathBoundaries() {
        val policy = com.aiassistant.data.remote.HttpAccessPolicy
        val rules = com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://192.0.2.1:8080/v1"))
        fun permits(url: String) = policy.permits(okhttp3.HttpUrl.Companion.run { url.toHttpUrl() }, rules)
        assertTrue(permits("http://192.0.2.1:8080/v1"))
        assertTrue(permits("http://192.0.2.1:8080/v1/models?limit=10"))
        assertTrue(permits("http://192.0.2.1:8080/v1/chat/completions"))
        assertFalse(permits("http://192.0.2.1:8081/v1/models"))
        assertFalse(permits("http://192.0.2.2:8080/v1/models"))
        assertFalse(permits("http://192.0.2.1:8080/v10/models"))
        assertFalse(permits("http://192.0.2.1:8080/admin"))
        assertFalse(permits("http://192.0.2.1:8080/v1/../admin"))
        assertFalse(permits("http://192.0.2.1:8080/v1/a%2f..%2f..%2fadmin"))
        assertTrue(permits("https://unlisted.example.test/v1/models"))
        val domainRules = com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://proxy.example.test:8045/v1"))
        assertFalse(policy.permits(okhttp3.HttpUrl.Companion.run { "http://proxy.example.test.evil.test:8045/v1".toHttpUrl() }, domainRules))
        assertFalse(policy.permits(okhttp3.HttpUrl.Companion.run { "http://sub.proxy.example.test:8045/v1".toHttpUrl() }, domainRules))
        assertEquals("http://proxy.example.test:8045/v1", policy.normalizeAddress(" HTTP://PROXY.EXAMPLE.TEST:8045/v1/ "))
        assertEquals("http://[::1]:8045/v1", policy.normalizeAddress("[::1]:8045/v1"))
        for (invalid in listOf("", "http://*.test", "https://proxy.test", "http://u:p@proxy.test", "http://proxy.test?key=secret", "http://proxy.test/#x", "http://proxy.test:99999", "http://a b", "http://proxy.test/v1%2fadmin", "ftp://proxy.test")) {
            try { policy.normalizeAddress(invalid); fail("Invalid allow address: $invalid") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun dynamicHttpPolicyBlocksBeforeSendingAndClosesPreviouslyPooledClients() {
        val policy = com.aiassistant.data.remote.HttpAccessPolicy
        LocalEndpoint { it.reply(body = "ok") }.use { server ->
            var rules = com.aiassistant.data.remote.HttpAccessRules()
            policy.initialize { rules }
            for (client in listOf(RetrofitClient.restHttpClient, RetrofitClient.streamHttpClient, RetrofitClient.longAnalysisHttpClient)) {
                try { client.newCall(request(server.port)).execute().close(); fail("Closed HTTP policy sent a request") }
                catch (e: com.aiassistant.data.remote.HttpAccessDeniedException) { assertTrue(e.message!!.contains("HTTP 访问已关闭")) }
            }
            assertEquals(0, server.requests.get())
            rules = com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://127.0.0.1:${server.port}/v1"))
            RetrofitClient.getJson("http://127.0.0.1:${server.port}/v1", "models", mapOf("Authorization" to "Bearer synthetic")).use { assertEquals("ok", it.body!!.string()) }
            assertEquals(1, server.requests.get())
            rules = rules.copy(addresses = emptyList())
            try { RetrofitClient.restHttpClient.newCall(request(server.port)).execute().close(); fail("Removed address still allowed") }
            catch (_: com.aiassistant.data.remote.HttpAccessDeniedException) { }
            assertEquals(1, server.requests.get())
            rules = rules.copy(enabled = false)
            val https = okhttp3.HttpUrl.Companion.run { "https://example.test".toHttpUrl() }
            policy.check(https)
            try { policy.check(okhttp3.HttpUrl.Companion.run { "http://127.0.0.1:${server.port}/v1?apiKey=do-not-log".toHttpUrl() }); fail("HTTP must be denied") }
            catch (e: com.aiassistant.data.remote.HttpAccessDeniedException) { assertFalse(e.message!!.contains("do-not-log")) }
            assertFalse(com.aiassistant.domain.model.RetryPolicy(mapOf(com.aiassistant.domain.model.RetryErrorType.OTHER to com.aiassistant.domain.model.RetryRule(true, 20))).canRetry(com.aiassistant.data.remote.HttpAccessDeniedException("blocked"), 0))
        }
    }

    @Test fun httpRedirectCannotEscapeAllowedOriginOrPath() {
        val policy = com.aiassistant.data.remote.HttpAccessPolicy
        LocalEndpoint { it.reply(body = "must not receive credentials") }.use { denied ->
            LocalEndpoint { it.reply("302 Found", body = "", extraHeaders = "Location: http://127.0.0.1:${denied.port}/v1/models\r\n") }.use { allowed ->
                policy.initialize { com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://127.0.0.1:${allowed.port}/v1")) }
                try { RetrofitClient.getJson("http://127.0.0.1:${allowed.port}/v1", "models", mapOf("Authorization" to "Bearer synthetic")).close(); fail("Redirect escaped whitelist") }
                catch (_: com.aiassistant.data.remote.HttpAccessDeniedException) { }
                assertEquals(1, allowed.requests.get()); assertEquals(0, denied.requests.get())
            }
        }
        LocalEndpoint { it.reply("302 Found", body = "", extraHeaders = "Location: /admin\r\n") }.use { allowed ->
            policy.initialize { com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://127.0.0.1:${allowed.port}/v1")) }
            try { RetrofitClient.getJson("http://127.0.0.1:${allowed.port}/v1", "models", emptyMap()).close(); fail("Redirect escaped path scope") }
            catch (_: com.aiassistant.data.remote.HttpAccessDeniedException) { }
            assertEquals(1, allowed.requests.get())
        }
    }

    @Test fun allowedRelativeRedirectWorksButHttpsDowngradeIsAlwaysRefused() {
        val policy = com.aiassistant.data.remote.HttpAccessPolicy
        LocalEndpoint { it.reply(body = "ok") }.use { target ->
            LocalEndpoint { it.reply("302 Found", body = "", extraHeaders = "Location: http://127.0.0.1:${target.port}/v1/models\r\n") }.use { source ->
                policy.initialize { com.aiassistant.data.remote.HttpAccessRules(true, listOf("http://127.0.0.1:${source.port}/v1", "http://127.0.0.1:${target.port}/v1")) }
                RetrofitClient.getJson("http://127.0.0.1:${source.port}/v1", "models", emptyMap()).use { assertEquals("ok", it.body!!.string()) }
                assertEquals(1, source.requests.get()); assertEquals(1, target.requests.get())
                try { policy.checkRedirect(okhttp3.HttpUrl.Companion.run { "https://proxy.test/v1".toHttpUrl() }, "http://127.0.0.1:${target.port}/v1/models"); fail("HTTPS downgrade permitted") }
                catch (e: com.aiassistant.data.remote.HttpAccessDeniedException) { assertTrue(e.message!!.contains("HTTPS 自动重定向")) }
                policy.checkRedirect(okhttp3.HttpUrl.Companion.run { "https://proxy.test/v1".toHttpUrl() }, "https://other.test/models")
            }
        }
    }
    @Test fun firstTokenDeadlineCannotConvertUserStopIntoTimeout() {
        LocalEndpoint { Thread.sleep(1000) }.use { server ->
            val call = RetrofitClient.streamHttpClient.newCall(request(server.port))
            com.aiassistant.data.repository.helpers.FirstTokenDeadline(100).use { deadline ->
                call.cancel()
                deadline.start(call)
                Thread.sleep(200)
                val stopped = java.io.IOException("Canceled")
                assertSame(stopped, deadline.failure(stopped))
                assertTrue(AiRepository.isRequestCancellation(deadline.failure(stopped)))
                assertFalse(com.aiassistant.domain.model.RetryPolicy().canRetry(deadline.failure(stopped), 0))
            }
            assertEquals(0, server.requests.get())
        }
    }

    @Test fun firstTokenDeadlineExpiresBlockedHeadersAndIsClassifiedAsTimeout() {
        LocalEndpoint { Thread.sleep(1000) }.use { server ->
            val call = RetrofitClient.streamHttpClient.newCall(request(server.port))
            com.aiassistant.data.repository.helpers.FirstTokenDeadline(300).use { deadline ->
                deadline.start(call)
                try { call.execute().close(); fail("Must time out") } catch (e: java.io.IOException) {
                    val failure = deadline.failure(e)
                    assertTrue(failure is SocketTimeoutException)
                    assertEquals(com.aiassistant.domain.model.RetryErrorType.TIMEOUT, com.aiassistant.domain.model.RetryErrorType.classify(failure))
                    assertFalse(AiRepository.isRequestCancellation(failure))
                }
            }
            assertEquals(1, server.requests.get())
        }
    }
    @Test fun concurrentRestAndAnalysisServicesKeepTheirOwnEndpointSnapshots() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (0 until 8).map { worker -> executor.submit {
                repeat(40) { round ->
                    val base = "http://127.0.0.1:${20000 + worker * 100 + round}/v1/"
                    for (service in listOf(RetrofitClient.getService(base), RetrofitClient.getAnalysisService(base))) {
                        val request = service.chatCompletion("Bearer test-only", com.aiassistant.domain.model.ChatCompletionRequest(
                            model = "vendor/Exact-ID", messages = emptyList(), stream = false
                        )).request()
                        assertEquals(base + "chat/completions", request.url.toString())
                    }
                }
            } }
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }
    private class LocalEndpoint(private val respond: (Socket) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        val requests = AtomicInteger()
        val requestLines = CopyOnWriteArrayList<String>()
        val payloads = CopyOnWriteArrayList<String>()
        val port: Int get() = server.localPort
        init {
            HttpTestAccess.allow("http://127.0.0.1:$port")
            HttpTestAccess.allow("http://model.test:$port")
            executor.submit {
                while (!server.isClosed) {
                    try {
                        server.accept().use { socket ->
                            socket.soTimeout = 2_000
                            val reader = socket.getInputStream().bufferedReader()
                            requestLines.add(reader.readLine())
                            var length = 0
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                            }
                            val payload = CharArray(length)
                            var read = 0
                            while (read < length) {
                                val count = reader.read(payload, read, length - read)
                                if (count < 0) break
                                read += count
                            }
                            payloads.add(String(payload, 0, read))
                            requests.incrementAndGet()
                            respond(socket)
                        }
                    } catch (_: java.io.IOException) {
                        if (server.isClosed) break
                    }
                }
            }
        }
        override fun close() {
            server.close()
            executor.shutdownNow()
            executor.awaitTermination(3, TimeUnit.SECONDS)
        }
    }

    private fun Socket.reply(status: String = "200 OK", body: String = "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n", extraHeaders: String = "") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        getOutputStream().apply {
            write("HTTP/1.1 $status\r\nContent-Type: text/event-stream\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n$extraHeaders\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    private fun request(port: Int, host: String = "127.0.0.1") = Request.Builder()
        .url("http://$host:$port/v1/chat/completions")
        .post("{\"model\":\"gpt-6.1-sol\",\"stream\":true}".toRequestBody())
        .build()

    @Test fun connectionFallsBackToSecondAddressBeforeSendingPost() {
        LocalEndpoint { it.reply() }.use { server ->
            val client = RetrofitClient.streamHttpClient.newBuilder()
                .dns(object : Dns {
                    override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.2"), InetAddress.getByName("127.0.0.1"))
                })
                .connectTimeout(300, TimeUnit.MILLISECONDS).readTimeout(2, TimeUnit.SECONDS).build()
            client.newCall(request(server.port, "model.test")).execute().use { response ->
                assertEquals(200, response.code)
                assertTrue(response.body!!.string().contains("[DONE]"))
            }
            assertEquals("Only the reachable address receives the model POST", 1, server.requests.get())
        }
    }

    @Test fun allAiClientsRecoverBeforeSendingButDoNotReplaySentPosts() {
        for (base in listOf(RetrofitClient.restHttpClient, RetrofitClient.longAnalysisHttpClient)) {
            LocalEndpoint { it.reply(body = "ok") }.use { server ->
                val client = base.newBuilder().dns(object : Dns {
                    override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.2"), InetAddress.getByName("127.0.0.1"))
                }).connectTimeout(300, TimeUnit.MILLISECONDS).build()
                client.newCall(request(server.port, "model.test")).execute().use { response ->
                    assertEquals("ok", response.body!!.string())
                }
                assertEquals(1, server.requests.get())
            }
            LocalEndpoint { }.use { server ->
                try { base.newCall(request(server.port)).execute().close(); fail("Disconnect must fail") }
                catch (_: java.io.IOException) { }
                assertEquals(1, server.requests.get())
            }
        }
    }

    @Test fun actualPostJsonDeliversFirstDeltaBeforeStreamClosesForBothProtocols() {
        val protocols = listOf(
            "chat/completions" to "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n",
            "messages" to "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}\n\n"
        )
        for ((path, firstChunk) in protocols) {
            val release = CountDownLatch(1)
            val tail = if (path == "messages") "data: {\"type\":\"message_stop\"}\n\n" else "data: [DONE]\n\n"
            val worker = Executors.newSingleThreadExecutor()
            LocalEndpoint { socket ->
                val length = (firstChunk + tail).toByteArray().size
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: $length\r\nConnection: close\r\n\r\n$firstChunk".toByteArray())
                output.flush()
                if (release.await(3, TimeUnit.SECONDS)) { output.write(tail.toByteArray()); output.flush() }
            }.use { server ->
                try {
                    val json = "{\"model\":\"gpt-6.1-sol\",\"stream\":true}"
                    val result = worker.submit<String> {
                        RetrofitClient.postJson("http://127.0.0.1:${server.port}/v1", path,
                            mapOf("Accept" to "text/event-stream", "Authorization" to "Bearer test-only"), json).use { response ->
                            assertEquals(200, response.code)
                            assertEquals(TimeUnit.SECONDS.toNanos(600), response.body!!.source().timeout().timeoutNanos())
                            response.body!!.byteStream().bufferedReader().readLine()
                        }
                    }
                    val line = result.get(2, TimeUnit.SECONDS)
                    if (path == "messages") {
                        val event = com.google.gson.Gson().fromJson(line.removePrefix("data: "), com.aiassistant.domain.model.AnthropicStreamEvent::class.java)
                        assertEquals("hello", event.delta!!.text)
                    } else assertEquals("hello", AiRepository.parseOpenAiStreamLine(line)!!.contentDelta)
                    assertEquals("POST /v1/$path HTTP/1.1", server.requestLines.single())
                    assertEquals(json, server.payloads.single())
                    assertEquals(1, server.requests.get())
                } finally { release.countDown(); worker.shutdownNow() }
            }
        }
    }

    @Test fun receivedPostIsNeverSilentlyReplayedAfterDisconnect() {
        LocalEndpoint { /* Close after reading the complete POST, without response headers. */ }.use { server ->
            val client = RetrofitClient.streamHttpClient.newBuilder().readTimeout(1, TimeUnit.SECONDS).build()
            try {
                client.newCall(request(server.port)).execute().close()
                fail("A disconnected response must fail")
            } catch (_: java.io.IOException) { }
            assertEquals(1, server.requests.get())
        }
    }

    @Test fun httpRetryResponsesRemainVisibleToExplicitPolicy() {
        for (status in listOf("408 Request Timeout", "503 Service Unavailable")) {
            LocalEndpoint { it.reply(status, "error", "Retry-After: 0\r\n") }.use { server ->
                RetrofitClient.streamHttpClient.newCall(request(server.port)).execute().use { response ->
                    assertEquals(status.substringBefore(' ').toInt(), response.code)
                    assertEquals("error", response.body!!.string())
                }
                assertEquals("HTTP retry must be owned by RetryPolicy", 1, server.requests.get())
            }
        }
    }

    @Test fun responseHeaderTimeoutIsFiniteAndClassifiedForRetry() {
        LocalEndpoint { socket -> Thread.sleep(600); socket.reply() }.use { server ->
            val client = RetrofitClient.streamHttpClient.newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build()
            try {
                client.newCall(request(server.port)).execute().close()
                fail("A silent server must time out")
            } catch (e: SocketTimeoutException) {
                assertEquals(com.aiassistant.domain.model.RetryErrorType.TIMEOUT, com.aiassistant.domain.model.RetryErrorType.classify(e))
            }
            assertEquals(1, server.requests.get())
        }
    }

    @Test fun cancellingBlockedHeaderReadUnblocksCallWithoutReplay() {
        LocalEndpoint { Thread.sleep(700) }.use { server ->
            val client = RetrofitClient.streamHttpClient.newBuilder().readTimeout(2, TimeUnit.SECONDS).build()
            val call = client.newCall(request(server.port))
            val worker = Executors.newSingleThreadExecutor()
            try {
                val result = worker.submit<Boolean> {
                    try { call.execute().close(); false } catch (_: java.io.IOException) { call.isCanceled() }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                while (server.requests.get() == 0 && System.nanoTime() < deadline) Thread.sleep(10)
                assertEquals(1, server.requests.get())
                call.cancel()
                assertTrue(result.get(2, TimeUnit.SECONDS))
                assertEquals(1, server.requests.get())
            } finally { worker.shutdownNow() }
        }
    }

    @Test
    fun retryClassifiesActualApiExceptionsBeforeBodyText() {
        val types = mapOf(400 to "BAD_REQUEST", 401 to "AUTHENTICATION", 403 to "PERMISSION", 404 to "NOT_FOUND", 408 to "REQUEST_TIMEOUT", 409 to "CONFLICT", 413 to "TOO_LARGE", 422 to "INVALID_PARAMETERS", 429 to "RATE_LIMIT", 500 to "SERVER", 502 to "BAD_GATEWAY", 503 to "UNAVAILABLE", 504 to "GATEWAY_TIMEOUT", 418 to "OTHER_HTTP")
        types.forEach { (code, name) ->
            assertEquals(name, com.aiassistant.domain.model.RetryErrorType.classify(com.aiassistant.data.repository.ApiException(code, "API错误 ($code): timeout in provider" )).name)
            assertEquals(name, com.aiassistant.domain.model.RetryErrorType.classify(Exception("HTTP $code: error")).name)
        }
        assertEquals(com.aiassistant.domain.model.RetryErrorType.EMPTY_RESPONSE, com.aiassistant.domain.model.RetryErrorType.classify(com.aiassistant.data.repository.ApiException(500, "empty response detected")))
        assertEquals(com.aiassistant.domain.model.RetryErrorType.TLS, com.aiassistant.domain.model.RetryErrorType.classify(javax.net.ssl.SSLException("certificate invalid")))
    }

    @Test
    fun retryRulesRespectCountDisabledCancellationAndPartialOutput() {
        val timeout = SocketTimeoutException("timeout")
        val policy = com.aiassistant.domain.model.RetryPolicy()
        assertTrue(policy.canRetry(timeout, 0))
        assertTrue(policy.canRetry(timeout, 2))
        assertFalse(policy.canRetry(timeout, 3))
        assertFalse(policy.canRetry(timeout, 0, hasOutput = true))
        assertFalse(policy.canRetry(kotlinx.coroutines.CancellationException("stop"), 0))
        assertFalse(policy.canRetry(Exception("HTTP 401: invalid"), 0))
        val custom = com.aiassistant.domain.model.RetryPolicy(mapOf(com.aiassistant.domain.model.RetryErrorType.AUTHENTICATION to com.aiassistant.domain.model.RetryRule(true, 2), com.aiassistant.domain.model.RetryErrorType.TIMEOUT to com.aiassistant.domain.model.RetryRule(false, 20)))
        assertTrue(custom.canRetry(Exception("HTTP 401: invalid"), 1))
        assertFalse(custom.canRetry(Exception("HTTP 401: invalid"), 2))
        assertFalse(custom.canRetry(timeout, 0))
        assertEquals(5_000L, custom.delayMillis(19))
    }

    // 1. 网络稳定性测试：验证 OkHttpClient 的 fastFallback、pingInterval、连接池配置及合规 User-Agent
    @Test
    fun testNetworkStabilityConfiguration() {
        val streamClient = RetrofitClient.streamHttpClient
        assertTrue("请求发送前必须允许连接地址回退", streamClient.retryOnConnectionFailure)
        assertTrue(RetrofitClient.longAnalysisHttpClient.retryOnConnectionFailure)
        assertEquals(120_000, streamClient.readTimeoutMillis)
        assertEquals(600L, RetrofitClient.STREAM_IDLE_TIMEOUT_SECONDS)
        assertEquals("streamHttpClient 应配置 15s 的 HTTP/2 pingInterval 保活心跳", 15_000, streamClient.pingIntervalMillis)
        assertNotNull("streamHttpClient 应配置专用连接池", streamClient.connectionPool)

        val restClient = RetrofitClient.restHttpClient
        assertTrue(restClient.retryOnConnectionFailure)
        assertNotNull("restClient 应配置专用连接池", restClient.connectionPool)

        assertEquals("默认 User-Agent 必须为合规标识而非默认 okhttp", "Echo-Assistant/2.2.5 (Android; Mobile)", RetrofitClient.DEFAULT_USER_AGENT)
    }

    // 2. 异常判定测试：验证协议抖动与超时异常均被正确识别为网络波动以触发退避重试
    @Test
    fun testNetworkFluctuationExceptionIdentification() {
        assertTrue("SocketTimeoutException 应被判定为网络波动", AiRepository.isNetworkFluctuationException(SocketTimeoutException("Read timed out")))
        assertTrue("ProtocolException 应被判定为网络波动", AiRepository.isNetworkFluctuationException(ProtocolException("unexpected end of stream")))
        assertTrue("包含 http2 stream reset 的异常应被判定为网络波动", AiRepository.isNetworkFluctuationException(Exception("HTTP2 stream was reset by peer")))
        assertTrue("SSL 握手抖动应被判定为网络波动", AiRepository.isNetworkFluctuationException(Exception("SSL handshake failed due to timeout")))
        assertFalse("业务逻辑参数错误不应被判定为网络波动", AiRepository.isNetworkFluctuationException(IllegalArgumentException("Invalid model name")))
    }

    // 3. 沉浸式记忆与去元词汇测试：彻底消除“用户把AI当成心爱的哥哥”等出戏表述
    @Test
    fun testSanitizeMetaLanguageRemovesImmersionBreakingTerms() {
        // 典型出戏文本输入
        val rawInput1 = "用户把AI当成心爱的哥哥"
        val sanitized1 = SmartMemoryExtractor.sanitizeMetaLanguage(rawInput1)
        assertEquals("视对方为心爱的哥哥", sanitized1)
        assertFalse("净化后绝对不可包含'用户把AI当成'", sanitized1.contains("用户把AI当成"))
        assertFalse("净化后绝对不可包含'AI'", sanitized1.contains("AI"))

        val rawInput2 = "把模型当做最好的朋友"
        val sanitized2 = SmartMemoryExtractor.sanitizeMetaLanguage(rawInput2)
        assertEquals("视对方为最好的朋友", sanitized2)
        assertFalse("净化后不应出现'模型'", sanitized2.contains("模型"))

        val rawInput3 = "用户要求AI在回答时保持温柔"
        val sanitized3 = SmartMemoryExtractor.sanitizeMetaLanguage(rawInput3)
        assertEquals("要求在互动中在回答时保持温柔", sanitized3)

        // 验证提炼管线 refineMemoryContent
        val (distilled, category) = SmartMemoryExtractor.refineMemoryContent("根据上述对话提炼出如下核心事实：用户把AI当成心爱的哥哥")
        assertEquals("角色设定：视对方为心爱的哥哥", distilled)
        assertEquals("PROJECT", category)
        assertFalse(distilled.contains("用户把AI"))
    }

    // 4. 辅助模型提炼提示词模板测试：验证严格沉浸感准则
    @Test
    fun testAuxiliaryMemoryPromptImmersionRules() {
        val prompt = PersonalizationManager.DEFAULT_AUXILIARY_MEMORY_PROMPT
        assertTrue("提炼提示词必须强调沉浸感最高准则", prompt.contains("沉浸感最高准则"))
        assertTrue("提炼提示词必须明文严禁用户、AI、模型等元词汇", prompt.contains("严禁在提炼的事实中出现“用户”、“AI”、“模型”"))
        assertTrue("提炼提示词必须要求规范角色扮演关系表述", prompt.contains("视对方为心爱的哥哥"))
    }

    // 5. 多 Key 错误汇总展示测试
    @Test
    fun testMultiKeyCompositeErrorReporting() {
        val failures = listOf(
            KeyAttemptFailure(1, "...4a8b", "HTTP 401 Unauthorized - 密钥无效"),
            KeyAttemptFailure(2, "...9c12", "HTTP 429 Too Many Requests - 频率超限"),
            KeyAttemptFailure(3, "...7e3f", "网络连接超时 (SocketTimeoutException 30s)", isTimeout = true)
        )

        val compositeMessage = buildString {
            append("所有 API Key 均请求失败 (共尝试 ${failures.size} 个 Key)：\n")
            failures.forEach { failure ->
                append("• Key #${failure.keyIndex} (${failure.keyMasked})：${failure.errorMessage}\n")
            }
            append("\n建议检查 API 地址、网络连接或对应 Key 的额度与可用状态。")
        }.trim()

        assertTrue(compositeMessage.contains("共尝试 3 个 Key"))
        assertTrue(compositeMessage.contains("Key #1 (...4a8b)：HTTP 401"))
        assertTrue(compositeMessage.contains("Key #2 (...9c12)：HTTP 429"))
        assertTrue(compositeMessage.contains("Key #3 (...7e3f)：网络连接超时"))
    }

    // 6. 暂停生成时保留前序 Key 报错原因测试
    @Test
    fun testRetainKeyErrorsWhenStoppedByUser() {
        val attemptErrors = mutableListOf(
            "Key #1 (...4a8b)：HTTP 401 Unauthorized",
            "Key #2 (...9c12)：网络连接超时 (SocketTimeoutException)"
        )

        val finalContent = buildString {
            append("回复已停止 (用户已暂停)\n\n")
            append("【已尝试 Key 报错记录】：\n")
            attemptErrors.forEach { append("• $it\n") }
            append("\n*(在尝试后续 Key 期间，用户主动暂停了回复)*")
        }.trim()

        assertTrue(finalContent.contains("回复已停止 (用户已暂停)"))
        assertTrue(finalContent.contains("【已尝试 Key 报错记录】："))
        assertTrue(finalContent.contains("• Key #1 (...4a8b)：HTTP 401"))
        assertTrue(finalContent.contains("• Key #2 (...9c12)：网络连接超时"))
        assertTrue(finalContent.contains("用户主动暂停了回复"))
    }
}
