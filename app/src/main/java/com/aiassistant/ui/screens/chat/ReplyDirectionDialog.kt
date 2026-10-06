package com.aiassistant.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aiassistant.domain.model.*
import com.aiassistant.ui.components.EchoGlassDialog
import dev.chrisbanes.haze.HazeState

@Composable
fun ReplyDirectionDialog(
    prompt: ReplyDirectionPrompt,
    hazeState: HazeState?,
    onDecision: (ReplyDirectionDecision) -> Unit,
    onDismiss: () -> Unit
) {
    var selection by remember(prompt) { mutableIntStateOf(-1) }
    var custom by rememberSaveable(prompt.directions) { mutableStateOf("") }
    val otherIndex = prompt.directions.size + 1
    EchoGlassDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        paneTitleText = "选择本轮回复方向",
        title = { Text(if (prompt.error == null) "这次，往哪个方向聊？" else "回复方向暂未生成", style = MaterialTheme.typography.titleLarge) },
        content = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prompt.error != null) {
                    Text(prompt.error, style = MaterialTheme.typography.bodyMedium)
                    Text("尚未生成正式回复。可以重试方向请求，或跳过本轮选择直接回复。", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onDecision(ReplyDirectionDecision(ReplyDirectionAction.RETRY)) }, modifier = Modifier.fillMaxWidth()) { Text("重新生成方向") }
                    Button(onClick = { onDecision(ReplyDirectionDecision(ReplyDirectionAction.SKIP)) }, modifier = Modifier.fillMaxWidth()) { Text("跳过选择，直接回复") }
                } else {
                    Text("依据本轮会话整理。选择只对这次回复生效。", style = MaterialTheme.typography.bodySmall)
                    fun decide(): ReplyDirectionDecision = when (selection) {
                        prompt.directions.size -> ReplyDirectionDecision(ReplyDirectionAction.AUTO)
                        otherIndex -> ReplyDirectionDecision(ReplyDirectionAction.OTHER, customText = custom)
                        else -> ReplyDirectionDecision(ReplyDirectionAction.SELECT, selection)
                    }
                    prompt.directions.forEachIndexed { index, direction ->
                        DirectionRow(selection == index, direction.title, direction.description) { selection = index }
                        HorizontalDivider()
                    }
                    DirectionRow(selection == prompt.directions.size, "自行决定", "由模型从上面的方向中选择一个展开") { selection = prompt.directions.size }
                    DirectionRow(selection == otherIndex, "其他", "填写自己的方向；留空则采用不同于上述选项的新方向") { selection = otherIndex }
                    if (selection == otherIndex) {
                        OutlinedTextField(value = custom, onValueChange = { custom = it.take(4000) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("自定义方向（可留空）") }, minLines = 2, maxLines = 5)
                    }
                    Button(onClick = { onDecision(decide()) }, enabled = selection >= 0, modifier = Modifier.fillMaxWidth()) { Text("按此方向回复") }
                }
            }
        },
        buttons = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onDecision(ReplyDirectionDecision(ReplyDirectionAction.CANCEL)) }) { Text("取消本轮") }
                TextButton(onClick = onDismiss) { Text("稍后选择") }
            }
        }
    )
}

@Composable
private fun DirectionRow(selected: Boolean, title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
