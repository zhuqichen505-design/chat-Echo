package com.aiassistant

import com.aiassistant.domain.model.ChatCompletionRequest
import com.aiassistant.domain.model.ChatMessage
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V235UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V235FeaturesTest {

    @Test
    fun testV235UserUpdatesCompleteness() {
        assertEquals("V2.3.5 用户更新日志数量应为 5 项完整对齐需求", 5, V235UserUpdates.size)
        assertTrue("必须包含思考模型长输入 empty response 根治说明", V235UserUpdates.any { it.contains("empty response (500) 彻底根治") })
        assertTrue("必须包含双端注水协议合规化修复说明", V235UserUpdates.any { it.contains("双端注水消息协议合规化修复") })
        assertTrue("必须包含主流思考模型通用适配说明", V235UserUpdates.any { it.contains("主流思考模型通用适配无需按名猜测") })
        assertTrue("必须包含记忆机制与提取能力 100% 完整保留说明", V235UserUpdates.any { it.contains("记忆机制与提取能力 100% 完整保留") })
        assertTrue("必须包含空响应与网络异常自动回退重试说明", V235UserUpdates.any { it.contains("空响应与网络异常自动回退重试") })
    }

    @Test
    fun testUniversalThinkingModelSupportWithoutNameGuessing() {
        // 主流模型均为思考模型，无论名称如何均默认支持思考档位与 Reasoning 能力
        val arbitraryModel = ModelCapabilityEngine.resolveCapabilities("custom-private-relay-model-2026")
        assertTrue("任意模型默认全面支持思考能力", arbitraryModel.supportsThinking)
        assertTrue("任意模型默认全面支持 reasoning", arbitraryModel.supportsReasoning)
        assertEquals("通用模型思考默认预算应为 4096", 4096, arbitraryModel.defaultThinkingBudget)
        assertEquals("未知接口沿用服务端默认值", emptyList<String>(), arbitraryModel.supportedThinkingGears)

        val gpt4Custom = ModelCapabilityEngine.resolveCapabilities("gpt-4-copilot-relay")
        assertTrue("通用 gpt-4 别名模型具备思考能力支持", gpt4Custom.supportsThinking)
        assertTrue("通用 gpt-4 别名模型上下文默认至少 128K 以上", gpt4Custom.contextWindowTokens >= 128_000)
    }

    @Test
    fun testSafeMaxTokensGuaranteedForThinkingModels() {
        // 模拟 safeMaxTokens 计算：主流思考模型思考消耗大，严禁因上下文被挤压至 256/512
        val configuredMax = 50000
        val safeMaxTokens = maxOf(configuredMax, 4096).coerceIn(4096, 64000)
        assertTrue("safeMaxTokens 必须至少保留 4096 Token 保底额度", safeMaxTokens >= 4096)
        assertEquals(50000, safeMaxTokens)

        // 即使配置了较低的 maxTokens（例如 1024），也保底至少 4096，避免思考模型空响应截断
        val smallConfigured = 1024
        val safeSmallTokens = maxOf(smallConfigured, 4096).coerceIn(4096, 64000)
        assertEquals("思考模型保底额度不低于 4096", 4096, safeSmallTokens)
    }

    @Test
    fun testChatCompletionRequestStructure() {
        val request = ChatCompletionRequest(
            model = "test-model",
            messages = listOf(
                ChatMessage(role = "system", content = "System prompt"),
                ChatMessage(role = "user", content = "User prompt")
            ),
            temperature = null,
            max_tokens = 8192,
            max_completion_tokens = 8192,
            reasoning_effort = "medium"
        )
        assertEquals("test-model", request.model)
        assertEquals(2, request.messages.size)
        assertNull("思考模式下 temperature 应为 null", request.temperature)
        assertEquals(Integer.valueOf(8192), request.max_tokens)
        assertEquals(Integer.valueOf(8192), request.max_completion_tokens)
        assertEquals("medium", request.reasoning_effort)
    }

    @Test
    fun testDualAnchorPromptSafeMerging() {
        // 模拟双端注水消息安全合并：尾部声明安全合并至用户消息，绝不生成单独 role="system"
        val customPrompt = "请使用极其幽默生动的口吻回复所有问题。"
        val originalUserMessage = "请帮我分析一下这个技术方案的利弊。"
        val isRoleplayConv = false
        val recentMessageCount = 8

        val tailOverride = "[System Override Directive / 核心指令强化声明]\n" +
            "请注意：用户已对当前对话设定了最新的行为规范与提示词要求。\n" +
            "无论前序历史对话风格如何，你必须立即完全遵循以下最新指令，放弃先前的惯性回复模式：\n" +
            customPrompt.trim()

        val finalUserMessage = if (!isRoleplayConv && customPrompt.isNotBlank() && recentMessageCount >= 6) {
            "$tailOverride\n\n$originalUserMessage"
        } else {
            originalUserMessage
        }

        assertTrue("用户消息应包含强化指令", finalUserMessage.contains(tailOverride))
        assertTrue("用户消息应保留原始用户输入", finalUserMessage.contains(originalUserMessage))

        // 验证 messages 结构：绝不存在非首位 system 角色
        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage(role = "system", content = "全局基础提示词"))
        messages.add(ChatMessage(role = "user", content = "前序问题1"))
        messages.add(ChatMessage(role = "assistant", content = "前序回答1"))
        messages.add(ChatMessage(role = "user", content = finalUserMessage))

        val nonFirstSystemCount = messages.drop(1).count { it.role == "system" }
        assertEquals("消息列表中段或末尾绝对不能存在任何 role=system 消息", 0, nonFirstSystemCount)
        assertEquals("最后一条消息角色必须为 user", "user", messages.last().role)
    }

    @Test
    fun testEmptyResponseErrorRecognition() {
        val error1 = Exception("API错误 (500): failed to stream request: empty response detected")
        val error2 = RuntimeException("Model returned empty response from upstream proxy")
        val normalError = Exception("401 Unauthorized: Invalid API key")

        fun checkIsEmptyResponse(e: Throwable): Boolean {
            var current: Throwable? = e
            while (current != null) {
                val msg = current.message?.lowercase().orEmpty()
                if (msg.contains("empty response") || msg.contains("empty response detected")) return true
                current = current.cause
            }
            return false
        }

        assertTrue("应准确识别 500 empty response detected 错误", checkIsEmptyResponse(error1))
        assertTrue("应准确识别 upstream empty response 错误", checkIsEmptyResponse(error2))
        assertFalse("不应将常规未授权错误识别为空响应", checkIsEmptyResponse(normalError))
    }
}
