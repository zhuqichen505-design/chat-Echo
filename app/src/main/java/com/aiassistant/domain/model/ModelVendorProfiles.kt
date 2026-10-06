package com.aiassistant.domain.model

import java.util.Locale

/**
 * 2026-09 主流模型厂商参数策略。
 * 按模型名识别厂商后，统一给出思考档位、温度、上下文、输出上限等适配，
 * 不再为 o1/o3 等过时模型做专项分支。
 */
enum class ModelVendor {
    GPT,
    MINIMAX,
    KIMI,
    DEEPSEEK,
    GEMINI,
    GLM,
    MIMO,
    CLAUDE,
    OTHER
}

enum class TemperaturePolicy {
    /** 可正常传入 temperature / top_p / penalty */
    ALLOWED,
    /** 思考开启时必须省略 temperature（现代 OpenAI 推理风格） */
    OMIT_WHEN_THINKING,
    /** 官方固定 temperature=1.0，不建议显式传入 */
    FIXED_ONE,
    /** 思考开启时温度强制 1.0（Anthropic） */
    FORCE_ONE_WHEN_THINKING
}

data class VendorParameterPolicy(
    val vendor: ModelVendor,
    val thinkingGears: List<String>,
    val defaultThinkingGear: String,
    val usesReasoningEffort: Boolean,
    val usesMaxCompletionTokens: Boolean,
    val temperaturePolicy: TemperaturePolicy,
    val omitsTopPAndPenalties: Boolean = false,
    val defaultMaxOutputTokens: Int,
    val maxOutputTokensCap: Int,
    val defaultContextWindow: Int,
    val defaultContextLabel: String,
    val alwaysThinking: Boolean = false
)

object ModelVendorProfiles {

    /** 通用回退：未识别厂商时使用的统一方案 */
    val DEFAULT = VendorParameterPolicy(
        vendor = ModelVendor.OTHER,
        thinkingGears = listOf("low", "medium", "high", "max"),
        defaultThinkingGear = "medium",
        usesReasoningEffort = true,
        usesMaxCompletionTokens = false,
        temperaturePolicy = TemperaturePolicy.ALLOWED,
        defaultMaxOutputTokens = 16_384,
        maxOutputTokensCap = 32_768,
        defaultContextWindow = 256_000,
        defaultContextLabel = ""
    )

    private val GPT = VendorParameterPolicy(
        vendor = ModelVendor.GPT,
        // GPT-6 Astra 官方 low/medium/high/xhigh/max；GPT-5.6+ 同属现代推理世代
        thinkingGears = listOf("low", "medium", "high", "xhigh", "max"),
        defaultThinkingGear = "medium",
        usesReasoningEffort = true,
        usesMaxCompletionTokens = true,
        temperaturePolicy = TemperaturePolicy.OMIT_WHEN_THINKING,
        defaultMaxOutputTokens = 64_000,
        maxOutputTokensCap = 128_000,
        defaultContextWindow = 1_050_000,
        defaultContextLabel = "1.05M"
    )

    private val MINIMAX = VendorParameterPolicy(
        vendor = ModelVendor.MINIMAX,
        thinkingGears = listOf("low", "medium", "high", "max"),
        defaultThinkingGear = "high",
        usesReasoningEffort = true,
        usesMaxCompletionTokens = false,
        temperaturePolicy = TemperaturePolicy.ALLOWED,
        defaultMaxOutputTokens = 64_000,
        maxOutputTokensCap = 128_000,
        defaultContextWindow = 1_000_000,
        defaultContextLabel = "1M"
    )

    private val KIMI = VendorParameterPolicy(
        vendor = ModelVendor.KIMI,
        // Kimi K3 官方仅 low/high/max 三档，默认 max，且始终思考
        thinkingGears = listOf("low", "high", "max"),
        defaultThinkingGear = "max",
        usesReasoningEffort = true,
        usesMaxCompletionTokens = true,
        temperaturePolicy = TemperaturePolicy.FIXED_ONE,
        omitsTopPAndPenalties = true,
        defaultMaxOutputTokens = 131_072,
        maxOutputTokensCap = 1_048_576,
        defaultContextWindow = 1_000_000,
        defaultContextLabel = "1M",
        alwaysThinking = true
    )

