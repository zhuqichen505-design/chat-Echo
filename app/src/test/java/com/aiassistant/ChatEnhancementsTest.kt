package com.aiassistant

import com.aiassistant.domain.model.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ChatEnhancementsTest {
    @Test fun directionChoicesHaveDistinctOneRoundSemantics() {
        assertEquals(com.aiassistant.ui.screens.chat.GenerationUiState.ChoosingDirection,
            com.aiassistant.ui.screens.chat.GenerationUiStateRules.derive(true, "", false, "等待选择回复方向"))
        assertEquals(com.aiassistant.ui.screens.chat.GenerationUiState.ChoosingDirection,
            com.aiassistant.ui.screens.chat.GenerationUiStateRules.derive(true, "", false, "回复方向生成失败，等待处理"))
        val directions = listOf(com.aiassistant.domain.model.ReplyDirection("A", "先聊感受"), com.aiassistant.domain.model.ReplyDirection("B", "继续剧情"))
        fun instruction(action: com.aiassistant.domain.model.ReplyDirectionAction, index: Int = -1, custom: String = "") =
            com.aiassistant.domain.model.ReplyDirections.instruction(com.aiassistant.domain.model.ReplyDirectionDecision(action, index, custom), directions)!!
        val selected = instruction(com.aiassistant.domain.model.ReplyDirectionAction.SELECT, 1)
        assertTrue(selected.contains("继续剧情")); assertFalse(selected.contains("先聊感受"))
        val auto = instruction(com.aiassistant.domain.model.ReplyDirectionAction.AUTO)
        assertTrue(auto.contains("选择一个")); assertTrue(auto.contains("先聊感受")); assertTrue(auto.contains("继续剧情"))
        val other = instruction(com.aiassistant.domain.model.ReplyDirectionAction.OTHER)
        assertTrue(other.contains("不同于")); assertTrue(other.contains("不得虚构既往事实"))
        val custom = instruction(com.aiassistant.domain.model.ReplyDirectionAction.OTHER, custom = "转为第一人称")
        assertTrue(custom.contains("转为第一人称")); assertFalse(custom.contains("继续剧情"))
        assertTrue(custom.contains("仅用于本轮"))
        assertFalse(com.aiassistant.domain.model.Conversation(title = "default", apiConfigId = 1, modelName = "id").enableReplyDirections)
        assertEquals(2, com.aiassistant.ui.screens.chat.TempChatSettings().replyDirectionCount)
    }

    @Test fun directionParserRejectsMalformedWrongCountAndDuplicateOutput() {
        val valid = """{"directions":[{"title":"A","description":"感受"},{"title":"B","description":"剧情"}]}"""
        assertEquals(2, com.aiassistant.domain.model.ReplyDirections.parse("```json\n$valid\n```", 2).size)
        for (input in listOf("plain text", "{}", valid.replace("\"B\"", "\"A\""), valid.replace("剧情", "感受"), valid.replace("感受", ""))) {
            try { com.aiassistant.domain.model.ReplyDirections.parse(input, 2); org.junit.Assert.fail("invalid direction accepted: $input") }
            catch (_: Exception) { }
        }
        try { com.aiassistant.domain.model.ReplyDirections.parse(valid, 3); org.junit.Assert.fail("wrong count accepted") }
        catch (_: IllegalArgumentException) { }
        assertTrue(com.aiassistant.domain.model.ReplyDirections.planningInstruction(4).contains("4 个"))
    }
    @Test fun capsuleExpansionKeepsFirstLineOriginAtEveryFontScale() {
        for ((header, line) in listOf(20 to 16, 20 to 20, 27 to 27, 41 to 41)) {
            val closed = com.aiassistant.ui.screens.chat.capsuleTextViewport(header, line, line * 4, false)
            val opened = com.aiassistant.ui.screens.chat.capsuleTextViewport(header, line, line * 4, true)
            assertEquals(header, closed.height)
            assertEquals(closed.textTop, opened.textTop)
            assertEquals((header - line) / 2, opened.textTop)
            assertEquals(line * 4 + opened.textTop, opened.height)
            val single = com.aiassistant.ui.screens.chat.capsuleTextViewport(header, line, line, true)
            assertEquals(closed, single)
        }
    }
    @Test
    fun bottomFollowOnlyConsumesPositiveMeasuredOverflow() {
        assertEquals(0, com.aiassistant.ui.screens.chat.bottomFollowDistance(100, 80, 500))
        assertEquals(0, com.aiassistant.ui.screens.chat.bottomFollowDistance(100, 400, 500))
        assertEquals(20, com.aiassistant.ui.screens.chat.bottomFollowDistance(100, 420, 500))
        assertEquals(300, com.aiassistant.ui.screens.chat.bottomFollowDistance(-200, 1000, 500))
    }

    @Test
    fun retriesRemainReconnectingEvenWhenCauseContainsErrorWords() {
        assertEquals(com.aiassistant.ui.screens.chat.GenerationUiState.Reconnecting,
            com.aiassistant.ui.screens.chat.GenerationUiStateRules.derive(true, "", false, "500 服务端错误 (HTTP 500)，正在尝试重新连接 (1/3)..."))
        assertEquals(com.aiassistant.ui.screens.chat.GenerationUiState.Failed,
            com.aiassistant.ui.screens.chat.GenerationUiStateRules.derive(true, "", false, "Key 请求报错: HTTP 500"))
    }

    @Test
    fun testDefaultMaxTokensIs4096() {
        val config = ApiConfig(
            id = 1L,
            name = "Test API",
            provider = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-test",
            modelName = "gpt-4o"
        )
        assertEquals("默认 ApiConfig 的 maxTokens 必须为 4096", 4096, config.maxTokens)
    }

    @Test
    fun testErrorMessageDetection() {
        fun isErrorMessage(content: String): Boolean {
            val trimmed = content.trim()
            return trimmed.startsWith("请求失败") ||
                   trimmed.startsWith("[请求失败]") ||
                   trimmed.startsWith("Error:") ||
                   trimmed.startsWith("error:") ||
                   trimmed.contains("[输出已被中断:")
        }

        assertTrue(isErrorMessage("请求失败\n\n网络超时，请检查网络设置"))
        assertTrue(isErrorMessage("[请求失败] 401 Unauthorized"))
        assertTrue(isErrorMessage("Error: connection refused"))
        assertTrue(isErrorMessage("error: 500 internal server error"))
        assertTrue(isErrorMessage("这是部分内容...\n\n[输出已被中断: 用户取消]"))
        
        assertFalse(isErrorMessage("你好！我是你的 AI 助手，有什么我可以帮你的？"))
        assertFalse(isErrorMessage("```kotlin\nval error = 1\n```"))
    }

    @Test
    fun testTokenSpeedCalculation() {
        val tokenCount = 150
        val responseTimeMs = 3000L // 3.0 秒
        val seconds = responseTimeMs / 1000.0
        val speed = tokenCount / seconds
        val formatted = String.format(Locale.US, "%.1f tokens/s", speed)

        assertEquals("50.0 tokens/s", formatted)
    }

    @Test
    fun testThinkingCapsuleTextFormatting() {
        fun formatCapsule(isThinkingActive: Boolean, modelName: String, timeMs: Long, thinkingTokens: Int, totalTokens: Int): String {
            return when {
                isThinkingActive -> "模型正在思考中"
                thinkingTokens > 0 -> "$modelName 已深度思考 (${timeMs / 1000.0}s, $thinkingTokens tokens)"
                else -> modelName
            }
        }

        assertEquals("模型正在思考中", formatCapsule(true, "DeepSeek", 0L, 0, 0))
        assertEquals("DeepSeek 已深度思考 (2.5s, 350 tokens)", formatCapsule(false, "DeepSeek", 2500L, 350, 500))
        assertEquals("GPT-4o", formatCapsule(false, "GPT-4o", 1200L, 0, 150))
    }

    @Test
    fun testHtmlEntityDecoding() {
        fun decodeEntities(input: String): String {
            return input
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
        }

        val htmlText = "&lt;div class=&quot;box&quot;&gt;Hello &amp; Welcome&lt;/div&gt;"
        assertEquals("<div class=\"box\">Hello & Welcome</div>", decodeEntities(htmlText))
    }

    @Test
    fun testMarkdownHeadingLevels() {
        fun parseHeadingLevel(line: String): Int {
            return when {
                line.startsWith("###### ") -> 6
                line.startsWith("##### ") -> 5
                line.startsWith("#### ") -> 4
                line.startsWith("### ") -> 3
                line.startsWith("## ") -> 2
                line.startsWith("# ") -> 1
                else -> 0
            }
        }

        assertEquals(1, parseHeadingLevel("# 一级标题"))
        assertEquals(5, parseHeadingLevel("##### 五级标题"))
        assertEquals(6, parseHeadingLevel("###### 六级标题"))
        assertEquals(0, parseHeadingLevel("普通文本"))
    }
}
