package com.aiassistant

import com.aiassistant.data.repository.helpers.ChatContextAssemblyHelper
import com.aiassistant.data.repository.helpers.ContentPruningHelper
import com.aiassistant.domain.model.CompressionTier
import com.aiassistant.domain.model.CompressionTierPolicy
import com.aiassistant.domain.model.Message
import org.junit.Assert.*
import org.junit.Test

class CompressionTierPolicyTest {

    @Test
    fun testEvaluateAutoUpgrade_whenDisabled_returnsNull() {
        val res = CompressionTierPolicy.evaluateAutoUpgrade(
            currentTier = CompressionTier.L0,
            usagePercent = 0.99f,
            autoEnabled = false
        )
        assertNull("自动升档关闭时严禁自动升档", res)
    }

    @Test
    fun testEvaluateAutoUpgrade_whenBelowThreshold_returnsNull() {
        val res = CompressionTierPolicy.evaluateAutoUpgrade(
            currentTier = CompressionTier.L0,
            usagePercent = 0.70f,
            autoEnabled = true,
            thresholdL2 = 0.75f
        )
        assertNull("占用率低于 75% 阈值时不建议升档", res)
    }

    @Test
    fun testEvaluateAutoUpgrade_progressiveTiers() {
        // 75% -> 触发 L2
        val resL2 = CompressionTierPolicy.evaluateAutoUpgrade(
            currentTier = CompressionTier.L0,
            usagePercent = 0.76f,
            autoEnabled = true
        )
        assertEquals(CompressionTier.L2, resL2)

        // 85% -> 触发 L3
        val resL3 = CompressionTierPolicy.evaluateAutoUpgrade(
            currentTier = CompressionTier.L2,
            usagePercent = 0.86f,
            autoEnabled = true
        )
        assertEquals(CompressionTier.L3, resL3)

        // 95% -> 触发 L4
        val resL4 = CompressionTierPolicy.evaluateAutoUpgrade(
            currentTier = CompressionTier.L3,
            usagePercent = 0.96f,
            autoEnabled = true
        )
        assertEquals(CompressionTier.L4, resL4)

        // 若当前已是 L4，不重复升档
        val resL4Max = CompressionTierPolicy.evaluateAutoUpgrade(
            currentTier = CompressionTier.L4,
            usagePercent = 0.99f,
            autoEnabled = true
        )
        assertNull(resL4Max)

        // 若当前已是 L3，占用率仅在 76%（低于 L3 阈值但高于 L2），不反向降级
        val noDowngrade = CompressionTierPolicy.evaluateAutoUpgrade(
            currentTier = CompressionTier.L3,
            usagePercent = 0.76f,
            autoEnabled = true
        )
        assertNull("自动升档判定绝不向低档位反向降级", noDowngrade)
    }

    @Test
    fun testFallbackOnContextOverflow() {
        // 未超上限
        assertNull(CompressionTierPolicy.fallbackOnContextOverflow(CompressionTier.L0, 1000, 2000))

        // 超过上限依次降档
        assertEquals(CompressionTier.L1, CompressionTierPolicy.fallbackOnContextOverflow(CompressionTier.L0, 2500, 2000))
        assertEquals(CompressionTier.L2, CompressionTierPolicy.fallbackOnContextOverflow(CompressionTier.L1, 2500, 2000))
        assertEquals(CompressionTier.L3, CompressionTierPolicy.fallbackOnContextOverflow(CompressionTier.L2, 2500, 2000))
        assertEquals(CompressionTier.L4, CompressionTierPolicy.fallbackOnContextOverflow(CompressionTier.L3, 2500, 2000))
        assertNull(CompressionTierPolicy.fallbackOnContextOverflow(CompressionTier.L4, 2500, 2000))
    }

    @Test
    fun testContentPruningHelper() {
        // 1. OCR 文本修剪
        val ocrRaw = "用户问题：\n[图片OCR识别：document.png]\n这里是非常长非常长的一大堆识别出来的印刷体文字，包含很多页的扫描文本。\n\n请帮我翻译第一句话。"
        val prunedOcr = ContentPruningHelper.pruneMessageContent(ocrRaw)
        assertTrue(prunedOcr.contains("[附件OCR内容已省略]"))
        assertTrue(prunedOcr.contains("请帮我翻译第一句话。"))
        assertFalse(prunedOcr.contains("一大堆识别出来的印刷体文字"))

        // 2. 联网与工具结果修剪
        val toolRaw = "【实时联网参考信息】\n1. 网页标题：最新科技快讯\n摘要：今日发布重要大模型更新...\n【用户输入的问题/指令】\n请总结今日要闻"
        val prunedTool = ContentPruningHelper.pruneMessageContent(toolRaw)
        assertTrue(prunedTool.contains("[联网与工具调用信息已省略]"))
        assertTrue(prunedTool.contains("请总结今日要闻"))
        assertFalse(prunedTool.contains("今日发布重要大模型更新"))

        // 3. 长代码块折叠（>8 行）
        val longCodeRaw = """
            以下是实现逻辑：
            ```kotlin
            fun line1() {}
            fun line2() {}
            fun line3() {}
            fun line4() {}
            fun line5() {}
            fun line6() {}
            fun line7() {}
            fun line8() {}
            fun line9() {}
            fun line10() {}
            ```
            请检查代码是否有问题。
        """.trimIndent()
        val prunedCode = ContentPruningHelper.pruneMessageContent(longCodeRaw)
        assertTrue(prunedCode.contains("[长代码块已精简折叠: 共 10 行]"))
        assertTrue(prunedCode.contains("请检查代码是否有问题。"))
        assertFalse(prunedCode.contains("fun line5()"))
    }

