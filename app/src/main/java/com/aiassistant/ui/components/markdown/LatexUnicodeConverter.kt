package com.aiassistant.ui.components.markdown

/**
 * LaTeX 数学公式解析与易读 Unicode 排版转换引擎
 */
object LatexUnicodeConverter {
    private val LATEX_SYMBOL_MAP_SORTED: List<Pair<String, String>> = listOf(
        // 三角/对数/极限/分析函数
        "\\arcsin" to "arcsin", "\\arccos" to "arccos", "\\arctan" to "arctan",
        "\\sinh" to "sinh", "\\cosh" to "cosh", "\\tanh" to "tanh",
        "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan", "\\cot" to "cot",
        "\\sec" to "sec", "\\csc" to "csc", "\\ln" to "ln", "\\log" to "log",
        "\\lg" to "lg", "\\exp" to "exp", "\\lim" to "lim", "\\max" to "max",
        "\\min" to "min", "\\sup" to "sup", "\\inf" to "inf", "\\det" to "det",
        "\\gcd" to "gcd", "\\arg" to "arg", "\\deg" to "deg", "\\dim" to "dim",
        "\\ker" to "ker", "\\hom" to "hom", "\\Pr" to "Pr", "\\bmod" to " mod ",
        "\\pmod" to " mod ",

        // 希腊字母（小写）
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ",
        "\\pi" to "π", "\\varpi" to "ϖ", "\\rho" to "ρ", "\\varrho" to "ϱ",
        "\\sigma" to "σ", "\\varsigma" to "ς", "\\tau" to "τ", "\\upsilon" to "υ",
        "\\phi" to "φ", "\\varphi" to "ϕ", "\\chi" to "χ", "\\psi" to "ψ",
        "\\omega" to "ω",

        // 希腊字母（大写）
        "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
        "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Upsilon" to "Υ",
        "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω",

        // 关系与运算符
        "\\times" to " × ", "\\cdot" to " · ", "\\div" to " ÷ ", "\\pm" to " ± ",
        "\\mp" to " ∓ ", "\\ast" to " * ", "\\star" to " ★ ", "\\circ" to " ∘ ",
        "\\bullet" to " • ", "\\oplus" to " ⊕ ", "\\otimes" to " ⊗ ", "\\odot" to " ⊙ ",
        "\\leq" to " ≤ ", "\\le" to " ≤ ", "\\geq" to " ≥ ", "\\ge" to " ≥ ",
        "\\neq" to " ≠ ", "\\ne" to " ≠ ", "\\approx" to " ≈ ", "\\equiv" to " ≡ ",
        "\\sim" to " ∼ ", "\\simeq" to " ≃ ", "\\cong" to " ≅ ", "\\propto" to " ∝ ",
        "\\ll" to " ≪ ", "\\gg" to " ≫ ", "\\mid" to " | ", "\\nmid" to " ∤ ",
        "\\parallel" to " ∥ ", "\\perp" to " ⊥ ",

        // 逻辑与集合
        "\\forall" to "∀", "\\exists" to "∃", "\\nexists" to "∄", "\\in" to " ∈ ",
        "\\notin" to " ∉ ", "\\ni" to " ∋ ", "\\subset" to " ⊂ ", "\\supset" to " ⊃ ",
        "\\subseteq" to " ⊆ ", "\\supseteq" to " ⊇ ", "\\cup" to " ∪ ", "\\cap" to " ∩ ",
        "\\setminus" to " \\ ", "\\emptyset" to "∅", "\\varnothing" to "∅",
        "\\land" to " ∧ ", "\\lor" to " ∨ ", "\\neg" to "¬", "\\lnot" to "¬",

        // 箭头
        "\\rightarrow" to " → ", "\\to" to " → ", "\\leftarrow" to " ← ",
        "\\Rightarrow" to " ⇒ ", "\\Leftarrow" to " ⇐ ", "\\Leftrightarrow" to " ⇔ ",
        "\\iff" to " ⇔ ", "\\implies" to " ⇒ ", "\\mapsto" to " ↦ ",
        "\\uparrow" to " ↑ ", "\\downarrow" to " ↓ ", "\\updownarrow" to " ↕ ",
        "\\nearrow" to " ↗ ", "\\searrow" to " ↘ ",

        // 微积分与特殊算子
        "\\sum" to "∑", "\\prod" to "∏", "\\coprod" to "∐",
        "\\int" to "∫", "\\iint" to "∬", "\\iiint" to "∭", "\\oint" to "∮",
        "\\nabla" to "∇", "\\partial" to "∂", "\\infty" to "∞",

        // 省略号与点
        "\\cdots" to "⋯", "\\ldots" to "…", "\\dots" to "…",
        "\\vdots" to "⋮", "\\ddots" to "⋱",

        // 装饰重音
        "\\vec" to "", "\\hat" to "", "\\bar" to "", "\\tilde" to "", "\\dot" to "", "\\ddot" to "",

        // 空格与括号界定符
        "\\quad" to "   ", "\\qquad" to "      ", "\\," to " ", "\\;" to " ",
        "\\:" to " ", "\\!" to "",
        "\\left(" to "(", "\\right)" to ")",
        "\\left[" to "[", "\\right]" to "]",
        "\\left\\{" to "{", "\\right\\}" to "}",
        "\\left|" to "|", "\\right|" to "|",
        "\\left." to "", "\\right." to "",
        "\\left" to "", "\\right" to "",
        "\\{" to "{", "\\}" to "}", "\\%" to "%", "\\_" to "_", "\\&" to "&"
    ).sortedByDescending { it.first.length }

