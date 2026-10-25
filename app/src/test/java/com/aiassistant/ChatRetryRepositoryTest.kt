package com.aiassistant

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
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
import kotlinx.coroutines.flow.first
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

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun manuallyEditedSessionMemorySurvivesSourceDeletionWhileUnchangedAndToggledMemoriesKeepTheirSources() = runBlocking {
        newSingleThreadContext("manual-memory-source-main").use { main ->
            Dispatchers.setMain(main)
            try {
                Fixture().use { f ->
                    f.enableViewModel()
                    val (config, id) = f.config("https://example.invalid/v1")
                    f.db.apiConfigDao().updateConfig(config.copy(apiKey = ""))
                    val sources = List(3) { index ->
                        val message = Message(conversationId = id, role = "user", content = "合成来源 $index")
                        message.copy(id = f.db.messageDao().insertMessage(message))
                    }
                    val memories = sources.mapIndexed { index, source ->
                        val memory = MemoryItem(content = "合成自动记忆 $index", scope = "conversation",
                            conversationId = id, sourceMessageId = source.id)
                        memory.copy(id = f.repository.insertMemory(memory))
                    }
                    val vm = ChatViewModel(id)
                    try {
                        withContext(Dispatchers.Main) {
                            vm.updateSessionMemory(memories[0].copy(content = "人工改写的合成记忆"))
                            vm.updateSessionMemory(memories[1])
                            vm.toggleSessionMemory(memories[2].id, false)
                        }
                        withTimeout(5000) {
                            while (f.repository.getMemoryById(memories[0].id)?.content != "人工改写的合成记忆" ||
                                f.repository.getMemoryById(memories[2].id)?.isEnabled != false) delay(10)
                        }
                        withContext(Dispatchers.Main) { /* Drain the unchanged save as well. */ }
                        assertNull(f.repository.getMemoryById(memories[0].id)!!.sourceMessageId)
                        assertEquals(sources[1].id, f.repository.getMemoryById(memories[1].id)!!.sourceMessageId)
                        assertEquals(sources[2].id, f.repository.getMemoryById(memories[2].id)!!.sourceMessageId)
                        sources.forEach { f.repository.deleteMessage(it) }
                        assertEquals("人工改写的合成记忆", f.repository.getMemoryById(memories[0].id)!!.content)
                        assertNull(f.repository.getMemoryById(memories[1].id))
                        assertNull(f.repository.getMemoryById(memories[2].id))
                    } finally {
                        vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                    }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun recoveredBackupOutputSurvivesNavigationAndPauseWithoutOldKeyErrorsOrDuplicateUsage() = runBlocking {
        newSingleThreadContext("backup-recovery-pause-main").use { main ->
            Dispatchers.setMain(main)
            try {
                for (protocol in listOf("openai", "anthropic")) for (thinkingFirst in listOf(true, false)) {
                    val allowSecondToken = CountDownLatch(1)
                    val release = CountDownLatch(1)
                    lateinit var endpoint: Endpoint
                    endpoint = Endpoint { socket, index ->
                        val headers = endpoint.requestHeaders[index - 1]
                        val key = (headers["authorization"] ?: headers["x-api-key"]).orEmpty().removePrefix("Bearer ")
                        if (key == "synthetic-unavailable") {
                            socket.response("401 Unauthorized", "{\"error\":{\"message\":\"synthetic invalid credential\"}}")
                        } else {
                            socket.streamHeaders()
                            fun delta(thinking: Boolean): String {
                                val text = if (thinking) "合成思考" else "合成正文"
                                return if (protocol == "openai")
                                    "data: {\"choices\":[{\"delta\":{\"${if (thinking) "reasoning_content" else "content"}\":\"$text\"}}]}\n\n"
                                else "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"${if (thinking) "thinking_delta" else "text_delta"}\",\"${if (thinking) "thinking" else "text"}\":\"$text\"}}\n\n"
                            }
                            socket.delta(delta(thinkingFirst))
                            allowSecondToken.await(10, TimeUnit.SECONDS)
                            socket.delta(delta(!thinkingFirst))
                            release.await(10, TimeUnit.SECONDS)
                        }
                    }
                    endpoint.use { Fixture(timeout = 10_000).use { f ->
                        f.enableViewModel()
                        f.settings.setBackupKeyFallbackEnabled(true)
                        val (_, id) = f.config(endpoint.url, protocol, "synthetic-unavailable\nsynthetic-healthy")
                        val original = ChatViewModel(id)
                        var restored: ChatViewModel? = null
                        try {
                            withTimeout(5000) { while (original.currentModelOption.value == null || original.contextUsage.value.usage == null) delay(10) }
                            withContext(Dispatchers.Main) { original.sendMessage("合成暂停测试") }
                            withTimeout(5000) {
                                while ((if (thinkingFirst) original.currentThinking.value != "合成思考" else original.currentResponse.value != "合成正文") ||
                                    original.error.value != null || original.reconnectStatus.value != null || original.keyAttemptErrors.value.isNotEmpty()) delay(10)
                            }
                            val session = ChatGenerationManager.getSession(id)!!
                            val generation = session.generationJob!!
                            assertEquals(1, session.keyAttemptHistory.value.size)
                            assertTrue(original.keyAttemptErrors.value.isEmpty())
                            assertNull(original.error.value)
                            assertNull(original.reconnectStatus.value)
                            assertEquals(if (thinkingFirst) com.aiassistant.ui.screens.chat.GenerationUiState.Thinking
                                else com.aiassistant.ui.screens.chat.GenerationUiState.Streaming,
                                com.aiassistant.ui.screens.chat.GenerationUiStateRules.derive(true, original.currentResponse.value,
                                    original.currentThinking.value.isNotBlank(), original.reconnectStatus.value))
                            original.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                            val attached = ChatViewModel(id).also { restored = it }
                            withTimeout(5000) { while (!attached.isGenerating.value ||
                                (if (thinkingFirst) attached.currentThinking.value != "合成思考" else attached.currentResponse.value != "合成正文")) delay(10) }
                            assertNull(attached.reconnectStatus.value)
                            assertNull(attached.error.value)
                            assertTrue(attached.keyAttemptErrors.value.isEmpty())
                            assertSame(session, ChatGenerationManager.getSession(id))
                            allowSecondToken.countDown()
                            withTimeout(5000) { while (attached.currentThinking.value != "合成思考" || attached.currentResponse.value != "合成正文") delay(10) }
                            withContext(Dispatchers.Main) { attached.stopGeneration() }
                            withTimeout(5000) { generation.join() }
                            withTimeout(5000) { while (f.db.messageDao().getMessagesList(id).none { it.role == "assistant" }) delay(10) }
                            val reply = f.db.messageDao().getMessagesList(id).single { it.role == "assistant" }
                            assertEquals("合成正文\n\n*(生成已停止)*", reply.content)
                            assertEquals("合成思考", reply.thinkingContent)
                            assertFalse(com.aiassistant.ui.screens.chat.isErrorMessage(reply.content))
                            assertEquals(com.aiassistant.ui.screens.chat.GenerationUiState.Cancelled,
                                com.aiassistant.ui.screens.chat.GenerationUiStateRules.derive(false, reply.content, true, null))
                            assertNull(attached.error.value)
                            assertNull(attached.reconnectStatus.value)
                            assertTrue(attached.keyAttemptErrors.value.isEmpty())
                            assertEquals(2, endpoint.payloads.size)
                            val stats = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE)
                            assertEquals("失败请求和暂停请求各记一次，不能按旧错误再补一条", 2, stats.size)
                            assertEquals(1, stats.count { it.completionStatus == "cancelled" })
                            assertTrue(stats.none { it.errorMessage.orEmpty().contains("此前连接报错") })
                        } finally {
                            allowSecondToken.countDown()
                            release.countDown()
                            restored?.stopGeneration()
                            restored?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
                            original.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                            ChatGenerationManager.cancelSession(id)
                        }
                    } }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun pausingConnectingBackupPreservesPendingErrorsAndActualRequestUsage() = runBlocking {
        newSingleThreadContext("backup-connecting-pause-main").use { main ->
            Dispatchers.setMain(main)
            try {
                for (protocol in listOf("openai", "anthropic")) {
                    val entered = CountDownLatch(1)
                    val release = CountDownLatch(1)
                    lateinit var endpoint: Endpoint
                    endpoint = Endpoint { socket, index ->
                        val headers = endpoint.requestHeaders[index - 1]
                        val key = (headers["authorization"] ?: headers["x-api-key"]).orEmpty().removePrefix("Bearer ")
                        if (key == "synthetic-unavailable") {
                            socket.response("401 Unauthorized", "{\"error\":{\"message\":\"synthetic invalid credential\"}}")
                        } else {
                            socket.streamHeaders()
                            entered.countDown()
                            release.await(10, TimeUnit.SECONDS)
                        }
                    }
                    endpoint.use { Fixture(timeout = 10_000).use { f ->
                        f.enableViewModel()
                        f.settings.setBackupKeyFallbackEnabled(true)
                        val (_, id) = f.config(endpoint.url, protocol, "synthetic-unavailable\nsynthetic-healthy")
                        val vm = ChatViewModel(id)
                        try {
                            withTimeout(5000) { while (vm.currentModelOption.value == null || vm.contextUsage.value.usage == null) delay(10) }
                            withContext(Dispatchers.Main) { vm.sendMessage("合成连接暂停") }
                            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
                            val session = ChatGenerationManager.getSession(id)!!
                            val generation = session.generationJob!!
                            assertEquals(1, session.keyAttemptHistory.value.size)
                            withContext(Dispatchers.Main) { vm.stopGeneration() }
                            withTimeout(5000) { generation.join() }
                            withTimeout(5000) { while (f.db.messageDao().getMessagesList(id).none { it.role == "assistant" }) delay(10) }
                            val reply = f.db.messageDao().getMessagesList(id).single { it.role == "assistant" }
                            assertTrue(reply.content.startsWith("生成已停止\n\n【连接异常信息记录】"))
                            val savedErrors = com.aiassistant.ui.screens.chat.GenerationUiStateRules.stoppedReplyErrorReport(reply.content)!!
                            assertTrue(savedErrors.contains("Key #1"))
                            assertTrue(savedErrors.contains("synthetic invalid credential"))
                            assertEquals("", com.aiassistant.ui.screens.chat.GenerationUiStateRules.stoppedReplyDisplayBody(reply.content))
                            assertNull(reply.thinkingContent)
                            assertFalse(com.aiassistant.ui.screens.chat.isErrorMessage(reply.content))
                            assertTrue(AiRepository.isErrorPlaceholderMessage(reply.content))
                            val stats = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE)
                            assertEquals("连接暂停不得伪造额外失败调用", 2, stats.size)
                            assertEquals(1, stats.count { it.completionStatus == "cancelled" })
                            assertNull(vm.error.value)
                            assertNull(vm.reconnectStatus.value)
                            assertTrue(vm.keyAttemptErrors.value.isEmpty())
                        } finally {
                            release.countDown()
                            vm.stopGeneration()
                            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                            ChatGenerationManager.cancelSession(id)
                        }
                    } }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun stoppingWhileTerminalNetworkErrorWaitsForMainKeepsErrorExactlyOnce() = runBlocking {
        newSingleThreadContext("terminal-error-pause-main").use { main ->
            Dispatchers.setMain(main)
            val release = CountDownLatch(1)
            val entered = CountDownLatch(1)
            try {
                Endpoint { socket, _ ->
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    socket.response("400 Bad Request", "{\"error\":{\"message\":\"synthetic terminal failure F42\"}}")
                }.use { endpoint -> Fixture().use { f ->
                    f.enableViewModel()
                    val (_, id) = f.config(endpoint.url)
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (vm.currentModelOption.value == null || vm.contextUsage.value.usage == null) delay(10) }
                        withContext(Dispatchers.Main) { vm.sendMessage("合成最终报错暂停竞态") }
                        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
                        val session = ChatGenerationManager.getSession(id)!!
                        val generation = session.generationJob!!
                        withContext(Dispatchers.Main) {
                            release.countDown()
                            // 保持本帧未让出，让 IO 错误先抵达、主线程收尾尚未执行。
                            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                            while (session.error.value == null && System.nanoTime() < deadline) Thread.yield()
                            assertTrue(session.error.value.orEmpty().contains("synthetic terminal failure F42"))
                            vm.stopGeneration()
                            vm.stopGeneration()
                        }
                        withTimeout(5000) { generation.join() }
                        withTimeout(5000) { while (f.db.messageDao().getMessagesList(id).none { it.role == "assistant" }) delay(10) }
                        val reply = f.db.messageDao().getMessagesList(id).single { it.role == "assistant" }
                        assertTrue(com.aiassistant.ui.screens.chat.GenerationUiStateRules.stoppedReplyErrorReport(reply.content)!!
                            .contains("synthetic terminal failure F42"))
                        assertTrue(com.aiassistant.ui.screens.chat.GenerationUiStateRules.isStoppedContent(reply.content))
                        assertNull(vm.error.value)
                        assertNull(ChatGenerationManager.getSession(id))
                        assertEquals(1, endpoint.payloads.size)
                        assertEquals(1, f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).size)
                    } finally {
                        release.countDown()
                        vm.stopGeneration()
                        vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                        ChatGenerationManager.cancelSession(id)
                    }
                } }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun branchClickImmediatelyShowsBusyDeduplicatesAndRecoversAfterFailure() = runBlocking {
        newSingleThreadContext("branch-operation-main").use { main ->
            Dispatchers.setMain(main)
            try {
                Fixture().use { f ->
                    f.enableViewModel()
                    val (_, id) = f.config("https://example.invalid/v1")
                    val replyId = f.repository.saveMessage(Message(conversationId = id, role = "assistant", content = "合成分支起点"))
                    val vm = ChatViewModel(id)
                    val release = CompletableDeferred<Unit>()
                    try {
                        withTimeout(5000) { while (vm.currentModelOption.value == null || vm.contextUsage.value.usage == null) delay(10) }
                        val entered = CompletableDeferred<Unit>()
                        val heldTransaction = launch(Dispatchers.IO) {
                            f.db.withTransaction { entered.complete(Unit); release.await() }
                        }
                        withTimeout(5000) { entered.await() }
                        val created = CompletableDeferred<Pair<Long, String>>()
                        val callbacks = java.util.concurrent.atomic.AtomicInteger()
                        withContext(Dispatchers.Main) {
                            repeat(6) {
                                vm.createBranch(replyId) { newId, title -> callbacks.incrementAndGet(); created.complete(newId to title) }
                                assertEquals(replyId, vm.creatingBranchMessageId.value)
                            }
                        }
                        assertFalse("事务繁忙时应保持明确的创建中状态", created.isCompleted)
                        release.complete(Unit)
                        heldTransaction.join()
                        val branch = withTimeout(5000) { created.await() }
                        withTimeout(5000) { while (vm.creatingBranchMessageId.value != null) delay(10) }
                        assertEquals("连点不得重复创建分支", 1, callbacks.get())
                        assertEquals(listOf(branch.first), f.repository.getConversationBranches(id).first().map { it.childConversationId })
                        assertEquals(listOf("合成分支起点"), f.db.messageDao().getMessagesList(branch.first).map { it.content })

                        // 原会话消失是实际可恢复失败；状态必须释放并给出可见原因。
                        f.db.conversationDao().deleteConversation(f.db.conversationDao().getConversationById(id)!!)
                        withContext(Dispatchers.Main) { vm.createBranch(replyId) { _, _ -> fail("已删除会话不能创建成功") } }
                        withTimeout(5000) { while (vm.creatingBranchMessageId.value != null) delay(10) }
                        assertTrue(vm.error.value.orEmpty().startsWith("创建分支失败:"))
                    } finally {
                        release.complete(Unit)
                        vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                    }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun queuedOldGenerationUiUpdateCannotRestoreBuffersAfterPause() = runBlocking {
        newSingleThreadContext("queued-generation-state-main").use { main ->
            Dispatchers.setMain(main)
            try {
                Fixture().use { f ->
                    f.enableViewModel()
                    val (_, id) = f.config("https://example.invalid/v1")
                    val session = ChatGenerationManager.startSession(id, "synthetic-model")
                    session.generationJob = Job()
                    session.appendResponse("合成部分正文")
                    session.appendThinking("合成思考")
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (!vm.isGenerating.value || vm.currentResponse.value != "合成部分正文") delay(10) }
                        val publish = ChatViewModel::class.java.getDeclaredMethod("publishGenerationState",
                            ChatGenerationManager.ActiveSession::class.java).apply { isAccessible = true }
                        withContext(Dispatchers.Main) {
                            // Main 暂不让出：IO 回调先排队，然后用户在同一帧停止。
                            val worker = Thread { publish.invoke(vm, session) }
                            worker.start()
                            worker.join(3000)
                            assertFalse(worker.isAlive)
                            vm.stopGeneration()
                        }
                        withContext(Dispatchers.Main) { /* Drain the earlier queued publication. */ }
                        withTimeout(5000) { while (f.db.messageDao().getMessagesList(id).none { it.role == "assistant" }) delay(10) }
                        assertEquals("", vm.currentResponse.value)
                        assertEquals("", vm.currentThinking.value)
                        assertNull(vm.error.value)
                        assertNull(vm.reconnectStatus.value)
                        val reply = f.db.messageDao().getMessagesList(id).single { it.role == "assistant" }
                        assertEquals("合成部分正文\n\n*(生成已停止)*", reply.content)
                        assertEquals("合成思考", reply.thinkingContent)
                    } finally {
                        vm.stopGeneration()
                        vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                        ChatGenerationManager.cancelSession(id)
                    }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @Test fun savedDefaultStrengthOmitsOnlyEffortAfterAnExplicitChoiceInBothWireProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, _ ->
                socket.response("200 OK", syntheticStreamResponse(protocol, "合成默认强度回复"), "text/event-stream")
            }.use { endpoint -> Fixture().use { f ->
                val (original, id) = f.config(endpoint.url, protocol)
                val model = if (protocol == "anthropic") "claude-opus-4-7" else "gpt-6-astra"
                val explicit = original.copy(modelName = model, enableThinking = true, thinkingEffort = "high")
                f.db.apiConfigDao().updateConfig(explicit)
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(modelName = model))
                f.repository.sendChatMessageWithConfig(explicit, id, "合成指定强度请求", onToken = {},
                    onComplete = { _, _, _ -> }, onError = { fail(it) })

                f.db.apiConfigDao().updateConfig(explicit.copy(thinkingEffort = "default"))
                val restored = f.db.apiConfigDao().getConfigById(explicit.id)!!
                assertTrue(restored.enableThinking)
                assertEquals("default", restored.thinkingEffort)
                f.repository.sendChatMessageWithConfig(restored, id, "合成服务默认请求", onToken = {},
                    onComplete = { _, _, _ -> }, onError = { fail(it) })
                assertEquals("每次发送只能请求一次", 2, endpoint.payloads.size)
                val first = com.google.gson.JsonParser.parseString(endpoint.payloads[0]).asJsonObject
                val second = com.google.gson.JsonParser.parseString(endpoint.payloads[1]).asJsonObject
                if (protocol == "openai") {
                    assertEquals("high", first["reasoning_effort"].asString)
                    assertFalse(second.has("reasoning_effort"))
                    assertFalse(second.has("thinking_effort"))
                    assertFalse(second.has("thinking_budget"))
                } else {
                    assertEquals("high", first.getAsJsonObject("output_config")["effort"].asString)
                    assertFalse(second.has("output_config"))
                    assertEquals("adaptive", second.getAsJsonObject("thinking")["type"].asString)
                    assertFalse(second.getAsJsonObject("thinking").has("budget_tokens"))
                }
            } }
        }
    }

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
    @Test fun rotationOffPinsFirstEnabledKeyAcrossChatAuxiliaryAndCatalogueAndNeverFallsBack() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, _ ->
                socket.response("401 Unauthorized", "{\"error\":{\"message\":\"synthetic invalid credential\"}}")
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setBackupKeyFallbackEnabled(true)
                f.settings.setModelRetryEnabled(false)
                val (config, id) = f.config(endpoint.url, protocol,
                    "[已禁用] synthetic-disabled\nsynthetic-unavailable\nsynthetic-healthy")
                val manager = f.settings.apiKeyRotationManager
                // 先制造旧冷却，再关闭总开关；旧健康状态不能阻断手动固定的 Key。
                manager.recordFailure(endpoint.url, protocol, "synthetic-unavailable",
                    com.aiassistant.data.repository.ApiException(401, "synthetic"))
                assertTrue(manager.saveSettings(endpoint.url, protocol,
                    manager.getSettings(endpoint.url, protocol).copy(enabled = false)))
                var failed = false
                f.repository.sendChatMessageWithConfig(config, id, "合成输入", onToken = {},
                    onComplete = { _, _, _ -> fail("Failure must not complete") }, onError = { failed = true })
                assertTrue(failed)
                assertTrue(f.repository.executeQuickCompletionWithResult(config, "合成辅助请求").isFailure)
                assertTrue(f.repository.fetchAvailableModelsDirect(endpoint.url, config.apiKey, protocol).isFailure)
                assertEquals(3, endpoint.payloads.size)
                assertEquals(List(3) { "synthetic-unavailable" }, endpoint.requestHeaders.map {
                    (it["authorization"] ?: it["x-api-key"]).orEmpty().removePrefix("Bearer ")
                })
            } }
        }
    }

    @Test fun modelCatalogueAcceptsExplicitEmptyArraysButRejectsUnknownOrMalformedResponses() = runBlocking {
        for (body in listOf("{\"data\":[]}", "{\"models\":[]}", "{\"model\":[]}")) {
            Endpoint { socket, _ -> socket.response("200 OK", body) }.use { endpoint -> Fixture().use { f ->
                val result = f.repository.fetchAvailableModelsDirect(endpoint.url, "synthetic-one")
                assertEquals(body, emptyList<String>(), result.getOrThrow())
                assertEquals(1, endpoint.payloads.size)
            } }
        }
        for (body in listOf("{}", "not-json", "{\"data\":[{}]}")) {
            Endpoint { socket, _ -> socket.response("200 OK", body) }.use { endpoint -> Fixture().use { f ->
                assertTrue(body, f.repository.fetchAvailableModelsDirect(endpoint.url, "synthetic-one").isFailure)
                assertEquals(1, endpoint.payloads.size)
            } }
        }
    }

    @Test fun badKeyIsSkippedOnLaterChatAndSharedAuxiliaryAndCatalogueRequests() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            lateinit var endpoint: Endpoint
            endpoint = Endpoint { socket, index ->
                val headers = endpoint.requestHeaders[index - 1]
                val key = (headers["authorization"] ?: headers["x-api-key"]).orEmpty().removePrefix("Bearer ")
                if (key == "synthetic-unavailable") {
                    socket.response("401 Unauthorized", "{\"error\":{\"message\":\"synthetic invalid credential\"}}")
                } else if (endpoint.requestLines[index - 1].contains("/models")) {
                    socket.response("200 OK", "{\"data\":[{\"id\":\"vendor/Exact-ID\"}]}")
                } else {
                    val stream = com.google.gson.JsonParser.parseString(endpoint.payloads[index - 1]).asJsonObject["stream"]?.asBoolean == true
                    if (stream) socket.response("200 OK", syntheticStreamResponse(protocol, "合成回复"), "text/event-stream")
                    else socket.response("200 OK", syntheticSummaryResponse(protocol))
                }
            }
            endpoint.use { Fixture().use { f ->
                f.settings.setBackupKeyFallbackEnabled(true)
                // Even an enabled auth retry must yield to an available backup once cooling starts.
                f.settings.saveRetryRule(RetryErrorType.AUTHENTICATION, RetryRule(true, 3))
                val (config, id) = f.config(endpoint.url, protocol, "synthetic-unavailable\nsynthetic-healthy")
                val failedIndices = mutableListOf<Int>()
                repeat(2) {
                    var completed = false
                    f.repository.sendChatMessageWithConfig(config, id, "合成输入 $it", onToken = {},
                        onComplete = { _, _, _ -> completed = true }, onError = { fail(it) },
                        onKeyAttemptError = { index, _, _ -> failedIndices.add(index) })
                    assertTrue(completed)
                }
                assertEquals(listOf(1), failedIndices)
                assertEquals(3, endpoint.payloads.size)
                assertTrue(f.repository.executeQuickCompletionWithResult(config, "合成辅助请求").isSuccess)
                assertEquals(listOf("vendor/Exact-ID"), f.repository.fetchAvailableModelsDirect(endpoint.url, config.apiKey, protocol).getOrThrow())
                val sentKeys = endpoint.requestHeaders.map { (it["authorization"] ?: it["x-api-key"]).orEmpty().removePrefix("Bearer ") }
                assertEquals(listOf("synthetic-unavailable") + List(4) { "synthetic-healthy" }, sentKeys)
                assertTrue(com.aiassistant.utils.ApiKeyRotationManager(f.context).isCoolingDown(endpoint.url, protocol, "synthetic-unavailable"))
            } }
        }
    }

    @Test fun embeddedAuthenticationErrorsInHttp200StreamsAlsoCoolFailedKeysInBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            lateinit var endpoint: Endpoint
            endpoint = Endpoint { socket, index ->
                val key = (endpoint.requestHeaders[index - 1]["authorization"] ?: endpoint.requestHeaders[index - 1]["x-api-key"])
                    .orEmpty().removePrefix("Bearer ")
                val body = if (key == "synthetic-unavailable") {
                    if (protocol == "openai") "data: {\"error\":{\"code\":\"invalid_api_key\"}}\n\n"
                    else "data: {\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"synthetic upstream error\"}}\n\n"
                } else syntheticStreamResponse(protocol, "合成恢复回复")
                socket.response("200 OK", body, "text/event-stream")
            }
            endpoint.use { Fixture().use { f ->
                f.settings.setBackupKeyFallbackEnabled(true)
                val (config, id) = f.config(endpoint.url, protocol, "synthetic-unavailable\nsynthetic-healthy")
                repeat(2) {
                    var completed = false
                    f.repository.sendChatMessageWithConfig(config, id, "合成输入", onToken = {},
                        onComplete = { _, _, _ -> completed = true }, onError = { fail(it) })
                    assertTrue(completed)
                }
                assertEquals(3, endpoint.payloads.size)
                assertTrue(f.settings.apiKeyRotationManager.isCoolingDown(endpoint.url, protocol, "synthetic-unavailable"))
                assertEquals(1, endpoint.requestHeaders.count {
                    (it["authorization"] ?: it["x-api-key"]).orEmpty().endsWith("synthetic-unavailable")
                })
            } }
        }
    }

    @Test fun embeddedQuotaErrorsInHttp200AuxiliaryAndCatalogueResponsesKeepTheirMachineCodes() = runBlocking {
        val errors = listOf(
            "openai" to mapOf("code" to "insufficient_quota"),
            "anthropic" to mapOf("code" to "insufficient_quota"),
            "anthropic" to mapOf("type" to "authentication_error")
        )
        for ((protocol, metadata) in errors) for (catalogue in listOf(true, false)) {
            lateinit var endpoint: Endpoint
            endpoint = Endpoint { socket, index ->
                val headers = endpoint.requestHeaders[index - 1]
                val key = (headers["authorization"] ?: headers["x-api-key"]).orEmpty().removePrefix("Bearer ")
                val body = when {
                    key == "synthetic-unavailable" -> com.google.gson.Gson().toJson(
                        mapOf("error" to metadata + ("message" to "synthetic upstream error")) +
                            if (protocol == "anthropic") mapOf("type" to "error", "stop_reason" to "end_turn",
                                "content" to listOf(mapOf("type" to "text", "text" to "不得接收的合成错误正文")))
                            else mapOf("choices" to listOf(mapOf("finish_reason" to "stop",
                                "message" to mapOf("content" to "不得接收的合成错误正文"))))
                    )
                    catalogue -> "{\"data\":[{\"id\":\"vendor/Exact-ID\"}]}"
                    else -> syntheticSummaryResponse(protocol)
                }
                socket.response("200 OK", body)
            }
            endpoint.use { Fixture().use { f ->
                f.settings.setBackupKeyFallbackEnabled(true)
                val keys = if (protocol == "anthropic") "Bearer synthetic-unavailable\nBearer synthetic-healthy"
                    else "synthetic-unavailable\nsynthetic-healthy"
                val (config, _) = f.config(endpoint.url, protocol, keys)
                repeat(2) {
                    if (catalogue) assertEquals(listOf("vendor/Exact-ID"),
                        f.repository.fetchAvailableModelsDirect(endpoint.url, config.apiKey, protocol).getOrThrow())
                    else assertFalse(f.repository.executeQuickCompletionWithResult(config, "合成输入").getOrThrow()
                        .contains("不得接收的合成错误正文"))
                }
                assertEquals(3, endpoint.payloads.size)
                val sentKeys = endpoint.requestHeaders.map {
                    (it["authorization"] ?: it["x-api-key"]).orEmpty().removePrefix("Bearer ")
                }
                assertEquals(listOf("synthetic-unavailable", "synthetic-healthy", "synthetic-healthy"), sentKeys)
                if (protocol == "anthropic") assertEquals(sentKeys, endpoint.requestHeaders.map { it["x-api-key"] })
                assertTrue(f.settings.apiKeyRotationManager.isCoolingDown(endpoint.url, protocol, "synthetic-unavailable"))
                if (!catalogue) {
                    val stats = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE)
                    assertEquals("每次实际请求只能记一条统计", 3, stats.size)
                    assertEquals(1, stats.count { !it.success })
                    assertEquals(2, stats.count { it.success })
                }
            } }
        }
    }

    @Test fun allCoolingKeysReturnExplicitFailureWithoutNewHttpUntilManualReset() = runBlocking {
        Endpoint { socket, _ -> socket.response("401 Unauthorized", "{\"error\":{\"message\":\"synthetic invalid credential\"}}") }.use { endpoint -> Fixture().use { f ->
            f.settings.setBackupKeyFallbackEnabled(true)
            val (config, _) = f.config(endpoint.url, keys = "synthetic-one\nsynthetic-two")
            assertTrue(f.repository.executeQuickCompletionWithResult(config, "合成输入").isFailure)
            assertEquals(2, endpoint.payloads.size)
            val failure = f.repository.executeQuickCompletionWithResult(config, "再次请求").exceptionOrNull()
            assertTrue(failure is com.aiassistant.utils.ApiKeyCoolingDownException)
            assertEquals(2, endpoint.payloads.size)
            assertTrue(f.repository.fetchAvailableModelsDirect(endpoint.url, config.apiKey).exceptionOrNull() is com.aiassistant.utils.ApiKeyCoolingDownException)
            assertEquals(2, endpoint.payloads.size)
            assertTrue(f.settings.apiKeyRotationManager.resetHealth(endpoint.url, "openai"))
            assertTrue(f.repository.executeQuickCompletionWithResult(config, "手工重置后请求").isFailure)
            assertEquals(4, endpoint.payloads.size)
        } }
    }

    @Test fun catalogueAndStreamingFallbackFailuresAlsoPersistKeyHealth() = runBlocking {
        for (catalogue in listOf(true, false)) {
            lateinit var endpoint: Endpoint
            endpoint = Endpoint { socket, index ->
                val key = endpoint.requestHeaders[index - 1]["authorization"].orEmpty().removePrefix("Bearer ")
                val stream = endpoint.payloads[index - 1].takeIf { it.isNotBlank() }
                    ?.let { com.google.gson.JsonParser.parseString(it).asJsonObject["stream"]?.asBoolean } == true
                when {
                    key == "synthetic-unavailable" && !catalogue && !stream ->
                        socket.response("405 Method Not Allowed", "{\"error\":{\"message\":\"synthetic streaming only\"}}")
                    key == "synthetic-unavailable" ->
                        socket.response("429 Too Many Requests", "{\"error\":{\"message\":\"token quota exceeded\"}}")
                    catalogue -> socket.response("200 OK", "{\"data\":[{\"id\":\"vendor/Exact-ID\"}]}")
                    else -> socket.response("200 OK", syntheticSummaryResponse())
                }
            }
            endpoint.use { Fixture().use { f ->
                f.settings.setBackupKeyFallbackEnabled(true)
                val (config, _) = f.config(endpoint.url, keys = "synthetic-unavailable\nsynthetic-healthy")
                repeat(2) {
                    if (catalogue) assertTrue(f.repository.fetchAvailableModelsDirect(endpoint.url, config.apiKey).isSuccess)
                    else assertTrue(f.repository.executeQuickCompletionWithResult(config, "合成流式保底输入").isSuccess)
                }
                val headers = endpoint.requestHeaders.map { it["authorization"] }
                assertEquals(if (catalogue) 1 else 2, headers.count { it == "Bearer synthetic-unavailable" })
                assertEquals(2, headers.count { it == "Bearer synthetic-healthy" })
                assertTrue(f.settings.apiKeyRotationManager.isCoolingDown(endpoint.url, "openai", "synthetic-unavailable"))
            } }
        }
    }

    @Test fun disabledKeysNeverBecomeAuxiliaryCredentialsAndHealthyChatRoundRobinAdvances() = runBlocking {
        Endpoint { socket, _ -> socket.response("200 OK", syntheticStreamResponse("openai", "合成轮询回复"), "text/event-stream") }.use { endpoint -> Fixture().use { f ->
            f.settings.setBackupKeyFallbackEnabled(true)
            val (config, id) = f.config(endpoint.url, keys = "[已禁用] synthetic-disabled\nsynthetic-one\nsynthetic-two")
            repeat(3) {
                f.repository.sendChatMessageWithConfig(config, id, "合成输入 $it", onToken = {}, onComplete = { _, _, _ -> }, onError = { fail(it) })
            }
            assertEquals(listOf("Bearer synthetic-one", "Bearer synthetic-two", "Bearer synthetic-one"), endpoint.requestHeaders.map { it["authorization"] })
            val disabled = config.copy(apiKey = "[已禁用] synthetic-disabled")
            assertTrue(f.repository.executeQuickCompletionWithResult(disabled, "合成辅助输入").isFailure)
            assertTrue(f.repository.fetchAvailableModelsDirect(endpoint.url, disabled.apiKey).isFailure)
            assertEquals(3, endpoint.payloads.size)
        } }
    }

    private class Endpoint(val respond: (Socket, Int) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        private val worker = Executors.newCachedThreadPool()
        val payloads = CopyOnWriteArrayList<String>()
        val requestLines = CopyOnWriteArrayList<String>()
        val requestHeaders = CopyOnWriteArrayList<Map<String, String>>()
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
                            val requestLine = line()
                            var length = 0
                            val headers = mutableMapOf<String, String>()
                            while (true) {
                                val header = line()
                                if (header.isEmpty()) break
                                headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
                                if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                            }
                            val body = ByteArray(length)
                            var read = 0
                            while (read < length) {
                                val count = input.read(body, read, length - read)
                                check(count > 0)
                                read += count
                            }
                            val requestIndex = synchronized(payloads) {
                                requestLines.add(requestLine)
                                requestHeaders.add(headers)
                                payloads.add(String(body, Charsets.UTF_8))
                                payloads.size
                            }
                            respond(socket, requestIndex)
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

    private class Fixture(
        timeout: Long = 2000,
        afterUsageInsert: (suspend (ApiUsageStat) -> Unit)? = null
    ) : AutoCloseable {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = PersonalizationManager(context)
        val repository: AiRepository
        val app = AiAssistantApp()
        private val instance = AiAssistantApp::class.java.getDeclaredField("instance").apply { isAccessible = true }
        private val previous = instance.get(null)
        init {
            context.getSharedPreferences("personalization_settings", 0).edit().clear().commit()
            context.getSharedPreferences("api_key_rotation", 0).edit().clear().commit()
            settings.setBackupKeyFallbackEnabled(false)
            settings.saveSettings(settings.getSettings().copy(autoNameEnabled = false, autoMemoryEnabled = false, auxiliaryMemoryEnabled = false))
            instance.set(null, app)
            AiAssistantApp::class.java.getDeclaredField("database").apply { isAccessible = true }.set(app, db)
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) }
            val crypto = unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, CryptoManager::class.java) as CryptoManager
            val actualUsageDao = db.usageStatDao()
            val usageDao = if (afterUsageInsert == null) actualUsageDao else object : com.aiassistant.data.local.UsageStatDao by actualUsageDao {
                override suspend fun insertStat(stat: ApiUsageStat) {
                    actualUsageDao.insertStat(stat)
                    afterUsageInsert?.invoke(stat)
                }
            }
            repository = AiRepository(db.folderDao(), db.apiConfigDao(), db.conversationDao(), db.messageDao(), usageDao,
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
    @Test fun selectingCompressionInTwoThousandRoundConversationActuallyReleasesTheInputBudget() = runBlocking {
        newSingleThreadContext("large-context-selection-main").use { main ->
            Dispatchers.setMain(main)
            try {
                Endpoint { socket, _ -> socket.response("200 OK", syntheticSummaryResponse()) }.use { endpoint -> Fixture().use { f ->
                    f.enableViewModel()
                    val (config, id) = f.config(endpoint.url)
                    f.db.apiConfigDao().updateConfig(config.copy(apiKey = ""))
                    f.db.conversationDao().updateContextWindowTokens(id, 32768, System.currentTimeMillis())
                    val original = (0 until 4000).map { index -> Message(conversationId = id,
                        role = if (index % 2 == 0) "user" else "assistant", content = "已确认的合成事实 $index。",
                        createdAt = index.toLong(), modelName = if (index % 2 == 1) config.modelName else null) }
                    f.db.messageDao().insertMessages(original)
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(10000) { while (vm.contextUsage.value.usage == null || vm.currentModelOption.value == null) delay(10) }
                        assertEquals("两千轮原文应超过此测试窗口", 1f, vm.contextUsage.value.usage!!.usagePercent, 0.0001f)
                        withContext(Dispatchers.Main) {
                            vm.setCompressionTier(CompressionTier.L4)
                            vm.setCompressionTier(CompressionTier.L2, recentRounds = 16)
                        }
                        withTimeout(30000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                        val saved = f.db.conversationDao().getConversationById(id)!!
                        assertEquals("忙时快速重复确认不能覆盖正在应用的选择", CompressionTier.L4.level, saved.compressionTier)
                        assertEquals(3998L, saved.summaryUpdatedMessageId)
                        assertFalse(saved.rollingSummary.isNullOrBlank())
                        assertTrue("确认档位必须真正调用模型整理历史", endpoint.payloads.isNotEmpty())
                        assertEquals(2, vm.contextUsage.value.usage!!.recentMessageCount)
                        assertTrue("成功保存摘要后圆环必须反映实际压缩上下文", vm.contextUsage.value.usage!!.usagePercent < 0.2f)
                        assertEquals(4000, f.db.messageDao().getMessagesList(id).size)
                        withContext(Dispatchers.Main) { vm.refreshContextUsage() }
                        withTimeout(5000) { while (vm.contextUsage.value.usage?.compressedThroughMessageId != 3998L) delay(10) }
                        assertTrue(vm.contextUsage.value.usage!!.usagePercent < 0.2f)
                    } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
                } }
            } finally { Dispatchers.resetMain() }
        }
    }

    private fun syntheticSummaryResponse(protocol: String = "openai"): String {
        val summary = "【核心背景与用户固定约束】\n合成角色遵守既有约定，事实名称和否定条件完整保留。\n" +
            "【历史关键里程碑与决策推进】\n双方完成早期方案比较，确认的决定继续有效。\n" +
            "【时空演变与关键时间节点（极重要，严禁遗漏）】\n原文未提供明确日期，事件保持原有先后顺序。\n" +
            "【当前未决议题与待办上下文】\n等待后续行动，尚未执行的建议仍是待办。"
        val response = if (protocol == "anthropic") mapOf("stop_reason" to "end_turn",
            "content" to listOf(mapOf("type" to "text", "text" to summary)))
        else mapOf("choices" to listOf(mapOf("finish_reason" to "stop",
            "message" to mapOf("role" to "assistant", "content" to summary))))
        return com.google.gson.Gson().toJson(response)
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun shoppingChatCanCompressRegenerateBranchContinueAndRenameWithoutChangingItsParentInBothProtocols() = runBlocking {
        newSingleThreadContext("shopping-branch-flow-main").use { main ->
            Dispatchers.setMain(main)
            try {
                for (protocol in listOf("openai", "anthropic")) {
                    val replies = java.util.concurrent.atomic.AtomicInteger()
                    lateinit var endpoint: Endpoint
                    endpoint = Endpoint { socket, index ->
                        val payload = com.google.gson.JsonParser.parseString(endpoint.payloads[index - 1]).asJsonObject
                        if (payload["stream"]?.asBoolean == true) {
                            socket.response("200 OK", syntheticStreamResponse(protocol, "合成购物回复${replies.incrementAndGet()}。"), "text/event-stream")
                        } else {
                            val prompt = payload.getAsJsonArray("messages").single().asJsonObject["content"].asString
                            // A saved story-time anchor also enables background timeline
                            // analysis. It is a separate request from the paid summary.
                            val response = if (prompt.contains("你是一个专业的故事时间线与常驻设定增量提取引擎")) {
                                val noUpdate = if (protocol == "anthropic") mapOf("content" to listOf(mapOf("type" to "text", "text" to "NO_UPDATE")))
                                    else mapOf("choices" to listOf(mapOf("message" to mapOf("content" to "NO_UPDATE"))))
                                com.google.gson.Gson().toJson(noUpdate)
                            } else syntheticSummaryResponse(protocol)
                            socket.response("200 OK", response)
                        }
                    }
                    endpoint.use { Fixture().use { f ->
                        f.enableViewModel()
                        // Unlike a summary-only fixture, the foreground send requires an
                        // enabled key. Keep the existing synthetic localhost credential.
                        val (_, parentId) = f.config(endpoint.url, protocol)
                        f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(parentId)!!.copy(
                            contextWindowTokens = 32768, enableSessionMemory = false, currentStoryTime = "合成购物当天"))
                        val parentVm = ChatViewModel(parentId)
                        var childVm: ChatViewModel? = null
                        var childId: Long? = null
                        try {
                            suspend fun ready(vm: ChatViewModel) {
                                withTimeout(5000) { while (vm.currentModelOption.value == null || vm.contextUsage.value.usage == null) delay(10) }
                            }
                            suspend fun send(vm: ChatViewModel, id: Long, input: String, replyNumber: Int) {
                                val previous = f.db.messageDao().getMessagesList(id)
                                withContext(Dispatchers.Main) { vm.sendMessage(input) }
                                withTimeout(10000) {
                                    while (vm.isGenerating.value || f.db.messageDao().getMessagesList(id).none {
                                        it.role == "assistant" && it.content == "合成购物回复$replyNumber。"
                                    }) {
                                        if (!vm.isGenerating.value) assertNull("正式发送提前失败：$protocol/$replyNumber", vm.error.value)
                                        delay(10)
                                    }
                                }
                                val saved = f.db.messageDao().getMessagesList(id)
                                assertNull(vm.error.value)
                                assertEquals("正式发送只新增一条输入和一条回复", previous.size + 2, saved.size)
                                assertEquals(1, saved.count { it.role == "user" && it.content == input })
                                assertEquals(previous, saved.take(previous.size))
                            }
                            ready(parentVm)
                            val shoppingSteps = listOf("商定预算并出门", "领取蓝色购物袋", "比较两种牛奶", "在生鲜区核对清单", "发现预算不足",
                                "协商退掉零食", "寻找遗失的钱包", "从服务台领回钱包", "核对找零并结账", "提着食材回家并约定下次列清单")
                            shoppingSteps.forEachIndexed { index, step ->
                                send(parentVm, parentId, "合成购物第${index + 1}轮：$step。", index + 1)
                            }
                            val originals = f.db.messageDao().getMessagesList(parentId)
                            assertEquals(20, originals.size)
                            withContext(Dispatchers.Main) { parentVm.setCompressionTier(CompressionTier.L4) }
                            withTimeout(10000) { while (parentVm.contextUsage.value.isCompressing) delay(10) }
                            val compressed = f.db.conversationDao().getConversationById(parentId)!!
                            assertEquals(CompressionTier.L4.level, compressed.compressionTier)
                            assertEquals(originals[17].id, compressed.summaryUpdatedMessageId)
                            assertFalse(compressed.rollingSummary.isNullOrBlank())
                            assertEquals(2, parentVm.contextUsage.value.usage!!.recentMessageCount)
                            assertEquals(originals, f.db.messageDao().getMessagesList(parentId))
                            val summaryPrompts = endpoint.payloads.map { com.google.gson.JsonParser.parseString(it).asJsonObject }
                                .filter { it["stream"]?.asBoolean != true && it.toString().contains("新增对话历史") }.joinToString("\n")
                            originals.take(18).forEach { assertTrue("购物过程原文必须进入摘要请求", summaryPrompts.contains("原文ID ${it.id}]")) }

                            withContext(Dispatchers.Main) { parentVm.regenerateLastMessage(originals.last()) }
                            withTimeout(10000) {
                                while (parentVm.isGenerating.value || f.db.messageDao().getMessagesList(parentId).none { it.content == "合成购物回复11。" }) delay(10)
                            }
                            assertNull(parentVm.error.value)
                            val beforeBranch = f.db.messageDao().getMessagesList(parentId)
                            assertEquals("重生成不能重复保存用户输入", 10, beforeBranch.count { it.role == "user" })
                            assertEquals(21, beforeBranch.size)
                            val regenerated = beforeBranch.single { it.content == "合成购物回复11。" }
                            val variants = beforeBranch.filter { it.variantGroupId == regenerated.variantGroupId }
                            assertEquals(listOf("合成购物回复10。", "合成购物回复11。"), variants.map { it.content })
                            val regenerationWire = endpoint.payloads.map { com.google.gson.JsonParser.parseString(it).asJsonObject }
                                .last { it["stream"]?.asBoolean == true }
                            val regenerationSystem = if (protocol == "anthropic") regenerationWire["system"].asString
                                else regenerationWire.getAsJsonArray("messages").first().asJsonObject["content"].asString
                            assertTrue(regenerationSystem.contains(compressed.rollingSummary!!))
                            assertFalse("重生成请求不能夹带旧版本回复", regenerationWire.toString().contains("合成购物回复10。"))
                            assertFalse("压缩后正式请求不能重新携带首轮原文", regenerationWire.toString().contains("合成购物第1轮"))

                            val created = CompletableDeferred<Pair<Long, String>>()
                            withContext(Dispatchers.Main) {
                                parentVm.createBranch(regenerated.id, beforeBranch) { id, title -> created.complete(id to title) }
                            }
                            val branch = withTimeout(5000) { created.await() }
                            childId = branch.first
                            assertTrue(branch.second.contains("分支"))
                            val branchLink = f.repository.getBranchByChild(branch.first)!!
                            assertEquals(parentId, branchLink.parentConversationId)
                            assertEquals(regenerated.id, branchLink.branchMessageId)
                            val copied = f.db.messageDao().getMessagesList(branch.first)
                            assertEquals(beforeBranch.map { it.content }, copied.map { it.content })
                            assertTrue("分支必须拥有独立消息记录", copied.none { copy -> beforeBranch.any { it.id == copy.id } })
                            assertEquals(2, copied.count { it.variantGroupId == copied.last().variantGroupId })
                            assertEquals(compressed.rollingSummary, f.db.conversationDao().getConversationById(branch.first)!!.rollingSummary)

                            send(parentVm, parentId, "SYNTHETIC_PARENT_ONLY：父路线决定下次改去北港。", 12)
                            val parentMessages = f.db.messageDao().getMessagesList(parentId)
                            val parentState = f.db.conversationDao().getConversationById(parentId)!!
                            val branchVm = ChatViewModel(branch.first)
                            childVm = branchVm
                            ready(branchVm)
                            send(branchVm, branch.first, "SYNTHETIC_BRANCH_ONLY：分支路线决定先整理购物袋。", 13)
                            val branchWire = endpoint.payloads.map { com.google.gson.JsonParser.parseString(it).asJsonObject }
                                .last { it["stream"]?.asBoolean == true }
                            assertTrue(branchWire.toString().contains("SYNTHETIC_BRANCH_ONLY"))
                            assertFalse("创建分支后父路线的新输入不得泄入分支", branchWire.toString().contains("SYNTHETIC_PARENT_ONLY"))
                            assertFalse(branchWire.toString().contains("合成购物回复12。"))
                            assertEquals(parentMessages, f.db.messageDao().getMessagesList(parentId))
                            val renamed = CompletableDeferred<Boolean>()
                            withContext(Dispatchers.Main) {
                                parentVm.renameConversation("  合成购物另一条路线  ", branch.first) { renamed.complete(it) }
                            }
                            assertTrue(withTimeout(5000) { renamed.await() })
                            val childState = f.db.conversationDao().getConversationById(branch.first)!!
                            assertEquals("合成购物另一条路线", childState.title)
                            assertEquals("合成购物当天", childState.currentStoryTime)
                            assertEquals(parentState.title, f.db.conversationDao().getConversationById(parentId)!!.title)
                            assertEquals(parentState.rollingSummary, f.db.conversationDao().getConversationById(parentId)!!.rollingSummary)
                            assertEquals(parentState.summaryUpdatedMessageId, f.db.conversationDao().getConversationById(parentId)!!.summaryUpdatedMessageId)
                            assertEquals(parentMessages, f.db.messageDao().getMessagesList(parentId))
                            assertEquals(copied, f.db.messageDao().getMessagesList(branch.first).take(copied.size))
                            assertEquals(13, replies.get())
                        } finally {
                            parentVm.stopGeneration()
                            childVm?.stopGeneration()
                            parentVm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                            childVm?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
                            ChatGenerationManager.cancelSession(parentId)
                            childId?.let { ChatGenerationManager.cancelSession(it) }
                        }
                    } }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun directTruncationAppliesAndPreviewsWithoutCallingSummaryOrKeepingItsFailedRetryAction() = runBlocking {
        newSingleThreadContext("direct-truncation-main").use { main ->
            Dispatchers.setMain(main)
            try {
                Endpoint { socket, _ -> socket.response("400 Bad Request", "{\"error\":{\"message\":\"synthetic summary rejection\"}}") }
                    .use { endpoint -> Fixture().use { f ->
                        f.enableViewModel()
                        f.settings.saveSettings(f.settings.getSettings().copy(autoCompressionTierEnabled = true))
                        val (config, id) = f.config(endpoint.url)
                        f.db.messageDao().insertMessages((0 until 40).map { index -> Message(conversationId = id,
                            role = if (index % 2 == 0) "user" else "assistant", content = "直接截断回归的合成事实 $index。",
                            createdAt = index.toLong(), modelName = if (index % 2 == 1) config.modelName else null) })
                        val original = f.db.messageDao().getMessagesList(id)
                        val oldSummary = "合成旧摘要：队伍持有蓝印凭证，铜钥匙尚未转交。"
                        val checkpoint = original[1].id
                        f.db.conversationDao().updateRollingSummary(id, oldSummary, checkpoint, 123L)
                        val vm = ChatViewModel(id)
                        try {
                            withTimeout(5000) { while (vm.contextUsage.value.usage == null) delay(10) }
                            withContext(Dispatchers.Main) { vm.setCompressionTier(CompressionTier.L4) }
                            withTimeout(10000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                            assertTrue("先制造一次摘要失败以覆盖旧重试状态", vm.contextUsage.value.canRetryCompression)
                            val failedSummaryCalls = endpoint.payloads.size
                            assertTrue(failedSummaryCalls > 0)

                            withContext(Dispatchers.Main) { vm.setCompressionTier(CompressionTier.LT, customPercent = 50) }
                            withTimeout(5000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                            assertEquals(CompressionTier.LT, vm.contextUsage.value.usage!!.compressionTier)
                            assertEquals(20, vm.contextUsage.value.usage!!.recentMessageCount)
                            assertFalse(vm.contextUsage.value.canRetryCompression)
                            assertTrue(vm.contextUsage.value.statusMessage.orEmpty().contains("直接截断已生效"))
                            assertFalse(vm.contextUsage.value.statusMessage.orEmpty().contains("摘要已保存"))
                            assertEquals(failedSummaryCalls, endpoint.payloads.size)

                            val confirmedUsage = vm.contextUsage.value.usage!!
                            withContext(Dispatchers.Main) { vm.previewCompressionSettings(8, 75) }
                            withTimeout(5000) { while (vm.contextUsage.value.usage!!.tierPreviews.none {
                                it.tier == CompressionTier.LT && it.retainedRoundsDesc.contains("75%") }) delay(10) }
                            assertEquals("预览不能提前保存截断比例", 50, vm.contextUsage.value.usage!!.compressionCustomPercent)
                            assertEquals("未确认前顶部用量与当前档对比条必须保持50%基线",
                                confirmedUsage.estimatedInputTokens, vm.contextUsage.value.usage!!.estimatedInputTokens)
                            assertEquals("未确认前当前原文仍保留20条", 20, vm.contextUsage.value.usage!!.recentMessageCount)
                            assertEquals("预览只更新候选档位，不应改变其他已确认用量",
                                confirmedUsage.copy(tierPreviews = vm.contextUsage.value.usage!!.tierPreviews), vm.contextUsage.value.usage)
                            assertEquals(50, f.db.conversationDao().getConversationById(id)!!.compressionCustomPercent)
                            withContext(Dispatchers.Main) { vm.refreshContextUsage() }
                            val discardedPreviewRefresh = ChatViewModel::class.java.getDeclaredField("contextUsageRefreshJob")
                                .apply { isAccessible = true }.get(vm) as Job
                            withTimeout(5000) { discardedPreviewRefresh.join() }
                            assertEquals("放弃草稿并刷新后仍为已确认的50%", 50, vm.contextUsage.value.usage!!.compressionCustomPercent)
                            assertEquals(20, vm.contextUsage.value.usage!!.recentMessageCount)
                            assertEquals(confirmedUsage.estimatedInputTokens, vm.contextUsage.value.usage!!.estimatedInputTokens)
                            assertTrue(vm.contextUsage.value.usage!!.tierPreviews.any {
                                it.tier == CompressionTier.LT && it.retainedRoundsDesc.contains("50%") })
                            assertEquals(50, f.db.conversationDao().getConversationById(id)!!.compressionCustomPercent)
                            withContext(Dispatchers.Main) { vm.setCompressionTier(CompressionTier.LT, customPercent = 75) }
                            withTimeout(5000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                            assertEquals(10, vm.contextUsage.value.usage!!.recentMessageCount)

                            withContext(Dispatchers.Main) {
                                vm.generateRollingSummaryNow()
                                vm.compressContextNow()
                            }
                            withTimeout(5000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                            withContext(Dispatchers.Main) { vm.refreshContextUsage(evaluateAuto = true) }
                            val refresh = ChatViewModel::class.java.getDeclaredField("contextUsageRefreshJob")
                                .apply { isAccessible = true }.get(vm) as Job
                            withTimeout(5000) { refresh.join() }
                            assertFalse("切入截断档后不能恢复摘要失败重试入口", vm.contextUsage.value.canRetryCompression)
                            assertNull(vm.contextUsage.value.pendingAutoTier)
                            assertEquals("截断、预览、手动压缩和刷新均不应再调用摘要", failedSummaryCalls, endpoint.payloads.size)
                            val saved = f.db.conversationDao().getConversationById(id)!!
                            assertEquals(75, saved.compressionCustomPercent)
                            assertEquals(oldSummary, saved.rollingSummary)
                            assertEquals(checkpoint, saved.summaryUpdatedMessageId)
                            assertEquals(123L, saved.summaryUpdatedAt)
                            assertEquals(original, f.db.messageDao().getMessagesList(id))

                            withContext(Dispatchers.Main) { vm.setCompressionTier(CompressionTier.L0) }
                            withTimeout(5000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                            assertEquals("切回完整保留应恢复读取全部原文", 40, vm.contextUsage.value.usage!!.recentMessageCount)
                            assertEquals(oldSummary, f.db.conversationDao().getConversationById(id)!!.rollingSummary)
                            assertEquals(failedSummaryCalls, endpoint.payloads.size)
                        } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
                    } }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun sameTierParameterChangesApplyWhilePreviewAndCompleteTiersDoNotPretendToSaveOrCharge() = runBlocking {
        newSingleThreadContext("context-policy-parameters-main").use { main ->
            Dispatchers.setMain(main)
            try {
                Endpoint { socket, _ -> socket.response("200 OK", syntheticSummaryResponse()) }.use { endpoint -> Fixture().use { f ->
                    f.enableViewModel()
                    val (config, id) = f.config(endpoint.url)
                    f.db.messageDao().insertMessages((0 until 40).map { index -> Message(conversationId = id,
                        role = if (index % 2 == 0) "user" else "assistant", content = "合成事实 $index。".repeat(10),
                        createdAt = index.toLong(), modelName = if (index % 2 == 1) config.modelName else null) })
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (vm.contextUsage.value.usage == null) delay(10) }
                        withContext(Dispatchers.Main) { vm.previewCompressionSettings(4, 80) }
                        withTimeout(5000) { while (vm.contextUsage.value.usage!!.tierPreviews.none {
                            it.tier == CompressionTier.LC && it.retainedRoundsDesc.contains("80%") }) delay(10) }
                        assertEquals("滑杆预览不得改写确认基线", 30, vm.contextUsage.value.usage!!.compressionCustomPercent)
                        assertEquals(8, vm.contextUsage.value.usage!!.compressionRecentRounds)
                        assertTrue(endpoint.payloads.isEmpty())
                        for ((percent, retained) in listOf(80 to 32, 20 to 8)) {
                            val before = endpoint.payloads.size
                            withContext(Dispatchers.Main) { vm.setCompressionTier(CompressionTier.LC, customPercent = percent) }
                            withTimeout(10000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                            assertEquals(percent, f.db.conversationDao().getConversationById(id)!!.compressionCustomPercent)
                            assertEquals(retained, vm.contextUsage.value.usage!!.recentMessageCount)
                            assertTrue("同档比例变更也要立即整理", endpoint.payloads.size > before)
                        }
                        val requests = endpoint.payloads.size
                        for (tier in listOf(CompressionTier.L0, CompressionTier.L1)) {
                            withContext(Dispatchers.Main) { vm.setCompressionTier(tier) }
                            withTimeout(5000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                            assertEquals(tier, vm.contextUsage.value.usage!!.compressionTier)
                            assertEquals("浅档应恢复可容纳的原文", 40, vm.contextUsage.value.usage!!.recentMessageCount)
                            assertEquals("完整保留与轻修剪不应主动发起付费摘要", requests, endpoint.payloads.size)
                        }
                    } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
                } }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun sendingInSameFrameWaitsForSelectedPolicySaveEvenWhenItCancelsTheOptionalSummary() = runBlocking {
        newSingleThreadContext("context-policy-save-send-main").use { main ->
            Dispatchers.setMain(main)
            val saveGate = Job()
            try {
                lateinit var endpoint: Endpoint
                endpoint = Endpoint { socket, index ->
                    val stream = com.google.gson.JsonParser.parseString(endpoint.payloads[index - 1]).asJsonObject["stream"]?.asBoolean == true
                    if (stream) socket.response("200 OK", "data: {\"choices\":[{\"delta\":{\"content\":\"已按新压缩策略继续合成剧情\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n", "text/event-stream")
                    else socket.response("200 OK", syntheticSummaryResponse())
                }
                endpoint.use { Fixture().use { f ->
                    f.enableViewModel()
                    val (config, id) = f.config(endpoint.url)
                    f.db.messageDao().insertMessages((0 until 40).map { index -> Message(conversationId = id,
                        role = if (index % 2 == 0) "user" else "assistant", content = "保存等待测试的合成事实 $index。",
                        createdAt = index.toLong(), modelName = if (index % 2 == 1) config.modelName else null) })
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (vm.contextUsage.value.usage == null || vm.currentModelOption.value == null) delay(10) }
                        ChatViewModel::class.java.getDeclaredField("systemPromptSaveJob").apply { isAccessible = true }.set(vm, saveGate)
                        withContext(Dispatchers.Main) {
                            vm.setCompressionTier(CompressionTier.L4)
                            vm.sendMessage("继续合成剧情")
                        }
                        delay(100)
                        assertTrue("档位保存完成前不能发送旧配置的报文", endpoint.payloads.isEmpty())
                        saveGate.complete()
                        withTimeout(10000) { while (f.db.messageDao().getMessagesList(id).none { it.content == "已按新压缩策略继续合成剧情" } || vm.isGenerating.value) delay(10) }
                        assertEquals(CompressionTier.L4.level, f.db.conversationDao().getConversationById(id)!!.compressionTier)
                        assertFalse(vm.contextUsage.value.isCompressing)
                        val chats = endpoint.payloads.map { com.google.gson.JsonParser.parseString(it).asJsonObject }.filter { it["stream"]?.asBoolean == true }
                        assertEquals(1, chats.size)
                        assertTrue("发送必须使用保存后的压缩范围", chats.single().getAsJsonArray("messages").size() < 10)
                    } finally {
                        saveGate.complete()
                        vm.stopGeneration()
                        vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                    }
                } }
            } finally {
                saveGate.complete()
                Dispatchers.resetMain()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun failedTierApplicationRetainsOriginalHistoryAndCanRetryWithoutReportingSuccess() = runBlocking {
        newSingleThreadContext("context-policy-failure-main").use { main ->
            Dispatchers.setMain(main)
            val failSummary = java.util.concurrent.atomic.AtomicBoolean(true)
            try {
                Endpoint { socket, _ ->
                    if (failSummary.get()) socket.response("500 Internal Server Error", "{\"error\":{\"message\":\"synthetic summary failure\"}}")
                    else socket.response("200 OK", syntheticSummaryResponse())
                }.use { endpoint -> Fixture().use { f ->
                    f.enableViewModel()
                    val (config, id) = f.config(endpoint.url)
                    f.db.apiConfigDao().updateConfig(config.copy(apiKey = ""))
                    f.db.messageDao().insertMessages((0 until 40).map { index -> Message(conversationId = id,
                        role = if (index % 2 == 0) "user" else "assistant", content = "失败重试测试的合成事实 $index。",
                        createdAt = index.toLong(), modelName = if (index % 2 == 1) config.modelName else null) })
                    val original = f.db.messageDao().getMessagesList(id)
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (vm.contextUsage.value.usage == null) delay(10) }
                        // Keep the custom policy unchanged across the failed request. The
                        // dialog must expose a retry even when the refreshed usage says that
                        // this policy has no additional raw turns to retire.
                        withContext(Dispatchers.Main) {
                            vm.setCompressionTier(CompressionTier.LC, customPercent = 50)
                        }
                        withTimeout(20000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                        assertTrue(endpoint.payloads.isNotEmpty())
                        assertEquals(CompressionTier.LC.level, f.db.conversationDao().getConversationById(id)!!.compressionTier)
                        assertEquals(50, f.db.conversationDao().getConversationById(id)!!.compressionCustomPercent)
                        assertNull(f.db.conversationDao().getConversationById(id)!!.rollingSummary)
                        assertEquals(original, f.db.messageDao().getMessagesList(id))
                        val failure = vm.contextUsage.value.statusMessage.orEmpty()
                        assertTrue("模型整理失败必须保留可见错误：$failure", failure.contains("失败") || failure.contains("500"))
                        assertFalse(failure.contains("已切换") || failure.contains("已总结并保存"))
                        assertTrue("压缩失败后应可用相同自定义比例直接重试", vm.contextUsage.value.canRetryCompression)
                        failSummary.set(false)
                        withContext(Dispatchers.Main) { vm.compressContextNow() }
                        withTimeout(10000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                        assertFalse(f.db.conversationDao().getConversationById(id)!!.rollingSummary.isNullOrBlank())
                        assertEquals(20, vm.contextUsage.value.usage!!.recentMessageCount)
                        assertFalse("压缩成功后不应继续显示重试动作", vm.contextUsage.value.canRetryCompression)
                    } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
                } }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun ordinaryPreferenceSavesCannotRestoreAnOlderSummaryOrCompressionPolicy() = runBlocking {
        newSingleThreadContext("context-policy-settings-main").use { main ->
            Dispatchers.setMain(main)
            try {
                Fixture().use { f ->
                    f.enableViewModel()
                    val (_, id) = f.config("https://example.invalid/v1", keys = "")
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (vm.contextUsage.value.usage == null) delay(10) }
                        // Simulate another operation finishing after the VM loaded its row.
                        f.db.conversationDao().updateCompressionTier(id, CompressionTier.LC.level, 12, 25)
                        f.db.conversationDao().updateRollingSummary(id, "稍后完成的合成摘要。", 38L)
                        withContext(Dispatchers.Main) {
                            vm.updateTempSettings(vm.tempSettings.value.copy(temperature = 0.45f))
                        }
                        withTimeout(5000) { while (f.db.conversationDao().getConversationById(id)?.temperature != 0.45f) delay(10) }
                        var saved = f.db.conversationDao().getConversationById(id)!!
                        assertEquals(CompressionTier.LC.level, saved.compressionTier)
                        assertEquals(25, saved.compressionCustomPercent)
                        assertEquals("稍后完成的合成摘要。", saved.rollingSummary)
                        assertEquals(38L, saved.summaryUpdatedMessageId)
                        f.db.conversationDao().updateRollingSummary(id, "更晚保存的合成摘要。", 40L)
                        withContext(Dispatchers.Main) { vm.updateSystemPrompt("合成的新系统设定") }
                        withTimeout(5000) { while (f.db.conversationDao().getConversationById(id)?.systemPrompt != "合成的新系统设定") delay(10) }
                        saved = f.db.conversationDao().getConversationById(id)!!
                        assertEquals("更晚保存的合成摘要。", saved.rollingSummary)
                        assertEquals(40L, saved.summaryUpdatedMessageId)
                        assertEquals(CompressionTier.LC.level, saved.compressionTier)
                    } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
                }
            } finally { Dispatchers.resetMain() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun selectingPolicyDuringAReplySavesForTheNextRequestWithoutStartingAnotherModelCall() = runBlocking {
        newSingleThreadContext("context-policy-during-reply-main").use { main ->
            Dispatchers.setMain(main)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            try {
                Endpoint { socket, _ ->
                    socket.streamHeaders()
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    socket.delta("data: {\"choices\":[{\"delta\":{\"content\":\"本轮合成回复正常完成\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                }.use { endpoint -> Fixture().use { f ->
                    f.enableViewModel()
                    val (_, id) = f.config(endpoint.url)
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) { while (vm.contextUsage.value.usage == null || vm.currentModelOption.value == null) delay(10) }
                        withContext(Dispatchers.Main) { vm.sendMessage("合成普通问题") }
                        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
                        assertTrue(vm.isGenerating.value)
                        withContext(Dispatchers.Main) { vm.setCompressionTier(CompressionTier.L4) }
                        withTimeout(5000) { while (vm.contextUsage.value.isCompressing) delay(10) }
                        assertEquals(1, endpoint.payloads.size)
                        val saved = f.db.conversationDao().getConversationById(id)!!
                        assertEquals(CompressionTier.L4.level, saved.compressionTier)
                        assertNull(saved.rollingSummary)
                        assertTrue(vm.contextUsage.value.statusMessage.orEmpty().contains("下一轮请求生效"))
                        release.countDown()
                        withTimeout(5000) { while (f.db.messageDao().getMessagesList(id).none { it.content == "本轮合成回复正常完成" } || vm.isGenerating.value) delay(10) }
                        assertEquals(1, endpoint.payloads.size)
                    } finally {
                        release.countDown()
                        vm.stopGeneration()
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

    private fun syntheticStreamResponse(protocol: String, text: String): String {
        val content = com.google.gson.Gson().toJson(text)
        return if (protocol == "openai")
            "data: {\"choices\":[{\"delta\":{\"content\":$content},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
        else "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":$content}}\n\ndata: {\"type\":\"message_stop\"}\n\n"
    }

    @Test fun contextFallbackWithoutReportedLimitNeverEnlargesSmallWindowsAndSendsTheSavedSummaryInBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) for (window in listOf(4000, 32768)) {
            val chatCalls = java.util.concurrent.atomic.AtomicInteger()
            lateinit var endpoint: Endpoint
            endpoint = Endpoint { socket, index ->
                val request = com.google.gson.JsonParser.parseString(endpoint.payloads[index - 1]).asJsonObject
                if (request["stream"]?.asBoolean == true) {
                    if (chatCalls.incrementAndGet() == 1) socket.response("400 Bad Request", "{\"error\":{\"message\":\"context too long\"}}")
                    else socket.response("200 OK", syntheticStreamResponse(protocol, "合成回退回复"), "text/event-stream")
                } else socket.response("200 OK", syntheticSummaryResponse(protocol))
            }
            endpoint.use { Fixture().use { f ->
                f.settings.setModelRetryEnabled(true)
                f.settings.saveRetryRule(RetryErrorType.BAD_REQUEST, RetryRule(false, 0))
                val (config, id) = f.config(endpoint.url, protocol)
                f.db.conversationDao().updateContextWindowTokens(id, window, System.currentTimeMillis())
                f.db.messageDao().insertMessages((0 until 20).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 0) "user" else "assistant", content = "合成回退事实 $index。", createdAt = index.toLong()) })
                val original = f.db.messageDao().getMessagesList(id)
                var prompts = 0
                var completed: String? = null
                var error: String? = null
                f.repository.sendChatMessageWithConfig(config, id, "继续合成回退剧情",
                    options = ChatRequestOptions(maxTokens = 512, contextWindowOverrideTokens = window,
                        enableSessionMemory = false, enableExternalMemory = false),
                    onToken = {}, onComplete = { text, _, _ -> completed = text }, onError = { error = it },
                    onContextFallbackPrompt = { reason ->
                        prompts++
                        assertTrue(reason.contains("context too long"))
                        assertNull("本例没有服务端声明的数字窗口", AiRepository.extractContextWindowFromError(reason))
                        ContextFallbackChoice.FALLBACK
                    })
                assertNull(error)
                assertEquals("合成回退回复", completed)
                assertEquals(1, prompts)
                assertEquals(2, chatCalls.get())
                val saved = f.db.conversationDao().getConversationById(id)!!
                val expectedWindow = if (window == 4000) 4000 else 16384
                assertEquals("无数字错误不得从 $window 放大窗口", expectedWindow, saved.contextWindowTokens)
                assertEquals(CompressionTier.L2.level, saved.compressionTier)
                assertEquals(4L, saved.summaryUpdatedMessageId)
                assertFalse(saved.rollingSummary.isNullOrBlank())
                val requests = endpoint.payloads.map { com.google.gson.JsonParser.parseString(it).asJsonObject }
                val chats = requests.filter { it["stream"]?.asBoolean == true }
                assertTrue("回退必须真实生成摘要", requests.any { it["stream"]?.asBoolean != true })
                assertTrue("重试报文应替换早期原文", chats.last().getAsJsonArray("messages").size() < chats.first().getAsJsonArray("messages").size())
                val system = if (protocol == "openai") chats.last().getAsJsonArray("messages").first {
                    it.asJsonObject["role"].asString == "system" }.asJsonObject["content"].asString
                else chats.last()["system"].asString
                assertTrue("重试报文应注入已保存摘要", system.contains("合成角色遵守既有约定"))
                assertEquals(original, f.db.messageDao().getMessagesList(id).take(original.size))
            } }
        }
    }

    @Test fun manualCompressionPreservesCheckpointWhenBothProtocolsReturnEmptyResponse() = runBlocking {
        val summaryCalls = java.util.concurrent.atomic.AtomicInteger()
        lateinit var endpoint: Endpoint
        endpoint = Endpoint { socket, index ->
            val request = com.google.gson.JsonParser.parseString(endpoint.payloads[index - 1]).asJsonObject
            if (request["stream"]?.asBoolean == true) {
                summaryCalls.incrementAndGet()
                socket.response("500 Internal Server Error", "{\"error\":{\"message\":\"failed to stream request: empty response detected\"}}")
            } else {
                summaryCalls.incrementAndGet()
                socket.response("500 Internal Server Error", "{\"error\":{\"message\":\"empty response detected\"}}")
            }
        }
        endpoint.use { Fixture().use { f ->
            f.settings.setModelRetryEnabled(true)
            f.settings.saveRetryRule(RetryErrorType.BAD_REQUEST, RetryRule(false, 0))
            f.settings.saveRetryRule(RetryErrorType.SERVER, RetryRule(false, 0))
            val (config, id) = f.config(endpoint.url)
            // Leave enough room for the structured summary prompt so the test
            // reaches the gateway and exercises the paired protocol failures.
            f.db.conversationDao().updateContextWindowTokens(id, 16000, System.currentTimeMillis())
            f.db.messageDao().insertMessages((0 until 20).map { index ->
                Message(conversationId = id, role = if (index % 2 == 0) "user" else "assistant",
                    content = "合成空响应兜底事实 $index。", createdAt = index.toLong())
            })
            val previous = "合成旧摘要：队伍必须保留蓝印凭证，铜钥匙尚未转交。"
            val checkpoint = f.db.messageDao().getMessagesList(id)[1].id
            f.db.conversationDao().updateRollingSummary(id, previous, checkpoint, 123L)
            val originals = f.db.messageDao().getMessagesList(id)
            val result = f.repository.generateRollingSummaryNow(
                conversationId = id,
                modelNameOverride = config.modelName,
                maxOutputTokens = 512,
                contextWindowOverrideTokens = 16000,
                apiConfigIdOverride = config.id
            )
            assertTrue("模型未生成正文时不能用本地抽取冒充成功", result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("empty response detected"))
            assertEquals("摘要非流式与流式兼容各请求一次", 2, summaryCalls.get())
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals(previous, saved.rollingSummary)
            assertEquals(checkpoint, saved.summaryUpdatedMessageId)
            assertEquals(123L, saved.summaryUpdatedAt)
            assertEquals(originals, f.db.messageDao().getMessagesList(id))
        } }
    }

    @Test fun contextFallbackSummaryFailureCannotCompleteOrResendUncompressedHistoryInEitherProtocol() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            val chatCalls = java.util.concurrent.atomic.AtomicInteger()
            lateinit var endpoint: Endpoint
            endpoint = Endpoint { socket, index ->
                val request = com.google.gson.JsonParser.parseString(endpoint.payloads[index - 1]).asJsonObject
                if (request["stream"]?.asBoolean == true) {
                    if (chatCalls.incrementAndGet() == 1) socket.response("400 Bad Request", "{\"error\":{\"message\":\"context too long\"}}")
                    else socket.response("200 OK", syntheticStreamResponse(protocol, "不应到达的未压缩回复"), "text/event-stream")
                } else socket.response("500 Internal Server Error", "{\"error\":{\"message\":\"synthetic summary failure\"}}")
            }
            endpoint.use { Fixture().use { f ->
                f.settings.setModelRetryEnabled(true)
                f.settings.saveRetryRule(RetryErrorType.BAD_REQUEST, RetryRule(false, 0))
                f.settings.saveRetryRule(RetryErrorType.SERVER, RetryRule(false, 0))
                val (config, id) = f.config(endpoint.url, protocol)
                f.db.conversationDao().updateContextWindowTokens(id, 4000, System.currentTimeMillis())
                f.db.messageDao().insertMessages((0 until 20).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 0) "user" else "assistant", content = "合成失败保护事实 $index。", createdAt = index.toLong()) })
                val original = f.db.messageDao().getMessagesList(id)
                val originalConversation = f.db.conversationDao().getConversationById(id)!!
                var prompts = 0
                var completed = 0
                var errors = 0
                val emitted = StringBuilder()
                f.repository.sendChatMessageWithConfig(config, id, "继续合成失败保护剧情",
                    options = ChatRequestOptions(maxTokens = 512, contextWindowOverrideTokens = 4000,
                        enableSessionMemory = false, enableExternalMemory = false),
                    onToken = { emitted.append(it) }, onComplete = { _, _, _ -> completed++ }, onError = { errors++ },
                    onContextFallbackPrompt = { prompts++; ContextFallbackChoice.FALLBACK })
                assertEquals(1, prompts)
                assertEquals("摘要失败不能冒充正式回复成功", 0, completed)
                assertEquals(1, errors)
                assertTrue(emitted.isEmpty())
                assertEquals("摘要失败后不得偷偷重发全历史", 1, chatCalls.get())
                assertTrue("测试必须走到真实摘要HTTP", endpoint.payloads.any {
                    com.google.gson.JsonParser.parseString(it).asJsonObject["stream"]?.asBoolean != true })
                val saved = f.db.conversationDao().getConversationById(id)!!
                assertEquals("摘要失败必须保留此前摘要", originalConversation.rollingSummary, saved.rollingSummary)
                assertEquals("摘要失败不能推进覆盖水线", originalConversation.summaryUpdatedMessageId, saved.summaryUpdatedMessageId)
                assertEquals("摘要失败不能伪造更新时间", originalConversation.summaryUpdatedAt, saved.summaryUpdatedAt)
                assertEquals(original, f.db.messageDao().getMessagesList(id))
            } }
        }
    }

    @Test fun cancellingContextFallbackSummaryPropagatesCancellationWithoutRetryOrCallbacksInEitherProtocol() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val chatCalls = java.util.concurrent.atomic.AtomicInteger()
            lateinit var endpoint: Endpoint
            endpoint = Endpoint { socket, index ->
                val request = com.google.gson.JsonParser.parseString(endpoint.payloads[index - 1]).asJsonObject
                if (request["stream"]?.asBoolean == true) {
                    chatCalls.incrementAndGet()
                    socket.response("400 Bad Request", "{\"error\":{\"message\":\"context too long\"}}")
                } else {
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    socket.response("200 OK", syntheticSummaryResponse(protocol))
                }
            }
            endpoint.use { Fixture().use { f ->
                f.settings.setModelRetryEnabled(true)
                f.settings.saveRetryRule(RetryErrorType.BAD_REQUEST, RetryRule(false, 0))
                val (config, id) = f.config(endpoint.url, protocol)
                f.db.conversationDao().updateContextWindowTokens(id, 32768, System.currentTimeMillis())
                f.db.messageDao().insertMessages((0 until 20).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 0) "user" else "assistant", content = "合成回退取消事实 $index。", createdAt = index.toLong()) })
                val original = f.db.messageDao().getMessagesList(id)
                var completed = 0
                var errors = 0
                val sending = async(Dispatchers.Default) {
                    f.repository.sendChatMessageWithConfig(config, id, "继续合成取消测试",
                        options = ChatRequestOptions(maxTokens = 512, contextWindowOverrideTokens = 32768,
                            enableSessionMemory = false, enableExternalMemory = false),
                        onToken = { fail("取消前未返回正式正文") }, onComplete = { _, _, _ -> completed++ }, onError = { errors++ },
                        onContextFallbackPrompt = { ContextFallbackChoice.FALLBACK })
                }
                try {
                    assertTrue("必须进入真实回退摘要请求后再取消", withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
                    sending.cancel(CancellationException("synthetic context fallback cancellation"))
                    try {
                        withTimeout(5000) { sending.await() }
                        fail("取消必须向调用方传播")
                    } catch (cancelled: CancellationException) {
                        assertFalse("取消必须及时完成，不能把等待超时当作取消通过", cancelled is TimeoutCancellationException)
                    }
                    assertTrue(sending.isCancelled)
                    assertEquals(0, completed)
                    assertEquals("取消不能转成普通错误回调", 0, errors)
                    assertEquals("取消摘要不能再次重发聊天", 1, chatCalls.get())
                    assertEquals(original, f.db.messageDao().getMessagesList(id))
                    assertNull(f.db.conversationDao().getConversationById(id)!!.rollingSummary)
                } finally {
                    release.countDown()
                    sending.cancelAndJoin()
                }
            } }
        }
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

    @Test fun customOpenAiPrefixesAndCompleteChatEndpointsReachTheConfiguredHttpPath() = runBlocking {
        for ((configuredPath, expectedPath) in listOf(
            "/v1beta/openai" to "/v1beta/openai/chat/completions",
            "/v1/chat/completions" to "/v1/chat/completions"
        )) {
            val remoteText = "REMOTE_CUSTOM_PATH_REPLY_529"
            lateinit var endpoint: Endpoint
            endpoint = Endpoint { socket, index ->
                val path = endpoint.requestLines[index - 1].split(' ')[1]
                if (path == expectedPath) {
                    socket.response("200 OK", syntheticStreamResponse("openai", remoteText), "text/event-stream")
                } else {
                    socket.response("404 Not Found", "{\"error\":{\"message\":\"synthetic incorrect request path\"}}")
                }
            }
            endpoint.use { Fixture().use { f ->
                val configuredUrl = endpoint.url.removeSuffix("/v1") + configuredPath
                HttpTestAccess.allow(configuredUrl)
                val (config, id) = f.config(configuredUrl)
                val emitted = StringBuilder()
                var completed: String? = null
                var error: String? = null
                f.repository.sendChatMessageWithConfig(config, id, "合成自定义路径请求",
                    onToken = { emitted.append(it) }, onComplete = { text, _, _ -> completed = text },
                    onError = { error = it })
                assertNull("自定义路径不能请求到其他端点：$configuredPath", error)
                assertEquals(listOf("POST $expectedPath HTTP/1.1"), endpoint.requestLines.toList())
                assertEquals("必须输出 HTTP 响应中的合成标识", remoteText, emitted.toString())
                assertEquals(remoteText, completed)
                assertEquals(remoteText, f.db.messageDao().getMessagesList(id).single().content)
            } }
        }
    }

    @Test fun structuredHttpStreamWithSplitThinkTagsSavesOnlyTheRemoteAnswerAsBody() = runBlocking {
        val remoteText = "REMOTE_STRUCTURED_RESPONSE_731"
        val remoteThinking = "远端合成思考阶段"
        val response = """
            data: {"choices":[{"delta":{"content":[{"type":"text","text":"<Th"}]}}]}

            data: {"choices":[{"delta":{"content":[{"type":"text","text":"InK>$remoteThinking</thi"}]}}]}

            data: {"choices":[{"delta":{"content":[{"type":"image_url","image_url":{"url":"https://example.invalid/synthetic.png"}},{"type":"output_text","text":"nk>$remoteText"}]},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent()
        Endpoint { socket, _ -> socket.response("200 OK", response, "text/event-stream") }.use { endpoint -> Fixture().use { f ->
            val (config, id) = f.config(endpoint.url)
            val emitted = StringBuilder()
            val thinking = StringBuilder()
            var completed: String? = null
            var completedThinking: String? = null
            var error: String? = null
            f.repository.sendChatMessageWithConfig(config, id, "合成结构化响应请求",
                onToken = { emitted.append(it) }, onThinkingToken = { thinking.append(it) },
                onComplete = { text, thought, _ -> completed = text; completedThinking = thought },
                onError = { error = it })
            assertNull(error)
            assertEquals("只能请求一次，不允许靠重试碰到另一响应", 1, endpoint.payloads.size)
            assertEquals("回调正文必须精确来自远端，不含标签、思考或补救提示", remoteText, emitted.toString())
            assertEquals(remoteText, completed)
            assertEquals(remoteThinking, thinking.toString())
            assertEquals(remoteThinking, completedThinking)
            val saved = f.db.messageDao().getMessagesList(id).single()
            assertEquals(remoteText, saved.content)
            assertEquals(remoteThinking, saved.thinkingContent)
        } }
    }

    @Test fun directionSnapshotsRejectDeletedRecentAndSummarizedSourcesInBothProtocols() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) for (summarized in listOf(false, true)) {
            Endpoint { socket, _ ->
                socket.response("200 OK", syntheticStreamResponse(protocol, "合成新上下文回复"), "text/event-stream")
            }.use { endpoint -> Fixture().use { f ->
                val (config, id) = f.config(endpoint.url, protocol)
                f.db.messageDao().insertMessages((0 until 12).map { index -> Message(conversationId = id,
                    role = if (index % 2 == 0) "user" else "assistant",
                    content = "合成来源事实编号$index。", createdAt = index.toLong()) })
                val sources = f.db.messageDao().getMessagesList(id)
                val conversation = f.db.conversationDao().getConversationById(id)!!
                f.db.conversationDao().updateConversation(conversation.copy(compressionTier = 2,
                    compressionRecentRounds = 4, rollingSummary = "合成摘要：测试队伍保管蓝色凭证，尚未交付。",
                    summaryUpdatedMessageId = sources[3].id, summaryUpdatedAt = 123L))
                val options = ChatRequestOptions(enableSessionMemory = false, enableExternalMemory = false)
                val prepared = f.repository.prepareReplyDirectionContext(config, id, "合成继续请求", options)
                val deleted = if (summarized) sources.first() else sources.last()
                assertEquals(!summarized, prepared.recentMessages.any { it.id == deleted.id })
                assertTrue(prepared.systemPrompt.orEmpty().contains("测试队伍保管蓝色凭证"))
                f.repository.deleteMessage(deleted)
                var planningError: Exception? = null
                try {
                    f.repository.generateReplyDirections(config, id, "合成继续请求", emptyList(),
                        options.copy(preparedDirectionContext = prepared), 2)
                } catch (error: Exception) { planningError = error }
                assertTrue(planningError?.message.orEmpty().contains("对话内容已变化"))
                var finalError: String? = null
                f.repository.sendChatMessageWithConfig(config, id, "合成继续请求",
                    options = options.copy(preparedDirectionContext = prepared), onToken = { fail(it) },
                    onComplete = { _, _, _ -> fail("不能发送删除前的快照") }, onError = { finalError = it })
                assertTrue(finalError.orEmpty().contains("对话内容已变化"))
                assertTrue("失效快照应在HTTP前拒绝", endpoint.payloads.isEmpty())
                f.db.conversationDao().updateConversation(f.db.conversationDao().getConversationById(id)!!.copy(compressionTier = 0))
                val refreshed = f.repository.prepareReplyDirectionContext(config, id, "合成继续请求", options)
                var completed: String? = null
                f.repository.sendChatMessageWithConfig(config, id, "合成继续请求",
                    options = options.copy(preparedDirectionContext = refreshed), onToken = {},
                    onComplete = { text, _, _ -> completed = text }, onError = { fail(it) })
                assertEquals("合成新上下文回复", completed)
                assertEquals(1, endpoint.payloads.size)
                assertFalse(endpoint.payloads.single().contains(deleted.content))
                assertFalse(endpoint.payloads.single().contains("测试队伍保管蓝色凭证"))
            } }
        }
    }

    @Test fun deletingContextBetweenKeyAttemptsCannotReplayTheOldPayloadInEitherProtocol() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint { socket, index ->
                if (index == 1) socket.response("401 Unauthorized", "{\"error\":\"synthetic key failure\"}")
                else socket.response("200 OK", syntheticStreamResponse(protocol, "不应触发旧上下文重试"), "text/event-stream")
            }.use { endpoint -> Fixture().use { f ->
                f.settings.setBackupKeyFallbackEnabled(true)
                val (config, id) = f.config(endpoint.url, protocol, "test-first\ntest-second")
                val sourceId = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "合成待删除的来源事实。"))
                val source = f.db.messageDao().getMessageById(sourceId)!!
                var failure: String? = null
                f.repository.sendChatMessageWithConfig(config, id, "合成继续请求", onToken = { fail(it) },
                    onKeyAttemptError = { _, _, _ -> runBlocking { f.repository.deleteMessage(source) } },
                    onComplete = { _, _, _ -> fail("不得继续发送旧上下文") }, onError = { failure = it })
                assertTrue(failure.orEmpty().contains("对话内容已变化"))
                assertEquals(1, endpoint.payloads.size)
                assertTrue(endpoint.payloads.single().contains(source.content))
                assertNull(f.db.messageDao().getMessageById(sourceId))
                var completed: String? = null
                f.repository.sendChatMessageWithConfig(config, id, "合成新的继续请求", onToken = {},
                    onComplete = { text, _, _ -> completed = text }, onError = { fail(it) })
                assertEquals("不应触发旧上下文重试", completed)
                assertEquals(2, endpoint.payloads.size)
                assertFalse(endpoint.payloads.last().contains(source.content))
            } }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test fun pausingAfterCompletedReplyAndUsageInsertCannotDuplicateEitherRecordInBothProtocols() = runBlocking {
        newSingleThreadContext("completed-save-pause-main").use { main ->
            Dispatchers.setMain(main)
            try {
                for (protocol in listOf("openai", "anthropic")) {
                    val usageInserted = CompletableDeferred<Unit>()
                    val releaseUsageReturn = CompletableDeferred<Unit>()
                    val completeReply = "合成完整回复唯一标识Q31"
                    Endpoint { socket, _ ->
                        socket.response("200 OK", syntheticStreamResponse(protocol, completeReply), "text/event-stream")
                    }.use { endpoint -> Fixture(afterUsageInsert = { stat ->
                        if (stat.requestKind == "chat" && stat.success && stat.completionStatus == "completed") {
                            // The actual Room insert has returned, so reads remain available while
                            // the repository is still between successful persistence and onComplete.
                            usageInserted.complete(Unit)
                            releaseUsageReturn.await()
                        }
                    }).use { f ->
                        f.enableViewModel()
                        val (_, id) = f.config(endpoint.url, protocol)
                        val vm = ChatViewModel(id)
                        var generation: Job? = null
                        try {
                            withTimeout(5000) {
                                while (vm.currentModelOption.value == null || vm.contextUsage.value.usage == null) delay(10)
                            }
                            withContext(Dispatchers.Main) { vm.sendMessage("合成成功保存后暂停竞态") }
                            withTimeout(5000) { usageInserted.await() }
                            val session = ChatGenerationManager.getSession(id)!!
                            val runningGeneration = session.generationJob!!
                            generation = runningGeneration
                            assertTrue("成功入库前必须已领取会话唯一保存权", session.isMessageSaved.get())
                            assertTrue("门闩必须位于完成回调前，确保暂停窗口稳定存在", session.isGenerating.value)
                            assertEquals(completeReply, f.db.messageDao().getMessagesList(id).single { it.role == "assistant" }.content)
                            assertEquals(1, f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE).size)

                            withContext(Dispatchers.Main) { vm.stopGeneration() }
                            assertNull(ChatGenerationManager.getSession(id))
                            assertFalse(vm.isGenerating.value)
                            // Pause cannot claim another save while the successful callback is held.
                            assertEquals(listOf(completeReply), f.db.messageDao().getMessagesList(id)
                                .filter { it.role == "assistant" }.map { it.content })
                            releaseUsageReturn.complete(Unit)
                            withTimeout(5000) { runningGeneration.join() }

                            val replies = f.db.messageDao().getMessagesList(id).filter { it.role == "assistant" }
                            assertEquals("正常完成与暂停竞争时只能保留一条完整回复", listOf(completeReply), replies.map { it.content })
                            assertFalse(replies.single().content.contains("生成已停止"))
                            assertEquals("实际请求只能发出一次", 1, endpoint.payloads.size)
                            val stats = f.db.usageStatDao().getStatsListByTimeRange(0, Long.MAX_VALUE)
                            assertEquals("已完成统计返回时取消也不能再补记一次取消调用", 1, stats.size)
                            assertTrue(stats.single().success)
                            assertEquals("chat", stats.single().requestKind)
                            assertEquals("completed", stats.single().completionStatus)
                            assertEquals("", vm.currentResponse.value)
                            assertEquals("", vm.currentThinking.value)
                            assertNull(vm.error.value)
                            assertNull(vm.reconnectStatus.value)
                        } finally {
                            releaseUsageReturn.complete(Unit)
                            withContext(Dispatchers.Main) { vm.stopGeneration() }
                            generation?.cancelAndJoin()
                            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                            ChatGenerationManager.cancelSession(id)
                        }
                    } }
                }
            } finally { Dispatchers.resetMain() }
        }
    }
}
