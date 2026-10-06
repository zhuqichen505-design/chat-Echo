package com.aiassistant.domain.model

/**
 * 上下文压缩档位枚举
 * 核心约束：压缩只作用于请求组装层，绝不物理删除或改写数据库消息。
 */
enum class CompressionTier(
    val level: Int,
    val displayName: String,
    val shortDesc: String,
    val detailLossNote: String
) {
    L0(
        level = 0,
        displayName = "完整保留",
        shortDesc = "默认档，行为与现状完全一致",
        detailLossNote = "无任何内容修剪，在预算范围内完整发送全部可用历史消息。"
    ),
    L1(
        level = 1,
        displayName = "轻量修剪",
        shortDesc = "剔除附件OCR/工具结果/代码块折叠，对话原文全保留",
        detailLossNote = "对话语言文字100%保留；剔除附件识别长文本、联网检索与工具结果大块、折叠多行长代码。"
    ),
    L2(
        level = 2,
        displayName = "滚动摘要",
        shortDesc = "较早轮次合并为摘要，保留最近 N 轮原文",
        detailLossNote = "超出最近 N 轮的较早历史合并为前序摘要，仅最近 N 轮对话保留逐字原文。"
    ),
    L3(
        level = 3,
        displayName = "深度压缩",
        shortDesc = "全部历史合并为结构化摘要，保留最近 16 轮原文",
        detailLossNote = "过往历史提炼为决策推进与待办要点三段式摘要，仅保留最近 16 轮逐字原文。"
    ),
    L4(
        level = 4,
        displayName = "极限压缩",
        shortDesc = "保留系统提示 + 记忆/固定事实 + 最近 8 轮原文",
        detailLossNote = "仅保留系统提示词、会话记忆与固定事实，并仅保留最近 8 轮原文。"
    ),
    LC(
        level = 5,
        displayName = "自定义比例",
        shortDesc = "保留最近 X% 消息原文，其余合并为摘要",
        detailLossNote = "按自定义百分比保留最近一段消息原文（预算内尽可能保留），更早历史交由摘要承载。"
    );

    companion object {
        fun fromLevel(level: Int?): CompressionTier = when (level) {
            1 -> L1
            2 -> L2
            3 -> L3
            4 -> L4
            5 -> LC
            else -> L0
        }
    }
}

/**
 * 档位压缩效果对比预览
 */
data class TierCompressionPreview(
    val tier: CompressionTier,
    val estimatedTokens: Int,
    val baselineTokens: Int,
    val tokensSaved: Int,
    val savingsPercent: Float,
    val retainedRoundsDesc: String,
    val lossNote: String
)

/**
 * 纯函数策略类：用于自动化升档判定、上限溢出自动降级与预估计算
 */
object CompressionTierPolicy {

    const val DEFAULT_L2_RECENT_ROUNDS = 8
    const val MIN_L2_RECENT_ROUNDS = 4
    const val MAX_L2_RECENT_ROUNDS = 32

    /** LC 自定义比例档：保留原文的消息条数占总消息数的百分比（10~90，步长 5） */
    const val DEFAULT_CUSTOM_RETAIN_PERCENT = 30
    const val MIN_CUSTOM_RETAIN_PERCENT = 10
    const val MAX_CUSTOM_RETAIN_PERCENT = 90
    const val STEP_CUSTOM_RETAIN_PERCENT = 5

    const val DEFAULT_THRESHOLD_L2 = 0.75f
    const val DEFAULT_THRESHOLD_L3 = 0.85f
    const val DEFAULT_THRESHOLD_L4 = 0.95f

    /**
     * 自动升档判定（纯函数）：
     * 当自动升档开启时，根据当前占用率和配置的阈值判定是否需要向用户建议升档。
     * 返回建议的目标更高档位，若无需升档则返回 null。
     */
    fun evaluateAutoUpgrade(
        currentTier: CompressionTier,
        usagePercent: Float,
        autoEnabled: Boolean,
        thresholdL2: Float = DEFAULT_THRESHOLD_L2,
        thresholdL3: Float = DEFAULT_THRESHOLD_L3,
        thresholdL4: Float = DEFAULT_THRESHOLD_L4
    ): CompressionTier? {
        if (!autoEnabled) return null

        val targetTier = when {
            usagePercent >= thresholdL4 -> CompressionTier.L4
            usagePercent >= thresholdL3 -> CompressionTier.L3
            usagePercent >= thresholdL2 -> CompressionTier.L2
            else -> null
        } ?: return null

        // 仅当目标档位严格高于当前档位时才建议升档
        return if (targetTier.level > currentTier.level) targetTier else null
    }

    /**
     * 上限溢出自动降档（纯函数）：
     * 任何档位组装后若仍超模型上下文上限，则自动降一档（L0 -> L1 -> L2 -> L3 -> L4），直到 L4。
     * 若当前已是 L4 或未超出，则返回 null。
     */
    fun fallbackOnContextOverflow(
        currentTier: CompressionTier,
        estimatedTokens: Int,
        maxBudgetTokens: Int
    ): CompressionTier? {
        if (estimatedTokens <= maxBudgetTokens) return null
        return when (currentTier) {
            CompressionTier.L0 -> CompressionTier.L1
            CompressionTier.L1 -> CompressionTier.L2
            CompressionTier.L2 -> CompressionTier.L3
            CompressionTier.L3 -> CompressionTier.L4
            CompressionTier.LC -> CompressionTier.L4 // 自定义比例档仍溢出时按既定阶梯降到极限档
            CompressionTier.L4 -> null // 已是极限档位
        }
    }

    /**
     * 获取指定档位推荐保留的轮数描述
     */
    fun getRetainedRoundsDesc(
        tier: CompressionTier,
        l2Rounds: Int = DEFAULT_L2_RECENT_ROUNDS,
        customRetainPercent: Int = DEFAULT_CUSTOM_RETAIN_PERCENT
    ): String {
        return when (tier) {
            CompressionTier.L0 -> "全部轮次完整保留"
            CompressionTier.L1 -> "全部轮次保留（轻量修剪）"
            CompressionTier.L2 -> "保留最近 $l2Rounds 轮原文"
            CompressionTier.L3 -> "保留最近 16 轮原文"
            CompressionTier.L4 -> "保留最近 8 轮原文"
            CompressionTier.LC -> "保留最近 $customRetainPercent% 消息原文（预算内尽量保留）"
        }
    }
}
