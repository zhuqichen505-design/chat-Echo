package com.aiassistant.data.repository.helpers

/** Separates a refusal of the summarization task from facts quoted inside a story. */
object SummaryResponseValidator {
    private val headingOrEmptyLine = Regex(
        "^(?:#{1,6}\\s+.+|【[^】]+】[：:]?|[-=_*]{3,}|(?:[-*•]|\\d+[.)、])\\s*|[^：:]{1,100}[：:])$"
    )
    private val placeholders = setOf("待补充", "待填写", "暂无内容", "无内容", "todo", "tbd", "n/a", "...", "…")
    private val summaryTask = Regex(
        "摘要|总结|概括|压缩|上下文|(?:本次|这个|该|此)(?:请求|任务)|这(?:类|种)(?:内容|请求)|所请求|提供的(?:内容|文本)|" +
            "\\b(?:summari[sz](?:e|ing|ation)|summary|your request|this request|that request|provided (?:content|text))\\b",
        RegexOption.IGNORE_CASE
    )
    private val policy = Regex(
        "安全(?:政策|策略|限制|规范|规则|准则)|内容(?:政策|限制|审核|规范)|违反.{0,12}(?:政策|规则|规范)|" +
            "(?:平台|使用|相关|适用)(?:政策|规则)|不适当|不允许的内容|" +
            "\\b(?:safety|content policy|policies|guidelines|prohibited|disallowed|not permitted)\\b",
        RegexOption.IGNORE_CASE
    )
    private val apology = Regex("抱歉|对不起|很遗憾|\\b(?:sorry|apologi[sz]e)\\b", RegexOption.IGNORE_CASE)
    private val chineseRefusal = Regex(
        "(?:无法|未能|不能|不会|没法|不便|不(?:被)?允许|拒绝)[^，。！？；;\\n]{0,16}?" +
            "(?:生成|提供|返回|输出|处理|完成|协助|帮助|总结|概括|压缩|整理|执行|撰写|创作|描述|显示)[^。！？\\n]{0,100}"
    )
    private val chineseSelf = Regex("(?:我(?:们)?|本(?:助手|模型|系统))[^。！？\\n]{0,30}(?:无法|未能|不能|不会|没法|不便|不被允许|拒绝)")
    private val chineseTaskAction = Regex(
        "生成|输出|总结|概括|压缩|撰写|创作|(?:提供|返回|处理|完成|协助|帮助|整理|执行|描述|显示).{0,24}(?:摘要|正文|文本|请求|任务|输入|上下文|这类内容|这种内容)"
    )
    private val chinesePreamble = Regex(
        "^(?:(?:很?抱歉|对不起|很遗憾)[，,\\s]*)?(?:(?:由于|根据|出于|基于|受).{0,70}[，,:：\\s]*)?$"
    )
    private val englishRefusal = Regex(
        "\\b(?:(?:i|we)\\s+(?:cannot|can['’]t|will not|won['’]t)|" +
            "(?:i(?:['’]m|\\s+am)|we(?:['’]re|\\s+are))\\s+(?:currently\\s+)?(?:unable|not able|not allowed|not permitted)\\s+to)\\s+" +
            "(?:provide|generate|produce|return|process|complete|assist|help|summari[sz]e|continue|fulfill|comply|write|create|describe)\\b[^.!?\\n]{0,160}",
        RegexOption.IGNORE_CASE
    )
    private val replacementRequest = Regex(
        "请.{0,28}(?:修改|调整|更换|重新提供|提供其他|移除|删除|重试)|" +
            "\\b(?:please|you can|you may)\\s+.{0,50}(?:revise|modify|change|remove|different|try again)\\b",
        RegexOption.IGNORE_CASE
    )
    private val blockedRequest = Regex(
        "(?:本次|当前|这个|该|此|提供的|所提供的).{0,18}(?:请求|内容|文本|输入|提示).{0,25}(?:触发|违反|不符合|被拦截|受到限制)|" +
            "\\b(?:your|this|that|the provided)\\s+(?:request|content|text|input)\\b.{0,70}\\b(?:blocked|violates?|trigger(?:s|ed)?|not comply)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Returns a bounded model explanation, or null when no task refusal is detected. */
    fun refusalReason(text: String?): String? {
        val body = ConversationTitleHelper.sanitizeSummaryCompletion(text, preserveCompleteText = true) ?: return null
        val readable = unwrapMarkdown(body).take(2400)
        // Keep a quotation around the entire response as a wrapper, but remove embedded dialogue.
        // A character saying "I cannot help" is evidence about the story, not an assistant refusal.
        val prose = maskQuotedDialogue(readable)
        val hasPolicy = policy.containsMatchIn(prose)
        val chinese = chineseRefusal.findAll(prose).any { match ->
            val sentenceStart = prose.lastIndexOfAny(charArrayOf('。', '！', '？', '\n'), match.range.first - 1) + 1
            val sentence = prose.substring(sentenceStart, match.range.last + 1)
            val ownRefusal = chineseSelf.containsMatchIn(sentence)
            val task = summaryTask.containsMatchIn(sentence)
            val policyContext = policy.containsMatchIn(sentence) || (ownRefusal && sentenceStart < 240 && hasPolicy)
            val preamble = prose.substring(sentenceStart, match.range.first).trim()
            val taskAction = chineseTaskAction.containsMatchIn(match.value)
            (ownRefusal || chinesePreamble.matches(preamble)) &&
                (task || (taskAction && (policyContext || apology.containsMatchIn(sentence))))
        }
        val english = englishRefusal.findAll(prose).any { match ->
            val sentenceStart = prose.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), match.range.first - 1) + 1
            val sentence = prose.substring(sentenceStart, match.range.last + 1)
            summaryTask.containsMatchIn(sentence) || policy.containsMatchIn(sentence) || apology.containsMatchIn(sentence) ||
                Regex("\\b(?:this|that|your request)\\b", RegexOption.IGNORE_CASE).containsMatchIn(match.value)
        }
        val blocked = blockedRequest.containsMatchIn(prose) && hasPolicy && replacementRequest.containsMatchIn(prose)
        return if (chinese || english || blocked) readable.take(600).trim().ifBlank { null } else null
    }

    /** Transport completion must be checked by the caller before accepting this text. */
    fun isUsableSummary(summary: String?): Boolean {
        val clean = ConversationTitleHelper.sanitizeSummaryCompletion(summary, preserveCompleteText = true)?.trim().orEmpty()
        if (clean.length < 24 || !hasSubstantiveContent(clean) || refusalReason(clean) != null) return false
        if (ConversationTitleHelper.isSummarySubstantiallyComplete(clean)) return true
        val last = clean.lineSequence().map(String::trim).filter(String::isNotBlank).lastOrNull().orEmpty()
        if (last.endsWith("：") || last.endsWith(":")) return false
        if (last.matches(Regex("^【.+?】[：:]?\\s*$"))) return false
        return clean.any { it in "。！？.!?" } || clean.lineSequence().count { it.isNotBlank() } >= 2
    }

    /** Preserve short manual facts, while rejecting an empty outline regardless of its total length. */
    fun hasSubstantiveContent(summary: String?): Boolean {
        val clean = ConversationTitleHelper.sanitizeSummaryCompletion(summary, preserveCompleteText = true) ?: return false
        val lines = clean.lines()
        return lines.withIndex().any { (index, rawLine) ->
            val line = rawLine.trim().removePrefix("> ").trim()
            val plain = line.trim('*', '_', '`', ' ', '-', '•').trim().lowercase(java.util.Locale.ROOT)
            val setextHeading = lines.getOrNull(index + 1)?.trim()?.matches(Regex("[=-]{3,}")) == true
            line.isNotBlank() && !setextHeading && !headingOrEmptyLine.matches(line) && plain !in placeholders && plain.any(Char::isLetterOrDigit)
        }
    }

    private fun unwrapMarkdown(text: String): String {
        var clean = text.trim().removePrefix("\uFEFF")
            .replace(Regex("(?m)^\\s*```[^\\n]*$"), "")
            .replace(Regex("(?m)^\\s*(?:>\\s*)*(?:#{1,6}\\s+)?"), "")
            .replace("**", "").replace("__", "").replace("`", "").trim()
        val wrappers = listOf('“' to '”', '「' to '」', '『' to '』', '"' to '"')
        while (clean.length > 1 && wrappers.any { clean.first() == it.first && clean.last() == it.second }) {
            clean = clean.substring(1, clean.lastIndex).trim()
        }
        return clean
    }

    private fun maskQuotedDialogue(text: String): String = text
        .replace(Regex("“[^”]*”|「[^」]*」|『[^』]*』|\"[^\"]*\"", RegexOption.DOT_MATCHES_ALL)) { match ->
            val quote = match.value.substring(1, match.value.lastIndex)
            if (quote.length >= 12 || chineseSelf.containsMatchIn(quote) || englishRefusal.containsMatchIn(quote)) " " else quote
        }
}
