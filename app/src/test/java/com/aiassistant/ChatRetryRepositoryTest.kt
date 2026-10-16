package com.aiassistant

import android.app.Application
import androidx.room.Room
import androidx.lifecycle.viewModelScope
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.data.repository.ChatGenerationManager
import com.aiassistant.data.repository.RoleplayRepository
import com.aiassistant.domain.model.*
import com.aiassistant.ui.screens.chat.ChatViewModel
import com.aiassistant.utils.CryptoManager
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.TavilySearchManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
        val app = AiAssistantApp()
        private val instance = AiAssistantApp::class.java.getDeclaredField("instance").apply { isAccessible = true }
        private val previous = instance.get(null)
        init {
            context.getSharedPreferences("personalization_settings", 0).edit().clear().commit()
            settings.setBackupKeyFallbackEnabled(false)
            settings.saveSettings(settings.getSettings().copy(autoNameEnabled = false, autoMemoryEnabled = false, auxiliaryMemoryEnabled = false))
            instance.set(null, app)
            AiAssistantApp::class.java.getDeclaredField("database").apply { isAccessible = true }.set(app, db)
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) }
            val crypto = unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, CryptoManager::class.java) as CryptoManager
            repository = AiRepository(db.folderDao(), db.apiConfigDao(), db.conversationDao(), db.messageDao(), db.usageStatDao(),
                db.environmentVariableDao(), db.promptTemplateDao(), db.memoryDao(), db.conversationBranchDao(), db.selectedModelDao(),
                crypto, settings, TavilySearchManager(context, crypto), worldBookDao = db.worldBookDao(),
                timelineNodeDao = db.timelineNodeDao(), firstTokenTimeoutMillis = timeout)
        }
        fun enableViewModel() {
            android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
                .apply { isAccessible = true }.invoke(app, context)
            AiAssistantApp::class.java.getDeclaredField("repository").apply { isAccessible = true }.set(app, repository)
            AiAssistantApp::class.java.getDeclaredField("personalizationManager").apply { isAccessible = true }.set(app, settings)
            val roleplay = RoleplayRepository(db.characterProfileDao(), db.roleplayScenarioDao(), db.roleplaySessionDao(),
                db.roleplayMemoryDao(), db.characterTagDao())
            AiAssistantApp::class.java.getDeclaredField("roleplayRepository").apply { isAccessible = true }.set(app, roleplay)
        }
        suspend fun config(url: String, protocol: String = "openai", keys: String = "test-only"): Pair<ApiConfig, Long> {
            val config = ApiConfig(name = "synthetic", provider = "proxy", apiType = protocol, baseUrl = url,
                apiKey = keys, modelName = "vendor/Exact-ID", enableThinking = false)
            val configId = db.apiConfigDao().insertConfig(config)
            val id = db.conversationDao().insertConversation(Conversation(title = "retry regression", apiConfigId = configId, modelName = config.modelName))
            return config.copy(id = configId) to id
        }
        override fun close() {
            runBlocking { app.applicationScope.coroutineContext[Job]?.cancelAndJoin() }
            db.close()
            instance.set(null, previous)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun contextRefreshCannotClearBusyStateAndCancellationKeepsOriginalHistory() = runBlocking {
        newSingleThreadContext("context-operation-main").use { main ->
            Dispatchers.setMain(main)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            try {
                Endpoint { socket, _ ->
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    socket.response("200 OK", "{}")
                }.use { endpoint -> Fixture().use { f ->
                    f.enableViewModel()
                    val (config, id) = f.config(endpoint.url)
                    f.db.apiConfigDao().updateConfig(config.copy(apiKey = ""))
                    repeat(40) { index ->
                        f.db.messageDao().insertMessage(Message(conversationId = id,
                            role = if (index % 2 == 0) "user" else "assistant",
                            content = "合成上下文消息 $index。", createdAt = index.toLong(),
                            // Include normal saved-reply metadata so ViewModel's legacy backfill
                            // is independent of this complete-row cancellation comparison.
                            modelName = if (index % 2 == 1) config.modelName else null))
                    }
                    val original = f.db.messageDao().getMessagesList(id)
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (vm.contextUsage.value.usage == null) delay(10) }
                        withContext(Dispatchers.Main) { vm.compressContextNow() }
                        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
                        val runningStatus = vm.contextUsage.value.statusMessage
                        f.db.conversationDao().updateContextWindowTokens(id, 32768, System.currentTimeMillis())
                        withContext(Dispatchers.Main) { vm.refreshContextUsage() }
                        withTimeout(5000) { while (vm.contextUsage.value.usage?.contextWindowTokens != 32768) delay(10) }
                        assertTrue("刷新不能提前结束仍在请求的压缩任务", vm.contextUsage.value.isCompressing)
                        assertEquals(runningStatus, vm.contextUsage.value.statusMessage)
                        withContext(Dispatchers.Main) {
                            vm.compressContextNow()
                            vm.generateRollingSummaryNow()
                        }
                        assertEquals("忙时重复点击不得创建额外请求", 1, endpoint.payloads.size)
                        withContext(Dispatchers.Main) { vm.cancelContextOperation() }
                        withTimeout(5000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                        assertFalse(vm.contextUsage.value.isGeneratingSummary)
                        assertTrue(vm.contextUsage.value.statusMessage!!.contains("已取消"))
                        assertNull(f.db.conversationDao().getConversationById(id)!!.rollingSummary)
                        assertEquals(original, f.db.messageDao().getMessagesList(id))
                        f.db.conversationDao().updateContextWindowTokens(id, 65536, System.currentTimeMillis())
                        withContext(Dispatchers.Main) { vm.refreshContextUsage() }
                        withTimeout(5000) { while (vm.contextUsage.value.usage?.contextWindowTokens != 65536) delay(10) }
                        assertTrue("刷新应保留取消结果", vm.contextUsage.value.statusMessage!!.contains("已取消"))
                    } finally {
                        release.countDown()
                        vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                    }
                } }
            } finally {
                release.countDown()
                Dispatchers.resetMain()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun manualSummaryEditCancelsPendingCompressionBeforeSavingUserText() = runBlocking {
        newSingleThreadContext("context-summary-edit-main").use { main ->
            Dispatchers.setMain(main)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            try {
                Endpoint { socket, _ ->
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    socket.response("200 OK", "{}")
                }.use { endpoint -> Fixture().use { f ->
                    f.enableViewModel()
                    val (config, id) = f.config(endpoint.url)
                    f.db.apiConfigDao().updateConfig(config.copy(apiKey = ""))
                    repeat(40) { index ->
                        f.db.messageDao().insertMessage(Message(conversationId = id,
                            role = if (index % 2 == 0) "user" else "assistant",
                            content = "用于取消测试的合成内容 $index。", createdAt = index.toLong()))
                    }
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (vm.contextUsage.value.usage == null) delay(10) }
                        withContext(Dispatchers.Main) { vm.compressContextNow() }
                        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
                        val manualSummary = "用户已审阅并修订的合成摘要。"
                        withContext(Dispatchers.Main) { vm.updateRollingSummary(manualSummary) }
                        withTimeout(5000) {
                            while (f.db.conversationDao().getConversationById(id)?.rollingSummary != manualSummary) delay(10)
                        }
                        assertFalse(vm.contextUsage.value.isCompressing)
                        assertEquals(1, endpoint.payloads.size)
                        assertEquals(40, f.db.messageDao().getMessagesList(id).size)
                        assertEquals(manualSummary, f.db.conversationDao().getConversationById(id)!!.rollingSummary)
                    } finally {
                        release.countDown()
                        vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                    }
                } }
            } finally {
                release.countDown()
                Dispatchers.resetMain()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun acceptingAutoCompressionPersistsTargetTierThenActuallyCallsSummaryModel() = runBlocking {
        newSingleThreadContext("context-auto-compression-main").use { main ->
            Dispatchers.setMain(main)
            try {
                val summary = "【核心背景与用户固定约束】\n仅记录合成对话已确认的要求，保留否定条件与名称。\n" +
                    "【历史关键里程碑与决策推进】\n合成讨论已完成早期方案比较，尚无新的执行结果。\n" +
                    "【时空演变与关键时间节点（极重要，严禁遗漏）】\n原文未提供明确时间，不能推断日期。\n" +
                    "【当前未决议题与待办上下文】\n等待下一步确认，尚未选择的建议不能写为已完成。"
                val response = com.google.gson.Gson().toJson(mapOf("choices" to listOf(mapOf(
                    "finish_reason" to "stop", "message" to mapOf("role" to "assistant", "content" to summary)))))
                Endpoint { socket, _ -> socket.response("200 OK", response) }.use { endpoint -> Fixture().use { f ->
                    f.enableViewModel()
                    f.settings.saveSettings(f.settings.getSettings().copy(autoCompressionTierEnabled = true,
                        autoCompressionThresholdL2 = 0f, autoCompressionThresholdL3 = 0f, autoCompressionThresholdL4 = 0f))
                    val (config, id) = f.config(endpoint.url)
                    f.db.apiConfigDao().updateConfig(config.copy(apiKey = ""))
                    repeat(40) { index ->
                        f.db.messageDao().insertMessage(Message(conversationId = id,
                            role = if (index % 2 == 0) "user" else "assistant",
                            content = "已确认的合成历史 $index。", createdAt = index.toLong()))
                    }
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (vm.contextUsage.value.usage == null) delay(10) }
                        withContext(Dispatchers.Main) { vm.refreshContextUsage(evaluateAuto = true) }
                        withTimeout(5000) { while (vm.contextUsage.value.pendingAutoTier == null) delay(10) }
                        assertEquals(CompressionTier.L4, vm.contextUsage.value.pendingAutoTier)
                        assertTrue(endpoint.payloads.isEmpty())
                        withContext(Dispatchers.Main) { vm.applyPendingAutoCompression() }
                        withTimeout(5000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                        val conversation = f.db.conversationDao().getConversationById(id)!!
                        assertTrue("确认后必须实际请求摘要模型", endpoint.payloads.isNotEmpty())
                        assertEquals(CompressionTier.L4.level, conversation.compressionTier)
                        assertFalse("应保存模型生成的摘要", conversation.rollingSummary.isNullOrBlank())
                        assertEquals(38L, conversation.summaryUpdatedMessageId)
                        assertTrue("更早原文由摘要承接", vm.contextUsage.value.usage!!.recentMessageCount < 40)
                        assertEquals("原始聊天记录必须完整保存", 40, f.db.messageDao().getMessagesList(id).size)
                    } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
                } }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun combinedStorySavePreservesCharactersScenarioAndEveryWorldBookScope() = runBlocking {
        newSingleThreadContext("story-settings-main").use { main ->
            Dispatchers.setMain(main)
            try {
                Fixture().use { f ->
                    f.enableViewModel()
                    val (_, id) = f.config("https://example.invalid/v1")
                    val firstCharacter = f.db.characterProfileDao().insertCharacter(CharacterProfile(name = "Synthetic First"))
                    val secondCharacter = f.db.characterProfileDao().insertCharacter(CharacterProfile(name = "Synthetic Second"))
                    val firstScenario = f.db.roleplayScenarioDao().insertScenario(RoleplayScenario(name = "Synthetic Old Setting"))
                    val secondScenario = f.db.roleplayScenarioDao().insertScenario(RoleplayScenario(name = "Synthetic New Setting"))
                    f.db.roleplaySessionDao().insertSession(RoleplaySession(conversationId = id, characterId = firstCharacter,
                        scenarioId = firstScenario, activeWorldBookIds = "99", currentPlotSummary = "old plot"))
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (vm.uiState.value.roleplaySession == null || vm.currentModelOption.value == null) delay(10) }
                        for (bookIds in listOf(null, "", "71,72")) {
                            val settings = vm.tempSettings.value.copy(enableWorldBook = true, activeWorldBookIds = bookIds,
                                enableExternalMemory = true, temperature = 0.45f)
                            val save = withContext(Dispatchers.Main) {
                                vm.saveStorySettings(listOf(secondCharacter, firstCharacter), secondScenario,
                                    NarrativeMode.NARRATOR, "new plot $bookIds", settings, "synthetic story prompt")
                            }
                            withTimeout(5000) { save.join() }
                            val session = f.db.roleplaySessionDao().getSessionByConversationId(id)!!
                            val conversation = f.db.conversationDao().getConversationById(id)!!
                            assertEquals(listOf(secondCharacter, firstCharacter), session.getEffectiveCharacterIds())
                            assertEquals(secondScenario, session.scenarioId)
                            assertEquals(NarrativeMode.NARRATOR.value, session.narrativeMode)
                            assertEquals("new plot $bookIds", session.currentPlotSummary)
                            assertTrue(session.enableWorldBook)
                            assertTrue(session.enableExternalMemory)
                            assertEquals(bookIds, session.activeWorldBookIds)
                            assertEquals(bookIds, conversation.activeWorldBookIds)
                            assertEquals("synthetic story prompt", conversation.systemPrompt)
                            assertEquals(0.45f, conversation.temperature!!, 0f)
                            assertEquals(session, vm.uiState.value.roleplaySession)
                            assertEquals(secondScenario, vm.uiState.value.roleplayScenario?.id)
                            assertEquals(bookIds, vm.tempSettings.value.activeWorldBookIds)
                        }
                        val saves = withContext(Dispatchers.Main) {
                            val first = vm.saveStorySettings(listOf(firstCharacter), firstScenario, NarrativeMode.CHARACTER,
                                "first queued save", vm.tempSettings.value.copy(activeWorldBookIds = "71"), "first prompt")
                            val second = vm.saveStorySettings(listOf(secondCharacter), secondScenario, NarrativeMode.AUTHOR,
                                "last queued save", vm.tempSettings.value.copy(activeWorldBookIds = null), "last prompt")
                            listOf(first, second)
                        }
                        withTimeout(5000) { saves.joinAll() }
                        val finalSession = f.db.roleplaySessionDao().getSessionByConversationId(id)!!
                        assertEquals(listOf(secondCharacter), finalSession.getEffectiveCharacterIds())
                        assertEquals(secondScenario, finalSession.scenarioId)
                        assertEquals(NarrativeMode.AUTHOR.value, finalSession.narrativeMode)
                        assertEquals("last queued save", finalSession.currentPlotSummary)
                        assertNull(finalSession.activeWorldBookIds)
                        val finalConversation = f.db.conversationDao().getConversationById(id)!!
                        assertNull(finalConversation.activeWorldBookIds)
                        assertEquals("last prompt", finalConversation.systemPrompt)
                        assertEquals(finalSession, vm.uiState.value.roleplaySession)
                    } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun sendingImmediatelyAfterStorySaveUsesNewCharactersPromptAndSelectedWorldBookInBothProtocols() = runBlocking {
        newSingleThreadContext("story-save-send-main").use { main ->
            Dispatchers.setMain(main)
            try {
                for (protocol in listOf("openai", "anthropic")) {
                    Endpoint { socket, _ ->
                        val body = if (protocol == "openai") "data: {\"choices\":[{\"delta\":{\"content\":\"saved settings reply\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                            else "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"saved settings reply\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n"
                        socket.response("200 OK", body, "text/event-stream")
                    }.use { endpoint -> Fixture().use { f ->
                        f.enableViewModel()
                        val (_, id) = f.config(endpoint.url, protocol)
                        val oldConversation = f.db.conversationDao().getConversationById(id)!!
                        f.db.conversationDao().updateConversation(oldConversation.copy(systemPrompt = "SYNTHETIC_OLD_PROMPT", enableReplyDirections = true))
                        val oldCharacter = f.db.characterProfileDao().insertCharacter(CharacterProfile(name = "SYNTHETIC_OLD_ROLE"))
                        val newCharacter = f.db.characterProfileDao().insertCharacter(CharacterProfile(name = "SYNTHETIC_NEW_ROLE"))
                        val newScenario = f.db.roleplayScenarioDao().insertScenario(RoleplayScenario(name = "SYNTHETIC_NEW_SCENARIO", worldview = "SYNTHETIC_NEW_WORLDVIEW"))
                        f.db.roleplaySessionDao().insertSession(RoleplaySession(conversationId = id, characterId = oldCharacter))
                        val selectedBook = f.db.worldBookDao().insertBook(WorldBook(name = "Synthetic selected book"))
                        val excludedBook = f.db.worldBookDao().insertBook(WorldBook(name = "Synthetic excluded book"))
                        f.db.worldBookDao().insertEntry(WorldBookEntry(bookId = selectedBook, name = "selected rule", keys = "", isConstant = true, content = "SYNTHETIC_SELECTED_LORE"))
                        f.db.worldBookDao().insertEntry(WorldBookEntry(bookId = excludedBook, name = "excluded rule", keys = "", isConstant = true, content = "SYNTHETIC_EXCLUDED_LORE"))
                        val vm = ChatViewModel(id)
                        try {
                            withTimeout(5000) { while (vm.uiState.value.roleplaySession == null || vm.currentModelOption.value == null) delay(10) }
                            val save = withContext(Dispatchers.Main) {
                                val pending = vm.saveStorySettings(listOf(newCharacter), newScenario, NarrativeMode.NARRATOR,
                                    "SYNTHETIC_NEW_PLOT", vm.tempSettings.value.copy(enableWorldBook = true,
                                        activeWorldBookIds = selectedBook.toString(), enableReplyDirections = false, enableSessionMemory = false),
                                    "SYNTHETIC_NEW_PROMPT")
                                // Deliberately do not join: this is the user's save-then-send interaction.
                                vm.sendMessage("Continue the synthetic story")
                                pending
                            }
                            withTimeout(5000) {
                                save.join()
                                while (f.db.messageDao().getMessagesList(id).none { it.content == "saved settings reply" } || vm.isGenerating.value) delay(10)
                            }
                            assertEquals(1, endpoint.payloads.size)
                            val body = com.google.gson.JsonParser.parseString(endpoint.payloads.single()).asJsonObject
                            val system = if (protocol == "openai") body.getAsJsonArray("messages").first { it.asJsonObject["role"].asString == "system" }.asJsonObject["content"].asString
                                else body["system"].asString
                            for (expected in listOf("SYNTHETIC_NEW_ROLE", "SYNTHETIC_NEW_WORLDVIEW", "SYNTHETIC_NEW_PLOT", "SYNTHETIC_NEW_PROMPT", "SYNTHETIC_SELECTED_LORE")) {
                                assertTrue("Missing $expected in $protocol request", system.contains(expected))
                            }
                            for (excluded in listOf("SYNTHETIC_OLD_ROLE", "SYNTHETIC_OLD_PROMPT", "SYNTHETIC_EXCLUDED_LORE")) {
                                assertFalse("Stale $excluded in $protocol request", system.contains(excluded))
                            }
                        } finally {
                            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                            ChatGenerationManager.cancelSession(id)
                        }
                    } }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun regenerationRestoresOriginalAttachmentsForEmptyAndTextInputsInEitherProtocol() = runBlocking {
        newSingleThreadContext("regeneration-main").use { main ->
            Dispatchers.setMain(main)
            try {
                val gson = com.google.gson.Gson()
                val image = Attachment("content://synthetic/image", "diagram.png", "image/png", base64Data = "c3ludGhldGlj", payloadMimeType = "image/png")
                val textFile = Attachment("content://synthetic/text", "notes.txt", "text/plain", textContent = "Synthetic attachment facts")
                val attachments = gson.toJson(listOf(image, textFile))
                for (protocol in listOf("openai", "anthropic")) {
                    for (input in listOf("", "请结合附件说明")) {
                        for (failedReply in listOf(false, true)) {
                            Endpoint { socket, _ ->
                                val body = if (protocol == "openai") "data: {\"choices\":[{\"delta\":{\"content\":\"regenerated\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                                    else "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"regenerated\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n"
                                socket.response("200 OK", body, "text/event-stream")
                            }.use { endpoint -> Fixture().use { f ->
                                f.enableViewModel()
                                val (_, id) = f.config(endpoint.url, protocol)
                                val userId = f.db.messageDao().insertMessage(Message(conversationId = id, role = "user", content = input,
                                    attachments = attachments, createdAt = 1L))
                                val reply = Message(conversationId = id, role = "assistant",
                                    content = if (failedReply) "请求失败\n\nsynthetic failure" else "previous reply", createdAt = 2L)
                                val replyId = f.db.messageDao().insertMessage(reply)
                                val vm = ChatViewModel(id)
                                try {
                                    withTimeout(5000) { while (vm.currentModelOption.value == null) delay(10) }
                                    withContext(Dispatchers.Main) { vm.regenerateLastMessage(reply.copy(id = replyId)) }
                                    withTimeout(5000) {
                                        while (f.db.messageDao().getMessagesList(id).none { it.content == "regenerated" } || vm.isGenerating.value) delay(10)
                                    }
                                    assertEquals(1, endpoint.payloads.size)
                                    val body = com.google.gson.JsonParser.parseString(endpoint.payloads.single()).asJsonObject
                                    val userContent = body.getAsJsonArray("messages").last().asJsonObject.getAsJsonArray("content")
                                    val text = userContent.first { it.asJsonObject["type"].asString == "text" }.asJsonObject["text"].asString
                                    assertTrue(text.contains("Synthetic attachment facts"))
                                    if (input.isNotBlank()) assertTrue(text.contains(input))
                                    val imagePart = userContent.first { it.asJsonObject["type"].asString == if (protocol == "openai") "image_url" else "image" }.asJsonObject
                                    if (protocol == "openai") {
                                        assertEquals("data:image/png;base64,c3ludGhldGlj", imagePart.getAsJsonObject("image_url")["url"].asString)
                                    } else {
                                        assertEquals("image/png", imagePart.getAsJsonObject("source")["media_type"].asString)
                                        assertEquals("c3ludGhldGlj", imagePart.getAsJsonObject("source")["data"].asString)
                                    }
                                    val saved = f.db.messageDao().getMessagesList(id)
                                    assertEquals(1, saved.count { it.role == "user" })
                                    assertEquals(attachments, saved.single { it.id == userId }.attachments)
                                    assertEquals(!failedReply, saved.any { it.id == replyId })
                                    assertEquals(if (failedReply) 2 else 3, saved.size)
                                } finally {
                                    vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                                    ChatGenerationManager.cancelSession(id)
                                }
                            } }
                        }
                    }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun unreadableRegenerationAttachmentsKeepOriginalErrorAndMakeNoRequest() = runBlocking {
        newSingleThreadContext("regeneration-invalid-main").use { main ->
            Dispatchers.setMain(main)
            try {
                for (raw in listOf("[broken", "[null]", "null", "[{}]")) {
                    Endpoint { socket, _ -> socket.response("500 Internal Server Error", "unexpected request") }.use { endpoint -> Fixture().use { f ->
                        f.enableViewModel()
                        val (_, id) = f.config(endpoint.url)
                        f.db.messageDao().insertMessage(Message(conversationId = id, role = "user", content = "", attachments = raw, createdAt = 1L))
                        val reply = Message(conversationId = id, role = "assistant", content = "请求失败\n\nsynthetic failure", createdAt = 2L)
                        val replyId = f.db.messageDao().insertMessage(reply)
                        val vm = ChatViewModel(id)
                        try {
                            withTimeout(5000) { while (vm.currentModelOption.value == null) delay(10) }
                            withContext(Dispatchers.Main) { vm.regenerateLastMessage(reply.copy(id = replyId)) }
                            withTimeout(5000) { while (vm.error.value == null) delay(10) }
                            assertTrue(vm.error.value!!.contains("原消息附件无法读取"))
                            assertFalse(vm.isGenerating.value)
                            assertTrue(endpoint.payloads.isEmpty())
                            assertEquals(2, f.db.messageDao().getMessagesList(id).size)
                            assertEquals(reply.content, f.db.messageDao().getMessageById(replyId)?.content)
                        } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
                    } }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @Test fun standaloneTimelineAndStoryTimeEnterBothProtocolsWithoutMemoryItems() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            for ((hasNode, hasTime) in listOf(true to true, true to false, false to true, false to false)) {
                Endpoint { socket, _ ->
                    val body = if (protocol == "openai") "data: {\"choices\":[{\"delta\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                        else "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n"
                    socket.response("200 OK", body, "text/event-stream")
                }.use { endpoint -> Fixture().use { f ->
                    val (config, id) = f.config(endpoint.url, protocol)
                    if (hasNode) f.db.timelineNodeDao().insertTimelineNode(TimelineNode(conversationId = id, timeTag = "第 2 天·清晨", event = "合成角色抵达测试城门"))
                    if (hasTime) {
                        val conversation = f.db.conversationDao().getConversationById(id)!!
                        f.db.conversationDao().updateConversation(conversation.copy(currentStoryTime = "第 3 天·上午"))
                    }
                    assertTrue(f.db.memoryDao().getCandidateMemories(id).isEmpty())
                    f.repository.sendChatMessageWithConfig(config, id, "继续", options = ChatRequestOptions(enableSessionMemory = false),
                        onToken = {}, onComplete = { text, _, _ -> assertEquals("OK", text) }, onError = { fail(it) })
                    val body = com.google.gson.JsonParser.parseString(endpoint.payloads.single()).asJsonObject
                    val system = if (protocol == "openai") body.getAsJsonArray("messages")
                        .firstOrNull { it.asJsonObject["role"].asString == "system" }?.asJsonObject?.get("content")?.asString.orEmpty()
                        else body["system"]?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                    assertEquals(hasNode, system.contains("合成角色抵达测试城门"))
                    assertEquals(hasTime, system.contains("第 3 天·上午"))
                    assertEquals(hasNode || hasTime, system.contains("<session_timeline>"))
                    assertTrue(f.db.memoryDao().getCandidateMemories(id).isEmpty())
                } }
            }
        }
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
