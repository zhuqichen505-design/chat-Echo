package com.aiassistant.utils

import com.aiassistant.data.repository.helpers.TokenEstimationHelper
import com.aiassistant.domain.model.ConversationSummaryChunk
import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.TimelineNode
import com.google.gson.JsonParser
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

/**
 * 跨会话长期记忆结构化分类。
 * - 静态核心画像（IDENTITY / VOICE / INSTRUCTION）驻留 Zone 1 前缀缓存区，不随本轮问题重排，永不衰减；
 * - 动态背景记忆（PREFERENCE / PROJECT / FACT）按本轮提问混合召回注入 Zone 3 尾注，零缓存击穿。
 */
enum class EchoMemoryCategory(
    val key: String,
    val displayName: String,
    val isStaticCore: Boolean,
    val defaultImportance: Int,
    val halfLifeDays: Double?,
    val decayFloor: Float,
    val zoneOrder: Int
) {
    INSTRUCTION("INSTRUCTION", "行为约束", true, 5, null, 1.0f, 0),
    VOICE("VOICE", "回复语气", true, 4, null, 1.0f, 1),
    IDENTITY("IDENTITY", "身份画像", true, 4, null, 1.0f, 2),
    PREFERENCE("PREFERENCE", "长期偏好", false, 4, 90.0, 0.45f, 3),
    PROJECT("PROJECT", "项目目标", false, 3, 30.0, 0.25f, 4),
    FACT("FACT", "背景事实", false, 3, 90.0, 0.40f, 5);

    companion object {
        fun fromKey(raw: String?): EchoMemoryCategory {
            val normalized = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull { it.key == normalized } ?: when (normalized) {
                "CONSTRAINT", "RULE", "RULE_CONSTRAINT" -> INSTRUCTION
                "STYLE", "TONE" -> VOICE
                "PROFILE", "USER", "PERSONA" -> IDENTITY
                "WORKFLOW", "HABIT" -> PREFERENCE
                else -> FACT
            }
        }

        fun inferFromContent(content: String): Pair<EchoMemoryCategory, Int> {
            val lower = content.trim().lowercase(Locale.ROOT)
            if (lower.isBlank()) return FACT to 3
            val instructionMarkers = listOf(
                "行为约束", "必须", "严禁", "禁止", "绝对不要", "千万不要", "不要叫", "别叫",
                "不准", "不得", "不许", "禁忌", "雷区", "格式要求", "规则", "规范", "约束"
            )
            if (instructionMarkers.any { lower.contains(it) }) return INSTRUCTION to 5

            val voiceMarkers = listOf(
                "语气", "口吻", "文风", "回复风格", "回答风格", "简洁", "简短回答",
                "直截了当", "少说客套", "不要客套", "温柔", "幽默", "客观中立", "表达方式"
            )
            if (voiceMarkers.any { lower.contains(it) }) return VOICE to 4

            val identityMarkers = listOf(
                "称呼我", "叫我", "我叫", "名字是", "自称", "身份", "职业", "我是",
                "常住", "住在", "搬到", "搬家到", "所在城市", "时区", "母语", "过敏", "忌口"
            )
            if (identityMarkers.any { lower.contains(it) }) return IDENTITY to 4

            val projectMarkers = listOf(
                "正在开发", "项目", "目标", "计划", "重构", "开发中的", "负责", "产品", "备考", "论文"
            )
            if (projectMarkers.any { lower.contains(it) }) return PROJECT to 3

            val prefMarkers = listOf(
                "偏好", "喜欢", "习惯", "常用", "主力", "倾向", "爱吃", "爱喝", "讨厌", "不喜欢"
            )
            if (prefMarkers.any { lower.contains(it) }) return PREFERENCE to 4

            return FACT to 3
        }
    }
}

/**
 * M1 长期记忆混合检索、Gatekeeper 守门员、SmartAdd 语义仲裁与 M3 剧情块四分选材引擎。
 * 零外部依赖，可在关闭 Embedding API 时独立完成高质量中文/英文混合召回与去重。
 */
object LongTermMemoryEngine {

    private const val BM25_K1 = 1.5f
    private const val BM25_B = 0.75f
    private const val MMR_LAMBDA = 0.75f

