package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ChatMessage
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V252UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V252FeaturesTest {

    @Test
    fun testV252UserUpdatesCompleteness() {
        assertEquals("V2.5.2 用户更新日志数量应为 5 项", 5, V252UserUpdates.size)
        assertTrue("必须包含 empty response 彻底根治说明", V252UserUpdates.any { it.contains("empty response detected 500") })
        assertTrue("必须包含 Role Alternation 角色交替规范修复说明", V252UserUpdates.any { it.contains("Role Alternation") })
        assertTrue("必须包含 max_completion_tokens 与 max_tokens 严格适配说明", V252UserUpdates.any { it.contains("max_completion_tokens") })
        assertTrue("必须包含重新生成智能清理失败占位说明", V252UserUpdates.any { it.contains("重新生成智能清理") })
        assertTrue("CurrentVersionUserUpdates 数量必须大于等于 5 项满足历史单测基准", CurrentVersionUserUpdates.size >= 5)
    }

    @Test
    fun testIsErrorPlaceholderMessage() {
        val standardFailure = "请求失败\n\nAPI错误 (500): failed to stream request: empty response detected\n\n可以检查 API 地址、密钥、模型名称或网络状态后重试。"
        assertTrue("标准请求失败模板应被判定为错误占位", AiRepository.isErrorPlaceholderMessage(standardFailure))

        val interruptedMsg = "这是前半部分回答\n\n[输出已被中断: Socket closed]"
        assertTrue("输出中断占位应被判定为错误占位", AiRepository.isErrorPlaceholderMessage(interruptedMsg))

        val directRawError = "failed to stream request: empty response detected"
        assertTrue("直接包含 empty response detected 应被识别为错误占位", AiRepository.isErrorPlaceholderMessage(directRawError))

        val normalAssistant = "你好！今天我们可以聊聊 Android 架构设计。"
        assertFalse("正常助手回复不得判定为错误占位", AiRepository.isErrorPlaceholderMessage(normalAssistant))

        assertFalse("null 内容不应判定为错误占位", AiRepository.isErrorPlaceholderMessage(null))
        assertFalse("空白内容不应判定为错误占位", AiRepository.isErrorPlaceholderMessage("   "))
    }

    @Test
    fun testNormalizeChatMessagesRoleAlternation() {
        val rawMessages = listOf(
            ChatMessage(role = "system", content = "你是一个专业助手。"),
            ChatMessage(role = "assistant", content = "我是孤立的开场白"), // 位于 user 前的孤立 assistant，应被丢弃
            ChatMessage(role = "user", content = "你好，第一句。"),
            ChatMessage(role = "user", content = "你好，第二句。"), // 连续 user，应安全合并
            ChatMessage(role = "assistant", content = "回答1"),
            ChatMessage(role = "assistant", content = "补充回答1"), // 连续 assistant，应安全合并
            ChatMessage(role = "user", content = "最新用户提问"),
            ChatMessage(role = "assistant", content = "残留的助手消息") // 末尾孤立 assistant，应移除保证以 user 结尾
        )

        val normalized = AiRepository.normalizeChatMessagesRoleAlternation(rawMessages)

        assertEquals(4, normalized.size)
        assertEquals("system", normalized[0].role)
        assertEquals("你是一个专业助手。", normalized[0].content)

        assertEquals("user", normalized[1].role)
        assertEquals("你好，第一句。\n\n你好，第二句。", normalized[1].content)

        assertEquals("assistant", normalized[2].role)
        assertEquals("回答1\n\n补充回答1", normalized[2].content)

        assertEquals("user", normalized[3].role)
        assertEquals("最新用户提问", normalized[3].content)
    }

    @Test
    fun testNormalizeChatMessagesWithoutSystem() {
        val rawMessages = listOf(
            ChatMessage(role = "user", content = "单条提问")
        )
        val normalized = AiRepository.normalizeChatMessagesRoleAlternation(rawMessages)
        assertEquals(1, normalized.size)
        assertEquals("user", normalized[0].role)
        assertEquals("单条提问", normalized[0].content)
    }

    @Test
    fun testNormalizeChatMessagesEmptyInput() {
        val normalized = AiRepository.normalizeChatMessagesRoleAlternation(emptyList())
        assertTrue("空列表应返回空列表", normalized.isEmpty())
    }

    @Test
    fun testOpenAIoSeriesVsStandardModelDetection() {
        val o1Model = "o1-preview"
        val o3Model = "o3-mini"
        val o4Model = "o4-mini"
        val proxyO1Model = "openai/o1-mini"
        val standardModel = "deepseek-chat"
        val gpt4oModel = "gpt-4o"

        fun isO1OrO3(model: String): Boolean {
            return model.startsWith("o1", ignoreCase = true) ||
                model.startsWith("o3", ignoreCase = true) ||
                model.startsWith("o4", ignoreCase = true) ||
                model.contains("/o1", ignoreCase = true) ||
                model.contains("/o3", ignoreCase = true) ||
                model.contains("/o4", ignoreCase = true)
        }

        assertTrue(isO1OrO3(o1Model))
        assertTrue(isO1OrO3(o3Model))
        assertTrue(isO1OrO3(o4Model))
        assertTrue(isO1OrO3(proxyO1Model))
        assertFalse(isO1OrO3(standardModel))
        assertFalse(isO1OrO3(gpt4oModel))
    }
}
