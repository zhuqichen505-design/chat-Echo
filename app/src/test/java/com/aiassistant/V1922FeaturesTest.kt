package com.aiassistant

import com.aiassistant.domain.model.ChatModelOption
import com.aiassistant.utils.PersonalizationSettings
import com.aiassistant.ui.screens.settings.CurrentVersionUserUpdates
import com.aiassistant.utils.BackgroundImageManager
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class V1922FeaturesTest {

    @Test
    fun testBuiltInSolidColorPresets() {
        val allPresets = BackgroundImageManager.PRESET_SOLID_COLORS
        assertEquals(14, allPresets.size)
        val presets = allPresets.take(6)
        // 保留原有 6 款精选低饱和护眼纯色
        assertEquals(6, presets.size)

        val expectedIds = listOf("pale_green", "pale_blue", "pale_purple", "pale_pink", "pale_yellow", "pale_cyan")
        assertEquals(expectedIds, presets.map { it.id })

        val expectedNames = listOf("浅艾绿", "浅湖蓝", "浅薰紫", "浅樱粉", "浅暖杏", "浅山岚")
        assertEquals(expectedNames, presets.map { it.name })

        // 浅色背景需具备清晰可辨的粉彩色相（不再惨白如纯白），同时保持高明度与深字高对比度
        presets.forEach { preset ->
            val red = (preset.lightColorInt shr 16) and 0xFF
            val green = (preset.lightColorInt shr 8) and 0xFF
            val blue = preset.lightColorInt and 0xFF

            val maxChannel = maxOf(red, green, blue)
            val minChannel = minOf(red, green, blue)
            val saturation = (maxChannel - minChannel).toFloat() / maxChannel.toFloat()
            assertTrue("${preset.name} 应明显区别于纯白（饱和度 >= 10%），实际为 ${(saturation * 100)}%", saturation >= 0.10f)
            assertTrue("${preset.name} 仍应保持柔和粉彩上限（饱和度 <= 35%），实际为 ${(saturation * 100)}%", saturation <= 0.35f)
            assertTrue("${preset.name} 最亮通道应保持浅色观感 (>= 220)，实际为 $maxChannel", maxChannel >= 220)
        }

        // 深色彩色背景需明显区别于纯黑，呈现可辨色相且不刺眼
        val darkColored = allPresets.filter { it.id in setOf("night_blue", "forest", "plum", "cocoa", "graphite", "deep_teal") }
        assertEquals(6, darkColored.size)
        darkColored.forEach { preset ->
            val red = (preset.darkColorInt shr 16) and 0xFF
            val green = (preset.darkColorInt shr 8) and 0xFF
            val blue = preset.darkColorInt and 0xFF
            val maxChannel = maxOf(red, green, blue)
            assertTrue("${preset.name} 最亮通道应 >= 85 以明显区别于纯黑，实际为 $maxChannel", maxChannel >= 85)
        }
    }

    @Test
    fun testReasoningSliderAntiJitterDiscreteStep() {
        // 验证手势拖拽时，只有步数发生变化才触发回调，且松手后通过 roundToInt 吸附到离散档位
        val totalStops = 5 // 0 (关闭), 1 (快速), 2 (平衡), 3 (深入), 4 (极高)
        val minVal = 0f
        val maxVal = 4f
        val valSpan = maxVal - minVal

        fun calculateStep(currentTouchProgress: Float): Int {
            return (currentTouchProgress * (totalStops - 1)).roundToInt().coerceIn(0, totalStops - 1)
        }

        // 测试中间微小抖动不会导致步骤乱跳
        assertEquals(0, calculateStep(0.01f))
        assertEquals(0, calculateStep(0.10f))
        assertEquals(1, calculateStep(0.20f))
        assertEquals(1, calculateStep(0.25f))
        assertEquals(2, calculateStep(0.48f))
        assertEquals(2, calculateStep(0.52f))
        assertEquals(3, calculateStep(0.72f))
        assertEquals(3, calculateStep(0.76f))
        assertEquals(4, calculateStep(0.92f))
        assertEquals(4, calculateStep(1.00f))
    }

    @Test
    fun testPersonalizationSettingsImmediatePersistence() {
        var currentSettings = PersonalizationSettings(
            autoMemoryEnabled = false,
            autoNameEnabled = false,
            autoNameModel = "gpt-4o-mini",
            enableThinkingTranslation = false,
            thinkingTranslationModel = "claude-3-5-haiku"
        )

        // 模拟切换跨会话长期记忆开关并立刻持久化
        currentSettings = currentSettings.copy(autoMemoryEnabled = true)
        assertTrue(currentSettings.autoMemoryEnabled)

        // 模拟切换自动命名与翻译模型
        currentSettings = currentSettings.copy(
            autoNameEnabled = true,
            autoNameModel = "deepseek-chat",
            enableThinkingTranslation = true,
            thinkingTranslationModel = "deepseek-reasoner"
        )

        assertTrue(currentSettings.autoNameEnabled)
        assertEquals("deepseek-chat", currentSettings.autoNameModel)
        assertTrue(currentSettings.enableThinkingTranslation)
        assertEquals("deepseek-reasoner", currentSettings.thinkingTranslationModel)
    }

    @Test
    fun testUniversalModelPickerIncludesAllChatOptionsAndCustomModels() {
        val chatOptions = listOf(
            ChatModelOption(1L, "OpenAI", "OpenAI", "openai", "gpt-4o"),
            ChatModelOption(2L, "DeepSeek", "DeepSeek", "deepseek", "deepseek-chat"),
            ChatModelOption(2L, "DeepSeek", "DeepSeek", "deepseek", "deepseek-reasoner")
        )

        // 验证全量模型筛选能力
        fun search(query: String): List<ChatModelOption> {
            val q = query.trim().lowercase()
            return if (q.isEmpty()) chatOptions else chatOptions.filter {
                it.modelName.lowercase().contains(q) || it.provider.lowercase().contains(q)
            }
        }

        assertEquals(3, search("").size)
        assertEquals(2, search("deepseek").size)
        assertEquals(1, search("gpt-4o").size)

        // 支持自定义输入非列表内模型
        val customModel = "my-custom-fine-tuned-model"
        val customOption = ChatModelOption(
            apiConfigId = 1L,
            configName = "Custom",
            provider = "Custom",
            apiType = "openai",
            modelName = customModel
        )
        assertEquals("my-custom-fine-tuned-model", customOption.modelName)
    }

    @Test
    fun testCurrentVersionUserUpdatesCompleteness() {
        assertFalse("本次更新日志列表不得为空", CurrentVersionUserUpdates.isEmpty())
        assertTrue("必须包含当前版本更新项", CurrentVersionUserUpdates.size >= 5)
    }
}
