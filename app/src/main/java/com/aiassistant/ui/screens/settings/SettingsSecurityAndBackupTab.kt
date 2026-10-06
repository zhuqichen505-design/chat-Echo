@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aiassistant.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.aiassistant.domain.model.ChatModelOption
import com.aiassistant.ui.components.ImageCropEditDialog
import com.aiassistant.ui.components.CropShapeMode
import com.aiassistant.ui.components.ExpandableText
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.AiAssistantApp
import com.aiassistant.BuildConfig
import com.aiassistant.R
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.NamedApiKey
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.EnvironmentVariable
import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.WorldBook
import com.aiassistant.domain.model.WorldBookEntry
import com.aiassistant.domain.model.ModelCapabilityEngine
import com.aiassistant.domain.model.ModelCustomSettings
import com.aiassistant.domain.model.PromptTemplate
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoBadge
import com.aiassistant.ui.components.EchoBadgeType
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.rememberReadableBackdropColors
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoSegmentedButtonBorder
import com.aiassistant.ui.components.echoSegmentedButtonColors
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoHazeSource
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.components.rememberSmoothReorderState
import com.aiassistant.ui.components.reorderItem
import com.aiassistant.ui.components.reorderDragHandle
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.BackgroundImageManager
import com.aiassistant.utils.BackupManager
import com.aiassistant.utils.HiddenConversationLock
import com.aiassistant.utils.TavilySearchSettings
import com.aiassistant.utils.AppThemeMode
import com.aiassistant.tools.search.SearchEngineType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aiassistant.tools.cloud.OpenMeteoWeatherEngine
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HiddenConversationsTab(
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
    onNavigateToChat: (Long) -> Unit,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = AiAssistantApp.instance.repository
    val scope = rememberCoroutineScope()
    val lock = remember(context) { HiddenConversationLock(context) }
    var hasPassword by remember { mutableStateOf(lock.hasPassword()) }
    var unlocked by remember { mutableStateOf(lock.isSessionUnlocked) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val hiddenConversations by repository.getHiddenConversations().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var renamingConversation by remember { mutableStateOf<Conversation?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var deletingConversation by remember { mutableStateOf<Conversation?>(null) }

    val filteredConversations = remember(hiddenConversations, searchQuery) {
        if (searchQuery.isBlank()) hiddenConversations
        else hiddenConversations.filter {
            it.title.contains(searchQuery.trim(), ignoreCase = true) ||
            it.modelName.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.cardGap)
    ) {
        item {
            SettingsGlassCard(hazeState = hazeState) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("其他对话", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "隐藏对话不会出现在首页。请使用 6 位数字密码查看、搜索或管理隐藏对话。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            }
        }

        if (!hasPassword) {
            item {
                PinSetupCard(
                    hazeState = hazeState,
                    pin = pin,
                    confirmPin = confirmPin,
                    message = message,
                    onPinChange = { pin = it.onlySixDigits() },
                    onConfirmPinChange = { confirmPin = it.onlySixDigits() },
                    onSave = {
                        when {
                            pin.length != 6 || confirmPin.length != 6 -> message = "请输入 6 位数字密码"
                            pin != confirmPin -> message = "两次输入的密码不一致"
                            lock.setPassword(pin) -> {
                                hasPassword = true
                                unlocked = true
                                pin = ""
                                confirmPin = ""
                                message = "密码已设置"
                            }
                            else -> message = "密码保存失败，请重试"
                        }
                    }
                )
            }
        } else if (!unlocked) {
            item {
                PinVerifyCard(
                    hazeState = hazeState,
                    pin = pin,
                    message = message,
                    onPinChange = { pin = it.onlySixDigits() },
                    onVerify = {
                        if (lock.verify(pin)) {
                            unlocked = true
                            pin = ""
                            message = null
                        } else {
                            message = "密码不正确"
                        }
                    }
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "隐藏对话 (${hiddenConversations.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            lock.lockSession()
                            unlocked = false
                        }
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "重新锁定",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重新锁定", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索隐藏对话标题或模型...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "清空", modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = SettingsInnerShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (filteredConversations.isEmpty()) {
                item {
                    SettingsGlassCard(hazeState = hazeState) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "未找到包含 “$searchQuery” 的隐藏对话" else "当前没有隐藏对话。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredConversations, key = { it.id }) { conversation ->
                    HiddenConversationCard(
                        hazeState = hazeState,
                        conversation = conversation,
                        onOpen = { onNavigateToChat(conversation.id) },
                        onUnhide = {
                            scope.launch {
                                repository.setConversationHidden(conversation.id, false)
                            }
                        },
                        onRename = {
                            renamingConversation = conversation
                            renameTitle = conversation.title
                        },
                        onDelete = {
                            deletingConversation = conversation
                        },
                        onTogglePin = {
                            scope.launch {
                                repository.setPinned(conversation.id, !conversation.isPinned)
                            }
                        },
                        onDuplicate = {
                            scope.launch {
                                val newId = repository.duplicateConversation(conversation.id)
                                if (newId > 0) {
                                    android.widget.Toast.makeText(context, "已成功复制隐藏对话", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onBackup = {
                            // v2.7.3 流畅度：备份为多表查询+序列化+写盘，迁到 IO 协程，回主线程 Toast
                            scope.launch(Dispatchers.IO) {
                                val backupPath = BackupManager.createSingleConversationBackup(context, conversation.id)
                                withContext(Dispatchers.Main) {
                                    if (backupPath != null) {
                                        android.widget.Toast.makeText(context, "隐藏对话已备份至 Echo_Backups", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "对话备份失败", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (renamingConversation != null) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { renamingConversation = null },
            title = { Text("重命名对话") },
            text = {
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("对话名称") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val conv = renamingConversation
                        if (conv != null && renameTitle.isNotBlank()) {
                            scope.launch {
                                repository.updateConversationTitle(conv.id, renameTitle.trim())
                            }
                        }
                        renamingConversation = null
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingConversation = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (deletingConversation != null) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { deletingConversation = null },
            title = { Text("删除对话") },
            text = {
                Text("确定要彻底删除该隐藏对话吗？此操作无法撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val conv = deletingConversation
                        if (conv != null) {
                            scope.launch {
                                repository.deleteConversation(conv.id)
                            }
                        }
                        deletingConversation = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingConversation = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun PinSetupCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    pin: String,
    confirmPin: String,
    message: String?,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onSave: () -> Unit
) {
    SettingsGlassCard(hazeState = hazeState) {
            Text("首次使用请设置密码", style = MaterialTheme.typography.titleSmall)
            PinField(value = pin, onValueChange = onPinChange, label = "输入 6 位数字密码")
            PinField(value = confirmPin, onValueChange = onConfirmPinChange, label = "再次输入密码")
            HiddenLockMessage(message)
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = pin.length == 6 && confirmPin.length == 6
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("设置并进入")
            }
    }
}

@Composable
private fun PinVerifyCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    pin: String,
    message: String?,
    onPinChange: (String) -> Unit,
    onVerify: () -> Unit
) {
    SettingsGlassCard(hazeState = hazeState) {
            Text("输入密码", style = MaterialTheme.typography.titleSmall)
            PinField(value = pin, onValueChange = onPinChange, label = "6 位数字密码")
            HiddenLockMessage(message)
            Button(
                onClick = onVerify,
                modifier = Modifier.fillMaxWidth(),
                enabled = pin.length == 6
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("进入其他对话")
            }
    }
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    SettingsInputField(
        title = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = "请输入 6 位数字",
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}

@Composable
private fun HiddenLockMessage(message: String?) {
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HiddenConversationCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    conversation: Conversation,
    onOpen: () -> Unit,
    onUnhide: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onDuplicate: () -> Unit,
    onBackup: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    SettingsGlassCard(hazeState = hazeState) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (conversation.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "已置顶",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = conversation.title.ifBlank { "未命名对话" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (conversation.isPinned) "取消置顶" else "置顶",
                        tint = if (conversation.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制对话",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onBackup, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = "备份此对话",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "重命名",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text(
            text = "${com.aiassistant.domain.model.ModelDisplayName.format(conversation.modelName)} · ${conversation.messageCount} 条 · ${dateFormat.format(Date(conversation.updatedAt))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onUnhide,
                modifier = Modifier.weight(1f)
            ) {
                Text("取消隐藏")
            }
            Button(
                onClick = onOpen,
                modifier = Modifier.weight(1f)
            ) {
                Text("进入")
            }
        }
    }
}

private fun String.onlySixDigits(): String {
    return filter { it.isDigit() }.take(6)
}



@Composable
fun BackupTab(
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var backups by remember { mutableStateOf(BackupManager.getBackupList(context)) }
    var isBackingUp by remember { mutableStateOf(false) }
    var showMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showMessage) {
        showMessage?.let {
            snackbarHostState.showSnackbar(it)
            showMessage = null
        }
    }
    val exportBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            scope.launch {
                // v2.7.3 流畅度：zip 打包+整文件拷贝迁 IO，UI 状态回写在 Main
                isBackingUp = true
                val result = withContext(Dispatchers.IO) { BackupManager.exportBackupToUri(context, it) }
                isBackingUp = false
                showMessage = if (result) "备份已导出到所选位置" else "备份导出失败"
                backups = BackupManager.getBackupList(context)
            }
        }
    }
    val importBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                // v2.7.3 流畅度：解压+逐表合并迁 IO，UI 状态回写在 Main
                isBackingUp = true
                val result = withContext(Dispatchers.IO) { BackupManager.restoreBackupFromUri(context, it) }
                isBackingUp = false
                showMessage = if (result) "导入成功，已安全合并备份数据" else "导入失败，请确认文件格式有效"
                backups = BackupManager.getBackupList(context)
            }
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.cardGap)
    ) {
        item {
            SettingsGlassCard(hazeState = hazeState) {
                Text(
                    text = "数据备份与恢复",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "定期备份可以防止数据丢失。建议在更新应用前备份数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Button(
                onClick = {
                    scope.launch {
                        isBackingUp = true
                        // v2.7.3 流畅度：zip 打包数据库迁 IO
                        val result = withContext(Dispatchers.IO) { BackupManager.createBackup(context) }
                        isBackingUp = false
                        if (result != null) {
                            showMessage = "备份成功！"
                            backups = BackupManager.getBackupList(context)
                        } else {
                            showMessage = "备份失败"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBackingUp
            ) {
                if (isBackingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Backup, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("立即备份")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val fileName = "Echo_Backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.zip"
                        exportBackupLauncher.launch(fileName)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isBackingUp
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("导出备份")
                }
                OutlinedButton(
                    onClick = {
                        importBackupLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/plain", "*/*"))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isBackingUp
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("导入备份")
                }
            }
        }

        // T-8：保存/备份反馈统一走 Snackbar（showMessage → LaunchedEffect → showSnackbar），
        // 不再使用列表内联卡片，避免与 Snackbar 双反馈并存

        if (backups.isNotEmpty()) {
            item {
                Text(
                    text = "备份列表",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            items(backups) { backup ->
                BackupItemCard(
                    hazeState = hazeState,
                    backup = backup,
                    onRestore = {
                        scope.launch {
                            // v2.7.3 流畅度：整库合并事务迁 IO，回 Main 更新提示
                            val result = withContext(Dispatchers.IO) {
                                BackupManager.restoreBackup(context, backup.filePath)
                            }
                            showMessage = if (result) {
                                if (backup.fileName.endsWith(".json", ignoreCase = true)) "恢复成功！已成功导入该对话" else "恢复成功！请重启应用"
                            } else "恢复失败"
                        }
                    },
                    onDelete = {
                        scope.launch {
                            BackupManager.deleteBackup(backup.filePath)
                            backups = BackupManager.getBackupList(context)
                        }
                    },
                    onShare = {
                        BackupManager.shareBackup(context, backup.filePath)
                    }
                )
            }
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun BackupItemCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    backup: BackupManager.BackupItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val isSingleConv = backup.fileName.endsWith(".json", ignoreCase = true)

    SettingsGlassCard(hazeState = hazeState) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSingleConv) Icons.Default.ChatBubble else Icons.Default.FolderZip,
                contentDescription = if (isSingleConv) "单对话备份" else "全量备份",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = backup.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    EchoBadge(
                        text = if (isSingleConv) "单对话" else "全量",
                        type = EchoBadgeType.Primary
                    )
                }
                Text(
                    text = dateFormat.format(Date(backup.lastModified)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "分享")
            }
            IconButton(onClick = { showRestoreDialog = true }) {
                Icon(Icons.Default.Restore, contentDescription = "恢复")
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showRestoreDialog) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showRestoreDialog = false },
            title = {
                Text(if (isSingleConv) "恢复单对话备份" else "恢复全量备份", style = MaterialTheme.typography.titleLarge)
            },
            content = {
                Text(
                    text = if (isSingleConv)
                        "确定要从 ${backup.fileName} 恢复该对话吗？仅增量导入该对话，绝不影响其它任何对话。"
                    else
                        "确定要从 ${backup.fileName} 恢复数据吗？将增量合并备份数据，本地已有且未在备份中的对话也将完整保留。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            onRestore()
                            showRestoreDialog = false
                        }
                    ) {
                        Text("恢复")
                    }
                }
            }
        )
    }

    if (showDeleteDialog) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text("删除备份", style = MaterialTheme.typography.titleLarge)
            },
            content = {
                Text(
                    text = "确定要删除 ${backup.fileName} 吗？此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            onDelete()
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("删除")
                    }
                }
            }
        )
    }
}

@Composable
fun AboutTab(
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(EchoTokens.Spacing.cardGap)
    ) {
        // 应用信息
        item {
            SettingsGlassCard(hazeState = hazeState) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Echo",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 本次更新
        item {
            SettingsGlassCard(hazeState = hazeState) {
                Column {
                    Text(
                        text = "本次更新",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CurrentVersionUserUpdates.forEach { update ->
                        FeatureItem(update)
                    }
                }
            }
        }

        // 功能特性
        item {
            SettingsGlassCard(hazeState = hazeState) {
                Column {
                    Text(
                        text = "功能特性",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CurrentFeatureHighlights.forEach { feature ->
                        FeatureItem(feature)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun FeatureItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}
