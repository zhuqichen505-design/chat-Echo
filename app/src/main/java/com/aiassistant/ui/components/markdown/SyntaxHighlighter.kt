package com.aiassistant.ui.components.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 代码块组件（支持语法高亮与复制）
 */
@Composable
fun CodeBlock(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val highlightedCode = remember(code, language, isDark) {
        highlightSyntax(code, language, isDark)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.95f) else Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column {
            // 顶部栏：语言标签与复制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF0F172A).copy(alpha = 0.7f) else Color(0xFFE2E8F0).copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制代码",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 代码内容高亮展示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = highlightedCode,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    ),
                    softWrap = false,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}

/**
 * 现代轻量语法高亮引擎
 */
fun highlightSyntax(code: String, language: String, isDark: Boolean): AnnotatedString {
    val keywordColor = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED) // 紫色
    val stringColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A) // 绿色
    val numberColor = if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C) // 橙色
    val commentColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B) // 灰色
    val typeColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7) // 浅蓝
    val defaultColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)

    val keywords = setOf(
        "fun", "val", "var", "class", "interface", "object", "import", "package",
        "return", "if", "else", "while", "for", "in", "is", "as", "try", "catch",
        "finally", "throw", "when", "def", "from", "const", "let", "function",
        "async", "await", "struct", "impl", "enum", "public", "private", "protected",
        "static", "void", "select", "update", "delete", "insert", "where", "case",
        "switch", "break", "continue", "default", "true", "false", "null", "nil"
    )

    return buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            when {
                // 单行注释 // 或 #
                code.startsWith("//", i) || (language.lowercase() in listOf("python", "py", "bash", "sh", "yaml", "yml", "dockerfile") && code[i] == '#') -> {
                    val end = code.indexOf('\n', i)
                    val commentText = if (end != -1) code.substring(i, end) else code.substring(i)
                    withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                        append(commentText)
                    }
                    i += commentText.length
                }

                // 块注释 /* ... */
                code.startsWith("/*", i) -> {
                    val end = code.indexOf("*/", i + 2)
                    val commentText = if (end != -1) code.substring(i, end + 2) else code.substring(i)
                    withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                        append(commentText)
                    }
                    i += commentText.length
                }

                // 字符串 "..." 或 '...' 或 `...`
                code[i] == '"' || code[i] == '\'' || code[i] == '`' -> {
                    val quote = code[i]
                    var end = i + 1
                    while (end < code.length) {
                        if (code[end] == '\\') {
                            end += 2
                        } else if (code[end] == quote) {
                            end++
                            break
                        } else {
                            end++
                        }
                    }
                    val strText = code.substring(i, end.coerceAtMost(code.length))
                    withStyle(SpanStyle(color = stringColor)) {
                        append(strText)
                    }
                    i = end
                }

                // 标识符或关键词
                code[i].isLetter() || code[i] == '_' || code[i] == '@' -> {
                    var end = i
                    while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) {
                        end++
                    }
                    val word = code.substring(i, end)
                    val style = when {
                        word in keywords -> SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)
                        word.startsWith("@") -> SpanStyle(color = numberColor)
                        word.isNotEmpty() && word[0].isUpperCase() -> SpanStyle(color = typeColor, fontWeight = FontWeight.SemiBold)
                        else -> SpanStyle(color = defaultColor)
                    }
                    withStyle(style) {
                        append(word)
                    }
                    i = end
                }

                // 数字
                code[i].isDigit() -> {
                    var end = i
                    while (end < code.length && (code[end].isDigit() || code[end] == '.' || code[end] in "xXaAbBcCdDeEfF")) {
                        end++
                    }
                    val num = code.substring(i, end)
                    withStyle(SpanStyle(color = numberColor)) {
                        append(num)
                    }
                    i = end
                }

                else -> {
                    withStyle(SpanStyle(color = defaultColor)) {
                        append(code[i])
                    }
                    i++
                }
            }
        }
    }
}
