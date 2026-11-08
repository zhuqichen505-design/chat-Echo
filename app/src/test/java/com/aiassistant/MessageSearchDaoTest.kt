package com.aiassistant

import android.app.Application
import androidx.room.Room
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.Message
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 多关键词消息检索的 SQL 行为：AND 语义、隐藏对话排除、ASCII 大小写折叠、空关键词跳过。
 * 这些是 UI 层无法替代验证的部分（Room 编译期不校验运行结果）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
class MessageSearchDaoTest {

    @Test
    fun multiTermSearchRequiresEveryTermAndSkipsHiddenConversations() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val visibleId = db.conversationDao().insertConversation(
                Conversation(title = "项目需求", apiConfigId = 1L, modelName = "test-model")
            )
            val hiddenId = db.conversationDao().insertConversation(
                Conversation(title = "隐藏对话", apiConfigId = 1L, modelName = "test-model", tags = "hidden")
            )
            db.messageDao().insertMessage(
                Message(conversationId = visibleId, role = "user", content = "项目需求文档需要评审")
            )
            db.messageDao().insertMessage(
                Message(conversationId = visibleId, role = "assistant", content = "无关内容")
            )
            db.messageDao().insertMessage(
                Message(conversationId = hiddenId, role = "user", content = "项目需求")
            )

            val hits = db.messageDao().searchMessagesIn("项目", "需求", "", "")
            assertEquals(1, hits.size)
            assertEquals("项目需求文档需要评审", hits[0].content)

            assertTrue(db.messageDao().searchMessagesIn("项目", "设计", "", "").isEmpty())
            assertTrue(db.messageDao().searchMessagesIn("不存在", "", "", "").isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun asciiTermsMatchRegardlessOfCase() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val conversationId = db.conversationDao().insertConversation(
                Conversation(title = "英文", apiConfigId = 1L, modelName = "test-model")
            )
            db.messageDao().insertMessage(
                Message(conversationId = conversationId, role = "user", content = "Alpha BETA gamma")
            )
            val hits = db.messageDao().searchMessagesIn("alpha", "beta", "", "")
            assertEquals(1, hits.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun emptyTermsDoNotFilter() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val conversationId = db.conversationDao().insertConversation(
                Conversation(title = "空词", apiConfigId = 1L, modelName = "test-model")
            )
            db.messageDao().insertMessage(
                Message(conversationId = conversationId, role = "user", content = "任意内容")
            )
            assertEquals(1, db.messageDao().searchMessagesIn("", "", "", "").size)
        } finally {
            db.close()
        }
    }
}
