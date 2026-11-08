package com.aiassistant

import com.aiassistant.data.repository.helpers.firstEnabledConfig
import com.aiassistant.data.repository.helpers.resolveAuxiliaryModel
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.SelectedModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 辅助任务（起名 / 翻译 / 记忆提炼）不得使用停用配置与停用模型。
 * 这些规则之前散落在 repository 的三处内联分支里，这里固化为可回归的策略。
 */
class AuxiliaryModelPolicyTest {

    private fun config(id: Long, enabled: Boolean, model: String = "model-$id") = ApiConfig(
        id = id,
        name = "config-$id",
        provider = "provider",
        baseUrl = "https://api.example.com/v1",
        apiKey = "key",
        apiType = "openai",
        modelName = model,
        isEnabled = enabled
    )

    private fun model(name: String, enabled: Boolean) = SelectedModel(
        apiConfigId = 1L,
        modelName = name,
        isEnabled = enabled
    )

    @Test
    fun preferredDisabledConfigFallsThroughToConversationConfig() {
        val preferred = config(1L, enabled = false)
        val conversation = config(2L, enabled = true)
        assertSame(conversation, firstEnabledConfig(preferred, conversation))
    }

    @Test
    fun firstEnabledConfigSkipsNullAndDisabledEntries() {
        val disabled = config(1L, enabled = false)
        val enabled = config(2L, enabled = true)
        assertSame(enabled, firstEnabledConfig(null, disabled, enabled))
        assertNull(firstEnabledConfig(null, disabled))
    }

    @Test
    fun requestedEnabledModelWins() {
        val resolved = resolveAuxiliaryModel(
            requestedModel = "  deepseek-chat  ",
            configDefaultModel = "gpt-4o",
            selectedModels = listOf(model("deepseek-chat", true), model("gpt-4o", true))
        )
        assertEquals("deepseek-chat", resolved)
    }

    @Test
    fun requestedDisabledModelFallsBackToConfigDefault() {
        val resolved = resolveAuxiliaryModel(
            requestedModel = "old-model",
            configDefaultModel = "gpt-4o",
            selectedModels = listOf(model("old-model", false), model("gpt-4o", true))
        )
        assertEquals("gpt-4o", resolved)
    }

    @Test
    fun disabledConfigDefaultFallsBackToFirstEnabledModel() {
        val resolved = resolveAuxiliaryModel(
            requestedModel = "",
            configDefaultModel = "retired-model",
            selectedModels = listOf(model("retired-model", false), model("active-a", true), model("active-b", true))
        )
        assertEquals("active-a", resolved)
    }

    @Test
    fun noEnabledModelsKeepsConfiguredDefault() {
        val resolved = resolveAuxiliaryModel(
            requestedModel = "disabled-model",
            configDefaultModel = "legacy-model",
            selectedModels = listOf(model("disabled-model", false))
        )
        assertEquals("legacy-model", resolved)
    }

    @Test
    fun freeFormModelNameStillAllowed() {
        val resolved = resolveAuxiliaryModel(
            requestedModel = "custom-not-listed",
            configDefaultModel = "gpt-4o",
            selectedModels = listOf(model("gpt-4o", true))
        )
        assertEquals("custom-not-listed", resolved)
    }
}
