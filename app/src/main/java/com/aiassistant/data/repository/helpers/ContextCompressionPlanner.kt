package com.aiassistant.data.repository.helpers

import com.aiassistant.domain.model.CompressionTier
import com.aiassistant.domain.model.Message

/** Pure planning shared by the usage preview and the request builder. Coverage always refers to real message IDs. */
object ContextCompressionPlanner {
    data class Plan(
        val retainedMessages: List<Message>,
        val summarySource: List<Message>,
        val summaryThroughId: Long,
        val estimatedHistoryTokens: Int,
        val needsSummary: Boolean
    )

    fun messageTokens(message: Message): Int =
        TokenEstimationHelper.estimateTokenCount(message.content) + 24

    fun plan(
        messages: List<Message>,
        tier: CompressionTier,
        historyBudget: Int,
        summary: String?,
        coveredThroughId: Long?,
        recentRounds: Int = 8,
        retainPercent: Int = 30,
        forceCompression: Boolean = false
    ): Plan {
        if (messages.isEmpty()) return Plan(emptyList(), emptyList(), 0, 0, false)
        val turns = mutableListOf<MutableList<Message>>()
        for (message in messages) {
            if (message.role == "user" || turns.isEmpty()) turns += mutableListOf<Message>()
            turns.last() += message
        }
        val validCoverage = coveredThroughId?.takeIf { id ->
            !summary.isNullOrBlank() && messages.any { it.id == id }
        } ?: 0L
        val desiredMessages = when (tier) {
            CompressionTier.L0, CompressionTier.L1 -> if (forceCompression) 16 else messages.size
            CompressionTier.L2 -> recentRounds.coerceIn(4, 32) * 2
            CompressionTier.L3 -> 32
            CompressionTier.L4 -> 16
            CompressionTier.LC -> maxOf(2, (messages.size * retainPercent.coerceIn(10, 90) + 50) / 100)
        }
        var cut = 0
        var retainedCount = messages.size
        while (cut < turns.lastIndex && retainedCount - turns[cut].size >= desiredMessages) {
            retainedCount -= turns[cut].size
            cut++
        }
        val coveredTurnCount = turns.takeWhile { turn -> turn.all { it.id <= validCoverage } }.size.coerceAtMost(turns.lastIndex)
        if (!forceCompression && validCoverage > 0L) {
            // L0 grows its recent buffer until pressure returns. Other tiers merge in batches,
            // avoiding an additional billable summarization call after every short reply.
            cut = if (tier == CompressionTier.L0 || tier == CompressionTier.L1) coveredTurnCount
                else if (turns.take(cut).flatten().count { it.id > validCoverage } < 8) minOf(cut, coveredTurnCount)
                else cut
        }
        // A pinned message protects its complete turn; never inject an orphan reply.
        fun retainedAt(index: Int) = turns.flatMapIndexed { turnIndex, turn ->
            if (turnIndex >= index || turn.any { it.isPinned }) turn else emptyList()
        }
        while (cut < turns.lastIndex && retainedAt(cut).sumOf(::messageTokens) > historyBudget) cut++
        val source = turns.take(cut).flatten()
        val through = source.lastOrNull()?.id ?: 0L
        val needsSummary = through > 0 && (validCoverage == 0L || source.any { it.id > validCoverage })
        val planned = retainedAt(cut)
        return Plan(planned, source, through, planned.sumOf(::messageTokens), needsSummary)
    }

    /** Sequential bounded chunks; a large message is split without dropping the middle. */
    fun chunks(messages: List<Message>, tokenBudget: Int, maxMessages: Int = 40): List<List<Message>> {
        val budget = tokenBudget.coerceAtLeast(256)
        val fragments = messages.flatMap { message ->
            if (messageTokens(message) <= budget) listOf(message) else {
                val parts = mutableListOf<Message>()
                var remaining = message.content
                while (remaining.isNotEmpty()) {
                    var low = 1
                    var high = remaining.length
                    while (low < high) {
                        val mid = (low + high + 1) / 2
                        if (TokenEstimationHelper.estimateTokenCount(remaining.take(mid)) + 32 <= budget) low = mid else high = mid - 1
                    }
                    val boundary = remaining.take(low).indexOfLast { it == '\n' || it in "。！？；.!?" }
                    var length = if (boundary >= low / 2) boundary + 1 else low
                    // JSON transports UTF-8: keep a supplementary character together so each
                    // independent request cannot replace half of an emoji with a replacement glyph.
                    if (length < remaining.length && remaining[length - 1].isHighSurrogate() && remaining[length].isLowSurrogate()) {
                        length = if (length > 1) length - 1 else 2
                    }
                    parts += message.copy(content = remaining.take(length))
                    remaining = remaining.drop(length)
                }
                parts
            }
        }
        val result = mutableListOf<List<Message>>()
        var current = mutableListOf<Message>()
        var tokens = 0
        for (message in fragments) {
            val cost = messageTokens(message)
            if (current.isNotEmpty() && (tokens + cost > budget || current.size >= maxMessages)) {
                result += current
                current = mutableListOf()
                tokens = 0
            }
            current += message
            tokens += cost
        }
        if (current.isNotEmpty()) result += current
        return result
    }
}
