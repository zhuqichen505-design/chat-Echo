package com.aiassistant.domain.model

import java.util.Locale

data class ModelCapabilityInfo(
    val contextWindowTokens: Int,
    val contextWindowLabel: String,
    val isMultimodal: Boolean,
    val supportsToolCalling: Boolean,
    val supportsThinking: Boolean,
    val supportedThinkingGears: List<String> = emptyList(),
    val defaultThinkingBudget: Int = 1024,
    val reasoningProviderType: String = "none", // "openai", "anthropic", "deepseek_fixed", "generic", "none"
    val maxOutputTokens: Int = 16_384,
    val usesMaxCompletionTokens: Boolean = false,
    val rejectsTemperature: Boolean = false,
    val vendor: ModelVendor = ModelVendor.OTHER,
    val temperaturePolicy: TemperaturePolicy = TemperaturePolicy.ALLOWED,
    val omitsTopPAndPenalties: Boolean = false,
    val alwaysThinking: Boolean = false
) {
    val contextWindowDisplay: String get() = contextWindowLabel
    val supportsVision: Boolean get() = isMultimodal
    val supportsTools: Boolean get() = supportsToolCalling
    val supportsReasoning: Boolean get() = supportsThinking
    val isContextWindowRecognized: Boolean get() = contextWindowLabel.isNotBlank()
}

object ModelCapabilityEngine {

    const val DEFAULT_CONTEXT_TOKENS = 256_000 // 未识别模型的安全默认上下文，不对外展示标签

    fun evaluateModel(
        modelName: String,
        provider: String = "",
        baseUrl: String = ""
    ): ModelCapabilityInfo = resolveCapabilities(modelName, provider, baseUrl)

    fun resolveCapabilities(
        modelName: String,
        provider: String = "",
        baseUrl: String = ""
    ): ModelCapabilityInfo {
        val name = modelName.trim().lowercase(Locale.ROOT)
        val fullIdentity = "$provider $baseUrl $name".lowercase(Locale.ROOT)
        val policy = ModelVendorProfiles.policyFor(modelName, provider, baseUrl)

        val (contextTokens, contextLabel) = resolveContextWindow(name, policy)
        val isMultimodal = resolveIsMultimodal(name, fullIdentity, policy)
        val supportsToolCalling = resolveSupportsToolCalling(name)
        val (supportsThinking, gears, defaultBudget, providerType) = resolveThinkingCapabilities(name, fullIdentity, policy)

        return ModelCapabilityInfo(
            contextWindowTokens = contextTokens,
            contextWindowLabel = contextLabel,
            isMultimodal = isMultimodal,
            supportsToolCalling = supportsToolCalling,
            supportsThinking = supportsThinking,
            supportedThinkingGears = gears,
            defaultThinkingBudget = defaultBudget,
            reasoningProviderType = providerType,
            maxOutputTokens = policy.maxOutputTokensCap,
            usesMaxCompletionTokens = policy.usesMaxCompletionTokens,
            rejectsTemperature = policy.temperaturePolicy == TemperaturePolicy.OMIT_WHEN_THINKING ||
                policy.temperaturePolicy == TemperaturePolicy.FIXED_ONE,
            vendor = policy.vendor,
            temperaturePolicy = policy.temperaturePolicy,
            omitsTopPAndPenalties = policy.omitsTopPAndPenalties,
            alwaysThinking = policy.alwaysThinking
        )
    }

    /**
     * 现代 OpenAI 风格推理模型：使用 max_completion_tokens，思考时省略 temperature。
     * 只覆盖 gpt-5.6+ / gpt-6 等常见现代命名，不为 o 系列等过时模型做专项适配。
     */
    fun isModernOpenAiReasoningStyle(modelName: String): Boolean {
        val vendor = ModelVendorProfiles.detectVendor(modelName)
        return vendor == ModelVendor.GPT &&
            (modelName.contains("gpt-5", ignoreCase = true) ||
                modelName.contains("gpt-6", ignoreCase = true) ||
                modelName.contains("gpt6", ignoreCase = true) ||
                modelName.contains("astra", ignoreCase = true) ||
                modelName.contains("sol", ignoreCase = true) ||
                modelName.contains("luna", ignoreCase = true))
    }

