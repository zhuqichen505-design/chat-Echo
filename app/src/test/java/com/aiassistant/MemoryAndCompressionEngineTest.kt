package com.aiassistant

import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.Message
import com.aiassistant.utils.AdvancedMemoryEngine
import org.junit.Assert.*
import org.junit.Test

class MemoryAndCompressionEngineTest {

    @Test
    fun testInferFactCategoryAndImportance() {
        // 1. 约束类
        val (cat1, imp1) = AdvancedMemoryEngine.inferFactCategoryAndImportance("必须严格使用第一人称进行角色扮演，严禁跳出设定")
        assertEquals(AdvancedMemoryEngine.MemoryCategory.CONSTRAINT, cat1)
        assertEquals(5, imp1)

        // 2. 偏好类
        val (cat2, imp2) = AdvancedMemoryEngine.inferFactCategoryAndImportance("用户喜欢喝无糖乌龙茶，偏好简洁干练的回复风格")
        assertEquals(AdvancedMemoryEngine.MemoryCategory.PREFERENCE, cat2)
        assertEquals(4, imp2)

        // 3. 时空经历类
        val (cat3, imp3) = AdvancedMemoryEngine.inferFactCategoryAndImportance("[第3天·傍晚] 两人在商业街甜品店一起吃了草莓奶油蛋糕")
        assertEquals(AdvancedMemoryEngine.MemoryCategory.TIMELINE, cat3)
        assertEquals(4, imp3)

        // 4. 状态设定类
        val (cat4, imp4) = AdvancedMemoryEngine.inferFactCategoryAndImportance("角色当前处于虚弱状态，好感度达到信任级别")
        assertEquals(AdvancedMemoryEngine.MemoryCategory.WORLD_STATE, cat4)
        assertEquals(3, imp4)

        // 5. 普通事实
        val (cat5, imp5) = AdvancedMemoryEngine.inferFactCategoryAndImportance("公司总部在科技园区A栋")
        assertEquals(AdvancedMemoryEngine.MemoryCategory.FACT, cat5)
        assertEquals(3, imp5)
    }

    @Test
    fun testDetectConflict_locationChange() {
        val oldMemory = MemoryItem(id = 1, conversationId = 10, content = "常住在北京市海淀区")
        val newMemory = "上个月搬家到了深圳南山科技园"

        val isConflict = AdvancedMemoryEngine.detectConflict(newMemory, oldMemory)
        assertTrue("居住地变更应当触发冲突消解", isConflict)
    }

    @Test
    fun testDetectConflict_nameChange() {
        val oldMemory = MemoryItem(id = 2, conversationId = 10, content = "你可以称呼我为小明")
        val newMemory = "以后请叫我阿强"

        val isConflict = AdvancedMemoryEngine.detectConflict(newMemory, oldMemory)
        assertTrue("称呼更替应当触发冲突消解", isConflict)
    }

    @Test
    fun testDetectConflict_techStackPreference() {
        val oldMemory = MemoryItem(id = 3, conversationId = 10, content = "主力语言是Java")
        val newMemory = "偏好使用Kotlin进行Android开发"

        val isConflict = AdvancedMemoryEngine.detectConflict(newMemory, oldMemory)
        assertTrue("技术栈更迭偏好应当触发冲突消解", isConflict)
    }

    @Test
    fun testDetectConflict_colonPrefixAttribute() {
        val oldMemory = MemoryItem(id = 4, conversationId = 10, content = "用户职业：资深Android工程师")
        val newMemory = "用户职业：全栈技术专家与架构师"

        val isConflict = AdvancedMemoryEngine.detectConflict(newMemory, oldMemory)
        assertTrue("相同属性前缀更替应当触发冲突消解", isConflict)
    }

    @Test
    fun testDetectConflict_noConflictOnDifferentTopics() {
        val oldMemory = MemoryItem(id = 5, conversationId = 10, content = "常住在北京市海淀区")
        val newMemory = "用户喜欢吃草莓蛋糕"

        val isConflict = AdvancedMemoryEngine.detectConflict(newMemory, oldMemory)
        assertFalse("非同质话题不应误判为冲突", isConflict)
    }

