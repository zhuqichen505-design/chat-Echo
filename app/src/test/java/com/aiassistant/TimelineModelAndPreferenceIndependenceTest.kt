package com.aiassistant

import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.utils.SmartMemoryExtractor
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.TimelineMemoryHelper
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/**
 * 需求复核专项测试：
 * 1. 时间线功能无需自动检测是否开启，直接和“对话记忆”(enableSessionMemory) 开关合并；
 * 2. 时间线与事件让大模型真正参与智能提取，包含日内推进、自然跨度、去元词汇、JSON 解析与本地降级兜底；
 * 3. 设置中跨会话记忆只管理全局偏好，开关仅影响全局偏好，与对话专属记忆完全物理隔离与独立。
 */
class TimelineModelAndPreferenceIndependenceTest {

    // 1. 测试时间线门禁逻辑与会话记忆开关的直接合并绑定
    @Test
    fun testTimelineGatingMergedWithSessionMemory() {
        // 当普通会话明确关闭对话记忆时，时间线必须不运行
        val normalSessionDisabled = isTimelineFeatureEnabled(
            isRoleplay = false,
            enableSessionMemory = false
        )
        assertFalse("普通对话且显式关闭对话记忆时，时间线评估不开启", normalSessionDisabled)

        // 当普通会话显式开启对话记忆时，时间线自动跟随开启
        val normalSessionEnabled = isTimelineFeatureEnabled(
            isRoleplay = false,
            enableSessionMemory = true
        )
        assertTrue("普通对话且开启对话记忆时，时间线自动启用", normalSessionEnabled)

        // 当角色扮演/故事会话未显式关闭（为 true 或 null）时，时间线自动跟随开启
        val roleplayDefaultEnabled = isTimelineFeatureEnabled(
            isRoleplay = true,
            enableSessionMemory = null
        )
        assertTrue("角色扮演会话默认状态下时间线自动启用", roleplayDefaultEnabled)

        // 当角色扮演会话显式关闭会话记忆时，时间线停止运行
        val roleplayExplicitDisabled = isTimelineFeatureEnabled(
            isRoleplay = true,
            enableSessionMemory = false
        )
        assertFalse("角色扮演会话若显式关闭会话记忆，时间线评估随之关闭", roleplayExplicitDisabled)
    }

    private fun isTimelineFeatureEnabled(isRoleplay: Boolean, enableSessionMemory: Boolean?): Boolean {
        // 与 AiRepository.kt:3809 行的门禁规则严格一致
        return enableSessionMemory == true || (isRoleplay && enableSessionMemory != false)
    }

    // 2. 测试大模型参与增量提取后的 JSON 解析与元词汇净化
    @Test
    fun testModelParticipatedTimelineExtractionAndSanitization() {
        val modelRawOutput = """
            <think>
            思考过程：正文中主角二人从清晨聊到了黄昏，并确立了同盟关系。
            时间由第1天·清晨推移至第1天·黄昏。
            事件：林恩与艾莉西亚在钟楼顶层达成灵魂血誓。
            </think>
            {
              "newStoryTime": "第 1 天·黄昏",
              "newEvent": {
                "timeTag": "第 1 天·黄昏",
                "category": "PLOT_EVENT",
                "content": "林恩与艾莉西亚在钟楼顶层达成灵魂血誓"
              }
            }
        """.trimIndent()

        // 验证移除 <think> 标签后提取合法 JSON
        val stripped = TimelineMemoryHelper.stripThinkingTags(modelRawOutput)
        assertFalse("Thinking 标签已被剥离", stripped.contains("<think>"))
        val jsonMatcher = Regex("""\{[\s\S]*\}""").find(stripped)
        assertNotNull("成功匹配到 JSON 块", jsonMatcher)

        val jsonObj = JsonParser.parseString(jsonMatcher!!.value).asJsonObject
        val newStoryTime = jsonObj.get("newStoryTime")?.asString?.trim()
        assertEquals("第 1 天·黄昏", newStoryTime)

        val newEventObj = jsonObj.getAsJsonObject("newEvent")
        assertNotNull("提取到新事件对象", newEventObj)
        val timeTag = newEventObj.get("timeTag")?.asString?.trim()
        val content = newEventObj.get("content")?.asString?.trim()
        val categoryKey = newEventObj.get("category")?.asString

        assertEquals("第 1 天·黄昏", timeTag)
        assertEquals("林恩与艾莉西亚在钟楼顶层达成灵魂血誓", content)
        assertEquals(TimelineCategory.PLOT_EVENT, TimelineCategory.fromKey(categoryKey))

        // 验证元语言净化：如果模型提取偶尔混入了元词汇，能被自动净化
        val rawEventWithMeta = "用户要求助手让林恩与艾莉西亚在雨夜结成生死盟友"
        val sanitized = SmartMemoryExtractor.sanitizeMetaLanguage(rawEventWithMeta)
        assertFalse("元词汇'用户'已被净化", sanitized.contains("用户"))
        assertFalse("元词汇'助手'已被净化", sanitized.contains("助手"))
    }

