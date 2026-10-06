package com.aiassistant

import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineMemoryHelper
import org.junit.Assert.*
import org.junit.Test

class ChronicleTimelineStudioTest {

    @Test
    fun testDirectorInstructionFiltering() {
        // 用户发出的纯指导指令，必须识别为导演指令
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("[让两人在雨夜再次相遇]"))
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("【请继续写他们第一次合作完成任务后的对话】"))
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("[冲突升级，但不要立即解决]"))
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("[从配角视角写这一段]"))
        assertTrue(TimelineMemoryHelper.isPureDirectorInstruction("[接下来让他们去图书馆借书]"))

        // 包含实际剧情或带时间标签的内容不能被误杀
        assertFalse(TimelineMemoryHelper.isPureDirectorInstruction("[第1天·上午] 两人在车站初次相遇"))
        assertFalse(TimelineMemoryHelper.isPureDirectorInstruction("“你今天看起来心情不错。”他微笑着说。"))
    }

    @Test
    fun testFormatEventContentWithCategories() {
        val event1 = TimelineEventItem(
            timeTag = "第 2 天·傍晚",
            content = "确认在学院禁林内严禁使用高阶暗黑魔法",
            category = TimelineCategory.RULE_CONSTRAINT
        )
        val formatted1 = TimelineMemoryHelper.formatEventContent(event1.timeTag, event1.content, event1.category)
        assertEquals("[第 2 天·傍晚] [规则约束] 确认在学院禁林内严禁使用高阶暗黑魔法", formatted1)

        val event2 = TimelineEventItem(
            timeTag = "第 3 天·夜间",
            content = "得知对方其实是失落帝国的第三皇子",
            category = TimelineCategory.CHARACTER_SETTING
        )
        val formatted2 = TimelineMemoryHelper.formatEventContent(event2.timeTag, event2.content, event2.category)
        assertEquals("[第 3 天·夜间] [角色设定] 得知对方其实是失落帝国的第三皇子", formatted2)

        val parsedBack2 = TimelineMemoryHelper.parseContentToEvent(formatted2)
        assertEquals("第 3 天·夜间", parsedBack2.timeTag)
        assertEquals(TimelineCategory.CHARACTER_SETTING, parsedBack2.category)
        assertEquals("得知对方其实是失落帝国的第三皇子", parsedBack2.content)
    }

    @Test
    fun testAtemporalSettingsModelAndDefaults() {
        val setting = AtemporalSettingItem(
            category = "角色设定",
            content = "角色对猫毛严重过敏，极度喜爱黑咖啡",
            isSelected = true,
            targetScope = "session"
        )
        assertTrue(setting.isSelected)
        assertEquals("角色设定", setting.category)
        assertEquals("session", setting.targetScope)

        // 切换为世界规则与全局作用域
        setting.category = "世界规则"
        setting.targetScope = "global"
        assertEquals("世界规则", setting.category)
        assertEquals("global", setting.targetScope)
    }

    @Test
    fun testModelOutputParsing_fullBilingualAndMultiDimensional() {
        val modelResponse = """
            这是从全量对话中梳理的编年史与设定：
            ```json
            {
              "currentStoryTime": "第 6 天·清晨",
              "timelineEvents": [
                {
                  "timeTag": "第 1 天·下午",
                  "category": "PLOT_EVENT",
                  "content": "两人在学院图书馆借了同一本古籍"
                },
                {
                  "timeTag": "第 3 天·傍晚",
                  "category": "RULE_CONSTRAINT",
                  "content": "发现在雨夜如果使用传送法阵会发生魔力紊乱"
                },
                {
                  "timeTag": "第 4 天·夜间",
                  "category": "CHARACTER_SETTING",
                  "content": "主角解开了左手封印，露出了龙族契约印记"
                },
                {
                  "timeTag": "第 5 天·黄昏",
                  "category": "WORLD_SETTING",
                  "content": "王都宣布实行一级防务管制"
                }
              ],
              "atemporalSettings": [
                {
                  "category": "角色特质",
                  "content": "主角始终保持独来独往的冷淡性格，但对甜食缺乏抵抗力",
                  "targetScope": "session"
                },
                {
                  "category": "世界法则",
                  "content": "该世界不可逆转死亡，且不存在跨维度的灵魂复活术",
                  "targetScope": "global"
                }
              ]
            }
            ```
        """.trimIndent()

        val result = TimelineMemoryHelper.parseModelOutput(modelResponse)
        assertEquals("第 6 天·清晨", result.currentStoryTime)
        assertEquals(4, result.events.size)
        assertEquals(TimelineCategory.PLOT_EVENT, result.events[0].category)
        assertEquals(TimelineCategory.RULE_CONSTRAINT, result.events[1].category)
        assertEquals(TimelineCategory.CHARACTER_SETTING, result.events[2].category)
        assertEquals(TimelineCategory.WORLD_SETTING, result.events[3].category)

        assertEquals(2, result.atemporalSettings.size)
        assertTrue(result.atemporalSettings[0].isSelected)
        assertEquals("角色特质", result.atemporalSettings[0].category)
        assertEquals("session", result.atemporalSettings[0].targetScope)

        assertEquals("世界法则", result.atemporalSettings[1].category)
        assertEquals("global", result.atemporalSettings[1].targetScope)
    }

    @Test
    fun testRelativeTimeCalculationAcrossMultipleDays() {
        val currentTime = "第 6 天·清晨"

        assertEquals("今天", TimelineMemoryHelper.calculateRelativeTime("第 6 天·凌晨", currentTime))
        assertEquals("昨天", TimelineMemoryHelper.calculateRelativeTime("第 5 天·黄昏", currentTime))
        assertEquals("前天", TimelineMemoryHelper.calculateRelativeTime("第 4 天·夜间", currentTime))
        assertEquals("3天前", TimelineMemoryHelper.calculateRelativeTime("第 3 天·傍晚", currentTime))
        assertEquals("5天前", TimelineMemoryHelper.calculateRelativeTime("第 1 天·下午", currentTime))
    }

    @Test
    fun testMonotonicTimelineAccumulation_resolvesSecondDayProgression() {
        // 验证问题 2：在第 2 天剧情发生后，后续再次出现的“第二天早上”能正确推断累进为第 3 天早上，而非时光倒退！
        val rawEvents = listOf(
            TimelineEventItem(timeTag = "第 1 天·下午", content = "初次相遇"),
            TimelineEventItem(timeTag = "第 2 天·傍晚", content = "一起在商场吃饭"),
            TimelineEventItem(timeTag = "第 2 天·早上", content = "清晨醒来收拾行囊出发探险"), // 叙事后文出现的第二天早上
            TimelineEventItem(timeTag = "第二天·夜间", content = "在野外扎营轮流守夜") // 再次出现的第二天
        )

        val normalized = TimelineMemoryHelper.normalizeMonotonicTimeline(rawEvents)
        assertEquals(4, normalized.size)
        assertEquals("第 1 天·下午", normalized[0].timeTag)
        assertEquals("第 2 天·傍晚", normalized[1].timeTag)
        assertEquals("第 3 天·早上", normalized[2].timeTag) // 成功单调累加至第 3 天！
        assertEquals("第 4 天·夜间", normalized[3].timeTag) // 再次单调累加至第 4 天！
    }

    @Test
    fun testAtemporalSettingCategoryCycle() {
        val item = AtemporalSettingItem(category = "角色特质")
        assertEquals("习惯偏好", item.nextCategory())
        item.category = "习惯偏好"
        assertEquals("生理禁忌", item.nextCategory())
        item.category = "生理禁忌"
        assertEquals("世界规则", item.nextCategory())
        item.category = "世界规则"
        assertEquals("人际羁绊", item.nextCategory())
        item.category = "人际羁绊"
        assertEquals("角色特质", item.nextCategory())
    }

    @Test
    fun testAtemporalSettingCategoryAndFromKey() {
        assertEquals(TimelineCategory.ATEMPORAL_SETTING, TimelineCategory.fromKey("固有设定"))
        assertEquals(TimelineCategory.ATEMPORAL_SETTING, TimelineCategory.fromKey("常驻设定"))
        assertEquals(TimelineCategory.ATEMPORAL_SETTING, TimelineCategory.fromKey("atemporal"))
        assertEquals(TimelineCategory.ATEMPORAL_SETTING, TimelineCategory.fromKey("固有特质"))

        assertEquals("固有设定", TimelineCategory.ATEMPORAL_SETTING.displayName)
        assertEquals("💡", TimelineCategory.ATEMPORAL_SETTING.emoji)
        assertEquals("#607D8B", TimelineCategory.ATEMPORAL_SETTING.tagColorHex)
    }

    @Test
    fun testParseAndFormatContent_withAtemporalSettingCategory() {
        val raw = "[第 2 天·清晨] [固有设定] 主角习惯在日出前练习剑术且极度厌恶胡萝卜"
        val event = TimelineMemoryHelper.parseContentToEvent(raw)
        assertEquals("第 2 天·清晨", event.timeTag)
        assertEquals(TimelineCategory.ATEMPORAL_SETTING, event.category)
        assertEquals("主角习惯在日出前练习剑术且极度厌恶胡萝卜", event.content)

        val formatted = TimelineMemoryHelper.formatEventContent(event.timeTag, event.content, event.category)
        assertEquals("[第 2 天·清晨] [固有设定] 主角习惯在日出前练习剑术且极度厌恶胡萝卜", formatted)
    }

    @Test
    fun testTimelineReconcileResultMetadata_modelAndFallback() {
        val aiResult = com.aiassistant.utils.TimelineReconcileResult(
            currentStoryTime = "第 3 天·夜间",
            extractionSource = "AI_MODEL",
            modelUsed = "deepseek-chat"
        )
        assertEquals("AI_MODEL", aiResult.extractionSource)
        assertEquals("deepseek-chat", aiResult.modelUsed)
        assertNull(aiResult.extractionErrorMessage)

        val fallbackResult = com.aiassistant.utils.TimelineReconcileResult(
            currentStoryTime = "第 1 天·下午",
            extractionSource = "LOCAL_FALLBACK",
            modelUsed = "",
            extractionErrorMessage = "模型响应超时（已超过180秒），自动触发本地启发式安全解析"
        )
        assertEquals("LOCAL_FALLBACK", fallbackResult.extractionSource)
        assertEquals("", fallbackResult.modelUsed)
        assertNotNull(fallbackResult.extractionErrorMessage)
        assertTrue(fallbackResult.extractionErrorMessage!!.contains("超时"))
    }
}
