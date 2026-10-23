package com.aiassistant.data.repository.helpers

import com.aiassistant.domain.model.ChatMessage
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.WorldBookEntry

/**
 * 活跃上下文消息装填与解析结果数据载体
 */
data class ActiveContextResolution(
    val activeMessages: List<Message>,
    val recentTokens: Int,
    val uncompressedOlderCount: Int,
    val canCompress: Boolean,
    val lastOlderMessageId: Long?
)

/**
 * 角色扮演、世界书与会话上下文装配辅助
 */
object ChatContextAssemblyHelper {

    const val UNCOMPRESSED_RECENT_MESSAGE_COUNT = 16

    fun formatWorldBookPrompt(entries: List<WorldBookEntry>): String {
        if (entries.isEmpty()) return ""
        val lines = entries.map { entry ->
            "- 【${entry.name}】${entry.content.trim()}"
        }
        return "【世界书设定】\n" + lines.joinToString("\n")
    }

    fun isErrorPlaceholderMessage(content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        val trimmed = content.trim()
        return trimmed == "生成已停止" ||
            trimmed.startsWith("生成已停止\n\n【连接异常信息记录】") ||
            trimmed == "回复已停止 (用户已暂停)" ||
            trimmed.startsWith("回复已停止 (用户已暂停)\n\n【连接异常信息记录】") ||
            trimmed == "*(思考已停止，回复已暂停)*" ||
            trimmed.startsWith("*(思考已停止，回复已暂停)*\n\n【连接异常信息记录】") ||
            trimmed.startsWith("请求失败\n\n") ||
            trimmed.startsWith("请求失败\n") ||
            trimmed.startsWith("[输出已被中断:") ||
            trimmed.contains("[输出已被中断:") ||
            trimmed.contains("failed to stream request: empty response detected", ignoreCase = true)
    }

    /** 连接诊断只供用户查看；已输出的部分回复可以继续作为历史，诊断不能进入模型上下文。 */
    fun withoutStoppedConnectionReport(content: String): String {
        val prefix = content.substringBefore("\n\n【连接异常信息记录】")
        if (prefix == content) return content
        val stopped = prefix.trim().let { body ->
            body == "生成已停止" || body == "回复已停止 (用户已暂停)" ||
                body.substringAfterLast("\n\n") in setOf("*(生成已停止)*", "*(回复已被暂停)*", "*(思考已停止，回复已暂停)*")
        }
        return if (stopped) prefix else content
    }

    fun isRoleplayConversation(conversation: Conversation?): Boolean {
        if (conversation == null) return false
        return hasConversationTag(conversation, "roleplay") ||
            hasConversationTag(conversation, "story") ||
            conversation.systemPrompt?.contains("Role Definition", ignoreCase = true) == true ||
            conversation.systemPrompt?.contains("角色扮演", ignoreCase = true) == true ||
            conversation.systemPrompt?.contains("剧情扮演", ignoreCase = true) == true
    }

    fun formatRoleplaySystemPrompt(
        customPrompt: String?,
        memoryBlock: String? = null,
        worldBookBlock: String? = null
    ): String? {
        return listOfNotNull(
            customPrompt?.takeIf { it.isNotBlank() },
            memoryBlock,
            worldBookBlock
        ).joinToString("\n\n").ifBlank { null }
    }

    fun hasConversationTag(conversation: Conversation?, tag: String): Boolean {
        return conversation?.tags
            ?.split(',', ';', '|', ' ')
            ?.map { it.trim() }
            ?.any { it.equals(tag, ignoreCase = true) } == true
    }

    fun normalizeChatMessagesRoleAlternation(rawMessages: List<ChatMessage>): List<ChatMessage> {
        if (rawMessages.isEmpty()) return emptyList()

        val result = mutableListOf<ChatMessage>()
        var index = 0
        // 1. 保留开头的 system 消息（若有），合并为唯一的规范首位 system 消息
        val systemContents = mutableListOf<String>()
        while (index < rawMessages.size && rawMessages[index].role == "system") {
            val sysContent = rawMessages[index].content.toString().trim()
            if (sysContent.isNotBlank()) {
                systemContents.add(sysContent)
            }
            index++
        }
        if (systemContents.isNotEmpty()) {
            result.add(ChatMessage(role = "system", content = systemContents.joinToString("\n\n")))
        }

        // 2. 严格以 user 角色作为非 system 首位：丢弃后续紧跟的孤立 assistant 消息
        while (index < rawMessages.size && rawMessages[index].role == "assistant") {
            index++
        }

        // 3. 严格交替装填，若相邻角色相同则安全合并 content
        for (i in index until rawMessages.size) {
            val current = rawMessages[i]
            if (current.content is String && (current.content as String).isBlank()) {
                continue
            }

            val last = result.lastOrNull()
            if (last != null && last.role != "system" && last.role == current.role) {
                if (last.content is String && current.content is String) {
                    result[result.lastIndex] = last.copy(content = "${last.content}\n\n${current.content}")
                } else {
                    result[result.lastIndex] = current
                }
            } else {
                result.add(current)
            }
        }

        // 4. 确保最后一条消息是 user 角色（若末尾残留孤立 assistant，安全移除）
        while (result.size > 1 && result.last().role == "assistant") {
            result.removeAt(result.lastIndex)
        }

        return result
    }

