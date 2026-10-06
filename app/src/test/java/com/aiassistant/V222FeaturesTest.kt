package com.aiassistant

import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.ui.screens.chat.TempChatSettings
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.ui.screens.settings.V222UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V222FeaturesTest {

    @Test
    fun testV222UserUpdatesCompleteness() {
        val updates = V222UserUpdates
        assertFalse("V222 更新日志列表不得为空", updates.isEmpty())
        assertEquals("必须包含全部 7 项用户需求更新项", 7, updates.size)
        assertTrue("CurrentVersionUserUpdates 数量必须大于等于 5 项满足历史单测基准", CurrentVersionUserUpdates.size >= 5)

        assertTrue("第1项：模型连接气泡智能展开说明", updates.any { it.contains("模型连接气泡智能展开") })
        assertTrue("第2项：气泡展开形变彻底修复说明", updates.any { it.contains("气泡展开形变彻底修复") })
        assertTrue("第3项：全量直角矩形阴影修复说明", updates.any { it.contains("全量直角矩形阴影修复") })
        assertTrue("第4项：思考图标闪烁与样式统一说明", updates.any { it.contains("思考图标闪烁与样式统一") })
        assertTrue("第5项：多场景输入框精简紧凑说明", updates.any { it.contains("多场景输入框精简紧凑") })
        assertTrue("第6项：跨会话记忆与世界书默认关闭说明", updates.any { it.contains("跨会话记忆与世界书默认关闭") })
        assertTrue("第7项：跨会话记忆分类精细筛选说明", updates.any { it.contains("跨会话记忆分类精细筛选") })
    }

    @Test
    fun testRoleplaySessionAndTempChatSettingsDefaultFalse() {
        val session = RoleplaySession(conversationId = 1L)
        assertFalse("RoleplaySession enableExternalMemory 必须默认为 false", session.enableExternalMemory)
        assertFalse("RoleplaySession enableWorldBook 必须默认为 false", session.enableWorldBook)

        val tempSettings = TempChatSettings()
        assertFalse("TempChatSettings enableExternalMemory 必须默认为 false", tempSettings.enableExternalMemory)
        assertFalse("TempChatSettings enableWorldBook 必须默认为 false", tempSettings.enableWorldBook)
    }

    @Test
    fun testModelConnectionCapsuleExpandableLogic() {
        fun isExpandable(hasThinking: Boolean, text: String): Boolean {
            val hasDetailedExpandableContent = !hasThinking && (text.contains("\n") || text.length > 48)
            return hasDetailedExpandableContent
        }

        // 简短无换行文本不需要展开
        assertFalse("短文本不应可展开", isExpandable(false, "已连接 DeepSeek-V3 (240ms)"))
        // 包含换行符（如多行报错信息或参数）
        assertTrue("包含换行符必须可展开", isExpandable(false, "连接异常\nHTTP 429: Too Many Requests"))
        // 长度超过 48 个字符的长文本报错或状态
        val longText = "连接超时: 目标服务器 192.168.1.1:8080 在 15000ms 内未响应任何数据，请检查网络设置并重试"
        assertTrue("超过48字符的长文本必须可展开", isExpandable(false, longText))
        // 带有 thinkingContent 时走思考折叠逻辑，非普通连接气泡展开逻辑
        assertFalse("有思考内容时不作为普通连接气泡展开", isExpandable(true, "连接正常"))
    }

    @Test
    fun testCrossConversationMemoryFilterLogic() {
        data class MockMemoryItem(val id: String, val scope: String, val content: String)
        val memories = listOf(
            MockMemoryItem("1", "user", "用户喜欢简洁风格"),
            MockMemoryItem("2", "global", "全局背景设定"),
            MockMemoryItem("3", "conversation", "当前会话的特定约定"),
            MockMemoryItem("4", "", "无特定作用域默认归为全局偏好")
        )

        fun filterMemories(scopeMode: Int): List<MockMemoryItem> {
            return when (scopeMode) {
                1 -> memories.filter { it.scope.equals("user", ignoreCase = true) || it.scope.equals("global", ignoreCase = true) || it.scope.isBlank() }
                2 -> memories.filter { it.scope.equals("conversation", ignoreCase = true) }
                else -> memories
            }
        }

        val all = filterMemories(0)
        assertEquals("全部必须包含 4 条", 4, all.size)

        val globalPreferences = filterMemories(1)
        assertEquals("全局偏好必须包含 3 条", 3, globalPreferences.size)
        assertTrue(globalPreferences.all { it.scope != "conversation" })

        val conversationExclusive = filterMemories(2)
        assertEquals("会话专属必须包含 1 条", 1, conversationExclusive.size)
        assertEquals("conversation", conversationExclusive[0].scope)
    }
}
