package com.aiassistant.utils

import com.aiassistant.domain.model.PendingMemoryCandidate
import java.util.Locale

object SmartMemoryExtractor {

    private val NEGATIVE_MARKERS = listOf(
        "不要记住", "别记住", "不用记", "不用记住", "不要保存", "别保存",
        "do not remember", "don't remember", "forget"
    )

    private val QUESTION_MARKERS = listOf(
        "吗", "？", "?", "怎么", "什么", "为什么", "如何", "是不是", "有没有",
        "能否", "可以吗", "记不记得", "哪样", "哪位", "几点", "多少", "哪些"
    )

    // 短暂瞬态日常寒暄/动作过滤（非长期事实）
    private val EPHEMERAL_MARKERS = listOf(
        "刚刚", "现在去", "准备去", "正在吃", "去睡觉了", "晚安", "早安", "你好",
        "在吗", "哈哈", "谢谢", "好的", "收到", "收到谢谢", "拜拜", "再见", "ok", "yes", "no"
    )

    // 单次任务动作词：如果包含这些词且没有显式“记住：”或持久指示，说明是普通单次请求，绝不提取为记忆
    private val SINGLE_TURN_TASK_VERBS = listOf(
        "帮我", "请写", "写一个", "写一段", "写篇", "解释一下", "分析一下", "翻译一下",
        "总结一下", "修改一下", "优化一下", "重构一下", "查找", "查一下",
        "看看", "算一下", "画一个", "推荐几个", "介绍一下", "讲讲", "怎么做", "列举一下",
        "排查一下", "修复一下"
    )

