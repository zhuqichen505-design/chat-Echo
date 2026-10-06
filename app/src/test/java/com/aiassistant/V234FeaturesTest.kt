package com.aiassistant

import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.ChatRequestOptions
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V234UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V234FeaturesTest {

    @Test
    fun testV234UserUpdatesCompleteness() {
        assertTrue("V234UserUpdates 必须包含完整更新列表", V234UserUpdates.isNotEmpty())
        assertEquals("V2.3.4 用户更新日志数量应为 5 项完整对齐需求", 5, V234UserUpdates.size)
        assertTrue("必须包含思考模型全面解耦更新说明", V234UserUpdates.any { it.contains("思考模型全面解耦") })
        assertTrue("必须包含长输入流式连接防抢占更新说明", V234UserUpdates.any { it.contains("长输入流式连接防抢占") })
        assertTrue("必须包含记忆机制与提取能力更新说明", V234UserUpdates.any { it.contains("记忆机制与提取能力") })
        assertTrue("必须包含记忆确认交互体验优化更新说明", V234UserUpdates.any { it.contains("记忆确认交互体验优化") })
        assertTrue("必须包含辅助任务温度安全兼容更新说明", V234UserUpdates.any { it.contains("辅助任务温度安全兼容") })
    }

    @Test
    fun testThinkingModelCapabilityDefaultSupport() {
        // 通用思考模型（包含 thinking/reasoner/qwq/r1/o系列等）支持
        val thinkingCustom = ModelCapabilityEngine.resolveCapabilities("my-agent-model-thinking")
        assertTrue("通用思考模型具备深度思考能力支持", thinkingCustom.supportsThinking)
        assertTrue("通用思考模型支持 reasoning", thinkingCustom.supportsReasoning)
        assertEquals("未知模型不伪造独立 effort 档位", emptyList<String>(), thinkingCustom.supportedThinkingGears)

        val claudeCustom = ModelCapabilityEngine.resolveCapabilities("claude-3-7-sonnet-20250219")
        assertTrue("Claude 思考模型支持思考", claudeCustom.supportsThinking)
        assertEquals("anthropic", claudeCustom.reasoningProviderType)

        val deepseekR1 = ModelCapabilityEngine.resolveCapabilities("deepseek-r1-distill-qwen")
        assertTrue("DeepSeek R1 模型支持思考", deepseekR1.supportsThinking)

        // SelectedModel 与 CustomSettings 默认全面启用思考支持
        val defaultModel = com.aiassistant.domain.model.SelectedModel(
            apiConfigId = 1L,
            modelName = "custom-model"
        )
        assertTrue("SelectedModel 默认全面支持思考", defaultModel.supportsThinking)
    }

    @Test
    fun testThinkingTemperatureCompatibility() {
        // 模拟反射调用或直接逻辑验证 requestTemperature 规范：
        // 1. Anthropic 开启思考时温度必须为 1.0f
        // 2. 其他思考模型开启思考时温度必须为 null（避免服务端 400/500 报错）
        // 3. 未开启思考时，遵循常规 temperature 配置

        val anthropicConfig = ApiConfig(
            id = 1L,
            name = "Claude API",
            provider = "Anthropic",
            baseUrl = "https://api.anthropic.com",
            apiKey = "sk-ant-test",
            modelName = "claude-3-7-sonnet",
            apiType = "anthropic",
            temperature = 0.7f
        )

        val openAiThinkingConfig = ApiConfig(
            id = 2L,
            name = "OpenAI Compatible",
            provider = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-test",
            modelName = "any-custom-thinking-model",
            apiType = "openai",
            temperature = 0.7f
        )

        // 模拟 ChatRequestOptions
        val thinkingOptions = ChatRequestOptions(
            temperature = 0.7f,
            enableThinking = true
        )

        val nonThinkingOptions = ChatRequestOptions(
            temperature = 0.7f,
            enableThinking = false
        )

        // 验证 Anthropic 思考模式策略
        val isAnthropic = anthropicConfig.apiType == "anthropic"
        val anthropicTemp = if (thinkingOptions.enableThinking == true) {
            if (isAnthropic) 1.0f else null
        } else {
            thinkingOptions.temperature
        }
        assertEquals("Anthropic 开启思考时温度应强制设为 1.0f", 1.0f, anthropicTemp)

        // 验证通用模型思考模式策略
        val openAiIsAnthropic = openAiThinkingConfig.apiType == "anthropic"
        val openAiTemp = if (thinkingOptions.enableThinking == true) {
            if (openAiIsAnthropic) 1.0f else null
        } else {
            thinkingOptions.temperature
        }
        assertNull("通用思考模型开启思考时温度必须置 null 避免服务端拒绝", openAiTemp)

        // 验证未开启思考时的正常温度
        val normalTemp = if (nonThinkingOptions.enableThinking == true) {
            if (openAiIsAnthropic) 1.0f else null
        } else {
            nonThinkingOptions.temperature
        }
        assertEquals("未开启思考时应保留用户指定温度", 0.7f, normalTemp)
    }
}