    private fun resolveContextWindow(name: String, policy: VendorParameterPolicy): Pair<Int, String> {
        // A. 显式数字与单位后缀优先提取：例如 -128k, -200k, -1m, -2m, -32k, -64k
        val explicitSuffix = Regex("""(?i)(?:^|[-_./])(\d+)([km])(?:$|[-_./])""").find(name)
            ?: Regex("""(?i)(?:^|[-_./])([1-9]\d{0,2})m(?:$|[-_./])""").find(name)
            ?: Regex("""(?i)(?:^|[-_./])(\d{1,4})k(?:$|[-_./])""").find(name)
        if (explicitSuffix != null) {
            val num = explicitSuffix.groupValues[1].toIntOrNull()
            val unit = explicitSuffix.groupValues[2].lowercase(Locale.ROOT)
            if (num != null) {
                if (unit == "m" && num in 1..10) {
                    return Pair(num * 1_000_000, "${num}M")
                } else if (unit == "k" && num in 4..2048) {
                    return Pair(num * 1_000, "${num}K")
                }
            }
        }

        // B. 厂商/代际细分（优先于粗粒度历史规则，避免把 V4/1M 模型误判为 32k）
        return when {
            // DeepSeek V4+：官方 1M（最大输出可达 384K）
            name.contains("deepseek-v4") || name.contains("deepseekv4") ||
            (name.contains("deepseek") && Regex("""(^|[^0-9])v[4-9]([^0-9]|$)""").containsMatchIn(name)) ||
            name.contains("deepseek-flash") || name.contains("deepseek-v4-pro") ->
                Pair(1_000_000, "1M")

            // 历史 DeepSeek chat / reasoner 保持 128K（避免吞掉 deepseek-chat 基线测试）
            name.contains("deepseek-chat") || name.contains("deepseek-reasoner") ->
                Pair(128_000, "128K")

            // GPT-6 Astra：官方 1,050,000
            name.contains("gpt-6") || name.contains("gpt6") || name.contains("astra") ->
                Pair(1_050_000, "1.05M")

            // MiniMax M3：1M；M2.x 长上下文档
            name.contains("minimax") && (name.contains("m3") || name.contains("m2.7")) ->
                Pair(1_000_000, "1M")
            name.contains("minimax") || name.contains("m2.7") ->
                Pair(1_000_000, "1M")

            // Kimi K3：1M；K2.6：256K
            name.contains("kimi-k3") || name.contains("kimi-k2.7") || name.contains("kimi-k2.8") ->
                Pair(1_000_000, "1M")
            name.contains("kimi-k2.6") || name.contains("kimi-k2.5") ->
                Pair(256_000, "256K")

            // GLM-5.x：1M；GLM-4.7+：至少 128K，现代代际按 1M 处理
            name.contains("glm-5") || name.contains("glm-4.7") || name.contains("glm-4.8") ||
            name.contains("glm-4.9") ->
                Pair(1_000_000, "1M")

            // MiMo V2.5+/V2.6：256K 起
            name.contains("mimo-v2.6") || name.contains("mimo-v2.5") || name.contains("mimo-v3") ->
                Pair(256_000, "256K")

            // Gemini 3.x+：1M
            name.contains("gemini-3") || name.contains("gemini3") ->
                Pair(1_000_000, "1M")

            // 历史 Gemini Pro 长文本世代
            name.contains("gemini-1.5-pro") || name.contains("gemini-2.0-pro") ||
            name.contains("gemini-2.5-pro") || name.contains("gemini-pro-1.5") ->
                Pair(2_000_000, "2M")

            // 1M：Flash/Long 架构与 gpt-4.1/5.5/5.6
            name.contains("flash") || name.contains("long") || name.contains("gemini") ||
            name.contains("grok-3") || name.contains("grok-4") || name.contains("gpt-4.1") ||
            name.contains("gpt-5.5") || name.contains("gpt-5.6") || name.contains("gpt-5.7") ->
                Pair(1_000_000, "1M")

            // 200K：Anthropic 与历史长文本
            name.contains("claude") || name.contains("mythos") || name.contains("fable") ||
            name.contains("yi-34b-200k") || name.contains("yi-large-rag") ->
                Pair(200_000, "200K")

            // 128K：GPT-5 早期 / 4o 等与常见基座
            name.contains("gpt-5") || name.contains("gpt-4o") || name.contains("gpt-4.5") || name.contains("gpt-4-turbo") ||
            name.contains("qwen-2.5") || name.contains("qwen2.5") || name.contains("qwq") ||
            name.contains("qwen-plus") || name.contains("qwen-max") || name.contains("qwen-turbo") ||
            name.contains("glm-4") || name.contains("glm-3-turbo") || name.contains("kimi") || name.contains("moonshot") ||
            name.contains("llama-3.3") || name.contains("llama-3.2") || name.contains("llama-3.1") ||
            name.contains("mistral-large") || name.contains("mistral-small") || name.contains("codestral") ||
            name.contains("baichuan4") || name.contains("grok-2") || name.contains("abab6") ->
                Pair(128_000, "128K")

            name.contains("deepseek-v2") || name.contains("open-mixtral-8x22b") ->
                Pair(64_000, "64K")

            name.contains("gpt-4-32k") || name.contains("chatglm3") || name.contains("baichuan3") || name.contains("baichuan2") ||
            name.contains("yi-large") || name.contains("yi-medium") || name.contains("open-mixtral-8x7b") || name.contains("mistral-7b") ->
                Pair(32_000, "32K")

            name.contains("gpt-3.5-turbo-0613") || name.contains("gpt-3.5-turbo-0301") ->
                Pair(16_000, "16K")

            name.contains("gpt-4-0613") || name.contains("gpt-4-0314") ->
                Pair(8_000, "8K")

            name.contains("llama-2") ->
                Pair(4_000, "4K")

            // 厂商默认上下文（已识别厂商但无更细代际信息时）
            policy.vendor != ModelVendor.OTHER && policy.defaultContextLabel.isNotBlank() ->
                Pair(policy.defaultContextWindow, policy.defaultContextLabel)

            else -> Pair(DEFAULT_CONTEXT_TOKENS, "")
        }
    }

