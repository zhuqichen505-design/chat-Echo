package com.aiassistant.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiassistant.domain.model.RetryErrorType
import com.aiassistant.domain.model.RetryPolicy
import com.aiassistant.domain.model.RetryRule
import com.aiassistant.ui.components.EchoSwitch
import com.aiassistant.ui.components.EchoSettingRow
import com.aiassistant.utils.PersonalizationManager

@Composable
internal fun SettingsRetryControls(manager: PersonalizationManager) {
    var expanded by remember { mutableStateOf(true) }
    var retryEnabled by remember { mutableStateOf(manager.isModelRetryEnabled()) }
    var policy by remember { mutableStateOf(manager.getRetryPolicy()) }
    var backupKeyFallback by remember { mutableStateOf(manager.isBackupKeyFallbackEnabled()) }
    Text("模型连接与错误重试", style = MaterialTheme.typography.titleMedium)
    EchoSettingRow(title = "模型失败后自动重试", subtitle = "总开关：关闭后不自动重连、不自动切换备用 Key；首次失败立即显示原因", contentPaddingHorizontal = 0.dp) {
        EchoSwitch(checked = retryEnabled, onCheckedChange = {
            retryEnabled = it
            manager.setModelRetryEnabled(it)
            policy = manager.getRetryPolicy()
            backupKeyFallback = manager.isBackupKeyFallbackEnabled()
        })
    }
    Text(
        "对话、角色回复及辅助模型共用。次数为首次请求之外的额外尝试，每个 Key 单独计数；已收到内容或手动停止时不自动重发。鉴权、参数和额度问题通常不能靠重试解决。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    EchoSettingRow(title = "失败后尝试备用 Key", subtitle = "关闭后只使用第一个已启用 Key；分类开关控制同一 Key 的重试", contentPaddingHorizontal = 0.dp) {
        EchoSwitch(checked = backupKeyFallback, enabled = retryEnabled, onCheckedChange = {
            backupKeyFallback = it
            manager.setBackupKeyFallbackEnabled(it)
        })
    }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起分类设置" else "展开分类设置") }
    if (expanded && retryEnabled) {
        RetryErrorType.entries.forEach { type ->
            val rule = policy.rule(type)
            EchoSettingRow(title = type.label, contentPaddingHorizontal = 0.dp) {
                EchoSwitch(checked = rule.enabled, onCheckedChange = { enabled ->
                    manager.saveRetryRule(type, rule.copy(enabled = enabled))
                    policy = manager.getRetryPolicy()
                })
            }
            var count by remember(type) { mutableStateOf(rule.maxRetries.toString()) }
            OutlinedTextField(
                value = count,
                onValueChange = { value ->
                    count = value.filter(Char::isDigit).take(2)
                    count.toIntOrNull()?.takeIf { it in 0..RetryPolicy.MAX_RETRIES }?.let {
                        manager.saveRetryRule(type, RetryRule(rule.enabled, it))
                        policy = manager.getRetryPolicy()
                    }
                },
                label = { Text("额外重试次数（0–${RetryPolicy.MAX_RETRIES}）") },
                enabled = rule.enabled,
                isError = count.toIntOrNull() !in 0..RetryPolicy.MAX_RETRIES,
                supportingText = {
                    if (count.toIntOrNull() !in 0..RetryPolicy.MAX_RETRIES) {
                        Text("请输入 0–${RetryPolicy.MAX_RETRIES}；当前仍使用已保存的 ${rule.maxRetries} 次")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
    }
}
