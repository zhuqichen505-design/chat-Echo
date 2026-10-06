package com.aiassistant

import com.aiassistant.domain.model.TimelineNode
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineMemoryHelper
import org.junit.Assert.*
import org.junit.Test

class TimelineArchitectureAndOptimizationTest {

    @Test
    fun testSameSceneMultiTurnConversationDoesNotErroneouslyCrossDays() {
        // 用户需求 2：一件事情（比如吃饭）由多轮对话组成，但模型旧算法会错误地将其分成多天的多顿饭。
        // 测试在同一天内，连续多轮关于聚餐/下午茶的对话不会导致天数无限累进。
        val events = listOf(
            TimelineEventItem(
                timeTag = "第 1 天·傍晚",
                content = "两人在商业街餐厅碰面，点了一份寿喜烧",
                category = TimelineCategory.PLOT_EVENT
            ),
            TimelineEventItem(
                timeTag = "第 1 天·傍晚",
                content = "席间聊起各自过去的经历，氛围逐渐融洽",
                category = TimelineCategory.PLOT_EVENT
            ),
            TimelineEventItem(
                timeTag = "第 1 天·傍晚",
                content = "服务员端上甜点，两人共同品尝了抹茶冰淇淋",
                category = TimelineCategory.PLOT_EVENT
            ),
            TimelineEventItem(
                timeTag = "第 1 天·入夜",
                content = "用餐结束，走出餐厅漫步在夜风微凉的街道",
                category = TimelineCategory.PLOT_EVENT
            )
        )

        val normalized = TimelineMemoryHelper.normalizeMonotonicTimeline(events)

        assertEquals(4, normalized.size)
        // 验证前三个同一场景的事件全部保持在第 1 天，不会被递增成第 2 天、第 3 天
        assertEquals("第 1 天·傍晚", normalized[0].timeTag)
        assertEquals("第 1 天·傍晚", normalized[1].timeTag)
        assertEquals("第 1 天·傍晚", normalized[2].timeTag)
        assertEquals("第 1 天·入夜", normalized[3].timeTag)
    }

    @Test
    fun testPreventAbnormalTimeJumpFromRetrospectivePhrases() {
        // 用户需求 2：杜绝不正常的时间大跳跃。
        // 口语中提及“两年前的旧事”或“这两天的见闻”，旧逻辑误识别为前向跳跃 730 天。
        val retrospectiveText1 = "回忆起两年前我们刚认识的时候，也是在这样一个下雪天"
        val jumpDays1 = TimelineMemoryHelper.estimateTimeSpanJumpDays(retrospectiveText1)
        assertEquals("口头提及两年前回忆不应导致当前剧情时间暴跳 730 天", 0, jumpDays1)

        val retrospectiveText2 = "这两天天气真不错，适合出门走走"
        val jumpDays2 = TimelineMemoryHelper.estimateTimeSpanJumpDays(retrospectiveText2)
        assertEquals("日常用语'这两天'不应被当作跳跃两天", 0, jumpDays2)

        val retrospectiveText3 = "数天前发生的那场风波总算平息了"
        val jumpDays3 = TimelineMemoryHelper.estimateTimeSpanJumpDays(retrospectiveText3)
        assertEquals("回顾'数天前'不应向前跳跃", 0, jumpDays3)

        // 真正的剧情推进用词应当正常识别
        val forwardProgressText = "眨眼间过了整整三天，伤势终于好转"
        val jumpDaysForward = TimelineMemoryHelper.estimateTimeSpanJumpDays(forwardProgressText)
        assertTrue("真正剧情推进3天应当识别", jumpDaysForward in 2..4)
    }