    // v2.7.3 流畅度：映射表与正则原先在每次 parseLaTeXToUnicode / convertSuperSubScripts 调用时
    // 全量重建（符号表 150 项 + sortedByDescending 排序 + 5 个 Regex），流式尾部每个行内公式
    // 片段每帧触发；提升为常量，一次构建终身复用（不可变、线程安全）
    private val LATEX_WHITESPACE_REGEX = Regex("""[ \t]+""")
    private val LATEX_ENV_REGEX = Regex("""\\begin\{([a-zA-Z*]+)\}([\s\S]*?)\\end\{\1\}""")
    private val SUPERSCRIPT_BRACE_REGEX = Regex("""\^\{([^}]+)\}""")
    private val SUPERSCRIPT_CHAR_REGEX = Regex("""\^([0-9a-zA-Z+\-=])""")
    private val SUBSCRIPT_BRACE_REGEX = Regex("""_\{([^}]+)\}""")
    private val SUBSCRIPT_CHAR_REGEX = Regex("""_([0-9a-zA-Z+\-=])""")
    private val FONT_CMDS = listOf("\\mathbf", "\\mathit", "\\mathrm", "\\text", "\\textbf", "\\textit", "\\operatorname", "\\bm", "\\boldsymbol", "\\pmb", "\\underline", "\\overline")
    private val MATHBB_MAP = mapOf(
        "\\mathbb{R}" to "ℝ", "\\mathbb{N}" to "ℕ", "\\mathbb{Z}" to "ℤ",
        "\\mathbb{Q}" to "ℚ", "\\mathbb{C}" to "ℂ", "\\mathbb{H}" to "ℍ",
        "\\mathbb{P}" to "ℙ", "\\mathbb{E}" to "𝔼"
    )
/**
 * 将常见 LaTeX 数学符号与表达式转换为易读的数学 Unicode 排版
 */
fun parseLaTeXToUnicode(raw: String): String {
    var text = raw.trim()
    if (text.isBlank()) return ""

    // 1. 解析多行环境（pmatrix, bmatrix, cases, aligned等）
    text = parseEnvironments(text)

    // 2. 解析分数 \frac{a}{b} 与 \dfrac, \tfrac
    text = text.replace("\\dfrac", "\\frac").replace("\\tfrac", "\\frac")
    text = parseFractions(text)

    // 3. 解析根号 \sqrt[n]{x} 与 \sqrt{x}
    text = parseRoots(text)

    // 4. 清理格式化指令如 \mathbf{x} -> x, \text{abc} -> abc
    for (cmd in FONT_CMDS) {
        var idx = text.indexOf(cmd)
        var guard = 0
        while (idx != -1 && guard < 50) {
            guard++
            val braceStart = text.indexOf('{', idx + cmd.length)
            if (braceStart != -1 && text.substring(idx + cmd.length, braceStart).trim().isEmpty()) {
                val braceEnd = findMatchingBrace(text, braceStart)
                if (braceEnd != -1) {
                    val inner = text.substring(braceStart + 1, braceEnd)
                    text = text.substring(0, idx) + inner + text.substring(braceEnd + 1)
                    idx = text.indexOf(cmd)
                    continue
                }
            }
            break
        }
    }

    // 5. 黑体集合 \mathbb{R} -> ℝ 等
    for ((latex, unicode) in MATHBB_MAP) {
        text = text.replace(latex, unicode)
    }

    // 6. 符号与函数映射表（v2.7.3 提升为常量并预排序）
    for ((latex, unicode) in LATEX_SYMBOL_MAP_SORTED) {
        text = text.replace(latex, unicode)
    }

    // 7. 转换上下标
    text = convertSuperSubScripts(text)

    // 8. 规整多余空格
    return text.replace(LATEX_WHITESPACE_REGEX, " ").trim()
}

private fun findMatchingBrace(text: String, openBraceIndex: Int): Int {
    if (openBraceIndex < 0 || openBraceIndex >= text.length || text[openBraceIndex] != '{') return -1
    var depth = 0
    for (i in openBraceIndex until text.length) {
        when (text[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return i
            }
        }
    }
    return -1
}

private fun parseEnvironments(input: String): String {
    var text = input
    val envRegex = LATEX_ENV_REGEX
    text = envRegex.replace(text) { matchResult ->
        val env = matchResult.groupValues[1]
        val body = matchResult.groupValues[2].trim()
        when (env) {
            "matrix" -> formatMatrix(body, "", "")
            "pmatrix" -> formatMatrix(body, "(", ")")
            "bmatrix" -> formatMatrix(body, "[", "]")
            "Bmatrix" -> formatMatrix(body, "{", "}")
            "vmatrix" -> formatMatrix(body, "|", "|")
            "Vmatrix" -> formatMatrix(body, "‖", "‖")
            "cases" -> formatCases(body)
            "aligned", "align", "equation", "gather", "split" -> {
                body.replace("\\\\", "\n").replace("&", " ")
            }
            else -> body.replace("\\\\", "\n").replace("&", " ")
        }
    }
    return text
}

private fun formatMatrix(body: String, leftDelim: String, rightDelim: String): String {
    val rows = body.split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
    if (rows.isEmpty()) return "$leftDelim $rightDelim"
    val parsedRows = rows.map { row ->
        row.split("&").map { it.trim() }.joinToString(" ")
    }
    return "$leftDelim " + parsedRows.joinToString(" ; ") + " $rightDelim"
}

private fun formatCases(body: String): String {
    val rows = body.split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
    if (rows.isEmpty()) return "{ "
    val parsedRows = rows.map { row ->
        row.split("&").map { it.trim() }.joinToString(", if ")
    }
    return "{ " + parsedRows.joinToString(" ; ")
}

private fun parseFractions(input: String): String {
    var text = input
    var idx = text.indexOf("\\frac")
    var guard = 0
    while (idx != -1 && guard < 50) {
        guard++
        val firstBrace = text.indexOf('{', idx)
        if (firstBrace == -1) break
        val firstEnd = findMatchingBrace(text, firstBrace)
        if (firstEnd == -1) break
        val secondBrace = text.indexOf('{', firstEnd + 1)
        if (secondBrace == -1) break
        if (text.substring(firstEnd + 1, secondBrace).trim().isNotEmpty()) break
        val secondEnd = findMatchingBrace(text, secondBrace)
        if (secondEnd == -1) break

        val numerator = text.substring(firstBrace + 1, firstEnd).trim()
        val denominator = text.substring(secondBrace + 1, secondEnd).trim()

        val parsedNum = parseFractions(numerator)
        val parsedDen = parseFractions(denominator)

        val formatted = if (!parsedNum.contains('/') && !parsedDen.contains('/') &&
            parsedNum.length <= 4 && parsedDen.length <= 4 &&
            !parsedNum.contains(' ') && !parsedDen.contains(' ')) {
            "$parsedNum/$parsedDen"
        } else {
            "($parsedNum) / ($parsedDen)"
        }
        text = text.substring(0, idx) + formatted + text.substring(secondEnd + 1)
        idx = text.indexOf("\\frac")
    }
    return text
}

private fun parseRoots(input: String): String {
    var text = input
    var idx = text.indexOf("\\sqrt")
    var guard = 0
    while (idx != -1 && guard < 50) {
        guard++
        val afterSqrt = idx + 5
        if (afterSqrt < text.length && text[afterSqrt] == '[') {
            val bracketEnd = text.indexOf(']', afterSqrt)
            if (bracketEnd != -1) {
                val degree = text.substring(afterSqrt + 1, bracketEnd).trim()
                val braceStart = text.indexOf('{', bracketEnd)
                if (braceStart != -1) {
                    val braceEnd = findMatchingBrace(text, braceStart)
                    if (braceEnd != -1) {
                        val body = text.substring(braceStart + 1, braceEnd).trim()
                        val degSuper = convertToSuperscript(degree)
                        text = text.substring(0, idx) + "${degSuper}√($body)" + text.substring(braceEnd + 1)
                        idx = text.indexOf("\\sqrt")
                        continue
                    }
                }
            }
        }
        val braceStart = text.indexOf('{', afterSqrt)
        if (braceStart != -1) {
            val braceEnd = findMatchingBrace(text, braceStart)
            if (braceEnd != -1) {
                val body = text.substring(braceStart + 1, braceEnd).trim()
                text = text.substring(0, idx) + "√($body)" + text.substring(braceEnd + 1)
                idx = text.indexOf("\\sqrt")
                continue
            }
        }
        break
    }
    return text
}

private fun convertToSuperscript(input: String): String {
    val supers = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ'
    )
    return input.map { supers[it] ?: it }.joinToString("")
}

