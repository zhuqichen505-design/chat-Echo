package com.aiassistant

import com.aiassistant.utils.TimelineMemoryHelper
import org.junit.Assert.*
import org.junit.Test

class TimelineMemoryTest {

    @Test
    fun testParseContentToEvent_withStandardTimeTag() {
        val raw = "[第3天·傍晚] 两人在商业街甜品店一起吃了草莓奶油蛋糕"
        val event = TimelineMemoryHelper.parseContentToEvent(raw)
        assertEquals("第3天·傍晚", event.timeTag)
        assertEquals("两人在商业街甜品店一起吃了草莓奶油蛋糕", event.content)
    }

    @Test
    fun testParseContentToEvent_withChineseBrackets() {
        val raw = "【第5天】下雨天在电车站门口避雨，分吃了一份关东煮"
        val event = TimelineMemoryHelper.parseContentToEvent(raw)
        assertEquals("第5天", event.timeTag)
        assertEquals("下雨天在电车站门口避雨，分吃了一份关东煮", event.content)
    }

    @Test
    fun testParseContentToEvent_withoutTimeTag() {
        val raw = "平时喜欢喝无糖乌龙茶"
        val event = TimelineMemoryHelper.parseContentToEvent(raw)
        assertEquals("", event.timeTag)
        assertEquals("平时喜欢喝无糖乌龙茶", event.content)
    }

    @Test
    fun testFormatEventContent() {
        val formatted = TimelineMemoryHelper.formatEventContent("第4天·下午", "在图书馆借了一本历史书")
        assertEquals("[第4天·下午] 在图书馆借了一本历史书", formatted)

        val unformatted = TimelineMemoryHelper.formatEventContent("", "无标签记忆")
        assertEquals("无标签记忆", unformatted)
    }

    @Test
    fun testCalculateRelativeTime() {
        val current = "第 5 天·上午"

        // 昨天
        val yesterday = TimelineMemoryHelper.calculateRelativeTime("第 4 天·夜间", current)
        assertEquals("昨天", yesterday)

        // 前天 / 2天前
        val dayBefore = TimelineMemoryHelper.calculateRelativeTime("第 3 天·傍晚", current)
        assertEquals("前天", dayBefore)

        // 4天前
        val fourDaysAgo = TimelineMemoryHelper.calculateRelativeTime("第 1 天·清晨", current)
        assertEquals("4天前", fourDaysAgo)

        // 今天
        val today = TimelineMemoryHelper.calculateRelativeTime("第 5 天·早晨", current)
        assertEquals("今天", today)
    }

    @Test
    fun testParseModelOutput_jsonCodeBlock() {
        val modelResponse = """
            这是为你梳理的时间线：
            ```json
            {
              "currentStoryTime": "第 6 天·早晨",
              "timelineEvents": [
                {
                  "timeTag": "第 1 天·下午",
                  "content": "两人在走廊初次搭话，借了同一本小说"
                },
                {
                  "timeTag": "第 3 天·傍晚",
                  "content": "在商业街甜品店吃了草莓奶油蛋糕"
                },
                {
                  "timeTag": "第 5 天·雨夜",
                  "content": "暴雨中在电车站避雨并分吃了关东煮"
                }
              ]
            }
            ```
            以上请核对。
        """.trimIndent()

        val result = TimelineMemoryHelper.parseModelOutput(modelResponse)
        assertEquals("第 6 天·早晨", result.currentStoryTime)
        assertEquals(3, result.events.size)
        assertEquals("第 1 天·下午", result.events[0].timeTag)
        assertEquals("两人在走廊初次搭话，借了同一本小说", result.events[0].content)
        assertEquals("第 5 天·雨夜", result.events[2].timeTag)
    }

    @Test
    fun testParseModelOutput_textFallback() {
        val rawText = """
            当前故事时间：第 4 天·傍晚
            - [第 1 天] 两人相遇
            - [第 2 天·中午] 在食堂一起吃午餐
            - [第 3 天] 一起去图书馆自习
        """.trimIndent()

        val result = TimelineMemoryHelper.parseModelOutput(rawText)
        assertEquals("第 4 天·傍晚", result.currentStoryTime)
        assertEquals(3, result.events.size)
        assertEquals("第 1 天", result.events[0].timeTag)
        assertEquals("两人相遇", result.events[0].content)
    }

    @Test
    fun testBuildTimelinePromptContext() {
        val memories = listOf(
            "[第 3 天·傍晚] 两人在商业街吃了草莓蛋糕",
            "[第 4 天·夜间] 两人在电车站避雨",
            "约定周末一起去买书"
        )
        val context = TimelineMemoryHelper.buildTimelinePromptContext("第 5 天·上午", memories)

        assertTrue(context.contains("【故事当前时间节点】：第 5 天·上午"))
        assertTrue(context.contains("相对于当前：前天"))
        assertTrue(context.contains("相对于当前：昨天"))
        assertTrue(context.contains("时序交互准则"))
    }

    @Test
    fun testIsPureDirectorInstruction() {
        assertTrue(com.aiassistant.utils.TimelineMemoryHelper.isPureDirectorInstruction("[让两人在雨夜再次相遇]"))
        assertTrue(com.aiassistant.utils.TimelineMemoryHelper.isPureDirectorInstruction("【请继续写他们第一次合作完成任务后的对话】"))
        assertTrue(com.aiassistant.utils.TimelineMemoryHelper.isPureDirectorInstruction("[推进剧情，增加环境描写]"))
        assertTrue(com.aiassistant.utils.TimelineMemoryHelper.isPureDirectorInstruction("[从配角视角写这一段]"))

        // 正常带时间标签的客观故事事件不应被识别为导演指令
        assertFalse(com.aiassistant.utils.TimelineMemoryHelper.isPureDirectorInstruction("[第3天·傍晚] 两人在商业街甜品店一起吃了草莓奶油蛋糕"))
        assertFalse(com.aiassistant.utils.TimelineMemoryHelper.isPureDirectorInstruction("【第5天】下雨天在电车站门口避雨，分吃了一份关东煮"))
        assertFalse(com.aiassistant.utils.TimelineMemoryHelper.isPureDirectorInstruction("普通的一句角色对白"))
    }