    private fun resolveIsMultimodal(name: String, identity: String, policy: VendorParameterPolicy): Boolean {
        if (name.contains("text-only") || name.contains("embedding") || name.contains("dall-e")) return false

        // DeepSeek：flash/vision 支持，pro/reasoner 不支持
        if (policy.vendor == ModelVendor.DEEPSEEK || name.contains("deepseek")) {
            if (name.contains("deepseek-v4-pro") || name.contains("deepseek-reasoner") ||
                name.contains("deepseek-r1") || name == "deepseek-chat"
            ) {
                return false
            }
            return name.contains("flash") || name.contains("vision") || name.contains("vl")
        }

        // MiniMax M3 原生多模态；Kimi K3 原生视觉；MiMo V2.6 全模态
        if (policy.vendor == ModelVendor.MINIMAX && (name.contains("m3") || name.contains("m2.7"))) return true
        if (policy.vendor == ModelVendor.KIMI && (name.contains("k3") || name.contains("k2.6"))) return true
        if (policy.vendor == ModelVendor.MIMO && (name.contains("v2.5") || name.contains("v2.6") || name.contains("v3"))) return true
        if (policy.vendor == ModelVendor.GEMINI) return true
        if (policy.vendor == ModelVendor.GPT && (name.contains("gpt-6") || name.contains("gpt-5") || name.contains("astra"))) return true

        return name.contains("vision") ||
            name.contains("vl") ||
            name.contains("4o") ||
            name.contains("omni") ||
            name.contains("claude-3") ||
            name.contains("claude-4") ||
            name.contains("claude-5") ||
            name.contains("gpt-4-turbo") ||
            name.contains("gpt-4.5") ||
            name.contains("glm-4v") ||
            name.contains("internvl") ||
            name.contains("minicpm-v") ||
            identity.contains("multimodal")
    }

    private fun resolveSupportsToolCalling(name: String): Boolean {
        if (name.contains("text-embedding") || name.contains("dall-e")) return false
        return true
    }

    private fun resolveThinkingCapabilities(
        name: String,
        identity: String,
        policy: VendorParameterPolicy
    ): Tuple4<Boolean, List<String>, Int, String> {
        // 历史纯非思考对照组（gpt-4o / gpt-3.5 / embedding 等）
        if (name.contains("text-embedding") || name.contains("dall-e") || name.contains("tts") || name.contains("whisper") ||
            name.contains("gpt-4o") || name == "gpt-4" || name.startsWith("gpt-3.5")
        ) {
            return Tuple4(false, emptyList(), 0, "none")
        }

        val providerType = when (policy.vendor) {
            ModelVendor.CLAUDE -> "anthropic"
            ModelVendor.DEEPSEEK -> "deepseek_fixed"
            ModelVendor.GPT, ModelVendor.GEMINI, ModelVendor.GLM, ModelVendor.KIMI,
            ModelVendor.MINIMAX, ModelVendor.MIMO -> "openai"
            ModelVendor.OTHER -> "generic"
        }

        // 主流现代模型默认支持思考；档位严格按厂商策略
        return Tuple4(true, policy.thinkingGears, 4096, providerType)
    }

    data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
