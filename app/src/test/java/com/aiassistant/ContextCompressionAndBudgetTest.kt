package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.Message
import org.junit.Assert.*
import org.junit.Test

class ContextCompressionAndBudgetTest {

    @Test
    fun testExtractContextWindowFromError_userRealError() {
        val errorMessage = "This endpoint's maximum context length is 32768 tokens. However, you requested about 416131 tokens (366131 of text input, 50000 in the output). Please reduce the length of either one, or use the context-compression plugin to compress your prompt automatically."
        
        val extracted = AiRepository.extractContextWindowFromError(errorMessage)
        
        assertEquals("应当精准提取端点声明的真实上下文上限 32768", 32768, extracted)
    }

    @Test
    fun testExtractContextWindowFromError_variousFormats() {
        val err1 = "Error: max context length is 16384, but received 20000 tokens"
        assertEquals(16384, AiRepository.extractContextWindowFromError(err1))

        val err2 = "context-window is 65536, please reduce prompt length"
        assertEquals(65536, AiRepository.extractContextWindowFromError(err2))

        val err3 = "limit of 8192 tokens exceeded"
        assertEquals(8192, AiRepository.extractContextWindowFromError(err3))
    }

    @Test
    fun testSafeMaxTokensCalculation_neverExceedsContextHeadroom() {
        val contextWindow = 32768
        val configuredMaxTokens = 50000 // 用户配置了过大的 50000
        val estimatedPromptTokens = 26000 // 已经消耗了 26000

        val headroom = (contextWindow - estimatedPromptTokens - 512).coerceAtLeast(256)
        val safeMaxTokens = minOf(configuredMaxTokens, headroom).coerceIn(256, 16384)

        // 验证：prompt + safeMaxTokens 必然严格小于 contextWindow
        assertTrue(
            "输入 Token ($estimatedPromptTokens) + 安全输出 Token ($safeMaxTokens) 必须小于等于上下文窗口 ($contextWindow)",
            estimatedPromptTokens + safeMaxTokens <= contextWindow
        )
        assertEquals(6256, safeMaxTokens)
    }

    @Test
    fun testActiveCandidateMessages_excludesSummarizedOldMessages() {
        val messages = (1..20).map { id ->
            Message(
                id = id.toLong(),
                conversationId = 1L,
                role = if (id % 2 == 1) "user" else "assistant",
                content = "第 $id 条对话内容",
                isPinned = (id == 5) // 第 5 条被用户显式置顶钉住
            )
        }

        val summarizedThrough = 14L // 摘要已归约到第 14 条
        val hasRollingSummary = true

        val activeCandidates = if (hasRollingSummary && summarizedThrough > 0L) {
            messages.filter { it.id > summarizedThrough || it.isPinned }
        } else {
            messages
        }

        // 活跃消息应包含：第 5 条（钉住）+ 第 15 到 20 条（新消息），共 7 条
        assertEquals(7, activeCandidates.size)
        assertTrue(activeCandidates.any { it.id == 5L })
        assertFalse(activeCandidates.any { it.id == 4L })
        assertFalse(activeCandidates.any { it.id == 14L })
        assertTrue(activeCandidates.any { it.id == 15L })
        assertTrue(activeCandidates.any { it.id == 20L })
    }

    @Test
    fun testEstimateTokenCount_handlesCjkAndAscii() {
        val text = "你好，世界！Hello World 123"
        val count = AiRepository.estimateTokenCount(text)
        assertTrue("混合文本应能准确合理估算 Token", count in 5..20)
    }

    @Test
    fun testPromptBudgetOutputReserve_doesNotConsumeEntireContext() {
        val contextWindow = 32768
        val maxOutputTokens = 50000 // 传入过大的输出设置

        val maxAllowedOutputReserve = (contextWindow * 0.25f).toInt().coerceAtLeast(1024).coerceAtMost(8192)
        val outputReserve = maxOutputTokens.coerceIn(512, maxAllowedOutputReserve)

        assertEquals("对于 32768 上下文，输出预留上限应限制在 8192（25% 比例）", 8192, outputReserve)
        val promptBudget = (contextWindow - outputReserve - 1024).coerceAtLeast(3000)
        assertEquals(23552, promptBudget)
    }

