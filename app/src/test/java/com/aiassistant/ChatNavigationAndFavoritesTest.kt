package com.aiassistant

import com.aiassistant.domain.model.Message
import com.aiassistant.ui.components.SideAnchorItem
import com.aiassistant.ui.components.buildAnchorSearchPreview
import com.aiassistant.ui.components.filterAnchorItems
import com.aiassistant.ui.screens.chat.buildChatAnchorItems
import com.aiassistant.ui.screens.chat.buildChatSearchAnchorItems
import com.aiassistant.ui.screens.chat.buildDisplayMessages
import com.aiassistant.ui.screens.chat.groupDisplayAnchor
import com.aiassistant.ui.screens.chat.resolveChatListAnchor
import com.aiassistant.utils.ChatScrollAnchor
import com.aiassistant.utils.FavoriteMessage
import com.aiassistant.utils.decodeChatScrollAnchor
import com.aiassistant.utils.encodeChatScrollAnchor
import com.aiassistant.utils.favoritesIn
import com.aiassistant.utils.toggledFavorites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 右侧输入列表（编号 / 搜索）、消息收藏与阅读位置记录。
 *
 * 回归背景（v2.8.38）：
 * - 输入列表需要“1 起编号 + 按输入全文搜索 + 点击跳转”；
 * - 收藏需要跨页面共享、按对话过滤并可跳回消息；
 * - 阅读位置按会话持久化，重进对话时恢复，列表底部不保留位置。
 */
class ChatNavigationAndFavoritesTest {

    // ==================== 输入列表编号与搜索 ====================

    @Test
    fun buildChatAnchorItems_numbersInputsFromOneAndKeepsFullSearchText() {
        val items = buildDisplayMessages(
            listOf(
                message(id = 1L, role = "user", content = "第一问：先介绍一下项目背景", createdAt = 100L),
                message(id = 2L, role = "assistant", content = "背景如下", createdAt = 200L),
                message(id = 3L, role = "user", content = "第二问：这个搜索功能怎么用", createdAt = 300L),
                message(id = 4L, role = "assistant", content = "可以这样用", createdAt = 400L),
                message(id = 5L, role = "user", content = "第三问：收藏在哪里查看", createdAt = 500L)
            ),
            emptyMap()
        )

        val anchors = buildChatAnchorItems(items)

        assertEquals(3, anchors.size)
        assertEquals(listOf(1, 2, 3), anchors.map { it.number })
        assertEquals("编号必须对应从 1 开始的提问顺序", listOf(0, 2, 4), anchors.map { it.itemIndex })
        assertEquals("第三问：收藏在哪里查看", anchors[2].searchText)
        assertTrue("标题只是预览，搜索文本保留完整输入", anchors[2].searchText!!.contains("收藏在哪里查看"))
    }

    @Test
    fun filterAnchorItems_matchesFullTextIgnoringCaseAndBlankQuery() {
        val items = listOf(
            SideAnchorItem("第一问", 0, number = 1, searchText = "请帮我优化 SQL 查询性能"),
            SideAnchorItem("第二问", 2, number = 2, searchText = "再解释一下索引下推"),
            SideAnchorItem("第三问", 4, number = 3, searchText = "最后总结全文")
        )

        assertTrue("空查询返回全部条目", filterAnchorItems(items, "   ").size == 3)
        assertEquals(listOf(1), filterAnchorItems(items, "sql").map { it.number })
        assertEquals(listOf(2), filterAnchorItems(items, "一下").map { it.number })
        assertEquals(listOf(3), filterAnchorItems(items, "总结").map { it.number })
        assertEquals("空格分隔多词需全部命中", listOf(1), filterAnchorItems(items, "sql 性能").map { it.number })
        assertTrue("多词中任一词未命中时返回空", filterAnchorItems(items, "sql 索引").isEmpty())
        assertTrue("没有任何命中时返回空列表", filterAnchorItems(items, "不存在的内容").isEmpty())
    }

