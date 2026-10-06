package com.aiassistant.ui.screens.roleplay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiassistant.domain.model.RoleplayMemory
import com.aiassistant.ui.components.EchoConfirmDialog
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.echoFilterChipElevation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplayMemoryScreen(
    viewModel: RoleplayViewModel,
    sessionId: Long,
    onBack: () -> Unit
) {
    val memories by viewModel.memories.collectAsState()
    val pinnedFacts by viewModel.pinnedFacts.collectAsState()
    val plotSummary by viewModel.plotSummary.collectAsState()

    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var showEditSummaryDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<RoleplayMemory?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddMemoryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加记忆")
                    }
                    IconButton(onClick = { showEditSummaryDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑摘要")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 标签页
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("剧情摘要") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("固定事实") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("全部记忆") }
                )
            }

            // 内容区域
            when (selectedTab) {
                0 -> PlotSummaryTab(
                    summary = plotSummary,
                    onEditSummary = { showEditSummaryDialog = true }
                )
                1 -> PinnedFactsTab(
                    facts = pinnedFacts,
                    onUnpin = { viewModel.setMemoryPinned(it.id, false) },
                    onDelete = { viewModel.deleteMemory(it) }
                )
                2 -> AllMemoriesTab(
                    memories = memories,
                    onPin = { viewModel.setMemoryPinned(it.id, !it.isPinned) },
                    onEdit = { editingMemory = it },
                    onDelete = { viewModel.deleteMemory(it) }
                )
            }
        }
    }

    // 添加记忆对话框
    if (showAddMemoryDialog) {
        AddMemoryDialog(
            onDismiss = { showAddMemoryDialog = false },
            onAdd = { type, content ->
                viewModel.addMemory(sessionId, type, content)
                showAddMemoryDialog = false
            }
        )
    }

    // 编辑摘要对话框
    if (showEditSummaryDialog) {
        EditSummaryDialog(
            summary = plotSummary,
            onDismiss = { showEditSummaryDialog = false },
            onSave = { summary ->
                viewModel.savePlotSummary(summary)
                showEditSummaryDialog = false
            }
        )
    }

    // 编辑记忆对话框
    editingMemory?.let { memory ->
        EditMemoryDialog(
            memory = memory,
            onDismiss = { editingMemory = null },
            onSave = { updatedMemory ->
                viewModel.updateMemory(updatedMemory)
                editingMemory = null
            }
        )
    }
}

@Composable
private fun PlotSummaryTab(
    summary: String,
    onEditSummary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (summary.isBlank()) {
            EmptyState(
                icon = Icons.Default.Summarize,
                title = "暂无剧情摘要",
                description = "点击右上角的编辑按钮添加剧情摘要"
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "当前剧情摘要",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedFactsTab(
    facts: List<RoleplayMemory>,
    onUnpin: (RoleplayMemory) -> Unit,
    onDelete: (RoleplayMemory) -> Unit
) {
    if (facts.isEmpty()) {
        EmptyState(
            icon = Icons.Default.PushPin,
            title = "暂无固定事实",
            description = "在记忆列表中点击固定按钮将重要事实固定"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(facts) { fact ->
                MemoryCard(
                    memory = fact,
                    onUnpin = { onUnpin(fact) },
                    onDelete = { onDelete(fact) }
                )
            }
        }
    }
}

@Composable
private fun AllMemoriesTab(
    memories: List<RoleplayMemory>,
    onPin: (RoleplayMemory) -> Unit,
    onEdit: (RoleplayMemory) -> Unit,
    onDelete: (RoleplayMemory) -> Unit
) {
    if (memories.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Memory,
            title = "暂无记忆",
            description = "点击右上角的 + 按钮添加新记忆"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(memories) { memory ->
                MemoryCard(
                    memory = memory,
                    onPin = { onPin(memory) },
                    onEdit = { onEdit(memory) },
                    onDelete = { onDelete(memory) }
                )
            }
        }
    }
}

@Composable
private fun MemoryCard(
    memory: RoleplayMemory,
    onPin: (() -> Unit)? = null,
    onUnpin: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (memory.memoryType) {
                        "fact" -> "事实"
                        "summary" -> "摘要"
                        "relationship" -> "关系"
                        "event" -> "事件"
                        else -> memory.memoryType
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    if (memory.isPinned) {
                        IconButton(onClick = { onUnpin?.invoke() }) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "取消固定",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(onClick = { onPin?.invoke() }) {
                            Icon(Icons.Default.PushPin, contentDescription = "固定")
                        }
                    }
                    if (onEdit != null) {
                        IconButton(onClick = { onEdit() }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (showDeleteDialog) {
        EchoConfirmDialog(
            title = "确认删除",
            text = "确定要删除这条记忆吗？",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                onDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf("fact") }
    var content by remember { mutableStateOf("") }

    EchoGlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加记忆") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 类型选择
                Text("记忆类型")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isFact = selectedType == "fact"
                    FilterChip(
                        selected = isFact,
                        onClick = { selectedType = "fact" },
                        label = { Text("事实") },
                        colors = echoFilterChipColors(),
                        border = echoFilterChipBorder(isFact),
                        elevation = echoFilterChipElevation()
                    )
                    val isRel = selectedType == "relationship"
                    FilterChip(
                        selected = isRel,
                        onClick = { selectedType = "relationship" },
                        label = { Text("关系") },
                        colors = echoFilterChipColors(),
                        border = echoFilterChipBorder(isRel),
                        elevation = echoFilterChipElevation()
                    )
                    val isEvent = selectedType == "event"
                    FilterChip(
                        selected = isEvent,
                        onClick = { selectedType = "event" },
                        label = { Text("事件") },
                        colors = echoFilterChipColors(),
                        border = echoFilterChipBorder(isEvent),
                        elevation = echoFilterChipElevation()
                    )
                }

                // 内容输入
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("记忆内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("输入要记住的内容") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (content.isNotBlank()) {
                        onAdd(selectedType, content)
                    }
                },
                enabled = content.isNotBlank()
            ) {
                Text("添加")
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
private fun EditSummaryDialog(
    summary: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var editedSummary by remember { mutableStateOf(summary) }

    EchoGlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑剧情摘要") },
        text = {
            OutlinedTextField(
                value = editedSummary,
                onValueChange = { editedSummary = it },
                label = { Text("剧情摘要") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                placeholder = { Text("输入当前剧情的摘要") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(editedSummary) }) {
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
private fun EditMemoryDialog(
    memory: RoleplayMemory,
    onDismiss: () -> Unit,
    onSave: (RoleplayMemory) -> Unit
) {
    var content by remember { mutableStateOf(memory.content) }

    EchoGlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记忆") },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("记忆内容") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(memory.copy(content = content, updatedAt = System.currentTimeMillis()))
                },
                enabled = content.isNotBlank()
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
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
