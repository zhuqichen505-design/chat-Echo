package com.aiassistant.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object AvatarManager {
    private const val AVATAR_FILE = "user_avatar.dat"
    private const val MODEL_AVATAR_FILE = "model_avatar.dat"

    private fun apiModelAvatarFileName(apiConfigId: Long): String =
        "model_avatar_api_$apiConfigId.dat"

    // 保存头像（Base64编码）
    fun saveAvatar(context: Context, base64Data: String) {
        saveAvatar(context, AVATAR_FILE, base64Data)
    }

    // 从URI保存头像
    fun saveAvatarFromUri(context: Context, uri: Uri): Boolean {
        return saveAvatarFromUri(context, uri, AVATAR_FILE)
    }

    fun saveModelAvatarFromUri(context: Context, uri: Uri): Boolean {
        return saveAvatarFromUri(context, uri, MODEL_AVATAR_FILE)
    }

    fun saveApiModelAvatarFromUri(context: Context, apiConfigId: Long, uri: Uri): Boolean {
        if (apiConfigId <= 0L) return false
        return saveAvatarFromUri(context, uri, apiModelAvatarFileName(apiConfigId))
    }

    fun saveAvatarBitmap(context: Context, bitmap: Bitmap): Boolean {
        return saveAvatarBitmap(context, bitmap, AVATAR_FILE)
    }

    fun saveModelAvatarBitmap(context: Context, bitmap: Bitmap): Boolean {
        return saveAvatarBitmap(context, bitmap, MODEL_AVATAR_FILE)
    }

    fun saveApiModelAvatarBitmap(context: Context, apiConfigId: Long, bitmap: Bitmap): Boolean {
        if (apiConfigId <= 0L) return false
        return saveAvatarBitmap(context, bitmap, apiModelAvatarFileName(apiConfigId))
    }

    fun saveCharacterAvatarBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            val fileName = "character_avatar_${System.currentTimeMillis()}.png"
            val file = File(context.filesDir, fileName)
            val resized = if (bitmap.width > 512 || bitmap.height > 512) {
                Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            } else {
                bitmap
            }
            file.outputStream().use { output ->
                resized.compress(Bitmap.CompressFormat.PNG, 90, output)
            }
            if (resized != bitmap) resized.recycle()
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveConversationModelAvatarBitmap(context: Context, conversationId: Long, bitmap: Bitmap): String? {
        return try {
            val fileName = "conversation_avatar_${conversationId}_${System.currentTimeMillis()}.png"
            val file = File(context.filesDir, fileName)
            val resized = if (bitmap.width > 512 || bitmap.height > 512) {
                Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            } else {
                bitmap
            }
            file.outputStream().use { output ->
                resized.compress(Bitmap.CompressFormat.PNG, 90, output)
            }
            if (resized != bitmap) resized.recycle()
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteConversationModelAvatar(context: Context, avatarUri: String?) {
        if (avatarUri.isNullOrBlank()) return
        runCatching {
            val uri = Uri.parse(avatarUri)
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.name.startsWith("conversation_avatar_")) {
                    file.delete()
                }
            }
        }
    }

    private fun saveAvatarBitmap(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            val resized = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            saveAvatar(context, fileName, base64)
            if (resized != bitmap) resized.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveAvatar(context: Context, fileName: String, base64Data: String) {
        val file = File(context.filesDir, fileName)
        file.writeText(base64Data)
    }

    fun saveTempAvatarBitmap(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val file = File(context.cacheDir, "temp_avatar_${System.currentTimeMillis()}.png")
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, output)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveAvatarFromUri(context: Context, uri: Uri, fileName: String): Boolean {
        return try {
            val inputStream = if (uri.scheme == "file") {
                uri.path?.let { File(it).inputStream() } ?: context.contentResolver.openInputStream(uri)
            } else {
                context.contentResolver.openInputStream(uri)
            }
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap == null) return false

            // 压缩并转换为Base64
            val resized = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

            saveAvatar(context, fileName, base64)

            if (resized != bitmap) resized.recycle()
            bitmap.recycle()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 获取头像（Base64编码）
    fun getAvatar(context: Context): String? {
        return getAvatar(context, AVATAR_FILE)
    }

    fun getModelAvatar(context: Context): String? {
        return getAvatar(context, MODEL_AVATAR_FILE)
    }

    fun getApiModelAvatar(context: Context, apiConfigId: Long): String? {
        if (apiConfigId <= 0L) return null
        return getAvatar(context, apiModelAvatarFileName(apiConfigId))
    }

    private fun getAvatar(context: Context, fileName: String): String? {
        val file = File(context.filesDir, fileName)
        return if (file.exists()) {
            file.readText().ifBlank { null }
        } else null
    }

    // 获取头像Bitmap
    fun getAvatarBitmap(context: Context): Bitmap? {
        return getAvatarBitmap(context, AVATAR_FILE)
    }

    fun getModelAvatarBitmap(context: Context): Bitmap? {
        return getAvatarBitmap(context, MODEL_AVATAR_FILE)
    }

    fun getApiModelAvatarBitmap(context: Context, apiConfigId: Long?): Bitmap? {
        val id = apiConfigId ?: return null
        if (id <= 0L) return null
        return getAvatarBitmap(context, apiModelAvatarFileName(id))
    }

    fun getPreferredModelAvatarBitmap(context: Context, apiConfigId: Long?): Bitmap? {
        return getApiModelAvatarBitmap(context, apiConfigId) ?: getModelAvatarBitmap(context)
    }

    fun base64ToBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val byteArray = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        } catch (e: Exception) {
            null
        }
    }

    // v2.7.3 流畅度：头像位图 LruCache——消息列表每行 ChatAvatar 原先各自读盘+Base64+PNG 解码
    // 同一头像，长会话首帧 = N 次重复解码（全部在主线程组合期）；缓存后每头像仅解码一次。
    // key 携带文件 lastModified，保存/删除头像后自然取到新值，旧条目由 LRU 淘汰
    private val avatarBitmapCache = object : android.util.LruCache<String, Bitmap>(16) {}

    private fun getAvatarBitmap(context: Context, fileName: String): Bitmap? {
        val file = File(context.filesDir, fileName)
        if (!file.exists() || file.length() <= 0) return null
        val cacheKey = fileName + ":" + file.lastModified()
        avatarBitmapCache.get(cacheKey)?.let { return it }
        val base64 = getAvatar(context, fileName) ?: return null
        val bitmap = base64ToBitmap(base64) ?: return null
        avatarBitmapCache.put(cacheKey, bitmap)
        return bitmap
    }

    // 删除头像
    fun deleteAvatar(context: Context) {
        deleteAvatar(context, AVATAR_FILE)
    }

    fun deleteModelAvatar(context: Context) {
        deleteAvatar(context, MODEL_AVATAR_FILE)
    }

    fun deleteApiModelAvatar(context: Context, apiConfigId: Long) {
        if (apiConfigId > 0L) deleteAvatar(context, apiModelAvatarFileName(apiConfigId))
    }

    private fun deleteAvatar(context: Context, fileName: String) {
        val file = File(context.filesDir, fileName)
        if (file.exists()) file.delete()
    }

    // 检查是否有自定义头像
    fun hasCustomAvatar(context: Context): Boolean {
        val file = File(context.filesDir, AVATAR_FILE)
        return file.exists() && file.length() > 0
    }

    fun hasCustomModelAvatar(context: Context): Boolean {
        val file = File(context.filesDir, MODEL_AVATAR_FILE)
        return file.exists() && file.length() > 0
    }

    fun hasCustomApiModelAvatar(context: Context, apiConfigId: Long): Boolean {
        if (apiConfigId <= 0L) return false
        val file = File(context.filesDir, apiModelAvatarFileName(apiConfigId))
        return file.exists() && file.length() > 0
    }
}
