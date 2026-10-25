package com.aiassistant.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiassistant.domain.model.ApiKeyRotationSettings
import com.aiassistant.ui.components.EchoSwitch

/** 数字保留编辑原文；无效输入不会在保存时悄悄变成默认值。 */
internal data class ApiKeyRotationDraft(
    val settings: ApiKeyRotationSettings,
    val failures: String = settings.failuresBeforeCooldown.toString(),
    val cooldown: String = settings.cooldownSeconds.toString(),
    val maxCooldown: String = settings.maxCooldownSeconds.toString(),
    val failureReset: String = settings.failureResetSeconds.toString()
) {
    val failuresError: String?
        get() = if (settings.enabled && settings.cooldownEnabled && failures.toIntOrNull() !in 1..ApiKeyRotationSettings.MAX_FAILURE_THRESHOLD) {
            "请输入 1 至 ${ApiKeyRotationSettings.MAX_FAILURE_THRESHOLD} 次"
        } else null

    val cooldownError: String?
        get() = if (settings.enabled && settings.cooldownEnabled && cooldown.toLongOrNull() !in 1L..ApiKeyRotationSettings.MAX_BASE_COOLDOWN_SECONDS) {
            "请输入 1 至 ${ApiKeyRotationSettings.MAX_BASE_COOLDOWN_SECONDS} 秒"
        } else null

    val maxCooldownError: String?
        get() {
            if (!settings.enabled || !settings.cooldownEnabled || !settings.exponentialBackoff) return null
            val maximum = maxCooldown.toLongOrNull()
            return when {
                maximum !in 1L..ApiKeyRotationSettings.MAX_COOLDOWN_SECONDS ->
                    "请输入 1 至 ${ApiKeyRotationSettings.MAX_COOLDOWN_SECONDS} 秒"
                cooldown.toLongOrNull()?.let { maximum != null && maximum < it } == true ->
                    "最大冷却不能小于基础冷却"
                else -> null
            }
        }

    val failureResetError: String?
        get() = if (settings.enabled && failureReset.toLongOrNull() !in 0L..ApiKeyRotationSettings.MAX_FAILURE_RESET_SECONDS) {
            "请输入 0 至 ${ApiKeyRotationSettings.MAX_FAILURE_RESET_SECONDS} 秒"
        } else null

    val validationMessage: String?
        get() = failuresError ?: cooldownError ?: maxCooldownError ?: failureResetError

    fun toSettingsOrNull(): ApiKeyRotationSettings? {
        if (validationMessage != null) return null
        return settings.copy(
            failuresBeforeCooldown = failures.toIntOrNull()
                ?.takeIf { it in 1..ApiKeyRotationSettings.MAX_FAILURE_THRESHOLD }
                ?: settings.failuresBeforeCooldown,
            cooldownSeconds = cooldown.toLongOrNull()
                ?.takeIf { it in 1L..ApiKeyRotationSettings.MAX_BASE_COOLDOWN_SECONDS }
                ?: settings.cooldownSeconds,
            maxCooldownSeconds = maxCooldown.toLongOrNull()
                ?.takeIf { it in 1L..ApiKeyRotationSettings.MAX_COOLDOWN_SECONDS }
                ?: settings.maxCooldownSeconds,
            failureResetSeconds = failureReset.toLongOrNull()
                ?.takeIf { it in 0L..ApiKeyRotationSettings.MAX_FAILURE_RESET_SECONDS }
                ?: settings.failureResetSeconds
        ).normalized()
    }
}

