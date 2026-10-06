package com.aiassistant

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.aiassistant.ui.components.EchoTextToolbarState
import com.aiassistant.ui.components.parseInlineColor
import com.aiassistant.ui.components.parseInlineMarkdown
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import org.junit.Assert.*
import org.junit.Test

class V1926FeaturesTest {

    @Test
    fun testFontColorParsingTolerantHex() {
        // Test non-standard hex typos like "2B7DEP" where P is mapped to 0 -> 2B7DE0
        val parsedTypo = parseInlineColor("2B7DEP")
        assertNotNull("2B7DEP 应该被容错解析为有效颜色", parsedTypo)
        assertEquals(Color(0xFF2B7DE0), parsedTypo)

        // Test with # prefix
        val parsedHash = parseInlineColor("#2B7DE0")
        assertNotNull(parsedHash)
        assertEquals(Color(0xFF2B7DE0), parsedHash)

        // Test 3-digit hex "00F" -> "#0000FF"
        val parsed3Digit = parseInlineColor("00F")
        assertNotNull(parsed3Digit)
        assertEquals(Color(0xFF0000FF), parsed3Digit)

        // Test standard named colors
        val blueColor = parseInlineColor("blue")
        assertNotNull(blueColor)
        assertEquals(Color(0xFF2563EB), blueColor)

        val cyanColor = parseInlineColor("cyan")
        assertNotNull(cyanColor)
        assertEquals(Color(0xFF06B6D4), cyanColor)
    }

    @Test
    fun testFontTagAndDanglingAsteriskInMarkdown() {
        val rawMarkdown = "前置文本<font color=\"2B7DEP\">重点核心内容</font>*后置文本"
        val annotatedString = parseInlineMarkdown(rawMarkdown)
        val plainText = annotatedString.text

        // The text should cleanly contain "前置文本重点核心内容后置文本"
        assertEquals("前置文本重点核心内容后置文本", plainText)
        assertFalse("不应残留 <font> 原始标签", plainText.contains("<font"))
        assertFalse("不应残留 </font> 原始标签", plainText.contains("</font>"))
        assertFalse("不应包含悬挂的尾随星号", plainText.contains("*"))

        // Verify that the styled span exists for "重点核心内容"
        val spans = annotatedString.spanStyles
        val coloredSpan = spans.find { span ->
            span.item.color != Color.Unspecified
        }
        assertNotNull("重点核心内容应当被应用颜色样式", coloredSpan)
        assertEquals(Color(0xFF2B7DE0), coloredSpan?.item?.color)
    }

    @Test
    fun testEchoTextToolbarStateEquivalence() {
        val rect1 = Rect(10f, 20f, 100f, 60f)
        val state1 = EchoTextToolbarState(
            rect = rect1,
            canCopy = true,
            canPaste = false,
            canCut = false,
            canSelectAll = true,
            onCopy = { /* lambda 1 */ },
            onPaste = null,
            onCut = null,
            onSelectAll = { /* lambda 1 */ }
        )

        // Same abilities and very close rect (within 2px) but different lambda references
        val rect2 = Rect(11f, 20f, 100f, 61f)
        assertTrue(
            "微小位移和同等能力标志且lambda引用不同时，isEquivalent 必须返回 true 避免无限重组与闪烁",
            state1.isEquivalent(
                newRect = rect2,
                hasCopy = true,
                hasPaste = false,
                hasCut = false,
                hasSelectAll = true
            )
        )

        // Large position shift (> 8px)
        val rectShifted = Rect(50f, 20f, 150f, 60f)
        assertFalse(
            "位置明显发生变化时，isEquivalent 应返回 false 以便正确移动弹窗",
            state1.isEquivalent(
                newRect = rectShifted,
                hasCopy = true,
                hasPaste = false,
                hasCut = false,
                hasSelectAll = true
            )
        )

        // Capability flag change (e.g. Paste becomes available)
        assertFalse(
            "菜单动作能力发生改变时，isEquivalent 应返回 false 以便刷新按钮集",
            state1.isEquivalent(
                newRect = rect1,
                hasCopy = true,
                hasPaste = true,
                hasCut = false,
                hasSelectAll = true
            )
        )
    }

    @Test
    fun testV1926UserUpdatesHighlights() {
        val expectedHighlights = listOf(
            "顶部悬浮栏与错误弹窗直接复用输入框玻璃背景规范，消除悬浮栏与弹窗状态栏阴影异常穿透",
            "输入框右上角弧线控制手柄与边框精确同心贴合，拖拽微调更优雅",
            "全面兼容大模型非标颜色标签（如 <font color=\"2B7DEP\"> 与尾随星号）的精准渲染",
            "加宽用户输入气泡与上一条模型回复的纵向间距，提升长对话视觉呼吸感",
            "思考强度快速档重调为柔和纯正天蓝色，告别偏灰暗沉感",
            "对话页悬浮滚动快捷键升级为4键独立体系（到顶/上一条/下一条/到底），阶梯色彩与双线箭头",
            "文本长按选中弹窗防抖优化",
            "延续新建对话专属记忆默认关闭与长列表滚动条防断触优化"
        )
        assertEquals(8, com.aiassistant.ui.screens.settings.V1926UserUpdates.size)
        expectedHighlights.forEach { highlight ->
            assertTrue("更新日志中必须包含: $highlight", com.aiassistant.ui.screens.settings.V1926UserUpdates.contains(highlight))
        }
    }
}
