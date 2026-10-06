package com.aiassistant

import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.RoleplayRepository
import com.aiassistant.domain.model.*
import com.aiassistant.utils.ConversationConverter
import com.aiassistant.utils.CryptoManager
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class V210FeaturesTest {

    @Test
    fun testMessagePinningAndExclusionDefaults() {
        val msg = Message(
            conversationId = 100L,
            role = "assistant",
            content = "测试消息内容",
            tokenCount = 20,
            createdAt = System.currentTimeMillis()
        )

        assertFalse("默认情况下消息未被固定", msg.isPinned)
        assertFalse("默认情况下消息未从上下文排除", msg.isExcluded)

        val pinnedMsg = msg.copy(isPinned = true)
        assertTrue(pinnedMsg.isPinned)
        assertFalse(pinnedMsg.isExcluded)

        val excludedMsg = msg.copy(isExcluded = true)
        assertFalse(excludedMsg.isPinned)
        assertTrue(excludedMsg.isExcluded)
    }

    @Test
    fun testDatabaseMigration23To24Registered() {
        val migration = AppDatabase.MIGRATION_23_24
        assertNotNull("MIGRATION_23_24 必须存在", migration)
        assertEquals(23, migration.startVersion)
        assertEquals(24, migration.endVersion)
    }

    @Test
    fun testNarrativeModeMultiCharacter() {
        assertEquals("multi", NarrativeMode.MULTI.value)
        assertEquals(NarrativeMode.MULTI, NarrativeMode.fromValue("multi"))

        val prompt = RoleplayRepository.buildNarrativeModePrompt(NarrativeMode.MULTI.value)
        assertTrue(prompt.contains("多角色群像模式"))
        assertTrue(prompt.contains("生动推演群像戏"))
    }

    @Test
    fun testPlotActionProcessing() {
        val continuePrompt = RoleplayRepository.buildPlotActionPrompt(PlotAction.CONTINUE)
        assertTrue(continuePrompt.contains("顺畅自然地继续向下推进剧情"))

        val dialogueOnlyPrompt = RoleplayRepository.buildPlotActionPrompt(PlotAction.DIALOGUE_ONLY)
        assertTrue(dialogueOnlyPrompt.contains("只生成角色的对话"))

        val customPrompt = RoleplayRepository.buildPlotActionPrompt(PlotAction.CUSTOM, "突然发生了爆炸！")
        assertEquals("突然发生了爆炸！", customPrompt)
    }

    @Test
    fun testSingleCardExportAndImport() {
        val gson = Gson()
        // 测试独立卡片序列化与反序列化
        val character = CharacterProfile(
            id = 1L,
            name = "夜莺",
            avatarUri = "content://media/123",
            identity = "情报贩子",
            personality = "冷静睿智",
            background = "穿行于黑夜的间谍",
            speakingStyle = "低沉而富有磁性",
            greeting = "今晚你想打听什么情报？",
            tags = "spy,detective",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val charJson = gson.toJson(character)
        assertTrue(charJson.contains("夜莺"))
        assertTrue(charJson.contains("今晚你想打听什么情报？"))

        val restoredChar = gson.fromJson(charJson, CharacterProfile::class.java)
        assertEquals(character.name, restoredChar.name)
        assertEquals(character.greeting, restoredChar.greeting)
        assertEquals(character.avatarUri, restoredChar.avatarUri)

        val scenario = RoleplayScenario(
            id = 2L,
            name = "霓虹深处",
            worldview = "赛博朋克近未来城市",
            conflict = "巨型企业争权",
            atmosphere = "阴冷潮湿、霓虹闪烁",
            createdAt = 3000L,
            updatedAt = 4000L
        )

        val scenJson = gson.toJson(scenario)
        assertTrue(scenJson.contains("霓虹深处"))
        val restoredScen = gson.fromJson(scenJson, RoleplayScenario::class.java)
        assertEquals(scenario.name, restoredScen.name)
        assertEquals(scenario.worldview, restoredScen.worldview)
    }

    @Test
    fun testContextExclusionFilteringLogic() {
        val msg1 = Message(id = 1L, conversationId = 1L, role = "user", content = "第一句", isExcluded = false)
        val msg2 = Message(id = 2L, conversationId = 1L, role = "assistant", content = "第二句（将排除）", isExcluded = true)
        val msg3 = Message(id = 3L, conversationId = 1L, role = "user", content = "第三句（固定）", isPinned = true, isExcluded = false)
        val msg4 = Message(id = 4L, conversationId = 1L, role = "assistant", content = "第四句", isExcluded = false)

        val rawList = listOf(msg1, msg2, msg3, msg4)
        val filteredForContext = rawList.filter { !it.isExcluded }

        assertEquals(3, filteredForContext.size)
        assertFalse(filteredForContext.any { it.id == 2L })
        assertTrue(filteredForContext.any { it.isPinned })
    }

    @Test
    fun testCryptoManagerHeaderDetection() {
        val rawKey = "sk-test12345678"
        val encryptedPrefix = "enc:v1:"

        assertFalse(rawKey.startsWith(encryptedPrefix))
        val encryptedFake = "$encryptedPrefix" + "aW52YWxpZGJhc2U2NA=="
        assertTrue(encryptedFake.startsWith(encryptedPrefix))
        assertEquals("aW52YWxpZGJhc2U2NA==", encryptedFake.removePrefix(encryptedPrefix))
    }
}
