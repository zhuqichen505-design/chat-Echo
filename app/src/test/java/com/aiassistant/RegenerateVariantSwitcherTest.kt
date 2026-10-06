package com.aiassistant

import com.aiassistant.domain.model.Message
import com.aiassistant.ui.screens.chat.buildDisplayMessages
import com.aiassistant.ui.screens.chat.isGeneratingAnchorHostItem
import com.aiassistant.ui.screens.chat.isStreamingBranchHostItem
import org.junit.Assert.*
import org.junit.Test

class RegenerateVariantSwitcherTest {

    @Test
    fun testBuildDisplayMessages_withDuplicateIndices_normalizesAndShowsSwitcher() {
        val userMsg = Message(
            id = 1L,
            conversationId = 100L,
            role = "user",
            content = "Hello",
            createdAt = 1000L
        )
        // 模拟历史数据异常或并发导致的相同 variantIndex
        val reply1 = Message(
            id = 2L,
            conversationId = 100L,
            role = "assistant",
            content = "First response",
            variantGroupId = "reply_2",
            variantIndex = 1,
            createdAt = 2000L
        )
        val reply2 = Message(
            id = 3L,
            conversationId = 100L,
            role = "assistant",
            content = "Second response (regenerated)",
            variantGroupId = "reply_2",
            variantIndex = 1, // 重复的 index = 1
            createdAt = 3000L
        )

        val displayItems = buildDisplayMessages(listOf(userMsg, reply1, reply2), emptyMap())

        assertEquals("显示消息项数量应为2（1条用户消息 + 1条多版本聚合助手消息）", 2, displayItems.size)
        val assistantItem = displayItems[1]
        assertNotNull("多版本回复必须生成 variantInfo", assistantItem.variantInfo)
        assertEquals("变体总数应为 2", 2, assistantItem.variantInfo?.total)
        assertEquals("可用索引列表应被规整为 [1, 2]", listOf(1, 2), assistantItem.variantInfo?.availableIndices)
        // 默认显示最新的一版 (index = 2)
        assertEquals("默认选中的内容应为第二版回复", "Second response (regenerated)", assistantItem.message.content)
        assertEquals("当前选中索引应为 2", 2, assistantItem.variantInfo?.currentIndex)

        // 切换回第 1 版
        val displayItemsV1 = buildDisplayMessages(listOf(userMsg, reply1, reply2), mapOf("reply_2" to 1))
        val assistantItemV1 = displayItemsV1[1]
        assertEquals("切换至索引 1 应显示第一版回复", "First response", assistantItemV1.message.content)
        assertEquals("当前选中索引应为 1", 1, assistantItemV1.variantInfo?.currentIndex)
    }

    @Test
    fun testBuildDisplayMessages_withNormalIncrementingIndices_allowsSwitching() {
        val userMsg = Message(
            id = 10L,
            conversationId = 200L,
            role = "user",
            content = "Write a poem",
            createdAt = 1000L
        )
        val replyV1 = Message(
            id = 11L,
            conversationId = 200L,
            role = "assistant",
            content = "Roses are red",
            variantGroupId = "group_poem",
            variantIndex = 1,
            createdAt = 2000L
        )
        val replyV2 = Message(
            id = 12L,
            conversationId = 200L,
            role = "assistant",
            content = "Violets are blue",
            variantGroupId = "group_poem",
            variantIndex = 2,
            createdAt = 3000L
        )
        val replyV3 = Message(
            id = 13L,
            conversationId = 200L,
            role = "assistant",
            content = "Sugar is sweet",
            variantGroupId = "group_poem",
            variantIndex = 3,
            createdAt = 4000L
        )

        val messages = listOf(userMsg, replyV1, replyV2, replyV3)

        // 默认状态（未指定选择）：显示最新第 3 版
        val defaultItems = buildDisplayMessages(messages, emptyMap())
        assertEquals(2, defaultItems.size)
        val defaultAssistant = defaultItems[1]
        assertEquals("Sugar is sweet", defaultAssistant.message.content)
        assertEquals(3, defaultAssistant.variantInfo?.total)
        assertEquals(3, defaultAssistant.variantInfo?.currentIndex)
        assertEquals(listOf(1, 2, 3), defaultAssistant.variantInfo?.availableIndices)

        // 用户点击切换到第 2 版
        val itemsV2 = buildDisplayMessages(messages, mapOf("group_poem" to 2))
        val assistantV2 = itemsV2[1]
        assertEquals("Violets are blue", assistantV2.message.content)
        assertEquals(2, assistantV2.variantInfo?.currentIndex)

        // 用户点击切换到第 1 版
        val itemsV1 = buildDisplayMessages(messages, mapOf("group_poem" to 1))
        val assistantV1 = itemsV1[1]
        assertEquals("Roses are red", assistantV1.message.content)
        assertEquals(1, assistantV1.variantInfo?.currentIndex)
    }

