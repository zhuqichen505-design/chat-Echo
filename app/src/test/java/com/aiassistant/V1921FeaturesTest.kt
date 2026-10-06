package com.aiassistant

import com.aiassistant.domain.model.ChatModelOption
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class V1921FeaturesTest {

    @Test
    fun testReasoningEffortLevelMapping() {
        fun resolveEffortStep(enableThinking: Boolean, effort: String, maxStep: Int): Int {
            if (!enableThinking) return 0
            return when (effort.lowercase()) {
                "low", "fast" -> 1
                "medium", "balanced" -> 2
                "high", "deep" -> 3
                "ultra", "max" -> if (maxStep >= 4) 4 else 3
                else -> 2
            }.coerceIn(0, maxStep)
        }

        // 思考关闭时固定为 0
        assertEquals(0, resolveEffortStep(false, "high", 4))
        // low / fast
        assertEquals(1, resolveEffortStep(true, "low", 4))
        assertEquals(1, resolveEffortStep(true, "fast", 4))
        // medium / balanced
        assertEquals(2, resolveEffortStep(true, "medium", 4))
        assertEquals(2, resolveEffortStep(true, "balanced", 4))
        // high / deep
        assertEquals(3, resolveEffortStep(true, "high", 4))
        assertEquals(3, resolveEffortStep(true, "deep", 4))
        // ultra / max 在 4 档模型下为 4，在 3 档模型下受限为 3
        assertEquals(4, resolveEffortStep(true, "ultra", 4))
        assertEquals(3, resolveEffortStep(true, "ultra", 3))
    }

    @Test
    fun testPillSliderSnappingCalculation() {
        val totalStops = 5 // 0, 1, 2, 3, 4
        fun snapProgress(progress: Float): Int {
            return (progress * (totalStops - 1)).roundToInt().coerceIn(0, totalStops - 1)
        }

        assertEquals(0, snapProgress(0.0f))
        assertEquals(0, snapProgress(0.12f))
        assertEquals(1, snapProgress(0.24f))
        assertEquals(1, snapProgress(0.26f))
        assertEquals(2, snapProgress(0.50f))
        assertEquals(3, snapProgress(0.74f))
        assertEquals(4, snapProgress(0.95f))
        assertEquals(4, snapProgress(1.0f))
    }

    @Test
    fun testUniversalModelPickerFiltering() {
        val models = listOf(
            ChatModelOption(apiConfigId = 1L, configName = "OpenAI Official", provider = "OpenAI", apiType = "openai", modelName = "gpt-4o"),
            ChatModelOption(apiConfigId = 2L, configName = "Anthropic Direct", provider = "Anthropic", apiType = "anthropic", modelName = "claude-3-5-sonnet"),
            ChatModelOption(apiConfigId = 3L, configName = "DeepSeek Platform", provider = "DeepSeek", apiType = "deepseek", modelName = "deepseek-chat"),
            ChatModelOption(apiConfigId = 3L, configName = "DeepSeek Platform", provider = "DeepSeek", apiType = "deepseek", modelName = "deepseek-reasoner")
        )

        fun filterModels(query: String): List<ChatModelOption> {
            if (query.isBlank()) return models
            val q = query.trim().lowercase()
            return models.filter {
                it.modelName.lowercase().contains(q) ||
                it.provider.lowercase().contains(q) ||
                it.configName.lowercase().contains(q)
            }
        }

        // 搜索厂商
        val deepseekModels = filterModels("deepseek")
        assertEquals(2, deepseekModels.size)

        // 搜索模型名
        val claudeModels = filterModels("sonnet")
        assertEquals(1, claudeModels.size)
        assertEquals("claude-3-5-sonnet", claudeModels[0].modelName)

        // 空搜索返回全部
        assertEquals(4, filterModels("").size)
    }

    @Test
    fun testQuoteFormatting() {
        val selectedText = "这是需要引用的重要段落。\n包含多行内容。"
        val formattedQuote = "> " + selectedText.replace("\n", "\n> ") + "\n\n"

        assertTrue(formattedQuote.startsWith("> 这是需要引用的重要段落。"))
        assertTrue(formattedQuote.contains("\n> 包含多行内容。"))
        assertTrue(formattedQuote.endsWith("\n\n"))
    }

    @Test
    fun testDeletedModelFallbackIdentification() {
        val availableConfigIds = setOf(1L, 2L) // 仅存在 1 和 2
        val conversationConfigId = 99L // 已被删除的配置

        val isInvalid = conversationConfigId !in availableConfigIds
        assertTrue("当对话所用 apiConfigId 不在系统配置中时应判定为失效", isInvalid)

        // 自愈候选列表：直接提供全部可用模型，切换后绑定新的有效 configId
        val candidates = listOf(
            ChatModelOption(apiConfigId = 1L, configName = "P1", provider = "P1", apiType = "openai", modelName = "model-a"),
            ChatModelOption(apiConfigId = 2L, configName = "P2", provider = "P2", apiType = "openai", modelName = "model-b")
        )
        assertFalse(candidates.isEmpty())
        val newlySelected = candidates.first()
        val recoveredConfigId = newlySelected.apiConfigId
        assertTrue(recoveredConfigId in availableConfigIds)
    }
}
