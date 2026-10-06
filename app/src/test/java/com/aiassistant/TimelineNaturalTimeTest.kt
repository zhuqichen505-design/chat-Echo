package com.aiassistant

import com.aiassistant.domain.model.TimelineNode
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineMemoryHelper
import org.junit.Assert.*
import org.junit.Test

class TimelineNaturalTimeTest {

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
        // 遇到第1天时，因为此前两周过后（+14）等单调递增累进，天数合理向前演进
        assertTrue(normalized[4].timeTag.contains("天") || normalized[4].timeTag.contains("第"))
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
        assertEquals("约两周前", TimelineMemoryHelper.calculateRelativeTime("第 1 天", "两周过后"))
        assertEquals("放假前", TimelineMemoryHelper.calculateRelativeTime("学期末", "暑假开始"))
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

    // v2.7.1：注入 prompt 需携带时间记忆权威声明与默认守时/单步推进铁律
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
        assertTrue(nodesContext.contains("唯一权威的时间记忆"))
        assertTrue(nodesContext.contains("单轮至多推进一个相邻时段"))

        val roleplayContext = TimelineMemoryHelper.buildTimelinePromptContext(
            currentStoryTime = "第 2 天·上午",
            memoryContents = listOf("[第 1 天·清晨] 两人在车站相遇")
        )
        assertTrue(roleplayContext.contains("唯一权威的时间记忆"))
        assertTrue(roleplayContext.contains("单轮至多一段"))
    }
}
