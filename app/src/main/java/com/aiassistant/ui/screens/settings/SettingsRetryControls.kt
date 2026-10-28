package com.aiassistant.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiassistant.domain.model.RetryErrorType
import com.aiassistant.domain.model.RetryPolicy
import com.aiassistant.domain.model.RetryRule
import com.aiassistant.ui.components.EchoSwitch
import com.aiassistant.ui.components.EchoSwitchSize
import com.aiassistant.ui.components.EchoSettingRow
import com.aiassistant.utils.PersonalizationManager

@Composable
internal fun SettingsRetryControls(manager: PersonalizationManager) {
    // 这是设置页中的可展开面板，必须保存到配置页生命周期，切换子页或重组时不能自动展开。
    var expanded by rememberSaveable { mutableStateOf(true) }
    var streamingEnabled by remember { mutableStateOf(manager.isStreamingEnabled()) }
    var streamingSaveFailed by remember { mutableStateOf(false) }
    var retryEnabled by remember { mutableStateOf(manager.isModelRetryEnabled()) }
    var policy by remember { mutableStateOf(manager.getRetryPolicy()) }
    var backupKeyFallback by remember { mutableStateOf(manager.isBackupKeyFallbackEnabled()) }
    Text("模型连接与错误重试", style = MaterialTheme.typography.titleMedium)
    EchoSettingRow(title = "流式输出", subtitle = "开启后逐步显示回复；关闭后等待完整回复，适用于不支持流式的服务商。对新请求生效。", contentPaddingHorizontal = 0.dp) {
        EchoSwitch(checked = streamingEnabled, onCheckedChange = { enabled ->
            val saved = runCatching { manager.setStreamingEnabled(enabled) }.getOrDefault(false)
            if (saved) streamingEnabled = enabled
            streamingSaveFailed = !saved
        })
    }
    if (streamingSaveFailed) {
        Text("流式输出设置保存失败，请重试。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
    EchoSettingRow(title = "模型失败后自动重试", subtitle = "总开关：关闭后不自动重连、不自动切换备用 Key；首次失败立即显示原因", contentPaddingHorizontal = 0.dp) {
        EchoSwitch(checked = retryEnabled, onCheckedChange = {
            retryEnabled = it
            manager.setModelRetryEnabled(it)
            policy = manager.getRetryPolicy()
            backupKeyFallback = manager.isBackupKeyFallbackEnabled()
        })
    }
    Text(
        "对话、角色回复及辅助模型共用。次数是首次请求之外的额外尝试；已收到内容或手动停止时不会重发。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EchoSettingRow(title = "失败后尝试备用 Key", subtitle = "关闭后只使用第一个已启用 Key；分类开关控制同一 Key 的重试", contentPaddingHorizontal = 0.dp) {
        EchoSwitch(checked = backupKeyFallback, enabled = retryEnabled, onCheckedChange = {
            backupKeyFallback = it
            manager.setBackupKeyFallbackEnabled(it)
        })
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("按错误类型设置", style = MaterialTheme.typography.titleSmall)
            Text(
                if (expanded) "每个 Key 的额外次数：0–${RetryPolicy.MAX_RETRIES}" else "已折叠；${RetryErrorType.entries.count { policy.rule(it).enabled }} 类错误已开启",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(if (expanded) "收起" else "展开")
        }
    }
    if (expanded && retryEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RetryErrorType.entries.forEach { type ->
                RetryRuleCard(
                    modifier = Modifier.fillMaxWidth(),
                    type = type,
                    rule = policy.rule(type),
                    manager = manager,
                    onPolicyChanged = { policy = manager.getRetryPolicy() }
                )
            }
        }
    }
}

@Composable
private fun RetryRuleCard(
    modifier: Modifier,
    type: RetryErrorType,
    rule: RetryRule,
    manager: PersonalizationManager,
    onPolicyChanged: () -> Unit
) {
    var count by rememberSaveable(type) { mutableStateOf(rule.maxRetries.toString()) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
        )
    ) {
        val fontScale = LocalDensity.current.fontScale
        val countField = @Composable {
            OutlinedTextField(
                value = count,
                onValueChange = { value ->
                    count = value.filter(Char::isDigit).take(2)
                    count.toIntOrNull()?.takeIf { it in 0..RetryPolicy.MAX_RETRIES }?.let {
                        manager.saveRetryRule(type, rule.copy(maxRetries = it))
                        onPolicyChanged()
                    }
                },
                label = { Text("次数", style = MaterialTheme.typography.labelSmall) },
                enabled = rule.enabled,
                isError = count.toIntOrNull() !in 0..RetryPolicy.MAX_RETRIES,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width((76 * fontScale.coerceAtLeast(1f)).dp)
            )
        }
        val toggle = @Composable {
            EchoSwitch(
                checked = rule.enabled,
                size = EchoSwitchSize.Compact,
                onCheckedChange = { enabled ->
                    manager.saveRetryRule(type, rule.copy(enabled = enabled))
                    onPolicyChanged()
                }
            )
        }
        BoxWithConstraints(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            // 两个控件有稳定的操作宽度；窄屏或大字体时将次数移到下一行。
            if (maxWidth < (280 * fontScale).dp) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(type.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        toggle()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("额外重试次数", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        countField()
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(type.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    toggle()
                    countField()
                }
            }
        }
    }
}
