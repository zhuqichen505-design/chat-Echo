@file:OptIn(ExperimentalMaterial3Api::class)

package com.aiassistant.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.theme.EchoFolderColors
import com.aiassistant.AiAssistantApp
import com.aiassistant.domain.model.Folder
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.EchoGlassDropdownMenu
import com.aiassistant.ui.components.EchoGlassPagePanelShape
import com.aiassistant.ui.components.EchoWallpaperBackground
import com.aiassistant.ui.components.echoGlassPalette
import com.aiassistant.ui.components.echoHazePanel
import com.aiassistant.ui.components.echoShapeClick
import com.aiassistant.ui.components.rememberEchoHazeState
import com.aiassistant.utils.BackgroundImageManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManagerScreen(
    onNavigateBack: () -> Unit,
    onFolderSelected: (Long?) -> Unit
) {
    val repository = AiAssistantApp.instance.repository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val folderBackgroundBitmap = remember(context) {
        BackgroundImageManager.getHomeBackgroundBitmap(context)
    }
    val hazeState = rememberEchoHazeState()
    val folders by repository.getAllFolders().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<Folder?>(null) }

    EchoWallpaperBackground(
        backgroundBitmap = folderBackgroundBitmap,
        hazeState = hazeState
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("文件夹管理") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            },
            floatingActionButton = {
                val fabShape = RoundedCornerShape(22.dp)
                Surface(
                    modifier = Modifier
                        .height(56.dp)
                        .echoHazePanel(
                            hazeState = hazeState,
                            shape = fabShape,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            blurRadius = 28.dp
                        )
                        .echoShapeClick(fabShape) { showAddDialog = true },
                    shape = fabShape,
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        Text("新建文件夹", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "全部"选项
                item {
                    FolderItem(
                        hazeState = hazeState,
                        folder = null,
                        onClick = { onFolderSelected(null) },
                        onEdit = {},
                        onDelete = {}
                    )
                }

                // "未分类"选项
                item {
                    FolderShortcutItem(
                        hazeState = hazeState,
                        title = "未分类",
                        icon = Icons.Default.FolderOff,
                        onClick = { onFolderSelected(-1) }
                    )
                }

                if (folders.isNotEmpty()) {
                    item {
                        Text(
                            text = "文件夹",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                items(folders) { folder ->
                    FolderItem(
                        hazeState = hazeState,
                        folder = folder,
                        onClick = { onFolderSelected(folder.id) },
                        onEdit = { editingFolder = folder },
                        onDelete = {
                            scope.launch {
                                repository.deleteFolder(folder.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // 添加文件夹对话框
    if (showAddDialog) {
        FolderEditDialog(
            hazeState = hazeState,
            folder = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, icon, color ->
                scope.launch {
                    repository.createFolder(name, icon = icon, color = color)
                }
                showAddDialog = false
            }
        )
    }

    // 编辑文件夹对话框
    editingFolder?.let { folder ->
        FolderEditDialog(
            hazeState = hazeState,
            folder = folder,
            onDismiss = { editingFolder = null },
            onSave = { name, icon, color ->
                scope.launch {
                    repository.updateFolder(folder.copy(name = name, icon = icon, color = color))
                }
                editingFolder = null
            }
        )
    }
}

@Composable
fun FolderItem(
    hazeState: dev.chrisbanes.haze.HazeState,
    folder: Folder?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = echoGlassPalette().panel,
                blurRadius = 18.dp
            )
            .echoShapeClick(EchoGlassPagePanelShape, onClick = onClick),
        shape = EchoGlassPagePanelShape,
        color = echoGlassPalette().panel,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件夹图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (folder != null && folder.color > 0)
                            folderColors.getOrElse(folder.color - 1) { MaterialTheme.colorScheme.primary }
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = folderIcons[folder?.icon] ?: Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (folder != null && folder.color > 0) Color.White
                           else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder?.name ?: "全部对话",
                    style = MaterialTheme.typography.titleMedium
                )
                // 这里可以显示文件夹中的对话数量
            }

            if (folder != null) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    EchoGlassDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                onEdit()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
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
    }

    if (showDeleteDialog) {
        EchoGlassDialog(
            hazeState = hazeState,
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除文件夹") },
            text = { Text("确定要删除这个文件夹吗？文件夹中的对话将被移至未分类。") },
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
private fun FolderShortcutItem(
    hazeState: dev.chrisbanes.haze.HazeState,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = EchoGlassPagePanelShape,
                tint = echoGlassPalette().panel,
                blurRadius = 18.dp
            )
            .echoShapeClick(EchoGlassPagePanelShape, onClick = onClick),
        shape = EchoGlassPagePanelShape,
        color = echoGlassPalette().panel,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun FolderEditDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    folder: Folder?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: Int) -> Unit
) {
    var name by remember { mutableStateOf(folder?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(folder?.icon ?: "folder") }
    var selectedColor by remember { mutableIntStateOf(folder?.color ?: 0) }

    val folderColors = listOf(
        0 to "默认",
        1 to "红",
        2 to "橙",
        3 to "黄",
        4 to "天蓝",
        5 to "蓝",
        6 to "紫",
        7 to "靛蓝",
        8 to "棕"
    )

    val folderIcons = listOf(
        "folder" to "文件夹",
        "star" to "星标",
        "work" to "工作",
        "school" to "学习",
        "code" to "代码",
        "chat" to "对话",
        "favorite" to "收藏",
        "bookmark" to "书签"
    )

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = { Text(if (folder == null) "新建文件夹" else "编辑文件夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("文件夹名称") },
                    singleLine = true
                )

                // 颜色选择
                Text("选择颜色", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    folderColors.forEach { (colorIndex, colorName) ->
                        val color = EchoFolderColors.pastelPalette.getOrElse(colorIndex - 1) {
                            MaterialTheme.colorScheme.primaryContainer
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = colorIndex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == colorIndex) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (colorIndex == 0) MaterialTheme.colorScheme.onPrimaryContainer
                                           else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 图标选择
                Text("选择图标", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    folderIcons.take(5).forEach { (iconName, _) ->
                        val icon = when (iconName) {
                            "star" -> Icons.Default.Star
                            "work" -> Icons.Default.Work
                            "school" -> Icons.Default.School
                            "code" -> Icons.Default.Code
                            "chat" -> Icons.AutoMirrored.Filled.Chat
                            "favorite" -> Icons.Default.Favorite
                            "bookmark" -> Icons.Default.Bookmark
                            else -> Icons.Default.Folder
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedIcon == iconName)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedIcon = iconName },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (selectedIcon == iconName)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, selectedIcon, selectedColor) },
                enabled = name.isNotBlank()
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
