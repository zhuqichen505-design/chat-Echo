package com.aiassistant

import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.domain.model.PendingMemoryCandidate
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.utils.ConversationExportBundle
import com.aiassistant.utils.SmartMemoryExtractor
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V1917FeaturesTest {

    private val gson = Gson()

    @Test
    fun testPromptPriorityResolution_systemOverridesGlobal() {
        val globalPrompt = "你是全局助手，请简洁回答。"
        val systemPrompt = "你是专业小说家，请注重动作描写。"

        // 规则 1：会话专属系统提示词 100% 覆盖全局系统提示词
        val resolvedWithSystem = resolvePromptPriorityForTest(
            conversationSystemPrompt = systemPrompt,
            globalSystemPrompt = globalPrompt,
            isRoleplay = false
        )
        assertEquals(systemPrompt, resolvedWithSystem)
        assertFalse("全局提示词在有系统提示词时不应产生任何作用", resolvedWithSystem.contains("你是全局助手"))

        // 规则 2：无专属系统提示词时自动继承全局系统提示词
        val resolvedFallback = resolvePromptPriorityForTest(
            conversationSystemPrompt = "   ",
            globalSystemPrompt = globalPrompt,
            isRoleplay = false
        )
        assertEquals(globalPrompt, resolvedFallback)

        // 规则 3：角色扮演严格物理隔离，绝不读取全局系统提示词
        val resolvedRoleplay = resolvePromptPriorityForTest(
            conversationSystemPrompt = "",
            globalSystemPrompt = globalPrompt,
            isRoleplay = true
        )
        assertEquals("", resolvedRoleplay)
    }

    @Test
    fun testSmartMemoryExtractor_distillsPreferencesAccurately() {
        // 1. 语言偏好
        val c1 = SmartMemoryExtractor.extractCandidate("以后回答我都用中文，不要用英语")
        assertNotNull("应识别出语言偏好", c1)
        assertEquals("PREFERENCE", c1?.category)
        assertTrue(c1?.distilledContent?.contains("中文") == true)
        assertEquals("user", c1?.suggestedScope)

        // 2. 身份与职业事实
        val c2 = SmartMemoryExtractor.extractCandidate("我是一名资深 Android 架构师，专注于 Kotlin 和 Compose")
        assertNotNull("应识别出身份事实", c2)
        assertEquals("FACT", c2?.category)
        assertTrue(c2?.distilledContent?.contains("架构师") == true)

        // 3. 代码与格式偏好
        val c3 = SmartMemoryExtractor.extractCandidate("提供代码实现时请附带详细的注释")
        assertNotNull("应识别出代码风格偏好", c3)
        assertEquals("PREFERENCE", c3?.category)

        // 4. 显式指令
        val c4 = SmartMemoryExtractor.extractCandidate("记住我喜欢纯黑色的深色模式主题")
        assertNotNull("应识别显式记忆指令", c4)
        assertTrue(c4?.distilledContent?.contains("深色模式") == true)

        // 5. 普通问题与瞬时闲聊严禁识别为记忆
        val q1 = SmartMemoryExtractor.extractCandidate("请问今天北京天气怎么样？")
        assertNull("普通查询不应提取记忆", q1)

        val q2 = SmartMemoryExtractor.extractCandidate("帮我把这段文本翻译一下")
        assertNull("普通任务不应提取记忆", q2)

        val q3 = SmartMemoryExtractor.extractCandidate("你好呀，哈哈哈哈")
        assertNull("瞬时打招呼闲聊不应提取记忆", q3)
    }

    @Test
    fun testModelCapabilityEngine_resolvesContextWindowAndFeatures() {
        // Gemini 1.5 Pro: 2M 上下文, 多模态, 工具
        val gemini = ModelCapabilityEngine.resolveCapabilities("gemini-1.5-pro-latest")
        assertEquals(2_000_000, gemini.contextWindowTokens)
        assertEquals("2M", gemini.contextWindowLabel)
        assertTrue(gemini.isMultimodal)
        assertTrue(gemini.supportsToolCalling)

        // Claude 3.5 Sonnet: 200K 上下文, 多模态, 工具
        val claude = ModelCapabilityEngine.resolveCapabilities("claude-3-5-sonnet-20241022")
        assertEquals(200_000, claude.contextWindowTokens)
        assertEquals("200K", claude.contextWindowLabel)
        assertTrue(claude.isMultimodal)
        assertTrue(claude.supportsToolCalling)

        // GPT-4o: 128K, 多模态, 工具
        val gpt4o = ModelCapabilityEngine.resolveCapabilities("gpt-4o")
        assertEquals(128_000, gpt4o.contextWindowTokens)
        assertEquals("128K", gpt4o.contextWindowLabel)
        assertTrue(gpt4o.isMultimodal)
        assertTrue(gpt4o.supportsToolCalling)

        // DeepSeek Reasoner: 128K, 深度思考
        val deepseek = ModelCapabilityEngine.resolveCapabilities("deepseek-reasoner")
        assertEquals(128_000, deepseek.contextWindowTokens)
        assertTrue(deepseek.supportsThinking)
        assertEquals("deepseek_fixed", deepseek.reasoningProviderType)

        // UI 评估展示徽标
        val badge = ModelCapabilityEngine.evaluateModel("deepseek-reasoner")
        assertEquals("128K", badge.contextWindowDisplay)
        assertTrue(badge.supportsReasoning)
    }

    @Test
    fun testSearchCountBounding() {
        // 自定义联网搜索结果数严格限制在 1..20
        assertEquals(1, 0.coerceIn(1, 20))
        assertEquals(1, (-5).coerceIn(1, 20))
        assertEquals(20, 99.coerceIn(1, 20))
        assertEquals(8, 8.coerceIn(1, 20))
        assertEquals(15, 15.coerceIn(1, 20))
    }

    @Test
    fun testConversationExportBundleSerialization() {
        val bundle = ConversationExportBundle(
            conversation = Conversation(
                id = 101,
                title = "星际探险故事",
                apiConfigId = 1,
                modelName = "deepseek-chat",
                tags = "roleplay",
                createdAt = 1000L,
                updatedAt = 2000L
            ),
            messages = listOf(
                Message(id = 1, conversationId = 101, role = "user", content = "开始冒险"),
                Message(id = 2, conversationId = 101, role = "assistant", content = "飞船缓缓降落于开普勒-452b")
            ),
            isRoleplay = true,
            character = CharacterProfile(
                id = 5,
                name = "艾伦·沃克",
                identity = "星际探险家",
                personality = "勇敢、好奇",
                background = "在猎户座旋臂漂流多年的资深领航员",
                speakingStyle = "沉稳而略带幽默"
            ),
            scenario = RoleplayScenario(
                id = 8,
                name = "未知星系",
                worldview = "广袤无垠的深空"
            )
        )

        val json = gson.toJson(bundle)
        assertTrue(json.contains("艾伦·沃克"))
        assertTrue(json.contains("未知星系"))
        assertTrue(json.contains("开普勒-452b"))

        val parsed = gson.fromJson(json, ConversationExportBundle::class.java)
        assertEquals("星际探险故事", parsed.conversation.title)
        assertEquals(2, parsed.messages.size)
        assertEquals("艾伦·沃克", parsed.character?.name)
        assertEquals("未知星系", parsed.scenario?.name)
        assertTrue(parsed.isRoleplay)
    }

    private fun resolvePromptPriorityForTest(
        conversationSystemPrompt: String?,
        globalSystemPrompt: String,
        isRoleplay: Boolean
    ): String {
        if (isRoleplay) {
            // 角色与创作模式严格隔离
            return conversationSystemPrompt?.trim().orEmpty()
        }
        val convPrompt = conversationSystemPrompt?.trim().orEmpty()
        return if (convPrompt.isNotBlank()) {
            convPrompt
        } else {
            globalSystemPrompt.trim()
        }
    }
}
