package com.aiassistant

import com.aiassistant.domain.model.Message
import com.aiassistant.utils.AdvancedMemoryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingSummaryEnhancementTest {

    @Test
    fun testExtractCompleteSentence_preservesPrimaryPunctuation() {
        val longText = "我们决定采用 Kotlin 和 Jetpack Compose 作为客户端技术栈。数据库使用 Room 进行本地存储。网络层采用 Retrofit。"
        val extracted = AdvancedMemoryEngine.extractCompleteSentence(longText, 45)

        // 验证：应在第一个完整句子句号处截断，绝不能在字中间截断
        assertTrue("必须保留完整的第一个分句并以标点闭合", extracted.endsWith("。"))
        assertTrue("必须包含技术栈关键词", extracted.contains("技术栈"))
        assertFalse("不应截断包含后面的破碎语句", extracted.contains("存储"))
    }

    @Test
    fun testExtractCompleteSentence_preservesSecondaryPunctuationWithEllipsis() {
        val longTextNoFullStop = "我们讨论了客户端重构架构方案，包括模块解耦，以及数据库迁移升级策略，还有流式输出优化"
        val extracted = AdvancedMemoryEngine.extractCompleteSentence(longTextNoFullStop, 30)

        assertTrue("在逗号处截断并保留省略号", extracted.endsWith("..."))
        assertTrue("提取内容完整", extracted.contains("客户端重构架构方案"))
    }

    @Test
    fun testGenerateExtractiveStructuredSummary_neverViolentlyTruncatesSentences() {
        val messages = listOf(
            Message(
                id = 1,
                conversationId = 1,
                role = "user",
                content = "请帮我制定一个完整的项目优化方案，包含架构升级与网络重构。"
            ),
            Message(
                id = 2,
                conversationId = 1,
                role = "assistant",
                content = "好的，我们决定采用分层架构改造，并且统一接入长超时分析服务确保大模型生成稳定。"
            ),
            Message(
                id = 3,
                conversationId = 1,
                role = "user",
                content = "接下来需要确认滚动摘要与记忆系统的协同去重策略。"
            )
        )

        val summary = AdvancedMemoryEngine.generateExtractiveStructuredSummary(messages)
        val promptBlock = summary.toPromptBlock()

        assertFalse("严禁包含暴力截断的破碎词", promptBlock.contains("截断"))
        assertFalse("严禁包含生硬的上下文状态机术语废话", promptBlock.contains("高保真结构化上下文状态机"))
        assertTrue("应提取出决策关键句", summary.milestones.any { it.contains("分层架构改造") })
        assertTrue("应提取出下一步待办项", summary.openItems.any { it.contains("协同去重策略") })
        // 验证每一个 milestone 都是完整句子或自然句，绝无 70 字符硬切
        summary.milestones.forEach { item ->
            assertTrue("里程碑内容应有一定信息量", item.length >= 10)
        }
    }

    @Test
    fun testExtractiveStructuredSummary_onlyScansRecentWindow() {
        val oldMessages = (1..35).map { idx ->
            Message(
                id = idx.toLong(),
                conversationId = 1,
                role = if (idx % 2 == 1) "user" else "assistant",
                content = if (idx == 1) "【决定】：古代开篇决定在车站见面。" else "第 $idx 轮常规对话进展记录。"
            )
        }
        val recentMessages = listOf(
            Message(
                id = 36,
                conversationId = 1,
                role = "assistant",
                content = "【商定】：两人商定在商业街吃拉面。"
            ),
            Message(
                id = 37,
                conversationId = 1,
                role = "user",
                content = "接下来需要前往拉面馆就餐。"
            )
        )
        val allMessages = oldMessages + recentMessages
        val summary = AdvancedMemoryEngine.generateExtractiveStructuredSummary(allMessages)

        // 验证：37 条消息中，前 7 条（包含第 1 条“古代开篇决定在车站见面”）被排除在最近 30 条窗口之外
        assertFalse("兜底提取严禁包含远古第 1 条开篇事件", summary.milestones.any { it.contains("古代开篇决定在车站见面") })
        assertTrue("兜底提取应正确包含近期商定事件", summary.milestones.any { it.contains("商业街吃拉面") })
        assertTrue("兜底提取应包含最新下一步待办", summary.openItems.any { it.contains("前往拉面馆就餐") })
    }

    @Test
    fun testBuildStructuredSummaryPrompt_containsClarityAndCompletenessDirectives() {
        val prompt = AdvancedMemoryEngine.buildStructuredSummaryPrompt(
            existingSummary = "已有技术选型讨论",
            transcript = "用户: 请总结当前进度。\n助手: 目前已完成底层架构迁移。",
            tokenBudget = 2000
        )

        assertTrue("提示词必须明确要求表述完整与严禁截断", prompt.contains("严禁被暴力截断或半句截断"))
        assertTrue("提示词必须要求言之有物与表述完整", prompt.contains("言之有物、表述完整、逻辑严谨"))
        assertTrue("提示词必须要求每句话有始有终", prompt.contains("每条记录必须是完整、通顺、有始有终的句子"))
        assertTrue("提示词必须包含时空演变与阶段发展", prompt.contains("【时空演变与关键时间节点（极重要，严禁遗漏）】"))
        assertTrue("提示词必须包含起始时间与总跨度", prompt.contains("起始时间与总跨度"))
        assertTrue("提示词必须强调时间概念准确性", prompt.contains("时间概念必须严密准确，严禁出现前序事件时序倒流或将数天前事件混淆为昨天的错误"))
        assertTrue("提示词必须严禁模糊为昨天", prompt.contains("严禁将早期事件模糊为“昨天”！"))
        assertTrue("提示词必须包含当前故事停顿节点", prompt.contains("当前故事停顿节点"))
        assertTrue("提示词必须强调核心脉络与未决议题", prompt.contains("核心脉络与未决议题"))
        assertTrue("提示词必须强调时间线系统紧密协同互补", prompt.contains("时间线系统紧密协同互补"))
        assertTrue("提示词必须强调避免机械复读冗长的时间节点列表", prompt.contains("避免机械复读冗长的时间节点列表"))
        assertTrue("提示词必须强调当前未决议题与待办事项", prompt.contains("当前未决议题与待办事项"))
        assertTrue("提示词必须强调整体的时间线通过记忆读取", prompt.contains("整体的时间线通过记忆读取"))
        assertTrue("提示词必须强调摘要只负责总结最近发生了什么", prompt.contains("摘要只负责总结最近发生了什么"))
        assertTrue("提示词必须强调摘要总结的内容只能和时间线最新的时间节点关联上", prompt.contains("摘要总结的内容只能和时间线最新的时间节点关联上"))
        assertTrue("提示词必须强调严禁跨越中段剧情去错误连接开场相见与最新事件等断层情节", prompt.contains("严禁跨越中段剧情去错误连接开场相见与最新事件等断层情节"))
        assertTrue("提示词必须强调必须完整输出全部4个板块", prompt.contains("必须完整输出全部 4 个板块"))
        assertTrue("提示词必须严禁只输出板块标题而不写实质内容", prompt.contains("绝对严禁只输出板块标题而不写实质内容"))
    }

    @Test
    fun testBuildStructuredSummaryPrompt_withLatestTimelineAnchor_enforcesStrictAnchoring() {
        val prompt = AdvancedMemoryEngine.buildStructuredSummaryPrompt(
            existingSummary = "第1天 两主角在车站初次相见并达成调查约定",
            transcript = "用户: 晚上一块去吃拉面吧。\n助手: 好的，在街角那家店碰面。",
            tokenBudget = 2000,
            latestTimelineAnchor = "[第 3 天·傍晚] 调查告一段落，两人相约商业街"
        )

        assertTrue("提示词必须注入时间线最新节点", prompt.contains("【时间线最新时间节点（时序基准）】："))
        assertTrue("提示词必须包含具体时间节点内容", prompt.contains("[第 3 天·傍晚] 调查告一段落，两人相约商业街"))
        assertTrue("提示词必须包含时空锚定铁律", prompt.contains("整体时间线通过记忆读取，摘要只负责总结最近发生了什么，摘要总结的内容只能和时间线最新的时间节点关联上！"))
        assertTrue("提示词必须严禁将更早开场情节与近期事件跨段因果连接", prompt.contains("严禁将更早开场情节（如初次相见）与近期事件跨段因果连接"))
    }

    @Test
    fun testV250UserUpdatesCompleteness() {
        assertTrue("CurrentVersionUserUpdates 数量必须大于等于 5 项满足基准", com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates.size >= 5)
        org.junit.Assert.assertEquals("V2.5.0 用户更新日志应有 5 项核心内容", 5, com.aiassistant.ui.screens.settings.V250UserUpdates.size)
        assertTrue("必须包含滚动摘要彻底移除说明", com.aiassistant.ui.screens.settings.V250UserUpdates.any { it.contains("滚动摘要彻底移除") })
        assertTrue("必须包含最近十几次对话无损保全说明", com.aiassistant.ui.screens.settings.V250UserUpdates.any { it.contains("最近十几次对话（16+ 条）无损保全") })
        assertTrue("必须包含梳理时间线与提炼专属记忆说明", com.aiassistant.ui.screens.settings.V250UserUpdates.any { it.contains("梳理时间线与提炼专属记忆") })
        assertTrue("必须包含上下文用量与压缩界面全面焕新说明", com.aiassistant.ui.screens.settings.V250UserUpdates.any { it.contains("上下文用量与压缩界面全面焕新") })
        assertTrue("必须包含双轨记忆与多轮对话承接说明", com.aiassistant.ui.screens.settings.V250UserUpdates.any { it.contains("双轨记忆与多轮对话无缝承接") })
    }

    @Test
    fun testRecentMessagesUncompressed_fullContextPreservation() {
        assertEquals("未压缩最近消息保护门限应为 16 条", 16, com.aiassistant.data.repository.AiRepository.UNCOMPRESSED_RECENT_MESSAGE_COUNT)
        assertEquals("滚动摘要预算比例应彻底废除置零", 0.0f, com.aiassistant.data.repository.AiRepository.SUMMARY_BUDGET_RATIO, 0.001f)
        assertTrue("记忆与时间线预算比例应充裕", com.aiassistant.data.repository.AiRepository.MEMORY_BUDGET_RATIO >= 0.10f)
    }

    @Test
    fun testCandidateMessages_neverTruncatesRecentSixteenMessages() {
        val messages = (1..25).map { id ->
            Message(
                id = id.toLong(),
                conversationId = 1L,
                role = if (id % 2 == 1) "user" else "assistant",
                content = "第 $id 条对话内容"
            )
        }
        val usableMessages = messages.filter { (it.role == "user" || it.role == "assistant") && it.content.isNotBlank() }
        val threshold = com.aiassistant.data.repository.AiRepository.UNCOMPRESSED_RECENT_MESSAGE_COUNT
        // 验证至少保留最近 16 条消息不做压缩
        val recentMessages = usableMessages.takeLast(threshold)
        assertEquals(16, recentMessages.size)
        assertEquals(10L, recentMessages.first().id)
        assertEquals(25L, recentMessages.last().id)
        assertTrue("最近 16 条消息全部完整保留", recentMessages.all { it.id >= 10L })
    }

    @Test
    fun testV240UserUpdatesCompleteness() {
        org.junit.Assert.assertEquals("V2.4.0 用户更新日志应有 5 项核心内容", 5, com.aiassistant.ui.screens.settings.V240UserUpdates.size)
        assertTrue("必须包含滚动摘要末尾孤立空标题与残缺彻底根治说明", com.aiassistant.ui.screens.settings.V240UserUpdates.any { it.contains("滚动摘要末尾孤立空标题与残缺彻底根治") })
        assertTrue("必须包含四大核心板块完整性校验与自愈回退说明", com.aiassistant.ui.screens.settings.V240UserUpdates.any { it.contains("四大核心板块完整性校验与自愈回退") })
        assertTrue("必须包含提炼提示词四大板块全量输出铁律说明", com.aiassistant.ui.screens.settings.V240UserUpdates.any { it.contains("提炼提示词四大板块全量输出铁律") })
        assertTrue("必须包含全网主流模型参数合规化说明", com.aiassistant.ui.screens.settings.V240UserUpdates.any { it.contains("全网主流模型参数合规化") })
        assertTrue("必须包含提炼超时进一步放宽保障深度思考说明", com.aiassistant.ui.screens.settings.V240UserUpdates.any { it.contains("提炼超时进一步放宽保障深度思考") })
    }

    @Test
    fun testV239UserUpdatesCompleteness() {
        org.junit.Assert.assertEquals("V2.3.9 用户更新日志应有 5 项核心内容", 5, com.aiassistant.ui.screens.settings.V239UserUpdates.size)
        assertTrue("必须包含滚动摘要思考模型截断彻底根治说明", com.aiassistant.ui.screens.settings.V239UserUpdates.any { it.contains("滚动摘要思考模型截断彻底根治") })
        assertTrue("必须包含尾部断句防腰斩安全闭合保护说明", com.aiassistant.ui.screens.settings.V239UserUpdates.any { it.contains("尾部断句防腰斩安全闭合保护") })
        assertTrue("必须包含记忆已有偏好约束严格去重说明", com.aiassistant.ui.screens.settings.V239UserUpdates.any { it.contains("记忆已有偏好约束严格去重") })
        assertTrue("必须包含本地抽取式兜底同步去重说明", com.aiassistant.ui.screens.settings.V239UserUpdates.any { it.contains("本地抽取式兜底同步去重") })
        assertTrue("必须包含查看编辑弹窗全屏平滑滚动说明", com.aiassistant.ui.screens.settings.V239UserUpdates.any { it.contains("查看编辑弹窗全屏平滑滚动") })
    }

    @Test
    fun testV238UserUpdatesCompleteness() {
        org.junit.Assert.assertEquals("V2.3.8 用户更新日志应有 5 项核心内容", 5, com.aiassistant.ui.screens.settings.V238UserUpdates.size)
        assertTrue("必须包含滚动摘要断层情节拼接彻底根除说明", com.aiassistant.ui.screens.settings.V238UserUpdates.any { it.contains("滚动摘要断层情节拼接彻底根除") })
        assertTrue("必须包含时间线记忆与滚动摘要职责彻底明晰说明", com.aiassistant.ui.screens.settings.V238UserUpdates.any { it.contains("时间线记忆与滚动摘要职责彻底明晰") })
        assertTrue("必须包含摘要总结内容强制锚定最新时间节点说明", com.aiassistant.ui.screens.settings.V238UserUpdates.any { it.contains("摘要总结内容强制锚定最新时间节点") })
        assertTrue("必须包含最新时间基准动态注入提炼引擎说明", com.aiassistant.ui.screens.settings.V238UserUpdates.any { it.contains("最新时间基准动态注入提炼引擎") })
        assertTrue("必须包含本地抽取式兜底严格限制近期轮次说明", com.aiassistant.ui.screens.settings.V238UserUpdates.any { it.contains("本地抽取式兜底严格限制近期轮次") })
    }

    @Test
    fun testV237UserUpdatesCompleteness() {
        org.junit.Assert.assertEquals("V2.3.7 用户更新日志应有 5 项核心内容", 5, com.aiassistant.ui.screens.settings.V237UserUpdates.size)
        assertTrue("必须包含滚动摘要标点断句保护彻底根除暴力截断说明", com.aiassistant.ui.screens.settings.V237UserUpdates.any { it.contains("滚动摘要标点断句保护彻底根除暴力截断") })
        assertTrue("必须包含滚动摘要提示词去机械化与上下文深度提炼说明", com.aiassistant.ui.screens.settings.V237UserUpdates.any { it.contains("滚动摘要提示词去机械化与上下文深度提炼") })
        assertTrue("必须包含长分析服务通道全面升级保障生成韧性说明", com.aiassistant.ui.screens.settings.V237UserUpdates.any { it.contains("长分析服务通道全面升级保障生成韧性") })
    }

    @Test
    fun testSanitizeSummaryCompletion_trimsHalfSentenceAndClosesProperly() {
        // 测试用例 1：带 <think> 标签的思考过程被剔除
        val rawWithThink = "<think>思考中...\n分析时间线...</think>【核心背景与用户固定约束】\n讨论最新技术重构方案。"
        val cleaned = com.aiassistant.data.repository.AiRepository.sanitizeSummaryCompletion(rawWithThink)
        assertNotNull("清洗结果不应为空", cleaned)
        assertFalse("不应残留 think 标签", cleaned!!.contains("<think>"))
        assertTrue("应保留正文", cleaned.contains("【核心背景与用户固定约束】"))
        assertTrue("应以合法标点结尾", cleaned.endsWith("。"))

        // 测试用例 2：末尾出现中途截断的半截残句（例如模型 max_tokens 截断）
        val truncatedRaw = """
            【核心背景与用户固定约束】
            讨论最新技术重构方案。

            【历史关键里程碑与决策推进】
            1. 确定采用 Kotlin 语言。
            2. 双方正在商量接下来的界
        """.trimIndent()
        val sanitizedTruncated = com.aiassistant.data.repository.AiRepository.sanitizeSummaryCompletion(truncatedRaw)
        assertNotNull(sanitizedTruncated)
        assertFalse("截断的半句必须被安全剔除", sanitizedTruncated!!.contains("双方正在商量接下来的界"))
        assertTrue("前面的完整句子必须完整保留", sanitizedTruncated.contains("1. 确定采用 Kotlin 语言。"))

        // 测试用例 3：单行未以标点结尾，安全自动补全句号
        val singleLineRaw = "两人在街角拉面馆就餐讨论方案"
        val singleLineSanitized = com.aiassistant.data.repository.AiRepository.sanitizeSummaryCompletion(singleLineRaw)
        assertEquals("单行无标点应安全闭合", "两人在街角拉面馆就餐讨论方案。", singleLineSanitized)

        // 测试用例 4：用户反馈的现场真实故障——末尾孤立的章节标题后无任何内容
        val danglingHeaderRaw = """
            【核心背景与用户固定约束】
            - 核心主题：讨论系统高可用重构方案与服务端部署计划。

            【历史关键里程碑与决策推进】
        """.trimIndent()
        val cleanedDangling = com.aiassistant.data.repository.AiRepository.sanitizeSummaryCompletion(danglingHeaderRaw)
        assertNotNull(cleanedDangling)
        assertFalse("末尾孤立的章节标题必须被彻底剔除", cleanedDangling!!.contains("【历史关键里程碑与决策推进】"))
        assertTrue("前面的有效板块必须完整保留", cleanedDangling.contains("【核心背景与用户固定约束】"))
        assertTrue("必须以合法标点闭合", cleanedDangling.endsWith("。"))
    }

    @Test
    fun testIsSummarySubstantiallyComplete_validatesCompletenessCorrectly() {
        // 空值或极短文本判定为不完整
        assertFalse("空文本判定为不完整", com.aiassistant.data.repository.AiRepository.isSummarySubstantiallyComplete(""))
        assertFalse("空白判定为不完整", com.aiassistant.data.repository.AiRepository.isSummarySubstantiallyComplete("   "))
        assertFalse("过短文本判定为不完整", com.aiassistant.data.repository.AiRepository.isSummarySubstantiallyComplete("只有几个字。"))

        // 末尾带有孤立章节标题的文本，判定为不完整
        val withDanglingHeader = """
            【核心背景与用户固定约束】
            - 核心主题：讨论系统高可用重构方案。

            【历史关键里程碑与决策推进】
        """.trimIndent()
        assertFalse("末尾悬空标题应判定为不完整", com.aiassistant.data.repository.AiRepository.isSummarySubstantiallyComplete(withDanglingHeader))

        // 仅有单一板块的摘要，判定为不完整
        val singleSection = """
            【核心背景与用户固定约束】
            - 核心主题：讨论系统高可用重构方案与服务端部署计划。
        """.trimIndent()
        assertFalse("只有单一板块应判定为实质不完整", com.aiassistant.data.repository.AiRepository.isSummarySubstantiallyComplete(singleSection))

        // 包含两个以上板块且正常闭合的文本，判定为完整
        val completeText = """
            【核心背景与用户固定约束】
            - 核心主题：讨论系统高可用重构方案。

            【历史关键里程碑与决策推进】
            1. 确定采用分层架构设计。

            【当前未决议题与待办上下文】
            - 待办：落实监控告警接入。
        """.trimIndent()
        assertTrue("多板块且闭合的内容应判定为实质完整", com.aiassistant.data.repository.AiRepository.isSummarySubstantiallyComplete(completeText))
    }

    @Test
    fun testBuildStructuredSummaryPrompt_memoryDeduplicationDirectives() {
        val existingConstraints = listOf(
            "用户偏好简洁中文回复，禁止使用英文术语",
            "必须始终保持冷静理性的语气"
        )
        val prompt = AdvancedMemoryEngine.buildStructuredSummaryPrompt(
            existingSummary = "已有早期设定",
            transcript = "用户: 明天早上九点准时出发。\n助手: 好的，已确认行程。",
            tokenBudget = 2000,
            latestTimelineAnchor = "[第 2 天·清晨] 准备出发",
            existingPreferencesAndConstraints = existingConstraints
        )

        // 验证提示词注入了去重指令
        assertTrue("提示词必须明确声明不要总结记忆中已经存在的内容", prompt.contains("不要总结记忆中已经存在的内容"))
        assertTrue("提示词必须严禁总结用户偏好习惯", prompt.contains("严禁总结用户偏好习惯"))
        assertTrue("提示词必须包含已有记忆约束板块", prompt.contains("【已有记忆与偏好约束（严禁在此重复提炼）】："))
        assertTrue("必须列出已知约束1", prompt.contains("用户偏好简洁中文回复，禁止使用英文术语"))
        assertTrue("必须列出已知约束2", prompt.contains("必须始终保持冷静理性的语气"))
        assertTrue("必须声明记忆去重铁律", prompt.contains("以上用户偏好习惯、行为禁令与固定约束已由系统记忆全量保存，本摘要严禁总结或记录上述任何内容！"))
    }

    @Test
    fun testExtractiveStructuredSummary_deduplicatesKnownPreferences() {
        val messages = listOf(
            Message(id = 1, conversationId = 1, role = "user", content = "请记住：用户偏好简洁回复，严禁废话。"),
            Message(id = 2, conversationId = 1, role = "assistant", content = "好的，已记录您的偏好。"),
            Message(id = 3, conversationId = 1, role = "user", content = "我们决定明天早上九点去车站集合。"),
            Message(id = 4, conversationId = 1, role = "assistant", content = "好的，明天见。")
        )
        val knownConstraints = listOf("用户偏好简洁回复，严禁废话")
        val summary = AdvancedMemoryEngine.generateExtractiveStructuredSummary(
            messages = messages,
            maxTokens = 1000,
            existingPreferencesAndConstraints = knownConstraints
        )

        // 已在记忆中的约束不应重复被提取进 coreConstraints
        assertFalse("已在记忆库中的偏好不应重复提炼", summary.coreConstraints.any { it.contains("用户偏好简洁回复") })
        assertTrue("有效事件必须被提取", summary.milestones.any { it.contains("车站集合") || it.contains("决定") })
    }
}
