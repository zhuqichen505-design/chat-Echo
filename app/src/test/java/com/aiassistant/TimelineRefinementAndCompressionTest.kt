package com.aiassistant

import com.aiassistant.domain.model.Message
import com.aiassistant.utils.*
import org.junit.Assert.*
import org.junit.Test

class TimelineRefinementAndCompressionTest {

    // 1. 日内时段细分状态机测试：验证 DayPhase 推断与防突兀跨越时段
    @Test
    fun testDayPhaseDetectionAndDescriptions() {
        val morningText = "晨光微熹，两人一起坐在餐桌前吃着热气腾腾的早餐。"
        val phaseMorning = DayPhase.inferFromText(morningText)
        assertNotNull("吃早餐、晨光应当推断为早晨时段", phaseMorning)
        assertEquals(DayPhase.EARLY_MORNING, phaseMorning)
        assertEquals("清晨/早晨", phaseMorning?.displayName)

        val afternoonText = "午后的阳光透过窗棂洒在书桌上，两人喝着红茶聊起接下来的打算。"
        val phaseAfternoon = DayPhase.inferFromText(afternoonText)
        assertEquals(DayPhase.AFTERNOON, phaseAfternoon)
        assertEquals("下午", phaseAfternoon?.displayName)

        val duskText = "夕阳西下，天边的晚霞被染成金红色，两人漫步在海边。"
        val phaseDusk = DayPhase.inferFromText(duskText)
        assertEquals(DayPhase.DUSK, phaseDusk)
        assertEquals("傍晚/黄昏", phaseDusk?.displayName)

        val nightText = "夜幕降临，街灯初上，两人互道晚安准备入睡。"
        val phaseNight = DayPhase.inferFromText(nightText)
        assertEquals(DayPhase.NIGHT, phaseNight)
        assertEquals("入夜/晚间", phaseNight?.displayName)

        val lateNightText = "时钟指向凌晨两点，窗外一片寂静。"
        val phaseLateNight = DayPhase.inferFromText(lateNightText)
        assertEquals(DayPhase.LATE_NIGHT, phaseLateNight)
        assertEquals("深夜/拂晓", phaseLateNight?.displayName)
    }

    // 2. 时空看板与时序守护上下文构建测试：验证绝对跨度、防“昨天”漂移与日序铁律
    @Test
    fun testBuildTimelinePromptContextWithMilestoneAndPhaseGuard() {
        val memoryContents = listOf(
            "[第1天] 男女主正式确立恋爱关系",
            "[第3天 下午] 两人共同参加了大学同学聚会",
            "[第5天 清晨] 两人吃完早餐商量周末旅行计划"
        )

        val currentStoryTime = "第5天 清晨"
        val contextPrompt = TimelineMemoryHelper.buildTimelinePromptContext(currentStoryTime, memoryContents)

        // 验证置顶看板
        assertTrue("必须包含故事当前时间节点与时空看板", contextPrompt.contains("【故事当前时间节点与时空看板】"))
        assertTrue("必须标明当前故事时间为第5天 清晨", contextPrompt.contains("第5天 清晨"))

        // 验证关键里程碑防漂移看板（第1天确立关系，当前第5天，跨度为4天，绝非昨天）
        assertTrue("必须包含关键里程碑置顶防漂移看板", contextPrompt.contains("核心关系与重大里程碑锚点（绝对禁止混淆时序！）："))
        assertTrue("确立关系里程碑必须计算并提示距今跨度", contextPrompt.contains("距今已过去 4 天") && contextPrompt.contains("绝非昨天！"))

        // 验证日内时序守护铁律
        assertTrue("必须包含时空连贯性与日内时序守护铁律", contextPrompt.contains("【时空连贯性与日内时序守护铁律"))
        assertTrue("当前为清晨时必须明确指示日内生理与时序连贯要求", contextPrompt.contains("当前故事时段停留在【清晨/早晨】"))
        assertTrue("严禁无过渡突兀跳跃至天黑入睡", contextPrompt.contains("严格禁止在未描写数小时时间自然流逝"))
    }

