package com.aiassistant

import com.aiassistant.utils.TimelineMemoryHelper
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
}
