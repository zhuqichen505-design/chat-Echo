package com.aiassistant

import com.aiassistant.domain.model.Message
import com.aiassistant.data.repository.helpers.ConversationTitleHelper
import com.aiassistant.data.repository.helpers.SummaryResponseValidator
import com.aiassistant.utils.AdvancedMemoryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingSummaryEnhancementTest {

    @Test
    fun summaryValidatorRejectsLongTaskRefusalsBehindMarkdownAndPolicyIntroductions() {
        val refusals = listOf(
            "由于安全政策限制，我不能为你生成这份摘要。请调整提供的内容，移除不符合规范的部分后再试。",
            "## 响应说明\n\n> **很抱歉，我无法总结所提供的上下文，因为这些内容触发了安全政策。**\n> 请修改输入后重新提交。",
            "```text\n根据相关政策，当前请求触发内容审核。请修改提供的文本后重试，以便继续处理摘要任务。\n```",
            "“我无法完成这个请求。所提供的文本包含不允许的内容，请更换材料之后重新尝试生成摘要。”",
            "无法生成摘要，因为这段输入不符合适用的内容政策。你可以提供其他材料之后再进行总结。"
        )
        refusals.forEach { refusal ->
            assertNotNull("任务拒绝不能作为历史摘要保存：$refusal", SummaryResponseValidator.refusalReason(refusal))
            assertFalse(SummaryResponseValidator.isUsableSummary(refusal))
        }
    }

    @Test
    fun summaryValidatorRejectsEnglishRefusalsWithContractionsAndLongExplanations() {
        val refusals = listOf(
            "In accordance with safety guidelines, I cannot provide a summary of the supplied text. Please revise the request before trying again.",
            "**I’m unable to summarize the provided content because it conflicts with the applicable content policy.** I can assist with a different request.",
            "I'm sorry, but I can't assist with that request. Please remove the restricted material and try a different input.",
            "We are not permitted to process this request under our safety guidelines. Please modify the input and try again.",
            "Your request has been blocked under the content policy. Please revise the material and try again."
        )
        refusals.forEach { refusal ->
            assertNotNull("英文拒绝必须单独报告", SummaryResponseValidator.refusalReason(refusal))
            assertFalse(SummaryResponseValidator.isUsableSummary(refusal))
        }
    }

    @Test
    fun summaryRefusalReasonIsBoundedWithoutAcceptingARepeatedLongExplanation() {
        val refusal = "很抱歉，受内容政策限制，我无法对此类内容进行处理并生成摘要。" +
            "这是一段合成的限制说明，请调整输入后重试。".repeat(80)
        val reason = SummaryResponseValidator.refusalReason(refusal)
        assertNotNull(reason)
        assertTrue("展示原因须限制长度", reason!!.length <= 600)
        assertTrue(reason.contains("无法对此类内容进行处理"))
        assertFalse(SummaryResponseValidator.isUsableSummary(refusal))
    }

    @Test
    fun summaryValidatorPreservesExistingTaskRefusalCoverageAndRejectsBareApologiesByLength() {
        val refusals = listOf(
            "无法返回这份摘要，因为本次没有获得能够独立使用的完整上下文，请重新提供有效原文后再试。",
            "无法生成所请求的摘要，请重新提供完整上下文后再试，当前请求不能被正常完成。",
            "未能生成这份摘要，因为缺少完成本次任务所需的上下文，请重新提供原文后再试。",
            "不能生成这份摘要，因为所提供的上下文不完整，请重新提供能够独立使用的全部原文。",
            "抱歉，我无法协助完成这个请求，请修改输入之后重新尝试生成摘要。",
            "抱歉, 我不能生成这份摘要，请重新提供符合内容政策的其他材料之后再试。",
            "Sorry, I cannot provide the requested summary. Please provide the complete source material first.",
            "I cannot generate a summary of the provided content. Please revise the request before trying again.",
            "I can't help with that request. Please provide different content that can be summarized."
        )
        refusals.forEach { refusal ->
            assertNotNull(SummaryResponseValidator.refusalReason(refusal))
            assertFalse(SummaryResponseValidator.isUsableSummary(refusal))
        }
        listOf("抱歉，", "抱歉。", "sorry,", "I cannot", "无法生成").forEach { incomplete ->
            assertFalse("独立道歉或无正文声明不能作为摘要", SummaryResponseValidator.isUsableSummary(incomplete))
        }
    }

    @Test
    fun summaryValidatorPreservesNarrativeSafetyRulesAndQuotedCharacterRefusals() {
        val summaries = listOf(
            "【背景约束】\n资料室存在安全限制，守卫只允许持蓝印凭证者进入。\n【经过】\n角色在伪造信件中引用“抱歉，我无法生成这份摘要，因为它违反安全政策。”来揭穿骗局。\n【待办】\n队伍准备核对下一张凭证。",
            "The guard said, \"I cannot help with that request due to safety policy.\" The synthetic crew left the archive, kept the blue pass, and planned to ask the keeper the next morning.",
            "合成故事中的角色因安全政策不能进入资料室，因此决定返回北港等待管理员授权；同伴保管蓝印凭证，下一步核对通行名单。",
            "船队受到安全限制。守卫无法提供通行许可，角色决定先核对身份。蓝印凭证仍由领航员妥善保管。",
            "角色无法总结此前经历，因此暂缓答复。队伍决定先核对北港记录，并在翌日带齐凭证后再去寻找见证人。",
            "我无法返回北港，因为新的安全政策暂时禁止船只靠岸；同伴仍保管蓝印凭证，队伍决定先在外港等待通行许可。",
            "安全政策规定合成魔法不能生成临时通行证，因此队伍改向港口申请正式凭证，并决定等待下一班渡船到来。",
            "【当前摘要】\n我无法帮助同伴打开上锁的资料柜，因此队伍先返回港口。\n【后续】\n明日寻找管理员取回铜钥匙，现有凭证继续保管。"
        )
        summaries.forEach { summary ->
            assertNull("剧情事实与嵌入引文不等于模型拒绝：$summary", SummaryResponseValidator.refusalReason(summary))
            assertTrue(SummaryResponseValidator.isUsableSummary(summary))
        }
    }

    @Test
    fun summaryValidatorKeepsPlainCompletedFactsButRejectsOnlyHeadingsAndThinking() {
        val summary = "合成队伍已完成北港登记，蓝印凭证仍由领航员保管；此前的采购已结账，下一步核对航线并等待放行。"
        assertTrue(SummaryResponseValidator.isUsableSummary(summary))
        assertFalse(SummaryResponseValidator.isUsableSummary("【核心背景与用户固定约束】\n【历史关键里程碑与决策推进】"))
        assertFalse(SummaryResponseValidator.isUsableSummary(null))
        assertFalse(SummaryResponseValidator.isUsableSummary("<think>这段很长的合成思考并不是摘要正文，不能通过长度检查后进入历史记忆。</think>"))
        assertNull(ConversationTitleHelper.sanitizeSummaryCompletion("<think>我无法生成摘要，因为触发安全政策。</think>", preserveCompleteText = true))
    }

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
        assertTrue("摘要不能依赖可能未注入的记忆", prompt.contains("不能假设省略的事实一定由其他记忆完整承载"))
        assertFalse("摘要不得丢弃已确认早期事实", prompt.contains("摘要只负责总结最近发生了什么"))
        assertTrue("各事件保留原始时间", prompt.contains("每个事件保留原始时间"))
        assertTrue("摘要不得编造未发生事实", prompt.contains("不把假设、计划、导演要求或未选择的回复当作已发生事实"))
        assertTrue("无事实的板块不应诱导幻觉", prompt.contains("不要补造内容"))
        assertTrue("合并时必须保留未解决矛盾", prompt.contains("矛盾未解决时标注双方说法"))
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
        assertTrue("最新时间不能改写旧事件日期", prompt.contains("最新时间锚点不能覆盖早期事件日期"))
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
        assertEquals("滚动摘要必须分配明确预算", 0.12f, com.aiassistant.data.repository.AiRepository.SUMMARY_BUDGET_RATIO, 0.001f)
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
    fun summaryThinkRemovalKeepsBodyBeforeBetweenAndAfterMultipleThinkingBlocks() {
        val raw = "合成早期约束。<think>合成推理一</think>合成事件结果。<ThInK>合成推理二</tHiNk>合成未决事项。"
        val expected = "合成早期约束。合成事件结果。合成未决事项。"

        assertEquals(expected, ConversationTitleHelper.sanitizeSummaryCompletion(raw))
        assertEquals(expected, ConversationTitleHelper.sanitizeSummaryCompletion(raw, preserveCompleteText = true))
    }

    @Test
    fun unclosedThinkingNeverLeaksIntoSummaryAndDoesNotEraseEarlierBody() {
        val raw = "合成约束仍然有效。<THINK>尚未结束的合成推理</Thi"

        assertEquals("合成约束仍然有效。", ConversationTitleHelper.sanitizeSummaryCompletion(raw))
        assertEquals("合成约束仍然有效。", ConversationTitleHelper.sanitizeSummaryCompletion(raw, preserveCompleteText = true))
        assertNull(ConversationTitleHelper.sanitizeSummaryCompletion("<ThInK>只有合成推理"))
        assertNull(ConversationTitleHelper.sanitizeSummaryCompletion("<THINK>只有合成推理</THINK>", preserveCompleteText = true))
    }

    @Test
    fun completedSummaryKeepsUnpunctuatedIdentifiersWhenRemovingThinking() {
        val raw = "合成约束。<THINK>合成推理</think>\nCONFIG_ID=synthetic_config"

        assertEquals("合成约束。\nCONFIG_ID=synthetic_config",
            ConversationTitleHelper.sanitizeSummaryCompletion(raw, preserveCompleteText = true))
    }

    @Test
    fun unknownCompletionStillRemovesDanglingHeaderAfterThinking() {
        val raw = "合成约束。\n<think>合成推理</THINK>\n【合成待办】"

        assertEquals("合成约束。", ConversationTitleHelper.sanitizeSummaryCompletion(raw))
        assertEquals("合成约束。\n\n【合成待办】",
            ConversationTitleHelper.sanitizeSummaryCompletion(raw, preserveCompleteText = true))
    }

    @Test
    fun summaryMustContainFactsBeyondMarkdownHeadingsAndEmptyPlaceholders() {
        val titleOnly = "## 人物身份与关系\n## 世界观与场景设定\n## 已发生的重要事件\n## 当前状态与未完成事项"
        val placeholders = "【核心背景固定约束】\n- 待补充\n【历史事件推进】\n- TODO\n【当前未决事项】\n-"
        val setextTitles = "人物身份与关系\n================\n世界观与场景设定\n----------------\n当前状态与未完成事项\n================"
        for (empty in listOf(titleOnly, placeholders, setextTitles)) {
            assertFalse(com.aiassistant.data.repository.helpers.SummaryResponseValidator.hasSubstantiveContent(empty))
            assertFalse(com.aiassistant.data.repository.helpers.SummaryResponseValidator.isUsableSummary(empty))
        }
        val valid = "## 人物身份与关系\n合成船队仍由铜印守卫护送，已核对北港凭证，下一步检查 synthetic-outline.json"
        assertTrue(com.aiassistant.data.repository.helpers.SummaryResponseValidator.isUsableSummary(valid))
        assertTrue("短手工事实不能因长度被撤销覆盖", com.aiassistant.data.repository.helpers.SummaryResponseValidator.hasSubstantiveContent("船队已到北港。"))
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
        assertTrue("应保留仍有效的早期事实", prompt.contains("保留既有摘要中仍有效的早期事实"))
        assertTrue("关键约束必须在去重后仍被保留", prompt.contains("仍须保留后续回复必需的约束"))
        assertTrue("提示词必须包含已有记忆约束板块", prompt.contains("【已有记忆与偏好约束（去重参考）】："))
        assertTrue("必须列出已知约束1", prompt.contains("用户偏好简洁中文回复，禁止使用英文术语"))
        assertTrue("必须列出已知约束2", prompt.contains("必须始终保持冷静理性的语气"))
        assertFalse("不得再要求丢弃用户约束", prompt.contains("严禁总结或记录上述任何内容"))
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