@Composable
internal fun ApiKeyRotationSettingsSection(
    scopeKey: String,
    draft: ApiKeyRotationDraft,
    onDraftChange: (ApiKeyRotationDraft) -> Unit,
    enabled: Boolean,
    canResetHealth: Boolean,
    resettingHealth: Boolean,
    resetFeedback: Pair<Boolean, String>?,
    onResetHealth: () -> Unit
) {
    var expanded by rememberSaveable(scopeKey) { mutableStateOf(false) }
    val settings = draft.settings
    val modes = remember {
        listOf(
            Triple(ApiKeyRotationSettings.ROUND_ROBIN, "轮流优先", "每次请求轮换起点，在健康状态相同的 Key 之间分摊调用。"),
            Triple(ApiKeyRotationSettings.PREFER_SUCCESS, "最近成功优先", "优先延续最近成功的 Key，失败后改用其他可用 Key。"),
            Triple(ApiKeyRotationSettings.ORDERED, "列表顺序优先", "以手动排序为基础，冷却和失败降权仍优先生效。")
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RotationToggleRow(
                title = "启用多 Key 轮询",
                description = if (settings.enabled) "按下方策略选择 Key；关闭后固定使用列表中第一个已启用的 Key。"
                    else "已关闭：固定使用第一个已启用的 Key，失败后不切换其他 Key，也不受冷却记录拦截。",
                checked = settings.enabled,
                enabled = enabled,
                onCheckedChange = { onDraftChange(draft.copy(settings = settings.copy(enabled = it))) }
            )
            Text(
                "同一 API 地址及类型的配置共用此开关和策略；点击“保存”后生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("多 Key 轮询与冷却", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${modes.firstOrNull { it.first == settings.mode }?.second ?: "轮流优先"} · " +
                                if (settings.cooldownEnabled) "失败冷却已开启" else "失败冷却已关闭",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起轮询设置" else "展开轮询设置"
                    )
                }
                if (expanded) {
                    Text(
                        "同一 API 地址及类型的配置共用此策略和失败记录。更改地址或类型会载入对应策略；以下修改点击“保存”后生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    modes.forEach { (mode, label, description) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = settings.mode == mode,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onDraftChange(draft.copy(settings = settings.copy(mode = mode))) }
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.mode == mode, onClick = null, enabled = enabled)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    RotationToggleRow(
                        title = "失败自动降权",
                        description = "失败较多的 Key 排到健康 Key 后面，避免每次从故障 Key 开始。",
                        checked = settings.deprioritizeFailures,
                        enabled = enabled,
                        onCheckedChange = { onDraftChange(draft.copy(settings = settings.copy(deprioritizeFailures = it))) }
                    )
                    RotationToggleRow(
                        title = "失败后暂时冷却",
                        description = "达到失败阈值后跳过该 Key，冷却结束后重新参与选择。",
                        checked = settings.cooldownEnabled,
                        enabled = enabled,
                        onCheckedChange = { onDraftChange(draft.copy(settings = settings.copy(cooldownEnabled = it))) }
                    )
                    if (settings.cooldownEnabled) {
                        RotationNumberField(
                            label = "触发冷却的失败次数", value = draft.failures,
                            onValueChange = { onDraftChange(draft.copy(failures = it)) },
                            description = "1 至 ${ApiKeyRotationSettings.MAX_FAILURE_THRESHOLD} 次；设为 1 即首次失败后冷却。",
                            error = draft.failuresError, enabled = enabled
                        )
                        RotationNumberField(
                            label = "基础冷却时长（秒）", value = draft.cooldown,
                            onValueChange = { onDraftChange(draft.copy(cooldown = it)) },
                            description = "1 至 ${ApiKeyRotationSettings.MAX_BASE_COOLDOWN_SECONDS} 秒（7 天）；300 秒为 5 分钟。",
                            error = draft.cooldownError, enabled = enabled
                        )
                        RotationToggleRow(
                            title = "重复失败递增冷却",
                            description = "继续失败时将冷却时长逐次加倍，直到设定上限。",
                            checked = settings.exponentialBackoff,
                            enabled = enabled,
                            onCheckedChange = { onDraftChange(draft.copy(settings = settings.copy(exponentialBackoff = it))) }
                        )
                        if (settings.exponentialBackoff) {
                            RotationNumberField(
                                label = "最大冷却时长（秒）", value = draft.maxCooldown,
                                onValueChange = { onDraftChange(draft.copy(maxCooldown = it)) },
                                description = "不得小于基础冷却，最多 ${ApiKeyRotationSettings.MAX_COOLDOWN_SECONDS} 秒（30 天）。",
                                error = draft.maxCooldownError, enabled = enabled
                            )
                        }
                        RotationToggleRow(
                            title = "全部冷却时探测一个 Key",
                            description = if (settings.probeWhenAllCooling) "提前尝试最早恢复的一个 Key；仍失败则返回错误。"
                                else "直接提示稍后重试，避免继续触发失败。",
                            checked = settings.probeWhenAllCooling,
                            enabled = enabled,
                            onCheckedChange = { onDraftChange(draft.copy(settings = settings.copy(probeWhenAllCooling = it))) }
                        )
                    }
                    RotationNumberField(
                        label = "失败记录自动重置间隔（秒）", value = draft.failureReset,
                        onValueChange = { onDraftChange(draft.copy(failureReset = it)) },
                        description = "距上次失败达到此间隔后清除记录。0 关闭；最多 ${ApiKeyRotationSettings.MAX_FAILURE_RESET_SECONDS} 秒（365 天）。",
                        error = draft.failureResetError, enabled = enabled
                    )
                    RotationToggleRow(
                        title = "调用成功后清除失败记录",
                        description = "该 Key 再次成功时恢复健康优先级，并重置递增冷却。",
                        checked = settings.resetOnSuccess,
                        enabled = enabled,
                        onCheckedChange = { onDraftChange(draft.copy(settings = settings.copy(resetOnSuccess = it))) }
                    )
                    Text(
                        "备用 Key 总开关及单次请求的错误重试次数仍在“模型失败与重试”中设置。关闭备用 Key 时仍会按此策略选择首个 Key，失败后不切换其他 Key。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onResetHealth, enabled = enabled && canResetHealth && !resettingHealth) {
                        Text(if (resettingHealth) "正在重置…" else "立即重置此地址的 Key 健康记录")
                    }
                    Text(
                        "重置会立即清除失败次数、冷却和调用顺序记录，即使取消本次编辑也会生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    resetFeedback?.let { (success, message) ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RotationToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EchoSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun RotationNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    error: String?,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.all { it in '0'..'9' }) onValueChange(input) },
        label = { Text(label) },
        supportingText = { Text(error ?: description) },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodySmall
    )
}
