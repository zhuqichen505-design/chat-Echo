package com.aiassistant

import com.aiassistant.domain.model.PlotAction
import com.aiassistant.ui.screens.chat.formatNonThinkingCapsuleText
import com.aiassistant.ui.screens.chat.formatThinkingCapsuleText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V1913FeaturesTest {

    @Test
    fun testPlotActionExhaustiveness() {
        for (action in PlotAction.entries) {
            assertTrue("Action ${action.name} should have non-blank displayName", action.displayName.isNotBlank())
            assertTrue("Action ${action.name} should have non-blank description", action.description.isNotBlank())
        }
    }

    @Test
    fun testThinkingCapsuleFormatting_withCustomTemplate() {
        val formatted = formatThinkingCapsuleText(
            template = "{model} 深度思考耗时 {time}，消耗 {tokens}",
            modelName = "deepseek-reasoner",
            isThinkingActive = false,
            responseTimeMs = 3800L,
            thinkingTokens = 420,
            totalTokens = 850
        )
        assertTrue(formatted.contains("deepseek-reasoner"))
        assertTrue(formatted.contains("3s"))
        assertTrue(formatted.contains("420 token"))
    }

    @Test
    fun testThinkingCapsuleFormatting_whileThinkingActive() {
        val formatted = formatThinkingCapsuleText(
            template = "{model} {status}",
            modelName = "claude-3-7-sonnet",
            isThinkingActive = true,
            responseTimeMs = 0L,
            thinkingTokens = 0,
            totalTokens = 0
        )
        assertEquals("claude-3-7-sonnet 思考中...", formatted)
    }

    @Test
    fun testNonThinkingCapsule_zeroResponseTimeFormatting() {
        val result = formatNonThinkingCapsuleText(
            modelName = "deepseek-chat",
            responseTimeMs = 0L,
            tokenCount = 100
        )
        assertEquals("deepseek-chat吃掉了你100token", result)
    }
}
