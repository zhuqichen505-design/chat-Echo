package com.aiassistant

import com.aiassistant.utils.DayPhase
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineMemoryHelper
import org.junit.Assert.*
import org.junit.Test

/**
 * 需求复核专项测试：
 * 1. 泛化时序参照系与全域里程碑看板测试；
 * 2. 粗粒度/笼统时间节点概念理解与先后相对判断测试；
 * 3. 普通知识问答/技术咨询与小说剧情严格隔离测试；
 * 4. 显式故事时间线事件检测。
 */
class TimelineGeneralizationAndIsolationTest {

    // 1. 全域里程碑事件泛化性测试：涵盖人际剧变、冲突决战、生死境界、环境迁徙与人生节点
    @Test
    fun testGeneralizedMilestoneRecognition() {
        // 关系剧变与人际羁绊
        assertTrue("恋爱确立关系", TimelineMemoryHelper.isCoreMilestoneEvent("男女主角正式在一起并互许终身"))
        assertTrue("表白/告白", TimelineMemoryHelper.isCoreMilestoneEvent("在樱花树下向对方告白"))
        assertTrue("反目决裂", TimelineMemoryHelper.isCoreMilestoneEvent("同袍决裂，拔刀相向"))
        assertTrue("盟约背叛", TimelineMemoryHelper.isCoreMilestoneEvent("昔日盟友突然背叛誓言"))
        assertTrue("拜师收徒", TimelineMemoryHelper.isCoreMilestoneEvent("正式拜入青云门下拜师学艺"))
        assertTrue("成婚契约", TimelineMemoryHelper.isCoreMilestoneEvent("二人结下神魂契约并举行大婚"))

        // 重大冲突与决战转折
        assertTrue("重大决战", TimelineMemoryHelper.isCoreMilestoneEvent("北境长城终极决战爆发"))
        assertTrue("刺杀遇刺", TimelineMemoryHelper.isCoreMilestoneEvent("深夜行刺当朝重臣遇刺身亡"))
        assertTrue("破城突围", TimelineMemoryHelper.isCoreMilestoneEvent("守军突围成功，城池陷落"))
        assertTrue("称帝登基", TimelineMemoryHelper.isCoreMilestoneEvent("平定天下后登基为帝"))
        assertTrue("坠崖遇险", TimelineMemoryHelper.isCoreMilestoneEvent("被逼入绝境坠崖失踪"))

        // 生死境界与质变
        assertTrue("战死牺牲", TimelineMemoryHelper.isCoreMilestoneEvent("老将军为掩护撤退战死沙场"))
        assertTrue("突破晋升", TimelineMemoryHelper.isCoreMilestoneEvent("闭关三月终于突破至元婴期"))
        assertTrue("觉醒蜕变", TimelineMemoryHelper.isCoreMilestoneEvent("神兽血脉觉醒，实力暴涨"))
        assertTrue("重伤痊愈", TimelineMemoryHelper.isCoreMilestoneEvent("服下九转还魂丹后重伤痊愈"))
        assertTrue("记忆恢复", TimelineMemoryHelper.isCoreMilestoneEvent("触碰旧物瞬间恢复记忆"))

        // 环境迁徙与人生节点
        assertTrue("开学典礼", TimelineMemoryHelper.isCoreMilestoneEvent("新生开学入读第一魔法学院"))
        assertTrue("宗门灭门", TimelineMemoryHelper.isCoreMilestoneEvent("家族惨遭神秘势力灭门惨祸"))
        assertTrue("启程远征", TimelineMemoryHelper.isCoreMilestoneEvent("率领舰队启程远征星海"))
        assertTrue("流放边陲", TimelineMemoryHelper.isCoreMilestoneEvent("被贬削爵流放到极北苦寒之地"))

        // 普通日常对话/非里程碑事件必须返回 false
        assertFalse("普通买菜日常", TimelineMemoryHelper.isCoreMilestoneEvent("今天去超市买了些新鲜蔬菜和水果"))
        assertFalse("普通技术讨论", TimelineMemoryHelper.isCoreMilestoneEvent("讨论了关于数据库索引优化的几个方案"))
        assertFalse("日常问候", TimelineMemoryHelper.isCoreMilestoneEvent("早上好，今天天气真不错"))
    }