    fun resolveActiveContextMessages(
        usableMessages: List<Message>,
        compressedThrough: Long,
        recentBudget: Int
    ): ActiveContextResolution {
        if (usableMessages.isEmpty()) {
            return ActiveContextResolution(
                activeMessages = emptyList(),
                recentTokens = 0,
                uncompressedOlderCount = 0,
                canCompress = false,
                lastOlderMessageId = null
            )
        }

        if (usableMessages.size <= UNCOMPRESSED_RECENT_MESSAGE_COUNT) {
            var tokens = 0
            for (msg in usableMessages) {
                tokens += TokenEstimationHelper.estimateTokenCount(TokenEstimationHelper.compactMessageForHistory(msg.content)) + 24
            }
            return ActiveContextResolution(
                activeMessages = usableMessages,
                recentTokens = tokens,
                uncompressedOlderCount = 0,
                canCompress = false,
                lastOlderMessageId = null
            )
        }

        val olderMessages = usableMessages.dropLast(UNCOMPRESSED_RECENT_MESSAGE_COUNT)
        val recentWindow = usableMessages.takeLast(UNCOMPRESSED_RECENT_MESSAGE_COUNT)
        val recentWindowIds = recentWindow.map { it.id }.toSet()
        val lastOlderMessageId = olderMessages.lastOrNull()?.id

        // 尚未被压缩水线覆盖的较早历史消息
        val uncompressedOlderMessages = if (compressedThrough <= 0L) {
            olderMessages
        } else {
            olderMessages.filter { it.id > compressedThrough }
        }
        val uncompressedOlderCount = uncompressedOlderMessages.size

        // 只要存在超出最近16条窗口且未被水线覆盖的早期消息，即支持压缩
        val canCompress = uncompressedOlderCount > 0 && lastOlderMessageId != null &&
            (compressedThrough <= 0L || compressedThrough < lastOlderMessageId)

        // 活跃消息装填规则：
        // 1. 最近16条消息（recentWindow）无条件无损保留；
        // 2. 用户置顶消息（isPinned）无条件保留；
        // 3. 尚未被压缩水线覆盖的早期历史消息（it.id > compressedThrough），在 recentBudget 预算内按时间倒序尽可能装填；
        // 4. 已被压缩水线覆盖且未置顶的早期历史消息（it.id <= compressedThrough），已由 memoryBlock 承载，安全退休。
        val candidateMessages = usableMessages.filter { msg ->
            msg.isPinned || recentWindowIds.contains(msg.id) || (compressedThrough <= 0L || msg.id > compressedThrough)
        }

        var usedTokens = 0
        val activeReversed = mutableListOf<Message>()

        for (message in candidateMessages.asReversed()) {
            val compact = TokenEstimationHelper.compactMessageForHistory(message.content)
            val cost = TokenEstimationHelper.estimateTokenCount(compact) + 24

            val isInRecentWindow = recentWindowIds.contains(message.id)
            if (!isInRecentWindow && !message.isPinned && usedTokens + cost > recentBudget) {
                continue
            }

            activeReversed.add(message)
            usedTokens += cost
        }

        val activeMessages = activeReversed.asReversed()

        return ActiveContextResolution(
            activeMessages = activeMessages,
            recentTokens = usedTokens,
            uncompressedOlderCount = uncompressedOlderCount,
            canCompress = canCompress,
            lastOlderMessageId = lastOlderMessageId
        )
    }

    /** Compatibility entry point; summary tiers retire history only after confirmed coverage. */
    fun assembleTieredContextMessages(
        tier: com.aiassistant.domain.model.CompressionTier,
        usableMessages: List<Message>,
        recentBudget: Int,
        l2RecentRounds: Int = com.aiassistant.domain.model.CompressionTierPolicy.DEFAULT_L2_RECENT_ROUNDS,
        existingRollingSummary: String? = null,
        structuredSummary: String? = null,
        customRetainPercent: Int = com.aiassistant.domain.model.CompressionTierPolicy.DEFAULT_CUSTOM_RETAIN_PERCENT,
        coveredThroughId: Long? = null
    ): TieredContextResult {
        if (usableMessages.isEmpty()) return TieredContextResult(emptyList(), null, 0, 0, false)
        val injectedSummary = when (tier) {
            com.aiassistant.domain.model.CompressionTier.LT -> null
            com.aiassistant.domain.model.CompressionTier.L3 ->
                structuredSummary?.takeIf { it.isNotBlank() } ?: existingRollingSummary?.takeIf { it.isNotBlank() }
            else -> existingRollingSummary?.takeIf { it.isNotBlank() } ?: structuredSummary?.takeIf { it.isNotBlank() }
        }
        val plan = ContextCompressionPlanner.plan(
            messages = usableMessages,
            tier = tier,
            historyBudget = recentBudget,
            summary = injectedSummary,
            coveredThroughId = coveredThroughId,
            recentRounds = l2RecentRounds,
            retainPercent = customRetainPercent
        )
        // This synchronous adapter cannot generate a checked replacement summary. Its
        // caller must first summarize the uncovered range; until then keep the source.
        val activeMessages = if (plan.needsSummary) usableMessages else plan.retainedMessages
        val coveredIndex = if (injectedSummary != null && coveredThroughId != null) {
            usableMessages.indexOfFirst { it.id == coveredThroughId }
        } else -1
        val pendingCount = (plan.summarySource.size - coveredIndex - 1).coerceAtLeast(0)
        val summaryTokens = injectedSummary?.let { TokenEstimationHelper.estimateTokenCount(it) + 32 } ?: 0
        return TieredContextResult(
            activeMessages = activeMessages,
            injectedSummary = injectedSummary,
            recentTokens = activeMessages.sumOf(ContextCompressionPlanner::messageTokens) + summaryTokens,
            uncompressedOlderCount = pendingCount,
            canCompress = plan.needsSummary
        )
    }

}

/**
 * 分档组装解析结果载体
 */
data class TieredContextResult(
    val activeMessages: List<Message>,
    val injectedSummary: String? = null,
    val recentTokens: Int = 0,
    val uncompressedOlderCount: Int = 0,
    val canCompress: Boolean = false
)