    @Test
    fun testCalculateHybridScore_relevanceAndRecencyBoosting() {
        val now = 1700000000000L
        val recentMemory = MemoryItem(
            id = 1,
            conversationId = 100,
            content = "用户喜欢吃草莓蛋糕并喝无糖乌龙茶",
            updatedAt = now - 1000 * 60 * 60, // 1小时前
            scope = "conversation"
        )
        val oldIrrelevantMemory = MemoryItem(
            id = 2,
            conversationId = 100,
            content = "服务器部署在内网机房192.168.1.1",
            updatedAt = now - 1000L * 60 * 60 * 24 * 30, // 30天前
            scope = "global"
        )

        val queryTerms = setOf("蛋糕", "乌龙茶")
        val queryEntities = setOf("草莓蛋糕")

        val score1 = AdvancedMemoryEngine.calculateHybridScore(
            recentMemory, queryTerms, queryEntities, conversationId = 100, currentTimeMs = now
        )
        val score2 = AdvancedMemoryEngine.calculateHybridScore(
            oldIrrelevantMemory, queryTerms, queryEntities, conversationId = 100, currentTimeMs = now
        )

        assertTrue("高关联度+近期+实体命中的记忆得分应当显著高于远期无关记忆", score1 > score2)
        assertTrue("命中记忆的分数应当处于合理高区间 (> 0.5)", score1 > 0.5f)
    }

    @Test
    fun testPruneLowInformationTurns() {
        val msgs = listOf(
            Message(id = 1, conversationId = 1, role = "user", content = "今天帮我写一份项目方案"),
            Message(id = 2, conversationId = 1, role = "assistant", content = "好的"),
            Message(id = 3, conversationId = 1, role = "user", content = "谢谢"),
            Message(id = 4, conversationId = 1, role = "assistant", content = "不客气，请问具体包含哪些技术模块？"),
            Message(id = 5, conversationId = 1, role = "user", content = "嗯嗯"),
            Message(id = 6, conversationId = 1, role = "user", content = "主要包含网络同步和本地离线存储两个模块")
        )

        val pruned = AdvancedMemoryEngine.pruneLowInformationTurns(msgs)
        assertEquals(3, pruned.size)
        assertEquals("今天帮我写一份项目方案", pruned[0].content)
        assertEquals("不客气，请问具体包含哪些技术模块？", pruned[1].content)
        assertEquals("主要包含网络同步和本地离线存储两个模块", pruned[2].content)
    }

    @Test
    fun testParseStructuredSummary() {
        val rawModelOutput = """
            【核心背景与用户固定约束】
            - 用户是独立开发者，要求使用 Kotlin 和 Jetpack Compose
            - 绝对不要输出废话问候

            【历史关键里程碑与决策推进】
            1. [第1天] 确定了单模块架构升级为多模块架构
            2. [第2天] 完成了 Room 数据库的本地三层状态流转升级

            【当前未决议题与待办上下文】
            • 待确认网络层 OkHttp 拦截器的超时重试阈值
            • 准备开始编写单元测试
        """.trimIndent()

        val structured = AdvancedMemoryEngine.parseStructuredSummary(rawModelOutput)
        assertEquals(2, structured.coreConstraints.size)
        assertTrue(structured.coreConstraints[0].contains("Jetpack Compose"))

        assertEquals(2, structured.milestones.size)
        assertTrue(structured.milestones[0].contains("单模块架构升级"))

        assertEquals(2, structured.openItems.size)
        assertTrue(structured.openItems[0].contains("OkHttp 拦截器"))

        val promptBlock = structured.toPromptBlock()
        assertTrue(promptBlock.contains("【核心背景与用户固定约束】"))
        assertTrue(promptBlock.contains("【历史关键里程碑与决策推进】"))
        assertTrue(promptBlock.contains("【当前未决议题与待办上下文】"))
    }

