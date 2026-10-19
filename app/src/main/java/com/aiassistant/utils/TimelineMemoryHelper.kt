package com.aiassistant.utils

import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.TimelineNode
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID
import java.util.regex.Pattern

/**
 * 时间线与设定条目分类
 */
enum class TimelineCategory(val displayName: String, val emoji: String, val tagColorHex: String) {
    PLOT_EVENT("剧情事件", "📖", "#2196F3"),
    TURNING_POINT("转折关键", "⚡", "#E91E63"),
    CHARACTER_BOND("羁绊进展", "🤝", "#9C27B0"),
    RELATIONSHIP("关系状态", "❤️", "#F06292"),
    KEY_FACT("重要事实", "📌", "#009688"),
    RULE_CONSTRAINT("规则约束", "⚖️", "#FF9800"),
    CHARACTER_SETTING("角色设定", "🎭", "#673AB7"),
    WORLD_SETTING("世界设定", "🌍", "#4CAF50"),
    ATEMPORAL_SETTING("固有设定", "💡", "#607D8B");

    val key: String get() = name

    companion object {
        fun fromKey(key: String?): TimelineCategory {
            if (key.isNullOrBlank()) return PLOT_EVENT
            val lower = key.lowercase()
            return when {
                lower.contains("turning") || lower.contains("转折") -> TURNING_POINT
                lower.contains("bond") || lower.contains("羁绊") -> CHARACTER_BOND
                lower.contains("relationship") || lower.contains("关系") -> RELATIONSHIP
                lower.contains("fact") || lower.contains("事实") -> KEY_FACT
                lower.contains("atemporal") || lower.contains("固有") || lower.contains("常驻") || lower.contains("固有设定") -> ATEMPORAL_SETTING
                lower.contains("rule") || lower.contains("constraint") || lower.contains("规则") || lower.contains("约束") || lower.contains("禁止") -> RULE_CONSTRAINT
                lower.contains("char") || lower.contains("role") || lower.contains("角色") || lower.contains("人物") -> CHARACTER_SETTING
                lower.contains("world") || lower.contains("scene") || lower.contains("世界") || lower.contains("设定") || lower.contains("状态") -> WORLD_SETTING
                else -> PLOT_EVENT
            }
        }
    }
}

/**
 * 日内时段细分状态机（解决吃完早餐突兀天黑等割裂问题）
 */
enum class DayPhase(val order: Int, val displayName: String, val typicalActivities: String) {
    EARLY_MORNING(1, "清晨/早晨", "醒来、洗漱、吃早餐、晨间谈话"),
    MORNING(2, "上午", "白天工作、外出行动、研讨事务"),
    NOON(3, "中午/午间", "午餐、短暂休憩、午后计划"),
    AFTERNOON(4, "下午", "下午活动、茶歇、外出推进事情"),
    DUSK(5, "傍晚/黄昏", "日落、夕阳、返程、晚餐准备"),
    NIGHT(6, "入夜/晚间", "晚餐、夜间交谈、室内放松、夜间事件"),
    LATE_NIGHT(7, "深夜/拂晓", "就寝休息、失眠深谈、夜巡、破晓前夕");

    companion object {
        fun inferFromText(text: String): DayPhase? {
            val lower = text.lowercase()
            // 排除仅作为日常用语、往事回忆、将来计划中包含“晚”或“夜”的非当前时段词汇
            val filtered = lower
                .replace("昨晚", "")
                .replace("昨夜", "")
                .replace("前晚", "")
                .replace("前夜", "")
                .replace("早晚", "")
                .replace("晚点", "")
                .replace("来晚", "")
                .replace("太晚", "")
                .replace("还不晚", "")
                .replace("不早不晚", "")
                .replace("连夜", "")
                .replace("日夜", "")
                .replace("隔夜", "")
                .replace("这几天晚上", "")
                .replace("那几天晚上", "")

            return when {
                filtered.contains("清晨") || filtered.contains("早晨") || filtered.contains("早上") || filtered.contains("早安") ||
                filtered.contains("晨光") || filtered.contains("破晓") || filtered.contains("晨曦") || filtered.contains("早餐") ||
                filtered.contains("早点") || filtered.contains("拂晓") || filtered.contains("大清早") || filtered.contains("朝阳") -> EARLY_MORNING
                filtered.contains("上午") || filtered.contains("半上午") || filtered.contains("白天") -> MORNING
                filtered.contains("中午") || filtered.contains("正午") || filtered.contains("晌午") || filtered.contains("午间") || filtered.contains("午餐") || filtered.contains("吃午饭") -> NOON
                filtered.contains("下午") || filtered.contains("午后") || filtered.contains("下午茶") || filtered.contains("未时") || filtered.contains("申时") -> AFTERNOON
                filtered.contains("傍晚") || filtered.contains("黄昏") || filtered.contains("日落") || filtered.contains("暮色") || filtered.contains("夕阳") || filtered.contains("晚霞") || filtered.contains("天色渐暗") || filtered.contains("酉时") -> DUSK
                filtered.contains("深夜") || filtered.contains("子时") || filtered.contains("半夜") || filtered.contains("凌晨") || filtered.contains("夜深人静") || filtered.contains("深更半夜") || filtered.contains("更深") -> LATE_NIGHT
                // 仅匹配明确表述当前进入夜晚/晚间时段的真实场景描写，严禁对单个“晚”或“夜”字符泛滥误判
                filtered.contains("夜幕降临") || filtered.contains("天黑了") || filtered.contains("天色已晚") || filtered.contains("天色已黑") ||
                filtered.contains("入夜") || filtered.contains("掌灯") || filtered.contains("吃晚餐") || filtered.contains("吃晚饭") ||
                filtered.contains("夜色深沉") || filtered.contains("月上中天") || filtered.contains("夜幕笼罩") || filtered.contains("准备就寝") ||
                filtered.contains("就寝休息") || filtered.contains("晚上好") || filtered.contains("晚间时分") ||
                (filtered.contains("晚上") && !filtered.contains("今晚去") && !filtered.contains("明晚") && !filtered.contains("等晚上")) ||
                (filtered.contains("夜晚") && (filtered.contains("降临") || filtered.contains("此时") || filtered.contains("笼罩") || filtered.contains("深沉"))) -> NIGHT
                else -> null
            }
        }
    }
}

/**
 * 时间线单条事件或时间锚定设定模型
 */
data class TimelineEventItem(
    val id: String = UUID.randomUUID().toString(),
    var timeTag: String = "",
    var content: String = "",
    var category: TimelineCategory = TimelineCategory.PLOT_EVENT
)

/**
 * 与具体时间无关的全局角色/世界设定模型（用于确认加入记忆）
 */
data class AtemporalSettingItem(
    val id: String = UUID.randomUUID().toString(),
    var category: String = "角色特质", // 6维/5维设定轮转
    var content: String = "",
    var isSelected: Boolean = true,
    var targetScope: String = "session", // "session" (会话专属记忆) 或 "global" (长期记忆)
    val action: String = "ADD",
    val targetMemoryId: Long? = null,
    val previousContent: String? = null,
    val evidence: String = "",
    val changeReason: String = ""
) {
    fun nextCategory(): String = when (category) {
        "角色设定", "角色特质" -> "习惯偏好"
        "习惯偏好" -> "生理禁忌"
        "生理禁忌" -> "世界规则"
        "世界规则" -> "人际羁绊"
        "人际羁绊" -> "角色特质"
        // 6维全称扩展
        "角色核心特质" -> "习惯与偏好"
        "习惯与偏好" -> "生理禁忌与弱点"
        "生理禁忌与弱点" -> "人际羁绊与契约"
        "人际羁绊与契约" -> "秘密揭露与真相"
        "秘密揭露与真相" -> "世界铁律与规则"
        "世界铁律与规则" -> "角色核心特质"
        else -> "角色特质"
    }
}

/**
 * 时间线全量校对提炼结果模型
 */
data class TimelineReconcileResult(
    var currentStoryTime: String = "",
    val events: MutableList<TimelineEventItem> = mutableListOf(),
    val atemporalSettings: MutableList<AtemporalSettingItem> = mutableListOf(),
    var extractionSource: String = "AI_MODEL", // "AI_MODEL" 或 "LOCAL_FALLBACK"
    var modelUsed: String = "",
    var extractionErrorMessage: String? = null,
    var lastProcessedMessageId: Long = 0L,
    var totalProcessedMessages: Int = 0
)

/**
 * 时间线记忆辅助类
 */
object TimelineMemoryHelper {

    /**
     * 提取结果只承载可复用事实，不承载小说式修辞。模型偶尔仍会把一整段描写放进字段，
     * 因此在入库前按完整句边界收敛长度；优先保留开头的事实句，绝不在半个词中间截断。
     */
    private const val MAX_EVENT_FACT_CHARS = 160
    private const val MAX_SETTING_FACT_CHARS = 96
    const val MAX_AUTO_EVENT_CHARS = 96

