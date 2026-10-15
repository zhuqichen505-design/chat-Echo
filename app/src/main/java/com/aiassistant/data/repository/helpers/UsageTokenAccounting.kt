package com.aiassistant.data.repository.helpers

/** 两种协议都把推理包含在生成量内；缓存命中是输入的子集。缺失字段允许估算但须标记。 */
internal object UsageTokenAccounting {
    data class Counts(val input: Int, val output: Int, val reasoning: Int, val total: Int, val cached: Int, val source: String)

    fun normalize(
        input: Int?, completion: Int?, reasoning: Int?, total: Int?, cached: Int?,
        estimatedInput: Int, estimatedOutput: Int, estimatedReasoning: Int
    ): Counts {
        val safeInput = input?.coerceAtLeast(0) ?: estimatedInput.coerceAtLeast(0)
        val safeReasoning = (reasoning ?: estimatedReasoning).coerceAtLeast(0)
            .let { if (completion != null) it.coerceAtMost(completion.coerceAtLeast(0)) else it }
        val safeOutput = if (completion != null) (completion.coerceAtLeast(0) - safeReasoning).coerceAtLeast(0)
            else estimatedOutput.coerceAtLeast(0)
        val known = (safeInput.toLong() + safeOutput + safeReasoning).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return Counts(safeInput, safeOutput, safeReasoning, maxOf(total?.coerceAtLeast(0) ?: 0, known),
            cached?.coerceIn(0, safeInput) ?: 0,
            when {
                input == null && completion == null && reasoning == null && total == null && (cached == null || cached == 0) -> "estimated"
                input != null && completion != null && (reasoning != null || estimatedReasoning == 0) -> "reported"
                else -> "mixed"
            })
    }
}