    @Test
    fun eventTimeIdentityRejectsDifferentAndUnknownTimes() {
        assertTrue(TimelineMemoryHelper.isSameTimelineEventTime("第1天 · 清晨", "第1天·清晨"))
        assertFalse(TimelineMemoryHelper.isSameTimelineEventTime("第1天·清晨", "第1天·傍晚"))
        assertFalse(TimelineMemoryHelper.isSameTimelineEventTime("第1天·清晨", "第2天·清晨"))
        assertFalse(TimelineMemoryHelper.isSameTimelineEventTime("第1天", "第1天·清晨"))
        assertFalse(TimelineMemoryHelper.isSameTimelineEventTime("未知", "未知"))
        assertFalse(TimelineMemoryHelper.isSameTimelineEventTime("", ""))
    }

    @Test
    fun testIsNoChangeLikeResponse_coversMarkerAndChinesePhrasing() {
        assertTrue(TimelineMemoryHelper.isNoChangeLikeResponse("NO_UPDATE"))
        assertTrue(TimelineMemoryHelper.isNoChangeLikeResponse("no_update，本轮无变化"))
        assertTrue(TimelineMemoryHelper.isNoChangeLikeResponse("本轮只是日常寒暄，时间没有推进"))
        assertTrue(TimelineMemoryHelper.isNoChangeLikeResponse("故事时间保持原时段不变"))
        assertTrue(TimelineMemoryHelper.isNoChangeLikeResponse("无需更新"))
        assertFalse(TimelineMemoryHelper.isNoChangeLikeResponse("林岚抵达古塔并与守门人缔结盟约"))
        assertFalse(TimelineMemoryHelper.isNoChangeLikeResponse("哈哈，这段剧情真有意思"))
        assertFalse(TimelineMemoryHelper.isNoChangeLikeResponse(""))
    }

    @Test
    fun testParseContentToEvent_withCategories() {
        val raw1 = "[第2天·上午] [规则约束] 严禁在学院内施展高阶暗黑魔法"
        val event1 = com.aiassistant.utils.TimelineMemoryHelper.parseContentToEvent(raw1)
        assertEquals("第2天·上午", event1.timeTag)
        assertEquals(com.aiassistant.utils.TimelineCategory.RULE_CONSTRAINT, event1.category)
        assertEquals("严禁在学院内施展高阶暗黑魔法", event1.content)

        val raw2 = "【第3天·傍晚】 【角色设定】 确认对方其实是失散多年的童年旧友"
        val event2 = com.aiassistant.utils.TimelineMemoryHelper.parseContentToEvent(raw2)
        assertEquals("第3天·傍晚", event2.timeTag)
        assertEquals(com.aiassistant.utils.TimelineCategory.CHARACTER_SETTING, event2.category)
        assertEquals("确认对方其实是失散多年的童年旧友", event2.content)

        val raw3 = "[第4天] 【设定】 帝都宣布实施全城宵禁"
        val event3 = com.aiassistant.utils.TimelineMemoryHelper.parseContentToEvent(raw3)
        assertEquals("第4天", event3.timeTag)
        assertEquals(com.aiassistant.utils.TimelineCategory.WORLD_SETTING, event3.category)
        assertEquals("帝都宣布实施全城宵禁", event3.content)
    }

    @Test
    fun testParseModelOutput_withAtemporalSettingsAndCategories() {
        val modelJson = """
            ```json
            {
              "currentStoryTime": "第 5 天·傍晚",
              "timelineEvents": [
                {
                  "timeTag": "第 1 天·清晨",
                  "category": "PLOT_EVENT",
                  "content": "初次在列车站台偶遇并交换了车票"
                },
                {
                  "timeTag": "第 2 天·中午",
                  "category": "RULE_CONSTRAINT",
                  "content": "获知该城邦内不得携带未登记的以太水晶"
                }
              ],
              "atemporalSettings": [
                {
                  "category": "角色特质",
                  "content": "主角对猫毛严重过敏，且对苦味饮料有强烈偏好",
                  "targetScope": "session"
                },
                {
                  "category": "世界法则",
                  "content": "整个大陆处于双生魔力潮汐的衰退周期",
                  "targetScope": "global"
                }
              ]
            }
            ```
        """.trimIndent()

        val result = com.aiassistant.utils.TimelineMemoryHelper.parseModelOutput(modelJson)
        assertEquals("第 5 天·傍晚", result.currentStoryTime)
        assertEquals(2, result.events.size)
        assertEquals(com.aiassistant.utils.TimelineCategory.PLOT_EVENT, result.events[0].category)
        assertEquals(com.aiassistant.utils.TimelineCategory.RULE_CONSTRAINT, result.events[1].category)

        // 验证时间无关设定
        assertEquals(2, result.atemporalSettings.size)
        assertTrue(result.atemporalSettings[0].isSelected)
        assertEquals("角色特质", result.atemporalSettings[0].category)
        assertTrue(result.atemporalSettings[0].content.contains("猫毛严重过敏"))
        assertEquals("session", result.atemporalSettings[0].targetScope)

        assertEquals("世界法则", result.atemporalSettings[1].category)
        assertEquals("global", result.atemporalSettings[1].targetScope)
    }
}
