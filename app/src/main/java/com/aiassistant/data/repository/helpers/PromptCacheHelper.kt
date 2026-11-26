package com.aiassistant.data.repository.helpers

import com.aiassistant.domain.model.AnthropicCacheControl
import com.aiassistant.domain.model.AnthropicContent
import com.aiassistant.domain.model.AnthropicMessage
import com.aiassistant.domain.model.ContentPart
import com.aiassistant.domain.model.ModelVendor
import com.aiassistant.domain.model.ModelVendorProfiles
import com.aiassistant.domain.model.WorldBookEntry
import com.aiassistant.utils.EchoPromptCacheTtl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.util.Locale

/**
 * 提示词前缀缓存核心策略与诊断引擎。
 *
 * 负责：
 * 1. Anthropic Messages API 三断点滑动缓存标记（system 静态前缀 + 历史锚点 + 当前稳定用户输入）；
 * 2. OpenAI / OpenRouter / 兼容网关的会话粘性路由键与厂商安全字段过滤；
 * 3. 三区稳定性拆分（静态前缀 Zone 1、追加历史 Zone 2、本轮易变尾注 Zone 3）；
 * 4. 阶梯滞后截断预算，避免每轮滑动 1 条导致 KV Cache 连续击穿；
 * 5. 会话级前缀哈希与缓存未命中归因诊断。
 */
object PromptCacheHelper {

    const val OVERFLOW_HYSTERESIS_KEEP_RATIO = 0.80f
    const val EXTENDED_TTL_BETA_HEADER = "extended-cache-ttl-2025-04-11"
    const val MAX_ANTHROPIC_BREAKPOINTS = 4

    data class AnthropicCachedPayload(
        val system: Any?,
        val messages: List<AnthropicMessage>,
        val breakpointCount: Int,
        val extraHeaders: Map<String, String> = emptyMap()
    )

    data class OpenAiCacheHints(
        val promptCacheKey: String? = null,
        val sessionId: String? = null,
        val cacheControl: AnthropicCacheControl? = null,
        val extraHeaders: Map<String, String> = emptyMap()
    )

    data class WorldBookZoneSplit(
        val constantBlock: String?,
        val dynamicBlock: String?
    )

    enum class PromptCacheStatus {
        COLD_START,
        HIT,
        MISS_MODEL_CHANGED,
        MISS_SYSTEM_CHANGED,
        MISS_HISTORY_REWRITTEN,
        MISS_UPSTREAM_NO_CACHE
    }

    data class PromptCacheSnapshot(
        val conversationId: Long,
        val protocol: String,
        val modelName: String,
        val systemPrefixHash: String,
        val historyPrefixHash: String,
        val historyMessageCount: Int,
        val cacheBreakpointsPlaced: Int,
        val inputTokens: Int,
        val cacheReadTokens: Int,
        val cacheCreationTokens: Int,
        val hitRate: Float,
        val status: PromptCacheStatus,
        val timestamp: Long = System.currentTimeMillis()
    )

    /** 5m 为 Anthropic 默认 TTL，不输出 ttl 字段；仅 1h 显式携带 ttl="1h"。 */
    fun cacheControlForTtl(ttl: String?): AnthropicCacheControl {
        val sanitized = EchoPromptCacheTtl.sanitize(ttl)
        return if (sanitized == EchoPromptCacheTtl.ONE_HOUR) {
            AnthropicCacheControl(type = "ephemeral", ttl = EchoPromptCacheTtl.ONE_HOUR)
        } else {
            AnthropicCacheControl(type = "ephemeral", ttl = null)
        }
    }

    fun sessionRoutingKey(conversationId: Long): String = "echo-conv-$conversationId"