    /**
     * 自动增量只做保守的确定性清理：删除完整重复句和明确的寒暄/日常流水账。
     * 不用字符相似度推断同义，不按字数裁掉未知事实；超预算交给模型语义改写。
     * 手工梳理和已有节点不经过这一准入规则，避免追溯删除用户已确认的历史。
     */
    fun compactAutoTimelineEvent(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[\\t\\r ]+"), " ").trim()
        val sentences = splitAutoEventSentences(cleaned)
            .map { it.trim() }.filter { it.isNotBlank() }
            .distinct()
            .filterNot(::isRoutineOnlyAutoTimelineSentence)
        return sentences.joinToString("；")
    }

    private fun splitAutoEventSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val current = StringBuilder()
        var closingQuote: Char? = null
        for (index in text.indices) {
            val char = text[index]
            val openingQuote = closingQuoteAt(text, index)
            if (closingQuote != null) {
                current.append(char)
                if (char == closingQuote) closingQuote = null
            } else if (openingQuote != null) {
                closingQuote = openingQuote
                current.append(char)
            } else if (char in "。！？；\n") {
                // 问号表达未确认状态，不能把疑问改写成已经成立的事实。
                if (char == '？') current.append(char)
                sentences += current.toString()
                current.clear()
            } else current.append(char)
        }
        sentences += current.toString()
        return sentences
    }

    private fun closingQuoteAt(text: String, index: Int): Char? = when (text[index]) {
        '“' -> '”'
        '‘' -> '’'
        '「' -> '」'
        '『' -> '』'
        '[' -> ']'
        '【' -> '】'
        '"' -> '"'
        '\'' -> if (text.getOrNull(index - 1)?.let { it in 'A'..'Z' || it in 'a'..'z' || it.isDigit() } == true &&
            text.getOrNull(index + 1)?.let { it in 'A'..'Z' || it in 'a'..'z' || it.isDigit() } == true) null else '\''
        else -> null
    }

    private fun isRoutineOnlyAutoTimelineSentence(sentence: String): Boolean {
        // 只接受由已知日常动作构成的整句。含原因、承诺、否定、线索、数字等任何
        // 未知成分都会保留，例如“吃饭时发现药物中毒”绝不能按“吃饭”关键词丢弃。
        val subject = "(?:两人|双方|众人|大家|他|她|他们|她们)"
        val action = "(?:都|一起|继续|只是|随后|相互|互相)?(?:吃饭|用餐|吃早餐|吃午饭|吃晚饭|喝茶|喝水|散步|闲聊|寒暄|打招呼|休息|整理衣服|收拾餐具)(?:完毕|结束)?"
        val routine = Regex("^$subject$action(?:[，、]*(?:并|和|后|然后|接着|再)?(?:$subject)?$action)*$")
        val neutralDescription = Regex("""^(?:阳光洒进房间|月光照进房间|微风轻轻吹过|雨水轻敲窗户|灯光柔和|气氛轻松|气氛温馨)$""")
        return routine.matches(sentence) || neutralDescription.matches(sentence)
    }

    fun compactExtractedFact(raw: String, maxChars: Int = MAX_EVENT_FACT_CHARS): String {
        val cleaned = raw.replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= maxChars) return cleaned
        val sentences = cleaned.split(Regex("(?<=[。！？；.!?])\\s*"))
            .map { it.trim() }.filter { it.isNotBlank() }
        val complete = buildString {
            for (sentence in sentences) {
                if (isNotEmpty() && length + sentence.length > maxChars) break
                if (isEmpty() && sentence.length > maxChars) break
                if (isNotEmpty()) append(' ')
                append(sentence)
            }
        }.trim()
        if (complete.length >= 12) return complete
        val boundary = cleaned.take(maxChars).indexOfLast { it in "，、；：,;:" }
        return if (boundary >= 12) cleaned.take(boundary).trim() else cleaned.take(maxChars).trimEnd()
    }

    fun compactEventFact(raw: String): String = compactExtractedFact(raw, MAX_EVENT_FACT_CHARS)

    fun compactSettingFact(raw: String): String = compactExtractedFact(raw, MAX_SETTING_FACT_CHARS)

    /** Chinese bigrams and Latin words let an old named event compete with recent events. */
    fun contextTerms(text: String): Set<String> {
        val result = Regex("[a-zA-Z0-9_]{2,}").findAll(text.lowercase()).map { it.value }.toMutableSet()
        Regex("[\\p{IsHan}]{2,}").findAll(text).forEach { match ->
            match.value.windowed(2).forEach { result += it }
        }
        return result
    }

    fun selectTimelineNodesForContext(nodes: List<TimelineNode>, query: String, tokenBudget: Int): List<TimelineNode> {
        val ordered = nodes.sortedWith(compareBy<TimelineNode> { it.orderIndex }.thenBy { it.createdAt })
        val terms = contextTerms(query)
        val ranked = ordered.withIndex().sortedByDescending { (index, node) ->
            val relevance = contextTerms(node.event).count { it in terms }
            relevance * 8 + if (index >= ordered.size - 4) 20 + index - ordered.size else 0
        }
        var remaining = (tokenBudget - 64).coerceAtLeast(0)
        val selected = mutableSetOf<Int>()
        for ((index, node) in ranked) {
            val cost = com.aiassistant.data.repository.helpers.TokenEstimationHelper.estimateTokenCount("[${node.timeTag}] ${node.event}") + 12
            if (cost <= remaining) { selected += index; remaining -= cost }
        }
        return ordered.filterIndexed { index, _ -> index in selected }
    }

    // 匹配如 [第3天·傍晚]、[第3天]、[DAY 2]、[10月5日·上午]、[周三·晚上] 等时间标签
    private val TIME_TAG_PATTERN = Pattern.compile("""^[\s\[【](?:第\s*(\d+)\s*天(?:[·\s\-]([^\]】]+))?|DAY\s*(\d+)|([^\]】]+))[\]】]\s*(.*)$""", Pattern.CASE_INSENSITIVE)
    private val DAY_NUMBER_PATTERN = Pattern.compile("""(?:第\s*(\d+)\s*天|DAY\s*(\d+))""", Pattern.CASE_INSENSITIVE)
    private val CATEGORY_TAG_PATTERN = Pattern.compile("""^\[(剧情推进|规则约束|角色设定|剧情设定|固有设定)\]\s*(.*)$""")

    /**
     * 判断某文本是否为纯用户导演/作者剧情指导（而非故事发生的客观事实）
     * 增强版：同时支持带括号包裹（如 [接下来...]）与不带括号的自由口令（如 接下来让他们...）
     */
    fun isPureDirectorInstruction(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        val isEnclosed = (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
                         (trimmed.startsWith("【") && trimmed.endsWith("】")) ||
                         (trimmed.startsWith("(") && trimmed.endsWith(")")) ||
                         (trimmed.startsWith("（") && trimmed.endsWith("）"))
        val inside = if (isEnclosed && trimmed.length >= 2) {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else {
            trimmed
        }

        val directorPrefixes = listOf(
            "让", "请继续", "继续写", "继续剧情", "继续", "重写", "改写", "描写", "从配角视角", "视角切换",
            "增加", "冲突升级", "不要解决", "不要立即", "接下来让", "接下来", "推进", "剧情推进", "展开", "写一段",
            "安排", "设定为", "要求", "注意", "提示", "下文", "接上文", "剧情发展", "生成剧情", "只生成",
            "请让", "请描写", "请写", "切换视角", "换个语气", "延长内容", "缩短内容", "剧情提示", "导演指令", "导演："
        )
        if (directorPrefixes.any { inside.startsWith(it) }) return true

        val directorKeywords = listOf(
            "推进剧情", "不要解决", "冲突升级", "重写上一段", "延长内容", "缩短内容",
            "只生成角色对白", "只生成旁白", "增加环境描写", "做出符合性格的选择",
            "切换为第一人称", "切换为第三人称", "不要立即解决", "剧情走向", "写一段对话"
        )
        return directorKeywords.any { inside.contains(it) }
    }

    /**
     * 校验某事件文本是否非法或误截取了用户输入/导演指令（解决问题 1）
     * 坚决拦截将用户输入发言、口令原样截取为事件的错误现象
     */
    fun isInvalidOrUserInstructionEvent(content: String, userMessages: Collection<String> = emptyList()): Boolean {
        val trimmed = content.trim()
        if (trimmed.length < 3) return true

        // 1. 本身即为编剧/导演指令
        if (isPureDirectorInstruction(trimmed)) return true

        // 2. 含有出戏元词汇
        val metaKeywords = listOf("用户", "玩家", "指令", "提示词", "AI", "助手", "模型", "剧情提示", "导演要求")
        if (metaKeywords.any { trimmed.contains(it) }) return true

        // 3. 指令性祈使短语开头（非客观叙事）
        val instructionStarts = listOf("接下来", "继续写", "让两人", "让角色", "请让", "请继续", "要求", "重写", "改写", "让对方", "让大家")
        if (instructionStarts.any { trimmed.startsWith(it) }) return true

        // 4. 与用户历史发言进行比对，防止直接截取用户输入作为事件
        fun normalizedSpeech(text: String) = text.replace(Regex("""[\p{P}\p{Z}\s]"""), "")
        val cleanContent = normalizedSpeech(trimmed)
        for (userMsg in userMessages) {
            val cleanUser = normalizedSpeech(userMsg.trim())
            // 已发生的第三人称用户叙述同样是剧情来源。只防搬运导演口令/第一人称台词，
            // 不能因字符相似就拒绝客观事实改写。
            if (!isPureDirectorInstruction(userMsg) && !cleanUser.startsWith("我")) continue
            if (cleanUser.length >= 4) {
                // 完全相等或包含关系（长度接近）
                if (cleanContent == cleanUser) return true
                if (cleanContent.contains(cleanUser) && cleanContent.length <= cleanUser.length + 6) return true
                if (cleanUser.contains(cleanContent) && cleanUser.length <= cleanContent.length + 6) return true

            }
        }

        return false
    }

    /** 兼容旧调用的文本清理器。事件详略由原文信息决定，长度参数不再截断事实。 */
    @Suppress("UNUSED_PARAMETER")
    fun compactSentenceKeepComplete(text: String, maxIdealLen: Int = 28): String {
        var clean = text.trim()
            .replace(Regex("""^\[.*?\]\s*"""), "")
            .replace(Regex("""^【.*?】\s*"""), "")
            .replace(Regex("""^[，。！？、\s]+"""), "")
            .replace(Regex("""[，。！？、\s]+$"""), "")
            .replace(Regex("""["“”'’]"""), "")

        // 去除冗长无增量前缀
        val redundantPrefixes = listOf(
            "两人在对话中", "剧情展开为", "接下来的情节中", "在这段剧情里", "在此期间",
            "随后两人", "双方在交谈中", "经过一番商议", "在本次剧情中", "随后"
        )
        for (prefix in redundantPrefixes) {
            if (clean.startsWith(prefix) && clean.length > prefix.length + 6) {
                clean = clean.removePrefix(prefix).trim()
            }
        }

        return clean
    }

    /**
     * 判断并估算叙事时间跨度跃迁天数（例如：两周后、一个月后、暑假开始、新学期、数日后等）
     * 返回跃迁的天数（> 0），若非时间跨度则返回 0
     * 核心改进：严谨排除“两年前”、“这两天”、“数日前”、“持续数日”等非前向推进语境，杜绝异常时间暴跳！
     */
    @Deprecated("仅兼容历史工具调用；剧情提取、时间推进及上下文禁止用估算天数改写自然时间")
    fun estimateTimeSpanJumpDays(tag: String): Int {
        val clean = tag.trim().lowercase()

        // 1. 严格排除过去时态、范围修饰与非前向推进语境
        if (clean.contains("前") || clean.contains("回忆") || clean.contains("往事") ||
            clean.contains("这") || clean.contains("持续") || clean.contains("历经") || clean.contains("耗费")) {
            return 0
        }

        // 2. 仅匹配明确向前推进的时间跨度词
        return when {
            clean.contains("十年后") || clean.contains("10年后") || clean.contains("十年过后") || clean.contains("十年之") -> 3650
            clean.contains("五年后") || clean.contains("5年后") || clean.contains("五年过后") -> 1825
            clean.contains("四年后") || clean.contains("4年后") || clean.contains("四年过后") -> 1460
            clean.contains("三年后") || clean.contains("3年后") || clean.contains("三年过后") || clean.contains("三载后") -> 1095
            clean.contains("两年后") || clean.contains("2年后") || clean.contains("两年过后") || clean.contains("两载后") -> 730
            clean.contains("一年后") || clean.contains("1年后") || clean.contains("一年过后") || clean.contains("次年") -> 365
            clean.contains("数年后") || clean.contains("数载后") -> 730
            clean.contains("半年后") || clean.contains("半年过后") -> 180
            clean.contains("四个月后") || clean.contains("4个月后") -> 120
            clean.contains("三个月后") || clean.contains("3个月后") -> 90
            clean.contains("两个月后") || clean.contains("2个月后") || clean.contains("两个月过后") -> 60
            clean.contains("数月后") || clean.contains("数月过后") -> 60
            clean.contains("一个半月后") -> 45
            clean.contains("一个月后") || clean.contains("1个月后") || clean.contains("一月后") || clean.contains("次月") -> 30
            clean.contains("四周后") || clean.contains("4周后") -> 28
            clean.contains("三周后") || clean.contains("3周后") -> 21
            clean.contains("半个月后") || clean.contains("半月后") -> 15
            clean.contains("两周后") || clean.contains("两周过后") || clean.contains("2周后") -> 14
            clean.contains("数周后") || clean.contains("几周后") -> 14
            clean.contains("十天后") || clean.contains("10天后") -> 10
            clean.contains("九天后") || clean.contains("9天后") -> 9
            clean.contains("八天后") || clean.contains("8天后") -> 8
            clean.contains("一周后") || clean.contains("一周过后") || clean.contains("1周后") || clean.contains("一星期后") || clean.contains("七天后") -> 7
            clean.contains("六天后") || clean.contains("6天后") -> 6
            clean.contains("五天后") || clean.contains("5天后") -> 5
            clean.contains("四天后") || clean.contains("4天后") -> 4
            clean.contains("大后天") -> 3
            clean.contains("三天后") || clean.contains("3天后") || clean.contains("数日后") || clean.contains("几天后") || clean.contains("数天后") || (clean.contains("三天") && clean.contains("过")) -> 3
            clean.contains("两天后") || clean.contains("2天后") || clean.contains("隔天") || clean.contains("后天") -> 2
            // 完整独立的自然时间跨度标签兜底
            clean == "两周" || clean == "两周过后" || clean == "2周" -> 14
            clean == "一周" || clean == "一周过后" || clean == "1周" -> 7
            clean == "一个月" || clean == "1个月" -> 30
            clean == "半年" -> 180
            clean == "一年" -> 365
            else -> 0
        }
    }

    /**
     * 保留旧入口与存储格式，只规范显式天数的排版，不再以日序状态机改写文学时间。
     * 列表顺序代表叙事呈现顺序；自然跨度、章节锚点、倒叙与并行剧情均保留原标签。
     */
    fun normalizeMonotonicTimeline(events: List<TimelineEventItem>): List<TimelineEventItem> = events.map { item ->
        val tag = item.timeTag.trim().trim('[', ']', '【', '】')
        val explicitDay = Regex("""^(?:第\s*(\d+)\s*天|DAY\s*(\d+))(?:[·\s\-]*(.*))?$""", RegexOption.IGNORE_CASE).matchEntire(tag)
        val normalizedTag = if (explicitDay != null) {
            val day = explicitDay.groupValues[1].ifBlank { explicitDay.groupValues[2] }
            val phase = explicitDay.groupValues[3].trim()
            "第 $day 天" + if (phase.isBlank()) "" else "·$phase"
        } else tag
        item.copy(timeTag = normalizedTag)
    }

    /** 回忆的发生时间保留在事件中，但不把主线“现在”搬回过去。 */
    fun isRetrospectiveTime(timeTag: String): Boolean =
        listOf("回忆", "倒叙", "往事", "追忆", "闪回", "梦境", "假设", "计划").any { timeTag.contains(it) }

    /** 同一节点的增量补充只能增加信息；保留旧事实、反转经过与尚未收尾的线索。 */
    fun supplementEventContent(previous: String, incoming: String): String {
        val old = previous.trim()
        val addition = incoming.trim()
        if (old.isBlank()) return addition
        if (addition.isBlank()) return old
        val oldFact = normalizedFact(old)
        val newFact = normalizedFact(addition)
        if (oldFact == newFact) return old
        val neitherHasQuestions = oldFact.none { it == '？' || it == '?' } && newFact.none { it == '？' || it == '?' }
        if (neitherHasQuestions && oldFact.contains(newFact)) return old
        if (neitherHasQuestions && newFact.contains(oldFact)) return addition
        val sentences = old.split(Regex("""(?<=[。！？；])|\n+""")).map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        addition.split(Regex("""(?<=[。！？；])|\n+""")).map { it.trim() }.filter { it.isNotBlank() }.forEach { sentence ->
            if (sentences.none { normalizedFact(it) == normalizedFact(sentence) }) sentences += sentence
        }
        return sentences.joinToString("\n")
    }

    /**
     * 将原始记忆内容解析为结构化事件
     */
    fun parseContentToEvent(rawContent: String): TimelineEventItem {
        val trimmed = rawContent.trim()
        val matcher = TIME_TAG_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            val contentGroup = matcher.group(5).orEmpty()
            val fullTag = trimmed.substring(matcher.start(), matcher.end() - contentGroup.length)
                .trim('[', ']', '【', '】', ' ')
            var eventContent = contentGroup.trim()
            var cat = TimelineCategory.PLOT_EVENT

            val catMatcher = CATEGORY_TAG_PATTERN.matcher(eventContent)
            if (catMatcher.find()) {
                val catName = catMatcher.group(1)
                cat = TimelineCategory.fromKey(catName)
                eventContent = catMatcher.group(2)?.trim().orEmpty()
            } else if (eventContent.startsWith("【规则") || eventContent.startsWith("[规则")) {
                cat = TimelineCategory.RULE_CONSTRAINT
                eventContent = eventContent.replace(Regex("""^[\[【][^\]】]+[\]】]\s*"""), "").trim()
            } else if (eventContent.startsWith("【角色") || eventContent.startsWith("[角色")) {
                cat = TimelineCategory.CHARACTER_SETTING
                eventContent = eventContent.replace(Regex("""^[\[【][^\]】]+[\]】]\s*"""), "").trim()
            } else if (eventContent.startsWith("【设定") || eventContent.startsWith("[设定") || eventContent.startsWith("【世界")) {
                cat = TimelineCategory.WORLD_SETTING
                eventContent = eventContent.replace(Regex("""^[\[【][^\]】]+[\]】]\s*"""), "").trim()
            } else if (eventContent.startsWith("【固有") || eventContent.startsWith("[固有") || eventContent.startsWith("【常驻") || eventContent.startsWith("[常驻")) {
                cat = TimelineCategory.ATEMPORAL_SETTING
                eventContent = eventContent.replace(Regex("""^[\[【][^\]】]+[\]】]\s*"""), "").trim()
            }

            if (eventContent.isNotBlank()) {
                return TimelineEventItem(
                    timeTag = fullTag,
                    content = eventContent,
                    category = cat
                )
            }
        }
        return TimelineEventItem(
            timeTag = "",
            content = trimmed,
            category = TimelineCategory.PLOT_EVENT
        )
    }

    /**
     * 将时间标签、类别与事件内容格式化为标准化存储字符串
     */
    fun formatEventContent(timeTag: String, content: String, category: TimelineCategory = TimelineCategory.PLOT_EVENT): String {
        val cleanTag = timeTag.trim().trim('[', ']', '【', '】')
        val cleanContent = content.trim()
        val catPrefix = if (category != TimelineCategory.PLOT_EVENT) "[${category.displayName}] " else ""
        return if (cleanTag.isNotBlank()) {
            "[$cleanTag] $catPrefix$cleanContent"
        } else {
            "$catPrefix$cleanContent"
        }
    }

    /**
     * 剥离思考模型推理过程（<think>...</think> 或未闭合的截断思考流）
     */
    fun stripThinkingTags(text: String): String {
        if (!text.contains("<think", ignoreCase = true)) return text.trim()
        var cleaned = text.replace(Regex("""<think[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
        if (cleaned.contains("<think", ignoreCase = true)) {
            cleaned = cleaned.replace(Regex("""<think[\s\S]*$""", RegexOption.IGNORE_CASE), "")
        }
        return cleaned.trim()
    }

    /**
     * 增量提取的自然语言无变化判定（纯函数，可单测）：
     * 模型未按指令输出 NO_UPDATE 或 JSON 时的兜底。仅在未找到 JSON 时调用，
     * 因此不会误伤 JSON 正文中的措辞。
     */
    /** Conservative identity check: unknown or differently labelled times must never overwrite a node. */
    fun isSameTimelineEventTime(existing: String, incoming: String): Boolean {
        fun normalize(value: String) = value.trim().replace(Regex("""[\s·・：:]"""), "")
        val left = normalize(existing)
        val right = normalize(incoming)
        return left.isNotBlank() && left !in setOf("未确定", "未知") && left == right
    }

    fun isNoChangeLikeResponse(text: String): Boolean {
        if (text.contains("NO_UPDATE", ignoreCase = true)) return true
        val phrases = listOf(
            "无变化", "无推进", "无新增", "无更新", "无需更新", "无需记录", "无需提取",
            "不需要更新", "不需要记录", "没有变化", "没有推进", "没有新增", "没有更新",
            "保持原", "保持不变", "维持原", "维持不变", "不需更新", "不需记录", "不用更新"
        )
        return phrases.any { text.contains(it) }
    }

    /**
     * 解析模型输出（支持标准 JSON、Markdown 包裹的 JSON，以及行列表格式兜底）
     */
    fun parseModelOutput(rawOutput: String, fallbackCurrentTime: String? = null): TimelineReconcileResult {
        // 先剥离思考过程，防止模型推理过程中的花括号或心理分析文本污染 JSON 与行解析
        val stripped = stripThinkingTags(rawOutput)
        val trimmed = stripped.ifBlank { rawOutput.trim() }
        if (trimmed.isBlank()) {
            val safeTime = fallbackCurrentTime?.takeIf { it.isNotBlank() && it != "未确定" && it != "未知" } ?: "未确定"
            return TimelineReconcileResult(currentStoryTime = safeTime, events = mutableListOf())
        }

        // 1. 尝试提取 JSON 代码块或 JSON 字符串
        val jsonStr = extractJsonString(trimmed)
        if (jsonStr != null) {
            try {
                val jsonElement = JsonParser.parseString(jsonStr)
                if (jsonElement.isJsonObject) {
                    val obj = jsonElement.asJsonObject
                    val currentTime = obj.get("currentStoryTime")?.asString?.trim().orEmpty()
                    val eventsList = mutableListOf<TimelineEventItem>()
                    val atemporalList = mutableListOf<AtemporalSettingItem>()

                    // 解析时序事件
                    val eventsArray = obj.getAsJsonArray("timelineEvents")
                        ?: obj.getAsJsonArray("events")
                    if (eventsArray != null) {
                        for (item in eventsArray) {
                            if (item.isJsonObject) {
                                val itemObj = item.asJsonObject
                                val tag = itemObj.get("timeTag")?.asString?.trim()
                                    ?: itemObj.get("time")?.asString?.trim()
                                    ?: itemObj.get("day")?.asString?.trim().orEmpty()
                                val text = itemObj.get("content")?.asString?.trim()
                                    ?: itemObj.get("event")?.asString?.trim().orEmpty()
                                val catKey = itemObj.get("category")?.asString?.trim()
                                val cat = TimelineCategory.fromKey(catKey)
                                if (text.isNotBlank()) {
                                    eventsList.add(TimelineEventItem(timeTag = tag, content = compactEventFact(text), category = cat))
                                }
                            } else if (item.isJsonPrimitive) {
                                val text = item.asString.trim()
                                if (text.isNotBlank()) {
                                    eventsList.add(parseContentToEvent(text))
                                }
                            }
                        }
                    }

                    // 解析时间无关设定 (atemporalSettings)
                    val atemporalArray = obj.getAsJsonArray("atemporalSettings")
                        ?: obj.getAsJsonArray("settings")
                        ?: obj.getAsJsonArray("globalSettings")
                    if (atemporalArray != null) {
                        for (item in atemporalArray) {
                            if (item.isJsonObject) {
                                SessionSettingReconciler.parse(item.asJsonObject)?.let(atemporalList::add)
                            } else if (item.isJsonPrimitive) {
                                val text = item.asString.trim()
                                if (text.isNotBlank()) {
                                    atemporalList.add(
                                        AtemporalSettingItem(
                                            category = "角色核心特质",
                                            content = compactSettingFact(text),
                                            isSelected = true
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val normalizedEvents = normalizeMonotonicTimeline(eventsList)
                    val resolvedTime = if (currentTime.isNotBlank() && currentTime != "未确定" && currentTime != "未知" && !isRetrospectiveTime(currentTime)) {
                        currentTime
                    } else {
                        inferCurrentStoryTime(normalizedEvents, fallbackCurrentTime)
                    }
                    return TimelineReconcileResult(
                        currentStoryTime = resolvedTime,
                        events = normalizedEvents.toMutableList(),
                        atemporalSettings = atemporalList
                    )
                }
            } catch (_: Exception) {
                // JSON 解析失败降级走文本解析
            }
        }

        // 2. 纯文本行列表解析兜底
        return parseTextFallback(trimmed, fallbackCurrentTime)
    }

    /** 空数组也是有效的“无新事实”结果；格式失败不能冒充已处理完整片段。 */
    fun hasStructuredTimelinePayload(rawOutput: String): Boolean = runCatching {
        val json = extractJsonString(stripThinkingTags(rawOutput)) ?: return@runCatching false
        val element = JsonParser.parseString(json)
        element.isJsonObject && element.asJsonObject.let { obj ->
            listOf("timelineEvents", "events").any { obj.get(it)?.isJsonArray == true }
        }
    }.getOrDefault(false)

    /**
     * 仅验证本次模型请求的逐源覆盖，不限制旧节点、备份或草稿格式。
     * 每条源消息必须关联输出事实，或给出无新事实理由；输出事实也必须有来源。
     */
    fun hasCompleteSourceCoverage(rawOutput: String, expectedMessageIds: Collection<Long>): Boolean = runCatching {
        val json = extractJsonString(stripThinkingTags(rawOutput)) ?: return@runCatching false
        val obj = JsonParser.parseString(json).asJsonObject
        validateSourceCoverage(obj, expectedMessageIds)
    }.getOrDefault(false)

    private fun exactJsonLong(value: JsonElement?): Long? = runCatching {
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return@runCatching null
        value.asBigDecimal.longValueExact()
    }.getOrNull()

    private fun optionalJsonString(obj: JsonObject, key: String, default: String = ""): String {
        val value = obj.get(key) ?: return default
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$key 必须是字符串" }
        return value.asString.trim()
    }

    private fun validateSourceCoverage(obj: JsonObject, expectedMessageIds: Collection<Long>): Boolean {
        val coverage = obj.getAsJsonArray("sourceCoverage") ?: return false
        val events = obj.getAsJsonArray("timelineEvents") ?: return false
        val settings = obj.getAsJsonArray("atemporalSettings")
        val expected = expectedMessageIds.toSet()
        val reviewed = mutableSetOf<Long>()
        val sourcedEvents = mutableSetOf<Int>()
        val sourcedSettings = mutableSetOf<Int>()
        for (entry in coverage) {
            if (!entry.isJsonObject) return false
            val item = entry.asJsonObject
            val id = exactJsonLong(item.get("messageId")) ?: return false
            if (id !in expected || !reviewed.add(id)) return false
            fun indices(key: String): List<Int> {
                val value = item.get(key) ?: return emptyList()
                require(value.isJsonArray)
                return value.asJsonArray.map { index ->
                    val number = requireNotNull(exactJsonLong(index))
                    require(number in 0L..Int.MAX_VALUE.toLong())
                    number.toInt()
                }.also { require(it.distinct().size == it.size) }
            }
            val eventIndices = indices("eventIndices")
            val settingIndices = indices("settingIndices")
            if (eventIndices.any { it !in 0 until events.size() } ||
                settingIndices.any { it !in 0 until (settings?.size() ?: 0) }) return false
            val reason = optionalJsonString(item, "noFactReason")
            if (eventIndices.isEmpty() && settingIndices.isEmpty() && reason.isBlank()) return false
            sourcedEvents += eventIndices
            sourcedSettings += settingIndices
        }
        return reviewed == expected && sourcedEvents == (0 until events.size()).toSet() &&
            sourcedSettings == (0 until (settings?.size() ?: 0)).toSet()
    }

    /**
     * 新请求使用严格解析：同一 JSON 完成类型、精确整数、目标设定和来源校验后才生成结果。
     * 不走旧格式的行文本兜底，避免损坏 JSON 被当作剧情并推进已处理水线。
     */
    fun parseVerifiedModelOutput(
        rawOutput: String,
        expectedMessageIds: Collection<Long>,
        existingSettingIds: Collection<Long>,
        fallbackCurrentTime: String? = null
    ): TimelineReconcileResult? = runCatching {
        val json = extractJsonString(stripThinkingTags(rawOutput)) ?: return@runCatching null
        val obj = JsonParser.parseString(json).asJsonObject
        val currentTime = optionalJsonString(obj, "currentStoryTime")
        val rawEvents = obj.getAsJsonArray("timelineEvents") ?: return@runCatching null
        val rawSettings = obj.getAsJsonArray("atemporalSettings") ?: return@runCatching null
        val events = rawEvents.map { element ->
            require(element.isJsonObject)
            val item = element.asJsonObject
            val content = optionalJsonString(item, "content").also { require(it.isNotBlank()) }
            TimelineEventItem(timeTag = optionalJsonString(item, "timeTag"), content = compactEventFact(content),
                category = TimelineCategory.fromKey(optionalJsonString(item, "category")))
        }
        val knownTargets = existingSettingIds.toSet()
        val settings = rawSettings.map { element ->
            require(element.isJsonObject)
            val item = element.asJsonObject
            require(optionalJsonString(item, "content").isNotBlank())
            listOf("category", "evidence", "changeReason", "targetScope").forEach { optionalJsonString(item, it) }
            val action = optionalJsonString(item, "action", "ADD").uppercase(java.util.Locale.ROOT)
            require(action in setOf("ADD", "SUPPLEMENT", "REVISE"))
            val target = item.get("targetMemoryId")
            val targetId = if (action == "ADD") {
                require(target == null || target.isJsonNull)
                null
            } else {
                requireNotNull(exactJsonLong(target)).also { require(it > 0L && it in knownTargets) }
            }
            requireNotNull(SessionSettingReconciler.parse(item)).copy(targetMemoryId = targetId)
        }
        require(validateSourceCoverage(obj, expectedMessageIds))
        val normalizedEvents = normalizeMonotonicTimeline(events)
        val resolvedTime = currentTime.takeIf { it.isNotBlank() && it !in setOf("未确定", "未知") && !isRetrospectiveTime(it) }
            ?: inferCurrentStoryTime(normalizedEvents, fallbackCurrentTime)
        TimelineReconcileResult(currentStoryTime = resolvedTime, events = normalizedEvents.toMutableList(),
            atemporalSettings = settings.toMutableList())
    }.getOrNull()

    private fun extractJsonString(text: String): String? {
        val codeBlockRegex = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
        val match = codeBlockRegex.find(text)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1).trim()
        }
        return null
    }

    private fun parseTextFallback(text: String, fallbackCurrentTime: String? = null): TimelineReconcileResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        var currentStoryTime = ""
        val events = mutableListOf<TimelineEventItem>()
        val atemporalList = mutableListOf<AtemporalSettingItem>()

        var isParsingAtemporal = false

        for (line in lines) {
            val clean = line.removePrefix("-").removePrefix("*").trim()
            if (clean.contains("当前故事时间") || clean.contains("当前时间") || clean.contains("当前停留在")) {
                currentStoryTime = clean.substringAfter("：").substringAfter(":").trim()
                continue
            }
            if (clean.contains("时间无关") || clean.contains("全局设定") || clean.contains("固定规则") || clean.contains("角色特质") || clean.contains("常驻设定")) {
                isParsingAtemporal = true
                continue
            }

            if (isParsingAtemporal) {
                if (clean.isNotBlank()) {
                    val cat = when {
                        clean.contains("规则") || clean.contains("禁止") -> "世界铁律与规则"
                        clean.contains("生理") || clean.contains("禁忌") || clean.contains("弱点") -> "生理禁忌与弱点"
                        clean.contains("羁绊") || clean.contains("契约") || clean.contains("关系") -> "人际羁绊与契约"
                        clean.contains("秘密") || clean.contains("真相") || clean.contains("揭露") -> "秘密揭露与真相"
                        clean.contains("习惯") || clean.contains("偏好") -> "习惯与偏好"
                        else -> "角色核心特质"
                    }
                    atemporalList.add(
                        AtemporalSettingItem(
                            category = cat,
                            content = clean,
                            isSelected = true
                        )
                    )
                }
            } else {
                if (clean.startsWith("[") || clean.startsWith("【") || (clean.contains("第") && clean.contains("天"))) {
                    events.add(parseContentToEvent(clean))
                } else if (clean.length > 5) {
                    events.add(TimelineEventItem(timeTag = "", content = clean))
                }
            }
        }

        val normalizedEvents = normalizeMonotonicTimeline(events)
        val resolvedTime = if (currentStoryTime.isNotBlank() && currentStoryTime != "未确定" && currentStoryTime != "未知") {
            currentStoryTime
        } else {
            inferCurrentStoryTime(normalizedEvents, fallbackCurrentTime)
        }
        return TimelineReconcileResult(
            currentStoryTime = resolvedTime,
            events = normalizedEvents.toMutableList(),
            atemporalSettings = atemporalList
        )
    }

    /**
     * 根据事件列表推断当前故事时间：
     * 1. 优先取倒序最新发生的事件的时间标签（支持“两周过后”、“暑假开始”、“第 5 天·傍晚”等自然与显式时间）；
     * 2. 若无显式时间标签，倒序从事件正文中智能提取自然文学叙事时间线索；
     * 3. 若仍无，则安全继承既有历史故事节点，杜绝粗暴退回到“未确定”。
     */
    fun inferCurrentStoryTime(events: List<TimelineEventItem>, fallbackTime: String? = null): String {
        // 1. 倒序寻找最新发生的事件有效时间标签
        for (item in events.reversed()) {
            val tag = item.timeTag.trim().trim('[', ']', '【', '】')
            if (tag.isNotBlank() && tag != "未确定" && tag != "未知" && !isRetrospectiveTime(tag)) {
                return tag
            }
        }
        // 没有可确认的主线锚点时继承既有时间；正文提到的往事/计划不作为现在。
        return fallbackTime?.takeIf { it.isNotBlank() && it != "未知" } ?: "未确定"
    }

    /** 只解析独立明确日序，避免把“承接【第6天】·两周后”的参照误认成当前日序。 */
    private fun explicitStoryDay(timeTag: String): Int? {
        val tag = timeTag.trim().trim('[', ']', '【', '】')
        if (isRetrospectiveTime(tag) || listOf("后", "前", "周", "月", "年", "承接").any { tag.contains(it) }) return null
        val match = Regex("""^(?:第\s*(\d+)\s*天|DAY\s*(\d+))(?:[·\s\-].*)?$""", RegexOption.IGNORE_CASE).matchEntire(tag) ?: return null
        return match.groupValues[1].ifBlank { match.groupValues[2] }.toIntOrNull()
    }

    /** 当前时段只能来自时间锚点末端明确写出的时段，不能沿用参照事件或人物习惯。 */
    private fun explicitCurrentPhase(timeTag: String): DayPhase? {
        if (isRetrospectiveTime(timeTag)) return null
        val phase = Regex("""(?:^|[·\s])(?:清晨|早晨|早上|晨曦|破晓|上午|中午|正午|午间|下午|午后|傍晚|黄昏|入夜|晚上|夜晚|晚间|夜间|深夜|凌晨|拂晓)(?:时分|时刻)?$""")
            .find(timeTag.trim())?.value?.trim('·', ' ') ?: return null
        return DayPhase.inferFromText(phase)
    }

    /**
     * 计算事件时间相对于当前时间的相对参照（如：今天 / 昨天 / 2天前 / 约两周前 / 放假之初 / 上学期）
     * 涵盖具体数字天数推算与粗粒度/文学阶段节点相对推算
     */
    fun calculateRelativeTime(eventTimeTag: String, currentTimeTag: String): String? {
        val cleanEvent = eventTimeTag.trim().trim('[', ']', '【', '】')
        val cleanCurrent = currentTimeTag.trim().trim('[', ']', '【', '】')
        if (cleanEvent.isBlank() || cleanCurrent.isBlank()) return null
        if (cleanEvent == cleanCurrent && explicitStoryDay(cleanEvent) != null) return "今天"

        val eventDay = explicitStoryDay(cleanEvent)
        val currentDay = explicitStoryDay(cleanCurrent)

        // 1. 双方均包含具体天数：精准计算相对天数与周/月换算
        if (eventDay != null && currentDay != null) {
            val diff = currentDay - eventDay
            return when {
                diff == 0 -> "今天"
                diff == 1 -> "昨天"
                diff == 2 -> "前天"
                diff in 3..6 -> "${diff}天前"
                diff in 7..13 -> "约1周前"
                diff in 14..20 -> "约2周前"
                diff in 21..29 -> "约3周前"
                diff in 30..59 -> "约1个月前"
                diff in 60..89 -> "约2个月前"
                diff >= 90 -> "数月前（约${diff}天前）"
                diff == -1 -> "明天"
                diff in -6..-2 -> "${-diff}天后"
                diff in -13..-7 -> "约1周后"
                diff <= -14 -> "${-diff}天后"
                else -> null
            }
        }

        // 自然标签的参照点未必相同：“两周后”与“一个月后”不能直接相减。
        // 季节/假期长度也没有固定值，保留原标签交由模型根据原文理解。
        return null
    }

    /**
     * 判断文本是否属于关系剧变、重大冲突、生死转折或人生节点等核心里程碑事件（全域泛化模型）
     */
    fun isCoreMilestoneEvent(text: String): Boolean {
        val lower = text.lowercase(java.util.Locale.ROOT)
        val milestoneKeywords = listOf(
            // 1. 关系剧变与人际羁绊
            "在一起", "确立关系", "恋爱", "告白", "表白", "初遇", "相遇", "相识", "重逢",
            "决裂", "断交", "反目", "背叛", "结盟", "同盟", "契约", "立誓", "誓言", "结拜",
            "拜师", "收徒", "成婚", "成亲", "订婚", "结婚", "分道扬镳", "误会冰释", "收养",
            // 2. 重大冲突与决战转折
            "决战", "大战", "围攻", "伏击", "刺杀", "遇刺", "败北", "大捷", "破城", "突围",
            "坠崖", "封印", "解封", "陷害", "反叛", "称帝", "登基", "继位", "篡位", "即位",
            // 3. 生死境界与重大质变
            "战死", "牺牲", "阵亡", "陨落", "重伤", "残废", "复活", "苏生", "觉醒", "蜕变",
            "突破", "晋升", "飞升", "入魔", "顿悟", "痊愈", "失忆", "恢复记忆",
            // 4. 环境迁移与人生阶段
            "毕业", "开学", "结业", "入职", "离职", "放假", "启程", "远征", "远行",
            "流放", "迁徙", "定居", "灭门", "分家", "退隐", "出山", "归来"
        )
        return milestoneKeywords.any { lower.contains(it) }
    }

    /**
     * 判断某条记忆是否具有显式故事时间线事件特征
     */
    fun isExplicitTimelineEvent(content: String): Boolean {
        val trimmed = content.trim()
        val eventPrefix = Regex("""^[\[【](?:主线剧情|剧情事件|转折关键|关键事件)[\]】]\s*""").find(trimmed)
        if (eventPrefix != null) {
            return isExplicitTimelineEvent(trimmed.substring(eventPrefix.range.last + 1))
        }
        val matcher = TIME_TAG_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            // The parser accepts arbitrary bracketed tags; classification must not treat
            // setting categories such as 【角色特征】 as a story timestamp.
            val tag = trimmed.substringBefore(']').substringBefore('】')
                .trim().trimStart('[', '【').trim()
            return Regex("""^(?:第\s*[\d一二三四五六七八九十百零]+\s*(?:天|日|周|月|年)|DAY\s*\d+|\d{4}[-年/]\d{1,2}|\d{1,2}[:：]\d{2}|暑假|寒假|新学期|两周|数日|几天|清晨|早晨|上午|中午|午后|下午|傍晚|黄昏|晚上|夜晚|深夜|凌晨|当天|次日|翌日|次晨|翌晨|今天|昨天|明天|次周|一周后|三天后|未确定|未知)""", RegexOption.IGNORE_CASE)
                .containsMatchIn(tag)
        }
        return trimmed.startsWith("[第") || trimmed.startsWith("【第") ||
               trimmed.startsWith("[day", ignoreCase = true) ||
               trimmed.startsWith("[暑假") || trimmed.startsWith("[寒假") ||
               trimmed.startsWith("[新学期") || trimmed.startsWith("[两周") ||
               trimmed.startsWith("[数日") || trimmed.startsWith("[几天")
    }

    /**
     * 判断当前交互是否属于小说创作、剧情推进或角色扮演叙事
     * 用于严格隔离普通技术问答、代码编写、学术论文、日常闲聊等会话，杜绝无意义开销与时空看板误注入
     */
    fun isNarrativeOrCreativeTurn(userMessage: String, assistantReply: String): Boolean {
        val user = userMessage.trim().lowercase(java.util.Locale.ROOT)
        val assistant = assistantReply.trim().lowercase(java.util.Locale.ROOT)
        val combined = "$user $assistant"

        // 1. 明确的纯技术与编程特征检测（若包含明显编程代码、技术关键词、无任何剧情特征，则判定为非叙事）
        val technicalMarkers = listOf(
            "```java", "```kt", "```kotlin", "```python", "```c", "```cpp", "```js", "```ts", "```html", "```sql",
            "```bash", "```sh", "```powershell", "```json", "```xml", "```gradle",
            "fun ", "val ", "var ", "class ", "def ", "import ", "public class", "private val",
            "npm ", "pip ", "git commit", "docker ", "kubernetes", "select * from", "spring boot",
            "android studio", "build.gradle", "dependencies {", "targetsdk", "compilesdk"
        )
        val hasStrongTechnicalCode = technicalMarkers.count { combined.contains(it) } >= 2

        // 2. 明确的剧情、创作、小说与角色互动特征检测
        val narrativeMarkers = listOf(
            "剧情", "故事", "角色", "小说", "设定", "旁白", "主角", "配角", "世界观",
            "下一章", "上一章", "续写", "重写", "接上文", "视角", "对话", "神情", "神色",
            "眼神", "微笑", "叹息", "轻声", "低语", "沉声", "冷笑", "点头", "摇头",
            "转身", "走向", "离去", "拔出", "握紧", "脚步", "心头", "眸中", "眉宇",
            "天色", "夜幕", "清晨", "黄昏", "客栈", "学院", "殿堂", "宗门", "王朝",
            "第1天", "第2天", "第3天", "第 1 天", "第 2 天", "第 3 天", "两天后", "数日后"
        )
        val hasNarrativeCues = narrativeMarkers.any { combined.contains(it) }

        // 如果包含强编程代码且几乎没有叙事线索，绝非小说剧情
        if (hasStrongTechnicalCode && !hasNarrativeCues) return false

        // 3. 常见非剧情问答模式判定（如翻译、润色纯公文、学术解析）
        val pureFactualQueryMarkers = listOf(
            "帮我翻译", "请翻译", "解释一下这个报错", "这段代码什么意思", "怎么优化",
            "如何实现", "帮我写一个函数", "帮我写个脚本", "计算一下", "公式是", "总结这篇文章"
        )
        if (pureFactualQueryMarkers.any { user.startsWith(it) } && !hasNarrativeCues) {
            return false
        }

        // 4. 叙事对话或小说创作特征判断
        if (hasNarrativeCues) return true

        // 5. 对白形态检测（小说常用的引号对白交替格式，如 “……”、“……”）
        val dialogueQuotesCount = combined.count { it == '“' || it == '”' || it == '「' || it == '」' }
        if (dialogueQuotesCount >= 4) return true

        // 默认若无任何剧情痕迹，保守返回 false，不打扰普通会话
        return false
    }

    /**
     * 构建注入给大模型的完整时间线参照上下文（强化泛化时空参照系、全域关键里程碑看板与时空连贯性三大铁律）
     */
    fun buildTimelinePromptContext(currentStoryTime: String?, memoryContents: List<String>): String {
        if (memoryContents.isEmpty()) {
            val time = currentStoryTime?.trim()?.takeIf { it.isNotBlank() && it != "未确定" } ?: return ""
            return "【故事当前时间节点】：$time\n以此为当前时间参照；尚无已确认事件，不凭空补写历史。"
        }

        val effectiveCurrent = currentStoryTime?.trim()?.takeIf { it.isNotBlank() } ?: "未指定"
        val events = memoryContents.map { parseContentToEvent(it) }
        val sb = StringBuilder()

        // 1. 故事当前时间与时空看板
        sb.append("【故事当前时间节点】：$effectiveCurrent\n")
        sb.append("【故事当前时间节点与时空看板】：\n")
        sb.append("• 当前主线时间参照：$effectiveCurrent\n")

        val currentDay = explicitStoryDay(effectiveCurrent)

        val currentPhase = explicitCurrentPhase(effectiveCurrent)

        // 2. 全域重大里程碑与关键转折防漂移看板（泛化模型：涵盖人际剧变、冲突决战、生死境界、人生转折等）
        val coreMilestones = events.filter { isCoreMilestoneEvent(it.content) }
        if (coreMilestones.isNotEmpty()) {
            sb.append("• 核心关系与重大里程碑锚点（绝对禁止混淆时序！）：\n")
            coreMilestones.forEach { m ->
                val rel = calculateRelativeTime(m.timeTag, effectiveCurrent)
                val mDay = explicitStoryDay(m.timeTag)
                val distanceHint = when {
                    mDay != null && currentDay != null && currentDay > mDay -> {
                        val daysAgo = currentDay - mDay
                        if (daysAgo >= 2) " [注意：此事件发生在 $daysAgo 天前，距今已过去 $daysAgo 天（$daysAgo 个日夜），绝非昨天！绝非刚刚发生，严禁时序错乱！]"
                        else " [注意：发生在昨天]"
                    }
                    rel != null && rel != "今天" -> " [注意：此事件发生在 $rel，绝非昨天！绝非刚刚发生，严禁时序错乱！]"
                    else -> ""
                }
                sb.append("  ★ [${m.timeTag.ifBlank { "早期" }}] ${m.content}$distanceHint\n")
            }
        }

        // 3. 完整时间线与事件演进明细
        sb.append("\n【剧情推进时间线与日常演进明细】：\n")
        events.forEach { event ->
            val catPrefix = if (event.category != TimelineCategory.PLOT_EVENT) "【${event.category.displayName}】" else ""
            if (event.timeTag.isNotBlank()) {
                val rel = calculateRelativeTime(event.timeTag, effectiveCurrent)
                val relPrefix = if (rel != null) "（相对于当前：$rel）" else ""
                sb.append("- [${event.timeTag}]$relPrefix $catPrefix${event.content}\n")
            } else {
                sb.append("- $catPrefix${event.content}\n")
            }
        }

        // 4. 全域时空连贯性与叙事时序三大铁律（时序交互准则，必须无条件遵守）
        sb.append("\n【时间记忆权威声明】：以下时间线是该会话唯一权威的时间记忆。凡涉及“现在/今天/昨天/刚才/多久之前”的时间表述，必须先以当前故事时间节点（$effectiveCurrent）为基准推算后再作答，严禁凭感觉臆断时间、严禁把往事当作刚刚发生！\n")
        sb.append("【时空连贯性与日内时序守护铁律（时序交互准则，全域叙事时序三大铁律，大模型必须无条件遵守）】：\n")
        sb.append("1.【时序参照系与相对跨度守恒律】：当提及以往发生的人际变故、重大转折、盟约决裂、生死考验或往事经历时，必须以当前故事时间节点（$effectiveCurrent）为基准严格推算相对时间跨度。若某重大转折发生在多日、数周或数月前，严禁叙述为“昨天才发生”或“刚发生”；若发生在几天前，亦不得夸大为“多年以前”。\n")

        val inferredPhase = currentPhase
        if (inferredPhase != null) {
            val phaseName = inferredPhase.displayName
            sb.append("2.【日内时序与生理作息连贯律】：当前故事时段停留在【$phaseName】（典型活动：${inferredPhase.typicalActivities}）。")
            if (inferredPhase.order <= DayPhase.NOON.order) {
                sb.append("当前仍处于白天！若上一情境为早晨/吃早餐/上午行动，严格禁止在未描写数小时时间自然流逝（如“夕阳西下”、“待到夜幕降临”）的情况下，突兀跳跃到“天黑了/深夜入睡”！无时间证据时保持当前时段；正文明确推进了数小时、数周或数月时，直接尊重原文跨度，不受相邻时段限制。\n")
            } else {
                sb.append("剧情推进尊重原文的时间过渡；允许明确的跨日、周、月跳跃及带标记的倒叙，回忆不改变主线现在。\n")
            }
        } else {
            sb.append("2.【日内时序与生理作息连贯律】：当前锚点没有明确日内时段，不从旧事件、角色习惯或参照时间推断当前早晚。保留自然跨度与模糊程度，依据新正文继续推进，不逐日补齐。\n")
        }

        sb.append("3.【跨度锚点与宏观阶段连贯律】：当故事经历“几天后”、“数周后”、“暑假”或“新学期”等时间跨度跃迁时，角色心理状态、环境演变及事件沉淀必须符合该跨度长度，禁止在跃迁后仍表现得如同事件就在上一秒发生。\n")

        return sb.toString().trim()
    }

    /** Keep distinct facts intact. Shared subjects, prefixes or scenes do not establish identity. */
    fun mergeOrAppendEvent(
        existingEvents: List<TimelineEventItem>,
        incoming: TimelineEventItem,
        normalizeAtEnd: Boolean = true
    ): List<TimelineEventItem> {
        val result = existingEvents.map { it.copy() }.toMutableList()
        val content = incoming.content.trim()
        if (content.isBlank()) return result
        if (result.none { isSameTimelineEventTime(it.timeTag, incoming.timeTag) && isSameTimelineFact(it.content, content) }) {
            result.add(incoming.copy(content = content))
        }
        return if (normalizeAtEnd) normalizeMonotonicTimeline(result) else result
    }

    fun isSameTimelineFact(existing: String, incoming: String): Boolean =
        normalizedFact(existing).let { it.isNotBlank() && it == normalizedFact(incoming) }

    fun buildTimelineNodesPromptContext(nodes: List<TimelineNode>, currentStoryTime: String? = null): String {
        if (nodes.isEmpty() && currentStoryTime.isNullOrBlank()) return ""
        return buildString {
            append("<session_timeline>\n")
            if (!currentStoryTime.isNullOrBlank()) append("【当前故事时间节点】：$currentStoryTime\n")
            nodes.sortedWith(compareBy<TimelineNode> { it.orderIndex }.thenBy { it.createdAt }).forEachIndexed { index, node ->
                val tag = if (node.timeTag.isNotBlank()) "[${node.timeTag}] " else ""
                append("[${index + 1}] $tag【${TimelineCategory.fromKey(node.category).displayName}】${node.event.trim()}\n")
            }
            append("请严格基于该时序脉络推进。当前节点仅是交互开始时的基准；无时间证据时保持当前时段。明确的时间跳跃以新原文为准。节点编号是叙事顺序；时间标签才是故事发生时间，回忆/并行线不能据编号强行改为次日。\n")
            append("回忆、计划和假设不等于已发生；近期明确修订优先。保留周/月/模糊跨度和它们的参照事件，无法确定的日期不换算成第几天或昨天。这里只列出本轮相关节点，未列出的历史不代表不存在。\n")
            append("</session_timeline>")
        }
    }

    /**
     * 正文是否包含明确的大跨度时间流逝描写（作为同日跨多时段推进的唯一豁免依据）。
     * 单独提及“晚上/黄昏”的计划或往事不算，必须是实际发生的时段流转场景或时长描述。
     */
    fun hasExplicitTimePassageDescription(text: String): Boolean {
        val markers = listOf(
            "夕阳西下", "夜幕降临", "暮色四合", "华灯初上", "掌灯时分", "日落", "日暮",
            "天黑", "天色暗了下来", "天色已晚", "入夜", "夜幕", "深夜",
            "几个小时", "数小时", "半日", "半天", "一整天", "大半天", "整整一日",
            "不知不觉", "转眼间", "转眼之间", "不知过了多久", "漫长的一天"
        )
        return markers.any { text.contains(it) }
    }

    /**
     * 判断一次故事时间推进是否属于“同日内跨多个时段的跳跃式推进”且正文缺乏时间流逝描写依据。
     * 用于拦截模型过度执着推进时间（如早晨的事还在发生就推进到晚上）。
     * 跨天推进与无法解析时段的时间标签不在此守卫范围内（交由其它守卫与用户确认处理）。
     */
    fun isUnreasonableStoryTimeJump(
        currentStoryTime: String?,
        proposedStoryTime: String?,
        replyText: String
    ): Boolean {
        val from = currentStoryTime?.trim().orEmpty()
        val to = proposedStoryTime?.trim().orEmpty()
        if (from.isBlank() || to.isBlank() || from == to) return false
        val fromDay = explicitStoryDay(from)
        val toDay = explicitStoryDay(to)
        if (fromDay == null || toDay == null || fromDay != toDay) return false
        val fromPhase = DayPhase.inferFromText(from) ?: return false
        val toPhase = DayPhase.inferFromText(to) ?: return false
        return toPhase.order - fromPhase.order > 1 && !hasExplicitTimePassageDescription(replyText)
    }

    /**
     * 模型不可用时只采纳正文中可复核的显式过渡。它是安全兜底，不替代语义抽取，
     * 不读导演指令、不从早餐/晚餐等活动推断时钟，也不把模糊周月换算为天数。
     */
    @Suppress("UNUSED_PARAMETER")
    fun detectAutoStoryTimeAdvancement(
        currentStoryTime: String?,
        userMessage: String,
        assistantReply: String
    ): String? {
        val base = currentStoryTime?.trim()?.takeIf { it.isNotBlank() && it !in setOf("未确定", "未知") }
        val text = stripThinkingTags(assistantReply).trim()
        if (text.isBlank()) return null
        val explicitLabel = Regex("""^[\[【]([^\]】]+)[\]】]""").find(text)?.groupValues?.get(1)
        if (explicitLabel != null && !isRetrospectiveTime(explicitLabel) &&
            (DAY_NUMBER_PATTERN.matcher(explicitLabel).find() || Regex("""[年月周日天季]|清晨|傍晚|暑假|学期""").containsMatchIn(explicitLabel))) {
            return normalizeMonotonicTimeline(listOf(TimelineEventItem(timeTag = explicitLabel))).first().timeTag
                .takeIf { it != base }
        }
        val clauses = text.split(Regex("""[。！？；\n，,]""")).map { it.trim() }
        val span = Regex("""^(?:(又?过了|转眼(?:间)?(?:已)?(?:过了)?|一晃(?:已)?(?:过了)?|时间(?:又)?过去了)\s*)?((?:约|大约)?[一二两三四五六七八九十百半数几\d]+(?:到[一二两三四五六七八九十\d]+)?(?:个)?(?:星期|周|月|年|天|日))(?:\s*(后|过后|之后|过去了))?""")
        for (clause in clauses) {
            val match = span.find(clause) ?: continue
            if (match.groupValues[1].isBlank() && match.groupValues[3].isBlank()) continue
            if (clause.drop(match.value.length).trimStart().let { it.startsWith("再") || it.startsWith("会") || it.startsWith("要") }) continue
            val marker = match.value.trim()
            return if (base == null) marker else "承接【$base】·$marker"
        }
        val nextDay = clauses.firstOrNull { Regex("""^(?:一夜无话[，,]?)?(?:次日|翌日|第二天|次晨)""").containsMatchIn(it) }
        if (nextDay != null) {
            val day = base?.let(::explicitStoryDay)
            val phase = DayPhase.inferFromText(nextDay)?.displayName?.substringBefore("/")
            return if (day != null) "第 ${day + 1} 天" + (phase?.let { "·$it" } ?: "")
                else (base?.let { "承接【$it】·" } ?: "") + nextDay.takeWhile { it != '的' }.trim()
        }
        if (base == null || !hasExplicitTimePassageDescription(text)) return null
        val explicitPhase = when {
            listOf("夕阳西下", "暮色四合", "日落", "待到傍晚").any { text.contains(it) } -> "傍晚"
            listOf("夜幕降临", "直到晚上", "转眼到了晚上", "天色彻底黑了下来").any { text.contains(it) } -> "夜间"
            else -> return null
        }
        return base.substringBefore("·") + "·$explicitPhase"
    }

    /** Remove only exact duplicate facts at the same time; preserve every distinct statement for review. */
    fun consolidateFinalTimelineEvents(
        events: List<TimelineEventItem>,
        userMessages: Collection<String> = emptyList()
    ): List<TimelineEventItem> {
        val normalized = normalizeMonotonicTimeline(events.filter {
            it.content.isNotBlank() && !isInvalidOrUserInstructionEvent(it.content, userMessages)
        })
        val result = mutableListOf<TimelineEventItem>()
        for (item in normalized) {
            val content = compactEventFact(item.content)
            if (result.none { isSameTimelineEventTime(it.timeTag, item.timeTag) && isSameTimelineFact(it.content, content) }) {
                result.add(item.copy(content = content))
            }
        }
        return result
    }

    /**
     * 全局与时间无关设定深度汇总与语义去重（解决问题 2 与问题 3）
     * 消除分段提取导致的“几个极其相似的设定总结”现象
     */
    fun consolidateFinalAtemporalSettings(settings: List<AtemporalSettingItem>): List<AtemporalSettingItem> {
        return settings.filter { it.content.isNotBlank() && !isPureDirectorInstruction(it.content) }
            .map { it.copy(content = compactSettingFact(it.content)) }
            .distinctBy { Triple(it.targetScope, it.targetMemoryId, normalizedFact(it.content)) }
    }

    private fun normalizedFact(text: String): String = buildString {
        var closingQuote: Char? = null
        for (index in text.indices) {
            val char = text[index]
            val openingQuote = closingQuoteAt(text, index)
            if (closingQuote != null) {
                if (char == closingQuote) closingQuote = null else append(char)
            } else if (openingQuote != null) {
                closingQuote = openingQuote
            } else if (!char.isWhitespace() && char !in "，。！；;、[]【】") {
                append(char)
            }
        }
    }

    /**
     * 跨界事件与设定综合消歧与深度去重（彻底解决问题 3）
     * 消除“某个事实在时间线事件中作为动态剧情发生，同时又在常驻设定中机械重复记录”的现象
     */
    fun crossDeduplicateEventsAndSettings(
        events: List<TimelineEventItem>, settings: List<AtemporalSettingItem>
    ): Pair<List<TimelineEventItem>, List<AtemporalSettingItem>> {
        // Character overlap cannot establish equivalent meaning, subject, polarity or time.
        // Only remove an exact duplicate dynamic event; preserve static rules and reviewable contradictions.
        val eventContents = events.map { normalizedFact(it.content) }.toSet()
        val filtered = settings.filterNot { setting ->
            val dynamic = listOf("遇到", "前往", "答应", "决定", "来到", "结识", "达成", "结盟").any { setting.content.contains(it) }
            dynamic && normalizedFact(setting.content) in eventContents
        }
        return events to filtered
    }

    /**
     * 全局时间线梳理结果综合汇总收敛 Pass（端到端整合）
     * 1. 拦截用户输入与指令事件；
     * 2. 宏观合并同场景多轮碎事件并基于分句精简（防截断）；
     * 3. 设定深度去重；
     * 4. 事件与设定跨界去重消歧；
     */
    fun consolidateFinalReconcileResult(
        result: TimelineReconcileResult,
        userMessages: Collection<String> = emptyList()
    ): TimelineReconcileResult {
        // 1. 事件内部合并与精炼（带用户发言防截取）
        val mergedEvents = consolidateFinalTimelineEvents(result.events, userMessages)
        // 2. 设定内部语义去重
        val mergedSettings = consolidateFinalAtemporalSettings(result.atemporalSettings)
        // 3. 跨界事件与设定消歧去重
        val (finalEvents, finalSettings) = crossDeduplicateEventsAndSettings(mergedEvents, mergedSettings)

        val resolvedStoryTime = if (result.currentStoryTime.isNotBlank() && result.currentStoryTime != "未确定" && result.currentStoryTime != "未知") {
            result.currentStoryTime
        } else {
            inferCurrentStoryTime(finalEvents, null)
        }
        return result.copy(
            currentStoryTime = resolvedStoryTime,
            events = finalEvents.toMutableList(),
            atemporalSettings = finalSettings.toMutableList()
        )
    }

    fun cleanTimelineResiduesFromMemories(memories: List<String>): List<String> {
        return memories.filter { content ->
            val trimmed = content.trim()
            !trimmed.startsWith("【当前故事时间】：") &&
            !trimmed.startsWith("当前故事时间：") &&
            !isExplicitTimelineEvent(trimmed)
        }
    }

    /**
     * 长会话分段切片（用于分段梳理 Chunking Map 阶段）
     * 将长消息列表按照 chunkSize 切片，相邻切片之间保留 overlap 条重叠消息以保持时序上下文连续
     */
    fun chunkMessagesForAnalysis(messages: List<Message>, chunkSize: Int = 25, overlap: Int = 3): List<List<Message>> {
        if (messages.size <= chunkSize) return listOf(messages)
        val chunks = mutableListOf<List<Message>>()
        var startIndex = 0
        while (startIndex < messages.size) {
            val endIndex = (startIndex + chunkSize).coerceAtMost(messages.size)
            chunks.add(messages.subList(startIndex, endIndex))
            if (endIndex >= messages.size) break
            startIndex += (chunkSize - overlap).coerceAtLeast(1)
        }
        return chunks
    }
}

fun TimelineNode.toTimelineEventItem(): TimelineEventItem = TimelineEventItem(
    id = this.id.toString(),
    timeTag = this.timeTag,
    content = this.event,
    category = TimelineCategory.fromKey(this.category)
)

fun TimelineEventItem.toTimelineNode(conversationId: Long, orderIndex: Int): TimelineNode = TimelineNode(
    id = this.id.toLongOrNull() ?: 0L,
    conversationId = conversationId,
    timeTag = this.timeTag,
    event = this.content,
    category = this.category.name,
    orderIndex = orderIndex
)