    @Test
    fun testResolveActiveContextMessages_50MessagesBeforeAndAfterCompression() {
        val messages = (1..50).map { id ->
            Message(
                id = id.toLong(),
                conversationId = 1L,
                role = if (id % 2 == 1) "user" else "assistant",
                content = "第 $id 条对话内容，这是一段较长的剧情上下文记录用于模拟大量消耗 Token 的消息内容。"
            )
        }

        val largeBudget = 800_000 // 模拟 1M 模型大预算

        // 1. 压缩前：从未压缩过 (compressedThrough = 0L)
        val beforeCompression = AiRepository.resolveActiveContextMessages(
            usableMessages = messages,
            compressedThrough = 0L,
            recentBudget = largeBudget
        )

        // 验证压缩前状态：
        // 全部 50 条消息都在大预算内作为活跃候选，待压缩早期消息为 34 条 (50 - 16)
        assertEquals("总消息 50 条时，超出最近 16 条的未压缩消息应为 34 条", 34, beforeCompression.uncompressedOlderCount)
        assertTrue("存在未压缩的早期历史时，canCompress 必须为 true", beforeCompression.canCompress)
        assertEquals(34L, beforeCompression.lastOlderMessageId)
        assertEquals("压缩前在大预算下全部 50 条消息都在活跃候选列表中", 50, beforeCompression.activeMessages.size)

        // 2. 执行压缩后：水线移动到第 34 条 (compressedThrough = 34L)
        val afterCompression = AiRepository.resolveActiveContextMessages(
            usableMessages = messages,
            compressedThrough = 34L,
            recentBudget = largeBudget
        )

        // 验证压缩后状态：
        // 早期 34 条消息已沉淀为时间线与记忆，安全退休；活跃消息仅包含最近 16 条！
        assertEquals("已压缩水线覆盖后，未压缩早期历史应为 0 条", 0, afterCompression.uncompressedOlderCount)
        assertFalse("已全部压缩沉淀后，canCompress 应为 false", afterCompression.canCompress)
        assertEquals("活跃上下文消息数应精准降至最近 16 条无损窗口", 16, afterCompression.activeMessages.size)
        assertEquals(35L, afterCompression.activeMessages.first().id)
        assertEquals(50L, afterCompression.activeMessages.last().id)
        assertTrue(
            "压缩后活跃消息 Token 占用 (${afterCompression.recentTokens}) 必须显著少于压缩前 (${beforeCompression.recentTokens})",
            afterCompression.recentTokens < beforeCompression.recentTokens / 2
        )
    }

    @Test
    fun testResolveActiveContextMessages_pinnedOlderMessageRetainedAfterCompression() {
        val messages = (1..50).map { id ->
            Message(
                id = id.toLong(),
                conversationId = 1L,
                role = if (id % 2 == 1) "user" else "assistant",
                content = "第 $id 条对话内容",
                isPinned = (id == 5) // 第 5 条较早对话被用户置顶钉住
            )
        }

        // 水线已压缩到第 34 条
        val result = AiRepository.resolveActiveContextMessages(
            usableMessages = messages,
            compressedThrough = 34L,
            recentBudget = 800_000
        )

        // 活跃消息应包含：第 5 条置顶消息 + 最近 16 条（第 35 到 50 条），共 17 条
        assertEquals(17, result.activeMessages.size)
        assertTrue("置顶的第 5 条消息即使已被水线覆盖，也必须无条件保留在活跃上下文", result.activeMessages.any { it.id == 5L })
        assertEquals(5L, result.activeMessages.first().id)
        assertEquals(35L, result.activeMessages[1].id)
        assertEquals(50L, result.activeMessages.last().id)
    }

    @Test
    fun testResolveActiveContextMessages_shortConversationNeverCompressed() {
        val messages = (1..10).map { id ->
            Message(
                id = id.toLong(),
                conversationId = 1L,
                role = if (id % 2 == 1) "user" else "assistant",
                content = "短对话第 $id 条"
            )
        }

        val result = AiRepository.resolveActiveContextMessages(
            usableMessages = messages,
            compressedThrough = 0L,
            recentBudget = 800_000
        )

        assertEquals("短对话未超出 16 条，olderCount 应为 0", 0, result.uncompressedOlderCount)
        assertFalse("短对话无需压缩，canCompress 应为 false", result.canCompress)
        assertEquals("短对话全部包含在活跃消息中", 10, result.activeMessages.size)
    }

    @Test
    fun testResolveActiveContextMessages_budgetConstraintPacksRecent16Losslessly() {
        val messages = (1..20).map { id ->
            Message(
                id = id.toLong(),
                conversationId = 1L,
                role = if (id % 2 == 1) "user" else "assistant",
                content = "第 $id 条较长内容，测试预算限制下最近16条无损保留机制。"
            )
        }

        // 故意设置极小预算 200 tokens
        val tinyBudget = 200

        val result = AiRepository.resolveActiveContextMessages(
            usableMessages = messages,
            compressedThrough = 0L,
            recentBudget = tinyBudget
        )

        // 验证：即使最近 16 条的总 Token 远超 tinyBudget，也必须无条件完整无损保留全部 16 条！
        assertEquals("最近 16 条必须无损保全", 16, result.activeMessages.size)
        assertEquals(5L, result.activeMessages.first().id)
        assertEquals(20L, result.activeMessages.last().id)
        assertTrue("总 Token 允许突破紧凑预算以确保最近16条无损", result.recentTokens > tinyBudget)
    }
}