    @Test
    fun testBuildTimelineNodesPromptContext_PhysicalIsolationFromSettings() {
        // 用户需求 1：时间和事件有独立、单一的存放位置，且在上下文注入中与纯设定物理隔离
        val nodes = listOf(
            TimelineNode(
                id = 1L,
                conversationId = 100L,
                timeTag = "第 1 天·上午",
                event = "在列车上初次偶遇，互换了姓名与联络方式",
                category = TimelineCategory.PLOT_EVENT.key,
                orderIndex = 1,
                createdAt = 1000L
            ),
            TimelineNode(
                id = 2L,
                conversationId = 100L,
                timeTag = "第 1 天·入夜",
                event = "在雨夜的车站再次相遇，角色递给对方一把黑色雨伞",
                category = TimelineCategory.TURNING_POINT.key,
                orderIndex = 2,
                createdAt = 2000L
            )
        )

        val contextPrompt = TimelineMemoryHelper.buildTimelineNodesPromptContext(
            nodes = nodes,
            currentStoryTime = "第 1 天·入夜"
        )

        assertTrue(contextPrompt.contains("<session_timeline>"))
        assertTrue(contextPrompt.contains("</session_timeline>"))
        assertTrue(contextPrompt.contains("【当前故事时间节点】：第 1 天·入夜"))
        assertTrue(contextPrompt.contains("[1] [第 1 天·上午] 【剧情事件】在列车上初次偶遇，互换了姓名与联络方式"))
        assertTrue(contextPrompt.contains("[2] [第 1 天·入夜] 【转折关键】在雨夜的车站再次相遇，角色递给对方一把黑色雨伞"))
        assertTrue(contextPrompt.contains("请严格基于该时序脉络推进"))
    }

    @Test
    fun testCleanTimelineResiduesFromMemories() {
        // 用户需求 1：时间和事件存入独立时间线后，记忆库中不再混杂时间标签脏数据
        val mixedMemories = listOf(
            "平时对猫毛轻微过敏，但很喜欢猫",
            "【当前故事时间】：第 2 天·黄昏",
            "[第 1 天·上午] 在列车站碰面",
            "【第 1 天·入夜】在海边散步",
            "性格温和但内心极有主见，不轻易妥协",
            "[主线剧情] [第 2 天] 共同破解了遗迹密码"
        )

        val cleaned = TimelineMemoryHelper.cleanTimelineResiduesFromMemories(mixedMemories)

        // 验证时间标记、时间事件与故事时间标签均被剔除，仅保留纯粹的角色与世界观设定
        assertEquals(2, cleaned.size)
        assertEquals("平时对猫毛轻微过敏，但很喜欢猫", cleaned[0])
        assertEquals("性格温和但内心极有主见，不轻易妥协", cleaned[1])
    }

    @Test
    fun testParseModelOutputSeparatesTimelineAndAtemporalSettings() {
        // 用户需求 1 & 2：梳理时间线时，将具有明确时空节点的事件与非时序设定彻底解耦
        val jsonOutput = """
            ```json
            {
              "currentStoryTime": "第 2 天·黄昏",
              "timelineEvents": [
                {
                  "timeTag": "第 2 天·早晨",
                  "event": "在古城钟楼前汇合，商讨探险路线",
                  "category": "PLOT_EVENT"
                },
                {
                  "timeTag": "第 2 天·黄昏",
                  "event": "到达山顶遗迹入口，发现了古代铭文",
                  "category": "TURNING_POINT"
                }
              ],
              "atemporalSettings": [
                {
                  "category": "CHARACTER_SETTING",
                  "content": "精通古代语言学，随身携带牛皮笔记本",
                  "confidence": 0.95
                },
                {
                  "category": "WORLD_SETTING",
                  "content": "该遗迹每逢满月之夜会释放微弱的幽蓝荧光",
                  "confidence": 0.90
                }
              ]
            }
            ```
        """.trimIndent()

        val result = TimelineMemoryHelper.parseModelOutput(jsonOutput)

        assertEquals("第 2 天·黄昏", result.currentStoryTime)
        assertEquals(2, result.events.size)
        assertEquals("第 2 天·早晨", result.events[0].timeTag)
        assertEquals("在古城钟楼前汇合，商讨探险路线", result.events[0].content)
        assertEquals(TimelineCategory.PLOT_EVENT, result.events[0].category)

        assertEquals(2, result.atemporalSettings.size)
        assertEquals("精通古代语言学，随身携带牛皮笔记本", result.atemporalSettings[0].content)
        assertEquals("CHARACTER_SETTING", result.atemporalSettings[0].category)
    }

    @Test
    fun testTimelineNodeEntityProperties() {
        // 验证 TimelineNode 实体的完整性与默认值
        val node = TimelineNode(
            conversationId = 888L,
            timeTag = "次日·破晓",
            event = "整顿行装，迎着朝阳出发",
            category = TimelineCategory.PLOT_EVENT.key,
            orderIndex = 0
        )

        assertEquals(0L, node.id)
        assertEquals(888L, node.conversationId)
        assertEquals("次日·破晓", node.timeTag)
        assertEquals("整顿行装，迎着朝阳出发", node.event)
        assertEquals(TimelineCategory.PLOT_EVENT.key, node.category)
        assertTrue(node.createdAt > 0L)
    }
}
