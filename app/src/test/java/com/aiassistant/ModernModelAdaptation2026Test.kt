package com.aiassistant

import com.aiassistant.data.repository.helpers.TokenEstimationHelper
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.utils.FileUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * 2026-09 主流模型适配回归：
 * DeepSeek V4 / GPT-6 Astra·Sol·Luna / Gemini 3.8 Flash 等不得再被误判为 32k/128k，
 * 也不得只对 deepseek-r1 / o1 做过时特殊优化。
 */
class ModernModelAdaptation2026Test {
    @Test
    fun displayNamesDoNotChangeRequestIdentity() {
        val names = mapOf(
            "gemini3.8flash" to "Gemini3.8Flash", "gpt-6.1-sol" to "GPT-6.1-Sol",
            "deepseek-v4-pro" to "DeepSeek-V4-Pro", "glm-5.2" to "GLM-5.2",
            "vendor/gpt-6.1-sol" to "Vendor/GPT-6.1-Sol", "customModel-alpha" to "CustomModel-Alpha"
        )
        names.forEach { (id, expected) ->
            assertEquals(expected, com.aiassistant.domain.model.ModelDisplayName.format(id))
            val config = com.aiassistant.domain.model.ApiConfig(name = "test", provider = "test", baseUrl = "https://example.org", apiKey = "test", modelName = id)
            assertEquals(id, config.modelName)
            assertEquals(id, com.aiassistant.domain.model.ChatCompletionRequest(model = config.modelName, messages = emptyList()).model)
        }
    }

    @Test
    fun testDeepSeekV4FamilyIs1MContext() {
        for (model in listOf(
            "deepseek-flash",
            "deepseek-v4-pro",
            "deepseek-v4-flash",
            "deepseekv4flash",
            "deepseek-chat-v4"
        )) {
            val cap = ModelCapabilityEngine.resolveCapabilities(model)
            assertEquals("$model 上下文应为 1M", 1_000_000, cap.contextWindowTokens)
            assertEquals("1M", cap.contextWindowLabel)
            assertTrue("$model 必须支持思考", cap.supportsThinking)
        }

        // 历史 DeepSeek chat / reasoner 仍保持 128K，不得被 V4 规则误吞
        val legacyChat = ModelCapabilityEngine.resolveCapabilities("deepseek-chat")
        assertEquals(128_000, legacyChat.contextWindowTokens)
    }

    @Test
    fun testDeepSeekV4MaxOutputNotClampedTo32k() {
        val flash = ModelCapabilityEngine.resolveCapabilities("deepseek-flash")
        assertEquals("DeepSeek V4 官方最大输出 384K", 384_000, flash.maxOutputTokens)

        val pro = ModelCapabilityEngine.resolveCapabilities("deepseek-v4-pro")
        assertEquals(384_000, pro.maxOutputTokens)
        assertFalse("deepseek-v4-pro 不支持视觉", pro.supportsVision)

        assertTrue("deepseek-flash 支持视觉", flash.supportsVision)
    }

    @Test
    fun testGpt6FamilyContextAndReasoningGears() {
        for (model in listOf("gpt-6-astra", "gpt-6-sol", "gpt-6-luna", "gpt-6.1-sol")) {
            val cap = ModelCapabilityEngine.resolveCapabilities(model)
            assertEquals("$model 上下文应为 1.05M", 1_050_000, cap.contextWindowTokens)
            assertTrue("$model 必须支持思考", cap.supportsThinking)
            assertTrue("$model 思考档位需含 xhigh", cap.supportedThinkingGears.contains("xhigh"))
            assertTrue("$model 思考档位需含 max", cap.supportedThinkingGears.contains("max"))
            assertTrue("$model 使用 max_completion_tokens", cap.usesMaxCompletionTokens)
            assertTrue("$model 推理时拒收 temperature", cap.rejectsTemperature)
        }

        val astra = ModelCapabilityEngine.resolveCapabilities("gpt-6-astra")
        assertEquals("GPT-6 Astra 最大输出 128K", 128_000, astra.maxOutputTokens)
        assertTrue(astra.supportsVision)
    }

    @Test
    fun testModernOpenAiReasoningStyleDetection() {
        assertTrue(ModelCapabilityEngine.isModernOpenAiReasoningStyle("gpt-6-astra"))
        assertTrue(ModelCapabilityEngine.isModernOpenAiReasoningStyle("gpt-6-luna"))
        assertTrue(ModelCapabilityEngine.isModernOpenAiReasoningStyle("gpt-6-sol"))
        assertTrue(ModelCapabilityEngine.isModernOpenAiReasoningStyle("gpt-5.5"))
        assertTrue(ModelCapabilityEngine.isModernOpenAiReasoningStyle("gpt-5.6"))

        // 过时 o 系列不再做专项识别，一律走默认统一方案
        assertFalse(ModelCapabilityEngine.isModernOpenAiReasoningStyle("o1-preview"))
        assertFalse(ModelCapabilityEngine.isModernOpenAiReasoningStyle("o3-mini"))
        assertFalse(ModelCapabilityEngine.isModernOpenAiReasoningStyle("gpt-4o"))
        assertFalse(ModelCapabilityEngine.isModernOpenAiReasoningStyle("deepseek-chat"))
        assertFalse(ModelCapabilityEngine.isModernOpenAiReasoningStyle("gemini-3.8-flash"))
    }