    @Test
    fun buildChatSearchAnchorItems_indexesQuestionsRepliesAndThinkingWithDirectIndices() {
        val items = buildDisplayMessages(
            listOf(
                message(id = 1L, role = "user", content = "第一问：请解释量子纠缠", createdAt = 100L),
                Message(
                    id = 2L,
                    conversationId = 1L,
                    role = "assistant",
                    content = "量子纠缠是一种非局域关联现象，贝尔不等式实验验证了它。",
                    thinkingContent = "先从爱因斯坦 EPR 佯谬说起",
                    createdAt = 200L
                ),
                message(id = 3L, role = "user", content = "第二问：那和量子通信有什么关系？", createdAt = 300L),
                message(id = 4L, role = "assistant", content = "量子密钥分发可以利用纠缠态检测窃听。", createdAt = 400L)
            ),
            emptyMap()
        )

        val promptOnly = buildChatAnchorItems(items)
        val searchAll = buildChatSearchAnchorItems(items)

        assertEquals(2, promptOnly.size)
        assertEquals(listOf("提问", "提问"), promptOnly.map { it.roleLabel })

        assertEquals("对话搜索必须覆盖提问与回复全部条目", 4, searchAll.size)
        assertEquals(listOf(0, 1, 2, 3), searchAll.map { it.itemIndex })
        assertEquals(listOf("提问", "回复", "提问", "回复"), searchAll.map { it.roleLabel })

        val replyHits = filterAnchorItems(searchAll, "贝尔不等式")
        assertEquals(listOf(1), replyHits.map { it.itemIndex })
        assertEquals("回复", replyHits.single().roleLabel)

        val thinkingHits = filterAnchorItems(searchAll, "EPR")
        assertEquals(listOf(1), thinkingHits.map { it.itemIndex })

        val bothHits = filterAnchorItems(searchAll, "量子")
        assertEquals(listOf(0, 1, 2, 3), bothHits.map { it.itemIndex })

        val longReply = SideAnchorItem(
            title = "长回复标题",
            itemIndex = 3,
            searchText = "前面有很多铺垫内容用来测试上下文截取效果，真正需要命中的核心关键词是星河档案馆，后面也有较长补充说明文字。",
            roleLabel = "回复"
        )
        val snippet = buildAnchorSearchPreview(longReply, "星河档案馆", maxChars = 24)
        assertTrue("搜索预览必须包含命中词", snippet.contains("星河档案馆"))
        assertTrue("超长正文预览应围绕命中词截断", snippet.length <= 28)
    }

    // ==================== 收藏 ====================

    @Test
    fun toggledFavorites_addsThenRemovesByMessageId() {
        val entry = favorite(conversationId = 7L, messageId = 101L, favoritedAt = 1000L)
        val added = toggledFavorites(emptyList(), entry)
        assertEquals(listOf(entry), added)

        val removed = toggledFavorites(added, entry)
        assertTrue("同一条消息再次切换必须移除收藏", removed.isEmpty())
    }

    @Test
    fun toggledFavorites_newestFirstAndIsolatedByConversation() {
        val first = favorite(conversationId = 7L, messageId = 101L, favoritedAt = 1000L)
        val second = favorite(conversationId = 8L, messageId = 201L, favoritedAt = 2000L)
        val third = favorite(conversationId = 7L, messageId = 301L, favoritedAt = 3000L)

        val all = toggledFavorites(toggledFavorites(toggledFavorites(emptyList(), first), second), third)

        assertEquals(listOf(third, second, first), all)
        assertEquals("按对话过滤只保留本对话收藏，最新在前", listOf(third, first), favoritesIn(all, 7L))
        assertEquals(listOf(second), favoritesIn(all, 8L))
        assertTrue(favoritesIn(all, 9L).isEmpty())
    }

    // ==================== 阅读位置 ====================

    @Test
    fun encodeDecodeChatScrollAnchor_roundTripsGroupAndFallbackIds() {
        val grouped = ChatScrollAnchor(groupId = "turn_12_user", messageId = 34L, offset = 96)
        assertEquals(grouped, decodeChatScrollAnchor(encodeChatScrollAnchor(grouped)))

        val ungrouped = ChatScrollAnchor(groupId = null, messageId = 56L, offset = 0)
        assertEquals(ungrouped, decodeChatScrollAnchor(encodeChatScrollAnchor(ungrouped)))
    }

    @Test
    fun decodeChatScrollAnchor_rejectsMalformedOrNegativeValues() {
        assertNull(decodeChatScrollAnchor(""))
        assertNull(decodeChatScrollAnchor("v0|g=-|m=1|o=0"))
        assertNull(decodeChatScrollAnchor("v1|g=-|m=abc|o=0"))
        assertNull(decodeChatScrollAnchor("v1|g=-|m=0|o=0"))
        val clamped = decodeChatScrollAnchor("v1|g=-|m=5|o=-20")
        assertEquals(0, clamped?.offset)
    }

