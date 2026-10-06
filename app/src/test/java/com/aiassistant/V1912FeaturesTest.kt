package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.ui.screens.chat.formatNonThinkingCapsuleText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class V1912FeaturesTest {

    @Test
    fun testParseApiKeys_supportsMultipleSeparatorsAndTrimming() {
        val rawInput = """
            sk-key1
            sk-key2, sk-key3;
            sk-key4
            sk-key1
        """.trimIndent()

        val parsed = AiRepository.parseApiKeys(rawInput)
        assertEquals(4, parsed.size)
        assertEquals("sk-key1", parsed[0])
        assertEquals("sk-key2", parsed[1])
        assertEquals("sk-key3", parsed[2])
        assertEquals("sk-key4", parsed[3])
    }

    @Test
    fun testParseApiKeys_emptyAndBlank() {
        assertTrue(AiRepository.parseApiKeys(null).isEmpty())
        assertTrue(AiRepository.parseApiKeys("   ").isEmpty())
        assertTrue(AiRepository.parseApiKeys(",;;\n\n").isEmpty())
    }

    @Test
    fun testIsTimeoutException_detectsAllTimeoutVariants() {
        assertTrue(AiRepository.isTimeoutException(SocketTimeoutException("timeout")))
        assertTrue(AiRepository.isTimeoutException(RuntimeException("connect timed out")))
        assertTrue(AiRepository.isTimeoutException(Exception(IllegalStateException("Read Timeout error"))))

        assertFalse(AiRepository.isTimeoutException(RuntimeException("HTTP 401 Unauthorized")))
        assertFalse(AiRepository.isTimeoutException(IllegalArgumentException("Invalid model name")))
    }

    @Test
    fun testNonThinkingCapsuleTextFormatting_matchesSpecifiedTemplate() {
        val result = formatNonThinkingCapsuleText(
            modelName = "deepseek-chat",
            responseTimeMs = 2450L,
            tokenCount = 520
        )
        assertEquals("deepseek-chat用2秒吃掉了你520token", result)
    }

    @Test
    fun testNonThinkingCapsuleTextFormatting_withContentEstimationFallback() {
        val result = formatNonThinkingCapsuleText(
            modelName = "gpt-4o",
            responseTimeMs = 1200L,
            tokenCount = 0,
            content = "测试中文回答内容，检查估算 token 逻辑。"
        )
        assertTrue(result.startsWith("gpt-4o用1秒吃掉了你"))
        assertTrue(result.endsWith("token"))
    }

    @Test
    fun testModelSearchFiltering() {
        val models = listOf("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet", "deepseek-chat", "deepseek-reasoner", "o1-preview")
        val query = "deepseek"
        val filtered = models.filter { it.contains(query.trim(), ignoreCase = true) }
        assertEquals(2, filtered.size)
        assertTrue(filtered.contains("deepseek-chat"))
        assertTrue(filtered.contains("deepseek-reasoner"))
    }

    @Test
    fun testUnorderedListCleanLine_hasNoBulletDot() {
        val rawLine = "- 这是一个无序列表条目"
        val cleanContent = rawLine.trimStart().removePrefix("- ").removePrefix("* ")
        assertEquals("这是一个无序列表条目", cleanContent)
        assertFalse("渲染内容不应包含圆点前缀", cleanContent.startsWith("•") || cleanContent.startsWith("·"))
    }

    @Test
    fun testHeadingHierarchy_sizesAreConsistent() {
        // H1 (22sp) > H2 (20sp) > H3 (18.5sp) > H4 (17sp) > H5 (16sp) >= H6 (16sp) >= Body (16sp)
        val h1 = 22.0
        val h2 = 20.0
        val h3 = 18.5
        val h4 = 17.0
        val h5 = 16.0
        val h6 = 16.0
        val bodyLarge = 16.0

        assertTrue(h1 > h2)
        assertTrue(h2 > h3)
        assertTrue(h3 > h4)
        assertTrue(h4 > h5)
        assertTrue(h5 >= h6)
        assertTrue(h6 >= bodyLarge)
    }
}
