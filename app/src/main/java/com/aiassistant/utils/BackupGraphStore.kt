package com.aiassistant.utils

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.ChatGenerationManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.util.UUID
import com.google.gson.stream.JsonWriter
import java.io.Reader
import java.io.Writer

/** A transactional logical snapshot; no live main/WAL/SHM file copying. */
object BackupGraphStore {
    private val gson = Gson()

    /** Keep only the current cursor row in memory, never a whole-database JSON tree/string. */
    suspend fun writeFullSnapshot(
        database: AppDatabase,
        output: Writer,
        decryptKey: (String) -> String = { com.aiassistant.AiAssistantApp.instance.cryptoManager.decrypt(it) }
    ) = database.withTransaction {
        val db = database.openHelper.writableDatabase
        val writer = JsonWriter(output).apply { serializeNulls = true }
        writer.beginObject()
        writer.name("formatVersion").value(2)
        writer.name("type").value("full_snapshot")
        writer.name("complete").value(true)
        writer.name("exportedAt").value(System.currentTimeMillis())
        writer.name("tables").beginObject()
        db.compileStatement("INSERT INTO backup_identities(entityTable,entityId,uuid) VALUES(?,?,?)").use { insert ->
            tables.forEach { table ->
                if (table != "character_tag_cross_ref") {
                    // Identity-only scan: no message bodies or attachments are loaded here.
                    db.query("SELECT t.id FROM `$table` t LEFT JOIN backup_identities b ON b.entityTable=? AND b.entityId=t.id WHERE b.entityId IS NULL", arrayOf(table)).use { cursor ->
                        while (cursor.moveToNext()) {
                            insert.bindString(1, table)
                            insert.bindLong(2, cursor.getLong(0))
                            insert.bindString(3, UUID.randomUUID().toString())
                            insert.executeInsert()
                        }
                    }
                }
                writer.name(table).beginArray()
                val query = if (table == "character_tag_cross_ref") "SELECT * FROM `$table`"
                    else "SELECT t.*, b.uuid AS _uuid FROM `$table` t JOIN backup_identities b ON b.entityTable='$table' AND b.entityId=t.id"
                db.query(query).use { cursor ->
                    val columns = cursor.columnNames
                    while (cursor.moveToNext()) {
                        writer.beginObject()
                        columns.forEachIndexed { index, name ->
                            writer.name(name)
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> writer.nullValue()
                                Cursor.FIELD_TYPE_INTEGER -> writer.value(cursor.getLong(index))
                                Cursor.FIELD_TYPE_FLOAT -> writer.value(cursor.getDouble(index))
                                Cursor.FIELD_TYPE_STRING -> writer.value(
                                    if (table == "api_configs" && name == "apiKey") decryptKey(cursor.getString(index)) else cursor.getString(index)
                                )
                                else -> error("不支持的备份字段：$name")
                            }
                        }
                        writer.endObject()
                    }
                }
                writer.endArray()
            }
        }
        writer.endObject().endObject()
        writer.flush() // Do not close the caller's ZIP stream; a failed flush rolls back identities.
    }

    val tables = listOf("folders", "api_configs", "character_profiles", "roleplay_scenarios", "character_tags",
        "world_books", "world_book_entries", "conversations", "messages", "roleplay_sessions", "roleplay_memories",
        "memory_items", "timeline_nodes", "conversation_branches", "selected_models", "environment_variables",
        "prompt_templates", "api_usage_stats", "character_tag_cross_ref")

    private val references = mapOf(
        "folders" to mapOf("parentId" to "folders"),
        "conversations" to mapOf("apiConfigId" to "api_configs", "folderId" to "folders", "summaryUpdatedMessageId" to "messages"),
        "messages" to mapOf("conversationId" to "conversations", "injectionTargetMessageId" to "messages"),
        "world_book_entries" to mapOf("bookId" to "world_books"),
        "roleplay_sessions" to mapOf("conversationId" to "conversations", "characterId" to "character_profiles", "scenarioId" to "roleplay_scenarios"),
        "roleplay_memories" to mapOf("sessionId" to "roleplay_sessions", "sourceMessageId" to "messages"),
        "memory_items" to mapOf("conversationId" to "conversations", "sourceMessageId" to "messages"),
        "timeline_nodes" to mapOf("conversationId" to "conversations"),
        "conversation_branches" to mapOf("parentConversationId" to "conversations", "childConversationId" to "conversations", "branchMessageId" to "messages"),
        "selected_models" to mapOf("apiConfigId" to "api_configs"),
        "api_usage_stats" to mapOf("apiConfigId" to "api_configs"),
        "character_tag_cross_ref" to mapOf("characterId" to "character_profiles", "tagId" to "character_tags")
    )

    fun ids(raw: String?): List<Long> = raw?.trim()?.removePrefix("[")?.removeSuffix("]")
        ?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()

    private fun id(row: JsonObject, field: String = "id"): Long? = row.get(field)?.takeUnless { it.isJsonNull }?.asLong

    suspend fun export(database: AppDatabase, conversationId: Long? = null,
        decryptKey: (String) -> String = { com.aiassistant.AiAssistantApp.instance.cryptoManager.decrypt(it) }
    ): JsonObject = database.withTransaction {
        val db = database.openHelper.writableDatabase
        val all = tables.associateWith { table ->
            db.query("SELECT * FROM `$table`").use { cursor ->
                buildList { while (cursor.moveToNext()) add(row(cursor)) }
            }
        }
        // Seed the conversation-owned records, then include their referenced shared entities.
        val selected = tables.associateWith { linkedSetOf<JsonObject>() }
        if (conversationId == null) all.forEach { (table, rows) -> selected.getValue(table).addAll(rows) }
        else {
            selected.getValue("conversations").addAll(all.getValue("conversations").filter { id(it) == conversationId })
            require(selected.getValue("conversations").isNotEmpty()) { "对话不存在" }
            listOf("messages", "memory_items", "timeline_nodes", "roleplay_sessions").forEach { table ->
                selected.getValue(table).addAll(all.getValue(table).filter { id(it, "conversationId") == conversationId })
            }
            val sessions = selected.getValue("roleplay_sessions").mapNotNull { id(it) }.toSet()
            selected.getValue("roleplay_memories").addAll(all.getValue("roleplay_memories").filter { id(it, "sessionId") in sessions })
            var changed: Boolean
            do {
                changed = false
                tables.forEach { table -> selected.getValue(table).toList().forEach { item ->
                    fun include(target: String, ids: Collection<Long>) {
                        all.getValue(target).filter { id(it) in ids }.forEach { if (selected.getValue(target).add(it)) changed = true }
                    }
                    references[table].orEmpty().forEach { (field, target) ->
                        // Never pull another conversation through an external source-message reference.
                        if (target != "messages" && target != "conversations") id(item, field)?.let { include(target, listOf(it)) }
                    }
                    include("world_books", ids(item.get("activeWorldBookIds")?.takeUnless { it.isJsonNull }?.asString))
                    include("character_profiles", ids(item.get("characterIds")?.takeUnless { it.isJsonNull }?.asString))
                    if (table == "world_books") all.getValue("world_book_entries").filter { id(it, "bookId") == id(item) }.forEach { if (selected.getValue("world_book_entries").add(it)) changed = true }
                    if (table == "character_profiles") all.getValue("character_tag_cross_ref").filter { id(it, "characterId") == id(item) }.forEach { if (selected.getValue("character_tag_cross_ref").add(it)) changed = true }
                } }
            } while (changed)
        }
        val content = JsonObject()
        tables.forEach { table ->
            content.add(table, JsonArray().apply { selected.getValue(table).forEach { item ->
                id(item)?.let { rowId ->
                    var uuid: String? = null
                    db.query("SELECT uuid FROM backup_identities WHERE entityTable=? AND entityId=?", arrayOf(table, rowId)).use { if (it.moveToFirst()) uuid = it.getString(0) }
                    if (uuid == null) {
                        uuid = UUID.randomUUID().toString()
                        db.execSQL("INSERT INTO backup_identities(entityTable,entityId,uuid) VALUES(?,?,?)", arrayOf(table, rowId, uuid))
                    }
                    item.addProperty("_uuid", uuid)
                }
                // Device-bound ciphertext cannot be used by a different Android keystore.
                if (table == "api_configs") item.get("apiKey")?.let {
                    item.addProperty("apiKey", decryptKey(it.asString))
                }
                add(item)
            } })
        }
        JsonObject().apply {
            addProperty("formatVersion", 2)
            addProperty("type", if (conversationId == null) "full_snapshot" else "single_conversation")
            addProperty("complete", true)
            addProperty("exportedAt", System.currentTimeMillis())
            add("tables", content)
        }
    }

    /** Legacy databases have no portable identity: use content-scoped identities, never local IDs/titles. */
    fun legacy(db: SQLiteDatabase): JsonObject {
        val content = JsonObject()
        tables.forEach { table ->
            val exists = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }
            val rows = JsonArray()
            if (exists) db.rawQuery("SELECT * FROM `$table`", null).use { cursor -> while (cursor.moveToNext()) rows.add(row(cursor)) }
            content.add(table, rows)
        }
        return legacyEnvelope(content)
    }

    fun legacySingle(json: String): JsonObject {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.has("conversation") && root.has("messages")) { "不是完整单对话备份" }
        val content = JsonObject().apply { tables.forEach { add(it, JsonArray()) } }
        mapOf("conversation" to "conversations", "characterProfile" to "character_profiles", "roleplayScenario" to "roleplay_scenarios", "roleplaySession" to "roleplay_sessions").forEach { (field, table) ->
            root.get(field)?.takeUnless { it.isJsonNull }?.let { content.getAsJsonArray(table).add(it) }
        }
        mapOf("messages" to "messages", "roleplayMemories" to "roleplay_memories", "timelineNodes" to "timeline_nodes", "sessionMemories" to "memory_items").forEach { (field, table) ->
            val value = root.get(field) ?: if (field == "sessionMemories") root.get("sessionSettings") ?: root.get("conversationMemories") else null
            value?.takeUnless { it.isJsonNull }?.let { require(it.isJsonArray); content.add(table, it) }
        }
        content.getAsJsonArray("timeline_nodes").forEach { node -> node.asJsonObject.let { if (it.has("event")) it.add("eventContent", it.remove("event")) } }
        return legacyEnvelope(content)
    }

    private fun legacyEnvelope(content: JsonObject): JsonObject {
        val source = UUID.nameUUIDFromBytes(content.toString().toByteArray()).toString()
        tables.forEach { table -> content.getAsJsonArray(table).forEach { element ->
            val item = element.asJsonObject
            id(item)?.let { item.addProperty("_uuid", UUID.nameUUIDFromBytes("$source/$table/$it".toByteArray()).toString()) }
        } }
        return JsonObject().apply { addProperty("formatVersion", 2); addProperty("complete", true); addProperty("legacy", true); add("tables", content) }
    }

    /**
     * Restores a snapshot that is already materialised as a tree. Kept for legacy conversion and tests;
     * on-disk imports use [restoreStream] so a backup is never copied into one string or one tree.
     */
    suspend fun restore(database: AppDatabase, snapshot: JsonObject,
        encryptKey: (String) -> String = { com.aiassistant.AiAssistantApp.instance.cryptoManager.encrypt(it) },
        cancelRequest: (Long) -> Unit = { com.aiassistant.AiAssistantApp.instance.repository.cancelActiveRequest(it) }
    ) {
        require(snapshot.get("formatVersion")?.asInt == 2 && snapshot.get("complete")?.asBoolean == true) { "备份不是完整快照" }
        val content = requireNotNull(snapshot.getAsJsonObject("tables"))
        val rows = tables.associateWith { table ->
            requireNotNull(content.getAsJsonArray(table)) { "备份缺少 $table" }.map { it.asJsonObject }
        }
        val state = RestoreState(database, encryptKey, cancelRequest).apply {
            legacy = snapshot.get("legacy")?.asBoolean == true
            type = snapshot.get("type")?.asString
        }
        rows.forEach { (table, items) -> items.forEach { observeRow(state, table, it) } }
        detachActiveSessions(state)
        database.withTransaction {
            allocateIds(state)
            rows.forEach { (table, items) -> items.forEach { writeRow(state, table, it) } }
            finishRestore(state)
        }
        database.invalidationTracker.refreshAsync()
    }

    /**
     * Streaming restore for files: the snapshot is read twice from [openReader] instead of being copied into a
     * single string and parsed into one tree, so memory stays proportional to one row plus identity maps.
     * With [tolerant] enabled, references to rows the device no longer holds are cleared or their rows skipped.
     */
    suspend fun restoreStream(database: AppDatabase, openReader: () -> Reader, tolerant: Boolean = false,
        encryptKey: (String) -> String = { com.aiassistant.AiAssistantApp.instance.cryptoManager.encrypt(it) },
        cancelRequest: (Long) -> Unit = { com.aiassistant.AiAssistantApp.instance.repository.cancelActiveRequest(it) }
    ): RestoreReport {
        val state = RestoreState(database, encryptKey, cancelRequest)
        state.tolerant = tolerant
        CountingReader(openReader()).use { counting ->
            scanSnapshot(JsonReader(counting).apply { isLenient = true }, state, counting)
        }
        requireRowsFitHeap(state)
        detachActiveSessions(state)
        database.withTransaction {
            allocateIds(state)
            openReader().use { reader -> writeSnapshot(JsonReader(reader).apply { isLenient = true }, state) }
            finishRestore(state)
        }
        database.invalidationTracker.refreshAsync()
        return state.report()
    }

    /** What a tolerant import had to clear or drop because the referenced rows are not in the snapshot. */
    data class RestoreReport(
        val skippedRows: Int = 0,
        val clearedRefs: Int = 0,
        val details: List<String> = emptyList()
    ) {
        val isEmpty: Boolean get() = skippedRows == 0 && clearedRefs == 0

        fun describe(): String? {
            if (isEmpty) return null
            val sample = details.take(3).joinToString("、")
            val suffix = if (details.size > 3) " 等" else ""
            return "已清理 $clearedRefs 处失效关联、跳过 $skippedRows 条失效记录（$sample$suffix）"
        }
    }

    /** One row plus its tree and ContentValues must still fit; refuse with a reason instead of dying with OOM. */
    private fun requireRowsFitHeap(state: RestoreState) {
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val peak = state.maxRowChars * 2 * 3
        check(peak < available) {
            "备份中存在超大记录（约 ${state.maxRowChars * 2 / (1024 * 1024)} MB），当前可用内存不足"
        }
    }

    private class CountingReader(private val delegate: Reader) : Reader() {
        var consumed = 0L
            private set

        override fun read(cbuf: CharArray, off: Int, len: Int): Int =
            delegate.read(cbuf, off, len).also { if (it > 0) consumed += it }

        override fun close() = delegate.close()
    }

    private class RestoreState(
        val database: AppDatabase,
        val encryptKey: (String) -> String,
        val cancelRequest: (Long) -> Unit
    ) {
        val db = database.openHelper.writableDatabase
        var legacy = false
        var type: String? = null
        var maxRowChars = 0L
        var conversationIds: Set<Long> = emptySet()
        /** Tolerant mode nulls nullable references and drops rows whose required reference is gone. */
        var tolerant = false
        var skippedRows = 0
        var clearedRefs = 0
        val details = LinkedHashSet<String>()

        private val identities = HashMap<String, LinkedHashMap<Long, String>>()
        private val hints = HashMap<String, HashMap<Long, Array<String?>>>()
        private val seenUuids = HashMap<String, HashSet<String>>()
        private val maps = HashMap<String, MutableMap<Long, Long>>()
        private val existing = HashMap<String, MutableSet<Long>>()
        private val columnCache = HashMap<String, Set<String>>()
        private val notNullCache = HashMap<String, Set<String>>()
        val messageRoles = HashMap<Long, String>()
        val messageConversations = HashMap<Long, Long>()
        val injections = ArrayList<LongArray>()

        fun identityMap(table: String): LinkedHashMap<Long, String> = identities.getOrPut(table) { LinkedHashMap() }
        fun hintMap(table: String): HashMap<Long, Array<String?>> = hints.getOrPut(table) { HashMap() }
        fun uuidSet(table: String): HashSet<String> = seenUuids.getOrPut(table) { HashSet() }
        fun idMap(table: String): MutableMap<Long, Long> = maps.getOrPut(table) { mutableMapOf() }
        fun existingSet(table: String): MutableSet<Long> = existing.getOrPut(table) { mutableSetOf() }
        fun columns(table: String): Set<String> = columnCache.getOrPut(table) {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
        }

        fun notNullColumns(table: String): Set<String> = notNullCache.getOrPut(table) {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                buildSet { while (cursor.moveToNext()) if (cursor.getInt(3) != 0) add(cursor.getString(1)) }
            }
        }

        fun report(): RestoreReport = RestoreReport(skippedRows, clearedRefs, details.toList())
    }

    private fun observeRow(state: RestoreState, table: String, item: JsonObject) {
        val rowId = id(item)
        val uuid = item.get("_uuid")
        if (table != "character_tag_cross_ref") {
            requireNotNull(rowId) { "$table 缺少 ID" }
            requireNotNull(uuid) { "$table 缺少身份" }
            val hints = when (table) {
                "api_configs" -> arrayOf(
                    item.get("name")?.asString, item.get("provider")?.asString, item.get("baseUrl")?.asString,
                    item.get("apiType")?.asString, item.get("modelName")?.asString
                )
                "character_tags" -> arrayOf(item.get("name")?.asString)
                else -> null
            }
            recordIdentity(state, table, rowId, uuid.asString, hints)
        }
        if (table == "messages") {
            recordMessage(state, rowId, item.get("role")?.asString, id(item, "conversationId"), id(item, "injectionTargetMessageId"))
        }
    }

    /** Light scan: reads only identity and injection fields, every other value is skipped without allocation. */
    private fun observeStreamRow(state: RestoreState, table: String, reader: JsonReader) {
        var rowId: Long? = null
        var uuid: String? = null
        var role: String? = null
        var conversationId: Long? = null
        var injectionTarget: Long? = null
        val hints: Array<String?>? = when (table) {
            "api_configs" -> arrayOfNulls(5)
            "character_tags" -> arrayOfNulls(1)
            else -> null
        }
        reader.beginObject()
        while (reader.hasNext()) {
            when (val name = reader.nextName()) {
                "id" -> rowId = reader.longOrNull()
                "_uuid" -> uuid = reader.stringOrNull()
                "role" -> if (table == "messages") role = reader.stringOrNull() else reader.skipValue()
                "conversationId" -> if (table == "messages") conversationId = reader.longOrNull() else reader.skipValue()
                "injectionTargetMessageId" -> if (table == "messages") injectionTarget = reader.longOrNull() else reader.skipValue()
                "name" -> if (hints != null) hints[0] = reader.stringOrNull() else reader.skipValue()
                "provider" -> if (hints != null && hints.size == 5) hints[1] = reader.stringOrNull() else reader.skipValue()
                "baseUrl" -> if (hints != null && hints.size == 5) hints[2] = reader.stringOrNull() else reader.skipValue()
                "apiType" -> if (hints != null && hints.size == 5) hints[3] = reader.stringOrNull() else reader.skipValue()
                "modelName" -> if (hints != null && hints.size == 5) hints[4] = reader.stringOrNull() else reader.skipValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (table != "character_tag_cross_ref") {
            val sourceId = rowId ?: error("$table 缺少 ID")
            val identity = uuid ?: error("$table 缺少身份")
            recordIdentity(state, table, sourceId, identity, hints)
        }
        if (table == "messages") recordMessage(state, rowId, role, conversationId, injectionTarget)
    }

    private fun recordIdentity(state: RestoreState, table: String, rowId: Long, uuid: String, hints: Array<String?>?) {
        require(state.identityMap(table).put(rowId, uuid) == null) { "$table 存在重复 ID" }
        require(state.uuidSet(table).add(uuid)) { "$table 存在重复身份" }
        if (hints != null) state.hintMap(table)[rowId] = hints
    }

    private fun recordMessage(state: RestoreState, rowId: Long?, role: String?, conversationId: Long?, injectionTarget: Long?) {
        if (rowId == null) return
        state.messageRoles[rowId] = role.orEmpty()
        state.messageConversations[rowId] = conversationId ?: 0L
        if (injectionTarget != null) state.injections.add(longArrayOf(rowId, injectionTarget))
    }

    private fun validateInjections(state: RestoreState) {
        state.injections.forEach { pair ->
            val target = pair[1]
            require(state.messageRoles[target] == "assistant" && state.messageRoles[pair[0]] == "user" &&
                state.messageConversations[pair[0]] == state.messageConversations[target]) {
                "备份的输入注入关联无效"
            }
        }
    }

    // Detach sessions before mutation; cancellation must not persist a late partial reply.
    private suspend fun detachActiveSessions(state: RestoreState) {
        state.identityMap("conversations").values.forEach { uuid ->
            state.db.query("SELECT entityId FROM backup_identities WHERE entityTable='conversations' AND uuid=?", arrayOf(uuid)).use {
                if (it.moveToFirst()) {
                    val local = it.getLong(0)
                    val job = ChatGenerationManager.getSession(local)?.generationJob
                    ChatGenerationManager.cancelSession(local)
                    state.cancelRequest(local)
                    job?.join()
                }
            }
        }
    }

    private fun allocateIds(state: RestoreState) {
        validateInjections(state)
        val db = state.db
        // Allocate all IDs first, so forward references and cycles can be translated before writes.
        tables.filter { it != "character_tag_cross_ref" }.forEach { table ->
            var next = db.query("SELECT COALESCE(MAX(id),0)+1 FROM `$table`").use { it.moveToFirst(); it.getLong(0) }
            db.query("SELECT seq+1 FROM sqlite_sequence WHERE name=?", arrayOf(table)).use { if (it.moveToFirst()) next = maxOf(next, it.getLong(0)) }
            val ids = state.idMap(table)
            state.identityMap(table).forEach { (sourceId, uuid) ->
                var local: Long? = null
                db.query("SELECT b.entityId FROM backup_identities b JOIN `$table` t ON t.id=b.entityId WHERE b.entityTable=? AND b.uuid=?", arrayOf(table, uuid)).use { if (it.moveToFirst()) local = it.getLong(0) }
                // Reuse API identity with local credentials instead of matching source numeric IDs.
                if (local == null && table == "api_configs") {
                    val hint = state.hintMap(table)[sourceId].orEmpty()
                    db.query("SELECT id FROM api_configs WHERE name=? AND provider=? AND baseUrl=? AND apiType=? AND modelName=? LIMIT 1",
                        arrayOf(hint.getOrNull(0).orEmpty(), hint.getOrNull(1).orEmpty(), hint.getOrNull(2).orEmpty(),
                            hint.getOrNull(3).orEmpty(), hint.getOrNull(4).orEmpty())).use { if (it.moveToFirst()) local = it.getLong(0) }
                }
                if (local == null && table == "character_tags") {
                    db.query("SELECT id FROM character_tags WHERE name=?", arrayOf(state.hintMap(table)[sourceId]?.getOrNull(0).orEmpty())).use { if (it.moveToFirst()) local = it.getLong(0) }
                }
                local?.let { state.existingSet(table).add(it) }
                ids[sourceId] = local ?: next++
            }
            // The write pass re-reads identities from each row; drop the scan copy before the largest table.
            state.identityMap(table).clear()
        }
        state.conversationIds = state.idMap("conversations").values.toSet()
        // Composite relations and world-book entries are also complete snapshots for included owners.
        state.idMap("character_profiles").values.forEach { db.delete("character_tag_cross_ref", "characterId=?", arrayOf(it)) }
    }

    private fun writeRow(state: RestoreState, table: String, original: JsonObject) {
        val db = state.db
        val item = original.deepCopy()
        // Missing fields in pre-feature snapshots restore the old content-only state,
        // including when an existing portable identity is being overwritten.
        if (table == "conversations") {
            if (!item.has("enableInputInjection")) item.addProperty("enableInputInjection", false)
            if (!item.has("inputInjectionTrigger")) item.add("inputInjectionTrigger", com.google.gson.JsonNull.INSTANCE)
        }
        if (table == "messages") {
            if (!item.has("modelContent")) item.add("modelContent", com.google.gson.JsonNull.INSTANCE)
            if (!item.has("injectionTargetMessageId")) item.add("injectionTargetMessageId", com.google.gson.JsonNull.INSTANCE)
        }
        val local = id(item)?.let { state.idMap(table).getValue(it) }
        if (local != null) item.addProperty("id", local)
        fun mapped(target: String, old: Long?): Long? = old?.let { state.idMap(target)[it] }
        val notNull = state.notNullColumns(table)
        var dropRow = false
        for ((field, target) in references[table].orEmpty()) {
            val old = id(item, field)
            var replacement = mapped(target, old)
            if (replacement == null && table == "conversations" && field == "apiConfigId") {
                replacement = db.query("SELECT id FROM api_configs WHERE isEnabled=1 ORDER BY isDefault DESC LIMIT 1").use { if (it.moveToFirst()) it.getLong(0) else null }
                require(replacement != null) { "请先配置可用的 API，再导入旧备份" }
            }
            if (old != null && replacement == null && !state.legacy && field !in setOf("sourceMessageId", "summaryUpdatedMessageId")) {
                if (!state.tolerant) error("备份关联缺失：$table.$field")
                // These columns carry no foreign key, so a device can hold references to rows deleted earlier.
                state.details += "$table.$field"
                if (field in notNull) dropRow = true else state.clearedRefs++
            }
            if (replacement == null) item.add(field, com.google.gson.JsonNull.INSTANCE) else item.addProperty(field, replacement)
        }
        if (dropRow) {
            state.skippedRows++
            return
        }
        listOf("activeWorldBookIds" to "world_books", "characterIds" to "character_profiles").forEach { (field, target) ->
            item.get(field)?.takeUnless { it.isJsonNull }?.let { value -> item.addProperty(field, ids(value.asString).mapNotNull { if (field == "characterIds" && it < 0) it else mapped(target, it) }.joinToString(",")) }
        }
        if (table == "roleplay_sessions") {
            item.get("customCharacterData")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }?.let { raw ->
                val embedded = JsonParser.parseString(raw).asJsonArray
                embedded.forEach { char -> mapped("character_profiles", id(char.asJsonObject))?.let { char.asJsonObject.addProperty("id", it) } }
                item.addProperty("customCharacterData", embedded.toString())
            }
            item.get("customScenarioData")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }?.let { raw ->
                val embedded = JsonParser.parseString(raw).asJsonObject
                mapped("roleplay_scenarios", id(embedded))?.let { embedded.addProperty("id", it) }
                item.addProperty("customScenarioData", embedded.toString())
            }
        }
        val cv = ContentValues()
        if (table == "messages") item.get("variantGroupId")?.takeUnless { it.isJsonNull }?.asString?.let { group ->
            val match = Regex("^turn_(\\d+)(.*)$").matchEntire(group)
            match?.groupValues?.get(1)?.toLongOrNull()?.let { mapped("messages", it) }?.let {
                item.addProperty("variantGroupId", "turn_${it}${match!!.groupValues[2]}")
            }
        }
        val columns = state.columns(table)
        item.entrySet().filter { it.key in columns }.forEach { (key, value) ->
            if (value.isJsonNull) cv.putNull(key)
            else if (value.asJsonPrimitive.isBoolean) cv.put(key, if (value.asBoolean) 1 else 0)
            else cv.put(key, value.asString)
        }
        if (table == "api_configs") {
            if (local in state.existingSet(table)) cv.remove("apiKey")
            else {
                val key = cv.getAsString("apiKey").orEmpty()
                cv.put("apiKey", if (state.legacy && key.startsWith(CryptoManager.CIPHER_PREFIX)) "" else state.encryptKey(key))
            }
        }
        if (table == "character_tag_cross_ref") {
            db.insert(table, SQLiteDatabase.CONFLICT_IGNORE, cv)
        } else {
            if (local in state.existingSet(table)) db.update(table, SQLiteDatabase.CONFLICT_ABORT, cv, "id=?", arrayOf(local))
            else check(db.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv) >= 0)
            db.execSQL("INSERT OR REPLACE INTO backup_identities(entityTable,entityId,uuid) VALUES(?,?,?)", arrayOf(table, local, original.get("_uuid").asString))
        }
    }

    private suspend fun finishRestore(state: RestoreState) {
        val db = state.db
        val conversationIds = state.conversationIds
        // Remove target-only owned rows after incoming rows exist. Reused IDs keep external references stable.
        listOf("messages", "memory_items", "timeline_nodes", "roleplay_sessions").forEach { table ->
            val keep = state.idMap(table).values.toSet()
            conversationIds.forEach { conversation ->
                val stale = db.query("SELECT id FROM `$table` WHERE conversationId=?", arrayOf(conversation)).use { cursor -> buildList { while(cursor.moveToNext()) if (cursor.getLong(0) !in keep) add(cursor.getLong(0)) } }
                stale.forEach { old ->
                    if (table == "messages") {
                        db.execSQL("UPDATE memory_items SET sourceMessageId=NULL WHERE sourceMessageId=?", arrayOf(old))
                        db.execSQL("UPDATE roleplay_memories SET sourceMessageId=NULL WHERE sourceMessageId=?", arrayOf(old))
                        db.execSQL("UPDATE conversations SET summaryUpdatedMessageId=NULL WHERE summaryUpdatedMessageId=?", arrayOf(old))
                        db.execSQL("UPDATE messages SET injectionTargetMessageId=NULL WHERE injectionTargetMessageId=?", arrayOf(old))
                        db.execSQL("DELETE FROM conversation_branches WHERE branchMessageId=?", arrayOf(old))
                    }
                    db.delete(table, "id=?", arrayOf(old))
                    db.execSQL("DELETE FROM backup_identities WHERE entityTable=? AND entityId=?", arrayOf(table, old))
                }
            }
        }
        val keptMemories = state.idMap("roleplay_memories").values.toSet()
        state.idMap("roleplay_sessions").values.forEach { session ->
            val stale = db.query("SELECT id FROM roleplay_memories WHERE sessionId=?", arrayOf(session)).use { c -> buildList { while(c.moveToNext()) if(c.getLong(0) !in keptMemories) add(c.getLong(0)) } }
            stale.forEach { db.delete("roleplay_memories", "id=?", arrayOf(it)) }
        }
        val keptEntries = state.idMap("world_book_entries").values.toSet()
        state.idMap("world_books").values.forEach { book ->
            val stale = db.query("SELECT id FROM world_book_entries WHERE bookId=?", arrayOf(book)).use { c -> buildList { while(c.moveToNext()) if(c.getLong(0) !in keptEntries) add(c.getLong(0)) } }
            stale.forEach { db.delete("world_book_entries", "id=?", arrayOf(it)) }
        }
        if (state.type == "full_snapshot") {
            val keep = state.idMap("conversation_branches").values.toSet()
            val stale = db.query("SELECT id,parentConversationId,childConversationId FROM conversation_branches").use { c ->
                buildList { while(c.moveToNext()) if (c.getLong(0) !in keep && c.getLong(1) in conversationIds && c.getLong(2) in conversationIds) add(c.getLong(0)) }
            }
            stale.forEach { db.delete("conversation_branches", "id=?", arrayOf(it)) }
        }
        conversationIds.forEach { state.database.conversationDao().refreshStats(it) }
        db.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "恢复后的关联检查失败" } }
    }

    private fun scanSnapshot(reader: JsonReader, state: RestoreState, counting: CountingReader) {
        var formatVersion = -1
        var complete = false
        var sawTables = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "formatVersion" -> formatVersion = reader.intOrNull() ?: -1
                "complete" -> complete = reader.booleanOrNull() ?: false
                "legacy" -> state.legacy = reader.booleanOrNull() ?: false
                "type" -> state.type = reader.stringOrNull()
                "tables" -> {
                    sawTables = true
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val table = reader.nextName()
                        // Unknown tables come from newer versions; skip them instead of failing the import.
                        if (table !in tables) { reader.skipValue(); continue }
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val start = counting.consumed
                            observeStreamRow(state, table, reader)
                            state.maxRowChars = maxOf(state.maxRowChars, counting.consumed - start)
                        }
                        reader.endArray()
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        require(sawTables) { "备份缺少 tables" }
        require(formatVersion == 2 && complete) { "备份不是完整快照" }
    }

    private fun writeSnapshot(reader: JsonReader, state: RestoreState) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "tables" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val table = reader.nextName()
                        if (table !in tables) { reader.skipValue(); continue }
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val item: JsonObject? = gson.fromJson(reader, JsonObject::class.java)
                            if (item != null) writeRow(state, table, item)
                        }
                        reader.endArray()
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun JsonReader.longOrNull(): Long? = if (peek() == JsonToken.NULL) { nextNull(); null } else nextLong()

    private fun JsonReader.intOrNull(): Int? = if (peek() == JsonToken.NULL) { nextNull(); null } else nextInt()

    private fun JsonReader.stringOrNull(): String? = if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()

    private fun JsonReader.booleanOrNull(): Boolean? = if (peek() == JsonToken.NULL) { nextNull(); null } else nextBoolean()

    private fun row(cursor: Cursor): JsonObject = JsonObject().apply {
        cursor.columnNames.forEachIndexed { index, name ->
            when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> add(name, com.google.gson.JsonNull.INSTANCE)
                Cursor.FIELD_TYPE_INTEGER -> addProperty(name, cursor.getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> addProperty(name, cursor.getDouble(index))
                Cursor.FIELD_TYPE_STRING -> addProperty(name, cursor.getString(index))
                else -> error("不支持的备份字段：$name")
            }
        }
    }
}
