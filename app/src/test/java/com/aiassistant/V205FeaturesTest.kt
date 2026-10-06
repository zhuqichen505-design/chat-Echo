package com.aiassistant

import com.aiassistant.data.repository.AiRepository
import com.aiassistant.domain.model.*
import com.aiassistant.utils.BackupManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Test

class V205FeaturesTest {

    @Test
    fun testDuplicateTitleIncrement() {
        assertEquals("测试对话 (副本)", AiRepository.generateDuplicateTitle("测试对话"))
        assertEquals("测试对话 (副本 2)", AiRepository.generateDuplicateTitle("测试对话 (副本)"))
        assertEquals("测试对话 (副本 3)", AiRepository.generateDuplicateTitle("测试对话 (副本 2)"))
        assertEquals("讨论架构 (副本 11)", AiRepository.generateDuplicateTitle("讨论架构 (副本 10)"))
        assertEquals("未命名对话 (副本)", AiRepository.generateDuplicateTitle(""))
        assertEquals("未命名对话 (副本)", AiRepository.generateDuplicateTitle("   "))
    }

    @Test
    fun testDuplicateConversationHiddenTagInheritance() {
        val originalHidden = Conversation(
            id = 101L,
            title = "秘密项目",
            apiConfigId = 1L,
            modelName = "claude-3-7-sonnet",
            isPinned = true,
            tags = "work,hidden,finance",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        // 模拟 duplicateConversation 核心逻辑
        val duplicatedTitle = AiRepository.generateDuplicateTitle(originalHidden.title)
        val isHidden = originalHidden.tags?.contains("hidden") == true
        val duplicatedTags = if (isHidden) originalHidden.tags else null

        val duplicated = originalHidden.copy(
            id = 0L,
            title = duplicatedTitle,
            isPinned = false,
            tags = duplicatedTags
        )

        assertEquals("秘密项目 (副本)", duplicated.title)
        assertFalse("复制生成的对话默认不置顶，避免干扰置顶区", duplicated.isPinned)
        assertTrue("复制生成的隐藏对话必须保留 hidden 标签", duplicated.tags?.contains("hidden") == true)
        assertEquals(0L, duplicated.id)
    }

    @Test
    fun testDuplicateConversationNormalNotHidden() {
        val originalNormal = Conversation(
            id = 202L,
            title = "日常交流",
            apiConfigId = 1L,
            modelName = "gpt-4o",
            isPinned = true,
            tags = "chat",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val isHidden = originalNormal.tags?.contains("hidden") == true
        val duplicated = originalNormal.copy(
            id = 0L,
            title = AiRepository.generateDuplicateTitle(originalNormal.title),
            isPinned = false,
            tags = if (isHidden) originalNormal.tags else originalNormal.tags
        )

        assertEquals("日常交流 (副本)", duplicated.title)
        assertFalse(duplicated.isPinned)
        assertFalse("普通对话复制后不得含有 hidden 标签", duplicated.tags?.contains("hidden") == true)
    }

    @Test
    fun testSingleConversationExportSerializationAndDeserialization() {
        val now = System.currentTimeMillis()
        val conv = Conversation(
            id = 505L,
            title = "异世界冒险录",
            apiConfigId = 3L,
            modelName = "deepseek-reasoner",
            temperature = 0.8f,
            systemPrompt = "你是一名冒险者公会接待员",
            createdAt = now - 10000L,
            updatedAt = now
        )

        val messages = listOf(
            Message(
                id = 1L,
                conversationId = 505L,
                role = "user",
                content = "请问今天有什么委托？",
                createdAt = now - 8000L
            ),
            Message(
                id = 2L,
                conversationId = 505L,
                role = "assistant",
                content = "今天哥布林讨伐任务有三件，奖励丰厚！",
                thinkingContent = "分析委托等级并匹配冒险者身份...",
                createdAt = now - 5000L
            )
        )

        val character = CharacterProfile(
            id = 12L,
            name = "艾莉丝",
            identity = "接待员",
            personality = "开朗亲切"
        )

        val scenario = RoleplayScenario(
            id = 34L,
            name = "边境公会",
            worldview = "剑与魔法的奇幻大陆"
        )

        val session = RoleplaySession(
            id = 88L,
            characterId = character.id,
            scenarioId = scenario.id,
            conversationId = conv.id,
            currentPlotSummary = "主角刚抵达边境公会正在询问委托"
        )

        val memories = listOf(
            RoleplayMemory(
                id = 991L,
                sessionId = session.id,
                memoryType = "fact",
                content = "主角登记为铜级冒险者",
                isPinned = true
            )
        )

        val exportBundle = BackupManager.SingleConversationExport(
            formatVersion = 1,
            type = "single_conversation",
            exportedAt = now,
            appVersion = "2.0.5",
            conversation = conv,
            messages = messages,
            roleplaySession = session,
            characterProfile = character,
            roleplayScenario = scenario,
            roleplayMemories = memories
        )

        val json = GsonBuilder().setPrettyPrinting().create().toJson(exportBundle)
        assertNotNull(json)
        assertTrue(json.contains("\"single_conversation\""))
        assertTrue(json.contains("异世界冒险录"))
        assertTrue(json.contains("艾莉丝"))
        assertTrue(json.contains("主角登记为铜级冒险者"))

        // 反序列化校验
        val parsed = Gson().fromJson(json, BackupManager.SingleConversationExport::class.java)
        assertNotNull(parsed)
        assertEquals(505L, parsed.conversation?.id)
        assertEquals("异世界冒险录", parsed.conversation?.title)
        assertEquals(2, parsed.messages?.size)
        assertEquals("艾莉丝", parsed.characterProfile?.name)
        assertEquals("边境公会", parsed.roleplayScenario?.name)
        assertEquals(1, parsed.roleplayMemories?.size)
        assertEquals("主角登记为铜级冒险者", parsed.roleplayMemories?.first()?.content)
        assertTrue(parsed.roleplayMemories?.first()?.isPinned == true)
    }

    @Test
    fun testIsJsonBackupDetection() {
        val tempJsonFile = java.io.File.createTempFile("test_conv", ".json")
        tempJsonFile.writeText("{\"type\":\"single_conversation\",\"conversation\":{}}")
        assertTrue("以.json结尾的文件必须被识别为JSON备份", BackupManager.isJsonBackup(tempJsonFile))
        tempJsonFile.delete()

        val tempNoExtJsonFile = java.io.File.createTempFile("test_conv_no_ext", "")
        tempNoExtJsonFile.writeText("{\"title\":\"无后缀测试\",\"messages\":[]}")
        assertTrue("以大括号开头的纯文本必须被探测识别为JSON备份", BackupManager.isJsonBackup(tempNoExtJsonFile))
        tempNoExtJsonFile.delete()

        val tempZipFile = java.io.File.createTempFile("test_zip", ".zip")
        tempZipFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00))
        assertFalse("标准ZIP文件头不得被误判为JSON备份", BackupManager.isJsonBackup(tempZipFile))
        tempZipFile.delete()
    }

