package com.aiassistant.data.repository.helpers

/**
 * 对话标题提取、清洗、去重与滚动摘要完整性判定助手
 */
object ConversationTitleHelper {

    fun sanitizeGeneratedTitle(rawTitle: String?): String? {
        if (rawTitle.isNullOrBlank()) return null

        // 1. 剔除思考过程标签（支持 <think>...</think> 或未闭合的 <think>）
        var cleaned = rawTitle.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        if (cleaned.contains("<think>")) {
            cleaned = cleaned.substringAfterLast("</think>", cleaned.substringBefore("<think>")).trim()
        }

        // 2. 取第一行非空文字
        var firstLine = cleaned
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() } ?: return null

        // 3. 剥离常见前缀（例如 "标题：", "对话标题：", "建议标题：", "Title:", "1. " 等）
        val prefixRegex = Regex("^(?:[0-9]+[\\.、]|[-*]\\s*|对话标题[：:]|标题[：:]|建议标题[：:]|Title[：:]|Topic[：:])\\s*", RegexOption.IGNORE_CASE)
        firstLine = firstLine.replace(prefixRegex, "").trim()

        // 4. 剥离首尾引号、标点与多余符号
        firstLine = firstLine
            .trim('"', '\'', '“', '”', '「', '」', '《', '》', '【', '】', '`', '。', '.', '：', ':', '！', '!', '？', '?')
            .trim()

