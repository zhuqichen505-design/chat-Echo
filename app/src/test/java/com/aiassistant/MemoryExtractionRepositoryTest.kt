package com.aiassistant

import android.app.Application
import androidx.room.Room
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.*
import com.aiassistant.utils.CryptoManager
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.TavilySearchManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MemoryExtractionRepositoryTest {
    @org.junit.Before fun allowSyntheticHttpEndpoints() { HttpTestAccess.start() }
    @org.junit.After fun restoreClosedHttpPolicy() { HttpTestAccess.stop() }
    private class Endpoint(val respond: (Socket, JsonObject) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newCachedThreadPool()
        val url = "http://127.0.0.1:${server.localPort}/v1"
        val payloads = CopyOnWriteArrayList<JsonObject>()
        val paths = CopyOnWriteArrayList<String>()
        val failures = CopyOnWriteArrayList<Throwable>()
        init {
            HttpTestAccess.allow(url)
            executor.submit {
                while (!server.isClosed) {
                    try {
                        val socket = server.accept()
                        executor.submit {
                            socket.use {
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
                                    paths.add(line())
                                    var length = 0
                                    while (true) {
                                        val header = line()
                                        if (header.isEmpty()) break
                                        if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                                    }
                                    val bytes = ByteArray(length)
                                    var read = 0
                                    while (read < length) {
                                        val count = input.read(bytes, read, length - read)
                                        check(count > 0)
                                        read += count
                                    }
                                    val body = JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
                                    payloads.add(body)
                                    respond(socket, body)
                                } catch (error: Throwable) {
                                    if (!server.isClosed) failures.add(error)
                                }
                            }
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

    private fun reply(socket: Socket, body: String, status: String = "200 OK", type: String = "application/json") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
        socket.getOutputStream().flush()
    }

    private fun memoryReply(socket: Socket, text: String) = reply(socket,
        Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to text))))))

    private fun streamReply(socket: Socket) = reply(socket,
        "data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\ndata: [DONE]\n\n", type = "text/event-stream")

    private class Fixture : AutoCloseable {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = PersonalizationManager(context)
        // Synthetic localhost key only. Bypass AndroidKeyStore construction in JVM tests;
        // the existing legacy-plain-key fallback returns this non-secret unchanged.
        private val unsafeClass = Class.forName("sun.misc.Unsafe")
        private val unsafe = unsafeClass.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) }
        private val crypto = unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, CryptoManager::class.java) as CryptoManager
        private val instanceField = AiAssistantApp::class.java.getDeclaredField("instance").apply { isAccessible = true }
        private val previousApp = instanceField.get(null)
        val repository: AiRepository
        init {
            val app = AiAssistantApp()
            instanceField.set(null, app)
            AiAssistantApp::class.java.getDeclaredField("database").apply { isAccessible = true }.set(app, db)
            settings.saveSettings(settings.getSettings().copy(autoMemoryEnabled = true, auxiliaryMemoryEnabled = false))
            repository = AiRepository(db.folderDao(), db.apiConfigDao(), db.conversationDao(), db.messageDao(), db.usageStatDao(),
                db.environmentVariableDao(), db.promptTemplateDao(), db.memoryDao(), db.conversationBranchDao(), db.selectedModelDao(),
                crypto, settings, TavilySearchManager(context, crypto), timelineNodeDao = db.timelineNodeDao())
        }
        suspend fun conversation(url: String, model: String = "vendor/gpt-5.2:Exact-ID", protocol: String = "openai", enabled: Boolean = true, tags: String? = null): Pair<ApiConfig, Long> {
            val config = ApiConfig(name = "Synthetic", provider = "proxy", baseUrl = url, apiKey = "test-only", modelName = model, apiType = protocol, enableThinking = false)
            val configId = db.apiConfigDao().insertConfig(config)
            val id = db.conversationDao().insertConversation(Conversation(title = "memory regression", apiConfigId = configId, modelName = model, enableSessionMemory = enabled, tags = tags))
            return config.copy(id = configId) to id
        }
        override fun close() {
            db.close()
            instanceField.set(null, previousApp)
        }
    }

    private suspend fun chat(f: Fixture, config: ApiConfig, id: Long) {
        val tokens = StringBuilder()
        f.repository.sendChatMessageWithConfig(config, id, "reply OK", onToken = { tokens.append(it) },
            onComplete = { text, _, _ -> assertEquals("OK", text) }, onError = { fail(it) })
        assertEquals("OK", tokens.toString())
    }

    @Test fun incrementalTimelineRequestsUseOnlyCompatibleTokenLimitAndKeepTailSettings() = runBlocking {
        for (model in listOf("vendor/gpt-5.2:Exact-ID", "deepseek-chat")) {
            Endpoint { socket, body ->
                if (body.has("max_tokens") && body.has("max_completion_tokens")) {
                    reply(socket, "{\"error\":{\"message\":\"mutually exclusive token limits\"}}", "400 Bad Request")
                } else memoryReply(socket, """<think>NO_UPDATE 草稿</think>{"newStoryTime":null,"action":"APPEND","targetNodeId":null,"newEvent":{"timeTag":"第1天·清晨","category":"PLOT_EVENT","content":"林岚抵达古塔并与守门人缔结盟约"},"atemporalSettings":[{"category":"世界规则","content":"古塔禁止一切火焰魔法","targetScope":"global"}]}""")
            }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url, model)
                val tail = "古塔禁止一切火焰魔法"
                val result = f.repository.evaluateAndAutoUpdateTimeline(id, "继续进入古塔", "叙述".repeat(1200) + tail, config.id, model)!!
                assertNull(result.extractionErrorMessage)
                assertNull(result.updatedStoryTime)
                assertEquals("林岚抵达古塔并与守门人缔结盟约", result.newEvent!!.content)
                assertEquals(tail, result.atemporalSettings.single().content)
                assertEquals("session", result.atemporalSettings.single().targetScope)
                assertTrue(f.db.memoryDao().getConversationMemories(id).isEmpty())
                assertTrue(f.repository.getTimelineNodes(id).isEmpty())
                val request = endpoint.payloads.single()
                assertEquals(model, request.get("model").asString)
                assertEquals(model.contains("gpt-5"), request.has("max_completion_tokens"))
                assertEquals(!model.contains("gpt-5"), request.has("max_tokens"))
                assertTrue(request.getAsJsonArray("messages").single().asJsonObject.get("content").asString.contains(tail))
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun incrementalSettingsOnlyHandlesNullEventAndDeduplicatesExistingFacts() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, """{"newStoryTime":null,"newEvent":null,"atemporalSettings":[{"category":"世界规则","content":"古塔禁止一切火焰魔法"},{"category":"角色特质","content":"林岚天生无法感知寒冷"},{"category":"角色特质","content":"林岚天生无法感知寒冷"}]}""") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            f.repository.addConversationMemory(id, "【世界规则】古塔禁止一切火焰魔法")
            val result = f.repository.evaluateAndAutoUpdateTimeline(id, "继续讲述故事", "林岚解释自己天生无法感知寒冷", config.id, config.modelName)!!
            assertNull(result.extractionErrorMessage)
            assertNull(result.newEvent)
            assertNull(result.updatedStoryTime)
            assertEquals("SETTINGS_ONLY", result.action)
            assertEquals("林岚天生无法感知寒冷", result.atemporalSettings.single().content)
            assertEquals(1, f.db.memoryDao().getConversationMemories(id).size)
        } }
    }

    @Test fun incrementalUpdateKeepsExistingEventTarget() = runBlocking {
        var node = 0L
        Endpoint { socket, _ -> memoryReply(socket, """{"newStoryTime":null,"action":"UPDATE","targetNodeId":$node,"newEvent":{"timeTag":"第1天·清晨","category":"PLOT_EVENT","content":"林岚抵达古塔并与守门人缔结盟约"},"atemporalSettings":[]}""") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            node = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id, timeTag = "第1天·清晨", event = "林岚抵达古塔", category = "PLOT_EVENT"))
            val result = f.repository.evaluateAndAutoUpdateTimeline(id, "继续交谈", "林岚抵达古塔并与守门人缔结盟约", config.id, config.modelName)!!
            assertEquals("UPDATE", result.action)
            assertEquals(node, result.targetNodeId)
            assertEquals("林岚抵达古塔", result.previousEventContent)
            assertEquals("林岚抵达古塔", f.repository.getTimelineNodes(id).single().event)
        } }
    }

    @Test fun incrementalNoUpdateDoesNotOfferEmptyProposalAndDisabledMemoryMakesNoRequest() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "NO_UPDATE") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, enabled = false)
            assertNull(f.repository.evaluateAndAutoUpdateTimeline(id, "你好今天如何", "你好很高兴见到你", config.id, config.modelName))
            assertEquals(0, endpoint.payloads.size)
            assertNull(f.repository.evaluateAndAutoUpdateTimeline(id, "你好今天如何", "你好很高兴见到你", config.id, config.modelName, sessionMemoryEnabled = true))
            assertEquals(1, endpoint.payloads.size)
        } }
    }

    @Test fun incrementalNoUpdateWithExplanationOrFencesStaysSilentInsteadOfJsonError() = runBlocking {
        // 回归：模型返回带说明/围栏/思考过程的 NO_UPDATE 时，必须静默无提案，
        // 不得误报“模型未返回有效的提取 JSON”打扰用户。
        for (text in listOf(
            "NO_UPDATE，本轮暂无时间推进",
            "```\nNO_UPDATE\n```",
            "<think>经分析{无关草稿}，时间未变</think>NO_UPDATE"
        )) {
            Endpoint { socket, _ -> memoryReply(socket, text) }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url)
                assertNull(f.repository.evaluateAndAutoUpdateTimeline(id, "继续闲聊", "今天天气不错，适合散步", config.id, config.modelName))
                assertEquals(1, endpoint.payloads.size)
            } }
        }
    }

    @Test fun incrementalChineseProseNoChangeStaysSilentButGarbageRemainsVisible() = runBlocking {
        // 回归：模型用自然语言（无 NO_UPDATE、无 JSON）表示无变化时静默；
        // 既无 JSON 又无无变化表述的 genuinely 异常输出仍可见。
        Endpoint { socket, _ -> memoryReply(socket, "本轮只是日常寒暄，时间没有推进，也没有新增事件") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            assertNull(f.repository.evaluateAndAutoUpdateTimeline(id, "继续闲聊", "好的，继续讲这个轻松的日常", config.id, config.modelName))
            assertEquals(1, endpoint.payloads.size)
        } }
        Endpoint { socket, _ -> memoryReply(socket, "哈哈，这段剧情真有意思") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val result = f.repository.evaluateAndAutoUpdateTimeline(id, "继续闲聊", "好的，继续讲这个轻松的日常", config.id, config.modelName)!!
            assertTrue(result.extractionErrorMessage!!.contains("有效的提取 JSON"))
            assertTrue(result.extractionErrorMessage!!.contains(config.modelName))
            assertNull(result.newEvent)
            assertTrue(result.atemporalSettings.isEmpty())
            assertEquals("严格格式重试必须恰好一次，不反复追打模型", 2, endpoint.payloads.size)
        } }
    }

    @Test fun incrementalStrictRetryRecoversWhenFirstReplyIgnoresFormat() = runBlocking {
        var calls = 0
        Endpoint { socket, _ ->
            calls++
            if (calls <= 1) memoryReply(socket, "让我想想这轮讲了什么")
            else memoryReply(socket, """{"newStoryTime":null,"action":"APPEND","targetNodeId":null,"newEvent":{"timeTag":"第1天·清晨","category":"PLOT_EVENT","content":"林岚抵达古塔并与守门人缔结盟约"},"atemporalSettings":[]}""")
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val result = f.repository.evaluateAndAutoUpdateTimeline(id, "继续讲述故事", "林岚抵达古塔并与守门人缔结盟约", config.id, config.modelName)!!
            assertNull(result.extractionErrorMessage)
            assertEquals("林岚抵达古塔并与守门人缔结盟约", result.newEvent!!.content)
            assertEquals(2, endpoint.payloads.size)
            assertTrue(endpoint.payloads[1].getAsJsonArray("messages").single().asJsonObject.get("content").asString.contains("只输出纯 JSON"))
        } }
    }

    @Test fun incrementalFailureAndMalformedJsonAreVisibleInsteadOfSilentTimeOnlyFallback() = runBlocking {
        for (malformed in listOf(false, true)) {
            Endpoint { socket, _ ->
                if (malformed) memoryReply(socket, "{无效JSON}")
                else reply(socket, "{\"error\":{\"message\":\"synthetic rejection\"}}", "400 Bad Request")
            }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url)
                val result = f.repository.evaluateAndAutoUpdateTimeline(id, "继续讲述故事", "林岚在古塔门前观察四周", config.id, config.modelName)!!
                assertNotNull(result.extractionErrorMessage)
                assertNull(result.newEvent)
                assertTrue(result.atemporalSettings.isEmpty())
                assertEquals(1, endpoint.payloads.size)
            } }
        }
    }

    @Test fun incrementalCancellationDoesNotBecomeTimeOnlyOrErrorProposal() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Endpoint { _, _ -> entered.countDown(); release.await(10, TimeUnit.SECONDS) }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val extraction = async(Dispatchers.IO) { f.repository.evaluateAndAutoUpdateTimeline(id, "继续讲述故事", "夜幕降临，林岚返回古塔", config.id, config.modelName) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                f.repository.cancelActiveRequest(id)
                try { withTimeout(3000) { extraction.await() }; fail("Cancellation must propagate") } catch (_: CancellationException) { }
                assertTrue(extraction.isCancelled)
                assertTrue(f.repository.getTimelineNodes(id).isEmpty())
            } finally { release.countDown(); extraction.cancelAndJoin() }
        } }
    }

    @Test fun differentTimeUpdateAndSimilarAppendNeverOverwriteEarlierEvent() = runBlocking {
        for (action in listOf("UPDATE", "APPEND")) {
            var nodeId = 0L
            Endpoint { socket, _ -> memoryReply(socket, """{"action":"$action","targetNodeId":$nodeId,"newEvent":{"timeTag":"第2天·清晨","category":"PLOT_EVENT","content":"林岚抵达古塔并与守门人缔结盟约"},"atemporalSettings":[]}""") }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url)
                nodeId = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                    timeTag = "第1天·清晨", event = "林岚抵达古塔", category = "PLOT_EVENT"))
                val result = f.repository.evaluateAndAutoUpdateTimeline(id, "继续故事", "林岚抵达古塔并与守门人缔结盟约", config.id, config.modelName)!!
                assertEquals("APPEND", result.action)
                assertNull(result.targetNodeId)
                // Even a stale/manually edited UPDATE proposal must be checked again at save time.
                f.repository.applyAutoTimelineProposal(id, result.copy(action = "UPDATE", targetNodeId = nodeId))
                val nodes = f.db.timelineNodeDao().getTimelineNodes(id)
                assertEquals(2, nodes.size)
                val earlier = nodes.single { it.id == nodeId }
                assertEquals("第1天·清晨", earlier.timeTag)
                assertEquals("林岚抵达古塔", earlier.event)
                assertEquals("第2天·清晨", nodes.single { it.id != nodeId }.timeTag)
            } }
        }
    }

    @Test fun confirmedIncrementalFactsSaveInBothStoryScopesWithoutReplacingTimelineOrLeakingGlobally() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation("https://example.invalid/v1", tags = "story")
            val rpId = f.db.roleplaySessionDao().insertSession(RoleplaySession(conversationId = id))
            val nodeId = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                timeTag = "第1天·清晨", event = "林岚抵达古塔", category = "PLOT_EVENT"))
            val untouched = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                timeTag = "第1天·清晨", event = "守门人开启石门", category = "PLOT_EVENT", orderIndex = 1))
            val proposal = com.aiassistant.data.repository.AutoTimelineUpdateResult(null,
                com.aiassistant.utils.TimelineEventItem(timeTag = "第1天·清晨", content = "林岚抵达古塔并与守门人缔结盟约"),
                "待确认", action = "UPDATE", targetNodeId = nodeId,
                atemporalSettings = listOf(
                    com.aiassistant.utils.AtemporalSettingItem(category = "世界规则", content = "古塔禁止一切火焰魔法", targetScope = "global"),
                    com.aiassistant.utils.AtemporalSettingItem(content = "未选中不应保存", isSelected = false)))
            f.repository.applyAutoTimelineProposal(id, proposal)
            f.repository.applyAutoTimelineProposal(id, proposal)
            val nodes = f.repository.getTimelineNodes(id)
            assertEquals(2, nodes.size)
            assertEquals(proposal.newEvent!!.content, nodes.first { it.id == nodeId }.event)
            assertEquals("守门人开启石门", nodes.first { it.id == untouched }.event)
            val memory = f.db.memoryDao().getConversationMemories(id).single()
            assertEquals("【世界规则】古塔禁止一切火焰魔法", memory.content)
            assertEquals("conversation", memory.scope)
            assertEquals(id, memory.conversationId)
            assertEquals(1, f.db.memoryDao().getCandidateMemories(id).size)
            val rp = f.db.roleplayMemoryDao().getMemoriesListBySession(rpId).single()
            assertEquals(memory.content, rp.content)
            assertTrue(rp.isPinned)
        }
    }

    @Test fun incrementalAnthropicAnalysisKeepsProtocolAndSettings() = runBlocking {
        Endpoint { socket, _ -> reply(socket, Gson().toJson(mapOf("content" to listOf(mapOf("type" to "text",
            "text" to """{"newEvent":null,"atemporalSettings":[{"category":"世界规则","content":"古塔禁止一切火焰魔法"}]}"""))))) }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "vendor/claude-sonnet-4-6:Exact-ID", "anthropic")
            val result = f.repository.evaluateAndAutoUpdateTimeline(id, "继续讲述故事", "林岚解释古塔禁止一切火焰魔法", config.id, config.modelName)!!
            assertNull(result.extractionErrorMessage)
            assertEquals("古塔禁止一切火焰魔法", result.atemporalSettings.single().content)
            assertEquals(listOf("POST /v1/messages HTTP/1.1"), endpoint.paths)
            assertEquals(config.modelName, endpoint.payloads.single().get("model").asString)
            assertEquals(8192, endpoint.payloads.single().get("max_tokens").asInt)
            assertFalse(endpoint.payloads.single().has("max_completion_tokens"))
        } }
    }

    @Test fun savingAndMainChatDoNotWaitForMemoryAndDistilledResultIsPreserved() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Endpoint { socket, body ->
            if (body.get("stream").asBoolean) streamReply(socket) else {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                memoryReply(socket, "<think>草稿不能存入记忆</think>用户偏好：长期使用Kotlin开发Android应用")
            }
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "我长期使用Kotlin开发Android应用"))
            assertEquals(0, endpoint.payloads.size)
            chat(f, config, id)
            assertTrue(endpoint.payloads.single().get("stream").asBoolean)
            val extraction = async(Dispatchers.IO) { f.repository.processSavedMessageMemory(msg, config.id, config.modelName) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertFalse(extraction.isCompleted)
                assertNull(f.repository.processSavedMessageMemory(msg, config.id, config.modelName))
                // A blocked background extraction also cannot block a following main chat.
                val next = f.db.conversationDao().insertConversation(Conversation(title = "next", apiConfigId = config.id, modelName = config.modelName))
                chat(f, config, next)
                release.countDown()
                val candidate = withTimeout(5000) { extraction.await() }!!
                val memory = f.db.memoryDao().getBySourceMessage(msg)!!
                assertEquals(candidate.distilledContent, memory.content)
                assertTrue(memory.content.contains("Kotlin"))
                assertFalse(memory.content.contains("草稿"))
                assertEquals("user", memory.scope)
                assertNull(memory.conversationId)
                assertNull(f.repository.processSavedMessageMemory(msg, config.id, config.modelName))
                val requests = endpoint.payloads.filter { !it.get("stream").asBoolean }
                assertEquals(1, requests.size)
                assertEquals(config.modelName, requests.single().get("model").asString)
                assertFalse(requests.single().has("max_tokens"))
                assertEquals(4096, requests.single().get("max_completion_tokens").asInt)
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } finally { release.countDown(); extraction.cancelAndJoin() }
        } }
    }

    @Test fun bothSwitchesOffAndRoleplayIsolationMakeNoExtractionRequest() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "IGNORE") }.use { endpoint -> Fixture().use { f ->
            f.settings.saveSettings(f.settings.getSettings().copy(autoMemoryEnabled = false))
            val (config, id) = f.conversation(endpoint.url, enabled = false)
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "请记住：长期使用Kotlin开发Android应用"))
            assertNull(f.repository.processSavedMessageMemory(msg, config.id, config.modelName))
            f.settings.saveSettings(f.settings.getSettings().copy(autoMemoryEnabled = true))
            for (tag in listOf("roleplay", "story")) {
                val (rp, rpId) = f.conversation(endpoint.url, tags = tag)
                val rpMsg = f.repository.saveMessage(Message(conversationId = rpId, role = "user", content = "角色偏好必须保持隔离"))
                assertNull(f.repository.processSavedMessageMemory(rpMsg, rp.id, rp.modelName))
            }
            assertEquals(0, endpoint.payloads.size)
            assertNull(f.db.memoryDao().getBySourceMessage(msg))
        } }
    }

    @Test fun sessionOnlyStillOffersGlobalCandidateWithoutAutoSavingIt() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "用户偏好：长期使用Kotlin开发Android应用") }.use { endpoint -> Fixture().use { f ->
            f.settings.saveSettings(f.settings.getSettings().copy(autoMemoryEnabled = false))
            val (config, id) = f.conversation(endpoint.url)
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "我长期使用Kotlin开发Android应用"))
            val candidate = f.repository.processSavedMessageMemory(msg, config.id, config.modelName)!!
            assertTrue(candidate.distilledContent.contains("Kotlin"))
            assertNull(f.db.memoryDao().getBySourceMessage(msg))
            assertEquals(1, endpoint.payloads.size)
        } }
    }

    @Test fun auxiliaryIgnoreFallsBackToDifferentActiveModelInsteadOfRepeatingAuxiliary() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "IGNORE") }.use { auxiliary ->
            Endpoint { socket, _ -> memoryReply(socket, "用户偏好：长期使用Kotlin开发Android应用") }.use { active -> Fixture().use { f ->
                val (aux, _) = f.conversation(auxiliary.url, model = "deepseek-chat")
                val (config, id) = f.conversation(active.url)
                f.settings.saveSettings(f.settings.getSettings().copy(auxiliaryMemoryEnabled = true, auxiliaryMemoryApiConfigId = aux.id, auxiliaryMemoryModel = aux.modelName))
                val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "我长期使用Kotlin开发Android应用"))
                assertNotNull(f.repository.processSavedMessageMemory(msg, config.id, config.modelName))
                assertEquals(1, auxiliary.payloads.size)
                assertEquals(1, active.payloads.size)
                assertEquals(aux.modelName, auxiliary.payloads.single().get("model").asString)
                assertTrue(auxiliary.payloads.single().has("max_tokens"))
                assertFalse(auxiliary.payloads.single().has("max_completion_tokens"))
            } }
        }
    }

    @Test fun failedAuxiliaryDoesNotRepeatSameModelAndLocalFallbackKeepsQuality() = runBlocking {
        Endpoint { socket, _ -> reply(socket, "{\"error\":{\"message\":\"synthetic rejection\"}}", "400 Bad Request") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            f.settings.saveSettings(f.settings.getSettings().copy(auxiliaryMemoryEnabled = true, auxiliaryMemoryApiConfigId = config.id, auxiliaryMemoryModel = config.modelName))
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "请记住：始终使用中文简短回答"))
            val candidate = f.repository.processSavedMessageMemory(msg, config.id, config.modelName)!!
            assertTrue(candidate.distilledContent.contains("始终使用中文简短回答"))
            assertEquals(candidate.distilledContent, f.db.memoryDao().getBySourceMessage(msg)!!.content)
            assertEquals(1, endpoint.payloads.size)
        } }
    }

    @Test fun anthropicExtractionKeepsProtocolAndModelId() = runBlocking {
        Endpoint { socket, _ -> reply(socket, Gson().toJson(mapOf("content" to listOf(mapOf("type" to "text", "text" to "用户偏好：长期使用Kotlin开发Android应用"))))) }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "vendor/claude-sonnet-4-6:Exact-ID", "anthropic")
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "我长期使用Kotlin开发Android应用"))
            assertNotNull(f.repository.processSavedMessageMemory(msg, config.id, config.modelName))
            assertEquals(listOf("POST /v1/messages HTTP/1.1"), endpoint.paths)
            assertEquals(config.modelName, endpoint.payloads.single().get("model").asString)
            assertEquals(4096, endpoint.payloads.single().get("max_tokens").asInt)
            assertFalse(endpoint.payloads.single().has("max_completion_tokens"))
        } }
    }

    @Test fun cancellingRegisteredExtractionPropagatesWithoutFallbackOrMemoryWrite() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        Endpoint { _, _ -> entered.countDown(); released.await(10, TimeUnit.SECONDS) }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "请记住：始终使用中文简短回答"))
            val job = async(Dispatchers.IO) { f.repository.processSavedMessageMemory(msg, config.id, config.modelName) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                f.repository.cancelActiveRequest(id)
                try { withTimeout(3000) { job.await() }; fail("Cancellation must propagate") } catch (_: CancellationException) { }
                assertTrue(job.isCancelled)
                assertNull(f.db.memoryDao().getBySourceMessage(msg))
                assertEquals(1, endpoint.payloads.size)
            } finally { released.countDown(); job.cancelAndJoin() }
        } }
    }

    @Test fun deletingSourceWhileExtractionRunsCannotResurrectMemory() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Endpoint { socket, _ -> entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); memoryReply(socket, "用户偏好：长期使用Kotlin开发Android应用") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "我长期使用Kotlin开发Android应用"))
            val extraction = async(Dispatchers.IO) { f.repository.processSavedMessageMemory(msg, config.id, config.modelName) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                f.db.messageDao().deleteMessagesByConversation(id)
                release.countDown()
                assertNull(withTimeout(5000) { extraction.await() })
                assertTrue(f.db.memoryDao().getCandidateMemories(id).isEmpty())
            } finally { release.countDown(); extraction.cancelAndJoin() }
        } }
    }

    @Test fun mainWireOmitsGuessedExtensionsAndNativeThinkingRetainsBudget() = runBlocking {
        for (model in listOf("deepseek-v4-pro", "gpt-5.2")) {
            Endpoint { socket, _ -> streamReply(socket) }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url, model)
                chat(f, config, id)
                val body = endpoint.payloads.single()
                assertEquals(model, body.get("model").asString)
                assertFalse(body.has("thinking"))
                assertFalse(body.has("reasoning_effort"))
            } }
        }
        Endpoint { socket, _ -> reply(socket,
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\n", type = "text/event-stream") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "claude-sonnet-4-6", "anthropic")
            chat(f, config.copy(enableThinking = true), id)
            val body = endpoint.payloads.single()
            assertEquals("enabled", body.getAsJsonObject("thinking").get("type").asString)
            val budget = body.getAsJsonObject("thinking").get("budget_tokens").asInt
            assertTrue(budget >= 1024)
            assertTrue(body.get("max_tokens").asInt > budget)
            assertFalse(body.has("output_config"))
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun independentSessionMemoryStillStoresScopedDistillation() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "会话规则：始终使用中文简短回答") }.use { endpoint -> Fixture().use { f ->
            f.settings.saveSettings(f.settings.getSettings().copy(autoMemoryEnabled = false))
            val (config, id) = f.conversation(endpoint.url)
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "在这个会话中始终使用中文简短回答"))
            val candidate = f.repository.processSavedMessageMemory(msg, config.id, config.modelName)!!
            val memory = f.db.memoryDao().getBySourceMessage(msg)!!
            assertEquals("conversation", candidate.suggestedScope)
            assertEquals("conversation", memory.scope)
            assertEquals(id, memory.conversationId)
            assertEquals(candidate.distilledContent, memory.content)
        } }
    }

    @Test fun longForegroundReplyDoesNotExpireMemorySource() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "用户偏好：长期使用Kotlin开发Android应用") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "我长期使用Kotlin开发Android应用", createdAt = System.currentTimeMillis() - 15 * 60_000L))
            assertNotNull(f.repository.processSavedMessageMemory(msg, config.id, config.modelName))
            assertNotNull(f.db.memoryDao().getBySourceMessage(msg))
            assertEquals(1, endpoint.payloads.size)
        } }
    }

    @Test fun changingSwitchesWhileModelRunsPreventsSavingDisabledScope() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Endpoint { socket, _ -> entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); memoryReply(socket, "用户偏好：长期使用Kotlin开发Android应用") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "我长期使用Kotlin开发Android应用"))
            val extraction = async(Dispatchers.IO) { f.repository.processSavedMessageMemory(msg, config.id, config.modelName, true) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                f.settings.saveSettings(f.settings.getSettings().copy(autoMemoryEnabled = false))
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(enableSessionMemory = false))
                release.countDown()
                assertNull(withTimeout(5000) { extraction.await() })
                assertNull(f.db.memoryDao().getBySourceMessage(msg))
            } finally { release.countDown(); extraction.cancelAndJoin() }
        } }
    }

    @Test fun temporarySessionMemoryOverrideStillWorksWithoutChangingSavedSettings() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "会话规则：始终使用中文简短回答") }.use { endpoint -> Fixture().use { f ->
            f.settings.saveSettings(f.settings.getSettings().copy(autoMemoryEnabled = false))
            val (config, id) = f.conversation(endpoint.url, enabled = false)
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "在这个会话中始终使用中文简短回答"))
            assertNotNull(f.repository.processSavedMessageMemory(msg, config.id, config.modelName, true))
            assertEquals(id, f.db.memoryDao().getBySourceMessage(msg)!!.conversationId)
            assertEquals(false, f.db.conversationDao().getConversationById(id)!!.enableSessionMemory)
            assertEquals(1, endpoint.payloads.size)
        } }
    }

    @Test fun modelDistillationStillUpdatesConflictingFactInPlace() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "用户偏好：偏好使用Kotlin进行Android开发") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val oldId = f.db.memoryDao().insertMemory(MemoryItem(scope = "user", content = "主力语言是Java"))
            val msg = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "以后偏好使用Kotlin进行Android开发"))
            val candidate = f.repository.processSavedMessageMemory(msg, config.id, config.modelName)!!
            val updated = f.db.memoryDao().getBySourceMessage(msg)!!
            assertEquals(oldId, updated.id)
            assertEquals(candidate.distilledContent, updated.content)
            assertTrue(updated.content.contains("Kotlin"))
            assertFalse(updated.content.contains("Java"))
            assertEquals(1, f.db.memoryDao().getCandidateMemories(id).size)
        } }
    }
}
