package com.aiassistant.utils

import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.Message
import java.util.Locale
import kotlin.math.exp

/**
 * 高级记忆提取、冲突消解与分层上下文压缩引擎 (AdvancedMemoryEngine)
 * 借鉴 Mem0, Generative Agents, LangChain Memory 等开源前沿实践：
 * 1. 原子事实分类体系与重要度权重 (1~5)
 * 2. 事实冲突消解与自适应更替 (Conflict Resolution & Upsert)
 * 3. 三维混合检索评分算法 (Relevance + Importance + Recency + Entity Boosting)
 * 4. 结构化多维分层上下文压缩模型 (Core Constraints + Milestones + Open Items)
 * 5. 智能信息密度提纯 (Loss-Aware Pre-pruning)
 * 6. 高保真本地抽取式多维结构化摘要兜底
 */
object AdvancedMemoryEngine {

    enum class MemoryCategory(val displayName: String, val baseImportance: Int) {
        CONSTRAINT("不可违背约束", 5),
        PREFERENCE("偏好习惯", 4),
        TIMELINE("时空经历", 4),
        FACT("客观事实", 3),
        WORLD_STATE("状态设定", 3)
    }

    data class AtomicFact(
        val content: String,
        val category: MemoryCategory,
        val importance: Int,
        val entities: List<String> = emptyList()
    )

    data class StructuredStateSummary(
        val coreConstraints: List<String> = emptyList(),
        val milestones: List<String> = emptyList(),
        val openItems: List<String> = emptyList()
    ) {
        fun toPromptBlock(): String {
            val sb = StringBuilder()
            if (coreConstraints.isNotEmpty()) {
                sb.append("【核心背景与用户固定约束】：\n")
                coreConstraints.forEach { sb.append("- $it\n") }
                sb.append("\n")
            }
            if (milestones.isNotEmpty()) {
                sb.append("【历史关键里程碑与决策推进】：\n")
                milestones.forEachIndexed { idx, m -> sb.append("${idx + 1}. $m\n") }
                sb.append("\n")
            }
            if (openItems.isNotEmpty()) {
                sb.append("【当前未决议题与待办上下文】：\n")
                openItems.forEach { sb.append("• $it\n") }
            }
            return sb.toString().trim()
        }

        fun isEmpty(): Boolean = coreConstraints.isEmpty() && milestones.isEmpty() && openItems.isEmpty()
    }

    // ==========================================
    // 1. 原子事实类别与重要度推断
    // ==========================================

