package com.aiassistant.ui.components

import com.aiassistant.ui.components.markdown.LatexUnicodeConverter
import com.aiassistant.ui.components.markdown.MarkdownInlineParser
import com.aiassistant.ui.components.markdown.MarkdownTableBlock
import com.aiassistant.ui.components.markdown.parseMarkdownTable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import com.aiassistant.ui.theme.EchoMotion
import com.aiassistant.ui.theme.rememberReducedMotion
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key

@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onCitationClick: ((Int) -> Unit)? = null,
    streaming: Boolean = false,
    cursor: Color? = null,
    cursorFading: Boolean = false
) {
    if (!streaming) {
        MarkdownContent(
            content = content,
            modifier = modifier,
            color = color,
            onCitationClick = onCitationClick
        )
    } else {
        // P0-2② 增量渲染（A1 修正）：稳定内容切分为不可变分段，每段 key(段文本) 独立组合；
        // 段字符串一旦完成永不变化 → remember(content=段) 不重算，历史段零重解析（R-2 字面达成）；
        // 新段仅在完成时解析一次；未稳定尾部每帧重解析（O(尾部长)）；流式结束走上方全文路径定稿
        Column(modifier = modifier) {
            // v2.7.3 流畅度：分段改走增量缓存（见 MarkdownSegmentationCache 注释），
            // 结果与全文重算严格相等，既有分段单测覆盖不变量
            val segmentation = remember { MarkdownSegmentationCache() }.get(content)
            segmentation.segments.forEachIndexed { segmentIndex, segment ->
                // 检查报告 P2-1：key 纳入位置信息——长回复中完全相同的段落（重复句式/模板化列表）
                // 会产生相同段文本，纯文本 key 存在组合身份歧义隐患，index+文本彻底消除
                key(segmentIndex to segment) {
                    MarkdownContent(
                        content = segment,
                        color = color,
                        onCitationClick = onCitationClick
                    )
                }
            }
            val split = MarkdownSegmentation(emptyList(), segmentation.tail, segmentation.tailInFence)
            if (split.tail.isEmpty()) {
                // v2.7.2 需求 6：段落边界瞬间尾段为空，光标以独立行形式保持在场，动画不中断
                if (cursor != null) {
                    StreamingTailCursor(color = cursor, fading = cursorFading)
                }
            } else if (split.tailInFence) {
                // 方案 P0-2④：未闭合 ``` 围栏——围栏开启行之前的文本照常渲染，
                // 围栏内内容以等宽 plain 文本预显示，闭合后自然升级为高亮代码块
                val fenceStart = split.tail.lastIndexOf("```")
                val preFence = if (fenceStart > 0) split.tail.substring(0, fenceStart) else ""
                val fenceBody = if (fenceStart >= 0) {
                    split.tail.substring(fenceStart).lineSequence().drop(1).joinToString("\n")
                } else {
                    split.tail
                }
                if (preFence.isNotBlank()) {
                    MarkdownContent(
                        content = preFence,
                        color = color,
                        onCitationClick = onCitationClick
                    )
                }
                if (fenceBody.isNotEmpty()) {
                    // 检查报告 P3-4：代码围栏流式期间补静态光标（恒亮不呼吸），
                    // 与正文光标同定位方案（onTextLayout 覆盖层，零测量干扰）
                    val fenceLayoutState = remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                    Box {
                        Text(
                            text = fenceBody,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = color,
                            onTextLayout = { fenceLayoutState.value = it },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        if (cursor != null) {
                            EchoStreamingCursor(
                                color = cursor,
                                breathing = false,
                                modifier = Modifier.echoCursorLineTransform(fenceLayoutState)
                            )
                        }
                    }
                } else if (cursor != null) {
                    // 围栏刚开启、尚无围栏内文本：光标以独立行保持在场
                    StreamingTailCursor(color = cursor, fading = cursorFading)
                }
            } else {
                MarkdownContent(
                    content = split.tail,
                    color = color,
                    onCitationClick = onCitationClick,
                    endCursorColor = cursor,
                    endCursorFading = cursorFading
                )
                // v2.7.2 需求 6：表格/数学块/闭合代码块/分割线/空尾等无法内联承载光标的尾部，
                // 追加独立行光标，保证流式动画一直持续到回复完毕
                if (cursor != null && tailNeedsStandaloneCursor(split.tail)) {
                    StreamingTailCursor(color = cursor, fading = cursorFading)
                }
            }
        }
    }
}

/**
 * 独立行流式光标（v2.7.2 需求 6）：尾部为表格/数学块/围栏开启瞬间等无法内联挂载光标的形态时，
 * 光标以行首独立元素呈现，随下一片文本到来自然并入正文。
 */
@Composable
private fun StreamingTailCursor(
    color: Color,
    fading: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(top = 2.dp)) {
        EchoStreamingCursor(color = color, fading = fading)
    }
}

