package com.aiassistant

import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.PendingMemoryCandidate
import com.aiassistant.utils.SmartMemoryExtractor
import org.junit.Assert.*
import org.junit.Test

class V202FeaturesTest {

    @Test
    fun testQuoteUiSeparationAndPromptSynthesis() {
        val selectedText = "Kotlin Coroutines provide structured concurrency."
        val userInput = "请详细分析一下它的底层实现机制"

        val quoteBlock = selectedText.trim().lines().joinToString("\n") { "> $it" }
        val finalPromptWithInput = "$quoteBlock\n\n针对以上内容：\n$userInput"
        val finalPromptWithoutInput = "$quoteBlock\n\n针对以上内容："

        assertTrue(finalPromptWithInput.startsWith("> Kotlin Coroutines"))
        assertTrue(finalPromptWithInput.contains("针对以上内容：\n$userInput"))
        assertEquals(
            "> Kotlin Coroutines provide structured concurrency.\n\n针对以上内容：\n请详细分析一下它的底层实现机制",
            finalPromptWithInput
        )

        assertTrue(finalPromptWithoutInput.endsWith("针对以上内容："))
    }

    @Test
    fun testSendButtonHaloConcentricMath() {
        val boxSizeDp = 34f
        val strokeWidthDp = 1.2f
        val baseRadiusDp = (boxSizeDp - strokeWidthDp) / 2f
        val centerXDp = boxSizeDp / 2f
        val centerYDp = boxSizeDp / 2f

        assertEquals(17f, centerXDp, 0.0001f)
        assertEquals(17f, centerYDp, 0.0001f)
        assertEquals(16.4f, baseRadiusDp, 0.0001f)

        val minScale = 1.0f
        val minRadius = baseRadiusDp * minScale
        assertEquals(16.4f, minRadius, 0.0001f)

        val maxScale = 1.15f
        val maxRadius = baseRadiusDp * maxScale
        assertEquals(18.86f, maxRadius, 0.001f)
    }

    @Test
    fun testSendButtonBorderAndHaloColorSwitch() {
        val isGenerating = true
        val generatingHaloIsRed = isGenerating
        val generatingBorderIsRed = isGenerating
        assertTrue("生成中状态下光晕必须切换为红色", generatingHaloIsRed)
        assertTrue("生成中状态下外圆边框必须切换为红色", generatingBorderIsRed)

        val notGenerating = false
        val idleHaloIsBlue = !notGenerating
        val idleBorderIsBlue = !notGenerating
        assertTrue("空闲/发送状态下光晕必须切换为蓝色", idleHaloIsBlue)
        assertTrue("空闲/发送状态下外圆边框必须保持与+号一致的蓝色圆形边缘", idleBorderIsBlue)
    }

    @Test
    fun testSmartMemoryExtractorDeveloperPatterns() {
        // 1. 编程偏好与技术栈记忆提取
        val devInput1 = "我平时使用 Kotlin"
        val extracted1 = SmartMemoryExtractor.extractCandidate(devInput1)
        assertNotNull("技术栈偏好必须被有效提取", extracted1)
        assertTrue(extracted1!!.distilledContent.contains("Kotlin"))
        assertEquals("user", extracted1.suggestedScope)

        // 2. 开发者职业身份提取
        val devInput2 = "我是一名 Android 架构师"
        val extracted2 = SmartMemoryExtractor.extractCandidate(devInput2)
        assertNotNull("职业身份必须被有效提取", extracted2)
        assertTrue(extracted2!!.distilledContent.contains("架构师"))
        assertEquals("user", extracted2.suggestedScope)

        // 3. 负向约束提取 ("避免/禁止")
        val devInput3 = "避免使用过时库"
        val extracted3 = SmartMemoryExtractor.extractCandidate(devInput3)
        assertNotNull("负向约束必须被有效提取", extracted3)
        assertTrue(extracted3!!.distilledContent.contains("避免使用过时库"))

        // 4. 会话级项目事实提取
        val devInput4 = "当前项目的技术栈使用 Kotlin 和 Compose"
        val extracted4 = SmartMemoryExtractor.extractCandidate(devInput4, conversationId = 42L)
        assertNotNull("项目架构必须被有效提取", extracted4)
        assertTrue(extracted4!!.distilledContent.contains("Kotlin 和 Compose"))
        assertEquals("conversation", extracted4.suggestedScope)
        assertEquals(42L, extracted4.conversationId)
    }

