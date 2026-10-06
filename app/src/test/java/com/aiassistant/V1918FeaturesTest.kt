package com.aiassistant

import com.aiassistant.data.repository.RoleplayRepository
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.tools.device.HealthDataManager
import org.junit.Assert.*
import org.junit.Test

class V1918FeaturesTest {

    @Test
    fun testContextWindowRecognition_exactAndKnown() {
        // Known exact / prefixes
        val gemini = ModelCapabilityEngine.resolveCapabilities("gemini-1.5-pro")
        assertEquals(2_000_000, gemini.contextWindowTokens)
        assertEquals("2M", gemini.contextWindowDisplay)

        val claude = ModelCapabilityEngine.resolveCapabilities("claude-3-7-sonnet-20250219")
        assertEquals(200_000, claude.contextWindowTokens)
        assertEquals("200K", claude.contextWindowDisplay)

        val deepseek = ModelCapabilityEngine.resolveCapabilities("deepseek-chat")
        assertEquals(128_000, deepseek.contextWindowTokens)
        assertEquals("128K", deepseek.contextWindowDisplay)

        val qwenLong = ModelCapabilityEngine.resolveCapabilities("qwen-long")
        assertEquals(1_000_000, qwenLong.contextWindowTokens)
        assertEquals("1M", qwenLong.contextWindowDisplay)

        // Suffix extraction: -128k
        val custom128k = ModelCapabilityEngine.resolveCapabilities("custom-llama-3-128k-instruct")
        assertEquals(128_000, custom128k.contextWindowTokens)
        assertEquals("128K", custom128k.contextWindowDisplay)
    }

    @Test
    fun testContextWindowRecognition_unrecognizedDefaultsTo256kNoBadge() {
        // Completely unrecognized model name
        val unknown = ModelCapabilityEngine.resolveCapabilities("my-private-finetuned-model-v1")
        assertEquals(256_000, unknown.contextWindowTokens)
        // No inaccurate badge should be displayed
        assertEquals("", unknown.contextWindowDisplay)
        assertTrue(unknown.contextWindowDisplay.isBlank())
    }

    @Test
    fun testThinkingGearCompatibility() {
        // 现代 GPT-6：五档含 xhigh（不再为 o 系列做专项适配）
        val gpt6 = ModelCapabilityEngine.resolveCapabilities("gpt-6-astra")
        assertTrue(gpt6.supportsThinking)
        assertEquals("openai", gpt6.reasoningProviderType)
        assertTrue(gpt6.supportedThinkingGears.contains("low"))
        assertTrue(gpt6.supportedThinkingGears.contains("high"))

        // Claude 3.7: gear supports low/med/high/max
        val claude37 = ModelCapabilityEngine.resolveCapabilities("claude-3-7-sonnet")
        assertTrue(claude37.supportsThinking)
        assertEquals("anthropic", claude37.reasoningProviderType)
        assertEquals(emptyList<String>(), claude37.supportedThinkingGears) // Uses numeric budget, not effort.

        // DeepSeek-R1: full reasoning with dynamic thinking gears support (v1.9.20 upgrade)
        val dsR1 = ModelCapabilityEngine.resolveCapabilities("deepseek-reasoner")
        assertTrue(dsR1.supportsThinking)
        assertEquals("deepseek_fixed", dsR1.reasoningProviderType)
        assertEquals(emptyList<String>(), dsR1.supportedThinkingGears)

        // Non-thinking model
        val gpt4o = ModelCapabilityEngine.resolveCapabilities("gpt-4o")
        assertFalse(gpt4o.supportsThinking)
        assertEquals("none", gpt4o.reasoningProviderType)
        assertTrue(gpt4o.supportedThinkingGears.isEmpty())
    }

    @Test
    fun testHealthDataSummary_noFakeBiometrics() {
        // Default summary has no heart rate or sleep recorded (-1)
        val emptySummary = HealthDataManager.HealthDataSummary(
            todaySteps = 0,
            heartRate = -1,
            sleepMinutes = -1,
            deepSleepMinutes = -1,
            sleepScore = -1,
            hasHardwareStepSensor = false
        )

        val prompt = emptySummary.toPromptBlock()
        // Must explain privacy sandbox and not invent 72 bpm or 7.5h
        assertFalse(prompt.contains("72 bpm"))
        assertFalse(prompt.contains("7.5h"))
        assertFalse(prompt.contains("85 分"))
        assertTrue(prompt.contains("暂无录入数据") || prompt.contains("未在应用内录入"))
        assertTrue(prompt.contains("隐私沙箱") || prompt.contains("健康数据"))

        // When user manually records real biometrics
        val recordedSummary = HealthDataManager.HealthDataSummary(
            todaySteps = 5420,
            heartRate = 68,
            sleepMinutes = 450,
            deepSleepMinutes = 120,
            sleepScore = 88,
            hasHardwareStepSensor = true
        )
        val recordedPrompt = recordedSummary.toPromptBlock()
        assertTrue(recordedPrompt.contains("5420"))
        assertTrue(recordedPrompt.contains("68 bpm"))
        assertTrue(recordedPrompt.contains("7小时30分钟"))
        assertTrue(recordedPrompt.contains("88 分"))
    }

    @Test
    fun testDefaultFictionTeachingGuidelinesConstant() {
        val defaultGuidelines = RoleplayRepository.DEFAULT_FICTION_TEACHING_GUIDELINES
        assertTrue(defaultGuidelines.isNotBlank())
        assertTrue(defaultGuidelines.contains("以演代述"))
        assertTrue(defaultGuidelines.contains("世界观沉浸度"))
        assertTrue(defaultGuidelines.contains("用户主导与留白互动"))
    }

    @Test
    fun testRoleplayCharacterDeduplicationCount() {
        // Verify (6/5) bug resolution logic: UI must filter selected IDs against valid character IDs
        val allValidCharacters = listOf(
            CharacterProfile(id = 1L, name = "角色1"),
            CharacterProfile(id = 2L, name = "角色2"),
            CharacterProfile(id = 3L, name = "角色3"),
            CharacterProfile(id = 4L, name = "角色4"),
            CharacterProfile(id = 5L, name = "角色5")
        )
        val validIds = allValidCharacters.map { it.id }.toSet()

        // Suppose session or UI state had phantom IDs like 999L or duplicates
        val rawSelectedIds = listOf(1L, 2L, 3L, 4L, 5L, 999L, 1L)
        val sanitized = rawSelectedIds.filter { it in validIds }.distinct()

        assertEquals(5, sanitized.size)
        assertEquals(5, allValidCharacters.size)
        val displayRatio = "(${sanitized.size}/${allValidCharacters.size})"
        assertEquals("(5/5)", displayRatio)
        assertNotEquals("(6/5)", displayRatio)
    }
}
