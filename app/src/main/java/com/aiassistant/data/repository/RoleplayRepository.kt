package com.aiassistant.data.repository

import com.aiassistant.data.local.*
import com.aiassistant.domain.model.*
import com.aiassistant.utils.TimelineMemoryHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull

/**
 * 角色扮演仓库 - 处理角色扮演相关的业务逻辑
 */
class RoleplayRepository(
    private val characterProfileDao: CharacterProfileDao,
    private val roleplayScenarioDao: RoleplayScenarioDao,
    private val roleplaySessionDao: RoleplaySessionDao,
    private val roleplayMemoryDao: RoleplayMemoryDao,
    private val characterTagDao: CharacterTagDao,
    private val conversationDao: ConversationDao? = null,
    private val messageDao: MessageDao? = null,
    private val worldBookDao: WorldBookDao? = null,
    private val memoryDao: MemoryDao? = null
) {
    // ============ 角色卡操作 ============

    fun getAllCharacters(): Flow<List<CharacterProfile>> = characterProfileDao.getAllCharacters()

    fun getFavoriteCharacters(): Flow<List<CharacterProfile>> = characterProfileDao.getFavoriteCharacters()

    fun searchCharacters(query: String): Flow<List<CharacterProfile>> = characterProfileDao.searchCharacters(query)

    suspend fun getCharacterById(id: Long): CharacterProfile? = characterProfileDao.getCharacterById(id)

    suspend fun getDefaultCharacter(): CharacterProfile? = characterProfileDao.getDefaultCharacter()

    suspend fun insertCharacter(character: CharacterProfile): Long = characterProfileDao.insertCharacter(character)

    suspend fun updateCharacter(character: CharacterProfile) = characterProfileDao.updateCharacter(character)

    suspend fun deleteCharacter(character: CharacterProfile) = characterProfileDao.deleteCharacter(character)

    suspend fun deleteCharacterById(id: Long) = characterProfileDao.deleteCharacterById(id)

    suspend fun setCharacterFavorite(id: Long, isFavorite: Boolean) = characterProfileDao.setFavorite(id, isFavorite)

    suspend fun setDefaultCharacter(id: Long) {
        characterProfileDao.clearDefaultCharacters()
        characterProfileDao.setDefaultCharacter(id)
    }

    suspend fun clearDefaultCharacter() = characterProfileDao.clearDefaultCharacters()

    suspend fun getCharacterCount(): Int = characterProfileDao.getCharacterCount()

    suspend fun deleteCharactersByIds(ids: List<Long>) {
        ids.forEach { characterProfileDao.deleteCharacterById(it) }
    }

    suspend fun deleteScenariosByIds(ids: List<Long>) {
        ids.forEach { roleplayScenarioDao.deleteScenarioById(it) }
    }

    suspend fun getCharactersByIds(ids: List<Long>): List<CharacterProfile> {
        return ids.mapNotNull { characterProfileDao.getCharacterById(it) }
    }

    // ============ 场景卡操作 ============

    fun getAllScenarios(): Flow<List<RoleplayScenario>> = roleplayScenarioDao.getAllScenarios()

    fun getFavoriteScenarios(): Flow<List<RoleplayScenario>> = roleplayScenarioDao.getFavoriteScenarios()

    fun searchScenarios(query: String): Flow<List<RoleplayScenario>> = roleplayScenarioDao.searchScenarios(query)

    suspend fun getScenarioById(id: Long): RoleplayScenario? = roleplayScenarioDao.getScenarioById(id)

    suspend fun insertScenario(scenario: RoleplayScenario): Long = roleplayScenarioDao.insertScenario(scenario)

    suspend fun updateScenario(scenario: RoleplayScenario) = roleplayScenarioDao.updateScenario(scenario)

    suspend fun deleteScenario(scenario: RoleplayScenario) = roleplayScenarioDao.deleteScenario(scenario)

    suspend fun deleteScenarioById(id: Long) = roleplayScenarioDao.deleteScenarioById(id)

    suspend fun setScenarioFavorite(id: Long, isFavorite: Boolean) = roleplayScenarioDao.setFavorite(id, isFavorite)

    suspend fun getScenarioCount(): Int = roleplayScenarioDao.getScenarioCount()

    // ============ 角色扮演会话操作 ============

    fun getAllSessions(): Flow<List<RoleplaySession>> = roleplaySessionDao.getAllSessions()

    fun getSessionsByCharacter(characterId: Long): Flow<List<RoleplaySession>> =
        roleplaySessionDao.getSessionsByCharacter(characterId)

    fun getSessionsByScenario(scenarioId: Long): Flow<List<RoleplaySession>> =
        roleplaySessionDao.getSessionsByScenario(scenarioId)

    suspend fun getSessionById(id: Long): RoleplaySession? = roleplaySessionDao.getSessionById(id)

    suspend fun getSessionByConversationId(conversationId: Long): RoleplaySession? =
        roleplaySessionDao.getSessionByConversationId(conversationId)

    suspend fun insertSession(session: RoleplaySession): Long = roleplaySessionDao.insertSession(session)

    suspend fun updateSession(session: RoleplaySession) = roleplaySessionDao.updateSession(session)

    suspend fun deleteSession(session: RoleplaySession) {
        roleplaySessionDao.deleteSession(session)
        roleplayMemoryDao.deleteAllMemories(session.id)
        conversationDao?.deleteConversationById(session.conversationId)
        messageDao?.deleteMessagesByConversation(session.conversationId)
    }

    suspend fun deleteSessionById(id: Long) {
        val session = roleplaySessionDao.getSessionById(id)
        if (session != null) {
            deleteSession(session)
        } else {
            roleplaySessionDao.deleteSessionById(id)
            roleplayMemoryDao.deleteAllMemories(id)
        }
    }

    suspend fun updateSessionMemoryState(id: Long, summary: String, pinnedFacts: String?) =
        roleplaySessionDao.updateMemoryState(id, summary, pinnedFacts)

    suspend fun getEffectiveCharactersForSession(session: RoleplaySession): List<CharacterProfile> {
        val characterIds = session.getEffectiveCharacterIds()
        val baseChars = if (characterIds.isNotEmpty()) {
            getCharactersByIds(characterIds)
        } else emptyList()
        return session.getCustomizedCharacters(baseChars)
    }

    suspend fun getEffectiveScenarioForSession(session: RoleplaySession): RoleplayScenario? {
        val baseScenario = session.scenarioId?.let { roleplayScenarioDao.getScenarioById(it) }
        return session.getCustomizedScenario(baseScenario)
    }

    /**
     * 将故事中的角色定制设定同步写回全局数据库 (Story -> DB)
     */
    suspend fun syncCharacterToDatabase(character: CharacterProfile): CharacterProfile {
        val existing = if (character.id > 0) {
            characterProfileDao.getCharacterById(character.id)
        } else {
            characterProfileDao.searchCharacters(character.name).firstOrNull()?.firstOrNull { it.name == character.name }
        }

        return if (existing != null) {
            val updated = character.copy(id = existing.id, updatedAt = System.currentTimeMillis())
            characterProfileDao.updateCharacter(updated)
            updated
        } else {
            val newId = characterProfileDao.insertCharacter(character.copy(id = 0, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            character.copy(id = newId)
        }
    }

    /**
     * 将故事中的世界观定制设定同步写回全局数据库 (Story -> DB)
     */
    suspend fun syncScenarioToDatabase(scenario: RoleplayScenario): RoleplayScenario {
        val existing = if (scenario.id > 0) {
            roleplayScenarioDao.getScenarioById(scenario.id)
        } else {
            roleplayScenarioDao.searchScenarios(scenario.name).firstOrNull()?.firstOrNull { it.name == scenario.name }
        }

        return if (existing != null) {
            val updated = scenario.copy(id = existing.id, updatedAt = System.currentTimeMillis())
            roleplayScenarioDao.updateScenario(updated)
            updated
        } else {
            val newId = roleplayScenarioDao.insertScenario(scenario.copy(id = 0, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            scenario.copy(id = newId)
        }
    }

    /**
     * 从全局数据库拉取最新角色设定覆盖故事中的本地定制 (DB -> Story)
     */
    suspend fun syncCharacterFromDatabase(session: RoleplaySession, characterId: Long): RoleplaySession {
        val targetChar = characterProfileDao.getCharacterById(characterId) ?: return session

        // 从 customCharacterData 中移除该角色的局部定制，使其恢复为数据库原始值
        val currentCustomized = session.getCustomizedCharacters(emptyList()).filterNot {
            (it.id > 0 && it.id == characterId) || it.name == targetChar.name
        }
        val newCustomJson = if (currentCustomized.isNotEmpty()) {
            com.google.gson.Gson().toJson(currentCustomized)
        } else null

        // 确保 characterIds 中包含该角色
        val charIds = session.getEffectiveCharacterIds().toMutableList()
        if (!charIds.contains(characterId)) {
            charIds.add(characterId)
        }
        val updatedSession = session.copy(
            characterIds = com.google.gson.Gson().toJson(charIds),
            customCharacterData = newCustomJson,
            updatedAt = System.currentTimeMillis()
        )
        roleplaySessionDao.updateSession(updatedSession)
        return updatedSession
    }

    /**
     * 从全局数据库拉取最新世界观设定覆盖故事中的本地定制 (DB -> Story)
     */
    suspend fun syncScenarioFromDatabase(session: RoleplaySession, scenarioId: Long): RoleplayScenario? {
        val targetScenario = roleplayScenarioDao.getScenarioById(scenarioId) ?: return null
        val updatedSession = session.copy(
            scenarioId = targetScenario.id,
            customScenarioData = null,
            updatedAt = System.currentTimeMillis()
        )
        roleplaySessionDao.updateSession(updatedSession)
        return targetScenario
    }

    // ============ 角色扮演记忆操作 ============

    fun getMemoriesBySession(sessionId: Long): Flow<List<RoleplayMemory>> =
        roleplayMemoryDao.getMemoriesBySession(sessionId)

    suspend fun getMemoriesListBySession(sessionId: Long): List<RoleplayMemory> =
        roleplayMemoryDao.getMemoriesListBySession(sessionId)

    fun getMemoriesByType(sessionId: Long, type: String): Flow<List<RoleplayMemory>> =
        roleplayMemoryDao.getMemoriesByType(sessionId, type)

    fun getPinnedMemories(sessionId: Long): Flow<List<RoleplayMemory>> =
        roleplayMemoryDao.getPinnedMemories(sessionId)

    suspend fun getMemoryById(id: Long): RoleplayMemory? = roleplayMemoryDao.getMemoryById(id)

    suspend fun insertMemory(memory: RoleplayMemory): Long = roleplayMemoryDao.insertMemory(memory)

    suspend fun updateMemory(memory: RoleplayMemory) = roleplayMemoryDao.updateMemory(memory)

    suspend fun deleteMemory(memory: RoleplayMemory) = roleplayMemoryDao.deleteMemory(memory)

    suspend fun deleteMemoryById(id: Long) = roleplayMemoryDao.deleteMemoryById(id)

    suspend fun deleteAllMemories(sessionId: Long) = roleplayMemoryDao.deleteAllMemories(sessionId)

    suspend fun setMemoryPinned(id: Long, isPinned: Boolean) = roleplayMemoryDao.setPinned(id, isPinned)

    suspend fun getPinnedFacts(sessionId: Long, limit: Int = 20): List<RoleplayMemory> =
        roleplayMemoryDao.getPinnedFacts(sessionId, limit)

    suspend fun getLatestSummary(sessionId: Long): RoleplayMemory? =
        roleplayMemoryDao.getLatestSummary(sessionId)

    // ============ 标签操作 ============

    fun getAllTags(): Flow<List<CharacterTag>> = characterTagDao.getAllTags()

    fun getTagsForCharacter(characterId: Long): Flow<List<CharacterTag>> =
        characterTagDao.getTagsForCharacter(characterId)

    fun getCharactersForTag(tagId: Long): Flow<List<CharacterProfile>> =
        characterTagDao.getCharactersForTag(tagId)

    suspend fun addTagToCharacter(characterId: Long, tagName: String) {
        val tag = characterTagDao.getTagByName(tagName) ?: run {
            val tagId = characterTagDao.insertTag(CharacterTag(name = tagName))
            characterTagDao.getTagById(tagId)!!
        }
        characterTagDao.insertCrossRef(CharacterTagCrossRef(characterId, tag.id))
    }

    suspend fun removeTagFromCharacter(characterId: Long, tagId: Long) {
        characterTagDao.deleteCrossRef(CharacterTagCrossRef(characterId, tagId))
    }

    suspend fun setCharacterTags(characterId: Long, tagNames: List<String>) {
        characterTagDao.deleteAllCrossRefsForCharacter(characterId)
        tagNames.forEach { tagName ->
            addTagToCharacter(characterId, tagName)
        }
    }

    // ============ 上下文组装 ============

    /**
     * 组装角色扮演上下文
     * 按照指定顺序组装：
     * 1. 全局创作规范与教学指引 (全局提示词)
     * 2. 本故事专属系统提示词 (系统提示词)
     * 3. 登场角色设定卡
     * 4. 世界观与场景卡
     * 5. 长期记忆与关键事实
     * 6. 当前剧情摘要与备忘
     * 7. 叙事模式与互动规范
     * 8. 用户最新消息或剧情提示
     */
    suspend fun assembleRoleplayContext(
        sessionId: Long,
        globalSystemPrompt: String?,
        userMessage: String?,
        globalRoleplayPrompt: String? = null,
        includeHistory: Boolean = true,
        maxHistoryMessages: Int = 50,
        queryText: String? = null
    ): String {
        val session = roleplaySessionDao.getSessionById(sessionId)
            ?: return userMessage ?: ""

        val parts = mutableListOf<String>()

        // 1. 全局创作规范与教学指引 (全局提示词)
        val teachingPrompt = if (!globalRoleplayPrompt.isNullOrBlank()) {
            globalRoleplayPrompt.trim()
        } else {
            DEFAULT_FICTION_TEACHING_GUIDELINES
        }
        parts.add("【全局创作规范与教学指引】\n$teachingPrompt")

        // 2. 本故事专属系统提示词 (会话级系统提示词)
        if (!globalSystemPrompt.isNullOrBlank()) {
            parts.add("【本故事专属系统提示词】\n${globalSystemPrompt.trim()}")
        }

        // 3. 角色卡 (支持多角色与故事编排，并支持本故事专属定制覆盖)
        // Local extracted characters remain effective even when no global character card is bound.
        val effectiveChars = getEffectiveCharactersForSession(session)
        val charPrompts = effectiveChars.map { buildCharacterCardPrompt(it) }
        if (charPrompts.isNotEmpty()) {
            parts.add("【登场角色设定 (${charPrompts.size}位)】\n\n" + charPrompts.joinToString("\n\n---\n\n"))
        }

        // 3. 世界观/场景设定 (支持本故事专属定制覆盖)
        val baseScenario = session.scenarioId?.let { roleplayScenarioDao.getScenarioById(it) }
        val effectiveScenario = session.getCustomizedScenario(baseScenario)
        if (effectiveScenario != null) {
            parts.add(buildScenarioCardPrompt(effectiveScenario))
        }

        // 4. 世界书设定 (如果启用了世界书，根据用户输入和当前剧情动态匹配词条)
        if (session.enableWorldBook && worldBookDao != null) {
            val scanText = listOfNotNull(
                queryText ?: userMessage,
                session.currentPlotSummary,
                teachingPrompt
            ).joinToString(" ")
            val bookIds = session.activeWorldBookIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
            val candidateEntries = if (!bookIds.isNullOrEmpty()) {
                worldBookDao.getActiveEntriesForBooks(bookIds)
            } else {
                worldBookDao.getActiveEntriesFromEnabledBooks()
            }
            val matchedEntries = candidateEntries.filter { it.matchesText(scanText) }
                .sortedWith(compareByDescending<WorldBookEntry> { it.priority }.thenByDescending { it.updatedAt })
                .take(15)
            if (matchedEntries.isNotEmpty()) {
                val loreLines = matchedEntries.map { "- 【${it.name}】${it.content.trim()}" }
                parts.add("【世界书设定】\n" + loreLines.joinToString("\n"))
            }
        }

        // 5. 剧情专属记忆与时间线
        val pinnedFacts = roleplayMemoryDao.getPinnedFacts(sessionId).map { it.content }
        val sessionMemories = memoryDao?.getCandidateMemories(session.conversationId)
            ?.filter { it.isEnabled && it.scope == "conversation" }
            ?.map { it.content } ?: emptyList()

        val allRoleplayMemories = (pinnedFacts + sessionMemories).distinct()
        if (allRoleplayMemories.isNotEmpty()) {
            // 时间线参照的“当前故事时间”必须是真实时间标签，严禁把剧情摘要 currentPlotSummary 当作时间注入
            // （否则【故事当前时间节点】会变成一段摘要，模型的时间记忆随之错乱）。
            // 优先取会话行上维护的 currentStoryTime，回退到会话记忆中的【当前故事时间】条目。
            val conversationStoryTime = conversationDao?.getConversationById(session.conversationId)
                ?.currentStoryTime?.takeIf { it.isNotBlank() && it != "未确定" }
            val memoryStoryTime = sessionMemories
                .firstOrNull { it.startsWith("【当前故事时间】：") || it.startsWith("当前故事时间：") }
                ?.substringAfter("：")?.trim()?.takeIf { it.isNotBlank() }
            val timelineContext = TimelineMemoryHelper.buildTimelinePromptContext(
                currentStoryTime = conversationStoryTime ?: memoryStoryTime,
                memoryContents = allRoleplayMemories
            )
            parts.add(timelineContext)
        }

        // 跨会话长期背景参考 (若明确开启，按相关性匹配注入)
        if (session.enableExternalMemory && memoryDao != null) {
            val effectiveQuery = (queryText ?: userMessage).orEmpty()
            val scanText = (if (effectiveQuery.isNotBlank()) effectiveQuery else session.currentPlotSummary).lowercase()
            val candidates = memoryDao.getCandidateMemories(session.conversationId).filter { it.isEnabled && it.scope == "user" }
            if (candidates.isNotEmpty()) {
                val matched = if (scanText.isNotBlank()) {
                    candidates.filter { mem ->
                        val keys = mem.keywords?.split(",") ?: emptyList()
                        keys.any { k -> k.isNotBlank() && scanText.contains(k.lowercase()) } ||
                        scanText.contains(mem.content.take(12).lowercase())
                    }.ifEmpty { candidates.take(5) }
                } else {
                    candidates.take(5)
                }
                if (matched.isNotEmpty()) {
                    val memLines = matched.map { "- ${it.content.trim()}" }
                    parts.add("【跨会话背景参考】\n" + memLines.joinToString("\n"))
                }
            }
        }

        // 6. 当前剧情摘要
        if (session.currentPlotSummary.isNotBlank()) {
            parts.add("【当前剧情摘要】\n${session.currentPlotSummary}")
        }

        // 7. 叙事模式说明
        parts.add(buildNarrativeModePrompt(session.narrativeMode))

        // 8. 用户最新消息或剧情提示
        if (!userMessage.isNullOrBlank()) {
            parts.add("【用户输入】\n$userMessage")
        }

        return parts.joinToString("\n\n")
    }

    /**
     * 构建角色卡提示词
     */
    private fun buildCharacterCardPrompt(character: CharacterProfile): String {
        val parts = mutableListOf<String>()
        parts.add("【角色设定】")
        parts.add("角色名称：${character.name}")

        if (character.identity.isNotBlank()) parts.add("身份/职业：${character.identity}")
        if (character.personality.isNotBlank()) parts.add("性格特征：${character.personality}")
        if (character.background.isNotBlank()) parts.add("背景故事：${character.background}")
        if (character.speakingStyle.isNotBlank()) parts.add("说话方式：${character.speakingStyle}")
        if (character.goals.isNotBlank()) parts.add("目标动机：${character.goals}")
        if (character.relationships.isNotBlank()) parts.add("关系设定：${character.relationships}")
        if (character.knowledge.isNotBlank()) parts.add("知识边界：${character.knowledge}")
        if (character.constraints.isNotBlank()) parts.add("禁止违背：${character.constraints}")
        if (character.behaviorRules.isNotBlank()) parts.add("行为约束：${character.behaviorRules}")
        if (character.greeting.isNotBlank()) parts.add("初始问候：${character.greeting}")
        if (character.exampleDialogue.isNotBlank()) parts.add("示例对话：${character.exampleDialogue}")

        parts.add("\n【文学创作与角色演绎铁律 (Show, Don't Tell)】")
        parts.add("1. 严禁在动作描写中直接使用生硬性格副词（如禁止输出“强硬地”、“冷酷地”、“温柔地”、“傲娇地”等词汇）。")
        parts.add("2. 必须通过角色的眼神、微表情、肢体动作、用词节奏、呼吸停顿以及选择性沉默来生动展现性格。")
        parts.add("3. 严格保持角色独立性：严禁代为描写用户的发言、动作、内心决定或情绪。")
        parts.add("4. 采用小说级沉浸排版：动作描写与台词对话分段交替。")

        return parts.joinToString("\n")
    }

    /**
     * 构建场景卡提示词
     */
    private fun buildScenarioCardPrompt(scenario: RoleplayScenario): String {
        val parts = mutableListOf<String>()
        parts.add("【场景设定】")
        parts.add("场景名称：${scenario.name}")

        if (scenario.worldview.isNotBlank()) parts.add("世界观：${scenario.worldview}")
        if (scenario.time.isNotBlank()) parts.add("时间：${scenario.time}")
        if (scenario.location.isNotBlank()) parts.add("地点：${scenario.location}")
        if (scenario.environment.isNotBlank()) parts.add("环境描述：${scenario.environment}")
        if (scenario.premise.isNotBlank()) parts.add("剧情前提：${scenario.premise}")
        if (scenario.rules.isNotBlank()) parts.add("世界规则：${scenario.rules}")
        if (scenario.relationshipState.isNotBlank()) parts.add("关系状态：${scenario.relationshipState}")
        if (scenario.conflict.isNotBlank()) parts.add("当前冲突：${scenario.conflict}")
        if (scenario.plotGoal.isNotBlank()) parts.add("剧情目标：${scenario.plotGoal}")
        if (scenario.atmosphere.isNotBlank()) parts.add("叙事氛围：${scenario.atmosphere}")
        if (scenario.narrativePerspective.isNotBlank()) parts.add("叙事视角：${scenario.narrativePerspective}")
        if (scenario.outputFormat.isNotBlank()) parts.add("输出格式：${scenario.outputFormat}")
        if (scenario.contentRestrictions.isNotBlank()) parts.add("内容限制：${scenario.contentRestrictions}")
        if (scenario.openingPrompt.isNotBlank()) parts.add("开场提示：${scenario.openingPrompt}")

        return parts.joinToString("\n")
    }

    /**
     * 构建叙事模式提示词
     */
    fun buildNarrativeModePrompt(mode: String): String {
        return Companion.buildNarrativeModePrompt(mode)
    }

    /**
     * 处理剧情提示
     */
    suspend fun processPlotAction(
        sessionId: Long,
        action: PlotAction,
        customInstruction: String?
    ): String {
        val session = roleplaySessionDao.getSessionById(sessionId)
            ?: return ""
        return buildPlotActionPrompt(action, customInstruction)
    }

    /**
     * 保存剧情摘要
     */
    suspend fun savePlotSummary(sessionId: Long, summary: String) {
        val session = roleplaySessionDao.getSessionById(sessionId) ?: return
        val existingSummary = roleplayMemoryDao.getLatestSummary(sessionId)

        if (existingSummary != null) {
            roleplayMemoryDao.updateMemory(
                existingSummary.copy(
                    content = summary,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            roleplayMemoryDao.insertMemory(
                RoleplayMemory(
                    sessionId = sessionId,
                    memoryType = "summary",
                    content = summary
                )
            )
        }

        roleplaySessionDao.updateMemoryState(
            id = sessionId,
            summary = summary,
            pinnedFacts = session.pinnedFacts
        )
    }

    /**
     * 添加固定事实
     */
    suspend fun addPinnedFact(sessionId: Long, fact: String) {
        roleplayMemoryDao.insertMemory(
            RoleplayMemory(
                sessionId = sessionId,
                memoryType = "fact",
                content = fact,
                isPinned = true
            )
        )
    }

    /**
     * 清除会话记忆
     */
    suspend fun clearSessionMemories(sessionId: Long) {
        roleplayMemoryDao.deleteAllMemories(sessionId)
        roleplaySessionDao.updateMemoryState(
            id = sessionId,
            summary = "",
            pinnedFacts = null
        )
    }

    /**
     * 检查是否为角色扮演会话
     */
    suspend fun isRoleplaySession(conversationId: Long): Boolean {
        return roleplaySessionDao.getSessionByConversationId(conversationId) != null
    }

    /**
     * 获取会话的角色和场景信息
     */
    suspend fun getSessionInfo(conversationId: Long): Triple<RoleplaySession?, CharacterProfile?, RoleplayScenario?> {
        val session = roleplaySessionDao.getSessionByConversationId(conversationId)
        val character = session?.characterId?.let { characterProfileDao.getCharacterById(it) }
        val scenario = session?.scenarioId?.let { roleplayScenarioDao.getScenarioById(it) }
        return Triple(session, character, scenario)
    }

    companion object {
        const val DEFAULT_FICTION_TEACHING_GUIDELINES = """你是一位具备深厚文学功底与戏剧编排能力的创作导师与沉浸式演绎专家。请遵循以下行文创作规范：
1. 【以演代述 (Show, Don't Tell)】：严禁在动作描写中直接使用生硬性格副词标签。通过角色的眼神、微表情、肢体动作、用词节奏、声调变化、呼吸停顿以及选择性沉默展现内心世界。
2. 【台词与动作交融】：台词契合角色身份经历，富有生活温度与张力，避免空洞说教或假大空独白。
3. 【世界观沉浸度】：严格遵守当前场景与世界设定的物理法则、社会关系与时代背景，严禁出戏或产生违背设定的现代/异质词汇。
4. 【用户主导与留白互动】：严禁擅自代替用户发言或替用户角色做主观决定；段落结尾保持情节动力，为用户留出推进空间。"""

        fun buildNarrativeModePrompt(mode: String): String {
            return when (NarrativeMode.fromValue(mode)) {
                NarrativeMode.CHARACTER -> {
                    "【叙事模式：角色内指令】\n" +
                    "你将完全以登场角色的身份做出回应与互动，严格沉浸在角色设定中，使用角色的口吻习惯与语言风格。"
                }
                NarrativeMode.AUTHOR -> {
                    "【叙事模式：作者/导演指令】\n" +
                    "用户为主导故事的大纲导演，你负责根据用户的剧情指示推进故事发展，精细铺陈所有登场角色的互动与情节进展。"
                }
                NarrativeMode.NARRATOR -> {
                    "【叙事模式：旁白模式】\n" +
                    "你只负责客观环境描写与第三人称剧情旁白，不代替用户做决定，不替用户角色做主观发言。"
                }
                NarrativeMode.MULTI -> {
                    "【叙事模式：多角色群像模式】\n" +
                    "多位登场角色共同参与情节互动。模型需根据各角色的独立人设、立场、动机与说话风格，生动推演群像戏，展现角色间交锋、合作与多方互动。"
                }
            }
        }

        fun buildPlotActionPrompt(action: PlotAction, customInstruction: String? = null): String {
            return when (action) {
                PlotAction.CONTINUE -> "请紧接上文末尾，顺畅自然地继续向下推进剧情，保持文风、角色人设与细节描写连贯，不要重复已有内容。"
                PlotAction.REGENERATE -> "请重新生成上一段内容，保持角色设定和场景一致性。"
                PlotAction.REWRITE -> "请改写上一段内容，可以调整细节但保持剧情走向。"
                PlotAction.EXTEND -> "请延长当前内容，增加更多细节描写和对话。"
                PlotAction.SHORTEN -> "请缩短当前内容，保留核心情节和关键对话。"
                PlotAction.CHANGE_PERSPECTIVE -> "请从不同的叙事视角重写当前内容。"
                PlotAction.CHANGE_TONE -> "请改变当前内容的语气和氛围。"
                PlotAction.BRANCH -> "请从当前点开始一个新的剧情分支。"
                PlotAction.ROLLBACK -> "请回到上一个版本重新开始。"
                PlotAction.SUMMARY -> "请总结提炼当前所有剧情进展与核心事实摘要，包括登场人物状态变化、关键剧情转折与未解决的伏笔。"
                PlotAction.BRANCH_CHOICES -> "【导演剧情分支决策】请不要直接输出单一走向。请基于当前局势与角色动机，提供 3~4 个不同节奏和方向的剧情分支选项（例如：A. 正面冲突方向；B. 智取暗中调查方向；C. 意外第三方介入方向；D. 情感转折方向），每个选项简要说明剧情走向预测与潜在风险。等待我做出选择后再正式展开后续详尽剧情。"
                PlotAction.DIALOGUE_ONLY -> "请只生成角色的对话，不要添加旁白和动作描写。"
                PlotAction.NARRATION_ONLY -> "请只生成旁白和环境描写，不要生成角色对话。"
                PlotAction.DIALOGUE_ACTION -> "请生成角色对话和动作描写，不要添加旁白。"
                PlotAction.CUSTOM -> customInstruction ?: "请继续。"
            }
        }
    }
}
