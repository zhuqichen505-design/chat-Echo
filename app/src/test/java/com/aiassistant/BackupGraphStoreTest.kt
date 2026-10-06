package com.aiassistant

import android.app.Application
import androidx.room.Room
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.local.migrations.AppDatabaseMigrations
import com.aiassistant.domain.model.*
import com.aiassistant.utils.BackupGraphStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupGraphStoreTest {
    @Test fun streamingZipFlushesSnapshotAndLeavesStreamOpenForFollowingEntries() = runBlocking {
        database().use { source -> database().use { target ->
            val id = seed(source, "ZIP round trip")
            val text = "小说正文😀\n".repeat(4096)
            source.messageDao().insertMessage(Message(conversationId = id, role = "assistant", content = text))
            val bytes = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("snapshot.json"))
                BackupGraphStore.writeFullSnapshot(source, zip.writer(Charsets.UTF_8).buffered(), { it })
                zip.closeEntry()
                zip.putNextEntry(java.util.zip.ZipEntry("backup_info.json"))
                zip.write("{\"version\":2}".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            java.util.zip.ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
                assertEquals("snapshot.json", zip.nextEntry.name)
                val snapshot = com.google.gson.JsonParser.parseString(zip.readBytes().toString(Charsets.UTF_8)).asJsonObject
                BackupGraphStore.restore(target, snapshot, { it }, {})
                assertEquals(text, target.messageDao().getMessagesList(id).single().content)
                assertEquals("backup_info.json", zip.nextEntry.name)
                assertEquals("{\"version\":2}", zip.readBytes().toString(Charsets.UTF_8))
                assertNull(zip.nextEntry)
            }
        } }
    }

    @Test fun streamingSnapshotPreservesFormatIdentitiesAndRestores() = runBlocking {
        database().use { source -> database().use { target ->
            val id = seed(source, "streamed")
            source.messageDao().insertMessage(Message(conversationId = id, role = "user", content = "引号\"换行\n与emoji😀"))
            val output = java.io.StringWriter()
            BackupGraphStore.writeFullSnapshot(source, output) { "decoded:$it" }
            val streamed = com.google.gson.JsonParser.parseString(output.toString()).asJsonObject
            val previous = BackupGraphStore.export(source, decryptKey = { "decoded:$it" })
            streamed.remove("exportedAt")
            previous.remove("exportedAt")
            assertEquals(previous, streamed)
            repeat(2) { BackupGraphStore.restore(target, streamed, { it }, {}) }
            assertEquals("引号\"换行\n与emoji😀", target.messageDao().getMessagesList(id).single().content)
            assertEquals("decoded:test", target.apiConfigDao().getConfigById(1)!!.apiKey)
        } }
    }

    @Test fun largeSnapshotUsesBoundedWritesInsteadOfOneWholeDatabaseString() = runBlocking {
        database().use { db ->
            val id = seed(db, "large history")
            val body = "x".repeat(128 * 1024)
            repeat(256) { db.messageDao().insertMessage(Message(conversationId = id, role = "user", content = body)) }
            var written = 0L
            val sink = object : java.io.Writer() {
                override fun write(buffer: CharArray, offset: Int, length: Int) {
                    assertTrue("whole snapshot was materialized", length <= 256 * 1024)
                    written += length
                }
                override fun flush() = Unit
                override fun close() = Unit
            }
            BackupGraphStore.writeFullSnapshot(db, sink, { it })
            assertTrue(written > 32L * 1024 * 1024)
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM backup_identities WHERE entityTable='messages'").use {
                it.moveToFirst(); assertEquals(256, it.getInt(0))
            }
        }
    }

    @Test fun failedStreamingFlushRollsBackNewIdentitiesAndKeepsMessages() = runBlocking {
        database().use { db ->
            val id = seed(db, "retained")
            db.messageDao().insertMessage(Message(conversationId = id, role = "user", content = "retained"))
            val sink = object : java.io.StringWriter() {
                override fun flush() { throw java.io.IOException("disk full") }
            }
            try { BackupGraphStore.writeFullSnapshot(db, sink, { it }); fail("write must fail") }
            catch (_: java.io.IOException) { }
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM backup_identities").use {
                it.moveToFirst(); assertEquals(0, it.getInt(0))
            }
            assertEquals("retained", db.messageDao().getMessagesList(id).single().content)
        }
    }

    private inline fun <T> AppDatabase.use(block: (AppDatabase) -> T): T = try { block(this) } finally { close() }
    private fun database() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries().build()

    private suspend fun seed(db: AppDatabase, title: String): Long {
        val api = db.apiConfigDao().insertConfig(ApiConfig(name = "Test", provider = "openai", baseUrl = "https://example.invalid", apiKey = "test", modelName = "gpt-5.2"))
        return db.conversationDao().insertConversation(Conversation(title = title, apiConfigId = api, modelName = "gpt-5.2"))
    }

    @Test fun roundTripReplacesEditsAndDeletesKeepsIdentityAndUnrelatedConversation() = runBlocking {
        database().use { a -> database().use { b ->
            val aId = seed(a, "小说交流")
            val first = a.messageDao().insertMessage(Message(conversationId = aId, role = "user", content = "original", createdAt = 100))
            val removed = a.messageDao().insertMessage(Message(conversationId = aId, role = "assistant", content = "remove", createdAt = 200))
            val unrelated = seed(b, "小说交流")
            val initial = BackupGraphStore.export(a, aId, { it })
            BackupGraphStore.restore(b, initial, { it }, {})
            val bId = b.openHelper.readableDatabase.query("SELECT id FROM conversations WHERE id != $unrelated").use { assertTrue(it.moveToFirst()); it.getLong(0) }
            assertNotEquals(aId, bId)
            val imported = b.messageDao().getMessagesList(bId)
            b.messageDao().updateMessage(imported.first().copy(content = "edited"))
            b.openHelper.writableDatabase.execSQL("DELETE FROM messages WHERE id=?", arrayOf(imported.last().id))
            b.messageDao().insertMessage(Message(conversationId = bId, role = "assistant", content = "continued on B", createdAt = 300))
            val returned = BackupGraphStore.export(b, bId, { it })
            repeat(2) { BackupGraphStore.restore(a, returned, { it }, {}) }
            val messages = a.messageDao().getMessagesList(aId)
            assertEquals(listOf("edited", "continued on B"), messages.map { it.content })
            assertEquals(first, messages.first().id)
            assertFalse(messages.any { it.id == removed })
            assertEquals(2, a.conversationDao().getConversationById(aId)!!.messageCount)
            a.messageDao().insertMessage(Message(conversationId = aId, role = "user", content = "continued on A", createdAt = 400))
            BackupGraphStore.restore(b, BackupGraphStore.export(a, aId, { it }), { it }, {})
            assertEquals(3, b.messageDao().getMessagesList(bId).size)
            assertTrue(b.messageDao().getMessagesList(unrelated).isEmpty())
            assertNotNull(b.conversationDao().getConversationById(unrelated))
        } }
    }

    @Test fun invalidForeignReferenceRollsBackEntireImport() = runBlocking {
        database().use { a -> database().use { b ->
            val id = seed(a, "source")
            val target = seed(b, "target")
            val snapshot = BackupGraphStore.export(a, id, { it })
            snapshot.getAsJsonObject("tables").getAsJsonArray("conversations")[0].asJsonObject.addProperty("folderId", 999)
            try { BackupGraphStore.restore(b, snapshot, { it }, {}); fail("invalid reference accepted") } catch (_: IllegalStateException) { }
            b.openHelper.readableDatabase.query("SELECT COUNT(*) FROM conversations").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            assertEquals("target", b.conversationDao().getConversationById(target)!!.title)
        } }
    }

    @Test fun migrationCreatesPortableIdentityWithoutChangingMessages() = runBlocking {
        database().use { db ->
            val id = seed(db, "existing")
            db.messageDao().insertMessage(Message(conversationId = id, role = "user", content = "preserved"))
            val sql = db.openHelper.writableDatabase
            sql.execSQL("DROP TABLE backup_identities")
            AppDatabaseMigrations.MIGRATION_32_33.migrate(sql)
            sql.execSQL("INSERT INTO backup_identities VALUES('conversations', ?, 'portable')", arrayOf(id))
            assertEquals("preserved", db.messageDao().getMessagesList(id).single().content)
            sql.query("PRAGMA table_info(backup_identities)").use { assertEquals(3, it.count) }
            try { sql.execSQL("INSERT INTO backup_identities VALUES('messages', 99, 'portable')"); fail("uuid must be unique") }
            catch (_: android.database.sqlite.SQLiteConstraintException) { }
        }
    }

    @Test fun fullSnapshotRemapsWorldBooksTagsSummaryAndBranches() = runBlocking {
        database().use { a -> database().use { b ->
            val parent = seed(a, "parent")
            val child = a.conversationDao().insertConversation(Conversation(title = "child", apiConfigId = 1, modelName = "gpt-5.2"))
            val message = a.messageDao().insertMessage(Message(conversationId = parent, role = "user", content = "branch point"))
            val sql = a.openHelper.writableDatabase
            // Use Room's real schema defaults by inserting model data via generated DAOs.
            val book = a.worldBookDao().insertBook(WorldBook(name = "source book"))
            a.worldBookDao().insertEntry(WorldBookEntry(bookId = book, name = "entry", keys = "lore", content = "lore"))
            val character = a.characterProfileDao().insertCharacter(CharacterProfile(name = "hero"))
            val session = a.roleplaySessionDao().insertSession(RoleplaySession(conversationId = parent, characterId = character, characterIds = "[$character]", activeWorldBookIds = "$book"))
            a.roleplayMemoryDao().insertMemory(RoleplayMemory(sessionId = session, content = "memory", sourceMessageId = message))
            sql.execSQL("UPDATE conversations SET activeWorldBookIds=?, summaryUpdatedMessageId=? WHERE id=?", arrayOf("$book", message, parent))
            sql.execSQL("INSERT INTO character_tags(id,name,createdAt) VALUES(1,'shared tag',0)")
            sql.execSQL("INSERT INTO character_tag_cross_ref VALUES(?,1)", arrayOf(character))
            sql.execSQL("INSERT INTO conversation_branches(parentConversationId,childConversationId,branchMessageId,createdAt) VALUES(?,?,?,0)", arrayOf(parent, child, message))
            seed(b, "unrelated")
            b.worldBookDao().insertBook(WorldBook(name = "unrelated book"))
            b.characterProfileDao().insertCharacter(CharacterProfile(name = "unrelated hero"))
            b.messageDao().insertMessage(Message(conversationId = 1, role = "user", content = "unrelated message"))
            b.openHelper.writableDatabase.execSQL("INSERT INTO character_tags(id,name,createdAt) VALUES(1,'shared tag',0)")
            val snapshot = BackupGraphStore.export(a, null, { it })
            repeat(2) { BackupGraphStore.restore(b, snapshot, { it }, {}) }
            val target = b.openHelper.readableDatabase
            target.query("SELECT e.content,b.name FROM world_book_entries e JOIN world_books b ON b.id=e.bookId").use {
                assertTrue(it.moveToFirst()); assertEquals("lore", it.getString(0)); assertEquals("source book", it.getString(1)); assertFalse(it.moveToNext())
            }
            target.query("SELECT m.content,c.title FROM conversations c JOIN messages m ON m.id=c.summaryUpdatedMessageId WHERE c.title='parent'").use {
                assertTrue(it.moveToFirst()); assertEquals("branch point", it.getString(0))
            }
            target.query("SELECT c.name,t.name FROM character_tag_cross_ref r JOIN character_profiles c ON c.id=r.characterId JOIN character_tags t ON t.id=r.tagId").use {
                assertTrue(it.moveToFirst()); assertEquals("hero", it.getString(0)); assertEquals("shared tag", it.getString(1)); assertFalse(it.moveToNext())
            }
            target.query("SELECT m.content,c.title FROM conversation_branches b JOIN messages m ON b.branchMessageId=m.id JOIN conversations c ON b.childConversationId=c.id").use {
                assertTrue(it.moveToFirst()); assertEquals("branch point", it.getString(0)); assertEquals("child", it.getString(1)); assertFalse(it.moveToNext())
            }
            target.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } }
    }

    @Test fun roomOpensVersion32WithMigrationAndValidatesSchema() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-${java.util.UUID.randomUUID()}.db"
        val initial = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val id = seed(initial, "before migration")
        initial.messageDao().insertMessage(Message(conversationId = id, role = "user", content = "retained"))
        initial.close()
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use { sql ->
            // v32 has precisely the same 19 application tables; v33 only adds backup_identities.
            sql.execSQL("DROP TABLE backup_identities")
            sql.version = 32
        }
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabaseMigrations.MIGRATION_32_33, AppDatabaseMigrations.MIGRATION_33_34).allowMainThreadQueries().build()
        try {
            assertEquals("retained", upgraded.messageDao().getMessagesList(id).single().content)
            assertEquals(34, upgraded.openHelper.readableDatabase.version)
            upgraded.openHelper.readableDatabase.query("SELECT COUNT(*) FROM backup_identities").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }

    @Test fun replyDirectionMigrationPreservesOldRowsAndRoomValidatesRealV33Schema() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "directions-${java.util.UUID.randomUUID()}.db"
        val oldSchema = com.google.gson.JsonParser.parseString(java.io.File("schemas/com.aiassistant.data.local.AppDatabase/33.json").readText()).asJsonObject.getAsJsonObject("database")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { sql ->
            val setup = oldSchema.getAsJsonArray("setupQueries")
            oldSchema.getAsJsonArray("entities").forEach { entity ->
                val obj = entity.asJsonObject
                val table = obj.get("tableName").asString
                sql.execSQL(obj.get("createSql").asString.replace("\${TABLE_NAME}", table))
                obj.getAsJsonArray("indices")?.forEach { index -> sql.execSQL(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table)) }
            }
            setup.forEach { sql.execSQL(it.asString) }
            fun insert(table: String, model: Any) {
                val entity = oldSchema.getAsJsonArray("entities").first { it.asJsonObject.get("tableName").asString == table }.asJsonObject
                val values = com.google.gson.Gson().toJsonTree(model).asJsonObject
                val cv = android.content.ContentValues()
                entity.getAsJsonArray("fields").forEach { field ->
                    val column = field.asJsonObject.get("columnName").asString
                    val value = values.get(column)
                    when {
                        value == null || value.isJsonNull -> cv.putNull(column)
                        value.asJsonPrimitive.isBoolean -> cv.put(column, if (value.asBoolean) 1 else 0)
                        else -> cv.put(column, value.asString)
                    }
                }
                sql.insertOrThrow(table, null, cv)
            }
            insert("api_configs", ApiConfig(id = 1, name = "old", provider = "proxy", baseUrl = "https://example.invalid", apiKey = "test", modelName = "exact"))
            insert("conversations", Conversation(id = 1, title = "retained", apiConfigId = 1, modelName = "exact"))
            insert("messages", Message(id = 1, conversationId = 1, role = "user", content = "old message"))
            sql.version = 33
        }
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabaseMigrations.MIGRATION_33_34).allowMainThreadQueries().build()
        try {
            val conv = upgraded.conversationDao().getConversationById(1)!!
            assertEquals("retained", conv.title); assertFalse(conv.enableReplyDirections); assertEquals(2, conv.replyDirectionCount)
            assertEquals(34, upgraded.openHelper.readableDatabase.version)
            assertEquals("old message", upgraded.messageDao().getMessagesList(1).single().content)
            assertNull(upgraded.messageDao().getMessagesList(1).single().replyDirection)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }

    @Test fun replyDirectionSettingsAndChosenRoundMetadataSurvivePortableBackup() = runBlocking {
        database().use { source -> database().use { target ->
            val id = seed(source, "directions")
            val conv = source.conversationDao().getConversationById(id)!!
            source.conversationDao().updateConversation(conv.copy(enableReplyDirections = true, replyDirectionCount = 4))
            source.messageDao().insertMessage(Message(conversationId = id, role = "assistant", content = "real reply", replyDirection = "one-round direction"))
            BackupGraphStore.restore(target, BackupGraphStore.export(source, id, { it }), { it }, {})
            val restored = target.openHelper.readableDatabase
            restored.query("SELECT enableReplyDirections,replyDirectionCount FROM conversations WHERE title='directions'").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)); assertEquals(4, it.getInt(1))
            }
            restored.query("SELECT content,replyDirection FROM messages WHERE role='assistant'").use {
                assertTrue(it.moveToFirst()); assertEquals("real reply", it.getString(0)); assertEquals("one-round direction", it.getString(1)); assertFalse(it.moveToNext())
            }
        } }
    }

    @Test fun legacySingleJsonImportsIdempotentlyWithoutOverwritingSameLocalId() = runBlocking {
        database().use { target ->
            val unrelated = seed(target, "小说交流")
            val old = com.aiassistant.utils.BackupManager.SingleConversationExport(
                conversation = Conversation(id = unrelated, title = "小说交流", apiConfigId = 999, modelName = "old-model"),
                messages = listOf(Message(id = 42, conversationId = unrelated, role = "user", content = "legacy text", createdAt = 100))
            )
            val json = com.google.gson.Gson().toJson(old)
            repeat(2) { BackupGraphStore.restore(target, BackupGraphStore.legacySingle(json), { it }, {}) }
            assertTrue(target.messageDao().getMessagesList(unrelated).isEmpty())
            target.openHelper.readableDatabase.query("SELECT m.content,c.apiConfigId FROM messages m JOIN conversations c ON c.id=m.conversationId").use {
                assertTrue(it.moveToFirst()); assertEquals("legacy text", it.getString(0)); assertEquals(1L, it.getLong(1)); assertFalse(it.moveToNext())
            }
        }
    }
}