    @Test
    fun testAssembleTieredContextMessages_safetyBoundaries() {
        val messages = mutableListOf<Message>()
        for (i in 1..40) {
            messages.add(
                Message(
                    id = i.toLong(),
                    conversationId = 1L,
                    role = if (i % 2 == 1) "user" else "assistant",
                    content = if (i == 5) "【固定事实】我必须在任何情况下被保留。" else "对话消息序号 $i",
                    isPinned = (i == 5) // 第 5 条置顶固定
                )
            )
        }

        // L4 极限压缩档位测试：
        // 应该保留：isPinned (id=5) + 最近 8 轮 (最后 16 条，id 25..40)
        val l4Result = ChatContextAssemblyHelper.assembleTieredContextMessages(
            tier = CompressionTier.L4,
            usableMessages = messages,
            recentBudget = 100_000
        )
        val l4Ids = l4Result.activeMessages.map { it.id }.toSet()
        assertTrue("L4 极限压缩必须无条件保留 isPinned 消息", l4Ids.contains(5L))
        assertTrue("L4 极限压缩必须保留最近 1 轮消息 (id=40)", l4Ids.contains(40L))
        assertTrue("L4 极限压缩必须保留最近 1 轮消息 (id=39)", l4Ids.contains(39L))
        assertTrue("L4 极限压缩必须保留最近 8 轮窗口内的消息 (id=25)", l4Ids.contains(25L))
        assertFalse("L4 极限压缩应安全退休未置顶的较早消息 (id=10)", l4Ids.contains(10L))

        // L2 滚动摘要档位测试 (保留 8 轮，16条)
        val l2Result = ChatContextAssemblyHelper.assembleTieredContextMessages(
            tier = CompressionTier.L2,
            usableMessages = messages,
            recentBudget = 100_000,
            l2RecentRounds = 8,
            existingRollingSummary = "之前关于项目技术选型的讨论摘要"
        )
        assertEquals("之前关于项目技术选型的讨论摘要", l2Result.injectedSummary)
        val l2Ids = l2Result.activeMessages.map { it.id }.toSet()
        assertTrue("L2 档位必须强制保留 isPinned 消息", l2Ids.contains(5L))
        assertTrue("L2 档位必须保留最近 1 轮", l2Ids.contains(40L))

        // L3 深度压缩档位测试 (保留 16 轮，32条)
        val l3Result = ChatContextAssemblyHelper.assembleTieredContextMessages(
            tier = CompressionTier.L3,
            usableMessages = messages,
            recentBudget = 100_000,
            structuredSummary = "【核心背景与用户固定约束】：\n- 重要约束"
        )
        assertEquals("【核心背景与用户固定约束】：\n- 重要约束", l3Result.injectedSummary)
        val l3Ids = l3Result.activeMessages.map { it.id }.toSet()
        assertTrue("L3 必须保留最近 16 轮内的消息 (id=15)", l3Ids.contains(15L))
        assertTrue("L3 必须强制保留 isPinned 消息", l3Ids.contains(5L))
    }

    @Test
    fun testDatabaseMigration29To30Registered() {
        val migration = com.aiassistant.data.local.AppDatabase.MIGRATION_29_30
        assertNotNull("MIGRATION_29_30 必须存在", migration)
        assertEquals(29, migration.startVersion)
        assertEquals(30, migration.endVersion)
    }

    // ==================== LC 自定义比例档（v2.7.0 需求 1） ====================

    @Test
    fun testCustomTier_levelMappingAndDescriptions() {
        assertEquals("level 5 必须映射到 LC 自定义比例档", CompressionTier.LC, CompressionTier.fromLevel(5))
        assertEquals(5, CompressionTier.LC.level)
        val desc = CompressionTierPolicy.getRetainedRoundsDesc(CompressionTier.LC, 8, 40)
        assertTrue("描述必须包含自定义百分比 40%", desc.contains("40%"))
    }

    @Test
    fun testFallbackOnContextOverflow_customTierFallsToL4() {
        assertEquals(
            "LC 档仍溢出时按阶梯降到 L4",
            CompressionTier.L4,
            CompressionTierPolicy.fallbackOnContextOverflow(CompressionTier.LC, 5000, 2000)
        )
        assertNull("LC 档未溢出时不降档", CompressionTierPolicy.fallbackOnContextOverflow(CompressionTier.LC, 1000, 2000))
    }

