package com.aiassistant.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.MessageDigest

object BackgroundImageManager {
    private const val HOME_BACKGROUND_FILE = "home_background.jpg"
    private const val CHAT_BACKGROUND_FILE = "chat_background.jpg"
    private const val MAX_BACKGROUND_SIDE = 2160
    private const val HISTORY_PREFIX = "background_history_"
    private const val HISTORY_SUFFIX = ".img"
    var revision by mutableIntStateOf(0)
        private set

    data class BackgroundHistoryEntry(
        val id: String,
        val updatedAt: Long
    )

    data class SolidColorPreset(
        val id: String,
        val name: String,
        val lightColorInt: Int,
        val darkColorInt: Int,
        val displayHex: String
    )

    // A preset is the selected color, not a bitmap whose hue silently changes with dark mode.
    // Dark options have their own visible swatches and remain available in either mode.
    val PRESET_SOLID_COLORS = listOf(
        SolidColorPreset("pale_green", "浅艾绿", 0xFFF1F8F4.toInt(), 0xFFF1F8F4.toInt(), "#F1F8F4"),
        SolidColorPreset("pale_blue", "浅湖蓝", 0xFFF0F5FA.toInt(), 0xFFF0F5FA.toInt(), "#F0F5FA"),
        SolidColorPreset("pale_purple", "浅薰紫", 0xFFF6F3F9.toInt(), 0xFFF6F3F9.toInt(), "#F6F3F9"),
        SolidColorPreset("pale_pink", "浅樱粉", 0xFFFAF2F4.toInt(), 0xFFFAF2F4.toInt(), "#FAF2F4"),
        SolidColorPreset("pale_yellow", "浅暖杏", 0xFFFAF8F0.toInt(), 0xFFFAF8F0.toInt(), "#FAF8F0"),
        SolidColorPreset("pale_cyan", "浅山岚", 0xFFF0F7F7.toInt(), 0xFFF0F7F7.toInt(), "#F0F7F7"),
        SolidColorPreset("night_blue", "深海蓝", 0xFF24374D.toInt(), 0xFF24374D.toInt(), "#24374D"),
        SolidColorPreset("forest", "夜森林", 0xFF263E35.toInt(), 0xFF263E35.toInt(), "#263E35"),
        SolidColorPreset("plum", "暮烟紫", 0xFF40334A.toInt(), 0xFF40334A.toInt(), "#40334A"),
        SolidColorPreset("cocoa", "暖可可", 0xFF44372F.toInt(), 0xFF44372F.toInt(), "#44372F"),
        SolidColorPreset("graphite", "石墨灰", 0xFF343B42.toInt(), 0xFF343B42.toInt(), "#343B42"),
        SolidColorPreset("deep_teal", "深青绿", 0xFF214148.toInt(), 0xFF214148.toInt(), "#214148"),
        SolidColorPreset("pure_white", "纯白", 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), "#FFFFFF"),
        SolidColorPreset("pure_black", "纯黑", 0xFF000000.toInt(), 0xFF000000.toInt(), "#000000")
    )

    /** The active theme paints its own solid backdrop; custom images stay available in history. */
    fun applyThemeBackground(context: Context): Boolean {
        return runCatching {
            listOf(HOME_BACKGROUND_FILE, CHAT_BACKGROUND_FILE).forEach { fileName ->
                val current = backgroundFile(context, fileName)
                if (current.isFile && current.length() > 0) {
                    check(recordBackgroundHistory(context, current))
                    check(current.delete())
                }
            }
            synchronized(backgroundBitmapCache) { backgroundBitmapCache.clear() }
            revision++
            true
        }.getOrDefault(false)
    }

    fun saveHomeBackgroundFromUri(context: Context, uri: Uri): Boolean {
        return saveBackgroundFromUri(context, uri, HOME_BACKGROUND_FILE)
    }

    fun saveHomeBackgroundBitmap(context: Context, bitmap: Bitmap): Boolean {
        return saveBackgroundBitmap(context, bitmap, HOME_BACKGROUND_FILE)
    }

    fun saveChatBackgroundFromUri(context: Context, uri: Uri): Boolean {
        return saveBackgroundFromUri(context, uri, CHAT_BACKGROUND_FILE)
    }

    fun saveChatBackgroundBitmap(context: Context, bitmap: Bitmap): Boolean {
        return saveBackgroundBitmap(context, bitmap, CHAT_BACKGROUND_FILE)
    }

    private fun saveBackgroundBitmap(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            val target = backgroundFile(context, fileName)
            check(recordBackgroundHistory(context, target))
            atomicWrite(target) { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) }
            check(recordBackgroundHistory(context, target, explicitlySelected = true))
            synchronized(backgroundBitmapCache) { backgroundBitmapCache.remove(fileName) }
            revision++
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun saveHomeBackgroundSolidColor(context: Context, colorInt: Int): Boolean {
        return saveSolidColor(context, HOME_BACKGROUND_FILE, colorInt, recordHistory = true)
    }

    fun saveChatBackgroundSolidColor(context: Context, colorInt: Int): Boolean {
        return saveSolidColor(context, CHAT_BACKGROUND_FILE, colorInt, recordHistory = true)
    }

    fun getHomeBackgroundBitmap(context: Context): Bitmap? {
        return getBackgroundBitmap(context, HOME_BACKGROUND_FILE)
    }

    fun getChatBackgroundBitmap(context: Context): Bitmap? {
        return getBackgroundBitmap(context, CHAT_BACKGROUND_FILE)
    }

    fun hasHomeBackground(context: Context): Boolean {
        return backgroundFile(context, HOME_BACKGROUND_FILE).length() > 0
    }

    fun hasChatBackground(context: Context): Boolean {
        return backgroundFile(context, CHAT_BACKGROUND_FILE).length() > 0
    }

    fun deleteHomeBackground(context: Context) {
        val file = backgroundFile(context, HOME_BACKGROUND_FILE)
        if (recordBackgroundHistory(context, file)) file.delete()
        synchronized(backgroundBitmapCache) { backgroundBitmapCache.remove(HOME_BACKGROUND_FILE) }
        revision++
    }

    fun deleteChatBackground(context: Context) {
        val file = backgroundFile(context, CHAT_BACKGROUND_FILE)
        if (recordBackgroundHistory(context, file)) file.delete()
        synchronized(backgroundBitmapCache) { backgroundBitmapCache.remove(CHAT_BACKGROUND_FILE) }
        revision++
    }

    private fun saveSolidColor(
        context: Context,
        fileName: String,
        colorInt: Int,
        recordHistory: Boolean
    ): Boolean {
        return try {
            val bitmap = Bitmap.createBitmap(120, 240, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(colorInt)
            val target = backgroundFile(context, fileName)
            check(recordBackgroundHistory(context, target))
            atomicWrite(target) { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            if (recordHistory) check(recordBackgroundHistory(context, target, explicitlySelected = true))
            bitmap.recycle()
            synchronized(backgroundBitmapCache) { backgroundBitmapCache.remove(fileName) }
            revision++
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveBackgroundFromUri(context: Context, uri: Uri, fileName: String): Boolean {
        return try {
            val bitmap = decodeScaledBitmap(context, uri) ?: return false
            val target = backgroundFile(context, fileName)
            check(recordBackgroundHistory(context, target))
            atomicWrite(target) { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) }
            check(recordBackgroundHistory(context, target, explicitlySelected = true))
            bitmap.recycle()
            synchronized(backgroundBitmapCache) { backgroundBitmapCache.remove(fileName) }
            revision++
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun listBackgroundHistory(context: Context): List<BackgroundHistoryEntry> {
        listOf(HOME_BACKGROUND_FILE, CHAT_BACKGROUND_FILE).forEach { recordBackgroundHistory(context, backgroundFile(context, it)) }
        return historyFiles(context).map { BackgroundHistoryEntry(it.name, it.lastModified()) }
    }

    fun getBackgroundHistoryBitmap(context: Context, entry: BackgroundHistoryEntry): Bitmap? {
        val file = historyFile(context, entry.id) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 320) sample *= 2
            inSampleSize = sample
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    fun restoreHomeBackground(context: Context, entry: BackgroundHistoryEntry): Boolean =
        restoreBackground(context, entry, HOME_BACKGROUND_FILE)

    fun restoreChatBackground(context: Context, entry: BackgroundHistoryEntry): Boolean =
        restoreBackground(context, entry, CHAT_BACKGROUND_FILE)

    private fun restoreBackground(context: Context, entry: BackgroundHistoryEntry, targetName: String): Boolean {
        val source = historyFile(context, entry.id) ?: return false
        return runCatching {
            check(recordBackgroundHistory(context, backgroundFile(context, targetName)))
            atomicWrite(backgroundFile(context, targetName)) { output -> source.inputStream().use { it.copyTo(output) } }
            synchronized(backgroundBitmapCache) { backgroundBitmapCache.remove(targetName) }
            revision++
            true
        }.getOrDefault(false)
    }

    /** Removes the history copy, leaving any active home/chat image untouched. */
    fun deleteBackgroundHistory(context: Context, entry: BackgroundHistoryEntry): Boolean {
        val file = historyFile(context, entry.id) ?: return false
        return runCatching {
            val prefs = context.getSharedPreferences("background_history", Context.MODE_PRIVATE)
            if (!prefs.edit().putBoolean(entry.id, true).commit()) return@runCatching false
            AtomicFile(file).delete()
            if (file.exists()) {
                prefs.edit().remove(entry.id).commit()
                false
            } else true
        }.getOrDefault(false)
    }

    private fun recordBackgroundHistory(context: Context, source: File, explicitlySelected: Boolean = false): Boolean {
        if (!source.isFile || source.length() <= 0L) return true
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var count = input.read(buffer)
                while (count > 0) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val history = File(context.filesDir, "$HISTORY_PREFIX$hash$HISTORY_SUFFIX")
            val prefs = context.getSharedPreferences("background_history", Context.MODE_PRIVATE)
            if (prefs.getBoolean(history.name, false)) {
                if (!explicitlySelected) return@runCatching true
                check(prefs.edit().remove(history.name).commit())
            }
            if (history.isFile && history.length() == source.length()) return@runCatching true
            atomicWrite(history) { output -> source.inputStream().use { it.copyTo(output) } }
            true
        }.getOrDefault(false)
    }

    private fun atomicWrite(file: File, write: (java.io.OutputStream) -> Unit) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            write(stream)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun historyFile(context: Context, id: String): File? {
        if (!id.matches(Regex("background_history_[a-f0-9]{64}\\.img"))) return null
        return File(context.filesDir, id).takeIf { it.isFile }
    }

    private fun historyFiles(context: Context): List<File> = context.filesDir.listFiles()
        ?.filter { it.isFile && it.name.matches(Regex("background_history_[a-f0-9]{64}\\.img")) &&
            !context.getSharedPreferences("background_history", Context.MODE_PRIVATE).getBoolean(it.name, false) }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

    // v2.7.3 流畅度：背景图位图缓存（key=文件名，lastModified 校验失效）——聊天/首页/历史/
    // 统计/设置/文件夹六个页面原先各自在组合期同步解码同一张大背景图（主线程几十至几百 ms +
    // 约 18MB ARGB 峰值，每次进入页面重复发生）；缓存后全应用仅解码一次，保存/删除自动失效
    private val backgroundBitmapCache = HashMap<String, Pair<Long, Bitmap>>()

    private fun getBackgroundBitmap(context: Context, fileName: String): Bitmap? {
        val file = backgroundFile(context, fileName)
        if (!file.exists() || file.length() <= 0) {
            synchronized(backgroundBitmapCache) { backgroundBitmapCache.remove(fileName) }
            return null
        }
        val stamp = file.lastModified()
        synchronized(backgroundBitmapCache) {
            backgroundBitmapCache[fileName]?.let { (cachedStamp, bitmap) ->
                if (cachedStamp == stamp) return bitmap
                backgroundBitmapCache.remove(fileName)
            }
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        synchronized(backgroundBitmapCache) { backgroundBitmapCache[fileName] = stamp to bitmap }
        return bitmap
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var scaledWidth = width
        var scaledHeight = height
        while (scaledWidth / 2 >= MAX_BACKGROUND_SIDE || scaledHeight / 2 >= MAX_BACKGROUND_SIDE) {
            sampleSize *= 2
            scaledWidth /= 2
            scaledHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun backgroundFile(context: Context, fileName: String): File {
        return File(context.filesDir, fileName)
    }
}
