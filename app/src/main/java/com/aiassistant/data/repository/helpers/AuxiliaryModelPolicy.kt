package com.aiassistant.data.repository.helpers

import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.SelectedModel

/**
 * 辅助任务（自动命名、思考链翻译、记忆提炼等）的配置选择：
 * 停用配置不得参与任何辅助调用，按优先级返回第一个启用项。
 */
internal fun firstEnabledConfig(vararg configs: ApiConfig?): ApiConfig? =
    configs.firstOrNull { it?.isEnabled == true }

/**
 * 辅助模型选择：显式指定的模型未被停用时优先，否则回落到配置默认模型，
 * 再回落到该配置下第一个启用模型。停用模型永远不会被选中。
 */
internal fun resolveAuxiliaryModel(
    requestedModel: String,
    configDefaultModel: String,
    selectedModels: List<SelectedModel>
): String {
    val requested = requestedModel.trim()
    val disabled = selectedModels.asSequence()
        .filter { !it.isEnabled }
        .map { it.modelName }
        .toSet()
    val enabled = selectedModels.asSequence()
        .filter { it.isEnabled }
        .map { it.modelName }
        .toList()
    return when {
        requested.isNotBlank() && requested !in disabled -> requested
        configDefaultModel in enabled -> configDefaultModel
        enabled.isNotEmpty() -> enabled.first()
        else -> configDefaultModel
    }
}
