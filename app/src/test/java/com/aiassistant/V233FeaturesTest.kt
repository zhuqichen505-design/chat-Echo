package com.aiassistant

import com.aiassistant.ui.screens.chat.ContextUsageUiState
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V233UserUpdates
import com.aiassistant.utils.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class V233FeaturesTest {

    private val gson = Gson()

    @Test
    fun testV233UserUpdatesCompleteness() {
        assertEquals("V2.3.3 用户更新日志数量应为 7 项完整对齐用户需求", 7, V233UserUpdates.size)
        assertTrue("V233UserUpdates 必须包含更新条目", V233UserUpdates.isNotEmpty())
        assertTrue("必须包含时间线断点水线记录更新", V233UserUpdates.any { it.contains("断点水线") })
        assertTrue("必须包含结合原有时间线智能优化更新", V233UserUpdates.any { it.contains("结合原有时间线") })
        assertTrue("必须包含更新摘要与主动压缩错位修复更新", V233UserUpdates.any { it.contains("更新摘要") && it.contains("主动压缩") })
        assertTrue("必须包含双卡片设计更新", V233UserUpdates.any { it.contains("双卡片") })
        assertTrue("必须包含功能职责明晰更新", V233UserUpdates.any { it.contains("功能职责明晰") || it.contains("不裁剪任何原文") })
    }

    @Test
    fun testTimelineReconcileCheckpointSerialization() {
        val checkpoint = TimelineReconcileCheckpoint(
            conversationId = 1001L,
            lastReconciledMessageId = 42L,
            lastReconciledMessageIndex = 25,
            totalMessageCountAtReconciliation = 25,
            storyTimeAtReconciliation = "第 3 天·夜",
            nodeCountAtReconciliation = 8,
            timestamp = 1710000000000L
        )

        val json = gson.toJson(checkpoint)
        val deserialized = gson.fromJson(json, TimelineReconcileCheckpoint::class.java)

        assertEquals(1001L, deserialized.conversationId)
        assertEquals(42L, deserialized.lastReconciledMessageId)
        assertEquals(25, deserialized.lastReconciledMessageIndex)
        assertEquals(25, deserialized.totalMessageCountAtReconciliation)
        assertEquals("第 3 天·夜", deserialized.storyTimeAtReconciliation)
        assertEquals(8, deserialized.nodeCountAtReconciliation)
        assertEquals(1710000000000L, deserialized.timestamp)
    }

    @Test
    fun testContextUsageUiStateIndependence() {
        val initial = ContextUsageUiState()
        assertFalse("初始压缩状态应为 false", initial.isCompressing)
        assertFalse("初始生成摘要状态应为 false", initial.isGeneratingSummary)

        // 仅触发生成摘要
        val generatingSummary = initial.copy(isGeneratingSummary = true, statusMessage = "正在提炼滚动摘要...")
        assertTrue("生成摘要中状态应为 true", generatingSummary.isGeneratingSummary)
        assertFalse("生成摘要时不应触发压缩中状态", generatingSummary.isCompressing)

        // 仅触发主动压缩
        val compressing = initial.copy(isCompressing = true, statusMessage = "正在压缩上下文...")
        assertTrue("压缩中状态应为 true", compressing.isCompressing)
        assertFalse("主动压缩时不应触发生成摘要状态", compressing.isGeneratingSummary)
    }

    @Test
    fun testIncrementalTimelineMessageSlicing() {
        data class SimpleMessage(val id: Long, val content: String)

        val allMessages = (1L..30L).map { SimpleMessage(it, "Message $it") }
        val checkpoint = TimelineReconcileCheckpoint(
            conversationId = 1L,
            lastReconciledMessageId = 20L,
            lastReconciledMessageIndex = 20,
            totalMessageCountAtReconciliation = 20
        )

        // 结合断点切片后续增量对话
        val subsequentMessages = allMessages.filter { it.id > checkpoint.lastReconciledMessageId }

        assertEquals(10, subsequentMessages.size)
        assertEquals(21L, subsequentMessages.first().id)
        assertEquals(30L, subsequentMessages.last().id)
    }

    @Test
    fun testTimelineReconcileResultWithCheckpointMetadata() {
        val result = TimelineReconcileResult(
            currentStoryTime = "第 5 天·清晨",
            events = mutableListOf(
                TimelineEventItem(timeTag = "第 1 天", content = "初始启程", category = TimelineCategory.PLOT_EVENT),
                TimelineEventItem(timeTag = "第 5 天·清晨", content = "抵达新城镇", category = TimelineCategory.PLOT_EVENT)
            ),
            lastProcessedMessageId = 55L,
            totalProcessedMessages = 30
        )

        assertEquals("第 5 天·清晨", result.currentStoryTime)
        assertEquals(2, result.events.size)
        assertEquals(55L, result.lastProcessedMessageId)
        assertEquals(30, result.totalProcessedMessages)
    }
}