/**
 * 判定流式尾部是否无法由末块内联承载光标（需求 6）。
 * 末行尚为空（以换行收尾）或为表格/数学/围栏/分割线形态时返回 true。
 */
private fun tailNeedsStandaloneCursor(tail: String): Boolean {
    if (tail.isBlank()) return true
    val lastLine = tail.substringAfterLast("\n")
    if (lastLine.isBlank()) return true
    val t = lastLine.trim()
    return t.startsWith("```") ||
        t.startsWith("|") ||
        t.startsWith("$$") ||
        t.startsWith("\\[") ||
        t.startsWith("\\begin{") ||
        t == "---" ||
        t == "***"
}

// v2.7.3 流畅度：热路径正则提升为文件级常量——流式尾部每帧逐行重解析时，
// 原先在函数体内现场编译 Regex（有序列表/参考资料/关键词标题/跨行合并格式探测）
private val MARKDOWN_ORDERED_LIST_REGEX = Regex("^\\d+\\.\\s+.*")
private val MARKDOWN_REFERENCE_ITEM_REGEX = Regex("""^\[\^?\d+\].*""")
private val MARKDOWN_KEYWORD_STRIP_REGEX = Regex("""[#*_\s：:]""")
private val MARKDOWN_FONT_OPEN_REGEX = Regex("<font", RegexOption.IGNORE_CASE)
private val MARKDOWN_FONT_CLOSE_REGEX = Regex("</font>", RegexOption.IGNORE_CASE)
private val MARKDOWN_SPAN_OPEN_REGEX = Regex("<span", RegexOption.IGNORE_CASE)
private val MARKDOWN_SPAN_CLOSE_REGEX = Regex("</span>", RegexOption.IGNORE_CASE)

/**
 * 流式分段增量缓存（v2.7.3 流畅度）：
 * 流式内容为追加式增长（content.startsWith(prevSource) 成立）时，仅对「上一个稳定段起点之后」
 * 的后缀重跑 computeStableSegments。段边界只产生于围栏/数学块之外（inFence/inMath 均为 false
 * 的干净状态点），从该点重算与全文重算严格等价；内容非前缀追加（切换 variant 等）时回退全量重算。
 * 原先 remember(content) 每帧对全文 split + 历史段 subList 重拷贝（O(全文)/帧），
 * 长回复流式期间是每帧最大解析开销与 GC 压力来源。
 */
internal class MarkdownSegmentationCache {
    private var source: String? = null
    private var segmentation: MarkdownSegmentation = computeStableSegments("")