    // 3. 事件增量去重与智能润色合并测试：多轮同一事件合并，不同事件正常追加
    @Test
    fun testMergeOrAppendEventDeduplication() {
        val existingEvents = listOf(
            TimelineEventItem(
                timeTag = "第1天 傍晚",
                content = "男女主在车站告别",
                category = TimelineCategory.PLOT_EVENT
            )
        )

        // 情况一：同一天傍晚，相同核心语义“在车站告别”，但提供了更丰富的细节描述
        val enrichedEvent = TimelineEventItem(
            timeTag = "第1天 傍晚",
            content = "男女主在车站依依不舍告别，并约定周末再次见面",
            category = TimelineCategory.PLOT_EVENT
        )

        val mergedEvents = TimelineMemoryHelper.mergeOrAppendEvent(existingEvents, enrichedEvent)
        assertEquals("事件总数不应增加，保持为 1", 1, mergedEvents.size)
        assertEquals("事件应更新为细节更丰富的描述", "男女主在车站依依不舍告别，并约定周末再次见面", mergedEvents[0].content)

        // 情况二：不同天或不同事件，应当追加
        val newDayEvent = TimelineEventItem(
            timeTag = "第2天 上午",
            content = "男主收到女主发来的早安问候",
            category = TimelineCategory.PLOT_EVENT
        )

        val appendedEvents = TimelineMemoryHelper.mergeOrAppendEvent(mergedEvents, newDayEvent)
        assertEquals("追加后事件总数应为 2", 2, appendedEvents.size)
        assertEquals("第二条事件时间锚点经标准化为第 2 天·上午", "第 2 天·上午", appendedEvents[1].timeTag)
    }

    // 4. 长对话切片分段与滑动窗口测试：验证分段数量与连续性重叠
    @Test
    fun testChunkMessagesForAnalysisWithOverlap() {
        val dummyMessages = (1..60).map { i ->
            Message(
                id = i.toLong(),
                conversationId = 1L,
                role = if (i % 2 == 1) "user" else "assistant",
                content = "第 $i 轮对话内容，测试时间线推进与剧情发展..."
            )
        }

        val chunks = TimelineMemoryHelper.chunkMessagesForAnalysis(dummyMessages, chunkSize = 25, overlap = 3)
        assertEquals("60 条消息按 25 条一组、3 条重叠应当被切为 3 个片段", 3, chunks.size)

        // 验证各片段大小
        assertEquals("第一段包含前 25 条消息", 25, chunks[0].size)
        assertEquals("第一段起始消息 ID 为 1", 1L, chunks[0].first().id)
        assertEquals("第一段结束消息 ID 为 25", 25L, chunks[0].last().id)

        // 验证滑动窗口重叠：第二段应当从 25 - 3 = 22 开始，即 ID 为 23
        assertEquals("第二段起始消息应当与第一段重叠 3 条（ID 为 23）", 23L, chunks[1].first().id)
        assertEquals("第二段包含 25 条消息", 25, chunks[1].size)
        assertEquals("第二段结束消息 ID 为 47", 47L, chunks[1].last().id)

        // 第三段从 47 - 3 = 44 开始，即 ID 为 45，直到 60
        assertEquals("第三段起始消息应当与第二段重叠 3 条（ID 为 45）", 45L, chunks[2].first().id)
        assertEquals("第三段包含剩余 16 条消息", 16, chunks[2].size)
        assertEquals("第三段最后一条消息 ID 为 60", 60L, chunks[2].last().id)
    }

    // 5. 滚动摘要提示词升级测试：验证包含【时空演变与关键时间节点】与绝对天数强制要求
    @Test
    fun testBuildStructuredSummaryPromptContainsTimeAnchors() {
        val prompt = AdvancedMemoryEngine.buildStructuredSummaryPrompt(
            existingSummary = "第1天 男女主确立关系",
            transcript = "第3天 两人去公园散步",
            tokenBudget = 800
        )

        assertTrue("提示词必须包含【时空演变与关键时间节点】要求", prompt.contains("【时空演变与关键时间节点（极重要，严禁遗漏）】"))
        assertTrue("必须强制要求记录故事起始点与总跨度天数", prompt.contains("起始时间与总跨度"))
        assertTrue("必须严厉禁止将早期事件模糊为昨天", prompt.contains("严禁将早期事件模糊为“昨天”！"))
        assertTrue("必须保留当前故事停顿节点", prompt.contains("当前故事停顿节点"))
        assertTrue("要求中必须强调时间概念准确性", prompt.contains("时间概念必须严密准确，严禁出现前序事件时序倒流或将数天前事件混淆为昨天的错误"))
    }

