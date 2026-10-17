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

    /**
     * Stable transcript representation used by rolling-summary requests.
     *
     * Keeping the role and source id in one place prevents the request builder and
     * its budget calculator from disagreeing about what was actually sent.  The
     * default keeps every source message; callers may pass a limit only when they
     * have already proved that the omitted messages are represented elsewhere.
     */
    fun summaryTranscript(messages: List<Message>, maxMessages: Int = Int.MAX_VALUE): String =
        messages.take(maxMessages.coerceAtLeast(0)).joinToString("\n") { message ->
            val role = when (message.role.lowercase()) {
                "user" -> "用户"
                "assistant" -> "助手"
                else -> message.role
            }
            "[$role · 原文ID ${message.id}] ${message.content}"
        }

    /**
     * Conservative token estimate for the text sent to a summary endpoint.
     *
     * The regular estimate is intentionally cheap and works well for ordinary
     * request previews, but it can undercount CJK-heavy UTF-8 text.  Counting half
     * the encoded bytes (rounded up) provides a bounded safety floor without
     * requiring a provider-specific tokenizer.
     */
    fun summaryInputTokens(text: String): Int {
        if (text.isEmpty()) return 0
        val utf8Half = (text.toByteArray(Charsets.UTF_8).size + 1) / 2
        return maxOf(TokenEstimationHelper.estimateTokenCount(text), utf8Half)
    }

    /** Cost of one line in [summaryTranscript], including its separator allowance. */
    fun summaryMessageTokens(message: Message): Int =
        summaryInputTokens(summaryTranscript(listOf(message))) + 1

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
        // A regenerated reply has a newer ID but occupies its original turn. Coverage
        // therefore follows the selected history order, never numeric ID comparisons.
        val coveredIndex = if (!summary.isNullOrBlank() && coveredThroughId != null) {
            messages.indexOfFirst { it.id == coveredThroughId }
        } else -1
        var coveredMessageCount = 0
        val coveredTurnCount = turns.takeWhile { turn ->
            val completelyCovered = coveredMessageCount + turn.size <= coveredIndex + 1
            if (completelyCovered) coveredMessageCount += turn.size
            completelyCovered
        }.size
        var cut = when (tier) {
            CompressionTier.L0, CompressionTier.L1 -> 0
            CompressionTier.L2 -> (turns.size - recentRounds.coerceIn(4, 32)).coerceAtLeast(0)
            CompressionTier.L3 -> (turns.size - 4).coerceAtLeast(0)
            CompressionTier.L4 -> turns.lastIndex
            CompressionTier.LC -> {
                val desiredMessages = maxOf(2, (messages.size * retainPercent.coerceIn(10, 90) + 50) / 100)
                var candidate = 0
                var retainedCount = messages.size
                while (candidate < turns.lastIndex && retainedCount - turns[candidate].size >= desiredMessages) {
                    retainedCount -= turns[candidate].size
                    candidate++
                }
                candidate
            }
        }
        if (!forceCompression && coveredIndex >= 0 && cut > coveredTurnCount && cut - coveredTurnCount < 4) {
            // Extend summaries in whole-turn batches during ordinary sends. A shallower
            // tier can restore raw history immediately; existing coverage never limits it.
            cut = coveredTurnCount
        }
        val requestTurns = turns.mapIndexed { index, turn ->
            if (tier == CompressionTier.L1 && index < turns.lastIndex && turn.none { it.isPinned }) {
                turn.map { it.copy(content = ContentPruningHelper.pruneMessageContent(it.content)) }
            } else turn
        }
        val turnCosts = requestTurns.map { turn -> turn.sumOf(::messageTokens) }
        val suffixCosts = IntArray(turns.size + 1)
        val pinnedPrefixCosts = IntArray(turns.size + 1)
        for (index in turns.indices.reversed()) suffixCosts[index] = suffixCosts[index + 1] + turnCosts[index]
        for (index in turns.indices) {
            pinnedPrefixCosts[index + 1] = pinnedPrefixCosts[index] + if (turns[index].any { it.isPinned }) turnCosts[index] else 0
        }
        // A pinned message protects its complete turn; never inject an orphan reply.
        fun retainedAt(index: Int) = requestTurns.flatMapIndexed { turnIndex, turn ->
            if (turnIndex >= index || turn.any { it.isPinned }) turn else emptyList()
        }
        fun retainedCost(index: Int) = suffixCosts[index] + pinnedPrefixCosts[index]
        while (cut < turns.lastIndex && retainedCost(cut) > historyBudget) cut++
        // Raw retention and summary coverage are independent. Restoring a larger raw
        // window may overlap a checked summary, whose factual range must not regress.
        val retiredMessageCount = turns.take(cut).sumOf { it.size }
        val source = messages.take(maxOf(retiredMessageCount, coveredIndex + 1))
        val through = source.lastOrNull()?.id ?: 0L
        val needsSummary = source.isNotEmpty() && source.size > coveredIndex + 1
        val planned = retainedAt(cut)
        return Plan(planned, source, through, retainedCost(cut), needsSummary)
    }

    /** Sequential bounded chunks; a large message is split without dropping the middle. */
    fun chunks(
        messages: List<Message>,
        tokenBudget: Int,
        maxMessages: Int = 40,
        tokenCounter: (Message) -> Int = { message -> messageTokens(message) }
    ): List<List<Message>> {
        val budget = tokenBudget.coerceAtLeast(256)
        val fragments = messages.flatMap { message ->
            if (tokenCounter(message) <= budget) listOf(message) else {
                val parts = mutableListOf<Message>()
                var remaining = message.content
                while (remaining.isNotEmpty()) {
                    var low = 1
                    var high = remaining.length
                    while (low < high) {
                        val mid = (low + high + 1) / 2
                        if (tokenCounter(message.copy(content = remaining.take(mid))) <= budget) low = mid else high = mid - 1
                    }
                    val boundary = remaining.take(low).indexOfLast { it == '\n' || it in "。！？；.!?" }
                    var length = if (boundary >= low / 2) boundary + 1 else low
                    // JSON transports UTF-8: keep a supplementary character together so each
                    // independent request cannot replace half of an emoji with a replacement glyph.
                    if (length < remaining.length && remaining[length - 1].isHighSurrogate() && remaining[length].isLowSurrogate()) {
                        length = if (length > 1) length - 1 else 2
                    }
                    // A custom counter may include a larger transcript wrapper than
                    // the default estimate.  Ensure progress without ever emitting
                    // an empty fragment if even one source character is over budget.
                    if (length <= 0) length = 1
                    while (length > 1 && tokenCounter(message.copy(content = remaining.take(length))) > budget) {
                        length--
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
            val cost = tokenCounter(message)
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