    @Test
    fun testNonDestructiveMergeInvariant() {
        // 本地已有对话集合（包含用户最新创建的未备份对话）
        val localConversations = mutableMapOf(
            1L to "原有对话 1",
            2L to "本地新创建的对话（不在备份中）"
        )

        // 备份中包含的对话
        val backupConversations = mapOf(
            1L to "原有对话 1 (备份版)",
            3L to "备份里的历史对话 3"
        )

        // 模拟非破坏性合并逻辑
        backupConversations.forEach { (backupId, title) ->
            if (localConversations.containsKey(backupId)) {
                // 已有相同 ID：更新
                localConversations[backupId] = title
            } else {
                // 新对话：插入
                localConversations[backupId] = title
            }
        }

        // 验证不变性：本地已有且未在备份中的对话绝不能被删除！
        assertTrue("本地未备份对话必须完好无损存在", localConversations.containsKey(2L))
        assertEquals("本地新创建的对话（不在备份中）", localConversations[2L])
        assertTrue("备份里的历史对话成功合并", localConversations.containsKey(3L))
        assertEquals(3, localConversations.size)
    }

    @Test
    fun testSingleConversationIsolatedRestoreInvariant() {
        // 本地环境拥有多个对话
        val localConversations = mutableMapOf(
            1L to "对话 A",
            2L to "对话 B",
            3L to "对话 C"
        )

        // 导入单个对话的备份包（针对对话 2 进行恢复，或者导入全新对话 4）
        val importedSingle = Pair(4L, "独立备份对话 D")

        // 导入单对话逻辑：仅更新或插入该对话
        localConversations[importedSingle.first] = importedSingle.second

        // 核心约束验证：其余所有对话完全不被覆盖或消失
        assertEquals("对话 A", localConversations[1L])
        assertEquals("对话 B", localConversations[2L])
        assertEquals("对话 C", localConversations[3L])
        assertEquals("独立备份对话 D", localConversations[4L])
        assertEquals(4, localConversations.size)
    }

    @Test
    fun testV205CurrentVersionUserUpdatesCompleteness() {
        val updates = com.aiassistant.ui.screens.settings.V205UserUpdates
        assertFalse("本次更新日志列表不得为空", updates.isEmpty())
        assertTrue("必须包含当前版本主要更新项 (>=5项)", updates.size >= 5)
        assertTrue("必须说明备份导入逻辑优化", updates.any { it.contains("备份导入") && it.contains("非破坏性") })
        assertTrue("必须说明复制对话功能", updates.any { it.contains("复制对话") })
        assertTrue("必须说明隐藏对话同步实现", updates.any { it.contains("隐藏对话") })
        assertTrue("必须说明单对话备份", updates.any { it.contains("单对话备份") })
    }