    // 6. 个性化配置字段测试：验证时间线自动更新开关默认状态
    @Test
    fun testPersonalizationSettingsAutoTimelineDefaults() {
        val defaultSettings = PersonalizationSettings()
        assertTrue("默认应开启每次对话后自动根据当前对话判断更新时间线", defaultSettings.autoTimelineEnabled)
        assertTrue("默认应开启时间线自动更新提醒", defaultSettings.autoTimelineNoticeEnabled)
    }

    // 7. 全局时间线事件深度汇总与去重压缩测试（需求 1 与需求 2）
    @Test
    fun testConsolidateFinalTimelineEventsMacroGroupingAndDeduplication() {
        // 模拟多轮对话将同一顿饭拆分成了多个细分子事件
        val fragmentedEvents = listOf(
            TimelineEventItem(
                timeTag = "第 1 天·中午",
                content = "两人进入餐厅入座并翻阅菜单点菜",
                category = TimelineCategory.PLOT_EVENT
            ),
            TimelineEventItem(
                timeTag = "第 1 天·中午",
                content = "两人在餐馆就餐并就下一步行动计划达成共识",
                category = TimelineCategory.PLOT_EVENT
            ),
            TimelineEventItem(
                timeTag = "第 1 天·中午",
                content = "两人用餐完毕准备结账出发",
                category = TimelineCategory.PLOT_EVENT
            ),
            TimelineEventItem(
                timeTag = "第 1 天·傍晚",
                content = "两人抵达车站初次相遇并结盟",
                category = TimelineCategory.TURNING_POINT
            ),
            TimelineEventItem(
                timeTag = "第 1 天·傍晚",
                content = "在车站初见并立下同行盟约",
                category = TimelineCategory.PLOT_EVENT
            )
        )

        val consolidated = TimelineMemoryHelper.consolidateFinalTimelineEvents(fragmentedEvents)

        // 验证：3条午餐子事件合并为 1 条，2条车站初遇合并为 1 条
        assertEquals("原 5 条事件经过全局汇总与去重应浓缩为 2 条宏观里程碑", 2, consolidated.size)
        assertTrue("午餐事件应当保留核心成果", consolidated[0].content.contains("餐") && consolidated[0].content.contains("共识"))
        assertTrue("车站相遇事件应当去重合并", consolidated[1].content.contains("车站") && (consolidated[1].content.contains("相遇") || consolidated[1].content.contains("初见")))
    }

    // 8. 全局常驻与固有设定语义去重测试（需求 2）
    @Test
    fun testConsolidateFinalAtemporalSettingsDeduplication() {
        val rawSettings = listOf(
            AtemporalSettingItem(
                category = "习惯偏好",
                content = "喜好饮用不加糖的浓黑咖啡"
            ),
            AtemporalSettingItem(
                category = "习惯与偏好",
                content = "习惯喝黑咖啡且不加糖"
            ),
            AtemporalSettingItem(
                category = "生理禁忌",
                content = "对花生严重过敏，误食会引发哮喘"
            ),
            AtemporalSettingItem(
                category = "生理禁忌与弱点",
                content = "花生过敏，误食易发哮喘"
            ),
            AtemporalSettingItem(
                category = "世界规则",
                content = "此世界魔力在满月之夜达到顶峰"
            )
        )

        val consolidated = TimelineMemoryHelper.consolidateFinalAtemporalSettings(rawSettings)
        assertEquals("相似的咖啡习惯与花生过敏设定应合并，最终浓缩为 3 条核心规则", 3, consolidated.size)
        assertTrue(consolidated.any { it.content.contains("黑咖啡") })
        assertTrue(consolidated.any { it.content.contains("花生") && it.content.contains("过敏") })
        assertTrue(consolidated.any { it.content.contains("满月") })
    }

