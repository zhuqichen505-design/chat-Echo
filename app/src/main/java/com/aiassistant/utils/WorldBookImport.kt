package com.aiassistant.utils

import com.aiassistant.domain.model.WorldBook
import com.aiassistant.domain.model.WorldBookEntry
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal

enum class WorldBookImportFormat(val displayName: String) {
    SILLY_TAVERN("SillyTavern 世界书"),
    CHARACTER_BOOK("Character Book 世界书"),
    EMBEDDED_CHARACTER_BOOK("角色卡内嵌世界书")
}

data class WorldBookImportEntry(
    val draft: WorldBookEntry,
    val warnings: List<String>,
    val unsupportedFields: List<String>
)

class WorldBookImportReport internal constructor(
    val format: WorldBookImportFormat,
    val name: String,
    val description: String,
    val entries: List<WorldBookImportEntry>,
    val warnings: List<String>,
    val unsupportedFields: List<String>
) {
    val requiresCompatibilityConfirmation: Boolean get() = warnings.isNotEmpty()
    val disabledEntryCount: Int get() = entries.count { !it.draft.isEnabled }
}

/** Only an explicitly prepared report can reach the transactional import API. */
class PreparedWorldBookImport internal constructor(
    val book: WorldBook,
    val entries: List<WorldBookEntry>
)

/** Basic-content import, not a lossless implementation of every external lorebook dialect. */
object WorldBookImport {
    const val MAX_JSON_BYTES = 2 * 1024 * 1024
    const val MAX_ENTRIES = 2000
    private const val MAX_NESTING = 32
    private const val MAX_JSON_NODES = 100_000

    fun parse(json: String, fallbackName: String = "导入的世界书"): WorldBookImportReport {
        require(json.isNotBlank()) { "世界书文件为空" }
        require(json.length <= MAX_JSON_BYTES && json.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) {
            "世界书文件不能超过 2 MiB"
        }
        val root = parseStrictJson(json)
        require(root.isJsonObject) { "世界书根节点必须是 JSON 对象" }
        val rootObject = root.asJsonObject
        val embedded = when {
            rootObject.has("character_book") -> rootObject.get("character_book")
            rootObject.get("data")?.isJsonObject == true -> rootObject.getAsJsonObject("data").get("character_book")
            else -> null
        }
        val book = if (embedded != null) {
            require(embedded.isJsonObject) { "character_book 必须是对象" }
            embedded.asJsonObject
        } else rootObject
        val container = book.get("entries")
        require(container != null && (container.isJsonObject || container.isJsonArray)) {
            "未找到受支持的 entries 对象或数组；仅导入世界书，不导入角色卡的其他内容"
        }
        val format = when {
            embedded != null -> WorldBookImportFormat.EMBEDDED_CHARACTER_BOOK
            container.isJsonArray -> WorldBookImportFormat.CHARACTER_BOOK
            else -> WorldBookImportFormat.SILLY_TAVERN
        }
        val sourceEntries = if (container.isJsonArray) container.asJsonArray.toList()
        else container.asJsonObject.entrySet().map { it.value }
        require(sourceEntries.isNotEmpty()) { "世界书没有词条" }
        require(sourceEntries.size <= MAX_ENTRIES) { "世界书最多支持 $MAX_ENTRIES 个词条" }

        val bookWarnings = mutableListOf<String>()
        val unsupportedBook = mutableListOf<String>()
        val basicBookFields = setOf("name", "description", "entries", "extensions")
        for ((field, _) in book.entrySet()) {
            if (field !in basicBookFields) unsupportedBook += field
        }
        val bookExtensions = book.get("extensions")
        if (bookExtensions != null && !bookExtensions.isJsonNull) {
            require(bookExtensions.isJsonObject) { "世界书 extensions 必须是对象" }
            unsupportedBook += bookExtensions.asJsonObject.keySet().map { "extensions.$it" }
        }
        if (unsupportedBook.isNotEmpty()) {
            bookWarnings += "书级设置未应用：${unsupportedBook.joinToString("、")}；相关原始字段不会保存，所有词条默认停用，请检查后启用"
        }
        if (embedded != null) bookWarnings += "仅提取角色卡内的世界书；角色、提示词及其他卡片数据不会导入"

        val parsed = sourceEntries.mapIndexed { index, value ->
            require(value.isJsonObject) { "第 ${index + 1} 个词条必须是对象" }
            parseEntry(value.asJsonObject, index, unsupportedBook.isNotEmpty())
        }
        val allWarnings = bookWarnings + parsed.flatMapIndexed { index, entry ->
            entry.warnings.map { "第 ${index + 1} 条：$it" }
        }
        return WorldBookImportReport(
            format = format,
            name = readString(book, "name")?.trim()?.takeIf { it.isNotBlank() }
                ?: fallbackName.trim().ifBlank { "导入的世界书" },
            description = readString(book, "description").orEmpty(),
            entries = parsed.toList(),
            warnings = allWarnings.distinct(),
            unsupportedFields = (unsupportedBook + parsed.flatMap { it.unsupportedFields }).distinct()
        )
    }