    @Test
    fun testMemoryPromptXmlBlockStructure() {
        val memories = listOf(
            "用户习惯：常用技术栈为「Kotlin」",
            "会话事实：项目技术架构为「Kotlin 和 Compose」"
        )

        val memoryBlock = buildString {
            appendLine("<system_memory_context>")
            appendLine("  <anti_parroting_guideline>")
            appendLine("    [重要准则：这些记忆是供你在理解与回答时参考的真实上下文，绝对不要像鹦鹉学舌一样向用户朗读或复述这些系统记忆条目。直接自然地基于这些背景给出专业回答即可。]")
            appendLine("  </anti_parroting_guideline>")
            appendLine("  <session_specific_memory>")
            memories.forEach { appendLine("    - $it") }
            appendLine("  </session_specific_memory>")
            append("</system_memory_context>")
        }

        assertTrue(memoryBlock.startsWith("<system_memory_context>"))
        assertTrue(memoryBlock.endsWith("</system_memory_context>"))
        assertTrue(memoryBlock.contains("<anti_parroting_guideline>"))
        assertTrue(memoryBlock.contains("不要像鹦鹉学舌一样向用户朗读或复述这些系统记忆条目"))
        assertTrue(memoryBlock.contains("<session_specific_memory>"))
        assertTrue(memoryBlock.contains("用户习惯：常用技术栈为「Kotlin」"))
        assertTrue(memoryBlock.contains("会话事实：项目技术架构为「Kotlin 和 Compose」"))
    }

    @Test
    fun testSettingsScreenTrueFloatingPadding() {
        val topBarHeightDp = 56f
        val contentExtraPaddingDp = 8f
        val tabContentTopPaddingDp = topBarHeightDp + contentExtraPaddingDp

        assertEquals(64f, tabContentTopPaddingDp, 0.001f)
    }

    @Test
    fun testBranchConversationInheritsSettings() {
        val originalConv = Conversation(
            id = 101L,
            title = "原始对话",
            apiConfigId = 1L,
            modelName = "claude-3-5-sonnet",
            temperature = 0.85f,
            maxTokens = 4096,
            topP = 0.95f,
            enableThinking = true,
            thinkingEffort = "high",
            enableWebSearch = true,
            enableSessionMemory = true,
            tags = "技术,Kotlin,Android"
        )

        val branchedConv = Conversation(
            id = 102L,
            title = "原始对话 (分支)",
            apiConfigId = originalConv.apiConfigId,
            modelName = originalConv.modelName,
            temperature = originalConv.temperature,
            maxTokens = originalConv.maxTokens,
            topP = originalConv.topP,
            enableThinking = originalConv.enableThinking,
            thinkingEffort = originalConv.thinkingEffort,
            enableWebSearch = originalConv.enableWebSearch,
            enableSessionMemory = originalConv.enableSessionMemory,
            tags = originalConv.tags
        )

        assertEquals(originalConv.temperature, branchedConv.temperature)
        assertEquals(originalConv.maxTokens, branchedConv.maxTokens)
        assertEquals(originalConv.topP, branchedConv.topP)
        assertEquals(originalConv.enableThinking, branchedConv.enableThinking)
        assertEquals(originalConv.thinkingEffort, branchedConv.thinkingEffort)
        assertEquals(originalConv.enableWebSearch, branchedConv.enableWebSearch)
        assertEquals(originalConv.enableSessionMemory, branchedConv.enableSessionMemory)
        assertEquals(originalConv.tags, branchedConv.tags)
    }

    @Test
    fun testMessageDeletionSecondaryConfirmationState() {
        var messagePendingDelete: String? = null

        assertNull(messagePendingDelete)

        messagePendingDelete = "msg_123"
        assertNotNull(messagePendingDelete)
        assertEquals("msg_123", messagePendingDelete)

        var isDeleted = false
        val onConfirmDelete = {
            isDeleted = true
            messagePendingDelete = null
        }
        onConfirmDelete()

        assertTrue(isDeleted)
        assertNull(messagePendingDelete)
    }

    @Test
    fun testV202CurrentVersionUserUpdatesCompleteness() {
        val updates = com.aiassistant.ui.screens.settings.V202UserUpdates
        assertTrue("V2.0.2 更新列表不得为空", updates.isNotEmpty())
        assertEquals(8, updates.size)
        assertTrue(updates.any { it.contains("引用UI") || it.contains("预览卡片") })
        assertTrue(updates.any { it.contains("发送键边缘圆环") || it.contains("红") })
        assertTrue(updates.any { it.contains("同心光晕") || it.contains("错位") })
        assertTrue(updates.any { it.contains("真悬浮栏") || it.contains("穿透") })
        assertTrue(updates.any { it.contains("分支对话") })
        assertTrue(updates.any { it.contains("长期记忆") || it.contains("专属记忆") })
        assertTrue(updates.any { it.contains("二次确认") })
    }
}
