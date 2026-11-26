package com.aiassistant

import android.app.Application
import androidx.room.Room
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.data.repository.RoleplayRepository
import com.aiassistant.data.repository.helpers.ChatContextAssemblyHelper
import com.aiassistant.data.repository.helpers.ContextCompressionPlanner
import com.aiassistant.data.repository.helpers.PromptCacheHelper
import com.aiassistant.domain.model.*
import com.aiassistant.utils.CryptoManager
import com.aiassistant.utils.EchoPromptCacheTtl
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.TavilySearchManager
import com.aiassistant.utils.TimelineMemoryHelper
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PromptCacheOptimizationTest {

    @Before fun allowSyntheticEndpoints() { HttpTestAccess.start() }
    @After fun restoreClosedHttpPolicy() { HttpTestAccess.stop() }

    @Test
    fun anthropicCachePlacerAddsThreeSlidingBreakpointsAndStripsStaleMarkers() {
        val staleCc = AnthropicCacheControl(type = "ephemeral", ttl = "1h")
        val messages = listOf(
            AnthropicMessage("user", listOf(AnthropicContent("text", "第一轮提问", cache_control = staleCc))),
            AnthropicMessage("assistant", listOf(AnthropicContent("text", "第一轮回复", cache_control = staleCc))),
            AnthropicMessage("user", "第二轮提问"),
            AnthropicMessage("assistant", "第二轮回复"),
            AnthropicMessage("user", "第三轮提问")
        )

        val result = PromptCacheHelper.applyAnthropicCacheBreakpoints(
            systemPrompt = "稳定系统设定",
            messages = messages,
            conversationId = 42L,
            enabled = true,
            ttl = EchoPromptCacheTtl.FIVE_MINUTES,
            tailVolatileBlock = "<turn_context>\n动态世界书\n</turn_context>",
            tailInstruction = "本轮方向指示"
        )

        assertEquals(3, result.breakpointCount)
        assertEquals("echo-conv-42", result.extraHeaders["X-Session-ID"])
        assertNull("默认 5m TTL 不应附带扩展 TTL beta 头", result.extraHeaders["anthropic-beta"])

        // BP1: system 为块数组且无 ttl 字段
        val sysBlocks = result.system as List<*>
        val sysBlock = sysBlocks.single() as AnthropicContent
        assertEquals("稳定系统设定", sysBlock.text)
        assertEquals("ephemeral", sysBlock.cache_control?.type)
        assertNull(sysBlock.cache_control?.ttl)

        // 旧标记全部清除
        val firstUserBlocks = result.messages[0].content as List<*>
        assertNull((firstUserBlocks.single() as AnthropicContent).cache_control)
        val firstAssistantBlocks = result.messages[1].content as List<*>
        assertNull((firstAssistantBlocks.single() as AnthropicContent).cache_control)

        // BP2: 落在最后一个历史 assistant (index = 3)
        val anchorBlocks = result.messages[3].content as List<*>
        assertEquals("ephemeral", (anchorBlocks.single() as AnthropicContent).cache_control?.type)

        // BP3: 落在当前 user 的稳定正文块，紧随其后的动态尾注与指令块不加缓存标记
        val lastUserBlocks = result.messages[4].content as List<*>
        assertEquals(3, lastUserBlocks.size)
        val stableUser = lastUserBlocks[0] as AnthropicContent
        val dynamicTail = lastUserBlocks[1] as AnthropicContent
        val instructionTail = lastUserBlocks[2] as AnthropicContent
        assertEquals("第三轮提问", stableUser.text)
        assertEquals("ephemeral", stableUser.cache_control?.type)
        assertTrue(dynamicTail.text!!.contains("动态世界书"))
        assertNull(dynamicTail.cache_control)
        assertEquals("本轮方向指示", instructionTail.text)
        assertNull(instructionTail.cache_control)
    }

    @Test
    fun anthropicCachePlacerSupportsOneHourTtlAndDirectionPlanningFourthBreakpoint() {
        val messages = listOf(
            AnthropicMessage("user", "历史问题"),
            AnthropicMessage("assistant", "历史回答"),
            AnthropicMessage("user", "当前问题")
        )
        val result = PromptCacheHelper.applyAnthropicCacheBreakpoints(
            systemPrompt = "静态角色设定",
            messages = messages,
            conversationId = 7L,
            enabled = true,
            ttl = EchoPromptCacheTtl.ONE_HOUR,
            tailVolatileBlock = "<turn_context>检索参考</turn_context>",
            tailInstruction = "规划指令",
            cacheTailVolatileBlock = true
        )

        assertEquals(4, result.breakpointCount)
        assertEquals(PromptCacheHelper.EXTENDED_TTL_BETA_HEADER, result.extraHeaders["anthropic-beta"])
        val lastBlocks = result.messages.last().content as List<*>
        assertEquals("1h", (lastBlocks[0] as AnthropicContent).cache_control?.ttl)
        assertEquals("1h", (lastBlocks[1] as AnthropicContent).cache_control?.ttl)
        assertNull("规划指令必须保持未缓存以便正式回复复用 BP4", (lastBlocks[2] as AnthropicContent).cache_control)
    }

    @Test
    fun anthropicCachePlacerDisabledPreservesPlainStringWireShape() {
        val result = PromptCacheHelper.applyAnthropicCacheBreakpoints(
            systemPrompt = "系统提示",
            messages = listOf(AnthropicMessage("user", "你好")),
            conversationId = 9L,
            enabled = false,
            tailVolatileBlock = "<turn_context>动态内容</turn_context>"
        )

        assertEquals(0, result.breakpointCount)
        assertTrue(result.extraHeaders.isEmpty())
        assertEquals("系统提示", result.system)
        assertEquals("你好\n\n<turn_context>动态内容</turn_context>", result.messages.single().content)
    }

    @Test
    fun openAiCacheHintsScopeVendorBodyFieldsSafelyByHost() {
        val openai = PromptCacheHelper.resolveOpenAiCacheHints(
            baseUrl = "https://api.openai.com/v1",
            provider = "OpenAI",
            modelName = "gpt-5.4",
            conversationId = 12L,
            enabled = true
        )
        assertEquals("echo-conv-12", openai.promptCacheKey)
        assertNull(openai.sessionId)
        assertNull(openai.cacheControl)
        assertEquals("echo-conv-12", openai.extraHeaders["X-Session-ID"])

        val openrouterClaude = PromptCacheHelper.resolveOpenAiCacheHints(
            baseUrl = "https://openrouter.ai/api/v1",
            provider = "OpenRouter",
            modelName = "anthropic/claude-sonnet-4-6",
            conversationId = 12L,
            enabled = true,
            ttl = EchoPromptCacheTtl.ONE_HOUR
        )
        assertNull(openrouterClaude.promptCacheKey)
        assertEquals("echo-conv-12", openrouterClaude.sessionId)
        assertEquals("ephemeral", openrouterClaude.cacheControl?.type)
        assertEquals("1h", openrouterClaude.cacheControl?.ttl)

        val openrouterGpt = PromptCacheHelper.resolveOpenAiCacheHints(
            baseUrl = "https://openrouter.ai/api/v1",
            provider = "OpenRouter",
            modelName = "openai/gpt-5.4",
            conversationId = 12L,
            enabled = true
        )
        assertEquals("echo-conv-12", openrouterGpt.promptCacheKey)
        assertEquals("echo-conv-12", openrouterGpt.sessionId)
        assertNull(openrouterGpt.cacheControl)

        val thirdPartyProxy = PromptCacheHelper.resolveOpenAiCacheHints(
            baseUrl = "https://gateway.example.invalid/v1",
            provider = "DeepSeek",
            modelName = "deepseek-chat",
            conversationId = 12L,
            enabled = true
        )
        assertNull("第三方未知代理不发送 prompt_cache_key 防 400", thirdPartyProxy.promptCacheKey)
        assertNull(thirdPartyProxy.sessionId)
        assertNull(thirdPartyProxy.cacheControl)
        assertEquals("echo-conv-12", thirdPartyProxy.extraHeaders["X-Session-ID"])

        val disabled = PromptCacheHelper.resolveOpenAiCacheHints(
            baseUrl = "https://api.openai.com/v1",
            provider = "OpenAI",
            modelName = "gpt-5.4",
            conversationId = 12L,
            enabled = false
        )
        assertNull(disabled.promptCacheKey)
        assertTrue(disabled.extraHeaders.isEmpty())
    }

    @Test
    fun deterministicTimelineAndSteppedTruncationProtectPrefixStability() {
        val nodes = (1..30).map { i ->
            TimelineNode(
                id = i.toLong(),
                conversationId = 1L,
                timeTag = "第 $i 天",
                event = "合成时间线节点 $i：港口记录编号 $i 的交接状态与人员变动。",
                orderIndex = i,
                createdAt = i * 1000L
            )
        }
        val (stable1, overflow1) = TimelineMemoryHelper.selectDeterministicTimelineNodesForContext(nodes, tokenBudget = 320)
        val (stable2, overflow2) = TimelineMemoryHelper.selectDeterministicTimelineNodesForContext(nodes, tokenBudget = 320)
        assertEquals("确定性时间线选择不依赖用户输入且完全可复现", stable1, stable2)
        assertEquals(overflow1, overflow2)
        assertTrue(stable1.isNotEmpty())
        assertTrue(overflow1.isNotEmpty())
        assertEquals(nodes.size, stable1.size + overflow1.size)
        assertEquals("稳定前缀必须保持正序", stable1.map { it.orderIndex }.sorted(), stable1.map { it.orderIndex })

        // 阶梯式窗口起点在步进区间内保持不变
        val starts = (11..15).map { total -> PromptCacheHelper.computeSteppedKeepStart(total, maxKeepMessages = 10, keepRatio = 0.5f) }
        assertEquals("11..15 条消息处于同一 5 条步进区间，前缀起点必须完全冻结", listOf(5, 5, 5, 5, 5), starts)
        assertEquals(10, PromptCacheHelper.computeSteppedKeepStart(16, maxKeepMessages = 10, keepRatio = 0.5f))

        // 预算溢出时 ContextCompressionPlanner 普通发送按 80% 滞后目标一次性腾出追加跑道
        val turns = (1L..20L).map { id ->
            Message(id = id, conversationId = 1L, role = if (id % 2 == 1L) "user" else "assistant",
                content = "合成长对话内容片段 $id：用于验证预算溢出后的阶梯滞后截断行为。".repeat(3))
        }
        val tightBudget = turns.takeLast(12).sumOf(ContextCompressionPlanner::messageTokens) - 10
        val normalPlan = ContextCompressionPlanner.plan(turns, CompressionTier.L0, tightBudget, null, null, forceCompression = false)
        val forcedPlan = ContextCompressionPlanner.plan(turns, CompressionTier.L0, tightBudget, null, null, forceCompression = true)
        assertTrue("滞后压缩应比紧贴预算截断多腾出追加空间", normalPlan.retainedMessages.size <= forcedPlan.retainedMessages.size)
        assertTrue(normalPlan.estimatedHistoryTokens <= PromptCacheHelper.overflowHysteresisTarget(tightBudget) || normalPlan.retainedMessages.size <= 2)
    }

    @Test
    fun threeZoneAssemblyKeepsSystemPrefixByteIdenticalAcrossVolatileTurnsInBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            val capturedPayloads = CopyOnWriteArrayList<JsonObject>()
            val capturedHeaders = CopyOnWriteArrayList<Map<String, String>>()
            val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            val executor = Executors.newSingleThreadExecutor()
            val url = "http://127.0.0.1:${server.localPort}/v1"
            HttpTestAccess.allow(url)
            val acceptJob = executor.submit {
                var count = 0
                while (!server.isClosed && count < 4) {
                    val socket = try { server.accept() } catch (_: Exception) { break }
                    count++
                    handleHttp(socket, capturedPayloads, capturedHeaders, protocol, count)
                }
            }
            val context = RuntimeEnvironment.getApplication()
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            val app = AiAssistantApp()
            val instanceField = AiAssistantApp::class.java.getDeclaredField("instance").apply { isAccessible = true }
            val previousInstance = instanceField.get(null)
            try {
                instanceField.set(null, app)
                AiAssistantApp::class.java.getDeclaredField("database").apply { isAccessible = true }.set(app, db)
                val manager = PersonalizationManager(context)
                manager.setModelRetryEnabled(false)
                manager.setPromptCachingEnabled(true)
                manager.setPromptCacheTtl(EchoPromptCacheTtl.FIVE_MINUTES)
                val repo = createRepository(context, db, manager)
                val configId = db.apiConfigDao().insertConfig(
                    ApiConfig(
                        name = "Synthetic $protocol",
                        provider = if (protocol == "anthropic") "Anthropic" else "Synthetic",
                        baseUrl = url,
                        apiKey = "test-key",
                        apiType = protocol,
                        modelName = if (protocol == "anthropic") "claude-sonnet-4-6" else "deepseek-chat",
                        enableThinking = false,
                        enableWebSearch = false
                    )
                )
                val bookId = db.worldBookDao().insertBook(WorldBook(name = "合成三区世界书", isEnabled = true))
                db.worldBookDao().insertEntry(
                    WorldBookEntry(bookId = bookId, name = "常驻法典", keys = "", content = "CONST_RULE_STABLE_ALPHA", isConstant = true)
                )
                db.worldBookDao().insertEntry(
                    WorldBookEntry(bookId = bookId, name = "火龙词条", keys = "赤龙", content = "DYNAMIC_DRAGON_LORE_BETA", isConstant = false)
                )
                db.worldBookDao().insertEntry(
                    WorldBookEntry(bookId = bookId, name = "港口词条", keys = "北港", content = "DYNAMIC_HARBOR_LORE_GAMMA", isConstant = false)
                )
                val convId = db.conversationDao().insertConversation(
                    Conversation(
                        title = "三区稳定性测试",
                        apiConfigId = configId,
                        modelName = if (protocol == "anthropic") "claude-sonnet-4-6" else "deepseek-chat",
                        tags = "story",
                        systemPrompt = "静态故事底设",
                        enableSessionMemory = true,
                        enableWorldBook = true,
                        activeWorldBookIds = bookId.toString()
                    )
                )
                // 插入两条不同主题的会话记忆，验证不随用户输入重排顺序
                db.memoryDao().insertMemory(MemoryItem(scope = "conversation", conversationId = convId, content = "角色甲擅长修复钟表齿轮", createdAt = 100L))
                db.memoryDao().insertMemory(MemoryItem(scope = "conversation", conversationId = convId, content = "角色乙随身携带铜制罗盘", createdAt = 200L))

                val config = db.apiConfigDao().getConfigById(configId)!!
                val opts = ChatRequestOptions(
                    enableThinking = false,
                    enableWebSearch = false,
                    enableSessionMemory = true,
                    enableWorldBook = true,
                    activeWorldBookIds = bookId.toString()
                )

                // 第 1 轮：触发“赤龙” + 提及“铜制罗盘”
                repo.saveMessage(Message(conversationId = convId, role = "user", content = "我们前往赤龙巢穴，检查铜制罗盘"))
                repo.sendChatMessageWithConfig(config, convId, "我们前往赤龙巢穴，检查铜制罗盘", options = opts,
                    onToken = {}, onComplete = { _, _, _ -> }, onError = { fail(it) })

                // 第 2 轮：切换关键词“北港” + 提及“钟表齿轮” + 传入本轮 replyDirection
                repo.saveMessage(Message(conversationId = convId, role = "user", content = "改去北港修理钟表齿轮"))
                repo.sendChatMessageWithConfig(config, convId, "改去北港修理钟表齿轮",
                    options = opts.copy(replyDirection = "本轮请侧重环境氛围描写"),
                    onToken = {}, onComplete = { _, _, _ -> }, onError = { fail(it) })

                assertEquals(2, capturedPayloads.size)
                val req1 = capturedPayloads[0]
                val req2 = capturedPayloads[1]

                // 验证 X-Session-ID 路由头一致
                assertEquals("echo-conv-$convId", capturedHeaders[0]["x-session-id"])
                assertEquals("echo-conv-$convId", capturedHeaders[1]["x-session-id"])

                val sys1 = extractSystemText(req1, protocol)
                val sys2 = extractSystemText(req2, protocol)

                // 核心断言：两轮间即使会话记忆关键词、动态世界书词条与回复方向全部变化，Zone 1 system 必须 100% 字节级一致！
                assertEquals("两轮之间静态 system 前缀必须逐字节一致 ($protocol)", sys1, sys2)
                assertTrue("常驻世界书必须留在 Zone 1 system", sys1.contains("CONST_RULE_STABLE_ALPHA"))
                assertFalse("关键词触发世界书不得污染 Zone 1 system", sys1.contains("DYNAMIC_DRAGON_LORE_BETA"))
                assertFalse("关键词触发世界书不得污染 Zone 1 system", sys2.contains("DYNAMIC_HARBOR_LORE_GAMMA"))
                assertFalse("本轮 replyDirection 不得污染 Zone 1 system", sys2.contains("本轮请侧重环境氛围描写"))
                assertTrue("会话记忆应按稳定时间顺序排列且不随提问重排",
                    sys1.indexOf("钟表齿轮") < sys1.indexOf("铜制罗盘") &&
                        sys2.indexOf("钟表齿轮") < sys2.indexOf("铜制罗盘"))

                // 验证动态词条与 replyDirection 正确进入 Zone 3（当前轮 user 尾注）
                val turn1LastUser = extractMessageText(req1.getAsJsonArray("messages").last().asJsonObject["content"])
                val turn2LastUser = extractMessageText(req2.getAsJsonArray("messages").last().asJsonObject["content"])
                assertTrue(turn1LastUser.contains("DYNAMIC_DRAGON_LORE_BETA"))
                assertTrue(turn2LastUser.contains("DYNAMIC_HARBOR_LORE_GAMMA"))
                assertTrue(turn2LastUser.contains("本轮请侧重环境氛围描写"))

                // 验证 Anthropic 协议确已写入 cache_control 断点，且诊断记录命中
                if (protocol == "anthropic") {
                    val sysArr = req2.getAsJsonArray("system")
                    assertEquals("ephemeral", sysArr[0].asJsonObject.getAsJsonObject("cache_control")["type"].asString)
                    val snap = repo.getLastPromptCacheSnapshot(convId)!!
                    assertEquals(PromptCacheHelper.PromptCacheStatus.HIT, snap.status)
                    assertEquals(3, snap.cacheBreakpointsPlaced)
                    assertEquals(120, snap.cacheReadTokens)
                } else {
                    val snap = repo.getLastPromptCacheSnapshot(convId)!!
                    assertEquals(PromptCacheHelper.PromptCacheStatus.HIT, snap.status)
                    assertEquals(120, snap.cacheReadTokens)
                }
            } finally {
                instanceField.set(null, previousInstance)
                db.close()
                server.close()
                executor.shutdownNow()
                executor.awaitTermination(2, TimeUnit.SECONDS)
                acceptJob.cancel(true)
            }
        }
    }

    @Test
    fun directionPlanningAndFinalReplyShareExactSystemAndHistoryPrefix() = runBlocking {
        val plannerJson = """{"directions":[{"title":"探险","description":"深入旧灯塔内部调查"},{"title":"休整","description":"先回港口整理线索"}]}"""
        val gson = Gson()
        for (protocol in listOf("openai", "anthropic")) {
            val capturedPayloads = CopyOnWriteArrayList<JsonObject>()
            val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            val executor = Executors.newSingleThreadExecutor()
            val url = "http://127.0.0.1:${server.localPort}/v1"
            HttpTestAccess.allow(url)
            val acceptJob = executor.submit {
                var count = 0
                while (!server.isClosed && count < 2) {
                    val socket = try { server.accept() } catch (_: Exception) { break }
                    count++
                    val text = if (count == 1) plannerJson else "正式回复正文"
                    handleCustomTextHttp(socket, capturedPayloads, protocol, gson.toJson(text))
                }
            }
            val context = RuntimeEnvironment.getApplication()
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            val app = AiAssistantApp()
            val instanceField = AiAssistantApp::class.java.getDeclaredField("instance").apply { isAccessible = true }
            val previousInstance = instanceField.get(null)
            try {
                instanceField.set(null, app)
                AiAssistantApp::class.java.getDeclaredField("database").apply { isAccessible = true }.set(app, db)
                val manager = PersonalizationManager(context)
                manager.setModelRetryEnabled(false)
                manager.setPromptCachingEnabled(true)
                val repo = createRepository(context, db, manager)
                val configId = db.apiConfigDao().insertConfig(
                    ApiConfig(
                        name = "Dir $protocol",
                        provider = if (protocol == "anthropic") "Anthropic" else "Synthetic",
                        baseUrl = url,
                        apiKey = "test-key",
                        apiType = protocol,
                        modelName = if (protocol == "anthropic") "claude-sonnet-4-6" else "deepseek-chat",
                        enableThinking = false,
                        enableWebSearch = false
                    )
                )
                val bookId = db.worldBookDao().insertBook(WorldBook(name = "方向测试书", isEnabled = true))
                db.worldBookDao().insertEntry(WorldBookEntry(bookId = bookId, name = "常驻", keys = "", content = "CONST_DIR_RULE", isConstant = true))
                db.worldBookDao().insertEntry(WorldBookEntry(bookId = bookId, name = "灯塔", keys = "旧灯塔", content = "DYNAMIC_LIGHTHOUSE_LORE", isConstant = false))
                val convId = db.conversationDao().insertConversation(
                    Conversation(title = "方向缓存共享", apiConfigId = configId, modelName = if (protocol == "anthropic") "claude-sonnet-4-6" else "deepseek-chat",
                        tags = "story", systemPrompt = "静态角色设定", enableWorldBook = true, activeWorldBookIds = bookId.toString())
                )
                db.messageDao().insertMessage(Message(conversationId = convId, role = "user", content = "我们来到旧灯塔门口", createdAt = 1L))
                db.messageDao().insertMessage(Message(conversationId = convId, role = "assistant", content = "海风卷起咸湿的雾气", createdAt = 2L))
                db.messageDao().insertMessage(Message(conversationId = convId, role = "user", content = "接下来怎么办", createdAt = 3L))

                val config = db.apiConfigDao().getConfigById(configId)!!
                val opts = ChatRequestOptions(enableThinking = false, enableWebSearch = false, enableSessionMemory = false,
                    enableWorldBook = true, activeWorldBookIds = bookId.toString())
                val prepared = repo.prepareReplyDirectionContext(config, convId, "接下来怎么办", opts)
                val dirs = repo.generateReplyDirections(config, convId, "接下来怎么办", emptyList(), opts.copy(preparedDirectionContext = prepared), 2)
                val chosen = ReplyDirections.instruction(ReplyDirectionDecision(ReplyDirectionAction.SELECT, 0), dirs)
                repo.sendChatMessageWithConfig(config, convId, "接下来怎么办",
                    options = opts.copy(preparedDirectionContext = prepared, replyDirection = chosen),
                    onToken = {}, onComplete = { _, _, _ -> }, onError = { fail(it) })

                assertEquals(2, capturedPayloads.size)
                val planReq = capturedPayloads[0]
                val finalReq = capturedPayloads[1]

                // 核心断言：方向规划调用与最终正式回复共享 100% 字节级一致的 system 前缀！
                assertEquals("方向规划与正式回复的静态 system 必须完全一致 ($protocol)",
                    extractSystemText(planReq, protocol), extractSystemText(finalReq, protocol))
                assertFalse("replyDirection 不得进入 system", extractSystemText(finalReq, protocol).contains("深入旧灯塔内部调查"))

                if (protocol == "anthropic") {
                    val planUserBlocks = planReq.getAsJsonArray("messages").last().asJsonObject.getAsJsonArray("content")
                    assertEquals(3, planUserBlocks.size())
                    assertEquals("ephemeral", planUserBlocks[0].asJsonObject.getAsJsonObject("cache_control")["type"].asString)
                    assertEquals("ephemeral", planUserBlocks[1].asJsonObject.getAsJsonObject("cache_control")["type"].asString)
                    assertFalse("规划指令自身不标记缓存断点", planUserBlocks[2].asJsonObject.has("cache_control"))
                }
            } finally {
                instanceField.set(null, previousInstance)
                db.close()
                server.close()
                executor.shutdownNow()
                executor.awaitTermination(2, TimeUnit.SECONDS)
                acceptJob.cancel(true)
            }
        }
    }

    @Test
    fun roleplaySessionMemoryToggleAndConversationTagIsolationWorkAsExpected() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val convId = db.conversationDao().insertConversation(
                Conversation(title = "故事", tags = "story", apiConfigId = 1L, modelName = "m")
            )
            assertTrue(ChatContextAssemblyHelper.isRoleplayConversation(db.conversationDao().getConversationById(convId)))

            val sessionId = db.roleplaySessionDao().insertSession(RoleplaySession(conversationId = convId))
            db.roleplayMemoryDao().insertMemory(RoleplayMemory(sessionId = sessionId, memoryType = "fact", content = "角色固有秘密", isPinned = true))
            db.memoryDao().insertMemory(MemoryItem(scope = "conversation", conversationId = convId, content = "会话专属记忆不重复"))

            val rpRepo = RoleplayRepository(
                characterProfileDao = db.characterProfileDao(),
                roleplayScenarioDao = db.roleplayScenarioDao(),
                roleplaySessionDao = db.roleplaySessionDao(),
                roleplayMemoryDao = db.roleplayMemoryDao(),
                characterTagDao = db.characterTagDao(),
                worldBookDao = db.worldBookDao(),
                messageDao = db.messageDao(),
                conversationDao = db.conversationDao(),
                memoryDao = db.memoryDao()
            )
            val standalone = rpRepo.assembleRoleplayContext(sessionId, null, null, includeHistory = false)
            assertTrue(standalone.contains("角色固有秘密"))
            assertTrue(standalone.contains("会话专属记忆不重复"))

            val chatPath = rpRepo.assembleRoleplayContext(
                sessionId, null, null, includeHistory = false,
                includeWorldBook = false, includeTimelineContext = false, includeSessionMemories = false
            )
            assertTrue(chatPath.contains("角色固有秘密"))
            assertFalse("聊天主链路关闭 includeSessionMemories 后不得重复注入 conversation memory", chatPath.contains("会话专属记忆不重复"))
        } finally {
            db.close()
        }
    }

    @Test
    fun promptCachePreferencesPersistAndSanitize() {
        val context = RuntimeEnvironment.getApplication()
        val manager = PersonalizationManager(context)
        assertTrue(manager.isPromptCachingEnabled())
        assertEquals(EchoPromptCacheTtl.FIVE_MINUTES, manager.getPromptCacheTtl())

        assertTrue(manager.setPromptCachingEnabled(false))
        assertFalse(PersonalizationManager(context).isPromptCachingEnabled())
        assertTrue(manager.setPromptCachingEnabled(true))

        assertTrue(manager.setPromptCacheTtl(EchoPromptCacheTtl.ONE_HOUR))
        assertEquals(EchoPromptCacheTtl.ONE_HOUR, PersonalizationManager(context).getPromptCacheTtl())
        assertTrue(manager.setPromptCacheTtl("invalid-ttl"))
        assertEquals(EchoPromptCacheTtl.FIVE_MINUTES, PersonalizationManager(context).getPromptCacheTtl())
    }

    @Test
    fun cacheDiagnosticsAttributesMissReasonsAccurately() {
        val turn1 = PromptCacheHelper.evaluateDiagnostics(
            previous = null, conversationId = 1L, protocol = "openai", modelName = "m1",
            systemPrefixHash = "sysA", historyPrefixHash = "hist1", previousHistoryPrefixHash = "",
            historyMessageCount = 0, cacheBreakpointsPlaced = 0,
            inputTokens = 1000, cacheReadTokens = 0, cacheCreationTokens = 0
        )
        assertEquals(PromptCacheHelper.PromptCacheStatus.COLD_START, turn1.status)

        val sysChanged = PromptCacheHelper.evaluateDiagnostics(
            previous = turn1, conversationId = 1L, protocol = "openai", modelName = "m1",
            systemPrefixHash = "sysB", historyPrefixHash = "hist2", previousHistoryPrefixHash = "hist1",
            historyMessageCount = 2, cacheBreakpointsPlaced = 0,
            inputTokens = 1200, cacheReadTokens = 0, cacheCreationTokens = 0
        )
        assertEquals(PromptCacheHelper.PromptCacheStatus.MISS_SYSTEM_CHANGED, sysChanged.status)

        val modelChanged = PromptCacheHelper.evaluateDiagnostics(
            previous = sysChanged, conversationId = 1L, protocol = "openai", modelName = "m2",
            systemPrefixHash = "sysB", historyPrefixHash = "hist3", previousHistoryPrefixHash = "hist2",
            historyMessageCount = 4, cacheBreakpointsPlaced = 0,
            inputTokens = 1400, cacheReadTokens = 0, cacheCreationTokens = 0
        )
        assertEquals(PromptCacheHelper.PromptCacheStatus.MISS_MODEL_CHANGED, modelChanged.status)

        val historyEdited = PromptCacheHelper.evaluateDiagnostics(
            previous = modelChanged, conversationId = 1L, protocol = "openai", modelName = "m2",
            systemPrefixHash = "sysB", historyPrefixHash = "hist4", previousHistoryPrefixHash = "DIFF_PREFIX",
            historyMessageCount = 4, cacheBreakpointsPlaced = 0,
            inputTokens = 1400, cacheReadTokens = 0, cacheCreationTokens = 0
        )
        assertEquals(PromptCacheHelper.PromptCacheStatus.MISS_HISTORY_REWRITTEN, historyEdited.status)

        val upstreamMiss = PromptCacheHelper.evaluateDiagnostics(
            previous = historyEdited, conversationId = 1L, protocol = "openai", modelName = "m2",
            systemPrefixHash = "sysB", historyPrefixHash = "hist5", previousHistoryPrefixHash = "hist4",
            historyMessageCount = 6, cacheBreakpointsPlaced = 0,
            inputTokens = 1600, cacheReadTokens = 0, cacheCreationTokens = 0
        )
        assertEquals(PromptCacheHelper.PromptCacheStatus.MISS_UPSTREAM_NO_CACHE, upstreamMiss.status)

        val hit = PromptCacheHelper.evaluateDiagnostics(
            previous = upstreamMiss, conversationId = 1L, protocol = "openai", modelName = "m2",
            systemPrefixHash = "sysB", historyPrefixHash = "hist6", previousHistoryPrefixHash = "hist5",
            historyMessageCount = 8, cacheBreakpointsPlaced = 0,
            inputTokens = 2000, cacheReadTokens = 1600, cacheCreationTokens = 0
        )
        assertEquals(PromptCacheHelper.PromptCacheStatus.HIT, hit.status)
        assertEquals(0.8f, hit.hitRate, 0.001f)
    }

    private fun createRepository(context: Application, db: AppDatabase, manager: PersonalizationManager): AiRepository {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) }
        val crypto = unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, CryptoManager::class.java) as CryptoManager
        return AiRepository(
            folderDao = db.folderDao(),
            apiConfigDao = db.apiConfigDao(),
            conversationDao = db.conversationDao(),
            messageDao = db.messageDao(),
            usageStatDao = db.usageStatDao(),
            environmentVariableDao = db.environmentVariableDao(),
            promptTemplateDao = db.promptTemplateDao(),
            memoryDao = db.memoryDao(),
            conversationBranchDao = db.conversationBranchDao(),
            selectedModelDao = db.selectedModelDao(),
            cryptoManager = crypto,
            personalizationManager = manager,
            tavilySearchManager = TavilySearchManager(context, crypto),
            worldBookDao = db.worldBookDao(),
            timelineNodeDao = db.timelineNodeDao(),
            firstTokenTimeoutMillis = 5000L
        )
    }

    private fun readHttpRequest(socket: Socket): Pair<Map<String, String>, String> {
        val input = socket.getInputStream()
        val headerBuf = java.io.ByteArrayOutputStream()
        var state = 0
        while (true) {
            val b = input.read()
            if (b < 0) break
            headerBuf.write(b)
            state = when (state) {
                0 -> if (b == '\r'.code) 1 else 0
                1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
                2 -> if (b == '\r'.code) 3 else 0
                3 -> if (b == '\n'.code) 4 else if (b == '\r'.code) 1 else 0
                else -> 0
            }
            if (state == 4) break
        }
        val headerText = headerBuf.toString(Charsets.UTF_8.name())
        val lines = headerText.split("\r\n")
        val headers = mutableMapOf<String, String>()
        var contentLength = 0
        for (line in lines.drop(1)) {
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                val k = line.substring(0, idx).trim().lowercase()
                val v = line.substring(idx + 1).trim()
                headers[k] = v
                if (k == "content-length") contentLength = v.toIntOrNull() ?: 0
            }
        }
        val bodyBytes = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(bodyBytes, offset, contentLength - offset)
            if (read <= 0) break
            offset += read
        }
        return headers to String(bodyBytes, 0, offset, Charsets.UTF_8)
    }

    private fun handleHttp(
        socket: Socket,
        payloads: CopyOnWriteArrayList<JsonObject>,
        headersOut: CopyOnWriteArrayList<Map<String, String>>,
        protocol: String,
        turn: Int
    ) {
        socket.use { s ->
            val (headers, bodyStr) = readHttpRequest(s)
            if (bodyStr.isBlank()) return
            headersOut += headers
            payloads += JsonParser.parseString(bodyStr).asJsonObject

            val cached = if (turn >= 2) 120 else 0
            val sseBody = if (protocol == "openai") {
                "data: {\"choices\":[{\"delta\":{\"content\":\"回复 $turn\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":200,\"completion_tokens\":10,\"total_tokens\":210,\"prompt_tokens_details\":{\"cached_tokens\":$cached}}}\n\n" +
                    "data: [DONE]\n\n"
            } else {
                "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":80,\"cache_read_input_tokens\":$cached,\"cache_creation_input_tokens\":40}}}\n\n" +
                    "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"回复 $turn\"}}\n\n" +
                    "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":10}}\n\n" +
                    "data: {\"type\":\"message_stop\"}\n\n"
            }
            val bytes = sseBody.toByteArray(Charsets.UTF_8)
            val response = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            s.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            s.getOutputStream().write(bytes)
            s.getOutputStream().flush()
        }
    }

    private fun handleCustomTextHttp(
        socket: Socket,
        payloads: CopyOnWriteArrayList<JsonObject>,
        protocol: String,
        jsonEncodedText: String
    ) {
        socket.use { s ->
            val (_, bodyStr) = readHttpRequest(s)
            if (bodyStr.isBlank()) return
            payloads += JsonParser.parseString(bodyStr).asJsonObject
            val sseBody = if (protocol == "openai") {
                "data: {\"choices\":[{\"delta\":{\"content\":$jsonEncodedText},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
            } else {
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":$jsonEncodedText}}\n\ndata: {\"type\":\"message_stop\"}\n\n"
            }
            val bytes = sseBody.toByteArray(Charsets.UTF_8)
            val response = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            s.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            s.getOutputStream().write(bytes)
            s.getOutputStream().flush()
        }
    }

    private fun extractSystemText(body: JsonObject, protocol: String): String =
        if (protocol == "openai") {
            extractMessageText(body.getAsJsonArray("messages").first { it.asJsonObject["role"].asString == "system" }.asJsonObject["content"])
        } else {
            extractMessageText(body["system"])
        }

    private fun extractMessageText(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        if (element.isJsonPrimitive) return element.asString
        if (element.isJsonArray) {
            return element.asJsonArray.mapNotNull { block ->
                val obj = block.takeIf { it.isJsonObject }?.asJsonObject
                obj?.get("text")?.takeUnless { it.isJsonNull }?.asString
            }.joinToString("\n\n")
        }
        return element.toString()
    }
}