    @Test
    fun testAssembleTieredContext_customPercentWindow() {
        val messages = mutableListOf<Message>()
        for (i in 1..40) {
            messages.add(
                Message(
                    id = i.toLong(),
                    conversationId = 1L,
                    role = if (i % 2 == 1) "user" else "assistant",
                    content = "对话消息序号 $i",
                    isPinned = (i == 5)
                )
            )
        }

        // 40 条消息保留 30% → 窗口 12 条 (id 29..40)，更早历史交由摘要承载
        val result = ChatContextAssemblyHelper.assembleTieredContextMessages(
            tier = CompressionTier.LC,
            usableMessages = messages,
            recentBudget = 100_000,
            existingRollingSummary = "较早历史的滚动摘要",
            customRetainPercent = 30
        )
        assertEquals("LC 档必须注入滚动/结构化摘要", "较早历史的滚动摘要", result.injectedSummary)
        val ids = result.activeMessages.map { it.id }.toSet()
        assertTrue("LC 必须保留最近 1 轮 (id=40)", ids.contains(40L))
        assertTrue("LC 必须保留最近 1 轮 (id=39)", ids.contains(39L))
        assertTrue("LC 窗口应覆盖 30% 起点 (id=29)", ids.contains(29L))
        assertTrue("LC 必须无条件保留 isPinned 消息 (id=5)", ids.contains(5L))
        assertFalse("LC 窗口外未置顶消息应退休 (id=10)", ids.contains(10L))
        assertEquals("LC 激活消息数应为窗口 12 条 + 置顶 1 条", 13, result.activeMessages.size)
    }

    @Test
    fun testAssembleTieredContext_customPercentBudgetAware() {
        val messages = mutableListOf<Message>()
        for (i in 1..40) {
            messages.add(
                Message(
                    id = i.toLong(),
                    conversationId = 1L,
                    role = if (i % 2 == 1) "user" else "assistant",
                    content = "对话消息序号 $i",
                    isPinned = (i == 5)
                )
            )
        }

        // 预算紧张时：LC 窗口内消息允许被裁剪，但置顶与最近 1 轮必须保留（绝不硬性溢出）
        val result = ChatContextAssemblyHelper.assembleTieredContextMessages(
            tier = CompressionTier.LC,
            usableMessages = messages,
            recentBudget = 120,
            existingRollingSummary = null,
            customRetainPercent = 90
        )
        val ids = result.activeMessages.map { it.id }.toSet()
        assertTrue("预算紧张时 isPinned 仍必须保留", ids.contains(5L))
        assertTrue("预算紧张时最近 1 轮仍必须保留 (id=40)", ids.contains(40L))
        assertTrue("预算紧张时激活消息数必须小于窗口理论值", result.activeMessages.size < 38)
    }

    @Test
    fun testAssembleTieredContext_customPercentSingleMessageNoCrash() {
        // v2.6.6 回归：新建对话首发消息后 usableMessages 仅 1 条，
        // 旧实现窗口计算 coerceIn(2, 1) 构成空区间抛 IllegalArgumentException，
        // 统计预览随 Room 流刷新即触发，应用直接闪退
        val single = listOf(
            Message(id = 1L, conversationId = 1L, role = "user", content = "第一条消息")
        )
        val result = ChatContextAssemblyHelper.assembleTieredContextMessages(
            tier = CompressionTier.LC,
            usableMessages = single,
            recentBudget = 100_000,
            customRetainPercent = 30
        )
        assertEquals("单条消息必须完整保留", 1, result.activeMessages.size)
        assertEquals("消息内容不得被改写", "第一条消息", result.activeMessages[0].content)
    }

    @Test
    fun testAssembleTieredContext_customPercentTwoMessagesKeepsLastRound() {
        val two = listOf(
            Message(id = 1L, conversationId = 1L, role = "user", content = "消息一"),
            Message(id = 2L, conversationId = 1L, role = "assistant", content = "消息二")
        )
        val result = ChatContextAssemblyHelper.assembleTieredContextMessages(
            tier = CompressionTier.LC,
            usableMessages = two,
            recentBudget = 100_000,
            customRetainPercent = 10
        )
        assertEquals("低百分比下窗口仍至少保留最近一轮（2 条）", 2, result.activeMessages.size)
    }

    @Test
    fun testDatabaseMigration30To31Registered() {
        val migration = com.aiassistant.data.local.AppDatabase.MIGRATION_30_31
        assertNotNull("MIGRATION_30_31 必须存在", migration)
        assertEquals(30, migration.startVersion)
        assertEquals(31, migration.endVersion)
    }

    @Test
    fun testDatabaseMigration31To32Registered() {
        val migration = com.aiassistant.data.local.AppDatabase.MIGRATION_31_32
        assertNotNull("MIGRATION_31_32 必须存在", migration)
        assertEquals(31, migration.startVersion)
        assertEquals(32, migration.endVersion)
    }
}