    /**
     * 计算 OpenAI 兼容协议的缓存路由与提示字段。
     * 严格按 Host / Vendor 白名单发送非标准顶层 JSON 字段，避免触发第三方严格网关 HTTP 400。
     */
    fun resolveOpenAiCacheHints(
        baseUrl: String,
        provider: String,
        modelName: String,
        conversationId: Long,
        enabled: Boolean,
        ttl: String? = null
    ): OpenAiCacheHints {
        if (!enabled || conversationId <= 0L) return OpenAiCacheHints()
        val routingKey = sessionRoutingKey(conversationId)
        val host = extractHost(baseUrl)
        val isOpenAiOfficial = host == "api.openai.com" || host?.endsWith(".openai.com") == true
        val isOpenRouter = host == "openrouter.ai" || host?.endsWith(".openrouter.ai") == true
        val vendor = ModelVendorProfiles.detectVendor(modelName, provider, baseUrl)

        return OpenAiCacheHints(
            promptCacheKey = if (isOpenAiOfficial || (isOpenRouter && vendor == ModelVendor.GPT)) routingKey else null,
            sessionId = if (isOpenRouter) routingKey else null,
            cacheControl = if (isOpenRouter && vendor == ModelVendor.CLAUDE) cacheControlForTtl(ttl) else null,
            extraHeaders = mapOf("X-Session-ID" to routingKey)
        )
    }

    /**
     * 为 Anthropic Messages API 放置滑动缓存断点（最多 4 个，常规对话使用 3 个）：
     * - BP1: 静态 system 前缀块；
     * - BP2: 历史锚点（最后一个历史 assistant，回退为当前轮之前最后一条可缓存历史消息）；
     * - BP3: 当前 user 稳定内容尾部（位于本轮易变 `<turn_context>` 之前，保证下一轮精确命中）；
     * - BP4（可选）: 当 [cacheTailVolatileBlock] 为 true（如回复方向规划）时标记尾部易变块，
     *   使紧随其后的正式回复连同本轮检索参考一并命中缓存。
     */
    fun applyAnthropicCacheBreakpoints(
        systemPrompt: String?,
        messages: List<AnthropicMessage>,
        conversationId: Long,
        enabled: Boolean,
        ttl: String? = null,
        tailVolatileBlock: String? = null,
        tailInstruction: String? = null,
        cacheTailVolatileBlock: Boolean = false
    ): AnthropicCachedPayload {
        val cleanTail = tailVolatileBlock?.trim()?.takeIf { it.isNotEmpty() }
        val cleanInstruction = tailInstruction?.trim()?.takeIf { it.isNotEmpty() }
        val combinedPlainTail = listOfNotNull(cleanTail, cleanInstruction).joinToString("\n\n").ifBlank { null }
        if (!enabled) {
            val plainMessages = if (combinedPlainTail != null && messages.isNotEmpty()) {
                val updated = messages.toMutableList()
                updated[updated.lastIndex] = appendTailToPlainAnthropicMessage(updated.last(), combinedPlainTail)
                updated
            } else {
                messages
            }
            return AnthropicCachedPayload(
                system = systemPrompt,
                messages = plainMessages,
                breakpointCount = 0,
                extraHeaders = emptyMap()
            )
        }

        val cc = cacheControlForTtl(ttl)
        var breakpoints = 0

        // 1. 静态 system 前缀 -> BP1
        val systemPayload: Any? = systemPrompt?.takeIf { it.isNotBlank() }?.let { text ->
            breakpoints++
            listOf(AnthropicContent(type = "text", text = text, cache_control = cc))
        }

        // 2. 先清除历史残余 cache_control，确保断点总数严格 <= 4
        val mutableMessages = messages.map(::stripMessageCacheControl).toMutableList()

        // 3. 历史锚点 -> BP2（优先最后一个历史 assistant，否则当前用户消息之前最后一条可缓存消息）
        if (mutableMessages.size >= 2 && breakpoints < MAX_ANTHROPIC_BREAKPOINTS) {
            val historyEnd = mutableMessages.lastIndex - 1
            val anchorIdx = (historyEnd downTo 0).firstOrNull { idx ->
                mutableMessages[idx].role == "assistant" && isCacheableMessage(mutableMessages[idx])
            } ?: (historyEnd downTo 0).firstOrNull { idx ->
                isCacheableMessage(mutableMessages[idx])
            }
            if (anchorIdx != null) {
                val marked = markLastCacheableBlock(mutableMessages[anchorIdx], cc)
                if (marked != null) {
                    mutableMessages[anchorIdx] = marked
                    breakpoints++
                }
            }
        }

        // 4. 当前轮稳定内容 -> BP3，随后依次追加易变检索尾注（可选 BP4）与单轮指令（永不缓存）
        if (mutableMessages.isNotEmpty()) {
            val lastIdx = mutableMessages.lastIndex
            var lastMsg = mutableMessages[lastIdx]
            if (breakpoints < MAX_ANTHROPIC_BREAKPOINTS) {
                val marked = markLastCacheableBlock(lastMsg, cc)
                if (marked != null) {
                    lastMsg = marked
                    breakpoints++
                }
            }
            if (cleanTail != null) {
                val tailCc = if (cacheTailVolatileBlock && breakpoints < MAX_ANTHROPIC_BREAKPOINTS) {
                    breakpoints++
                    cc
                } else null
                lastMsg = appendBlockToAnthropicMessage(
                    lastMsg,
                    AnthropicContent(type = "text", text = cleanTail, cache_control = tailCc)
                )
            }
            if (cleanInstruction != null) {
                lastMsg = appendBlockToAnthropicMessage(
                    lastMsg,
                    AnthropicContent(type = "text", text = cleanInstruction, cache_control = null)
                )
            }
            mutableMessages[lastIdx] = lastMsg
        }

        val headers = linkedMapOf<String, String>()
        if (conversationId > 0L) {
            headers["X-Session-ID"] = sessionRoutingKey(conversationId)
        }
        if (cc.ttl == EchoPromptCacheTtl.ONE_HOUR) {
            headers["anthropic-beta"] = EXTENDED_TTL_BETA_HEADER
        }

        return AnthropicCachedPayload(
            system = systemPayload,
            messages = mutableMessages,
            breakpointCount = breakpoints,
            extraHeaders = headers
        )
    }

