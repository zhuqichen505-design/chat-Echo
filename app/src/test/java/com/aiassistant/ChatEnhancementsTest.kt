package com.aiassistant

import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.ui.screens.chat.isErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ChatEnhancementsTest {
    @Test fun directTruncationPercentageChangesRemainPendingUntilConfirmed() {
        val saved = com.aiassistant.ui.screens.chat.ContextCompressionSelection(
            com.aiassistant.domain.model.CompressionTier.LT, recentRounds = 8, customPercent = 30)

        assertTrue(saved.matches(saved.copy(recentRounds = 16)))
        assertFalse("修改截断比例必须使确认按钮可用", saved.matches(saved.copy(customPercent = 75)))
        assertFalse("保留比例和截断比例不能当成同一策略", saved.matches(saved.copy(tier = com.aiassistant.domain.model.CompressionTier.LC)))
        assertFalse(saved.matches(null))
    }

    @Test fun savedDirectTruncationRestoresItsConfirmedPercentageAsTheDialogBaseline() {
        val usage = com.aiassistant.domain.model.ConversationContextUsage(
            compressionTier = com.aiassistant.domain.model.CompressionTier.LT,
            compressionCustomPercent = 65,
            compressionRecentRounds = 12)

        val selection = com.aiassistant.ui.screens.chat.ContextCompressionSelection.from(usage)

        assertEquals(com.aiassistant.domain.model.CompressionTier.LT, selection.tier)
        assertEquals(65, selection.customPercent)
        assertTrue(selection.matches(com.aiassistant.ui.screens.chat.ContextCompressionSelection.from(usage)))
        assertFalse(selection.copy(customPercent = 60).matches(selection))
    }

    @Test fun reopeningRegeneratingConversationRecoversVariantHostInsteadOfHidingBehindOldReply() {
        val conversationId = 908090L
        val manager = com.aiassistant.data.repository.ChatGenerationManager
        val session = manager.startSession(conversationId, "synthetic-model", "reply_11", 2)
        try {
            session.anchorUserMessageId = 10
            session.appendResponse("new reply still streaming")
            val user = com.aiassistant.domain.model.Message(id = 10, conversationId = conversationId, role = "user", content = "question")
            val oldReply = com.aiassistant.domain.model.Message(id = 11, conversationId = conversationId, role = "assistant",
                content = "old reply", variantGroupId = "reply_11")
            val display = com.aiassistant.ui.screens.chat.buildDisplayMessages(listOf(user, oldReply), emptyMap())
            assertTrue(com.aiassistant.ui.screens.chat.chatReplyRowKeys(display, user.id).hasPersistedReply)
            val restoredGroup = com.aiassistant.ui.screens.chat.activeStreamingBranchGroupId(conversationId)
            assertEquals("reply_11", restoredGroup)
            assertTrue(display.any { it.groupId == restoredGroup })
            assertEquals("new reply still streaming", manager.getSession(conversationId)!!.currentResponse.value)
            session.markFinished()
            assertEquals(null, com.aiassistant.ui.screens.chat.activeStreamingBranchGroupId(conversationId))
        } finally { manager.removeSession(conversationId, session) }
        assertEquals(null, com.aiassistant.ui.screens.chat.activeStreamingBranchGroupId(conversationId))
    }

    @Test fun streamingReplyKeepsItsRowKeyWhenSavedAndAcrossVariantSelection() {
        val user = com.aiassistant.domain.model.Message(id = 10, conversationId = 1, role = "user", content = "question")
        val reply = com.aiassistant.domain.model.Message(id = 11, conversationId = 1, role = "assistant", content = "long reply")
        fun rows(messages: List<com.aiassistant.domain.model.Message>, selections: Map<String, Int> = emptyMap()) =
            com.aiassistant.ui.screens.chat.chatReplyRowKeys(com.aiassistant.ui.screens.chat.buildDisplayMessages(messages, selections), user.id)
        val streaming = rows(listOf(user))
        val saved = rows(listOf(user, reply))
        assertFalse(streaming.hasPersistedReply)
        assertTrue(saved.hasPersistedReply)
        assertEquals(streaming.streamingKey, saved.messageKeys.last())
        val first = reply.copy(variantGroupId = "reply_11", variantIndex = 1)
        val second = first.copy(id = 12, variantIndex = 2, content = "another long reply")
        assertEquals(saved.messageKeys, rows(listOf(user, first, second), mapOf("reply_11" to 1)).messageKeys)
        assertEquals(saved.messageKeys, rows(listOf(user, first, second), mapOf("reply_11" to 2)).messageKeys)
    }

    @Test fun replyRowsRemainUniqueForExtraRepliesAndEditedUserBranches() {
        val user = com.aiassistant.domain.model.Message(id = 20, conversationId = 1, role = "user", content = "first", variantGroupId = "turn_20_user")
        val edited = user.copy(id = 23, content = "edited", variantIndex = 2)
        val reply = com.aiassistant.domain.model.Message(id = 21, conversationId = 1, role = "assistant", content = "reply", variantGroupId = "turn_20_assistant")
        val extra = reply.copy(id = 22, content = "extra", variantGroupId = null)
        val messages = listOf(user, reply, extra, edited)
        val first = com.aiassistant.ui.screens.chat.chatReplyRowKeys(
            com.aiassistant.ui.screens.chat.buildDisplayMessages(messages, mapOf("turn_20_user" to 1)), edited.id, "turn_20_user")
        val second = com.aiassistant.ui.screens.chat.chatReplyRowKeys(
            com.aiassistant.ui.screens.chat.buildDisplayMessages(messages, mapOf("turn_20_user" to 2)), edited.id, "turn_20_user")
        assertEquals(first.messageKeys.size, first.messageKeys.toSet().size)
        assertEquals(first.messageKeys, second.messageKeys)
        assertEquals(first.streamingKey, first.messageKeys[1])
        val missingAnchor = com.aiassistant.ui.screens.chat.chatReplyRowKeys(
            com.aiassistant.ui.screens.chat.buildDisplayMessages(messages, emptyMap()), 999L)
        assertFalse(missingAnchor.hasPersistedReply)
        assertFalse(missingAnchor.streamingKey in missingAnchor.messageKeys)
    }

    @Test fun assistantPersistenceDoesNotRequestScrollButNewUserInputDoes() {
        val user = com.aiassistant.domain.model.Message(id = 30, conversationId = 1, role = "user", content = "question")
        val reply = com.aiassistant.domain.model.Message(id = 31, conversationId = 1, role = "assistant", content = "reply")
        assertFalse(com.aiassistant.ui.screens.chat.hasNewUserMessage(setOf(user.id), listOf(user, reply)))
        assertFalse(com.aiassistant.ui.screens.chat.hasNewUserMessage(setOf(user.id, reply.id), listOf(user, reply.copy(content = "updated"))))
        assertTrue(com.aiassistant.ui.screens.chat.hasNewUserMessage(setOf(user.id, reply.id), listOf(user, reply, user.copy(id = 32))))
    }

    @Test fun directionChoicesHaveDistinctOneRoundSemantics() {
        assertEquals(com.aiassistant.ui.screens.chat.GenerationUiState.ChoosingDirection,
            com.aiassistant.ui.screens.chat.GenerationUiStateRules.derive(true, "", false, "等待选择回复方向"))
        assertEquals(com.aiassistant.ui.screens.chat.GenerationUiState.ChoosingDirection,
            com.aiassistant.ui.screens.chat.GenerationUiStateRules.derive(true, "", false, "回复方向生成失败，等待处理"))
        val directions = listOf(com.aiassistant.domain.model.ReplyDirection("A", "先聊感受"), com.aiassistant.domain.model.ReplyDirection("B", "继续剧情"))
        fun instruction(action: com.aiassistant.domain.model.ReplyDirectionAction, index: Int = -1, custom: String = "") =
            com.aiassistant.domain.model.ReplyDirections.instruction(com.aiassistant.domain.model.ReplyDirectionDecision(action, index, custom), directions)!!
        val selected = instruction(com.aiassistant.domain.model.ReplyDirectionAction.SELECT, 1)
        assertTrue(selected.contains("继续剧情")); assertFalse(selected.contains("先聊感受"))
        val auto = instruction(com.aiassistant.domain.model.ReplyDirectionAction.AUTO)
        assertTrue(auto.contains("选择一个")); assertTrue(auto.contains("先聊感受")); assertTrue(auto.contains("继续剧情"))
        val other = instruction(com.aiassistant.domain.model.ReplyDirectionAction.OTHER)
        assertTrue(other.contains("不同于")); assertTrue(other.contains("不得虚构既往事实"))
        val custom = instruction(com.aiassistant.domain.model.ReplyDirectionAction.OTHER, custom = "转为第一人称")
        assertTrue(custom.contains("转为第一人称")); assertFalse(custom.contains("继续剧情"))
        assertTrue(custom.contains("仅用于本轮"))
        assertFalse(com.aiassistant.domain.model.Conversation(title = "default", apiConfigId = 1, modelName = "id").enableReplyDirections)
        assertEquals(2, com.aiassistant.ui.screens.chat.TempChatSettings().replyDirectionCount)
    }

    @Test fun directionParserRejectsMalformedWrongCountAndDuplicateOutput() {
        val valid = """{"directions":[{"title":"A","description":"感受"},{"title":"B","description":"剧情"}]}"""
        assertEquals(2, com.aiassistant.domain.model.ReplyDirections.parse("```json\n$valid\n```", 2).size)
        for (input in listOf("plain text", "{}", valid.replace("\"B\"", "\"A\""), valid.replace("剧情", "感受"), valid.replace("感受", ""))) {
            try { com.aiassistant.domain.model.ReplyDirections.parse(input, 2); org.junit.Assert.fail("invalid direction accepted: $input") }
            catch (_: Exception) { }
        }
        try { com.aiassistant.domain.model.ReplyDirections.parse(valid, 3); org.junit.Assert.fail("wrong count accepted") }
        catch (_: IllegalArgumentException) { }
        assertTrue(com.aiassistant.domain.model.ReplyDirections.planningInstruction(4).contains("4 个"))
    }
    @Test fun capsuleExpansionKeepsFirstLineOriginAtEveryFontScale() {
        for ((header, line) in listOf(20 to 16, 20 to 20, 27 to 27, 41 to 41)) {
            val closed = com.aiassistant.ui.screens.chat.capsuleTextViewport(header, line, line * 4, false)
            val opened = com.aiassistant.ui.screens.chat.capsuleTextViewport(header, line, line * 4, true)
            assertEquals(header, closed.height)
            assertEquals(closed.textTop, opened.textTop)
            assertEquals((header - line) / 2, opened.textTop)
            assertEquals(line * 4 + opened.textTop, opened.height)
            val single = com.aiassistant.ui.screens.chat.capsuleTextViewport(header, line, line, true)
            assertEquals(closed, single)
        }
    }
    @Test
    fun bottomFollowOnlyConsumesPositiveMeasuredOverflow() {
        assertEquals(0, com.aiassistant.ui.screens.chat.bottomFollowDistance(100, 80, 500))
        assertEquals(0, com.aiassistant.ui.screens.chat.bottomFollowDistance(100, 400, 500))
        assertEquals(20, com.aiassistant.ui.screens.chat.bottomFollowDistance(100, 420, 500))
        assertEquals(300, com.aiassistant.ui.screens.chat.bottomFollowDistance(-200, 1000, 500))
    }

    @Test
    fun retriesRemainReconnectingEvenWhenCauseContainsErrorWords() {
        assertEquals(com.aiassistant.ui.screens.chat.GenerationUiState.Reconnecting,
            com.aiassistant.ui.screens.chat.GenerationUiStateRules.derive(true, "", false, "500 服务端错误 (HTTP 500)，正在尝试重新连接 (1/3)..."))
        assertEquals(com.aiassistant.ui.screens.chat.GenerationUiState.Failed,
            com.aiassistant.ui.screens.chat.GenerationUiStateRules.derive(true, "", false, "Key 请求报错: HTTP 500"))
    }

    @Test
    fun stoppedRepliesAreNeutralAndDoNotAnimateSuccessfulCompletion() {
        val rules = com.aiassistant.ui.screens.chat.GenerationUiStateRules
        for (content in listOf(rules.stoppedReplyContent(""), rules.stoppedReplyContent("合成部分正文"),
            "*(思考已停止，回复已暂停)*", "合成部分正文\n\n*(回复已被暂停)*")) {
            assertEquals(com.aiassistant.ui.screens.chat.GenerationUiState.Cancelled,
                rules.derive(false, content, true, "旧 Key 请求报错", contentIsError = true))
            assertFalse(rules.modelMarkHasSuccessfulBody(content))
        }
        assertEquals("生成已停止", rules.stoppedReplyContent(""))
        assertFalse(rules.isStoppedContent("她问：生成已停止是什么意思？"))
        assertFalse(rules.isStoppedContent("设定里写着 *(生成已停止)*，故事仍在继续。"))
    }

    @Test
    fun stoppedReplyDisplayKeepsRealBodyAndRemovesOnlyGeneratedStopMetadata() {
        val rules = com.aiassistant.ui.screens.chat.GenerationUiStateRules
        val oldReport = "\n\n【连接异常信息记录】：\n• 当前状态: 合成旧 Key 错误"
        for (placeholder in listOf("生成已停止", "回复已停止 (用户已暂停)",
            "回复已停止 (用户已暂停)$oldReport", "*(思考已停止，回复已暂停)*$oldReport")) {
            assertEquals("", rules.stoppedReplyDisplayBody(placeholder))
        }
        for (marker in listOf("*(生成已停止)*", "*(回复已被暂停)*")) {
            assertEquals("合成部分正文", rules.stoppedReplyDisplayBody("合成部分正文\n\n$marker"))
            assertEquals("合成部分正文", rules.stoppedReplyDisplayBody("合成部分正文\n\n$marker$oldReport"))
        }
        for (normal in listOf("她问：生成已停止是什么意思？", "正文提到 *(回复已被暂停)*，随后继续。",
            "合成段落\n\n【连接异常信息记录】：这是原文引用")) {
            assertEquals(normal, rules.stoppedReplyDisplayBody(normal))
        }
    }

    @Test
    fun testDefaultMaxTokensIs4096() {
        val config = ApiConfig(
            id = 1L,
            name = "Test API",
            provider = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-test",
            modelName = "gpt-4o"
        )
        assertEquals("默认 ApiConfig 的 maxTokens 必须为 4096", 4096, config.maxTokens)
    }

    @Test
    fun testErrorMessageDetection() {
        val detectError = ::isErrorMessage
        assertTrue(detectError("请求失败\n\n网络超时，请检查网络设置"))
        assertTrue(detectError("[请求失败] 401 Unauthorized"))
        assertTrue(detectError("Error: connection refused"))
        assertTrue(detectError("error: 500 internal server error"))
        assertTrue(detectError("合成部分正文\n\n[输出已被中断: HTTP 500]"))
        assertTrue(detectError("请求失败\n\n【连接异常信息记录】合成连接失败"))

        for (content in listOf(
            "生成已停止",
            "合成正文\n\n*(生成已停止)*",
            "回复已停止 (用户已暂停)\n\n报错详情：合成失败\n【连接异常信息记录】合成记录",
            "合成正文\n\n*(回复已被暂停)*",
            "*(思考已停止，回复已暂停)*"
        )) {
            assertFalse("用户停止应为中性状态", detectError(content))
        }
        assertFalse(detectError("你好！我是你的 AI 助手，有什么我可以帮你的？"))
        assertFalse(detectError("```kotlin\nval error = 1\n```"))
        assertFalse(detectError("她问：生成已停止是什么意思？"))
    }

    @Test
    fun testTokenSpeedCalculation() {
        val tokenCount = 150
        val responseTimeMs = 3000L // 3.0 秒
        val seconds = responseTimeMs / 1000.0
        val speed = tokenCount / seconds
        val formatted = String.format(Locale.US, "%.1f tokens/s", speed)

        assertEquals("50.0 tokens/s", formatted)
    }

    @Test
    fun testThinkingCapsuleTextFormatting() {
        fun formatCapsule(isThinkingActive: Boolean, modelName: String, timeMs: Long, thinkingTokens: Int, totalTokens: Int): String {
            return when {
                isThinkingActive -> "模型正在思考中"
                thinkingTokens > 0 -> "$modelName 已深度思考 (${timeMs / 1000.0}s, $thinkingTokens tokens)"
                else -> modelName
            }
        }

        assertEquals("模型正在思考中", formatCapsule(true, "DeepSeek", 0L, 0, 0))
        assertEquals("DeepSeek 已深度思考 (2.5s, 350 tokens)", formatCapsule(false, "DeepSeek", 2500L, 350, 500))
        assertEquals("GPT-4o", formatCapsule(false, "GPT-4o", 1200L, 0, 150))
    }

    @Test
    fun testHtmlEntityDecoding() {
        fun decodeEntities(input: String): String {
            return input
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
        }

        val htmlText = "&lt;div class=&quot;box&quot;&gt;Hello &amp; Welcome&lt;/div&gt;"
        assertEquals("<div class=\"box\">Hello & Welcome</div>", decodeEntities(htmlText))
    }

    @Test
    fun testMarkdownHeadingLevels() {
        fun parseHeadingLevel(line: String): Int {
            return when {
                line.startsWith("###### ") -> 6
                line.startsWith("##### ") -> 5
                line.startsWith("#### ") -> 4
                line.startsWith("### ") -> 3
                line.startsWith("## ") -> 2
                line.startsWith("# ") -> 1
                else -> 0
            }
        }

        assertEquals(1, parseHeadingLevel("# 一级标题"))
        assertEquals(5, parseHeadingLevel("##### 五级标题"))
        assertEquals(6, parseHeadingLevel("###### 六级标题"))
        assertEquals(0, parseHeadingLevel("普通文本"))
    }
}
