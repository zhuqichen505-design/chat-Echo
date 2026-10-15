package com.aiassistant.ui.screens.roleplay

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiassistant.AiAssistantApp
import com.aiassistant.utils.BoundedInput
import com.aiassistant.utils.WorldBookImport
import com.aiassistant.utils.WorldBookImportReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Functional import entry, using the current app's components while visual redesign is reviewed. */
@Composable
internal fun WorldBookImportButton(onImported: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<WorldBookImportReport?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var accepted by remember(report) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                report = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        BoundedInput.bytes(it, WorldBookImport.MAX_JSON_BYTES)
                    } ?: error("无法读取文件")
                    WorldBookImport.parse(bytes.toString(Charsets.UTF_8))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "世界书读取失败" }
            finally { busy = false }
        }
    }
    IconButton(enabled = !busy, onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
        Icon(Icons.Default.FileUpload, contentDescription = if (busy) "正在读取世界书" else "导入世界书 JSON")
    }
    report?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!busy) report = null },
            title = { Text("检查世界书导入") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("${preview.name}\n${preview.format.displayName} · ${preview.entries.size} 个词条")
                        Text("将新增独立副本，不覆盖同名素材。整本默认关闭；${preview.disabledEntryCount} 个词条保持停用。", style = MaterialTheme.typography.bodyMedium)
                    }
                    items(preview.warnings) { warning -> Text(warning, style = MaterialTheme.typography.bodySmall) }
                    items(preview.entries) { entry ->
                        Column {
                            Text(entry.draft.name, style = MaterialTheme.typography.titleSmall)
                            Text(entry.draft.content.take(180), style = MaterialTheme.typography.bodySmall)
                            entry.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    if (preview.requiresCompatibilityConfirmation) item {
                        Row {
                            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                            Text("我已阅读兼容提示。未支持的规则不会应用，相关条目保持停用；原始扩展不会保存。", modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && (!preview.requiresCompatibilityConfirmation || accepted), onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val id = withContext(Dispatchers.IO) {
                                AiAssistantApp.instance.database.worldBookDao().insertImportedBook(
                                    WorldBookImport.prepare(preview, acceptCompatibilityLoss = accepted))
                            }
                            report = null
                            message = "已保存世界书副本。下一步：设置 → 提示词与记忆 → 世界书，检查并启用书目和词条；在对话/故事设置的高级区打开世界书并选中此书。导入不会自动绑定。"
                            onImported(id)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { message = error.message ?: "导入失败，未保存部分词条" }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "正在保存…" else "新增副本") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { report = null }) { Text("取消") } }
        )
    }
    message?.let { text ->
        AlertDialog(onDismissRequest = { message = null }, title = { Text("世界书导入") },
            text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } })
    }
}