    fun get(content: String): MarkdownSegmentation {
        val prevSource = source
        if (content == prevSource) return segmentation
        val result = if (prevSource != null && content.startsWith(prevSource)) {
            // 稳定重算起点 = 末尾第 N 个段边界的字符偏移（段与尾在原文中连续拼接）。
            // 段边界处 inFence/inMath 必为 false（围栏闭合与空行切分均只在干净状态发生）；
            // 但切分判定 `i in 1 until size-1` 依赖行索引——重算区域首行若为空行，
            // 其在全文中的切分行为与区域内的行索引不一致（等价性单测捕获），
            // 故须回退到首个「首行非空」的段边界；全部为空行段时回退到 0 全量重算。
            // 重算区域首行非空时，区域内所有切分点与全文重算逐一对齐，结果严格等价。
            var collectedLength = segmentation.tail.length
            var dropCount = 0
            for (i in segmentation.segments.indices.reversed()) {
                val segment = segmentation.segments[i]
                if (segment.startsWith("\n")) {
                    collectedLength += segment.length
                    dropCount++
                } else {
                    collectedLength += segment.length
                    dropCount++
                    break
                }
            }
            if (dropCount == segmentation.segments.size && segmentation.segments.firstOrNull()?.startsWith("\n") == true) {
                collectedLength = segmentation.tail.length + segmentation.segments.sumOf { it.length }
            }
            val stableStart = prevSource.length - collectedLength
            val suffix = computeStableSegments(content.substring(stableStart))
            MarkdownSegmentation(
                segments = segmentation.segments.dropLast(dropCount) + suffix.segments,
                tail = suffix.tail,
                tailInFence = suffix.tailInFence
            )
        } else {
            computeStableSegments(content)
        }
        source = content
        segmentation = result
        return result
    }
}

/**
 * 流式 Markdown 分段结果（P0-2② / 审核修正版）
 */
data class MarkdownSegmentation(
    val segments: List<String>,   // 已完成段（不可变：后续内容增长不改变已产出段的字符串）
    val tail: String,             // 未稳定尾部（每帧重解析，成本 O(尾部长)）
    val tailInFence: Boolean      // 尾部是否处于未闭合 ``` 围栏内
)

/**
 * 计算流式 Markdown 的稳定分段（纯函数，供单元测试验证不变量，R-2）。
 * 保守策略：仅「围栏外的空行段落边界」与「闭合代码围栏行（其后仍有内容）」切段；
 * 数学块（$$/\[/\begin{...}）未闭合期间不切段，避免公式跨段撕裂；
 * 每段包含其边界行的换行符，保证 segments 拼接 + tail 与原文严格相等；
 * 段字符串前缀稳定（内容增长不修改历史段）→ 每段 remember(content=段) 零重算。
 */
fun computeStableSegments(content: String): MarkdownSegmentation {
    if (content.isEmpty()) return MarkdownSegmentation(emptyList(), "", false)
    val lines = content.split("\n")
    val segments = ArrayList<String>()
    var segmentStart = 0
    var inFence = false
    var inMath = false
    var mathEndTag: String? = null
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (inMath) {
            val tag = mathEndTag
            if (tag != null && line.contains(tag)) {
                inMath = false
                mathEndTag = null
            }
            i++
            continue
        }
        if (line.trimStart().startsWith("```")) {
            inFence = !inFence
            if (!inFence && i < lines.size - 1) {
                segments.add(lines.subList(segmentStart, i + 1).joinToString("\n") + "\n")
                segmentStart = i + 1
            }
            i++
            continue
        }
        if (inFence) {
            i++
            continue
        }
        if (trimmed.startsWith("\\begin{")) {
            val envName = trimmed.substringAfter("\\begin{").substringBefore("}")
            val endTag = "\\end{" + envName + "}"
            if (!line.contains(endTag)) {
                inMath = true
                mathEndTag = endTag
            }
            i++
            continue
        }
        if (trimmed.startsWith("\\[")) {
            if (!(trimmed.endsWith("\\]") && trimmed.length > 4)) {
                inMath = true
                mathEndTag = "\\]"
            }
            i++
            continue
        }
        if (trimmed.startsWith("$$")) {
            if (!(trimmed.endsWith("$$") && trimmed.length > 4)) {
                inMath = true
                mathEndTag = "$$"
            }
            i++
            continue
        }
        if (line.isBlank() && i in 1 until lines.size - 1) {
            segments.add(lines.subList(segmentStart, i + 1).joinToString("\n") + "\n")
            segmentStart = i + 1
        }
        i++
    }
    val tail = lines.subList(segmentStart, lines.size).joinToString("\n")
    return MarkdownSegmentation(segments, tail, inFence)
}