    /** 将世界书命中条目按稳定性拆分为：常驻条目（Zone 1 静态前缀）与关键词动态触发条目（Zone 3 尾注）。 */
    fun splitWorldBookEntries(entries: List<WorldBookEntry>): WorldBookZoneSplit {
        if (entries.isEmpty()) return WorldBookZoneSplit(null, null)
        val (constantEntries, dynamicEntries) = entries.partition { it.isConstant }
        val constantBlock = constantEntries.takeIf { it.isNotEmpty() }?.let {
            "<world_book_lore>\n${ChatContextAssemblyHelper.formatWorldBookPrompt(it)}\n</world_book_lore>"
        }
        val dynamicBlock = dynamicEntries.takeIf { it.isNotEmpty() }?.let {
            "<world_book_lore>\n${ChatContextAssemblyHelper.formatWorldBookPrompt(it)}\n</world_book_lore>"
        }
        return WorldBookZoneSplit(constantBlock, dynamicBlock)
    }

    /** 构造 Zone 3 本轮动态尾注块；无内容时返回 null。 */
    fun buildTurnContextTail(
        dynamicMemoryBlock: String? = null,
        dynamicWorldBookBlock: String? = null,
        replyDirection: String? = null,
        planningInstruction: String? = null
    ): String? {
        val sections = mutableListOf<String>()
        val dynamicRefs = listOfNotNull(
            dynamicMemoryBlock?.trim()?.takeIf { it.isNotEmpty() },
            dynamicWorldBookBlock?.trim()?.takeIf { it.isNotEmpty() }
        )
        if (dynamicRefs.isNotEmpty()) {
            sections += "<turn_context>\n本轮动态检索参考（仅用于辅助本轮回复，不覆盖系统设定与既往历史事实）：\n${dynamicRefs.joinToString("\n\n")}\n</turn_context>"
        }
        replyDirection?.trim()?.takeIf { it.isNotEmpty() }?.let { sections += it }
        planningInstruction?.trim()?.takeIf { it.isNotEmpty() }?.let { sections += it }
        return sections.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    /** 将 Zone 3 尾注安全附加到 OpenAI 当前 user 消息末尾，兼容纯文本与多模态 ContentPart 列表。 */
    fun appendTailToOpenAiUserContent(userContent: Any, tailVolatileBlock: String?): Any {
        val cleanTail = tailVolatileBlock?.trim()?.takeIf { it.isNotEmpty() } ?: return userContent
        return when (userContent) {
            is String -> if (userContent.isBlank()) cleanTail else "$userContent\n\n$cleanTail"
            is List<*> -> {
                val parts = userContent.filterIsInstance<ContentPart>().toMutableList()
                parts.add(ContentPart(type = "text", text = cleanTail))
                parts
            }
            else -> userContent
        }
    }

    /**
     * 阶梯滞后截断预算：仅在原文超出 [historyBudget] 时腾出 20% 追加跑道，
     * 使后续多轮对话以纯追加形式生长，避免每轮向前滑动 1 轮反复击穿前缀缓存。
     */
    fun overflowHysteresisTarget(historyBudget: Int): Int {
        if (historyBudget <= 0) return 0
        return (historyBudget * OVERFLOW_HYSTERESIS_KEEP_RATIO).toInt().coerceAtLeast(1)
    }

    /**
     * 阶梯式窗口起点计算（RikkaHub 50% 步进公式）：
     * 仅在累计消息越过步进门槛时批量前移窗口起点，保持多轮前缀完全一致。
     */
    fun computeSteppedKeepStart(
        totalMessages: Int,
        maxKeepMessages: Int,
        keepRatio: Float = 0.5f
    ): Int {
        if (maxKeepMessages <= 0 || totalMessages <= maxKeepMessages) return 0
        val keepCount = (maxKeepMessages * keepRatio.coerceIn(0.1f, 0.9f)).toInt().coerceAtLeast(1)
        val stride = (maxKeepMessages - keepCount).coerceAtLeast(1)
        val overflow = totalMessages - maxKeepMessages
        val drops = ((overflow - 1) / stride) + 1
        return (drops * stride).coerceAtMost((totalMessages - keepCount).coerceAtLeast(0))
    }

    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun computeHistoryHash(messages: List<Pair<String, String>>): String {
        if (messages.isEmpty()) return "empty"
        val joined = messages.joinToString("\u001E") { (role, content) -> "$role\u001F$content" }
        return sha256Hex(joined)
    }

    /** 基于上一轮快照与本轮上游返回的缓存指标，归因本次请求命中或未命中的原因。 */
    fun evaluateDiagnostics(
        previous: PromptCacheSnapshot?,
        conversationId: Long,
        protocol: String,
        modelName: String,
        systemPrefixHash: String,
        historyPrefixHash: String,
        previousHistoryPrefixHash: String,
        historyMessageCount: Int,
        cacheBreakpointsPlaced: Int,
        inputTokens: Int,
        cacheReadTokens: Int,
        cacheCreationTokens: Int
    ): PromptCacheSnapshot {
        val safeInput = inputTokens.coerceAtLeast(0)
        val safeRead = cacheReadTokens.coerceIn(0, safeInput)
        val safeCreate = cacheCreationTokens.coerceAtLeast(0)
        val hitRate = if (safeInput > 0) (safeRead.toFloat() / safeInput).coerceIn(0f, 1f) else 0f

        val status = when {
            safeRead > 0 -> PromptCacheStatus.HIT
            previous == null -> PromptCacheStatus.COLD_START
            previous.modelName != modelName || previous.protocol != protocol -> PromptCacheStatus.MISS_MODEL_CHANGED
            previous.systemPrefixHash != systemPrefixHash -> PromptCacheStatus.MISS_SYSTEM_CHANGED
            historyMessageCount < previous.historyMessageCount ||
                (previous.historyMessageCount > 0 && previous.historyPrefixHash != previousHistoryPrefixHash) ->
                PromptCacheStatus.MISS_HISTORY_REWRITTEN
            else -> PromptCacheStatus.MISS_UPSTREAM_NO_CACHE
        }

        return PromptCacheSnapshot(
            conversationId = conversationId,
            protocol = protocol,
            modelName = modelName,
            systemPrefixHash = systemPrefixHash,
            historyPrefixHash = historyPrefixHash,
            historyMessageCount = historyMessageCount,
            cacheBreakpointsPlaced = cacheBreakpointsPlaced,
            inputTokens = safeInput,
            cacheReadTokens = safeRead,
            cacheCreationTokens = safeCreate,
            hitRate = hitRate,
            status = status
        )
    }

    private fun extractHost(rawBaseUrl: String): String? {
        val trimmed = rawBaseUrl.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        return withScheme.toHttpUrlOrNull()?.host?.lowercase(Locale.ROOT)
    }

    private fun stripMessageCacheControl(message: AnthropicMessage): AnthropicMessage {
        val content = message.content
        if (content !is List<*>) return message
        val cleaned = content.map { part ->
            if (part is AnthropicContent && part.cache_control != null) part.copy(cache_control = null) else part
        }
        return message.copy(content = cleaned)
    }

    private fun isCacheableBlock(block: AnthropicContent): Boolean = when (block.type) {
        "text" -> !block.text.isNullOrBlank()
        "image", "document" -> block.source != null
        else -> false
    }

    private fun isCacheableMessage(message: AnthropicMessage): Boolean = when (val content = message.content) {
        is String -> content.isNotBlank()
        is List<*> -> content.filterIsInstance<AnthropicContent>().any(::isCacheableBlock)
        else -> false
    }

    private fun markLastCacheableBlock(
        message: AnthropicMessage,
        cacheControl: AnthropicCacheControl
    ): AnthropicMessage? = when (val content = message.content) {
        is String -> {
            if (content.isBlank()) null
            else message.copy(content = listOf(AnthropicContent(type = "text", text = content, cache_control = cacheControl)))
        }
        is List<*> -> {
            val parts = content.filterIsInstance<AnthropicContent>().toMutableList()
            val targetIdx = parts.indexOfLast(::isCacheableBlock)
            if (targetIdx < 0) null
            else {
                parts[targetIdx] = parts[targetIdx].copy(cache_control = cacheControl)
                message.copy(content = parts)
            }
        }
        else -> null
    }

    private fun appendBlockToAnthropicMessage(
        message: AnthropicMessage,
        block: AnthropicContent
    ): AnthropicMessage = when (val content = message.content) {
        is String -> {
            val parts = mutableListOf<AnthropicContent>()
            if (content.isNotBlank()) {
                parts += AnthropicContent(type = "text", text = content)
            }
            parts += block
            message.copy(content = parts)
        }
        is List<*> -> {
            val parts = content.filterIsInstance<AnthropicContent>().toMutableList()
            parts += block
            message.copy(content = parts)
        }
        else -> message
    }

    private fun appendTailToPlainAnthropicMessage(
        message: AnthropicMessage,
        cleanTail: String
    ): AnthropicMessage = when (val content = message.content) {
        is String -> message.copy(content = if (content.isBlank()) cleanTail else "$content\n\n$cleanTail")
        is List<*> -> appendBlockToAnthropicMessage(message, AnthropicContent(type = "text", text = cleanTail))
        else -> message
    }
}
