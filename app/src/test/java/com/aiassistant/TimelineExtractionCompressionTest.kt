package com.aiassistant

import com.aiassistant.utils.TimelineMemoryHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Extraction output should keep reusable facts while dropping prose-heavy overflow. */
class TimelineExtractionCompressionTest {
    @Test
    fun eventFactIsBoundedWithoutEllipsisPlaceholder() {
        val raw = "角色在雨夜穿过狭长、潮湿且灯光昏暗的街巷，沿途观察墙面、脚下积水和远处车辆的声音，随后在桥下发现被遗落的地图并决定带回营地仔细研究。"
        val compact = TimelineMemoryHelper.compactEventFact(raw)
        assertTrue(compact.length <= 160)
        assertTrue(compact.isNotBlank())
        assertTrue("不得用省略号掩盖未完成事实", !compact.endsWith("..."))
    }

    @Test
    fun settingFactKeepsOneShortReusableStatement() {
        val raw = "角色惧怕高处并会在临近悬崖时主动寻找稳固落脚点；当同伴提醒后仍会保持警惕，相关反应属于长期弱点而非本次临时情节。"
        val compact = TimelineMemoryHelper.compactSettingFact(raw)
        assertTrue(compact.length <= 96)
        assertTrue(compact.startsWith("角色惧怕高处"))
    }

    @Test
    fun automaticEventDropsOnlyWholeRoutineSentencesAndExactDuplicates() {
        val raw = "两人一起吃饭并闲聊。阳光洒进房间。角色甲因通行证过期被拒绝入城。角色甲因通行证过期被拒绝入城；角色乙承诺补办证件，钥匙去向仍未查明。"
        assertEquals("角色甲因通行证过期被拒绝入城；角色乙承诺补办证件，钥匙去向仍未查明",
            TimelineMemoryHelper.compactAutoTimelineEvent(raw))
        assertEquals("", TimelineMemoryHelper.compactAutoTimelineEvent("双方相互寒暄。两人喝茶后休息。气氛轻松。"))
    }

    @Test
    fun automaticEventKeepsRoutineActionsWhenTheyCarryConsequencesOrUnknownDetails() {
        val facts = listOf("两人吃饭时发现药物中毒", "两人没有喝水，因为水源已被污染", "两人喝茶并约定三日内归还铜钥匙",
            "角色甲向角色乙道谢并承认此前隐瞒了证词", "两人相视一笑后决定解除盟约")
        facts.forEach { assertEquals(it, TimelineMemoryHelper.compactAutoTimelineEvent(it)) }
    }

    @Test
    fun automaticEventNeverCutsUnknownFactsToMeetItsBudget() {
        val raw = "合成角色因证件失效而被拒绝通行，必须等待新证件。" + "未决线索仍需逐一核对，不能认定已经解决。".repeat(5)
        val compact = TimelineMemoryHelper.compactAutoTimelineEvent(raw)
        assertTrue(compact.contains("因证件失效而被拒绝通行"))
        assertTrue(compact.endsWith("不能认定已经解决"))
        assertFalse(compact.contains("..."))
        val indivisible = "角色甲声明" + "这一完整未知事实中的条件必须保留".repeat(10)
        assertTrue(indivisible.length > TimelineMemoryHelper.MAX_AUTO_EVENT_CHARS)
        assertEquals("超预算应交给语义改写，不能机械截断", indivisible, TimelineMemoryHelper.compactAutoTimelineEvent(indivisible))
    }

    @Test
    fun automaticEventKeepsQuotedCodesAndLiteralNamePunctuation() {
        val fact = "角色甲确认口令为“左；右。”且文件标识为 O'Brian-2；角色乙仍未归还钥匙"
        assertEquals(fact, TimelineMemoryHelper.compactAutoTimelineEvent(fact))
        val singleQuoted = "角色甲确认口令为'左。右；上'"
        assertEquals(singleQuoted, TimelineMemoryHelper.compactAutoTimelineEvent(singleQuoted))
    }

    @Test
    fun automaticEventKeepsBracketedCodesAndTheirLiteralPunctuation() {
        for (fact in listOf("[A7]是门禁口令", "【北港-2】是通行标识", "【A；B】是门禁口令", "[A。B]是门禁口令")) {
            assertEquals(fact, TimelineMemoryHelper.compactAutoTimelineEvent(fact))
        }
        assertFalse(TimelineMemoryHelper.isSameTimelineFact("【A；B】是门禁口令", "【AB】是门禁口令"))
        assertFalse(TimelineMemoryHelper.isSameTimelineFact("[A。B]是门禁口令", "[AB]是门禁口令"))
    }

    @Test
    fun automaticEventKeepsQuestionsAndContextDependentAnswers() {
        assertEquals("角色甲询问是否接受盟约；她点头；两人相视一笑",
            TimelineMemoryHelper.compactAutoTimelineEvent("角色甲询问是否接受盟约。她点头。两人相视一笑。"))
        assertEquals("角色乙背叛了盟约？；目前尚无证据",
            TimelineMemoryHelper.compactAutoTimelineEvent("角色乙背叛了盟约？目前尚无证据。"))
        assertEquals("他微笑", TimelineMemoryHelper.compactAutoTimelineEvent("他微笑。"))
        val supplemented = TimelineMemoryHelper.supplementEventContent("角色乙背叛了盟约？", "角色乙背叛了盟约")
        assertEquals("问句和陈述不能在补充时相互吞掉", 2, supplemented.lines().size)
        val mixed = TimelineMemoryHelper.supplementEventContent("北门已经关闭？南门已经关闭？", "北门已经关闭？南门已经关闭")
        assertEquals(listOf("北门已经关闭？", "南门已经关闭？", "南门已经关闭"), mixed.lines())
        assertEquals("重复补充不能再次新增相同确认", mixed,
            TimelineMemoryHelper.supplementEventContent(mixed, "北门已经关闭？南门已经关闭"))
    }

    @Test
    fun factComparisonIgnoresOnlyUnquotedDeclarativeSeparators() {
        assertTrue(TimelineMemoryHelper.isSameTimelineFact("角色甲交付药品。角色乙签收。", "角色甲交付药品；角色乙签收"))
        assertTrue(TimelineMemoryHelper.isSameTimelineFact("角色甲确认口令为“A；B”", "角色甲确认口令为\"A；B\""))
        for ((before, after) in listOf(
            "角色甲确认口令为“AB”" to "角色甲确认口令为“A；B”",
            "角色甲确认口令为'AB'" to "角色甲确认口令为'A。B'",
            "角色甲确认标识为 OBrian-2" to "角色甲确认标识为 O'Brian-2",
            "角色乙背叛了盟约" to "角色乙背叛了盟约？"
        )) assertFalse("引用内标点、字面标识及问句不能被当成同一事实", TimelineMemoryHelper.isSameTimelineFact(before, after))
    }
}