    /** Acceptance never enables unsupported entries or the imported book automatically. */
    fun prepare(
        report: WorldBookImportReport,
        acceptCompatibilityLoss: Boolean = false
    ): PreparedWorldBookImport {
        require(!report.requiresCompatibilityConfirmation || acceptCompatibilityLoss) {
            "请先确认兼容提示：未支持的字段不会保存，受影响词条保持停用"
        }
        require(report.entries.isNotEmpty() && report.entries.size <= MAX_ENTRIES) { "没有可导入的词条" }
        return PreparedWorldBookImport(
            book = WorldBook(name = report.name, description = report.description, isEnabled = false),
            entries = report.entries.map { it.draft.copy(id = 0, bookId = 0) }
        )
    }

    private fun parseEntry(obj: JsonObject, index: Int, disableForBookSettings: Boolean): WorldBookImportEntry {
        val prefix = "第 ${index + 1} 个词条"
        val content = readString(obj, "content")
        require(!content.isNullOrBlank()) { "$prefix 的 content 必须是非空字符串" }
        val keys = readKeys(obj, if (obj.has("keys")) "keys" else "key")
        val constant = readBoolean(obj, "constant") ?: false
        val sourceEnabled = readBoolean(obj, "enabled") ?: true
        val sourceDisabled = readBoolean(obj, "disable") ?: false
        val enabled = sourceEnabled && !sourceDisabled
        val warnings = mutableListOf<String>()
        val unsupported = mutableListOf<String>()
        var disable = disableForBookSettings
        val baseFields = setOf(
            "uid", "id", "name", "comment", "content", "key", "keys", "constant", "enabled", "disable",
            "order", "insertion_order", "priority", "extensions", "displayIndex", "addMemo"
        )
        for ((field, value) in obj.entrySet()) {
            if (field !in baseFields && !isNeutralSetting(field, value, obj)) unsupported += field
        }
        val extensions = obj.get("extensions")
        if (extensions != null && !extensions.isJsonNull) {
            require(extensions.isJsonObject) { "$prefix 的 extensions 必须是对象" }
            for ((field, value) in extensions.asJsonObject.entrySet()) {
                if (!isNeutralSetting(field, value, extensions.asJsonObject)) unsupported += "extensions.$field"
            }
        }
        if (keys.any { it.startsWith("/") && it.lastIndexOf('/') > 0 }) {
            unsupported += "正则关键词"
        }
        if (keys.any { it.contains("{{") } || content.contains("{{") || content.lineSequence().any { it.trimStart().startsWith("@@") }) {
            unsupported += "宏或脚本语法"
        }
        if (obj.has("key") && obj.has("keys") && readKeys(obj, "key") != keys) {
            unsupported += "key/keys 冲突"
        }
        if (unsupported.isNotEmpty()) {
            disable = true
            warnings += "未支持 ${unsupported.joinToString("、")}；仅保留基础正文，词条默认停用"
        }
        if (!constant && keys.isEmpty()) {
            disable = true
            warnings += "缺少触发关键词且不是常驻词条，默认停用"
        }
        val insertionOrder = readInt(obj, "insertion_order")
        val nativeOrder = readInt(obj, "order")
        val order = insertionOrder ?: nativeOrder
        val priority = readInt(obj, "priority") ?: order ?: 100
        if (order != null) {
            warnings += "外部插入顺序仅转为预算保留优先级；Echo 使用统一世界书段落，不保留原始插入顺序"
        }
        val keyJson = JsonArray().apply { keys.forEach { add(it) } }.toString()
        val title = readString(obj, "name")?.takeIf { it.isNotBlank() }
            ?: readString(obj, "comment")?.takeIf { it.isNotBlank() }
            ?: keys.firstOrNull() ?: "词条 ${index + 1}"
        return WorldBookImportEntry(
            draft = WorldBookEntry(
                bookId = 0, name = title, keys = keyJson, content = content,
                isEnabled = enabled && !disable, isConstant = constant, priority = priority
            ),
            warnings = warnings.toList(), unsupportedFields = unsupported.distinct()
        )
    }

