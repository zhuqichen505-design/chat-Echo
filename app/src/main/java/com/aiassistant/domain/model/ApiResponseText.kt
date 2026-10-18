package com.aiassistant.domain.model

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/** Text-only decoding shared by ordinary completions and streaming gateway responses. */
object ApiResponseText {
    private val bodyTypes = setOf("text", "output_text")
    private val reasoningTypes = setOf("reasoning", "thinking", "analysis", "reasoning_content", "reasoning.text", "summary_text")

    fun content(element: JsonElement?): String? = extract(element, bodyTypes)

    /** A named reasoning field may contain a string or structured text/thinking blocks. */
    fun reasoning(element: JsonElement?): String? = extract(element, bodyTypes + reasoningTypes)

    /** Reasoning blocks embedded in content must never become visible answer text. */
    fun embeddedReasoning(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonArray) return join(element.asJsonArray.mapNotNull(::embeddedReasoning))
        if (!element.isJsonObject) return null
        val block = element.asJsonObject
        val type = string(block.get("type"))?.lowercase(java.util.Locale.ROOT)
        return if (type in reasoningTypes) reasoning(element) else null
    }

    private fun extract(element: JsonElement?, allowedTypes: Set<String>, depth: Int = 0): String? {
        if (element == null || element.isJsonNull || depth > 32) return null
        if (element.isJsonPrimitive) return string(element)
        if (element.isJsonArray) return join(element.asJsonArray.mapNotNull { extract(it, allowedTypes, depth + 1) })
        if (!element.isJsonObject) return null
        val block = element.asJsonObject
        val type = string(block.get("type"))?.lowercase(java.util.Locale.ROOT)
        if (type != null && type !in allowedTypes) return null
        return listOf("text", "output_text", "value", "thinking", "reasoning")
            .asSequence()
            .filter { it !in setOf("thinking", "reasoning") || type in reasoningTypes }
            .mapNotNull { key -> extract(block.get(key), allowedTypes, depth + 1) }
            .firstOrNull()
    }

    private fun string(element: JsonElement?): String? =
        element?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun join(parts: List<String>): String? = parts.takeIf { it.isNotEmpty() }?.joinToString("")
}

/** Keep the public DTO String API while accepting structured text at the wire boundary. */
class ResponseTextAdapter : TypeAdapter<String>() {
    override fun read(reader: JsonReader): String? = ApiResponseText.content(JsonParser.parseReader(reader))

    override fun write(writer: JsonWriter, value: String?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}

class ResponseReasoningTextAdapter : TypeAdapter<String>() {
    override fun read(reader: JsonReader): String? = ApiResponseText.reasoning(JsonParser.parseReader(reader))

    override fun write(writer: JsonWriter, value: String?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}
