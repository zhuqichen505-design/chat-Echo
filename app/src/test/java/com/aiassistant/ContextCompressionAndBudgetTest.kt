package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.data.repository.helpers.ContentPruningHelper
import com.aiassistant.data.repository.helpers.ContextCompressionPlanner
import com.aiassistant.domain.model.CompressionTier
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
    @Test fun compressionPlanProtectsWholePinnedTurnsAndMarksUncoveredSources() {
        val messages = (1L..24L).map { id -> Message(id = id, conversationId = 1,
            role = if (id % 2 == 1L) "user" else "assistant", content = "synthetic-$id", isPinned = id == 2L) }
        val plan = com.aiassistant.data.repository.helpers.ContextCompressionPlanner.plan(messages,
            com.aiassistant.domain.model.CompressionTier.L2, 100000, null, 1700000000000L, recentRounds = 4)
        assertTrue(plan.needsSummary)
        assertEquals((1L..16L).toList(), plan.summarySource.map { it.id })
        assertEquals(16L, plan.summaryThroughId)
        assertEquals(listOf(1L, 2L) + (17L..24L), plan.retainedMessages.map { it.id })
        val covered = com.aiassistant.data.repository.helpers.ContextCompressionPlanner.plan(messages,
            com.aiassistant.domain.model.CompressionTier.L2, 100000, "checked summary", 16L, recentRounds = 4)
        assertFalse(covered.needsSummary)
        assertEquals(plan.retainedMessages, covered.retainedMessages)
    }

    @Test fun compressionChunksCoverEveryCharacterWithoutSkippingLongMessageMiddle() {
        val text = "段落开头。" + "中文与ASCII混合0123456789。".repeat(1800) + "中段唯一事实。" + "结尾。".repeat(700)
        val messages = listOf(Message(id = 1, conversationId = 1, role = "user", content = text),
            Message(id = 2, conversationId = 1, role = "assistant", content = "完整回答。"))
        val chunks = com.aiassistant.data.repository.helpers.ContextCompressionPlanner.chunks(messages, 512)
        assertTrue(chunks.size > 20)
        assertEquals(text, chunks.flatten().filter { it.id == 1L }.joinToString("") { it.content })
        assertEquals(messages[1], chunks.last().last())
        assertTrue(chunks.all { part -> part.sumOf(com.aiassistant.data.repository.helpers.ContextCompressionPlanner::messageTokens) <= 512 })
    }

    @Test fun summaryTranscriptUsesStableRolesAndSourceIdsAndConservativeUtf8Budget() {
        val messages = listOf(
            Message(id = 41, conversationId = 1, role = "user", content = "你好，北港。"),
            Message(id = 42, conversationId = 1, role = "assistant", content = "已记录🌠。"),
            Message(id = 43, conversationId = 1, role = "tool", content = "外部资料")
        )
        val transcript = ContextCompressionPlanner.summaryTranscript(messages)
        assertEquals(
            "[用户 · 原文ID 41] 你好，北港。\n" +
                "[助手 · 原文ID 42] 已记录🌠。\n" +
                "[tool · 原文ID 43] 外部资料",
            transcript
        )
        val expectedFloor = (transcript.toByteArray(Charsets.UTF_8).size + 1) / 2
        assertEquals(
            maxOf(AiRepository.estimateTokenCount(transcript), expectedFloor),
            ContextCompressionPlanner.summaryInputTokens(transcript)
        )
        assertEquals(
            ContextCompressionPlanner.summaryInputTokens(ContextCompressionPlanner.summaryTranscript(messages.take(1))) + 1,
            ContextCompressionPlanner.summaryMessageTokens(messages.first())
        )
    }

    @Test fun summaryChunksKeepLongEmojiMessageCompleteUnderWrappedConservativeBudget() {
        val text = "开头🌠𠮷。" + "中段唯一事实：铜钥匙仍在北港。".repeat(1600) + "结尾✅。"
        val message = Message(id = 7, conversationId = 1, role = "user", content = text)
        val counter = ContextCompressionPlanner::summaryMessageTokens
        val chunks = ContextCompressionPlanner.chunks(
            listOf(message), tokenBudget = 512, tokenCounter = counter
        )
        assertTrue(chunks.size > 20)
        assertEquals(text, chunks.flatten().joinToString("") { it.content })
        assertTrue(chunks.all { chunk -> chunk.sumOf(counter) <= 512 })
        assertTrue(chunks.flatten().all { fragment ->
            fragment.content.isEmpty() ||
                (!fragment.content.first().isLowSurrogate() && !fragment.content.last().isHighSurrogate()) &&
                String(fragment.content.toByteArray(Charsets.UTF_8), Charsets.UTF_8) == fragment.content
        })
    }

    @Test fun summaryChunksCoverTwoThousandRoundsWithoutDroppingSourceIds() {
        val messages = (1L..4_000L).map { id ->
            Message(
                id = id,
                conversationId = 1,
                role = if (id % 2L == 1L) "user" else "assistant",
                content = "第${(id + 1) / 2}轮的合成事实[$id]：记录沿途变化并保留铜钥匙。"
            )
        }
        val counter = ContextCompressionPlanner::summaryMessageTokens
        val chunks = ContextCompressionPlanner.chunks(
            messages,
            tokenBudget = 768,
            maxMessages = 40,
            tokenCounter = counter
        )
        assertTrue("2000轮历史必须拆为多个可发送分段", chunks.size > 100)
        assertEquals(messages.map { it.id }, chunks.flatten().map { it.id })
        assertEquals(messages.map { it.content }, chunks.flatten().map { it.content })
        assertTrue(chunks.all { chunk -> chunk.size <= 40 && chunk.sumOf(counter) <= 768 })
    }

    @Test fun smallContextCompressesOldWholeTurnsWithoutDroppingLastTurnOrPinnedText() {
        val messages = (1L..20L).map { id -> Message(id = id, conversationId = 1,
            role = if (id % 2 == 1L) "user" else "assistant", content = "事实${id}。".repeat(100), isPinned = id == 3L) }
        val plan = com.aiassistant.data.repository.helpers.ContextCompressionPlanner.plan(messages,
            com.aiassistant.domain.model.CompressionTier.L0, 700, null, null)
        assertTrue(plan.needsSummary)
        assertEquals(setOf(3L, 4L, 19L, 20L), plan.retainedMessages.map { it.id }.toSet())
        assertTrue(plan.retainedMessages.first().role == "user")
        assertTrue(plan.estimatedHistoryTokens > 700) // caller must explain the pinned/latest overflow, never silently drop it.
    }

    @Test fun compressionChunksPreserveSupplementaryCharactersInEachRequest() {
        val text = "🌠𠮷".repeat(800) + "最后一个事实。"
        val chunks = com.aiassistant.data.repository.helpers.ContextCompressionPlanner.chunks(
            listOf(Message(id = 1, conversationId = 1, role = "user", content = text)), 256)
        assertTrue(chunks.size > 1)
        val fragments = chunks.flatten().map { it.content }
        assertEquals(text, fragments.joinToString(""))
        assertTrue(fragments.all { !it.first().isLowSurrogate() && !it.last().isHighSurrogate() })
        assertTrue(fragments.all { String(it.toByteArray(Charsets.UTF_8), Charsets.UTF_8) == it })
    }

    @Test fun timelineSelectionPreservesChronologyAndRetrievesRelevantOlderFactsWithinBudget() {
        val nodes = (1..25).map { index -> com.aiassistant.domain.model.TimelineNode(id = index.toLong(),
            conversationId = 1, timeTag = "第${index}天", event = if (index == 2) "铜钥匙存放在北港灯塔。" else "第${index}段合成行程已经记录。", orderIndex = index) }
        val selected = com.aiassistant.utils.TimelineMemoryHelper.selectTimelineNodesForContext(nodes, "北港灯塔的铜钥匙在哪里", 220)
        assertTrue(selected.any { it.id == 2L })
        assertTrue(selected.any { it.id == 25L })
        assertTrue(selected.size < nodes.size)
        assertEquals(selected.sortedBy { it.orderIndex }, selected)
        assertTrue(selected.sumOf { AiRepository.estimateTokenCount("[${it.timeTag}] ${it.event}") + 12 } <= 220 - 64)
    }

    @Test fun completeTierRestoresRawHistoryWhileRollingTierBatchesNewShortTurns() {
        val messages = (1L..42L).map { id -> Message(id = id, conversationId = 1,
            role = if (id % 2 == 1L) "user" else "assistant", content = "短消息$id") }
        for (tier in listOf(com.aiassistant.domain.model.CompressionTier.L0, com.aiassistant.domain.model.CompressionTier.L2)) {
            val plan = com.aiassistant.data.repository.helpers.ContextCompressionPlanner.plan(messages, tier, 10000,
                "已确认前24条的摘要。", 24L)
            assertFalse("$tier 不应每条短回复都额外付费", plan.needsSummary)
            val expected = if (tier == CompressionTier.L0) messages else messages.drop(24)
            assertEquals(expected, plan.retainedMessages)
            assertEquals(messages.take(24), plan.summarySource)
            assertEquals(24L, plan.summaryThroughId)
        }
    }

    @Test fun rollingTierKeepsActualUserTurnsIncludingMultipleAssistantMessages() {
        val messages = (1L..10L).flatMap { turn ->
            listOf(
                Message(id = turn * 3 - 2, conversationId = 1, role = "user", content = "合成问题$turn"),
                Message(id = turn * 3 - 1, conversationId = 1, role = "assistant", content = "合成补充$turn"),
                Message(id = turn * 3, conversationId = 1, role = "assistant", content = "合成答复$turn")
            )
        }
        val plan = ContextCompressionPlanner.plan(messages, CompressionTier.L2, 10000, null, null, recentRounds = 4)
        assertEquals(messages.takeLast(12), plan.retainedMessages)
        assertEquals(messages.take(18), plan.summarySource)
        assertTrue(plan.needsSummary)
        val manual = ContextCompressionPlanner.plan(messages, CompressionTier.L0, 10000, null, null, forceCompression = true)
        assertEquals("完整档即使显式应用也应保留全部原文", messages, manual.retainedMessages)
        assertTrue(manual.summarySource.isEmpty())
        assertFalse(manual.needsSummary)
    }

    @Test fun consecutiveUserMessagesCountAsSeparateTurns() {
        val messages = (1L..12L).map { Message(id = it, conversationId = 1, role = "user", content = "合成追问$it") }
        val plan = ContextCompressionPlanner.plan(messages, CompressionTier.L2, 10000, null, null, recentRounds = 4)
        assertEquals(messages.takeLast(4), plan.retainedMessages)
        assertEquals(messages.take(8), plan.summarySource)
    }

    @Test fun lastTurnRemainsWholeEvenWhenItContainsSeveralRepliesAndExceedsBudget() {
        val messages = listOf(
            Message(id = 1, conversationId = 1, role = "user", content = "较早的合成问题。"),
            Message(id = 2, conversationId = 1, role = "assistant", content = "较早的合成答复。"),
            Message(id = 3, conversationId = 1, role = "user", content = "当前合成问题。".repeat(100)),
            Message(id = 4, conversationId = 1, role = "assistant", content = "当前合成分析。".repeat(100)),
            Message(id = 5, conversationId = 1, role = "assistant", content = "当前合成补充。".repeat(100))
        )
        for (tier in CompressionTier.entries) {
            val plan = ContextCompressionPlanner.plan(messages, tier, 1, null, null)
            assertEquals("$tier 不能留下孤立回复或裁掉当前轮内容", messages.takeLast(3), plan.retainedMessages)
            assertEquals(messages.take(2), plan.summarySource)
            assertTrue("调用方必须报告无法容纳完整最新轮次", plan.estimatedHistoryTokens > 1)
            assertTrue(plan.needsSummary)
        }
    }

    @Test fun deeperCompressionKeepsProgressivelyFewerWholeRecentTurns() {
        val messages = (1L..32L).map { Message(id = it, conversationId = 1,
            role = if (it % 2 == 1L) "user" else "assistant", content = "合成事实$it") }
        val plans = listOf(CompressionTier.L2, CompressionTier.L3, CompressionTier.L4).map { tier ->
            ContextCompressionPlanner.plan(messages, tier, 10000, null, null)
        }
        assertEquals(listOf(16, 8, 2), plans.map { it.retainedMessages.size })
        assertEquals(listOf(16, 24, 30), plans.map { it.summarySource.size })
        assertTrue(plans.zipWithNext().all { (lighter, deeper) -> lighter.estimatedHistoryTokens > deeper.estimatedHistoryTokens })
        assertTrue(plans.all { it.retainedMessages.last() == messages.last() && it.retainedMessages.first().role == "user" })
    }

    @Test fun lightPruningFitsToolHeavyHistoryBeforeRequestingPaidSummary() {
        val toolText = "【实时联网参考信息】\n" + "合成检索资料。".repeat(2500) + "\n【用户输入的问题/指令】\n请回答合成问题。"
        val messages = listOf(
            Message(id = 1, conversationId = 1, role = "user", content = toolText),
            Message(id = 2, conversationId = 1, role = "assistant", content = "已归纳合成资料。"),
            Message(id = 3, conversationId = 1, role = "user", content = "请继续讨论。"),
            Message(id = 4, conversationId = 1, role = "assistant", content = "合成讨论仍在继续。"),
            Message(id = 5, conversationId = 1, role = "user", content = toolText),
            Message(id = 6, conversationId = 1, role = "assistant", content = "最新完整答复。")
        )
        val expected = messages.mapIndexed { index, message ->
            if (index < 4) message.copy(content = ContentPruningHelper.pruneMessageContent(message.content)) else message
        }
        val budget = expected.sumOf(ContextCompressionPlanner::messageTokens)
        val light = ContextCompressionPlanner.plan(messages, CompressionTier.L1, budget, null, null)
        val full = ContextCompressionPlanner.plan(messages, CompressionTier.L0, budget, null, null)
        assertFalse("可修剪工具块腾出预算时不应发起摘要", light.needsSummary)
        assertTrue(light.summarySource.isEmpty())
        assertEquals(expected, light.retainedMessages)
        assertEquals(budget, light.estimatedHistoryTokens)
        assertTrue(full.needsSummary)
        assertEquals("最新完整轮次保留工具证据原文", toolText, light.retainedMessages[4].content)
        assertEquals("请求层修剪不得修改源消息", toolText, messages.first().content)
    }

    @Test fun lightPruningKeepsOriginalSummarySourceAndWholePinnedTurn() {
        val toolText = "【实时联网参考信息】\n" + "合成网页资料。".repeat(400) + "\n【用户输入的问题/指令】\n请处理。"
        val messages = (1L..24L).map { id -> Message(id = id, conversationId = 1,
            role = if (id % 2 == 1L) "user" else "assistant",
            content = if (id == 1L || id == 3L || id == 23L) toolText else "合成答复$id。", isPinned = id == 4L) }
        val pruned = messages.mapIndexed { index, message ->
            if (index < 22 && index !in 2..3) message.copy(content = ContentPruningHelper.pruneMessageContent(message.content)) else message
        }
        val budget = (pruned.slice(2..3) + pruned.takeLast(16)).sumOf(ContextCompressionPlanner::messageTokens)
        val plan = ContextCompressionPlanner.plan(messages, CompressionTier.L1, budget, null, null, forceCompression = true)
        assertEquals(messages.take(8), plan.summarySource)
        assertEquals("置顶消息保护同轮用户原文", messages[2], plan.retainedMessages.first())
        assertEquals(messages[3], plan.retainedMessages[1])
        assertEquals(messages.takeLast(2), plan.retainedMessages.takeLast(2))
        assertEquals(plan.retainedMessages.sumOf(ContextCompressionPlanner::messageTokens), plan.estimatedHistoryTokens)
        assertTrue(plan.needsSummary)
    }

    @Test fun coverageUsesHistoryPositionWhenAnEarlyReplyVariantHasANewerId() {
        val messages = (1L..24L).map { id -> Message(id = if (id == 2L) 99L else id, conversationId = 1,
            role = if (id % 2 == 1L) "user" else "assistant", content = "合成事实$id。") }
        val plan = ContextCompressionPlanner.plan(messages, CompressionTier.L2, 10000, "已整理前八轮合成事实。", 16L, recentRounds = 4)
        assertFalse("旧轮次内的新变体ID不能触发重复付费摘要", plan.needsSummary)
        assertEquals(messages.take(16), plan.summarySource)
        assertEquals(messages.takeLast(8), plan.retainedMessages)
    }

    @Test fun coverageDoesNotTreatLaterLowerIdsAsAlreadySummarized() {
        val messages = (1L..32L).map { id -> Message(id = if (id == 16L) 99L else id, conversationId = 1,
            role = if (id % 2 == 1L) "user" else "assistant", content = "合成事实$id。") }
        val plan = ContextCompressionPlanner.plan(messages, CompressionTier.L4, 10000, "合成摘要只涵盖前八轮。", 99L)
        assertTrue("水线之后的低ID原文仍须先生成摘要才能退休", plan.needsSummary)
        assertEquals(messages.take(30), plan.summarySource)
        assertEquals(30L, plan.summaryThroughId)
        assertEquals(messages.takeLast(2), plan.retainedMessages)
    }

    @Test fun tierChangesReuseExistingCoverageWithoutMovingBoundaryBackwards() {
        val messages = (1L..32L).map { id -> Message(id = id, conversationId = 1,
            role = if (id % 2 == 1L) "user" else "assistant", content = "合成事实$id。") }
        val retainedCounts = mapOf(CompressionTier.L0 to 32, CompressionTier.L1 to 32, CompressionTier.L2 to 16,
            CompressionTier.L3 to 8, CompressionTier.L4 to 2, CompressionTier.LC to 10)
        for (tier in CompressionTier.entries) {
            val plan = ContextCompressionPlanner.plan(messages, tier, 10000, "已经整理至第十五轮。", 30L, forceCompression = true)
            assertFalse("切换到${tier}不能重新付费整理已覆盖原文", plan.needsSummary)
            assertEquals(30L, plan.summaryThroughId)
            assertEquals(messages.take(30), plan.summarySource)
            assertEquals("${tier}应按目标档位恢复原文窗口", messages.takeLast(retainedCounts.getValue(tier)), plan.retainedMessages)
        }
    }

    @Test fun switchingFromDeepToShallowKeepsRestoredRawWindowOnTheNextSendWithoutRegeneratingSummary() {
        val messages = (1L..34L).map { Message(id = it, conversationId = 1,
            role = if (it % 2 == 1L) "user" else "assistant", content = "可恢复的合成事实$it。") }
        val before = messages.take(32)
        val deep = ContextCompressionPlanner.plan(before, CompressionTier.L4, 10000, "已确认前十五轮。", 30L)
        assertEquals(before.takeLast(2), deep.retainedMessages)
        for (tier in listOf(CompressionTier.L0, CompressionTier.L1, CompressionTier.L2, CompressionTier.L3, CompressionTier.LC)) {
            val applied = ContextCompressionPlanner.plan(before, tier, 10000, "已确认前十五轮。", 30L, forceCompression = true)
            val nextSend = ContextCompressionPlanner.plan(messages, tier, 10000, "已确认前十五轮。", 30L)
            val expectedApplied = when (tier) {
                CompressionTier.L0, CompressionTier.L1 -> before
                CompressionTier.L2 -> before.takeLast(16)
                CompressionTier.L3 -> before.takeLast(8)
                else -> before.takeLast(10)
            }
            val expectedNext = when (tier) {
                CompressionTier.L0, CompressionTier.L1 -> messages
                CompressionTier.L2 -> messages.takeLast(16)
                CompressionTier.L3 -> messages.takeLast(8)
                else -> messages.takeLast(10)
            }
            assertEquals("$tier 显式应用恢复其原文窗口", expectedApplied, applied.retainedMessages)
            assertEquals("$tier 下一轮不能退回旧深压缩水线", expectedNext, nextSend.retainedMessages)
            for (plan in listOf(applied, nextSend)) {
                assertFalse("恢复已覆盖原文不能重新请求摘要", plan.needsSummary)
                assertEquals(30L, plan.summaryThroughId)
                assertEquals(messages.take(30), plan.summarySource)
            }
        }
    }

    @Test fun fullOrPartialLastTurnCoverageNeverRegressesWhenRawHistoryIsRestored() {
        val messages = (1L..6L).flatMap { turn -> listOf(
            Message(id = turn * 3 - 2, conversationId = 1, role = "user", content = "合成问题$turn"),
            Message(id = turn * 3 - 1, conversationId = 1, role = "assistant", content = "合成补充$turn"),
            Message(id = turn * 3, conversationId = 1, role = "assistant", content = "合成回答$turn")) }
        for (through in listOf(17L, 18L)) for (tier in CompressionTier.entries) {
            val plan = ContextCompressionPlanner.plan(messages, tier, 10000, "已有摘要覆盖当前轮的部分或全部消息。", through,
                forceCompression = true)
            assertEquals(through, plan.summaryThroughId)
            assertEquals(messages.take(through.toInt()), plan.summarySource)
            assertFalse(plan.needsSummary)
            assertEquals(messages.takeLast(3), plan.retainedMessages.takeLast(3))
            assertEquals("每份原文窗口必须从完整轮次开始", "user", plan.retainedMessages.first().role)
        }
    }

    @Test fun restoredWindowRespectsBudgetAndWholePinnedTurnsWhileReusingDeeperCoverage() {
        val messages = (1L..32L).map { Message(id = it, conversationId = 1,
            role = if (it % 2 == 1L) "user" else "assistant", content = "带预算的合成事实$it。", isPinned = it == 2L) }
        val restored = ContextCompressionPlanner.plan(messages, CompressionTier.L2, 10000, "已整理至前十五轮。", 30L,
            forceCompression = true)
        assertEquals(messages.take(2) + messages.takeLast(16), restored.retainedMessages)
        val expected = messages.take(2) + messages.takeLast(4)
        val budget = expected.sumOf(ContextCompressionPlanner::messageTokens)
        val constrained = ContextCompressionPlanner.plan(messages, CompressionTier.L2, budget, "已整理至前十五轮。", 30L,
            forceCompression = true)
        assertEquals(expected, constrained.retainedMessages)
        assertEquals(budget, constrained.estimatedHistoryTokens)
        assertEquals(30L, constrained.summaryThroughId)
        assertEquals(messages.take(30), constrained.summarySource)
        assertFalse(constrained.needsSummary)
        val overflow = ContextCompressionPlanner.plan(messages, CompressionTier.L0, 1, "已整理至前十五轮。", 30L)
        assertEquals(messages.take(2) + messages.takeLast(2), overflow.retainedMessages)
        assertTrue("无法容纳的置顶和当前轮必须交给调用方明确报错", overflow.estimatedHistoryTokens > 1)
        assertEquals(overflow.retainedMessages.sumOf(ContextCompressionPlanner::messageTokens), overflow.estimatedHistoryTokens)
        assertFalse(overflow.needsSummary)
    }

    @Test fun rollingSummaryBatchesByWholeTurnsWhileBudgetPressureCanAdvanceImmediately() {
        val messages = (1L..40L).map { id -> Message(id = id, conversationId = 1,
            role = if (id % 2 == 1L) "user" else "assistant", content = "合成事实$id。") }
        val smallBatch = ContextCompressionPlanner.plan(messages.take(30), CompressionTier.L2, 10000, "已整理前八轮。", 16L, recentRounds = 4)
        assertFalse(smallBatch.needsSummary)
        assertEquals(16L, smallBatch.summaryThroughId)
        val explicit = ContextCompressionPlanner.plan(messages.take(30), CompressionTier.L2, 10000, "已整理前八轮。", 16L,
            recentRounds = 4, forceCompression = true)
        assertTrue("显式应用不能等待自动四轮批处理", explicit.needsSummary)
        assertEquals(22L, explicit.summaryThroughId)
        assertEquals(messages.take(30).takeLast(8), explicit.retainedMessages)
        val fullBatch = ContextCompressionPlanner.plan(messages.take(32), CompressionTier.L2, 10000, "已整理前八轮。", 16L, recentRounds = 4)
        assertTrue(fullBatch.needsSummary)
        assertEquals(24L, fullBatch.summaryThroughId)
        val lastTurnBudget = messages.take(30).takeLast(2).sumOf(ContextCompressionPlanner::messageTokens)
        val pressured = ContextCompressionPlanner.plan(messages.take(30), CompressionTier.L2, lastTurnBudget, "已整理前八轮。", 16L, recentRounds = 4)
        assertTrue(pressured.needsSummary)
        assertEquals(messages.take(30).takeLast(2), pressured.retainedMessages)
        assertTrue(pressured.estimatedHistoryTokens <= lastTurnBudget)
    }

}
