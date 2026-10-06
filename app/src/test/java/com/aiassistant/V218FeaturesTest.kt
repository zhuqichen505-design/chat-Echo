package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.NamedApiKey
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V218UserUpdates
import com.aiassistant.utils.SmartMemoryExtractor
import com.aiassistant.utils.TimelineMemoryHelper
import org.junit.Assert.*
import org.junit.Test

class V218FeaturesTest {

    @Test
    fun testV218UserUpdatesCompleteness() {
        val updates = V218UserUpdates
        assertFalse("本次更新日志列表不得为空", updates.isEmpty())
        assertEquals("必须包含全部 6 项主要更新项", 6, updates.size)

        assertTrue("必须包含记忆提取完整性与相关性重构", updates.any { it.contains("记忆提取完整性与相关性重构") })
        assertTrue("必须包含多 API Key 自动故障转移", updates.any { it.contains("多 API Key 自动故障转移") })
        assertTrue("必须包含模型输出工具栏二级菜单收纳", updates.any { it.contains("模型输出工具栏二级菜单收纳") })
        assertTrue("必须包含 API Key 自定义命名与备注区分", updates.any { it.contains("API Key 自定义命名与备注区分") })
        assertTrue("必须包含滚动摘要提醒逻辑深度修复", updates.any { it.contains("滚动摘要提醒逻辑深度修复") })
        assertTrue("必须包含核心巨型文件工程级模块化拆分", updates.any { it.contains("核心巨型文件工程级模块化拆分") })
    }

    @Test
    fun testNamedApiKeyDataClass() {
        val key = NamedApiKey(name = "备用Key-DeepSeek", key = "sk-deepseek-123456")
        assertEquals("备用Key-DeepSeek", key.name)
        assertEquals("sk-deepseek-123456", key.key)

        val updated = key.copy(name = "主Key-DeepSeek")
        assertEquals("主Key-DeepSeek", updated.name)
        assertEquals("sk-deepseek-123456", updated.key)
    }

    @Test
    fun testNamedApiKeyParsingAndFormatting() {
        // 测试 [名称] Key 格式
        val rawInput = """
            [主Key] sk-main-111111
            [备用一] sk-backup-222222
            sk-raw-333333
        """.trimIndent()

        val parsed = AiRepository.parseNamedApiKeys(rawInput)
        assertEquals(3, parsed.size)
        assertEquals("主Key", parsed[0].name)
        assertEquals("sk-main-111111", parsed[0].key)
        assertEquals("备用一", parsed[1].name)
        assertEquals("sk-backup-222222", parsed[1].key)
        assertEquals("", parsed[2].name)
        assertEquals("sk-raw-333333", parsed[2].key)

        // 测试格式化回存
        val formatted = AiRepository.formatNamedApiKeys(parsed)
        assertTrue(formatted.contains("[主Key] sk-main-111111"))
        assertTrue(formatted.contains("[备用一] sk-backup-222222"))
        assertTrue(formatted.contains("sk-raw-333333"))

        // 测试 名称:::Key 格式
        val colonInput = "生产线:::sk-prod-888888, 灰度:::sk-gray-999999"
        val parsedColon = AiRepository.parseNamedApiKeys(colonInput)
        assertEquals(2, parsedColon.size)
        assertEquals("生产线", parsedColon[0].name)
        assertEquals("sk-prod-888888", parsedColon[0].key)
        assertEquals("灰度", parsedColon[1].name)
        assertEquals("sk-gray-999999", parsedColon[1].key)
    }

    @Test
    fun testPureApiKeyExtractionStripsNames() {
        // 网络请求层只需纯 Key，不得夹带 [名称] 标签
        val raw = "[OpenAI-主力] sk-live-aaa111, [OpenAI-备用] sk-live-bbb222\n测试:::sk-live-ccc333"
        val pureKeys = AiRepository.parseApiKeys(raw)

        assertEquals(3, pureKeys.size)
        assertEquals("sk-live-aaa111", pureKeys[0])
        assertEquals("sk-live-bbb222", pureKeys[1])
        assertEquals("sk-live-ccc333", pureKeys[2])
    }

    @Test
    fun testStripThinkingTags() {
        // 闭合思考标签
        val inputWithThink = "<think>用户提到他喜欢喝无糖绿茶，我应该记住这个偏好。</think>用户的常驻饮料是无糖绿茶。"
        val stripped = TimelineMemoryHelper.stripThinkingTags(inputWithThink)
        assertEquals("用户的常驻饮料是无糖绿茶。", stripped)

        // 未闭合思考标签（模型输出中断或超长截断）
        val unclosedThink = "<think>正在推理用户的名字和关系，可能需要..."
        val strippedUnclosed = TimelineMemoryHelper.stripThinkingTags(unclosedThink)
        assertEquals("", strippedUnclosed)

        // 无思考标签
        val normalText = "在星巴克相遇，约定下周三见面。"
        val strippedNormal = TimelineMemoryHelper.stripThinkingTags(normalText)
        assertEquals(normalText, strippedNormal)
    }

    @Test
    fun testSmartMemoryExtractorUrlAndRelevanceFilter() {
        // 排除包含 markdown 链接语法 [text](url) 的伪造记忆
        val textWithUrl = "请参考文档 [官方手册](https://example.com/docs) 获取详细参数。"
        val candidate = SmartMemoryExtractor.extractCandidate(textWithUrl)
        assertNull("普通包含超链接的句子不得被误提取为用户事实记忆", candidate)

        // 验证相关性校验过滤掉虚假幻觉
        val original = "林夏是帝国理工学院毕业的物理学博士，性格沉稳冷静。"
        val relevantFact = "林夏毕业于帝国理工学院，拥有物理学博士学位"
        assertTrue(SmartMemoryExtractor.isRelevantToOriginalContent(relevantFact, original))

        val hallucinatedFact = "太空飞船拥有曲率引擎驱动能力和反物质武器系统"
        assertFalse(SmartMemoryExtractor.isRelevantToOriginalContent(hallucinatedFact, original))
    }
}
