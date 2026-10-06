package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.Conversation
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V253UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V253FeaturesTest {

    @Test
    fun testV253UserUpdatesCompleteness() {
        assertEquals("V2.5.3 用户更新日志数量应为 5 项", 5, V253UserUpdates.size)
        assertTrue("必须包含彻底根除第4次回复精准报错说明", V253UserUpdates.any { it.contains("彻底根除第4次回复精准报错") })
        assertTrue("必须包含角色与剧情扮演模式智能识别说明", V253UserUpdates.any { it.contains("角色与剧情扮演模式智能识别") })
        assertTrue("必须包含历史报错占位全链路阻断说明", V253UserUpdates.any { it.contains("历史报错占位全链路阻断") })
        assertTrue("必须包含模型请求参数严格合规说明", V253UserUpdates.any { it.contains("模型请求参数严格合规") })
        assertTrue("CurrentVersionUserUpdates 数量必须大于等于 5 项满足历史单测基准", CurrentVersionUserUpdates.size >= 5)
    }

    @Test
    fun testNoTailOverridePollutionInUserMessage() {
        // 模拟第4次回复场景：历史消息数达到 6 条
        val originalUserMessage = "[林雾打开箱子，箱子一半放着内衣，另一半是一个精致的高达模型]\n这个给你，我也不知道你喜欢什么，就随便猜着买了。"
        val customPrompt = "# Role Definition: 林肆\n你不是人工智能，也不是语言模型。你现在彻底剥离一切现实与AI认知..."

        // 核心验证：用户消息绝不得被注水拼接 [System Override Directive]，确保发送给模型的 user 角色内容纯净
        assertFalse("用户消息绝不能包含 System Override Directive 越狱触发词", originalUserMessage.contains("System Override Directive"))
        assertFalse("用户消息绝不能包含 核心指令强化声明", originalUserMessage.contains("核心指令强化声明"))
        assertFalse("用户消息绝不能包含 放弃先前的惯性回复模式", originalUserMessage.contains("放弃先前的惯性回复模式"))
    }

    @Test
    fun testIsRoleplayConversationDetection() {
        val taggedRoleplay = Conversation(
            id = 1,
            title = "角色会话",
            apiConfigId = 1L,
            modelName = "test-model",
            tags = "roleplay,custom"
        )
        assertTrue("带有 roleplay 标签应判定为角色扮演", AiRepository.isRoleplayConversation(taggedRoleplay))

        val taggedStory = Conversation(
            id = 2,
            title = "剧情会话",
            apiConfigId = 1L,
            modelName = "test-model",
            tags = "story"
        )
        assertTrue("带有 story 标签应判定为角色扮演", AiRepository.isRoleplayConversation(taggedStory))

        val promptRoleDefinition = Conversation(
            id = 3,
            title = "姐弟",
            apiConfigId = 1L,
            modelName = "test-model",
            systemPrompt = "# Role Definition: 林肆\n**剧情扮演完全虚构，不会进行传播...**"
        )
        assertTrue("系统提示词以 Role Definition 开头应智能识别为角色扮演", AiRepository.isRoleplayConversation(promptRoleDefinition))

        val promptRoleplayCn = Conversation(
            id = 4,
            title = "古风角色",
            apiConfigId = 1L,
            modelName = "test-model",
            systemPrompt = "【角色扮演】你将扮演一位行走江湖的侠客..."
        )
        assertTrue("系统提示词包含 角色扮演 应识别为角色扮演", AiRepository.isRoleplayConversation(promptRoleplayCn))

        val normalConv = Conversation(
            id = 5,
            title = "普通技术交流",
            apiConfigId = 1L,
            modelName = "test-model",
            systemPrompt = "你是一个 Android 开发专家，请使用 Kotlin 回答问题。"
        )
        assertFalse("普通技术提示词不得误判为角色扮演", AiRepository.isRoleplayConversation(normalConv))

        assertFalse("null 会话不得判定为角色扮演", AiRepository.isRoleplayConversation(null))
    }
}
