package com.aiassistant

import android.content.Context
import com.aiassistant.domain.model.Message
import com.aiassistant.utils.FavoriteMessage
import com.aiassistant.utils.FavoriteStore
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 收藏持久化回归。
 *
 * 背景（v2.8.38 release 点击进对话闪退）：FavoriteStore 曾用匿名 `TypeToken` 反序列化，
 * R8 把匿名子类合并进 TypeToken，运行时抛 “Missing type parameter”，聊天页读取收藏流即崩溃。
 * 现改为数组类反序列化，这里验证写入 / 读取 / 取消收藏与 JSON 字段完整。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, manifest = Config.NONE)
class FavoriteStorePersistenceTest {

    @Test
    fun togglePersistsPlainJsonAndRemovesOnSecondToggle() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("echo_favorites", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val message = Message(
            id = 42L,
            conversationId = 7L,
            role = "assistant",
            content = "合成收藏内容",
            createdAt = 1_000L
        )

        assertTrue("首次点击应收藏", FavoriteStore.toggle(context, message))
        assertTrue(FavoriteStore.isFavorite(context, 42L))
        assertEquals(listOf(42L), FavoriteStore.favorites.value.map { it.messageId })

        val raw = context.getSharedPreferences("echo_favorites", Context.MODE_PRIVATE)
            .getString("favorites_json", null)
        assertNotNull("收藏必须落盘", raw)
        val parsed = Gson().fromJson(raw, Array<FavoriteMessage?>::class.java)
            ?.filterNotNull()
            .orEmpty()
        assertEquals(1, parsed.size)
        assertEquals(42L, parsed[0].messageId)
        assertEquals(7L, parsed[0].conversationId)
        assertEquals("assistant", parsed[0].role)
        assertEquals("合成收藏内容", parsed[0].content)

        assertFalse("再次点击应取消收藏", FavoriteStore.toggle(context, message))
        assertFalse(FavoriteStore.isFavorite(context, 42L))
        assertTrue(FavoriteStore.favorites.value.isEmpty())
    }
}