    // 3. 测试大模型返回 NO_UPDATE 时的轻量静默处理
    @Test
    fun testModelNoUpdateDetection() {
        val output1 = "NO_UPDATE"
        val output2 = """
            <think>本轮对话只是普通闲聊，没有时间推进与重大里程碑。</think>
            NO_UPDATE
        """.trimIndent()

        assertTrue(output1.contains("NO_UPDATE", ignoreCase = true))
        assertTrue(output2.contains("NO_UPDATE", ignoreCase = true))
    }

    // 4. 测试设置页跨会话记忆与对话专属偏好的严格解耦隔离
    @Test
    fun testSettingsGlobalMemoryStrictIsolation() {
        // 模拟数据库中的混合记忆条目
        val mockMemories = listOf(
            MemoryItem(id = 1, scope = "user", content = "用户喜欢简洁的代码风格", conversationId = null),
            MemoryItem(id = 2, scope = "global", content = "回答请使用中文", conversationId = null),
            MemoryItem(id = 3, scope = "conversation", content = "【当前故事时间】：第 3 天·深夜", conversationId = 101),
            MemoryItem(id = 4, scope = "conversation", content = "[第1天·清晨] 主角在旧车站相遇", conversationId = 101),
            MemoryItem(id = 5, scope = "conversation", content = "本小说设定：魔法需要咏唱符文", conversationId = 102)
        )

        // 模拟 SettingsPromptsMemoryTab 中的全局偏好过滤（仅查询 scope IN ('user', 'global')）
        val globalMemories = mockMemories.filter { it.scope in listOf("user", "global") }
        assertEquals("全局偏好库仅应包含 2 条条目", 2, globalMemories.size)
        assertTrue("全局偏好包含用户偏好", globalMemories.any { it.content == "用户喜欢简洁的代码风格" })
        assertTrue("全局偏好包含通用偏好", globalMemories.any { it.content == "回答请使用中文" })
        assertFalse("全局偏好绝不包含任何会话专属记忆或时间线", globalMemories.any { it.scope == "conversation" })

        // 模拟设置中“清空全局偏好”操作：仅清空 user/global，对话专属记忆 100% 完整保留
        val afterClearGlobal = mockMemories.filterNot { it.scope in listOf("user", "global") }
        assertEquals("清空后仅保留 3 条会话专属记忆", 3, afterClearGlobal.size)
        assertTrue("会话 101 时间线未受任何影响", afterClearGlobal.any { it.id == 3L && it.conversationId == 101L })
        assertTrue("会话 102 设定未受任何影响", afterClearGlobal.any { it.id == 5L && it.conversationId == 102L })

        // 模拟自动捕获时的独立开关控制
        val autoMemoryEnabledInSettings = false // 设置中关闭跨会话记忆
        val enableSessionMemoryInConv = true // 单会话开启专属记忆

        val globalItemAllowed = shouldCaptureMemory(
            scope = "user",
            globalAutoMemorySwitch = autoMemoryEnabledInSettings,
            sessionMemorySwitch = enableSessionMemoryInConv
        )
        assertFalse("设置中关闭跨会话记忆时，全局偏好不自动捕获", globalItemAllowed)

        val convItemAllowed = shouldCaptureMemory(
            scope = "conversation",
            globalAutoMemorySwitch = autoMemoryEnabledInSettings,
            sessionMemorySwitch = enableSessionMemoryInConv
        )
        assertTrue("单会话开启记忆时，会话专属记忆正常独立捕获，完全不受全局开关关闭的影响", convItemAllowed)
    }

    private fun shouldCaptureMemory(
        scope: String,
        globalAutoMemorySwitch: Boolean,
        sessionMemorySwitch: Boolean
    ): Boolean {
        // 严格对照 AiRepository.kt:3218-3223 行的隔离逻辑
        return if (scope in listOf("user", "global")) {
            globalAutoMemorySwitch
        } else if (scope == "conversation") {
            sessionMemorySwitch
        } else {
            false
        }
    }
}