    fun extractCandidate(
        content: String,
        conversationId: Long = 0L,
        messageId: Long? = null
    ): PendingMemoryCandidate? {
        val trimmed = content.trim()
        if (trimmed.length < 4 || trimmed.length > 300) return null

        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. 过滤否定词
        if (NEGATIVE_MARKERS.any { lower.contains(it) }) return null

        // 2. 检查显式记忆指令（最高优先级）：例如 "请记住：..."、"牢记：..."、"记住我喜欢..."
        val explicitRememberRegex = Regex("""^(?:(?:请|麻烦|务必)?(?:记住|牢记|记下|记一下)[：:]?\s*)(.{4,120})$""")
        explicitRememberRegex.find(trimmed)?.let { match ->
            val fact = match.groupValues[1].trim()
            if (fact.isNotBlank()) {
                val cleanFact = sanitizeMetaLanguage(fact)
                val isConv = isConversationScoped(cleanFact.lowercase(Locale.ROOT))
                val isPref = listOf("喜欢", "偏好", "习惯", "希望", "要求", "必须", "注释", "语言", "回答", "代码").any { cleanFact.contains(it) }
                val isRoleOrWorld = listOf("身份", "角色", "设定", "关系", "视对方为", "哥哥", "姐姐", "妹妹", "弟弟", "恋人", "朋友", "搭档").any { cleanFact.contains(it) }
                val prefix = if (isRoleOrWorld) "角色设定：" else if (isPref) "用户偏好：" else "用户设定："
                return PendingMemoryCandidate(
                    distilledContent = "$prefix$cleanFact",
                    originalSnippet = trimmed.take(80),
                    suggestedScope = if (isConv) "conversation" else "user",
                    conversationId = conversationId,
                    sourceMessageId = messageId,
                    category = if (isRoleOrWorld) "PROJECT" else if (isPref) "PREFERENCE" else "FACT"
                )
            }
        }

        // 2.1 显式规则与设定：例如 "设定：在这个会话中始终使用中文"、"规则：不要输出解释"、"要求：代码附带类型标注"
        val explicitRuleRegex = Regex("""^(?:(?:会话|对话|当前)?(?:设定|规则|要求|约束)[：:]\s*)(.{4,120})$""")
        explicitRuleRegex.find(trimmed)?.let { match ->
            val rule = match.groupValues[1].trim()
            if (rule.isNotBlank()) {
                return PendingMemoryCandidate(
                    distilledContent = "会话规则：$rule",
                    originalSnippet = trimmed.take(80),
                    suggestedScope = "conversation",
                    conversationId = conversationId,
                    sourceMessageId = messageId,
                    category = "PREFERENCE"
                )
            }
        }

        // 2.2 用户偏好声明：例如 "我的偏好：优先使用Kotlin"、"习惯：回答简洁"
        val userPrefRegex = Regex("""^(?:(?:我的)?(?:偏好|习惯|喜好)(?:是|[：:])\s*)(.{3,80})$""")
        userPrefRegex.find(trimmed)?.let { match ->
            val pref = match.groupValues[1].trim()
            if (pref.isNotBlank()) {
                return PendingMemoryCandidate(
                    distilledContent = "用户偏好：$pref",
                    originalSnippet = trimmed.take(80),
                    suggestedScope = "user",
                    conversationId = conversationId,
                    sourceMessageId = messageId,
                    category = "PREFERENCE"
                )
            }
        }

        // 2.3 中括号结构化记忆提取（增加 Markdown 链接过滤、剧情推进过滤与记忆价值判定）
        val bracketRegex = Regex("""[\[【]([^\[\]【】]{2,100})[\]】]""")
        for (match in bracketRegex.findAll(trimmed)) {
            val inner = match.groupValues[1].trim()
            val endIdx = match.range.last + 1
            // 排除 Markdown 链接语法 [title](url)
            val isMarkdownLink = endIdx < trimmed.length && trimmed[endIdx] == '('
            if (!isMarkdownLink && !isCodeOrTechnicalNoise(inner) && isWorthBecomingMemory(inner)) {
                val (distilled, category) = refineMemoryContent(inner)
                if (distilled.isNotBlank() && isRelevantToOriginalContent(distilled, trimmed)) {
                    val isConv = isConversationScoped(inner.lowercase(Locale.ROOT))
                    return PendingMemoryCandidate(
                        distilledContent = distilled,
                        originalSnippet = trimmed.take(80),
                        suggestedScope = if (isConv) "conversation" else "user",
                        conversationId = conversationId,
                        sourceMessageId = messageId,
                        category = category
                    )
                }
            }
        }

        // 2.4 “注意”及衍生关键词提取（需求 2：以“注意”、“特别注意”、“请注意”等引出的记忆提取与提炼）
        val noticeRegex = Regex("""^(?:(?:另外|特别|务必|请)?注意|温馨提示|提示|Note)[：:，,\s]\s*(.{3,100})$""", RegexOption.IGNORE_CASE)
        noticeRegex.find(trimmed)?.let { match ->
            val noticeBody = match.groupValues[1].trim()
            if (!SINGLE_TURN_TASK_VERBS.any { noticeBody.startsWith(it) }) {
                val (distilled, category) = refineMemoryContent(noticeBody, defaultCategory = "PREFERENCE")
                if (distilled.isNotBlank()) {
                    val isConv = isConversationScoped(noticeBody.lowercase(Locale.ROOT))
                    return PendingMemoryCandidate(
                        distilledContent = distilled,
                        originalSnippet = trimmed.take(80),
                        suggestedScope = if (isConv) "conversation" else "user",
                        conversationId = conversationId,
                        sourceMessageId = messageId,
                        category = category
                    )
                }
            }
        }

        // 3. 过滤疑问句（疑问句绝不作为事实或偏好入库）
        if (QUESTION_MARKERS.any { trimmed.endsWith(it) || trimmed.contains(it) }) return null

        // 4. 过滤瞬态寒暄与日常聊天
        if (EPHEMERAL_MARKERS.any { lower.startsWith(it) || lower == it }) return null

        // 5. 过滤单次任务指令（非持久性任务）
        if (SINGLE_TURN_TASK_VERBS.any { lower.contains(it) }) {
            val hasDurableMarker = listOf("以后", "每次", "始终", "一直", "永远", "默认都", "时请", "请附带", "附带", "必须").any { lower.contains(it) }
            if (!hasDurableMarker) return null
        }

        val isConversationScoped = isConversationScoped(lower)
        val defaultScope = if (isConversationScoped) "conversation" else "user"

        // 6. 会话内项目架构或角色设定
        val projectStackRegex = Regex("""(?:当前项目|这个项目|本项目)的?(?:技术栈|架构|语言|主语言|核心依赖|框架|包名)?(?:是|使用|基于)\s*(.{3,60})""")
        projectStackRegex.find(trimmed)?.let { match ->
            val stack = match.groupValues[1].trim()
            return PendingMemoryCandidate(
                distilledContent = "会话事实：项目技术架构为「$stack」",
                originalSnippet = trimmed.take(80),
                suggestedScope = "conversation",
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "PROJECT"
            )
        }

        val sessionRoleRegex = Regex("""(?:在这个对话|在当前会话|在本对话)(?:中|里)?(?:，|,)?\s*(?:你是一个?|你的身份是|请扮演|你要作为)\s*(.{3,60})""")
        sessionRoleRegex.find(trimmed)?.let { match ->
            val role = match.groupValues[1].trim()
            return PendingMemoryCandidate(
                distilledContent = "会话设定：当前身份设定为「$role」",
                originalSnippet = trimmed.take(80),
                suggestedScope = "conversation",
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "PROJECT"
            )
        }

        // 7. 语言偏好
        if (lower.contains("中文") && (lower.contains("回答") || lower.contains("思考") || lower.contains("交流") || lower.contains("输出"))) {
            val hasThinking = lower.contains("思考")
            val distilled = if (hasThinking) {
                "用户偏好：要求模型始终使用中文进行思考与回答"
            } else {
                "用户偏好：要求模型始终使用中文回答问题"
            }
            return PendingMemoryCandidate(
                distilledContent = distilled,
                originalSnippet = trimmed.take(80),
                suggestedScope = defaultScope,
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "PREFERENCE"
            )
        }

        if ((lower.contains("英文") || lower.contains("english")) && (lower.contains("回答") || lower.contains("answer"))) {
            return PendingMemoryCandidate(
                distilledContent = "用户偏好：要求模型使用英文回答交流",
                originalSnippet = trimmed.take(80),
                suggestedScope = defaultScope,
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "PREFERENCE"
            )
        }

        // 8. 风格与格式偏好（注释、简短、精炼）
        if (lower.contains("注释") && (lower.contains("代码") || lower.contains("写代码"))) {
            return PendingMemoryCandidate(
                distilledContent = "用户偏好：提供代码实现时需附带详尽的中文注释",
                originalSnippet = trimmed.take(80),
                suggestedScope = defaultScope,
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "PREFERENCE"
            )
        }

        if (lower.contains("简短") || lower.contains("简洁") || lower.contains("精炼") || lower.contains("不要长篇大论") || lower.contains("直接给结论") || lower.contains("精简")) {
            return PendingMemoryCandidate(
                distilledContent = "用户偏好：回答需简短精炼，直奔主题，避免冗长说明",
                originalSnippet = trimmed.take(80),
                suggestedScope = defaultScope,
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "PREFERENCE"
            )
        }

        // 8.1 严禁省略代码与输出规范约束
        if (lower.contains("不要省略") || lower.contains("不要写todo") || lower.contains("完整代码") || lower.contains("严禁省略")) {
            return PendingMemoryCandidate(
                distilledContent = "用户偏好：输出代码时必须给出完整实现，严禁省略中间逻辑或使用TODO占位",
                originalSnippet = trimmed.take(80),
                suggestedScope = defaultScope,
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "PREFERENCE"
            )
        }

        // 8.2 负向约束（避免、切勿、禁止）
        val negativeConstraintRegex = Regex("""^(?:(?:请|务必)?(?:不要|切勿|避免|严禁|禁止)\s*)(.{3,60})$""")
        negativeConstraintRegex.find(trimmed)?.let { match ->
            val constraint = match.groupValues[1].trim()
            if (constraint.isNotBlank() && !SINGLE_TURN_TASK_VERBS.any { constraint.contains(it) }) {
                return PendingMemoryCandidate(
                    distilledContent = "行为约束：避免$constraint",
                    originalSnippet = trimmed.take(80),
                    suggestedScope = defaultScope,
                    conversationId = conversationId,
                    sourceMessageId = messageId,
                    category = "PREFERENCE"
                )
            }
        }

        // 9. 真实持久身份、姓名、职业与技术栈习惯
        val identityRegex = Regex("""(?:我叫|我的名字是|我名字叫)\s*([A-Za-z0-9\u4e00-\u9fa5]{2,10})""")
        identityRegex.find(trimmed)?.let { match ->
            val name = match.groupValues[1]
            return PendingMemoryCandidate(
                distilledContent = "用户事实：用户姓名/称呼为「$name」",
                originalSnippet = trimmed.take(80),
                suggestedScope = "user",
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "FACT"
            )
        }

        val careerRegex = Regex("""(?:我是(?:一名|一个)?)\s*([A-Za-z0-9\u4e00-\u9fa5\s]{0,25}?(?:工程师|程序员|开发者|架构师|学生|老师|设计师|产品经理|医生|律师|作家|学者|研究员))""")
        careerRegex.find(trimmed)?.let { match ->
            val job = match.groupValues[1].trim()
            return PendingMemoryCandidate(
                distilledContent = "用户事实：用户职业身份为「$job」",
                originalSnippet = trimmed.take(80),
                suggestedScope = "user",
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "FACT"
            )
        }

        // 9.1 个人常用技术栈与习惯
        val techHabitRegex = Regex("""(?:我(?:平时|经常|习惯|主要)?(?:使用|用|偏好|写))\s*([A-Za-z0-9\s#+.-]{0,30}?(?:Kotlin|Java|Python|Rust|Golang|Go|TypeScript|JavaScript|Compose|Jetpack Compose|React|Vue|Flutter|Swift|C\+\+|SQL))""", RegexOption.IGNORE_CASE)
        techHabitRegex.find(trimmed)?.let { match ->
            val tech = match.groupValues[1].trim()
            return PendingMemoryCandidate(
                distilledContent = "用户习惯：常用技术栈为「$tech」",
                originalSnippet = trimmed.take(80),
                suggestedScope = "user",
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "FACT"
            )
        }

        val envRegex = Regex("""(?:我的开发环境|我的操作系统|我的系统|我的电脑系统)(?:是|使用)\s*([A-Za-z0-9\s.]{3,30})""")
        envRegex.find(trimmed)?.let { match ->
            val os = match.groupValues[1].trim()
            return PendingMemoryCandidate(
                distilledContent = "用户环境：开发操作系统为「$os」",
                originalSnippet = trimmed.take(80),
                suggestedScope = "user",
                conversationId = conversationId,
                sourceMessageId = messageId,
                category = "FACT"
            )
        }

        // 10. 会话项目事实兜底
        if (isConversationScoped) {
            val cleanSnippet = trimmed.replace(Regex("""^(?:在|对于)?(?:当前项目|这个项目|本项目|这个对话|当前会话)[，,中里]?\s*"""), "")
            if (cleanSnippet.length in 6..60) {
                return PendingMemoryCandidate(
                    distilledContent = "会话事实：$cleanSnippet",
                    originalSnippet = trimmed.take(80),
                    suggestedScope = "conversation",
                    conversationId = conversationId,
                    sourceMessageId = messageId,
                    category = "PROJECT"
                )
            }
        }

        return null
    }