    private val STOP_WORDS = setOf(
        "用户", "偏好", "习惯", "目前", "现在", "已经", "正在", "经常", "平时", "比较",
        "非常", "一般", "主要", "可以", "需要", "希望", "要求", "相关", "内容", "问题",
        "这个", "那个", "什么", "怎么", "如何", "为什么", "一下", "一些", "一个", "我们",
        "他们", "自己", "就是", "还是", "或者", "以及", "因为", "所以", "如果", "但是",
        "the", "and", "for", "that", "this", "with", "from", "have", "are", "was", "not"
    )

    private val GREETING_OR_EPHEMERAL = setOf(
        "你好", "您好", "嗨", "哈喽", "在吗", "在不在", "早安", "晚安", "好的", "好滴",
        "收到", "谢谢", "感谢", "多谢", "嗯", "嗯嗯", "哦", "哦哦", "行", "可以", "没问题",
        "继续", "下一步", "开始", "明白了", "懂了", "知道了", "哈哈", "哈哈哈", "ok", "okay",
        "yes", "no", "hi", "hello", "thanks", "thank you", "continue", "go on", "next"
    )

    private val PERSONAL_OR_DURABLE_CUES = listOf(
        "我是", "我在", "我有", "我喜欢", "我习惯", "我常用", "我长期", "我平时", "我以后",
        "我讨厌", "我不喜欢", "我叫", "我已经", "我决定", "我对", "我的", "咱们", "本人",
        "叫我", "称呼", "以后", "一直", "始终", "长期", "平时", "习惯", "偏好", "喜欢",
        "不喜欢", "讨厌", "过敏", "忌口", "禁止", "不要", "严禁", "必须", "记住", "别忘",
        "设定", "规则", "约定", "语气", "风格", "住在", "搬到", "搬家", "职业", "工作",
        "开发", "项目", "主力", "常用", "本会话", "这个会话", "在这个会话", "角色",
        "世界观", "背景", "禁忌", "称谓", "雷区", "记住："
    )

    data class ScoredMemory(
        val memory: MemoryItem,
        val score: Float,
        val bm25Score: Float,
        val vectorScore: Float,
        val entityHit: Boolean
    )

    data class SmartAddDecision(
        val action: String,               // ADD / MERGE / CONFLICT / SKIP
        val category: String,
        val importance: Int,
        val content: String,
        val targetMemoryId: Long? = null,
        val reason: String = "",
        val relatedIds: List<Long> = emptyList()
    )

    data class PlotChunkContextSplit(
        val pinnedChunks: List<ConversationSummaryChunk>,
        val recentChunks: List<ConversationSummaryChunk>,
        val recalledDistantChunks: List<ConversationSummaryChunk>,
        val staticSummaryBlock: String?,
        val dynamicRecalledBlock: String?
    )

    /**
     * 统一中英文分词器：同时供查询端与文档端使用，彻底修复中文整句按标点切分导致的匹配失效。
     * 生成：英文/数字完整词元（>=2字符）、中文短片段（<=6字整词 + 2-gram + 3-gram），并过滤高频停用词。
     */
    fun tokenize(text: String, maxTerms: Int = 180): List<String> {
        if (text.isBlank()) return emptyList()
        val lower = text.lowercase(Locale.ROOT)
        val tokens = mutableListOf<String>()

        Regex("""[a-z0-9][a-z0-9_\-./+#]{1,31}""").findAll(lower).forEach { match ->
            val word = match.value.trim('.', '/', '-', '_')
            if (word.length >= 2 && word !in STOP_WORDS) {
                tokens += word
            }
        }

        Regex("""[\u4E00-\u9FFF]{2,}""").findAll(lower).forEach { match ->
            val run = match.value
            if (run.length <= 6 && run !in STOP_WORDS) {
                tokens += run
            }
            for (i in 0..(run.length - 2)) {
                val bi = run.substring(i, i + 2)
                if (bi !in STOP_WORDS) tokens += bi
            }
            if (run.length >= 3) {
                for (i in 0..(run.length - 3)) {
                    val tri = run.substring(i, i + 3)
                    if (tri !in STOP_WORDS) tokens += tri
                }
            }
        }
        return if (tokens.size > maxTerms) tokens.take(maxTerms) else tokens
    }

    fun tokenizeSet(text: String, maxTerms: Int = 180): Set<String> = tokenize(text, maxTerms).toSet()

    fun extractEntities(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val entities = linkedSetOf<String>()
        Regex("""[“"《『【「]([^“”"》』】」]{2,24})[”"》』】」]""").findAll(text).forEach {
            entities += it.groupValues[1].trim()
        }
        Regex("""\b[A-Z][a-zA-Z0-9_\-]{2,25}\b""").findAll(text).forEach {
            entities += it.value.trim()
        }
        return entities
    }