    // 2. 粗粒度/笼统时间节点概念理解与先后相对判断测试
    @Test
    fun testCoarseGrainedTimeSpanAndRelativeOrder() {
        // 精准数字天数差推算
        assertEquals("今天", TimelineMemoryHelper.calculateRelativeTime("第 5 天", "第 5 天"))
        assertEquals("昨天", TimelineMemoryHelper.calculateRelativeTime("第 4 天", "第 5 天"))
        assertEquals("前天", TimelineMemoryHelper.calculateRelativeTime("第 3 天", "第 5 天"))
        assertEquals("4天前", TimelineMemoryHelper.calculateRelativeTime("第 1 天", "第 5 天"))
        assertEquals("约2周前", TimelineMemoryHelper.calculateRelativeTime("第 1 天", "第 15 天"))
        assertEquals("约1个月前", TimelineMemoryHelper.calculateRelativeTime("第 1 天", "第 35 天"))
        assertEquals("数月前（约100天前）", TimelineMemoryHelper.calculateRelativeTime("第 1 天", "第 101 天"))

        // 没有共同参照的自然时间不能套用固定学期/假期长度或直接相减。
        listOf("暑假期间" to "新学期", "暑假快结束" to "开学第一天",
            "放假首日" to "新学期", "暑假开始" to "暑假快结束",
            "开学" to "深秋", "寒假" to "来年春天",
            "第 1 天" to "两周后", "第 1 天" to "几天后",
            "两周后" to "一个月后").forEach { (past, now) ->
            assertNull("自然标签没有共同精确参照，不能臆造相对天数：$past / $now",
                TimelineMemoryHelper.calculateRelativeTime(past, now))
        }
    }

    // 3. 全时空时序守护提示词看板测试：泛化里程碑与三大铁律注入
    @Test
    fun testTimelinePromptContextWithGeneralizedMilestonesAndLaws() {
        val memories = listOf(
            "[放假首日] 宗门惨遭神秘仇家灭门，二人发誓复仇并踏上逃亡之路",
            "[暑假中期] 二人在极北雪原找到古修士洞府，男主突破至金丹期",
            "[新学期] 二人改换容貌以普通弟子身份潜入天剑宗调查真相"
        )
        val currentTime = "新学期·晨曦"
        val promptContext = TimelineMemoryHelper.buildTimelinePromptContext(currentTime, memories)

        // 1. 验证时空看板
        assertTrue(promptContext.contains("【故事当前时间节点与时空看板】"))
        assertTrue(promptContext.contains("新学期·晨曦"))

        // 2. 里程碑保留原阶段锚点；没有实际日期，不能凭假期/学期名称编造相对跨度。
        assertTrue("必须识别灭门逃亡里程碑", promptContext.contains("宗门惨遭神秘仇家灭门"))
        assertTrue("里程碑必须保留已知的原文阶段", promptContext.contains("★ [放假首日] 宗门惨遭神秘仇家灭门"))
        assertFalse("无法从放假首日与新学期推出固定的日/月差", promptContext.contains("注意：此事件发生在"))
        assertFalse("不能臆造假期长度", promptContext.contains("两个月前"))

        // 3. 验证三大泛化时序铁律完整注入
        assertTrue("必须包含三大铁律总纲", promptContext.contains("全域叙事时序三大铁律，大模型必须无条件遵守"))
        assertTrue("铁律一：时序参照系与相对跨度守恒律", promptContext.contains("1.【时序参照系与相对跨度守恒律】"))
        assertTrue("铁律二：日内时序与生理作息连贯律", promptContext.contains("2.【日内时序与生理作息连贯律】"))
        assertTrue("铁律三：跨度锚点与宏观阶段连贯律", promptContext.contains("3.【跨度锚点与宏观阶段连贯律】"))
    }