    @Test
    fun testRegenerateNextIndexCalculation_strictlyIncrements() {
        // 场景 1: 单条消息准备重新生成
        val v1 = Message(id = 1L, conversationId = 1L, role = "assistant", content = "A", variantGroupId = "g1", variantIndex = 1)
        val list1 = listOf(v1)
        val group1 = list1.filter { it.variantGroupId == "g1" || it.id == v1.id }
        val maxExisting1 = group1.maxOfOrNull { it.variantIndex } ?: 1
        val nextIndex1 = maxOf(group1.size, maxExisting1) + 1
        assertEquals("从 1 条生成第 2 版时，nextIndex 应为 2", 2, nextIndex1)

        // 场景 2: 已有 2 条正常自增的消息
        val v2 = Message(id = 2L, conversationId = 1L, role = "assistant", content = "B", variantGroupId = "g1", variantIndex = 2)
        val list2 = listOf(v1, v2)
        val group2 = list2.filter { it.variantGroupId == "g1" || it.id == v2.id }
        val maxExisting2 = group2.maxOfOrNull { it.variantIndex } ?: 1
        val nextIndex2 = maxOf(group2.size, maxExisting2) + 1
        assertEquals("从 2 条正常消息生成第 3 版时，nextIndex 应为 3", 3, nextIndex2)

        // 场景 3: 历史异常数据（已有两条消息，但两者的 variantIndex 都是 1）
        val corruptedV2 = Message(id = 3L, conversationId = 1L, role = "assistant", content = "C", variantGroupId = "g1", variantIndex = 1)
        val listCorrupted = listOf(v1, corruptedV2)
        val groupCorrupted = listCorrupted.filter { it.variantGroupId == "g1" || it.id == corruptedV2.id }
        val maxExistingCorrupted = groupCorrupted.maxOfOrNull { it.variantIndex } ?: 1
        val nextIndexCorrupted = maxOf(groupCorrupted.size, maxExistingCorrupted) + 1
        assertEquals("历史异常相同 index 数据下，nextIndex 依然基于 group.size 自增为 3", 3, nextIndexCorrupted)
    }

    @Test
    fun testContextExcludesRegeneratingVariantGroupId() {
        val userPrompt = Message(id = 1L, conversationId = 1L, role = "user", content = "请帮我写个大纲")
        val oldReply = Message(id = 2L, conversationId = 1L, role = "assistant", content = "这是旧的错误/不满意大纲", variantGroupId = "reply_2", variantIndex = 1)
        val otherHistory = Message(id = 3L, conversationId = 1L, role = "user", content = "前置背景")

        val historyMessages = listOf(otherHistory, userPrompt, oldReply)
        val assistantVariantGroupId = "reply_2"

        // 模拟 AiRepository 中的上下文过滤逻辑
        val contextMessages = historyMessages.filter { msg ->
            assistantVariantGroupId.isBlank() || msg.variantGroupId != assistantVariantGroupId
        }

        assertFalse("正在重新生成的回复（匹配 groupId）不得被加入请求上下文", contextMessages.any { it.variantGroupId == "reply_2" })
        assertEquals(2, contextMessages.size)
        assertEquals("前置背景", contextMessages[0].content)
        assertEquals("请帮我写个大纲", contextMessages[1].content)
    }

    // ==================== 流式气泡内联挂载点判定（isStreamingBranchHostItem） ====================
    // 回归背景：旧实现 `displayItem.groupId == pairedVariantGroupId(streamingBranchGroupId)` 在
    // streamingBranchGroupId 为 "reply_*"（重生成无分组消息）时退化为 null == null，
    // 所有未分组消息项全部命中，造成多份相同回复同时流式输出且位置错乱。

    @Test
    fun testStreamingHost_replyGroupMustNotMatchUngroupedItems() {
        // 重生成无分组消息：streamingBranchGroupId = "reply_2"，列表中存在未分组的 user/assistant 消息
        assertFalse(
            "reply_* 流式分支不得把未分组消息项判定为挂载点（null == null 回归）",
            isStreamingBranchHostItem(itemGroupId = null, streamingBranchGroupId = "reply_2", messageId = 1L)
        )
        assertFalse(
            "reply_* 流式分支不得把未分组 assistant 消息项判定为挂载点",
            isStreamingBranchHostItem(itemGroupId = null, streamingBranchGroupId = "reply_2", messageId = 2L)
        )
        // 无任何挂载点时应由底部兜底气泡（streaming_assistant_message）承接，保证仅一份流式回复
    }

