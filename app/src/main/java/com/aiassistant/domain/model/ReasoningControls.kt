package com.aiassistant.domain.model

/** Shared by both editors and wire serialization. Display labels never change wire values. */
object ReasoningControls {
    data class Option(val value: String, val label: String, val enabled: Boolean = true)
    fun displayLabel(value: String): String = when (value) {
        "default" -> "默认"
        "disabled", "none" -> "关闭"
        else -> value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
    }

    fun policy(model: String, evidence: ReasoningCapabilityEvidence? = null): VendorParameterPolicy {
        val fallback = ModelVendorProfiles.policyFor(model)
        val gears = evidence?.efforts ?: fallback.thinkingGears
        return fallback.copy(
            thinkingGears = if (evidence?.supportsThinking == false) emptyList() else gears,
            defaultThinkingGear = evidence?.defaultEffort?.takeIf { it in gears }
                ?: fallback.defaultThinkingGear.takeIf { it in gears } ?: gears.firstOrNull { it != "none" } ?: "default",
            usesReasoningEffort = if (evidence?.supportsThinking == false) false else evidence?.efforts?.isNotEmpty() ?: fallback.usesReasoningEffort,
            alwaysThinking = evidence?.supportsThinking != false && (evidence?.alwaysThinking
                ?: if (evidence?.efforts?.any { it.equals("none", true) } == true) false else fallback.alwaysThinking)
        )
    }

    fun options(model: String, apiType: String = "openai", evidence: ReasoningCapabilityEvidence? = null): List<Option> {
        val policy = policy(model, evidence)
        val nativeClaude = apiType == "anthropic"
        if (evidence?.supportsThinking == false) return listOf(Option("default", "默认", false))
        if (policy.vendor == ModelVendor.CLAUDE && !nativeClaude && evidence?.efforts == null) return listOf(Option("default", "默认"))
        if (nativeClaude && policy.thinkingGears.isEmpty() && evidence?.thinkingType != "adaptive" &&
            (evidence?.thinkingType == "enabled" || (policy.vendor == ModelVendor.CLAUDE && evidence?.efforts == null &&
            Regex("claude-(3[-.]7|sonnet-4|opus-4)").containsMatchIn(model.lowercase())))) return listOf(
            Option("disabled", "关闭", false),
            Option("budget:1024", "Budget_tokens=1024"), Option("budget:4096", "Budget_tokens=4096"),
            Option("budget:8192", "Budget_tokens=8192"), Option("budget:16384", "Budget_tokens=16384")
        )
        if (policy.thinkingGears.isEmpty()) return listOf(Option("default", "默认"))
        val gears = policy.thinkingGears.filterNot { policy.alwaysThinking && it.equals("none", true) }
            .map { Option(it, displayLabel(it), !it.equals("none", true)) }
        if (gears.isEmpty()) return listOf(Option("default", "默认"))
        return if (policy.alwaysThinking || policy.thinkingGears.any { it.equals("none", true) }) gears
        else if (canToggle(model, apiType, evidence)) listOf(Option("disabled", "关闭", false)) + gears else gears
    }

    fun canToggle(model: String, apiType: String, evidence: ReasoningCapabilityEvidence? = null): Boolean {
        val policy = policy(model, evidence)
        if (policy.alwaysThinking || evidence?.supportsThinking == false) return false
        // Unknown does not mean forced. The UI remains editable; unsupported wire fields are omitted.
        return true
    }

    fun hasIntensityChoice(model: String, apiType: String, evidence: ReasoningCapabilityEvidence? = null): Boolean =
        options(model, apiType, evidence).count { it.enabled } > 1

