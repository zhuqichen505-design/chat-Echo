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

    /** Explicit refusal metadata is kept separate from ordinary response text. */
    fun refusal(element: JsonElement?, depth: Int = 0): String? {
        if (element == null || element.isJsonNull || depth > 32) return null
        if (element.isJsonPrimitive) return string(element)
        if (element.isJsonArray) return join(element.asJsonArray.mapNotNull { refusal(it, depth + 1) })
        if (!element.isJsonObject) return null
        val block = element.asJsonObject
        val type = string(block.get("type"))?.lowercase(java.util.Locale.ROOT)
        if (type != null && type !in setOf("refusal", "text", "output_text")) return null
        return listOf("refusal", "text", "value", "message").asSequence()
            .mapNotNull { refusal(block.get(it), depth + 1) }.firstOrNull()
    }

    fun embeddedRefusal(element: JsonElement?, depth: Int = 0): String? {
        if (element == null || element.isJsonNull || depth > 32) return null
        if (element.isJsonArray) return join(element.asJsonArray.mapNotNull { embeddedRefusal(it, depth + 1) })
        if (!element.isJsonObject) return null
        return if (string(element.asJsonObject.get("type"))?.equals("refusal", ignoreCase = true) == true) refusal(element) else null
    }

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

/** Read the whole response message so embedded refusal blocks cannot disappear in content decoding. */
class MessageDataAdapter : TypeAdapter<MessageData>() {
    override fun read(reader: JsonReader): MessageData? {
        val element = JsonParser.parseReader(reader)
        if (!element.isJsonObject) return null
        val message = element.asJsonObject
        return MessageData(
            role = message.get("role")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString,
            content = ApiResponseText.content(message.get("content")),
            reasoning_content = ApiResponseText.reasoning(message.get("reasoning_content")),
            refusal = listOfNotNull(ApiResponseText.refusal(message.get("refusal")), ApiResponseText.embeddedRefusal(message.get("content")))
                .filter(String::isNotBlank).distinct().takeIf { it.isNotEmpty() }?.joinToString("\n")
        )
    }

    override fun write(writer: JsonWriter, value: MessageData?) {
        if (value == null) { writer.nullValue(); return }
        writer.beginObject()
        writer.name("role"); if (value.role == null) writer.nullValue() else writer.value(value.role)
        writer.name("content"); if (value.content == null) writer.nullValue() else writer.value(value.content)
        writer.name("reasoning_content"); if (value.reasoning_content == null) writer.nullValue() else writer.value(value.reasoning_content)
        writer.name("refusal"); if (value.refusal == null) writer.nullValue() else writer.value(value.refusal)
        writer.endObject()
    }
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
