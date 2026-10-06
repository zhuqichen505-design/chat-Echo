package com.aiassistant.ui.components.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.aiassistant.ui.components.markdown.MarkdownColorUtils.decodeHtmlEntities
import com.aiassistant.ui.components.markdown.MarkdownColorUtils.parseHtmlTagColor
import com.aiassistant.ui.components.markdown.MarkdownColorUtils.parseInlineColor
import com.aiassistant.ui.components.markdown.LatexUnicodeConverter.isInlineMathStart
import com.aiassistant.ui.components.markdown.LatexUnicodeConverter.parseLaTeXToUnicode

/**
 * Markdown 行内多样式富文本解析引擎
 */
object MarkdownInlineParser {

    // v2.7.3 流畅度：cleanLeadingStarArtifacts 是每个 parseInlineMarkdown 的第一步
    //（含嵌套递归调用），流式尾部每帧每行触发；原先每次调用现场编译 4 个 Regex
    private val ESCAPED_STARS_REGEX = Regex("""\\(\*{2,3})""")
    private val LEADING_STAR_BEFORE_TAG_REGEX = Regex("""^\s*\*\s*(?=<font|<span|\{#)""", RegexOption.IGNORE_CASE)
    private val STAR_INSIDE_FONT_REGEX = Regex("""(<font[^>]*>)\s*\*""", RegexOption.IGNORE_CASE)
    private val STAR_INSIDE_SPAN_REGEX = Regex("""(<span[^>]*>)\s*\*""", RegexOption.IGNORE_CASE)

