package com.aiassistant.domain.model

import com.aiassistant.data.repository.helpers.TokenEstimationHelper

/** One deterministic activation policy for chat, story and the activation preview. */
object WorldBookActivation {
    const val DEFAULT_TOKEN_BUDGET = 1500
    const val SCAN_MESSAGE_COUNT = 4
    private const val SCAN_CHARS_PER_MESSAGE = 4000

    enum class Reason { CONSTANT, KEYWORD, DISABLED, NO_MATCH, EMPTY, OVER_BUDGET }
    data class Decision(val entry: WorldBookEntry, val reason: Reason, val matchedKey: String? = null)
    data class Result(val entries: List<WorldBookEntry>, val decisions: List<Decision>, val estimatedTokens: Int)

    fun latestVariants(messages: List<Message>): List<Message> {
        val groups = messages.filter { !it.variantGroupId.isNullOrBlank() }.groupBy { it.variantGroupId!! }
        val consumed = mutableSetOf<String>()
        return messages.mapNotNull { message ->
            val group = message.variantGroupId
            if (group.isNullOrBlank()) message
            else if (!consumed.add(group)) null
            else groups.getValue(group).maxWithOrNull(compareBy<Message> { it.variantIndex }.thenBy { it.createdAt })
        }
    }

    fun scanText(currentMessage: String, recentMessages: List<Message> = emptyList()): String =
        (latestVariants(recentMessages).filter { !it.isExcluded && (it.role == "user" || it.role == "assistant") &&
            !com.aiassistant.data.repository.helpers.ChatContextAssemblyHelper.isErrorPlaceholderMessage(it.content) }
            .takeLast(SCAN_MESSAGE_COUNT).map { it.content.takeLast(SCAN_CHARS_PER_MESSAGE) } + currentMessage)
            .joinToString("\n")

    fun select(entries: List<WorldBookEntry>, text: String, tokenBudget: Int = DEFAULT_TOKEN_BUDGET): Result {
        val selected = mutableListOf<WorldBookEntry>()
        val decisions = mutableListOf<Decision>()
        var used = 0
        val lowerText = text.lowercase(java.util.Locale.ROOT)
        // Priority chooses admission under a budget; it does not change the model's message role.
        val sorted = entries.sortedWith(compareByDescending<WorldBookEntry> { it.isConstant }
            .thenByDescending { it.priority }.thenBy { it.bookId }.thenBy { it.id }.thenBy { it.name })
        for (entry in sorted) {
            val key = entry.getKeyList().firstOrNull { lowerText.contains(it) }
            val reason = when {
                !entry.isEnabled -> Reason.DISABLED
                entry.content.isBlank() -> Reason.EMPTY
                !entry.isConstant && key == null -> Reason.NO_MATCH
                else -> null
            }
            if (reason != null) {
                decisions += Decision(entry, reason, key)
                continue
            }
            val cost = TokenEstimationHelper.estimateTokenCount("- 【${entry.name}】${entry.content.trim()}") + 4
            // Reserve the shared title and the transport wrapper used by both chat protocols.
            val headerCost = if (selected.isEmpty()) TokenEstimationHelper.estimateTokenCount("<world_book_lore>\n【世界书设定】\n</world_book_lore>") else 0
            if (used + cost + headerCost > tokenBudget.coerceAtLeast(0)) {
                decisions += Decision(entry, Reason.OVER_BUDGET, key)
                continue // An oversized entry must not prevent a smaller matching entry from fitting.
            }
            selected += entry
            used += cost + headerCost
            decisions += Decision(entry, if (entry.isConstant) Reason.CONSTANT else Reason.KEYWORD, key)
        }
        return Result(selected, decisions, used)
    }
}
