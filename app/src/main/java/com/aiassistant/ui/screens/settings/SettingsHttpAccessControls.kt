package com.aiassistant.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.components.EchoSettingRow
import com.aiassistant.ui.components.EchoSwitch
import com.aiassistant.utils.HttpAccessSettings

@Composable
internal fun SettingsHttpAccessControls(manager: HttpAccessSettings) {
    var rules by remember { mutableStateOf(manager.getRules()) }
    var address by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Text("HTTP 访问", style = MaterialTheme.typography.titleMedium)
    EchoSettingRow(
        title = "允许 HTTP",
        subtitle = "仅允许下方列表中的地址；默认关闭，HTTPS 不受影响",
        contentPaddingHorizontal = 0.dp
    ) {
        EchoSwitch(checked = rules.enabled, onCheckedChange = {
            manager.setEnabled(it)
            rules = manager.getRules()
        })
    }
    Text(
        "HTTP 不加密 API 密钥、消息与回复，请仅用于可信网络。放行不代表地址可达；组网地址仍需手机连接相应网络。",
        style = MaterialTheme.typography.bodyMedium
    )
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("允许的 HTTP 地址", style = MaterialTheme.typography.titleSmall)
    Text(
        "按主机、端口和路径范围匹配，不支持通配符。填写 /v1 会允许 /v1/models 等子路径，不允许 /v10 或其他端口。仅填主机与端口会允许其全部路径。",
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedTextField(
        value = address,
        onValueChange = { address = it; error = null },
        label = { Text("HTTP 地址") },
        placeholder = { Text("http://192.0.2.1:8080/v1") },
        supportingText = { Text(error ?: "示例地址不可用于连接，请填写自己的地址；可省略 http://，不要填写密钥或账号") },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(onClick = {
        try {
            manager.addAddress(address)
            rules = manager.getRules()
            address = ""
            error = null
        } catch (e: IllegalArgumentException) {
            error = e.message ?: "地址格式无效"
        }
    }, enabled = address.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("添加允许地址") }
    if (rules.addresses.isEmpty()) {
        Text("尚未添加地址。即使开启开关，也不会放行任何 HTTP 请求。", style = MaterialTheme.typography.bodyMedium)
    } else {
        rules.addresses.forEach { allowed ->
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(allowed, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    manager.removeAddress(allowed)
                    rules = manager.getRules()
                }) { Text("移除 $allowed") }
                HorizontalDivider()
            }
        }
    }
    Text(
        if (rules.enabled) "已开启：新请求按当前列表检查。关闭开关或移除地址不会撤回已发送的数据，也不会中断已接收的流。"
        else "已关闭：阻止新 HTTP 请求，保留允许列表。添加地址不会自动开启。",
        style = MaterialTheme.typography.bodySmall
    )
    Text("权限仅保存在本机，不随聊天备份导入其他设备；HTTPS 自动降级到 HTTP 的重定向始终拒绝。", style = MaterialTheme.typography.bodySmall)
}
