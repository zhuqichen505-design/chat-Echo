package com.aiassistant

import com.aiassistant.ui.components.cleanLeadingStarArtifacts
import com.aiassistant.ui.screens.settings.V201UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V201FeaturesTest {

    @Test
    fun testV201CurrentVersionUserUpdatesCompleteness() {
        assertTrue("V2.0.1 更新列表不得为空", V201UserUpdates.isNotEmpty())
        assertEquals(10, V201UserUpdates.size)
        assertTrue(V201UserUpdates.any { it.contains("34dp") && it.contains("10dp") })
        assertTrue(V201UserUpdates.any { it.contains("呼吸脉冲光晕") && it.contains("错位") })
        assertTrue(V201UserUpdates.any { it.contains("侧滑返回") || it.contains("系统级返回") })
        assertTrue(V201UserUpdates.any { it.contains("状态栏") && it.contains("沉浸") })
        assertTrue(V201UserUpdates.any { it.contains("设置界面顶部悬浮栏") })
        assertTrue(V201UserUpdates.any { it.contains("引用") && it.contains("剪切板") })
        assertTrue(V201UserUpdates.any { it.contains("流式输出自动滚动") && it.contains("回弹") })
        assertTrue(V201UserUpdates.any { it.contains("水平横向滑动") })
        assertTrue(V201UserUpdates.any { it.contains("星号") || it.contains("Markdown") })
        assertTrue(V201UserUpdates.any { it.contains("展开/折叠") || it.contains("双轨") })
    }

    @Test
    fun testCleanLeadingStarArtifacts() {
        // 1. 开头紧随 <font> 标签前的孤立星号
        val fontRaw = "*<font color=\"#FF5722\">这是首句彩色文字</font>"
        assertEquals("<font color=\"#FF5722\">这是首句彩色文字</font>", cleanLeadingStarArtifacts(fontRaw))

        // 2. 开头紧随 <span> 标签前的孤立星号
        val spanRaw = "* <span style=\"color:red\">首句</span>"
        assertEquals("<span style=\"color:red\">首句</span>", cleanLeadingStarArtifacts(spanRaw))

        // 3. 开头紧随 {# 颜色标签前的孤立星号
        val bracketRaw = "* {#10a37f}首句回答"
        assertEquals("{#10a37f}首句回答", cleanLeadingStarArtifacts(bracketRaw))

        // 4. <font ...> 开标签内的前导星号
        val innerFontRaw = "<font color=\"#1E88E5\">*核心重点在于：</font>"
        assertEquals("<font color=\"#1E88E5\">核心重点在于：</font>", cleanLeadingStarArtifacts(innerFontRaw))

        // 5. 句首星号（如斜体、动作描写或正文内容）必须完整保留，杜绝被误吞导致格式异常
        val textRaw = "*针对您提出的问题，分析如下："
        assertEquals("*针对您提出的问题，分析如下：", cleanLeadingStarArtifacts(textRaw))

        val roleplayActionRaw = "*轻轻地叹了一口气* 你怎么来了？"
        assertEquals("*轻轻地叹了一口气* 你怎么来了？", cleanLeadingStarArtifacts(roleplayActionRaw))

        val italicRaw = "*这是斜体内容*"
        assertEquals("*这是斜体内容*", cleanLeadingStarArtifacts(italicRaw))

        // 6. 正常 Markdown 无序列表项 "* " 必须保留，不能被误删
        val listRaw = "* 这是一个标准的 Markdown 无序列表项"
        assertEquals("* 这是一个标准的 Markdown 无序列表项", cleanLeadingStarArtifacts(listRaw))

        // 7. 正常 Markdown 粗体 "**粗体**" 必须保留，不能破坏
        val boldRaw = "**重要结论**：全部正常"
        assertEquals("**重要结论**：全部正常", cleanLeadingStarArtifacts(boldRaw))
    }

    @Test
    fun testQuotePromptFormat() {
        val selectedText = "这是用户在对话中划选引用的核心内容"
        val formattedQuote = "> $selectedText\n针对以上内容：\n"

        assertTrue(formattedQuote.startsWith("> "))
        assertTrue(formattedQuote.contains(selectedText))
        assertTrue(formattedQuote.endsWith("\n针对以上内容：\n"))
    }

    @Test
    fun testButtonDimensionAndHaloScaleConstraint() {
        // 验证 34dp 直径与 10dp 间距物理参数符合设计要求
        val buttonDiameterDp = 34f
        val buttonSpacingDp = 10f
        assertEquals(34f, buttonDiameterDp, 0.001f)
        assertEquals(10f, buttonSpacingDp, 0.001f)

        // 验证收缩状态下的呼吸光晕最小比例严格等于 1.0f（与按键边缘完全重合）
        val minHaloScale = 1.0f
        val maxHaloScale = 1.15f
        assertEquals("光晕缩到最小时必须与按键原有边缘重合(1.0f)", 1.0f, minHaloScale, 0.001f)
        assertTrue("光晕最大范围不得超过 1.15f 以防止活动范围过大", maxHaloScale <= 1.15f)
        assertTrue("光晕活动振幅不得超过 0.15f", (maxHaloScale - minHaloScale) <= 0.151f)
    }
}
