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
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val DB_NAME = "ai_assistant_database"

    /** A snapshot beyond this size cannot be imported on a phone even row by row. */
    private const val MAX_SNAPSHOT_BYTES = 512L * 1024 * 1024

    /** Restoring below this much free heap fails with a reason instead of dying with an OutOfMemoryError. */
    private const val MIN_RESTORE_HEAP_BYTES = 48L * 1024 * 1024

    /** Restore outcome carried to the UI; the message is shown instead of a generic failure. */
    sealed interface RestoreResult {
        /** [warning] describes references cleared or rows skipped by a tolerant retry, null when nothing was lost. */
        data class Success(val warning: String? = null) : RestoreResult
        data class Failure(val message: String) : RestoreResult
    }

    private fun getBackupDir(context: Context): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "Echo_Backups"
    ).apply { check(exists() || mkdirs()) }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())

    private fun availableHeap(): Long = Runtime.getRuntime().let {
        it.maxMemory() - (it.totalMemory() - it.freeMemory())
    }

    /** Pure pre-check so the guard itself is unit-testable; null means the import may proceed. */
    internal fun guardSnapshot(snapshotBytes: Long, availableHeap: Long): String? = when {
        snapshotBytes <= 0 -> "备份内容为空"
        snapshotBytes > MAX_SNAPSHOT_BYTES -> "备份过大（${snapshotBytes / (1024 * 1024)} MB），请改用单对话备份分批导入"
        availableHeap < MIN_RESTORE_HEAP_BYTES -> "可用内存不足（剩余约 ${availableHeap / (1024 * 1024)} MB），请关闭其他应用后重试"
        else -> null
    }

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
                        it.isFile && MediaHistoryBackup.isMediaFile(it.name)
                    }?.forEach { addFileToZip(zip, it, "files/${it.name}") }
                    zip.putNextEntry(ZipEntry("media_history.json"))
                    zip.write(MediaHistoryBackup.exportDeleted(context).toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
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
    fun restoreBackup(context: Context, backupPath: String): RestoreResult = try {
        val file = File(backupPath)
        when {
            !file.isFile -> RestoreResult.Failure("备份文件不存在")
            isJsonBackup(file) -> guardSnapshot(file.length(), availableHeap())?.let { RestoreResult.Failure(it) }
                ?: restoreSnapshotFile(context, file)
            else -> restoreZip(context, file)
        }
    } catch (e: OutOfMemoryError) {
        Log.e("BackupManager", "restoreBackup out of memory", e)
        RestoreResult.Failure("内存不足，备份过大；请改用单对话备份分批导入")
    } catch (e: Exception) {
        Log.e("BackupManager", "restoreBackup failed", e)
        RestoreResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: "导入失败，请确认文件格式有效")
    }

    private fun restoreZip(context: Context, file: File): RestoreResult {
        declaredSnapshotSize(file)?.let { size ->
            guardSnapshot(size, availableHeap())?.let { return RestoreResult.Failure(it) }
        }
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
            return when {
                snapshot.isFile -> restoreSnapshotFile(context, snapshot)
                legacy.isFile -> restoreLegacyDatabase(context, legacy)
                else -> temp.walkTopDown().firstOrNull { it.extension == "json" && it.name != "backup_info.json" }
                    ?.let { restoreSnapshotFile(context, it) } ?: RestoreResult.Failure("备份中未找到可导入数据")
            }
        } finally { temp.deleteRecursively() }
    }

    /** Uncompressed size recorded in the ZIP directory, read before anything is extracted. */
    private fun declaredSnapshotSize(file: File): Long? = try {
        ZipFile(file).use { zip -> zip.getEntry("snapshot.json")?.size?.takeIf { it > 0 } }
    } catch (e: Exception) { Log.e("BackupManager", "cannot read zip directory", e); null }

    /** Streams the snapshot from disk so a large backup never becomes one string or one JSON tree. */
    private fun restoreSnapshotFile(context: Context, file: File): RestoreResult = try {
        val report = runBlocking(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context)
            try {
                BackupGraphStore.restoreStream(database, openReader = { file.reader(Charsets.UTF_8) })
            } catch (e: IllegalStateException) {
                // Columns such as api_usage_stats.apiConfigId carry no foreign key, so a device can keep
                // references to rows deleted earlier. Retry once, clearing those links instead of failing.
                if (e.message?.startsWith("备份关联缺失") != true) throw e
                Log.w("BackupManager", "snapshot has dangling references, retrying tolerantly", e)
                BackupGraphStore.restoreStream(database, openReader = { file.reader(Charsets.UTF_8) }, tolerant = true)
            }
        }
        RestoreResult.Success(report.describe())
    } catch (e: OutOfMemoryError) {
        throw e
    } catch (e: Exception) {
        Log.e("BackupManager", "stream restore failed", e)
        RestoreResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: "导入失败，请确认文件格式有效")
    }

    private fun restoreLegacyDatabase(context: Context, legacy: File): RestoreResult = try {
        runBlocking(Dispatchers.IO) {
            SQLiteDatabase.openDatabase(legacy.path, null, SQLiteDatabase.OPEN_READWRITE).use { old ->
                old.rawQuery("PRAGMA integrity_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") }
                BackupGraphStore.restore(AppDatabase.getDatabase(context), BackupGraphStore.legacy(old))
            }
        }
        RestoreResult.Success()
    } catch (e: OutOfMemoryError) {
        throw e
    } catch (e: Exception) {
        Log.e("BackupManager", "legacy restore failed", e)
        RestoreResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: "旧版备份恢复失败")
    }

    fun restoreSingleConversationFromJson(context: Context, jsonString: String): Boolean = try {
        runBlocking(Dispatchers.IO) {
            val json = jsonString.trim().removePrefix("\uFEFF")
            val root = JsonParser.parseString(json).asJsonObject
            val snapshot = if (root.get("formatVersion")?.asInt == 2) root else BackupGraphStore.legacySingle(json)
            BackupGraphStore.restore(AppDatabase.getDatabase(context), snapshot)
            true
        }
    } catch (e: Exception) { Log.e("BackupManager", "JSON restore failed", e); false }

    fun restoreBackupFromUri(context: Context, uri: Uri): RestoreResult = try {
        val file = File(context.cacheDir, "import_${UUID.randomUUID()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: error("无法读取备份文件")
            restoreBackup(context, file.path)
        } finally { file.delete() }
    } catch (e: OutOfMemoryError) {
        Log.e("BackupManager", "URI restore out of memory", e)
        RestoreResult.Failure("内存不足，备份过大；请改用单对话备份分批导入")
    } catch (e: Exception) {
        Log.e("BackupManager", "URI restore failed", e)
        RestoreResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: "导入失败，请确认文件格式有效")
    }

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
