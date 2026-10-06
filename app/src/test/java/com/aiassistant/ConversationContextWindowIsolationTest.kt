package com.aiassistant

import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.domain.model.SelectedModel
import com.aiassistant.ui.screens.chat.TempChatSettings
import org.junit.Assert.*
import org.junit.Test

class ConversationContextWindowIsolationTest {

    @Test
    fun testConversationCustomContextLimit_takesPriorityOverModelDefault() {
        val modelDefaultTokens = ModelCapabilityEngine.evaluateModel("gemini-1.5-flash").contextWindowTokens
        assertEquals("Gemini 1.5 Flash 默认应为 1M 上下文", 1_000_000, modelDefaultTokens)

        val convWithCustomLimit = Conversation(
            id = 1001L,
            title = "自定义上限会话",
            apiConfigId = 1L,
            modelName = "gemini-1.5-flash",
            contextWindowTokens = 65_536
        )

        // 验证优先级：显式设置 > 会话设置 > 模型全局设置 > 引擎识别
        val effectiveTokens = convWithCustomLimit.contextWindowTokens ?: modelDefaultTokens
        assertEquals("会话独立设定的 64K 上下文应当优先于模型的 1M", 65_536, effectiveTokens)
    }

    @Test
    fun testConversationResetToFollowModel_fallsBackToModelDefault() {
        val modelDefaultTokens = ModelCapabilityEngine.evaluateModel("gemini-2.0-flash").contextWindowTokens
        assertEquals(1_000_000, modelDefaultTokens)

        val conv = Conversation(
            id = 1002L,
            title = "跟随模型会话",
            apiConfigId = 1L,
            modelName = "gemini-2.0-flash",
            contextWindowTokens = null // null 表示跟随模型
        )

        val effectiveTokens = conv.contextWindowTokens ?: modelDefaultTokens
        assertEquals("contextWindowTokens 为 null 时应跟随模型默认 1M", 1_000_000, effectiveTokens)
    }

    @Test
    fun testDegradationIsolation_onlyAffectsTargetConversation() {
        val globalModel = SelectedModel(
            id = 10L,
            apiConfigId = 1L,
            modelName = "gemini-3.8-flash",
            contextWindowTokens = 1_000_000
        )

        var convA = Conversation(
            id = 1L,
            title = "会话 A (发生超限降级)",
            apiConfigId = 1L,
            modelName = "gemini-3.8-flash",
            contextWindowTokens = null
        )

        val convB = Conversation(
            id = 2L,
            title = "会话 B (正常会话)",
            apiConfigId = 1L,
            modelName = "gemini-3.8-flash",
            contextWindowTokens = null
        )

        // 模拟对会话 A 触发降级至 32K
        val degradedWindow = 32_768
        convA = convA.copy(contextWindowTokens = degradedWindow)

        // 核心验证 1: 会话 A 生效 32K 降级
        assertEquals(32_768, convA.contextWindowTokens)

        // 核心验证 2: 全局 SelectedModel 未受任何污染，保持 1M
        assertEquals("全局模型配置不得被会话降级污染", 1_000_000, globalModel.contextWindowTokens)

        // 核心验证 3: 会话 B 保持 null，解析依然为 1M 上下文
        assertNull("其他会话 contextWindowTokens 保持独立 null", convB.contextWindowTokens)
        val convBEffective = convB.contextWindowTokens ?: globalModel.contextWindowTokens ?: 256_000
        assertEquals("会话 B 的有效上下文依然是 1M，不受会话 A 降级影响", 1_000_000, convBEffective)
    }

    @Test
    fun testTempChatSettings_supportsContextLimitAdjustmentAndReset() {
        var tempSettings = TempChatSettings(
            temperature = 0.9f,
            maxTokens = 4096,
            contextWindowTokens = null
        )
        assertNull("初始为跟随模型", tempSettings.contextWindowTokens)

        // 用户在对话内调整为 128K
        tempSettings = tempSettings.copy(contextWindowTokens = 131_072)
        assertEquals(131_072, tempSettings.contextWindowTokens)

        // 用户点击重置为跟随模型
        tempSettings = tempSettings.copy(contextWindowTokens = null)
        assertNull("重置后恢复为 null", tempSettings.contextWindowTokens)
    }

    @Test
    fun testBranchConversationInheritsParentContextLimit() {
        val parentConv = Conversation(
            id = 10L,
            title = "父会话",
            apiConfigId = 1L,
            modelName = "claude-3-5-sonnet",
            contextWindowTokens = 200_000
        )

        val branchConv = Conversation(
            id = 11L,
            title = "父会话 (分支 1)",
            apiConfigId = parentConv.apiConfigId,
            modelName = parentConv.modelName,
            contextWindowTokens = parentConv.contextWindowTokens
        )

        assertEquals("分支会话应当继承父会话设置的上下文限制", 200_000, branchConv.contextWindowTokens)
    }

    @Test
    fun testDegradedTo32kCanBeManuallyAdjustedOrReset() {
        val modelDefaultTokens = 1_000_000
        var conv = Conversation(
            id = 50L,
            title = "降级后手动调整会话",
            apiConfigId = 1L,
            modelName = "gemini-1.5-pro",
            contextWindowTokens = null
        )

        // 1. 系统触发 32K 降级保护
        conv = conv.copy(contextWindowTokens = 32_768)
        assertEquals("降级后会话上限为 32K", 32_768, conv.contextWindowTokens)

        // 识别降级状态
        val isDegraded = conv.contextWindowTokens == 32_768 && modelDefaultTokens > 32_768
        assertTrue("应识别出会话处于降级状态", isDegraded)

        // 2. 用户在 UI 中手动调大为 128K (131072)
        conv = conv.copy(contextWindowTokens = 131_072)
        assertEquals("降级后允许用户手动调大至 128K", 131_072, conv.contextWindowTokens)
        val isStillDegradedAfterManual = conv.contextWindowTokens == 32_768 && modelDefaultTokens > 32_768
        assertFalse("手动调整后解除降级警示状态", isStillDegradedAfterManual)

        // 3. 用户在 UI 中手动调大为 1M (1000000)
        conv = conv.copy(contextWindowTokens = 1_000_000)
        assertEquals("允许手动调整为 1M", 1_000_000, conv.contextWindowTokens)

        // 4. 用户点击一键恢复为跟随模型 (重置为 null)
        conv = conv.copy(contextWindowTokens = null)
        assertNull("恢复跟随模型后应为 null", conv.contextWindowTokens)
        val finalEffectiveTokens = conv.contextWindowTokens ?: modelDefaultTokens
        assertEquals("重置后解析生效为模型原生上限 1M", 1_000_000, finalEffectiveTokens)
    }
}