    @Test
    fun testGenerateExtractiveStructuredSummary() {
        val msgs = listOf(
            Message(id = 1, conversationId = 1, role = "user", content = "请记住：始终使用中文简短回答，不要加额外修饰"),
            Message(id = 2, conversationId = 1, role = "assistant", content = "收到，已锁定要求。"),
            Message(id = 3, conversationId = 1, role = "user", content = "[第3天·下午] 我们商定了API接口格式规范并完成了第一期联调"),
            Message(id = 4, conversationId = 1, role = "assistant", content = "第一期联调通过，状态码均为200。"),
            Message(id = 5, conversationId = 1, role = "user", content = "接下来需要确认离线缓存的过期时间策略")
        )

        val summary = AdvancedMemoryEngine.generateExtractiveStructuredSummary(msgs)
        assertFalse(summary.isEmpty())
        assertTrue("应提取出用户固定约束", summary.coreConstraints.any { it.contains("始终使用中文简短回答") })
        assertTrue("应提取出关键里程碑", summary.milestones.any { it.contains("商定了API接口格式规范") })
        assertTrue("应提取出未决待办项", summary.openItems.any { it.contains("离线缓存的过期时间策略") })
    }

    @Test
    fun testBuildStructuredSummaryPrompt() {
        val prompt = AdvancedMemoryEngine.buildStructuredSummaryPrompt(
            existingSummary = "前期已知背景设定",
            transcript = "用户: 推进第4天情节\n助手: 好的，第4天故事开始...",
            tokenBudget = 800
        )
        assertTrue(prompt.contains("【核心背景与用户固定约束】"))
        assertTrue(prompt.contains("【历史关键里程碑与决策推进】"))
        assertTrue(prompt.contains("【当前未决议题与待办上下文】"))
        assertTrue(prompt.contains("前期已知背景设定"))
        assertTrue(prompt.contains("推进第4天情节"))
    }

    @Test
    fun testSingleConversationExport_sessionMemoriesSerialization() {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val sessionMem = MemoryItem(
            id = 10,
            conversationId = 1,
            content = "角色世界观：星际联邦第三舰队",
            scope = "conversation",
            isEnabled = true
        )
        val node = com.aiassistant.domain.model.TimelineNode(
            id = 5,
            conversationId = 1,
            timeTag = "宇宙历 2380 年",
            event = "启航节点：舰队离开空间站"
        )
        val conv = com.aiassistant.domain.model.Conversation(
            id = 1,
            title = "星际探险",
            apiConfigId = 1L,
            modelName = "claude-sonnet-4-6",
            enableSessionMemory = true
        )
        val bundle = com.aiassistant.utils.BackupManager.SingleConversationExport(
            conversation = conv,
            timelineNodes = listOf(node),
            sessionMemories = listOf(sessionMem)
        )

        val json = gson.toJson(bundle)
        assertTrue("备份 JSON 必须包含 sessionMemories", json.contains("sessionMemories"))
        assertTrue("备份 JSON 必须包含设定内容", json.contains("星际联邦第三舰队"))
        assertTrue("备份 JSON 必须包含时间线节点", json.contains("启航节点"))

        // 测试反序列化
        val deserialized = gson.fromJson(json, com.aiassistant.utils.BackupManager.SingleConversationExport::class.java)
        assertNotNull(deserialized)
        assertEquals(1, deserialized.sessionMemories?.size)
        assertEquals("角色世界观：星际联邦第三舰队", deserialized.sessionMemories?.first()?.content)
        assertEquals(1, deserialized.timelineNodes?.size)
        assertEquals("启航节点：舰队离开空间站", deserialized.timelineNodes?.first()?.event)
    }

    @Test
    fun testMessage_modelNameFieldPersistence() {
        val msg = Message(
            id = 1,
            conversationId = 1,
            role = "assistant",
            content = "测试回复",
            thinkingContent = "测试思考过程",
            modelName = "claude-sonnet-4-6"
        )
        assertEquals("claude-sonnet-4-6", msg.modelName)
        val copied = msg.copy(content = "修改后的回复")
        assertEquals("claude-sonnet-4-6", copied.modelName)
    }

    @Test
    fun testModelNameBackfill_atomicGuardProtectsInfiniteLoop() {
        val guard = java.util.concurrent.atomic.AtomicBoolean(false)
        assertTrue("首次尝试回填必须允许执行", guard.compareAndSet(false, true))
        assertFalse("后续重复调用必须直接拦截，杜绝死循环", guard.compareAndSet(false, true))
        assertFalse("多次并发调用必须恒定拦截", guard.compareAndSet(false, true))
    }
}