@Composable
private fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    color: Color,
    onCitationClick: ((Int) -> Unit)?,
    endCursorColor: Color? = null,
    endCursorFading: Boolean = false
) {
    Column(modifier = modifier) {
        val lines = remember(content) { content.split("\n") }
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            // v2.7.2 需求 6：单行块（标题/列表/引用/参考资料）是否为内容末块——末块承载流式光标，
            // 保证光标在任意分块类型的尾部都持续显示到回复完毕
            val isLastLine = index == lines.size - 1

            when {
                // 代码块开始 ```lang
                line.trimStart().startsWith("```") -> {
                    val codeBlockLanguage = line.trimStart().removePrefix("```").trim()
                    val codeBlockContent = StringBuilder()
                    index++
                    while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                        codeBlockContent.appendLine(lines[index])
                        index++
                    }
                    CodeBlock(
                        code = codeBlockContent.toString().trimEnd(),
                        language = codeBlockLanguage
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (index < lines.size) index++
                }

                // 独立 LaTeX 数学块 $$...$$ 或 \[...\] 或 \begin{...}
                trimmed.startsWith("$$") || trimmed.startsWith("\\[") || trimmed.startsWith("\\begin{") -> {
                    val mathContent = StringBuilder()
                    if (trimmed.startsWith("\\begin{")) {
                        val envName = trimmed.substringAfter("\\begin{").substringBefore("}")
                        val endTag = "\\end{$envName}"
                        if (trimmed.contains(endTag)) {
                            mathContent.append(trimmed)
                            index++
                        } else {
                            mathContent.appendLine(trimmed)
                            index++
                            while (index < lines.size && !lines[index].contains(endTag)) {
                                mathContent.appendLine(lines[index])
                                index++
                            }
                            if (index < lines.size) {
                                mathContent.append(lines[index])
                                index++
                            }
                        }
                    } else if (trimmed.startsWith("\\[")) {
                        if (trimmed.endsWith("\\]") && trimmed.length > 4) {
                            mathContent.append(trimmed.removePrefix("\\[").removeSuffix("\\]").trim())
                            index++
                        } else {
                            mathContent.appendLine(trimmed.removePrefix("\\[").trim())
                            index++
                            while (index < lines.size && !lines[index].trim().endsWith("\\]")) {
                                mathContent.appendLine(lines[index])
                                index++
                            }
                            if (index < lines.size) {
                                mathContent.append(lines[index].trim().removeSuffix("\\]").trim())
                                index++
                            }
                        }
                    } else {
                        if (trimmed.endsWith("$$") && trimmed.length > 4) {
                            mathContent.append(trimmed.removePrefix("$$").removeSuffix("$$").trim())
                            index++
                        } else {
                            mathContent.appendLine(trimmed.removePrefix("$$").trim())
                            index++
                            while (index < lines.size && !lines[index].trim().endsWith("$$")) {
                                mathContent.appendLine(lines[index])
                                index++
                            }
                            if (index < lines.size) {
                                mathContent.append(lines[index].trim().removeSuffix("$$").trim())
                                index++
                            }
                        }
                    }
                    MathBlock(formula = mathContent.toString().trim())
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 标题 1-6 级（含特定关键词加粗、加大字号、斜体强化）
                line.startsWith("# ") -> {
                    val text = line.removePrefix("# ")
                    renderHeadingText(
                        text = text,
                        defaultStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        color = color,
                        topPad = 8.dp,
                        bottomPad = 4.dp,
                        endCursorColor = if (isLastLine) endCursorColor else null,
                        endCursorFading = endCursorFading
                    )
                    index++
                }
                line.startsWith("## ") -> {
                    val text = line.removePrefix("## ")
                    renderHeadingText(
                        text = text,
                        defaultStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = color,
                        topPad = 6.dp,
                        bottomPad = 3.dp,
                        endCursorColor = if (isLastLine) endCursorColor else null,
                        endCursorFading = endCursorFading
                    )
                    index++
                }
                line.startsWith("### ") -> {
                    val text = line.removePrefix("### ")
                    renderHeadingText(
                        text = text,
                        defaultStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.5.sp),
                        color = color,
                        topPad = 5.dp,
                        bottomPad = 3.dp,
                        endCursorColor = if (isLastLine) endCursorColor else null,
                        endCursorFading = endCursorFading
                    )
                    index++
                }
                line.startsWith("#### ") -> {
                    val text = line.removePrefix("#### ")
                    renderHeadingText(
                        text = text,
                        defaultStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        color = color,
                        topPad = 4.dp,
                        bottomPad = 2.dp,
                        endCursorColor = if (isLastLine) endCursorColor else null,
                        endCursorFading = endCursorFading
                    )
                    index++
                }
                line.startsWith("##### ") -> {
                    val text = line.removePrefix("##### ")
                    renderHeadingText(
                        text = text,
                        defaultStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = color,
                        topPad = 3.dp,
                        bottomPad = 2.dp,
                        endCursorColor = if (isLastLine) endCursorColor else null,
                        endCursorFading = endCursorFading
                    )
                    index++
                }
                line.startsWith("###### ") -> {
                    val text = line.removePrefix("###### ")
                    renderHeadingText(
                        text = text,
                        defaultStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = color,
                        topPad = 2.dp,
                        bottomPad = 2.dp,
                        endCursorColor = if (isLastLine) endCursorColor else null,
                        endCursorFading = endCursorFading
                    )
                    index++
                }

                // 表格渲染
                parseMarkdownTable(lines, index) != null -> {
                    val table = parseMarkdownTable(lines, index)!!
                    MarkdownTableBlock(table = table, color = color)
                    Spacer(modifier = Modifier.height(8.dp))
                    index += table.consumedLines
                }

                // 末尾参考资料项（如 - [1] 标题 或 * [1] 标题 或 [1] 标题：前面不要有圆点，正常大小显示）
                isReferenceListItem(line) -> {
                    val cleanRefLine = cleanReferenceItemLine(line)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InlineMarkdownText(
                            text = parseInlineMarkdown(cleanRefLine, isReferenceItem = true),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            onCitationClick = onCitationClick,
                            endCursorColor = if (isLastLine) endCursorColor else null,
                            endCursorFading = endCursorFading
                        )
                    }
                    index++
                }

                // 普通无序列表项（无前缀圆点·，保持自然缩进与正文字号一致）
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val indent = line.length - line.trimStart().length
                    val itemContent = line.trimStart().removePrefix("- ").removePrefix("* ")
                    Row(modifier = Modifier.padding(start = (8 + indent * 4).dp, top = 2.dp)) {
                        InlineMarkdownText(
                            text = parseInlineMarkdown(itemContent),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                            endCursorColor = if (isLastLine) endCursorColor else null,
                            endCursorFading = endCursorFading
                        )
                    }
                    index++
                }

                    // 有序列表
                    line.trimStart().matches(MARKDOWN_ORDERED_LIST_REGEX) -> {
                        val number = line.trimStart().substringBefore(".")
                        val itemContent = line.trimStart().substringAfter(". ")
                        Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp)) {
                            Text(
                                text = "$number.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = color
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            InlineMarkdownText(
                                text = parseInlineMarkdown(itemContent),
                                style = MaterialTheme.typography.bodyLarge,
                                color = color,
                                endCursorColor = if (isLastLine) endCursorColor else null,
                                endCursorFading = endCursorFading
                            )
                        }
                        index++
                    }

                    // 引用
                    line.startsWith("> ") -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            InlineMarkdownText(
                                text = parseInlineMarkdown(line.removePrefix("> ")),
                                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                                color = color.copy(alpha = 0.8f),
                                endCursorColor = if (isLastLine) endCursorColor else null,
                                endCursorFading = endCursorFading
                            )
                        }
                        index++
                    }

                    // 分割线
                    line.trim() == "---" || line.trim() == "***" -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        index++
                    }

                    // 空行
                    line.isBlank() -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        index++
                    }

                    // 普通文本
                    else -> {
                        // 检查普通单行中是否为核心关键词（参考资料/要点概括/详细解答）单独成行
                        if (isSpecialKeywordTitle(line.trim())) {
                            renderHeadingText(
                                text = line.trim(),
                                defaultStyle = MaterialTheme.typography.titleMedium,
                                color = color,
                                topPad = 6.dp,
                                bottomPad = 3.dp,
                                endCursorColor = if (isLastLine) endCursorColor else null,
                                endCursorFading = endCursorFading
                            )
                            index++
                        } else {
                            // 跨行格式保护：若当前行包含未闭合的加粗/斜体/删除线等标记，合并后续连续文本行
                            val mergedLines = StringBuilder(line)
                            index++
                            while (index < lines.size && hasUnclosedInlineFormatting(mergedLines.toString()) && !isBlockBoundaryLine(lines[index])) {
                                mergedLines.append("\n").append(lines[index])
                                index++
                            }
                            InlineMarkdownText(
                                text = parseInlineMarkdown(mergedLines.toString()),
                                style = MaterialTheme.typography.bodyLarge,
                                color = color,
                                modifier = Modifier.padding(vertical = 2.dp),
                                onCitationClick = onCitationClick,
                                endCursorColor = if (index >= lines.size) endCursorColor else null,
                                endCursorFading = endCursorFading
                            )
                        }
                    }
                }
            }
        }
    }