    @Test
    fun testStreamingHost_pairedUserGroupMatches() {
        // 编辑重发：streamingBranchGroupId = "turn_5_assistant"，配对 user 分组项 turn_5_user 必须命中
        assertTrue(
            "配对 user 分组项（turn_5_user）应作为内联挂载点",
            isStreamingBranchHostItem(itemGroupId = "turn_5_user", streamingBranchGroupId = "turn_5_assistant", messageId = 7L)
        )
        // 反向配对同样成立
        assertTrue(
            "配对 assistant 分组项（turn_5_assistant）应作为内联挂载点",
            isStreamingBranchHostItem(itemGroupId = "turn_5_assistant", streamingBranchGroupId = "turn_5_user", messageId = 7L)
        )
    }

    @Test
    fun testStreamingHost_turnPrefixMatchesUngroupedUserMessage() {
        // 未分组 user 消息（id=5）：groupId 嵌入 turn_ 前缀时命中，气泡挂在正确位置
        assertTrue(
            "turn_ 前缀内嵌消息 id 的未分组消息项应作为内联挂载点",
            isStreamingBranchHostItem(itemGroupId = null, streamingBranchGroupId = "turn_5_assistant", messageId = 5L)
        )
        assertFalse(
            "id 不匹配的未分组消息项不得作为挂载点",
            isStreamingBranchHostItem(itemGroupId = null, streamingBranchGroupId = "turn_5_assistant", messageId = 9L)
        )
        // groupId 相同但非成对命名（如自定义分支组）不通过 paired 判定，只能靠 turn_ 前缀
        assertFalse(
            "groupId 非配对命名时不得命中 paired 判定",
            isStreamingBranchHostItem(itemGroupId = "branch_custom", streamingBranchGroupId = "turn_5_assistant", messageId = 9L)
        )
    }

    // ==================== 生成锚点宿主判定（isGeneratingAnchorHostItem，v2.6.5） ====================
    // 回归背景：同一位置出现多条回复（如错误占位 + 正在连接的流式回复）时，删除过去的一条
    // 曾导致流式回复跳到上方/下方变成额外回复。锚点 = 触发本轮生成的用户消息，删除其他
    // 回复不影响锚点，流式气泡钉在锚点之后，位置稳定。

    @Test
    fun testAnchorHost_matchesByIdForUngroupedUserMessage() {
        // 未分组 user 消息（id=5）触发本轮生成：id 直接命中
        assertTrue(
            "锚点用户消息项（id 命中）应作为内联挂载点",
            isGeneratingAnchorHostItem(
                itemGroupId = null, itemMessageId = 5L,
                anchorUserMessageId = 5L, anchorUserGroupId = null
            )
        )
        assertFalse(
            "非锚点消息项不得作为挂载点",
            isGeneratingAnchorHostItem(
                itemGroupId = null, itemMessageId = 9L,
                anchorUserMessageId = 5L, anchorUserGroupId = null
            )
        )
    }

    @Test
    fun testAnchorHost_matchesByGroupForVariantUserTurn() {
        // 编辑重发场景：锚点用户消息属于 turn_5_user 分组，选中的显示 variant 可能是旧版本
        // （id 不同），此时按分组 id 命中，挂载位置依然正确
        assertTrue(
            "锚点 user 分组项应作为内联挂载点",
            isGeneratingAnchorHostItem(
                itemGroupId = "turn_5_user", itemMessageId = 7L,
                anchorUserMessageId = 8L, anchorUserGroupId = "turn_5_user"
            )
        )
        assertFalse(
            "其他分组的其他消息项不得命中锚点判定",
            isGeneratingAnchorHostItem(
                itemGroupId = "turn_6_user", itemMessageId = 9L,
                anchorUserMessageId = 8L, anchorUserGroupId = "turn_5_user"
            )
        )
    }

    @Test
    fun testAnchorHost_nullAnchorNeverMatches() {
        // 无锚点（生成会话缺失/锚点未知）时不得命中任何宿主，交由底部兜底气泡承接
        assertFalse(
            "锚点为空时不得命中",
            isGeneratingAnchorHostItem(
                itemGroupId = null, itemMessageId = 5L,
                anchorUserMessageId = null, anchorUserGroupId = null
            )
        )
        assertFalse(
            "锚点 id 非法（<=0）时不得命中",
            isGeneratingAnchorHostItem(
                itemGroupId = null, itemMessageId = 5L,
                anchorUserMessageId = 0L, anchorUserGroupId = "turn_5_user"
            )
        )
    }
}
