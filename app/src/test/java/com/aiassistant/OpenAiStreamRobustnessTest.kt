package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import org.junit.Assert.*
import org.junit.Test

class OpenAiStreamRobustnessTest {

    @Test
    fun testParseStandardSseChunk() {
        val line = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello world\"},\"finish_reason\":null}]}"
        val res = AiRepository.parseOpenAiStreamLine(line)
        assertNotNull(res)
        assertEquals("Hello world", res?.contentDelta)
        assertNull(res?.finishReason)
        assertFalse(res?.isDone ?: true)
    }

    @Test
    fun testParseDoneChunk() {
        val line = "data: [DONE]"
        val res = AiRepository.parseOpenAiStreamLine(line)
        assertNotNull(res)
        assertTrue(res?.isDone == true)
    }

    @Test
    fun testParseMissingFinishReasonChunk() {
        // 上游不返回 finish_reason
        val line = "data: {\"choices\":[{\"delta\":{\"content\":\"Partial content\"}}]}"
        val res = AiRepository.parseOpenAiStreamLine(line)
        assertNotNull(res)
        assertEquals("Partial content", res?.contentDelta)
        assertNull(res?.finishReason)
        assertFalse(res?.isDone ?: true)
    }

    @Test
    fun testParseWithBomAndExcessWhitespace() {
        // 带 UTF-8 BOM (\uFEFF) 与前后空格
        val line = "\uFEFF  data: {\"choices\":[{\"delta\":{\"content\":\"With BOM\"}}]}  "
        val res = AiRepository.parseOpenAiStreamLine(line)
        assertNotNull(res)
        assertEquals("With BOM", res?.contentDelta)
    }

    @Test
    fun testParseNdjsonFormat() {
        // NDJSON 格式，无 data: 前缀
        val line = "{\"choices\":[{\"delta\":{\"content\":\"NDJSON content\"},\"finish_reason\":\"stop\"}]}"
        val res = AiRepository.parseOpenAiStreamLine(line)
        assertNotNull(res)
        assertEquals("NDJSON content", res?.contentDelta)
        assertEquals("stop", res?.finishReason)
    }

    @Test
    fun testParseChoicesMessageFallback() {
        // 非标准中转：choices[0].message 而非 choices[0].delta
        val line = "data: {\"choices\":[{\"message\":{\"content\":\"Gateway non-standard content\"}}]}"
        val res = AiRepository.parseOpenAiStreamLine(line)
        assertNotNull(res)
        assertEquals("Gateway non-standard content", res?.contentDelta)
    }

    @Test
    fun testParseReasoningContentVariations() {
        // 测试 reasoning_content 以及 deepseek/thinking 变体
        val line1 = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking step 1\"}}]}"
        val res1 = AiRepository.parseOpenAiStreamLine(line1)
        assertEquals("thinking step 1", res1?.thinkingDelta)

        val line2 = "data: {\"choices\":[{\"delta\":{\"thought\":\"thinking step 2\"}}]}"
        val res2 = AiRepository.parseOpenAiStreamLine(line2)
        assertEquals("thinking step 2", res2?.thinkingDelta)
    }

    @Test
    fun testIgnoreCommentsAndControlLines() {
        // SSE ping/keepalive 注释以及空行、event、id、retry
        assertNull(AiRepository.parseOpenAiStreamLine(": keepalive"))
        assertNull(AiRepository.parseOpenAiStreamLine(": ping"))
        assertNull(AiRepository.parseOpenAiStreamLine("   "))
        assertNull(AiRepository.parseOpenAiStreamLine("event: message"))
        assertNull(AiRepository.parseOpenAiStreamLine("id: 12345"))
        assertNull(AiRepository.parseOpenAiStreamLine("retry: 10000"))
    }

    @Test
    fun testInlineErrorMessageExtraction() {
        val errorJson = "data: {\"error\":{\"message\":\"Rate limit reached\",\"code\":\"rate_limit_exceeded\"}}"
        val res = AiRepository.parseOpenAiStreamLine(errorJson)
        assertNotNull(res)
        assertEquals("Rate limit reached", res?.inlineErrorMessage)
    }

    @Test
    fun testV221UserUpdatesCompleteness() {
        val v221 = com.aiassistant.ui.screens.settings.V221UserUpdates
        assertEquals("V221 必须包含 4 项核心改动说明", 4, v221.size)
        assertTrue(v221.any { it.contains("OpenAI 兼容流式健壮解析") })
        assertTrue(v221.any { it.contains("断流内容绝对保全与防丢弃") })
        assertTrue(v221.any { it.contains("精准判空失败防护") })
        assertTrue(v221.any { it.contains("智能重试保护与指数退避") })
    }
}