/**
 * 校验文本行中是否存在未闭合的 Markdown 标记（如星号、下划线、删除线或 HTML 标签）
 */
private fun hasUnclosedInlineFormatting(text: String): Boolean {
    val norm = text.replace('＊', '*')
    fun countOccurrences(sub: String): Int {
        var count = 0
        var idx = 0
        while (idx < norm.length) {
            val found = norm.indexOf(sub, idx)
            if (found != -1) {
                count++
                idx = found + sub.length
            } else break
        }
        return count
    }
    val count3 = countOccurrences("***")
    val count2 = countOccurrences("**")
    val count1 = norm.count { it == '*' }
    val countTilde = countOccurrences("~~")
    val countFontOpen = norm.split(MARKDOWN_FONT_OPEN_REGEX).size - 1
    val countFontClose = norm.split(MARKDOWN_FONT_CLOSE_REGEX).size - 1
    val countSpanOpen = norm.split(MARKDOWN_SPAN_OPEN_REGEX).size - 1
    val countSpanClose = norm.split(MARKDOWN_SPAN_CLOSE_REGEX).size - 1

    return (count3 % 2 != 0) || (count2 % 2 != 0) || (count1 % 2 != 0) || (countTilde % 2 != 0) ||
        (countFontOpen > countFontClose) || (countSpanOpen > countSpanClose)
}

