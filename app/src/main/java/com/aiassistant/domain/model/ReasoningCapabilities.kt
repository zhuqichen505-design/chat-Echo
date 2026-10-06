package com.aiassistant.domain.model

import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap

/** Evidence from one endpoint's model catalogue. Missing fields mean unknown, not false. */
data class ReasoningCapabilityEvidence(
    val supportsThinking: Boolean? = null,
    val alwaysThinking: Boolean? = null,
    val efforts: List<String>? = null,
    val defaultEffort: String? = null,
    val thinkingType: String? = null
) {
    companion object {
        fun parse(model: JsonObject): ReasoningCapabilityEvidence? {
            fun JsonObject.objectField(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
            fun JsonObject.booleanField(key: String): Boolean? = get(key)?.takeIf {
                it.isJsonPrimitive && it.asJsonPrimitive.isBoolean
            }?.asBoolean
            val capabilities = model.objectField("capabilities")
            val nativeThinking = capabilities?.objectField("thinking")
            val nativeEffort = capabilities?.objectField("effort")
            val nativeTypes = nativeThinking?.objectField("types")
            val objects = mutableListOf(model)
            fun visit(obj: JsonObject, depth: Int) {
                if (depth > 3) return
                listOf("metadata", "capabilities", "parameters", "reasoning", "thinking", "model_info").forEach { key ->
                    obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.let { objects += it; visit(it, depth + 1) }
                }
            }
            visit(model, 0)
            fun boolean(vararg keys: String): Boolean? = objects.firstNotNullOfOrNull { obj ->
                keys.firstNotNullOfOrNull { key -> obj.get(key)?.takeIf {
                    it.isJsonPrimitive && it.asJsonPrimitive.isBoolean
                }?.asBoolean }
            }
            fun string(vararg keys: String): String? = objects.firstNotNullOfOrNull { obj ->
                keys.firstNotNullOfOrNull { key -> obj.get(key)?.takeIf {
                    it.isJsonPrimitive && it.asJsonPrimitive.isString
                }?.asString?.trim()?.takeIf { it.isNotEmpty() } }
            }
            val effortKeys = listOf("supported_reasoning_efforts", "reasoning_efforts", "supported_thinking_efforts", "thinking_efforts", "supported_reasoning_levels", "reasoning_effort")
            val efforts = when {
                nativeEffort?.booleanField("supported") == false -> emptyList()
                nativeEffort?.booleanField("supported") == true -> listOf("low", "medium", "high", "xhigh", "max")
                    .filter { nativeEffort.objectField(it)?.booleanField("supported") == true }
                else -> objects.firstNotNullOfOrNull { obj ->
                    effortKeys.firstNotNullOfOrNull { key ->
                        val value = obj.get(key)
                        val array = when {
                            value?.isJsonArray == true -> value.asJsonArray
                            value?.isJsonObject == true -> value.asJsonObject.get("enum")?.takeIf { it.isJsonArray }?.asJsonArray
                            else -> null
                        }
                        array?.mapNotNull { element ->
                            val item = if (element.isJsonObject) element.asJsonObject.get("effort") else element
                            item?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                                ?.asString?.trim()?.takeIf {
                                    it.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{0,31}")) && !it.equals("default", true)
                                }
                        }?.distinct()
                    }
                }
            }
            val supported = nativeThinking?.booleanField("supported")
                ?: boolean("supports_thinking", "supports_reasoning", "supportsThinking", "supportsReasoning")
            val canDisable = boolean("can_disable_thinking", "supports_thinking_toggle", "canDisableThinking")
            val forced = boolean("always_thinking", "requires_thinking", "reasoning_required", "alwaysThinking")
                ?: canDisable?.let { !it }
            val default = string("default_reasoning_effort", "default_thinking_effort", "default_effort", "default_reasoning_level")
            val type = when {
                nativeTypes?.objectField("adaptive")?.booleanField("supported") == true -> "adaptive"
                nativeTypes?.objectField("enabled")?.booleanField("supported") == true -> "enabled"
                else -> string("thinking_type")?.takeIf { it in listOf("enabled", "adaptive") }
            }
            return ReasoningCapabilityEvidence(supported, forced, efforts, default, type)
                .takeIf { supported != null || forced != null || efforts != null || default != null || type != null }
        }
    }
}

/** Endpoint + protocol + exact model id; never share one gateway's evidence with another. */
object ReasoningCapabilityCatalog {
    private val entries = ConcurrentHashMap<String, ReasoningCapabilityEvidence>()
    private var persist: ((String, ReasoningCapabilityEvidence?) -> Unit)? = null

    fun key(baseUrl: String, apiType: String, model: String): String {
        val url = baseUrl.trim().trimEnd('/').let { if (it.startsWith("http")) it else "https://$it" }
        val endpoint = when {
            url.endsWith("/v1") || (apiType == "anthropic" && url.endsWith("/anthropic")) -> url
            else -> "$url/v1"
        }
        return "$apiType|$endpoint|${model.trim()}"
    }

    fun initialize(saved: Map<String, ReasoningCapabilityEvidence>, save: (String, ReasoningCapabilityEvidence?) -> Unit) {
        entries.putAll(saved)
        persist = save
    }

    fun put(baseUrl: String, apiType: String, model: String, evidence: ReasoningCapabilityEvidence?) {
        val key = key(baseUrl, apiType, model)
        if (evidence == null) entries.remove(key) else entries[key] = evidence
        persist?.invoke(key, evidence)
    }

    fun get(baseUrl: String, apiType: String, model: String): ReasoningCapabilityEvidence? =
        entries[key(baseUrl, apiType, model)]
}
