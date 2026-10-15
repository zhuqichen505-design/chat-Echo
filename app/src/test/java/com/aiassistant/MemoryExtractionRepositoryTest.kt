package com.aiassistant

import android.app.Application
import androidx.room.Room
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.data.repository.RoleplayRepository
import com.aiassistant.domain.model.*
import com.aiassistant.utils.CryptoManager
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.TavilySearchManager
import com.aiassistant.utils.WorldBookImport
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

    private fun roleplayRepository(f: Fixture) = RoleplayRepository(
        f.db.characterProfileDao(), f.db.roleplayScenarioDao(), f.db.roleplaySessionDao(),
        f.db.roleplayMemoryDao(), f.db.characterTagDao(), f.db.conversationDao(),
        f.db.messageDao(), f.db.worldBookDao(), f.db.memoryDao()
    )

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
                "待确认", action = "UPDATE", targetNodeId = nodeId, previousEventContent = "林岚抵达古塔",
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

    @Test fun editingAndDeletingCoveredHistoryInvalidatesSummaryAndManualEditKeepsRealCoverage() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation("https://model.invalid")
            val first = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "原始合成事实。"))
            val second = f.repository.saveMessage(Message(conversationId = id, role = "assistant", content = "已确认原始事实。"))
            suspend fun summary() = f.db.conversationDao().updateRollingSummary(id, "原始合成摘要。", second)
            summary()
            f.repository.updateRollingSummary(id, "人工更正的摘要。")
            assertEquals(second, f.db.conversationDao().getConversationById(id)!!.summaryUpdatedMessageId)
            val original = f.db.messageDao().getMessageById(first)!!
            f.repository.updateMessage(original.copy(content = "用户已更正合成事实。"))
            assertNull(f.db.conversationDao().getConversationById(id)!!.rollingSummary)
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
            val job = async(Dispatchers.IO) { f.repository.compressConversationContext(id) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val source = f.db.messageDao().getMessageById(ids.first())!!
                f.repository.updateMessage(source.copy(content = "更正：船队尚未抵达。"))
                released.countDown()
                assertTrue(withTimeout(5000) { job.await() }.isFailure)
                assertNull(f.db.conversationDao().getConversationById(id)!!.rollingSummary)
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

}
