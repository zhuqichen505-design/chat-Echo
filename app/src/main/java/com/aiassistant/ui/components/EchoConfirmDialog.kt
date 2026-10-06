package com.aiassistant.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Echo 确认对话框 (EchoConfirmDialog)
 * 统一全应用确认弹窗的视觉规范与交互模式。
 *
 * 设计决策：
 * - 使用 EchoGlassDialog 作为容器，保持玻璃质感一致性
 * - 标题使用 titleLarge 样式，正文使用 bodyMedium
 * - 确认按钮使用 error 颜色（危险操作）或 primary 颜色（普通确认）
 * - 取消按钮使用 TextButton，降低视觉权重
 */
@Composable
fun EchoConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "确定",
    dismissText: String = "取消",
    isDestructive: Boolean = false,
    icon: (@Composable ColumnScope.() -> Unit)? = null
) {
    EchoGlassDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 44.dp) // §5.7：弹窗按钮最小高度
            ) {
                Text(
                    text = confirmText,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        },
        dismissButton = {
            // 按钮间距由 EchoGlassDialog 按钮行统一提供（§5.7：12dp），此处不再叠加
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 44.dp) // §5.7：弹窗按钮最小高度
            ) {
                Text(
                    text = dismissText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
