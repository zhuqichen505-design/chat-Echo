@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.aiassistant.ui.screens.home

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import com.aiassistant.ui.theme.EchoFolderColors
import com.aiassistant.ui.theme.EchoTokens
import com.aiassistant.ui.components.echoShapeCombinedClick
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiassistant.R
import com.aiassistant.domain.model.ApiConfig
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Folder
import com.aiassistant.utils.BackupManager
import android.widget.Toast
import com.aiassistant.ui.components.CardEmphasis
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.SideAnchorItem
import com.aiassistant.ui.components.SideAnchorNavigator
import com.aiassistant.ui.components.TransientLazyListScrollbar
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoHazeSource
import com.aiassistant.ui.components.readableTextColorFor
import com.aiassistant.ui.components.rememberReadableBackdropColor
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.ui.components.rememberLazyListControlsVisible
import com.aiassistant.ui.components.echoSwitchColors
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.BackgroundImageManager
import java.text.SimpleDateFormat
import java.util.*

// 批量删除弹窗专属"柔和删除"视觉（方案 §九：保留浅红柔和删除风格，不入语义 error）
private val DeleteDialogPink = Color(0xFFFFE4E6)
private val DeleteDialogContent = Color(0xFFBE123C)
private const val EchoWordmarkActiveVariant = "liquid-script"

private data class EchoWordmarkPreset(
    val id: String,
    val typefaceStyle: Int,
    val textScale: Float,
    val strokeWidth: Float,
    val underlineAlpha: Float,
    val accentAlpha: Float
)