    // 9. 时空推进推演与防停滞测试（需求 3）
    @Test
    fun testDetectAutoStoryTimeAdvancementAntiFreeze() {
        // 1. 活动完成推进：早晨吃完早餐出发 -> 上午
        val advancedFromMorning = TimelineMemoryHelper.detectAutoStoryTimeAdvancement(
            currentStoryTime = "第 1 天·清晨",
            userMessage = "吃完早餐了，我们准备去北门市场吧。",
            assistantReply = "两人吃完热腾腾的早点，收拾好随身装备，动身走出客栈，向繁华的北门街市走去。"
        )
        assertNotNull("吃完早餐动身出发应当推进时空节点", advancedFromMorning)
        assertEquals("第 1 天·上午", advancedFromMorning)

        // 2. 时段描写自然推进：下午 -> 傍晚
        val advancedToDusk = TimelineMemoryHelper.detectAutoStoryTimeAdvancement(
            currentStoryTime = "第 1 天·下午",
            userMessage = "在藏书阁查阅资料不知不觉过了好久。",
            assistantReply = "夕阳西下，天边漫卷金红色的晚霞，街市上的店铺陆续点亮了灯火。"
        )
        assertNotNull("夕阳西下掌灯应当推进至傍晚", advancedToDusk)
        assertEquals("第 1 天·傍晚", advancedToDusk)

        // 3. 次日跨天推进：夜间就寝 -> 次日清晨
        val advancedToNextDay = TimelineMemoryHelper.detectAutoStoryTimeAdvancement(
            currentStoryTime = "第 1 天·夜间",
            userMessage = "太累了，先睡吧，明天见。",
            assistantReply = "两人互道晚安各自回房。一夜无话，次日清晨的第一缕晨光悄然洒进窗台。"
        )
        assertNotNull("一夜无话次日清晨应当跨天推进", advancedToNextDay)
        assertEquals("第 2 天·清晨", advancedToNextDay)
    }

    // 10. 时间线 Prompt 防停滞强指令测试（需求 3）
    @Test
    fun testBuildTimelineNodesPromptContextContainsAntiFreezeRule() {
        val dummyNodes = listOf(
            com.aiassistant.domain.model.TimelineNode(
                id = 1L,
                conversationId = 1L,
                timeTag = "第 1 天·上午",
                event = "在车站结识同行",
                category = "PLOT_EVENT",
                orderIndex = 1
            )
        )
        val prompt = TimelineMemoryHelper.buildTimelineNodesPromptContext(
            nodes = dummyNodes,
            currentStoryTime = "第 1 天·上午"
        )
        // v2.7.1：防停滞指令由“主动推进”收敛为“默认守时 + 明确描写才推进 + 单步顺延”，
        // 防篡改与基准点语义保持不变
        assertTrue("必须包含时空自然推进与防篡改铁律", prompt.contains("时空自然推进与防篡改铁律"))
        assertTrue("必须指明当前时空仅为基准点而非永恒固化", prompt.contains("仅代表本轮交互开始时的基准时空，绝非永恒固化的时间"))
        assertTrue("必须默认保持当前时段，不得主动冒进", prompt.contains("默认保持当前时段不变"))
        assertTrue("必须约束单轮至多顺延一个相邻时段", prompt.contains("单轮至多推进一个相邻时段"))
        assertTrue("必须严禁跳跃式推进", prompt.contains("严禁跳跃式推进"))
    }

    // 11. 拦截用户输入与指令误当事件测试（问题 1）
    @Test
    fun testPreventUserInstructionExtractedAsEvent() {
        // 无括号口令指令应当被准确识别
        val freeTextInstruction = "接下来让他们在雨夜的车站再次相遇，并产生争执"
        assertTrue("无括号的自由文本指令应当判定为导演指令", TimelineMemoryHelper.isPureDirectorInstruction(freeTextInstruction))

        val promptInstruction = "请继续写他们第一次合作完成任务后的对话"
        assertTrue("请继续写...开头的口令应当判定为导演指令", TimelineMemoryHelper.isPureDirectorInstruction(promptInstruction))

        // 拦截与用户输入雷同的事件
        val userInputs = listOf(
            "接下来让他们在雨夜的车站再次相遇",
            "我拔出短剑，冷冷地看着他：‘你到底是谁？’"
        )

        val invalidEvent1 = "接下来让他们在雨夜的车站再次相遇"
        assertTrue("与用户指令一致的事件必须被拦截判定为非法", TimelineMemoryHelper.isInvalidOrUserInstructionEvent(invalidEvent1, userInputs))

        val invalidEvent2 = "我拔出短剑冷冷地看着他你到底是谁"
        assertTrue("直接抄录用户发言的事件必须被拦截判定为非法", TimelineMemoryHelper.isInvalidOrUserInstructionEvent(invalidEvent2, userInputs))

        val validNarrativeEvent = "两人于雨夜车站点燃香烟并达成初步同盟"
        assertFalse("客观第三人称的剧情事实不应被误判为非法", TimelineMemoryHelper.isInvalidOrUserInstructionEvent(validNarrativeEvent, userInputs))

        // 测试 consolidateFinalTimelineEvents 过滤非法事件
        val rawEvents = listOf(
            TimelineEventItem(timeTag = "第 1 天·夜晚", content = invalidEvent1),
            TimelineEventItem(timeTag = "第 1 天·夜晚", content = validNarrativeEvent)
        )
        val filtered = TimelineMemoryHelper.consolidateFinalTimelineEvents(rawEvents, userInputs)
        assertEquals("抄录用户指令的事件应当被直接剔除，只保留 1 条客观剧情事件", 1, filtered.size)
        assertEquals(validNarrativeEvent, filtered[0].content)
    }

