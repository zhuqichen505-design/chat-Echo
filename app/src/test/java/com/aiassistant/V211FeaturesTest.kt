package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.Attachment
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.QueuedMessage
import com.aiassistant.utils.SmartMemoryExtractor
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class V211FeaturesTest {

    // 1. 测试分支命名自增机制（解决 XX(分支1) 循环问题）
    @Test
    fun testExtractRootBaseTitle() {
        assertEquals("新对话", AiRepository.extractRootBaseTitle("新对话"))
        assertEquals("科幻剧本讨论", AiRepository.extractRootBaseTitle("科幻剧本讨论 (分支 1)"))
        assertEquals("科幻剧本讨论", AiRepository.extractRootBaseTitle("科幻剧本讨论（分支1）"))
        assertEquals("科幻剧本讨论", AiRepository.extractRootBaseTitle("科幻剧本讨论 (分支)"))
        assertEquals("科幻剧本讨论", AiRepository.extractRootBaseTitle("科幻剧本讨论（分支）"))
        assertEquals("历史探索", AiRepository.extractRootBaseTitle("历史探索（分支 1）（分支 2）"))
        assertEquals("历史探索", AiRepository.extractRootBaseTitle("历史探索 (分支 1) (分支 3)"))
    }

    @Test
    fun testCalculateNextBranchTitle() {
        val root = "星际迷航剧本"

        // 初始无分支
        val title1 = AiRepository.calculateNextBranchTitle(root, emptyList())
        assertEquals("星际迷航剧本 (分支 1)", title1)

        // 存在无编号分支
        val title2 = AiRepository.calculateNextBranchTitle(root, listOf("星际迷航剧本 (分支)"))
        assertEquals("星际迷航剧本 (分支 2)", title2)

        // 存在 XX（分支1）
        val title3 = AiRepository.calculateNextBranchTitle(root, listOf("星际迷航剧本（分支1）"))
        assertEquals("星际迷航剧本 (分支 2)", title3)

        // 存在多个中文和英文分支
        val existing = listOf(
            "星际迷航剧本 (分支 1)",
            "星际迷航剧本（分支2）",
            "星际迷航剧本 (分支 3)",
            "无关标题"
        )
        val title4 = AiRepository.calculateNextBranchTitle(root, existing)
        assertEquals("星际迷航剧本 (分支 4)", title4)
    }

    // 2. 测试记忆提取与优化（[]、注意关键词、提炼加工）
    @Test
    fun testSmartMemoryExtractorBracketContents() {
        val candidate1 = SmartMemoryExtractor.extractCandidate("[用户喜欢喝无糖乌龙茶，对坚果过敏]")
        assertNotNull("应提取出括号内的偏好记忆", candidate1)
        assertTrue("应包含偏好内容", candidate1!!.distilledContent.contains("用户喜欢喝无糖乌龙茶"))

        val candidate2 = SmartMemoryExtractor.extractCandidate("【设定：主角名为林渊，代号破晓】")
        assertNotNull("应提取出【】括号内的设定记忆", candidate2)
        assertTrue("应包含设定内容", candidate2!!.distilledContent.contains("主角名为林渊"))

        // 测试剧情推进/瞬态动作/时间场景过渡等不应作为记忆提取（需求 1）
        val plotAction1 = SmartMemoryExtractor.extractCandidate("[他叹了一口气，转身走进了暴风雨中]")
        assertNull("瞬态动作描写绝不应提取为记忆", plotAction1)

        val plotAction2 = SmartMemoryExtractor.extractCandidate("[一小时后，飞船降落在火星基地]")
        assertNull("时间场景过渡绝不应提取为记忆", plotAction2)

        val plotAction3 = SmartMemoryExtractor.extractCandidate("[场景切换到议会控制室]")
        assertNull("分镜镜头指示绝不应提取为记忆", plotAction3)
    }

    @Test
    fun testSmartMemoryExtractorNoticeKeywords() {
        val candidate1 = SmartMemoryExtractor.extractCandidate("注意：请始终使用简洁的格式回答，不要输出冗长解释")
        assertNotNull("应提取注意关键词后面的约束", candidate1)
        assertTrue("应提取格式约束", candidate1!!.distilledContent.contains("简洁的格式回答"))

        val candidate2 = SmartMemoryExtractor.extractCandidate("特别注意：回答时主角性格必须保持高冷克制")
        assertNotNull("应提取特别注意关键词后面的约束", candidate2)
        assertTrue("应提取主角性格约束", candidate2!!.distilledContent.contains("保持高冷克制"))
    }

    @Test
    fun testRefineMemoryContentClassification() {
        val (pref, cat1) = SmartMemoryExtractor.refineMemoryContent("我最喜欢用暗黑模式和紧凑布局")
        assertEquals("PREFERENCE", cat1)
        assertTrue(pref.startsWith("用户偏好："))

        val (constraint, cat2) = SmartMemoryExtractor.refineMemoryContent("禁止在回答中包含markdown代码块外的解释")
        assertEquals("PREFERENCE", cat2)
        assertTrue(constraint.startsWith("行为约束："))

        val (fact, cat3) = SmartMemoryExtractor.refineMemoryContent("服务器IP地址已更换至东京节点")
        assertEquals("FACT", cat3)
        assertTrue(fact.startsWith("重要事实："))
    }

    // 3. 测试网络波动异常判断
    @Test
    fun testNetworkFluctuationException() {
        assertTrue(AiRepository.isNetworkFluctuationException(SocketTimeoutException("Read timed out")))
        assertTrue(AiRepository.isNetworkFluctuationException(ConnectException("Failed to connect")))
        assertTrue(AiRepository.isNetworkFluctuationException(SocketException("Connection reset")))
        assertTrue(AiRepository.isNetworkFluctuationException(SocketException("Software caused connection abort")))
        assertTrue(AiRepository.isNetworkFluctuationException(UnknownHostException("api.openai.com")))
        assertTrue(AiRepository.isNetworkFluctuationException(SSLHandshakeException("Handshake failed")))
        assertTrue(AiRepository.isNetworkFluctuationException(IOException("unexpected end of stream on http://...")))

        // 业务异常或普通空指针不应判定为网络波动
        assertFalse(AiRepository.isNetworkFluctuationException(IllegalArgumentException("Invalid API key")))
        assertFalse(AiRepository.isNetworkFluctuationException(NullPointerException("Missing field")))
    }

    // 4. 测试排队消息数据模型与操作
    @Test
    fun testQueuedMessageOperations() {
        val qm1 = QueuedMessage(id = "1", content = "第一条排队消息", attachments = emptyList(), timestamp = 1000L)
        val qm2 = QueuedMessage(id = "2", content = "第二条排队消息", attachments = emptyList(), timestamp = 2000L)

        val queue = mutableListOf(qm1, qm2)
        assertEquals(2, queue.size)

        // 调序：第二条移到第一位
        val item = queue.removeAt(1)
        queue.add(0, item)
        assertEquals("2", queue[0].id)
        assertEquals("1", queue[1].id)

        // 编辑
        val edited = queue[0].copy(content = "修改后的第二条排队消息")
        queue[0] = edited
        assertEquals("修改后的第二条排队消息", queue[0].content)
    }

    // 5. 测试多版本分支数据保留
    @Test
    fun testBranchMultiVariantPreservationLogic() {
        val parentId = 1L
        val variantGroupId = "group_round_2"

        val msg1 = Message(id = 1, conversationId = parentId, role = "user", content = "第一轮提问", createdAt = 1000L)
        val msg2v1 = Message(id = 2, conversationId = parentId, role = "assistant", content = "第一轮回答v1", variantGroupId = variantGroupId, variantIndex = 1, createdAt = 2000L)
        val msg2v2 = Message(id = 3, conversationId = parentId, role = "assistant", content = "第一轮回答v2（重新生成）", variantGroupId = variantGroupId, variantIndex = 2, createdAt = 2050L)
        val msg2v3 = Message(id = 4, conversationId = parentId, role = "assistant", content = "第一轮回答v3（改写）", variantGroupId = variantGroupId, variantIndex = 3, createdAt = 2100L)

        val allParentMessages = listOf(msg1, msg2v1, msg2v2, msg2v3)

        // 假设用户当前停留在 v2 并发起分支
        val displaySlice = listOf(msg1, msg2v2)
        val targetGroupIds = displaySlice.mapNotNull { it.variantGroupId }.toSet()

        val messagesToCopy = allParentMessages.filter { msg ->
            (!msg.variantGroupId.isNullOrBlank() && msg.variantGroupId in targetGroupIds) ||
            displaySlice.any { it.id == msg.id }
        }

        // 验证：分支不仅包含了 msg1，还包含了该轮次全部 3 个变体！
        assertEquals(4, messagesToCopy.size)
        assertTrue(messagesToCopy.contains(msg2v1))
        assertTrue(messagesToCopy.contains(msg2v2))
        assertTrue(messagesToCopy.contains(msg2v3))
    }

    // 6. 测试 v2.1.1 更新日志完整性
    @Test
    fun testV211CurrentVersionUserUpdatesCompleteness() {
        val updates = com.aiassistant.ui.screens.settings.V211UserUpdates
        assertFalse("本次更新日志列表不得为空", updates.isEmpty())
        assertTrue("必须包含当前版本主要更新项 (>=7项)", updates.size >= 7)
        assertTrue("必须说明分支命名自增", updates.any { it.contains("分支命名") && it.contains("自增") })
        assertTrue("必须说明记忆提取优化", updates.any { it.contains("记忆") && it.contains("中括号") })
        assertTrue("必须说明模型配置清空", updates.any { it.contains("模型配置快速清空") })
        assertTrue("必须说明多Key优先级", updates.any { it.contains("多 Key 优先级") })
        assertTrue("必须说明网络波动容错", updates.any { it.contains("网络波动") })
        assertTrue("必须说明消息排队功能", updates.any { it.contains("消息排队") })
        assertTrue("必须说明分支多版本保留", updates.any { it.contains("分支创建完整保留多版本") })
    }
}