    fun isConversationScoped(lower: String): Boolean {
        return listOf("这个项目", "当前项目", "本项目", "这个对话", "当前会话", "本会话", "此会话", "该会话", "这个会话", "当前对话", "此对话", "该对话", "this project", "this conversation")
            .any { lower.contains(it) }
    }

    fun refineMemoryContent(rawText: String, defaultCategory: String = "FACT"): Pair<String, String> {
        var text = rawText.trim()
        val prefixRegex = Regex(
            """^(?:(?:根据|基于)(?:上述|以上|用户|对话|发言|聊天)?(?:内容|对话|记录)?(?:分析|提炼|总结|梳理)?(?:得出|得出如下|提炼出|提炼出如下|提炼如下|出如下|如下)?[：:]?\s*|(?:核心事实|重要事实|记忆事实|提取结果|提炼事实|用户事实|用户表示|用户提到|建议记住|需要记住|经分析(?:如下)?)[：:]?\s*|(?:记忆|设定|事实|注意|特别注意|务必注意|规则|要求|约束|偏好|提醒|提示|Note)[：:]\s*)""",
            RegexOption.IGNORE_CASE
        )
        val fillerRegex = Regex("""^(?:那个|就是|还有|请|麻烦|务必)\s*""")

        // 循环剥离直到稳定，彻底清除复合前缀（如“根据上述对话提炼出如下核心事实：”）
        var prevText = ""
        while (prevText != text) {
            prevText = text
            text = text.replace(Regex("""^[\s*\-•\d+.\s]+"""), "").trim()
            text = text.trim('[', ']', '【', '】', '`', '"', '\'', '“', '”', '，', ',', '。', '.', '；', ';', ':', '：')
            text = text.replace(prefixRegex, "").trim()
            text = text.replace(fillerRegex, "").trim()
        }

        if (text.length < 2) return "" to defaultCategory

        // 沉浸感净化：彻底消除“用户把AI当成...”、“用户要求模型...”等出戏的第三方技术元词汇
        text = sanitizeMetaLanguage(text)

        val isPreference = listOf("喜欢", "偏好", "习惯", "讨厌", "风格", "爱喝", "爱吃", "倾向", "简短", "精炼", "注释").any { text.contains(it) }
        val isConstraint = listOf("不要", "别", "禁止", "严禁", "必须", "避免", "务必", "始终", "格式", "规范", "限制", "不许", "不允许", "不得", "不准", "切勿", "称呼", "叫我", "自称").any { text.contains(it) }
        val isRoleOrWorld = listOf("身份", "角色", "设定", "世界观", "背景", "关系", "扮演", "你是一个", "你是", "视对方为", "哥哥", "姐姐", "妹妹", "弟弟", "恋人", "朋友", "搭档").any { text.contains(it) }

        return when {
            isConstraint -> "行为约束：$text" to "PREFERENCE"
            isRoleOrWorld -> "角色设定：$text" to "PROJECT"
            isPreference -> "用户偏好：$text" to "PREFERENCE"
            defaultCategory == "PROJECT" -> "会话事实：$text" to "PROJECT"
            defaultCategory == "PREFERENCE" -> "行为约束：$text" to "PREFERENCE"
            else -> "重要事实：$text" to "FACT"
        }
    }

