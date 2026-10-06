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
import com.google.gson.JsonParser
import java.util.UUID
import com.google.gson.stream.JsonWriter
import java.io.Writer

/** A transactional logical snapshot; no live main/WAL/SHM file copying. */
object BackupGraphStore {
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
        "messages" to mapOf("conversationId" to "conversations"),
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

    suspend fun restore(database: AppDatabase, snapshot: JsonObject,
        encryptKey: (String) -> String = { com.aiassistant.AiAssistantApp.instance.cryptoManager.encrypt(it) },
        cancelRequest: (Long) -> Unit = { com.aiassistant.AiAssistantApp.instance.repository.cancelActiveRequest(it) }
    ) {
        require(snapshot.get("formatVersion")?.asInt == 2 && snapshot.get("complete")?.asBoolean == true) { "备份不是完整快照" }
        val content = requireNotNull(snapshot.getAsJsonObject("tables"))
        val legacy = snapshot.get("legacy")?.asBoolean == true
        val rows = tables.associateWith { table ->
            requireNotNull(content.getAsJsonArray(table)) { "备份缺少 $table" }.map { it.asJsonObject }
        }
        rows.forEach { (table, items) ->
            if (table != "character_tag_cross_ref") {
                require(items.map { requireNotNull(id(it)) }.distinct().size == items.size) { "$table 存在重复 ID" }
                require(items.map { requireNotNull(it.get("_uuid")).asString }.distinct().size == items.size) { "$table 存在重复身份" }
            }
        }
        val db = database.openHelper.writableDatabase
        // Detach sessions before mutation; cancellation must not persist a late partial reply.
        rows.getValue("conversations").forEach { item ->
            db.query("SELECT entityId FROM backup_identities WHERE entityTable='conversations' AND uuid=?", arrayOf(item.get("_uuid").asString)).use {
                if (it.moveToFirst()) {
                    val local = it.getLong(0)
                    val job = ChatGenerationManager.getSession(local)?.generationJob
                    ChatGenerationManager.cancelSession(local)
                    cancelRequest(local)
                    job?.join()
                }
            }
        }
        database.withTransaction {
            db.execSQL("PRAGMA defer_foreign_keys=ON")
            val maps = tables.associateWith { mutableMapOf<Long, Long>() }
            val existing = tables.associateWith { mutableSetOf<Long>() }
            // Allocate all IDs first, so forward references and cycles can be translated before writes.
            tables.filter { it != "character_tag_cross_ref" }.forEach { table ->
                var next = db.query("SELECT COALESCE(MAX(id),0)+1 FROM `$table`").use { it.moveToFirst(); it.getLong(0) }
                db.query("SELECT seq+1 FROM sqlite_sequence WHERE name=?", arrayOf(table)).use { if (it.moveToFirst()) next = maxOf(next, it.getLong(0)) }
                rows.getValue(table).forEach { item ->
                    var local: Long? = null
                    db.query("SELECT b.entityId FROM backup_identities b JOIN `$table` t ON t.id=b.entityId WHERE b.entityTable=? AND b.uuid=?", arrayOf(table, item.get("_uuid").asString)).use { if (it.moveToFirst()) local = it.getLong(0) }
                    // Reuse API identity with local credentials instead of matching source numeric IDs.
                    if (local == null && table == "api_configs") db.query("SELECT id FROM api_configs WHERE name=? AND provider=? AND baseUrl=? AND apiType=? AND modelName=? LIMIT 1", arrayOf("name", "provider", "baseUrl", "apiType", "modelName").map { item.get(it)?.asString.orEmpty() }.toTypedArray()).use { if (it.moveToFirst()) local = it.getLong(0) }
                    if (local == null && table == "character_tags") db.query("SELECT id FROM character_tags WHERE name=?", arrayOf(item.get("name").asString)).use { if (it.moveToFirst()) local = it.getLong(0) }
                    local?.let { existing.getValue(table).add(it) }
                    maps.getValue(table)[id(item)!!] = local ?: next++
                }
            }
            val conversationIds = maps.getValue("conversations").values.toSet()
            // Composite relations and world-book entries are also complete snapshots for included owners.
            maps.getValue("character_profiles").values.forEach { db.delete("character_tag_cross_ref", "characterId=?", arrayOf(it)) }
            fun mapped(target: String, old: Long?): Long? = old?.let { maps.getValue(target)[it] }
            tables.forEach { table ->
                val columns = db.query("PRAGMA table_info(`$table`)").use { cursor -> buildSet { while(cursor.moveToNext()) add(cursor.getString(1)) } }
                rows.getValue(table).forEach { original ->
                    val item = original.deepCopy()
                    val local = id(item)?.let { maps.getValue(table).getValue(it) }
                    if (local != null) item.addProperty("id", local)
                    references[table].orEmpty().forEach { (field, target) ->
                        val old = id(item, field)
                        var replacement = mapped(target, old)
                        if (replacement == null && table == "conversations" && field == "apiConfigId") {
                            replacement = db.query("SELECT id FROM api_configs WHERE isEnabled=1 ORDER BY isDefault DESC LIMIT 1").use { if (it.moveToFirst()) it.getLong(0) else null }
                            require(replacement != null) { "请先配置可用的 API，再导入旧备份" }
                        }
                        if (old != null && replacement == null && !legacy && field !in setOf("sourceMessageId", "summaryUpdatedMessageId")) error("备份关联缺失：$table.$field")
                        if (replacement == null) item.add(field, com.google.gson.JsonNull.INSTANCE) else item.addProperty(field, replacement)
                    }
                    listOf("activeWorldBookIds" to "world_books", "characterIds" to "character_profiles").forEach { (field, target) ->
                        item.get(field)?.takeUnless { it.isJsonNull }?.let { value -> item.addProperty(field, ids(value.asString).mapNotNull { mapped(target, it) }.joinToString(",")) }
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
                    item.entrySet().filter { it.key in columns }.forEach { (key, value) ->
                        if (value.isJsonNull) cv.putNull(key)
                        else if (value.asJsonPrimitive.isBoolean) cv.put(key, if (value.asBoolean) 1 else 0)
                        else cv.put(key, value.asString)
                    }
                    if (table == "api_configs") {
                        if (local in existing.getValue(table)) cv.remove("apiKey")
                        else {
                            val key = cv.getAsString("apiKey").orEmpty()
                            cv.put("apiKey", if (legacy && key.startsWith(CryptoManager.CIPHER_PREFIX)) "" else encryptKey(key))
                        }
                    }
                    if (table == "character_tag_cross_ref") {
                        db.insert(table, SQLiteDatabase.CONFLICT_IGNORE, cv)
                    } else {
                        if (local in existing.getValue(table)) db.update(table, SQLiteDatabase.CONFLICT_ABORT, cv, "id=?", arrayOf(local))
                        else check(db.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv) >= 0)
                        db.execSQL("INSERT OR REPLACE INTO backup_identities(entityTable,entityId,uuid) VALUES(?,?,?)", arrayOf(table, local, original.get("_uuid").asString))
                    }
                }
            }
            // Remove target-only owned rows after incoming rows exist. Reused IDs keep external references stable.
            listOf("messages", "memory_items", "timeline_nodes", "roleplay_sessions").forEach { table ->
                conversationIds.forEach { conversation ->
                    val keep = maps.getValue(table).values.toSet()
                    val stale = db.query("SELECT id FROM `$table` WHERE conversationId=?", arrayOf(conversation)).use { cursor -> buildList { while(cursor.moveToNext()) if (cursor.getLong(0) !in keep) add(cursor.getLong(0)) } }
                    stale.forEach { old ->
                        if (table == "messages") {
                            db.execSQL("UPDATE memory_items SET sourceMessageId=NULL WHERE sourceMessageId=?", arrayOf(old))
                            db.execSQL("UPDATE roleplay_memories SET sourceMessageId=NULL WHERE sourceMessageId=?", arrayOf(old))
                            db.execSQL("UPDATE conversations SET summaryUpdatedMessageId=NULL WHERE summaryUpdatedMessageId=?", arrayOf(old))
                            db.execSQL("DELETE FROM conversation_branches WHERE branchMessageId=?", arrayOf(old))
                        }
                        db.delete(table, "id=?", arrayOf(old))
                        db.execSQL("DELETE FROM backup_identities WHERE entityTable=? AND entityId=?", arrayOf(table, old))
                    }
                }
            }
            maps.getValue("roleplay_sessions").values.forEach { session ->
                val keep = maps.getValue("roleplay_memories").values.toSet()
                val stale = db.query("SELECT id FROM roleplay_memories WHERE sessionId=?", arrayOf(session)).use { c -> buildList { while(c.moveToNext()) if(c.getLong(0) !in keep) add(c.getLong(0)) } }
                stale.forEach { db.delete("roleplay_memories", "id=?", arrayOf(it)) }
            }
            maps.getValue("world_books").values.forEach { book ->
                val keep = maps.getValue("world_book_entries").values.toSet()
                val stale = db.query("SELECT id FROM world_book_entries WHERE bookId=?", arrayOf(book)).use { c -> buildList { while(c.moveToNext()) if(c.getLong(0) !in keep) add(c.getLong(0)) } }
                stale.forEach { db.delete("world_book_entries", "id=?", arrayOf(it)) }
            }
            if (snapshot.get("type")?.asString == "full_snapshot") {
                val keep = maps.getValue("conversation_branches").values.toSet()
                val stale = db.query("SELECT id,parentConversationId,childConversationId FROM conversation_branches").use { c ->
                    buildList { while(c.moveToNext()) if (c.getLong(0) !in keep && c.getLong(1) in conversationIds && c.getLong(2) in conversationIds) add(c.getLong(0)) }
                }
                stale.forEach { db.delete("conversation_branches", "id=?", arrayOf(it)) }
            }
            conversationIds.forEach { database.conversationDao().refreshStats(it) }
            db.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "恢复后的关联检查失败" } }
        }
        database.invalidationTracker.refreshAsync()
    }

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
