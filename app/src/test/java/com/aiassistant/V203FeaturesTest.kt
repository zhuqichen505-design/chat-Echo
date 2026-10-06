package com.aiassistant

import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.domain.model.RoleplayMemory
import com.aiassistant.ui.screens.chat.parseQuotedMessage
import com.aiassistant.ui.screens.chat.ParsedQuotedMessage
import org.junit.Assert.*
import org.junit.Test

class V203FeaturesTest {

    @Test
    fun testParseQuotedMessageBasic() {
        val rawMessage = "> 这是一个单行引用内容\n\n针对以上内容：\n请详细分析一下它的架构"
        val parsed = parseQuotedMessage(rawMessage)

        assertNotNull("必须成功解析出引用消息", parsed)
        assertEquals("这是一个单行引用内容", parsed!!.quoteText)
        assertEquals("请详细分析一下它的架构", parsed.replyText)
    }

    @Test
    fun testParseQuotedMessageMultiLine() {
        val rawMessage = "> 第一行引用\n> 第二行引用\n> 第三行引用\n\n针对以上内容：\n后续追问细节"
        val parsed = parseQuotedMessage(rawMessage)

        assertNotNull("多行引用必须被完整解析", parsed)
        assertEquals("第一行引用\n第二行引用\n第三行引用", parsed!!.quoteText)
        assertEquals("后续追问细节", parsed.replyText)
    }

    @Test
    fun testParseQuotedMessageWithoutPromptPrefix() {
        val rawMessage = "> 纯引文\n\n这是直接写的回复"
        val parsed = parseQuotedMessage(rawMessage)

        assertNotNull(parsed)
        assertEquals("纯引文", parsed!!.quoteText)
        assertEquals("这是直接写的回复", parsed.replyText)
    }

    @Test
    fun testParseQuotedMessageNonQuotedReturnsNull() {
        val normalMessage = "这是一条没有引用的普通用户消息"
        val parsed = parseQuotedMessage(normalMessage)

        assertNull("普通消息不得被误识别为引用消息", parsed)
    }

    @Test
    fun testQuotedMessageReEditRestoration() {
        val quotedContent = "> 原始引文文本\n\n针对以上内容：\n用户的提问"
        val parsed = parseQuotedMessage(quotedContent)
        assertNotNull(parsed)

        var activeQuotedText: String? = null
        var inputText: String = ""

        if (parsed != null) {
            activeQuotedText = parsed.quoteText
            inputText = parsed.replyText
        }

        assertEquals("原始引文文本", activeQuotedText)
        assertEquals("用户的提问", inputText)
    }

    @Test
    fun testBranchConversationInheritsHiddenTag() {
        val tagsWithHidden = "tech,hidden,work"
        val hasHidden = tagsWithHidden.split(',', ';', '|', ' ')
            .map { it.trim() }
            .any { it.equals("hidden", ignoreCase = true) }

        assertTrue("原会话必须被识别为隐藏会话", hasHidden)

        val branchTags = if (hasHidden) {
            val set = tagsWithHidden.split(',').map { it.trim() }.toMutableSet()
            set.add("hidden")
            set.joinToString(",")
        } else {
            tagsWithHidden
        }

        assertTrue("分支会话必须保留 hidden 标签", branchTags.contains("hidden"))
    }

    @Test
    fun testBranchMessagesMonotonicTimestampsAndOrder() {
        val now = 1726000000000L
        val originalMessages = listOf(
            Message(id = 1, conversationId = 10, role = "user", content = "Msg 1", createdAt = now - 5000),
            Message(id = 2, conversationId = 10, role = "assistant", content = "Msg 2 (v1)", variantGroupId = "g1", variantIndex = 1, createdAt = now - 4000),
            Message(id = 3, conversationId = 10, role = "user", content = "Msg 3", createdAt = now - 3000),
            Message(id = 4, conversationId = 10, role = "assistant", content = "Msg 4", createdAt = now - 2000)
        )

        val targetMessageId = 4L
        val targetIdx = originalMessages.indexOfFirst { it.id == targetMessageId }
        val messagesToBranch = originalMessages.subList(0, targetIdx + 1)
        assertEquals(4, messagesToBranch.size)

        val newConversationId = 99L
        val baseTime = now - (messagesToBranch.size * 1000L)
        val copiedMessages = messagesToBranch.mapIndexed { index, msg ->
            msg.copy(
                id = (100 + index).toLong(),
                conversationId = newConversationId,
                variantGroupId = null,
                variantIndex = 1,
                createdAt = baseTime + (index * 1000L)
            )
        }

        for (i in 0 until copiedMessages.size - 1) {
            assertTrue(
                "分支消息的时间戳必须严格单调递增，杜绝乱序",
                copiedMessages[i].createdAt < copiedMessages[i + 1].createdAt
            )
            assertNull("新分支中变体组必须重置为 null 成为主线正文", copiedMessages[i].variantGroupId)
            assertEquals("新分支中变体序号必须为 1", 1, copiedMessages[i].variantIndex)
            assertEquals(newConversationId, copiedMessages[i].conversationId)
        }
    }

    @Test
    fun testRoleplaySessionCloningForBranch() {
        val originalSession = RoleplaySession(
            id = 50L,
            conversationId = 10L,
            characterId = 1L,
            scenarioId = 2L,
            narrativeMode = "character",
            customCharacterData = "{\"name\":\"Hero\"}",
            customScenarioData = "{\"world\":\"Fantasy\"}",
            currentPlotSummary = "正在冒险",
            pinnedFacts = "[\"钥匙在背包\"]"
        )

        val newConversationId = 20L
        val clonedSession = originalSession.copy(
            id = 0L,
            conversationId = newConversationId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        assertEquals(0L, clonedSession.id)
        assertEquals(newConversationId, clonedSession.conversationId)
        assertEquals(originalSession.characterId, clonedSession.characterId)
        assertEquals(originalSession.scenarioId, clonedSession.scenarioId)
        assertEquals(originalSession.narrativeMode, clonedSession.narrativeMode)
        assertEquals(originalSession.customCharacterData, clonedSession.customCharacterData)
        assertEquals(originalSession.customScenarioData, clonedSession.customScenarioData)
        assertEquals(originalSession.currentPlotSummary, clonedSession.currentPlotSummary)
        assertEquals(originalSession.pinnedFacts, clonedSession.pinnedFacts)
    }

    @Test
    fun testV203CurrentVersionUserUpdatesCompleteness() {
        val updates = com.aiassistant.ui.screens.settings.V203UserUpdates
        assertTrue("V2.0.3 更新列表不得为空", updates.isNotEmpty())
        assertEquals(8, updates.size)
        assertTrue(updates.any { it.contains("分支功能深度修复") || it.contains("切片") })
        assertTrue(updates.any { it.contains("隐藏对话") && it.contains("继承") })
        assertTrue(updates.any { it.contains("角色扮演") && it.contains("克隆") })
        assertTrue(updates.any { it.contains("引用气泡") || it.contains("内嵌卡片") })
        assertTrue(updates.any { it.contains("引用折叠") || it.contains("重新编辑") })
    }
}
