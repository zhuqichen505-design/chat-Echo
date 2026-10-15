package com.aiassistant.utils

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Portable media library metadata. Only hashes are exported, never arbitrary preference keys. */
internal object MediaHistoryBackup {
    private val avatarHistory = Regex("user_avatar_history_[a-f0-9]{64}\\.dat")
    private val backgroundHistory = Regex("background_history_[a-f0-9]{64}\\.img")
    private val apiAvatar = Regex("model_avatar_api_[0-9]+\\.dat")

    fun isMediaFile(name: String): Boolean = name in setOf(
        "user_avatar.dat", "model_avatar.dat", "home_background.jpg", "chat_background.jpg"
    ) || apiAvatar.matches(name) || avatarHistory.matches(name) || backgroundHistory.matches(name)

    fun exportDeleted(context: Context): JsonObject = JsonObject().apply {
        for ((key, pattern) in listOf("avatar_history" to avatarHistory, "background_history" to backgroundHistory)) {
            val entries = JsonArray()
            context.getSharedPreferences(key, Context.MODE_PRIVATE).all.forEach { (id, value) ->
                if (value == true && pattern.matches(id)) entries.add(id)
            }
            add(key, entries)
        }
    }

    /** Merge deletions: restoring an older backup must not undo a deletion made on this device. */
    fun importDeleted(context: Context, data: JsonObject) {
        for ((key, pattern) in listOf("avatar_history" to avatarHistory, "background_history" to backgroundHistory)) {
            val entries = data.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
            val editor = context.getSharedPreferences(key, Context.MODE_PRIVATE).edit()
            entries.forEach { value ->
                val id = value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                if (id != null && pattern.matches(id)) editor.putBoolean(id, true)
            }
            check(editor.commit()) { "媒体历史删除状态恢复失败" }
            context.getSharedPreferences(key, Context.MODE_PRIVATE).all.forEach { (id, value) ->
                if (value == true && pattern.matches(id)) android.util.AtomicFile(java.io.File(context.filesDir, id)).delete()
            }
        }
    }
}
