package com.aiassistant

import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V215UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V215FeaturesTest {

    @Test
    fun testV215CurrentVersionUserUpdatesCompleteness() {
        val updates = V215UserUpdates
        assertFalse("本次更新日志列表不得为空", updates.isEmpty())
        assertTrue("必须包含当前版本主要更新项 (>=7项)", updates.size >= 7)
        assertTrue("必须包含180s深度推理接入", updates.any { it.contains("180s") && it.contains("推理") })
        assertTrue("必须包含固有设定独立分类与专属筛选", updates.any { it.contains("固有设定") && it.contains("筛选") })
        assertTrue("必须包含固有设定原子化提纯铁律", updates.any { it.contains("原子化") && it.contains("8~25") })
        assertTrue("必须包含长篇编年史里程碑事件法则", updates.any { it.contains("编年史") && it.contains("里程碑") })
        assertTrue("必须包含大模型提炼透明状态指示条", updates.any { it.contains("状态指示") && it.contains("模型") })
        assertTrue("必须包含聊天窗口活动模型动态智能绑定", updates.any { it.contains("活动模型") && it.contains("绑定") })
        assertTrue("必须包含时间输入框时序单调状态机特性保持", updates.any { it.contains("时间输入框") && it.contains("保持") })
    }
}
