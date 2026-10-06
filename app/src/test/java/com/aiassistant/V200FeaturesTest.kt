package com.aiassistant

import androidx.compose.ui.graphics.Color
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.domain.model.ModelCustomSettings
import com.aiassistant.domain.model.SelectedModel
import com.aiassistant.ui.screens.chat.TempChatSettings
import com.aiassistant.ui.screens.settings.V200UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V200FeaturesTest {

    @Test
    fun testV200CurrentVersionUserUpdatesCompleteness() {
        assertTrue("V2.0.0 更新列表不得为空", V200UserUpdates.isNotEmpty())
        assertEquals(10, V200UserUpdates.size)
        assertTrue(V200UserUpdates.any { it.contains("左侧加深") })
        assertTrue(V200UserUpdates.any { it.contains("设置页面控制栏完全统一") })
        assertTrue(V200UserUpdates.any { it.contains("收缩至发送键") })
        assertTrue(V200UserUpdates.any { it.contains("收缩至圆形返回键") })
        assertTrue(V200UserUpdates.any { it.contains("蓝色高亮微光包边") })
        assertTrue(V200UserUpdates.any { it.contains("引用") })
        assertTrue(V200UserUpdates.any { it.contains("隐藏会话") })
        assertTrue(V200UserUpdates.any { it.contains("使用统计图表全面重构") })
        assertTrue(V200UserUpdates.any { it.contains("专属会话记忆保存逻辑修复") })
        assertTrue(V200UserUpdates.any { it.contains("已添加") && it.contains("从Key读取") })
    }

    @Test
    fun testBorderHighlightGradientDarkeningContrast() {
        val baseOutline = Color(0xFFE2E8F0)
        val leftAlphaLight = 0.50f
        val rightAlphaLight = 0.16f
        val leftOutlineLight = baseOutline.copy(alpha = leftAlphaLight)
        val rightOutlineLight = baseOutline.copy(alpha = rightAlphaLight)

        assertTrue(
            "左侧高亮边框不透明度必须显著高于右侧",
            leftOutlineLight.alpha > rightOutlineLight.alpha * 2.5f
        )
        assertTrue("左侧边框透明度应为 0.50f", leftOutlineLight.alpha in 0.49f..0.51f)
        assertTrue("右侧边框透明度应为 0.16f", rightOutlineLight.alpha in 0.15f..0.17f)
    }

    @Test
    fun testModelCustomSettingsDefaultsAndCustomization() {
        val defaultSettings = ModelCustomSettings()
        assertNull(defaultSettings.contextWindowTokens)
        assertTrue(defaultSettings.supportsTools)
        assertFalse(defaultSettings.supportsVision)
        assertTrue(defaultSettings.supportsThinking)
        assertTrue(defaultSettings.supportsWebSearch)

        val customSettings = ModelCustomSettings(
            contextWindowTokens = 128_000,
            supportsTools = true,
            supportsVision = false,
            supportsThinking = true,
            supportsWebSearch = true
        )
        assertEquals(128_000, customSettings.contextWindowTokens)
        assertTrue(customSettings.supportsTools)
        assertFalse(customSettings.supportsVision)
        assertTrue(customSettings.supportsThinking)
        assertTrue(customSettings.supportsWebSearch)
    }

    @Test
    fun testSelectedModelWithCustomSettingsFields() {
        val selected = SelectedModel(
            apiConfigId = 1L,
            modelName = "claude-3-7-sonnet",
            contextWindowTokens = 200_000,
            supportsTools = true,
            supportsVision = true,
            supportsThinking = true,
            supportsWebSearch = false
        )
        assertEquals(1L, selected.apiConfigId)
        assertEquals("claude-3-7-sonnet", selected.modelName)
        assertEquals(200_000, selected.contextWindowTokens)
        assertTrue(selected.supportsTools)
        assertTrue(selected.supportsVision)
        assertTrue(selected.supportsThinking)
        assertFalse(selected.supportsWebSearch)
    }

    @Test
    fun testRoomMigration22_23RegisteredAndSchemaVersion() {
        assertNotNull(AppDatabase.MIGRATION_22_23)
        assertEquals(22, AppDatabase.MIGRATION_22_23.startVersion)
        assertEquals(23, AppDatabase.MIGRATION_22_23.endVersion)
    }

    @Test
    fun testSessionMemoryDisabledByDefault() {
        val settings = TempChatSettings()
        assertFalse("新建对话专属记忆必须默认为关闭", settings.enableSessionMemory)
    }

    @Test
    fun testQuoteTextFormatting() {
        val originalText = "Hello\nWorld"
        val quotedLines = originalText.lines().joinToString("\n") { line -> "> $line" }
        val finalQuote = "$quotedLines\n\n"
        assertEquals("> Hello\n> World\n\n", finalQuote)
    }
}
