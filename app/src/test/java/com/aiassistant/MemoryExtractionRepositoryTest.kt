package com.aiassistant

import android.app.Application
import androidx.room.Room
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.data.repository.RoleplayRepository
import com.aiassistant.data.repository.helpers.ContextCompressionPlanner
import com.aiassistant.domain.model.*
import com.aiassistant.utils.CryptoManager
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.TavilySearchManager
import com.aiassistant.utils.WorldBookImport
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineReconcileDraft
import com.aiassistant.utils.SessionSettingReconciler
import com.aiassistant.utils.RoleplaySmartAnalyzer
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
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
import java.util.concurrent.atomic.AtomicInteger

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
        val authorizations = java.util.concurrent.ConcurrentHashMap<Socket, String>()
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
                                        if (header.startsWith("Authorization:", true)) authorizations[socket] = header.substringAfter(':').trim()
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

    private fun completedSummaryReply(socket: Socket, text: String, protocol: String = "openai") {
        val response = if (protocol == "anthropic") {
            mapOf("stop_reason" to "end_turn", "content" to listOf(mapOf("type" to "text", "text" to text)))
        } else {
            mapOf("choices" to listOf(mapOf("finish_reason" to "stop", "message" to mapOf("content" to text))))
        }
        reply(socket, Gson().toJson(response))
    }

    /** Reconstruct only source slices, excluding repeated templates and rolling summaries. */
    private fun sourceFragmentsFromSummaryPrompt(prompt: String): List<Pair<Long, String>> {
        check(prompt.contains("新增对话历史：\n"))
        val transcript = prompt.substringAfter("新增对话历史：\n")
        val headers = Regex("(?m)^[ \\t]*\\[(?:用户|助手) · 原文ID (\\d+)] ").findAll(transcript).toList()
        return headers.mapIndexed { index, header ->
            val nextHeader = headers.getOrNull(index + 1)
            val content = transcript.substring(header.range.last + 1, nextHeader?.range?.first ?: transcript.length)
            header.groupValues[1].toLong() to if (nextHeader == null) content else content.removeSuffix("\n")
        }
    }

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
                crypto, settings, TavilySearchManager(context, crypto), worldBookDao = db.worldBookDao(),
                timelineNodeDao = db.timelineNodeDao())
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

    private fun isSummaryPayload(payload: JsonObject): Boolean =
        payload.getAsJsonArray("messages").singleOrNull()?.asJsonObject?.get("content")
            ?.takeIf { it.isJsonPrimitive }?.asString?.contains("新增对话历史：\n") == true

    private fun summaryWireViolations(payload: JsonObject, model: String, completionLimit: Boolean,
        expectedEffort: String = "low"): List<String> = buildList {
        if (payload.get("model")?.asString != model) add("wrong model")
        val requiredLimit = if (completionLimit) "max_completion_tokens" else "max_tokens"
        val forbiddenLimit = if (completionLimit) "max_tokens" else "max_completion_tokens"
        val limit = payload.get(requiredLimit)?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        if (payload.has(forbiddenLimit)) add("unexpected $forbiddenLimit")
        if (limit !in 8192..(32768 / 3)) add("insufficient or excessive reasoning completion budget")
        if (payload.get("reasoning_effort")?.asString != expectedEffort) add("summary must use $expectedEffort reasoning effort")
        if (payload.has("thinking_budget")) add("unsupported fixed thinking budget")
        if (payload.has("temperature")) add("unexpected summary sampling override")
    }

    @Test fun summaryProviderWireMatrixRequiresCorrectLimitsAndEffortBeforeSavingAndUsingRemoteText() = runBlocking {
        val metadataModel = "synthetic-metadata-summary-model"
        val models = listOf(
            Triple("glm-5.3", false, "low"), Triple("minimax-m3.1", false, "low"), Triple("gpt-5.2", true, "low"),
            Triple("glm-5.2", false, "high"), Triple(metadataModel, false, "medium")
        )
        for ((model, completionLimit, expectedEffort) in models) for (streamOnly in listOf(false, true)) {
            val summary = "合成远端参数校验标识[$model/$streamOnly]：北港领航员已经核对蓝印凭证，铜钥匙仍在资料柜，下一步等待航线确认。"
            val violations = CopyOnWriteArrayList<String>()
            val summaryRequests = AtomicInteger()
            val chatRequests = AtomicInteger()
            Endpoint { socket, payload ->
                if (isSummaryPayload(payload)) {
                    summaryRequests.incrementAndGet()
                    val errors = summaryWireViolations(payload, model, completionLimit, expectedEffort)
                    when {
                        errors.isNotEmpty() -> {
                            violations.addAll(errors)
                            reply(socket, "{\"error\":{\"message\":\"synthetic summary parameter rejection\"}}", "500 Internal Server Error")
                        }
                        streamOnly && payload.get("stream")?.asBoolean != true ->
                            reply(socket, "{\"error\":{\"message\":\"synthetic endpoint requires streaming\"}}", "405 Method Not Allowed")
                        payload.get("stream")?.asBoolean == true -> reply(socket,
                            "data: {\"choices\":[{\"delta\":{\"content\":${Gson().toJson(summary)}},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                            type = "text/event-stream")
                        else -> completedSummaryReply(socket, summary)
                    }
                } else {
                    chatRequests.incrementAndGet()
                    val system = payload.getAsJsonArray("messages").firstOrNull()?.asJsonObject
                        ?.takeIf { it.get("role")?.asString == "system" }?.get("content")?.asString.orEmpty()
                    if (payload.get("stream")?.asBoolean == true && system.contains(summary)) streamReply(socket)
                    else reply(socket, "{\"error\":{\"message\":\"synthetic next chat is missing the accepted remote summary\"}}", "500 Internal Server Error")
                }
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                f.settings.setBackupKeyFallbackEnabled(false)
                val (config, id) = f.conversation(endpoint.url, model, enabled = false)
                val previousEvidence = ReasoningCapabilityCatalog.get(endpoint.url, "openai", model)
                try {
                if (model == metadataModel) {
                    val evidence = ReasoningCapabilityEvidence(supportsThinking = true,
                        efforts = listOf("medium", "high"), defaultEffort = "high")
                    ReasoningCapabilityCatalog.put(endpoint.url, "openai", model, evidence)
                    assertEquals(evidence, ReasoningCapabilityCatalog.get(endpoint.url, "openai", model))
                }
                val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成参数矩阵原文[$index]：核对蓝印凭证。", createdAt = index.toLong()) })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level))
                val originals = f.db.messageDao().getMessagesList(id)
                val result = withTimeout(10_000) { f.repository.compressConversationContext(id) }
                assertTrue("$model/$streamOnly: ${violations.joinToString()} ${result.exceptionOrNull()?.message}", result.isSuccess)
                assertTrue("服务端必须按实际 JSON 校验摘要参数", violations.isEmpty())
                assertEquals(if (streamOnly) 2 else 1, summaryRequests.get())
                val summaryPayloads = endpoint.payloads.filter(::isSummaryPayload)
                assertFalse(summaryPayloads.first().get("stream").asBoolean)
                if (streamOnly) {
                    assertTrue(summaryPayloads.last().get("stream").asBoolean)
                    assertEquals("协议兼容重试不能更换原文", summaryPayloads.first().get("messages"), summaryPayloads.last().get("messages"))
                    val limitField = if (completionLimit) "max_completion_tokens" else "max_tokens"
                    assertEquals("两条通道必须保留相同输出预算", summaryPayloads.first().get(limitField), summaryPayloads.last().get(limitField))
                }
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals("必须保存模型返回的唯一标识，不能由本地摘要冒充成功", summary, saved.rollingSummary)
                assertEquals(ids[21], saved.summaryUpdatedMessageId)
                assertEquals(originals, f.db.messageDao().getMessagesList(id))
                chat(f, config, id)
                assertEquals("下一次正式聊天必须接收已保存的远端摘要", 1, chatRequests.get())
                assertEquals("正式聊天不应再次请求相同范围的摘要", if (streamOnly) 2 else 1, summaryRequests.get())
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
                } finally {
                    if (model == metadataModel) ReasoningCapabilityCatalog.put(endpoint.url, "openai", model, previousEvidence)
                }
            } }
        }
    }

    @Test fun structuredNonStreamingSummaryTextIsSavedExactlyAndInjectedWithoutEmbeddedThinking() = runBlocking {
        val summary = "【合成远端结构化摘要】\n北港领航员仍保管蓝印凭证，铜钥匙没有转交他人，下一步需要确认合成航线。"
        val hidden = "这段合成内部思考不得出现在摘要或下次聊天中"
        val violations = CopyOnWriteArrayList<String>()
        Endpoint { socket, payload ->
            if (isSummaryPayload(payload)) {
                val errors = summaryWireViolations(payload, "gpt-5.2", completionLimit = true)
                if (payload.get("stream")?.asBoolean == true) violations += "structured summary must use non-streaming response"
                violations.addAll(errors)
                if (violations.isNotEmpty()) reply(socket,
                    "{\"error\":{\"message\":\"synthetic structured summary parameter rejection\"}}", "500 Internal Server Error")
                else reply(socket, Gson().toJson(mapOf("choices" to listOf(mapOf("finish_reason" to "stop", "message" to mapOf(
                    "content" to listOf(
                        mapOf("type" to "thinking", "thinking" to hidden),
                        mapOf("type" to "text", "text" to "【合成远端结构化摘要】\n"),
                        mapOf("type" to "output_text", "text" to mapOf("value" to summary.substringAfter('\n')))
                    )))))))
            } else {
                val system = payload.getAsJsonArray("messages").first().asJsonObject.get("content").asString
                if (system.contains(summary) && !system.contains(hidden)) streamReply(socket)
                else reply(socket, "{\"error\":{\"message\":\"synthetic structured summary injection mismatch\"}}", "500 Internal Server Error")
            }
        }.use { endpoint -> Fixture().use { f ->
            f.settings.setModelRetryEnabled(false)
            f.settings.setBackupKeyFallbackEnabled(false)
            val (config, id) = f.conversation(endpoint.url, "gpt-5.2", enabled = false)
            val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成结构化摘要原文[$index]：凭证仍在北港。", createdAt = index.toLong()) })
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level))
            val result = withTimeout(10_000) { f.repository.compressConversationContext(id) }
            assertTrue("${violations.joinToString()} ${result.exceptionOrNull()?.message}", result.isSuccess)
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals(summary, saved.rollingSummary)
            assertEquals(ids[21], saved.summaryUpdatedMessageId)
            assertFalse(saved.rollingSummary!!.contains(hidden))
            assertEquals(1, endpoint.payloads.size)
            chat(f, config, id)
            assertEquals("一次非流式摘要和一次正式聊天", 2, endpoint.payloads.size)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    /** Match the UI: persist the current user first, then let the repository save its reply. */
    private suspend fun chatWithSavedUser(f: Fixture, config: ApiConfig, id: Long, content: String): String? {
        f.db.messageDao().insertMessage(Message(conversationId = id, role = "user", content = content))
        val tokens = StringBuilder()
        var completed: String? = null
        var error: String? = null
        f.repository.sendChatMessageWithConfig(config, id, content,
            options = ChatRequestOptions(maxTokens = 512, enableSessionMemory = false, enableExternalMemory = false),
            onToken = { tokens.append(it) }, onComplete = { text, _, _ -> completed = text }, onError = { error = it })
        if (error == null) {
            assertEquals("OK", completed)
            assertEquals("OK", tokens.toString())
        } else {
            assertNull("失败发送不能同时完成回复", completed)
            assertEquals("", tokens.toString())
        }
        return error
    }

    private fun roleplayRepository(f: Fixture) = RoleplayRepository(
        f.db.characterProfileDao(), f.db.roleplayScenarioDao(), f.db.roleplaySessionDao(),
        f.db.roleplayMemoryDao(), f.db.characterTagDao(), f.db.conversationDao(),
        f.db.messageDao(), f.db.worldBookDao(), f.db.memoryDao()
    )

    @Test fun creativeAnalysisUsesBothProtocolsAndSendsCompleteLongText() = runBlocking {
        for ((protocol, model) in listOf("openai" to "vendor/gpt-5.2:Exact-ID", "anthropic" to "synthetic-anthropic-model")) {
            Endpoint { socket, _ -> completedSummaryReply(socket, """{"characters":[{"name":"合成领航员"}]}""", protocol) }
                .use { endpoint -> Fixture().use { f ->
                    val (config, _) = f.conversation(endpoint.url, model, protocol)
                    val text = "创作资料：" + "合成正文".repeat(6000) + "末尾必须保留的合成设定"
                    val result = f.repository.analyzeCreativeText(config, text, model)
                    assertTrue(result.contains("合成领航员"))
                    val request = endpoint.payloads.single()
                    assertEquals(text, request.getAsJsonArray("messages").single().asJsonObject.get("content").asString)
                    assertEquals(model, request.get("model").asString)
                    assertNull(request.get("temperature"))
                    if (protocol == "openai") {
                        assertTrue(endpoint.paths.single().contains("chat/completions"))
                        assertTrue(request.has("max_completion_tokens"))
                        assertFalse(request.has("max_tokens"))
                    } else {
                        assertTrue(endpoint.paths.single().contains("/messages"))
                        assertTrue(request.has("max_tokens"))
                    }
                    val stat = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).single()
                    assertEquals("story_analysis", stat.requestKind)
                    assertTrue(stat.success)
                    assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
                } }
        }
    }

    @Test fun creativeAnalyzerIntegrationForwardsCompleteInputAndDisabledConfigDoesNotRequest() = runBlocking {
        Endpoint { socket, _ -> completedSummaryReply(socket, """{"characters":[{"name":"合成领航员","identity":"合成船长"}],"scenario":null}""") }
            .use { endpoint -> Fixture().use { f ->
                val (config, _) = f.conversation(endpoint.url, "synthetic-creative-model")
                // A no-op progress callback still switches dispatchers inside the analyzer.
                val mainExecutor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
                Dispatchers.setMain(mainExecutor)
                try {
                    val text = "合成故事开头" + "海上合成剧情".repeat(4000) + "结尾合成设定：角色对花粉过敏"
                    val bundle = withTimeout(10000) { RoleplaySmartAnalyzer.analyzeTextOrNovel(f.context, text,
                        f.repository, config, config.modelName) }
                    assertTrue(bundle.isAiAnalyzed)
                    assertEquals("合成领航员", bundle.characters.single().name)
                    val prompt = endpoint.payloads.single().getAsJsonArray("messages").single().asJsonObject.get("content").asString
                    assertTrue(prompt.contains("合成故事开头"))
                    assertTrue(prompt.contains("结尾合成设定：角色对花粉过敏"))
                    val count = endpoint.payloads.size
                    val disabled = withTimeout(10000) { RoleplaySmartAnalyzer.analyzeTextOrNovel(f.context,
                        "角色名称：合成船员", f.repository, config.copy(isEnabled = false), config.modelName) }
                    assertFalse(disabled.isAiAnalyzed)
                    assertEquals(count, endpoint.payloads.size)
                } finally { Dispatchers.resetMain(); mainExecutor.close() }
            } }
    }

    @Test fun creativeAnalysisRejectsIncompleteResponsesEvenWhenJsonCanBeParsed() = runBlocking {
        for ((protocol, stop) in listOf("openai" to "length", "anthropic" to "max_tokens")) {
            Endpoint { socket, _ ->
                val text = """{"characters":[{"name":"只有前半部分的合成角色"}]}"""
                val body = if (protocol == "anthropic") mapOf("stop_reason" to stop, "content" to listOf(mapOf("type" to "text", "text" to text)))
                    else mapOf("choices" to listOf(mapOf("finish_reason" to stop, "message" to mapOf("content" to text))))
                reply(socket, Gson().toJson(body))
            }.use { endpoint -> Fixture().use { f ->
                val (config, _) = f.conversation(endpoint.url, "synthetic-creative-model", protocol)
                val result = runCatching { f.repository.analyzeCreativeText(config, "请提取合成人物设定") }
                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("未完整结束"))
                assertEquals(1, endpoint.payloads.size)
                val stat = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).single()
                assertEquals("story_analysis", stat.requestKind)
                assertEquals("interrupted", stat.completionStatus)
                assertFalse(stat.success)
            } }
        }
    }

    @Test fun creativeAnalysisRejectsOversizeAndDisabledInputsBeforeHttp() = runBlocking {
        Endpoint { socket, _ -> completedSummaryReply(socket, "{}") }.use { endpoint -> Fixture().use { f ->
            val (config, _) = f.conversation(endpoint.url, "synthetic-small-window-model")
            f.db.selectedModelDao().insertModel(SelectedModel(apiConfigId = config.id, modelName = config.modelName, contextWindowTokens = 4000))
            assertTrue(runCatching { f.repository.analyzeCreativeText(config, "合成超长正文".repeat(6000)) }.isFailure)
            val selected = f.db.selectedModelDao().getModelByName(config.id, config.modelName)!!
            f.db.selectedModelDao().updateModel(selected.copy(isEnabled = false))
            assertTrue(runCatching { f.repository.analyzeCreativeText(config, "合成正文") }.isFailure)
            f.db.apiConfigDao().updateConfig(config.copy(isEnabled = false))
            assertTrue(runCatching { f.repository.analyzeCreativeText(config, "合成正文") }.isFailure)
            assertEquals(0, endpoint.payloads.size)
        } }
    }

    @Test fun creativeAnalysisCancellationStopsWaitingForHttp() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        Endpoint { socket, _ ->
            entered.countDown()
            check(released.await(10, TimeUnit.SECONDS))
            completedSummaryReply(socket, "{}")
        }.use { endpoint -> Fixture().use { f ->
            val (config, _) = f.conversation(endpoint.url, "synthetic-creative-model")
            val job = async(Dispatchers.IO) { f.repository.analyzeCreativeText(config, "合成小说解析") }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                withTimeout(3000) { job.cancelAndJoin() }
                assertTrue(job.isCancelled)
                assertEquals(1, endpoint.payloads.size)
            } finally { released.countDown(); job.cancelAndJoin() }
        } }
    }

    @Test fun explicitGlobalSettingChoiceIsRespectedInCreativeSession() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation("https://model.invalid", tags = "story")
            f.db.roleplaySessionDao().insertSession(RoleplaySession(conversationId = id))
            f.repository.applyConfirmedSessionSettings(id, listOf(AtemporalSettingItem(category = "写作规则",
                content = "合成作品始终采用第三人称", targetScope = "global")))
            assertEquals(1, f.db.memoryDao().getConversationMemories(id).size)
            assertEquals(1, f.db.memoryDao().getGlobalMemoriesFlow().first().size)
        }
    }

    @Test fun semanticSettingNoopIsAuthoritativeAndExistingIdsAreSentToModel() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, """{"action":"NOOP"}""") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val oldId = f.repository.addConversationMemory(id, "【习惯偏好】合成角色只在夜晚练琴")
            val messageId = f.repository.saveMessage(Message(conversationId = id, role = "user",
                content = "请记住当前会话设定：合成角色习惯趁夜色弹琴"))
            assertNull(f.repository.processSavedMessageMemory(messageId, config.id, config.modelName))
            val memories = f.db.memoryDao().getConversationMemories(id)
            assertEquals(listOf(oldId), memories.map { it.id })
            val prompt = endpoint.payloads.single().getAsJsonArray("messages").joinToString { it.asJsonObject.get("content").asString }
            assertTrue(prompt.contains("\"id\":$oldId"))
            assertTrue(prompt.contains("同义改写"))
            assertTrue(f.db.memoryDao().getCandidateMemories(id).all { it.scope == "conversation" })
        } }
    }

    @Test fun singleMessageSettingSupplementIsStableAndRevisionWaitsForConfirmation() = runBlocking {
        val targetId = java.util.concurrent.atomic.AtomicLong()
        val requests = AtomicInteger()
        Endpoint { socket, _ ->
            val response = if (requests.getAndIncrement() == 0)
                """{"action":"SUPPLEMENT","targetMemoryId":${targetId.get()},"content":"只演奏自己作的曲子","evidence":"她只弹自己写的曲子"}"""
            else """{"action":"REVISE","targetMemoryId":${targetId.get()},"content":"合成角色因旧伤已不再练琴","evidence":"旧伤让她停止练琴","changeReason":"正文明确纠正当前习惯"}"""
            memoryReply(socket, response)
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            targetId.set(f.repository.addConversationMemory(id, "【习惯偏好】合成角色在夜间练琴；练琴时关闭窗户"))
            val firstMessage = f.repository.saveMessage(Message(conversationId = id, role = "user",
                content = "请记住当前会话设定：她只弹自己写的曲子"))
            val supplement = f.repository.processSavedMessageMemory(firstMessage, config.id, config.modelName)!!
            assertEquals("SUPPLEMENT", supplement.action)
            assertEquals(targetId.get(), supplement.targetMemoryId)
            val supplemented = f.db.memoryDao().getConversationMemories(id).single()
            assertEquals(targetId.get(), supplemented.id)
            assertTrue(supplemented.content.contains("关闭窗户"))
            assertTrue(supplemented.content.contains("自己作的曲子"))
            val secondMessage = f.repository.saveMessage(Message(conversationId = id, role = "user",
                content = "修订当前会话设定：旧伤让她停止练琴"))
            val revision = f.repository.processSavedMessageMemory(secondMessage, config.id, config.modelName)!!
            assertEquals("REVISE", revision.action)
            assertEquals(supplemented.content, f.db.memoryDao().getMemoryById(targetId.get())!!.content)
            f.repository.saveConfirmedMemory(revision.distilledContent, "conversation", id, revision.sourceMessageId,
                revision.targetMemoryId, revision.previousContent)
            val saved = f.db.memoryDao().getConversationMemories(id).single()
            assertEquals(targetId.get(), saved.id)
            assertEquals("合成角色因旧伤已不再练琴", saved.content)
        } }
    }

    @Test fun reviewedSupplementsUpdateStableRowsAndCreativeMirrorsWithoutLosingFacts() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation("https://model.invalid", tags = "story")
            val sessionId = f.db.roleplaySessionDao().insertSession(RoleplaySession(conversationId = id))
            val original = "【习惯偏好】合成角色在夜间练琴；练琴时关闭窗户"
            val oldId = f.repository.addConversationMemory(id, original)
            val mirrorId = f.db.roleplayMemoryDao().insertMemory(RoleplayMemory(sessionId = sessionId,
                memoryType = "fact", content = original, isPinned = true))
            val proposals = SessionSettingReconciler.reconcileCandidates(listOf(AtemporalSettingItem(
                category = "习惯偏好", content = "只演奏自己作的曲子", action = "SUPPLEMENT", targetMemoryId = oldId)),
                f.db.memoryDao().getConversationMemories(id))
            f.repository.applyConfirmedSessionSettings(id, proposals)
            f.repository.applyConfirmedSessionSettings(id, proposals)
            val saved = f.db.memoryDao().getConversationMemories(id).single()
            assertEquals(oldId, saved.id)
            assertEquals("【习惯偏好】合成角色在夜间练琴；练琴时关闭窗户；只演奏自己作的曲子", saved.content)
            val mirror = f.db.roleplayMemoryDao().getMemoriesListBySession(sessionId).single()
            assertEquals(mirrorId, mirror.id)
            assertEquals(saved.content, mirror.content)
            assertTrue(f.db.memoryDao().getCandidateMemories(0).isEmpty())
        }
    }

    @Test fun staleOrCrossConversationSettingEditsFailWithoutPartialWrites() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation("https://model.invalid")
            val (_, otherId) = f.conversation("https://model.invalid")
            val oldId = f.repository.addConversationMemory(id, "【世界规则】合成城禁止火焰魔法")
            val original = f.db.memoryDao().getMemoryById(oldId)!!
            val revision = AtemporalSettingItem(category = "世界规则", content = "合成城允许受控火焰魔法", action = "REVISE",
                targetMemoryId = oldId, previousContent = original.content)
            assertTrue(runCatching { f.repository.applyConfirmedSessionSettings(otherId, listOf(revision)) }.isFailure)
            f.db.memoryDao().updateMemory(original.copy(content = "人工修改：合成城仍禁止一切火焰魔法"))
            val newSetting = AtemporalSettingItem(content = "合成城每月举行灯会")
            assertTrue(runCatching { f.repository.applyConfirmedSessionSettings(id, listOf(newSetting, revision)) }.isFailure)
            val saved = f.db.memoryDao().getConversationMemories(id).single()
            assertEquals("人工修改：合成城仍禁止一切火焰魔法", saved.content)
            assertTrue(f.db.memoryDao().getConversationMemories(otherId).isEmpty())
        }
    }

    @Test fun renameBranchOnlyChangesTargetTitleAndRejectsBlankOrDeletedTargets() = runBlocking {
        Fixture().use { f ->
            val (config, parentId) = f.conversation("https://model.invalid")
            val childId = f.db.conversationDao().insertConversation(Conversation(title = "对话（分支2）",
                apiConfigId = config.id, modelName = config.modelName, currentStoryTime = "第六天"))
            val messageId = f.repository.saveMessage(Message(conversationId = parentId, role = "assistant", content = "合成分支起点"))
            f.db.conversationBranchDao().insertBranch(ConversationBranch(parentConversationId = parentId,
                childConversationId = childId, branchMessageId = messageId))
            // Simulate another feature writing its current state after the rename dialog was opened.
            val liveChild = f.db.conversationDao().getConversationById(childId)!!
                .copy(currentStoryTime = "两周后", rollingSummary = "合成角色已抵达港口", messageCount = 23, isPinned = true)
            f.db.conversationDao().updateConversation(liveChild)
            f.repository.updateConversationTitle(childId, "  海港另一条路  ")
            val saved = f.db.conversationDao().getConversationById(childId)!!
            assertEquals("海港另一条路", saved.title)
            assertEquals("两周后", saved.currentStoryTime)
            assertEquals("合成角色已抵达港口", saved.rollingSummary)
            assertEquals(23, saved.messageCount)
            assertTrue(saved.isPinned)
            assertEquals("memory regression", f.db.conversationDao().getConversationById(parentId)!!.title)
            val link = f.db.conversationBranchDao().getBranchesByParent(parentId).first().single()
            assertEquals(childId, link.childConversationId)
            assertEquals(messageId, link.branchMessageId)
            assertTrue(runCatching { f.repository.updateConversationTitle(childId, "  ") }.isFailure)
            f.db.conversationDao().deleteConversationById(childId)
            assertTrue(runCatching { f.repository.updateConversationTitle(childId, "已删目标") }.isFailure)
        }
    }

    @Test fun storyContextKeepsUserRulesAndCharacterFactsWithoutRepeatedStyleMandates() = runBlocking {
        Fixture().use { f ->
            val (_, conversationId) = f.conversation("https://model.invalid", tags = "story")
            val conversation = f.db.conversationDao().getConversationById(conversationId)!!
            f.db.conversationDao().updateConversation(conversation.copy(currentStoryTime = "第九日·傍晚"))
            val characters = listOf(
                CharacterProfile(name = "合成领航员", background = "在雾海学习辨认星图", speakingStyle = "说话简短", constraints = "不透露航线密码"),
                CharacterProfile(name = "合成修船师", background = "只修理木制船只", speakingStyle = "喜欢用航海比喻")
            )
            val sessionId = f.db.roleplaySessionDao().insertSession(RoleplaySession(
                conversationId = conversationId, customCharacterData = Gson().toJson(characters),
                currentPlotSummary = "众人正在筹备修船"
            ))
            val globalRule = "全局自定义规则：允许直接描述情绪，采用朴素的剧本对白。"
            val storyRule = "本故事规则：以修船师视角展开本幕。"
            val context = roleplayRepository(f).assembleRoleplayContext(
                sessionId, storyRule, "继续", globalRoleplayPrompt = globalRule, includeHistory = false
            )
            assertEquals(1, Regex(Regex.escape(globalRule)).findAll(context).count())
            assertEquals(1, Regex(Regex.escape(storyRule)).findAll(context).count())
            for (character in characters) {
                assertTrue(context.contains(character.name))
                assertTrue(context.contains(character.background))
                assertTrue(context.contains(character.speakingStyle))
            }
            assertTrue(context.contains(characters.first().constraints))
            assertFalse(context.contains("文学创作与角色演绎铁律"))
            assertFalse(context.contains("严禁在动作描写中直接使用生硬性格副词"))
            assertFalse(context.contains("动作描写与台词对话分段交替"))
            assertTrue(f.db.roleplayMemoryDao().getPinnedFacts(sessionId).isEmpty())
            assertTrue(f.db.memoryDao().getConversationMemories(conversationId).isEmpty())
            assertTrue("没有记忆记录时也必须携带会话当前时间", context.contains("第九日·傍晚"))
            assertFalse(context.contains("【故事当前时间节点】\n众人正在筹备修船"))
        }
    }

    @Test fun storyWorldBookUsesRecentMessagesAndExplicitScopeWithoutTeachingPromptTriggers() = runBlocking {
        Fixture().use { f ->
            val (_, conversationId) = f.conversation("https://model.invalid", tags = "story")
            val bookId = f.db.worldBookDao().insertBook(WorldBook(name = "合成群岛资料"))
            val harborLore = "北港通行证必须盖三枚蓝印。"
            val teachingOnlyLore = "琥珀王国的王冠藏在枯井。"
            f.db.worldBookDao().insertEntry(WorldBookEntry(
                bookId = bookId, name = "北港规则", keys = "[\"North Harbor\"]", content = harborLore
            ))
            f.db.worldBookDao().insertEntry(WorldBookEntry(
                bookId = bookId, name = "教学误触发哨兵", keys = "琥珀王国", content = teachingOnlyLore
            ))
            f.db.messageDao().insertMessage(Message(
                conversationId = conversationId, role = "assistant", content = "他们刚刚驶入 North Harbor。"
            ))
            f.db.messageDao().insertMessage(Message(
                conversationId = conversationId, role = "assistant", content = "琥珀王国的废弃分支。",
                variantGroupId = "synthetic-branch", variantIndex = 0
            ))
            f.db.messageDao().insertMessage(Message(
                conversationId = conversationId, role = "assistant", content = "船员在码头等候。",
                variantGroupId = "synthetic-branch", variantIndex = 1
            ))
            f.db.messageDao().insertMessage(Message(
                conversationId = conversationId, role = "user", content = "琥珀王国的已排除备注。",
                isExcluded = true
            ))
            val sessionId = f.db.roleplaySessionDao().insertSession(RoleplaySession(
                conversationId = conversationId, enableWorldBook = true, activeWorldBookIds = bookId.toString()
            ))
            val repo = roleplayRepository(f)
            val teaching = "用琥珀王国作修辞示例，保持叙事简洁。"
            val context = repo.assembleRoleplayContext(sessionId, null, "继续向前", globalRoleplayPrompt = teaching)
            assertTrue("最近消息应触发当前书目", context.contains(harborLore))
            assertTrue(context.contains(teaching))
            assertFalse("全局教学指引、旧回复变体和排除消息不应触发背景设定", context.contains(teachingOnlyLore))
            val withoutWorldBook = repo.assembleRoleplayContext(
                sessionId, null, "North Harbor", globalRoleplayPrompt = teaching, includeWorldBook = false
            )
            assertFalse(withoutWorldBook.contains(harborLore))
            assertFalse(withoutWorldBook.contains(teachingOnlyLore))
            val withoutHistory = repo.assembleRoleplayContext(
                sessionId, null, "继续向前", globalRoleplayPrompt = teaching, includeHistory = false
            )
            assertFalse(withoutHistory.contains(harborLore))

            val session = f.db.roleplaySessionDao().getSessionById(sessionId)!!
            f.db.roleplaySessionDao().updateSession(session.copy(activeWorldBookIds = ""))
            val emptySelection = repo.assembleRoleplayContext(sessionId, null, "North Harbor")
            assertFalse("显式取消全部书目不应回退到所有全局书", emptySelection.contains(harborLore))
            f.db.roleplaySessionDao().updateSession(session.copy(activeWorldBookIds = null))
            val legacyGlobalSelection = repo.assembleRoleplayContext(sessionId, null, "North Harbor")
            assertTrue("未指定书目保留既有全局启用书语义", legacyGlobalSelection.contains(harborLore))
        }
    }

    @Test fun worldBookRecentWindowUsesOriginalHistoryBeforeCompressionForBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, _ ->
                if (protocol == "openai") streamReply(socket) else reply(socket,
                    "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\n" +
                        "data: {\"type\":\"message_stop\"}\n\n", type = "text/event-stream")
            }.use { endpoint -> Fixture().use { f ->
                val model = if (protocol == "openai") "deepseek-chat" else "claude-sonnet-4-6"
                val (config, id) = f.conversation(endpoint.url, model, protocol, enabled = false)
                val conversation = f.db.conversationDao().getConversationById(id)!!
                f.db.conversationDao().updateConversation(conversation.copy(
                    compressionTier = CompressionTier.LC.level, compressionCustomPercent = 10,
                    contextWindowTokens = 128000, rollingSummary = "合成历史摘要。", summaryUpdatedMessageId = 18L
                ))
                for (index in 0 until 20) {
                    f.db.messageDao().insertMessage(Message(
                        conversationId = id, role = if (index % 2 == 0) "user" else "assistant",
                        content = when (index) {
                            0 -> "旧日航程经过远古石堡。"
                            16 -> "现在已抵达青色港湾。"
                            else -> "合成航海记录 $index。"
                        }, isPinned = index == 0, createdAt = index.toLong()
                    ))
                }
                val bookId = f.db.worldBookDao().insertBook(WorldBook(name = "合成压缩回归资料"))
                val oldLore = "旧城守卫只接受铜制凭证。"
                val recentLore = "港口的灯塔每夜鸣笛三次。"
                f.db.worldBookDao().insertEntry(WorldBookEntry(
                    bookId = bookId, name = "旧背景", keys = "远古石堡", content = oldLore
                ))
                f.db.worldBookDao().insertEntry(WorldBookEntry(
                    bookId = bookId, name = "近期背景", keys = "青色港湾", content = recentLore
                ))
                val options = ChatRequestOptions(enableWorldBook = true,
                    activeWorldBookIds = bookId.toString(), enableSessionMemory = false,
                    enableExternalMemory = false, enableWebSearch = false)
                val prepared = f.repository.prepareReplyDirectionContext(config, id, "继续", options)
                assertTrue("压缩应保留旧置顶消息", prepared.recentMessages.any { it.content.contains("远古石堡") })
                assertFalse("回归前提：近期触发词已被压缩移出原文", prepared.recentMessages.any { it.content.contains("青色港湾") })
                assertTrue("方向准备应扫描真实最近四条消息", prepared.systemPrompt.orEmpty().contains(recentLore))
                assertFalse("旧置顶消息不能冒充近期消息", prepared.systemPrompt.orEmpty().contains(oldLore))

                val tokens = StringBuilder()
                f.repository.sendChatMessageWithConfig(config, id, "继续", options = options,
                    onToken = { tokens.append(it) }, onComplete = { text, _, _ -> assertEquals("OK", text) },
                    onError = { fail(it) })
                assertEquals("OK", tokens.toString())
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
                val payload = endpoint.payloads.single()
                val system = if (protocol == "anthropic") payload.get("system").toString() else
                    payload.getAsJsonArray("messages").filter { it.asJsonObject.get("role").asString == "system" }
                        .joinToString("\n") { it.asJsonObject.get("content").toString() }
                assertTrue("$protocol 请求应包含压缩前近期消息触发的设定", system.contains(recentLore))
                assertFalse("$protocol 请求不得由旧置顶消息触发设定", system.contains(oldLore))
            } }
        }
    }

    @Test fun repositoryWorldBookBudgetSkipsOversizedEntriesAndMatchesImportedPhrases() = runBlocking {
        Fixture().use { f ->
            val report = WorldBookImport.parse("""
                {"name":"合成短语书","entries":[{"name":"北港","keys":["North Harbor"],"content":"港口有钟塔。","priority":10}]}
            """.trimIndent())
            val bookId = f.db.worldBookDao().insertImportedBook(WorldBookImport.prepare(report))
            f.db.worldBookDao().setBookEnabled(bookId, true)
            val shortEntry = f.db.worldBookDao().getEntriesForBookList(bookId).single()
            assertTrue("旧手填方括号关键词仍按字面匹配", shortEntry.copy(keys = "[港口]，钟塔").matchesText("前往[港口]"))
            assertFalse("方括号不应被宽松 JSON 解析器吞掉", shortEntry.copy(keys = "[港口]").matchesText("前往港口"))
            f.db.worldBookDao().insertEntry(WorldBookEntry(
                bookId = bookId, name = "超预算常驻条目", keys = "", content = "冗长的合成背景资料。".repeat(1000),
                isConstant = true, priority = 1000
            ))
            val selected = f.repository.matchWorldBookEntries("抵达 NORTH HARBOR", listOf(bookId), maxTokenBudget = 64)
            assertEquals("大条目跳过后仍需尝试后续小条目", listOf(shortEntry.id), selected.map { it.id })
            assertTrue(f.repository.matchWorldBookEntries("只有 North 一词", listOf(bookId), maxTokenBudget = 64).isEmpty())
            assertTrue(f.repository.matchWorldBookEntries("North Harbor", emptyList(), maxTokenBudget = 64).isEmpty())
            assertTrue(f.repository.matchWorldBookEntries("North Harbor", listOf(bookId), maxTokenBudget = 0).isEmpty())
            f.db.worldBookDao().setBookEnabled(bookId, false)
            assertTrue(f.repository.matchWorldBookEntries("North Harbor", listOf(bookId), maxTokenBudget = 64).isEmpty())
        }
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

    @Test fun automaticTimelineSkipsRoutineOnlyEventsAndAlreadyRecordedFacts() = runBlocking {
        for (content in listOf("两人一起吃饭并闲聊。气氛轻松。", "角色甲交付两箱药品。")) {
            Endpoint { socket, _ -> memoryReply(socket, Gson().toJson(mapOf("newStoryTime" to null,
                "newEvent" to mapOf("timeTag" to "第1天·上午", "content" to content),
                "atemporalSettings" to emptyList<String>()))) }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url)
                f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                    timeTag = "第1天·上午", event = "角色甲交付两箱药品"))
                assertNull(f.repository.evaluateAndAutoUpdateTimeline(id, "继续交谈", "两人边吃饭边闲聊", config.id, config.modelName))
                assertEquals("角色甲交付两箱药品", f.repository.getTimelineNodes(id).single().event)
                assertEquals(1, endpoint.payloads.size)
            } }
        }
    }

    @Test fun automaticTimelineSemanticallyCondensesVerboseEventsInBothProtocols() = runBlocking {
        val verbose = "角色甲在一片昏暗的灯光中缓慢地走进古塔，认真观察了门前潮湿的地面以及斑驳的墙壁，" +
            "随后向守门人详细说明通行证的来历，经过漫长的核对后因证件过期被拒绝入塔，" +
            "角色乙在一旁反复安慰同伴并承诺补办通行证，但铜钥匙的去向直到最后仍然没有查明。"
        val concise = "角色甲因通行证过期被拒绝入塔；角色乙承诺补办，铜钥匙去向仍未查明"
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, body ->
                val prompt = body.getAsJsonArray("messages").single().asJsonObject.get("content").asString
                val text = if (prompt.contains("【待精简事件】")) Gson().toJson(mapOf("content" to concise))
                    else Gson().toJson(mapOf("newStoryTime" to null,
                        "newEvent" to mapOf("timeTag" to "古塔门前", "content" to verbose), "atemporalSettings" to emptyList<String>()))
                completedSummaryReply(socket, text, protocol)
            }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url, "synthetic-timeline-model", protocol)
                val proposal = f.repository.evaluateAndAutoUpdateTimeline(id, "继续合成故事", verbose, config.id, config.modelName)!!
                assertNull(proposal.extractionErrorMessage)
                assertEquals(concise, proposal.newEvent!!.content)
                assertTrue(proposal.newEvent!!.content.length <= com.aiassistant.utils.TimelineMemoryHelper.MAX_AUTO_EVENT_CHARS)
                assertEquals(2, endpoint.payloads.size)
                val rewrite = endpoint.payloads.last().getAsJsonArray("messages").single().asJsonObject.get("content").asString
                assertTrue(rewrite.contains(verbose.trimEnd('。')))
                assertTrue(rewrite.contains("不能只留最终收尾"))
                assertTrue(f.repository.getTimelineNodes(id).isEmpty())
            } }
        }
    }

    @Test fun automaticTimelineRefusesUncondensedUpdateWithoutChangingConfirmedHistory() = runBlocking {
        val original = "角色甲因证件过期被拒绝入塔，角色乙承诺补办；铜钥匙去向仍未查明"
        val verbose = "角色甲在塔门外等待并反复观察四周，角色乙仔细检查证件的纹理与印章并解释证件来历，" +
            "守门人逐项核对登记簿后表示仍然需要补交担保书，随后将证件原样交还并指向城南办事处，" +
            "两人继续讨论下一步的行程以及如何寻找已经失去联系的担保人，但尚未确定担保人的具体位置。"
        var nodeId = 0L
        Endpoint { socket, body ->
            val prompt = body.getAsJsonArray("messages").single().asJsonObject.get("content").asString
            if (prompt.contains("【待精简事件】")) memoryReply(socket, Gson().toJson(mapOf("content" to verbose)))
            else memoryReply(socket, Gson().toJson(mapOf("action" to "UPDATE", "targetNodeId" to nodeId,
                "newEvent" to mapOf("timeTag" to "古塔门前", "content" to verbose), "atemporalSettings" to emptyList<String>())))
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            nodeId = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id, timeTag = "古塔门前", event = original))
            val proposal = f.repository.evaluateAndAutoUpdateTimeline(id, "继续合成故事", verbose, config.id, config.modelName)!!
            assertNull(proposal.newEvent)
            assertNull(proposal.targetNodeId)
            assertTrue(proposal.extractionErrorMessage.orEmpty().contains("精简校验"))
            assertEquals(2, endpoint.payloads.size)
            val rewrite = endpoint.payloads.last().getAsJsonArray("messages").single().asJsonObject.get("content").asString
            assertTrue("合并旧事件后再精简，不能丢掉既有未决事项", rewrite.contains(original))
            f.repository.applyAutoTimelineProposal(id, proposal)
            assertEquals(original, f.repository.getTimelineNodes(id).single().event)
        } }
    }

    @Test fun automaticTimelineSkipsKnownFactsAfterSemanticCondensationForAppendAndUpdate() = runBlocking {
        val known = "角色甲因通行证过期被拒绝入塔；角色乙承诺补办，铜钥匙去向仍未查明"
        val verbose = "角色甲在一片昏暗的灯光中缓慢地走进古塔，认真观察了门前潮湿的地面以及斑驳的墙壁，" +
            "随后向守门人详细说明通行证的来历，经过漫长的核对后因证件过期被拒绝入塔，" +
            "角色乙在一旁反复安慰同伴并承诺补办通行证，但铜钥匙的去向直到最后仍然没有查明。"
        for (action in listOf("APPEND", "UPDATE")) {
            var nodeId = 0L
            Endpoint { socket, body ->
                val prompt = body.getAsJsonArray("messages").single().asJsonObject.get("content").asString
                if (prompt.contains("【待精简事件】")) memoryReply(socket, Gson().toJson(mapOf("content" to known)))
                else memoryReply(socket, Gson().toJson(mapOf("action" to action, "targetNodeId" to nodeId,
                    "newEvent" to mapOf("timeTag" to "古塔门前", "content" to verbose), "atemporalSettings" to emptyList<String>())))
            }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url)
                nodeId = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id, timeTag = "古塔门前", event = known))
                assertNull(f.repository.evaluateAndAutoUpdateTimeline(id, "继续合成故事", verbose, config.id, config.modelName))
                assertEquals(2, endpoint.payloads.size)
                assertEquals(known, f.repository.getTimelineNodes(id).single().event)
            } }
        }
    }

    @Test fun automaticTimelineDoesNotDeduplicateDifferentQuotedCredentials() = runBlocking {
        val old = "角色甲确认塔门口令为“AB”"
        val changed = "角色甲确认塔门口令为“A；B”"
        Endpoint { socket, _ -> memoryReply(socket, Gson().toJson(mapOf("action" to "APPEND",
            "newEvent" to mapOf("timeTag" to "古塔门前", "content" to changed), "atemporalSettings" to emptyList<String>())))
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id, timeTag = "古塔门前", event = old))
            val proposal = f.repository.evaluateAndAutoUpdateTimeline(id, "核对合成口令", changed, config.id, config.modelName)!!
            assertEquals(changed, proposal.newEvent!!.content)
            f.repository.applyAutoTimelineProposal(id, proposal)
            assertEquals(listOf(old, changed), f.repository.getTimelineNodes(id).map { it.event })
            assertEquals(1, endpoint.payloads.size)
        } }
    }

    @Test fun cancellingAutomaticTimelineDuringSecondCondensationRequestKeepsHistory() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val verbose = "角色甲在一片昏暗的灯光中缓慢地走进古塔，认真观察了门前潮湿的地面以及斑驳的墙壁，" +
            "随后向守门人详细说明通行证的来历，经过漫长的核对后因证件过期被拒绝入塔，" +
            "角色乙在一旁反复安慰同伴并承诺补办通行证，但铜钥匙的去向直到最后仍然没有查明。"
        Endpoint { socket, body ->
            val prompt = body.getAsJsonArray("messages").single().asJsonObject.get("content").asString
            if (prompt.contains("【待精简事件】")) {
                entered.countDown()
                released.await(10, TimeUnit.SECONDS)
            } else memoryReply(socket, Gson().toJson(mapOf("action" to "APPEND",
                "newEvent" to mapOf("timeTag" to "古塔门前", "content" to verbose), "atemporalSettings" to emptyList<String>())))
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val original = "合成角色已经抵达塔门，尚未入塔"
            f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id, timeTag = "古塔门前", event = original))
            val extraction = async(Dispatchers.IO) { f.repository.evaluateAndAutoUpdateTimeline(id, "继续合成故事", verbose, config.id, config.modelName) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                f.repository.cancelActiveRequest(id)
                try { withTimeout(3000) { extraction.await() }; fail("精简请求取消必须传播，不能变成错误提案") }
                catch (_: CancellationException) { }
                assertTrue(extraction.isCancelled)
                assertEquals(2, endpoint.payloads.size)
                assertEquals(original, f.repository.getTimelineNodes(id).single().event)
                assertTrue(f.db.memoryDao().getConversationMemories(id).isEmpty())
            } finally { released.countDown(); extraction.cancelAndJoin() }
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

    @Test fun actualIncrementalExtractionRejectsTruncatedJsonAndReasoningOnlyResults() = runBlocking {
        val syntacticallyComplete = """{"newStoryTime":null,"action":"APPEND","newEvent":{"timeTag":"第1天·上午","content":"合成船队交付两箱药品"},"atemporalSettings":[{"category":"世界规则","content":"合成码头需要通行证"}]}"""
        for (protocol in listOf("openai", "anthropic")) for (truncated in listOf(true, false)) {
            Endpoint { socket, _ ->
                val response = if (protocol == "openai") {
                    mapOf("choices" to listOf(mapOf("finish_reason" to if (truncated) "length" else "stop",
                        "message" to if (truncated) mapOf("content" to syntacticallyComplete)
                            else mapOf("reasoning_content" to syntacticallyComplete))))
                } else {
                    mapOf("stop_reason" to if (truncated) "max_tokens" else "end_turn",
                        "content" to listOf(if (truncated) mapOf("type" to "text", "text" to syntacticallyComplete)
                            else mapOf("type" to "thinking", "text" to syntacticallyComplete)))
                }
                reply(socket, Gson().toJson(response))
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                val (config, id) = f.conversation(endpoint.url, "synthetic-timeline-model", protocol)
                val proposal = f.repository.evaluateAndAutoUpdateTimeline(id, "继续合成故事", "合成船队交付两箱药品，码头需要通行证", config.id, config.modelName)!!
                assertNotNull("截断或只有推理时必须显示提取错误", proposal.extractionErrorMessage)
                assertNull(proposal.newEvent)
                assertTrue(proposal.atemporalSettings.isEmpty())
                assertTrue(f.repository.getTimelineNodes(id).isEmpty())
                assertTrue(f.db.memoryDao().getConversationMemories(id).isEmpty())
                assertEquals("不能把失败正文当格式错误再发一次严格JSON请求", 1, endpoint.payloads.size)
                val stat = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).single()
                assertEquals("timeline", stat.requestKind)
                assertFalse(stat.success)
                assertEquals(if (truncated) "interrupted" else "failed", stat.completionStatus)
            } }
        }
    }

    @Test fun incrementalExtractionIncludesTheEntireLongTurnWhenItsWindowFits() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "NO_UPDATE") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-timeline-model")
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(contextWindowTokens = 128000))
            val user = "合成请求。".repeat(900) + "用户尾段唯一约束：蓝印不得改为红印。"
            val assistant = "合成铺垫。".repeat(3000) + "正文中段唯一事实：铜钥匙仍由角色乙保管。" + "合成后续。".repeat(3000)
            f.repository.evaluateAndAutoUpdateTimeline(id, user, assistant, config.id, config.modelName)
            val prompt = endpoint.payloads.single().getAsJsonArray("messages").single().asJsonObject.get("content").asString
            assertTrue(prompt.contains(user))
            assertTrue(prompt.contains(assistant))
            assertFalse(prompt.contains("超长正文中段省略"))
            assertTrue(f.repository.getTimelineNodes(id).isEmpty())
        } }
    }

    @Test fun oversizedIncrementalExtractionRequestsChunkedReconciliationWithoutSendingTruncatedText() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "NO_UPDATE") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-timeline-model")
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(contextWindowTokens = 4000))
            val proposal = f.repository.evaluateAndAutoUpdateTimeline(id, "继续合成故事", "合成超长原文。".repeat(8000), config.id, config.modelName)!!
            assertTrue(proposal.extractionErrorMessage.orEmpty().contains("全历史分块梳理"))
            assertNull(proposal.newEvent)
            assertTrue(proposal.atemporalSettings.isEmpty())
            assertEquals(0, endpoint.payloads.size)
            assertTrue(f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).isEmpty())
        } }
    }

    @Test fun incrementalUpdateKeepsTheOriginalSnapshotWhenSourceIsEditedDuringTheRequest() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        var targetNodeId = 0L
        val oldText = "北港船队今日向角色甲交付两箱药品"
        val newText = "人工更正：船队尚未交付药品"
        Endpoint { socket, _ ->
            entered.countDown()
            check(released.await(10, TimeUnit.SECONDS))
            memoryReply(socket, """{"action":"UPDATE","targetNodeId":$targetNodeId,"newEvent":{"timeTag":"第1天·上午","content":"北港船队交付两箱药品并收到签收单"},"atemporalSettings":[]}""")
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-timeline-model")
            targetNodeId = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id, timeTag = "第1天·上午", event = oldText))
            val job = async(Dispatchers.IO) {
                f.repository.evaluateAndAutoUpdateTimeline(id, "继续合成故事", "船队交付药品后收到签收单", config.id, config.modelName)
            }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                f.db.timelineNodeDao().updateTimelineNode(f.repository.getTimelineNodes(id).single().copy(event = newText))
                released.countDown()
                val proposal = withTimeout(5000) { job.await() }!!
                assertEquals("UPDATE", proposal.action)
                assertEquals(oldText, proposal.previousEventContent)
                try {
                    f.repository.applyAutoTimelineProposal(id, proposal)
                    fail("生成期间编辑原事件后，不能用新正文冒充提案的旧快照")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message.orEmpty().contains("已被修改"))
                }
                assertEquals(newText, f.repository.getTimelineNodes(id).single().event)
            } finally { released.countDown(); job.cancelAndJoin() }
        } }
    }

    @Test fun differentTimeUpdateAndSimilarAppendNeverOverwriteEarlierEvent() = runBlocking {
        for (action in listOf("UPDATE", "APPEND")) {
            var nodeId = 0L
            Endpoint { socket, _ -> memoryReply(socket, """{"action":"$action","targetNodeId":$nodeId,"newEvent":{"timeTag":"第2天·清晨","category":"PLOT_EVENT","content":"林岚抵达古塔并与守门人缔结盟约"},"atemporalSettings":[]}""") }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url)
                f.repository.updateStoryTime(id, "第1天·清晨")
                nodeId = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                    timeTag = "第1天·清晨", event = "林岚抵达古塔", category = "PLOT_EVENT"))
                val before = f.repository.getTimelineNodes(id).single()
                val result = f.repository.evaluateAndAutoUpdateTimeline(id, "继续故事", "林岚抵达古塔并与守门人缔结盟约", config.id, config.modelName)!!
                assertEquals("APPEND", result.action)
                assertNull(result.targetNodeId)
                // Even a stale/manually edited UPDATE proposal must be checked again at save time.
                try {
                    f.repository.applyAutoTimelineProposal(id, result.copy(action = "UPDATE", targetNodeId = nodeId,
                        previousEventContent = before.event, previousEventCategory = before.category,
                        updatedStoryTime = "第2天·清晨",
                        atemporalSettings = listOf(AtemporalSettingItem(content = "无效跨时提案的合成规则不应保存"))))
                    fail("跨时 UPDATE 必须拒绝，不能覆盖早先事件或保存其它提案变化")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message.orEmpty().contains("请重新提取或审阅"))
                }
                assertEquals(listOf(before), f.repository.getTimelineNodes(id))
                assertEquals("第1天·清晨", f.db.conversationDao().getConversationById(id)!!.currentStoryTime)
                assertTrue(f.db.memoryDao().getConversationMemories(id).isEmpty())
                f.repository.applyAutoTimelineProposal(id, result)
                f.repository.applyAutoTimelineProposal(id, result)
                val nodes = f.db.timelineNodeDao().getTimelineNodes(id)
                assertEquals(2, nodes.size)
                val earlier = nodes.single { it.id == nodeId }
                assertEquals(before, earlier)
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
                "待确认", action = "UPDATE", targetNodeId = nodeId, previousEventContent = "林岚抵达古塔",
                previousEventCategory = "PLOT_EVENT",
                atemporalSettings = listOf(
                    // Model proposals default to this session; global promotion requires an explicit UI choice.
                    com.aiassistant.utils.AtemporalSettingItem(category = "世界规则", content = "古塔禁止一切火焰魔法", targetScope = "session"),
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
            assertTrue(f.db.memoryDao().getGlobalMemoriesFlow().first().isEmpty())
            val rp = f.db.roleplayMemoryDao().getMemoriesListBySession(rpId).single()
            assertEquals(memory.content, rp.content)
            assertTrue(rp.isPinned)
        }
    }

    @Test fun sameTimeSimilarEventsNeverInferAnUpdateWithoutAnExplicitTarget() = runBlocking {
        val existingText = "北港船队今日向角色甲交付两箱药品"
        val differentFacts = listOf("北港船队今日向角色乙交付两箱药品", "北港船队今日未向角色甲交付两箱药品", "北港船队今日向角色甲交付三箱药品")
        for (action in listOf("APPEND", "UPDATE")) for (incoming in differentFacts) {
            Endpoint { socket, _ -> memoryReply(socket, Gson().toJson(mapOf(
                "action" to action, "targetNodeId" to null,
                "newEvent" to mapOf("timeTag" to "第1天·上午", "category" to "PLOT_EVENT", "content" to incoming),
                "atemporalSettings" to emptyList<String>()
            ))) }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url)
                val nodeId = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                    timeTag = "第1天·上午", event = existingText, category = "PLOT_EVENT"))
                val proposal = f.repository.evaluateAndAutoUpdateTimeline(id, "继续合成故事", incoming, config.id, config.modelName)!!
                assertEquals("APPEND", proposal.action)
                assertNull(proposal.targetNodeId)
                f.repository.applyAutoTimelineProposal(id, proposal)
                f.repository.applyAutoTimelineProposal(id, proposal.copy(newEvent = proposal.newEvent!!.copy(content = incoming + "。")))
                val nodes = f.repository.getTimelineNodes(id)
                assertEquals("两个不同事实保留，标点变化的重复不新增", 2, nodes.size)
                assertEquals(existingText, nodes.single { it.id == nodeId }.event)
                assertEquals(incoming, nodes.single { it.id != nodeId }.event)
                assertEquals(1, endpoint.payloads.size)
            } }
        }
    }

    @Test fun applyingStaleTimelineUpdatePreservesManualEditAndRollsBackOtherProposalChanges() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation("https://example.invalid/v1")
            f.repository.updateStoryTime(id, "第1天·上午")
            val before = "北港船队今日向角色甲交付两箱药品"
            val manual = "人工更正：北港船队尚未交付药品"
            val nodeId = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                timeTag = "第1天·上午", event = before, category = "PLOT_EVENT"))
            val proposal = com.aiassistant.data.repository.AutoTimelineUpdateResult("第1天·中午",
                com.aiassistant.utils.TimelineEventItem(timeTag = "第1天·上午", content = "北港船队交付两箱药品并收回签收单"),
                "待确认", action = "UPDATE", targetNodeId = nodeId, previousEventContent = before,
                previousEventCategory = "PLOT_EVENT",
                atemporalSettings = listOf(com.aiassistant.utils.AtemporalSettingItem(content = "本提案未确认成功，不应保存")))
            f.db.timelineNodeDao().updateTimelineNode(f.repository.getTimelineNodes(id).single().copy(event = manual))
            try {
                f.repository.applyAutoTimelineProposal(id, proposal)
                fail("陈旧提案必须要求重新审阅，不能覆盖人工修改")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("已被修改"))
            }
            assertEquals(manual, f.repository.getTimelineNodes(id).single().event)
            assertEquals("第1天·上午", f.db.conversationDao().getConversationById(id)!!.currentStoryTime)
            assertTrue(f.db.memoryDao().getConversationMemories(id).isEmpty())
        }
    }

    @Test fun applyingTimelineUpdateRejectsDeletedOrRetimedTargetsAndRollsBackOtherChanges() = runBlocking {
        for (deleted in listOf(true, false)) {
            Fixture().use { f ->
                val (_, id) = f.conversation("https://example.invalid/v1")
                f.repository.updateStoryTime(id, "第1天·上午")
                val before = "合成船队已经交付两箱药品"
                val nodeId = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                    timeTag = "第1天·上午", event = before))
                val proposal = com.aiassistant.data.repository.AutoTimelineUpdateResult("第1天·中午",
                    TimelineEventItem(timeTag = "第1天·上午", content = "合成船队已经交付两箱药品并收到签收单"),
                    "待确认", action = "UPDATE", targetNodeId = nodeId, previousEventContent = before,
                    previousEventCategory = "PLOT_EVENT",
                    atemporalSettings = listOf(AtemporalSettingItem(content = "未确认成功的合成规则不应保存")))
                if (deleted) f.db.timelineNodeDao().deleteTimelineNodeById(nodeId)
                else f.db.timelineNodeDao().updateTimelineNode(f.repository.getTimelineNodes(id).single().copy(timeTag = "第2天·上午"))
                val unchangedNodes = f.repository.getTimelineNodes(id)
                try {
                    f.repository.applyAutoTimelineProposal(id, proposal)
                    fail("目标失效后必须重新审阅，不能复活节点或追加陈旧事件")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message.orEmpty().contains("请重新提取或审阅"))
                }
                assertEquals(unchangedNodes, f.repository.getTimelineNodes(id))
                assertEquals("第1天·上午", f.db.conversationDao().getConversationById(id)!!.currentStoryTime)
                assertTrue(f.db.memoryDao().getConversationMemories(id).isEmpty())
            }
        }
    }

    @Test fun applyingTimelineUpdatePreservesCategoryOnlyEditsBeforeAndAfterAcceptance() = runBlocking {
        for (acceptedFirst in listOf(false, true)) {
            var nodeId = 0L
            Endpoint { socket, _ -> memoryReply(socket,
                """{"action":"UPDATE","targetNodeId":$nodeId,"newEvent":{"timeTag":"第1天·上午","category":"PLOT_EVENT","content":"合成船队交付两箱药品并收到签收单"},"atemporalSettings":[]}""")
            }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url)
                f.repository.updateStoryTime(id, "第1天·上午")
                nodeId = f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                    timeTag = "第1天·上午", event = "合成船队交付两箱药品", category = "PLOT_EVENT"))
                val proposal = f.repository.evaluateAndAutoUpdateTimeline(id, "继续合成故事",
                    "合成船队交付两箱药品并收到签收单", config.id, config.modelName)!!
                assertEquals("UPDATE", proposal.action)
                assertEquals("PLOT_EVENT", proposal.previousEventCategory)
                if (acceptedFirst) {
                    f.repository.applyAutoTimelineProposal(id, proposal)
                    f.repository.applyAutoTimelineProposal(id, proposal)
                }
                val manual = f.repository.getTimelineNodes(id).single().copy(category = "KEY_FACT")
                f.db.timelineNodeDao().updateTimelineNode(manual)
                try {
                    f.repository.applyAutoTimelineProposal(id, proposal.copy(updatedStoryTime = "第1天·中午",
                        atemporalSettings = listOf(AtemporalSettingItem(content = "分类冲突提案的合成规则不应保存"))))
                    fail("只修改分类也必须使旧提案失效，包括曾确认过的同一提案")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message.orEmpty().contains("已被修改"))
                }
                assertEquals(listOf(manual), f.repository.getTimelineNodes(id))
                assertEquals("第1天·上午", f.db.conversationDao().getConversationById(id)!!.currentStoryTime)
                assertTrue(f.db.memoryDao().getConversationMemories(id).isEmpty())
            } }
        }
    }

    @Test fun ordinaryChatConfirmedCharacterSettingsRemainVisibleAndEnterNextRequest() = runBlocking {
        Endpoint { socket, _ -> streamReply(socket) }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            val categories = listOf("角色特征", "习惯偏好", "生理禁忌", "世界规则", "人际羁绊", "固有设定")
            val proposal = com.aiassistant.data.repository.AutoTimelineUpdateResult(null, null, "待确认",
                atemporalSettings = categories.map { com.aiassistant.utils.AtemporalSettingItem(
                    category = it, content = "合成角色的${it}仅在本对话有效") })
            f.repository.applyAutoTimelineProposal(id, proposal)
            val saved = f.repository.getConversationMemoriesList(id)
            assertEquals(categories.size, saved.size)
            assertTrue(saved.all { it.isEnabled && it.scope == "conversation" && it.conversationId == id })
            // The same timeline predicate is used by both the settings list and request assembly.
            assertTrue(saved.none { com.aiassistant.utils.TimelineMemoryHelper.isExplicitTimelineEvent(it.content) })
            chat(f, config, id)
            val request = endpoint.payloads.last().getAsJsonArray("messages").toString()
            for (setting in saved) assertTrue(request.contains(setting.content))
            val (_, otherId) = f.conversation(endpoint.url)
            chat(f, config, otherId)
            val otherRequest = endpoint.payloads.last().getAsJsonArray("messages").toString()
            for (setting in saved) assertFalse(otherRequest.contains(setting.content))
        } }
    }

    @Test fun extractedLocalSettingsSurviveReloadAndEnterContextWithoutBoundCards() = runBlocking {
        Endpoint { socket, _ -> memoryReply(socket, "NO_UPDATE") }.use { endpoint -> Fixture().use { f ->
            val (_, id) = f.conversation(endpoint.url)
            val characters = listOf(
                com.aiassistant.domain.model.CharacterProfile(name = "合成守塔人", background = "只在黎明开启石门"),
                com.aiassistant.domain.model.CharacterProfile(name = "合成旅人", background = "携带蓝色通行证"))
            val scenario = com.aiassistant.domain.model.RoleplayScenario(name = "合成古塔", worldview = "塔内禁止火焰魔法")
            val rpId = f.db.roleplaySessionDao().insertSession(com.aiassistant.domain.model.RoleplaySession(
                conversationId = id, customCharacterData = Gson().toJson(characters),
                customScenarioData = Gson().toJson(scenario)))
            val repo = com.aiassistant.data.repository.RoleplayRepository(f.db.characterProfileDao(),
                f.db.roleplayScenarioDao(), f.db.roleplaySessionDao(), f.db.roleplayMemoryDao(),
                f.db.characterTagDao(), f.db.conversationDao(), f.db.messageDao(), f.db.worldBookDao(), f.db.memoryDao())
            val reloaded = f.db.roleplaySessionDao().getSessionById(rpId)!!
            assertTrue(reloaded.getEffectiveCharacterIds().isEmpty())
            assertEquals(characters.map { it.name }, repo.getEffectiveCharactersForSession(reloaded).map { it.name })
            val context = repo.assembleRoleplayContext(rpId, null, "继续故事", includeHistory = false)
            for (character in characters) {
                assertTrue(context.contains(character.name))
                assertTrue(context.contains(character.background))
            }
            assertTrue(context.contains(scenario.worldview))
        } }
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
    @Test fun compressionCoversAllEarlyMessagesAndNextRequestUsesSummaryWithRecentTurns() = runBlocking {
        val summary = "【核心背景与用户固定约束】\n合成探险队必须保留铜钥匙并遵守北港通行规则。\n【历史关键里程碑与决策推进】\n已完成各阶段的合成航海记录并在港口集结。\n【当前未决议题与待办上下文】\n接下来等待领航员确认新的航线。"
        Endpoint { socket, payload ->
            if (payload.get("stream")?.asBoolean == true) streamReply(socket) else memoryReply(socket, summary)
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-model", enabled = false)
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(contextWindowTokens = 128000))
            val ids = (1..106).map { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "独立合成事实 [$index]。".repeat(3), createdAt = index.toLong())) }
            val result = f.repository.compressConversationContext(id).getOrThrow()
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals(summary, saved.rollingSummary)
            assertEquals(ids[89], saved.summaryUpdatedMessageId)
            assertEquals(106, f.db.messageDao().getMessagesList(id).size)
            val summaries = endpoint.payloads.filter { it.get("stream")?.asBoolean != true }
            assertEquals(3, summaries.size)
            val sentSources = summaries.joinToString("\n") { it.toString() }
            for (messageId in ids.take(90)) assertTrue("未覆盖原文ID $messageId", sentSources.contains("原文ID $messageId]"))
            assertTrue(summaries[1].toString().contains("铜钥匙"))
            assertFalse(result.canCompress)
            assertEquals(16, result.recentMessageCount)
            chat(f, config, id)
            val chatBody = endpoint.payloads.last().toString()
            assertTrue(chatBody.contains("铜钥匙"))
            assertTrue(chatBody.contains("独立合成事实 [106]"))
            assertFalse(chatBody.contains("独立合成事实 [1]"))
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun twoThousandTurnsAreRetiredFromActualRequestsAndIncrementalUsageInBothProtocols() = runBlocking {
        val summary = "【核心背景与用户固定约束】\n合成旅队保管铜钥匙。\n【历史关键里程碑与决策推进】\n已整理各站的合成通行记录。\n【当前未决议题与待办上下文】\n核对 synthetic-long-journey.json"
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, payload ->
                if (payload.get("stream")?.asBoolean == true) {
                    if (protocol == "openai") streamReply(socket) else reply(socket,
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\n" +
                            "data: {\"type\":\"message_stop\"}\n\n", type = "text/event-stream")
                } else completedSummaryReply(socket, summary, protocol)
            }.use { endpoint ->
                for ((tier, expectedMaximum) in listOf(CompressionTier.L2 to 16, CompressionTier.L3 to 8,
                    CompressionTier.L4 to 2, CompressionTier.LC to 400)) {
                    // A real send persists its assistant reply. Keep each policy's initial
                    // two-thousand-turn history independent, rather than manufacturing
                    // extra assistant-only messages in the same last user turn.
                    Fixture().use { f ->
                    val (config, id) = f.conversation(endpoint.url, "synthetic-long-history-model", protocol, enabled = false)
                    f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(contextWindowTokens = 32768))
                    val originals = (1..4000).map { index -> Message(conversationId = id,
                        role = if (index % 2 == 1) "user" else "assistant", createdAt = index.toLong(),
                        content = "合成长历史唯一片段[$index]：核对通行凭证并记录沿途变化。".repeat(2)) }
                    val ids = f.db.messageDao().insertMessages(originals)
                    val before = f.repository.getConversationContextUsage(id)
                    assertEquals("回归前提：两千轮原文已超过模型输入预算", 1f, before.usagePercent)
                    val requestStart = endpoint.payloads.size
                    f.repository.updateConversationCompressionTier(id, tier, 8, 10)
                    val compressed = f.repository.compressConversationContext(id).getOrThrow()
                    assertEquals(tier, compressed.compressionTier)
                    assertTrue("$protocol/$tier 必须使圆环脱离满载", compressed.usagePercent < 1f)
                    assertEquals("$protocol/$tier 应严格应用目标原文窗口", expectedMaximum, compressed.recentMessageCount)
                    val sourceCount = 4000 - expectedMaximum
                    assertEquals(ids[sourceCount - 1], f.db.conversationDao().getConversationById(id)!!.summaryUpdatedMessageId)
                    val summaries = endpoint.payloads.drop(requestStart).filter { it.get("stream")?.asBoolean != true }
                    assertTrue("两千轮历史应完整分段处理", summaries.size >= (sourceCount + 39) / 40)
                    val sourcePrompts = summaries.joinToString("\n") { it.getAsJsonArray("messages").single().asJsonObject.get("content").asString }
                    for (sourceId in ids.take(sourceCount)) assertTrue("源消息未进入任何摘要请求：$sourceId", sourcePrompts.contains("原文ID $sourceId]"))
                    chat(f, config, id)
                    val request = endpoint.payloads.last()
                    val system = if (protocol == "anthropic") request.get("system").asString else
                        request.getAsJsonArray("messages").first().asJsonObject.get("content").asString
                    assertTrue(system.contains("铜钥匙"))
                    val body = request.getAsJsonArray("messages").filter { it.asJsonObject.get("role").asString != "system" }
                    assertEquals(compressed.recentMessageCount + 1, body.size)
                    assertFalse("已被摘要覆盖的开头不能继续发送", request.toString().contains("合成长历史唯一片段[1]"))
                    assertTrue(request.toString().contains("合成长历史唯一片段[4000]"))
                    val actualTokens = body.sumOf { AiRepository.estimateTokenCount(it.asJsonObject.get("content").toString()) + 24 } + AiRepository.estimateTokenCount(system)
                    assertTrue("实际HTTP正文仍超过输入预算：$protocol/$tier/$actualTokens", actualTokens <= compressed.promptBudgetTokens)
                    assertEquals(originals.map { it.content }, f.db.messageDao().getMessagesList(id).take(4000).map { it.content })
                    assertEquals("实际发送只新增正式回复，不删除任何原文", 4001, f.db.messageDao().getMessagesList(id).size)
                    }
                }
                // Four new turns require incremental coverage. Previously that condition
                // alone put all two thousand already-covered turns back into the ring.
                Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url, "synthetic-long-history-model", protocol, enabled = false)
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(contextWindowTokens = 32768))
                val originals = (1..4000).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", createdAt = index.toLong(),
                    content = "合成长历史唯一片段[$index]：核对通行凭证并记录沿途变化。".repeat(2)) }
                val ids = f.db.messageDao().insertMessages(originals)
                f.repository.updateConversationCompressionTier(id, CompressionTier.L4, 8, 10)
                f.repository.compressConversationContext(id).getOrThrow()
                assertEquals(ids[3997], f.db.conversationDao().getConversationById(id)!!.summaryUpdatedMessageId)
                (4001..4008).forEach { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", createdAt = index.toLong(), content = "合成新增片段[$index]。")) }
                val incremental = f.repository.getConversationContextUsage(id)
                assertTrue("增量摘要待更新不能重新计入已覆盖的4000条历史", incremental.usagePercent < 0.1f)
                assertTrue(incremental.recentMessageCount <= 10)
                val summaryCalls = endpoint.payloads.count { it.get("stream")?.asBoolean != true }
                chat(f, config, id)
                val nextSummaries = endpoint.payloads.filter { it.get("stream")?.asBoolean != true }.drop(summaryCalls)
                assertEquals("只需补充未覆盖的四轮", 1, nextSummaries.size)
                assertFalse(nextSummaries.single().toString().contains("合成长历史唯一片段[1]"))
                assertTrue(nextSummaries.single().toString().contains("合成新增片段[4006]"))
                assertEquals(3, endpoint.payloads.last().getAsJsonArray("messages").count { it.asJsonObject.get("role").asString != "system" })
                assertEquals(originals.map { it.content }, f.db.messageDao().getMessagesList(id).take(4000).map { it.content })
                assertEquals(4009, f.db.messageDao().getMessagesList(id).size)
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
                }
            }
        }
    }

    @Test fun copiedCompressedConversationMapsCoverageAndDoesNotResendEarlyHistory() = runBlocking {
        val summary = "【核心背景与用户固定约束】\n合成角色携带铜钥匙。\n【历史关键里程碑与决策推进】\n已完成合成采购。\n【当前未决议题与待办上下文】\n等待复核清单。"
        Endpoint { socket, payload ->
            if (payload.get("stream")?.asBoolean == true) streamReply(socket) else completedSummaryReply(socket, summary)
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-copy-coverage-model", enabled = false)
            val ids = (1..40).map { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成复制原文[$index]。", createdAt = index.toLong())) }
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                compressionTier = CompressionTier.L4.level, rollingSummary = summary,
                summaryUpdatedMessageId = ids[37], contextWindowTokens = 32768))
            val duplicateId = f.repository.duplicateConversation(id)
            val copiedMessages = f.db.messageDao().getMessagesList(duplicateId)
            val copied = f.db.conversationDao().getConversationById(duplicateId)!!
            assertEquals(summary, copied.rollingSummary)
            assertEquals(copiedMessages[37].id, copied.summaryUpdatedMessageId)
            assertTrue(copied.summaryUpdatedMessageId!! > ids.last())
            f.repository.compressConversationContext(duplicateId).getOrThrow()
            assertTrue("复制时已覆盖的原文不能重新请求摘要", endpoint.payloads.isEmpty())
            chat(f, config, duplicateId)
            assertEquals(1, endpoint.payloads.size)
            assertTrue(endpoint.payloads.single().toString().contains("铜钥匙"))
            assertFalse(endpoint.payloads.single().toString().contains("合成复制原文[1]"))
            assertTrue(endpoint.payloads.single().toString().contains("合成复制原文[40]"))
            assertEquals(ids[37], f.db.conversationDao().getConversationById(id)!!.summaryUpdatedMessageId)
        } }
    }

    @Test fun compressionUsesSelectedApiEndpointProtocolAndWindowWithoutRebindingConversation() = runBlocking {
        val summary = "【核心背景与用户固定约束】\n合成队伍保留铜钥匙。\n【历史关键里程碑与决策推进】\n合成资料已整理。\n【当前未决议题与待办上下文】\n等待复核。"
        Endpoint { socket, _ -> completedSummaryReply(socket, summary) }.use { oldEndpoint ->
            Endpoint { socket, _ -> completedSummaryReply(socket, summary, "anthropic") }.use { selectedEndpoint -> Fixture().use { f ->
                val (original, id) = f.conversation(oldEndpoint.url, "synthetic-shared-model", enabled = false)
                val (selected, _) = f.conversation(selectedEndpoint.url, original.modelName, "anthropic", enabled = false)
                f.db.selectedModelDao().insertModel(SelectedModel(apiConfigId = original.id, modelName = original.modelName, contextWindowTokens = 128000))
                f.db.selectedModelDao().insertModel(SelectedModel(apiConfigId = selected.id, modelName = selected.modelName, contextWindowTokens = 4000))
                (1..24).forEach { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成配置隔离原文[$index]。", createdAt = index.toLong())) }
                f.repository.updateConversationCompressionTier(id, CompressionTier.L4, 8)
                val usage = f.repository.compressConversationContext(id, apiConfigIdOverride = selected.id).getOrThrow()
                assertEquals(4000, usage.contextWindowTokens)
                assertEquals(2, usage.recentMessageCount)
                assertTrue(oldEndpoint.payloads.isEmpty())
                assertTrue(selectedEndpoint.payloads.isNotEmpty())
                assertTrue(selectedEndpoint.paths.all { it.contains("/messages") })
                assertEquals(original.id, f.db.conversationDao().getConversationById(id)!!.apiConfigId)
                val normal = f.repository.getConversationContextUsage(id)
                assertEquals(128000, normal.contextWindowTokens)
                assertEquals(4000, f.repository.getConversationContextUsage(id, apiConfigIdOverride = selected.id).contextWindowTokens)
                assertTrue(selectedEndpoint.failures.toString(), selectedEndpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun selectedModelSmallWindowBoundsEveryLongSourceSummaryRequest() = runBlocking {
        val summary = "【核心背景与用户固定约束】\n合成档案必须保留蓝印凭证。\n【历史关键里程碑与决策推进】\n已核对原文中的首段、中段与末段。\n【当前未决议题与待办上下文】\n等待复核 synthetic-record.json"
        for (protocol in listOf("openai", "anthropic")) for (generateAll in listOf(false, true)) {
            Endpoint { socket, _ -> completedSummaryReply(socket, summary, protocol) }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                val (config, id) = f.conversation(endpoint.url, "synthetic-small-window-model", protocol, enabled = false)
                f.db.selectedModelDao().insertModel(SelectedModel(apiConfigId = config.id,
                    modelName = config.modelName, contextWindowTokens = 4000))
                assertNull("回归前提：窗口只在已选择模型上设置", f.db.conversationDao().getConversationById(id)!!.contextWindowTokens)
                val source = "首段唯一事实：北港收到蓝印凭证。\n" + "合成档案铺垫。".repeat(700) +
                    "\n中段唯一事实：铜钥匙留在合成资料柜。\n" + "合成档案后续。".repeat(700) +
                    "\n末段唯一事实：待办文件为 synthetic-record.json"
                val ids = (1..24).map { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", createdAt = index.toLong(),
                    content = if (index == 1) source else "合成短轮次 $index。")) }
                val result = if (generateAll) f.repository.generateRollingSummaryNow(id).getOrThrow()
                    else f.repository.compressConversationContext(id).getOrThrow()
                assertEquals(4000, result.contextWindowTokens)
                assertTrue("长原文必须分多次完整处理", endpoint.payloads.size > 1)
                for (payload in endpoint.payloads) {
                    val prompt = payload.getAsJsonArray("messages").single().asJsonObject.get("content").asString
                    val outputTokens = (payload.get("max_tokens") ?: payload.get("max_completion_tokens")).asInt
                    val total = AiRepository.estimateTokenCount(prompt) + outputTokens
                    assertTrue("完整摘要提示与输出超出 4K 窗口：$total，协议=$protocol，生成全部=$generateAll", total <= 4000 - 512)
                    assertEquals(config.modelName, payload.get("model").asString)
                }
                // A fact may cross a legitimate chunk boundary. Reassemble only the actual
                // HTTP source fragments, excluding repeated summary templates and role labels.
                val receivedSources = linkedMapOf<Long, StringBuilder>()
                val receivedOrder = mutableListOf<Long>()
                val sourceHeader = Regex("(?m)^[ \\t]*\\[(?:用户|助手) · 原文ID (\\d+)] ")
                for (payload in endpoint.payloads) {
                    val prompt = payload.getAsJsonArray("messages").single().asJsonObject.get("content").asString
                    assertTrue(prompt.contains("新增对话历史：\n"))
                    val transcript = prompt.substringAfter("新增对话历史：\n")
                    val headers = sourceHeader.findAll(transcript).toList()
                    assertTrue("每个分块请求都必须携带实际原文", headers.isNotEmpty())
                    for ((index, header) in headers.withIndex()) {
                        val messageId = header.groupValues[1].toLong()
                        val nextHeader = headers.getOrNull(index + 1)
                        val body = transcript.substring(header.range.last + 1, nextHeader?.range?.first ?: transcript.length)
                        // joinToString adds exactly one separator between message fragments;
                        // keep every newline that belongs to the source itself.
                        val fragment = if (nextHeader == null) body else body.removeSuffix("\n")
                        receivedSources.getOrPut(messageId) { StringBuilder() }.append(fragment)
                        if (receivedOrder.lastOrNull() != messageId) receivedOrder += messageId
                    }
                }
                val expectedSourceIds = if (generateAll) ids else ids.take(8)
                assertEquals("全部分块必须依原始顺序覆盖连续来源", expectedSourceIds, receivedOrder)
                assertEquals(expectedSourceIds, receivedSources.keys.toList())
                for ((index, messageId) in expectedSourceIds.withIndex()) {
                    val expectedSource = if (index == 0) source else "合成短轮次 ${index + 1}。"
                    assertEquals("原文 $messageId 的首中尾与换行必须逐字完整进入 HTTP", expectedSource,
                        receivedSources.getValue(messageId).toString())
                }
                assertTrue("下一分段必须合并前一段摘要", endpoint.payloads[1].toString().contains("蓝印凭证"))
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(summary, saved.rollingSummary)
                assertEquals(if (generateAll) ids.last() else ids[7], saved.summaryUpdatedMessageId)
                assertEquals(24, f.db.messageDao().getMessagesList(id).size)
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun summaryCapacityRejectionSplitsOnlyFailedLongSourceAndPreservesEverySuccessfulCharacterInBothProtocols() = runBlocking {
        val summary = "合成船队仍须保留蓝印凭证；已记录各站通行经过，铜钥匙仍在北港资料柜。下一步等待领航员确认航线。"
        for (protocol in listOf("openai", "anthropic")) {
            val rejected = java.util.concurrent.atomic.AtomicBoolean()
            val longSourceId = java.util.concurrent.atomic.AtomicLong()
            val rejectedPrompt = java.util.concurrent.atomic.AtomicReference<String>()
            val successfulPrompts = CopyOnWriteArrayList<String>()
            Endpoint { socket, payload ->
                if (payload.get("stream")?.asBoolean == true) {
                    if (protocol == "openai") streamReply(socket) else reply(socket,
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\n" +
                            "data: {\"type\":\"message_stop\"}\n\n", type = "text/event-stream")
                } else {
                    val prompt = payload.getAsJsonArray("messages").single().asJsonObject.get("content").asString
                    val sources = sourceFragmentsFromSummaryPrompt(prompt)
                    if (sources.any { it.first == longSourceId.get() } && rejected.compareAndSet(false, true)) {
                        rejectedPrompt.set(prompt)
                        reply(socket, "{\"error\":{\"message\":\"maximum context length is 8192 tokens; input too long\"}}", "400 Bad Request")
                    } else {
                        successfulPrompts.add(prompt)
                        completedSummaryReply(socket, summary, protocol)
                    }
                }
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                f.settings.setBackupKeyFallbackEnabled(false)
                val (config, id) = f.conversation(endpoint.url, "synthetic-capacity-summary-model", protocol, enabled = false)
                val originalContent = (1..86).map { index ->
                    if (index == 41) "长片段首段：收到蓝印凭证。\n" + "合成中段经过：核对铜钥匙与沿途通行记录。".repeat(1_200) +
                        "\n长片段末段：待领航员确认 synthetic-capacity-route.json"
                    else "容量回归合成原文[$index]：记录沿途变化并保留蓝印凭证。"
                }
                val ids = f.db.messageDao().insertMessages(originalContent.mapIndexed { index, text -> Message(
                    conversationId = id, role = if (index % 2 == 0) "user" else "assistant", content = text, createdAt = index.toLong()) })
                longSourceId.set(ids[40])
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level))
                val originalMessages = f.db.messageDao().getMessagesList(id)
                val result = withTimeout(20_000) { f.repository.compressConversationContext(id).getOrThrow() }
                assertTrue("必须真实经过服务端容量错误", rejected.get())
                val summaries = endpoint.payloads.toList()
                assertEquals("容量错误不能换成同大小的流式请求", 0, summaries.count { it.get("stream")?.asBoolean == true })
                assertEquals("只拒绝一个原始分段", successfulPrompts.size + 1, summaries.size)
                val failedIndex = summaries.indexOfFirst {
                    it.getAsJsonArray("messages").single().asJsonObject.get("content").asString == rejectedPrompt.get()
                }
                assertTrue("失败前应已有成功分段，验证不会从头重来", failedIndex > 0)
                val failedLength = sourceFragmentsFromSummaryPrompt(rejectedPrompt.get()).sumOf { it.second.length }
                val nextPrompt = summaries[failedIndex + 1].getAsJsonArray("messages").single().asJsonObject.get("content").asString
                val nextFragments = sourceFragmentsFromSummaryPrompt(nextPrompt)
                assertEquals("重试应从失败的原文开始", ids[40], nextFragments.first().first)
                assertTrue("容量错误后必须缩小失败分段", nextFragments.sumOf { it.second.length } < failedLength)
                val reconstructed = linkedMapOf<Long, StringBuilder>()
                val successfulOrder = mutableListOf<Long>()
                successfulPrompts.forEach { prompt ->
                    sourceFragmentsFromSummaryPrompt(prompt).forEach { (sourceId, text) ->
                        reconstructed.getOrPut(sourceId) { StringBuilder() }.append(text)
                        if (successfulOrder.lastOrNull() != sourceId) successfulOrder += sourceId
                    }
                }
                assertEquals("成功请求必须连续覆盖每条待退休原文且不重复已成功片段", ids.take(84), successfulOrder)
                assertEquals(ids.take(84), reconstructed.keys.toList())
                ids.take(84).forEachIndexed { index, sourceId ->
                    assertEquals("原文 $sourceId 的首中尾与换行不能因容量重试丢失", originalContent[index], reconstructed.getValue(sourceId).toString())
                }
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(summary, saved.rollingSummary)
                assertEquals(ids[83], saved.summaryUpdatedMessageId)
                assertEquals(2, result.recentMessageCount)
                assertEquals(originalMessages, f.db.messageDao().getMessagesList(id))
                chat(f, config, id)
                val wire = endpoint.payloads.last()
                val system = if (protocol == "anthropic") wire.get("system").asString else
                    wire.getAsJsonArray("messages").first().asJsonObject.get("content").asString
                assertTrue("成功压缩后的实际对话请求必须注入摘要", system.contains(summary))
                assertFalse("已压缩早期原文不得继续发送", wire.toString().contains("容量回归合成原文[1]"))
                assertTrue("最近完整轮次仍须保留", wire.toString().contains("容量回归合成原文[86]"))
                assertEquals("正式发送只新增回复，原文保留", originalMessages, f.db.messageDao().getMessagesList(id).take(originalMessages.size))
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun completedPlainAndSingleHeadingSummariesAreSavedAndUsedInFollowingChatInBothProtocols() = runBlocking {
        val summaries = listOf(
            "合成角色已一同完成超市采购，挑选了蓝色购物袋并核对找零，回家后将铜钥匙放回北港资料柜。下一步等待确认晚餐安排。",
            "【合成采购记录】\n两名合成角色已挑选蓝色购物袋并完成采购，回家后铜钥匙仍保存在北港资料柜，接下来等待确认晚餐安排。"
        )
        for (protocol in listOf("openai", "anthropic")) for (summary in summaries) {
            Endpoint { socket, payload ->
                if (payload.get("stream")?.asBoolean == true) {
                    if (protocol == "openai") streamReply(socket) else reply(socket,
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\n" +
                            "data: {\"type\":\"message_stop\"}\n\n", type = "text/event-stream")
                } else completedSummaryReply(socket, summary, protocol)
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                val (config, id) = f.conversation(endpoint.url, "synthetic-plain-summary-model", protocol, enabled = false)
                val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成采购原文[$index]：核对蓝色购物袋与铜钥匙。", createdAt = index.toLong()) })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level))
                val originals = f.db.messageDao().getMessagesList(id)
                f.repository.compressConversationContext(id).getOrThrow()
                assertEquals("正常结束的完整正文不能因缺少固定四标题被拒绝", 1, endpoint.payloads.size)
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(summary, saved.rollingSummary)
                assertEquals(ids[21], saved.summaryUpdatedMessageId)
                chat(f, config, id)
                val request = endpoint.payloads.last()
                val system = if (protocol == "anthropic") request.get("system").asString else
                    request.getAsJsonArray("messages").first().asJsonObject.get("content").asString
                assertTrue(system.contains(summary))
                assertFalse(request.toString().contains("合成采购原文[1]"))
                assertTrue(request.toString().contains("合成采购原文[24]"))
                assertEquals(originals, f.db.messageDao().getMessagesList(id).take(24))
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun unknownSummaryBadRequestNeverSplitsOrFallsBackToStreamingAndKeepsSavedSummaryInBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, _ -> reply(socket, "{\"error\":{\"message\":\"synthetic unsupported argument\"}}", "400 Bad Request") }
                .use { endpoint -> Fixture().use { f ->
                    f.settings.setModelRetryEnabled(false)
                    f.settings.setBackupKeyFallbackEnabled(false)
                    val (_, id) = f.conversation(endpoint.url, "synthetic-unknown-bad-request-model", protocol, enabled = false)
                    val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                        role = if (index % 2 == 1) "user" else "assistant", content = "合成未知参数失败原文[$index]。", createdAt = index.toLong()) })
                    f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                        contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level,
                        rollingSummary = "上次确认：合成队伍仍在北港，尚未出发。", summaryUpdatedMessageId = ids[1], summaryUpdatedAt = 123L))
                    val original = f.db.conversationDao().getConversationById(id)!!
                    val originalMessages = f.db.messageDao().getMessagesList(id)
                    val result = f.repository.compressConversationContext(id)
                    assertTrue(result.isFailure)
                    assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("400"))
                    assertEquals("未知400不能拆分原文或再发流式请求", 1, endpoint.payloads.size)
                    assertFalse(endpoint.payloads.single().get("stream")?.asBoolean == true)
                    val saved = f.db.conversationDao().getConversationById(id)!!
                    assertEquals(original.rollingSummary, saved.rollingSummary)
                    assertEquals(original.summaryUpdatedMessageId, saved.summaryUpdatedMessageId)
                    assertEquals(original.summaryUpdatedAt, saved.summaryUpdatedAt)
                    assertEquals(originalMessages, f.db.messageDao().getMessagesList(id))
                    assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
                } }
        }
    }

    @Test fun policyRefusalsAndProtocolRefusalSignalsNeverReplaceSummaryOrAdvanceCoverage() = runBlocking {
        val refusal = "由于安全政策限制，我无法处理本次摘要请求，也不能提供这段材料的总结。请调整提供的文本后重新尝试。"
        val plausible = "合成船队已核对铜钥匙与蓝印凭证，仍在北港等待确认，下一步需要检查 synthetic-route.json。"
        for (protocol in listOf("openai", "anthropic")) {
            val kinds = if (protocol == "openai") listOf("policy", "markdown", "field", "block", "finish", "filter")
                else listOf("policy", "markdown", "finish")
            for (kind in kinds) Endpoint { socket, _ ->
                val text = if (kind == "markdown") "**很抱歉**，我不能为你提供这次对话的摘要，因为它触发了内容限制。请修改输入后再试。"
                    else if (kind == "policy") refusal else plausible
                val response = if (protocol == "openai") mapOf("choices" to listOf(mapOf(
                    "finish_reason" to when (kind) { "finish" -> "refusal"; "filter" -> "content_filter"; else -> "stop" },
                    "message" to mapOf("content" to if (kind == "block") listOf(
                        mapOf("type" to "text", "text" to plausible), mapOf("type" to "refusal", "refusal" to refusal)) else text,
                        "refusal" to if (kind == "field") refusal else null))))
                else mapOf("stop_reason" to if (kind == "finish") "refusal" else "end_turn",
                    "content" to listOf(mapOf("type" to "text", "text" to text)))
                reply(socket, Gson().toJson(response))
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                f.settings.setBackupKeyFallbackEnabled(false)
                val (_, id) = f.conversation(endpoint.url, "synthetic-policy-summary-model", protocol, enabled = false)
                val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成政策回归原文[$index]。", createdAt = index.toLong()) })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level,
                    rollingSummary = plausible, summaryUpdatedMessageId = ids[1], summaryUpdatedAt = 419L))
                val before = f.db.conversationDao().getConversationById(id)!!
                val originals = f.db.messageDao().getMessagesList(id)
                val result = f.repository.compressConversationContext(id)
                assertTrue("$protocol/$kind 应明确失败", result.isFailure)
                assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("拒绝生成摘要"))
                assertEquals("拒绝不能继续同请求重放或调用流式", 1, endpoint.payloads.size)
                val after = f.db.conversationDao().getConversationById(id)!!
                assertEquals(before.rollingSummary, after.rollingSummary)
                assertEquals(before.summaryUpdatedMessageId, after.summaryUpdatedMessageId)
                assertEquals(before.summaryUpdatedAt, after.summaryUpdatedAt)
                assertEquals(originals, f.db.messageDao().getMessagesList(id))
                val stat = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).single()
                assertFalse("拒绝不能计为成功摘要调用", stat.success)
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun streamingPolicyAndExplicitRefusalCannotSaveEvenWithPlausiblePartialSummary() = runBlocking {
        val refusal = "由于安全限制，我无法生成本次请求的摘要。请修改提供的内容后重新尝试总结。"
        for (explicit in listOf(false, true)) Endpoint { socket, payload ->
            if (payload.get("stream")?.asBoolean != true) reply(socket, "{}", "405 Method Not Allowed")
            else {
                val delta = if (explicit) mapOf("content" to "合成船队已经抵达北港，下一步核对凭证。", "refusal" to refusal)
                    else mapOf("content" to refusal)
                val event = Gson().toJson(mapOf("choices" to listOf(mapOf("delta" to delta))))
                reply(socket, "data: $event\n\ndata: {\"choices\":[{\"finish_reason\":\"stop\",\"delta\":{}}]}\n\ndata: [DONE]\n\n", type = "text/event-stream")
            }
        }.use { endpoint -> Fixture().use { f ->
            f.settings.setModelRetryEnabled(false)
            val (_, id) = f.conversation(endpoint.url, "synthetic-stream-policy-model", enabled = false)
            val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成流式拒绝原文[$index]。", createdAt = index.toLong()) })
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level,
                rollingSummary = "合成旧摘要仍需完整保留，船队等待核对蓝印通行凭证。", summaryUpdatedMessageId = ids[1], summaryUpdatedAt = 613L))
            val before = f.db.conversationDao().getConversationById(id)!!
            val result = f.repository.compressConversationContext(id)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("拒绝生成摘要"))
            assertEquals(2, endpoint.payloads.size)
            val after = f.db.conversationDao().getConversationById(id)!!
            assertEquals(before.rollingSummary, after.rollingSummary)
            assertEquals(before.summaryUpdatedMessageId, after.summaryUpdatedMessageId)
            assertEquals(before.summaryUpdatedAt, after.summaryUpdatedAt)
            assertEquals(24, f.db.messageDao().getMessagesList(id).size)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun legacyInvalidSummaryNeverReplacesRawHistoryAndCanBeRebuiltFromOriginals() = runBlocking {
        for (refusal in listOf(
            "由于安全政策限制，我无法处理本次摘要请求，也不能提供这段材料的总结。请调整提供的文本后重新尝试。",
            "## 人物身份与关系\n## 世界观与场景设定\n## 已发生的重要事件\n## 当前状态与未完成事项"
        )) {
            val recovered = "合成船队已从北港启程，携带两枚蓝印凭证与铜钥匙，下一步检查 synthetic-recovery.json。"
            var allowSummary = false
            Endpoint { socket, payload ->
                if (payload.get("stream")?.asBoolean == true) streamReply(socket)
                else completedSummaryReply(socket, if (allowSummary) recovered else refusal)
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                val (config, id) = f.conversation(endpoint.url, "synthetic-legacy-refusal-model", enabled = false)
                val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成可恢复原文[$index]。", createdAt = index.toLong()) })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level,
                    rollingSummary = refusal, summaryUpdatedMessageId = ids[21], summaryUpdatedAt = 827L))
                chat(f, config, id)
                val sent = endpoint.payloads.last().toString()
                assertFalse("错误摘要不能进入后续请求", sent.contains(refusal))
                for (index in 1..24) assertTrue("恢复完整未摘要原文[$index]", sent.contains("合成可恢复原文[$index]。"))
                val preserved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(refusal, preserved.rollingSummary)
                assertEquals(ids[21], preserved.summaryUpdatedMessageId)
                assertEquals(827L, preserved.summaryUpdatedAt)
                allowSummary = true
                f.repository.compressConversationContext(id).getOrThrow()
                val summaryPrompt = endpoint.payloads.last().getAsJsonArray("messages").single().asJsonObject.get("content").asString
                assertFalse("重建不能把拒绝文合并为旧摘要", summaryPrompt.contains(refusal))
                assertTrue("必须从最早遗漏的原文开始重建", summaryPrompt.contains("合成可恢复原文[1]。"))
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(recovered, saved.rollingSummary)
                assertNotEquals(827L, saved.summaryUpdatedAt)
                for (index in 1..24) assertTrue(f.db.messageDao().getMessagesList(id).any { it.content == "合成可恢复原文[$index]。" })
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun emptyInvalidAndTruncatedSummaryResponsesFailOnceAndPreservePreviousCheckpointInBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) for (kind in listOf("empty", "invalid", "titles", "truncated")) {
            Endpoint { socket, _ ->
                val text = when (kind) {
                    "empty" -> ""
                    "invalid" -> "抱歉，我无法生成这份摘要，因为本次没有获得能够独立使用的完整上下文，请重新提供全部有效原文之后再试。"
                    "titles" -> "## 人物身份与关系\n## 世界观与场景设定\n## 已发生的重要事件\n## 当前状态与未完成事项"
                    else -> "合成队伍已经核对蓝印凭证并准备出发。这段正文看起来完整，但接口明确声明输出已截断。"
                }
                val response = if (protocol == "openai") mapOf("choices" to listOf(mapOf(
                    "finish_reason" to if (kind == "truncated") "length" else "stop", "message" to mapOf("content" to text))))
                    else mapOf("stop_reason" to if (kind == "truncated") "max_tokens" else "end_turn",
                        "content" to listOf(mapOf("type" to "text", "text" to text)))
                reply(socket, Gson().toJson(response))
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                f.settings.setBackupKeyFallbackEnabled(false)
                val (_, id) = f.conversation(endpoint.url, "synthetic-invalid-summary-model", protocol, enabled = false)
                val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成摘要保留原文[$index]。", createdAt = index.toLong()) })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level,
                    rollingSummary = "上次已确认：合成角色仍保管铜钥匙，不能提前离开北港。", summaryUpdatedMessageId = ids[1], summaryUpdatedAt = 321L))
                val original = f.db.conversationDao().getConversationById(id)!!
                val originals = f.db.messageDao().getMessagesList(id)
                val result = f.repository.compressConversationContext(id)
                assertTrue("$protocol/$kind 必须失败，不能把无效正文当成已压缩", result.isFailure)
                assertEquals("$protocol/$kind 不能再走流式通道或自动重放", 1, endpoint.payloads.size)
                assertFalse(endpoint.payloads.single().get("stream")?.asBoolean == true)
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(original.rollingSummary, saved.rollingSummary)
                assertEquals(original.summaryUpdatedMessageId, saved.summaryUpdatedMessageId)
                assertEquals(original.summaryUpdatedAt, saved.summaryUpdatedAt)
                assertEquals(originals, f.db.messageDao().getMessagesList(id))
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun refusedSummaryConnectionStopsDespiteNetworkRetriesAndBackupKeysAndKeepsCheckpointInBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val url = "http://127.0.0.1:$closedPort/v1"
            HttpTestAccess.allow(url)
            Fixture().use { f ->
                f.settings.setModelRetryEnabled(true)
                f.settings.saveRetryRule(RetryErrorType.NETWORK, RetryRule(true, 20))
                f.settings.setBackupKeyFallbackEnabled(true)
                val (config, id) = f.conversation(url, "synthetic-refused-summary-model", protocol, enabled = false)
                f.db.apiConfigDao().updateConfig(config.copy(apiKey = "test-first\ntest-second\ntest-third"))
                val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成连接拒绝原文[$index]。", createdAt = index.toLong()) })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level,
                    rollingSummary = "此前确认：合成船队仍在北港，蓝印凭证尚未交出。", summaryUpdatedMessageId = ids[1], summaryUpdatedAt = 456L))
                val original = f.db.conversationDao().getConversationById(id)!!
                val originals = f.db.messageDao().getMessagesList(id)
                val started = System.nanoTime()
                val result = withTimeout(10_000) { f.repository.compressConversationContext(id) }
                val elapsedMillis = (System.nanoTime() - started) / 1_000_000
                assertTrue(result.isFailure)
                assertTrue("连接拒绝应及时交还UI，实际${elapsedMillis}ms", elapsedMillis < 5_000)
                val stats = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).filter { it.requestKind == "summary" }
                assertEquals("连接拒绝不能叠加20次重试或备用Key", 1, stats.size)
                assertFalse(stats.single().success)
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(original.rollingSummary, saved.rollingSummary)
                assertEquals(original.summaryUpdatedMessageId, saved.summaryUpdatedMessageId)
                assertEquals(original.summaryUpdatedAt, saved.summaryUpdatedAt)
                assertEquals(originals, f.db.messageDao().getMessagesList(id))
            }
        }
    }

    @Test fun streamOnlySummaryDisconnectIsNotReclassifiedAsEmptyResponseOrReplayedAcrossKeys() = runBlocking {
        Endpoint { socket, payload ->
            if (payload.get("stream")?.asBoolean == true) socket.shutdownOutput()
            else reply(socket, "{\"error\":{\"message\":\"synthetic endpoint only supports streaming\"}}", "405 Method Not Allowed")
        }.use { endpoint -> Fixture().use { f ->
            f.settings.setModelRetryEnabled(true)
            f.settings.saveRetryRule(RetryErrorType.EMPTY_RESPONSE, RetryRule(true, 20))
            f.settings.saveRetryRule(RetryErrorType.NETWORK, RetryRule(true, 20))
            f.settings.setBackupKeyFallbackEnabled(true)
            val (config, id) = f.conversation(endpoint.url, "synthetic-stream-summary-disconnect-model", enabled = false)
            f.db.apiConfigDao().updateConfig(config.copy(apiKey = "test-first\ntest-second\ntest-third"))
            val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成流式摘要断连原文[$index]。", createdAt = index.toLong()) })
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level,
                rollingSummary = "上次确认：合成角色仍在北港，铜钥匙没有转交给他人。", summaryUpdatedMessageId = ids[1], summaryUpdatedAt = 567L))
            val original = f.db.conversationDao().getConversationById(id)!!
            val originals = f.db.messageDao().getMessagesList(id)
            val result = withTimeout(10_000) { f.repository.compressConversationContext(id) }
            assertTrue(result.isFailure)
            assertEquals("仅允许非流式405之后进入一次流式通道，断连不能重放", 2, endpoint.payloads.size)
            assertFalse(endpoint.payloads[0].get("stream")?.asBoolean == true)
            assertTrue(endpoint.payloads[1].get("stream")?.asBoolean == true)
            assertFalse("网络断连必须保留原始错误类别", result.exceptionOrNull()!!.message.orEmpty().contains("模型未返回有效"))
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals(original.rollingSummary, saved.rollingSummary)
            assertEquals(original.summaryUpdatedMessageId, saved.summaryUpdatedMessageId)
            assertEquals(original.summaryUpdatedAt, saved.summaryUpdatedAt)
            assertEquals(originals, f.db.messageDao().getMessagesList(id))
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun streamOnlySummaryCapacityErrorsSplitFailedSourceWithoutReplayingItsBodyOrDroppingText() = runBlocking {
        val summary = "合成领航员仍在北港保管铜钥匙，蓝印凭证已经核对；此前各段通行经过均已记录，接下来等待确认新航线。"
        for (failureKind in listOf("http413", "http400", "sse200")) {
            val longSourceId = java.util.concurrent.atomic.AtomicLong()
            val failedPrompt = java.util.concurrent.atomic.AtomicReference<String>()
            val successfulPrompts = CopyOnWriteArrayList<String>()
            Endpoint { socket, payload ->
                val prompt = payload.getAsJsonArray("messages").single().asJsonObject.get("content").asString
                if (payload.get("stream")?.asBoolean != true) {
                    reply(socket, "{\"error\":{\"message\":\"synthetic endpoint only supports streaming\"}}", "405 Method Not Allowed")
                } else {
                    val sources = sourceFragmentsFromSummaryPrompt(prompt)
                    if (sources.any { it.first == longSourceId.get() }) failedPrompt.compareAndSet(null, prompt)
                    if (prompt == failedPrompt.get()) {
                        when (failureKind) {
                            // No textual context hint: the HTTP status itself must trigger splitting.
                            "http413" -> reply(socket, "{\"error\":{\"message\":\"synthetic upload rejected\"}}", "413 Payload Too Large")
                            "http400" -> reply(socket,
                                "{\"error\":{\"type\":\"context_length_exceeded\",\"message\":\"context_length_exceeded: input too long\"}}", "400 Bad Request")
                            else -> reply(socket,
                                "data: {\"error\":{\"type\":\"context_length_exceeded\",\"message\":\"context_length_exceeded: input too long\"}}\n\n",
                                type = "text/event-stream")
                        }
                    } else {
                        successfulPrompts.add(prompt)
                        val content = Gson().toJson(summary)
                        reply(socket,
                            "data: {\"choices\":[{\"delta\":{\"content\":$content},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                            type = "text/event-stream")
                    }
                }
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(true)
                listOf(RetryErrorType.BAD_REQUEST, RetryErrorType.TOO_LARGE, RetryErrorType.EMPTY_RESPONSE,
                    RetryErrorType.OTHER_HTTP, RetryErrorType.OTHER).forEach { f.settings.saveRetryRule(it, RetryRule(true, 20)) }
                f.settings.setBackupKeyFallbackEnabled(true)
                val (config, id) = f.conversation(endpoint.url, "synthetic-stream-capacity-model", enabled = false)
                f.db.apiConfigDao().updateConfig(config.copy(apiKey = "test-first\ntest-second\ntest-third"))
                val contents = (1..44).map { index ->
                    if (index == 41) "流式长原文首段：蓝印凭证已核对。\n" +
                        "流式中段合成事实：铜钥匙仍由北港领航员保管。".repeat(800) +
                        "\n流式长原文末段：等待确认 synthetic-stream-route.json"
                    else "流式容量回归合成原文[$index]：核对北港通行记录。"
                }
                val ids = f.db.messageDao().insertMessages(contents.mapIndexed { index, content -> Message(conversationId = id,
                    role = if (index % 2 == 0) "user" else "assistant", content = content, createdAt = index.toLong()) })
                longSourceId.set(ids[40])
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level))
                val originals = f.db.messageDao().getMessagesList(id)
                val result = withTimeout(20_000) { f.repository.compressConversationContext(id).getOrThrow() }
                assertNotNull("$failureKind 必须真实经过容量错误", failedPrompt.get())
                val streams = endpoint.payloads.filter { it.get("stream")?.asBoolean == true }
                val streamPrompts = streams.map { it.getAsJsonArray("messages").single().asJsonObject.get("content").asString }
                assertEquals("$failureKind 不能对同一失败输入叠加20次重试或备用Key", 1, streamPrompts.count { it == failedPrompt.get() })
                assertEquals("每个逻辑请求只允许一次405探测和一次流式调用", streams.size * 2, endpoint.payloads.size)
                val failureIndex = streamPrompts.indexOf(failedPrompt.get())
                assertTrue("应先成功整理早期分段，再拒绝长来源片段", failureIndex > 0)
                val failedSources = sourceFragmentsFromSummaryPrompt(failedPrompt.get())
                val nextSources = sourceFragmentsFromSummaryPrompt(streamPrompts[failureIndex + 1])
                assertEquals("容量错误只重排失败的来源片段", ids[40], nextSources.first().first)
                assertTrue("$failureKind 必须缩小失败输入", nextSources.sumOf { it.second.length } < failedSources.sumOf { it.second.length })
                val reconstructed = linkedMapOf<Long, StringBuilder>()
                val sourceOrder = mutableListOf<Long>()
                successfulPrompts.forEach { prompt ->
                    sourceFragmentsFromSummaryPrompt(prompt).forEach { (sourceId, content) ->
                        reconstructed.getOrPut(sourceId) { StringBuilder() }.append(content)
                        if (sourceOrder.lastOrNull() != sourceId) sourceOrder += sourceId
                    }
                }
                assertEquals("失败片段拆分不能回到已成功的早期原文", ids.take(42), sourceOrder)
                assertEquals(ids.take(42), reconstructed.keys.toList())
                ids.take(42).forEachIndexed { index, sourceId ->
                    assertEquals("$failureKind 原文 $sourceId 必须逐字完整进入成功请求", contents[index], reconstructed.getValue(sourceId).toString())
                }
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(summary, saved.rollingSummary)
                assertEquals(ids[41], saved.summaryUpdatedMessageId)
                assertEquals(2, result.recentMessageCount)
                assertEquals(originals, f.db.messageDao().getMessagesList(id))
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun connectedButSilentSummaryUsesRealResponseTimeoutWithoutRetryingOrChangingCheckpoint() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Endpoint { _, _ ->
            entered.countDown()
            release.await(50, TimeUnit.SECONDS)
        }.use { endpoint -> Fixture().use { f ->
            try {
                f.settings.setModelRetryEnabled(true)
                f.settings.saveRetryRule(RetryErrorType.TIMEOUT, RetryRule(true, 20))
                f.settings.saveRetryRule(RetryErrorType.NETWORK, RetryRule(true, 20))
                f.settings.setBackupKeyFallbackEnabled(true)
                val (config, id) = f.conversation(endpoint.url, "synthetic-silent-summary-model", enabled = false)
                f.db.apiConfigDao().updateConfig(config.copy(apiKey = "test-first\ntest-second\ntest-third"))
                val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成无响应摘要原文[$index]。", createdAt = index.toLong()) })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level,
                    rollingSummary = "此前确认：合成队伍已经回到北港，仍须保管蓝印凭证。", summaryUpdatedMessageId = ids[1], summaryUpdatedAt = 678L))
                val original = f.db.conversationDao().getConversationById(id)!!
                val originals = f.db.messageDao().getMessagesList(id)
                val started = System.nanoTime()
                val result = withTimeout(40_000) { f.repository.compressConversationContext(id) }
                val elapsedMillis = (System.nanoTime() - started) / 1_000_000
                assertEquals("必须建立真实HTTP连接后等待服务端无响应", 0L, entered.count)
                assertTrue(result.isFailure)
                assertTrue("实际读超时不能退回600秒通道，耗时${elapsedMillis}ms", elapsedMillis in 25_000L..39_999L)
                assertEquals("读超时不能叠加20次重试或备用Key", 1, endpoint.payloads.size)
                val stats = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).filter { it.requestKind == "summary" }
                assertEquals(1, stats.size)
                assertFalse(stats.single().success)
                assertEquals("超时应作为失败反馈，不能误标为用户取消", "failed", stats.single().completionStatus)
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(original.rollingSummary, saved.rollingSummary)
                assertEquals(original.summaryUpdatedMessageId, saved.summaryUpdatedMessageId)
                assertEquals(original.summaryUpdatedAt, saved.summaryUpdatedAt)
                assertEquals(originals, f.db.messageDao().getMessagesList(id))
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } finally {
                release.countDown()
            }
        } }
    }

    @Test fun failedManualSummaryAllowsTwoCompleteChatsAndSameTierManualRecoveryInBothProtocols() = runBlocking {
        val previous = "【核心背景与用户固定约束】\n旧摘要唯一事实：合成船队携带蓝印凭证。\n【历史关键里程碑与决策推进】\n已核对最初两条合成记录。\n【当前未决议题与待办上下文】\n等待继续整理。"
        val recovered = "【核心背景与用户固定约束】\n恢复摘要唯一事实：合成船队仍携带蓝印凭证。\n【历史关键里程碑与决策推进】\n已补齐此前未覆盖的合成通行经过。\n【当前未决议题与待办上下文】\n等待下一轮确认。"
        for (protocol in listOf("openai", "anthropic")) {
            val serviceRecovered = java.util.concurrent.atomic.AtomicBoolean(false)
            Endpoint { socket, payload ->
                if (isSummaryPayload(payload)) {
                    if (serviceRecovered.get()) completedSummaryReply(socket, recovered, protocol)
                    else reply(socket, "{\"error\":{\"message\":\"method not allowed\",\"type\":\"invalid_request_error\"}}", "405 Method Not Allowed")
                } else if (protocol == "openai") streamReply(socket) else reply(socket,
                    "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\n" +
                        "data: {\"type\":\"message_stop\"}\n\n", type = "text/event-stream")
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                f.settings.setBackupKeyFallbackEnabled(false)
                val (config, id) = f.conversation(endpoint.url, "synthetic-manual-summary-recovery-model", protocol, enabled = false)
                val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "待覆盖合成原文[$index]：核对通行记录。", createdAt = index.toLong()) })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, maxTokens = 512, compressionTier = CompressionTier.LC.level,
                    compressionCustomPercent = 30, rollingSummary = previous, summaryUpdatedMessageId = ids[1], summaryUpdatedAt = 123L))
                val original = f.db.conversationDao().getConversationById(id)!!
                val originals = f.db.messageDao().getMessagesList(id)
                assertTrue(f.repository.compressConversationContext(id).isFailure)
                val failedRequests = endpoint.payloads.filter(::isSummaryPayload)
                assertEquals("只允许OpenAI额外尝试一次兼容流式，不能普通重试", if (protocol == "openai") 2 else 1, failedRequests.size)
                assertEquals(originals, f.db.messageDao().getMessagesList(id))

                for (round in 1..2) {
                    val current = "合成失败后继续发送第${round}轮"
                    assertNull("摘要服务失败不能阻断预算内的完整正式对话", chatWithSavedUser(f, config, id, current))
                    val request = endpoint.payloads.last()
                    assertTrue(request.get("stream").asBoolean)
                    assertFalse(isSummaryPayload(request))
                    val body = request.toString()
                    assertTrue("旧摘要必须继续注入", body.contains("旧摘要唯一事实"))
                    originals.drop(2).forEach { source -> assertTrue("遗漏未覆盖原文 ${source.id}", body.contains(source.content)) }
                    assertFalse("已有摘要覆盖且不在原文窗口中的早期消息无需重发", body.contains(originals.first().content))
                    assertEquals("当前用户消息只能发送一次", 1, request.getAsJsonArray("messages").count {
                        it.asJsonObject.get("content").toString().contains(current)
                    })
                    assertEquals("后续发送不能再次调用同一失败摘要服务", failedRequests.size, endpoint.payloads.count(::isSummaryPayload))
                    val saved = f.db.conversationDao().getConversationById(id)!!
                    assertEquals(original.rollingSummary, saved.rollingSummary)
                    assertEquals(original.summaryUpdatedMessageId, saved.summaryUpdatedMessageId)
                    assertEquals(original.summaryUpdatedAt, saved.summaryUpdatedAt)
                    assertEquals(original.compressionTier, saved.compressionTier)
                    assertEquals(original.compressionCustomPercent, saved.compressionCustomPercent)
                    assertEquals(originals, f.db.messageDao().getMessagesList(id).take(originals.size))
                }
                val chats = endpoint.payloads.filterNot(::isSummaryPayload)
                assertEquals(2, chats.size)
                assertTrue("第二轮需包含第一轮已保存的用户内容", chats[1].toString().contains("合成失败后继续发送第1轮"))
                assertEquals(28, f.db.messageDao().getMessagesList(id).size)

                serviceRecovered.set(true)
                val resumed = f.repository.compressConversationContext(id).getOrThrow()
                assertEquals(CompressionTier.LC, resumed.compressionTier)
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(recovered, saved.rollingSummary)
                assertNotEquals(original.summaryUpdatedMessageId, saved.summaryUpdatedMessageId)
                assertEquals(original.compressionCustomPercent, saved.compressionCustomPercent)
                assertEquals("无需更改档位或比例即可手动重试", failedRequests.size + 1, endpoint.payloads.count(::isSummaryPayload))
                assertNull(chatWithSavedUser(f, config, id, "合成恢复后的正式发送"))
                val next = endpoint.payloads.last().toString()
                assertTrue(next.contains("恢复摘要唯一事实"))
                assertFalse("成功摘要覆盖的旧原文不能再占正式请求", next.contains(originals[2].content))
                assertTrue(next.contains(originals.last().content))
                assertEquals("成功后本轮不应再次补摘要", failedRequests.size + 1, endpoint.payloads.count(::isSummaryPayload))
                assertEquals(originals, f.db.messageDao().getMessagesList(id).take(originals.size))
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun firstAutomaticSummaryFailureKeepsEveryUncoveredSourceAndDoesNotBlockFollowingChatsInBothProtocols() = runBlocking {
        val previous = "旧自动摘要唯一事实：合成领航员保管蓝印凭证，此前两条记录已核对。"
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, payload ->
                if (isSummaryPayload(payload)) reply(socket,
                    "{\"error\":{\"message\":\"synthetic summary service unavailable\",\"type\":\"internal_server_error\"}}", "500 Internal Server Error")
                else if (protocol == "openai") streamReply(socket) else reply(socket,
                    "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\n" +
                        "data: {\"type\":\"message_stop\"}\n\n", type = "text/event-stream")
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                f.settings.setBackupKeyFallbackEnabled(false)
                val (config, id) = f.conversation(endpoint.url, "synthetic-auto-summary-failure-model", protocol, enabled = false)
                val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "自动回退完整合成原文[$index]。", createdAt = index.toLong()) })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, maxTokens = 512, compressionTier = CompressionTier.L4.level,
                    rollingSummary = previous, summaryUpdatedMessageId = ids[1], summaryUpdatedAt = 123L))
                val original = f.db.conversationDao().getConversationById(id)!!
                val originals = f.db.messageDao().getMessagesList(id)
                for (round in 1..2) {
                    assertNull(chatWithSavedUser(f, config, id, "合成自动回退第${round}轮"))
                    assertEquals("首次失败只调用一次，之后不再重复失败摘要", 1, endpoint.payloads.count(::isSummaryPayload))
                    val request = endpoint.payloads.last()
                    assertTrue(request.get("stream").asBoolean)
                    assertTrue(request.toString().contains(previous))
                    originals.drop(2).forEach { source ->
                        assertTrue("$protocol/$round 必须完整恢复未摘要来源 ${source.id}", request.toString().contains(source.content))
                    }
                    val saved = f.db.conversationDao().getConversationById(id)!!
                    assertEquals(original.rollingSummary, saved.rollingSummary)
                    assertEquals(original.summaryUpdatedMessageId, saved.summaryUpdatedMessageId)
                    assertEquals(original.summaryUpdatedAt, saved.summaryUpdatedAt)
                    assertEquals(original.compressionTier, saved.compressionTier)
                    assertEquals(originals, f.db.messageDao().getMessagesList(id).take(originals.size))
                }
                assertEquals(2, endpoint.payloads.count { !isSummaryPayload(it) && it.get("stream")?.asBoolean == true })
                assertEquals(28, f.db.messageDao().getMessagesList(id).size)
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun failedAutomaticSummaryCannotSilentlyDropOverBudgetSourcesOrRepeatItsFailureInBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, _ -> reply(socket,
                "{\"error\":{\"message\":\"synthetic summary service unavailable\",\"type\":\"internal_server_error\"}}", "500 Internal Server Error")
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                f.settings.setBackupKeyFallbackEnabled(false)
                val (config, id) = f.conversation(endpoint.url, "synthetic-no-room-after-failed-summary-model", protocol, enabled = false)
                val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", createdAt = index.toLong(),
                    content = if (index <= 22) "超预算完整合成记录[$index]：" + "合成跨窗事实仍需保留。".repeat(60)
                    else "最新合成原文[$index]。") })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 4000, maxTokens = 512, compressionTier = CompressionTier.L4.level,
                    rollingSummary = "旧超限摘要唯一事实：前两条合成记录已经核对。", summaryUpdatedMessageId = ids[1], summaryUpdatedAt = 123L))
                val original = f.db.conversationDao().getConversationById(id)!!
                val originals = f.db.messageDao().getMessagesList(id)
                assertTrue("回归前提：完整恢复所有未覆盖来源会超过4K输入预算",
                    originals.drop(2).sumOf { AiRepository.estimateTokenCount(it.content) + 24 } > 4000)
                for (round in 1..2) {
                    val error = chatWithSavedUser(f, config, id, "合成超限失败第${round}轮")
                    assertNotNull("不能丢弃来源来伪装正式发送成功", error)
                    assertTrue("应提供同档位手动重试入口的提示：$error", error!!.contains("重试压缩"))
                    if (round == 2) {
                        assertTrue("后续发送应明确说明上次摘要失败：$error", error.contains("上次摘要失败"))
                        assertTrue("冷却状态仍须明确告知，不能静默绕过：$error", error.contains("冷却"))
                    }
                    assertEquals("同一失败摘要服务不能在每轮再次调用", 1, endpoint.payloads.size)
                    assertTrue("预算放不下完整原文时不能发送正式聊天", endpoint.payloads.all(::isSummaryPayload))
                    val saved = f.db.conversationDao().getConversationById(id)!!
                    assertEquals(original.rollingSummary, saved.rollingSummary)
                    assertEquals(original.summaryUpdatedMessageId, saved.summaryUpdatedMessageId)
                    assertEquals(original.summaryUpdatedAt, saved.summaryUpdatedAt)
                    assertEquals(original.compressionTier, saved.compressionTier)
                    assertEquals(originals, f.db.messageDao().getMessagesList(id).take(originals.size))
                    assertEquals("失败仅保留UI已保存的当前输入，不产生助手伪回复", originals.size + round, f.db.messageDao().getMessagesList(id).size)
                }
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun explicitEmptyResponseServerErrorAllowsOneShortOpenAiStreamAndCommitsOnlyCompletedSummary() = runBlocking {
        val summary = "合成兼容恢复事实：北港领航员仍保管蓝印凭证，旧航程已经核对，接下来检查新的合成路线。"
        Endpoint { socket, payload ->
            if (payload.get("stream")?.asBoolean != true) reply(socket,
                "{\"error\":{\"message\":\"empty response detected\",\"type\":\"internal_server_error\"}}", "500 Internal Server Error")
            else reply(socket,
                "data: {\"choices\":[{\"delta\":{\"content\":${Gson().toJson(summary)}},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                type = "text/event-stream")
        }.use { endpoint -> Fixture().use { f ->
            f.settings.setModelRetryEnabled(false)
            f.settings.setBackupKeyFallbackEnabled(false)
            val (_, id) = f.conversation(endpoint.url, "synthetic-empty-server-stream-recovery-model", enabled = false)
            val ids = f.db.messageDao().insertMessages((1..24).map { index -> Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成兼容回归原文[$index]。", createdAt = index.toLong()) })
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level))
            val originals = f.db.messageDao().getMessagesList(id)
            f.repository.compressConversationContext(id).getOrThrow()
            assertEquals("只允许非流式一次和兼容流式一次", 2, endpoint.payloads.size)
            assertFalse(endpoint.payloads[0].get("stream")?.asBoolean == true)
            assertTrue(endpoint.payloads[1].get("stream").asBoolean)
            assertEquals("兼容通道必须处理完全相同的来源", endpoint.payloads[0].getAsJsonArray("messages"), endpoint.payloads[1].getAsJsonArray("messages"))
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals(summary, saved.rollingSummary)
            assertEquals(ids[21], saved.summaryUpdatedMessageId)
            assertEquals(originals, f.db.messageDao().getMessagesList(id))
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun longEmptyGatewayErrorsCacheStreamingPerActualBackupKeyAcrossEverySourceChunk() = runBlocking {
        val summary = "合成备用通道事实：领航员已经核对北港蓝印凭证，各段合成通行记录均已整理，下一步等待核对新的合成路线。"
        for (status in listOf("500 Internal Server Error", "502 Bad Gateway")) {
            val attempts = CopyOnWriteArrayList<Triple<String, Boolean, String>>()
            val successfulPrompts = CopyOnWriteArrayList<String>()
            lateinit var endpoint: Endpoint
            endpoint = Endpoint { socket, payload ->
                val authorization = endpoint.authorizations[socket].orEmpty()
                val stream = payload.get("stream")?.asBoolean == true
                val prompt = payload.getAsJsonArray("messages").single().asJsonObject.get("content").asString
                attempts += Triple(authorization, stream, prompt)
                when {
                    authorization == "Bearer test-first" -> reply(socket,
                        "{\"error\":{\"message\":\"synthetic first credential rejected\",\"type\":\"authentication_error\"}}", "401 Unauthorized")
                    authorization != "Bearer test-second" -> reply(socket,
                        "{\"error\":{\"message\":\"unexpected synthetic credential\"}}", "401 Unauthorized")
                    !stream -> reply(socket, Gson().toJson(mapOf("error" to mapOf(
                        "message" to "empty response detected " + "x".repeat(400), "type" to "internal_server_error"))), status)
                    else -> {
                        successfulPrompts += prompt
                        reply(socket,
                            "data: {\"choices\":[{\"delta\":{\"content\":${Gson().toJson(summary)}},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                            type = "text/event-stream")
                    }
                }
            }
            endpoint.use { Fixture().use { f ->
                // The global switch also gates backup credentials. Disable each
                // retry rule while leaving backup-key failover itself enabled.
                f.settings.setModelRetryEnabled(true)
                RetryErrorType.entries.forEach { f.settings.saveRetryRule(it, RetryRule(false, 0)) }
                f.settings.setBackupKeyFallbackEnabled(true)
                assertTrue("回归前提：备用凭据回退必须实际开启", f.settings.isBackupKeyFallbackEnabled())
                val (config, id) = f.conversation(endpoint.url, "synthetic-backup-stream-cache-model", enabled = false)
                f.db.apiConfigDao().updateConfig(config.copy(apiKey = "Bearer test-first\nBearer test-second"))
                val ids = f.db.messageDao().insertMessages((1..46).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "备用流式缓存合成原文[$index]：核对通行凭证。", createdAt = index.toLong()) })
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level))
                val originals = f.db.messageDao().getMessagesList(id)
                f.repository.compressConversationContext(id).getOrThrow()
                assertEquals("44条来源应按40条上限分成两块", 2, successfulPrompts.size)
                assertEquals("备用Key的后续分块必须直接使用已验证的流式通道", 1,
                    attempts.count { it.first == "Bearer test-second" && !it.second })
                assertEquals("首Key鉴权失败一次后应被冷却，后续分块不能重复请求坏Key", 1,
                    attempts.count { it.first == "Bearer test-first" && !it.second })
                assertEquals("首Key没有流式成功证据，不能继承备用Key的流式能力", 0,
                    attempts.count { it.first == "Bearer test-first" && it.second })
                assertEquals(2, attempts.count { it.first == "Bearer test-second" && it.second })
                assertEquals("一次401、一次网关探测、两次成功流式，不得重放额外请求", 4, attempts.size)
                assertEquals("首次探测与兼容流式必须处理同一份完整来源", attempts[1].third, attempts[2].third)
                val reconstructed = successfulPrompts.flatMap(::sourceFragmentsFromSummaryPrompt)
                assertEquals("所有来源仅在成功请求中覆盖一次，顺序不得改变", ids.take(44), reconstructed.map { it.first })
                assertEquals("分块不能丢失、裁剪或重复原文", originals.take(44).map { it.content }, reconstructed.map { it.second })
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(summary, saved.rollingSummary)
                assertEquals(ids[43], saved.summaryUpdatedMessageId)
                assertEquals(originals, f.db.messageDao().getMessagesList(id))
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun oversizedCompleteSummaryGetsOnlyOneSemanticRewriteBeforeCommittingCoverage() = runBlocking {
        val oversized = "【核心背景与用户固定约束】\n" + "合成事实必须保留蓝印凭证。".repeat(70) +
            "\n【历史关键里程碑与决策推进】\n合成船队已到北港。\n【当前未决议题与待办上下文】\n核对 synthetic-rewrite.json"
        val shortened = "【核心背景与用户固定约束】\n合成船队必须保留蓝印凭证。\n【历史关键里程碑与决策推进】\n已到北港。\n【当前未决议题与待办上下文】\n核对 synthetic-rewrite.json"
        assertTrue("回归前提：首次完整摘要大于 4K 窗口的 360-token 摘要预算", AiRepository.estimateTokenCount(oversized) > 360)
        for (protocol in listOf("openai", "anthropic")) for (rewriteSucceeds in listOf(true, false)) {
            val attempts = AtomicInteger()
            Endpoint { socket, _ ->
                completedSummaryReply(socket, if (attempts.incrementAndGet() == 1 || !rewriteSucceeds) oversized else shortened, protocol)
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                f.settings.setBackupKeyFallbackEnabled(false)
                val (_, id) = f.conversation(endpoint.url, "synthetic-bounded-rewrite-model", protocol, enabled = false)
                val ids = (1..4).map { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成重写回归原文 $index。", createdAt = index.toLong())) }
                val previous = "上次已经确认的合成背景。"
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 4000, rollingSummary = previous, summaryUpdatedMessageId = ids[1]))
                val result = f.repository.generateRollingSummaryNow(id)
                assertEquals("完整但超预算只允许一次语义缩短请求", 2, endpoint.payloads.size)
                val rewritePrompt = endpoint.payloads[1].getAsJsonArray("messages").single().asJsonObject.get("content").asString
                assertTrue("应把完整的首次摘要交给模型重新组织", rewritePrompt.contains(oversized))
                assertTrue(rewritePrompt.contains("没有新增原文"))
                for (payload in endpoint.payloads) {
                    val prompt = payload.getAsJsonArray("messages").single().asJsonObject.get("content").asString
                    val outputTokens = (payload.get("max_tokens") ?: payload.get("max_completion_tokens")).asInt
                    assertTrue(AiRepository.estimateTokenCount(prompt) + outputTokens <= 4000 - 512)
                }
                val saved = f.db.conversationDao().getConversationById(id)!!
                if (rewriteSucceeds) {
                    result.getOrThrow()
                    assertEquals(shortened, saved.rollingSummary)
                    assertEquals(ids.last(), saved.summaryUpdatedMessageId)
                } else {
                    assertTrue("第二次仍超预算必须明确失败", result.isFailure)
                    assertEquals(previous, saved.rollingSummary)
                    assertEquals(ids[1], saved.summaryUpdatedMessageId)
                }
                assertEquals(4, f.db.messageDao().getMessagesList(id).size)
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun completeSummaryWithinFirstOutputAllowanceCanReachSemanticRewriteInSmallWindow() = runBlocking {
        val oversized = (0 until 250).joinToString("") { "record-${it.toString().padStart(3, '0')} done. " }
        val shortened = "Records 000 through 249 are verified complete. Pending: check synthetic-headroom-manifest.json."
        // Synthetic endpoint usage is an independent wire value, not a claim about any provider's tokenizer.
        val firstCompletionTokens = 1063
        val attempts = AtomicInteger()
        Endpoint { socket, _ ->
            if (attempts.incrementAndGet() == 1) {
                reply(socket, Gson().toJson(mapOf(
                    "choices" to listOf(mapOf("finish_reason" to "stop", "message" to mapOf("content" to oversized))),
                    "usage" to mapOf("completion_tokens" to firstCompletionTokens)
                )))
            } else {
                completedSummaryReply(socket, shortened)
            }
        }.use { endpoint -> Fixture().use { f ->
            f.settings.setModelRetryEnabled(false)
            f.settings.setBackupKeyFallbackEnabled(false)
            val (_, id) = f.conversation(endpoint.url, "synthetic-small-window-rewrite-model", enabled = false)
            val ids = (1..4).map { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成缩短预算回归原文 $index。", createdAt = index.toLong())) }
            val previous = "上次已经确认的合成背景。"
            val window = 4000
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = window, rollingSummary = previous, summaryUpdatedMessageId = ids[1]))
            val originals = f.db.messageDao().getMessagesList(id)

            val result = f.repository.generateRollingSummaryNow(id)

            assertTrue("首轮完整请求必须确实到达 HTTP 服务", endpoint.payloads.isNotEmpty())
            val firstRequest = endpoint.payloads.first()
            val firstOutputAllowance = (firstRequest.get("max_tokens") ?: firstRequest.get("max_completion_tokens")).asInt
            assertTrue("首轮完整响应的合成 usage 不得超过实际请求的输出额度", firstCompletionTokens <= firstOutputAllowance)
            val firstPrompt = firstRequest.getAsJsonArray("messages").single().asJsonObject.get("content").asString
            assertTrue(firstPrompt.contains(previous))
            assertEquals(ids.drop(2), sourceFragmentsFromSummaryPrompt(firstPrompt).map { it.first })
            assertEquals("首轮完整成功后，超保存预算的摘要应能进入一次真正的远端语义缩短；${result.exceptionOrNull()?.message}",
                2, endpoint.payloads.size)
            val rewritePrompt = endpoint.payloads[1].getAsJsonArray("messages").single().asJsonObject.get("content").asString
            assertTrue("缩短请求必须包含首轮的完整正文，不能先在本地裁切", rewritePrompt.contains(oversized.trim()))
            for (payload in endpoint.payloads) {
                val prompt = payload.getAsJsonArray("messages").single().asJsonObject.get("content").asString
                val outputTokens = (payload.get("max_tokens") ?: payload.get("max_completion_tokens")).asInt
                assertTrue("实际发出的提示、安全余量与输出预算必须适配会话窗口",
                    ContextCompressionPlanner.summaryInputTokens(prompt) + outputTokens <= window - 512)
            }
            result.getOrThrow()
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals("必须精确保存第二次 HTTP 返回的摘要", shortened, saved.rollingSummary)
            assertEquals("全部待处理原文成功后才推进覆盖水线", ids.last(), saved.summaryUpdatedMessageId)
            assertEquals("语义缩短不能改写或删除任何原文", originals, f.db.messageDao().getMessagesList(id))
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun fullHistorySummaryReusesCoverageWhileRawRetentionFollowsSelectedTier() = runBlocking {
        val summary = "【核心背景与用户固定约束】\n合成船队遵守蓝印通行规则。\n【历史关键里程碑与决策推进】\n已整理全部合成航程。\n【当前未决议题与待办上下文】\n核对 synthetic-complete.json"
        Endpoint { socket, _ -> completedSummaryReply(socket, summary) }.use { endpoint -> Fixture().use { f ->
            val (_, id) = f.conversation(endpoint.url, "synthetic-full-coverage-model", enabled = false)
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = 32768, compressionTier = CompressionTier.L4.level))
            val ids = (1..24).map { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成完整覆盖原文 $index。", createdAt = index.toLong())) }
            val result = f.repository.generateRollingSummaryNow(id).getOrThrow()
            assertEquals(ids.last(), f.db.conversationDao().getConversationById(id)!!.summaryUpdatedMessageId)
            assertEquals("水位在完整历史末尾时，较早窗口中的消息也全部已覆盖", 0, result.olderMessageCount)
            assertFalse(result.canCompress)
            val snapshot = f.repository.getConversationContextUsage(id)
            assertEquals(0, snapshot.olderMessageCount)
            assertFalse(snapshot.canCompress)
            f.repository.generateRollingSummaryNow(id).getOrThrow()
            assertEquals("没有新增原文不能重复付费整理", 1, endpoint.payloads.size)
            f.repository.updateConversationCompressionTier(id, CompressionTier.L0, 8)
            val restored = f.repository.getConversationContextUsage(id)
            assertEquals("完整摘要不应阻止L0恢复原文", 24, restored.recentMessageCount)
            assertEquals(8, restored.olderMessageCount)
            assertTrue(restored.canCompress)
            val retired = f.repository.compressConversationContext(id).getOrThrow()
            assertEquals("显式手动压缩须同步采用滚动摘要策略", CompressionTier.L2, retired.compressionTier)
            assertEquals(16, retired.recentMessageCount)
            assertEquals(0, retired.olderMessageCount)
            assertFalse(retired.canCompress)
            assertEquals("已有完整摘要可以直接复用，无需再次付费", 1, endpoint.payloads.size)
            assertEquals(24, f.db.messageDao().getMessagesList(id).size)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun queuedPartialCompressionReusesCompletedFullHistorySummaryWithoutRegressingCoverage() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val partialReachedLock = CountDownLatch(1)
        val summary = "【核心背景与用户固定约束】\n合成船队必须保留蓝印凭证。\n【历史关键里程碑与决策推进】\n已整理全部二十四条合成原文。\n【当前未决议题与待办上下文】\n核对 synthetic-queued-summary.json"
        Endpoint { socket, _ ->
            entered.countDown()
            check(released.await(10, TimeUnit.SECONDS))
            completedSummaryReply(socket, summary)
        }.use { endpoint -> Fixture().use { f ->
            val (_, id) = f.conversation(endpoint.url, "synthetic-queued-summary-model", enabled = false)
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(contextWindowTokens = 32768))
            val ids = (1..24).map { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成并发摘要原文 $index。", createdAt = index.toLong())) }
            // Observe the actual lock boundary after planning, without sleeps or coroutine internals.
            val underlying = kotlinx.coroutines.sync.Mutex()
            val arrivals = AtomicInteger()
            val observed = object : kotlinx.coroutines.sync.Mutex by underlying {
                override suspend fun lock(owner: Any?) {
                    if (arrivals.incrementAndGet() == 2) partialReachedLock.countDown()
                    underlying.lock(owner)
                }
            }
            val locksField = AiRepository::class.java.getDeclaredField("summaryLocks").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val locks = locksField.get(f.repository) as MutableMap<Long, kotlinx.coroutines.sync.Mutex>
            locks[id] = observed
            val full = async(Dispatchers.IO) { f.repository.generateRollingSummaryNow(id) }
            var partial: Deferred<Result<ConversationContextUsage>>? = null
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val queued = async(Dispatchers.IO) { f.repository.compressConversationContext(id) }
                partial = queued
                assertTrue("部分压缩必须先按旧快照规划并到达同一会话锁", partialReachedLock.await(5, TimeUnit.SECONDS))
                assertTrue(queued.isActive)
                assertEquals(1, endpoint.payloads.size)
                released.countDown()
                withTimeout(5000) { full.await() }.getOrThrow()
                val result = withTimeout(5000) { queued.await() }.getOrThrow()
                assertEquals("排队的较小范围应复用已完成的全历史摘要", 1, endpoint.payloads.size)
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(summary, saved.rollingSummary)
                assertEquals("覆盖水位不能从末条回退到前八条", ids.last(), saved.summaryUpdatedMessageId)
                assertEquals(0, result.olderMessageCount)
                assertFalse(result.canCompress)
                assertEquals(24, f.db.messageDao().getMessagesList(id).size)
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } finally {
                released.countDown()
                full.cancelAndJoin()
                partial?.cancelAndJoin()
            }
        } }
    }

    @Test fun completedSummaryKeepsUnpunctuatedFinalTodoAndInjectsItIntoNextRequest() = runBlocking {
        val finalTodo = "待办：核对 synthetic-manifest.json"
        val summary = "【核心背景与用户固定约束】\n合成船队必须保留两枚蓝印。\n【历史关键里程碑与决策推进】\n已核对港口通行凭证。\n【当前未决议题与待办上下文】\n$finalTodo"
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, payload ->
                if (payload.get("stream")?.asBoolean == true) {
                    if (protocol == "openai") streamReply(socket) else reply(socket,
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\n" +
                            "data: {\"type\":\"message_stop\"}\n\n", type = "text/event-stream")
                } else completedSummaryReply(socket, summary, protocol)
            }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url, "synthetic-completed-summary-model", protocol, enabled = false)
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(contextWindowTokens = 32768))
                (1..24).forEach { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成原文 $index。", createdAt = index.toLong())) }
                f.repository.compressConversationContext(id).getOrThrow()
                assertEquals("接口已明确正常结束，不能按句末标点删除合法待办", summary,
                    f.db.conversationDao().getConversationById(id)!!.rollingSummary)
                chat(f, config, id)
                val payload = endpoint.payloads.last()
                val systemPrompt = if (protocol == "anthropic") payload.get("system").asString else
                    payload.getAsJsonArray("messages").first().asJsonObject.get("content").asString
                assertTrue(systemPrompt.contains(summary))
                assertTrue(systemPrompt.contains(finalTodo))
                assertEquals("已有摘要的发送不能再付费整理一次", 1,
                    endpoint.payloads.count { it.get("stream")?.asBoolean != true })
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun regeneratedEarlyReplyUsesHistoryPositionForIncrementalCoverage() = runBlocking {
        val updatedSummary = "【核心背景与用户固定约束】\n已保留合成首轮重生成回复的决定。\n【历史关键里程碑与决策推进】\n已合并后续低编号原文中的三次合成确认。\n【当前未决议题与待办上下文】\n仍需核对 synthetic-route.json"
        Endpoint { socket, payload ->
            if (payload.get("stream")?.asBoolean == true) streamReply(socket) else completedSummaryReply(socket, updatedSummary)
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-variant-summary-model", enabled = false)
            val group = "synthetic-early-reply-variants"
            val ids = (1..24).map { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", createdAt = index.toLong(),
                content = "合成顺序原文 [$index]。", variantGroupId = if (index == 2) group else null)) }
            val regeneratedId = f.db.messageDao().insertMessage(Message(conversationId = id, role = "assistant",
                content = "合成首轮重生成内容仅应由已有摘要承载。", variantGroupId = group, variantIndex = 1, createdAt = 1000L))
            assertTrue("回归前提：早期回复的重生成 ID 大于全部后续原文", regeneratedId > ids.last())
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = 32768, rollingSummary = "已确认合成首轮重生成的决定。", summaryUpdatedMessageId = regeneratedId))
            f.repository.compressConversationContext(id).getOrThrow()
            assertEquals("后续低 ID 消息仍必须进入新增摘要请求", 1, endpoint.payloads.size)
            val prompt = endpoint.payloads.single().getAsJsonArray("messages").single().asJsonObject.get("content").asString
            for (messageId in ids.slice(2..7)) assertTrue("遗漏后续低 ID 原文 $messageId", prompt.contains("原文ID $messageId]"))
            assertFalse("已经覆盖的高 ID 变体不能重新付费整理", prompt.contains("原文ID $regeneratedId]"))
            assertFalse(prompt.contains("原文ID ${ids.first()}]"))
            assertTrue(prompt.contains("已确认合成首轮重生成的决定"))
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals(ids[7], saved.summaryUpdatedMessageId)
            assertEquals(updatedSummary, saved.rollingSummary)
            val second = f.repository.compressConversationContext(id).getOrThrow()
            assertFalse(second.canCompress)
            assertEquals("相同覆盖范围再次压缩不能重复计费", 1, endpoint.payloads.size)
            chat(f, config, id)
            assertEquals(2, endpoint.payloads.size)
            val chatPayload = endpoint.payloads.last().toString()
            assertTrue(chatPayload.contains("后续低编号原文"))
            assertTrue(chatPayload.contains("合成顺序原文 [24]"))
            assertFalse(chatPayload.contains("合成顺序原文 [3]"))
            val afterChat = f.db.messageDao().getMessagesList(id)
            for ((index, messageId) in ids.withIndex()) {
                assertEquals("原始消息 $messageId 必须完整保留", "合成顺序原文 [${index + 1}]。",
                    afterChat.single { it.id == messageId }.content)
            }
            assertEquals("合成首轮重生成内容仅应由已有摘要承载。", afterChat.single { it.id == regeneratedId }.content)
            val newReply = afterChat.filter { it.id !in ids && it.id != regeneratedId }.single()
            assertEquals("assistant", newReply.role)
            assertEquals("OK", newReply.content)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun cancellingSummaryDoesNotAdvanceExistingCoverageOrReplaceItsText() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        Endpoint { _, _ -> entered.countDown(); released.await(10, TimeUnit.SECONDS) }.use { endpoint -> Fixture().use { f ->
            val (_, id) = f.conversation(endpoint.url, "synthetic-cancelled-summary-model", enabled = false)
            val ids = (1..24).map { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "待压缩的合成原文 $index。", createdAt = index.toLong())) }
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = 32768, rollingSummary = "上次已确认的合成摘要。", summaryUpdatedMessageId = ids[1]))
            val job = async(Dispatchers.IO) { f.repository.compressConversationContext(id) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                f.repository.cancelActiveRequest(id)
                try { withTimeout(3000) { job.await() }; fail("取消必须向调用者传播") } catch (_: CancellationException) { }
                assertTrue(job.isCancelled)
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals("上次已确认的合成摘要。", saved.rollingSummary)
                assertEquals(ids[1], saved.summaryUpdatedMessageId)
                assertEquals(24, f.db.messageDao().getMessagesList(id).size)
                assertEquals(1, endpoint.payloads.size)
            } finally { released.countDown(); job.cancelAndJoin() }
        } }
    }

    @Test fun failedSummaryNeverAdvancesCoverageOrDeletesOriginalMessages() = runBlocking {
        Endpoint { socket, _ -> reply(socket, "{\"error\":{\"message\":\"synthetic unavailable\"}}", "503 Service Unavailable") }.use { endpoint -> Fixture().use { f ->
            f.settings.setModelRetryEnabled(false)
            val (_, id) = f.conversation(endpoint.url, "synthetic-model")
            val ids = (1..24).map { index -> f.db.messageDao().insertMessage(Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "待总结原文 $index。", createdAt = index.toLong())) }
            val old = f.db.conversationDao().getConversationById(id)!!
            f.db.conversationDao().updateConversation(old.copy(rollingSummary = "上次确认的摘要。", summaryUpdatedMessageId = ids[1], contextWindowTokens = 32768))
            assertTrue(f.repository.compressConversationContext(id).isFailure)
            val latest = f.db.conversationDao().getConversationById(id)!!
            assertEquals("上次确认的摘要。", latest.rollingSummary)
            assertEquals(ids[1], latest.summaryUpdatedMessageId)
            assertEquals(24, f.db.messageDao().getMessagesList(id).size)
            assertTrue(f.db.timelineNodeDao().getTimelineNodes(id).isEmpty())
            assertTrue(f.db.memoryDao().getConversationMemories(id).isEmpty())
            assertEquals(1, endpoint.payloads.size)
        } }
    }

    @Test fun editingCoveredHistoryPreservesSummaryWhileDeletionInvalidatesItAndManualEditKeepsRealCoverage() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation("https://model.invalid")
            val first = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "原始合成事实。"))
            val second = f.repository.saveMessage(Message(conversationId = id, role = "assistant", content = "已确认原始事实。"))
            suspend fun summary() = f.db.conversationDao().updateRollingSummary(id, "原始合成摘要。", second)
            summary()
            f.repository.updateRollingSummary(id, "人工更正的摘要。")
            assertEquals(second, f.db.conversationDao().getConversationById(id)!!.summaryUpdatedMessageId)
            val beforeEdit = f.db.conversationDao().getConversationById(id)!!
            val original = f.db.messageDao().getMessageById(first)!!
            f.repository.updateMessage(original.copy(content = "用户已更正合成事实。"))
            val afterEdit = f.db.conversationDao().getConversationById(id)!!
            assertEquals("人工更正的摘要。", afterEdit.rollingSummary)
            assertEquals(beforeEdit.summaryUpdatedMessageId, afterEdit.summaryUpdatedMessageId)
            assertEquals(beforeEdit.summaryUpdatedAt, afterEdit.summaryUpdatedAt)
            assertEquals("用户已更正合成事实。", f.db.messageDao().getMessageById(first)!!.content)
            summary()
            f.repository.deleteMessage(f.db.messageDao().getMessageById(second)!!)
            assertNull(f.db.conversationDao().getConversationById(id)!!.rollingSummary)
            f.repository.updateRollingSummary(id, "没有覆盖原文的手工背景。")
            assertEquals(0L, f.db.conversationDao().getConversationById(id)!!.summaryUpdatedMessageId)
        }
    }

    @Test fun summaryCompletingAfterSourceEditCannotOverwriteCurrentHistory() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        Endpoint { socket, _ ->
            entered.countDown(); check(released.await(10, TimeUnit.SECONDS))
            memoryReply(socket, "【核心背景与用户固定约束】\n合成的原始目标是向北港送信。\n【历史关键里程碑与决策推进】\n旧资料记载船队已经顺利抵达港口。")
        }.use { endpoint -> Fixture().use { f ->
            val (_, id) = f.conversation(endpoint.url, "synthetic-model")
            val ids = (1..24).map { index -> f.repository.saveMessage(Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成旧事实 $index。", createdAt = index.toLong())) }
            val beforeEdit = f.db.conversationDao().getConversationById(id)!!.copy(
                rollingSummary = "合成已保存摘要：船队的旧计划是前往北港。",
                summaryUpdatedMessageId = ids[1],
                summaryUpdatedAt = 1729L
            )
            f.db.conversationDao().updateConversation(beforeEdit)
            val job = async(Dispatchers.IO) { f.repository.compressConversationContext(id) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val source = f.db.messageDao().getMessageById(ids.first())!!
                f.repository.updateMessage(source.copy(content = "更正：船队尚未抵达。"))
                released.countDown()
                assertTrue(withTimeout(5000) { job.await() }.isFailure)
                val afterEdit = f.db.conversationDao().getConversationById(id)!!
                assertEquals(beforeEdit.rollingSummary, afterEdit.rollingSummary)
                assertEquals(beforeEdit.summaryUpdatedMessageId, afterEdit.summaryUpdatedMessageId)
                assertEquals(beforeEdit.summaryUpdatedAt, afterEdit.summaryUpdatedAt)
                assertEquals("更正：船队尚未抵达。", f.db.messageDao().getMessageById(source.id)!!.content)
            } finally { released.countDown(); job.cancelAndJoin() }
        } }
    }

    @Test fun httpRetryRecordsEveryAttemptInsteadOfOnlyTheFinalSuccess() = runBlocking {
        val attempt = AtomicInteger()
        Endpoint { socket, _ ->
            if (attempt.incrementAndGet() <= 2) reply(socket, "{\"error\":{\"message\":\"synthetic temporary error\"}}", "500 Internal Server Error")
            else streamReply(socket)
        }.use { endpoint -> Fixture().use { f ->
            f.settings.setModelRetryEnabled(true)
            f.settings.setBackupKeyFallbackEnabled(false)
            f.settings.saveRetryRule(RetryErrorType.SERVER, RetryRule(enabled = true, maxRetries = 2))
            val (config, id) = f.conversation(endpoint.url, "synthetic-retry-model", enabled = false)
            chat(f, config, id)
            val stats = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE)
            assertEquals(3, endpoint.payloads.size)
            assertEquals(3, stats.size)
            assertTrue(stats.all { it.apiConfigId == config.id && it.modelName == config.modelName && it.requestKind == "chat" })
            val failures = stats.filterNot { it.success }
            assertEquals(2, failures.size)
            assertTrue(failures.all { it.completionStatus == "failed" && it.tokenSource == "unknown" && it.totalTokens == 0 })
            val completed = stats.single { it.success }
            assertEquals("completed", completed.completionStatus)
            assertEquals("estimated", completed.tokenSource)
            assertTrue(completed.totalTokens > 0)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun auxiliaryExtractionRecordsReportedUsageAndDoesNotDoubleCountReasoningOrCache() = runBlocking {
        val distilled = "用户偏好：长期使用Kotlin开发Android应用"
        Endpoint { socket, _ -> reply(socket, Gson().toJson(mapOf(
            "choices" to listOf(mapOf("finish_reason" to "stop", "message" to mapOf("content" to distilled))),
            "usage" to mapOf("prompt_tokens" to 100, "completion_tokens" to 30, "total_tokens" to 130,
                "completion_tokens_details" to mapOf("reasoning_tokens" to 10),
                "prompt_tokens_details" to mapOf("cached_tokens" to 20))
        ))) }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-memory-model")
            val messageId = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "我长期使用Kotlin开发Android应用"))
            val candidate = f.repository.processSavedMessageMemory(messageId, config.id, config.modelName)!!
            val stat = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).single()
            assertEquals("memory", stat.requestKind)
            assertEquals("reported", stat.tokenSource)
            assertEquals("completed", stat.completionStatus)
            assertTrue(stat.success)
            assertEquals(config.modelName, stat.modelName)
            assertEquals(100, stat.inputTokens)
            assertEquals(20, stat.outputTokens)
            assertEquals(10, stat.thinkingTokens)
            assertEquals(20, stat.cachedTokens)
            assertEquals(130, stat.totalTokens)
            assertEquals(candidate.distilledContent, f.db.memoryDao().getBySourceMessage(messageId)!!.content)
            assertTrue(candidate.distilledContent.contains("长期使用Kotlin开发Android应用"))
            assertEquals(1, endpoint.payloads.size)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun anthropicAuxiliaryUsageKeepsMissingInputEstimatedAndSaturatesCacheAddition() = runBlocking {
        for (reportedInput in listOf<Int?>(null, 100, Int.MAX_VALUE)) {
            Endpoint { socket, _ -> reply(socket, Gson().toJson(mapOf(
                "stop_reason" to "end_turn",
                "content" to listOf(mapOf("type" to "text", "text" to "用户偏好：长期使用Kotlin开发Android应用")),
                "usage" to mapOf("input_tokens" to reportedInput, "output_tokens" to 30,
                    "cache_read_input_tokens" to 20, "cache_creation_input_tokens" to 10)
            ))) }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url, "synthetic-memory-model", "anthropic")
                val messageId = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "我长期使用Kotlin开发Android应用"))
                assertNotNull(f.repository.processSavedMessageMemory(messageId, config.id, config.modelName))
                val stat = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).single()
                val prompt = endpoint.payloads.single().getAsJsonArray("messages").single().asJsonObject.get("content").asString
                val expectedInput = reportedInput?.let { (it.toLong() + 30).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
                    ?: (AiRepository.estimateTokenCount(prompt) + 24)
                assertEquals(expectedInput, stat.inputTokens)
                assertEquals(if (reportedInput == null) "mixed" else "reported", stat.tokenSource)
                assertEquals(30, stat.outputTokens)
                assertEquals(0, stat.thinkingTokens)
                assertEquals(20, stat.cachedTokens)
                assertEquals((expectedInput.toLong() + 30).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), stat.totalTokens)
                assertEquals("memory", stat.requestKind)
                assertEquals("completed", stat.completionStatus)
                assertTrue(stat.success)
            } }
        }
    }

    @Test fun lengthTruncatedSummaryCannotAdvanceCoverageEvenWhenTextEndsWithPeriod() = runBlocking {
        val plausible = "【核心背景与用户固定约束】\n这是看起来完整的合成设定与目标。\n【历史关键里程碑与决策推进】\n最后一句也以句号结束但接口明确报告截断。"
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, _ ->
                val response = if (protocol == "openai") mapOf("choices" to listOf(mapOf("finish_reason" to "length", "message" to mapOf("content" to plausible))))
                    else mapOf("stop_reason" to "max_tokens", "content" to listOf(mapOf("type" to "text", "text" to plausible)))
                reply(socket, Gson().toJson(response))
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                val (_, id) = f.conversation(endpoint.url, "synthetic-model", protocol)
                (1..20).forEach { index -> f.repository.saveMessage(Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成消息$index。", createdAt = index.toLong())) }
                assertTrue(f.repository.compressConversationContext(id).isFailure)
                assertNull(f.db.conversationDao().getConversationById(id)!!.rollingSummary)
                assertNull(f.db.conversationDao().getConversationById(id)!!.summaryUpdatedMessageId)
                assertEquals(20, f.db.messageDao().getMessagesList(id).size)
                assertEquals(1, endpoint.payloads.size)
                val stat = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).single()
                assertEquals("summary", stat.requestKind)
                assertEquals("interrupted", stat.completionStatus)
                assertFalse(stat.success)
            } }
        }
    }

    @Test fun quickExtractionRejectsTruncatedTextForBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, _ ->
                val response = if (protocol == "openai") mapOf("choices" to listOf(mapOf("finish_reason" to "length", "message" to mapOf("content" to "合成记录的前半部分。"))))
                    else mapOf("stop_reason" to "max_tokens", "content" to listOf(mapOf("type" to "text", "text" to "合成记录的前半部分。")))
                reply(socket, Gson().toJson(response))
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setModelRetryEnabled(false)
                val (config, _) = f.conversation(endpoint.url, "synthetic-model", protocol)
                assertTrue(f.repository.executeQuickCompletionWithResult(config, "整理合成记录", kind = "timeline").isFailure)
                assertEquals(1, endpoint.payloads.size)
                val stat = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).single()
                assertEquals("timeline", stat.requestKind)
                assertEquals("interrupted", stat.completionStatus)
                assertFalse(stat.success)
            } }
        }
    }

    @Test fun timelineReconciliationChecksEverySourceTurnAndKeepsShoppingProcessWithoutASecondCompressionCall() = runBlocking {
        val facts = listOf("合成角色甲陪同角色乙挑选生日礼物，两人最终选中蓝色围巾",
            "两人在生鲜区因预算发生争执，之后决定各自退掉一件零食以保留礼物",
            "结账前角色乙发现钱包遗失，角色甲陪同沿货架寻找并在服务台领回",
            "两人结账返家并分好食材，约定下次购物前共同列清单")
        var sourceIds = emptyList<Long>()
        val complete = java.util.concurrent.atomic.AtomicBoolean(false)
        Endpoint { socket, _ ->
            val events = if (complete.get()) facts else listOf("合成角色甲乙购物后回家")
            val covered = if (complete.get()) sourceIds else sourceIds.takeLast(1)
            memoryReply(socket, Gson().toJson(mapOf(
                "currentStoryTime" to "购物当天·返家后",
                "sourceCoverage" to covered.mapIndexed { index, messageId -> mapOf("messageId" to messageId,
                    "eventIndices" to listOf(if (complete.get()) (index / 6).coerceAtMost(3) else 0), "settingIndices" to emptyList<Int>()) },
                "timelineEvents" to events.map { mapOf("timeTag" to "购物当天", "content" to it, "category" to "PLOT_EVENT") },
                "atemporalSettings" to emptyList<String>())))
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-model")
            val conversation = f.db.conversationDao().getConversationById(id)!!
            f.db.conversationDao().updateConversation(conversation.copy(contextWindowTokens = 32000))
            sourceIds = (0 until 20).map { index -> f.repository.saveMessage(Message(conversationId = id,
                role = if (index % 2 == 0) "user" else "assistant", content = "合成购物片段$index：${facts[(index / 6).coerceAtMost(3)]}", createdAt = index.toLong())) }
            var draftCount = 0
            val failure = runCatching { f.repository.reconcileConversationTimeline(id, config.id, config.modelName,
                onIntermediateResult = { draftCount++ }) }.exceptionOrNull()
            assertTrue("只覆盖最后一条源消息不能宣称完成", failure is IllegalStateException)
            assertEquals(0, draftCount)
            assertTrue(f.repository.getTimelineNodes(id).isEmpty())
            complete.set(true)
            val staleNode = TimelineNode(conversationId = id, timeTag = "第7天", event = "旧的错误概括")
            val result = f.repository.reconcileConversationTimeline(id, config.id, config.modelName,
                existingTimelineNodes = listOf(staleNode))
            assertEquals(facts, result.events.map { it.content })
            assertEquals("购物当天·返家后", result.currentStoryTime)
            assertEquals(sourceIds.last(), result.lastProcessedMessageId)
            assertEquals(20, result.totalProcessedMessages)
            assertEquals("失败一次、成功一次；完成后不再调用模型二次压缩", 2, endpoint.payloads.size)
            val prompt = endpoint.payloads.last().getAsJsonArray("messages").single().asJsonObject.get("content").asString
            sourceIds.forEach { assertTrue(prompt.contains("消息 $it ·")) }
            assertTrue(endpoint.failures.isEmpty())
        } }
    }

    @Test fun malformedTimelineResponsesCannotAdvanceAResumedDraftOrChangeSavedFacts() = runBlocking {
        val response = java.util.concurrent.atomic.AtomicReference("")
        Endpoint { socket, _ -> memoryReply(socket, response.get()) }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-model")
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(contextWindowTokens = 32000))
            f.repository.updateStoryTime(id, "购物当天·返家后")
            val ids = (0..1).map { index -> f.repository.saveMessage(Message(conversationId = id,
                role = "assistant", content = "合成角色在车站交换第${index + 1}份采购清单", createdAt = index.toLong())) }
            f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                timeTag = "购物当天", event = "合成角色已经领回钱包"))
            val targetId = f.db.memoryDao().insertMemory(MemoryItem(scope = "conversation", conversationId = id,
                content = "【习惯偏好】合成角色每周整理采购清单"))
            val globalId = f.db.memoryDao().insertMemory(MemoryItem(scope = "global", conversationId = id,
                content = "明确合成的跨范围对照资料"))
            val draft = TimelineReconcileDraft(conversationId = id, lastProcessedMessageId = ids.first(),
                currentStoryTime = "购物当天·返家后", events = listOf(TimelineEventItem(timeTag = "购物当天", content = "合成角色已经领回钱包")))
            val beforeNodes = f.repository.getTimelineNodes(id)
            val beforeSettings = f.db.memoryDao().getConversationMemories(id)
            val valid = JsonParser.parseString("""{"currentStoryTime":"购物后约两周","timelineEvents":[{"timeTag":"购物后约两周","content":"合成角色在车站交换第二份采购清单"}],"atemporalSettings":[{"action":"ADD","targetMemoryId":null,"content":"合成角色不能食用花生"}],"sourceCoverage":[{"messageId":${ids.last()},"eventIndices":[0],"settingIndices":[0]}]}""").asJsonObject
            val malformed = listOf<Pair<String, (JsonObject) -> Unit>>(
                "null 时间" to { it.add("currentStoryTime", JsonNull.INSTANCE) },
                "null 正文" to { it.getAsJsonArray("timelineEvents")[0].asJsonObject.add("content", JsonNull.INSTANCE) },
                "空正文" to { it.getAsJsonArray("timelineEvents")[0].asJsonObject.addProperty("content", " ") },
                "小数源 ID" to { it.getAsJsonArray("sourceCoverage")[0].asJsonObject.addProperty("messageId", ids.last() + 0.5) },
                "小数事件索引" to { it.getAsJsonArray("sourceCoverage")[0].asJsonObject.add("eventIndices", JsonParser.parseString("[0.5]")) },
                "不存在的补充目标" to { it.getAsJsonArray("atemporalSettings")[0].asJsonObject.apply {
                    addProperty("action", "SUPPLEMENT"); addProperty("targetMemoryId", targetId + 1000) } },
                "跨范围的修订目标" to { it.getAsJsonArray("atemporalSettings")[0].asJsonObject.apply {
                    addProperty("action", "REVISE"); addProperty("targetMemoryId", globalId) } }
            )
            for ((label, damage) in malformed) {
                response.set(valid.deepCopy().also(damage).toString())
                val emitted = mutableListOf<TimelineReconcileDraft>()
                val failure = runCatching { f.repository.reconcileConversationTimeline(id, config.id, config.modelName,
                    startFromDraft = draft, existingTimelineNodes = beforeNodes, onIntermediateResult = { emitted += it }) }.exceptionOrNull()
                assertTrue(label, failure is IllegalStateException)
                assertTrue("$label 不能发布已完成草稿", emitted.isEmpty())
                assertEquals(ids.first(), draft.lastProcessedMessageId)
                assertEquals(beforeNodes, f.repository.getTimelineNodes(id))
                assertEquals(beforeSettings, f.db.memoryDao().getConversationMemories(id))
                assertEquals("购物当天·返家后", f.db.conversationDao().getConversationById(id)!!.currentStoryTime)
            }
            response.set(valid.toString())
            val accepted = f.repository.reconcileConversationTimeline(id, config.id, config.modelName,
                startFromDraft = draft, existingTimelineNodes = beforeNodes)
            assertEquals(ids.last(), accepted.lastProcessedMessageId)
            assertEquals(listOf("合成角色已经领回钱包", "合成角色在车站交换第二份采购清单"), accepted.events.map { it.content })
            assertEquals("ADD", accepted.atemporalSettings.single().action)
            assertNull(accepted.atemporalSettings.single().targetMemoryId)
            val supplement = valid.deepCopy().apply { getAsJsonArray("atemporalSettings")[0].asJsonObject.apply {
                addProperty("action", "SUPPLEMENT"); addProperty("targetMemoryId", targetId)
                addProperty("content", "整理清单时会额外标注过敏食材") } }
            response.set(supplement.toString())
            val supplemented = f.repository.reconcileConversationTimeline(id, config.id, config.modelName, startFromDraft = draft)
            assertEquals(targetId, supplemented.atemporalSettings.single().targetMemoryId)
            assertTrue(supplemented.atemporalSettings.single().content.contains("每周整理采购清单"))
            assertTrue(supplemented.atemporalSettings.single().content.contains("额外标注过敏食材"))
            assertEquals(beforeNodes, f.repository.getTimelineNodes(id))
            assertEquals(beforeSettings, f.db.memoryDao().getConversationMemories(id))
            assertEquals(malformed.size + 2, endpoint.payloads.size)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun aMalformedLaterTimelineChunkKeepsOnlyTheCompletedSourceRange() = runBlocking {
        var ids = emptyList<Long>()
        val calls = AtomicInteger(0)
        Endpoint { socket, _ ->
            val first = calls.getAndIncrement() == 0
            val chunkIds = if (first) ids.take(25) else ids.drop(25)
            val text = Gson().toJson(mapOf("currentStoryTime" to "购物当天·结账前",
                "timelineEvents" to listOf(mapOf("timeTag" to "购物当天", "content" to "合成角色已经选好礼物并解决预算分歧")),
                "atemporalSettings" to emptyList<String>(),
                "sourceCoverage" to chunkIds.map { mapOf("messageId" to it, "eventIndices" to listOf(0)) }))
            val body = JsonParser.parseString(text).asJsonObject
            if (!first) body.getAsJsonArray("timelineEvents")[0].asJsonObject.add("content", JsonNull.INSTANCE)
            memoryReply(socket, body.toString())
        }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-model")
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(contextWindowTokens = 32000))
            f.repository.updateStoryTime(id, "购物当天·出发前")
            f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id, timeTag = "购物当天", event = "此前已确认的合成事件"))
            ids = (0 until 26).map { index -> f.repository.saveMessage(Message(conversationId = id,
                role = if (index % 2 == 0) "user" else "assistant", content = "合成角色购物片段$index：选礼物并核对预算", createdAt = index.toLong())) }
            val beforeNodes = f.repository.getTimelineNodes(id)
            val drafts = mutableListOf<TimelineReconcileDraft>()
            val failure = runCatching { f.repository.reconcileConversationTimeline(id, config.id, config.modelName,
                onIntermediateResult = { drafts += it }) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertTrue(failure!!.message.orEmpty().contains("第 2 段"))
            val kept = drafts.single()
            assertFalse(kept.isCompleted)
            assertEquals(1, kept.processedChunks)
            assertEquals(2, kept.totalChunks)
            assertEquals(ids[24], kept.lastProcessedMessageId)
            assertEquals("合成角色已经选好礼物并解决预算分歧", kept.events.single().content)
            assertEquals(beforeNodes, f.repository.getTimelineNodes(id))
            assertTrue(f.db.memoryDao().getConversationMemories(id).isEmpty())
            assertEquals("购物当天·出发前", f.db.conversationDao().getConversationById(id)!!.currentStoryTime)
            assertEquals(2, endpoint.payloads.size)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun reviewedShoppingClosureSupplementsPersistedProcessInOrdinaryAndCreativeConversations() = runBlocking {
        for (tags in listOf<String?>(null, "story")) {
            val target = java.util.concurrent.atomic.AtomicLong(0)
            val old = "合成角色甲乙挑选蓝色围巾。两人在生鲜区因预算争执后退掉零食。角色乙遗失的钱包已从服务台领回。"
            val ending = "两人结账后提着食材回家，约定下次共同列购物清单"
            Endpoint { socket, _ -> memoryReply(socket, Gson().toJson(mapOf("newStoryTime" to null,
                "action" to "UPDATE", "targetNodeId" to target.get(),
                "newEvent" to mapOf("timeTag" to "购物当天", "content" to ending),
                "atemporalSettings" to emptyList<String>()))) }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url, tags = tags)
                target.set(f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                    timeTag = "购物当天", event = old)))
                val proposal = f.repository.evaluateAndAutoUpdateTimeline(id, "一起回家吧", ending, config.id, config.modelName)!!
                assertEquals("UPDATE", proposal.action)
                assertEquals(old, proposal.previousEventContent)
                f.repository.applyAutoTimelineProposal(id, proposal)
                f.repository.applyAutoTimelineProposal(id, proposal)
                val saved = f.repository.getTimelineNodes(id).single()
                assertEquals(target.get(), saved.id)
                assertTrue(saved.event.contains("挑选蓝色围巾"))
                assertTrue(saved.event.contains("因预算争执后退掉零食"))
                assertTrue(saved.event.contains("钱包已从服务台领回"))
                assertTrue(saved.event.contains(ending))
                assertTrue(saved.event.length > 28)
                assertEquals(1, endpoint.payloads.size)
            } }
        }
    }

    @Test fun naturalWeekAndMonthJumpsPersistAndRemainTheTimeAnchorInTheNextChatRequest() = runBlocking {
        for (span in listOf("购物返家后约一两周", "购物返家后一两个月")) {
            Endpoint { socket, payload ->
                if (payload.get("stream")?.asBoolean == true) streamReply(socket)
                else {
                    val prompt = payload.getAsJsonArray("messages").single().asJsonObject.get("content").asString
                    if (prompt.contains("[助手剧情正文]: 继续讨论清单")) memoryReply(socket, "NO_UPDATE")
                    else memoryReply(socket, Gson().toJson(mapOf("newStoryTime" to span, "newEvent" to null,
                        "atemporalSettings" to emptyList<String>())))
                }
            }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url)
                f.repository.updateStoryTime(id, "第6天·上午")
                f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id,
                    timeTag = "第6天·上午", event = "合成角色甲乙购物返家"))
                val proposal = f.repository.evaluateAndAutoUpdateTimeline(id, "继续剧情", "过了${span.substringAfter("后")}，两人再次见面", config.id, config.modelName)!!
                assertEquals(span, proposal.updatedStoryTime)
                f.repository.applyAutoTimelineProposal(id, proposal)
                assertEquals(span, f.db.conversationDao().getConversationById(id)!!.currentStoryTime)
                assertNull(f.repository.evaluateAndAutoUpdateTimeline(id, "继续聊吧", "继续讨论清单", config.id, config.modelName))
                chat(f, config, id)
                val request = endpoint.payloads.last().getAsJsonArray("messages").joinToString { it.asJsonObject.get("content").asString }
                assertTrue(request.contains("【当前故事时间节点】：$span"))
                assertFalse(request.contains("第 7 天"))
                assertTrue(request.contains("[第6天·上午]"))
                assertEquals(3, endpoint.payloads.size)
            } }
        }
    }

    @Test fun manualSummaryEditDuringGenerationCannotBeOverwritten() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        Endpoint { socket, _ ->
            entered.countDown()
            check(released.await(10, TimeUnit.SECONDS))
            memoryReply(socket, "【核心背景与用户固定约束】\n合成探险队仍需遵守北港通行规则。\n【历史关键里程碑与决策推进】\n船队已在合成码头集结。")
        }.use { endpoint -> Fixture().use { f ->
            val (_, id) = f.conversation(endpoint.url, "synthetic-model")
            val ids = (1..24).map { index -> f.repository.saveMessage(Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成消息$index。", createdAt = index.toLong())) }
            f.db.conversationDao().updateRollingSummary(id, "既有的合成摘要。", ids[3])
            val job = async(Dispatchers.IO) { f.repository.compressConversationContext(id) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                f.repository.updateRollingSummary(id, "人工更正：角色仍在旧港，尚未出发。")
                released.countDown()
                assertTrue(withTimeout(5000) { job.await() }.isFailure)
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals("人工更正：角色仍在旧港，尚未出发。", saved.rollingSummary)
                assertEquals(ids[3], saved.summaryUpdatedMessageId)
            } finally { released.countDown(); job.cancelAndJoin() }
        } }
    }

    @Test fun directTruncationPersistsNormalizedRatioWithoutNetworkOrChangingSummaryCoverage() = runBlocking {
        Endpoint { socket, _ -> streamReply(socket) }.use { endpoint -> Fixture().use { f ->
            val (_, id) = f.conversation(endpoint.url, "synthetic-direct-truncation-model", enabled = false)
            val ids = f.db.messageDao().insertMessages((1..20).map { index -> Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "合成直接截断原文[$index]。",
                createdAt = index.toLong(), isPinned = index == 2) })
            val badSummary = "由于安全限制，本模型无法提供该内容的摘要。此合成拒绝说明仅用于回归验证。"
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = 32768, rollingSummary = badSummary,
                summaryUpdatedMessageId = ids[17], summaryUpdatedAt = 123L))
            val originals = f.db.messageDao().getMessagesList(id)

            f.repository.updateConversationCompressionTier(id, CompressionTier.LT, 8, 33)
            val usage = f.repository.compressConversationContext(id).getOrThrow()
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals(6, saved.compressionTier)
            assertEquals(35, saved.compressionCustomPercent)
            assertEquals(CompressionTier.LT, usage.compressionTier)
            assertEquals(35, usage.compressionCustomPercent)
            assertEquals("十轮截去三轮，但完整保留第一置顶轮", 16, usage.recentMessageCount)
            assertEquals("统计必须扣除已保留的置顶整轮", 4, usage.olderMessageCount)
            assertEquals("未注入旧摘要时不可计入摘要预算", 0, usage.summaryTokens)
            assertEquals(badSummary, saved.rollingSummary)
            assertEquals(ids[17], saved.summaryUpdatedMessageId)
            assertEquals(123L, saved.summaryUpdatedAt)
            assertEquals(originals, f.db.messageDao().getMessagesList(id))
            assertTrue("应用直接截断不可请求模型摘要", endpoint.payloads.isEmpty())
            assertTrue(f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).isEmpty())
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun directTruncationSendsOnlySelectedRawTurnsAndWholePinnedTurnsForBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, _ ->
                if (protocol == "openai") streamReply(socket) else reply(socket,
                    "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\n" +
                        "data: {\"type\":\"message_stop\"}\n\n", type = "text/event-stream")
            }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url, "synthetic-direct-truncation-model", protocol, enabled = false)
                val ids = f.db.messageDao().insertMessages((1..20).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 1) "user" else "assistant", content = "合成线报[$index]：保留完整原始句子。",
                    createdAt = index.toLong(), isPinned = index == 2) })
                val badSummary = "由于安全限制，不能生成摘要。该旧合成拒绝文本绝不能作为历史背景继续发送。"
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                    contextWindowTokens = 32768, rollingSummary = badSummary,
                    summaryUpdatedMessageId = ids[17], summaryUpdatedAt = 123L))
                val originals = f.db.messageDao().getMessagesList(id)
                f.repository.updateConversationCompressionTier(id, CompressionTier.LT, 8, 50)

                chat(f, config, id)

                val request = endpoint.payloads.single()
                assertTrue(request.get("stream").asBoolean)
                assertEquals("synthetic-direct-truncation-model", request.get("model").asString)
                val contents = request.getAsJsonArray("messages")
                    .filter { it.asJsonObject.get("role").asString != "system" }
                    .map { it.asJsonObject.get("content").asString }
                assertEquals((originals.take(2) + originals.drop(10)).map { it.content } + "reply OK", contents)
                assertFalse(request.toString().contains(badSummary))
                assertEquals("每次直接截断发送只能产生正式对话请求", 1, endpoint.payloads.size)
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals(badSummary, saved.rollingSummary)
                assertEquals(ids[17], saved.summaryUpdatedMessageId)
                assertEquals(123L, saved.summaryUpdatedAt)
                assertEquals(originals, f.db.messageDao().getMessagesList(id).take(originals.size))
                assertEquals(originals.size + 1, f.db.messageDao().getMessagesList(id).size)
                assertTrue(f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).none { it.requestKind == "summary" })
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun loweringDirectTruncationRatioAndReturningToCompleteHistoryRestoresOriginalsInActualRequests() = runBlocking {
        Endpoint { socket, _ -> streamReply(socket) }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url, "synthetic-direct-truncation-restore-model", enabled = false)
            val ids = f.db.messageDao().insertMessages((1..20).map { index -> Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant", content = "可恢复合成片段[$index]。", createdAt = index.toLong()) })
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = 32768, rollingSummary = "既有合成摘要：旧港通行安排已确认。",
                summaryUpdatedMessageId = ids[17], summaryUpdatedAt = 123L))
            val originals = f.db.messageDao().getMessagesList(id)
            for ((tier, percent, firstRetained) in listOf(
                Triple(CompressionTier.LT, 80, 17), Triple(CompressionTier.LT, 20, 5), Triple(CompressionTier.L0, 20, 1))) {
                f.repository.updateConversationCompressionTier(id, tier, 8, percent)
                chat(f, config, id)
                val body = endpoint.payloads.last().toString()
                for (index in 1..20) {
                    assertEquals("$tier/$percent 原文片段 $index 的恢复范围必须准确", index >= firstRetained,
                        body.contains("可恢复合成片段[$index]"))
                }
                assertEquals(originals, f.db.messageDao().getMessagesList(id).take(originals.size))
                assertEquals(ids[17], f.db.conversationDao().getConversationById(id)!!.summaryUpdatedMessageId)
            }
            assertEquals(3, endpoint.payloads.size)
            assertTrue(endpoint.payloads.all { it.get("stream")?.asBoolean == true })
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun overflowingDirectTruncationReportsTheChosenRatioWithoutExtraCutsOrSummaryRequests() = runBlocking {
        Endpoint { socket, _ -> streamReply(socket) }.use { endpoint -> Fixture().use { f ->
            f.settings.setModelRetryEnabled(false)
            f.settings.setBackupKeyFallbackEnabled(false)
            val (config, id) = f.conversation(endpoint.url, "synthetic-direct-truncation-overflow-model", enabled = false)
            val ids = f.db.messageDao().insertMessages((1..20).map { index -> Message(conversationId = id,
                role = if (index % 2 == 1) "user" else "assistant",
                content = "合成超预算原文[$index]：保留用户指定的完整窗口。".repeat(400), createdAt = index.toLong()) })
            f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(
                contextWindowTokens = 4000, rollingSummary = "先前已确认的合成摘要。",
                summaryUpdatedMessageId = ids[17], summaryUpdatedAt = 123L))
            f.repository.updateConversationCompressionTier(id, CompressionTier.LT, 8, 50)
            val before = f.db.conversationDao().getConversationById(id)!!
            val originals = f.db.messageDao().getMessagesList(id)
            val usage = f.repository.getConversationContextUsage(id)
            assertEquals("即使超预算也只截去用户指定的前五轮", 10, usage.recentMessageCount)
            assertTrue(usage.estimatedInputTokens > usage.promptBudgetTokens)
            assertEquals(0, usage.summaryTokens)
            val compressed = f.repository.compressConversationContext(id)
            assertTrue(compressed.isFailure)
            assertTrue(compressed.exceptionOrNull()!!.message.orEmpty().contains("截断比例"))
            val errors = mutableListOf<String>()
            f.repository.sendChatMessageWithConfig(config, id, "继续合成对话", onToken = { fail("超预算不能发出正文") },
                onComplete = { _, _, _ -> fail("超预算不能宣称发送成功") }, onError = { errors += it })
            assertEquals(1, errors.size)
            assertTrue(errors.single().contains("截断比例"))
            val after = f.db.conversationDao().getConversationById(id)!!
            assertEquals(CompressionTier.LT.level, after.compressionTier)
            assertEquals(50, after.compressionCustomPercent)
            assertEquals(before.rollingSummary, after.rollingSummary)
            assertEquals(before.summaryUpdatedMessageId, after.summaryUpdatedMessageId)
            assertEquals(before.summaryUpdatedAt, after.summaryUpdatedAt)
            assertEquals(originals, f.db.messageDao().getMessagesList(id))
            assertTrue("本地超预算不得调用聊天或额外摘要", endpoint.payloads.isEmpty())
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

}