    fun selected(model: String, apiType: String, enabled: Boolean, raw: String?, evidence: ReasoningCapabilityEvidence? = null): Option {
        val options = options(model, apiType, evidence)
        if (!enabled) options.firstOrNull { !it.enabled }?.let { return it }
        if (!enabled && !policy(model, evidence).alwaysThinking) return Option("default", "默认", false)
        options.firstOrNull { it.value.equals(raw, true) && it.enabled }?.let { return it }
        if (apiType == "anthropic" && options.any { it.value.startsWith("budget:") }) {
            val budget = when(raw) { "low", "fast" -> 1024; "high", "deep" -> 8192; "max", "ultra" -> 16384; else -> 4096 }
            return options.first { it.value == "budget:$budget" }
        }
        val policy = policy(model, evidence)
        val mapped = ModelVendorProfiles.mapThinkingGear(raw?.takeUnless { it.equals("none", true) }, policy)
        return options.firstOrNull { it.value == mapped && it.enabled }
            ?: options.firstOrNull { it.value == policy.defaultThinkingGear && it.enabled }
            ?: options.firstOrNull { it.enabled } ?: options.first()
    }

    fun wireEffort(model: String, apiType: String, enabled: Boolean, raw: String?, evidence: ReasoningCapabilityEvidence? = null): String? {
        val selected = selected(model, apiType, enabled, raw, evidence)
        return selected.value.takeIf { it != "default" && it != "disabled" && !it.startsWith("budget:") && (selected.enabled || it.equals("none", true)) }
    }

    fun thinkingType(model: String, apiType: String, enabled: Boolean, evidence: ReasoningCapabilityEvidence? = null): String? {
        if (evidence?.supportsThinking == false) return null
        val policy = policy(model, evidence)
        val supportedType = evidence?.thinkingType ?: when {
            apiType == "anthropic" && policy.vendor == ModelVendor.CLAUDE -> when {
                policy.thinkingGears.isNotEmpty() -> "adaptive"
                options(model, apiType, evidence).any { it.value.startsWith("budget:") } -> "enabled"
                else -> null
            }
            else -> when (policy.vendor) {
                ModelVendor.DEEPSEEK -> if (policy.thinkingGears.isNotEmpty()) "enabled" else null
                ModelVendor.GLM -> if (Regex("glm-(5|4[.][567])").containsMatchIn(model.lowercase())) "enabled" else null
                ModelVendor.MINIMAX -> if (model.contains("m3", true)) "adaptive" else null
                ModelVendor.KIMI -> if (!model.contains("k2.7-code", true) && Regex("k2[.]?[56]").containsMatchIn(model.lowercase())) "enabled" else null
                else -> null
            }
        }
        return supportedType?.let { if (enabled || policy.alwaysThinking) it else "disabled" }
    }

    /** Gateway wire compatibility is stricter than the UI's model-name fallback. */
    fun wireThinkingType(model: String, apiType: String, enabled: Boolean, evidence: ReasoningCapabilityEvidence? = null): String? {
        val type = thinkingType(model, apiType, enabled, evidence)
        if (apiType != "anthropic") return type.takeIf { evidence?.thinkingType != null }
        // Only the transitional 4.6 family accepts both contracts. Adaptive-only
        // native models must never receive the removed budget_tokens contract.
        val budgetCompatible = Regex("claude-(sonnet|opus)-4[-.]6").containsMatchIn(model.lowercase())
        if (type == "adaptive" && evidence?.thinkingType == null && budgetCompatible) return if (enabled) "enabled" else null
        if (type == "disabled" && evidence?.thinkingType == null) return null
        return type
    }

    fun wireChatEffort(model: String, apiType: String, enabled: Boolean, raw: String?, evidence: ReasoningCapabilityEvidence? = null): String? {
        if (apiType == "anthropic" && wireThinkingType(model, apiType, enabled, evidence) != "adaptive") return null
        val effort = wireEffort(model, apiType, enabled, raw, evidence)
        // Older OpenAI-compatible gateways may not implement the new explicit off value.
        return effort.takeUnless { it.equals("none", true) && evidence?.efforts?.any { value -> value.equals("none", true) } != true }
    }
}
