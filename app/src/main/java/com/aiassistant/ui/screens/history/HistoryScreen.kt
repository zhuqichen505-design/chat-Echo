@file:OptIn(ExperimentalLayoutApi::class)

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.aiassistant.ui.components.echoCheckboxColors
import com.aiassistant.AiAssistantApp
import com.aiassistant.BuildConfig
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Message
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.echoContentInset
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
import com.aiassistant.ui.search.MessageSearchHit
import com.aiassistant.ui.search.MessageSearchHitCard
import com.aiassistant.ui.search.SearchMessageHitLimit
import com.aiassistant.ui.search.buildSearchSnippet
import com.aiassistant.ui.search.highlightRanges
import com.aiassistant.ui.search.matchesAllTerms
import com.aiassistant.ui.search.searchTerms
import com.aiassistant.ui.search.titleMatchScore
import com.aiassistant.utils.BackgroundImageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit,
    onNavigateToMessage: (Long, Long) -> Unit = { conversationId, _ -> onNavigateToChat(conversationId) },
    initialQuery: String = ""
) {
    val repository = AiAssistantApp.instance.repository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val historyBackgroundBitmap = remember(context, BackgroundImageManager.revision) {
        BackgroundImageManager.getHomeBackgroundBitmap(context)
    }
    val hazeState = rememberEchoHazeState()
    val readableBackdrop = rememberReadableBackdropColor(historyBackgroundBitmap)

    val conversations by repository.getAllConversations().collectAsState(initial = emptyList())
    var searchQuery by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    // 默认同时搜索消息内容：旧实现默认只搜标题，用户搜索聊过的内容时直接得到空结果。
    var includeMessageContent by rememberSaveable { mutableStateOf(true) }
    var messageHits by remember { mutableStateOf<List<MessageSearchHit>>(emptyList()) }
    var isSearchingMessages by remember { mutableStateOf(false) }
    var messageHitsTruncated by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
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

    // 搜索词归一化：去首尾空白（输入法尾随空格会让旧实现整条 SQL 匹配失败）、折叠空白、拆词。
    val terms = remember(searchQuery) { searchTerms(searchQuery) }
    val hasQuery = terms.isNotEmpty()

    // 对话命中：标题 / 模型 / 系统提示词，多词按 AND 匹配，标题相关度高的排前面。
    val conversationHits = remember(conversations, terms) {
        if (terms.isEmpty()) {
            conversations
        } else {
            conversations
                .filter {
                    matchesAllTerms(it.title, terms) ||
                        matchesAllTerms(it.modelName, terms) ||
                        matchesAllTerms(it.systemPrompt.orEmpty(), terms)
                }
                .sortedWith(
                    compareByDescending<Conversation> { titleMatchScore(it.title, terms) }
                        .thenByDescending { it.updatedAt }
                )
        }
    }

    // 消息内容命中：与对话命中是并集，不再要求用户先勾选开关才能搜到内容。
    LaunchedEffect(terms, includeMessageContent, conversations) {
        if (terms.isEmpty() || !includeMessageContent || conversations.isEmpty()) {
            messageHits = emptyList()
            messageHitsTruncated = false
            isSearchingMessages = false
            return@LaunchedEffect
        }
        // 防抖期间显示“正在搜索”，并且保留上一次结果，避免每敲一个字都闪一次“未找到匹配的对话”。
        isSearchingMessages = true
        delay(200)
        val byId = conversations.associateBy { it.id }
        val found = repository.searchMessagesForTerms(terms)
        messageHits = found
            .take(SearchMessageHitLimit)
            .mapNotNull { message ->
                val conversation = byId[message.conversationId] ?: return@mapNotNull null
                val snippet = buildSearchSnippet(message.content, terms) ?: return@mapNotNull null
                MessageSearchHit(conversation, message, snippet)
            }
        messageHitsTruncated = found.size > SearchMessageHitLimit
        isSearchingMessages = false
    }

    val filteredConversations = conversationHits

    EchoWallpaperBackground(
        backgroundBitmap = historyBackgroundBitmap,
        hazeState = hazeState
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
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
                    placeholder = { Text("搜索对话与消息内容...") },
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

            // 搜索选项：默认开启，关闭后只搜索对话标题 / 模型 / 提示词
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeMessageContent,
                    onCheckedChange = { includeMessageContent = it },
                    colors = echoCheckboxColors()
                )
                Text(
                    text = "搜索消息内容",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 统计信息
            Text(
                text = when {
                    !hasQuery -> "共 ${conversations.size} 个对话"
                    isSearchingMessages -> "正在搜索…  已找到 ${conversationHits.size} 个对话"
                    includeMessageContent ->
                        "${conversationHits.size} 个对话 · ${messageHits.size} 条消息" +
                            if (messageHitsTruncated) "（仅显示最近 $SearchMessageHitLimit 条）" else ""
                    else -> "${conversationHits.size} 个对话"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            val hasAnyHit = conversationHits.isNotEmpty() || messageHits.isNotEmpty()
            if (hasQuery && !hasAnyHit && isSearchingMessages) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "正在搜索消息内容…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (hasQuery && !hasAnyHit) {
                EmptyHistoryContent(
                    modifier = Modifier.weight(1f),
                    hasSearch = true
                )
            } else if (!hasQuery && conversations.isEmpty()) {
                EmptyHistoryContent(
                    modifier = Modifier.weight(1f),
                    hasSearch = false
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = echoContentInset(base = 16.dp), vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (conversationHits.isNotEmpty()) {
                        if (hasQuery) {
                            item(key = "search_conversation_header") {
                                SearchSectionHeader("对话 · ${conversationHits.size}")
                            }
                        }
                        items(
                            items = conversationHits,
                            key = { "conversation_${it.id}" }
                        ) { conversation ->
                            HistoryConversationCard(
                                hazeState = hazeState,
                                conversation = conversation,
                                readableBackdrop = readableBackdrop,
                                highlightTerms = terms,
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
                    if (messageHits.isNotEmpty()) {
                        item(key = "search_message_header") {
                            SearchSectionHeader(
                                "消息内容 · ${messageHits.size}" + if (messageHitsTruncated) "+" else ""
                            )
                        }
                        items(
                            items = messageHits,
                            key = { "message_${it.message.id}" }
                        ) { hit ->
                            MessageSearchHitCard(
                                hazeState = hazeState,
                                hit = hit,
                                readableBackdrop = readableBackdrop,
                                onClick = { onNavigateToMessage(hit.conversation.id, hit.message.id) }
                            )
                        }
                        if (messageHitsTruncated) {
                            item(key = "search_message_truncated") {
                                Text(
                                    text = "匹配结果较多，仅展示最近 $SearchMessageHitLimit 条消息；可补充更精确的关键词。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                )
                            }
                        }
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
    onDelete: () -> Unit,
    highlightTerms: List<String> = emptyList()
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val cardTint = echoGlassPalette().panelStrong
    val content = readableTextColorFor(
        background = cardTint,
        fallbackSurface = readableBackdrop
    )
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    val highlightedTitle = remember(conversation.title, highlightTerms, highlight) {
        buildHighlightedTitle(conversation.title, highlightTerms, highlight)
    }

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
                    text = highlightedTitle,
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

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

/** 搜索结果分区标题：把“对话”和“消息内容”分开，避免两类结果混排后无法判断来源。 */
@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

/** 标题高亮只加背景色，不覆盖 Text 的文字颜色，深色模式下仍由 content 保证可读性。 */
private fun buildHighlightedTitle(
    title: String,
    terms: List<String>,
    background: Color
): AnnotatedString = buildAnnotatedString {
    append(title)
    if (terms.isEmpty()) return@buildAnnotatedString
    highlightRanges(title, terms).forEach { range ->
        if (range.first >= 0 && range.last < title.length) {
            addStyle(SpanStyle(background = background), range.first, range.last + 1)
        }
    }
}