    private fun isNeutralSetting(field: String, value: JsonElement, owner: JsonObject): Boolean {
        if (value.isJsonNull) return field in nullableSettings
        if (field in falseSettings) return value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && !value.asBoolean
        if (field in emptyArraySettings) return value.isJsonArray && value.asJsonArray.size() == 0
        if (field == "selective") {
            val secondary = owner.get("keysecondary") ?: owner.get("secondary_keys")
            return value.isJsonPrimitive && value.asJsonPrimitive.isBoolean &&
                (!value.asBoolean || secondary == null || (secondary.isJsonArray && secondary.asJsonArray.size() == 0))
        }
        if (field == "probability") return value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble == 100.0
        if (field == "useProbability") return value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
        if (field in setOf("sticky", "cooldown", "delay", "selectiveLogic")) {
            return value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble == 0.0
        }
        return false
    }

    private val falseSettings = setOf(
        "caseSensitive", "case_sensitive", "matchWholeWords", "match_whole_words", "vectorized",
        "excludeRecursion", "exclude_recursion", "preventRecursion", "prevent_recursion",
        "delayUntilRecursion", "delay_until_recursion", "ignoreBudget", "ignore_budget"
    )
    private val emptyArraySettings = setOf("keysecondary", "secondary_keys", "triggers")
    private val nullableSettings = falseSettings + emptyArraySettings + setOf("scanDepth", "scan_depth", "sticky", "cooldown", "delay")

    private fun readString(obj: JsonObject, key: String): String? {
        val value = obj.get(key) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$key 必须是字符串" }
        return value.asString
    }

    private fun readBoolean(obj: JsonObject, key: String): Boolean? {
        val value = obj.get(key) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$key 必须是布尔值" }
        return value.asBoolean
    }

    private fun readInt(obj: JsonObject, key: String): Int? {
        val value = obj.get(key) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$key 必须是整数" }
        return try { value.asBigDecimal.intValueExact() }
        catch (_: ArithmeticException) { throw IllegalArgumentException("$key 超出整数范围或不是整数") }
    }

    private fun readKeys(obj: JsonObject, key: String): List<String> {
        val value = obj.get(key) ?: return emptyList()
        require(value.isJsonArray) { "$key 必须是字符串数组" }
        require(value.asJsonArray.size() <= 256) { "单个词条最多支持 256 个关键词" }
        return value.asJsonArray.map {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "$key 中的关键词必须是字符串" }
            it.asString.trim()
        }.filter { it.isNotBlank() }.distinct()
    }

    /** Gson's JsonParser is lenient; read tokens explicitly to reject malformed and duplicate keys. */
    private fun parseStrictJson(json: String): JsonElement {
        var nodes = 0
        fun read(reader: JsonReader, depth: Int): JsonElement {
            require(depth <= MAX_NESTING && ++nodes <= MAX_JSON_NODES) { "JSON 嵌套或节点数量过多" }
            return when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        require(!has(name)) { "JSON 存在重复字段，请先修正文件" }
                        add(name, read(reader, depth + 1))
                    }
                    reader.endObject()
                }
                JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                    reader.beginArray()
                    while (reader.hasNext()) add(read(reader, depth + 1))
                    reader.endArray()
                }
                JsonToken.STRING -> JsonPrimitive(reader.nextString())
                JsonToken.NUMBER -> {
                    val number = reader.nextString()
                    require(number.length <= 64) { "JSON 数字过长" }
                    JsonPrimitive(BigDecimal(number))
                }
                JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
                else -> throw IllegalArgumentException("JSON 格式不正确")
            }
        }
        return try {
            JsonReader(StringReader(json.removePrefix("\uFEFF"))).use { reader ->
                reader.isLenient = false
                val result = read(reader, 0)
                require(reader.peek() == JsonToken.END_DOCUMENT) { "JSON 结束后还有额外内容" }
                result
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("JSON 格式不正确，请选择有效的世界书 JSON 文件")
        }
    }
}
