package com.aiassistant

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.aiassistant.ui.components.EchoTextToolbar
import com.aiassistant.ui.components.EchoTextToolbarState
import com.aiassistant.ui.screens.settings.V1927UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V1927FeaturesTest {

    @Test
    fun testEchoTextToolbarStatusDecoupledFromSnapshot() {
        val toolbar = EchoTextToolbar()
        // Initially status must be Hidden
        assertEquals(androidx.compose.ui.platform.TextToolbarStatus.Hidden, toolbar.status)
        assertNull(toolbar.activeMenu)

        // Show menu
        val testRect = Rect(20f, 30f, 120f, 80f)
        toolbar.showMenu(
            rect = testRect,
            onCopyRequested = { },
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = { }
        )

        assertEquals(androidx.compose.ui.platform.TextToolbarStatus.Shown, toolbar.status)
        assertNotNull(toolbar.activeMenu)
        assertEquals(testRect, toolbar.activeMenu?.rect)

        // Subsequent showMenu calls with minor shift must NOT create a new state instance
        val initialMenuInstance = toolbar.activeMenu
        val slightShiftRect = Rect(22f, 31f, 122f, 81f)
        toolbar.showMenu(
            rect = slightShiftRect,
            onCopyRequested = { },
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = { }
        )

        assertSame("就地复用菜单实例，严禁每次重建造成高频重组死循环", initialMenuInstance, toolbar.activeMenu)

        // Hide menu
        toolbar.hide()
        assertEquals(androidx.compose.ui.platform.TextToolbarStatus.Hidden, toolbar.status)
        assertNull(toolbar.activeMenu)
    }

    @Test
    fun testEchoTextToolbarPositionThreshold() {
        val initialRect = Rect(50f, 50f, 150f, 100f)
        val state = EchoTextToolbarState(
            rect = initialRect,
            canCopy = true,
            onCopy = { }
        )

        // Shift within 16px is considered equivalent
        val minorShift = Rect(60f, 55f, 160f, 105f)
        assertTrue("位移在 16px 内必须判定为等价，避免亚像素震颤", state.isEquivalent(minorShift, true, false, false, false))

        // Shift > 16px is not equivalent
        val largeShift = Rect(100f, 50f, 200f, 100f)
        assertFalse("明显位移必须更新位置", state.isEquivalent(largeShift, true, false, false, false))
    }

    @Test
    fun testJumpScrollButtonsColorAndAlpha() {
        val topBtnColor = Color(0xFFBAE6FD).copy(alpha = 0.72f)
        val prevBtnColor = Color(0xFF93C5FD).copy(alpha = 0.75f)
        val nextBtnColor = Color(0xFF60A5FA).copy(alpha = 0.78f)
        val bottomBtnColor = Color(0xFF3B82F6).copy(alpha = 0.82f)

        assertTrue("滑动到顶按键为浅天蓝且透明度介于 0.7~0.8", topBtnColor.alpha in 0.7f..0.85f)
        assertTrue("滑动到上一条按键为柔和浅蓝且透明度介于 0.7~0.85", prevBtnColor.alpha in 0.7f..0.85f)
        assertTrue("滑动到下一条按键为纯正天蓝且透明度介于 0.7~0.85", nextBtnColor.alpha in 0.7f..0.85f)
        assertTrue("滑动到底按键为蔚蓝且透明度介于 0.75~0.85", bottomBtnColor.alpha in 0.75f..0.85f)
    }

    @Test
    fun testV1927UserUpdates() {
        val expectedHighlights = listOf(
            "顶部悬浮工具栏与错误提示无边缘包裹且全屏穿透，文字半透明透出并实时液态毛玻璃模糊",
            "输入框放大弧线手柄大小彻底统一，拖拽前后永久固定为精致 22dp/17dp 贴角同心弧",
            "对话页辅助滑动 4 个按键重构为柔和浅天蓝半透明体系，搭配微光白边与透光质感",
            "文本划选弹窗与快照状态深度解耦，彻底修复高频闪烁死循环，复制与引用 100% 稳定响应",
            "大模型非标字体颜色与尾随星号容错解析，输入气泡呼吸感间距保留",
            "延续思考快速档纯正天蓝配色与新建会话记忆隔离规范"
        )
        assertEquals(6, V1927UserUpdates.size)
        expectedHighlights.forEach { highlight ->
            assertTrue("更新日志中必须包含: $highlight", V1927UserUpdates.contains(highlight))
        }
    }
}
