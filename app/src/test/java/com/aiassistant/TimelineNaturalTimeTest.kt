package com.aiassistant

import com.aiassistant.domain.model.TimelineNode
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineMemoryHelper
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TimelineNaturalTimeTest {

    @Test fun naturalRangesAndFlashbacksKeepTheirAnchorsWithoutCalendarCoercion() {
        val tags = listOf("第6天·上午", "购物返家后约一两周", "回忆·两年前的冬天", "返回主线·购物返家后一两个月", "同一日下午")
        val events = tags.mapIndexed { index, tag -> TimelineEventItem(timeTag = tag, content = "合成事件$index") }
        val normalized = TimelineMemoryHelper.normalizeMonotonicTimeline(events)
        assertEquals(listOf("第 6 天·上午") + tags.drop(1), normalized.map { it.timeTag })
        assertEquals(events.map { it.content }, normalized.map { it.content })
        assertEquals("购物返家后约一两周", TimelineMemoryHelper.inferCurrentStoryTime(normalized.take(3)))
        assertEquals("未确定", TimelineMemoryHelper.inferCurrentStoryTime(listOf(TimelineEventItem(content = "提到两年前的计划"))))
        assertNull(TimelineMemoryHelper.calculateRelativeTime("两周后", "一两个月后"))
        assertNull(TimelineMemoryHelper.calculateRelativeTime("第6天", "承接【第6天】·过了一两周"))
        val anchored = TimelineMemoryHelper.buildTimelinePromptContext("承接【第6天】·过了一两周",
            listOf("[第6天] 两人在旧车站立誓结盟"))
        assertFalse(anchored.contains("相对于当前：今天"))
    }

    @Test fun localFallbackKeepsExplicitAmbiguousSpansAndIgnoresPlansAndDirectorRequests() {
        assertEquals("承接【第6天·上午】·过了一两周", TimelineMemoryHelper.detectAutoStoryTimeAdvancement(
            "第6天·上午", "继续", "过了一两周，两人再次在旧超市门前见面。"))
        assertEquals("承接【购物当天】·一两个月过去了", TimelineMemoryHelper.detectAutoStoryTimeAdvancement(
            "购物当天", "继续", "一两个月过去了，窗外迎来了初雪。"))
        assertNull(TimelineMemoryHelper.detectAutoStoryTimeAdvancement("第6天·上午", "过两周再继续故事", "两人继续整理购物清单。"))
        assertNull(TimelineMemoryHelper.detectAutoStoryTimeAdvancement("第6天·上午", "继续", "两周后再见吧，她在电话里约定。"))
        assertNull(TimelineMemoryHelper.detectAutoStoryTimeAdvancement("未确定", "你好", "你好，今天有什么安排？"))
    }

    @Test fun sourceCoverageRejectsMissingMiddleMessagesAndUnsupportedEventsWithoutAffectingLegacyParsing() {
        fun payload(coverage: String) = """{"timelineEvents":[{"timeTag":"购物当天","content":"两人解决预算分歧后结账"}],"atemporalSettings":[],"sourceCoverage":$coverage}"""
        assertFalse(TimelineMemoryHelper.hasCompleteSourceCoverage(payload("""[{"messageId":3,"eventIndices":[0]}]"""), listOf(1, 2, 3).map { it.toLong() }))
        assertFalse(TimelineMemoryHelper.hasCompleteSourceCoverage(payload("""[{"messageId":1,"eventIndices":[2]}]"""), listOf(1L)))
        assertTrue(TimelineMemoryHelper.hasCompleteSourceCoverage(payload("""[{"messageId":1,"noFactReason":"导演指导"},{"messageId":2,"eventIndices":[0]},{"messageId":3,"noFactReason":"重复确认"}]"""), listOf(1L, 2L, 3L)))
        val legacy = """{"currentStoryTime":"两周后","timelineEvents":[{"timeTag":"第1天","content":"两人在车站初次见面"}]}"""
        assertEquals("两周后", TimelineMemoryHelper.parseModelOutput(legacy).currentStoryTime)
        assertEquals("两人在车站初次见面", TimelineMemoryHelper.parseModelOutput(legacy).events.single().content)
    }

    @Test fun verifiedExtractionRejectsDamagedTypesAndLossyNumbersBeforeLegacyFallbackCanRun() {
        val valid = JsonParser.parseString("""{"currentStoryTime":"购物后约两周","timelineEvents":[{"timeTag":"购物后约两周","category":"PLOT_EVENT","content":"两人在车站交换采购清单"}],"atemporalSettings":[{"action":"ADD","targetMemoryId":null,"content":"合成角色不能食用花生"}],"sourceCoverage":[{"messageId":7,"eventIndices":[0],"settingIndices":[0]}]}""").asJsonObject
        fun rejected(label: String, damage: (JsonObject) -> Unit) {
            val damaged = valid.deepCopy().also(damage)
            assertNull(label, TimelineMemoryHelper.parseVerifiedModelOutput(damaged.toString(), listOf(7L), listOf(11L)))
        }
        fun JsonObject.event() = getAsJsonArray("timelineEvents")[0].asJsonObject
        fun JsonObject.setting() = getAsJsonArray("atemporalSettings")[0].asJsonObject
        fun JsonObject.coverage() = getAsJsonArray("sourceCoverage")[0].asJsonObject
        rejected("null 时间不能触发旧文本兜底") { it.add("currentStoryTime", JsonNull.INSTANCE) }
        rejected("时间必须为字符串") { it.addProperty("currentStoryTime", 6) }
        rejected("null 正文不能被静默丢弃") { it.event().add("content", JsonNull.INSTANCE) }
        rejected("数字正文不能被转成字符串") { it.event().addProperty("content", 42) }
        rejected("空白正文不能推进水线") { it.event().addProperty("content", "  ") }
        rejected("时间标签不能为对象") { it.event().add("timeTag", JsonObject()) }
        rejected("分类不能为 null") { it.event().add("category", JsonNull.INSTANCE) }
        rejected("设定正文必须为字符串") { it.setting().addProperty("content", true) }
        rejected("NOOP 必须按协议省略") { it.setting().addProperty("action", "NOOP") }
        rejected("ADD 不接受修改目标") { it.setting().addProperty("targetMemoryId", 11) }
        rejected("补充不能指向不存在的设定") {
            it.setting().addProperty("action", "SUPPLEMENT"); it.setting().addProperty("targetMemoryId", 12)
        }
        rejected("修订不能将字符串当 ID") {
            it.setting().addProperty("action", "REVISE"); it.setting().addProperty("targetMemoryId", "11")
        }
        rejected("补充不能截掉 ID 小数") {
            it.setting().addProperty("action", "SUPPLEMENT"); it.setting().addProperty("targetMemoryId", 11.5)
        }
        rejected("源 ID 不能从字符串强转") { it.coverage().addProperty("messageId", "7") }
        rejected("源 ID 不能截掉小数") { it.coverage().addProperty("messageId", 7.5) }
        rejected("源 ID 不能溢出 Long") { it.coverage().add("messageId", JsonParser.parseString("9223372036854775808")) }
        for (indices in listOf("[0.5]", "[\"0\"]", "[-1]", "[2147483648]", "[0,0]")) {
            rejected("事件索引必须是唯一有效整数：$indices") { it.coverage().add("eventIndices", JsonParser.parseString(indices)) }
        }
        rejected("无事实理由也必须符合字符串协议") { it.coverage().addProperty("noFactReason", false) }
        rejected("设定数组不能为 null") { it.add("atemporalSettings", JsonNull.INSTANCE) }

        val accepted = TimelineMemoryHelper.parseVerifiedModelOutput(valid.toString(), listOf(7L), listOf(11L))!!
        assertEquals("两人在车站交换采购清单", accepted.events.single().content)
        assertEquals("ADD", accepted.atemporalSettings.single().action)
        assertNull(accepted.atemporalSettings.single().targetMemoryId)
        for (action in listOf("SUPPLEMENT", "REVISE")) {
            val update = valid.deepCopy().apply {
                setting().addProperty("action", action); setting().addProperty("targetMemoryId", 11)
            }
            assertEquals(11L, TimelineMemoryHelper.parseVerifiedModelOutput(update.toString(), listOf(7L), listOf(11L))!!.atemporalSettings.single().targetMemoryId)
        }
        val noNewFact = """{"timelineEvents":[],"atemporalSettings":[],"sourceCoverage":[{"messageId":7,"noFactReason":"已有设定的再次确认，按 NOOP 省略"}]}"""
        val unchanged = TimelineMemoryHelper.parseVerifiedModelOutput(noNewFact, listOf(7L), emptyList(), "购物后约两周")!!
        assertTrue(unchanged.events.isEmpty())
        assertTrue(unchanged.atemporalSettings.isEmpty())
        assertEquals("购物后约两周", unchanged.currentStoryTime)
        val legacy = """{"currentStoryTime":"两周后","timelineEvents":[{"content":"两人在车站初次见面"}]}"""
        assertNull(TimelineMemoryHelper.parseVerifiedModelOutput(legacy, listOf(7L), emptyList()))
        assertEquals("两人在车站初次见面", TimelineMemoryHelper.parseModelOutput(legacy).events.single().content)
    }

    @Test fun legacyContextNeverInfersCurrentPhaseFromPastEventsNestedReferencesOrBreakfastHabits() {
        val memories = listOf("[第6天·上午] 两人在旧超市碰面", "【习惯偏好】合成角色总在早餐后练琴")
        for (anchor in listOf("过了一两个月", "购物返家后约一两周", "承接【第6天·上午】·过了一两个月")) {
            val context = TimelineMemoryHelper.buildTimelinePromptContext(anchor, memories)
            assertTrue(context.contains("【故事当前时间节点】：$anchor"))
            assertTrue(context.contains("两人在旧超市碰面"))
            assertTrue(context.contains("总在早餐后练琴"))
            assertTrue(context.contains("当前锚点没有明确日内时段"))
            assertFalse("过去的上午或早餐习惯不能约束当前白天", context.contains("当前仍处于白天"))
            assertFalse(context.contains("当前故事时段停留在"))
        }
        val explicit = TimelineMemoryHelper.buildTimelinePromptContext("购物返家后一两个月·傍晚", memories)
        assertTrue("原文明确给出的当前傍晚应保留", explicit.contains("当前故事时段停留在【傍晚/黄昏】"))
        assertFalse(explicit.contains("当前仍处于白天"))
    }

    @Test
    fun testEstimateTimeSpanJumpDays() {
        assertEquals(14, TimelineMemoryHelper.estimateTimeSpanJumpDays("两周过后"))
        assertEquals(14, TimelineMemoryHelper.estimateTimeSpanJumpDays("2周后"))
        assertEquals(7, TimelineMemoryHelper.estimateTimeSpanJumpDays("一周后"))
        assertEquals(30, TimelineMemoryHelper.estimateTimeSpanJumpDays("一个月后"))
        assertEquals(15, TimelineMemoryHelper.estimateTimeSpanJumpDays("半个月后"))
        assertEquals(365, TimelineMemoryHelper.estimateTimeSpanJumpDays("一年后"))
        assertEquals(1095, TimelineMemoryHelper.estimateTimeSpanJumpDays("三年后"))
        assertEquals(3, TimelineMemoryHelper.estimateTimeSpanJumpDays("数日后"))
        assertEquals(0, TimelineMemoryHelper.estimateTimeSpanJumpDays("第 3 天·傍晚"))
        assertEquals(0, TimelineMemoryHelper.estimateTimeSpanJumpDays("暑假开始"))
    }

    @Test
    fun testParseContentToEvent_withNaturalTimeTags() {
        val event1 = TimelineMemoryHelper.parseContentToEvent("[两周过后] 两人再次在废弃车站碰头，交换了封印的情报")
        assertEquals("两周过后", event1.timeTag)
        assertEquals("两人再次在废弃车站碰头，交换了封印的情报", event1.content)
        assertEquals(TimelineCategory.PLOT_EVENT, event1.category)

        val event2 = TimelineMemoryHelper.parseContentToEvent("【暑假开始】 [规则约束] 学院封闭后山禁地，严禁私自进入")
        assertEquals("暑假开始", event2.timeTag)
        assertEquals("学院封闭后山禁地，严禁私自进入", event2.content)
        assertEquals(TimelineCategory.RULE_CONSTRAINT, event2.category)

        val event3 = TimelineMemoryHelper.parseContentToEvent("[三年后·春] 帝国新皇登基，两人在典礼上遥遥相望")
        assertEquals("三年后·春", event3.timeTag)
        assertEquals("帝国新皇登基，两人在典礼上遥遥相望", event3.content)
    }

    @Test
    fun testNormalizeMonotonicTimeline_withNaturalTimeSpansAndPhases() {
        val rawEvents = listOf(
            TimelineEventItem(timeTag = "第 1 天·上午", content = "两人在车站碰面并达成契约"),
            TimelineEventItem(timeTag = "第 2 天·黄昏", content = "击退了影兽夜袭"),
            TimelineEventItem(timeTag = "两周过后", content = "在旧城废墟完成第二阶段封印修复"),
            TimelineEventItem(timeTag = "暑假开始", content = "两人决定启程前往南方群岛调查身世之谜"),
            TimelineEventItem(timeTag = "第 1 天·清晨", content = "抵达群岛港口租下向导帆船")
        )

        val normalized = TimelineMemoryHelper.normalizeMonotonicTimeline(rawEvents)
        assertEquals(5, normalized.size)
        assertEquals("第 1 天·上午", normalized[0].timeTag)
        assertEquals("第 2 天·黄昏", normalized[1].timeTag)
        assertEquals("两周过后", normalized[2].timeTag) // 保留自然时间跨度标签
        assertEquals("暑假开始", normalized[3].timeTag) // 保留阶段节点标签
        // 新篇章的第1天可以是局部锚点，不得根据前文自然跨度擅自改成全局第17天。
        assertEquals("第 1 天·清晨", normalized[4].timeTag)
    }

    @Test
    fun testInferCurrentStoryTime_withNaturalTime() {
        val eventsWithNaturalEnd = listOf(
            TimelineEventItem(timeTag = "第 1 天·上午", content = "初遇"),
            TimelineEventItem(timeTag = "第 2 天·傍晚", content = "并肩战斗"),
            TimelineEventItem(timeTag = "两周过后", content = "再次相会")
        )
        val inferredTime1 = TimelineMemoryHelper.inferCurrentStoryTime(eventsWithNaturalEnd)
        assertEquals("两周过后", inferredTime1)

        val eventsWithHoliday = listOf(
            TimelineEventItem(timeTag = "第 3 天·夜间", content = "完成任务"),
            TimelineEventItem(timeTag = "暑假开始", content = "开启新篇章")
        )
        val inferredTime2 = TimelineMemoryHelper.inferCurrentStoryTime(eventsWithHoliday)
        assertEquals("暑假开始", inferredTime2)
    }

    @Test
    fun testCalculateRelativeTime_naturalAndExplicit() {
        // 显式天数
        assertEquals("昨天", TimelineMemoryHelper.calculateRelativeTime("第 2 天", "第 3 天"))
        assertEquals("前天", TimelineMemoryHelper.calculateRelativeTime("第 1 天", "第 3 天"))
        assertEquals("今天", TimelineMemoryHelper.calculateRelativeTime("第 3 天", "第 3 天"))

        // 自然语义
        assertNull(TimelineMemoryHelper.calculateRelativeTime("第 1 天", "两周过后"))
        assertNull(TimelineMemoryHelper.calculateRelativeTime("学期末", "暑假开始"))
    }

    @Test
    fun testParseModelOutput_withRichNaturalTimelineAndSettings() {
        val modelJson = """
            ```json
            {
              "currentStoryTime": "暑假开始",
              "timelineEvents": [
                {
                  "timeTag": "第 1 天·清晨",
                  "category": "PLOT_EVENT",
                  "content": "两人在中央车站首次相遇并达成组队同行的约定"
                },
                {
                  "timeTag": "两周过后",
                  "category": "PLOT_EVENT",
                  "content": "在旧城废墟完成第二阶段封印修复，解开了关于守护符文的谜题"
                },
                {
                  "timeTag": "暑假开始",
                  "category": "RULE_CONSTRAINT",
                  "content": "学院全面封闭后山禁地，任何人严禁私自携武器入内"
                }
              ],
              "atemporalSettings": [
                {
                  "category": "生理禁忌",
                  "content": "酒精严重过敏，滴酒不沾",
                  "targetScope": "session"
                },
                {
                  "category": "习惯偏好",
                  "content": "思考对策时习惯以指尖轻叩桌面三下",
                  "targetScope": "session"
                },
                {
                  "category": "角色特质",
                  "content": "极度重视契约承诺，一旦立誓绝不反悔",
                  "targetScope": "session"
                },
                {
                  "category": "世界规则",
                  "content": "帝国宵禁生效时，平民严禁携武器穿行中央广场",
                  "targetScope": "global"
                },
                {
                  "category": "专属信物",
                  "content": "随身携带刻有家族古老纹章的银质怀表",
                  "targetScope": "session"
                }
              ]
            }
            ```
        """.trimIndent()

        val result = TimelineMemoryHelper.parseModelOutput(modelJson)
        assertEquals("暑假开始", result.currentStoryTime)
        assertEquals(3, result.events.size)
        assertEquals("第 1 天·清晨", result.events[0].timeTag)
        assertEquals("两周过后", result.events[1].timeTag)
        assertEquals("暑假开始", result.events[2].timeTag)

        assertEquals(5, result.atemporalSettings.size)
        assertEquals("专属信物", result.atemporalSettings[4].category)
        assertTrue(result.atemporalSettings[4].content.contains("银质怀表"))
    }

    // v2.7.1：同日跨多时段的跳跃式推进守卫（防模型过度执着推进时间）
    @Test
    fun testIsUnreasonableStoryTimeJump_sameDayMultiPhaseLeap() {
        // 同日跨多个时段且无明确时间流逝描写 → 拦截
        assertTrue(
            TimelineMemoryHelper.isUnreasonableStoryTimeJump("第 1 天·清晨", "第 1 天·深夜", "两人围着火炉聊了聊天。")
        )
        assertTrue(
            TimelineMemoryHelper.isUnreasonableStoryTimeJump("第 1 天·上午", "第 1 天·下午", "两人继续商讨对策。")
        )
        // 单步顺延（相邻时段）→ 放行
        assertFalse(TimelineMemoryHelper.isUnreasonableStoryTimeJump("第 1 天·清晨", "第 1 天·上午", ""))
        assertFalse(TimelineMemoryHelper.isUnreasonableStoryTimeJump("第 1 天·下午", "第 1 天·傍晚", ""))
        // 跨天推进不在此守卫范围（过夜属于自然推进）
        assertFalse(TimelineMemoryHelper.isUnreasonableStoryTimeJump("第 1 天·清晨", "第 2 天·上午", ""))
        // 正文有明确时间流逝描写 → 豁免
        assertFalse(
            TimelineMemoryHelper.isUnreasonableStoryTimeJump(
                "第 1 天·清晨", "第 1 天·傍晚", "两人在城里逛了一整天，直到夕阳西下才回城。"
            )
        )
        // 目标时间无法解析时段（自然跨度标签）→ 不拦截
        assertFalse(TimelineMemoryHelper.isUnreasonableStoryTimeJump("第 1 天·清晨", "两周过后", ""))
        // 当前时间缺失或无法解析 → 不拦截
        assertFalse(TimelineMemoryHelper.isUnreasonableStoryTimeJump(null, "第 1 天·深夜", ""))
        assertFalse(TimelineMemoryHelper.isUnreasonableStoryTimeJump("第 1 天·起始", "第 1 天·深夜", ""))
    }

    // v2.7.1：本地兜底推演不得在无时间流逝描写时跨多时段顺延（早晨提及晚上的计划≠已入夜）
    @Test
    fun testDetectAutoStoryTimeAdvancement_noOverEagerAdvancement() {
        // 上午回忆“去年黄昏的往事”：无时间流逝描写，不得直接推进至傍晚（旧实现会直接采信）
        val duskMemory = TimelineMemoryHelper.detectAutoStoryTimeAdvancement(
            currentStoryTime = "第 1 天·上午",
            userMessage = "继续。",
            assistantReply = "两人提起去年黄昏时在桥上立下的约定，相视一笑，又谈起了接下来的安排。"
        )
        assertNull("上午提及黄昏往事不得直接推进至傍晚", duskMemory)

        // 同样跨多时段，但正文有明确时间流逝描写 → 允许推进
        val duskWithPassage = TimelineMemoryHelper.detectAutoStoryTimeAdvancement(
            currentStoryTime = "第 1 天·上午",
            userMessage = "继续。",
            assistantReply = "不知不觉，两人在书阁中翻找了整整一日。夕阳西下，暮色四合，两人才收拾东西踏上归程。"
        )
        assertEquals("第 1 天·傍晚", duskWithPassage)
    }

    // 没有时间证据时守时；原文明示跨度不受日内时段上限限制。
    @Test
    fun testTimelinePromptContext_containsTimeMemoryAuthorityAndSingleStepRule() {
        val nodes = listOf(
            TimelineNode(
                conversationId = 1L,
                timeTag = "第 1 天·清晨",
                event = "两人在车站相遇并达成同行的约定",
                orderIndex = 0
            )
        )
        val nodesContext = TimelineMemoryHelper.buildTimelineNodesPromptContext(nodes, "第 1 天·上午")
        assertTrue(nodesContext.contains("近期明确修订优先"))
        assertTrue(nodesContext.contains("无时间证据时保持当前时段"))

        val roleplayContext = TimelineMemoryHelper.buildTimelinePromptContext(
            currentStoryTime = "第 2 天·上午",
            memoryContents = listOf("[第 1 天·清晨] 两人在车站相遇")
        )
        assertTrue(roleplayContext.contains("唯一权威的时间记忆"))
        assertTrue(roleplayContext.contains("不受相邻时段限制"))
    }
}
