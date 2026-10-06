package com.aiassistant

import com.aiassistant.ui.components.CropShapeMode
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V219UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V219FeaturesTest {

    @Test
    fun testV219UserUpdatesCompleteness() {
        val updates = V219UserUpdates
        assertFalse("V219 更新日志列表不得为空", updates.isEmpty())
        assertEquals("必须包含全部 6 项主要更新项", 6, updates.size)

        assertTrue("必须包含 12 项需求全量核验说明", updates.any { it.contains("12 项全项目专项需求深度核验与精细落地") })
        assertTrue("必须包含全局图片手势裁剪与编辑统一闭环说明", updates.any { it.contains("全局图片手势裁剪与编辑统一闭环") })
        assertTrue("必须包含手势微调自由操控说明", updates.any { it.contains("手势微调自由操控") })
        assertTrue("必须包含记忆提炼辅助模型层级归一与自由直选说明", updates.any { it.contains("记忆提炼辅助模型层级归一与自由直选") })
        assertTrue("必须包含跨服务商自由直选说明", updates.any { it.contains("跨服务商自由直选") })
        assertTrue("必须包含即时测试连接与平滑降级验证说明", updates.any { it.contains("即时测试连接与平滑降级验证") })
    }

    @Test
    fun testCropShapeModeEnumValues() {
        val modes = CropShapeMode.values()
        assertTrue("必须包含 CIRCLE 模式", modes.contains(CropShapeMode.CIRCLE))
        assertTrue("必须包含 RECTANGLE 模式", modes.contains(CropShapeMode.RECTANGLE))
    }
}