    /**
     * v2.7.3 流畅度：引用角标形态的有界前瞻判定（'[' + 可选 '^' + ASCII 数字 + ']'）。
     * 与原实现「对剩余全文 substring 后匹配 ^\[\^?\d+\].*」严格等价（.* 恒真），
     * 但不再为每个 '[' 分配剩余全文子串、不再现场编译正则
     */
    private fun isCitationMarkerAt(text: String, start: Int): Boolean {
        var idx = start + 1
        if (idx < text.length && text[idx] == '^') idx++
        val digitsStart = idx
        while (idx < text.length && text[idx] in '0'..'9') idx++
        if (idx == digitsStart) return false
        return idx < text.length && text[idx] == ']'
    }

/**
 * 清理因模型非标颜色标签不兼容或格式错乱而在句首留下的孤立星号 '*'，同时正规化全角星号与常见转义符
 */
fun cleanLeadingStarArtifacts(raw: String): String {
    var s = raw.replace('＊', '*') // 1. 全角星号归一化为半角星号，彻底支持中文全角星号排版
    // 2. 处理大模型常见的 Markdown 转义反斜杠星号，例如 \***文字\*** 或 \*\*文字\*\*
    s = s.replace(ESCAPED_STARS_REGEX, "$1")
    // 3. 清理开头紧随 <font>、<span>、{# 颜色标签出现的孤立星号，例如 "*<font", "* <font", "*<span", "* {#", etc.
    s = s.replace(LEADING_STAR_BEFORE_TAG_REGEX, "")
    // 4. 清理 <font ...>* 或 <span ...>* 紧随开标签后的孤立星号
    s = s.replace(STAR_INSIDE_FONT_REGEX, "$1")
    s = s.replace(STAR_INSIDE_SPAN_REGEX, "$1")
    return s
}

/**
 * 行内 Markdown 与多格式富文本解析
 */
fun parseInlineMarkdown(
    text: String,
    isReferenceItem: Boolean = false
): AnnotatedString {
    val sanitized = cleanLeadingStarArtifacts(text)
    val decoded = decodeHtmlEntities(sanitized)

    return buildAnnotatedString {
        var i = 0
        while (i < decoded.length) {
            when {
                // 自定义颜色语法 {#color|text}
                decoded.startsWith("{#", i) -> {
                    val separator = decoded.indexOf('|', i + 2)
                    val end = if (separator != -1) decoded.indexOf('}', separator + 1) else -1
                    val colorValue = if (separator != -1) decoded.substring(i + 1, separator) else ""
                    val parsedColor = parseInlineColor(colorValue)
                    if (separator != -1 && end != -1 && parsedColor != null) {
                        withStyle(SpanStyle(color = parsedColor)) {
                            append(parseInlineMarkdown(decoded.substring(separator + 1, end)))
                        }
                        i = end + 1
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // HTML <font color="...">text</font>
                decoded.startsWith("<font", i, ignoreCase = true) -> {
                    val openEnd = decoded.indexOf('>', i)
                    val closeStart = decoded.indexOf("</font>", if (openEnd != -1) openEnd + 1 else i, ignoreCase = true)
                    val openTag = if (openEnd != -1) decoded.substring(i, openEnd + 1) else ""
                    val parsedColor = parseHtmlTagColor(openTag) ?: Color(0xFF2B7DE0)
                    if (openEnd != -1) {
                        if (closeStart != -1) {
                            val innerContent = cleanLeadingStarArtifacts(decoded.substring(openEnd + 1, closeStart))
                            withStyle(SpanStyle(color = parsedColor)) {
                                append(parseInlineMarkdown(innerContent))
                            }
                            var nextIdx = closeStart + "</font>".length
                            while (nextIdx < decoded.length && (decoded[nextIdx] == '*' || decoded[nextIdx] == ' ')) {
                                nextIdx++
                            }
                            i = nextIdx
                        } else {
                            // 流式未闭合标签：对剩余文本应用颜色渲染
                            val innerContent = cleanLeadingStarArtifacts(decoded.substring(openEnd + 1))
                            withStyle(SpanStyle(color = parsedColor)) {
                                append(parseInlineMarkdown(innerContent))
                            }
                            i = decoded.length
                        }
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // 孤立闭合标签 </font> 及后续残留的星号 (如 </font>*)
                decoded.startsWith("</font", i, ignoreCase = true) -> {
                    val closeEnd = decoded.indexOf('>', i)
                    var nextIdx = if (closeEnd != -1) closeEnd + 1 else (i + 7).coerceAtMost(decoded.length)
                    while (nextIdx < decoded.length && (decoded[nextIdx] == '*' || decoded[nextIdx] == ' ')) {
                        nextIdx++
                    }
                    i = nextIdx
                }

                // HTML <span style="...">text</span>
                decoded.startsWith("<span", i, ignoreCase = true) -> {
                    val openEnd = decoded.indexOf('>', i)
                    val closeStart = decoded.indexOf("</span>", if (openEnd != -1) openEnd + 1 else i, ignoreCase = true)
                    val openTag = if (openEnd != -1) decoded.substring(i, openEnd + 1) else ""
                    val parsedColor = parseHtmlTagColor(openTag) ?: Color(0xFF2B7DE0)
                    if (openEnd != -1) {
                        if (closeStart != -1) {
                            val innerContent = cleanLeadingStarArtifacts(decoded.substring(openEnd + 1, closeStart))
                            withStyle(SpanStyle(color = parsedColor)) {
                                append(parseInlineMarkdown(innerContent))
                            }
                            var nextIdx = closeStart + "</span>".length
                            while (nextIdx < decoded.length && (decoded[nextIdx] == '*' || decoded[nextIdx] == ' ')) {
                                nextIdx++
                            }
                            i = nextIdx
                        } else {
                            val innerContent = cleanLeadingStarArtifacts(decoded.substring(openEnd + 1))
                            withStyle(SpanStyle(color = parsedColor)) {
                                append(parseInlineMarkdown(innerContent))
                            }
                            i = decoded.length
                        }
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // 孤立闭合标签 </span> 及后续残留的星号
                decoded.startsWith("</span", i, ignoreCase = true) -> {
                    val closeEnd = decoded.indexOf('>', i)
                    var nextIdx = if (closeEnd != -1) closeEnd + 1 else (i + 7).coerceAtMost(decoded.length)
                    while (nextIdx < decoded.length && (decoded[nextIdx] == '*' || decoded[nextIdx] == ' ')) {
                        nextIdx++
                    }
                    i = nextIdx
                }

                // HTML <b> 或 <strong>
                decoded.startsWith("<b>", i, ignoreCase = true) || decoded.startsWith("<strong>", i, ignoreCase = true) -> {
                    val isStrong = decoded.startsWith("<strong>", i, ignoreCase = true)
                    val tagLen = if (isStrong) 8 else 3
                    val closeTag = if (isStrong) "</strong>" else "</b>"
                    val closeIndex = decoded.indexOf(closeTag, i + tagLen, ignoreCase = true)
                    if (closeIndex != -1) {
                        val inner = decoded.substring(i + tagLen, closeIndex)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(parseInlineMarkdown(inner))
                        }
                        i = closeIndex + closeTag.length
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // HTML <i> 或 <em>
                decoded.startsWith("<i>", i, ignoreCase = true) || decoded.startsWith("<em>", i, ignoreCase = true) -> {
                    val isEm = decoded.startsWith("<em>", i, ignoreCase = true)
                    val tagLen = if (isEm) 4 else 3
                    val closeTag = if (isEm) "</em>" else "</i>"
                    val closeIndex = decoded.indexOf(closeTag, i + tagLen, ignoreCase = true)
                    if (closeIndex != -1) {
                        val inner = decoded.substring(i + tagLen, closeIndex)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(parseInlineMarkdown(inner))
                        }
                        i = closeIndex + closeTag.length
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // HTML <u> 下划线
                decoded.startsWith("<u>", i, ignoreCase = true) -> {
                    val closeIndex = decoded.indexOf("</u>", i + 3, ignoreCase = true)
                    if (closeIndex != -1) {
                        val inner = decoded.substring(i + 3, closeIndex)
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(parseInlineMarkdown(inner))
                        }
                        i = closeIndex + "</u>".length
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // HTML <s>, <del>, <strike> 删除线
                decoded.startsWith("<s>", i, ignoreCase = true) || decoded.startsWith("<del>", i, ignoreCase = true) || decoded.startsWith("<strike>", i, ignoreCase = true) -> {
                    val closeTag = when {
                        decoded.startsWith("<del>", i, ignoreCase = true) -> "</del>"
                        decoded.startsWith("<strike>", i, ignoreCase = true) -> "</strike>"
                        else -> "</s>"
                    }
                    val openLen = if (closeTag == "</s>") 3 else if (closeTag == "</del>") 5 else 8
                    val closeIndex = decoded.indexOf(closeTag, i + openLen, ignoreCase = true)
                    if (closeIndex != -1) {
                        val inner = decoded.substring(i + openLen, closeIndex)
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(parseInlineMarkdown(inner))
                        }
                        i = closeIndex + closeTag.length
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // HTML <code>
                decoded.startsWith("<code>", i, ignoreCase = true) -> {
                    val closeIndex = decoded.indexOf("</code>", i + 6, ignoreCase = true)
                    if (closeIndex != -1) {
                        val inner = decoded.substring(i + 6, closeIndex)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                background = Color.Gray.copy(alpha = 0.12f)
                            )
                        ) {
                            append(inner)
                        }
                        i = closeIndex + "</code>".length
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // HTML <mark> 高亮
                decoded.startsWith("<mark>", i, ignoreCase = true) -> {
                    val closeIndex = decoded.indexOf("</mark>", i + 6, ignoreCase = true)
                    if (closeIndex != -1) {
                        val inner = decoded.substring(i + 6, closeIndex)
                        withStyle(SpanStyle(background = Color(0xFFFEF08A), color = Color(0xFF713F12))) {
                            append(parseInlineMarkdown(inner))
                        }
                        i = closeIndex + "</mark>".length
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // HTML <br> 或 <br/>
                decoded.startsWith("<br/>", i, ignoreCase = true) || decoded.startsWith("<br>", i, ignoreCase = true) -> {
                    append("\n")
                    i += if (decoded.startsWith("<br/>", i, ignoreCase = true)) 5 else 4
                }

                // LaTeX 行内数学块 $$...$$
                decoded.startsWith("$$", i) -> {
                    val end = decoded.indexOf("$$", i + 2)
                    if (end != -1 && end > i + 2) {
                        val math = decoded.substring(i + 2, end)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFF6BA4F8),
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(parseLaTeXToUnicode(math))
                        }
                        i = end + 2
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // LaTeX 行内数学块 \[...\]
                decoded.startsWith("\\[", i) -> {
                    val end = decoded.indexOf("\\]", i + 2)
                    if (end != -1 && end > i + 2) {
                        val math = decoded.substring(i + 2, end)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFF6BA4F8),
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(parseLaTeXToUnicode(math))
                        }
                        i = end + 2
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // LaTeX 行内数学公式 \(...\)
                decoded.startsWith("\\(", i) -> {
                    val end = decoded.indexOf("\\)", i + 2)
                    if (end != -1) {
                        val math = decoded.substring(i + 2, end)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFF6BA4F8),
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(parseLaTeXToUnicode(math))
                        }
                        i = end + 2
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // LaTeX 行内数学公式 $...$
                decoded.startsWith("$", i) && isInlineMathStart(decoded, i) -> {
                    val end = decoded.indexOf('$', i + 1)
                    if (end != -1 && end > i + 1 && !decoded.substring(i + 1, end).contains('\n')) {
                        val math = decoded.substring(i + 1, end)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFF6BA4F8),
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(parseLaTeXToUnicode(math))
                        }
                        i = end + 1
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // 4 星以上粗体 ****text**** (常见大模型强化输出)
                decoded.startsWith("****", i) -> {
                    val end = decoded.indexOf("****", i + 4)
                    if (end != -1 && end > i + 4) {
                        val boldText = decoded.substring(i + 4, end)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSynthesis = FontSynthesis.Weight)) {
                            append(parseInlineMarkdown(boldText))
                        }
                        i = end + 4
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // 粗斜体 ***text*** 或 ___text___ 或 **_text_** 或 *__text__*，及非对称容错 ***text**
                (decoded.startsWith("***", i) || decoded.startsWith("___", i)) -> {
                    val marker = if (decoded.startsWith("***", i)) "***" else "___"
                    val end = decoded.indexOf(marker, i + 3)
                    val end2 = if (marker == "***") decoded.indexOf("**", i + 3) else -1

                    // 优先采用最近邻闭合策略，防止行内非对称星号跨词吞噬后续文本
                    val effectiveEnd: Int
                    val closeLen: Int
                    val isBoldItalic: Boolean

                    if (marker == "***" && end2 != -1 && !decoded.startsWith("***", end2) && (end == -1 || end2 < end)) {
                        // 容错匹配：开头 3 星，最近结尾为 2 星 (如 ***text**)
                        effectiveEnd = end2
                        closeLen = 2
                        isBoldItalic = false
                    } else if (end != -1) {
                        // 标准匹配：***text*** 或 ___text___
                        effectiveEnd = end
                        closeLen = 3
                        isBoldItalic = true
                    } else {
                        effectiveEnd = -1
                        closeLen = 0
                        isBoldItalic = false
                    }

                    if (effectiveEnd != -1 && effectiveEnd > i + 3) {
                        val innerText = decoded.substring(i + 3, effectiveEnd)
                        val spanStyle = if (isBoldItalic) {
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSynthesis = FontSynthesis.All)
                        } else {
                            SpanStyle(fontWeight = FontWeight.Bold, fontSynthesis = FontSynthesis.Weight)
                        }
                        withStyle(spanStyle) {
                            append(parseInlineMarkdown(innerText))
                        }
                        i = effectiveEnd + closeLen
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                decoded.startsWith("**_", i) -> {
                    val end = decoded.indexOf("_**", i + 3)
                    if (end != -1) {
                        val boldItalicText = decoded.substring(i + 3, end)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSynthesis = FontSynthesis.All)) {
                            append(parseInlineMarkdown(boldItalicText))
                        }
                        i = end + 3
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                decoded.startsWith("*__", i) -> {
                    val end = decoded.indexOf("__*", i + 3)
                    if (end != -1) {
                        val boldItalicText = decoded.substring(i + 3, end)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSynthesis = FontSynthesis.All)) {
                            append(parseInlineMarkdown(boldItalicText))
                        }
                        i = end + 3
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // 粗体 **text** 或 __text__，及非对称容错 **text***
                (decoded.startsWith("**", i) || decoded.startsWith("__", i)) -> {
                    val marker = if (decoded.startsWith("**", i)) "**" else "__"
                    val end = decoded.indexOf(marker, i + 2)
                    if (end != -1 && end > i + 2) {
                        val boldText = decoded.substring(i + 2, end)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSynthesis = FontSynthesis.Weight)) {
                            append(parseInlineMarkdown(boldText))
                        }
                        // 容错：若结尾多出一个星号（如 **text***），一同消费，避免留下孤立的星号
                        if (marker == "**" && end + 2 < decoded.length && decoded[end + 2] == '*') {
                            i = end + 3
                        } else {
                            i = end + 2
                        }
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // 斜体 *text* 或 _text_
                (decoded.startsWith("*", i) && !decoded.startsWith("**", i)) ||
                (decoded.startsWith("_", i) && !decoded.startsWith("__", i)) -> {
                    val marker = if (decoded.startsWith("*", i)) "*" else "_"
                    val end = decoded.indexOf(marker, i + 1)
                    if (end != -1 && end > i + 1) {
                        val italicText = decoded.substring(i + 1, end)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontSynthesis = FontSynthesis.Style)) {
                            append(parseInlineMarkdown(italicText))
                        }
                        i = end + 1
                    } else {
                        // 孤立未配对星号：正常作为字面量字符追加，避免首字符星号在流式或单字时被误吞
                        append(decoded[i])
                        i++
                    }
                }

                // 删除线 ~~text~~
                decoded.startsWith("~~", i) -> {
                    val end = decoded.indexOf("~~", i + 2)
                    if (end != -1) {
                        val strikethroughText = decoded.substring(i + 2, end)
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(parseInlineMarkdown(strikethroughText))
                        }
                        i = end + 2
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // 行内代码 `code`
                decoded.startsWith("`", i) -> {
                    val end = decoded.indexOf("`", i + 1)
                    if (end != -1) {
                        val codeText = decoded.substring(i + 1, end)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                background = Color.Gray.copy(alpha = 0.12f)
                            )
                        ) {
                            append(codeText)
                        }
                        i = end + 1
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // 引用角标 [1] 或 [^1]
                decoded.startsWith("[", i) && isCitationMarkerAt(decoded, i) -> {
                    val closeBracket = decoded.indexOf("]", i)
                    if (closeBracket != -1) {
                        val numStr = decoded.substring(i + 1, closeBracket).removePrefix("^")
                        pushStringAnnotation(tag = "CITATION", annotation = numStr)

                        if (isReferenceItem || i == 0) {
                            // 末尾参考资料列表展示为正常大小，不使用上标
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF6BA4F8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            ) {
                                append("[$numStr] ")
                            }
                        } else {
                            // 正文内引用角标使用微型上标
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF6BA4F8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    baselineShift = BaselineShift.Superscript
                                )
                            ) {
                                append("[$numStr]")
                            }
                        }
                        pop()
                        i = closeBracket + 1
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // Markdown 链接 [text](url)
                decoded.startsWith("[", i) -> {
                    val closeBracket = decoded.indexOf("]", i)
                    if (closeBracket != -1 && closeBracket + 1 < decoded.length && decoded[closeBracket + 1] == '(') {
                        val closeParen = decoded.indexOf(")", closeBracket + 1)
                        if (closeParen != -1) {
                            val linkText = decoded.substring(i + 1, closeBracket)
                            val url = decoded.substring(closeBracket + 2, closeParen)
                            pushStringAnnotation(tag = "URL", annotation = url)
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF6BA4F8),
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append(linkText)
                            }
                            pop()
                            i = closeParen + 1
                        } else {
                            append(decoded[i])
                            i++
                        }
                    } else {
                        append(decoded[i])
                        i++
                    }
                }

                // 纯文本链接 http:// 或 https:// 自动识别为可点击
                decoded.startsWith("http://", i) || decoded.startsWith("https://", i) -> {
                    var end = i
                    while (end < decoded.length && !decoded[end].isWhitespace() && decoded[end] !in listOf(')', ']', '}', '>', '"', '\'')) {
                        end++
                    }
                    val url = decoded.substring(i, end)
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF6BA4F8),
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(url)
                    }
                    pop()
                    i = end
                }

                // 普通字符
                else -> {
                    append(decoded[i])
                    i++
                }
            }
        }
    }
}

}