    fun parseRelatedIds(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0L }
            .distinct()
    }

    fun encodeRelatedIds(ids: List<Long>): String? {
        val clean = ids.filter { it > 0L }.distinct().take(8)
        return if (clean.isEmpty()) null else clean.joinToString(",", "[", "]")
    }

    /**
     * 轻量零成本 Gatekeeper 守门员：
     * 拦截纯寒暄、单字确认、纯代码/报错粘贴等明显不含持久记忆价值的轮次，大幅降低后台模型开销。
     */
    fun shouldAttemptGlobalExtraction(userMessage: String): Boolean {
        val trimmed = userMessage.trim()
        if (trimmed.length < 5) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower in GREETING_OR_EPHEMERAL) return false

        // 若明确带有会话规则声明，立即放行
        if (SmartMemoryExtractor.isConversationScoped(trimmed)) return true

        // 纯一次性工具指令（翻译/改写/润色/计算）且无长期记忆/规则要求 -> 拦截
        val oneOffTaskPrefixes = listOf(
            "帮我翻译", "请翻译", "翻译一下", "把这段翻译", "解释一下这段",
            "总结一下这段", "润色一下", "帮我改写", "这段代码什么意思", "帮我写一个"
        )
        if (oneOffTaskPrefixes.any { lower.startsWith(it) } &&
            listOf("记住", "以后", "始终", "长期", "总是", "偏好", "习惯").none { lower.contains(it) }) {
            return false
        }

        // 纯代码/日志/堆栈粘贴且不含个人/约束信号 -> 拦截
        val codeFenceCount = Regex("```").findAll(trimmed).count()
        val looksLikeErrorDump = lower.contains("exception in thread") ||
            lower.contains("caused by:") ||
            lower.contains("at com.") ||
            lower.contains("traceback (most recent call last)") ||
            lower.contains("syntaxerror:")
        if ((codeFenceCount >= 2 || looksLikeErrorDump) && trimmed.length > 180 &&
            PERSONAL_OR_DURABLE_CUES.none { lower.contains(it) }) {
            return false
        }

        if (PERSONAL_OR_DURABLE_CUES.any { lower.contains(it) }) return true

        // 过短纯疑问（如“明天天气怎么样？”）且无第一人称持久陈述 -> 拦截
        if (trimmed.length <= 24 && (trimmed.endsWith("?") || trimmed.endsWith("？"))) {
            return false
        }
        return true
    }

    /**
     * 对一组文档执行真实 BM25（IDF + 文档长度归一化）评分。
     */
    fun <T> scoreDocumentsBm25(
        query: String,
        items: List<T>,
        textSelector: (T) -> String
    ): Map<Int, Float> {
        if (query.isBlank() || items.isEmpty()) return emptyMap()
        val queryTerms = tokenizeSet(query)
        if (queryTerms.isEmpty()) return emptyMap()

        val docTokens = items.map { tokenize(textSelector(it)) }
        val docFreqs = HashMap<String, Int>()
        var totalLen = 0
        for (tokens in docTokens) {
            totalLen += tokens.size
            val unique = tokens.toSet()
            for (term in unique) {
                if (term in queryTerms) {
                    docFreqs[term] = (docFreqs[term] ?: 0) + 1
                }
            }
        }
        if (docFreqs.isEmpty()) return emptyMap()
        val n = items.size.toFloat()
        val avgDl = (totalLen.toFloat() / items.size).coerceAtLeast(1f)

        val scores = HashMap<Int, Float>()
        for (idx in items.indices) {
            val tokens = docTokens[idx]
            if (tokens.isEmpty()) continue
            val tfMap = HashMap<String, Int>()
            for (t in tokens) {
                if (t in queryTerms) tfMap[t] = (tfMap[t] ?: 0) + 1
            }
            if (tfMap.isEmpty()) continue

            val dl = tokens.size.toFloat()
            var sum = 0f
            for ((term, tf) in tfMap) {
                val df = docFreqs[term] ?: 0
                val idf = ln(1.0 + ((n - df + 0.5) / (df + 0.5))).toFloat().coerceAtLeast(0.05f)
                val tfFloat = tf.toFloat()
                val denom = tfFloat + BM25_K1 * (1f - BM25_B + BM25_B * (dl / avgDl))
                sum += idf * ((tfFloat * (BM25_K1 + 1f)) / denom)
            }
            if (sum > 0f) scores[idx] = sum
        }
        return scores
    }

    /**
     * 混合召回 + 可选 Embedding RRF 融合 + 1-Hop 关联图谱增强 + 分类差异化衰减 + MMR 多样性去重。
     */
    fun rankMemories(
        query: String,
        candidates: List<MemoryItem>,
        vectorSimilarities: Map<Long, Float> = emptyMap(),
        maxResults: Int = 8,
        minRelevanceThreshold: Float = 0.08f,
        currentTimeMs: Long = System.currentTimeMillis()
    ): List<ScoredMemory> {
        if (candidates.isEmpty() || query.isBlank()) return emptyList()
        val queryEntities = extractEntities(query)
        val rawBm25 = scoreDocumentsBm25(query, candidates) { mem ->
            if (mem.keywords.isNullOrBlank()) mem.content else "${mem.content} ${mem.keywords}"
        }
        val maxBm25 = rawBm25.values.maxOrNull()?.takeIf { it > 0f } ?: 1f

        val bm25RankedIds = rawBm25.entries
            .sortedByDescending { it.value }
            .map { candidates[it.key].id }
        val vectorRankedIds = vectorSimilarities.entries
            .filter { it.value >= 0.25f }
            .sortedByDescending { it.value }
            .map { it.key }
        val hasVectorSignal = vectorRankedIds.isNotEmpty()
        val rrfScores = if (hasVectorSignal) {
            SemanticEmbeddingEngine.fuseRrf(bm25RankedIds, vectorRankedIds)
        } else {
            emptyMap()
        }
        val maxRrf = rrfScores.values.maxOrNull()?.takeIf { it > 0f } ?: 1f

        val baseScored = candidates.mapIndexed { idx, memory ->
            val bm25Norm = ((rawBm25[idx] ?: 0f) / maxBm25).coerceIn(0f, 1f)
            val vecSim = (vectorSimilarities[memory.id] ?: 0f).coerceIn(0f, 1f)
            val entityHit = queryEntities.any { it.isNotBlank() && memory.content.contains(it, ignoreCase = true) }
            val entityScore = if (entityHit) 1f else 0f

            // 严格相关性门控：无词面、无实体、无向量命中时不得凭新鲜度混入噪音
            val hasDirectSignal = bm25Norm > 0f || entityHit || vecSim >= 0.28f
            if (!hasDirectSignal) {
                ScoredMemory(memory, 0f, bm25Norm, vecSim, false)
            } else {
                val category = EchoMemoryCategory.fromKey(memory.category)
                val importanceNorm = (memory.importance.coerceIn(1, 5) / 5f)
                val recencyStability = computeCategoryRecencyAndAccess(memory, category, currentTimeMs)
                val relevance = if (hasVectorSignal) {
                    val rrfNorm = ((rrfScores[memory.id] ?: 0f) / maxRrf).coerceIn(0f, 1f)
                    (0.55f * rrfNorm + 0.25f * bm25Norm + 0.20f * vecSim).coerceIn(0f, 1f)
                } else {
                    bm25Norm
                }
                val pinBoost = if (memory.isPinned) 0.12f else 0f
                val total = (0.55f * relevance + 0.20f * entityScore + 0.15f * importanceNorm + 0.10f * recencyStability + pinBoost)
                    .coerceIn(0f, 1.25f)
                ScoredMemory(memory, total, bm25Norm, vecSim, entityHit)
            }
        }.toMutableList()

        // 轻量 1-Hop 关联图谱唤醒：若某条高分记忆显式关联了其他记忆，给予适度联动加分
        val scoreById = baseScored.associateBy { it.memory.id }.toMutableMap()
        val topAnchors = baseScored.filter { it.score >= 0.35f }.sortedByDescending { it.score }.take(3)
        for (anchor in topAnchors) {
            for (relId in parseRelatedIds(anchor.memory.relatedIdsJson)) {
                val neighbor = scoreById[relId] ?: continue
                val boosted = maxOf(neighbor.score, (anchor.score * 0.45f).coerceAtLeast(minRelevanceThreshold + 0.05f))
                if (boosted > neighbor.score) {
                    scoreById[relId] = neighbor.copy(score = boosted)
                }
            }
        }

        val eligible = scoreById.values
            .filter { it.score >= minRelevanceThreshold }
            .sortedWith(compareByDescending<ScoredMemory> { it.score }.thenByDescending { it.memory.updatedAt })

        return applyMmrDiversity(eligible, maxResults)
    }

    private fun computeCategoryRecencyAndAccess(
        memory: MemoryItem,
        category: EchoMemoryCategory,
        currentTimeMs: Long
    ): Float {
        val halfLife = category.halfLifeDays ?: return 1.0f
        if (memory.isPinned) return 1.0f
        val anchorTime = maxOf(memory.updatedAt, memory.lastAccessedAt ?: 0L)
        val ageDays = ((currentTimeMs - anchorTime) / (1000.0 * 60.0 * 60.0 * 24.0)).coerceAtLeast(0.0)
        val decayed = exp(-0.693 * (ageDays / halfLife)).toFloat().coerceIn(category.decayFloor, 1.0f)
        // ACT-R 风格访问频次唤醒：经常被用到的重要老记忆稳定上浮
        val accessBonus = (ln(1.0 + memory.accessCount.coerceAtLeast(0).toDouble()) * 0.08).toFloat().coerceAtMost(0.25f)
        return (decayed + accessBonus).coerceIn(category.decayFloor, 1.0f)
    }

    private fun applyMmrDiversity(
        ranked: List<ScoredMemory>,
        maxResults: Int
    ): List<ScoredMemory> {
        if (ranked.size <= 1 || maxResults <= 0) return ranked.take(maxResults.coerceAtLeast(0))
        val tokenSets = ranked.associate { it.memory.id to tokenizeSet(it.memory.content) }
        val remaining = ranked.toMutableList()
        val selected = mutableListOf<ScoredMemory>()

        while (remaining.isNotEmpty() && selected.size < maxResults) {
            if (selected.isEmpty()) {
                selected += remaining.removeAt(0)
                continue
            }
            var bestIdx = 0
            var bestMmr = Float.NEGATIVE_INFINITY
            for (i in remaining.indices) {
                val cand = remaining[i]
                val candTokens = tokenSets[cand.memory.id].orEmpty()
                val maxSim = selected.maxOf { chosen ->
                    jaccardSimilarity(candTokens, tokenSets[chosen.memory.id].orEmpty())
                }
                val mmr = MMR_LAMBDA * cand.score - (1f - MMR_LAMBDA) * maxSim
                if (mmr > bestMmr) {
                    bestMmr = mmr
                    bestIdx = i
                }
            }
            selected += remaining.removeAt(bestIdx)
        }
        return selected
    }

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val inter = a.count { it in b }
        if (inter == 0) return 0f
        val union = a.size + b.size - inter
        return if (union <= 0) 0f else inter.toFloat() / union.toFloat()
    }

    /**
     * 针对痛点 #1（设定与时间线被遗忘）的 Zone 3 近端场景召回：
     * 从全部已保存的会话设定和时间线节点中，挑出与本轮对话高度相关的前几项，
     * 作为精简“近端锚点”贴近用户输入放置（完全不破坏 Zone 1 缓存）。
     */
    fun selectSceneRelevantContinuityAnchors(
        query: String,
        sessionSettings: List<MemoryItem>,
        timelineNodes: List<TimelineNode>,
        currentStoryTime: String?,
        settingVectorScores: Map<Long, Float> = emptyMap(),
        timelineVectorScores: Map<Long, Float> = emptyMap(),
        maxSettings: Int = 4,
        maxTimelineNodes: Int = 3,
        maxTokens: Int = 420
    ): String? {
        if (query.isBlank() && currentStoryTime.isNullOrBlank()) return null

        val settingBm25 = scoreDocumentsBm25(query, sessionSettings) { "${it.content} ${it.keywords.orEmpty()}" }
        val maxSettingBm25 = settingBm25.values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val topSettings = sessionSettings.mapIndexedNotNull { idx, mem ->
            val bm = (settingBm25[idx] ?: 0f) / maxSettingBm25
            val vec = (settingVectorScores[mem.id] ?: 0f).coerceIn(0f, 1f)
            val entityHit = extractEntities(query).any { mem.content.contains(it, ignoreCase = true) }
            val pinnedBoost = if (mem.isPinned) 0.15f else 0f
            val score = if (bm > 0f || vec >= 0.30f || entityHit) {
                0.55f * bm + 0.30f * vec + (if (entityHit) 0.20f else 0f) + pinnedBoost
            } else 0f
            if (score > 0.12f) mem to score else null
        }.sortedByDescending { it.second }.take(maxSettings).map { it.first }

        val nodeBm25 = scoreDocumentsBm25(query, timelineNodes) { "${it.timeTag} ${it.event}" }
        val maxNodeBm25 = nodeBm25.values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val recalledNodes = timelineNodes.mapIndexedNotNull { idx, node ->
            val bm = (nodeBm25[idx] ?: 0f) / maxNodeBm25
            val vec = (timelineVectorScores[node.id] ?: 0f).coerceIn(0f, 1f)
            val score = if (bm > 0f || vec >= 0.30f) 0.65f * bm + 0.35f * vec else 0f
            if (score > 0.15f) node to score else null
        }.sortedByDescending { it.second }.take(maxTimelineNodes).map { it.first }

        val latestNode = timelineNodes.maxWithOrNull(compareBy<TimelineNode> { it.orderIndex }.thenBy { it.createdAt })
        val mergedNodes = (listOfNotNull(latestNode) + recalledNodes)
            .distinctBy { it.id }
            .sortedWith(compareBy<TimelineNode> { it.orderIndex }.thenBy { it.createdAt })

        val cleanTime = currentStoryTime?.trim()?.takeIf { it.isNotBlank() && it != "未确定" && it != "未知" }
        if (cleanTime == null && topSettings.isEmpty() && mergedNodes.isEmpty()) return null

        val lines = mutableListOf<String>()
        var used = 36
        cleanTime?.let {
            val line = "- 【当前故事时间锚点】$it"
            used += TokenEstimationHelper.estimateTokenCount(line) + 4
            lines += line
        }
        for (setting in topSettings) {
            val line = "- 【已确认设定】${SmartMemoryExtractor.sanitizeMetaLanguage(setting.content.trim())}"
            val cost = TokenEstimationHelper.estimateTokenCount(line) + 4
            if (used + cost > maxTokens) break
            lines += line
            used += cost
        }
        for (node in mergedNodes) {
            val tag = node.timeTag.takeIf { it.isNotBlank() }?.let { "[$it] " }.orEmpty()
            val line = "- 【时间线事实】$tag${node.event.trim()}"
            val cost = TokenEstimationHelper.estimateTokenCount(line) + 4
            if (used + cost > maxTokens) break
            lines += line
            used += cost
        }
        if (lines.isEmpty()) return null
        return "<active_continuity_anchor>\n【本轮高相关已确认设定与时空锚点（请严格呼应并保持一致，不得遗忘或矛盾）】：\n${lines.joinToString("\n")}\n</active_continuity_anchor>"
    }

    /**
     * M3 长篇创作 / 角色扮演剧情记忆块（HypaV3 四分预算范式）：
     * 1. 星标伏笔块（isPinned）-> Zone 1 静态前缀；
     * 2. 最近 2~3 段连续剧情块 -> Zone 1 静态前缀；
     * 3. 远古非置顶块 -> 按本轮场景 BM25（+ 可选 M2 向量 RRF）召回 Top-K -> Zone 3 尾注；
     * 4. 全部选中的块严格按原始 chunkIndex 升序重排，杜绝时序倒错。
     */
    fun selectPlotChunksForContext(
        chunks: List<ConversationSummaryChunk>,
        query: String,
        vectorSimilarities: Map<Long, Float> = emptyMap(),
        recentChunkCount: Int = 2,
        maxDistantRecalled: Int = 3,
        staticTokenBudget: Int = 1800,
        dynamicTokenBudget: Int = 900
    ): PlotChunkContextSplit {
        if (chunks.isEmpty()) {
            return PlotChunkContextSplit(emptyList(), emptyList(), emptyList(), null, null)
        }
        val ordered = chunks.sortedWith(compareBy<ConversationSummaryChunk> { it.chunkIndex }.thenBy { it.id })
        val pinned = ordered.filter { it.isPinned }
        val pinnedIds = pinned.mapTo(mutableSetOf()) { it.id }
        val recent = ordered.filter { it.id !in pinnedIds }.takeLast(recentChunkCount.coerceAtLeast(1))
        val staticIds = (pinned + recent).mapTo(mutableSetOf()) { it.id }

        // 1. 装填 Zone 1 静态剧情主干（星标伏笔优先，再装近期连续剧情，最终严格按 chunkIndex 升序输出）
        val chosenStatic = mutableListOf<ConversationSummaryChunk>()
        var staticUsed = 48
        for (chunk in pinned) {
            val cost = TokenEstimationHelper.estimateTokenCount(chunk.summaryText) + 16
            if (staticUsed + cost <= staticTokenBudget || chosenStatic.isEmpty()) {
                chosenStatic += chunk
                staticUsed += cost
            }
        }
        for (chunk in recent) {
            val cost = TokenEstimationHelper.estimateTokenCount(chunk.summaryText) + 16
            if (staticUsed + cost <= staticTokenBudget || chosenStatic.isEmpty()) {
                chosenStatic += chunk
                staticUsed += cost
            }
        }
        val staticSorted = chosenStatic.sortedWith(compareBy<ConversationSummaryChunk> { it.chunkIndex }.thenBy { it.id })

        // 2. 召回 Zone 3 远古伏笔剧情块（不破坏 Zone 1 缓存）
        val distantPool = ordered.filter { it.id !in staticIds }
        val recalledDistant = if (distantPool.isNotEmpty() && query.isNotBlank() && maxDistantRecalled > 0) {
            val bm25 = scoreDocumentsBm25(query, distantPool) {
                "${it.timeRangeTag.orEmpty()} ${it.summaryText} ${it.keywords.orEmpty()}"
            }
            val maxBm = bm25.values.maxOrNull()?.takeIf { it > 0f } ?: 1f
            val bmIds = bm25.entries.sortedByDescending { it.value }.map { distantPool[it.key].id }
            val vecIds = vectorSimilarities.entries
                .filter { it.value >= 0.25f && distantPool.any { c -> c.id == it.key } }
                .sortedByDescending { it.value }
                .map { it.key }
            val rrf = if (vecIds.isNotEmpty()) SemanticEmbeddingEngine.fuseRrf(bmIds, vecIds) else emptyMap()
            val maxRrf = rrf.values.maxOrNull()?.takeIf { it > 0f } ?: 1f

            val scored = distantPool.mapIndexedNotNull { idx, chunk ->
                val bm = (bm25[idx] ?: 0f) / maxBm
                val vec = (vectorSimilarities[chunk.id] ?: 0f).coerceIn(0f, 1f)
                if (bm <= 0f && vec < 0.28f) return@mapIndexedNotNull null
                val score = if (vecIds.isNotEmpty()) {
                    0.60f * ((rrf[chunk.id] ?: 0f) / maxRrf) + 0.25f * bm + 0.15f * vec
                } else bm
                chunk to score
            }.sortedByDescending { it.second }

            val picked = mutableListOf<ConversationSummaryChunk>()
            var dynUsed = 48
            for ((chunk, _) in scored) {
                if (picked.size >= maxDistantRecalled) break
                val cost = TokenEstimationHelper.estimateTokenCount(chunk.summaryText) + 16
                if (dynUsed + cost <= dynamicTokenBudget) {
                    picked += chunk
                    dynUsed += cost
                }
            }
            picked.sortedWith(compareBy<ConversationSummaryChunk> { it.chunkIndex }.thenBy { it.id })
        } else {
            emptyList()
        }

        val staticBlock = staticSorted.takeIf { it.isNotEmpty() }?.joinToString("\n\n") { chunk ->
            formatChunkEntry(chunk)
        }
        val dynamicBlock = recalledDistant.takeIf { it.isNotEmpty() }?.let { list ->
            val body = list.joinToString("\n\n") { formatChunkEntry(it) }
            "<recalled_plot_chunks>\n【本轮关联的早期分段剧情回忆（按发生顺序排列）】：\n$body\n</recalled_plot_chunks>"
        }

        return PlotChunkContextSplit(
            pinnedChunks = pinned,
            recentChunks = recent,
            recalledDistantChunks = recalledDistant,
            staticSummaryBlock = staticBlock,
            dynamicRecalledBlock = dynamicBlock
        )
    }

    private fun formatChunkEntry(chunk: ConversationSummaryChunk): String {
        val star = if (chunk.isPinned) "★ " else ""
        val time = chunk.timeRangeTag?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val fallbackTag = if (chunk.sourceKind == "LOCAL_FALLBACK") " · 本地安全摘录" else ""
        return "【第 ${chunk.chunkIndex + 1} 段$time$fallbackTag】${star}${chunk.summaryText.trim()}"
    }

    /**
     * 构建融合反幻觉四铁律与 SmartAdd 语义仲裁契约的全局长期记忆提炼提示词。
     */
    fun buildGlobalSmartAddContract(similarExisting: List<MemoryItem>): String {
        val existingBlock = if (similarExisting.isEmpty()) {
            "（当前库中暂无相似的既有全局长期记忆）"
        } else {
            similarExisting.joinToString("\n") { mem ->
                """{"id":${mem.id},"category":"${mem.category}","content":${com.google.gson.Gson().toJson(mem.content)}}"""
            }
        }
        return """
            【反幻觉与质量四铁律（必须遵守）】
            1. [亲口陈述原则]：只提取用户明确亲口陈述的长期身份、偏好、习惯、项目、事实或约束；严禁记录 AI 自行推测或未证实的结论。
            2. [校准防泛化]：单次提到的工具、话题、临时调试或偶发情绪不要升级为长期习惯/长期爱好。
            3. [时效性检验]：问自己“一个月后在一个全新话题里，这条信息还有用吗？”若无价值则跳过。
            4. [去元词与反谄媚]：严禁出现“用户”“AI”“助手”“模型”等出戏元词，也不记录吹捧/客套话。

            【已有相似全局记忆（供语义去重/合并/冲突仲裁，严禁编造不存在的 id）】
            $existingBlock

            【输出格式（请严格只输出一个 JSON 对象）】
            - 若无值得长期记住的新信息，或已被已有记忆完整覆盖：输出 {"action":"SKIP"}
            - 若为全新长期记忆：{"action":"ADD","category":"IDENTITY|VOICE|INSTRUCTION|PREFERENCE|PROJECT|FACT","importance":1到5,"content":"30~80字完整清晰陈述","relatedIds":[关联的既有记忆id或空数组]}
            - 若是对某条已有记忆的补充细化（不矛盾）：{"action":"MERGE","targetMemoryId":已有id,"category":"...","importance":1到5,"content":"融合旧内容与新细节的完整陈述","reason":"补充原因"}
            - 若与某条已有记忆事实冲突/更替（如搬家、改称呼、换主力技术栈）：{"action":"CONFLICT","targetMemoryId":被取代的旧id,"category":"...","importance":1到5,"content":"最新完整陈述","reason":"变更原因"}
        """.trimIndent()
    }

    /**
     * 解析 SmartAdd JSON 决策；若模型返回非 JSON 纯文本则返回 null 由调用方平滑走旧纯文本精炼兼容链路。
     */
    fun parseGlobalSmartAddDecision(rawCleaned: String, existingIds: Set<Long>): SmartAddDecision? {
        val trimmed = rawCleaned.trim()
        if (!trimmed.startsWith("{")) return null
        val obj = runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull() ?: return null
        val actionRaw = obj.get("action")?.takeUnless { it.isJsonNull }?.asString?.trim()?.uppercase(Locale.ROOT)
            ?: return null
        if (actionRaw in setOf("SKIP", "NOOP", "NO_UPDATE", "IGNORE")) {
            return SmartAddDecision(action = "SKIP", category = "FACT", importance = 3, content = "")
        }
        val normalizedAction = when (actionRaw) {
            "ADD", "NEW", "CREATE" -> "ADD"
            "MERGE", "SUPPLEMENT", "UPDATE" -> "MERGE"
            "CONFLICT", "REVISE", "REPLACE" -> "CONFLICT"
            else -> return null
        }
        val content = obj.get("content")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
        if (content.length < 3) return null
        val targetId = obj.get("targetMemoryId")?.takeUnless { it.isJsonNull }?.let {
            runCatching { it.asLong }.getOrNull()
        }?.takeIf { it in existingIds }
        val effectiveAction = if (normalizedAction in setOf("MERGE", "CONFLICT") && targetId == null) "ADD" else normalizedAction
        val rawCategory = obj.get("category")?.takeUnless { it.isJsonNull }?.asString
        val (inferredCat, inferredImp) = EchoMemoryCategory.inferFromContent(content)
        val category = if (rawCategory.isNullOrBlank()) inferredCat else EchoMemoryCategory.fromKey(rawCategory)
        val importance = obj.get("importance")?.takeUnless { it.isJsonNull }?.let {
            runCatching { it.asInt }.getOrNull()
        }?.coerceIn(1, 5) ?: inferredImp
        val reason = obj.get("reason")?.takeUnless { it.isJsonNull }?.asString?.trim().orEmpty()
        val relatedIds = obj.get("relatedIds")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
            runCatching { el.asLong }.getOrNull()?.takeIf { it in existingIds && it != targetId }
        }?.distinct().orEmpty()

        return SmartAddDecision(
            action = effectiveAction,
            category = category.key,
            importance = importance,
            content = content,
            targetMemoryId = targetId,
            reason = reason,
            relatedIds = relatedIds
        )
    }
}