    /**
     * 沉浸式元技术词汇净化过滤：
     * 彻底清除在角色扮演与日常深度对话中容易导致出戏的第三方旁白式技术词汇（如“用户把AI当成...”、“用户要求模型...”）。
     * 将其智能重构为沉浸自然的“视对方为...”、“互动时...”等设定与偏好表述。
     */
    fun sanitizeMetaLanguage(raw: String): String {
        var result = raw.trim()
        // 1. 净化关系与人设出戏表述：“用户把AI/模型当成/视为心爱的哥哥” -> “视对方为心爱的哥哥”
        result = result.replace(Regex("""(?:用户)?(?:把|将)(?:AI|ai|模型|大模型|助手|机器人)(?:当成|当做|当做是|当成是|视为|看作|看做|看成|设定为)\s*"""), "视对方为")
        result = result.replace(Regex("""(?:把|将)(?:AI|ai|模型|大模型|助手|机器人)(?:作为|认作)\s*"""), "视对方为")
        result = result.replace(Regex("""(?:用户)?希望(?:AI|ai|模型|大模型|助手|机器人)\s*"""), "期望在交流中")
        result = result.replace(Regex("""(?:用户)?要求(?:AI|ai|模型|大模型|助手|机器人)\s*"""), "要求在互动中")
        result = result.replace(Regex("""(?:AI|ai|模型|大模型|助手|机器人)(?:应该|应当|需|需要|必须)\s*"""), "互动时必须")
        return result
    }

