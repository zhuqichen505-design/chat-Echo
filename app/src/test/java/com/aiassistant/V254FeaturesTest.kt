package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.Conversation
import com.aiassistant.ui.screens.chat.TempChatSettings
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V254UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V254FeaturesTest {

    @Test
    fun testV254UserUpdatesCompleteness() {
        assertEquals("V2.5.4 用户更新日志数量应为 5 项", 5, V254UserUpdates.size)
        assertTrue("必须包含根治大模型超限空回复说明", V254UserUpdates.any { it.contains("根治大模型超限空回复") })
        assertTrue("必须包含上下文降级保护全面放宽至 200k 说明", V254UserUpdates.any { it.contains("上下文降级保护全面放宽至 200k") })
        assertTrue("必须包含对话设置高级参数优雅折叠说明", V254UserUpdates.any { it.contains("对话设置高级参数优雅折叠") })
        assertTrue("必须包含非 o 系列思考参数智能兼容说明", V254UserUpdates.any { it.contains("非 o 系列思考参数智能兼容") })
        assertTrue("CurrentVersionUserUpdates 数量必须大于等于 5 项满足历史单测基准", CurrentVersionUserUpdates.size >= 5)
    }

    @Test
    fun testDefaultMaxTokensIs4096() {
        val config = ApiConfig(
            id = 1,
            name = "Test",
            apiKey = "sk-test",
            baseUrl = "https://api.openai.com",
            provider = "OpenAI",
            modelName = "gpt-4o"
        )
        assertEquals("ApiConfig 默认 maxTokens 应为 4096 而非 50000", 4096, config.maxTokens)

        val tempSettings = TempChatSettings()
        assertEquals("TempChatSettings 默认 maxTokens 应为 4096", 4096, tempSettings.maxTokens)
    }

    @Test
    fun testContextOverflowRetryWindowTokensIs200k() {
        assertEquals("重试降级保护窗口应为 200k (200000 tokens) 而非 32k", 200_000, AiRepository.CONTEXT_OVERFLOW_RETRY_WINDOW_TOKENS)
    }

    @Test
    fun testRoleplaySystemPromptPreservesMemoriesAndCustomPrompt() {
        val customPrompt = "# Role Definition: 探险家\n你是一位穿越沙漠的探险家。"
        val memoryBlock = "【故事时间线与关键节点】\n- [第一天] 抵达绿洲\n【会话专属记忆】\n- 随身携带指南针"
        val effective = AiRepository.formatRoleplaySystemPrompt(
            customPrompt = customPrompt,
            memoryBlock = memoryBlock,
            worldBookBlock = "【世界观】神秘沙漠"
        )
        assertNotNull("角色扮演提示词不可为 null", effective)
        assertTrue("角色扮演模式下必须包含人设提示词", effective!!.contains("Role Definition: 探险家"))
        assertTrue("角色扮演模式下必须完整保留记忆与时间线", effective.contains("故事时间线与关键节点"))
        assertTrue("角色扮演模式下必须完整保留时间线事件", effective.contains("抵达绿洲"))
        assertTrue("角色扮演模式下必须完整保留会话专属记忆", effective.contains("随身携带指南针"))
        assertTrue("角色扮演模式下必须包含世界书内容", effective.contains("神秘沙漠"))
    }

    @Test
    fun testIsRoleplayConversationDetection() {
        val taggedRoleplay = Conversation(
            id = 1,
            title = "角色会话",
            apiConfigId = 1L,
            modelName = "test-model",
            tags = "roleplay"
        )
        assertTrue("带有 roleplay 标签应判定为角色扮演", AiRepository.isRoleplayConversation(taggedRoleplay))

        val promptRoleDefinition = Conversation(
            id = 2,
            title = "姐弟",
            apiConfigId = 1L,
            modelName = "test-model",
            systemPrompt = "# Role Definition: 林肆\n**剧情扮演完全虚构，不会进行传播...**"
        )
        assertTrue("系统提示词以 Role Definition 开头应识别为角色扮演", AiRepository.isRoleplayConversation(promptRoleDefinition))
    }
}