    // 12. 语义自然完整收尾与防腰斩截断测试（问题 2）
    @Test
    fun testCompactSentenceKeepCompletePreventsMidSentenceTruncation() {
        // 模拟长句子：如果暴力 take(25)，会切在“应对守卫”后面变成半截残句
        val longSentence = "两人前往藏书阁寻找失落的古卷，并在路上商量了应对守卫的策略暗号"
        val compacted = TimelineMemoryHelper.compactSentenceKeepComplete(longSentence, 28)

        // 验证：应当在前半句逗号处完整断开，保持主谓宾完整，绝不能变成半截残句
        assertTrue("精炼句子应当保留完整的语法分句", compacted == "两人前往藏书阁寻找失落的古卷" || compacted.endsWith("策略暗号"))
        assertFalse("绝不能出现半截残词腰斩", compacted.endsWith("的") || compacted.endsWith("应对") || compacted.endsWith("在路"))
        assertTrue("字数应当合理控制在 28 字内", compacted.length <= 28)

        // 验证冗长无增量前缀自动清理
        val redundantPrefixSentence = "两人在对话中达成共识，决定次日清晨启程出发"
        val cleaned = TimelineMemoryHelper.compactSentenceKeepComplete(redundantPrefixSentence, 28)
        assertFalse("应当剥离无增量前缀", cleaned.startsWith("两人在对话中"))
        assertTrue("核心事件应当保留完整", cleaned.contains("达成共识") || cleaned.contains("次日清晨启程出发"))
    }

    // 13. 跨界事件与设定消歧去重测试（问题 3）
    @Test
    fun testCrossDeduplicateEventsAndSettingsEliminatesDuplicateConcepts() {
        val events = listOf(
            TimelineEventItem(
                timeTag = "第 1 天·傍晚",
                content = "二人在石桥上结为生死同盟，立誓共进退",
                category = TimelineCategory.PLOT_EVENT
            ),
            TimelineEventItem(
                timeTag = "第 2 天·中午",
                content = "林晨在测试中觉醒了罕见双生武魂",
                category = TimelineCategory.TURNING_POINT
            )
        )

        val settings = listOf(
            // 重复项 1：与事件 1 本质完全一致（属于同一件事的动态复述）
            AtemporalSettingItem(
                category = "人际羁绊",
                content = "在石桥上结为生死同盟立下誓言"
            ),
            // 重复项 2：与事件 2 本质完全一致
            AtemporalSettingItem(
                category = "角色特质",
                content = "在测试中觉醒了双生武魂"
            ),
            // 独立静态规则：纯粹属性与禁忌，非事件复述，应当保留！
            AtemporalSettingItem(
                category = "生理禁忌",
                content = "对某种特殊寒性药草严重过敏"
            )
        )

        val (finalEvents, finalSettings) = TimelineMemoryHelper.crossDeduplicateEventsAndSettings(events, settings)

        assertEquals("时间线事件应完整保留 2 条", 2, finalEvents.size)
        assertEquals("与时间线事件本质一致的 2 条重复设定应被剔除，仅保留 1 条纯粹静态规则", 1, finalSettings.size)
        assertEquals("保留的设定应当为独立生理禁忌", "对某种特殊寒性药草严重过敏", finalSettings[0].content)
    }
}