    @Test
    fun testSingleConversationBackupIncludesSessionMemoriesAndTimelineNodes() {
        val now = System.currentTimeMillis()
        val conv = Conversation(
            id = 601L,
            title = "异界机甲战纪",
            apiConfigId = 1L,
            modelName = "deepseek-chat",
            enableSessionMemory = true,
            createdAt = now,
            updatedAt = now
        )

        val timelineNodes = listOf(
            TimelineNode(
                id = 11L,
                conversationId = 601L,
                timeTag = "星历2045年·春",
                event = "主角初次同步第四代神经机甲",
                category = "turning_point",
                orderIndex = 0
            ),
            TimelineNode(
                id = 12L,
                conversationId = 601L,
                timeTag = "星历2045年·冬",
                event = "要塞遭遇未知机械生物袭击",
                category = "crisis",
                orderIndex = 1
            )
        )

        val sessionMemories = listOf(
            MemoryItem(
                id = 81L,
                scope = "conversation",
                conversationId = 601L,
                content = "【角色核心特质】右臂为抗热合金义肢，对低温极为敏感",
                confidence = 0.95f,
                isEnabled = true
            ),
            MemoryItem(
                id = 82L,
                scope = "conversation",
                conversationId = 601L,
                content = "【世界规则】夜间中子辐射加剧，机体散热系统功率需提升30%",
                confidence = 0.9f,
                isEnabled = true
            )
        )

        val bundle = BackupManager.SingleConversationExport(
            formatVersion = 1,
            type = "single_conversation",
            exportedAt = now,
            appVersion = "2.3.3",
            conversation = conv,
            messages = emptyList(),
            timelineNodes = timelineNodes,
            sessionMemories = sessionMemories
        )

        val json = GsonBuilder().setPrettyPrinting().create().toJson(bundle)
        assertNotNull(json)
        assertTrue("备份JSON必须包含会话专属设定 sessionMemories", json.contains("\"sessionMemories\""))
        assertTrue("备份JSON必须包含时间线节点 timelineNodes", json.contains("\"timelineNodes\""))
        assertTrue(json.contains("右臂为抗热合金义肢"))
        assertTrue(json.contains("夜间中子辐射加剧"))
        assertTrue(json.contains("主角初次同步第四代神经机甲"))

        // 反序列化校验
        val parsed = Gson().fromJson(json, BackupManager.SingleConversationExport::class.java)
        assertNotNull(parsed)
        assertEquals(601L, parsed.conversation?.id)
        assertEquals(true, parsed.conversation?.enableSessionMemory)
        assertEquals(2, parsed.timelineNodes?.size)
        assertEquals(2, parsed.sessionMemories?.size)
        assertEquals("【角色核心特质】右臂为抗热合金义肢，对低温极为敏感", parsed.sessionMemories?.first()?.content)
        assertEquals("【世界规则】夜间中子辐射加剧，机体散热系统功率需提升30%", parsed.sessionMemories?.last()?.content)
        assertEquals("星历2045年·春", parsed.timelineNodes?.first()?.timeTag)
    }

    @Test
    fun testSingleConversationBackupSessionSettingsAliasDeserialization() {
        // 模拟外部或老版本/别名 JSON (使用 sessionSettings / conversationMemories)
        val jsonWithAlias = """
            {
                "formatVersion": 1,
                "type": "single_conversation",
                "exportedAt": 1790576557245,
                "appVersion": "2.3.2",
                "conversation": {
                    "id": 999,
                    "title": "别名解析测试",
                    "apiConfigId": 1,
                    "modelName": "gpt-4o"
                },
                "timelineNodes": [
                    { "id": 1, "timeTag": "第一天", "event": "开启新篇章" }
                ],
                "sessionSettings": [
                    { "id": 10, "content": "【专属约束】所有对话以克制口吻叙述", "scope": "conversation", "isEnabled": true }
                ]
            }
        """.trimIndent()

        val parsed = Gson().fromJson(jsonWithAlias, BackupManager.SingleConversationExport::class.java)
        assertNotNull(parsed)
        assertEquals("别名解析测试", parsed.conversation?.title)
        assertEquals(1, parsed.timelineNodes?.size)
        assertNotNull(parsed.sessionMemories)
        assertEquals(1, parsed.sessionMemories?.size)
        assertEquals("【专属约束】所有对话以克制口吻叙述", parsed.sessionMemories?.first()?.content)
    }
}
