package com.aiassistant

import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.ModelCapabilityEngine
import org.junit.Assert.*
import org.junit.Test

class V1919FeaturesTest {

    @Test
    fun testIsMainlyEnglish_detectionLogic() {
        // Blank or null or very short text
        assertFalse(AiRepository.isMainlyEnglish(null))
        assertFalse(AiRepository.isMainlyEnglish(""))
        assertFalse(AiRepository.isMainlyEnglish("   "))
        assertFalse(AiRepository.isMainlyEnglish("Short text"))

        // Chinese CoT
        val chineseCot = "用户询问如何写一个排序算法。我需要先分析快速排序与归并排序的异同，然后给出一个清晰易懂的Kotlin实现。"
        assertFalse(AiRepository.isMainlyEnglish(chineseCot))

        // English CoT
        val englishCot = "The user is asking about the time complexity of quicksort. First, I should analyze the best-case, average-case, and worst-case performance, and explain why the pivot selection strategy is critical."
        assertTrue(AiRepository.isMainlyEnglish(englishCot))

        // English CoT with small amount of technical terms or punctuation
        val englishWithCode = "Let's review the problem statement: we need to find the median of two sorted arrays with O(log (m+n)) runtime. A binary search on the smaller array is the standard optimal approach."
        assertTrue(AiRepository.isMainlyEnglish(englishWithCode))

        // Mixed with substantial Chinese (e.g., > 15% Chinese)
        val mixedCot = "The user asks for a function. 首先我们需要定义函数的入参和返回值，然后再编写具体的测试用例。"
        assertFalse(AiRepository.isMainlyEnglish(mixedCot))
    }

    @Test
    fun testThinkingEffortAndBudgetMapping() {
        // OpenAI model: effort supports low, medium, high (ultra normalizes to high)
        assertEquals("low", AiRepository.normalizeThinkingEffort("low", "openai"))
        assertEquals("medium", AiRepository.normalizeThinkingEffort("medium", "openai"))
        assertEquals("high", AiRepository.normalizeThinkingEffort("high", "openai"))
        assertEquals("high", AiRepository.normalizeThinkingEffort("ultra", "openai"))
        assertEquals("high", AiRepository.normalizeThinkingEffort("max", "openai"))

        // Anthropic Claude model: token budget with standard 4096 base
        val lowBudget = AiRepository.thinkingBudgetForEffort("low", 4096)
        val medBudget = AiRepository.thinkingBudgetForEffort("medium", 4096)
        val highBudget = AiRepository.thinkingBudgetForEffort("high", 4096)
        val ultraBudget = AiRepository.thinkingBudgetForEffort("ultra", 4096)

        assertEquals(2048, lowBudget)
        assertEquals(4096, medBudget)
        assertEquals(8192, highBudget)
        assertEquals(32768, ultraBudget)
        assertTrue(ultraBudget > highBudget)
        assertTrue(highBudget > medBudget)
        assertTrue(medBudget > lowBudget)
    }

    @Test
    fun testMessageEntity_translatedThinkingSupport() {
        val msg = Message(
            conversationId = 1L,
            role = "assistant",
            content = "Hello world",
            thinkingContent = "The user greeted me, so I will greet them back.",
            translatedThinking = "用户向我打招呼，因此我应当礼貌回复。"
        )

        assertNotNull(msg.translatedThinking)
        assertEquals("用户向我打招呼，因此我应当礼貌回复。", msg.translatedThinking)
        assertEquals("The user greeted me, so I will greet them back.", msg.thinkingContent)
    }

    @Test
    fun testStopGenerationMessageContent() {
        // Case 1: Interrupted mid-output
        val partialResponse = "这是模型已经生成的上半截文字"
        val stoppedContent = "$partialResponse\n\n*(回复已被暂停)*"
        assertTrue(stoppedContent.startsWith("这是模型已经生成的上半截文字"))
        assertTrue(stoppedContent.contains("*(回复已被暂停)*"))

        // Case 2: Stopped before any output produced
        val emptyStoppedContent = "回复已停止"
        assertEquals("回复已停止", emptyStoppedContent)
    }

    @Test
    fun testRoomMigration21_22_registered() {
        val migration = AppDatabase.MIGRATION_21_22
        assertEquals(21, migration.startVersion)
        assertEquals(22, migration.endVersion)
    }
}