    @Test
    fun testGemini38FlashAnd31ProContext() {
        val flash = ModelCapabilityEngine.resolveCapabilities("gemini-3.8-flash")
        assertEquals(1_000_000, flash.contextWindowTokens)
        assertTrue(flash.supportsThinking)
        assertTrue(flash.supportsVision)

        val pro = ModelCapabilityEngine.resolveCapabilities("gemini-3.1-pro")
        assertEquals("Gemini 3.1 Pro 输入 1M", 1_000_000, pro.contextWindowTokens)
        assertEquals("1M", pro.contextWindowLabel)
    }

    @Test
    fun testThinkingEffortVendorSpecificGears() {
        // GPT-6 世代支持 xhigh / max
        assertEquals("xhigh", TokenEstimationHelper.normalizeThinkingEffort("xhigh", "openai", "gpt-6-astra"))
        assertEquals("max", TokenEstimationHelper.normalizeThinkingEffort("max", "openai", "gpt-6-luna"))

        // Kimi K3 / GLM-5.3 仅 low/high/max
        assertEquals("max", TokenEstimationHelper.normalizeThinkingEffort("max", "openai", "kimi-k3"))
        assertEquals("high", TokenEstimationHelper.normalizeThinkingEffort("xhigh", "openai", "kimi-k3"))
        assertEquals("low", TokenEstimationHelper.normalizeThinkingEffort("low", "openai", "glm-5.3"))
        assertEquals("max", TokenEstimationHelper.normalizeThinkingEffort("ultra", "openai", "glm-5.3"))

        // 未识别模型走默认统一方案（low/medium/high/max）
        assertEquals("medium", TokenEstimationHelper.normalizeThinkingEffort("max", "openai", "some-unknown-model"))
        assertEquals("medium", TokenEstimationHelper.normalizeThinkingEffort(null, "openai", "some-unknown-model"))
    }

    @Test
    fun testRequestChainPreservesTopGearsAndMediumFallback() {
        // P0 回归：UI 以 ultra 存储最高档，请求链路归一化（携带模型名）后必须保留厂商最高档，
        // 不得在进入厂商映射前被历史安全映射降级为 high
        assertEquals("max", TokenEstimationHelper.normalizeThinkingEffort("ultra", "openai", "gpt-6-astra"))
        assertEquals("max", TokenEstimationHelper.normalizeThinkingEffort("ultra", "openai", "kimi-k3"))
        assertEquals("max", TokenEstimationHelper.normalizeThinkingEffort("ultra", "openai", "glm-5.3"))
        assertEquals("xhigh", TokenEstimationHelper.normalizeThinkingEffort("xhigh", "openai", "gpt-6-luna"))
        assertEquals("high", TokenEstimationHelper.normalizeThinkingEffort("xhigh", "openai", "kimi-k3"))

        // P2 回归：仅 low/high/max 三档厂商（GLM/Kimi）选「平衡(medium)」必须就近收敛为 high，而非 low
        val glmPolicy = com.aiassistant.domain.model.ModelVendorProfiles.policyFor("glm-5.3")
        assertEquals("high", com.aiassistant.domain.model.ModelVendorProfiles.mapThinkingGear("medium", glmPolicy))
        assertEquals("high", TokenEstimationHelper.normalizeThinkingEffort("medium", "openai", "kimi-k3"))
        assertEquals("high", TokenEstimationHelper.normalizeThinkingEffort("medium", "openai", "glm-5.3"))
        // 四档厂商 medium 原样保留
        assertEquals("high", TokenEstimationHelper.normalizeThinkingEffort("medium", "openai", "deepseek-v4-pro"))

        // 未识别模型名：默认统一方案（low/medium/high/max），最高档保留
        assertEquals("medium", TokenEstimationHelper.normalizeThinkingEffort("ultra", "openai", "some-unknown-model"))
        assertEquals("medium", TokenEstimationHelper.normalizeThinkingEffort("medium", "openai", "some-unknown-model"))

        // 完全无模型名：历史安全映射兜底不变（max/ultra→high）
        assertEquals("high", TokenEstimationHelper.normalizeThinkingEffort("ultra", "openai"))
        assertEquals("high", TokenEstimationHelper.normalizeThinkingEffort("xhigh", "openai"))
    }