    private val DEEPSEEK = VendorParameterPolicy(
        vendor = ModelVendor.DEEPSEEK,
        thinkingGears = listOf("low", "medium", "high", "max"),
        defaultThinkingGear = "medium",
        usesReasoningEffort = true,
        usesMaxCompletionTokens = false,
        temperaturePolicy = TemperaturePolicy.ALLOWED,
        defaultMaxOutputTokens = 64_000,
        maxOutputTokensCap = 384_000,
        defaultContextWindow = 1_000_000,
        defaultContextLabel = "1M"
    )

    private val GEMINI = VendorParameterPolicy(
        vendor = ModelVendor.GEMINI,
        thinkingGears = listOf("low", "medium", "high", "max"),
        defaultThinkingGear = "medium",
        usesReasoningEffort = true,
        usesMaxCompletionTokens = false,
        temperaturePolicy = TemperaturePolicy.ALLOWED,
        defaultMaxOutputTokens = 64_000,
        maxOutputTokensCap = 65_536,
        defaultContextWindow = 1_000_000,
        defaultContextLabel = "1M"
    )

    private val GLM = VendorParameterPolicy(
        vendor = ModelVendor.GLM,
        // GLM-5.3 官方 reasoning_effort 仅 low/high/max，默认 max，始终思考
        thinkingGears = listOf("low", "high", "max"),
        defaultThinkingGear = "max",
        usesReasoningEffort = true,
        usesMaxCompletionTokens = false,
        temperaturePolicy = TemperaturePolicy.FIXED_ONE,
        defaultMaxOutputTokens = 65_536,
        maxOutputTokensCap = 128_000,
        defaultContextWindow = 1_000_000,
        defaultContextLabel = "1M",
        alwaysThinking = true
    )

    private val MIMO = VendorParameterPolicy(
        vendor = ModelVendor.MIMO,
        thinkingGears = listOf("low", "medium", "high", "max"),
        defaultThinkingGear = "medium",
        usesReasoningEffort = true,
        usesMaxCompletionTokens = false,
        temperaturePolicy = TemperaturePolicy.ALLOWED,
        defaultMaxOutputTokens = 32_768,
        maxOutputTokensCap = 64_000,
        defaultContextWindow = 256_000,
        defaultContextLabel = "256K"
    )

    private val CLAUDE = VendorParameterPolicy(
        vendor = ModelVendor.CLAUDE,
        thinkingGears = listOf("low", "medium", "high", "max"),
        defaultThinkingGear = "medium",
        usesReasoningEffort = false,
        usesMaxCompletionTokens = false,
        temperaturePolicy = TemperaturePolicy.FORCE_ONE_WHEN_THINKING,
        defaultMaxOutputTokens = 32_000,
        maxOutputTokensCap = 64_000,
        defaultContextWindow = 200_000,
        defaultContextLabel = "200K"
    )

    fun detectVendor(modelName: String, provider: String = "", baseUrl: String = ""): ModelVendor {
        val name = modelName.trim().lowercase(Locale.ROOT)
        val identity = "$provider $baseUrl $name".lowercase(Locale.ROOT)
        if (name.isBlank() && identity.isBlank()) return ModelVendor.OTHER

        return when {
            name.contains("minimax") || Regex("""(^|[-_/])m2[._-]?[0-9]""").containsMatchIn(name) ||
                name.contains("minimax-m") || name.contains("m2.7") || name.contains("m3-") ||
                name.startsWith("minimax") || identity.contains("minimax") ->
                ModelVendor.MINIMAX

            name.contains("kimi") || name.contains("moonshot") ||
                Regex("""(^|[-_/])k[23]([._-]|$)""").containsMatchIn(name) ||
                identity.contains("moonshot") || identity.contains("kimi") ->
                ModelVendor.KIMI

            name.contains("deepseek") || identity.contains("deepseek") ->
                ModelVendor.DEEPSEEK

            name.contains("gemini") || identity.contains("gemini") || identity.contains("google") ->
                ModelVendor.GEMINI

            name.contains("glm") || name.contains("chatglm") || name.contains("zhipu") ||
                name.contains("bigmodel") || identity.contains("zhipu") || identity.contains("bigmodel") ->
                ModelVendor.GLM

            name.contains("mimo") || identity.contains("mimo") || identity.contains("xiaomi") ->
                ModelVendor.MIMO

            name.contains("claude") || name.contains("anthropic") || name.contains("mythos") ||
                name.contains("fable") || identity.contains("anthropic") ->
                ModelVendor.CLAUDE

            // 仅匹配 gpt / gpt-5.6 / gpt-6 等常见现代命名，不为 o 系列做专项识别
            name.contains("gpt-5") || name.contains("gpt-6") || name.contains("gpt6") ||
                name.contains("astra") || name.contains("codex") ||
                Regex("""(^|[-_/])gpt([._-]|$)""").containsMatchIn(name) ||
                identity.contains("openai") ->
                ModelVendor.GPT

            else -> ModelVendor.OTHER
        }
    }

