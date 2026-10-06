package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.domain.model.WorldBook
import com.aiassistant.domain.model.WorldBookEntry
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.SmartMemoryExtractor
import org.junit.Assert.*
import org.junit.Test

class V212FeaturesTest {

    // 1. 世界书词条关键词匹配算法测试
    @Test
    fun testWorldBookEntryKeyListParsing() {
        val entry1 = WorldBookEntry(
            bookId = 1L,
            name = "亚特兰蒂斯",
            keys = "亚特兰蒂斯, 深海之都，亚特兰提斯\n失落之城",
            content = "深海文明遗迹"
        )
        val keys = entry1.getKeyList()
        assertEquals(4, keys.size)
        assertTrue(keys.contains("亚特兰蒂斯"))
        assertTrue(keys.contains("深海之都"))
        assertTrue(keys.contains("亚特兰提斯"))
        assertTrue(keys.contains("失落之城"))
    }

    @Test
    fun testWorldBookEntryMatchesText() {
        val entry = WorldBookEntry(
            bookId = 1L,
            name = "量子核心",
            keys = "量子核心, Quantum Core",
            content = "驱动整个空天母舰的动力来源",
            isConstant = false,
            isEnabled = true
        )

        // 包含中文关键词
        assertTrue(entry.matchesText("我们必须前往机舱重启量子核心才能恢复动力"))
        // 包含英文关键词（大小写不敏感）
        assertTrue(entry.matchesText("Initiate the quantum core diagnostic sequence!"))
        // 不包含关键词 -> 0 命中，避免无意义消耗 Token
        assertFalse(entry.matchesText("今天天气真不错，我们出去散步吧"))

        // 当条目被停用时，即使匹配关键词也不应生效
        val disabledEntry = entry.copy(isEnabled = false)
        assertFalse(disabledEntry.matchesText("量子核心超载了！"))

        // 常驻词条（isConstant = true）无需关键词即应始终匹配
        val constantEntry = entry.copy(isConstant = true)
        assertTrue(constantEntry.matchesText("任何没有任何关键词的日常对话"))
    }

    // 2. 世界书提示词格式化测试
    @Test
    fun testFormatWorldBookPrompt() {
        val entries = listOf(
            WorldBookEntry(
                bookId = 1L,
                name = "以太水晶",
                keys = "以太水晶",
                content = "高能结晶矿物，极为稀缺",
                priority = 99
            ),
            WorldBookEntry(
                bookId = 1L,
                name = "蒸汽之城",
                keys = "蒸汽之城",
                content = "齿轮与蒸汽轰鸣的工业重镇",
                priority = 10
            )
        )

        val formatted = AiRepository.formatWorldBookPrompt(entries)
        assertTrue("应包含世界书根标签", formatted.contains("【世界书设定】"))
        assertTrue("应包含以太水晶设定", formatted.contains("以太水晶"))
        assertTrue("应包含蒸汽之城设定", formatted.contains("蒸汽之城"))

        // 优先条目（以太水晶）排在前面
        val crystalIndex = formatted.indexOf("以太水晶")
        val cityIndex = formatted.indexOf("蒸汽之城")
        assertTrue("设定顺序应保留排序", crystalIndex < cityIndex)
    }

    // 3. 对话与角色扮演会话中的外置记忆与世界书开关模型持久化与默认值
    @Test
    fun testConversationAndRoleplaySessionSettingsModel() {
        // 普通对话
        val conversation = Conversation(
            title = "科技世界观讨论",
            apiConfigId = 1L,
            modelName = "claude-3-7-sonnet",
            enableExternalMemory = true,
            enableWorldBook = true,
            activeWorldBookIds = "1,2,3"
        )
        assertTrue(conversation.enableExternalMemory == true)
        assertTrue(conversation.enableWorldBook == true)
        assertEquals("1,2,3", conversation.activeWorldBookIds)

        // 故事角色扮演会话
        val roleplaySession = RoleplaySession(
            characterId = 101L,
            scenarioId = 202L,
            conversationId = 1L,
            enableExternalMemory = true,
            enableWorldBook = false
        )
        assertTrue(roleplaySession.enableExternalMemory)
        assertFalse(roleplaySession.enableWorldBook)
    }

    // 4. 辅助模型提炼提示词模板与降级机制测试
    @Test
    fun testAuxiliaryMemoryPromptTemplateAndFallback() {
        // 默认提示词模板存在且具有指导规范
        val defaultPrompt = PersonalizationManager.DEFAULT_AUXILIARY_MEMORY_PROMPT
        assertTrue(defaultPrompt.contains("IGNORE"))
        assertTrue(defaultPrompt.contains("提炼"))

        // 验证当辅助模型不可用或无法解析时，本地纯规则提取兜底能正常稳定工作
        val userInputFact = "注意：主角林渊拥有操控雷电的超凡异能"
        val candidate = SmartMemoryExtractor.extractCandidate(userInputFact)
        assertNotNull("本地规则必须成功兜底提炼事实", candidate)
        assertTrue(candidate!!.distilledContent.contains("主角林渊"))

        // 验证日常寒暄与单次任务在本地规则下亦不误存为记忆
        val transientGreeting = "你好啊，今天吃什么？"
        val greetingCandidate = SmartMemoryExtractor.extractCandidate(transientGreeting)
        assertNull("日常寒暄不应提取为长期记忆", greetingCandidate)
    }
}