        // 5. 限制在18个汉字/字符以内
        val title = firstLine.take(18).trim()
        return title.ifBlank { null }
    }

    /**
     * 滚动摘要生成文本清洗与尾部断句防腰斩安全闭合保护：
     * 1. 彻底剔除思考模型遗留的 <think>...</think> 过程；
     * 2. 循环检测并清除尾部因模型生成中断或截断遗留的孤立空章节标题与半句残词；
     * 3. 若尾行缺失合法标点，安全修剪至上一处合法完整句子或安全闭合，彻底杜绝半截文字或孤立空标题入库。
     */
    fun sanitizeSummaryCompletion(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        var text = rawText.trim()
        if (text.contains("<think>")) {
            text = text.substringAfterLast("</think>", text.substringBefore("<think>")).trim()
        }
        if (text.isBlank()) return null

        val lines = text.lines().map { it.trimEnd() }.toMutableList()
        val validEndPunctuation = setOf('。', '！', '？', '；', '”', '’', '"', '\'', '…', '.', '!', '?', ')')

        fun isSectionHeaderOrDangling(line: String): Boolean {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return true
            if (trimmed.startsWith("【") && trimmed.endsWith("】")) return true
            if (trimmed.startsWith("#")) return true
            if (trimmed.matches(Regex("""^【.+?】[：:]?\s*$"""))) return true
            if (trimmed.matches(Regex("""^\d+[\.、\s]*$"""))) return true
            if (trimmed.matches(Regex("""^[-*•][\s]*$"""))) return true
            if (trimmed.endsWith("：") || (trimmed.endsWith(":") && !trimmed.contains("http"))) return true
            return false
        }

        while (lines.isNotEmpty()) {
            // 1. 移除末尾空白行
            while (lines.isNotEmpty() && lines.last().isBlank()) {
                lines.removeAt(lines.lastIndex)
            }
            if (lines.isEmpty()) return null

            val lastLine = lines.last().trim()

            // 2. 检测末尾是否为悬空的孤立章节标题或序号前缀
            if (isSectionHeaderOrDangling(lastLine)) {
                if (lines.size > 1) {
                    lines.removeAt(lines.lastIndex)
                    continue
                } else {
                    return null
                }
            }

            val lastChar = lastLine.lastOrNull()
            if (lastChar != null && lastChar !in validEndPunctuation) {
                // 最后一行未以正常结束标点结尾，说明在半句被截断
                val lastSentenceEndIdx = lastLine.indexOfLast { it in validEndPunctuation }
                if (lastSentenceEndIdx >= 0 && lastSentenceEndIdx >= lastLine.length / 3) {
                    // 最后一行内部有完整句末标点，修剪掉尾部半截残句
                    lines[lines.lastIndex] = lastLine.substring(0, lastSentenceEndIdx + 1).trim()
                    continue
                } else if (lines.size > 1) {
                    // 若最后一行几乎全部是破碎残句且已有前序内容，直接剔除此残缺行
                    lines.removeAt(lines.lastIndex)
                    continue
                } else {
                    // 仅单行且无句尾标点，去除末尾残留标点并安全追加句号
                    val cleanedSingle = lastLine.trimEnd('，', ',', '、', '-', ' ', '：', ':')
                    lines[0] = cleanedSingle + "。"
                    break
                }
            } else {
                break
            }
        }

        val result = lines.joinToString("\n").trim()
        return result.ifBlank { null }
    }

    /**
     * 判定滚动摘要是否实质完整
     */
    fun isSummarySubstantiallyComplete(summary: String?): Boolean {
        if (summary.isNullOrBlank()) return false
        val clean = summary.trim()
        if (clean.length < 35) return false
        val nonBlankLines = clean.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (nonBlankLines.isEmpty()) return false
        val lastLine = nonBlankLines.last()
        if (lastLine.startsWith("【") && lastLine.endsWith("】")) return false
        if (lastLine.matches(Regex("""^【.+?】[：:]?\s*$"""))) return false
        if (lastLine.endsWith("：") || lastLine.endsWith(":")) return false
        if (lastLine.matches(Regex("""^\d+[\.、\s]*$"""))) return false
        val bracketHeaderCount = Regex("""^【.+?】""", RegexOption.MULTILINE).findAll(clean).count()
        if (clean.contains("【") && bracketHeaderCount < 2) return false
        return true
    }

    fun generateDuplicateTitle(originalTitle: String): String {
        val trimmed = originalTitle.trim()
        if (trimmed.isEmpty()) return "未命名对话 (副本)"
        val copyRegex = Regex("""^(.*?)\s*\(副本(?:\s*(\d+))?\)$""")
        val match = copyRegex.find(trimmed)
        return if (match != null) {
            val baseName = match.groupValues[1].trim()
            val copyIndex = match.groupValues[2].toIntOrNull() ?: 1
            "$baseName (副本 ${copyIndex + 1})"
        } else {
            "$trimmed (副本)"
        }
    }

    fun extractRootBaseTitle(rawTitle: String): String {
        var current = rawTitle.trim()
        val branchSuffixRegex = Regex("""[\s_]*[\(（]分支[\s_]*\d*[\)）]\s*$""")
        while (true) {
            val next = current.replace(branchSuffixRegex, "").trim()
            if (next == current || next.isEmpty()) break
            current = next
        }
        return current.ifBlank { "对话" }
    }

    fun calculateNextBranchTitle(rootBaseTitle: String, existingTitles: List<String>): String {
        val escapedBase = Regex.escape(rootBaseTitle)
        val branchIndexRegex = Regex("""^$escapedBase[\s_]*[\(（]分支[\s_]*(\d*)[\)）]$""")
        var maxIndex = 0
        var hasUnnumberedBranch = false

        for (title in existingTitles) {
            val trimmed = title.trim()
            val match = branchIndexRegex.find(trimmed)
            if (match != null) {
                val numStr = match.groupValues[1]
                if (numStr.isNotBlank()) {
                    val num = numStr.toIntOrNull() ?: 0
                    if (num > maxIndex) maxIndex = num
                } else {
                    hasUnnumberedBranch = true
                }
            }
        }

        val nextIndex = if (maxIndex > 0) {
            maxIndex + 1
        } else if (hasUnnumberedBranch) {
            2
        } else {
            1
        }

        return "$rootBaseTitle (分支 $nextIndex)"
    }
}