private fun convertSuperSubScripts(input: String): String {
    val supers = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
        'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ',
        'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ',
        'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
        'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ'
    )
    val subs = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )

    var res = input
    // ^{...}
    res = SUPERSCRIPT_BRACE_REGEX.replace(res) { m ->
        val inner = m.groupValues[1]
        if (inner.all { supers.containsKey(it) || it.isWhitespace() }) {
            inner.map { supers[it] ?: it }.joinToString("")
        } else {
            "^($inner)"
        }
    }
    // ^x
    res = SUPERSCRIPT_CHAR_REGEX.replace(res) { m ->
        val c = m.groupValues[1][0]
        supers[c]?.toString() ?: "^$c"
    }
    // _{...}
    res = SUBSCRIPT_BRACE_REGEX.replace(res) { m ->
        val inner = m.groupValues[1]
        if (inner.all { subs.containsKey(it) || it.isWhitespace() }) {
            inner.map { subs[it] ?: it }.joinToString("")
        } else {
            "₍$inner₎"
        }
    }
    // _x
    res = SUBSCRIPT_CHAR_REGEX.replace(res) { m ->
        val c = m.groupValues[1][0]
        subs[c]?.toString() ?: "_$c"
    }

    return res
}

/**
 * 校验是否为行内数学公式起始（避免把普通美元价格如 $100 当作公式）
 */
fun isInlineMathStart(text: String, index: Int): Boolean {
    if (index + 1 >= text.length) return false
    val nextChar = text[index + 1]
    if (nextChar.isWhitespace() || nextChar.isDigit()) return false
    val nextDollar = text.indexOf('$', index + 1)
    if (nextDollar == -1 || nextDollar == index + 1) return false
    val content = text.substring(index + 1, nextDollar)
    return content.any { it.isLetter() || it in "+-*/=^_{}()\\<>" }
}
}