/**
 * 校验当前行是否为独立 Markdown 块级元素边界（标题、列表、代码块、引用等），跨行合并不得越界
 */
private fun isBlockBoundaryLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.isBlank() ||
        line.trimStart().startsWith("```") ||
        trimmed.startsWith("$$") || trimmed.startsWith("\\[") || trimmed.startsWith("\\begin{") ||
        line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ") ||
        line.startsWith("#### ") || line.startsWith("##### ") || line.startsWith("###### ") ||
        isReferenceListItem(line) ||
        line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") ||
        line.trimStart().matches(MARKDOWN_ORDERED_LIST_REGEX) ||
        line.startsWith("> ") ||
        trimmed == "---" || trimmed == "***"
}

/**
 * 判断是否为特定关键词标题（加粗、加大字号、斜体）
 */
private fun isSpecialKeywordTitle(text: String): Boolean {
    val clean = text.replace(MARKDOWN_KEYWORD_STRIP_REGEX, "")
    return clean.contains("参考资料") ||
        clean.contains("要点概括") ||
        clean.contains("详细解答") ||
        clean.contains("资料来源") ||
        clean.contains("要点总结") ||
        clean.contains("核心解答")
}

@Composable
private fun renderHeadingText(
    text: String,
    defaultStyle: TextStyle,
    color: Color,
    topPad: Dp,
    bottomPad: Dp,
    endCursorColor: Color? = null,
    endCursorFading: Boolean = false
) {
    val isSpecial = isSpecialKeywordTitle(text)
    val finalStyle = if (isSpecial) {
        MaterialTheme.typography.titleMedium.copy(
            fontSize = 17.5.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic
        )
    } else {
        defaultStyle.copy(fontWeight = FontWeight.Bold)
    }
    val finalColor = if (isSpecial) MaterialTheme.colorScheme.primary else color

    InlineMarkdownText(
        text = parseInlineMarkdown(text),
        style = finalStyle,
        color = finalColor,
        modifier = Modifier.padding(top = topPad, bottom = bottomPad),
        endCursorColor = endCursorColor,
        endCursorFading = endCursorFading
    )
}

