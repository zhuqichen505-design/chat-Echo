package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.domain.model.NamedApiKey
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.SmartMemoryExtractor
import org.junit.Assert.*
import org.junit.Test

class SevenUserRequestsTest {

    @Test
    fun testAuxiliaryMemoryPromptRequirement() {
        val prompt = PersonalizationManager.DEFAULT_AUXILIARY_MEMORY_PROMPT
        assertTrue("提示词应要求完整主谓宾结构而非截断语句", prompt.contains("主谓宾结构完整"))
        assertTrue("提示词应支持30~80字完整表意", prompt.contains("30~80字"))
        assertTrue("提示词应明确禁止废话", prompt.contains("严禁输出任何'根据分析'等说明或标点废话"))
    }

    @Test
    fun testRefineMemoryContentCleansFluffAndCategorizesCorrectly() {
        // 1. 测试分析型废话与序号前缀剥离
        val (fact1, cat1) = SmartMemoryExtractor.refineMemoryContent("根据上述对话提炼出如下核心事实：用户平时喜欢喝无糖绿茶")
        assertEquals("用户偏好：用户平时喜欢喝无糖绿茶", fact1)
        assertEquals("PREFERENCE", cat1)

        // 2. 测试序号与列表符号剥离
        val (fact2, cat2) = SmartMemoryExtractor.refineMemoryContent("- 1. 记忆事实：主力编程语言为Kotlin")
        assertEquals("重要事实：主力编程语言为Kotlin", fact2)
        assertEquals("FACT", cat2)

        // 3. 测试负向约束与称谓禁令（需求4c）
        val (fact3, cat3) = SmartMemoryExtractor.refineMemoryContent("经分析如下：不允许使用老板和特助的称呼")
        assertEquals("行为约束：不允许使用老板和特助的称呼", fact3)
        assertEquals("PREFERENCE", cat3)

        // 4. 测试“建议记住”类语气词剥离
        val (fact4, cat4) = SmartMemoryExtractor.refineMemoryContent("建议记住：严禁省略代码中间逻辑")
        assertEquals("行为约束：严禁省略代码中间逻辑", fact4)
        assertEquals("PREFERENCE", cat4)
    }

    @Test
    fun testModelCapabilityEngineReasoningProviderDetection() {
        // 1. 代理/第三方中转 URL 上的现代 GPT-6 识别
        val gpt6Proxy = ModelCapabilityEngine.evaluateModel(
            modelName = "gpt-6-luna",
            provider = "CustomProxy",
            baseUrl = "https://proxy.example.invalid/v1"
        )
        assertTrue("gpt-6-luna 必须支持思考", gpt6Proxy.supportsThinking)
        assertEquals("openai", gpt6Proxy.reasoningProviderType)

        // 2. Claude 3.7 模型识别
        val claude37 = ModelCapabilityEngine.evaluateModel(
            modelName = "claude-3-7-sonnet-20250219",
            provider = "Anthropic",
            baseUrl = "https://api.anthropic.com"
        )
        assertTrue("Claude 3.7 必须支持思考", claude37.supportsThinking)
        assertEquals("anthropic", claude37.reasoningProviderType)

        // 3. DeepSeek R1 模型识别
        val deepseekR1 = ModelCapabilityEngine.evaluateModel(
            modelName = "deepseek-reasoner",
            provider = "DeepSeek",
            baseUrl = "https://api.deepseek.com"
        )
        assertTrue("DeepSeek R1 必须支持思考", deepseekR1.supportsThinking)
        assertEquals("deepseek_fixed", deepseekR1.reasoningProviderType)
    }

    @Test
    fun testContextRetentionThresholds() {
        assertTrue(
            "MIN_RECENT_CONTEXT_TOKENS 至少为 16000 以免 2-3 轮对话后遗忘",
            AiRepository.MIN_RECENT_CONTEXT_TOKENS >= 16_000
        )
        assertTrue(
            "MIN_SUMMARY_SOURCE_MESSAGES 至少为 16 轮消息以上才允许归约生成摘要",
            AiRepository.MIN_SUMMARY_SOURCE_MESSAGES >= 16
        )
        assertTrue(
            "MIN_SUMMARY_SOURCE_TOKENS 至少为 8000 tokens",
            AiRepository.MIN_SUMMARY_SOURCE_TOKENS >= 8_000
        )
    }

    @Test
    fun testNamedApiKeySerialization() {
        val keys = listOf(
            NamedApiKey(name = "生产主号", key = "sk-main-123"),
            NamedApiKey(name = "备用团队", key = "sk-backup-456")
        )
        val serialized = AiRepository.formatNamedApiKeys(keys)
        val parsed = AiRepository.parseNamedApiKeys(serialized)

        assertEquals(2, parsed.size)
        assertEquals("生产主号", parsed[0].name)
        assertEquals("sk-main-123", parsed[0].key)
        assertEquals("备用团队", parsed[1].name)
        assertEquals("sk-backup-456", parsed[1].key)
    }

    @Test
    fun testV220UserUpdatesCompleteness() {
        val updates = com.aiassistant.ui.screens.settings.V220UserUpdates
        assertEquals("V220 必须包含 7 项核心改动说明", 7, updates.size)
        assertTrue("必须包含 API 设置拖拽阴影圆角统一", updates.any { it.contains("API 设置拖拽阴影圆角统一") })
        assertTrue("必须包含 API Key 命名同行紧凑排版", updates.any { it.contains("API Key 命名同行紧凑排版") })
        assertTrue("必须包含 思考与连接状态气泡完整查看", updates.any { it.contains("思考与连接状态气泡完整查看") })
        assertTrue("必须包含 记忆提取完整性与废话剥离", updates.any { it.contains("记忆提取完整性与废话剥离") })
        assertTrue("必须包含 绝对行为约束与负向禁令生效", updates.any { it.contains("绝对行为约束与负向禁令生效") })
        assertTrue("必须包含 多轮上下文记忆保留大幅提升", updates.any { it.contains("多轮上下文记忆保留大幅提升") })
        assertTrue("必须包含 提示词输入框视口防抖与全平台思考参数透传", updates.any { it.contains("提示词输入框视口防抖与全平台思考参数透传") })
    }
}
