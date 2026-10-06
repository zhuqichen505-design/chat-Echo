package com.aiassistant

import com.aiassistant.ui.screens.settings.V214UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V214FeaturesTest {

    @Test
    fun testV214CurrentVersionUserUpdatesCompleteness() {
        val updates = V214UserUpdates
        assertFalse("本次更新日志列表不得为空", updates.isEmpty())
        assertTrue("必须包含当前版本主要更新项 (>=7项)", updates.size >= 7)
        assertTrue("必须包含时间输入框修复", updates.any { it.contains("时间输入框") && it.contains("打字") })
        assertTrue("必须包含时序单向递增状态机", updates.any { it.contains("时序单向递增状态机") && it.contains("第二天") })
        assertTrue("必须包含放开时间轴捕捉上限", updates.any { it.contains("时间轴捕捉上限") && it.contains("4096") })
        assertTrue("必须包含编剧写作指导脱敏与正文事实总结", updates.any { it.contains("写作指导") && it.contains("脱敏") })
        assertTrue("必须包含固有设定6维敏锐挖掘", updates.any { it.contains("固有设定") && it.contains("6 维") })
        assertTrue("必须包含底栏重构为Echo胶囊Dock", updates.any { it.contains("底栏") && it.contains("胶囊 Dock") })
    }
}
