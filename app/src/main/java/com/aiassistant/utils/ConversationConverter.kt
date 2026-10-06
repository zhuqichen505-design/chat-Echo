package com.aiassistant.utils

import com.aiassistant.data.local.CharacterProfileDao
import com.aiassistant.data.local.ConversationDao
import com.aiassistant.data.local.MemoryDao
import com.aiassistant.data.local.MessageDao
import com.aiassistant.data.local.RoleplayScenarioDao
import com.aiassistant.data.local.RoleplaySessionDao
import com.aiassistant.data.local.TimelineNodeDao
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.Message
import com.aiassistant.domain.model.NarrativeMode
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.domain.model.TimelineNode
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ConversationExportBundle(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val conversation: Conversation,
    val messages: List<Message>,
    val isRoleplay: Boolean = false,
    val roleplaySession: RoleplaySession? = null,
    val character: CharacterProfile? = null,
    val scenario: RoleplayScenario? = null,
    val sessionMemories: List<MemoryItem> = emptyList(),
    val timelineNodes: List<TimelineNode> = emptyList()
)

class ConversationConverter(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val roleplaySessionDao: RoleplaySessionDao,
    private val characterProfileDao: CharacterProfileDao,
    private val roleplayScenarioDao: RoleplayScenarioDao,
    private val memoryDao: MemoryDao? = null,
    private val timelineNodeDao: TimelineNodeDao? = null,
    private val gson: Gson = Gson()
) {

    /**
     * 将普通对话转换为角色扮演/故事创作会话
     */
    suspend fun convertToRoleplay(
        conversationId: Long,
        charName: String? = null,
        charIdentity: String? = null,
        charPersonality: String? = null,
        scenarioName: String? = null,
        scenarioWorld: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val conv = conversationDao.getConversationById(conversationId)
            ?: throw IllegalArgumentException("会话不存在: $conversationId")

        // 若已是角色扮演，直接返回 session id
        val existingSession = roleplaySessionDao.getSessionByConversationId(conversationId)
        if (existingSession != null) return@withContext existingSession.id

        val now = System.currentTimeMillis()

        // 1. 创建关联的角色卡
        val resolvedCharName = charName?.takeIf { it.isNotBlank() } ?: "对话主角 (${conv.title.take(8)})"
        val character = CharacterProfile(
            name = resolvedCharName,
            identity = charIdentity.orEmpty().ifBlank { "故事核心主角" },
            personality = charPersonality.orEmpty().ifBlank { "沉着、智慧、性格鲜明" },
            background = conv.systemPrompt?.take(300).orEmpty().ifBlank { "基于历史对话提炼的角色背景" },
            speakingStyle = "符合角色身份的自然对白",
            createdAt = now,
            updatedAt = now
        )
        val charId = characterProfileDao.insertCharacter(character)

        // 2. 创建关联的世界观/场景卡
        val resolvedScenarioName = scenarioName?.takeIf { it.isNotBlank() } ?: "${conv.title.take(10)} · 世界观"
        val scenario = RoleplayScenario(
            name = resolvedScenarioName,
            worldview = scenarioWorld.orEmpty().ifBlank { "现实与幻想交织的叙事空间" },
            environment = "开阔场景",
            conflict = "双方互动与情节发展",
            atmosphere = "沉浸而引人入胜",
            createdAt = now,
            updatedAt = now
        )
        val scenarioId = roleplayScenarioDao.insertScenario(scenario)

        // 3. 创建 RoleplaySession
        val roleplaySession = RoleplaySession(
            conversationId = conversationId,
            characterId = charId,
            scenarioId = scenarioId,
            characterIds = charId.toString(),
            narrativeMode = NarrativeMode.CHARACTER.value,
            currentPlotSummary = "",
            createdAt = now,
            updatedAt = now
        )
        val sessionId = roleplaySessionDao.insertSession(roleplaySession)

        // 4. 更新对话 tags，加入 "roleplay"
        val existingTags = conv.tags.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        existingTags.add("roleplay")
        conversationDao.updateConversation(conv.copy(tags = existingTags.joinToString(","), updatedAt = now))

        return@withContext sessionId
    }

    /**
     * 将角色扮演会话转换回普通对话
     * 关键要求：角色卡人设与世界观完整转译为对话的 systemPrompt，绝不丢失任何设定！
     */
    suspend fun convertToNormal(conversationId: Long): Boolean = withContext(Dispatchers.IO) {
        val conv = conversationDao.getConversationById(conversationId) ?: return@withContext false
        val session = roleplaySessionDao.getSessionByConversationId(conversationId) ?: return@withContext false

        val character = session.characterId?.let { characterProfileDao.getCharacterById(it) }
        val scenario = session.scenarioId?.let { roleplayScenarioDao.getScenarioById(it) }

        // 完整转译合并设定为常规系统提示词
        val mergedPrompt = buildString {
            if (character != null) {
                append("【角色设定】\n")
                append("角色名称：").append(character.name).append("\n")
                if (character.identity.isNotBlank()) append("身份职业：").append(character.identity).append("\n")
                if (character.personality.isNotBlank()) append("性格特点：").append(character.personality).append("\n")
                if (character.background.isNotBlank()) append("背景：").append(character.background).append("\n")
                if (character.speakingStyle.isNotBlank()) append("对白风格：").append(character.speakingStyle).append("\n")
                append("\n")
            }
            if (scenario != null) {
                append("【世界观与场景】\n")
                append("场景名称：").append(scenario.name).append("\n")
                if (scenario.worldview.isNotBlank()) append("世界法则：").append(scenario.worldview).append("\n")
                if (scenario.conflict.isNotBlank()) append("当前冲突：").append(scenario.conflict).append("\n")
                append("\n")
            }
            if (session.currentPlotSummary.isNotBlank()) {
                append("【前序剧情摘要】\n").append(session.currentPlotSummary).append("\n\n")
            }
            conv.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                append("【原补充提示词】\n").append(it)
            }
        }.trim()

        // 移除 "roleplay" 标签
        val existingTags = conv.tags.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "roleplay" }
            .joinToString(",")

        val now = System.currentTimeMillis()
        conversationDao.updateConversation(
            conv.copy(
                systemPrompt = mergedPrompt,
                tags = existingTags.ifBlank { null },
                updatedAt = now
            )
        )

        // 移除 RoleplaySession 绑定
        roleplaySessionDao.deleteSession(session)
        return@withContext true
    }

    /**
     * 导出完整会话（支持常规与角色创作）
     */
    suspend fun exportBundle(conversationId: Long): String = withContext(Dispatchers.IO) {
        val conv = conversationDao.getConversationById(conversationId)
            ?: throw IllegalArgumentException("会话不存在")
        val messages = messageDao.getMessagesList(conversationId)
        val session = roleplaySessionDao.getSessionByConversationId(conversationId)
        val char = session?.characterId?.let { characterProfileDao.getCharacterById(it) }
        val sc = session?.scenarioId?.let { roleplayScenarioDao.getScenarioById(it) }
        val sessionMems = memoryDao?.getConversationMemories(conversationId).orEmpty()
        val nodes = timelineNodeDao?.getTimelineNodes(conversationId).orEmpty()

        val bundle = ConversationExportBundle(
            conversation = conv,
            messages = messages,
            isRoleplay = session != null,
            roleplaySession = session,
            character = char,
            scenario = sc,
            sessionMemories = sessionMems,
            timelineNodes = nodes
        )
        gson.toJson(bundle)
    }

    /**
     * 导出为 Markdown 文档格式
     */
    suspend fun exportAsMarkdown(conversationId: Long): String = withContext(Dispatchers.IO) {
        val conv = conversationDao.getConversationById(conversationId)
            ?: throw IllegalArgumentException("会话不存在: $conversationId")
        val messages = messageDao.getMessagesList(conversationId)
        val session = roleplaySessionDao.getSessionByConversationId(conversationId)
        val character = session?.characterId?.let { characterProfileDao.getCharacterById(it) }
        val scenario = session?.scenarioId?.let { roleplayScenarioDao.getScenarioById(it) }

        buildString {
            append("# ${conv.title}\n\n")
            append("- **导出时间**: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
            if (conv.modelName != null) {
                append("- **使用模型**: ${conv.modelName}\n")
            }
            if (character != null) {
                append("- **扮演角色**: ${character.name} (${character.identity})\n")
            }
            if (scenario != null) {
                append("- **设定场景**: ${scenario.name}\n")
            }
            if (!conv.systemPrompt.isNullOrBlank()) {
                append("\n> **系统提示词**:\n> ${conv.systemPrompt.replace("\n", "\n> ")}\n")
            }
            append("\n---\n\n")

            for (msg in messages) {
                val roleTitle = when (msg.role) {
                    "user" -> "👤 **用户**"
                    "assistant" -> if (character != null) "🎭 **${character.name}**" else "🤖 **${conv.modelName ?: "AI 助手"}**"
                    "system" -> "⚙️ **系统设定**"
                    else -> "📝 **${msg.role}**"
                }
                append("$roleTitle\n\n")
                if (!msg.thinkingContent.isNullOrBlank()) {
                    append("<details>\n<summary>💭 思考过程</summary>\n\n")
                    append(msg.thinkingContent)
                    append("\n</details>\n\n")
                }
                append(msg.content)
                append("\n\n---\n\n")
            }
        }
    }

    /**
     * 导出为纯文本 TXT 格式
     */
    suspend fun exportAsPlainText(conversationId: Long): String = withContext(Dispatchers.IO) {
        val conv = conversationDao.getConversationById(conversationId)
            ?: throw IllegalArgumentException("会话不存在: $conversationId")
        val messages = messageDao.getMessagesList(conversationId)
        val session = roleplaySessionDao.getSessionByConversationId(conversationId)
        val character = session?.characterId?.let { characterProfileDao.getCharacterById(it) }

        buildString {
            append("【会话标题】: ${conv.title}\n")
            append("【导出时间】: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
            if (character != null) {
                append("【角色】: ${character.name}\n")
            }
            append("=".repeat(40)).append("\n\n")

            for (msg in messages) {
                val sender = when (msg.role) {
                    "user" -> "用户"
                    "assistant" -> character?.name ?: "AI"
                    else -> msg.role
                }
                append("[$sender]:\n")
                append(msg.content)
                append("\n\n")
            }
        }
    }

    /**
     * 单张角色卡导出与导入
     */
    fun exportCharacter(character: CharacterProfile): String = gson.toJson(character)
    fun importCharacter(json: String): CharacterProfile? = runCatching { gson.fromJson(json, CharacterProfile::class.java) }.getOrNull()

    /**
     * 单张场景卡导出与导入
     */
    fun exportScenario(scenario: RoleplayScenario): String = gson.toJson(scenario)
    fun importScenario(json: String): RoleplayScenario? = runCatching { gson.fromJson(json, RoleplayScenario::class.java) }.getOrNull()

    /**
     * 单个提示词模板导出与导入
     */
    fun exportTemplate(template: com.aiassistant.domain.model.PromptTemplate): String = gson.toJson(template)
    fun importTemplate(json: String): com.aiassistant.domain.model.PromptTemplate? = runCatching { gson.fromJson(json, com.aiassistant.domain.model.PromptTemplate::class.java) }.getOrNull()

    /**
     * 导入会话 Bundle
     */
    suspend fun importBundle(json: String): Long = withContext(Dispatchers.IO) {
        val bundle = gson.fromJson(json, ConversationExportBundle::class.java)
            ?: throw IllegalArgumentException("无效的会话导出数据")
        val now = System.currentTimeMillis()
        val importedConv = bundle.conversation.copy(
            id = 0,
            title = "${bundle.conversation.title} (导入)",
            createdAt = now,
            updatedAt = now
        )
        val newConvId = conversationDao.insertConversation(importedConv)

        val newCharId = bundle.character?.let { char ->
            characterProfileDao.insertCharacter(char.copy(id = 0, createdAt = now, updatedAt = now))
        }

        val newScenarioId = bundle.scenario?.let { scen ->
            roleplayScenarioDao.insertScenario(scen.copy(id = 0, createdAt = now, updatedAt = now))
        }

        if (bundle.isRoleplay && bundle.roleplaySession != null) {
            val newSession = bundle.roleplaySession.copy(
                id = 0,
                conversationId = newConvId,
                characterId = newCharId ?: bundle.roleplaySession.characterId,
                scenarioId = newScenarioId ?: bundle.roleplaySession.scenarioId,
                characterIds = (newCharId ?: bundle.roleplaySession.characterId)?.toString(),
                createdAt = now,
                updatedAt = now
            )
            roleplaySessionDao.insertSession(newSession)
        }

        bundle.messages.forEach { msg ->
            messageDao.insertMessage(
                msg.copy(
                    id = 0,
                    conversationId = newConvId
                )
            )
        }

        memoryDao?.let { mDao ->
            bundle.sessionMemories.forEach { mem ->
                mDao.insertMemory(
                    mem.copy(
                        id = 0,
                        conversationId = newConvId,
                        scope = "conversation",
                        createdAt = if (mem.createdAt > 0) mem.createdAt else now,
                        updatedAt = now
                    )
                )
            }
        }

        timelineNodeDao?.let { tDao ->
            if (bundle.timelineNodes.isNotEmpty()) {
                val nodesToInsert = bundle.timelineNodes.map { node ->
                    node.copy(
                        id = 0,
                        conversationId = newConvId,
                        createdAt = if (node.createdAt > 0) node.createdAt else now,
                        updatedAt = now
                    )
                }
                tDao.insertTimelineNodes(nodesToInsert)
            }
        }

        newConvId
    }
}
