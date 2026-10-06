package com.aiassistant.ui.components.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.components.InlineMarkdownText
import com.aiassistant.ui.components.markdown.MarkdownInlineParser.parseInlineMarkdown

/**
 * Markdown 表格解析与排版渲染组件（支持双列卡片与水平滚动两套适配）
 */
data class MarkdownTable(
    val headers: List<String>,
    val rows: List<List<String>>,
    val consumedLines: Int
)

fun parseMarkdownTable(lines: List<String>, startIndex: Int): MarkdownTable? {
    if (startIndex + 2 >= lines.size) return null
    val headerLine = lines[startIndex].trim()
    val dividerLine = lines[startIndex + 1].trim()
    if (!headerLine.startsWith("|") || !headerLine.endsWith("|")) return null
    if (!isMarkdownTableDivider(dividerLine)) return null

    val headers = splitTableRow(headerLine)
    if (headers.size < 2) return null

    val rows = mutableListOf<List<String>>()
    var index = startIndex + 2
    while (index < lines.size) {
        val line = lines[index].trim()
        if (!line.startsWith("|") || !line.endsWith("|")) break
        rows += normalizeTableCells(splitTableRow(line), headers.size)
        index++
    }

    if (rows.isEmpty()) return null
    return MarkdownTable(headers = headers, rows = rows, consumedLines = index - startIndex)
}

fun isMarkdownTableDivider(line: String): Boolean {
    if (!line.startsWith("|") || !line.endsWith("|")) return false
    val cells = splitTableRow(line)
    return cells.size >= 2 && cells.all { cell ->
        cell.matches(Regex(":?-{3,}:?"))
    }
}

fun splitTableRow(line: String): List<String> {
    val trimmed = line.trim()
    val content = trimmed
        .removePrefix("|")
        .removeSuffix("|")
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var inCodeSpan = false
    var index = 0

    while (index < content.length) {
        val char = content[index]
        val next = content.getOrNull(index + 1)
        when {
            char == '\\' && next == '|' -> {
                current.append('|')
                index += 2
            }
            char == '`' -> {
                inCodeSpan = !inCodeSpan
                current.append(char)
                index++
            }
            char == '|' && !inCodeSpan -> {
                cells += current.toString().trim()
                current.clear()
                index++
            }
            else -> {
                current.append(char)
                index++
            }
        }
    }
    cells += current.toString().trim()
    return cells
}

fun normalizeTableCells(cells: List<String>, columnCount: Int): List<String> {
    if (columnCount <= 0) return cells
    return when {
        cells.size == columnCount -> cells
        cells.size < columnCount -> cells + List(columnCount - cells.size) { "" }
        else -> cells.take(columnCount - 1) + cells.drop(columnCount - 1).joinToString(" | ")
    }
}

@Composable
fun MarkdownTableBlock(
    table: MarkdownTable,
    color: Color
) {
    if (table.headers.size == 2) {
        MobileTwoColumnTable(table = table, color = color)
    } else {
        ScrollableMarkdownTable(table = table, color = color)
    }
}

@Composable
fun MobileTwoColumnTable(
    table: MarkdownTable,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        table.rows.forEach { row ->
            val title = row.getOrNull(0).orEmpty()
            val detail = row.getOrNull(1).orEmpty()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    InlineMarkdownText(
                        text = parseInlineMarkdown(title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    InlineMarkdownText(
                        text = parseInlineMarkdown(detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
fun ScrollableMarkdownTable(
    table: MarkdownTable,
    color: Color
) {
    val scrollState = rememberScrollState()
    val rows = remember(table) {
        table.rows.map { normalizeTableCells(it, table.headers.size) }
    }
    val columnWidths = remember(table) {
        table.headers.indices.map { index ->
            val maxLength = (listOf(table.headers) + rows)
                .map { it.getOrNull(index).orEmpty().length }
                .maxOrNull()
                ?: 0
            (maxLength * 7 + 52).dp.coerceIn(112.dp, 260.dp)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .horizontalScroll(scrollState)
    ) {
        TableRow(cells = table.headers, columnWidths = columnWidths, color = color, isHeader = true)
        rows.forEach { row ->
            TableRow(cells = row, columnWidths = columnWidths, color = color)
        }
    }
}

@Composable
fun TableRow(
    cells: List<String>,
    columnWidths: List<Dp>,
    color: Color,
    isHeader: Boolean = false
) {
    Row(modifier = Modifier.width(columnWidths.fold(0.dp) { total, width -> total + width })) {
        cells.forEachIndexed { index, cell ->
            Surface(
                modifier = Modifier
                    .width(columnWidths.getOrElse(index) { 140.dp })
                    .heightIn(min = 42.dp),
                color = if (isHeader) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                },
                border = BorderStroke(
                    width = 0.6.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)
                )
            ) {
                InlineMarkdownText(
                    text = parseInlineMarkdown(cell),
                    style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    color = if (isHeader) MaterialTheme.colorScheme.primary else color,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}
