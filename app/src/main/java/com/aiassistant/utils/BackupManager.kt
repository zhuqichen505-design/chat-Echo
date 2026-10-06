package com.aiassistant.utils

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.annotation.Keep
import androidx.core.content.FileProvider
import com.aiassistant.BuildConfig
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.domain.model.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val DB_NAME = "ai_assistant_database"

    private fun getBackupDir(context: Context): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "Echo_Backups"
    ).apply { check(exists() || mkdirs()) }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())

    fun createBackup(context: Context): String? = try {
        runBlocking(Dispatchers.IO) {
            val file = File(getBackupDir(context), "Echo_Backup_${timestamp()}.zip")
            val pending = File(file.path + ".pending")
            try {
                ZipOutputStream(pending.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("snapshot.json"))
                    BackupGraphStore.writeFullSnapshot(AppDatabase.getDatabase(context), zip.writer(Charsets.UTF_8).buffered())
                    zip.closeEntry()
                    context.filesDir.listFiles()?.filter {
                        it.isFile && (it.name in setOf("user_avatar.dat", "model_avatar.dat") ||
                            (it.name.startsWith("model_avatar_api_") && it.name.endsWith(".dat")))
                    }?.forEach { addFileToZip(zip, it, "files/${it.name}") }
                    val prefs = File(context.applicationInfo.dataDir, "shared_prefs/personalization_settings.xml")
                    if (prefs.exists()) addFileToZip(zip, prefs, "shared_prefs/personalization_settings.xml")
                    zip.putNextEntry(ZipEntry("backup_info.json"))
                    zip.write(Gson().toJson(BackupInfo(2, System.currentTimeMillis(), BuildConfig.VERSION_NAME,
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")).toByteArray())
                    zip.closeEntry()
                }
                check(pending.renameTo(file)) { "备份文件保存失败" }
                file.absolutePath
            } finally { pending.delete() }
        }
    } catch (e: Exception) {
        Log.e("BackupManager", "createBackup failed", e)
        null
    }

    fun exportBackupToUri(context: Context, uri: Uri): Boolean = try {
        val file = createBackup(context)
        file != null && context.contentResolver.openOutputStream(uri)?.use { out ->
            File(file).inputStream().use { it.copyTo(out) }; true
        } == true
    } catch (e: Exception) { Log.e("BackupManager", "export failed", e); false }

    fun isJsonBackup(file: File): Boolean = try {
        file.exists() && (file.extension.equals("json", true) || file.reader().use {
            val buffer = CharArray(256)
            val size = it.read(buffer)
            size > 0 && String(buffer, 0, size).trim().removePrefix("\uFEFF").startsWith("{")
        })
    } catch (_: Exception) { false }

    fun createSingleConversationBackup(context: Context, conversationId: Long): String? = try {
        runBlocking(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context)
            val conversation = database.conversationDao().getConversationById(conversationId)
                ?: return@runBlocking null
            val snapshot = BackupGraphStore.export(database, conversationId)
            val title = conversation.title.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(30).ifBlank { "对话" }
            val file = File(getBackupDir(context), "Echo_Backup_Conv_${title}_${timestamp()}.json")
            val pending = File(file.path + ".pending")
            try {
                pending.writeText(snapshot.toString(), Charsets.UTF_8)
                check(pending.renameTo(file))
                file.absolutePath
            } finally { pending.delete() }
        }
    } catch (e: Exception) { Log.e("BackupManager", "single export failed", e); null }

    /** Only conversations present in the complete snapshot are replaced. */
    fun restoreBackup(context: Context, backupPath: String): Boolean = try {
        val file = File(backupPath)
        if (isJsonBackup(file)) restoreSingleConversationFromJson(context, file.readText(Charsets.UTF_8))
        else {
            val temp = File(context.cacheDir, "restore_${UUID.randomUUID()}").apply { check(mkdirs()) }
            try {
                ZipInputStream(file.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val target = File(temp, entry.name)
                        require(target.canonicalPath.startsWith(temp.canonicalPath + File.separator)) { "无效备份路径" }
                        if (!entry.isDirectory) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                        entry = zip.nextEntry
                    }
                }
                val snapshot = File(temp, "snapshot.json")
                val legacy = File(temp, "database/$DB_NAME")
                val success = when {
                    snapshot.exists() -> restoreSingleConversationFromJson(context, snapshot.readText(Charsets.UTF_8))
                    legacy.exists() -> runBlocking(Dispatchers.IO) {
                        SQLiteDatabase.openDatabase(legacy.path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
                            old.rawQuery("PRAGMA integrity_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") }
                            BackupGraphStore.restore(AppDatabase.getDatabase(context), BackupGraphStore.legacy(old))
                        }
                        true
                    }
                    else -> temp.walkTopDown().firstOrNull { it.extension == "json" && it.name != "backup_info.json" }
                        ?.let { restoreSingleConversationFromJson(context, it.readText(Charsets.UTF_8)) } ?: false
                }
                if (success) {
                    File(temp, "files").listFiles()?.filter { it.isFile }?.forEach { source ->
                        source.copyTo(File(context.filesDir, source.name), overwrite = true)
                    }
                    val prefs = File(temp, "shared_prefs/personalization_settings.xml")
                    if (prefs.exists()) {
                        val target = File(context.applicationInfo.dataDir, "shared_prefs/personalization_settings.xml")
                        target.parentFile?.mkdirs()
                        prefs.copyTo(target, overwrite = true)
                    }
                }
                success
            } finally { temp.deleteRecursively() }
        }
    } catch (e: Exception) { Log.e("BackupManager", "restoreBackup failed", e); false }

    fun restoreSingleConversationFromJson(context: Context, jsonString: String): Boolean = try {
        runBlocking(Dispatchers.IO) {
            val json = jsonString.trim().removePrefix("\uFEFF")
            val root = JsonParser.parseString(json).asJsonObject
            val snapshot = if (root.get("formatVersion")?.asInt == 2) root else BackupGraphStore.legacySingle(json)
            BackupGraphStore.restore(AppDatabase.getDatabase(context), snapshot)
            true
        }
    } catch (e: Exception) { Log.e("BackupManager", "JSON restore failed", e); false }

    fun restoreBackupFromUri(context: Context, uri: Uri): Boolean = try {
        val file = File(context.cacheDir, "import_${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: error("无法读取备份")
            restoreBackup(context, file.path)
        } finally { file.delete() }
    } catch (e: Exception) { Log.e("BackupManager", "URI restore failed", e); false }

    fun getBackupList(context: Context): List<BackupItem> = getBackupDir(context).listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in setOf("zip", "json") }
        ?.map { BackupItem(it.name, it.absolutePath, it.length(), it.lastModified()) }
        ?.sortedByDescending { it.lastModified }.orEmpty()

    fun deleteBackup(backupPath: String): Boolean = try { File(backupPath).delete() } catch (_: Exception) { false }

    fun shareBackup(context: Context, backupPath: String) {
        try {
            val file = File(backupPath)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension.equals("json", true)) "application/json" else "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享备份文件"))
        } catch (e: Exception) { Log.e("BackupManager", "share failed", e) }
    }

    fun autoBackup(context: Context) {
        try {
            val backups = getBackupList(context).filter { it.fileName.endsWith(".zip") }
            val day = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
            if (backups.firstOrNull()?.let { day.format(Date(it.lastModified)) } != day.format(Date()) && createBackup(context) != null) {
                backups.drop(4).forEach { deleteBackup(it.filePath) }
            }
        } catch (e: Exception) { Log.e("BackupManager", "autoBackup failed", e) }
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    @Keep
    data class SingleConversationExport(
        @SerializedName("formatVersion") val formatVersion: Int = 1,
        @SerializedName("type") val type: String = "single_conversation",
        @SerializedName("exportedAt") val exportedAt: Long = System.currentTimeMillis(),
        @SerializedName("appVersion") val appVersion: String = BuildConfig.VERSION_NAME,
        @SerializedName("conversation") val conversation: Conversation? = null,
        @SerializedName("messages") val messages: List<Message>? = emptyList(),
        @SerializedName("roleplaySession") val roleplaySession: RoleplaySession? = null,
        @SerializedName("characterProfile") val characterProfile: CharacterProfile? = null,
        @SerializedName("roleplayScenario") val roleplayScenario: RoleplayScenario? = null,
        @SerializedName("roleplayMemories") val roleplayMemories: List<RoleplayMemory>? = emptyList(),
        @SerializedName("timelineNodes") val timelineNodes: List<TimelineNode>? = emptyList(),
        @SerializedName("sessionMemories", alternate = ["sessionSettings", "conversationMemories", "conversationSettings"])
        val sessionMemories: List<MemoryItem>? = emptyList()
    )

    @Keep
    data class BackupInfo(
        @SerializedName("version") val version: Int,
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("appVersion") val appVersion: String,
        @SerializedName("deviceInfo") val deviceInfo: String,
        @SerializedName("includeRoleplayData") val includeRoleplayData: Boolean = true,
        @SerializedName("roleplayCharacterCount") val roleplayCharacterCount: Int = 0,
        @SerializedName("roleplayScenarioCount") val roleplayScenarioCount: Int = 0,
        @SerializedName("roleplaySessionCount") val roleplaySessionCount: Int = 0
    )

    @Keep
    data class BackupItem(val fileName: String, val filePath: String, val fileSize: Long, val lastModified: Long)
}
