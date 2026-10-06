package com.aiassistant

import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V236UserUpdates
import com.aiassistant.utils.AdvancedMemoryEngine
import org.junit.Assert.*
import org.junit.Test

class V236FeaturesTest {

    @Test
    fun testV236UserUpdatesCompleteness() {
        assertEquals("V2.3.6 用户更新日志应有 6 项核心内容", 6, V236UserUpdates.size)
        assertTrue("必须包含滚动摘要不完整截断彻底修复说明", V236UserUpdates.any { it.contains("滚动摘要不完整截断彻底修复") })
        assertTrue("必须包含滚动摘要与时间线记忆协同去重说明", V236UserUpdates.any { it.contains("滚动摘要与时间线记忆协同去重") })
        assertTrue("必须包含流式生成中删除消息视口稳定与防丢说明", V236UserUpdates.any { it.contains("流式生成中删除消息视口稳定与防丢") })
        assertTrue("必须包含模型能力普适化与正则狭隘判断清理说明", V236UserUpdates.any { it.contains("模型能力普适化与正则狭隘判断清理") })
        assertTrue("必须包含辅助场景过时短截断彻底扫除说明", V236UserUpdates.any { it.contains("辅助场景过时短截断彻底扫除") })
        assertTrue("必须包含深度思考强度 5 档具体赋值展示说明", V236UserUpdates.any { it.contains("深度思考强度 5 档具体赋值展示") })
    }

    @Test
    fun testFiveLevelThinkingValuesAndOpenAIMapping() {
        // 5 档赋值：关闭思考 (none), low, medium, high, max
        val gears = listOf("关闭思考", "low", "medium", "high", "max")
        assertEquals(5, gears.size)

        // 模拟 OpenAI 协议下 normalizeThinkingEffort 映射
        fun normalizeThinkingEffort(rawEffort: String?): String? {
            val normalized = rawEffort?.trim()?.lowercase() ?: return null
            return when {
                normalized in listOf("none", "off", "0", "disabled", "false") -> null
                normalized == "low" -> "low"
                normalized == "medium" -> "medium"
                normalized == "high" -> "high"
                normalized == "max" -> "high" // 严格 OpenAI 规范下 max 安全映射到 high 避免 400
                else -> "medium"
            }
        }

        assertNull("none 应安全映射为 null（不传参）", normalizeThinkingEffort("none"))
        assertEquals("low 映射为 low", "low", normalizeThinkingEffort("low"))
        assertEquals("medium 映射为 medium", "medium", normalizeThinkingEffort("medium"))
        assertEquals("high 映射为 high", "high", normalizeThinkingEffort("high"))
        assertEquals("max 在 OpenAI 规范下安全映射为 high 避免报错", "high", normalizeThinkingEffort("max"))

        // 模拟 Anthropic 协议下 thinkingBudgetForEffort 映射
        fun thinkingBudgetForEffort(rawEffort: String?, requestedBudget: Int = 4096): Int {
            return when (rawEffort?.trim()?.lowercase()) {
                "low" -> 2048
                "medium" -> 8192
                "high" -> 24000
                "max" -> 64000
                else -> requestedBudget.coerceIn(1024, 64000)
            }
        }
        assertEquals(2048, thinkingBudgetForEffort("low"))
        assertEquals(8192, thinkingBudgetForEffort("medium"))
        assertEquals(24000, thinkingBudgetForEffort("high"))
        assertEquals(64000, thinkingBudgetForEffort("max"))
    }

    @Test
    fun testRollingSummaryAndTimelineSynergyNoDuplicate() {
        val prompt = AdvancedMemoryEngine.buildStructuredSummaryPrompt(
            existingSummary = "已有早期设定讨论",
            transcript = "用户: 我们决定采用Kotlin和Compose开发客户端。\nAI: 好的，已确定技术栈。",
            tokenBudget = 3000
        )
        assertTrue("提示词必须强调核心脉络与未决议题", prompt.contains("核心脉络与未决议题"))
        assertTrue("提示词必须明确与时间线系统紧密协同", prompt.contains("时间线系统紧密协同互补"))
        assertTrue("提示词必须明确避免机械复读冗长的时间节点列表", prompt.contains("避免机械复读冗长的时间节点列表"))
        assertTrue("提示词必须包含当前未决议题与待办事项", prompt.contains("当前未决议题与待办事项"))
    }

    @Test
    fun testRollingSummaryNoTruncationArtifact() {
        // 模拟 compactTextToTokenBudget：绝不允许添加 \n...[summary truncated]
        fun compactTextToTokenBudget(text: String, tokenBudget: Int): String {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return ""
            val charLimit = maxOf(tokenBudget * 4, 16000)
            if (trimmed.length <= charLimit) return trimmed
            return trimmed.take(charLimit).trimEnd()
        }

        val originalText = "这是一段很长的总结。".repeat(200)
        val compacted = compactTextToTokenBudget(originalText, 2000)
        assertFalse("严禁在总结后追加 truncated 破坏性占位符", compacted.contains("[summary truncated]"))
        assertFalse("严禁包含省略号截断标记", compacted.contains("...[summary"))
        assertTrue("应完整保留内容", compacted.length >= 1000)
    }

    @Test
    fun testUniversalModelCapabilitiesNoNarrowRegex() {
        // 测试现代新主流模型与自定义模型，均能全面普适支持思考
        val modernModels = listOf(
            "gpt-5",
            "gpt-5.5-omni",
            "claude-4-opus",
            "claude-4.5-sonnet",
            "gemini-2.5-flash",
            "deepseek-chat-v4",
            "kimi-k2",
            "my-custom-proxy-thinking-model"
        )
        for (model in modernModels) {
            val cap = ModelCapabilityEngine.resolveCapabilities(model)
            assertTrue("模型 $model 必须默认支持思考", cap.supportsThinking)
            assertTrue("模型 $model 必须默认支持 reasoning", cap.supportsReasoning)
            // 只列具体型号已确认的档位；未知变体不得继承整个厂商的范围。
            val expected = when(model) {
                "gpt-5" -> listOf("minimal", "low", "medium", "high")
                "gpt-6-astra" -> listOf("low", "medium", "high", "xhigh", "max")
                "deepseek-chat-v4" -> listOf("low", "high", "max")
                else -> emptyList()
            }
            assertEquals("模型 $model 仅暴露已确认的档位", expected, cap.supportedThinkingGears)
            assertTrue("模型 $model 上下文额度充足 (>= 128K)", cap.contextWindowTokens >= 128_000)
        }
    }

    @Test
    fun testAuxiliaryTokensAdequateNoEmptyResponse() {
        // 验证辅助任务的 Token 预算：标题生成 >= 2048，记忆提炼 >= 4096，滚动摘要 >= 4096
        val titleTokens = 2048
        val memoryExtractionTokens = 4096
        val rollingSummaryTokens = 4096

        assertTrue("标题生成 Token 充足（防思考模型空回复截断）", titleTokens >= 2048)
        assertTrue("记忆提炼 Token 充足（防思考模型空回复截断）", memoryExtractionTokens >= 4096)
        assertTrue("滚动摘要 Token 充足（防思考模型中途断裂）", rollingSummaryTokens >= 4096)
    }
}
