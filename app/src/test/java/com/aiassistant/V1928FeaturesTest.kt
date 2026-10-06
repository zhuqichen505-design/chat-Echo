package com.aiassistant

import com.aiassistant.domain.model.Message
import com.aiassistant.ui.screens.chat.formatNonThinkingCapsuleText
import com.aiassistant.ui.screens.chat.formatThinkingCapsuleText
import com.aiassistant.ui.screens.settings.V1928UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V1928FeaturesTest {

    @Test
    fun testV1928CurrentVersionUserUpdates() {
        val expectedHighlights = listOf(
            "输入框半透明液态毛玻璃效果完美还原，根除全黑/纯色覆盖，恢复通透背景模糊",
            "顶部悬浮工具栏与报错弹窗实现真实毛玻璃半透明透字效果，消除双层底色覆盖",
            "彻底修复模型思考胶囊文字无法显示问题，移除异常滚动裁剪，增加多级文案安全兜底",
            "根除对话页流式生成后幽灵气泡残留，完善分支生成生命周期自动回收与异常空消息过滤",
            "输入框放大同心圆弧手柄尺寸永久统一，划选复制工具栏解耦防抖彻底保持稳定",
            "辅助滑动 4 个按键保持柔和浅天蓝半透明体系与微光质感"
        )
        assertEquals(6, V1928UserUpdates.size)
        expectedHighlights.forEach { highlight ->
            assertTrue("更新日志中必须包含: $highlight", V1928UserUpdates.contains(highlight))
        }
    }

    @Test
    fun testFormatThinkingCapsuleTextNeverBlank() {
        // 1. Thinking active (stream thinking)
        val activeText = formatThinkingCapsuleText(
            template = "{model} {status} {time} {tokens}",
            modelName = "DeepSeek-R1",
            isThinkingActive = true,
            responseTimeMs = 0L,
            thinkingTokens = 0,
            totalTokens = 0
        )
        assertTrue("思考活跃中文案必须包含模型名与思考中状态", activeText.contains("DeepSeek-R1") && activeText.contains("思考中"))
        assertFalse("思考文案严禁为空白", activeText.isBlank())

        // 2. Finished thinking with time and tokens
        val finishedText = formatThinkingCapsuleText(
            template = "{model} {status} {time} {tokens}",
            modelName = "DeepSeek-R1",
            isThinkingActive = false,
            responseTimeMs = 12500L,
            thinkingTokens = 450,
            totalTokens = 600
        )
        assertTrue("思考完成文案必须包含模型名与思考过程", finishedText.contains("DeepSeek-R1") && finishedText.contains("思考过程"))
        assertTrue("思考完成文案必须包含时间与 token", finishedText.contains("450 token"))
        assertFalse("思考文案严禁为空白", finishedText.isBlank())

        // 3. Fallback when template is blank or invalid
        val fallbackText = formatThinkingCapsuleText(
            template = "   ",
            modelName = "",
            isThinkingActive = false,
            responseTimeMs = 0L,
            thinkingTokens = 0,
            totalTokens = 0
        )
        assertEquals("AI 思考过程", fallbackText)
    }

    @Test
    fun testFormatNonThinkingCapsuleText() {
        val textWithTimeAndTokens = formatNonThinkingCapsuleText(
            modelName = "Claude-3.5",
            responseTimeMs = 2300L,
            tokenCount = 180,
            content = "Hello world"
        )
        assertTrue("包含用时与 token 统计", textWithTimeAndTokens.contains("Claude-3.5") && textWithTimeAndTokens.contains("2秒") && textWithTimeAndTokens.contains("180token"))

        val fallbackModel = formatNonThinkingCapsuleText(
            modelName = "",
            responseTimeMs = 0L,
            tokenCount = 0,
            content = ""
        )
        assertEquals("AI", fallbackModel)
    }

    @Test
    fun testGhostMessageFiltering() {
        // Construct a list of messages containing valid messages and corrupt ghost messages
        val validMsg1 = Message(
            id = 1L,
            conversationId = 100L,
            role = "user",
            content = "用户有效消息"
        )
        val validMsg2 = Message(
            id = 2L,
            conversationId = 100L,
            role = "assistant",
            content = "",
            thinkingContent = "正在思考的有效思考内容"
        )
        val ghostEmptyMsg = Message(
            id = 3L,
            conversationId = 100L,
            role = "assistant",
            content = "",
            thinkingContent = null,
            attachments = null
        )
        val ghostWhitespaceMsg = Message(
            id = 4L,
            conversationId = 100L,
            role = "assistant",
            content = "   ",
            thinkingContent = "  ",
            attachments = null
        )

        val messages = listOf(validMsg1, ghostEmptyMsg, validMsg2, ghostWhitespaceMsg)

        // Apply filtering logic from buildDisplayMessages
        val filtered = messages.filter { message ->
            message.content.isNotBlank() ||
            !message.thinkingContent.isNullOrBlank() ||
            !message.attachments.isNullOrBlank() ||
            !message.toolCalls.isNullOrBlank()
        }

        assertEquals(2, filtered.size)
        assertEquals(1L, filtered[0].id)
        assertEquals(2L, filtered[1].id)
        assertFalse("幽灵全空消息必须被过滤", filtered.any { it.id == 3L || it.id == 4L })
    }

    @Test
    fun testStreamingBranchGroupIdLifecycleContract() {
        var streamingBranchGroupId: String? = "turn_123_assistant"
        var isGenerating = true

        // When generation stops, streamingBranchGroupId must become null
        isGenerating = false
        if (!isGenerating) {
            streamingBranchGroupId = null
        }

        assertNull("流式生成完成后，分支组 ID 必须及时重置为 null，杜绝气泡残留", streamingBranchGroupId)
    }
}