/**
 * 判断是否为末尾参考资料项，例如：
 * - [1] 标题 (URL)
 * * [1] 标题
 * [1] 标题 http://...
 */
private fun isReferenceListItem(line: String): Boolean {
    val trimmed = line.trimStart().removePrefix("- ").removePrefix("* ").trim()
    return trimmed.matches(MARKDOWN_REFERENCE_ITEM_REGEX)
}

private fun cleanReferenceItemLine(line: String): String {
    return line.trimStart().removePrefix("- ").removePrefix("* ").trim()
}

/**
 * LaTeX 数学公式块
 */
@Composable
private fun MathBlock(
    formula: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val parsedFormula = remember(formula) { parseLaTeXToUnicode(formula) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Functions,
                        contentDescription = "LaTeX公式",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "公式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(formula)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制公式",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = parsedFormula,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}


@Composable
internal fun InlineMarkdownText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onCitationClick: ((Int) -> Unit)? = null,
    endCursorColor: Color? = null,
    endCursorFading: Boolean = false
) {
    val uriHandler = LocalUriHandler.current
    val hasCitations = remember(text) { text.getStringAnnotations(tag = "CITATION", start = 0, end = text.length).isNotEmpty() }
    val hasUrls = remember(text) { text.getStringAnnotations(tag = "URL", start = 0, end = text.length).isNotEmpty() }

    if (!hasCitations && !hasUrls) {
        if (endCursorColor == null) {
            Text(
                text = text,
                style = style.copy(color = color),
                modifier = modifier
            )
        } else {
            // P0-2② 呼吸光标：以 onTextLayout 覆盖层定位到文本末尾，
            // 位置/透明度均仅在绘制阶段读取（graphicsLayer/Canvas），零测量干扰（R-3）
            val layoutState = remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
            Box(modifier) {
                Text(
                    text = text,
                    style = style.copy(color = color),
                    onTextLayout = { layoutState.value = it }
                )
                EchoStreamingCursor(
                    color = endCursorColor,
                    fading = endCursorFading,
                    modifier = Modifier.echoCursorLineTransform(layoutState)
                )
            }
        }
    } else {
        ClickableText(
            text = text,
            style = style.copy(color = color),
            modifier = modifier,
            onClick = { offset ->
                text.getStringAnnotations(tag = "CITATION", start = offset, end = offset)
                    .firstOrNull()
                    ?.let { annotation ->
                        val id = annotation.item.toIntOrNull() ?: 1
                        onCitationClick?.invoke(id)
                    }
                text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()
                    ?.let { annotation ->
                        runCatching { uriHandler.openUri(annotation.item) }
                    }
            }
        )
    }
}

// =========================================================================
// 向下兼容重导出函数（保障既有模块及 66 项单元测试无痛调用）
// =========================================================================

fun parseInlineMarkdown(text: String, isReferenceItem: Boolean = false): AnnotatedString =
    MarkdownInlineParser.parseInlineMarkdown(text, isReferenceItem)

fun cleanLeadingStarArtifacts(raw: String): String =
    MarkdownInlineParser.cleanLeadingStarArtifacts(raw)