    fun inferFactCategoryAndImportance(text: String): Pair<MemoryCategory, Int> {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            // 约束类：行为规则、输出禁止、格式要求
            listOf("必须", "绝对不能", "不要输出", "严禁", "只能使用", "始终使用", "输出格式", "约束", "规则").any { lower.contains(it) } ->
                Pair(MemoryCategory.CONSTRAINT, 5)

            // 偏好类：个人习惯、喜好、风格
            listOf("喜欢", "偏好", "习惯", "擅长", "倾向于", "喜好", "常用").any { lower.contains(it) } ->
                Pair(MemoryCategory.PREFERENCE, 4)

            // 时空经历类：时间、日期、在某处发生了什么
            listOf("第", "天", "年", "月", "日", "昨天", "去了", "吃了", "见过", "相遇").any { lower.contains(it) } &&
                    (lower.startsWith("[") || lower.startsWith("【") || lower.contains("·")) ->
                Pair(MemoryCategory.TIMELINE, 4)

            // 状态设定类：角色健康、关系、世界观参数
            listOf("等级", "状态", "处于", "关系", "好感", "生命值", "血量", "心情").any { lower.contains(it) } ->
                Pair(MemoryCategory.WORLD_STATE, 3)

            // 普通客观事实
            else -> Pair(MemoryCategory.FACT, 3)
        }
    }

    // ==========================================
    // 2. 记忆冲突检测与消解更替 (Conflict Resolution)
    // ==========================================

    /**
     * 判定新记忆是否与已有旧记忆发生排他性属性冲突（需要覆盖更新旧记忆）
     * 例如：“我改名叫李四” 冲突并覆盖 “我叫张三”
     *      “搬到了深圳” 冲突并覆盖 “住在北京”
     *      “偏好改为使用Python” 冲突并覆盖 “偏好使用Java”
     */
    fun detectConflict(newContent: String, existingItem: MemoryItem): Boolean {
        val n = newContent.trim()
        val o = existingItem.content.trim()
        if (n == o) return false // 完全相同走常规强化流程，不是冲突覆盖

        // 居住地冲突
        val locationVerbs = listOf("住在", "搬到", "位于", "常住", "搬家到")
        if (locationVerbs.any { n.contains(it) } && locationVerbs.any { o.contains(it) }) {
            return true
        }

        // 姓名/称呼冲突
        val namePatterns = listOf("叫我", "我叫", "名字是", "称呼我")
        if (namePatterns.any { n.contains(it) } && namePatterns.any { o.contains(it) }) {
            return true
        }

        // 偏好技术栈/工具更替
        val prefPatterns = listOf("偏好使用", "改为使用", "换用", "主力语言是")
        if (prefPatterns.any { n.contains(it) } && prefPatterns.any { o.contains(it) }) {
            return true
        }

        // 相同前缀声明冲突（如两者均以相同属性开头）
        val colonPrefixNew = n.substringBefore("：").substringBefore(":")
        val colonPrefixOld = o.substringBefore("：").substringBefore(":")
        if (colonPrefixNew.length in 2..8 && colonPrefixNew == colonPrefixOld) {
            val attrKey = colonPrefixNew.removePrefix("用户").removePrefix("会话")
            if (attrKey in listOf("身份", "职业", "称谓", "年龄", "城市", "时区", "语言偏好", "当前目标")) {
                return true
            }
        }

        return false
    }

    // ==========================================
    // 3. 三维混合检索评分算法 (Tri-Factor Hybrid Scoring)
    // ==========================================

    /**
     * 综合 Relevance (40%) + Importance (25%) + Recency (20%) + Entity Boosting (15%) + ScopeBoost
     */
    fun calculateHybridScore(
        memory: MemoryItem,
        queryTerms: Set<String>,
        queryEntities: Set<String>,
        conversationId: Long,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Float {
        // 1. Relevance: 字面重合度 (0.0 ~ 1.0)
        val memoryTerms = memory.content.lowercase(Locale.ROOT).split(Regex("""[\s,，.。!！?？:：;；、]+"""))
            .filter { it.length >= 2 }
            .toSet()
        val overlapCount = queryTerms.count { it in memoryTerms }
        val relevanceScore = (overlapCount * 0.35f).coerceIn(0f, 1f)

        // 2. Importance: 静态重要度 (0.2 ~ 1.0)
        val (_, importance) = inferFactCategoryAndImportance(memory.content)
        val importanceScore = (importance / 5f).coerceIn(0.2f, 1f)

        // 3. Recency: 时间衰减 (0.1 ~ 1.0)
        val ageDays = ((currentTimeMs - memory.updatedAt) / (1000.0 * 60 * 60 * 24)).coerceAtLeast(0.0)
        val halfLifeDays = 7.0
        val recencyScore = exp(-0.693 * (ageDays / halfLifeDays)).toFloat().coerceIn(0.1f, 1f)

        // 4. Entity Boosting: 实体精准命中加成 (0.0 或 1.0)
        val entityHit = queryEntities.any { it.isNotBlank() && memory.content.contains(it, ignoreCase = true) }
        val entityScore = if (entityHit) 1f else 0f

        // 5. Scope Boost: 当前会话记忆加成
        val scopeBoost = if (memory.scope == "conversation" && memory.conversationId == conversationId) 0.18f else 0f

        return (0.40f * relevanceScore) +
                (0.25f * importanceScore) +
                (0.20f * recencyScore) +
                (0.15f * entityScore) +
                scopeBoost
    }

    // ==========================================
    // 4. 智能信息密度提纯 (Loss-Aware Pre-pruning)
    // ==========================================

    private val PURE_NOISE_SNIPPETS = setOf(
        "好的", "收到", "谢谢", "好的谢谢", "收到谢谢", "嗯", "嗯嗯", "行", "可以", "没问题",
        "哈哈", "是的", "对", "ok", "yes", "thanks", "thank you", "got it"
    )

    /**
     * 过滤短纯寒暄或无实质信息的废话轮次，提升送入摘要器的信息信噪比
     */
    fun pruneLowInformationTurns(messages: List<Message>): List<Message> {
        return messages.filter { msg ->
            val trimmed = msg.content.trim().lowercase(Locale.ROOT)
            val isShortNoise = trimmed.length <= 6 && PURE_NOISE_SNIPPETS.contains(trimmed)
            !isShortNoise
        }
    }

    // ==========================================
    // 5. 结构化多维摘要模型解析与本地抽取式兜底
    // ==========================================

    fun parseStructuredSummary(rawText: String): StructuredStateSummary {
        val text = rawText.trim()
        val constraints = mutableListOf<String>()
        val milestones = mutableListOf<String>()
        val openItems = mutableListOf<String>()

        var currentSection = 0 // 0: none, 1: constraints, 2: milestones, 3: openItems

        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.contains("核心背景") || trimmed.contains("固定约束") || trimmed.contains("约束") ->
                    currentSection = 1
                trimmed.contains("里程碑") || trimmed.contains("事件推进") || trimmed.contains("决策") ->
                    currentSection = 2
                trimmed.contains("未决") || trimmed.contains("待办") || trimmed.contains("下一步") ->
                    currentSection = 3
                trimmed.isNotBlank() -> {
                    val content = trimmed.removePrefix("-").removePrefix("•").removePrefix("*")
                        .replace(Regex("""^\d+[\.、]\s*"""), "").trim()
                    if (content.isNotBlank()) {
                        when (currentSection) {
                            1 -> constraints.add(content)
                            2 -> milestones.add(content)
                            3 -> openItems.add(content)
                            else -> {
                                if (milestones.size < 5) milestones.add(content)
                            }
                        }
                    }
                }
            }
        }

        return StructuredStateSummary(
            coreConstraints = constraints,
            milestones = milestones,
            openItems = openItems
        )
    }

    /**
     * 安全提取完整句子或分句，基于标点边界完整闭合，彻底杜绝在词句中途暴力截断
     */
    fun extractCompleteSentence(text: String, maxChars: Int = 140): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed

        val candidate = trimmed.substring(0, maxChars)
        // 优先在主句末标点（。！？\n；.!?）处闭合
        val primaryPunctuation = listOf('。', '！', '？', '\n', '；', '.', '!', '?')
        val lastPrimaryIdx = candidate.indexOfLast { it in primaryPunctuation }
        if (lastPrimaryIdx >= 16) {
            return candidate.substring(0, lastPrimaryIdx + 1).trim()
        }

        // 次选分句标点（，、,）处闭合并追加省略号
        val secondaryPunctuation = listOf('，', '、', ',')
        val lastSecondaryIdx = candidate.indexOfLast { it in secondaryPunctuation }
        if (lastSecondaryIdx >= 16) {
            return candidate.substring(0, lastSecondaryIdx).trim() + "..."
        }

        // 若前 maxChars 字符完全无标点，安全截断并加省略号
        return candidate.trimEnd() + "..."
    }

    /**
     * 本地高保真抽取式结构化多维摘要兜底
     * 当外部模型调用不可用或超时时，基于启发式算法抽取核心约束、关键讨论和待办项，保证断句完整
     */
    fun generateExtractiveStructuredSummary(
        messages: List<Message>,
        maxTokens: Int = 1000,
        existingPreferencesAndConstraints: List<String>? = null
    ): StructuredStateSummary {
        // 聚焦近期对话轮次（最多最近 30 条），防止将全篇开场的陈旧消息与近期消息强行拼合
        val recentMessages = if (messages.size > 30) messages.takeLast(30) else messages
        val pruned = pruneLowInformationTurns(recentMessages)
        val constraints = mutableListOf<String>()
        val milestones = mutableListOf<String>()
        val openItems = mutableListOf<String>()

        val knownConstraints = existingPreferencesAndConstraints?.map { it.lowercase(Locale.ROOT) }.orEmpty()

        for (msg in pruned) {
            val content = msg.content.trim()
            val lower = content.lowercase(Locale.ROOT)

            // 1. 抽取用户固定约束与核心要求（去重：若在已知记忆约束中已存在，则严禁重复提炼）
            if (msg.role == "user") {
                if (listOf("请记住", "要求", "设定", "必须", "不要", "始终", "偏好").any { lower.contains(it) }) {
                    val targetLine = content.lines().firstOrNull { l ->
                        listOf("要求", "设定", "必须", "不要", "始终", "偏好", "记住").any { l.contains(it) }
                    } ?: content
                    val clean = extractCompleteSentence(targetLine, 140)
                    val isAlreadyKnown = knownConstraints.any { it.contains(clean.lowercase(Locale.ROOT)) || clean.lowercase(Locale.ROOT).contains(it) }
                    if (!isAlreadyKnown && constraints.none { it == clean }) {
                        constraints.add(clean)
                    }
                }
            }

            // 2. 抽取关键里程碑与决策
            if (content.startsWith("[") || content.startsWith("【") || listOf("决定", "完成了", "达成", "发现", "商定", "推进至").any { lower.contains(it) }) {
                val targetLine = content.lines().firstOrNull { l ->
                    listOf("决定", "完成了", "达成", "发现", "商定", "推进至").any { l.contains(it) }
                } ?: content
                val clean = extractCompleteSentence(targetLine, 160)
                if (milestones.none { it == clean }) milestones.add(clean)
            }

            // 3. 抽取末尾未决议题与待办事项
            if (listOf("下一步", "还需", "待办", "待确认", "待解决", "稍后", "接下来需要").any { lower.contains(it) }) {
                val targetLine = content.lines().firstOrNull { l ->
                    listOf("下一步", "还需", "待办", "待确认", "待解决", "稍后", "接下来需要").any { l.contains(it) }
                } ?: content
                val clean = extractCompleteSentence(targetLine, 140)
                if (openItems.none { it == clean }) openItems.add(clean)
            }
        }

        // 如果未命中明确里程碑，从关键轮次对话中提取有实际意义的整句，杜绝机械无头无尾截断
        if (milestones.isEmpty() && pruned.isNotEmpty()) {
            val keyMessages = pruned.takeLast(4)
            keyMessages.forEach { msg ->
                val role = if (msg.role == "user") "用户" else "助手"
                val sentence = extractCompleteSentence(msg.content, 120)
                if (sentence.isNotBlank()) {
                    milestones.add("$role: $sentence")
                }
            }
        }

        return StructuredStateSummary(
            coreConstraints = constraints.take(4),
            milestones = milestones.take(8),
            openItems = openItems.take(4)
        )
    }

    /**
     * 生成引导模型输出结构化多维摘要的标准系统提示词（与时间线系统紧密协同，杜绝断层剧情拼接与句子截断）
     * 架构原则：
     * 1. 整体的时间线通过记忆读取：全篇历史脉络与时间线独立承载，滚动摘要严禁从头编造或错误串联开场情节；
     * 2. 摘要只负责总结最近发生了什么：聚焦近期对话的核心进展与当前停顿状态；
     * 3. 摘要总结的内容只能和时间线最新的时间节点关联上；
     * 4. 优化滚动摘要的提取方式：记忆库与约束系统已独立完整承载偏好习惯，严禁总结记忆中已经存在的内容（如用户偏好约束等）。
     */
    fun buildStructuredSummaryPrompt(
        existingSummary: String?,
        transcript: String,
        tokenBudget: Int,
        latestTimelineAnchor: String? = null,
        existingPreferencesAndConstraints: List<String>? = null
    ): String {
        val timelineAnchorDirective = if (!latestTimelineAnchor.isNullOrBlank()) {
            """

            【时间线最新时间节点（时序基准）】：
            $latestTimelineAnchor
            - 锚定铁律：整体时间线通过记忆读取，摘要只负责总结最近发生了什么，摘要总结的内容只能和时间线最新的时间节点关联上！严禁将更早开场情节（如初次相见）与近期事件跨段因果连接。
            """.trimIndent()
        } else {
            ""
        }

        val existingConstraintsDirective = if (!existingPreferencesAndConstraints.isNullOrEmpty()) {
            """

            【已有记忆与偏好约束（严禁在此重复提炼）】：
            ${existingPreferencesAndConstraints.joinToString("\n") { "- $it" }}
            - 极重要去重铁律：以上用户偏好习惯、行为禁令与固定约束已由系统记忆全量保存，本摘要严禁总结或记录上述任何内容！
            """.trimIndent()
        } else {
            ""
        }

        return """
            请将以下历史对话提炼为高质量、结构清晰、信息完整的会话滚动摘要。
            【架构定位与分工】：整体的时间线通过记忆读取，滚动摘要只负责总结最近发生了什么。侧重提炼“前序对话核心脉络与未决议题、达成的共识与决策、当前未决议题与待办事项”，与时间线系统紧密协同互补，避免机械复读冗长的时间节点列表。
            【极重要去重原则】：不要总结记忆中已经存在的内容（严禁总结用户偏好习惯、称谓要求、输出格式、行为准则等固定约束），避免冗余复读与挤占篇幅。
            摘要必须言之有物、表述完整、逻辑严谨，每句话必须有始有终，结尾必须以完整标点符号（如句号）闭合，严禁输出残缺短句、截断词组或毫无意义的机械套话。$timelineAnchorDirective$existingConstraintsDirective

            请按以下结构组织内容：

            【核心背景与用户固定约束】
            - 准确概括本次对话的核心主题与当前讨论目标。【极重要去重原则】：严禁总结记忆中已经存在的内容！用户的偏好习惯（如称谓、语气风格、格式要求）、固定约束与行为准则已由系统记忆独立全量承载，本摘要严禁总结或重复记录任何用户偏好约束与长期设定，必须将宝贵篇幅全量留给近期真实发生的事件推进与最新时空动态。

            【历史关键里程碑与决策推进】
            - 按顺序提炼双方经历的核心事件、已解决的关键技术/业务决策或剧情推进（编号 1, 2, 3...，简短聚焦事件本身与决策推进，与时间线系统紧密协同互补）。每条记录必须是完整、通顺、有始有终的句子。严禁跨越中段剧情去错误连接开场相见与最新事件等断层情节。

            【时空演变与关键时间节点（极重要，严禁遗漏）】
            - 起始时间与总跨度：整体历史时间线由记忆系统独立读取与管理，滚动摘要只负责总结最近发生了什么；若对话涉及具体故事剧情或明确的时间跨度，记录故事或事件发生的起始时间与总跨度（以时间线最新时间节点为基准展开）；若为常规技术/工作讨论，则记录对话发展的起始阶段与当前演进过程。时间概念必须严密准确，严禁出现前序事件时序倒流或将数天前事件混淆为昨天的错误，严禁将早期事件模糊为“昨天”！
            - 当前故事停顿节点：摘要总结的内容只能和时间线最新的时间节点关联上，记录当前对话停顿时所在的时空位置、剧情节点或最新讨论停顿阶段。

            【当前未决议题与待办上下文】
            - 明确提取当前对话停顿处正在进行、尚未完成的事项或下一步待办，保持前序对话核心脉络与未决议题清晰，便于后续无缝承接。

            要求：
            1. 语言必须自然通顺、表述完整，每句话都必须意思表达充分，结尾必须以完整标点闭合，严禁被暴力截断或半句截断。
            2. 必须完整输出全部 4 个板块（【核心背景与用户固定约束】、【历史关键里程碑与决策推进】、【时空演变与关键时间节点】、【当前未决议题与待办上下文】），绝对严禁只输出板块标题而不写实质内容！若当前阶段尚未形成复杂里程碑或待办，请根据对话现状提炼简明概括（例如“当前处于起步推进阶段，双方正聚焦即时互动”），严禁留下孤立空标题或中途截断。
            3. 控制在 $tokenBudget token 以内，使用简明中文，直切要点，杜绝废话和无意义客套。
            4. 与时间线系统紧密协同互补，避免机械复读冗长的时间节点列表。
            5. 整体的时间线通过记忆读取，摘要只负责总结最近发生了什么，摘要总结的内容只能和时间线最新的时间节点关联上。
            6. 极重要去重原则：不要总结记忆中已经存在的内容，严禁总结用户的偏好习惯、称谓禁忌、输出格式要求或行为准则等约束，全量聚焦近期对话的具体进展与最新时空节点。
            7. 不要输出任何开场白或“好的，以下是摘要”等客套。

            已有摘要：
            ${existingSummary ?: "无"}

            新增对话历史：
            $transcript
        """.trimIndent()
    }
}
