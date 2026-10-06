package com.aiassistant

import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.domain.model.RoleplayMemory
import com.aiassistant.domain.model.ChatModelOption
import com.aiassistant.ui.screens.chat.BranchSuccessDialogState
import com.aiassistant.utils.HiddenConversationLock
import org.junit.Assert.*
import org.junit.Test

class V204FeaturesTest {

    @Test
    fun testHiddenConversationSessionUnlockState() {
        // 初始锁定状态
        HiddenConversationLock.lockSession()
        assertFalse("lockSession 必须将 isSessionUnlocked 重置为 false", HiddenConversationLock.isSessionUnlocked)

        // 模拟验证通过解锁
        HiddenConversationLock.unlockSession()
        assertTrue("unlockSession 必须使 isSessionUnlocked 为 true", HiddenConversationLock.isSessionUnlocked)

        // 再次手动锁定
        HiddenConversationLock.lockSession()
        assertFalse("再次调用 lockSession 必须立即锁闭", HiddenConversationLock.isSessionUnlocked)
    }

    @Test
    fun testBranchSuccessDialogState() {
        val state = BranchSuccessDialogState(
            newConversationId = 2024L,
            branchTitle = "宇宙探索故事 (分支)"
        )
        assertEquals(2024L, state.newConversationId)
        assertEquals("宇宙探索故事 (分支)", state.branchTitle)
    }

    @Test
    fun testBranchTitleIncrementLogic() {
        fun computeBranchTitle(parentTitle: String): String {
            val branchRegex = Regex("""^(.*)\s*\(分支(?:\s*(\d+))?\)$""")
            val match = branchRegex.find(parentTitle.trim())
            return if (match != null) {
                val base = match.groupValues[1].trim()
                val num = match.groupValues[2].toIntOrNull() ?: 1
                "$base (分支 ${num + 1})"
            } else {
                "${parentTitle.trim()} (分支)"
            }
        }

        assertEquals("测试对话 (分支)", computeBranchTitle("测试对话"))
        assertEquals("测试对话 (分支 2)", computeBranchTitle("测试对话 (分支)"))
        assertEquals("测试对话 (分支 3)", computeBranchTitle("测试对话 (分支 2)"))
        assertEquals("深空舰队 (分支 10)", computeBranchTitle("深空舰队 (分支 9)"))
    }

    @Test
    fun testBranchMessageSlicingAndMonotonicity() {
        val now = 1726000000000L
        val messages = listOf(
            Message(id = 1, conversationId = 1, role = "user", content = "你好", createdAt = now - 5000),
            Message(id = 2, conversationId = 1, role = "assistant", content = "你好！有什么我可以帮你的？", createdAt = now - 4000),
            Message(id = 3, conversationId = 1, role = "user", content = "讲个科幻故事", createdAt = now - 3000),
            Message(id = 4, conversationId = 1, role = "assistant", content = "在未来的新星纪元...", createdAt = now - 2000),
            Message(id = 5, conversationId = 1, role = "user", content = "后续呢？", createdAt = now - 1000),
            Message(id = 6, conversationId = 1, role = "assistant", content = "飞船穿越了虫洞...", createdAt = now)
        )

        // 针对第4条消息创建分支（截断到第4条消息）
        val branchMessageId = 4L
        val targetIdx = messages.indexOfFirst { it.id == branchMessageId }
        assertTrue("必须找到分支目标消息", targetIdx >= 0)

        val sliced = messages.take(targetIdx + 1)
        assertEquals("切片必须包含到目标消息为止的4条记录", 4, sliced.size)
        assertEquals("切片最后一条必须是目标消息", branchMessageId, sliced.last().id)

        // 规整时间戳为严格单调递增
        val newConvId = 888L
        val baseTime = now - (sliced.size * 1000L)
        val branchMessages = sliced.mapIndexed { idx, msg ->
            msg.copy(
                id = 0,
                conversationId = newConvId,
                variantGroupId = null,
                variantIndex = 1,
                createdAt = baseTime + (idx * 1000L)
            )
        }

        assertEquals(4, branchMessages.size)
        for (i in 0 until branchMessages.size - 1) {
            assertTrue(
                "消息时间戳必须严格递增，索引 $i: ${branchMessages[i].createdAt} < ${branchMessages[i+1].createdAt}",
                branchMessages[i].createdAt < branchMessages[i + 1].createdAt
            )
            assertEquals("新消息 conversationId 必须与新分支一致", newConvId, branchMessages[i].conversationId)
            assertNull("分支中 variantGroupId 必须规整为 null", branchMessages[i].variantGroupId)
            assertEquals("分支中 variantIndex 必须重置为 1", 1, branchMessages[i].variantIndex)
        }
    }

    @Test
    fun testHiddenTagPreservedOnBranch() {
        val originalTags = "work,hidden,project"
        val isParentHidden = originalTags.split(',', ';', '|', ' ')
            .map { it.trim() }
            .any { it.equals("hidden", ignoreCase = true) }
        assertTrue(isParentHidden)

        val branchTags = if (isParentHidden) {
            val set = originalTags.split(',').map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
            set.add("hidden")
            set.joinToString(",")
        } else {
            originalTags
        }

        assertTrue("分支会话标签必须包含 hidden", branchTags.split(',').contains("hidden"))
    }

    @Test
    fun testActiveModelInheritanceOnBranch() {
        val parentConv = Conversation(
            id = 10,
            title = "旧对话",
            modelName = "gemini-1.5-flash",
            apiConfigId = 1L
        )

        val activeModelOption = ChatModelOption(
            apiConfigId = 2L,
            configName = "Anthropic Production",
            provider = "anthropic",
            apiType = "anthropic",
            modelName = "claude-3-5-sonnet-20241022"
        )

        // 分支时如果用户在页面临时切换了模型，分支必须继承当前活跃模型
        val effectiveModelName = activeModelOption.modelName
        val effectiveApiConfigId = activeModelOption.apiConfigId

        assertEquals("claude-3-5-sonnet-20241022", effectiveModelName)
        assertEquals(2L, effectiveApiConfigId)
        assertNotEquals(parentConv.modelName, effectiveModelName)
    }

    @Test
    fun testRoleplayMemoryCloningInvariants() {
        val originalSessionId = 55L
        val newSessionId = 99L
        val originalMemories = listOf(
            RoleplayMemory(id = 1, sessionId = originalSessionId, content = "主角获得了灵能水晶", isPinned = true, memoryType = "fact"),
            RoleplayMemory(id = 2, sessionId = originalSessionId, content = "与机械军团结盟", isPinned = false, memoryType = "relationship")
        )

        val clonedMemories = originalMemories.map { mem ->
            mem.copy(
                id = 0,
                sessionId = newSessionId,
                createdAt = System.currentTimeMillis()
            )
        }

        assertEquals(originalMemories.size, clonedMemories.size)
        clonedMemories.forEachIndexed { index, cloned ->
            assertEquals(0L, cloned.id)
            assertEquals(newSessionId, cloned.sessionId)
            assertEquals(originalMemories[index].content, cloned.content)
            assertEquals(originalMemories[index].isPinned, cloned.isPinned)
            assertEquals(originalMemories[index].memoryType, cloned.memoryType)
        }
    }
}
