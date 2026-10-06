@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.aiassistant.ui.screens.roleplay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiassistant.domain.model.PlotAction
import com.aiassistant.ui.components.EchoGlassCard
import com.aiassistant.ui.components.EchoGlassDialog
import com.aiassistant.ui.components.echoFilterChipBorder
import com.aiassistant.ui.components.echoFilterChipColors
import com.aiassistant.ui.components.echoFilterChipElevation
import com.aiassistant.ui.theme.EchoTokens

@Composable
fun PlotActionBar(
    onAction: (PlotAction, String?) -> Unit,
    onProposeSetting: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "剧情操作",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 常用操作
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onProposeSetting != null) {
                    item {
                        AssistChip(
                            onClick = onProposeSetting,
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            },
                            label = { Text("识别输入补充设定", color = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
                val quickActions = listOf(
                    PlotAction.CONTINUE,
                    PlotAction.BRANCH_CHOICES,
                    PlotAction.REGENERATE,
                    PlotAction.REWRITE,
                    PlotAction.EXTEND,
                    PlotAction.SHORTEN,
                    PlotAction.SUMMARY,
                    PlotAction.DIALOGUE_ONLY,
                    PlotAction.NARRATION_ONLY,
                    PlotAction.DIALOGUE_ACTION
                )
                items(quickActions) { action ->
                    ActionChip(
                        action = action,
                        onClick = { onAction(action, null) }
                    )
                }
                item {
                    ActionChip(
                        action = PlotAction.CUSTOM,
                        onClick = { showCustomDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 更多操作
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { onAction(PlotAction.CHANGE_PERSPECTIVE, null) },
                    label = { Text("改变视角") }
                )
                AssistChip(
                    onClick = { onAction(PlotAction.CHANGE_TONE, null) },
                    label = { Text("改变语气") }
                )
                AssistChip(
                    onClick = { onAction(PlotAction.BRANCH, null) },
                    label = { Text("创建分支") }
                )
                AssistChip(
                    onClick = { onAction(PlotAction.ROLLBACK, null) },
                    label = { Text("回退版本") }
                )
            }
        }
    }

    // 自定义指令对话框
    if (showCustomDialog) {
        CustomInstructionDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { instruction ->
                showCustomDialog = false
                onAction(PlotAction.CUSTOM, instruction)
            }
        )
    }
}

@Composable
fun PlotActionDialog(
    hazeState: dev.chrisbanes.haze.HazeState,
    onAction: (PlotAction, String?) -> Unit,
    onProposeSetting: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.AltRoute,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "剧情导演操作",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "选择剧情指令，引导模型生成下文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onProposeSetting != null) {
                    item {
                        EchoGlassCard(
                            onClick = {
                                onDismiss()
                                onProposeSetting()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = EchoTokens.Radius.shapeSm,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("💡 AI 智能提取与设定融入", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("从近期剧情精准提取人设与世界观更新", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("🚀 推进与改写", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ActionChip(action = PlotAction.CONTINUE) { onDismiss(); onAction(PlotAction.CONTINUE, null) }
                        ActionChip(action = PlotAction.REGENERATE) { onDismiss(); onAction(PlotAction.REGENERATE, null) }
                        ActionChip(action = PlotAction.REWRITE) { onDismiss(); onAction(PlotAction.REWRITE, null) }
                        ActionChip(action = PlotAction.EXTEND) { onDismiss(); onAction(PlotAction.EXTEND, null) }
                        ActionChip(action = PlotAction.SHORTEN) { onDismiss(); onAction(PlotAction.SHORTEN, null) }
                    }
                }

                item {
                    Text("🎭 模式与对白", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ActionChip(action = PlotAction.DIALOGUE_ONLY) { onDismiss(); onAction(PlotAction.DIALOGUE_ONLY, null) }
                        ActionChip(action = PlotAction.NARRATION_ONLY) { onDismiss(); onAction(PlotAction.NARRATION_ONLY, null) }
                        ActionChip(action = PlotAction.DIALOGUE_ACTION) { onDismiss(); onAction(PlotAction.DIALOGUE_ACTION, null) }
                        ActionChip(action = PlotAction.CHANGE_PERSPECTIVE) { onDismiss(); onAction(PlotAction.CHANGE_PERSPECTIVE, null) }
                        ActionChip(action = PlotAction.CHANGE_TONE) { onDismiss(); onAction(PlotAction.CHANGE_TONE, null) }
                    }
                }

                item {
                    Text("🌿 分支与记忆", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ActionChip(action = PlotAction.BRANCH_CHOICES) { onDismiss(); onAction(PlotAction.BRANCH_CHOICES, null) }
                        ActionChip(action = PlotAction.BRANCH) { onDismiss(); onAction(PlotAction.BRANCH, null) }
                        ActionChip(action = PlotAction.SUMMARY) { onDismiss(); onAction(PlotAction.SUMMARY, null) }
                        ActionChip(action = PlotAction.ROLLBACK) { onDismiss(); onAction(PlotAction.ROLLBACK, null) }
                    }
                }

                item {
                    Text("✍️ 自定义指令", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    ActionChip(action = PlotAction.CUSTOM) { showCustomDialog = true }
                }
            }
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )

    if (showCustomDialog) {
        CustomInstructionDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { instruction ->
                showCustomDialog = false
                onDismiss()
                onAction(PlotAction.CUSTOM, instruction)
            }
        )
    }
}

@Composable
private fun ActionChip(
    action: PlotAction,
    onClick: () -> Unit
) {
    val icon = when (action) {
        PlotAction.CONTINUE -> Icons.Default.PlayArrow
        PlotAction.BRANCH_CHOICES -> Icons.AutoMirrored.Filled.AltRoute
        PlotAction.REGENERATE -> Icons.Default.Refresh
        PlotAction.REWRITE -> Icons.Default.Edit
        PlotAction.EXTEND -> Icons.Default.Add
        PlotAction.SHORTEN -> Icons.Default.Remove
        PlotAction.SUMMARY -> Icons.Default.Summarize
        PlotAction.DIALOGUE_ONLY -> Icons.AutoMirrored.Filled.Chat
        PlotAction.NARRATION_ONLY -> Icons.AutoMirrored.Filled.MenuBook
        PlotAction.DIALOGUE_ACTION -> Icons.Default.RecordVoiceOver
        PlotAction.CUSTOM -> Icons.Default.MoreHoriz
        PlotAction.CHANGE_PERSPECTIVE -> Icons.Default.Visibility
        PlotAction.CHANGE_TONE -> Icons.Default.Tune
        PlotAction.BRANCH -> Icons.AutoMirrored.Filled.CallSplit
        PlotAction.ROLLBACK -> Icons.Default.History
    }

    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(action.displayName) },
        colors = echoFilterChipColors(),
        border = echoFilterChipBorder(false),
        elevation = echoFilterChipElevation(),
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Composable
private fun CustomInstructionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var instruction by remember { mutableStateOf("") }

    EchoGlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义指令") },
        text = {
            Column {
                Text(
                    text = "输入你想要的剧情发展方向：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("指令内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = {
                        Text("例如：\n- 让他们在雨夜的车站再次相遇\n- 让冲突升级，但不要立即解决\n- 从配角视角继续这一段")
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(instruction) },
                enabled = instruction.isNotBlank()
            ) {
                Text("执行")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