    fun isCodeOrTechnicalNoise(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.all { it.isDigit() }) return true
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return true
        if (trimmed.equals("todo", ignoreCase = true) || trimmed.equals("fixme", ignoreCase = true)) return true
        return false
    }

    /**
     * 判定中括号识别出的内容是否真正值得沉淀为持久记忆（需求 1）
     * 区分普通剧情推进/瞬态动作描写/时间场景过渡与具有长期价值的设定/规则/偏好
     */
    fun isWorthBecomingMemory(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 3) return false
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. 剧情推进/动作描写/瞬时旁白/时间过渡/镜头转换特征词
        val plotProgressionMarkers = listOf(
            "此时", "这时", "随后", "紧接着", "突然", "片刻后", "过了一会儿", "不久后",
            "翌日", "第二天", "半小时后", "一小时后", "几天后", "镜头一转", "场景转换",
            "场景切换", "来到", "走进了", "走出了", "推开门", "转过身", "转头", "叹了一口气",
            "叹了口气", "叹气", "摇了摇头", "摇了头", "点了点头", "冷笑一声", "微微一笑",
            "笑了笑", "沉默片刻", "沉默良久", "深吸一口气", "皱了皱眉", "皱起眉头",
            "拔出", "握紧", "跳下", "冲向", "抱住", "看向", "望向", "倒在地上",
            "站起身", "坐下", "第一幕", "第二幕", "画外音", "动作描写", "心理描写",
            "旁白", "环境描写", "过场", "缓缓", "悄悄", "突然间", "猛然", "快步", "飞速"
        )
        val hasPlotProgression = plotProgressionMarkers.any { lower.contains(it) }

        // 2. 核心持久设定/偏好/约束/规则特征词（赋予内容长期记忆价值）
        val persistentValueMarkers = listOf(
            "设定", "身份", "职业", "性格", "特征", "背景", "关系", "好感", "能力", "异能",
            "技能", "武器", "装备", "弱点", "秘密", "禁忌", "雷区", "法则", "规则", "约束",
            "要求", "世界观", "阵营", "线索", "道具", "契约", "同盟", "宿敌", "喜欢", "讨厌",
            "偏好", "习惯", "爱吃", "爱喝", "过敏", "害怕", "必须", "严禁", "禁止", "不能",
            "始终", "记住", "牢记", "已知事实", "情报", "密码", "代号", "真名", "年龄", "住址"
        )
        val hasPersistentValue = persistentValueMarkers.any { lower.contains(it) }

        // 如果明确命中瞬态剧情推进/动作词，且缺乏强烈的长期设定/规则/偏好标签，则坚决排除
        if (hasPlotProgression && !hasPersistentValue) {
            return false
        }

        // 如果含有长期设定/偏好/约束特征词，判定为高价值记忆
        if (hasPersistentValue) {
            return true
        }

        // 结构化设定格式判定（如 [主角: 林渊]、[阵营 - 帝国反抗军]、[关系: 盟友] 等键值对设定）
        val isKeyValueStructure = trimmed.contains("：") || trimmed.contains(":") || trimmed.contains(" - ") || trimmed.contains("——")
        if (isKeyValueStructure && !hasPlotProgression) {
            return true
        }

        // 普通无特殊设定标识的陈述句或动作推进，默认不作为记忆
        return false
    }

    /**
     * 校验提取出的记忆事实与用户原始输入是否具有语义相关性（需求 1：杜绝不相关记忆或幻觉入库）
     */
    fun isRelevantToOriginalContent(distilled: String, originalContent: String): Boolean {
        if (originalContent.isBlank()) return false
        // 剥离前缀标签（如“用户偏好：”、“行为约束：”、“会话设定：”、“重要事实：”）
        val coreFact = distilled.replace(Regex("""^(?:用户|会话|行为|重要)?(?:偏好|约束|设定|事实|习惯|环境)[：:]\s*"""), "").trim()
        if (coreFact.length < 2) return false

        val cleanOriginal = originalContent.lowercase(Locale.ROOT)
        val cleanFact = coreFact.lowercase(Locale.ROOT)

        // 1. 若原文直接包含核心事实，完全相关
        if (cleanOriginal.contains(cleanFact)) return true

        // 2. 提取分词片段或 2-gram 字符片段，避免中文无空格分词导致长句不匹配
        val stopChars = setOf('的', '了', '是', '在', '也', '有', '和', '与', '于', '就', '不', '人', '都', '一', '个', '上', '很', '到', '说', '要', '去', '你', '我', '他', '她', '它', '这', '那', '被', '让', '把')

        // 词级匹配（英文或标点分词）
        val words = cleanFact.split(Regex("""[\s,，.。:：;；!！?？"“”'‘’\[\]【】()（）/、]+"""))
            .map { it.trim() }
            .filter { it.length >= 2 && !listOf("要求", "希望", "必须", "不要", "始终", "一个", "我们", "你们", "他们", "这个", "那个").contains(it) }

        if (words.any { cleanOriginal.contains(it) }) return true

        // 中文连续 2-gram 关键词校验
        val nGrams = (0..cleanFact.length - 2)
            .map { cleanFact.substring(it, it + 2) }
            .filter { gram -> gram.none { it in stopChars } && gram.all { it.isLetterOrDigit() } }

        return nGrams.any { cleanOriginal.contains(it) }
    }
}
