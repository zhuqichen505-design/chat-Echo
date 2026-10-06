package com.aiassistant

import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineMemoryHelper
import org.junit.Assert.*
import org.junit.Test

class TimelineDeepModelExtractionTest {

    @Test
    fun testDeepModelJsonParsing_withDiverseSettingsAndEvents() {
        val jsonPayload = """
            {
              "currentStoryTime": "第 7 天·黄昏",
              "timelineEvents": [
                {
                  "timeTag": "第 1 天·清晨",
                  "category": "PLOT_EVENT",
                  "content": "林恩在旧城区火车站与艾莉西亚汇合并交接密函"
                },
                {
                  "timeTag": "第 3 天·深夜",
                  "category": "RULE_CONSTRAINT",
                  "content": "发现在血月之夜绝对不可直视高塔之眼"
                },
                {
                  "timeTag": "第 5 天·傍晚",
                  "category": "CHARACTER_SETTING",
                  "content": "确认艾莉西亚掌握远古失传的星辉治愈术"
                }
              ],
              "atemporalSettings": [
                {
                  "category": "生理禁忌",
                  "content": "林恩对深渊迷雾有严重过敏性排斥",
                  "targetScope": "session"
                },
                {
                  "category": "世界规则",
                  "content": "所有契约魔法均以真名与等价灵魂交换为基石",
                  "targetScope": "global"
                }
              ]
            }
        """.trimIndent()

        val parsed = TimelineMemoryHelper.parseModelOutput(jsonPayload)
        assertEquals("第 7 天·黄昏", parsed.currentStoryTime)
        assertEquals("AI_MODEL", parsed.extractionSource)
        assertEquals(3, parsed.events.size)
        assertEquals(2, parsed.atemporalSettings.size)

        // 验证事件字段
        assertEquals("第 1 天·清晨", parsed.events[0].timeTag)
        assertEquals(TimelineCategory.PLOT_EVENT, parsed.events[0].category)
        assertEquals("林恩在旧城区火车站与艾莉西亚汇合并交接密函", parsed.events[0].content)

        assertEquals("第 3 天·深夜", parsed.events[1].timeTag)
        assertEquals(TimelineCategory.RULE_CONSTRAINT, parsed.events[1].category)

        // 验证固有设定字段
        val setting1 = parsed.atemporalSettings[0]
        assertEquals("生理禁忌", setting1.category)
        assertEquals("林恩对深渊迷雾有严重过敏性排斥", setting1.content)
        assertTrue(setting1.isSelected)
        assertEquals("session", setting1.targetScope)

        val setting2 = parsed.atemporalSettings[1]
        assertEquals("世界规则", setting2.category)
        assertEquals("所有契约魔法均以真名与等价灵魂交换为基石", setting2.content)
        assertEquals("global", setting2.targetScope)
    }

    @Test
    fun testDirectorInstructionAndLiterarySentenceExclusion() {
        // 用户导演指令必须被过滤，不能进入时间轴事件
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("[让两人在雨夜的车站再次相遇]"))
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("【让冲突升级，但不要立即解决】"))
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("[从配角视角继续这一段]"))
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("[重写上一段，让角色更冷静、更克制]"))
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("[继续剧情，增加环境描写和人物动作]"))
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("[安排林恩在门外偷听]"))

        // 实际客观事件与设定不应被误判为导演指令
        assertFalse(TimelineMemoryHelper.isPureDirectorInstruction("[第1天·上午] 两人在车站初次相遇"))
        assertFalse(TimelineMemoryHelper.isPureDirectorInstruction("两人决定共同前往地下城"))
    }

    @Test
    fun testCategoryDisplayNameAndTagColors() {
        // 验证 9 大类别名称与色值完整性
        val categories = TimelineCategory.values()
        assertEquals(9, categories.size)

        val atemporal = TimelineCategory.ATEMPORAL_SETTING
        assertEquals("固有设定", atemporal.displayName)
        assertEquals("💡", atemporal.emoji)
        assertEquals("#607D8B", atemporal.tagColorHex)

        val plot = TimelineCategory.PLOT_EVENT
        assertEquals("剧情事件", plot.displayName)
        assertEquals("📖", plot.emoji)

        val rule = TimelineCategory.RULE_CONSTRAINT
        assertEquals("规则约束", rule.displayName)
        assertEquals("⚖️", rule.emoji)

        val charSetting = TimelineCategory.CHARACTER_SETTING
        assertEquals("角色设定", charSetting.displayName)
        assertEquals("🎭", charSetting.emoji)

        val world = TimelineCategory.WORLD_SETTING
        assertEquals("世界设定", world.displayName)
        assertEquals("🌍", world.emoji)
    }
}