fun parseLaTeXToUnicode(raw: String): String =
    LatexUnicodeConverter.parseLaTeXToUnicode(raw)

fun parseInlineColor(raw: String?): Color? =
    com.aiassistant.ui.components.markdown.MarkdownColorUtils.parseInlineColor(raw)

fun decodeHtmlEntities(input: String): String =
    com.aiassistant.ui.components.markdown.MarkdownColorUtils.decodeHtmlEntities(input)

fun highlightSyntax(code: String, language: String, isDark: Boolean): AnnotatedString =
    com.aiassistant.ui.components.markdown.highlightSyntax(code, language, isDark)

@Composable
fun CodeBlock(code: String, language: String = "", modifier: Modifier = Modifier) {
    com.aiassistant.ui.components.markdown.CodeBlock(code = code, language = language, modifier = modifier)
}

/**
 * Echo 流式打字光标（P0-2②/P0-3①）
 * 2dp 宽、字高约 70% 的竖线，530ms 周期透明度呼吸；落定时以 300ms 淡出（fading=true）。
 * reduced motion：光标保持静态可见（保留状态指示），淡出路径不变。
 *
 * v2.7.2 需求 8：几何常量提升为顶层值，定位逻辑（echoCursorLineTransform）与其共用，
 * 保证绘制尺寸与定位计算一致。
 */
internal val EchoStreamingCursorWidth = 2.dp
internal val EchoStreamingCursorHeight = 18.dp
/** 需求 8：光标与文字末尾的间距（原定位与文字重叠 1dp，现外移留出间隙） */
internal val EchoStreamingCursorGap = 2.dp

/**
 * 需求 8：光标贴行定位——水平贴文字末尾并外移一个固定间隙；垂直在末行行盒内居中
 * （原先 translationY = lineTop 顶对齐，视觉偏上、与文字不齐）。
 * 布局结果尚未产出时隐藏，产出后恢复可见。
 */
private fun Modifier.echoCursorLineTransform(
    layoutState: androidx.compose.runtime.State<androidx.compose.ui.text.TextLayoutResult?>
): Modifier = graphicsLayer {
    val layout = layoutState.value
    if (layout == null || layout.lineCount == 0) {
        alpha = 0f
        return@graphicsLayer
    }
    alpha = 1f
    val lastLine = layout.lineCount - 1
    translationX = layout.getLineRight(lastLine) + EchoStreamingCursorGap.toPx()
    val lineTop = layout.getLineTop(lastLine)
    val lineHeight = layout.getLineBottom(lastLine) - lineTop
    translationY = lineTop + (lineHeight - EchoStreamingCursorHeight.toPx()) / 2f
}

@Composable
internal fun EchoStreamingCursor(
    color: Color,
    modifier: Modifier = Modifier,
    fading: Boolean = false,
    breathing: Boolean = true
) {
    val reduced = rememberReducedMotion()
    // 检查报告 P3-2：reduced motion / 静态光标（breathing=false）下条件创建 InfiniteTransition，
    // 避免帧回调空转（微功耗）
    val breathe: Float = if (reduced || !breathing) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "echoCursor")
        val v by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = EchoMotion.reverseCycleSpec<Float>(EchoMotion.Typewriter.cursorBlinkMs),
            label = "cursorBreathe"
        )
        v
    }
    val fade by animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = EchoMotion.tweenSpec<Float>(EchoMotion.Typewriter.cursorFadeMs),
        label = "cursorFade"
    )
    Canvas(modifier.size(width = EchoStreamingCursorWidth, height = EchoStreamingCursorHeight)) {
        // fading（落定）：停止呼吸、只做静态淡出；正常流式：呼吸；breathing=false：恒亮静态；reduced：静态
        val alpha = when {
            reduced -> fade
            fading -> fade
            !breathing -> 1f * fade
            else -> breathe * fade
        }
        if (alpha > 0.01f) {
            drawRoundRect(
                color = color.copy(alpha = alpha),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
    }
}
