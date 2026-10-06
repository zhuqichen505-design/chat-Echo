package com.aiassistant

import android.app.Application
import androidx.room.Room
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.*
import com.aiassistant.utils.CryptoManager
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.TavilySearchManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ChatRetryRepositoryTest {
    @org.junit.Before fun allowSyntheticHttpEndpoints() { HttpTestAccess.start() }
    @org.junit.After fun restoreClosedHttpPolicy() { HttpTestAccess.stop() }
    @Test fun modelCatalogueHttpDeniedReportsPermissionRatherThanPresetOrKeyFallback() = runBlocking {
        Endpoint { socket, _ -> socket.response("200 OK", "{\"data\":[{\"id\":\"vendor/Exact-ID\"}]}") }.use { endpoint -> Fixture().use { f ->
            com.aiassistant.data.remote.HttpAccessPolicy.initialize { com.aiassistant.data.remote.HttpAccessRules() }
            val direct = f.repository.fetchAvailableModelsDirect(endpoint.url, "synthetic-key-one\nsynthetic-key-two")
            assertTrue(direct.exceptionOrNull() is com.aiassistant.data.remote.HttpAccessDeniedException)
            assertTrue(endpoint.payloads.isEmpty())
            val (config, id) = f.config(endpoint.url, keys = "synthetic-key-one\nsynthetic-key-two")
            // Blank stored key avoids unrelated AndroidKeyStore migration in this Unsafe JVM fixture.
            f.db.apiConfigDao().updateConfig(config.copy(apiKey = ""))
            val stored = f.repository.fetchAvailableModels(config.id)
            assertTrue(stored.exceptionOrNull() is com.aiassistant.data.remote.HttpAccessDeniedException)
            var error: String? = null
            f.settings.setBackupKeyFallbackEnabled(true)
            f.repository.sendChatMessageWithConfig(config, id, "request", onToken = {}, onComplete = { _, _, _ -> fail("Blocked chat completed") }, onError = { error = it })
            assertTrue(error!!.contains("HTTP 访问已关闭"))
            assertTrue(endpoint.payloads.isEmpty())
            com.aiassistant.data.remote.HttpAccessPolicy.initialize { com.aiassistant.data.remote.HttpAccessRules(true, listOf(endpoint.url)) }
            assertEquals(listOf("vendor/Exact-ID"), f.repository.fetchAvailableModelsDirect(endpoint.url, "synthetic-key-one").getOrThrow())
            assertEquals(1, endpoint.payloads.size)
        } }
    }
    private class Endpoint(val respond: (Socket, Int) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        private val worker = Executors.newCachedThreadPool()
        val payloads = CopyOnWriteArrayList<String>()
        val url = "http://127.0.0.1:${server.localPort}/v1"
        init {
            HttpTestAccess.allow(url)
            worker.submit {
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    worker.submit { socket.use {
                        try {
                            socket.soTimeout = 5000
                            val input = socket.getInputStream()
                            fun line(): String {
                                val bytes = java.io.ByteArrayOutputStream()
                                while (true) {
                                    val value = input.read()
                                    if (value < 0 || value == 10) break
                                    if (value != 13) bytes.write(value)
                                }
                                return bytes.toString("UTF-8")
                            }
                            line()
                            var length = 0
                            while (true) {
                                val header = line()
                                if (header.isEmpty()) break
                                if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                            }
                            val body = ByteArray(length)
                            var read = 0
                            while (read < length) {
                                val count = input.read(body, read, length - read)
                                check(count > 0)
                                read += count
                            }
                            payloads.add(String(body, Charsets.UTF_8))
                            respond(socket, payloads.size)
                        } catch (_: java.io.IOException) { /* Expected peer cancellation. */ }
                    } }
                } catch (_: java.io.IOException) { if (server.isClosed) break }
            }
        } }
        override fun close() {
            server.close()
            worker.shutdownNow()
            worker.awaitTermination(3, TimeUnit.SECONDS)
        }
    }

    private fun Socket.response(status: String, body: String, type: String = "application/json") {
        val bytes = body.toByteArray()
        getOutputStream().apply {
            write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    private fun Socket.streamHeaders() {
        getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
        getOutputStream().flush()
    }
    private fun Socket.delta(text: String) {
        getOutputStream().write(text.toByteArray())
        getOutputStream().flush()
    }

    private class Fixture(timeout: Long = 2000) : AutoCloseable {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = PersonalizationManager(context)
        val repository: AiRepository
        private val instance = AiAssistantApp::class.java.getDeclaredField("instance").apply { isAccessible = true }
        private val previous = instance.get(null)
        init {
            context.getSharedPreferences("personalization_settings", 0).edit().clear().commit()
            settings.setBackupKeyFallbackEnabled(false)
            settings.saveSettings(settings.getSettings().copy(autoNameEnabled = false, autoMemoryEnabled = false, auxiliaryMemoryEnabled = false))
            val app = AiAssistantApp()
            instance.set(null, app)
            AiAssistantApp::class.java.getDeclaredField("database").apply { isAccessible = true }.set(app, db)
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) }
            val crypto = unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, CryptoManager::class.java) as CryptoManager
            repository = AiRepository(db.folderDao(), db.apiConfigDao(), db.conversationDao(), db.messageDao(), db.usageStatDao(),
                db.environmentVariableDao(), db.promptTemplateDao(), db.memoryDao(), db.conversationBranchDao(), db.selectedModelDao(),
                crypto, settings, TavilySearchManager(context, crypto), firstTokenTimeoutMillis = timeout)
        }
        suspend fun config(url: String, protocol: String = "openai", keys: String = "test-only"): Pair<ApiConfig, Long> {
            val config = ApiConfig(name = "synthetic", provider = "proxy", apiType = protocol, baseUrl = url,
                apiKey = keys, modelName = "vendor/Exact-ID", enableThinking = false)
            val configId = db.apiConfigDao().insertConfig(config)
            val id = db.conversationDao().insertConversation(Conversation(title = "retry regression", apiConfigId = configId, modelName = config.modelName))
            return config.copy(id = configId) to id
        }
        override fun close() { db.close(); instance.set(null, previous) }
    }

    @Test fun directionPlanningAndFinalReplyShareContextWithoutSavingUnchosenOptionsInEitherProtocol() = runBlocking {
        val gson = com.google.gson.Gson()
        val planner = """{"directions":[{"title":"感受","description":"先聊昨天的感受"},{"title":"剧情","description":"继续合理的新发展"}]}"""
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, n ->
                val text = gson.toJson(if (n == 1) planner else "正式回复")
                val body = if (protocol == "openai") "data: {\"choices\":[{\"delta\":{\"content\":$text},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                    else "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":$text}}\n\ndata: {\"type\":\"message_stop\"}\n\n"
                socket.response("200 OK", body, "text/event-stream")
            }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.config(endpoint.url, protocol)
                f.repository.saveMessage(Message(conversationId = id, role = "user", content = "昨天在公园散步"))
                f.repository.saveMessage(Message(conversationId = id, role = "assistant", content = "我们聊到了那棵树"))
                f.repository.saveMessage(Message(conversationId = id, role = "user", content = "接下来呢"))
                val options = ChatRequestOptions(enableThinking = false, enableWebSearch = false, enableSessionMemory = false,
                    overrideSystemPrompt = true, systemPromptOverride = "保持角色设定，昨天是故事里的时间")
                val prepared = f.repository.prepareReplyDirectionContext(config, id, "接下来呢", options)
                val directions = f.repository.generateReplyDirections(config, id, "接下来呢", emptyList(), options.copy(preparedDirectionContext = prepared), 2)
                assertEquals(2, directions.size)
                assertEquals(3, f.db.messageDao().getMessagesList(id).size)
                val selection = ReplyDirections.instruction(ReplyDirectionDecision(ReplyDirectionAction.SELECT, 0), directions)
                var final: String? = null
                f.repository.sendChatMessageWithConfig(config, id, "接下来呢", options = options.copy(preparedDirectionContext = prepared, replyDirection = selection),
                    onToken = {}, onComplete = { text, _, _ -> final = text }, onError = { fail(it) })
                assertEquals("正式回复", final)
                assertEquals(2, endpoint.payloads.size)
                endpoint.payloads.forEach { payload ->
                    assertTrue(payload.contains("vendor/Exact-ID")); assertTrue(payload.contains("昨天在公园散步"))
                    assertTrue(payload.contains("那棵树")); assertTrue(payload.contains("保持角色设定"))
                }
                assertTrue(endpoint.payloads[0].contains("暂不生成正式回复"))
                assertFalse(endpoint.payloads[1].contains("暂不生成正式回复"))
                assertTrue(endpoint.payloads[1].contains("先聊昨天的感受"))
                assertFalse(endpoint.payloads[1].contains("继续合理的新发展"))
                val messages = f.db.messageDao().getMessagesList(id)
                assertEquals(4, messages.size); assertEquals(selection, messages.last().replyDirection)
                assertFalse(messages.any { it.content.contains("directions") })
                f.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM memory_items").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            } }
        }
    }

    @Test fun invalidDirectionResponseFailsWithoutMessagesOrHiddenExtraRequests() = runBlocking {
        Endpoint { socket, _ -> socket.response("200 OK", "data: {\"choices\":[{\"delta\":{\"content\":\"not JSON\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n", "text/event-stream") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.config(endpoint.url)
            val prepared = PreparedDirectionContext("system", emptyList(), "user", emptyList())
            try { f.repository.generateReplyDirections(config, id, "user", emptyList(), ChatRequestOptions(preparedDirectionContext = prepared), 2); fail("invalid JSON accepted") }
            catch (_: Exception) { }
            assertEquals(1, endpoint.payloads.size)
            assertTrue(f.db.messageDao().getMessagesList(id).isEmpty())
        } }
    }

    @Test fun server500PublishesEveryFailureBeforeRetryAndHonorsExactCount() = runBlocking {
        val events = CopyOnWriteArrayList<String>()
        Endpoint { socket, n -> events.add("request$n"); socket.response("500 Internal Server Error", "{\"error\":{\"message\":\"upstream synthetic failure\"}}") }.use { endpoint ->
            Fixture().use { f ->
                f.settings.saveRetryRule(RetryErrorType.SERVER, RetryRule(true, 2))
                val (config, id) = f.config(endpoint.url)
                f.repository.sendChatMessageWithConfig(config, id, "OK", onToken = { fail("No output expected") },
                    onKeyAttemptError = { _, _, error -> events.add("failure:$error") }, onStatusUpdate = { events.add("status:$it") },
                    onComplete = { _, _, _ -> fail("Must fail") }, onError = { events.add("final:$it") })
                assertEquals(3, endpoint.payloads.size)
                assertEquals(3, events.count { it.startsWith("failure:") })
                assertTrue(events.last().startsWith("final:API错误 (500)"))
                for (n in 1..2) {
                    val failure = events.indexOfFirst { it.startsWith("failure:第 $n 次") }
                    val status = events.indexOfFirst { it.startsWith("status:") && it.contains("($n/2)") }
                    assertTrue(failure >= 0 && failure < status && status < events.indexOf("request${n + 1}"))
                }
            }
        }
    }

    @Test fun masterOffPreventsAllRetriesAndBackupKeys() = runBlocking {
        Endpoint { socket, _ -> socket.response("500 Internal Server Error", "{\"error\":\"synthetic\"}") }.use { endpoint -> Fixture().use { f ->
            f.settings.saveRetryRule(RetryErrorType.SERVER, RetryRule(true, 3))
            f.settings.setBackupKeyFallbackEnabled(true)
            f.settings.setModelRetryEnabled(false)
            val (config, id) = f.config(endpoint.url, keys = "test-first\ntest-second")
            var error: String? = null
            f.repository.sendChatMessageWithConfig(config, id, "OK", onToken = { fail(it) }, onComplete = { _, _, _ -> fail("Must fail") }, onError = { error = it })
            assertEquals(1, endpoint.payloads.size)
            assertTrue(error!!.contains("API错误 (500)"))
        } }
    }

    @Test fun emptyResponseCannotStartHiddenCompressionRetry() = runBlocking {
        Endpoint { socket, _ -> socket.response("200 OK", "data: [DONE]\n\n", "text/event-stream") }.use { endpoint -> Fixture().use { f ->
            f.settings.saveRetryRule(RetryErrorType.EMPTY_RESPONSE, RetryRule(true, 1))
            val (config, id) = f.config(endpoint.url)
            var prompts = 0
            var error: String? = null
            f.repository.sendChatMessageWithConfig(config, id, "OK", onToken = {}, onComplete = { _, _, _ -> fail("Must fail") },
                onError = { error = it }, onContextFallbackPrompt = { prompts++; ContextFallbackChoice.FALLBACK })
            assertEquals(2, endpoint.payloads.size)
            assertEquals(0, prompts)
            assertTrue(error!!.contains("empty response detected"))
        } }
    }

    @Test fun backupKeySwitchIsSeparateFromSameKeyRetryBudget() = runBlocking {
        Endpoint { socket, _ -> socket.response("500 Internal Server Error", "{\"error\":\"synthetic\"}") }.use { endpoint -> Fixture().use { f ->
            f.settings.saveRetryRule(RetryErrorType.SERVER, RetryRule(true, 0))
            f.settings.setBackupKeyFallbackEnabled(true)
            val (config, id) = f.config(endpoint.url, keys = "test-first\ntest-second")
            val keys = mutableListOf<Int>()
            var error: String? = null
            f.repository.sendChatMessageWithConfig(config, id, "OK", onToken = {}, onComplete = { _, _, _ -> fail("Must fail") },
                onKeyAttemptError = { key, _, _ -> keys.add(key) }, onError = { error = it })
            assertEquals(listOf(1, 2), keys)
            assertEquals(2, endpoint.payloads.size)
            assertTrue(error!!.contains("共尝试 2 个 Key"))
        } }
    }

    @Test fun disabledCategoryAndZeroCountEachMakeExactlyOneRequest() = runBlocking {
        for (rule in listOf(RetryRule(false, 3), RetryRule(true, 0))) {
            Endpoint { socket, _ -> socket.response("500 Internal Server Error", "{\"error\":\"synthetic\"}") }.use { endpoint -> Fixture().use { f ->
                f.settings.saveRetryRule(RetryErrorType.SERVER, rule)
                val (config, id) = f.config(endpoint.url)
                var error: String? = null
                f.repository.sendChatMessageWithConfig(config, id, "OK", onToken = {}, onComplete = { _, _, _ -> fail("Must fail") }, onError = { error = it })
                assertEquals(1, endpoint.payloads.size)
                assertTrue(error!!.contains("API错误 (500)"))
            } }
        }
    }

    @Test fun partialStreamDisconnectIsPreservedAndNeverReplayed() = runBlocking {
        Endpoint { socket, _ -> socket.streamHeaders(); socket.delta("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n") }.use { endpoint -> Fixture().use { f ->
            f.settings.saveRetryRule(RetryErrorType.NETWORK, RetryRule(true, 3))
            f.settings.setBackupKeyFallbackEnabled(true)
            val (config, id) = f.config(endpoint.url, keys = "test-first\ntest-second")
            var completed: String? = null
            f.repository.sendChatMessageWithConfig(config, id, "OK", onToken = {}, onComplete = { text, _, _ -> completed = text }, onError = { fail(it) })
            assertEquals("partial", completed)
            assertEquals(1, endpoint.payloads.size)
            assertEquals("partial", f.db.messageDao().getMessagesList(id).single().content)
        } }
    }

    @Test fun heartbeatsAndEmptyDeltasCannotKeepEitherProtocolConnectingForever() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, _ ->
                socket.streamHeaders()
                repeat(30) { socket.delta(": ping\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}\n\n"); Thread.sleep(50) }
            }.use { endpoint -> Fixture(500).use { f ->
                f.settings.saveRetryRule(RetryErrorType.TIMEOUT, RetryRule(true, 1))
                val (config, id) = f.config(endpoint.url, protocol)
                val failures = mutableListOf<String>()
                var error: String? = null
                f.repository.sendChatMessageWithConfig(config, id, "OK", onToken = {}, onComplete = { _, _, _ -> fail("Must time out") },
                    onKeyAttemptError = { _, _, e -> failures.add(e) }, onError = { error = it })
                assertEquals(protocol, 2, endpoint.payloads.size)
                assertEquals(2, failures.size)
                assertTrue(error!!.contains("首个有效回复等待超时"))
                assertFalse(error!!.contains("Canceled"))
                assertEquals(0, f.db.messageDao().getMessagesList(id).size)
            } }
        }
    }

    @Test fun firstTextOrThinkingDeltaAllowsGenerationPastFirstTokenDeadline() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, _ ->
                socket.streamHeaders()
                if (protocol == "openai") socket.delta("data: {\"choices\":[{\"delta\":{\"content\":\"O\"}}]}\n\n")
                else socket.delta("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"thought\"}}\n\n")
                Thread.sleep(900)
                if (protocol == "openai") socket.delta("data: {\"choices\":[{\"delta\":{\"content\":\"K\"}}]}\n\ndata: [DONE]\n\n")
                else socket.delta("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\nevent: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
            }.use { endpoint -> Fixture(500).use { f ->
                val (config, id) = f.config(endpoint.url, protocol)
                var completed = false
                val thinking = StringBuilder()
                f.repository.sendChatMessageWithConfig(config, id, "OK", onToken = {}, onThinkingToken = { thinking.append(it) },
                    onComplete = { text, _, _ -> assertEquals("OK", text); completed = true }, onError = { fail(it) })
                assertTrue(completed)
                assertEquals(1, endpoint.payloads.size)
                assertEquals("OK", f.db.messageDao().getMessagesList(id).single().content)
                if (protocol == "anthropic") assertEquals("thought", thinking.toString())
            } }
        }
    }
}
