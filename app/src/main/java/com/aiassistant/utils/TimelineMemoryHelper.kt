package com.aiassistant.utils

import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.TimelineNode
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
    var targetScope: String = "session" // "session" (会话专属记忆) 或 "global" (长期记忆)
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
        val cleanContent = trimmed.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
        for (userMsg in userMessages) {
            val cleanUser = userMsg.trim().replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
            if (cleanUser.length >= 4) {
                // 完全相等或包含关系（长度接近）
                if (cleanContent == cleanUser) return true
                if (cleanContent.contains(cleanUser) && cleanContent.length <= cleanUser.length + 6) return true
                if (cleanUser.contains(cleanContent) && cleanUser.length <= cleanContent.length + 6) return true

                // Jaccard 字符交集比对
                val setA = cleanContent.toSet()
                val setB = cleanUser.toSet()
                val inter = setA.intersect(setB).size
                val union = setA.union(setB).size
                if (union > 0 && inter.toFloat() / union >= 0.52f && cleanContent.length in (cleanUser.length - 8)..(cleanUser.length + 8)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * 将长事件文本精炼收敛，坚决避免暴力劈砍导致句子被中间腰斩（解决问题 2）
     * 优先基于标点符号寻找完整语法分句，保持主谓宾完整，字数控制在 12~28 字最佳区间
     */
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

        if (clean.length <= maxIdealLen) return clean

        // 若超长，绝不可直接暴力劈断！基于标点符号寻找完整分句
        val punctuationIndices = mutableListOf<Int>()
        val punctuations = charArrayOf('，', '。', '；', '！', '？', '、')
        for (i in clean.indices) {
            if (punctuations.contains(clean[i])) {
                punctuationIndices.add(i)
            }
        }

        // 优先在 10..maxIdealLen 范围内寻找最近标点截断，保留完整句子
        val goodCut = punctuationIndices.lastOrNull { it in 10..maxIdealLen }
        if (goodCut != null) {
            return clean.substring(0, goodCut).trim()
        }

        // 寻找在 maxIdealLen..34 范围内的第一个标点断句（允许略微放宽以确保句意绝对完整不腰斩）
        val slightlyLongerCut = punctuationIndices.firstOrNull { it in maxIdealLen..34 }
        if (slightlyLongerCut != null) {
            return clean.substring(0, slightlyLongerCut).trim()
        }

        // 若没有标点，寻找核心连词断开
        val conjunctions = listOf("并", "且", "而", "但", "随后", "决定")
        for (conj in conjunctions) {
            val idx = clean.indexOf(conj)
            if (idx in 12..maxIdealLen) {
                return clean.substring(0, idx).trim()
            }
        }

        // 兜底截断：截取并在末尾修剪悬挂的连词或虚词
        val sub = clean.take(maxIdealLen)
        return sub.replace(Regex("""[的了着与和把在被向从到，、；]$"""), "").trim()
    }

    private fun getPhaseOrder(subPhase: String): Int {
        val clean = subPhase.trim()
        return when {
            clean.contains("早") || clean.contains("晨") || clean.contains("上午") || clean.contains("破晓") || clean.contains("拂晓") -> 1
            clean.contains("中") || clean.contains("正午") || clean.contains("午间") || clean.contains("晌午") -> 2
            clean.contains("下午") || clean.contains("午后") -> 3
            clean.contains("傍晚") || clean.contains("黄昏") || clean.contains("日落") || clean.contains("夕阳") -> 4
            clean.contains("晚") || clean.contains("夜") || clean.contains("宿") || clean.contains("子时") || clean.contains("掌灯") -> 5
            else -> 0
        }
    }

    /**
     * 判断并估算叙事时间跨度跃迁天数（例如：两周后、一个月后、暑假开始、新学期、数日后等）
     * 返回跃迁的天数（> 0），若非时间跨度则返回 0
     * 核心改进：严谨排除“两年前”、“这两天”、“数日前”、“持续数日”等非前向推进语境，杜绝异常时间暴跳！
     */
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
     * 单调递增时序状态机：
     * 1. 解决同日内多轮对话事件（如同一顿午餐、同一下午讨论）被机械识别为递增天数的严重割裂问题；
     * 2. 拥抱自然文学叙事与阶段锚点：全面兼容“两周过后”、“暑假开始”、“一年后·春”等自然时间标签。
     */
    fun normalizeMonotonicTimeline(events: List<TimelineEventItem>): List<TimelineEventItem> {
        var currentDay = 1
        var lastPhaseOrder = 0
        var hasSeenDayInCurrentEpoch = false
        val normalized = mutableListOf<TimelineEventItem>()

        for (item in events) {
            val tag = item.timeTag.trim()
            val matcher = DAY_NUMBER_PATTERN.matcher(tag)
            val isRelativeNextDay = tag.contains("第二天") || tag.contains("次日") || tag.contains("翌日") || tag.contains("隔天") || tag.contains("又过了一天")
            val spanJumpDays = estimateTimeSpanJumpDays(tag)

            val updatedTag = if (matcher.find()) {
                val parsedDay = (matcher.group(1) ?: matcher.group(2))?.toIntOrNull() ?: 1
                val subPhase = tag.substringAfter("·", "").ifBlank {
                    tag.substringAfter("天", "").trim('-', ' ', '·')
                }
                val phaseOrder = getPhaseOrder(subPhase)

                if (parsedDay > currentDay) {
                    currentDay = parsedDay
                    lastPhaseOrder = phaseOrder
                    hasSeenDayInCurrentEpoch = true
                } else if (parsedDay == currentDay) {
                    // 仅当包含明确的次日指示（如“第二天/次日”）且此前已记录过当前天数事件时，才推进到次日；
                    // 或前一事件已是入夜/深夜(order >= 4)，后一事件回到早晨/上午(order == 1)且包含过夜或次日指示时，才推进；
                    // 核心铁律：同一天内的同一时段（如吃饭点菜、就餐、餐桌交谈，或下午同一场会谈）绝对不累加天数！
                    if (isRelativeNextDay && hasSeenDayInCurrentEpoch) {
                        currentDay++
                        lastPhaseOrder = phaseOrder
                    } else if (lastPhaseOrder >= 4 && phaseOrder == 1 && (item.content.contains("睡") || item.content.contains("醒") || isRelativeNextDay)) {
                        currentDay++
                        lastPhaseOrder = phaseOrder
                    } else {
                        if (phaseOrder > lastPhaseOrder) {
                            lastPhaseOrder = phaseOrder
                        }
                        hasSeenDayInCurrentEpoch = true
                    }
                } else {
                    // parsedDay < currentDay
                    // 若此前发生过自然时间跨度跳跃（如“两周过后”），重置 epoch 后合理顺延
                    if (!hasSeenDayInCurrentEpoch) {
                        currentDay++
                    }
                    lastPhaseOrder = phaseOrder
                    hasSeenDayInCurrentEpoch = true
                }

                if (subPhase.isNotBlank()) "第 $currentDay 天·$subPhase" else "第 $currentDay 天"
            } else if (isRelativeNextDay) {
                currentDay++
                val subPhase = when {
                    tag.contains("早") || tag.contains("晨") || tag.contains("上午") -> "早晨"
                    tag.contains("午") -> "中午"
                    tag.contains("下午") -> "下午"
                    tag.contains("傍晚") || tag.contains("黄昏") -> "傍晚"
                    tag.contains("夜") || tag.contains("晚") -> "夜间"
                    else -> "白天"
                }
                lastPhaseOrder = getPhaseOrder(subPhase)
                hasSeenDayInCurrentEpoch = true
                "第 $currentDay 天·$subPhase"
            } else if (spanJumpDays > 0) {
                currentDay += spanJumpDays
                lastPhaseOrder = 0
                hasSeenDayInCurrentEpoch = false
                tag
            } else {
                val subPhase = tag.substringAfter("·", "")
                if (subPhase.isNotBlank()) {
                    lastPhaseOrder = getPhaseOrder(subPhase)
                }
                tag
            }

            normalized.add(item.copy(timeTag = updatedTag))
        }

        return normalized
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
            val safeTime = fallbackCurrentTime?.takeIf { it.isNotBlank() && it != "未确定" && it != "未知" } ?: "第 1 天·起始"
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
                                    eventsList.add(TimelineEventItem(timeTag = tag, content = text, category = cat))
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
                                val itemObj = item.asJsonObject
                                val cat = itemObj.get("category")?.asString?.trim() ?: "角色核心特质"
                                val text = itemObj.get("content")?.asString?.trim()
                                    ?: itemObj.get("setting")?.asString?.trim().orEmpty()
                                val scope = itemObj.get("targetScope")?.asString?.trim() ?: "session"
                                if (text.isNotBlank()) {
                                    atemporalList.add(
                                        AtemporalSettingItem(
                                            category = cat,
                                            content = text,
                                            isSelected = true,
                                            targetScope = if (scope == "global") "global" else "session"
                                        )
                                    )
                                }
                            } else if (item.isJsonPrimitive) {
                                val text = item.asString.trim()
                                if (text.isNotBlank()) {
                                    atemporalList.add(
                                        AtemporalSettingItem(
                                            category = "角色核心特质",
                                            content = text,
                                            isSelected = true
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val normalizedEvents = normalizeMonotonicTimeline(eventsList)
                    val resolvedTime = if (currentTime.isNotBlank() && currentTime != "未确定" && currentTime != "未知") {
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
            if (tag.isNotBlank() && tag != "未确定" && tag != "未知") {
                return tag
            }
        }
        // 2. 倒序从事件正文中嗅探文学/自然叙事时间锚点
        val timeRegex = Regex("""(第\s*\d+\s*天(?:[·\s\-][^，。；\s]+)?|[两三四五六七八九十\d]+[年月周天日]+[后过后之余]*|暑假(?:开始|首日|期间)?|寒假(?:开始|首日)?|新学期(?:伊始|首日)?|[春夏秋冬][季天]?·?[^，。；\s]*)""")
        for (item in events.reversed()) {
            val match = timeRegex.find(item.content)
            if (match != null && match.value.isNotBlank()) {
                return match.value.trim()
            }
        }
        // 3. 继承外部已知的既有故事驻留时间节点
        if (!fallbackTime.isNullOrBlank() && fallbackTime != "未确定" && fallbackTime != "未知") {
            return fallbackTime
        }
        return "第 1 天·起始"
    }

    /**
     * 计算事件时间相对于当前时间的相对参照（如：今天 / 昨天 / 2天前 / 约两周前 / 放假之初 / 上学期）
     * 涵盖具体数字天数推算与粗粒度/文学阶段节点相对推算
     */
    fun calculateRelativeTime(eventTimeTag: String, currentTimeTag: String): String? {
        val cleanEvent = eventTimeTag.trim().trim('[', ']', '【', '】')
        val cleanCurrent = currentTimeTag.trim().trim('[', ']', '【', '】')
        if (cleanEvent.isBlank() || cleanCurrent.isBlank()) return null
        if (cleanEvent == cleanCurrent) return "今天"

        val eventMatcher = DAY_NUMBER_PATTERN.matcher(cleanEvent)
        val currentMatcher = DAY_NUMBER_PATTERN.matcher(cleanCurrent)

        // 1. 双方均包含具体天数：精准计算相对天数与周/月换算
        if (eventMatcher.find() && currentMatcher.find()) {
            val eventDay = (eventMatcher.group(1) ?: eventMatcher.group(2))?.toIntOrNull() ?: return null
            val currentDay = (currentMatcher.group(1) ?: currentMatcher.group(2))?.toIntOrNull() ?: return null

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

        // 2. 粗粒度/文学与阶段性时间节点相对推导
        val currentJump = estimateTimeSpanJumpDays(cleanCurrent)
        val eventJump = estimateTimeSpanJumpDays(cleanEvent)

        // 语义级阶段匹配
        if (cleanCurrent.contains("新学期") || cleanCurrent.contains("开学")) {
            return when {
                cleanEvent.contains("暑假快结束") || cleanEvent.contains("暑假尾声") -> "暑假末尾（数天前）"
                cleanEvent.contains("放假首日") || cleanEvent.contains("暑假开始") || cleanEvent.contains("放假之初") -> "放假之初（约两个多月前）"
                cleanEvent.contains("暑假期间") || cleanEvent.contains("暑假中期") || cleanEvent.contains("暑假") -> "暑假期间（约1-2个月前）"
                cleanEvent.contains("上学期") || cleanEvent.contains("期末") || cleanEvent.contains("第 1 天") -> "上学期（数月前）"
                else -> "上一学期/假期"
            }
        }

        if (cleanCurrent.contains("暑假快结束") || cleanCurrent.contains("暑假尾声")) {
            return when {
                cleanEvent.contains("暑假开始") || cleanEvent.contains("放假首日") -> "暑假之初（约两个月前）"
                cleanEvent.contains("暑假期间") || cleanEvent.contains("暑假中期") -> "暑假中期（约一个月前）"
                cleanEvent.contains("第 1 天") || cleanEvent.contains("期末") -> "放假前（两个多月前）"
                else -> "暑假前期"
            }
        }

        if (cleanCurrent.contains("暑假期间") || cleanCurrent.contains("暑假中期") || cleanCurrent.contains("暑假过半")) {
            return when {
                cleanEvent.contains("放假首日") || cleanEvent.contains("暑假开始") || cleanEvent.contains("刚放假") -> "放假之初（约一个月前）"
                cleanEvent.contains("第 1 天") || cleanEvent.contains("学期") -> "放假前（一个多月前）"
                else -> "放假前夕"
            }
        }

        if (cleanCurrent.contains("暑假开始") || cleanCurrent.contains("放假首日") || cleanCurrent.contains("刚放假")) {
            return when {
                cleanEvent.contains("期末") || cleanEvent.contains("学期末") || cleanEvent.contains("学期") || cleanEvent.contains("第 1 天") -> "放假前"
                else -> "放假前"
            }
        }

        if (cleanCurrent.contains("深秋") || cleanCurrent.contains("中秋") || cleanCurrent.contains("入秋")) {
            return when {
                cleanEvent.contains("暑假") || cleanEvent.contains("夏天") -> "盛夏（约两三个月前）"
                cleanEvent.contains("新学期") || cleanEvent.contains("开学") -> "开学之初（约一个月前）"
                cleanEvent.contains("春天") || cleanEvent.contains("第 1 天") -> "春季（半年前）"
                else -> "上一季节"
            }
        }

        if (cleanCurrent.contains("寒假") || cleanCurrent.contains("新年") || cleanCurrent.contains("过年") || cleanCurrent.contains("春节")) {
            return when {
                cleanEvent.contains("新学期") || cleanEvent.contains("开学") -> "秋季学期之初（数月前）"
                cleanEvent.contains("暑假") -> "去年夏天（半年前）"
                cleanEvent.contains("深秋") -> "几个月前"
                else -> "去年/上学期"
            }
        }

        if (cleanCurrent.contains("来年春天") || cleanCurrent.contains("次年春")) {
            return when {
                cleanEvent.contains("寒假") || cleanEvent.contains("过年") || cleanEvent.contains("冬天") -> "上一冬季（两三个月前）"
                cleanEvent.contains("暑假") || cleanEvent.contains("夏天") -> "去年盛夏（大半年前）"
                cleanEvent.contains("第 1 天") || cleanEvent.contains("最初") -> "整整一年前"
                else -> "上一年度"
            }
        }

        // 自然相对跨度匹配
        if (cleanCurrent.contains("两周") && (cleanEvent.contains("第 1 天") || cleanEvent.contains("第1天") || cleanEvent.contains("初遇") || cleanEvent.contains("初期") || cleanEvent.contains("当初"))) {
            return "约两周前"
        }
        if (cleanCurrent.contains("一周") && (cleanEvent.contains("第 1 天") || cleanEvent.contains("第1天") || cleanEvent.contains("初遇"))) {
            return "约一周前"
        }
        if ((cleanCurrent.contains("几天后") || cleanCurrent.contains("数日后")) && (cleanEvent.contains("第 1 天") || cleanEvent.contains("前日"))) {
            return "数日前"
        }

        // 跨度换算推导
        if (currentJump > 0 && eventJump > 0) {
            val jumpDiff = currentJump - eventJump
            return when {
                jumpDiff == 0 -> "同一时期"
                jumpDiff in 1..2 -> "一两天前"
                jumpDiff in 3..6 -> "数天前"
                jumpDiff in 7..13 -> "约1周前"
                jumpDiff in 14..27 -> "约两周前"
                jumpDiff in 28..59 -> "约1个月前"
                jumpDiff in 60..89 -> "约2个月前"
                jumpDiff >= 90 -> "数月前"
                jumpDiff in -6..-1 -> "数天后"
                jumpDiff in -27..-7 -> "数周后"
                jumpDiff <= -28 -> "数月后"
                else -> null
            }
        }

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
        val matcher = TIME_TAG_PATTERN.matcher(trimmed)
        if (matcher.find()) return true
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
        if (memoryContents.isEmpty()) return ""

        val effectiveCurrent = currentStoryTime?.trim()?.takeIf { it.isNotBlank() } ?: "未指定"
        val events = memoryContents.map { parseContentToEvent(it) }
        val sb = StringBuilder()

        // 1. 故事当前时间与时空看板
        sb.append("【故事当前时间节点】：$effectiveCurrent\n")
        sb.append("【故事当前时间节点与时空看板】：\n")
        sb.append("• 当前绝对故事时间：$effectiveCurrent\n")

        val currentDayMatcher = DAY_NUMBER_PATTERN.matcher(effectiveCurrent)
        val currentDay = if (currentDayMatcher.find()) {
            (currentDayMatcher.group(1) ?: currentDayMatcher.group(2))?.toIntOrNull() ?: 1
        } else 1

        val currentPhase = DayPhase.inferFromText(effectiveCurrent)

        // 2. 全域重大里程碑与关键转折防漂移看板（泛化模型：涵盖人际剧变、冲突决战、生死境界、人生转折等）
        val coreMilestones = events.filter { isCoreMilestoneEvent(it.content) }
        if (coreMilestones.isNotEmpty()) {
            sb.append("• 核心关系与重大里程碑锚点（绝对禁止混淆时序！）：\n")
            coreMilestones.forEach { m ->
                val rel = calculateRelativeTime(m.timeTag, effectiveCurrent)
                val mMatcher = DAY_NUMBER_PATTERN.matcher(m.timeTag)
                val mDay = if (mMatcher.find()) (mMatcher.group(1) ?: mMatcher.group(2))?.toIntOrNull() else null
                val distanceHint = when {
                    mDay != null && currentDay > mDay -> {
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

        val lastEvent = events.lastOrNull { it.timeTag.isNotBlank() || it.content.isNotBlank() }
        val inferredPhase = currentPhase ?: lastEvent?.let { DayPhase.inferFromText(it.timeTag + " " + it.content) }
        if (inferredPhase != null) {
            val phaseName = inferredPhase.displayName
            sb.append("2.【日内时序与生理作息连贯律】：当前故事时段停留在【$phaseName】（典型活动：${inferredPhase.typicalActivities}）。")
            if (inferredPhase.order <= DayPhase.NOON.order) {
                sb.append("当前仍处于白天！若上一情境为早晨/吃早餐/上午行动，严格禁止在未描写数小时时间自然流逝（如“夕阳西下”、“待到夜幕降临”）的情况下，突兀跳跃到“天黑了/深夜入睡”！时间默认保持当前时段，仅当正文明确描写时间流逝或活动/场景结束时才顺延至相邻时段（单轮至多一段），严禁过早推进！\n")
            } else {
                sb.append("剧情推进必须保持时空自然过渡，严禁时序倒流或无逻辑时空突变！时间默认保持当前时段，仅当正文明确描写时间流逝或活动/场景结束时才顺延至相邻时段（单轮至多一段）。\n")
            }
        } else {
            sb.append("2.【日内时序与生理作息连贯律】：严格保持日内时间流逝与生活节律的自然过渡。若当前为早晨/白天，严禁在无明确时间流逝过渡描述的情况下突兀跳转至夜间就寝；若当前为深更半夜，亦严禁突兀转入次日白昼活动。时间默认保持当前时段，单轮至多顺延一个相邻时段。\n")
        }

        sb.append("3.【跨度锚点与宏观阶段连贯律】：当故事经历“几天后”、“数周后”、“暑假”或“新学期”等时间跨度跃迁时，角色心理状态、环境演变及事件沉淀必须符合该跨度长度，禁止在跃迁后仍表现得如同事件就在上一秒发生。\n")

        return sb.toString().trim()
    }

    /**
     * 智能增量合并或追加事件（解决多轮对话同一事件如吃饭、讨论被拆分重复冗余问题）
     * 如果传入事件与已有事件在同一时间节点且核心语义/实体重合度高，执行增量润色合并，否则追加。
     */
    fun mergeOrAppendEvent(
        existingEvents: List<TimelineEventItem>,
        incoming: TimelineEventItem,
        normalizeAtEnd: Boolean = true
    ): List<TimelineEventItem> {
        val result = existingEvents.map { it.copy() }.toMutableList()
        val incomingTag = incoming.timeTag.trim()
        val incomingContent = incoming.content.trim()
        if (incomingContent.isBlank()) return result

        val overlapKeywords = listOf(
            "早餐", "午餐", "晚餐", "吃饭", "就餐", "用餐", "点菜", "餐厅", "食堂", "茶馆", "酒楼", "同席", "聚餐", "喝茶", "饮茶",
            "在一起", "同行", "战斗", "交手", "逃跑", "商议", "讨论", "对策", "告白", "重逢", "初遇", "相遇", "车站", "学校", "离开", "到达"
        )

        // 寻找同时间或同天且语义高度重叠的已有条目
        val targetIndex = result.indexOfFirst { existing ->
            val tagMatch = (existing.timeTag.trim() == incomingTag && incomingTag.isNotBlank()) ||
                    (DAY_NUMBER_PATTERN.matcher(existing.timeTag).find() &&
                     DAY_NUMBER_PATTERN.matcher(incomingTag).find() &&
                     existing.timeTag.substringBefore("·") == incomingTag.substringBefore("·"))
            if (!tagMatch) return@indexOfFirst false

            // 计算关键词/字重叠
            val cleanExisting = existing.content.replace(Regex("""[，。！？、\s]"""), "")
            val cleanIncoming = incomingContent.replace(Regex("""[，。！？、\s]"""), "")

            val isSubset = cleanExisting.contains(cleanIncoming) || cleanIncoming.contains(cleanExisting)
            val keywordMatch = overlapKeywords.any { cleanExisting.contains(it) && cleanIncoming.contains(it) }

            isSubset || keywordMatch
        }

        if (targetIndex != -1) {
            val existing = result[targetIndex]
            val cleanExisting = existing.content.replace(Regex("""[，。！？、\s]"""), "")
            val cleanIncoming = incomingContent.replace(Regex("""[，。！？、\s]"""), "")
            val hasKeywordOverlap = overlapKeywords.any { cleanExisting.contains(it) && cleanIncoming.contains(it) }

            val mergedContent = when {
                existing.content == incomingContent -> existing.content
                existing.content.contains(incomingContent) -> existing.content
                incomingContent.contains(existing.content) -> incomingContent
                incomingContent.length >= existing.content.length &&
                    (cleanIncoming.contains(cleanExisting) || hasKeywordOverlap) -> incomingContent
                existing.content.length > incomingContent.length &&
                    (cleanExisting.contains(cleanIncoming) || hasKeywordOverlap) -> existing.content
                else -> "${existing.content}，且${incomingContent}"
            }
            result[targetIndex] = existing.copy(
                content = mergedContent,
                timeTag = if (incomingTag.isNotBlank() && incomingTag.contains("·")) incomingTag else existing.timeTag
            )
        } else {
            result.add(incoming)
        }

        return if (normalizeAtEnd) normalizeMonotonicTimeline(result) else result
    }

    fun buildTimelineNodesPromptContext(
        nodes: List<TimelineNode>,
        currentStoryTime: String? = null
    ): String {
        if (nodes.isEmpty() && currentStoryTime.isNullOrBlank()) return ""
        val sb = StringBuilder()
        sb.append("<session_timeline>\n")
        sb.append("以下是当前会话经过梳理确认的故事时间线脉络（按发生时序排列）：\n")
        if (!currentStoryTime.isNullOrBlank()) {
            sb.append("【当前故事时间节点】：$currentStoryTime\n")
        }
        val sortedNodes = nodes.sortedWith(compareBy<TimelineNode> { it.orderIndex }.thenBy { it.createdAt })
        // 降低模型注意力与 Token 负担：若条目较多，保留最新 15 条核心里程碑
        val displayNodes = if (sortedNodes.size > 15) sortedNodes.takeLast(15) else sortedNodes
        displayNodes.forEachIndexed { index, node ->
            val cat = TimelineCategory.fromKey(node.category)
            val tagStr = if (node.timeTag.isNotBlank()) "[${node.timeTag}] " else ""
            val cleanEvent = if (node.event.length > 32) compactSentenceKeepComplete(node.event, 28) else node.event
            sb.append("[${index + 1}] $tagStr【${cat.displayName}】$cleanEvent\n")
        }
        sb.append("【时间记忆权威声明】：本时间线与会话记忆是当前会话唯一权威的时间记忆。凡涉及“现在/今天/昨天/刚才/多久之前”等时间表述，必须先对照【当前故事时间节点】推算后再作答；严禁凭感觉或凭印象臆断时间，严禁把往事当作刚刚发生！\n")
        sb.append("【时序约束】：请严格基于该时序脉络推进，后续对话若发生时间推移请主动输出新时间节点。\n")
        sb.append("【时空自然推进与防篡改铁律（大模型必须严格遵循）】：\n")
        sb.append("1.【时空基准点与默认守时】：当前故事时空节点【${currentStoryTime ?: "未指定"}】仅代表本轮交互开始时的基准时空，绝非永恒固化的时间；但时间默认保持当前时段不变！仅当剧情明确描写了时间流逝、活动结束或场景转移时，才顺着相邻时段自然推进，且单轮至多推进一个相邻时段（如早晨→上午），严禁跳跃式推进！\n")
        sb.append("2.【防时段篡改铁律（核心）】：若当前时间设定为早晨/白天，严禁在正文没有描写数小时大跨度时间流逝（如夕阳西下、夜幕降临）的情况下，擅自将时间直接篡改为晚上或一直以晚上的口吻作答！普通交谈商议或提及过往往事（包括提及“今晚/晚上”的计划安排）绝不可作为入夜的依据！\n")
        sb.append("3.【自然流逝法则】：时间推进应当平滑自然（如早晨->上午->正午->下午->傍晚->入夜），剧情交谈完毕准备动身、就餐结束、转移场景或长途跋涉时，顺理成章地平滑推移至相邻时段，严禁一轮之内跨越多个时段。\n")
        sb.append("</session_timeline>")
        return sb.toString()
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
        val fromDay = DAY_NUMBER_PATTERN.matcher(from).let { m ->
            if (m.find()) (m.group(1) ?: m.group(2))?.toIntOrNull() else null
        }
        val toDay = DAY_NUMBER_PATTERN.matcher(to).let { m ->
            if (m.find()) (m.group(1) ?: m.group(2))?.toIntOrNull() else null
        }
        if (fromDay != null && toDay != null && fromDay != toDay) return false
        val fromPhase = DayPhase.inferFromText(from) ?: return false
        val toPhase = DayPhase.inferFromText(to) ?: return false
        return toPhase.order - fromPhase.order > 1 && !hasExplicitTimePassageDescription(replyText)
    }

    /**
     * 智能检测当前轮次对话中的时空推进
     * 解决模型错误地一直“停留在当前时空”、时空冻结的问题
     */
    fun detectAutoStoryTimeAdvancement(
        currentStoryTime: String?,
        userMessage: String,
        assistantReply: String
    ): String? {
        val baseTime = currentStoryTime?.trim()?.takeIf { it.isNotBlank() && it != "未确定" && it != "未知" } ?: "第 1 天·清晨"
        val combined = "$userMessage $assistantReply".trim()
        if (combined.length < 6) return null

        // 1. 显式跨天 / 相对跨度推进匹配
        val nextDayKeywords = listOf("第二天", "次日", "次晨", "翌日", "隔天", "又过了一天", "一夜无话", "睡下后", "一觉醒来", "次日清晨", "翌日清晨")
        val matcher = DAY_NUMBER_PATTERN.matcher(baseTime)
        val currentDay = if (matcher.find()) {
            (matcher.group(1) ?: matcher.group(2))?.toIntOrNull() ?: 1
        } else 1

        val spanJumpDays = estimateTimeSpanJumpDays(combined)
        if (spanJumpDays > 0) {
            val newDay = currentDay + spanJumpDays
            val inferredPhase = DayPhase.inferFromText(combined)?.displayName?.substringBefore("/") ?: "白天"
            return "第 $newDay 天·$inferredPhase"
        }

        if (nextDayKeywords.any { combined.contains(it) }) {
            val newDay = currentDay + 1
            val phase = DayPhase.inferFromText(combined)?.displayName?.substringBefore("/") ?: "清晨"
            return "第 $newDay 天·$phase"
        }

        // 2. 日内时段推移：当前已有时段 -> 文本中体现出向后时段推进
        val currentPhase = DayPhase.inferFromText(baseTime) ?: DayPhase.EARLY_MORNING
        val textPhase = DayPhase.inferFromText(combined)
        val dayPrefix = if (baseTime.contains("第") && baseTime.contains("天")) {
            baseTime.substringBefore("·").substringBefore("-").trim()
        } else {
            "第 $currentDay 天"
        }

        // 防虚假跳跃与篡改铁律：
        // 若当前处于早晨/上午，严禁在一轮常规对话中无日内变迁描写地直接跃迁至入夜/夜晚/深夜（跨越>=4个时段）！
        // 防过度推进（单步守时）：顺延超过一个相邻时段时，必须以正文明确的时间流逝描写为依据；
        // 否则不采纳（例如上午提及“今晚聚餐的计划”或回忆“去年黄昏的往事”绝不代表时段已流转），
        // 落空后交由第 3 段的单步活动顺延兜底。
        if (textPhase != null && textPhase.order > currentPhase.order) {
            val step = textPhase.order - currentPhase.order
            val isLeapToNight = (currentPhase == DayPhase.EARLY_MORNING || currentPhase == DayPhase.MORNING) &&
                (textPhase == DayPhase.NIGHT || textPhase == DayPhase.LATE_NIGHT)
            val hasExplicitEveningTransition = combined.contains("夕阳西下") || combined.contains("夜幕降临") ||
                combined.contains("直到晚上") || combined.contains("天色彻底黑了下来") || combined.contains("转眼到了晚上")

            val allowed = when {
                step <= 1 -> true
                isLeapToNight -> hasExplicitEveningTransition
                else -> hasExplicitTimePassageDescription(combined)
            }
            if (allowed) {
                val cleanPhase = textPhase.displayName.substringBefore("/")
                return "$dayPrefix·$cleanPhase"
            }
        }

        // 3. 典型活动结束触发顺延（例如：吃完早餐出发、会议结束、傍晚散场）
        val morningCompletionKeywords = listOf("吃完早餐", "吃完早点", "吃过早餐", "早餐过后", "晨间准备完毕", "动身出发", "走出客栈", "动身前往")
        val noonCompletionKeywords = listOf("吃完午饭", "吃过午餐", "午休结束", "午后出发")
        val duskCompletionKeywords = listOf("夜幕降临", "天色暗了下来", "掌灯时分", "夕阳西下", "晚霞消退", "华灯初上")
        val nightCompletionKeywords = listOf("回房就寝", "熄灯休息", "吹熄烛火", "互道晚安", "沉沉睡去", "进入梦乡")

        if (currentPhase == DayPhase.EARLY_MORNING && morningCompletionKeywords.any { combined.contains(it) }) {
            return "$dayPrefix·上午"
        }
        if (currentPhase == DayPhase.MORNING && (combined.contains("午餐") || combined.contains("吃午饭") || combined.contains("正午"))) {
            return "$dayPrefix·中午"
        }
        if (currentPhase == DayPhase.NOON && noonCompletionKeywords.any { combined.contains(it) }) {
            return "$dayPrefix·下午"
        }
        if (currentPhase.order <= DayPhase.AFTERNOON.order && duskCompletionKeywords.any { combined.contains(it) }) {
            return "$dayPrefix·傍晚"
        }
        if (currentPhase.order <= DayPhase.DUSK.order && (combined.contains("晚饭") || combined.contains("晚餐") || combined.contains("夜间"))) {
            return "$dayPrefix·入夜"
        }
        if (currentPhase == DayPhase.NIGHT && nightCompletionKeywords.any { combined.contains(it) }) {
            return "$dayPrefix·深夜"
        }
        if (currentPhase == DayPhase.LATE_NIGHT && (combined.contains("天亮") || combined.contains("破晓") || combined.contains("晨光"))) {
            return "第 ${currentDay + 1} 天·清晨"
        }

        return null
    }

    /**
     * 全局时间线事件深度汇总与去重压缩（解决问题 1 与问题 2）
     * 1. 将同一事件（例如同一顿饭、同一场战斗、同一个场景由多轮对话展开）高度凝练并合并为单个事件；
     * 2. 对跨分段提炼产生的表述极其相似的重复事件进行语义去重；
     * 3. 严格按时序单调排列，基于语法分句保持完整性，彻底杜绝腰斩截断与多余废话（控制在 12~28 字最佳区间）。
     */
    fun consolidateFinalTimelineEvents(
        events: List<TimelineEventItem>,
        userMessages: Collection<String> = emptyList()
    ): List<TimelineEventItem> {
        if (events.isEmpty()) return emptyList()
        // 过滤掉误截取用户指令或出戏元词汇的事件
        val validEvents = events.filter { !isInvalidOrUserInstructionEvent(it.content, userMessages) }
        val normalized = normalizeMonotonicTimeline(validEvents)
        val consolidated = mutableListOf<TimelineEventItem>()

        val sceneClusterKeywords = listOf(
            listOf("早餐", "早点", "晨餐", "早饭", "午餐", "午饭", "中饭", "晚餐", "晚饭", "夜宵", "晚宴", "用餐", "吃饭", "点菜", "餐厅", "餐馆", "食堂", "茶馆", "酒楼", "同席", "聚餐", "品茗", "小酌"),
            listOf("战斗", "交手", "对决", "交锋", "围攻", "遇袭", "伏击", "激战", "击败", "交战", "切磋", "冲突", "拔剑"),
            listOf("商议", "讨论", "对策", "计划", "筹划", "商谈", "谋划", "密谈", "交谈", "谈话", "长谈", "对质", "质询"),
            listOf("相遇", "初遇", "重逢", "碰面", "车站", "初识", "重聚", "相见", "偶遇", "初见"),
            listOf("同行", "结伴", "启程", "动身", "出发", "上路", "赶路", "散步", "漫步", "长街", "同游", "游览"),
            listOf("告白", "表白", "誓言", "立誓", "确立关系", "心意", "约定", "契约", "结盟", "立约", "同盟"),
            listOf("调查", "探查", "搜寻", "发现", "探秘", "查探", "潜入", "搜查", "打探", "暗访")
        )

        for (item in normalized) {
            val itemTag = item.timeTag.trim()
            val itemContent = compactSentenceKeepComplete(item.content)
            if (itemContent.isBlank()) continue

            // 寻找同日或同时间标签且具有相同场景/动作集群的已有事件
            val existingIdx = consolidated.indexOfFirst { existing ->
                val existingTag = existing.timeTag.trim()
                val isSameTimeScope = (existingTag == itemTag && itemTag.isNotBlank()) ||
                        (DAY_NUMBER_PATTERN.matcher(existingTag).find() &&
                         DAY_NUMBER_PATTERN.matcher(itemTag).find() &&
                         existingTag.substringBefore("·") == itemTag.substringBefore("·"))

                val cleanA = existing.content.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
                val cleanB = itemContent.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")

                // 1. 直接子集包含
                if (cleanA.contains(cleanB) || cleanB.contains(cleanA)) return@indexOfFirst true

                // 2. 字符交集相似度 (Jaccard)
                val setA = cleanA.toSet()
                val setB = cleanB.toSet()
                val intersection = setA.intersect(setB).size
                val union = setA.union(setB).size
                val similarity = if (union > 0) intersection.toFloat() / union else 0f

                if (isSameTimeScope && similarity >= 0.35f) return@indexOfFirst true
                if (!isSameTimeScope && similarity >= 0.60f) return@indexOfFirst true

                // 3. 场景关键词同义集群重合
                val sharesCluster = sceneClusterKeywords.any { cluster ->
                    cluster.any { cleanA.contains(it) } && cluster.any { cleanB.contains(it) }
                }
                if (isSameTimeScope && sharesCluster) return@indexOfFirst true
                if (!isSameTimeScope && sharesCluster && (similarity >= 0.22f || intersection >= 3)) return@indexOfFirst true

                false
            }

            if (existingIdx != -1) {
                val existing = consolidated[existingIdx]
                val cleanerTime = if (itemTag.contains("·") && !existing.timeTag.contains("·")) itemTag else existing.timeTag
                val higherCategory = if (item.category != TimelineCategory.PLOT_EVENT) item.category else existing.category

                val mergedCandidate = when {
                    existing.content == itemContent -> existing.content
                    existing.content.contains(itemContent) -> existing.content
                    itemContent.contains(existing.content) -> itemContent
                    existing.content.length in 8..24 && itemContent.length in 8..24 -> {
                        val commonEntities = existing.content.take(3)
                        if (itemContent.startsWith(commonEntities)) {
                            "${existing.content}，并${itemContent.removePrefix(commonEntities)}"
                        } else {
                            if (existing.content.length >= itemContent.length) existing.content else itemContent
                        }
                    }
                    existing.content.length >= itemContent.length -> existing.content
                    else -> itemContent
                }
                // 使用语法分句收束，保证主谓宾完整不腰斩
                val finalContent = compactSentenceKeepComplete(mergedCandidate, 28)
                consolidated[existingIdx] = existing.copy(
                    timeTag = cleanerTime,
                    content = finalContent,
                    category = higherCategory
                )
            } else {
                consolidated.add(item.copy(content = itemContent))
            }
        }

        return normalizeMonotonicTimeline(consolidated)
    }

    /**
     * 全局与时间无关设定深度汇总与语义去重（解决问题 2 与问题 3）
     * 消除分段提取导致的“几个极其相似的设定总结”现象
     */
    fun consolidateFinalAtemporalSettings(settings: List<AtemporalSettingItem>): List<AtemporalSettingItem> {
        if (settings.isEmpty()) return emptyList()
        val consolidated = mutableListOf<AtemporalSettingItem>()

        for (item in settings) {
            val content = compactSentenceKeepComplete(item.content, 26)
            if (content.isBlank() || isPureDirectorInstruction(content)) continue

            val cleanItem = content.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
            val existingIdx = consolidated.indexOfFirst { existing ->
                val cleanExisting = existing.content.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
                if (cleanExisting == cleanItem) return@indexOfFirst true
                if (cleanExisting.contains(cleanItem) || cleanItem.contains(cleanExisting)) return@indexOfFirst true

                // Jaccard 相似度比对
                val setA = cleanExisting.toSet()
                val setB = cleanItem.toSet()
                val inter = setA.intersect(setB).size
                val union = setA.union(setB).size
                val sim = if (union > 0) inter.toFloat() / union else 0f

                if (sim >= 0.40f) return@indexOfFirst true
                if (inter >= 4 && sim >= 0.28f) return@indexOfFirst true
                if (inter >= 3 && (cleanItem.length <= 8 || cleanExisting.length <= 8)) return@indexOfFirst true

                false
            }

            if (existingIdx != -1) {
                val existing = consolidated[existingIdx]
                val best = if (content.length in 6..26 && content.length > existing.content.length) content else existing.content
                consolidated[existingIdx] = existing.copy(content = best)
            } else {
                consolidated.add(item.copy(content = content))
            }
        }

        return consolidated
    }

    /**
     * 跨界事件与设定综合消歧与深度去重（彻底解决问题 3）
     * 消除“某个事实在时间线事件中作为动态剧情发生，同时又在常驻设定中机械重复记录”的现象
     */
    fun crossDeduplicateEventsAndSettings(
        events: List<TimelineEventItem>,
        settings: List<AtemporalSettingItem>
    ): Pair<List<TimelineEventItem>, List<AtemporalSettingItem>> {
        if (events.isEmpty() || settings.isEmpty()) return Pair(events, settings)

        val cleanEvents = events.toMutableList()
        val filteredSettings = mutableListOf<AtemporalSettingItem>()

        for (setting in settings) {
            val cleanSetting = setting.content.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
            if (cleanSetting.length < 3) continue

            // 检查是否有时间线事件与该设定本质一致
            val matchingEvent = cleanEvents.firstOrNull { event ->
                val cleanEvent = event.content.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
                // 1. 直接包含
                if (cleanEvent.contains(cleanSetting) || cleanSetting.contains(cleanEvent)) return@firstOrNull true

                // 2. 核心字符交集 Jaccard
                val setA = cleanSetting.toSet()
                val setB = cleanEvent.toSet()
                val inter = setA.intersect(setB).size
                val union = setA.union(setB).size
                val sim = if (union > 0) inter.toFloat() / union else 0f
                if (sim >= 0.45f) return@firstOrNull true
                if (inter >= 5 && sim >= 0.32f) return@firstOrNull true

                false
            }

            if (matchingEvent != null) {
                // 如果设定是静态规则（如生理禁忌、世界规则、纯属性），保留精炼表达；
                // 若设定是动态叙事/经历动作（如“在车站结识”、“前往酒楼”、“答应结盟”），则判定为与时间线事件本质完全一致的重复，剔除设定保留时间线！
                val isStrictStaticRule = setting.category in listOf("生理禁忌", "世界规则", "习惯偏好") &&
                        !setting.content.contains("遇到") && !setting.content.contains("前往") &&
                        !setting.content.contains("答应") && !setting.content.contains("决定") &&
                        !setting.content.contains("来到")
                if (isStrictStaticRule) {
                    filteredSettings.add(setting)
                } else {
                    // 动态动作事件已经在时间线编年表中准确记录时空，剔除设定的重复项！
                }
            } else {
                filteredSettings.add(setting)
            }
        }

        return Pair(cleanEvents, filteredSettings)
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
