package com.aiassistant

import android.app.Application
import androidx.room.Room
import androidx.lifecycle.viewModelScope
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.data.repository.ChatGenerationManager
import com.aiassistant.data.repository.RoleplayRepository
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.ChatRequestOptions
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.CompressionTier
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.WorldBook
import com.aiassistant.domain.model.WorldBookEntry
import com.aiassistant.ui.screens.chat.ChatViewModel
import com.aiassistant.utils.CryptoManager
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.TavilySearchManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

/** Display text, persisted model text and real HTTP requests must stay distinct. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class InputInjectionRepositoryTest {
    @Before fun allowSyntheticHttpEndpoints() = HttpTestAccess.start()
    @After fun restoreClosedHttpPolicy() = HttpTestAccess.stop()

    private class Fixture : AutoCloseable {
        private val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        private val settings = PersonalizationManager(context)
        private val unsafeClass = Class.forName("sun.misc.Unsafe")
        private val unsafe = unsafeClass.getDeclaredField("theUnsafe").let { it.isAccessible = true; it.get(null) }
        private val crypto = unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, CryptoManager::class.java) as CryptoManager
        private val instanceField = AiAssistantApp::class.java.getDeclaredField("instance").apply { isAccessible = true }
        private val previousApp = instanceField.get(null)
        private val app = AiAssistantApp()
        val repository: AiRepository

        init {
            instanceField.set(null, app)
            AiAssistantApp::class.java.getDeclaredField("database").apply { isAccessible = true }.set(app, db)
            settings.saveSettings(settings.getSettings().copy(autoNameEnabled = false, autoMemoryEnabled = false, auxiliaryMemoryEnabled = false))
            repository = AiRepository(db.folderDao(), db.apiConfigDao(), db.conversationDao(), db.messageDao(),
                db.usageStatDao(), db.environmentVariableDao(), db.promptTemplateDao(), db.memoryDao(),
                db.conversationBranchDao(), db.selectedModelDao(), crypto, settings, TavilySearchManager(context, crypto),
                worldBookDao = db.worldBookDao(), timelineNodeDao = db.timelineNodeDao())
        }

        suspend fun conversation(url: String = "https://model.invalid", protocol: String = "openai"): Pair<ApiConfig, Long> {
            val config = ApiConfig(name = "Synthetic injection", provider = "proxy", baseUrl = url,
                apiKey = "test-only", modelName = "synthetic-injection-model", apiType = protocol,
                enableThinking = false, enableWebSearch = false)
            val configId = db.apiConfigDao().insertConfig(config)
            val id = db.conversationDao().insertConversation(Conversation(title = "Synthetic injection regression",
                apiConfigId = configId, modelName = config.modelName, enableSessionMemory = false))
            return config.copy(id = configId) to id
        }

        suspend fun message(conversationId: Long, role: String, content: String, createdAt: Long = 1L): Long =
            db.messageDao().insertMessage(Message(conversationId = conversationId, role = role,
                content = content, createdAt = createdAt))

        fun enableViewModel() {
            android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
                .apply { isAccessible = true }.invoke(app, context)
            AiAssistantApp::class.java.getDeclaredField("repository").apply { isAccessible = true }.set(app, repository)
            AiAssistantApp::class.java.getDeclaredField("personalizationManager").apply { isAccessible = true }.set(app, settings)
            val roleplay = RoleplayRepository(db.characterProfileDao(), db.roleplayScenarioDao(), db.roleplaySessionDao(),
                db.roleplayMemoryDao(), db.characterTagDao())
            AiAssistantApp::class.java.getDeclaredField("roleplayRepository").apply { isAccessible = true }.set(app, roleplay)
        }

        override fun close() {
            runBlocking { app.applicationScope.coroutineContext[Job]?.cancelAndJoin() }
            db.close()
            instanceField.set(null, previousApp)
        }
    }

    /** Only synthetic localhost HTTP is enabled by the suite's network guard. */
    private class Endpoint(
        private val protocol: String,
        private val failFirst: Boolean = false,
        private val summaryText: String? = null,
        private val rejectAll: Boolean = false
    ) : AutoCloseable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        val url = "http://127.0.0.1:${server.localPort}/v1"
        val payloads = CopyOnWriteArrayList<JsonObject>()
        val failures = CopyOnWriteArrayList<Throwable>()

        init {
            HttpTestAccess.allow(url)
            executor.submit {
                while (!server.isClosed) {
                    try {
                        server.accept().use { socket ->
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
                            val bytes = ByteArray(length)
                            var read = 0
                            while (read < length) {
                                val count = input.read(bytes, read, length - read)
                                check(count > 0)
                                read += count
                            }
                            payloads += JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
                            if (rejectAll) {
                                reply(socket, "500 Internal Server Error", "application/json",
                                    "{\"error\":{\"message\":\"empty response detected\",\"type\":\"internal_server_error\"}}")
                            } else if (failFirst && payloads.size == 1) {
                                reply(socket, "400 Bad Request", "application/json",
                                    "{\"error\":{\"message\":\"synthetic retry required\"}}")
                            } else if (summaryText != null && payloads.last().get("stream")?.asBoolean != true) {
                                reply(socket, "200 OK", "application/json", Gson().toJson(mapOf(
                                    "choices" to listOf(mapOf("finish_reason" to "stop", "message" to mapOf("content" to summaryText))))))
                            } else if (protocol == "anthropic") {
                                reply(socket, "200 OK", "text/event-stream",
                                    "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}\n\nevent: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
                            } else {
                                reply(socket, "200 OK", "text/event-stream",
                                    "data: {\"choices\":[{\"delta\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                            }
                        }
                    } catch (error: Throwable) {
                        if (!server.isClosed) failures += error
                    }
                }
            }
        }

        private fun reply(socket: Socket, status: String, type: String, body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            socket.getOutputStream().write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
            socket.getOutputStream().flush()
        }

        override fun close() {
            server.close()
            executor.shutdownNow()
            executor.awaitTermination(3, TimeUnit.SECONDS)
        }
    }

    private suspend fun request(f: Fixture, config: ApiConfig, id: Long, user: Message,
        options: ChatRequestOptions? = null, assistantVariantGroupId: String? = null, assistantVariantIndex: Int = 1) {
        val streamed = StringBuilder()
        var completed: String? = null
        f.repository.sendChatMessageWithConfig(config, id, user.modelContent ?: user.content,
            options = options, assistantVariantGroupId = assistantVariantGroupId, assistantVariantIndex = assistantVariantIndex,
            onToken = { streamed.append(it) },
            onComplete = { text, _, _ -> completed = text }, onError = { fail(it) })
        assertEquals("OK", completed)
        assertEquals("OK", streamed.toString())
    }

    private fun JsonObject.messageTexts(role: String): List<String> = getAsJsonArray("messages")
        .map { it.asJsonObject }.filter { it.get("role").asString == role }.map { message ->
            val content = message.get("content")
            if (content.isJsonPrimitive) content.asString else content.asJsonArray.joinToString("") {
                it.asJsonObject.get("text")?.asString.orEmpty()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private suspend fun withViewModel(
        enabled: Boolean,
        block: suspend (Fixture, Endpoint, Long, Long, ChatViewModel) -> Unit
    ) {
        newSingleThreadContext("input-injection-main").use { main ->
            Dispatchers.setMain(main)
            try {
                Endpoint("openai").use { endpoint -> Fixture().use { f ->
                    f.enableViewModel()
                    val (_, id) = f.conversation(endpoint.url)
                    val conversation = f.db.conversationDao().getConversationById(id)!!
                    f.db.conversationDao().updateConversation(conversation.copy(enableInputInjection = enabled,
                        inputInjectionTrigger = "合成VM自定义触发语", enableTimeline = false, enableThinking = false))
                    f.message(id, "user", "合成VM旧输入", 1L)
                    val assistantId = f.message(id, "assistant", "合成VM旧回复", 2L)
                    val vm = ChatViewModel(id)
                    try {
                        withTimeout(5000) {
                            while (vm.currentModelOption.value == null || vm.contextUsage.value.usage == null) delay(10)
                        }
                        block(f, endpoint, id, assistantId, vm)
                    } finally {
                        vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                        ChatGenerationManager.cancelSession(id)
                    }
                } }
            } finally { Dispatchers.resetMain() }
        }
    }

    private suspend fun awaitViewModelReply(f: Fixture, id: Long, vm: ChatViewModel, expectedCount: Int = 1) {
        withTimeout(10000) {
            while (vm.isGenerating.value || f.db.messageDao().getMessagesList(id).count {
                it.role == "assistant" && it.content == "OK"
            } < expectedCount) {
                if (!vm.isGenerating.value) assertNull("VM正式发送不能提前失败", vm.error.value)
                delay(10)
            }
        }
        assertNull(vm.error.value)
    }

    @Test fun viewModelFeatureOffUsesOrdinarySaveAndOrdinaryRequest() = runBlocking {
        withViewModel(enabled = false) { f, endpoint, id, assistantId, vm ->
            withContext(Dispatchers.Main) { vm.sendMessage("合成VM本轮输入") }
            awaitViewModelReply(f, id, vm)
            val user = f.db.messageDao().getMessagesList(id).single { it.role == "user" && it.content == "合成VM本轮输入" }
            assertNull(user.modelContent)
            assertNull(user.injectionTargetMessageId)
            assertNull(f.db.messageDao().getMessageById(assistantId)!!.modelContent)
            val payload = endpoint.payloads.single()
            assertEquals(listOf("合成VM旧回复"), payload.messageTexts("assistant"))
            assertEquals(listOf("合成VM旧输入", "合成VM本轮输入"), payload.messageTexts("user"))
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        }
    }

    @Test fun viewModelFeatureOnKeepsDisplayPromptAndSendsSavedCustomTrigger() = runBlocking {
        withViewModel(enabled = true) { f, endpoint, id, assistantId, vm ->
            withContext(Dispatchers.Main) { vm.sendMessage("合成VM本轮剧情提示") }
            awaitViewModelReply(f, id, vm)
            val user = f.db.messageDao().getMessagesList(id).single { it.role == "user" && it.content == "合成VM本轮剧情提示" }
            assertEquals("合成VM自定义触发语", user.modelContent)
            assertEquals(assistantId, user.injectionTargetMessageId)
            val assistant = f.db.messageDao().getMessageById(assistantId)!!
            assertEquals("合成VM旧回复", assistant.content)
            assertEquals("合成VM旧回复\n\n合成VM本轮剧情提示", assistant.modelContent)
            val payload = endpoint.payloads.single()
            assertEquals(listOf("合成VM旧回复\n\n合成VM本轮剧情提示"), payload.messageTexts("assistant"))
            assertEquals(listOf("合成VM旧输入", "合成VM自定义触发语"), payload.messageTexts("user"))
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        }
    }

    @Test fun viewModelRegenerationUsesOriginalTriggerAndNeverPreparesAnotherInjection() = runBlocking {
        withViewModel(enabled = true) { f, endpoint, id, assistantId, vm ->
            withContext(Dispatchers.Main) { vm.sendMessage("合成VM重生提示") }
            awaitViewModelReply(f, id, vm)
            val before = f.db.messageDao().getMessagesList(id)
            val generated = before.single { it.role == "assistant" && it.content == "OK" }
            withContext(Dispatchers.Main) { vm.regenerateLastMessage(generated) }
            awaitViewModelReply(f, id, vm, expectedCount = 2)
            val after = f.db.messageDao().getMessagesList(id)
            assertEquals(before.filter { it.role == "user" }, after.filter { it.role == "user" })
            assertEquals("合成VM旧回复\n\n合成VM重生提示", f.db.messageDao().getMessageById(assistantId)!!.modelContent)
            assertEquals(2, endpoint.payloads.size)
            endpoint.payloads.forEach { payload ->
                assertEquals(listOf("合成VM旧回复\n\n合成VM重生提示"), payload.messageTexts("assistant"))
                assertEquals(listOf("合成VM旧输入", "合成VM自定义触发语"), payload.messageTexts("user"))
            }
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        }
    }

    @Test fun ordinarySaveLeavesBothRolesAndExistingSummaryUntouchedWhenFeatureIsOff() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val assistantId = f.message(id, "assistant", "合成原回复")
            val original = f.db.conversationDao().getConversationById(id)!!
            assertFalse(original.enableInputInjection)
            f.db.conversationDao().updateConversation(original.copy(rollingSummary = "合成已有摘要",
                summaryUpdatedMessageId = assistantId, summaryUpdatedAt = 17L))
            val userId = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "合成普通输入"))
            val messages = f.db.messageDao().getMessagesList(id)
            assertEquals(listOf("合成原回复", "合成普通输入"), messages.map { it.content })
            assertEquals(listOf(null, null), messages.map { it.modelContent })
            assertNull(f.db.messageDao().getMessageById(userId)!!.injectionTargetMessageId)
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals("合成已有摘要", saved.rollingSummary)
            assertEquals(assistantId, saved.summaryUpdatedMessageId)
            assertEquals(17L, saved.summaryUpdatedAt)
        }
    }

    @Test fun injectionPersistsExactTwoNewlinesWithoutChangingEitherVisibleMessage() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val text = "合成原回复\n"
            val input = "  合成提示第一行\n第二行  "
            val trigger = "按用户自定义指令续写。"
            val assistantId = f.message(id, "assistant", text)
            val userId = f.repository.prepareInjectedUserMessage(id, input, trigger, "[]")
            val assistant = f.db.messageDao().getMessageById(assistantId)!!
            val user = f.db.messageDao().getMessageById(userId)!!
            assertEquals(text, assistant.content)
            assertEquals(text + "\n\n" + input, assistant.modelContent)
            assertEquals(input, user.content)
            assertEquals(trigger, user.modelContent)
            assertEquals(assistantId, user.injectionTargetMessageId)
            assertEquals("[]", user.attachments)
            assertEquals(2, f.db.messageDao().getMessagesList(id).size)
        }
    }

    @Test fun anotherPendingInputAppendsToExistingModelTextExactlyOnceAndRetainsEachTrigger() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val assistantId = f.message(id, "assistant", "合成原回复")
            val first = f.repository.prepareInjectedUserMessage(id, "合成提示一", "合成触发一", null)
            val second = f.repository.prepareInjectedUserMessage(id, "合成提示二", "合成触发二", null)
            val assistant = f.db.messageDao().getMessageById(assistantId)!!
            assertEquals("合成原回复", assistant.content)
            assertEquals("合成原回复\n\n合成提示一\n\n合成提示二", assistant.modelContent)
            assertEquals("合成触发一", f.db.messageDao().getMessageById(first)!!.modelContent)
            assertEquals("合成触发二", f.db.messageDao().getMessageById(second)!!.modelContent)
            assertEquals(assistantId, f.db.messageDao().getMessageById(second)!!.injectionTargetMessageId)
        }
    }

    @Test fun consecutiveTurnsInjectIntoLatestReplyAndDoNotReappendOlderUserInput() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val firstReply = f.message(id, "assistant", "合成第一段")
            f.repository.prepareInjectedUserMessage(id, "合成提示一", "合成续写", null)
            val nextReply = f.message(id, "assistant", "合成第二段", System.currentTimeMillis())
            val secondUser = f.repository.prepareInjectedUserMessage(id, "合成提示二", "合成续写", null)
            assertEquals("合成第一段\n\n合成提示一", f.db.messageDao().getMessageById(firstReply)!!.modelContent)
            assertEquals("合成第二段", f.db.messageDao().getMessageById(nextReply)!!.content)
            assertEquals("合成第二段\n\n合成提示二", f.db.messageDao().getMessageById(nextReply)!!.modelContent)
            assertEquals(nextReply, f.db.messageDao().getMessageById(secondUser)!!.injectionTargetMessageId)
        }
    }

    @Test fun noEligibleAssistantFallsBackToUnchangedUserMessageIncludingAttachments() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            f.message(id, "user", "合成开场")
            val excluded = f.db.messageDao().insertMessage(Message(conversationId = id, role = "assistant",
                content = "合成排除回复", isExcluded = true, createdAt = 2L))
            val error = f.message(id, "assistant", "请求失败\n\n合成错误", 3L)
            val attachment = "[{\"uri\":\"content://synthetic/document\",\"name\":\"synthetic.txt\",\"mimeType\":\"text/plain\"}]"
            val userId = f.repository.prepareInjectedUserMessage(id, "合成首轮提示", "合成触发语", attachment)
            val user = f.db.messageDao().getMessageById(userId)!!
            assertEquals("合成首轮提示", user.content)
            assertNull(user.modelContent)
            assertNull(user.injectionTargetMessageId)
            assertEquals(attachment, user.attachments)
            assertNull(f.db.messageDao().getMessageById(excluded)!!.modelContent)
            assertNull(f.db.messageDao().getMessageById(error)!!.modelContent)
        }
    }

    @Test fun selectedReplyVariantReceivesInjectionWithoutMutatingAlternative() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val first = f.db.messageDao().insertMessage(Message(conversationId = id, role = "assistant",
                content = "合成选中版本", variantGroupId = "synthetic-reply", variantIndex = 1, createdAt = 1L))
            val other = f.db.messageDao().insertMessage(Message(conversationId = id, role = "assistant",
                content = "合成其他版本", variantGroupId = "synthetic-reply", variantIndex = 2, createdAt = 2L))
            val user = f.repository.prepareInjectedUserMessage(id, "合成提示", "合成续写", null,
                targetAssistantId = first)
            assertEquals("合成选中版本\n\n合成提示", f.db.messageDao().getMessageById(first)!!.modelContent)
            assertNull(f.db.messageDao().getMessageById(other)!!.modelContent)
            assertEquals(first, f.db.messageDao().getMessageById(user)!!.injectionTargetMessageId)
        }
    }

    @Test fun editingCoveredBodyAndPreparingInjectionPreserveSummaryTextWatermarkAndTimestamp() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val assistantId = f.message(id, "assistant", "合成旧正文")
            val conversation = f.db.conversationDao().getConversationById(id)!!
            f.db.conversationDao().updateConversation(conversation.copy(rollingSummary = "合成保留摘要",
                summaryUpdatedMessageId = assistantId, summaryUpdatedAt = 1729L))
            val assistant = f.db.messageDao().getMessageById(assistantId)!!
            f.repository.updateMessage(assistant.copy(content = "合成编辑正文"))
            f.repository.prepareInjectedUserMessage(id, "合成追加提示", "合成续写", null)
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals("合成保留摘要", saved.rollingSummary)
            assertEquals(assistantId, saved.summaryUpdatedMessageId)
            assertEquals(1729L, saved.summaryUpdatedAt)
            assertEquals("合成编辑正文", f.db.messageDao().getMessageById(assistantId)!!.content)
            assertEquals("合成编辑正文\n\n合成追加提示", f.db.messageDao().getMessageById(assistantId)!!.modelContent)
        }
    }

    @Test fun editingVisibleInjectedInputAndAssistantRebuildsOnlyTheHiddenProjection() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val assistantId = f.message(id, "assistant", "合成原回复")
            val userId = f.repository.prepareInjectedUserMessage(id, "合成旧提示", "合成触发语", null)
            val user = f.db.messageDao().getMessageById(userId)!!
            f.repository.updateMessage(user.copy(content = "合成编辑提示"))
            assertEquals("合成原回复\n\n合成编辑提示", f.db.messageDao().getMessageById(assistantId)!!.modelContent)
            val assistant = f.db.messageDao().getMessageById(assistantId)!!
            f.repository.updateMessage(assistant.copy(content = "合成编辑回复"))
            assertEquals("合成编辑回复", f.db.messageDao().getMessageById(assistantId)!!.content)
            assertEquals("合成编辑回复\n\n合成编辑提示", f.db.messageDao().getMessageById(assistantId)!!.modelContent)
            assertEquals("合成编辑提示", f.db.messageDao().getMessageById(userId)!!.content)
            assertEquals("合成触发语", f.db.messageDao().getMessageById(userId)!!.modelContent)
        }
    }

    @Test fun deletingOneInjectedInputRebuildsRemainingSuffixAndDeletingLastRestoresOrdinaryReply() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val assistantId = f.message(id, "assistant", "合成原回复")
            val first = f.repository.prepareInjectedUserMessage(id, "合成提示一", "合成触发一", null)
            val second = f.repository.prepareInjectedUserMessage(id, "合成提示二", "合成触发二", null)
            f.repository.deleteMessage(f.db.messageDao().getMessageById(first)!!)
            assertEquals("合成原回复\n\n合成提示二", f.db.messageDao().getMessageById(assistantId)!!.modelContent)
            f.repository.deleteMessage(f.db.messageDao().getMessageById(second)!!)
            val assistant = f.db.messageDao().getMessageById(assistantId)!!
            assertEquals("合成原回复", assistant.content)
            assertNull(assistant.modelContent)
            assertEquals(listOf(assistantId), f.db.messageDao().getMessagesList(id).map { it.id })
        }
    }

    @Test fun deletingInjectionTargetRestoresRemainingUserToItsOriginalPrompt() = runBlocking {
        Endpoint("openai").use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            f.message(id, "user", "合成旧输入", 1L)
            val assistantId = f.message(id, "assistant", "合成待删除回复", 2L)
            val userId = f.repository.prepareInjectedUserMessage(id, "合成应保留原提示", "合成隐藏触发语", null)
            f.repository.deleteMessage(f.db.messageDao().getMessageById(assistantId)!!)
            val user = f.db.messageDao().getMessageById(userId)!!
            assertEquals("合成应保留原提示", user.content)
            assertNull(user.modelContent)
            assertNull(user.injectionTargetMessageId)
            request(f, config, id, user)
            val users = endpoint.payloads.single().messageTexts("user").joinToString("\n")
            assertTrue(users.contains("合成应保留原提示"))
            assertFalse(users.contains("合成隐藏触发语"))
            assertTrue(endpoint.payloads.single().messageTexts("assistant").isEmpty())
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun summaryAlreadyCoveringTargetCannotHideNewInjectedPromptFromTheRealRequest() = runBlocking {
        Endpoint("openai").use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            var assistantId = 0L
            repeat(20) { index ->
                f.message(id, "user", "合成旧输入$index", index * 2L)
                assistantId = f.message(id, "assistant", "合成旧回复$index", index * 2L + 1)
            }
            val conversation = f.db.conversationDao().getConversationById(id)!!
            f.db.conversationDao().updateConversation(conversation.copy(rollingSummary = "合成旧摘要不含新提示。",
                summaryUpdatedMessageId = assistantId, summaryUpdatedAt = 919L,
                compressionTier = CompressionTier.L4.level))
            val userId = f.repository.prepareInjectedUserMessage(id, "合成刚输入的关键剧情事实", "合成续写触发语", null)
            request(f, config, id, f.db.messageDao().getMessageById(userId)!!)
            assertEquals("注入不应自动重建已有摘要", 1, endpoint.payloads.size)
            val payload = endpoint.payloads.single()
            assertTrue(payload.messageTexts("assistant").any { it == "合成旧回复19\n\n合成刚输入的关键剧情事实" })
            assertEquals("合成续写触发语", payload.messageTexts("user").last())
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals("合成旧摘要不含新提示。", saved.rollingSummary)
            assertEquals(assistantId, saved.summaryUpdatedMessageId)
            assertEquals(919L, saved.summaryUpdatedAt)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun manualSummaryRegenerationReReadsEditedCoveredBodyInsteadOfReusingOldSummary() = runBlocking {
        val remote = "合成远端新摘要：北港队伍已确认编辑后的蓝印通行规则，仍保留铜钥匙并等待领航员确认路线。"
        Endpoint("openai", summaryText = remote).use { endpoint -> Fixture().use { f ->
            val (_, id) = f.conversation(endpoint.url)
            f.message(id, "user", "合成旧问题", 1L)
            val assistantId = f.message(id, "assistant", "合成旧回答", 2L)
            val conversation = f.db.conversationDao().getConversationById(id)!!
            f.db.conversationDao().updateConversation(conversation.copy(rollingSummary = "合成旧摘要",
                summaryUpdatedMessageId = assistantId, summaryUpdatedAt = 717L))
            f.repository.updateMessage(f.db.messageDao().getMessageById(assistantId)!!.copy(content = "合成编辑后的蓝印通行规则"))
            assertEquals("合成旧摘要", f.db.conversationDao().getConversationById(id)!!.rollingSummary)
            f.repository.generateRollingSummaryNow(id, rebuildSummary = true).getOrThrow()
            assertEquals(1, endpoint.payloads.size)
            val prompt = endpoint.payloads.single().messageTexts("user").single()
            assertTrue(prompt.contains("合成编辑后的蓝印通行规则"))
            assertFalse(prompt.contains("合成旧回答"))
            assertEquals(remote, f.db.conversationDao().getConversationById(id)!!.rollingSummary)
            assertEquals(assistantId, f.db.conversationDao().getConversationById(id)!!.summaryUpdatedMessageId)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun failedManualSummaryRegenerationRetainsOldSummaryAndEditedOriginalBody() = runBlocking {
        Endpoint("openai", rejectAll = true).use { endpoint -> Fixture().use { f ->
            val (_, id) = f.conversation(endpoint.url)
            f.message(id, "user", "合成旧问题", 1L)
            val assistantId = f.message(id, "assistant", "合成旧回答", 2L)
            val conversation = f.db.conversationDao().getConversationById(id)!!
            f.db.conversationDao().updateConversation(conversation.copy(rollingSummary = "合成应保留旧摘要",
                summaryUpdatedMessageId = assistantId, summaryUpdatedAt = 817L))
            f.repository.updateMessage(f.db.messageDao().getMessageById(assistantId)!!.copy(content = "合成编辑后正文"))
            val result = f.repository.generateRollingSummaryNow(id, rebuildSummary = true)
            assertTrue(result.isFailure)
            assertEquals(2, endpoint.payloads.size)
            val saved = f.db.conversationDao().getConversationById(id)!!
            assertEquals("合成应保留旧摘要", saved.rollingSummary)
            assertEquals(assistantId, saved.summaryUpdatedMessageId)
            assertEquals(817L, saved.summaryUpdatedAt)
            assertEquals("合成编辑后正文", f.db.messageDao().getMessageById(assistantId)!!.content)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun invalidCrossConversationTargetRejectsWholePreparationWithoutChangingEitherHistory() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val (_, otherId) = f.conversation()
            f.message(id, "assistant", "合成本会话回复")
            val otherAssistant = f.message(otherId, "assistant", "合成另一会话回复")
            val before = f.db.messageDao().getMessagesList(id)
            val otherBefore = f.db.messageDao().getMessagesList(otherId)
            val failure = runCatching {
                f.repository.prepareInjectedUserMessage(id, "合成提示", "合成触发语", null,
                    targetAssistantId = otherAssistant)
            }.exceptionOrNull()
            assertNotNull("跨会话目标不能静默注入到其他会话", failure)
            assertEquals(before, f.db.messageDao().getMessagesList(id))
            assertEquals(otherBefore, f.db.messageDao().getMessagesList(otherId))
        }
    }

    @Test fun duplicateConversationRemapsInjectionTargetAndKeepsBothBodiesAndConfiguration() = runBlocking {
        Fixture().use { f ->
            f.enableViewModel()
            val (_, id) = f.conversation()
            val conversation = f.db.conversationDao().getConversationById(id)!!
            f.db.conversationDao().updateConversation(conversation.copy(enableInputInjection = true,
                inputInjectionTrigger = "合成复制自定义触发语"))
            f.message(id, "user", "合成复制旧输入", 1L)
            val assistantId = f.message(id, "assistant", "合成复制旧回复", 2L)
            val userId = f.repository.prepareInjectedUserMessage(id, "合成复制剧情提示", "合成复制自定义触发语", null)
            val before = f.db.messageDao().getMessagesList(id)
            val copiedId = f.repository.duplicateConversation(id)
            assertTrue(copiedId > 0L && copiedId != id)
            val copied = f.db.messageDao().getMessagesList(copiedId)
            assertEquals(before.map { it.content }, copied.map { it.content })
            assertEquals(before.map { it.modelContent }, copied.map { it.modelContent })
            val copiedAssistant = copied.single { it.role == "assistant" }
            val copiedUser = copied.single { it.content == "合成复制剧情提示" }
            assertEquals(copiedAssistant.id, copiedUser.injectionTargetMessageId)
            assertNotEquals(assistantId, copiedAssistant.id)
            assertNotEquals(userId, copiedUser.id)
            assertEquals("合成复制旧回复\n\n合成复制剧情提示", copiedAssistant.modelContent)
            val copiedConversation = f.db.conversationDao().getConversationById(copiedId)!!
            assertTrue(copiedConversation.enableInputInjection)
            assertEquals("合成复制自定义触发语", copiedConversation.inputInjectionTrigger)
            assertEquals(before, f.db.messageDao().getMessagesList(id))
        }
    }

    @Test fun staleEditCannotReviveClearedInjectionMetadataOrDeletedHiddenSuffix() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val assistantId = f.message(id, "assistant", "合成将删除的模型回复")
            val userId = f.repository.prepareInjectedUserMessage(id, "合成原提示", "合成旧触发语", null)
            val staleUser = f.db.messageDao().getMessageById(userId)!!
            f.repository.deleteMessage(f.db.messageDao().getMessageById(assistantId)!!)
            assertNull(f.db.messageDao().getMessageById(userId)!!.injectionTargetMessageId)
            f.repository.updateMessage(staleUser.copy(content = "合成编辑后的原提示"))
            val user = f.db.messageDao().getMessageById(userId)!!
            assertEquals("合成编辑后的原提示", user.content)
            assertNull("旧编辑快照不能恢复已解除的触发语", user.modelContent)
            assertNull("旧编辑快照不能恢复已删除的目标关联", user.injectionTargetMessageId)

            val (_, otherId) = f.conversation()
            val otherAssistantId = f.message(otherId, "assistant", "合成应保留模型正文")
            val otherUserId = f.repository.prepareInjectedUserMessage(otherId, "合成将删除的提示", "合成触发语", null)
            val staleAssistant = f.db.messageDao().getMessageById(otherAssistantId)!!
            f.repository.deleteMessage(f.db.messageDao().getMessageById(otherUserId)!!)
            assertNull(f.db.messageDao().getMessageById(otherAssistantId)!!.modelContent)
            f.repository.updateMessage(staleAssistant.copy(content = "合成编辑后模型正文"))
            val assistant = f.db.messageDao().getMessageById(otherAssistantId)!!
            assertEquals("合成编辑后模型正文", assistant.content)
            assertNull("旧编辑快照不能恢复已删除用户提示的隐藏后缀", assistant.modelContent)
        }
    }

    @Test fun duplicateKeepsSummaryWatermarkOnOlderReplySelectedByInjection() = runBlocking {
        Fixture().use { f ->
            f.enableViewModel()
            val (_, id) = f.conversation()
            f.message(id, "user", "合成选择旧版的开场", 1L)
            val selectedId = f.db.messageDao().insertMessage(Message(conversationId = id, role = "assistant",
                content = "合成被选中的旧版本", variantGroupId = "synthetic-covered-variants", variantIndex = 1, createdAt = 2L))
            f.db.messageDao().insertMessage(Message(conversationId = id, role = "assistant",
                content = "合成未选中的新版本", variantGroupId = "synthetic-covered-variants", variantIndex = 2, createdAt = 3L))
            f.repository.prepareInjectedUserMessage(id, "合成锁定旧版本的提示", "合成触发语", null,
                targetAssistantId = selectedId)
            val conversation = f.db.conversationDao().getConversationById(id)!!
            f.db.conversationDao().updateConversation(conversation.copy(rollingSummary = "合成覆盖所选旧版本的摘要",
                summaryUpdatedMessageId = selectedId, summaryUpdatedAt = 221L))
            val before = f.db.messageDao().getMessagesList(id)
            val copyId = f.repository.duplicateConversation(id)
            val copied = f.db.messageDao().getMessagesList(copyId)
            val selectedCopy = copied.single { it.content == "合成被选中的旧版本" }
            val copiedUser = copied.single { it.content == "合成锁定旧版本的提示" }
            assertEquals(selectedCopy.id, copiedUser.injectionTargetMessageId)
            val copy = f.db.conversationDao().getConversationById(copyId)!!
            assertEquals("合成覆盖所选旧版本的摘要", copy.rollingSummary)
            assertEquals("映射后的所选旧版本仍必须作为摘要覆盖水位", selectedCopy.id, copy.summaryUpdatedMessageId)
            assertEquals(221L, copy.summaryUpdatedAt)
            assertEquals("合成被选中的旧版本\n\n合成锁定旧版本的提示", selectedCopy.modelContent)
            assertEquals(before, f.db.messageDao().getMessagesList(id))
        }
    }

    @Test fun branchEndingAtInjectedAssistantDoesNotCopyLaterPromptIntoHiddenBody() = runBlocking {
        Fixture().use { f ->
            f.enableViewModel()
            val (_, id) = f.conversation()
            f.message(id, "user", "合成分支旧输入", 1L)
            val assistantId = f.message(id, "assistant", "合成分支截止回复", 2L)
            f.repository.prepareInjectedUserMessage(id, "合成截止后的剧情提示", "合成分支触发语", null)
            f.repository.updateConversationCompressionTier(id, CompressionTier.LT, 12, 65)
            val before = f.db.messageDao().getMessagesList(id)
            val branch = f.repository.createBranchConversation(id, assistantId, before)
            val branchConversation = f.db.conversationDao().getConversationById(branch.first)!!
            assertEquals(CompressionTier.LT.level, branchConversation.compressionTier)
            assertEquals(12, branchConversation.compressionRecentRounds)
            assertEquals(65, branchConversation.compressionCustomPercent)
            val parentConversation = f.db.conversationDao().getConversationById(id)!!
            assertEquals(CompressionTier.LT.level, parentConversation.compressionTier)
            assertEquals(12, parentConversation.compressionRecentRounds)
            assertEquals(65, parentConversation.compressionCustomPercent)
            val copied = f.db.messageDao().getMessagesList(branch.first)
            assertEquals(listOf("合成分支旧输入", "合成分支截止回复"), copied.map { it.content })
            assertTrue(copied.all { it.modelContent == null && it.injectionTargetMessageId == null })
            assertEquals(before, f.db.messageDao().getMessagesList(id))
            assertEquals("合成分支截止回复\n\n合成截止后的剧情提示", f.db.messageDao().getMessageById(assistantId)!!.modelContent)
        }
    }

    @Test fun editedOldUserSelectsOnlyAssistantBeforeOriginalUserAndNeverFutureReply() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            f.message(id, "user", "合成前轮输入", 1L)
            val assistantId = f.message(id, "assistant", "合成原始注入目标", 2L)
            val originalUserId = f.repository.prepareInjectedUserMessage(id, "合成旧剧情提示", "合成旧触发语", null,
                userVariantGroupId = "synthetic-edit-user", userVariantIndex = 1)
            val futureAssistant = f.message(id, "assistant", "合成未来回复", System.currentTimeMillis() + 1000L)
            val editedId = f.repository.prepareInjectedUserMessage(id, "合成编辑剧情提示", "合成编辑触发语", null,
                userVariantGroupId = "synthetic-edit-user", userVariantIndex = 2,
                beforeUserMessageId = originalUserId)
            val edited = f.db.messageDao().getMessageById(editedId)!!
            assertEquals(assistantId, edited.injectionTargetMessageId)
            assertEquals("合成编辑触发语", edited.modelContent)
            assertEquals("合成原始注入目标\n\n合成编辑剧情提示", f.db.messageDao().getMessageById(assistantId)!!.modelContent)
            assertNull(f.db.messageDao().getMessageById(futureAssistant)!!.modelContent)
            assertEquals("合成旧剧情提示", f.db.messageDao().getMessageById(originalUserId)!!.content)
        }
    }

    @Test fun ordinaryReplacementUserVariantRemovesInactiveOldInjectionFromAssistantBody() = runBlocking {
        Fixture().use { f ->
            val (_, id) = f.conversation()
            val assistantId = f.message(id, "assistant", "合成原始回复")
            val originalUserId = f.repository.prepareInjectedUserMessage(id, "合成旧注入提示", "合成旧触发语", null,
                userVariantGroupId = "synthetic-disabled-user", userVariantIndex = 1)
            val ordinaryId = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "合成关闭后普通编辑提示",
                variantGroupId = "synthetic-disabled-user", variantIndex = 2))
            val ordinary = f.db.messageDao().getMessageById(ordinaryId)!!
            assertNull(ordinary.modelContent)
            assertNull(ordinary.injectionTargetMessageId)
            val assistant = f.db.messageDao().getMessageById(assistantId)!!
            assertEquals("合成原始回复", assistant.content)
            assertNull(assistant.modelContent)
            assertEquals("合成旧注入提示", f.db.messageDao().getMessageById(originalUserId)!!.content)
            assertEquals("合成旧触发语", f.db.messageDao().getMessageById(originalUserId)!!.modelContent)
        }
    }

    @Test fun realRequestsUseAppendedAssistantAndCustomTriggerWhileRetryKeepsStoredMessagesIdentical() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint(protocol).use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url, protocol)
                f.message(id, "user", "合成前轮输入", 1L)
                val assistantId = f.message(id, "assistant", "合成前轮回复", 2L)
                val userId = f.repository.prepareInjectedUserMessage(id, "合成本轮剧情提示", "合成自定义触发语", null)
                val user = f.db.messageDao().getMessageById(userId)!!
                val before = f.db.messageDao().getMessagesList(id)
                request(f, config, id, user, assistantVariantGroupId = "synthetic-retry-reply", assistantVariantIndex = 1)
                request(f, config, id, user, assistantVariantGroupId = "synthetic-retry-reply", assistantVariantIndex = 2)
                assertEquals(2, endpoint.payloads.size)
                endpoint.payloads.forEach { payload ->
                    assertEquals(listOf("合成前轮回复\n\n合成本轮剧情提示"), payload.messageTexts("assistant"))
                    assertEquals(listOf("合成前轮输入", "合成自定义触发语"), payload.messageTexts("user"))
                }
                val after = f.db.messageDao().getMessagesList(id)
                assertEquals(before, after.filter { message -> before.any { it.id == message.id } })
                val generated = after.filter { message -> before.none { it.id == message.id } }
                assertEquals(2, generated.size)
                assertEquals(listOf("OK", "OK"), generated.map { it.content })
                assertTrue(generated.all { it.role == "assistant" && it.variantGroupId == "synthetic-retry-reply" })
                assertEquals(listOf(1, 2), generated.map { it.variantIndex })
                assertEquals("合成前轮回复", f.db.messageDao().getMessageById(assistantId)!!.content)
                assertEquals("合成本轮剧情提示", f.db.messageDao().getMessageById(userId)!!.content)
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }

    @Test fun ordinaryHttpRequestWithFeatureOffKeepsCurrentUserAndAssistantTextUnchanged() = runBlocking {
        Endpoint("openai").use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            f.message(id, "user", "合成旧输入", 1L)
            f.message(id, "assistant", "合成旧回复", 2L)
            val userId = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "合成本轮普通输入"))
            request(f, config, id, f.db.messageDao().getMessageById(userId)!!)
            val payload = endpoint.payloads.single()
            assertEquals(listOf("合成旧回复"), payload.messageTexts("assistant"))
            assertEquals(listOf("合成旧输入", "合成本轮普通输入"), payload.messageTexts("user"))
            assertTrue(f.db.messageDao().getMessagesList(id).all { it.modelContent == null && it.injectionTargetMessageId == null })
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun failedHttpRequestThenManualRetryDoesNotReinjectOrReplaceVisiblePrompt() = runBlocking {
        Endpoint("openai", failFirst = true).use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            f.message(id, "user", "合成旧输入", 1L)
            f.message(id, "assistant", "合成旧回复", 2L)
            val userId = f.repository.prepareInjectedUserMessage(id, "合成真实提示", "合成触发语", null)
            val user = f.db.messageDao().getMessageById(userId)!!
            val before = f.db.messageDao().getMessagesList(id)
            var error: String? = null
            f.repository.sendChatMessageWithConfig(config, id, user.modelContent!!, onToken = {},
                onComplete = { _, _, _ -> fail("合成400响应不能成功") }, onError = { error = it })
            assertTrue("应保留真实请求失败", error?.contains("400") == true)
            assertEquals(before, f.db.messageDao().getMessagesList(id))
            request(f, config, id, user)
            assertEquals(2, endpoint.payloads.size)
            assertEquals(endpoint.payloads[0].get("messages"), endpoint.payloads[1].get("messages"))
            val after = f.db.messageDao().getMessagesList(id)
            assertEquals(before, after.filter { message -> before.any { it.id == message.id } })
            val generated = after.filter { message -> before.none { it.id == message.id } }.single()
            assertEquals("assistant", generated.role)
            assertEquals("OK", generated.content)
            assertEquals("合成真实提示", f.db.messageDao().getMessageById(userId)!!.content)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun disablingFeatureLeavesHistoricalInjectionStableAndSendsNewUserMessageNormally() = runBlocking {
        Endpoint("openai").use { endpoint -> Fixture().use { f ->
            val (config, id) = f.conversation(endpoint.url)
            f.message(id, "user", "合成首轮输入", 1L)
            val assistantId = f.message(id, "assistant", "合成首轮回复", 2L)
            f.repository.prepareInjectedUserMessage(id, "合成注入提示", "合成旧触发语", null)
            f.message(id, "assistant", "合成第二轮回复", System.currentTimeMillis())
            val conversation = f.db.conversationDao().getConversationById(id)!!
            f.db.conversationDao().updateConversation(conversation.copy(enableInputInjection = false,
                inputInjectionTrigger = "合成已修改触发语"))
            val userId = f.repository.saveMessage(Message(conversationId = id, role = "user", content = "合成新普通输入"))
            val user = f.db.messageDao().getMessageById(userId)!!
            request(f, config, id, user)
            val payload = endpoint.payloads.single()
            assertEquals(listOf("合成首轮回复\n\n合成注入提示", "合成第二轮回复"), payload.messageTexts("assistant"))
            assertEquals(listOf("合成首轮输入", "合成旧触发语", "合成新普通输入"), payload.messageTexts("user"))
            assertNull(user.modelContent)
            assertNull(user.injectionTargetMessageId)
            assertEquals("合成首轮回复", f.db.messageDao().getMessageById(assistantId)!!.content)
            assertEquals("合成首轮回复\n\n合成注入提示", f.db.messageDao().getMessageById(assistantId)!!.modelContent)
            assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
        } }
    }

    @Test fun originalPromptStillActivatesWorldBookWhenModelReceivesOnlyTrigger() = runBlocking {
        for (protocol in listOf("openai", "anthropic")) {
            Endpoint(protocol).use { endpoint -> Fixture().use { f ->
                val (config, id) = f.conversation(endpoint.url, protocol)
                f.message(id, "user", "合成旧输入", 1L)
                f.message(id, "assistant", "合成旧回复", 2L)
                val book = f.db.worldBookDao().insertBook(WorldBook(name = "合成资料"))
                val lore = "合成星港的蓝印通行证必须由领航员签字。"
                f.db.worldBookDao().insertEntry(WorldBookEntry(bookId = book, name = "合成星港",
                    keys = "SyntheticHarbor", content = lore))
                assertEquals(listOf(lore), f.repository.matchWorldBookEntries(
                    "去 SyntheticHarbor 核对合成通行证", listOf(book)).map { it.content })
                // Avoid relying on the injected assistant history to activate this entry.
                // A plain current user plus retrieval override exercises the override directly.
                val userId = f.repository.saveMessage(Message(conversationId = id, role = "user",
                    content = "合成显示提示", modelContent = "合成触发语"))
                request(f, config, id, f.db.messageDao().getMessageById(userId)!!,
                    ChatRequestOptions(enableWorldBook = true, activeWorldBookIds = book.toString(),
                        retrievalQuery = "去 SyntheticHarbor 核对合成通行证"))
                val payload = endpoint.payloads.single()
                val system = if (protocol == "anthropic") payload.get("system").asString
                    else payload.messageTexts("system").joinToString("\n")
                assertTrue("原始输入应触发世界书，而非只用触发语检索", system.contains(lore))
                assertEquals("合成触发语", payload.messageTexts("user").last())
                assertTrue(endpoint.failures.toString(), endpoint.failures.isEmpty())
            } }
        }
    }
}
