package com.aiassistant

import android.app.Application
import androidx.room.Room
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.*
import com.aiassistant.utils.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LongTermMemoryAndResilientCompressionTest {

    @Before fun allowEndpoints() { HttpTestAccess.start() }
    @After fun stopEndpoints() { HttpTestAccess.stop() }

    private class LocalEndpoint(val respond: (String, Socket, JsonObject) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newCachedThreadPool()
        val url = "http://127.0.0.1:${server.localPort}/v1"
        val paths = CopyOnWriteArrayList<String>()
        val payloads = CopyOnWriteArrayList<JsonObject>()
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
                                    fun readLine(): String {
                                        val baos = java.io.ByteArrayOutputStream()
                                        while (true) {
                                            val b = input.read()
                                            if (b < 0 || b == 10) break
                                            if (b != 13) baos.write(b)
                                        }
                                        return baos.toString("UTF-8")
                                    }
                                    val requestLine = readLine()
                                    paths += requestLine
                                    var len = 0
                                    while (true) {
                                        val h = readLine()
                                        if (h.isEmpty()) break
                                        if (h.startsWith("Content-Length:", true)) len = h.substringAfter(':').trim().toInt()
                                    }
                                    val buf = ByteArray(len)
                                    var read = 0
                                    while (read < len) {
                                        val c = input.read(buf, read, len - read)
                                        check(c > 0)
                                        read += c
                                    }
                                    val body = JsonParser.parseString(String(buf, Charsets.UTF_8)).asJsonObject
                                    payloads += body
                                    respond(requestLine, socket, body)
                                } catch (t: Throwable) {
                                    if (!server.isClosed) failures += t
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

    private fun writeHttp(socket: Socket, body: String, status: String = "200 OK", contentType: String = "application/json") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().write(header.toByteArray(Charsets.UTF_8) + bytes)
        socket.getOutputStream().flush()
    }

    private fun streamOk(socket: Socket) = writeHttp(
        socket,
        "data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\ndata: [DONE]\n\n",
        contentType = "text/event-stream"
    )

    private fun chatJsonReply(socket: Socket, content: String, finishReason: String = "stop") = writeHttp(
        socket,
        Gson().toJson(mapOf("choices" to listOf(mapOf("finish_reason" to finishReason, "message" to mapOf("content" to content)))))
    )

    private class RepoFixture : AutoCloseable {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = PersonalizationManager(context)
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
            settings.setModelRetryEnabled(false)
            settings.setBackupKeyFallbackEnabled(false)
            settings.saveSettings(
                PersonalizationSettings(
                    autoMemoryEnabled = true,
                    auxiliaryMemoryEnabled = false,
                    embeddingEnabled = false,
                    enableSettingContinuityAnchor = true,
                    enablePlotChunkRecall = true,
                    sensitiveCompressionMode = EchoSensitiveCompressionMode.ROLEPLAY_AUTO
                )
            )
            repository = AiRepository(
                db.folderDao(), db.apiConfigDao(), db.conversationDao(), db.messageDao(), db.usageStatDao(),
                db.environmentVariableDao(), db.promptTemplateDao(), db.memoryDao(), db.conversationBranchDao(),
                db.selectedModelDao(), crypto, settings, TavilySearchManager(context, crypto),
                worldBookDao = db.worldBookDao(), timelineNodeDao = db.timelineNodeDao(),
                summaryChunkDao = db.summaryChunkDao(), vectorEmbeddingDao = db.vectorEmbeddingDao()
            )
        }

        suspend fun createConversation(url: String, model: String = "synthetic-chat", tags: String? = null, extMem: Boolean = true): Pair<ApiConfig, Long> {
            val cfg = ApiConfig(name = "TestCfg", provider = "openai", baseUrl = url, apiKey = "k-test", modelName = model, apiType = "openai", enableThinking = false)
            val cfgId = db.apiConfigDao().insertConfig(cfg)
            val convId = db.conversationDao().insertConversation(
                Conversation(title = "mem-test", apiConfigId = cfgId, modelName = model, tags = tags, enableSessionMemory = true, enableExternalMemory = extMem, contextWindowTokens = 32768)
            )
            return cfg.copy(id = cfgId) to convId
        }

        override fun close() {
            db.close()
            instanceField.set(null, previousApp)
        }
    }

    // ==================== M1 核心检索与评分测试 ====================

    @Test
    fun chineseBm25RecallFindsThirtyDayOldMemoryAndRejectsFreshIrrelevantNoise() {
        val now = 1_760_000_000_000L
        val oldRelevant = MemoryItem(
            id = 1L,
            content = "平时主力使用Kotlin和Jetpack Compose开发安卓客户端应用",
            category = EchoMemoryCategory.PREFERENCE.key,
            importance = 4,
            updatedAt = now - 30L * 24 * 60 * 60 * 1000
        )
        val freshIrrelevant = MemoryItem(
            id = 2L,
            content = "昨天刚去西湖边散步并喝了一杯桂花龙井茶",
            category = EchoMemoryCategory.FACT.key,
            importance = 3,
            updatedAt = now - 60_000L
        )
        val ranked = LongTermMemoryEngine.rankMemories(
            query = "帮我优化一下安卓端的Compose列表卡顿问题",
            candidates = listOf(oldRelevant, freshIrrelevant),
            currentTimeMs = now
        )
        assertEquals("30天前高度相关的中文记忆必须精准召回，且刚存的无关记忆不得凭新鲜度混入", listOf(1L), ranked.map { it.memory.id })
        assertTrue(ranked.single().score > 0.45f)

        // 同时验证旧入口 AdvancedMemoryEngine.calculateHybridScore 中文匹配已修复
        val terms = LongTermMemoryEngine.tokenizeSet("安卓Compose列表优化")
        val legacyScore = AdvancedMemoryEngine.calculateHybridScore(oldRelevant, terms, emptySet(), 1L, now)
        assertTrue("修复后的混合评分对中文老记忆应给出高相关分，当前=$legacyScore", legacyScore > 0.55f)
    }

    @Test
    fun noEightyRowTruncationAllActiveGlobalMemoriesAreSearchable() = runBlocking {
        RepoFixture().use { f ->
            val targetId = f.db.memoryDao().insertMemory(
                MemoryItem(scope = "user", content = "特别约定：暗影航线密码是北极星七号", category = "FACT", updatedAt = 10L)
            )
            for (i in 1..110) {
                f.db.memoryDao().insertMemory(
                    MemoryItem(scope = "user", content = "常规背景条目编号 $i：记录测试用工作流日志。", category = "FACT", updatedAt = 1000L + i)
                )
            }
            val all = f.db.memoryDao().getAllActiveGlobalMemories()
            assertEquals("全量检索不得受 LIMIT 80 截断", 111, all.size)
            val ranked = LongTermMemoryEngine.rankMemories("暗影航线密码是什么", all, maxResults = 5)
            assertEquals(targetId, ranked.first().memory.id)
        }
    }

    @Test
    fun mmrDiversityAndOneHopGraphBoostSurfaceComplementaryContext() {
        val dup1 = MemoryItem(id = 1L, content = "日常技术栈主力使用Kotlin开发Android应用", category = "PREFERENCE", relatedIdsJson = "[3]")
        val dup2 = MemoryItem(id = 2L, content = "日常技术栈主力使用Kotlin开发Android应用界面", category = "PREFERENCE")
        val relatedNeighbor = MemoryItem(id = 3L, content = "数据库选型坚持Room配合显式迁移脚本", category = "PROJECT")
        val ranked = LongTermMemoryEngine.rankMemories(
            query = "聊聊Kotlin开发Android应用",
            candidates = listOf(dup1, dup2, relatedNeighbor),
            maxResults = 3
        )
        assertTrue("1-hop 关联邻居必须被图谱联动唤醒", ranked.any { it.memory.id == 3L })
    }

    @Test
    fun gatekeeperBlocksChitChatAndCodeDumpsWhileAllowingDurableUserFacts() {
        assertFalse(LongTermMemoryEngine.shouldAttemptGlobalExtraction("好的"))
        assertFalse(LongTermMemoryEngine.shouldAttemptGlobalExtraction("谢谢"))
        assertFalse(LongTermMemoryEngine.shouldAttemptGlobalExtraction("继续"))
        assertFalse(LongTermMemoryEngine.shouldAttemptGlobalExtraction("明天天气怎么样？"))
        assertFalse(LongTermMemoryEngine.shouldAttemptGlobalExtraction("帮我翻译一下这段英文"))
        assertFalse(LongTermMemoryEngine.shouldAttemptGlobalExtraction("```kotlin\n" + "val x = 1\n".repeat(40) + "```"))

        assertTrue(LongTermMemoryEngine.shouldAttemptGlobalExtraction("我长期使用Kotlin开发Android应用"))
        assertTrue(LongTermMemoryEngine.shouldAttemptGlobalExtraction("请记住：以后回答不要过于客套，直接给结论"))
        assertTrue(LongTermMemoryEngine.shouldAttemptGlobalExtraction("在这个会话中始终使用文言文回复"))
    }

    @Test
    fun smartAddConflictSoftArchivesOldFactAndReviewModeWaitsForConfirmation() = runBlocking {
        val calls = AtomicInteger()
        LocalEndpoint { _, socket, _ ->
            calls.incrementAndGet()
            chatJsonReply(
                socket,
                """{"action":"CONFLICT","targetMemoryId":1,"category":"IDENTITY","importance":4,"content":"目前常住在深圳市南山区","reason":"用户已从北京搬迁至深圳"}"""
            )
        }.use { ep -> RepoFixture().use { f ->
            val (cfg, convId) = f.createConversation(ep.url)
            val oldId = f.db.memoryDao().insertMemory(
                MemoryItem(scope = "user", content = "常住在北京市海淀区", category = "IDENTITY", importance = 4)
            )
            assertEquals(1L, oldId)

            // 1. AUTO 模式：就地更新活跃记录并写入不可变软归档副本，且可一键恢复
            val msg1 = f.repository.saveMessage(Message(conversationId = convId, role = "user", content = "我已经从北京搬家到深圳南山了"))
            val autoCandidate = f.repository.processSavedMessageMemory(msg1, cfg.id, cfg.modelName)!!
            assertEquals(oldId, autoCandidate.alreadySavedMemoryId)
            val active = f.db.memoryDao().getAllActiveGlobalMemories()
            assertEquals(1, active.size)
            assertTrue(active.single().content.contains("深圳"))
            val archived = f.db.memoryDao().getArchivedGlobalMemoriesFlow().first()
            assertEquals(1, archived.size)
            assertTrue("旧事实必须以软归档形式保留而非永久销毁", archived.single().content.contains("北京"))
            assertEquals(oldId, archived.single().supersededById)

            // 一键恢复归档记忆
            f.repository.restoreArchivedMemory(archived.single().id)
            assertEquals(2, f.db.memoryDao().getAllActiveGlobalMemories().size)

            // 2. REVIEW 模式：仅返回候选不提前落库
            f.settings.saveSettings(f.settings.getSettings().copy(memoryWriteMode = EchoMemoryWriteMode.REVIEW))
            val msg2 = f.repository.saveMessage(Message(conversationId = convId, role = "user", content = "我决定以后都住在大理"))
            val reviewCandidate = f.repository.processSavedMessageMemory(msg2, cfg.id, cfg.modelName)!!
            assertNull("审阅确认模式不得提前写入数据库", reviewCandidate.alreadySavedMemoryId)
            assertNull(f.db.memoryDao().getBySourceMessage(msg2))
        } }
    }

    @Test
    fun smartAddJsonSkipStopsImmediatelyWithoutSecondModelCall() = runBlocking {
        LocalEndpoint { _, socket, _ ->
            chatJsonReply(socket, """{"action":"SKIP"}""")
        }.use { auxEp ->
            LocalEndpoint { _, socket, _ ->
                chatJsonReply(socket, """{"action":"ADD","category":"FACT","importance":3,"content":"不应被调用"}""")
            }.use { activeEp -> RepoFixture().use { f ->
                val (auxCfg, _) = f.createConversation(auxEp.url, "aux-model")
                val (activeCfg, convId) = f.createConversation(activeEp.url, "main-model")
                f.settings.saveSettings(
                    f.settings.getSettings().copy(
                        auxiliaryMemoryEnabled = true,
                        auxiliaryMemoryApiConfigId = auxCfg.id,
                        auxiliaryMemoryModel = auxCfg.modelName
                    )
                )
                val msg = f.repository.saveMessage(Message(conversationId = convId, role = "user", content = "我今天随便看看新闻"))
                val res = f.repository.processSavedMessageMemory(msg, activeCfg.id, activeCfg.modelName)
                assertNull(res)
                assertEquals("辅助模型返回 JSON SKIP 后必须立刻停止", 1, auxEp.payloads.size)
                assertEquals("绝不能再重复调用昂贵的主聊天模型", 0, activeEp.payloads.size)
            } }
        }
    }

    // ==================== M2 可选 Embedding 语义向量与零依赖降级测试 ====================

    @Test
    fun embeddingToggleOffMakesZeroEmbeddingRequestsAndToggleOnRecallsCrossLexicalMemory() = runBlocking {
        val embeddingRequests = AtomicInteger()
        LocalEndpoint { path, socket, body ->
            if (path.contains("/embeddings")) {
                embeddingRequests.incrementAndGet()
                val inputs = body.getAsJsonArray("input").map { it.asString }
                val data = inputs.mapIndexed { idx, text ->
                    // 模拟跨词语义空间：“海鲜过敏”与“大虾螃蟹”映射到同一方向向量，其余正交
                    val vec = when {
                        text.contains("海鲜") || text.contains("甲壳") || text.contains("大虾") || text.contains("螃蟹") ->
                            listOf(0.98f, 0.15f, 0.05f, 0.0f)
                        else -> listOf(0.0f, 0.05f, 0.99f, 0.0f)
                    }
                    mapOf("index" to idx, "embedding" to vec)
                }
                writeHttp(socket, Gson().toJson(mapOf("data" to data, "usage" to mapOf("prompt_tokens" to 12, "total_tokens" to 12))))
            } else {
                streamOk(socket)
            }
        }.use { ep -> RepoFixture().use { f ->
            val (cfg, convId) = f.createConversation(ep.url, "synthetic-chat", extMem = true)
            f.db.memoryDao().insertMemory(MemoryItem(scope = "user", content = "对甲壳类海鲜严重过敏", category = "FACT", importance = 4))
            f.db.memoryDao().insertMemory(MemoryItem(scope = "user", content = "周三下午固定去健身房力量训练", category = "FACT", importance = 3))

            // 1. M2 关闭：0 次 /embeddings 请求，聊天全流程正常
            f.repository.sendChatMessageWithConfig(cfg, convId, "今晚想去吃蒜蓉大虾和螃蟹", onToken = {}, onComplete = { _, _, _ -> }, onError = { fail(it) })
            assertEquals("M2 关闭时不得发送任何 Embedding 请求", 0, embeddingRequests.get())

            // 2. M2 开启：跨词语义命中“对甲壳类海鲜严重过敏”并注入 Zone 3 尾注
            f.settings.saveSettings(
                f.settings.getSettings().copy(
                    embeddingEnabled = true,
                    embeddingApiConfigId = cfg.id,
                    embeddingModel = "text-embedding-3-small"
                )
            )
            val stats = f.repository.rebuildGlobalMemoryEmbeddings().getOrThrow()
            assertEquals(2, stats.indexedGlobalMemories)
            assertEquals(4, stats.dimension)
            assertTrue(embeddingRequests.get() >= 1)

            f.repository.sendChatMessageWithConfig(cfg, convId, "今晚想去吃蒜蓉大虾和螃蟹", onToken = {}, onComplete = { _, _, _ -> }, onError = { fail(it) })
            val lastChatPayload = ep.payloads.last { it.has("messages") }.toString()
            assertTrue("开启 M2 后应通过语义向量召回零词面重合的海鲜过敏记忆", lastChatPayload.contains("对甲壳类海鲜严重过敏"))
            assertFalse("无关健身记忆不应被召回", lastChatPayload.contains("健身房力量训练"))
            assertTrue(ep.failures.toString(), ep.failures.isEmpty())
        } }
    }

    @Test
    fun embeddingSpaceGuardPreventsCrossModelVectorMixingAndHandlesApiErrorsGracefully() {
        val spaceA = SemanticEmbeddingEngine.computeSpaceId("https://api.a.invalid/v1", "bge-m3", 1024)
        val spaceB = SemanticEmbeddingEngine.computeSpaceId("https://api.a.invalid/v1", "text-embedding-3-small", 1536)
        assertNotEquals(spaceA, spaceB)
        assertEquals(0f, SemanticEmbeddingEngine.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f, 0f)), 1e-6f)
        val encoded = SemanticEmbeddingEngine.encodeVector(listOf(0.5f, -0.25f, 1.0f))
        val decoded = SemanticEmbeddingEngine.decodeVector(encoded)
        assertArrayEquals(floatArrayOf(0.5f, -0.25f, 1.0f), decoded, 1e-6f)
        val rrf = SemanticEmbeddingEngine.fuseRrf(listOf(10L, 20L), listOf(20L, 30L))
        assertTrue("同时在两路靠前的 20L 融合分最高", rrf.getValue(20L) > rrf.getValue(30L))
    }

    // ==================== 痛点 #2/#3 分批断点、敏感隔离、超额压缩与 M3 剧情块测试 ====================

    @Test
    fun multiBatchCompressionCommitsCompletedBatchCheckpointAndResumesWithoutRepeatingWork() = runBlocking {
        val failSecondBatch = java.util.concurrent.atomic.AtomicBoolean(true)
        val summaryPrompts = CopyOnWriteArrayList<String>()
        LocalEndpoint { _, socket, payload ->
            val prompt = payload.getAsJsonArray("messages").single().asJsonObject.get("content").asString
            summaryPrompts += prompt
            if (summaryPrompts.size == 2 && failSecondBatch.get()) {
                writeHttp(socket, "{\"error\":{\"message\":\"transient gateway timeout\"}}", "503 Service Unavailable")
            } else {
                chatJsonReply(socket, "【核心背景与用户固定约束】\n第${summaryPrompts.size}批已完成合成归档，蓝印凭证仍在船长处。\n【当前未决议题与待办上下文】\n等待后续核对。")
            }
        }.use { ep -> RepoFixture().use { f ->
            val (_, convId) = f.createConversation(ep.url, "synthetic-chunk-model", tags = "story")
            // 构造 36 条带 story 标签的消息（每批最多 18 条 -> 严格拆成 2 批）
            val ids = (1..38).map { i ->
                f.db.messageDao().insertMessage(
                    Message(conversationId = convId, role = if (i % 2 == 1) "user" else "assistant", content = "长篇剧情第${i}条记录：港口线索核对。", createdAt = i.toLong())
                )
            }
            f.db.conversationDao().updateConversation(
                f.db.conversationDao().getConversationById(convId)!!.copy(compressionTier = CompressionTier.L4.level, contextWindowTokens = 32768)
            )

            // 第一次压缩：第 1 批成功落盘，第 2 批 503 失败
            val first = f.repository.compressConversationContext(convId)
            assertTrue("第 2 批失败应返回失败提示", first.isFailure)
            assertTrue("错误文案应告知已保存前 1 批断点", first.exceptionOrNull()?.message.orEmpty().contains("保存断点"))
            val midConv = f.db.conversationDao().getConversationById(convId)!!
            assertEquals("第 1 批的 18 条断点必须已持久化", ids[17], midConv.summaryUpdatedMessageId)
            assertEquals("应已保存第 1 个独立剧情块", 1, f.db.summaryChunkDao().getChunksList(convId).size)

            // 恢复网络（解除冷却）后第二次压缩：直接从第 19 条续压，绝不重发第 1~18 条！
            failSecondBatch.set(false)
            f.settings.apiKeyRotationManager.resetHealth(ep.url, "openai")
            val second = f.repository.compressConversationContext(convId).getOrThrow()
            assertEquals(3, summaryPrompts.size)
            assertFalse("续压请求不得重发已完成的第一批消息 #1", summaryPrompts.last().contains("原文ID ${ids[0]}]"))
            assertTrue("续压请求应从第 19 条继续", summaryPrompts.last().contains("原文ID ${ids[18]}]"))
            val finalConv = f.db.conversationDao().getConversationById(convId)!!
            assertEquals(ids[35], finalConv.summaryUpdatedMessageId)
            assertEquals(2, f.db.summaryChunkDao().getChunksList(convId).size)
            assertEquals(2, second.recentMessageCount)
        } }
    }

    @Test
    fun roleplaySensitiveContentRefusalBisectsIsolatesSingleBadMessageAndCompletesCompression() = runBlocking {
        val sensitiveMarker = "SENSITIVE_TRIGGER_SENTENCE_XYZ"
        LocalEndpoint { _, socket, payload ->
            val prompt = payload.getAsJsonArray("messages").single().asJsonObject.get("content").asString
            if (prompt.contains(sensitiveMarker)) {
                chatJsonReply(socket, "抱歉，由于安全政策限制，我无法总结包含敏感内容的部分。", finishReason = "content_filter")
            } else {
                chatJsonReply(socket, "【核心背景与固定约束】\n角色已抵达北港并完成情报交接，继续调查密信。\n【当前状态与未决伏笔】\n等待次日会面。")
            }
        }.use { ep -> RepoFixture().use { f ->
            val (_, convId) = f.createConversation(ep.url, "synthetic-rp-model", tags = "roleplay")
            val ids = (1..10).map { i ->
                val body = if (i == 5) "第5段剧情包含 $sensitiveMarker 冲突场景，随后两人各自离开大厅。"
                else "第${i}段正常剧情：领航员在北港核对蓝印并记录航海日志。"
                f.db.messageDao().insertMessage(
                    Message(conversationId = convId, role = if (i % 2 == 1) "user" else "assistant", content = body, createdAt = i.toLong())
                )
            }
            f.db.conversationDao().updateConversation(
                f.db.conversationDao().getConversationById(convId)!!.copy(compressionTier = CompressionTier.L4.level, contextWindowTokens = 32768)
            )
            val progresses = CopyOnWriteArrayList<AiRepository.CompressionProgress>()
            val result = f.repository.compressConversationContext(convId, onProgress = { progresses += it })
            assertTrue("创作模式遇局部敏感内容应通过二分隔离 + 安全摘录成功完成压缩: ${result.exceptionOrNull()?.message}", result.isSuccess)
            val saved = f.db.conversationDao().getConversationById(convId)!!
            assertEquals("全部 8 条早期消息水线必须推进到位", ids[7], saved.summaryUpdatedMessageId)
            val chunks = f.db.summaryChunkDao().getChunksList(convId)
            assertTrue("应包含模型成功总结的正常分段与 1 个局部安全摘录分段", chunks.any { it.sourceKind == "MODEL_NEUTRAL" } && chunks.any { it.sourceKind == "LOCAL_FALLBACK" })
            assertEquals("仅孤立敏感叶消息降级为本地安全摘录", 1, chunks.count { it.sourceKind == "LOCAL_FALLBACK" })
            assertTrue(progresses.last().fallbackIsolatedCount == 1)
        } }
    }

    @Test
    fun overBudgetConversationCanStillPerformStagedChunkCompressionInsteadOfDeadlocking() = runBlocking {
        LocalEndpoint { _, socket, _ ->
            chatJsonReply(socket, "【核心背景与用户固定约束】\n超额长对话早期内容已安全分段归档，核心主线保持完整。\n【当前未决议题与待办上下文】\n继续下一幕。")
        }.use { ep -> RepoFixture().use { f ->
            val (_, convId) = f.createConversation(ep.url, "synthetic-4k-model", tags = "story")
            // 构造明显超过 4K 窗口的长历史（每条 ~600+ tokens，12 条约 7200+ tokens >> 4K 窗口，即使 L2 保留最近 8 轮也远超窗口）
            val ids = (1..12).map { i ->
                f.db.messageDao().insertMessage(
                    Message(
                        conversationId = convId,
                        role = if (i % 2 == 1) "user" else "assistant",
                        content = "超额长章第${i}段：" + "北港船队记录了完整的补给清单与航行细节。".repeat(55),
                        createdAt = i.toLong()
                    )
                )
            }
            // 即便用户处于 L2（保留最近 8 轮，本身也超过 4K 窗口），手动压缩仍必须能分段跑完而不是被预算 check 死锁！
            f.db.conversationDao().updateConversation(
                f.db.conversationDao().getConversationById(convId)!!.copy(
                    compressionTier = CompressionTier.L2.level,
                    compressionRecentRounds = 8,
                    contextWindowTokens = 4000
                )
            )
            val result = f.repository.compressConversationContext(convId)
            assertTrue("上下文超额时必须能够执行分段压缩而非前置锁死: ${result.exceptionOrNull()?.message}", result.isSuccess)
            val saved = f.db.conversationDao().getConversationById(convId)!!
            assertNotNull(saved.rollingSummary)
            assertTrue("早期消息水线必须真实推进", (saved.summaryUpdatedMessageId ?: 0L) >= ids[7])
            assertTrue("应生成多个独立分段压缩块", f.db.summaryChunkDao().getChunksList(convId).isNotEmpty())
        } }
    }

    @Test
    fun plotChunkFourBucketAllocationPinsForeshadowingRecallsDistantAndKeepsChronology() {
        val chunks = (0..5).map { idx ->
            ConversationSummaryChunk(
                id = (idx + 1).toLong(),
                conversationId = 1L,
                chunkIndex = idx,
                startMessageId = idx * 10L + 1,
                endMessageId = idx * 10L + 10,
                messageCount = 10,
                summaryText = when (idx) {
                    0 -> "开篇伏笔：主角在钟楼暗格藏下了银色怀表。"
                    2 -> "中段支线：在古老钟楼地下室发现了隐藏密道与暗号。"
                    else -> "常规旅途第${idx}章：队伍沿海岸公路前进并休整。"
                },
                isPinned = idx == 0
            )
        }
        val split = LongTermMemoryEngine.selectPlotChunksForContext(
            chunks = chunks,
            query = "他们再次回到古老钟楼寻找地下室密道",
            recentChunkCount = 2,
            maxDistantRecalled = 2
        )
        assertEquals(listOf(1L), split.pinnedChunks.map { it.id })
        assertEquals(listOf(5L, 6L), split.recentChunks.map { it.id })
        assertEquals(listOf(3L), split.recalledDistantChunks.map { it.id })
        assertTrue(split.staticSummaryBlock!!.contains("银色怀表"))
        assertTrue(split.dynamicRecalledBlock!!.contains("隐藏密道"))
    }

    // ==================== 痛点 #1 设定与时间线近端强化 + 缓存稳定性 + 防预算崩溃测试 ====================

    @Test
    fun continuityAnchorReinforcesRelevantSettingsInZone3WithoutBreakingZone1CachePrefix() = runBlocking {
        LocalEndpoint { _, socket, _ -> streamOk(socket) }.use { ep -> RepoFixture().use { f ->
            val (cfg, convId) = f.createConversation(ep.url, "synthetic-story-model", tags = "story", extMem = false)
            f.db.conversationDao().updateConversation(
                f.db.conversationDao().getConversationById(convId)!!.copy(currentStoryTime = "第 5 天·黄昏")
            )
            f.db.memoryDao().insertMemory(
                MemoryItem(scope = "conversation", conversationId = convId, content = "【角色特征】林岚左手腕有一道旧伤，下雨天会隐隐作痛", category = "FACT")
            )
            f.db.timelineNodeDao().insertTimelineNode(
                TimelineNode(conversationId = convId, timeTag = "第 2 天·雨夜", event = "林岚在码头为了夺回铁盒撞伤了左手腕", orderIndex = 1)
            )

            f.repository.sendChatMessageWithConfig(cfg, convId, "外面下起暴雨了，林岚抬起左手腕准备开锁", onToken = {}, onComplete = { _, _, _ -> }, onError = { fail(it) })
            f.repository.sendChatMessageWithConfig(cfg, convId, "众人决定在旅馆大厅点一杯热茶休息", onToken = {}, onComplete = { _, _, _ -> }, onError = { fail(it) })

            assertEquals(2, ep.payloads.size)
            val sys1 = ep.payloads[0].getAsJsonArray("messages")[0].asJsonObject.get("content").asString
            val sys2 = ep.payloads[1].getAsJsonArray("messages")[0].asJsonObject.get("content").asString
            assertEquals("Zone 1 静态系统前缀跨轮次必须逐字节一致以保 KV Cache", sys1, sys2)

            val userTurn1 = ep.payloads[0].getAsJsonArray("messages").last().asJsonObject.get("content").asString
            assertTrue("本轮尾注应注入近端连续性锚点", userTurn1.contains("<active_continuity_anchor>"))
            assertTrue("相关设定必须贴近本轮输入提醒防止遗忘", userTurn1.contains("左手腕有一道旧伤"))
            assertTrue("故事当前时间锚点必须贴近本轮输入提醒", userTurn1.contains("第 5 天·黄昏"))
        } }
    }

    @Test
    fun manyDirectivesNeverThrowMemoryBudgetExceptionDuringSend() = runBlocking {
        LocalEndpoint { _, socket, _ -> streamOk(socket) }.use { ep -> RepoFixture().use { f ->
            val (cfg, convId) = f.createConversation(ep.url, "synthetic-small-window", extMem = true)
            f.db.conversationDao().updateConversation(
                f.db.conversationDao().getConversationById(convId)!!.copy(contextWindowTokens = 4000)
            )
            for (i in 1..40) {
                f.db.memoryDao().insertMemory(
                    MemoryItem(
                        scope = "user",
                        content = "行为约束第${i}条：必须严格遵守详细的代码规范与命名格式要求，不得遗漏测试注释和边界检查说明。",
                        category = "INSTRUCTION",
                        importance = 5
                    )
                )
            }
            var errorText: String? = null
            f.repository.sendChatMessageWithConfig(
                cfg, convId, "帮我看一下这个函数",
                onToken = {}, onComplete = { _, _, _ -> }, onError = { errorText = it }
            )
            assertNull("规则/约束条目较多时必须优雅裁剪预算，绝不能抛异常阻断发送: $errorText", errorText)
        } }
    }
}
