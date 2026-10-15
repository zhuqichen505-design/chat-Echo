package com.aiassistant.ui.screens.roleplay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiassistant.AiAssistantApp
import com.aiassistant.domain.model.WorldBook
import com.aiassistant.domain.model.WorldBookEntry
import com.aiassistant.ui.components.EchoConfirmDialog
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.screens.settings.WorldBookEditDialog
import com.aiassistant.ui.screens.settings.WorldBookEntriesManageDialog
import com.aiassistant.ui.screens.settings.WorldBookEntryEditDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 创作资料库和故事工作区共用世界书管理，导入后无需绕行全局设置。 */
@Composable
internal fun StudioLoreLibrary(modifier: Modifier = Modifier) {
    val repository = remember { AiAssistantApp.instance.repository }
    val books by remember { repository.getAllWorldBooks() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val haze = rememberEchoHazeState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorldBook?>(null) }
    var managing by remember { mutableStateOf<WorldBook?>(null) }
    var deleting by remember { mutableStateOf<WorldBook?>(null) }
    var addingEntryTo by remember { mutableStateOf<Long?>(null) }
    var editingEntry by remember { mutableStateOf<WorldBookEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<WorldBookEntry?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    fun mutate(action: suspend () -> Unit) { scope.launch {
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "操作失败，请重试" }
    } }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("世界书资料库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("检查词条 → 启用书目 → 在作品中绑定", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                WorldBookImportButton(onImported = { id -> mutate { managing = repository.getWorldBookById(id) } })
                IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "创建世界书") }
            }
        }
        item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("搜索书目") }) }
        if (books.isEmpty()) item { Text("暂无世界书。可以导入 SillyTavern JSON、Character Book 或自行创建。导入保留兼容检查，未支持的规则会提示。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(books.filter { it.name.contains(query, true) || it.description.contains(query, true) }, key = { it.id }) { book ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { managing = book }.padding(vertical = 8.dp)) {
                        Text(book.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(if (book.isEnabled) "已启用 · 点击管理词条" else "已停用 · 绑定到故事也不会注入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = book.isEnabled, onCheckedChange = { enabled -> mutate { repository.setWorldBookEnabled(book.id, enabled) } })
                    IconButton(onClick = { editing = book }) { Icon(Icons.Default.Edit, "编辑 ${book.name}") }
                    IconButton(onClick = { deleting = book }) { Icon(Icons.Default.DeleteOutline, "删除 ${book.name}") }
                }
                if (book.description.isNotBlank()) Text(book.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { managing = book }) { Text("管理词条与触发关键词") }
                HorizontalDivider()
            }
        }
    }
    if (creating || editing != null) WorldBookEditDialog(hazeState = haze, book = editing,
        onDismiss = { creating = false; editing = null }, onConfirm = { name, description, tags ->
            val original = editing
            mutate {
                val book = original?.copy(name = name, description = description, tags = tags, updatedAt = System.currentTimeMillis())
                    ?: WorldBook(name = name, description = description, tags = tags)
                if (original == null) repository.insertWorldBook(book) else repository.updateWorldBook(book)
                creating = false; editing = null
            }
        })
    managing?.let { book -> WorldBookEntriesManageDialog(hazeState = haze, book = book,
        onDismiss = { managing = null }, onAddEntry = { addingEntryTo = book.id },
        onEditEntry = { editingEntry = it }, onDeleteEntry = { deletingEntry = it }) }
    val targetBookId = addingEntryTo ?: editingEntry?.bookId
    if (targetBookId != null) WorldBookEntryEditDialog(hazeState = haze, entry = editingEntry, bookId = targetBookId,
        onDismiss = { addingEntryTo = null; editingEntry = null }, onConfirm = { name, keys, content, constant, priority ->
            val original = editingEntry
            mutate {
                val value = original?.copy(name = name, keys = keys, content = content, isConstant = constant, priority = priority, updatedAt = System.currentTimeMillis())
                    ?: WorldBookEntry(bookId = targetBookId, name = name, keys = keys, content = content, isConstant = constant, priority = priority)
                if (original == null) repository.insertWorldBookEntry(value) else repository.updateWorldBookEntry(value)
                addingEntryTo = null; editingEntry = null
            }
        })
    deleting?.let { book -> EchoConfirmDialog(title = "删除世界书？", text = "“${book.name}”及其词条会从资料库移除，所有故事都将无法再引用它。", confirmText = "删除", isDestructive = true,
        onConfirm = { mutate { repository.deleteWorldBook(book.id); deleting = null } }, onDismiss = { deleting = null }) }
    deletingEntry?.let { entry -> EchoConfirmDialog(title = "删除词条？", text = "删除“${entry.name}”，影响所有绑定此书的故事。", confirmText = "删除", isDestructive = true,
        onConfirm = { mutate { repository.deleteWorldBookEntry(entry.id); deletingEntry = null } }, onDismiss = { deletingEntry = null }) }
    error?.let { text -> AlertDialog(onDismissRequest = { error = null }, title = { Text("操作未完成") }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } }) }
}
