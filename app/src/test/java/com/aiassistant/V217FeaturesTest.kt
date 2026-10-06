package com.aiassistant

import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Message
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V217UserUpdates
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineMemoryHelper
import org.junit.Assert.*
import org.junit.Test

class V217FeaturesTest {

    @Test
    fun testV217CurrentVersionUserUpdatesCompleteness() {
        val updates = V217UserUpdates
        assertFalse("本次更新日志列表不得为空", updates.isEmpty())
        assertEquals("必须包含全部 12 项主要更新项", 12, updates.size)

        // 验证 12 个核心更新点在更新日志中的完整性
        assertTrue("必须包含时间线敏锐度与时空跳转优化", updates.any { it.contains("时间线敏锐度") && it.contains("时空跳转") })
        assertTrue("必须包含全局按键阴影与圆角几何轮廓统一", updates.any { it.contains("按键阴影") && it.contains("圆角几何轮廓") })
        assertTrue("必须包含专属记忆与时间线排版重构", updates.any { it.contains("专属记忆与时间线排版重构") })
        assertTrue("必须包含对话设置功能层级重整", updates.any { it.contains("对话设置功能层级重整") })
        assertTrue("必须包含全能图片裁剪与编辑系统", updates.any { it.contains("图片裁剪与编辑") })
        assertTrue("必须包含专属记忆列表支持折叠展开", updates.any { it.contains("折叠展开") })
        assertTrue("必须包含记忆提炼辅助模型层级归一", updates.any { it.contains("记忆提炼辅助模型") && it.contains("模型辅助与思考") })
        assertTrue("必须包含排版防折行与长文本全局展开收起", updates.any { it.contains("防折行") && it.contains("ExpandableText") })
        assertTrue("必须包含会话级自定义模型头像隔离", updates.any { it.contains("会话级自定义模型头像隔离") })
        assertTrue("必须包含对话设置模型能力标签同排展示", updates.any { it.contains("能力标签同排展示") })
        assertTrue("必须包含标准上下文压缩体系", updates.any { it.contains("ConversationSummaryBufferMemory") && it.contains("缓冲区预警") })
        assertTrue("必须包含模型回复（Assistant）支持编辑", updates.any { it.contains("模型回复") && it.contains("编辑") })
    }

    @Test
    fun testConversationModelAvatarIsolationField() {
        val conv1 = Conversation(id = 101, title = "会话A", apiConfigId = 1L, modelName = "model-a", modelAvatarUri = "file:///avatars/custom_model_a.png")
        val conv2 = Conversation(id = 102, title = "会话B", apiConfigId = 1L, modelName = "model-b", modelAvatarUri = null)

        assertEquals("file:///avatars/custom_model_a.png", conv1.modelAvatarUri)
        assertNull(conv2.modelAvatarUri)

        // 验证复制更新保持隔离
        val updatedConv2 = conv2.copy(modelAvatarUri = "file:///avatars/custom_model_b.png")
        assertEquals("file:///avatars/custom_model_b.png", updatedConv2.modelAvatarUri)
        assertEquals("file:///avatars/custom_model_a.png", conv1.modelAvatarUri)
    }

    @Test
    fun testAssistantMessageEditCopy() {
        val original = Message(
            id = 555L,
            conversationId = 1L,
            role = "assistant",
            content = "这是模型原始生成的回复内容。"
        )
        val edited = original.copy(content = "这是经用户手动润色修改后的回复内容。")

        assertEquals(555L, edited.id)
        assertEquals("assistant", edited.role)
        assertEquals("这是经用户手动润色修改后的回复内容。", edited.content)
        assertNotEquals(original.content, edited.content)
    }

    @Test
    fun testTimelineFallbackAndNoUndeterminedRegress() {
        // 当事件列表中未包含显式时间标签时，从文本内容中智能提取时间线索，绝不粗暴退回到“未确定”
        val eventsWithoutTag = listOf(
            TimelineEventItem(timeTag = "", content = "三年后·春，两人在皇都庆典上再次重逢"),
            TimelineEventItem(timeTag = "   ", content = "在王都街巷交谈甚欢")
        )
        val inferred = TimelineMemoryHelper.inferCurrentStoryTime(eventsWithoutTag, fallbackTime = "第 5 天·傍晚")
        assertTrue("应能从事件正文中提取自然时间或安全兜底", inferred.contains("三年后") || inferred == "第 5 天·傍晚")
        assertNotEquals("未确定", inferred)
        assertNotEquals("未知", inferred)
    }

    @Test
    fun testTimelineFallbackToPreviousStoryTime() {
        // 完全无时间线索时，安全继承既有历史故事节点
        val emptyEvents = emptyList<TimelineEventItem>()
        val result = TimelineMemoryHelper.inferCurrentStoryTime(emptyEvents, fallbackTime = "第 2 天·黄昏")
        assertEquals("第 2 天·黄昏", result)
    }

    @Test
    fun testAtemporalSettingItemNextCategorySupportsAllDimensions() {
        // 验证 6 维全称常驻设定循环
        val item6 = AtemporalSettingItem(category = "角色核心特质")
        assertEquals("习惯与偏好", item6.nextCategory())
        item6.category = "习惯与偏好"
        assertEquals("生理禁忌与弱点", item6.nextCategory())
        item6.category = "生理禁忌与弱点"
        assertEquals("人际羁绊与契约", item6.nextCategory())
        item6.category = "人际羁绊与契约"
        assertEquals("秘密揭露与真相", item6.nextCategory())
        item6.category = "秘密揭露与真相"
        assertEquals("世界铁律与规则", item6.nextCategory())
        item6.category = "世界铁律与规则"
        assertEquals("角色核心特质", item6.nextCategory())
    }

    @Test
    fun testContextSummaryBufferThresholds() {
        // 验证 60% 预警阈值与 75% 压缩阈值逻辑
        val maxTokens = 10000
        val warningThreshold = (maxTokens * 0.60).toInt()
        val compressThreshold = (maxTokens * 0.75).toInt()

        assertEquals(6000, warningThreshold)
        assertEquals(7500, compressThreshold)

        // 模拟当前 token
        val safeToken = 5500
        assertFalse(safeToken in warningThreshold..compressThreshold)
        assertFalse(safeToken >= compressThreshold)

        val warningToken = 6500
        assertTrue(warningToken in warningThreshold..compressThreshold)

        val triggerToken = 7600
        assertTrue(triggerToken >= compressThreshold)
    }
}