    fun policyFor(modelName: String, provider: String = "", baseUrl: String = ""): VendorParameterPolicy {
        val name = modelName.lowercase(Locale.ROOT)
        val base = when (detectVendor(modelName, provider, baseUrl)) {
            ModelVendor.GPT -> GPT
            ModelVendor.MINIMAX -> MINIMAX
            ModelVendor.KIMI -> KIMI
            ModelVendor.DEEPSEEK -> DEEPSEEK
            ModelVendor.GEMINI -> GEMINI
            ModelVendor.GLM -> GLM
            ModelVendor.MIMO -> MIMO
            ModelVendor.CLAUDE -> CLAUDE
            ModelVendor.OTHER -> DEFAULT
        }
        return when (base.vendor) {
            ModelVendor.GPT -> when {
                "gpt-6.1-sol" in name || "astra" in name -> base.copy(alwaysThinking = true)
                "gpt-6-sol" in name || "gpt-6-luna" in name -> base.copy(thinkingGears = listOf("none", "low", "medium", "high", "xhigh", "max"))
                "gpt-6" in name -> base.copy(thinkingGears = emptyList(), usesReasoningEffort = false)
                "gpt-5.2-pro" in name -> base.copy(thinkingGears = listOf("medium", "high", "xhigh"), alwaysThinking = true)
                "gpt-5.6" in name -> base.copy(thinkingGears = listOf("none", "low", "medium", "high", "xhigh", "max"))
                "gpt-5.2" in name || "gpt-5.4" in name -> base.copy(thinkingGears = listOf("none", "low", "medium", "high", "xhigh"))
                "gpt-5.1" in name -> base.copy(thinkingGears = listOf("none", "low", "medium", "high"))
                Regex("gpt-5(?:-(?:mini|nano))?(?:-\\d{4}-\\d{2}-\\d{2})?$").containsMatchIn(name) -> base.copy(thinkingGears = listOf("minimal", "low", "medium", "high"), alwaysThinking = true)
                else -> base.copy(thinkingGears = emptyList(), usesReasoningEffort = false)
            }
            ModelVendor.KIMI -> if ("k3" in name) base else base.copy(thinkingGears = emptyList(), usesReasoningEffort = false,
                alwaysThinking = "thinking" in name || "k2.7-code" in name)
            ModelVendor.GLM -> when {
                "5.3" in name -> base.copy(alwaysThinking = "flashx" !in name)
                "5.2" in name -> base.copy(thinkingGears = listOf("high", "max"), alwaysThinking = false)
                else -> base.copy(thinkingGears = emptyList(), usesReasoningEffort = false, alwaysThinking = false)
            }
            ModelVendor.MINIMAX -> if ("m3.1" in name) base.copy(thinkingGears = listOf("low", "medium", "high", "xhigh", "max"), defaultThinkingGear = "max", alwaysThinking = true)
                else base.copy(thinkingGears = emptyList(), usesReasoningEffort = false, alwaysThinking = Regex("minimax-m2([._-]|$)").containsMatchIn(name))
            ModelVendor.DEEPSEEK -> if ("v4" in name || "flash" in name || "pro" in name) base.copy(thinkingGears = listOf("low", "high", "max"), defaultThinkingGear = "high")
                else base.copy(thinkingGears = emptyList(), usesReasoningEffort = false, alwaysThinking = "reasoner" in name || "r1" in name)
            ModelVendor.GEMINI -> when {
                "3.8-flash" in name || "3.7-flash" in name -> base.copy(thinkingGears = listOf("low", "medium", "high"), alwaysThinking = true)
                "3.6-flash" in name || "3.5-flash-lite" in name -> base.copy(thinkingGears = listOf("minimal", "low", "medium", "high"), defaultThinkingGear = if ("lite" in name) "minimal" else "medium", alwaysThinking = true)
                "3.1-pro" in name -> base.copy(thinkingGears = listOf("low", "medium", "high"), defaultThinkingGear = "high", alwaysThinking = true)
                "3-pro" in name -> base.copy(thinkingGears = listOf("low", "high"), defaultThinkingGear = "high", alwaysThinking = true)
                "3-flash" in name -> base.copy(thinkingGears = listOf("minimal", "low", "medium", "high"), defaultThinkingGear = "high", alwaysThinking = true)
                else -> base.copy(thinkingGears = emptyList(), usesReasoningEffort = false)
            }
            ModelVendor.CLAUDE -> when {
                "fable-5" in name || "mythos" in name -> base.copy(thinkingGears = listOf("low", "medium", "high", "xhigh", "max"), defaultThinkingGear = "high", alwaysThinking = "5-1" in name || "5.1" in name)
                "sonnet-5" in name -> base.copy(thinkingGears = listOf("low", "medium", "high", "xhigh", "max"), defaultThinkingGear = "high", alwaysThinking = "sonnet-5-5" in name || "sonnet-5.5" in name)
                "opus-5" in name || "opus-4-7" in name || "opus-4.7" in name || "opus-4-8" in name || "opus-4.8" in name -> base.copy(thinkingGears = listOf("low", "medium", "high", "xhigh", "max"), alwaysThinking = "opus-5-5" in name || "opus-5.5" in name)
                "opus-4-6" in name || "opus-4.6" in name -> base
                "sonnet-4-6" in name || "sonnet-4.6" in name -> base.copy(defaultThinkingGear = "high")
                else -> base.copy(thinkingGears = emptyList())
            }
            ModelVendor.MIMO, ModelVendor.OTHER -> base.copy(thinkingGears = emptyList(), usesReasoningEffort = false)
        }
    }