    // 4. 普通知识问答与日常技术会话严格隔离测试
    @Test
    fun testNormalConversationIsolationFromNarrative() {
        // 技术问答输入：严禁识别为叙事会话
        val techUser1 = "如何使用 Kotlin 协程实现并发请求合并与超时控制？"
        val techAssistant1 = """
            可以使用 `async` 与 `awaitAll` 进行并发请求合并：
            ```kotlin
            suspend fun fetchAll(): List<Data> = coroutineScope {
                val deferred1 = async { api.getA() }
                val deferred2 = async { api.getB() }
                awaitAll(deferred1, deferred2)
            }
            ```
        """.trimIndent()
        assertFalse("纯技术编程问答绝不能识别为剧情叙事", TimelineMemoryHelper.isNarrativeOrCreativeTurn(techUser1, techAssistant1))

        val techUser2 = "帮我解释一下这段 Android build.gradle.kts 为什么报错"
        val techAssistant2 = "这是因为 targetSdk 和 compileSdk 版本不匹配，请升级 dependencies 中的库版本。"
        assertFalse("构建排错绝不能识别为剧情叙事", TimelineMemoryHelper.isNarrativeOrCreativeTurn(techUser2, techAssistant2))

        val factualUser = "请翻译以下英文短文并总结三个要点"
        val factualAssistant = "1. 要点一；2. 要点二；3. 要点三。"
        assertFalse("翻译摘要绝不能识别为剧情叙事", TimelineMemoryHelper.isNarrativeOrCreativeTurn(factualUser, factualAssistant))

        // 小说剧情/角色扮演对话：正确识别为叙事交互
        val storyUser = "继续写下一章，让顾辰和林清雪在雨夜客栈重逢"
        val storyAssistant = "窗外风雨交加，顾辰推开客栈虚掩的木门，视线与坐在角落里的女子相遇。林清雪微微抬眸，神色复杂地轻声道：“你终究还是来了。”"
        assertTrue("小说情节推进必须识别为叙事交互", TimelineMemoryHelper.isNarrativeOrCreativeTurn(storyUser, storyAssistant))

        val roleplayUser = "（拔出长剑，冷冷地看着前方的黑衣人）今天就是做个了断的时候！"
        val roleplayAssistant = "黑衣人冷笑一声，身形如鬼魅般一闪而逝：“就凭你现在的修为，也妄想与老夫抗衡？”"
        assertTrue("角色扮演对白与动作必须识别为叙事交互", TimelineMemoryHelper.isNarrativeOrCreativeTurn(roleplayUser, roleplayAssistant))
    }

    // 5. 显式故事时间线事件判定测试
    @Test
    fun testIsExplicitTimelineEvent() {
        assertTrue(TimelineMemoryHelper.isExplicitTimelineEvent("[第1天] 初遇"))
        assertTrue(TimelineMemoryHelper.isExplicitTimelineEvent("【第3天·傍晚】 突破境界"))
        assertTrue(TimelineMemoryHelper.isExplicitTimelineEvent("[DAY 5] 抵达要塞"))
        assertTrue(TimelineMemoryHelper.isExplicitTimelineEvent("[暑假开始] 一起去旅行"))
        assertTrue(TimelineMemoryHelper.isExplicitTimelineEvent("[两周后] 伤势痊愈"))

        assertFalse(TimelineMemoryHelper.isExplicitTimelineEvent("用户偏好：回答请保持严谨简洁"))
        assertFalse(TimelineMemoryHelper.isExplicitTimelineEvent("核心准则：严禁自称特助或老板"))
        assertFalse(TimelineMemoryHelper.isExplicitTimelineEvent("开发项目名称：Echo AI Assistant"))
        assertFalse(TimelineMemoryHelper.isExplicitTimelineEvent("【角色特征】合成角色性格沉稳"))
        assertFalse(TimelineMemoryHelper.isExplicitTimelineEvent("【习惯偏好】合成角色喜欢清晨散步"))
        assertFalse(TimelineMemoryHelper.isExplicitTimelineEvent("【生理禁忌】合成角色对花粉过敏"))
        assertFalse(TimelineMemoryHelper.isExplicitTimelineEvent("【世界规则】古塔禁止火焰魔法"))
        assertTrue(TimelineMemoryHelper.isExplicitTimelineEvent("【清晨】抵达古塔"))
        assertTrue(TimelineMemoryHelper.isExplicitTimelineEvent("【2026年10月6日】抵达古塔"))
    }
}
