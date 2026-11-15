package com.aiassistant.utils

import android.content.Context
import androidx.core.content.edit
import com.aiassistant.domain.model.Message
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一条收藏记录。
 *
 * 内容快照保证消息被删除或变体切换后仍能在收藏列表里展示原始文字；
 * [messageId] 用于从收藏列表跳回对话中的对应位置。
 */
data class FavoriteMessage(
    val conversationId: Long,
    val messageId: Long,
    val role: String,
    val content: String,
    val messageCreatedAt: Long,
    val favoritedAt: Long
)

/**
 * 消息收藏存储：消息本身仍以 Room 为唯一数据源，这里只保存“收藏了哪条消息”的索引
 * 与展示用快照，避免为一个轻量功能改动数据库结构。
 *
 * 读写在主线程以 SharedPreferences 完成（记录数量很少），对外暴露 StateFlow 供
 * 设置页、对话设置与消息气泡同步刷新。
 */
object FavoriteStore {
    private const val PREFS_NAME = "echo_favorites"
    private const val KEY_ITEMS = "favorites_json"

    /** 快照超过该长度时截断，收藏列表只需要预览。 */
    private const val MAX_SNAPSHOT_CHARS = 2000

    private val gson = Gson()

    private val _favorites = MutableStateFlow<List<FavoriteMessage>>(emptyList())
    val favorites: StateFlow<List<FavoriteMessage>> = _favorites.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _favorites.value = readFromPrefs(context)
            loaded = true
        }
    }

    fun isFavorite(context: Context, messageId: Long): Boolean {
        ensureLoaded(context)
        return _favorites.value.any { it.messageId == messageId }
    }

    fun favoritesForConversation(context: Context, conversationId: Long): List<FavoriteMessage> {
        ensureLoaded(context)
        return favoritesIn(_favorites.value, conversationId)
    }

    /** 收藏 / 取消收藏，返回操作后是否处于收藏状态。 */
    fun toggle(context: Context, message: Message): Boolean {
        ensureLoaded(context)
        val entry = FavoriteMessage(
            conversationId = message.conversationId,
            messageId = message.id,
            role = message.role,
            content = message.content.take(MAX_SNAPSHOT_CHARS),
            messageCreatedAt = message.createdAt,
            favoritedAt = System.currentTimeMillis()
        )
        val current = _favorites.value
        val next = toggledFavorites(current, entry)
        persist(context, next)
        return next.any { it.messageId == message.id }
    }

    fun remove(context: Context, messageId: Long) {
        ensureLoaded(context)
        val next = _favorites.value.filterNot { it.messageId == messageId }
        if (next.size != _favorites.value.size) persist(context, next)
    }

    private fun persist(context: Context, next: List<FavoriteMessage>) {
        _favorites.value = next
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                // List 序列化按运行时元素类型反射，不依赖 TypeToken。
                .edit { putString(KEY_ITEMS, gson.toJson(next)) }
        }
    }

    private fun readFromPrefs(context: Context): List<FavoriteMessage> {
        val raw = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, null)
        }.getOrNull() ?: return emptyList()
        // 用数组类反序列化：匿名 TypeToken 子类会被 R8 类合并移除（release 下 Missing type parameter）。
        return runCatching {
            gson.fromJson(raw, Array<FavoriteMessage?>::class.java)?.filterNotNull().orEmpty()
        }
            .getOrNull()
            .orEmpty()
            .filter { it.conversationId > 0L && it.messageId > 0L }
            .sortedByDescending { it.favoritedAt }
    }
}

/** 收藏切换的纯逻辑：同一消息已收藏则移除，否则追加并按收藏时间倒序。 */
internal fun toggledFavorites(
    current: List<FavoriteMessage>,
    entry: FavoriteMessage
): List<FavoriteMessage> {
    val exists = current.any { it.messageId == entry.messageId }
    return if (exists) {
        current.filterNot { it.messageId == entry.messageId }
    } else {
        (current + entry).sortedByDescending { it.favoritedAt }
    }
}

/** 某个对话内的收藏，最新收藏在前。 */
internal fun favoritesIn(
    all: List<FavoriteMessage>,
    conversationId: Long
): List<FavoriteMessage> = all
    .filter { it.conversationId == conversationId }
    .sortedByDescending { it.favoritedAt }