    @Test
    fun resolveChatListAnchor_findsSameRowOrFallsBackToDefault() {
        val keys = listOf("chat_group:turn_1_user", "chat_reply:chat_group:turn_1_user", "chat_message:9_1_user")
        assertEquals(1, resolveChatListAnchor(keys, "chat_reply:chat_group:turn_1_user"))
        assertEquals("被删除的行找不到时交给列表默认锚定", -1, resolveChatListAnchor(keys, "chat_group:deleted"))
        assertEquals(-1, resolveChatListAnchor(keys, null))
    }

    // ==================== 分页删除后的轮次定位 ====================

    @Test
    fun groupDisplayAnchor_pairedGroupsShareEarliestAnchor() {
        val anchors = mapOf(
            "turn_5_user" to 9000L,
            "turn_5_assistant" to 200L
        )
        assertEquals(200L, groupDisplayAnchor("turn_5_user", anchors))
        assertEquals(200L, groupDisplayAnchor("turn_5_assistant", anchors))
        assertEquals(
            "非成对分组只使用自身时间",
            5000L,
            groupDisplayAnchor("reply_9", mapOf("reply_9" to 5000L))
        )
    }

    @Test
    fun buildDisplayMessages_keepsEditedTurnInPlaceAfterDeletingOriginalVariant() {
        // 编辑重发后的轮次：原始分页在 100/200，重发分页在 9000/9100，之后还有一轮 5000/5100
        val userV1 = message(1L, "user", "原始输入", 100L, groupId = "turn_1_user", index = 1)
        val assistantV1 = message(2L, "assistant", "原始回复", 200L, groupId = "turn_1_assistant", index = 1)
        val laterUser = message(20L, "user", "之后的输入", 5000L)
        val laterAssistant = message(21L, "assistant", "之后的回复", 5100L)
        val userV2 = message(10L, "user", "重发输入", 9000L, groupId = "turn_1_user", index = 2)
        val assistantV2 = message(11L, "assistant", "重发回复", 9100L, groupId = "turn_1_assistant", index = 2)

        val beforeDelete = buildDisplayMessages(
            listOf(userV1, assistantV1, laterUser, laterAssistant, userV2, assistantV2),
            mapOf("turn_1_user" to 2, "turn_1_assistant" to 2)
        )
        assertEquals(
            listOf("turn_1_user", "turn_1_assistant", null, null),
            beforeDelete.map { it.groupId }
        )

        // 删除用户原始分页（第 1 页）：整轮必须仍留在原位，而不是跳到会话末尾
        val afterUserDelete = buildDisplayMessages(
            listOf(assistantV1, laterUser, laterAssistant, userV2, assistantV2),
            mapOf("turn_1_user" to 2, "turn_1_assistant" to 2)
        )
        assertEquals(
            listOf("turn_1_user", "turn_1_assistant", null, null),
            afterUserDelete.map { it.groupId }
        )
        assertEquals("重发分页仍显示在新输入位置", "重发输入", afterUserDelete.first().message.content)

        // 删除助手原始分页（第 1 页）：同样留在原位
        val afterAssistantDelete = buildDisplayMessages(
            listOf(userV1, laterUser, laterAssistant, userV2, assistantV2),
            mapOf("turn_1_user" to 2, "turn_1_assistant" to 2)
        )
        assertEquals(
            listOf("turn_1_user", "turn_1_assistant", null, null),
            afterAssistantDelete.map { it.groupId }
        )
    }

    @Test
    fun buildDisplayMessages_keepsUserBeforeAssistantWhenAnchorTimesTie() {
        val user = message(10L, "user", "重发输入", 9000L, groupId = "turn_1_user", index = 2)
        val assistant = message(11L, "assistant", "重发回复", 9100L, groupId = "turn_1_assistant", index = 2)

        val items = buildDisplayMessages(listOf(assistant, user), emptyMap())

        assertEquals("同一轮锚点时间相同时输入必须排在回复之前", listOf("turn_1_user", "turn_1_assistant"), items.map { it.groupId })
    }

    private fun message(
        id: Long,
        role: String,
        content: String,
        createdAt: Long,
        groupId: String? = null,
        index: Int = 1
    ): Message = Message(
        id = id,
        conversationId = 1L,
        role = role,
        content = content,
        variantGroupId = groupId,
        variantIndex = index,
        createdAt = createdAt
    )

    private fun favorite(conversationId: Long, messageId: Long, favoritedAt: Long): FavoriteMessage =
        FavoriteMessage(
            conversationId = conversationId,
            messageId = messageId,
            role = "user",
            content = "收藏内容 $messageId",
            messageCreatedAt = favoritedAt,
            favoritedAt = favoritedAt
        )
}