    @Test
    fun testVisionSupportFor2026Models() {
        assertTrue(FileUtils.supportsImageInput("gpt-6-astra"))
        assertTrue(FileUtils.supportsImageInput("gemini-3.8-flash"))
        assertTrue(FileUtils.supportsImageInput("deepseek-flash"))
        assertTrue(FileUtils.supportsImageInput("deepseek-v4-flash-vision-exp"))

        assertFalse(FileUtils.supportsImageInput("deepseek-v4-pro"))
        assertFalse(FileUtils.supportsImageInput("deepseek-reasoner"))
    }

    @Test
    fun testUnknownModelStillDoesNotFabricateLabel() {
        val unknown = ModelCapabilityEngine.resolveCapabilities("some-unknown-private-model")
        assertEquals("", unknown.contextWindowLabel)
        assertEquals(256_000, unknown.contextWindowTokens)
        // 现代默认：未知模型仍按思考架构兼容，避免再次把主流模型锁死
        assertTrue(unknown.supportsThinking)
    }

    @Test
    fun testVendorDetectionAndThinkingGears() {
        assertEquals(
            com.aiassistant.domain.model.ModelVendor.GPT,
            com.aiassistant.domain.model.ModelVendorProfiles.detectVendor("gpt-6-astra")
        )
        assertEquals(
            com.aiassistant.domain.model.ModelVendor.MINIMAX,
            com.aiassistant.domain.model.ModelVendorProfiles.detectVendor("MiniMax-M3")
        )
        assertEquals(
            com.aiassistant.domain.model.ModelVendor.KIMI,
            com.aiassistant.domain.model.ModelVendorProfiles.detectVendor("kimi-k3")
        )
        assertEquals(
            com.aiassistant.domain.model.ModelVendor.DEEPSEEK,
            com.aiassistant.domain.model.ModelVendorProfiles.detectVendor("deepseek-flash")
        )
        assertEquals(
            com.aiassistant.domain.model.ModelVendor.GEMINI,
            com.aiassistant.domain.model.ModelVendorProfiles.detectVendor("gemini-3.8-flash")
        )
        assertEquals(
            com.aiassistant.domain.model.ModelVendor.GLM,
            com.aiassistant.domain.model.ModelVendorProfiles.detectVendor("glm-5.3")
        )
        assertEquals(
            com.aiassistant.domain.model.ModelVendor.MIMO,
            com.aiassistant.domain.model.ModelVendorProfiles.detectVendor("MiMo-V2.6-Pro")
        )

        // Kimi/GLM 仅三档，GPT-6 五档，DeepSeek/MiniMax/MiMo 四档
        assertEquals(listOf("low", "high", "max"), ModelCapabilityEngine.resolveCapabilities("kimi-k3").supportedThinkingGears)
        assertEquals(listOf("low", "high", "max"), ModelCapabilityEngine.resolveCapabilities("glm-5.3").supportedThinkingGears)
        assertTrue(ModelCapabilityEngine.resolveCapabilities("gpt-6-astra").supportedThinkingGears.contains("xhigh"))
        assertEquals(listOf("low", "high", "max"), ModelCapabilityEngine.resolveCapabilities("deepseek-v4-pro").supportedThinkingGears)
    }

    @Test
    fun testVendorTemperatureAndTokenApiStyle() {
        val kimi = ModelCapabilityEngine.resolveCapabilities("kimi-k3")
        assertTrue("Kimi K3 温度固定，应省略 temperature", kimi.rejectsTemperature)
        assertTrue("Kimi 省略 top_p/penalty", kimi.omitsTopPAndPenalties)
        assertTrue("Kimi 始终思考", kimi.alwaysThinking)
        assertEquals(1_048_576, kimi.maxOutputTokens)

        val glm = ModelCapabilityEngine.resolveCapabilities("glm-5.3")
        assertTrue("GLM-5.3 始终思考", glm.alwaysThinking)
        assertTrue(glm.rejectsTemperature)

        val gpt6 = ModelCapabilityEngine.resolveCapabilities("gpt-6-astra")
        assertTrue(gpt6.usesMaxCompletionTokens)
        assertEquals(128_000, gpt6.maxOutputTokens)

        val deepseek = ModelCapabilityEngine.resolveCapabilities("deepseek-flash")
        assertEquals("DeepSeek V4 最大输出 384K", 384_000, deepseek.maxOutputTokens)
        assertFalse("DeepSeek 使用 max_tokens", deepseek.usesMaxCompletionTokens)
    }

    @Test
    fun testMiniMaxKimiGlmMimoContextWindows() {
        assertEquals(1_000_000, ModelCapabilityEngine.resolveCapabilities("MiniMax-M3").contextWindowTokens)
        assertEquals(1_000_000, ModelCapabilityEngine.resolveCapabilities("kimi-k3").contextWindowTokens)
        assertEquals(256_000, ModelCapabilityEngine.resolveCapabilities("kimi-k2.6").contextWindowTokens)
        assertEquals(1_000_000, ModelCapabilityEngine.resolveCapabilities("glm-5.3").contextWindowTokens)
        assertEquals(256_000, ModelCapabilityEngine.resolveCapabilities("MiMo-V2.6").contextWindowTokens)
    }
}
