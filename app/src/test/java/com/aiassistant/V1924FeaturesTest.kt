package com.aiassistant

import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.ChatRequestOptions
import com.aiassistant.ui.screens.chat.ChatUiState
import com.aiassistant.ui.screens.chat.TempChatSettings
import com.aiassistant.ui.screens.chat.displayModelShortName
import com.aiassistant.ui.screens.settings.V1924UserUpdates
import org.junit.Assert.*
import org.junit.Test

class V1924FeaturesTest {

    @Test
    fun testDisplayModelShortName() {
        // Display-only casing follows prefix stripping; request IDs remain untouched.
        assertEquals("GLM-5.2", "z-ai/glm-5.2".displayModelShortName())
        assertEquals("GPT-4O", "openai/gpt-4o".displayModelShortName())
        assertEquals("Claude-3-5-Sonnet", "anthropic/claude-3-5-sonnet".displayModelShortName())
        assertEquals("DeepSeek-R1", "deepseek-ai/deepseek-r1".displayModelShortName())
        assertEquals("Final-Model", "a/b/c/final-model".displayModelShortName())
        
        // Test without slash
        assertEquals("GPT-4O-Mini", "gpt-4o-mini".displayModelShortName())
        assertEquals("Custom-Local-Model", "custom-local-model".displayModelShortName())
        assertEquals("", "".displayModelShortName())
    }

    @Test
    fun testDefaultsMaxTokensAndThinking() {
        // ApiConfig defaults
        val apiConfig = ApiConfig(
            name = "Test",
            provider = "openai",
            baseUrl = "https://api.openai.com",
            apiKey = "sk-test",
            modelName = "gpt-4o"
        )
        assertEquals(4096, apiConfig.maxTokens)
        assertTrue(apiConfig.enableThinking)

        // TempChatSettings defaults
        val tempSettings = TempChatSettings()
        assertEquals(4096, tempSettings.maxTokens)
        assertTrue(tempSettings.enableThinking)
        assertFalse(tempSettings.enableSessionMemory)

        // ChatUiState defaults
        val uiState = ChatUiState()
        assertTrue(uiState.enableThinking)
    }

    @Test
    fun testChatRequestOptionsEnableSessionMemory() {
        val defaultOptions = ChatRequestOptions()
        assertNull(defaultOptions.enableSessionMemory)

        val disabledOptions = ChatRequestOptions(enableSessionMemory = false)
        assertEquals(false, disabledOptions.enableSessionMemory)

        val enabledOptions = ChatRequestOptions(enableSessionMemory = true)
        assertEquals(true, enabledOptions.enableSessionMemory)
    }

    @Test
    fun testCurrentVersionUserUpdatesCompleteness() {
        assertFalse("本次更新日志列表不得为空", V1924UserUpdates.isEmpty())
        assertTrue("必须包含状态栏阴影修复说明", V1924UserUpdates.any { it.contains("状态栏阴影") })
        assertTrue("必须包含悬浮栏修复说明", V1924UserUpdates.any { it.contains("悬浮栏") })
        assertTrue("必须包含滚动条优化说明", V1924UserUpdates.any { it.contains("滚动条") })
    }
}
