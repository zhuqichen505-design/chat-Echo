package com.aiassistant

import com.aiassistant.ui.components.EchoTextToolbarState
import com.aiassistant.ui.screens.chat.TempChatSettings
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V1925UserUpdates
import com.aiassistant.utils.PersonalizationSettings
import com.aiassistant.utils.SmartMemoryExtractor
import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.geometry.Rect

class V1925FeaturesTest {

    @Test
    fun testPersonalizationTemplates() {
        val settings = PersonalizationSettings()
        assertEquals("{model} 正在连接中...", settings.connectingTextTemplate)
        assertEquals("{model} 正在思考中...", settings.thinkingTextTemplate)

        val model = "glm-5.2"
        val connectingText = settings.connectingTextTemplate.replace("{model}", model)
        assertEquals("glm-5.2 正在连接中...", connectingText)

        val thinkingText = settings.thinkingTextTemplate.replace("{model}", model)
        assertEquals("glm-5.2 正在思考中...", thinkingText)

        // Custom templates
        val custom = settings.copy(
            connectingTextTemplate = "Echo 正在呼叫 {model}...",
            thinkingTextTemplate = "{model} 正在高速深度思考..."
        )
        assertEquals("Echo 正在呼叫 deepseek-r1...", custom.connectingTextTemplate.replace("{model}", "deepseek-r1"))
        assertEquals("deepseek-r1 正在高速深度思考...", custom.thinkingTextTemplate.replace("{model}", "deepseek-r1"))
    }

    @Test
    fun testSmartMemoryExtractorSessionRulesAndPreferences() {
        val rulesText = "设定：在这个会话中始终使用中文回答并且不要输出多余解释"
        val candidate = SmartMemoryExtractor.extractCandidate(rulesText)
        assertNotNull("应能提炼本会话的专属规则或约束", candidate)
        assertEquals("conversation", candidate?.suggestedScope)

        val preferenceText = "我的偏好：优先使用Kotlin和Compose"
        val prefCandidate = SmartMemoryExtractor.extractCandidate(preferenceText)
        assertNotNull("应能提炼用户的明确编程偏好", prefCandidate)

        // Casual chat should be filtered out
        val casualChat = "你好啊，今天天气真不错"
        val extractedCasual = SmartMemoryExtractor.extractCandidate(casualChat)
        assertNull("寒暄闲聊应当被高信噪比过滤机制过滤", extractedCasual)
    }

    @Test
    fun testNewConversationDefaults() {
        val tempSettings = TempChatSettings()
        assertEquals("默认 maxTokens 必须为 4096", 4096, tempSettings.maxTokens)
        assertTrue("默认思考模式必须开启", tempSettings.enableThinking)
        assertFalse("默认联网搜索必须关闭", tempSettings.enableWebSearch)
        assertFalse("默认会话专属记忆必须关闭", tempSettings.enableSessionMemory)
    }

    @Test
    fun testEchoTextToolbarCutAndPasteSupport() {
        var cutCalled = false
        var pasteCalled = false
        val state = EchoTextToolbarState(
            rect = Rect(0f, 0f, 100f, 40f),
            onCopy = { },
            onPaste = { pasteCalled = true },
            onCut = { cutCalled = true },
            onSelectAll = { }
        )
        assertNotNull(state.onPaste)
        assertNotNull(state.onCut)
        state.onPaste?.invoke()
        state.onCut?.invoke()
        assertTrue(pasteCalled)
        assertTrue(cutCalled)
    }

    @Test
    fun testV1925CurrentVersionUserUpdatesHighlights() {
        val expectedHighlights = listOf(
            "对话流式响应时增加跟手跟随自动滚动",
            "修复对话顶部悬浮栏在部分机型上背景异常与玻璃穿透问题",
            "修复弹窗状态栏阴影未完整覆盖状态栏顶部边缘",
            "新建对话默认思考开启、联网关闭，专属记忆默认关闭",
            "新对话默认 API 选择入口移至「设置 - API 配置」顶部",
            "思考中与连接中状态文案支持在设置中自定义并一键重置",
            "长按文本工具栏增加剪切与粘贴，修复高频闪烁与空框问题",
            "修复点击中断后错误生成两条回复的问题",
            "对话中错误提示气泡支持折叠与双击展开/收起",
            "优化长列表滚动条滑动稳定性，避免快速拖动断触",
            "输入框展开按钮重构为优雅弧形控制手柄，支持拖拽随手调整高度"
        )
        assertEquals(expectedHighlights.size, V1925UserUpdates.size)
        expectedHighlights.forEach { highlight ->
            assertTrue("更新日志中必须包含: $highlight", V1925UserUpdates.contains(highlight))
        }
    }
}