    /**
     * 将任意输入档位映射到该厂商真实支持的档位。
     * 未识别厂商时使用当前默认统一方案（low/medium/high/max）。
     */
    fun mapThinkingGear(raw: String?, policy: VendorParameterPolicy): String {
        val gears = policy.thinkingGears
        if (gears.isEmpty()) return policy.defaultThinkingGear
        val exact = raw?.lowercase(Locale.ROOT)?.trim()
        if (exact in gears) return exact!!
        val normalized = when (raw?.lowercase(Locale.ROOT)?.trim()) {
            null, "", "none", "off", "disabled" -> policy.defaultThinkingGear
            "fast", "minimal", "light", "low" -> "low"
            "balanced", "medium", "mid" -> if ("medium" in gears) "medium" else "high"
            "deep", "high" -> "high"
            "xhigh", "very_high" -> if ("xhigh" in gears) "xhigh" else "high"
            "max", "ultra", "ultra_high", "highest" -> "max"
            else -> raw.lowercase(Locale.ROOT).trim()
        }
        return when {
            normalized in gears -> normalized
            normalized == "medium" && "medium" !in gears -> if ("high" in gears) "high" else policy.defaultThinkingGear
            normalized == "xhigh" && "xhigh" !in gears -> if ("max" in gears) "max" else "high"
            normalized == "max" && "max" !in gears -> if ("high" in gears) "high" else policy.defaultThinkingGear
            else -> policy.defaultThinkingGear
        }
    }

    fun mapThinkingGear(raw: String?, modelName: String, provider: String = "", baseUrl: String = ""): String {
        return mapThinkingGear(raw, policyFor(modelName, provider, baseUrl))
    }
}