private val EchoWordmarkPresets = listOf(
    EchoWordmarkPreset("liquid-script", Typeface.BOLD_ITALIC, 1.00f, 1.8f, 0.36f, 0.90f),
    EchoWordmarkPreset("crystal-serif", Typeface.BOLD, 0.96f, 1.35f, 0.28f, 0.72f),
    EchoWordmarkPreset("haze-calligraphy", Typeface.ITALIC, 1.04f, 2.2f, 0.42f, 0.84f),
    EchoWordmarkPreset("aurora-compact", Typeface.BOLD_ITALIC, 0.90f, 1.55f, 0.30f, 0.78f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToChat: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToRoleplayStudio: () -> Unit = {}
) {
    val context = LocalContext.current
    // v2.7.3 流畅度：对话备份涉及 DB 全量查询+序列化+写盘，统一经 IO 协程执行，点击回调不再冻结主线程
    val backupScope = rememberCoroutineScope()
    val homeBackgroundBitmap = remember(context) {
        BackgroundImageManager.getHomeBackgroundBitmap(context)
    }
    val viewModel: HomeViewModel = viewModel()
    val conversations by viewModel.conversations.collectAsState()
    val apiConfigs by viewModel.apiConfigs.collectAsState()
    val visibleModelOptions by viewModel.visibleModelOptions.collectAsState()
    val selectedConfig by viewModel.selectedConfig.collectAsState()
    val showNewChatDialog by viewModel.showNewChatDialog.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val hazeState = rememberEchoHazeState()
    val readableBackdrop = rememberReadableBackdropColor(homeBackgroundBitmap)
    val conversationListState = rememberLazyListState()
    val showScrollControls by rememberLazyListControlsVisible(conversationListState)
    var searchQuery by remember { mutableStateOf("") }
    var pinnedExpanded by rememberSaveable { mutableStateOf(true) }
    var selectedConversationIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    val filteredConversations = remember(conversations, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.modelName.contains(query, ignoreCase = true) ||
                    it.systemPrompt?.contains(query, ignoreCase = true) == true
            }
        }
    }
    val visibleConversationIds = remember(filteredConversations) {
        filteredConversations.map { it.id }.toSet()
    }
    val selectedConversations = remember(filteredConversations, selectedConversationIds) {
        filteredConversations.filter { it.id in selectedConversationIds }
    }
    val homeNavItems = remember(filteredConversations, pinnedExpanded) {
        buildHomeAnchorItems(filteredConversations, pinnedExpanded)
    }
    val isSelectionMode = selectedConversationIds.isNotEmpty()
    val selectedAreAllInFolders = selectedConversations.isNotEmpty() &&
        selectedConversations.all { it.folderId != null }

    fun toggleConversationSelection(conversationId: Long) {
        selectedConversationIds = if (conversationId in selectedConversationIds) {
            selectedConversationIds - conversationId
        } else {
            selectedConversationIds + conversationId
        }
    }

    LaunchedEffect(visibleConversationIds) {
        val visibleSelection = selectedConversationIds.intersect(visibleConversationIds)
        if (visibleSelection.size != selectedConversationIds.size) {
            selectedConversationIds = visibleSelection
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (conversations.isNotEmpty() && !isSelectionMode) {
                NewConversationFab(
                    hazeState = hazeState,
                    readableBackdrop = readableBackdrop,
                    onClick = {
                        viewModel.createDefaultConversation { conversationId ->
                            onNavigateToChat(conversationId)
                        }
                    },
                    onLongClick = { viewModel.showNewChatDialog() }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .echoHazeSource(hazeState)
            ) {
                homeBackgroundBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null, // decorative
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = paddingValues.calculateBottomPadding())
            ) {
                HomeDashboardHeader(
                    hazeState = hazeState,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToStats = onNavigateToStats,
                    onNavigateToRoleplayStudio = onNavigateToRoleplayStudio
                )

                HomeSearchRow(
                    hazeState = hazeState,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onNavigateToHistory = onNavigateToHistory,
                    readableBackdrop = readableBackdrop
                )

                // 文件夹选择栏
                FolderSelector(
                    hazeState = hazeState,
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    onFolderSelected = { viewModel.selectFolder(it) },
                    onManageFolders = onNavigateToFolders,
                    readableBackdrop = readableBackdrop
                )

            AnimatedVisibility(visible = isSelectionMode) {
                BatchSelectionBar(
                    selectedCount = selectedConversationIds.size,
                    moveActionText = if (selectedAreAllInFolders) "\u79fb\u51fa\u6587\u4ef6\u5939" else "\u79fb\u5165\u6587\u4ef6\u5939",
                    onClearSelection = { selectedConversationIds = emptySet() },
                    onPinSelected = {
                        viewModel.setPinned(selectedConversationIds, true)
                        selectedConversationIds = emptySet()
                    },
                    onUnpinSelected = {
                        viewModel.setPinned(selectedConversationIds, false)
                        selectedConversationIds = emptySet()
                    },
                    onMoveSelected = {
                        if (selectedAreAllInFolders) {
                            viewModel.moveConversationsToFolder(selectedConversationIds, null)
                            selectedConversationIds = emptySet()
                        } else {
                            showBatchMoveDialog = true
                        }
                    },
                    onHideSelected = {
                        viewModel.hideConversations(selectedConversationIds)
                        selectedConversationIds = emptySet()
                    },
                    onDeleteSelected = {
                        showBatchDeleteDialog = true
                    }
                )
            }

            if (filteredConversations.isEmpty()) {
                EmptyHomeContent(
                    modifier = Modifier.weight(1f),
                    hazeState = hazeState,
                    readableBackdrop = readableBackdrop,
                    onStartChat = {
                        viewModel.createDefaultConversation { conversationId ->
                            onNavigateToChat(conversationId)
                        }
                    },
                    isSearching = searchQuery.isNotBlank()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        state = conversationListState,
                        // v2.7.10 需求 2：左右间距对称（end 28dp→16dp）——原先右侧多出的 12dp 是为
                        // 右缘字母锚点条（24dp+4dp 边距，仅滚动时短暂显示）预留的恒定空距；
                        // 收窄后锚点条显示时恰好只覆盖卡片右侧留白条，不遮挡任何交互元素
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 置顶对话
                        val pinnedConversations = filteredConversations.filter { it.isPinned }
                        if (pinnedConversations.isNotEmpty()) {
                            item {
                                PinnedSectionHeader(
                                    count = pinnedConversations.size,
                                    expanded = pinnedExpanded,
                                    onToggle = { pinnedExpanded = !pinnedExpanded }
                                )
                            }
                            if (pinnedExpanded) {
                                items(
                                    items = pinnedConversations,
                                    key = { "pinned_${it.id}" }
                                ) { conversation ->
                                    ConversationCard(
                                        hazeState = hazeState,
                                        conversation = conversation,
                                        selected = conversation.id in selectedConversationIds,
                                        selectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                toggleConversationSelection(conversation.id)
                                            } else {
                                                onNavigateToChat(conversation.id)
                                            }
                                        },
                                        onLongClick = { toggleConversationSelection(conversation.id) },
                                        onDelete = { viewModel.deleteConversation(conversation) },
                                        onPin = { viewModel.togglePin(conversation) },
                                        onDuplicate = {
                                            viewModel.duplicateConversation(conversation.id) {
                                                Toast.makeText(context, "已成功复制对话", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onBackup = {
                                            backupScope.launch(Dispatchers.IO) {
                                                val backupPath = BackupManager.createSingleConversationBackup(context, conversation.id)
                                                withContext(Dispatchers.Main) {
                                                    if (backupPath != null) {
                                                        Toast.makeText(context, "对话已备份至 Echo_Backups", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "对话备份失败", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        onMoveToFolder = { folderId ->
                                            viewModel.moveToFolder(conversation.id, folderId)
                                        },
                                        onRename = { newTitle ->
                                            viewModel.renameConversation(conversation.id, newTitle)
                                        },
                                        onHide = {
                                            viewModel.hideConversations(listOf(conversation.id))
                                        },
                                        onCreateFolder = { name, icon, color ->
                                            viewModel.createFolder(name, icon, color)
                                        },
                                        onUpdateFolder = { folder, name, icon, color ->
                                            viewModel.updateFolder(folder, name, icon, color)
                                        },
                                        folders = folders
                                    )
                                }
                            }
                        }

                        // 普通对话
                        val normalConversations = filteredConversations.filter { !it.isPinned }
                        if (normalConversations.isNotEmpty() && pinnedConversations.isNotEmpty()) {
                            item {
                                Text(
                                    text = "其他",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                        items(
                            items = normalConversations,
                            key = { it.id }
                        ) { conversation ->
                            ConversationCard(
                                hazeState = hazeState,
                                conversation = conversation,
                                selected = conversation.id in selectedConversationIds,
                                selectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        toggleConversationSelection(conversation.id)
                                    } else {
                                        onNavigateToChat(conversation.id)
                                    }
                                },
                                onLongClick = { toggleConversationSelection(conversation.id) },
                                onDelete = { viewModel.deleteConversation(conversation) },
                                onPin = { viewModel.togglePin(conversation) },
                                onDuplicate = {
                                    viewModel.duplicateConversation(conversation.id) {
                                        Toast.makeText(context, "已成功复制对话", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onBackup = {
                                    backupScope.launch(Dispatchers.IO) {
                                        val backupPath = BackupManager.createSingleConversationBackup(context, conversation.id)
                                        withContext(Dispatchers.Main) {
                                            if (backupPath != null) {
                                                Toast.makeText(context, "对话已备份至 Echo_Backups", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "对话备份失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onMoveToFolder = { folderId ->
                                    viewModel.moveToFolder(conversation.id, folderId)
                                },
                                onRename = { newTitle ->
                                    viewModel.renameConversation(conversation.id, newTitle)
                                },
                                onHide = {
                                    viewModel.hideConversations(listOf(conversation.id))
                                },
                                onCreateFolder = { name, icon, color ->
                                    viewModel.createFolder(name, icon, color)
                                },
                                onUpdateFolder = { folder, name, icon, color ->
                                    viewModel.updateFolder(folder, name, icon, color)
                                },
                                folders = folders
                            )
                        }

                        // 底部间距
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                    TransientLazyListScrollbar(
                        listState = conversationListState,
                        visible = showScrollControls,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                    )
                }
            }
        }
            SideAnchorNavigator(
                items = homeNavItems,
                listState = conversationListState,
                visible = showScrollControls,
                hazeState = hazeState,
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 4.dp)
            )
        }
    }

    if (showBatchMoveDialog) {
        val commonFolderId = selectedConversations
            .map { it.folderId }
            .distinct()
            .singleOrNull()

        MoveToFolderDialog(
            hazeState = hazeState,
            folders = folders,
            currentFolderId = commonFolderId,
            onDismiss = { showBatchMoveDialog = false },
            onCreateFolder = { name, icon, color ->
                viewModel.createFolder(name, icon, color)
            },
            onUpdateFolder = { folder, name, icon, color ->
                viewModel.updateFolder(folder, name, icon, color)
            },
            onMove = { folderId ->
                viewModel.moveConversationsToFolder(selectedConversationIds, folderId)
                selectedConversationIds = emptySet()
                showBatchMoveDialog = false
            }
        )
    }

    if (showBatchDeleteDialog) {
        BatchDeleteConfirmDialog(
            hazeState = hazeState,
            selectedCount = selectedConversationIds.size,
            onDismiss = { showBatchDeleteDialog = false },
            onConfirm = {
                viewModel.deleteConversations(selectedConversationIds)
                selectedConversationIds = emptySet()
                showBatchDeleteDialog = false
            }
        )
    }

    // 新对话对话框
    if (showNewChatDialog) {
        NewChatDialog(
            hazeState = hazeState,
            selectedConfig = selectedConfig,
            configs = apiConfigs,
            visibleModelOptions = visibleModelOptions,
            folders = folders,
            currentFolderId = selectedFolderId?.takeUnless { it == -2L || it == -1L },
            onDismiss = { viewModel.hideNewChatDialog() },
            onCreate = { title, prompt, folderId, newFolderName, config, modelName, isPrivate ->
                if (newFolderName.isNullOrBlank()) {
                    viewModel.createNewConversation(title, prompt, folderId, config, modelName, isPrivate) { conversationId ->
                        onNavigateToChat(conversationId)
                    }
                } else {
                    viewModel.createConversationInNewFolder(title, prompt, newFolderName, config, modelName, isPrivate) { conversationId ->
                        onNavigateToChat(conversationId)
                    }
                }
            }
        )
    }
}

private fun buildHomeAnchorItems(
    conversations: List<Conversation>,
    pinnedExpanded: Boolean
): List<SideAnchorItem> {
    val pinned = conversations.filter { it.isPinned }
    val normal = conversations.filter { !it.isPinned }
    val anchors = mutableListOf<SideAnchorItem>()
    var index = 0

    if (pinned.isNotEmpty()) {
        index += 1 // pinned section header
        if (pinnedExpanded) {
            pinned.forEach { conversation ->
                anchors += SideAnchorItem(
                    title = anchorTitle(conversation.title),
                    itemIndex = index
                )
                index += 1
            }
        }
    }

    if (normal.isNotEmpty() && pinned.isNotEmpty()) {
        index += 1 // "other" section header
    }

    normal.forEach { conversation ->
        anchors += SideAnchorItem(
            title = anchorTitle(conversation.title),
            itemIndex = index
        )
        index += 1
    }

    return anchors
}

private fun anchorTitle(value: String): String {
    return value
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(26)
        ?: "未命名对话"
}

@Composable
fun HomeDashboardHeader(
    hazeState: dev.chrisbanes.haze.HazeState,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToRoleplayStudio: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EchoWordmark(modifier = Modifier.weight(1f))
            GlassHomeIconButton(
                hazeState = hazeState,
                icon = Icons.Default.AutoStories,
                contentDescription = "角色与创作",
                onClick = onNavigateToRoleplayStudio,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatsIconButton(
                hazeState = hazeState,
                onClick = onNavigateToStats,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassHomeIconButton(
                hazeState = hazeState,
                icon = Icons.Default.Settings,
                contentDescription = "设置",
                onClick = onNavigateToSettings,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
private fun NewConversationFab(
    hazeState: dev.chrisbanes.haze.HazeState,
    readableBackdrop: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    NewConversationGlassButton(
        hazeState = hazeState,
        readableBackdrop = readableBackdrop,
        modifier = Modifier.height(54.dp),
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "配置新对话"
    )
}

@Composable
private fun NewConversationGlassButton(
    hazeState: dev.chrisbanes.haze.HazeState,
    readableBackdrop: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null
) {
    val buttonShape = com.aiassistant.ui.theme.EchoTokens.Radius.shapePill
    val glass = echoGlassPalette()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primary = MaterialTheme.colorScheme.primary
    val tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.92f else 0.95f)
    val content = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = modifier
            .shadow(
                elevation = EchoTokens.Elevation.overlay(isDark).elevation,
                shape = buttonShape,
                ambientColor = EchoTokens.Elevation.overlay(isDark).ambient,
                spotColor = EchoTokens.Elevation.overlay(isDark).spot
            )
            .echoShapeCombinedClick(
                shape = buttonShape,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel
            ),
        shape = buttonShape,
        color = tint,
        contentColor = content,
        border = BorderStroke(1.dp, glass.outlineSelected),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null) // decorative
            Text(
                text = "新对话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EchoWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ECHO",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp // 字距参数（非字号），品牌字标需要，场景特殊保留
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(5.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun StatsIconButton(
    hazeState: dev.chrisbanes.haze.HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = echoGlassPalette()
    val buttonTint = glass.control
    val glassBlue = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = buttonTint,
        contentColor = glassBlue,
        border = BorderStroke(0.8.dp, glass.outline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .echoShapeClick(CircleShape, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(glassBlue.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .width(15.dp)
                        .height(15.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    listOf(5.dp, 9.dp, 13.dp).forEach { barHeight ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barHeight)
                                .background(glassBlue, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassHomeIconButton(
    hazeState: dev.chrisbanes.haze.HazeState,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = echoGlassPalette()
    val buttonTint = glass.control
    val glassBlue = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = buttonTint,
        contentColor = glassBlue,
        border = BorderStroke(0.8.dp, glass.outline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .echoShapeClick(CircleShape, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = glassBlue
            )
        }
    }
}

@Composable
private fun HomeSearchRow(
    hazeState: dev.chrisbanes.haze.HazeState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    readableBackdrop: Color
) {
    val glass = echoGlassPalette()
    val searchTint = glass.input
    val searchTextColor = readableTextColorFor(
        background = searchTint,
        fallbackSurface = readableBackdrop
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(22.dp),
            color = searchTint,
            border = BorderStroke(0.8.dp, glass.outline),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null, // decorative
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = searchTextColor),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isBlank()) {
                                Text(
                                    text = "搜索对话、模型或提示词",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = searchTextColor.copy(alpha = 0.62f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "清空搜索",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        GlassHomeIconButton(
            hazeState = hazeState,
            icon = Icons.Default.History,
            contentDescription = "历史记录",
            onClick = onNavigateToHistory,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun BatchSelectionBar(
    selectedCount: Int,
    moveActionText: String,
    onClearSelection: () -> Unit,
    onPinSelected: () -> Unit,
    onUnpinSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onHideSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val barShape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = barShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已选择 $selectedCount 个对话",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "退出多选",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onPinSelected,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("置顶", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onUnpinSelected,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("取消置顶", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onMoveSelected,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(moveActionText, maxLines = 1)
                }
            }

            FilledTonalButton(
                onClick = onHideSelected,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp)
            ) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null, // decorative
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("隐藏对话")
            }

            FilledTonalButton(
                onClick = onDeleteSelected,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DeleteDialogPink,
                    contentColor = DeleteDialogContent
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null, // decorative
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("批量删除")
            }
        }
    }
}

@Composable
private fun BatchDeleteConfirmDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        containerColor = DeleteDialogPink,
        icon = {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null, // decorative
                tint = DeleteDialogContent
            )
        },
        title = {
            Text(
                text = "确认删除 $selectedCount 个对话？",
                color = DeleteDialogContent
            )
        },
        text = {
            Text(
                text = "删除后这些对话和消息记录将无法恢复。请再次确认是否继续删除。",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeleteDialogContent,
                    contentColor = Color.White
                )
            ) {
                Text("确认删除")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = DeleteDialogContent)
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun PinnedSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val headerShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoShapeClick(headerShape, onClick = onToggle),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PushPin,
                contentDescription = null, // decorative
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "置顶",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起置顶对话" else "展开置顶对话",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HomeMetric(
    modifier: Modifier = Modifier,
    value: String? = null,
    label: String,
    onClick: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null
) {
    val metricShape = RoundedCornerShape(20.dp)
    val clickableModifier = if (onClick != null) {
        modifier.echoShapeClick(metricShape, onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = clickableModifier.height(64.dp),
        shape = metricShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    icon()
                } else {
                    Text(
                        text = value.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniStatsIcon() {
    Row(
        modifier = Modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(10.dp, 17.dp, 13.dp, 22.dp).forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index == 3) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    )
            )
        }
    }
}

@Composable
fun FolderSelector(
    hazeState: dev.chrisbanes.haze.HazeState,
    folders: List<Folder>,
    selectedFolderId: Long?,
    onFolderSelected: (Long?) -> Unit,
    onManageFolders: () -> Unit,
    readableBackdrop: Color
) {
    val folderColors = EchoFolderColors.pastelPalette

    val folderIcons = mapOf(
        "folder" to Icons.Default.Folder,
        "star" to Icons.Default.Star,
        "work" to Icons.Default.Work,
        "school" to Icons.Default.School,
        "code" to Icons.Default.Code,
        "chat" to Icons.AutoMirrored.Filled.Chat,
        "favorite" to Icons.Default.Favorite,
        "bookmark" to Icons.Default.Bookmark
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            HomeGlassChip(
                hazeState = hazeState,
                selected = selectedFolderId == null,
                onClick = { onFolderSelected(null) },
                label = "对话",
                readableBackdrop = readableBackdrop,
                leadingIcon = {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null, // decorative
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        item {
            HomeGlassChip(
                hazeState = hazeState,
                selected = selectedFolderId == -2L,
                onClick = { onFolderSelected(-2L) },
                label = "置顶",
                readableBackdrop = readableBackdrop,
                leadingIcon = {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null, // decorative
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        items(folders) { folder ->
            val color = folderColors.getOrElse(folder.color - 1) { MaterialTheme.colorScheme.primary }
            val icon = folderIcons[folder.icon] ?: Icons.Default.Folder

            HomeGlassChip(
                hazeState = hazeState,
                selected = selectedFolderId == folder.id,
                onClick = { onFolderSelected(folder.id) },
                label = folder.name,
                readableBackdrop = readableBackdrop,
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (selectedFolderId == folder.id) color else color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null, // decorative
                            modifier = Modifier.size(14.dp),
                            tint = if (selectedFolderId == folder.id) Color.White else color
                        )
                    }
                }
            )
        }

        item {
            HomeGlassChip(
                hazeState = hazeState,
                selected = false,
                onClick = onManageFolders,
                label = "添加",
                readableBackdrop = readableBackdrop,
                leadingIcon = {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = null, // decorative
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun HomeGlassChip(
    hazeState: dev.chrisbanes.haze.HazeState,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    readableBackdrop: Color,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val chipShape = RoundedCornerShape(999.dp)
    val glass = echoGlassPalette()
    val tint = if (selected) {
        glass.controlSelected
    } else {
        glass.control
    }
    val defaultContentColor = readableTextColorFor(
        background = tint,
        fallbackSurface = readableBackdrop
    )
    val chipContentColor = if (selected) MaterialTheme.colorScheme.primary else defaultContentColor
    Surface(
        modifier = Modifier
            .height(38.dp)
            .echoShapeClick(chipShape, onClick = onClick),
        shape = chipShape,
        color = tint,
        contentColor = chipContentColor,
        border = BorderStroke(if (selected) 1.2.dp else 0.8.dp, if (selected) glass.outlineSelected else glass.outline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            leadingIcon?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = chipContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ConfigSelector(
    configs: List<ApiConfig>,
    selectedConfig: ApiConfig?,
    onConfigSelected: (ApiConfig) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = selectedConfig?.name ?: "选择模型",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null) // decorative
        }

        EchoGlassDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            configs.forEach { config ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(config.name)
                            Row {
                                Text(
                                    text = com.aiassistant.domain.model.ModelDisplayName.format(config.modelName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onConfigSelected(config)
                        expanded = false
                    },
                    trailingIcon = {
                        if (config.isDefault) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "默认",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    leadingIcon = {
                        if (config.id == selectedConfig?.id) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null, // decorative
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }

            if (configs.isNotEmpty()) {
                HorizontalDivider()
            }

            DropdownMenuItem(
                text = { Text("管理API配置") },
                onClick = {
                    expanded = false
                    onNavigateToSettings()
                },
                leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = null) // decorative
                }
            )
        }
    }
}

@Composable
private fun HomeModelAvatar(
    apiConfigId: Long?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val modelAvatarBitmap = remember(context, apiConfigId) {
        AvatarManager.getPreferredModelAvatarBitmap(context, apiConfigId)
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (modelAvatarBitmap != null) {
            Image(
                bitmap = modelAvatarBitmap.asImageBitmap(),
                contentDescription = "模型头像",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.deepseek),
                contentDescription = "模型头像",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationCard(
    hazeState: dev.chrisbanes.haze.HazeState,
    conversation: Conversation,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onDuplicate: () -> Unit,
    onBackup: () -> Unit,
    onMoveToFolder: (Long?) -> Unit,
    onRename: (String) -> Unit,
    onHide: () -> Unit,
    onCreateFolder: (String, String, Int) -> Unit,
    onUpdateFolder: (Folder, String, String, Int) -> Unit,
    folders: List<Folder>
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val glass = com.aiassistant.ui.components.echoGlassPalette()
    val cardShape = com.aiassistant.ui.theme.EchoTokens.Radius.shapeLg
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardTint = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.92f else 0.96f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.88f else 0.92f)
    }
    EchoGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "多选对话",
        shape = cardShape,
        containerColor = cardTint,
        highlight = selected,
        emphasis = CardEmphasis.Subdued
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (selected) "已选择" else "未选择",
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            HomeModelAvatar(
                apiConfigId = conversation.apiConfigId,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 对话信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (conversation.isPinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "置顶",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 模型标签：增加弹性权重与不折行保护，超长截断，绝不挤压后续消息数量
                    Surface(
                        modifier = Modifier
                            .height(24.dp)
                            .weight(1f, fill = false),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = com.aiassistant.domain.model.ModelDisplayName.format(conversation.modelName),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }

                    // 消息数量：保持完整展示与单行防折行
                    Text(
                        text = "${conversation.messageCount} 条消息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (!selectionMode) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    EchoGlassDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名", fontWeight = FontWeight.Medium) },
                            onClick = {
                                showRenameDialog = true
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) // decorative
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (conversation.isPinned) "取消置顶" else "置顶", fontWeight = FontWeight.Medium) },
                            onClick = {
                                onPin()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null, // decorative
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("复制对话", fontWeight = FontWeight.Medium) },
                            onClick = {
                                onDuplicate()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null, // decorative
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("备份此对话", fontWeight = FontWeight.Medium) },
                            onClick = {
                                onBackup()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Backup,
                                    contentDescription = null, // decorative
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("移动到文件夹", fontWeight = FontWeight.Medium) },
                            onClick = {
                                showMoveToFolderDialog = true
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary) // decorative
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("隐藏", fontWeight = FontWeight.Medium) },
                            onClick = {
                                onHide()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) // decorative
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = glass.outline.copy(alpha = 0.5f))
                        DropdownMenuItem(
                            text = { Text("删除", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showDeleteDialog = true
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null, // decorative
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
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

    // 移动到文件夹对话框
    if (showMoveToFolderDialog) {
        MoveToFolderDialog(
            hazeState = hazeState,
            folders = folders,
            currentFolderId = conversation.folderId,
            onDismiss = { showMoveToFolderDialog = false },
            onCreateFolder = onCreateFolder,
            onUpdateFolder = onUpdateFolder,
            onMove = { folderId ->
                onMoveToFolder(folderId)
                showMoveToFolderDialog = false
            }
        )
    }

    // 重命名对话框
    if (showRenameDialog) {
        RenameConversationDialog(
            hazeState = hazeState,
            currentTitle = conversation.title,
            onDismiss = { showRenameDialog = false },
            onRename = { newTitle ->
                onRename(newTitle)
                showRenameDialog = false
            }
        )
    }
}

@Composable
fun MoveToFolderDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    folders: List<Folder>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onCreateFolder: (String, String, Int) -> Unit,
    onUpdateFolder: (Folder, String, String, Int) -> Unit,
    onMove: (Long?) -> Unit
) {
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<Folder?>(null) }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = { Text("移动到文件夹") },
        text = {
            Column {
                OutlinedButton(
                    onClick = { showCreateFolderDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = null, // decorative
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新建文件夹")
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    item {
                        val unfiledShape = RoundedCornerShape(16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .echoShapeClick(unfiledShape) { onMove(null) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FolderOff,
                                contentDescription = null, // decorative
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("未分类", modifier = Modifier.weight(1f))
                            if (currentFolderId == null) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null, // decorative
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    items(folders) { folder ->
                        val folderRowShape = RoundedCornerShape(16.dp)
                        val folderColors = EchoFolderColors.pastelPalette
                        val color = folderColors.getOrElse(folder.color - 1) {
                            MaterialTheme.colorScheme.primary
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .echoShapeClick(folderRowShape) { onMove(folder.id) }
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null, // decorative
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentFolderId == folder.id) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null, // decorative
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { editingFolder = folder },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "重命名文件夹",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (folders.isEmpty()) {
                        item {
                            Text(
                                text = "还没有文件夹，可以先新建一个。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showCreateFolderDialog) {
        FolderEditDialog(
            hazeState = hazeState,
            folder = null,
            onDismiss = { showCreateFolderDialog = false },
            onSave = { name, icon, color ->
                onCreateFolder(name, icon, color)
                showCreateFolderDialog = false
            }
        )
    }

    editingFolder?.let { folder ->
        FolderEditDialog(
            hazeState = hazeState,
            folder = folder,
            onDismiss = { editingFolder = null },
            onSave = { name, icon, color ->
                onUpdateFolder(folder, name, icon, color)
                editingFolder = null
            }
        )
    }
}

@Composable
fun RenameConversationDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    currentTitle: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("对话标题") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(title) },
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun EmptyHomeContent(
    modifier: Modifier = Modifier,
    hazeState: dev.chrisbanes.haze.HazeState,
    readableBackdrop: Color,
    onStartChat: () -> Unit,
    isSearching: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标 - 使用更现代的设计
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null, // decorative
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isSearching) "没有找到匹配的对话" else "开始新的对话",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSearching) "换个关键词试试，或直接开始一段新对话" else "与AI助手交流，获取帮助和灵感",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isSearching) {
            NewConversationGlassButton(
                hazeState = hazeState,
                readableBackdrop = readableBackdrop,
                onClick = onStartChat,
                modifier = Modifier.height(56.dp)
            )
        }
    }
}

@Composable
fun NewChatDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    selectedConfig: ApiConfig?,
    configs: List<ApiConfig>,
    visibleModelOptions: List<com.aiassistant.domain.model.ChatModelOption>,
    folders: List<Folder>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onCreate: (String, String?, Long?, String?, ApiConfig?, String?, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf(currentFolderId) }
    var createNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedApiConfig by remember(selectedConfig, configs) {
        mutableStateOf(selectedConfig ?: configs.firstOrNull())
    }
    var selectedModelName by remember(selectedApiConfig) {
        mutableStateOf(selectedApiConfig?.modelName.orEmpty())
    }
    var showModelPickerDialog by remember { mutableStateOf(false) }
    var isPrivate by remember { mutableStateOf(false) }

    val availableModels = remember(selectedApiConfig, visibleModelOptions) {
        val cfg = selectedApiConfig ?: return@remember emptyList<String>()
        val options = visibleModelOptions.filter { it.apiConfigId == cfg.id }.map { it.modelName }
        if (options.isNotEmpty()) {
            options.filter { it.isNotBlank() }.distinct()
        } else {
            val list = mutableListOf<String>()
            if (cfg.modelName.isNotBlank()) list.add(cfg.modelName.trim())
            val raw = cfg.availableModels
            if (!raw.isNullOrBlank()) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                    val parsed: List<String> = com.google.gson.Gson().fromJson(raw, type) ?: emptyList()
                    list.addAll(parsed.map { it.trim() })
                } catch (_: Exception) {
                    list.addAll(raw.split(',', ';', '\n').map { it.trim() })
                }
            }
            list.filter { it.isNotBlank() }.distinct()
        }
    }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "新对话",
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { showModelPickerDialog = true },
                    enabled = configs.isNotEmpty(),
                    leadingIcon = {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null, // decorative
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Column {
                            Text(
                                text = selectedApiConfig?.name ?: "选择配置",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = com.aiassistant.domain.model.ModelDisplayName.format(selectedModelName.ifBlank { selectedApiConfig?.modelName.orEmpty() }),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "对话标题",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如：代码助手") },
                    singleLine = true
                )

                Text(
                    text = "选择文件夹",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = !createNewFolder && selectedFolderId == null,
                            onClick = {
                                createNewFolder = false
                                selectedFolderId = null
                            },
                            colors = echoFilterChipColors(),
                            border = echoFilterChipBorder(!createNewFolder && selectedFolderId == null),
                            elevation = echoFilterChipElevation(),
                            label = { Text("无") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = createNewFolder,
                            onClick = {
                                createNewFolder = true
                                selectedFolderId = null
                            },
                            colors = echoFilterChipColors(),
                            border = echoFilterChipBorder(createNewFolder),
                            elevation = echoFilterChipElevation(),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CreateNewFolder,
                                    contentDescription = null, // decorative
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text("新建文件夹") }
                        )
                    }
                    items(folders) { folder ->
                        FilterChip(
                            selected = !createNewFolder && selectedFolderId == folder.id,
                            onClick = {
                                createNewFolder = false
                                selectedFolderId = folder.id
                            },
                            colors = echoFilterChipColors(),
                            border = echoFilterChipBorder(!createNewFolder && selectedFolderId == folder.id),
                            elevation = echoFilterChipElevation(),
                            label = { Text(folder.name) }
                        )
                    }
                }

                AnimatedVisibility(visible = createNewFolder) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("文件夹名称") },
                        singleLine = true
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null, // decorative
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("隐私对话", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "退出后立即销毁本次消息记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPrivate,
                            onCheckedChange = { isPrivate = it },
                            colors = echoSwitchColors()
                        )
                    }
                }

                Text(
                    text = "系统提示词（可选）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    placeholder = { Text("定义AI助手的行为和角色...") },
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        title.trim(),
                        systemPrompt.ifBlank { null },
                        if (createNewFolder) null else selectedFolderId,
                        newFolderName.trim().takeIf { createNewFolder && it.isNotBlank() },
                        selectedApiConfig,
                        selectedModelName.ifBlank { null },
                        isPrivate
                    )
                },
                enabled = selectedApiConfig != null && (!createNewFolder || newFolderName.isNotBlank())
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    // 全面模型选择器弹窗
    if (showModelPickerDialog) {
        var modelSearchQuery by remember { mutableStateOf("") }
        var customModelInput by remember { mutableStateOf("") }
        var showCustomInput by remember { mutableStateOf(false) }

        val filteredModels = remember(availableModels, modelSearchQuery) {
            if (modelSearchQuery.isBlank()) availableModels
            else availableModels.filter { it.contains(modelSearchQuery, ignoreCase = true) }
        }

        val glass = echoGlassPalette()

        EchoGlassDialog(
            onDismissRequest = { showModelPickerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) // decorative
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择 API 配置与模型")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("1. 选择 API 配置", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(configs) { cfg ->
                            val isCfgSelected = cfg.id == selectedApiConfig?.id
                            FilterChip(
                                selected = isCfgSelected,
                                onClick = {
                                    selectedApiConfig = cfg
                                    selectedModelName = cfg.modelName
                                },
                                label = { Text(cfg.name) },
                                colors = echoFilterChipColors(),
                                border = echoFilterChipBorder(isCfgSelected),
                                elevation = echoFilterChipElevation(),
                                leadingIcon = {
                                    if (isCfgSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) // decorative
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider()

                    Text("2. 选择或输入模型", style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        value = modelSearchQuery,
                        onValueChange = { modelSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索可用模型...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null)  }, // decorative
                        singleLine = true
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredModels) { model ->
                            val isSelected = model == selectedModelName
                            Surface(
                                onClick = {
                                    selectedModelName = model
                                    showModelPickerDialog = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) glass.controlSelected else glass.control,
                                border = BorderStroke(1.dp, if (isSelected) glass.outlineSelected else glass.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = com.aiassistant.domain.model.ModelDisplayName.format(model),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else glass.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null, // decorative
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!showCustomInput) {
                        TextButton(
                            onClick = { showCustomInput = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) // decorative
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("自定义模型名称")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customModelInput,
                                onValueChange = { customModelInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("如: gpt-4.5-preview") },
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    if (customModelInput.isNotBlank()) {
                                        selectedModelName = customModelInput.trim()
                                        showModelPickerDialog = false
                                    }
                                },
                                enabled = customModelInput.isNotBlank()
                            ) {
                                Text("确定")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPickerDialog = false }) {
                    Text("完成")
                }
            }
        )
    }
}
