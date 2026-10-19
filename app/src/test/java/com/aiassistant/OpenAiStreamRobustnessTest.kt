package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ChatCompletionResponse
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class OpenAiStreamRobustnessTest {

    @Test
    fun nonStreamingExplicitRefusalStaysSeparateFromBodyAndUsage() {
        val response = Gson().fromJson("""
            {"choices":[{"message":{"role":"assistant","content":null,
              "refusal":"Synthetic policy refusal: this request cannot be summarized."},"finish_reason":"stop"}],
              "usage":{"prompt_tokens":18,"completion_tokens":6,"total_tokens":24}}
        """.trimIndent(), ChatCompletionResponse::class.java)

        assertNull(response.choices?.single()?.message?.content)
        assertEquals("Synthetic policy refusal: this request cannot be summarized.", response.choices?.single()?.message?.refusal)
        assertEquals("stop", response.choices?.single()?.finish_reason)
        assertEquals(24, response.usage?.total_tokens)
    }

    @Test
    fun structuredRefusalCannotBecomeSummaryBodyOrEraseOtherContent() {
        val response = Gson().fromJson("""
            {"choices":[{"message":{"role":"assistant","content":[
              {"type":"text","text":"合成正文仍需由调用方检查。"},
              {"type":"refusal","refusal":"合成策略阻断说明。"},
              {"type":"refusal","text":{"value":"合成额外限制说明。"}},
              {"type":"thinking","thinking":"不应保存的合成推理"}
            ]},"finish_reason":"content_filter"}],"usage":{"total_tokens":13}}
        """.trimIndent(), ChatCompletionResponse::class.java)

        assertEquals("合成正文仍需由调用方检查。", response.choices?.single()?.message?.content)
        assertEquals("合成策略阻断说明。合成额外限制说明。", response.choices?.single()?.message?.refusal)
        assertEquals("content_filter", response.choices?.single()?.finish_reason)
        assertEquals(13, response.usage?.total_tokens)
    }

    @Test
    fun streamedRefusalFragmentsStaySeparateFromTextAndCompletionMetadata() {
        val first = AiRepository.parseOpenAiStreamLine("""
            data: {"choices":[{"delta":{"refusal":"Synthetic policy "},"finish_reason":null}]}
        """.trimIndent())
        val second = AiRepository.parseOpenAiStreamLine("""
            data: {"choices":[{"delta":{"refusal":"refusal."},"finish_reason":"content_filter"}],"usage":{"total_tokens":7}}
        """.trimIndent())

        assertEquals("Synthetic policy refusal.", first?.refusalDelta.orEmpty() + second?.refusalDelta.orEmpty())
        assertNull(first?.contentDelta)
        assertNull(second?.contentDelta)
        assertEquals("content_filter", second?.finishReason)
        assertEquals(7, second?.usage?.total_tokens)
    }

    @Test
    fun streamedEmbeddedAndGatewayRefusalsRemainVisibleWithoutLeakingIntoBody() {
        val result = AiRepository.parseOpenAiStreamLine("""
            data: {"choices":[{"delta":{"content":[{"type":"output_text","text":"合成普通正文。"},
              {"type":"refusal","refusal":"合成块拒绝。"}]},
              "message":{"refusal":"合成网关拒绝。"},"finish_reason":"stop"}]}
        """.trimIndent())

        assertEquals("合成普通正文。", result?.contentDelta)
        assertEquals("合成块拒绝。\n合成网关拒绝。", result?.refusalDelta)
        assertEquals("stop", result?.finishReason)
    }

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
    fun structuredContentPreservesTextValuesAndKeepsReasoningSeparate() {
        val result = AiRepository.parseOpenAiStreamLine("""
            data: {"choices":[{"delta":{"content":[
              {"type":"text","text":"合成正文一。"},
              {"type":"text","text":{"value":"合成正文二。"}},
              {"type":"output_text","text":"合成正文三。"},
              {"type":"thinking","thinking":"合成内部推理"},
              {"type":"image_url","image_url":{"url":"https://example.invalid/image.png"}}
            ]},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":7,"total_tokens":19}}
        """.trimIndent())

        assertEquals("合成正文一。合成正文二。合成正文三。", result?.contentDelta)
        assertEquals("合成内部推理", result?.thinkingDelta)
        assertEquals("stop", result?.finishReason)
        assertEquals(19, result?.usage?.total_tokens)
    }

    @Test
    fun gatewayMessageTextIsNotHiddenByDeltaReasoning() {
        val result = AiRepository.parseOpenAiStreamLine("""
            data: {"choices":[{"delta":{"content":"","reasoning_content":"合成推理"},
              "message":{"content":[{"type":"output_text","text":{"value":"真实合成正文。"}}]},
              "finish_reason":"stop"}]}
        """.trimIndent())

        assertEquals("真实合成正文。", result?.contentDelta)
        assertEquals("合成推理", result?.thinkingDelta)
    }

    @Test
    fun unsupportedContentFieldDoesNotEraseUsageOrFinishReason() {
        val result = AiRepository.parseOpenAiStreamLine("""
            data: {"choices":[{"delta":{"content":{"type":"future_non_text","text":"不得误当正文"}},
              "finish_reason":"length"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
        """.trimIndent())

        assertNotNull(result)
        assertNull(result?.contentDelta)
        assertEquals("length", result?.finishReason)
        assertEquals(15, result?.usage?.total_tokens)
    }

    @Test
    fun invalidReasoningShapeCannotEraseValidBodyAndUsage() {
        val result = AiRepository.parseOpenAiStreamLine("""
            data: {"choices":[{"delta":{"content":"合成有效正文。","reasoning_content":{"unsupported":true}},
              "finish_reason":"stop"}],"usage":{"completion_tokens":4}}
        """.trimIndent())

        assertEquals("合成有效正文。", result?.contentDelta)
        assertNull(result?.thinkingDelta)
        assertEquals(4, result?.usage?.completion_tokens)
    }

    @Test
    fun nonStreamingCompletionDecodesStructuredBodyWithoutChangingStringDto() {
        val response = Gson().fromJson("""
            {"choices":[{"message":{"role":"assistant","content":[
              {"type":"text","text":{"value":"合成早期约束。"}},
              {"type":"output_text","text":"合成事件结果。"},
              {"type":"reasoning","text":"不得保存为摘要的内部推理"}
            ],"reasoning_content":[{"type":"thinking","thinking":"单独的合成推理"}]},
            "finish_reason":"stop"}],"usage":{"prompt_tokens":18,"completion_tokens":6,"total_tokens":24}}
        """.trimIndent(), ChatCompletionResponse::class.java)

        assertEquals("合成早期约束。合成事件结果。", response.choices?.single()?.message?.content)
        assertEquals("单独的合成推理", response.choices?.single()?.message?.reasoning_content)
        assertEquals("stop", response.choices?.single()?.finish_reason)
        assertEquals(24, response.usage?.total_tokens)
    }

    @Test
    fun nonStreamingUnknownContentShapeKeepsCompletionMetadata() {
        val response = Gson().fromJson("""
            {"choices":[{"message":{"role":"assistant","content":{"future_payload":true},
              "reasoning_content":{"future_payload":true}},"finish_reason":"stop"}],"usage":{"total_tokens":13}}
        """.trimIndent(), ChatCompletionResponse::class.java)

        assertNull(response.choices?.single()?.message?.content)
        assertNull(response.choices?.single()?.message?.reasoning_content)
        assertEquals("stop", response.choices?.single()?.finish_reason)
        assertEquals(13, response.usage?.total_tokens)
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
        assertEquals("rate_limit_exceeded", res?.inlineErrorCode)
        assertNull(res?.inlineErrorType)
    }

    @Test
    fun inlineErrorCodeOrTypeWithoutMessageStillReportsFailure() {
        val codeOnly = AiRepository.parseOpenAiStreamLine("""data: {"error":{"code":"invalid_api_key"}}""")
        assertEquals("invalid_api_key", codeOnly?.inlineErrorCode)
        assertNull(codeOnly?.inlineErrorType)
        assertEquals("API 返回错误（未提供详细原因）", codeOnly?.inlineErrorMessage)

        val typeOnly = AiRepository.parseOpenAiStreamLine("""{"error":{"type":"authentication_error","message":null}}""")
        assertEquals("authentication_error", typeOnly?.inlineErrorType)
        assertNull(typeOnly?.inlineErrorCode)
        assertEquals("API 返回错误（未提供详细原因）", typeOnly?.inlineErrorMessage)

        val both = AiRepository.parseOpenAiStreamLine("""data: {"error":{"type":"rate_limit_error","code":"insufficient_quota","message":"  "}}""")
        assertEquals("rate_limit_error", both?.inlineErrorType)
        assertEquals("insufficient_quota", both?.inlineErrorCode)
        assertFalse(both?.inlineErrorMessage.isNullOrBlank())
    }

    @Test
    fun errorMetadataDoesNotLeakIntoSuccessfulContentAndStringErrorsRemainVisible() {
        val stringError = AiRepository.parseOpenAiStreamLine("""data: {"error":"Incorrect API key provided"}""")
        assertEquals("Incorrect API key provided", stringError?.inlineErrorMessage)
        assertNull(stringError?.inlineErrorCode)
        assertNull(stringError?.inlineErrorType)

        val successful = AiRepository.parseOpenAiStreamLine("""data: {"error":null,"choices":[{"delta":{"content":"synthetic invalid_api_key example"},"finish_reason":"stop"}]}""")
        assertEquals("synthetic invalid_api_key example", successful?.contentDelta)
        assertEquals("stop", successful?.finishReason)
        assertNull(successful?.inlineErrorMessage)
        assertNull(successful?.inlineErrorCode)
        assertNull(successful?.inlineErrorType)
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
