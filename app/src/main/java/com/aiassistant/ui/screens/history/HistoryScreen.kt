package com.aiassistant.ui.screens.history

import androidx.room.withTransaction

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.aiassistant.ui.components.echoCheckboxColors
import com.aiassistant.AiAssistantApp
import com.aiassistant.BuildConfig
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Message
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.EchoGlassPagePanelShape
import com.aiassistant.ui.components.EchoWallpaperBackground
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.components.rememberReadableBackdropColor
import com.aiassistant.utils.BackgroundImageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val HistoryGlassInnerAlpha = 0.58f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit
) {
    val repository = AiAssistantApp.instance.repository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val historyBackgroundBitmap = remember(context) {
        BackgroundImageManager.getHomeBackgroundBitmap(context)
    }
    val hazeState = rememberEchoHazeState()
    val readableBackdrop = rememberReadableBackdropColor(historyBackgroundBitmap)

    val conversations by repository.getAllConversations().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var searchInMessages by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    val importConversationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val imported = importConversation(context, it)
                Toast.makeText(
                    context,
                    if (imported) "对话导入成功" else "对话导入失败，请确认文件格式",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 搜索逻辑
    LaunchedEffect(searchQuery, searchInMessages, conversations) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }

        if (searchInMessages) {
            searchResults = emptyList()
            kotlinx.coroutines.delay(250)
            val byId = conversations.associateBy { it.id }
            searchResults = repository.searchMessages(searchQuery).mapNotNull { msg ->
                byId[msg.conversationId]?.let { SearchResult(it, msg, msg.content) }
            }
        }
    }

    val filteredConversations = if (searchInMessages) {
        conversations // 消息搜索时显示所有对话
    } else {
        conversations.filter {
            searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
        }
    }

    EchoWallpaperBackground(
        backgroundBitmap = historyBackgroundBitmap,
        hazeState = hazeState
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("历史记录") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { importConversationLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }) {
                            Icon(Icons.Default.FileUpload, contentDescription = "导入")
                        }
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "导出")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
            // 搜索栏
            val searchTint = echoGlassPalette().panelStrong
            val searchTextColor = readableTextColorFor(
                background = searchTint,
                fallbackSurface = readableBackdrop
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .echoHazePanel(
                        hazeState = hazeState,
                        shape = EchoGlassPagePanelShape,
                        tint = searchTint,
                        blurRadius = 18.dp
                ),
                shape = EchoGlassPagePanelShape,
                color = searchTint,
                contentColor = searchTextColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    placeholder = { Text("搜索对话...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = searchTextColor,
                        unfocusedTextColor = searchTextColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        cursorColor = searchTextColor,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        unfocusedBorderColor = searchTextColor.copy(alpha = 0.22f),
                        focusedLeadingIconColor = searchTextColor,
                        unfocusedLeadingIconColor = searchTextColor,
                        focusedTrailingIconColor = searchTextColor,
                        unfocusedTrailingIconColor = searchTextColor,
                        focusedPlaceholderColor = searchTextColor.copy(alpha = 0.58f),
                        unfocusedPlaceholderColor = searchTextColor.copy(alpha = 0.58f)
                    )
                )
            }

            // 搜索选项
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = searchInMessages,
                    onCheckedChange = { searchInMessages = it },
                    colors = echoCheckboxColors()
                )
                Text(
                    text = "搜索消息内容",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 统计信息
            Text(
                text = if (searchInMessages && searchQuery.isNotBlank()) {
                    "找到 ${searchResults.size} 条匹配消息"
                } else {
                    "共 ${filteredConversations.size} 个对话"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (searchInMessages && searchQuery.isNotBlank()) {
                // 显示消息搜索结果
                if (searchResults.isEmpty()) {
                    EmptyHistoryContent(
                        modifier = Modifier.weight(1f),
                        hasSearch = true
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = searchResults,
                            key = { "${it.conversation.id}_${it.message?.id}" }
                        ) { result ->
                            SearchResultCard(
                                hazeState = hazeState,
                                result = result,
                                searchQuery = searchQuery,
                                readableBackdrop = readableBackdrop,
                                onClick = { onNavigateToChat(result.conversation.id) }
                            )
                        }
                    }
                }
            } else if (filteredConversations.isEmpty()) {
                EmptyHistoryContent(
                    modifier = Modifier.weight(1f),
                    hasSearch = searchQuery.isNotBlank()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        items(
                            items = filteredConversations,
                            key = { it.id }
                        ) { conversation ->
                            HistoryConversationCard(
                                hazeState = hazeState,
                                conversation = conversation,
                                readableBackdrop = readableBackdrop,
                            onClick = { onNavigateToChat(conversation.id) },
                            onExport = { selectedConversation = conversation },
                            onDelete = {
                                scope.launch {
                                    repository.deleteConversation(conversation.id)
                                }
                            }
                        )
                    }
                }
            }
        }
        }
    }

    // 导出对话框
    if (showExportDialog) {
        ExportDialog(
            hazeState = hazeState,
            conversations = filteredConversations,
            onDismiss = { showExportDialog = false },
            onExport = { conv ->
                scope.launch {
                    exportConversation(context, conv)
                }
                showExportDialog = false
            }
        )
    }

    // 单个对话导出
    selectedConversation?.let { conv ->
        ExportSingleDialog(
            hazeState = hazeState,
            conversation = conv,
            onDismiss = { selectedConversation = null },
            onExport = { format ->
                scope.launch {
                    exportConversation(context, conv, format)
                }
                selectedConversation = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryConversationCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    conversation: Conversation,
    readableBackdrop: Color,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val cardTint = echoGlassPalette().panelStrong
    val content = readableTextColorFor(
        background = cardTint,
        fallbackSurface = readableBackdrop
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = cardTint,
                blurRadius = 18.dp
            )
            .echoShapeClick(EchoGlassPagePanelShape, onClick = onClick),
        shape = EchoGlassPagePanelShape,
        color = cardTint,
        contentColor = content,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 模型图标
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 对话信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = content,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = com.aiassistant.domain.model.ModelDisplayName.format(conversation.modelName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${conversation.messageCount} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.68f)
                    )
                    if (conversation.totalTokens > 0) {
                        Text(
                            text = "${conversation.totalTokens} tokens",
                            style = MaterialTheme.typography.labelSmall,
                            color = content.copy(alpha = 0.68f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(conversation.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.68f)
                )
            }

            // 菜单
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }

                EchoGlassDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("导出") },
                        onClick = {
                            onExport()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            showDeleteDialog = true
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除对话") },
            text = { Text("确定要删除这个对话吗？此操作不可撤销。") },
            confirmButton = {
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
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun EmptyHistoryContent(
    modifier: Modifier = Modifier,
    hasSearch: Boolean
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (hasSearch) Icons.Default.SearchOff else Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasSearch) "未找到匹配的对话" else "暂无历史记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hasSearch) {
            Text(
                text = "尝试使用不同的关键词搜索",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ExportDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    conversations: List<Conversation>,
    onDismiss: () -> Unit,
    onExport: (Conversation) -> Unit
) {
    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = { Text("导出对话") },
        text = {
            Column {
                Text(
                    text = "选择要导出的对话：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (conversations.isEmpty()) {
                    Text(
                        text = "没有可导出的对话",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(conversations.take(20)) { conv ->
                            OutlinedCard(
                                onClick = { onExport(conv) }
                            ) {
                                Text(
                                    text = conv.title,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun ExportSingleDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    conversation: Conversation,
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    var selectedFormat by remember { mutableStateOf("json") }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = { Text("导出格式") },
        text = {
            Column {
                Text(
                    text = "选择导出格式：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val jsonSelected = selectedFormat == "json"
                    FilterChip(
                        selected = jsonSelected,
                        onClick = { selectedFormat = "json" },
                        colors = echoFilterChipColors(),
                        border = echoFilterChipBorder(jsonSelected),
                        elevation = echoFilterChipElevation(),
                        label = { Text("JSON") }
                    )
                    val markdownSelected = selectedFormat == "markdown"
                    FilterChip(
                        selected = markdownSelected,
                        onClick = { selectedFormat = "markdown" },
                        colors = echoFilterChipColors(),
                        border = echoFilterChipBorder(markdownSelected),
                        elevation = echoFilterChipElevation(),
                        label = { Text("Markdown") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(selectedFormat) }) {
                Text("导出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

suspend fun exportConversation(
    context: android.content.Context,
    conversation: Conversation,
    format: String = "json"
) {
    val repository = AiAssistantApp.instance.repository
    try {
        val extension = if (format == "markdown") "md" else "json"
        val mimeType = if (format == "markdown") "text/markdown" else "application/json"
        val safeTitle = conversation.title
            .ifBlank { "conversation_${conversation.id}" }
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(48)
        val exportFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val messages = repository.getMessagesList(conversation.id)
            val content = if (format == "markdown") buildMarkdownExport(conversation, messages) else buildJsonExport(conversation, messages)
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            File(exportDir, "$safeTitle.$extension").apply { writeText(content, Charsets.UTF_8) }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            exportFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, conversation.title.ifBlank { "导出对话" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "导出对话"))
    } catch (e: Exception) {
        Toast.makeText(context, "导出失败：${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
    }
}

suspend fun importConversation(
    context: android.content.Context,
    uri: Uri
): Boolean {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { try {
        val repository = AiAssistantApp.instance.repository
        val defaultConfig = repository.getDefaultApiConfig()
            ?: repository.getAllApiConfigs().first().firstOrNull()
            ?: return@withContext false
        val json = context.contentResolver.openInputStream(uri)
            ?.use { com.aiassistant.utils.BoundedInput.bytes(it).toString(Charsets.UTF_8) }
            ?: return@withContext false
        val parsed = com.aiassistant.utils.HistoryExchange.parse(json, defaultConfig.modelName)
        val db = AiAssistantApp.instance.database
        db.withTransaction {
            val id = db.conversationDao().insertConversation(Conversation(title = parsed.title, apiConfigId = defaultConfig.id, modelName = parsed.model))
            db.messageDao().insertMessages(parsed.messages.map { it.copy(conversationId = id) })
            db.conversationDao().refreshStats(id)
        }
        true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    } }
}

fun buildJsonExport(conversation: Conversation, messages: List<Message>): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val export = mapOf(
        "title" to conversation.title,
        "model" to conversation.modelName,
        "created" to dateFormat.format(Date(conversation.createdAt)),
        "updated" to dateFormat.format(Date(conversation.updatedAt)),
        "messageCount" to messages.size,
        "messages" to messages.map { msg ->
            mapOf(
                "role" to msg.role,
                "content" to msg.content,
                "thinkingContent" to msg.thinkingContent,
                "attachments" to msg.attachments,
                "timestamp" to dateFormat.format(Date(msg.createdAt))
            )
        }
    )
    return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(export)
}

fun buildMarkdownExport(conversation: Conversation, messages: List<Message>): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return buildString {
        appendLine("# ${conversation.title}")
        appendLine()
        appendLine("- **模型**: ${conversation.modelName}")
        appendLine("- **创建时间**: ${dateFormat.format(Date(conversation.createdAt))}")
        appendLine("- **消息数**: ${messages.size}")
        appendLine()
        appendLine("---")
        appendLine()
        messages.forEach { msg ->
            val role = if (msg.role == "user") "👤 用户" else "🤖 助手"
            appendLine("**$role** (${dateFormat.format(Date(msg.createdAt))})")
            appendLine()
            appendLine(msg.content)
            appendLine()
            appendLine("---")
            appendLine()
        }
    }
}

// 搜索结果数据类
data class SearchResult(
    val conversation: Conversation,
    val message: Message?,
    val matchText: String
)

@Composable
fun SearchResultCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    result: SearchResult,
    searchQuery: String,
    readableBackdrop: Color,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val cardTint = echoGlassPalette().panelStrong
    val content = readableTextColorFor(
        background = cardTint,
        fallbackSurface = readableBackdrop
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = cardTint,
                blurRadius = 18.dp
            )
            .echoShapeClick(EchoGlassPagePanelShape, onClick = onClick),
        shape = EchoGlassPagePanelShape,
        color = cardTint,
        contentColor = content,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 对话标题
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.conversation.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 匹配的消息内容
            if (result.message != null) {
                Spacer(modifier = Modifier.height(8.dp))

                val messageContent = result.message.content
                val matchIndex = messageContent.indexOf(searchQuery, ignoreCase = true)
                val start = maxOf(0, matchIndex - 50)
                val end = minOf(messageContent.length, matchIndex + searchQuery.length + 50)
                val preview = buildString {
                    if (start > 0) append("...")
                    append(messageContent.substring(start, end))
                    if (end < messageContent.length) append("...")
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = HistoryGlassInnerAlpha)
                    )
                ) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = content,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 消息信息
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (result.message.role == "user") "用户" else "AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateFormat.format(Date(result.message.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.68f)
                    )
                }
            }
        }
    }
}
