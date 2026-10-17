package com.aiassistant.ui.screens.roleplay

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.aiassistant.AiAssistantApp
import com.aiassistant.data.local.AppDatabase
import com.aiassistant.data.repository.RoleplayRepository
import com.aiassistant.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RoleplayViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = RoleplayRepository(
        characterProfileDao = database.characterProfileDao(),
        roleplayScenarioDao = database.roleplayScenarioDao(),
        roleplaySessionDao = database.roleplaySessionDao(),
        roleplayMemoryDao = database.roleplayMemoryDao(),
        characterTagDao = database.characterTagDao(),
        conversationDao = database.conversationDao(),
        messageDao = database.messageDao()
    )

    // ============ 角色卡相关状态 ============
    private val _characters = MutableStateFlow<List<CharacterProfile>>(emptyList())
    val characters: StateFlow<List<CharacterProfile>> = _characters.asStateFlow()
    val allCharacters: StateFlow<List<CharacterProfile>> = repository.getAllCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCharacter = MutableStateFlow<CharacterProfile?>(null)
    val selectedCharacter: StateFlow<CharacterProfile?> = _selectedCharacter.asStateFlow()

    private val _characterSearchQuery = MutableStateFlow("")
    val characterSearchQuery: StateFlow<String> = _characterSearchQuery.asStateFlow()

    private val _characterTags = MutableStateFlow<List<CharacterTag>>(emptyList())
    val characterTags: StateFlow<List<CharacterTag>> = _characterTags.asStateFlow()

    // ============ 场景卡相关状态 ============
    private val _scenarios = MutableStateFlow<List<RoleplayScenario>>(emptyList())
    val scenarios: StateFlow<List<RoleplayScenario>> = _scenarios.asStateFlow()
    val allScenarios: StateFlow<List<RoleplayScenario>> = repository.getAllScenarios()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedScenario = MutableStateFlow<RoleplayScenario?>(null)
    val selectedScenario: StateFlow<RoleplayScenario?> = _selectedScenario.asStateFlow()

    private val _scenarioSearchQuery = MutableStateFlow("")
    val scenarioSearchQuery: StateFlow<String> = _scenarioSearchQuery.asStateFlow()

    // ============ 会话相关状态 ============
    private val _sessions = MutableStateFlow<List<RoleplaySession>>(emptyList())
    val sessions: StateFlow<List<RoleplaySession>> = _sessions.asStateFlow()
    val conversations: StateFlow<List<Conversation>> = database.conversationDao()
        .getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentSession = MutableStateFlow<RoleplaySession?>(null)
    val currentSession: StateFlow<RoleplaySession?> = _currentSession.asStateFlow()

    // ============ 记忆相关状态 ============
    private val _memories = MutableStateFlow<List<RoleplayMemory>>(emptyList())
    val memories: StateFlow<List<RoleplayMemory>> = _memories.asStateFlow()

    private val _pinnedFacts = MutableStateFlow<List<RoleplayMemory>>(emptyList())
    val pinnedFacts: StateFlow<List<RoleplayMemory>> = _pinnedFacts.asStateFlow()

    private val _plotSummary = MutableStateFlow("")
    val plotSummary: StateFlow<String> = _plotSummary.asStateFlow()

    // ============ UI状态 ============
    private val _uiState = MutableStateFlow<RoleplayUiState>(RoleplayUiState.Idle)
    val uiState: StateFlow<RoleplayUiState> = _uiState.asStateFlow()

    private val _narrativeMode = MutableStateFlow(NarrativeMode.CHARACTER)
    val narrativeMode: StateFlow<NarrativeMode> = _narrativeMode.asStateFlow()

    // 角色与创作页面选中的 Tab (0: 故事, 1: 角色, 2: 世界观)，返回时保持所在 Tab
    var studioTab by androidx.compose.runtime.mutableIntStateOf(0)

    init {
        loadCharacters()
        loadScenarios()
        loadSessions()
    }

    // ============ 角色卡操作 ============

    private var charactersJob: Job? = null

    private fun loadCharacters() {
        charactersJob?.cancel()
        charactersJob = viewModelScope.launch {
            repository.getAllCharacters().collect { list ->
                _characters.value = list
            }
        }
    }

    // v2.7.3 流畅度：搜索逐字符触发，原先每次输入都新增一条永不取消的 Flow 订阅，
    // 且陈旧查询会在角色表变更时竞写列表；改为先取消旧订阅，最新查询获胜
    private var characterSearchJob: Job? = null

    fun searchCharacters(query: String) {
        _characterSearchQuery.value = query
        charactersJob?.cancel()
        characterSearchJob?.cancel()
        characterSearchJob = viewModelScope.launch {
            if (query.isBlank()) {
                repository.getAllCharacters().collect { list ->
                    _characters.value = list
                }
            } else {
                repository.searchCharacters(query).collect { list ->
                    _characters.value = list
                }
            }
        }
    }

    fun selectCharacter(character: CharacterProfile) {
        _selectedCharacter.value = character
        loadCharacterTags(character.id)
    }

    fun clearSelectedCharacter() {
        _selectedCharacter.value = null
        _characterTags.value = emptyList()
    }

    fun saveCharacter(character: CharacterProfile, onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                _uiState.value = RoleplayUiState.Saving
                if (character.id == 0L) {
                    repository.insertCharacter(character)
                } else {
                    repository.updateCharacter(character)
                }
                _uiState.value = RoleplayUiState.SaveSuccess("角色保存成功")
                onResult?.invoke(true, null)
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("保存角色失败: ${e.message}")
                onResult?.invoke(false, e.message)
            }
        }
    }

    fun deleteCharacter(character: CharacterProfile) {
        viewModelScope.launch {
            try {
                repository.deleteCharacter(character)
                if (_selectedCharacter.value?.id == character.id) {
                    _selectedCharacter.value = null
                }
                _uiState.value = RoleplayUiState.SaveSuccess("角色已删除")
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("删除角色失败: ${e.message}")
            }
        }
    }

    fun setCharacterFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setCharacterFavorite(id, isFavorite)
        }
    }

    fun setDefaultCharacter(id: Long) {
        viewModelScope.launch {
            repository.setDefaultCharacter(id)
        }
    }

    // ============ 标签操作 ============

    // v2.7.3 流畅度：角色被重复选中/标签增删后反复调用，原先每次叠加一条 collect；
    // 改为单订阅，切换角色时取消旧订阅
    private var characterTagsJob: Job? = null

    private fun loadCharacterTags(characterId: Long) {
        characterTagsJob?.cancel()
        characterTagsJob = viewModelScope.launch {
            repository.getTagsForCharacter(characterId).collect { tags ->
                _characterTags.value = tags
            }
        }
    }

    fun addTagToCharacter(characterId: Long, tagName: String) {
        viewModelScope.launch {
            repository.addTagToCharacter(characterId, tagName)
            loadCharacterTags(characterId)
        }
    }

    fun removeTagFromCharacter(characterId: Long, tagId: Long) {
        viewModelScope.launch {
            repository.removeTagFromCharacter(characterId, tagId)
            loadCharacterTags(characterId)
        }
    }

    fun setCharacterTags(characterId: Long, tagNames: List<String>) {
        viewModelScope.launch {
            repository.setCharacterTags(characterId, tagNames)
            loadCharacterTags(characterId)
        }
    }

    // ============ 场景卡操作 ============

    private var scenariosJob: Job? = null

    private fun loadScenarios() {
        scenariosJob?.cancel()
        scenariosJob = viewModelScope.launch {
            repository.getAllScenarios().collect { list ->
                _scenarios.value = list
            }
        }
    }

    // v2.7.3 流畅度：同 searchCharacters，取消旧订阅防叠加与竞写
    private var scenarioSearchJob: Job? = null

    fun searchScenarios(query: String) {
        _scenarioSearchQuery.value = query
        scenariosJob?.cancel()
        scenarioSearchJob?.cancel()
        scenarioSearchJob = viewModelScope.launch {
            if (query.isBlank()) {
                repository.getAllScenarios().collect { list ->
                    _scenarios.value = list
                }
            } else {
                repository.searchScenarios(query).collect { list ->
                    _scenarios.value = list
                }
            }
        }
    }

    fun selectScenario(scenario: RoleplayScenario) {
        _selectedScenario.value = scenario
    }

    fun clearSelectedScenario() {
        _selectedScenario.value = null
    }

    fun saveScenario(scenario: RoleplayScenario, onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                _uiState.value = RoleplayUiState.Saving
                if (scenario.id == 0L) {
                    repository.insertScenario(scenario)
                } else {
                    repository.updateScenario(scenario)
                }
                _uiState.value = RoleplayUiState.SaveSuccess("场景保存成功")
                onResult?.invoke(true, null)
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("保存场景失败: ${e.message}")
                onResult?.invoke(false, e.message)
            }
        }
    }

    fun deleteScenario(scenario: RoleplayScenario) {
        viewModelScope.launch {
            try {
                repository.deleteScenario(scenario)
                if (_selectedScenario.value?.id == scenario.id) {
                    _selectedScenario.value = null
                }
                _uiState.value = RoleplayUiState.SaveSuccess("场景已删除")
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("删除场景失败: ${e.message}")
            }
        }
    }

    fun setScenarioFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setScenarioFavorite(id, isFavorite)
        }
    }

    // ============ 会话操作 ============

    private var sessionsJob: Job? = null

    private fun loadSessions() {
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            repository.getAllSessions().collect { list ->
                _sessions.value = list
            }
        }
    }

    fun createSession(
        characterId: Long?,
        scenarioId: Long?,
        conversationId: Long,
        narrativeMode: NarrativeMode = NarrativeMode.CHARACTER
    ) {
        viewModelScope.launch {
            try {
                val session = RoleplaySession(
                    characterId = characterId,
                    scenarioId = scenarioId,
                    conversationId = conversationId,
                    narrativeMode = narrativeMode.value
                )
                val sessionId = repository.insertSession(session)
                _currentSession.value = repository.getSessionById(sessionId)
                _narrativeMode.value = narrativeMode
                loadMemories(sessionId)
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("创建会话失败: ${e.message}")
            }
        }
    }

    fun createRoleplaySessionAndStart(
        characterId: Long?,
        scenarioId: Long?,
        apiConfigId: Long,
        narrativeMode: NarrativeMode,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = RoleplayUiState.Saving
                val character = characterId?.let { repository.getCharacterById(it) }
                val scenario = scenarioId?.let { repository.getScenarioById(it) }
                val apiConfig = (database.apiConfigDao().getConfigById(apiConfigId)?.takeIf { it.isEnabled }
                    ?: database.apiConfigDao().getDefaultConfig()?.takeIf { it.isEnabled }
                    ?: database.apiConfigDao().getEnabledConfigs().first().firstOrNull())
                    ?: throw IllegalStateException("未找到有效的 API 配置（或已被停用）")

                val title = when {
                    character != null && scenario != null -> "${character.name} · ${scenario.name}"
                    character != null -> "角色：${character.name}"
                    scenario != null -> "场景：${scenario.name}"
                    else -> "角色扮演会话"
                }

                val conversation = Conversation(
                    title = title,
                    enableSessionMemory = true,
                    apiConfigId = apiConfig.id,
                    modelName = apiConfig.modelName,
                    temperature = apiConfig.temperature ?: 0.95f,
                    maxTokens = apiConfig.maxTokens ?: 8192,
                    topP = apiConfig.topP ?: 1.0f,
                    enableThinking = apiConfig.enableThinking ?: true,
                    thinkingEffort = apiConfig.thinkingEffort ?: "high",
                    enableWebSearch = apiConfig.enableWebSearch ?: false
                )

                val (conversationId, sessionId) = database.withTransaction {
                    val conversationId = database.conversationDao().insertConversation(conversation)
                    val roleplaySession = RoleplaySession(
                        characterId = character?.id,
                        characterIds = com.google.gson.Gson().toJson(listOfNotNull(character?.id)),
                        scenarioId = scenario?.id,
                        conversationId = conversationId,
                        narrativeMode = narrativeMode.value,
                        customCharacterData = character?.let { com.google.gson.Gson().toJson(listOf(it)) },
                        customScenarioData = scenario?.let { com.google.gson.Gson().toJson(it) },
                        currentPlotSummary = scenario?.premise.orEmpty()
                    )
                    conversationId to repository.insertSession(roleplaySession)
                }
                _currentSession.value = repository.getSessionById(sessionId)

                _uiState.value = RoleplayUiState.Idle
                onSuccess(conversationId)
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("创建会话失败: ${e.message}")
            }
        }
    }

    fun deleteCharacters(ids: List<Long>) {
        viewModelScope.launch {
            try {
                repository.deleteCharactersByIds(ids)
                _uiState.value = RoleplayUiState.SaveSuccess("已批量删除 ${ids.size} 位角色")
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("删除角色失败: ${e.message}")
            }
        }
    }

    fun deleteScenarios(ids: List<Long>) {
        viewModelScope.launch {
            try {
                repository.deleteScenariosByIds(ids)
                _uiState.value = RoleplayUiState.SaveSuccess("已批量删除 ${ids.size} 个世界观")
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("删除世界观失败: ${e.message}")
            }
        }
    }

    fun createStorySessionAndStart(
        characterIds: List<Long>,
        scenarioId: Long?,
        apiConfigId: Long,
        modelName: String? = null,
        narrativeMode: NarrativeMode,
        onSuccess: (Long) -> Unit,
        titleOverride: String = "",
        writingInstructions: String = ""
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = RoleplayUiState.Saving
                val characters = repository.getCharactersByIds(characterIds)
                val scenario = scenarioId?.let { repository.getScenarioById(it) }
                val apiConfig = (database.apiConfigDao().getConfigById(apiConfigId)?.takeIf { it.isEnabled }
                    ?: database.apiConfigDao().getDefaultConfig()?.takeIf { it.isEnabled }
                    ?: database.apiConfigDao().getEnabledConfigs().first().firstOrNull())
                    ?: throw IllegalStateException("未找到有效的 API 配置（或已被停用）")

                val title = when {
                    characters.isNotEmpty() && scenario != null -> {
                        val names = characters.take(2).joinToString("、") { it.name } + if (characters.size > 2) "等" else ""
                        "$names · ${scenario.name}"
                    }
                    characters.isNotEmpty() -> {
                        "故事：" + characters.take(3).joinToString("、") { it.name }
                    }
                    scenario != null -> "世界观：${scenario.name}"
                    else -> "故事创作会话"
                }

                val targetModelName = modelName?.ifBlank { null } ?: apiConfig.modelName

                val conversation = Conversation(
                    title = titleOverride.trim().ifBlank { title },
                    systemPrompt = writingInstructions.trim().ifBlank { null },
                    enableSessionMemory = true,
                    apiConfigId = apiConfig.id,
                    modelName = targetModelName,
                    temperature = apiConfig.temperature ?: 0.95f,
                    maxTokens = apiConfig.maxTokens ?: 8192,
                    topP = apiConfig.topP ?: 1.0f,
                    enableThinking = apiConfig.enableThinking ?: true,
                    thinkingEffort = apiConfig.thinkingEffort ?: "high",
                    enableWebSearch = apiConfig.enableWebSearch ?: false
                )

                val (conversationId, sessionId) = database.withTransaction {
                    val conversationId = database.conversationDao().insertConversation(conversation)
                    val roleplaySession = RoleplaySession(
                        characterId = characters.firstOrNull()?.id,
                        characterIds = com.google.gson.Gson().toJson(characters.map { it.id }.distinct()),
                        scenarioId = scenario?.id,
                        conversationId = conversationId,
                        narrativeMode = narrativeMode.value,
                        customCharacterData = characters.takeIf { it.isNotEmpty() }?.let { com.google.gson.Gson().toJson(it) },
                        customScenarioData = scenario?.let { com.google.gson.Gson().toJson(it) },
                        currentPlotSummary = scenario?.premise.orEmpty()
                    )
                    conversationId to repository.insertSession(roleplaySession)
                }
                _currentSession.value = repository.getSessionById(sessionId)

                // 初始问候语不自动插入，由用户在故事中自主发起与掌控
                _uiState.value = RoleplayUiState.Idle
                onSuccess(conversationId)
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("创建故事会话失败: ${e.message}")
            }
        }
    }

    /**
     * 在故事列表中直接修改故事绑定的世界观、登场角色与叙事模式
     */
    fun updateStorySessionContext(
        session: RoleplaySession,
        newCharacterIds: List<Long>,
        newScenarioId: Long?,
        newNarrativeMode: NarrativeMode? = null,
        newPlotSummary: String? = null
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = RoleplayUiState.Saving
                val current = repository.getSessionById(session.id) ?: error("当前故事已不存在")
                val characters = (current.getCustomizedCharacters(repository.getEffectiveCharactersForSession(current)) +
                    repository.getCharactersByIds(newCharacterIds.filter { it > 0 })).distinctBy { it.id }
                val selected = StorySessionBindings.select(current, characters, newCharacterIds, newScenarioId,
                    newNarrativeMode ?: NarrativeMode.fromValue(current.narrativeMode), newPlotSummary ?: current.currentPlotSummary)
                val updatedSession = if (selected.customScenarioData == null && selected.scenarioId != null) {
                    selected.copy(customScenarioData = repository.getScenarioById(selected.scenarioId)
                        ?.let { com.google.gson.Gson().toJson(it) })
                } else selected

                repository.updateSession(updatedSession)
                _currentSession.value = updatedSession
                loadSessions()
                _uiState.value = RoleplayUiState.SaveSuccess("已成功更新故事设定与登场角色")
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("更新故事设定失败: ${e.message}")
            }
        }
    }

    /**
     * 将故事中角色的定制设定同步保存至全局角色库 (Story -> DB)
     */
    fun syncCharacterToDatabase(character: CharacterProfile, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val saved = repository.syncCharacterToDatabase(character)
                loadCharacters()
                onComplete("已将【${saved.name}】同步保存至全局角色库")
            } catch (e: Exception) {
                onComplete("同步失败: ${e.message}")
            }
        }
    }

    /**
     * 将故事中世界观的定制设定同步保存至全局世界观库 (Story -> DB)
     */
    fun syncScenarioToDatabase(scenario: RoleplayScenario, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val saved = repository.syncScenarioToDatabase(scenario)
                loadScenarios()
                onComplete("已将【${saved.name}】同步保存至全局世界观库")
            } catch (e: Exception) {
                onComplete("同步失败: ${e.message}")
            }
        }
    }

    /**
     * 从全局角色库拉取最新设定，覆盖故事中的本地定制 (DB -> Story)
     */
    fun syncCharacterFromDatabase(session: RoleplaySession, characterId: Long, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.syncCharacterFromDatabase(session, characterId)
                loadSessions()
                onComplete("已从全局角色库重新拉取最新设定")
            } catch (e: Exception) {
                onComplete("同步失败: ${e.message}")
            }
        }
    }

    /**
     * 从全局世界观库拉取最新设定，覆盖故事中的本地定制 (DB -> Story)
     */
    fun syncScenarioFromDatabase(session: RoleplaySession, scenarioId: Long, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.syncScenarioFromDatabase(session, scenarioId)
                loadSessions()
                onComplete("已从全局世界观库重新拉取最新设定")
            } catch (e: Exception) {
                onComplete("同步失败: ${e.message}")
            }
        }
    }

    fun importAnalyzedBundleWithConflictResolution(
        characters: List<CharacterProfile>,
        scenario: RoleplayScenario?,
        resolutionMap: Map<String, ConflictAction>,
        narrativeMode: NarrativeMode = NarrativeMode.CHARACTER,
        startSession: Boolean,
        apiConfigId: Long? = null,
        onSessionCreated: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = RoleplayUiState.Saving
                val (finalCharacterIds, scenarioId) = database.withTransaction {
                    val ids = characters.map { incoming ->
                        val action = resolutionMap[incoming.name.trim()] ?: ConflictAction.CREATE_COPY
                        repository.importCharacter(incoming, createCopy = action == ConflictAction.CREATE_COPY,
                            overwrite = action == ConflictAction.OVERWRITE).id
                    }.distinct()
                    ids to scenario?.let { repository.importScenario(it).id }
                }

                if (startSession && (finalCharacterIds.isNotEmpty() || scenarioId != null)) {
                    val targetApiId = apiConfigId ?: (database.apiConfigDao().getDefaultConfig()?.takeIf { it.isEnabled }
                        ?: database.apiConfigDao().getEnabledConfigs().first().firstOrNull())?.id

                    if (targetApiId != null) {
                        createStorySessionAndStart(
                            characterIds = finalCharacterIds,
                            scenarioId = scenarioId,
                            apiConfigId = targetApiId,
                            narrativeMode = narrativeMode,
                            onSuccess = { conversationId ->
                                _uiState.value = RoleplayUiState.SaveSuccess("已成功入库并开启故事创作")
                                onSessionCreated?.invoke(conversationId)
                            }
                        )
                        return@launch
                    }
                }

                _uiState.value = RoleplayUiState.SaveSuccess(
                    "已成功导入/融合 ${characters.size} 位角色" + (if (scenario != null) "与世界观 [${scenario.name}]" else "")
                )
                onSessionCreated?.invoke(-1L)
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            _currentSession.value = session
            if (session != null) {
                _narrativeMode.value = NarrativeMode.fromValue(session.narrativeMode)
                _plotSummary.value = session.currentPlotSummary
                loadMemories(sessionId)
                loadPinnedFacts(sessionId)
            }
        }
    }

    fun loadSessionByConversationId(conversationId: Long) {
        viewModelScope.launch {
            val session = repository.getSessionByConversationId(conversationId)
            _currentSession.value = session
            if (session != null) {
                _narrativeMode.value = NarrativeMode.fromValue(session.narrativeMode)
                _plotSummary.value = session.currentPlotSummary
                loadMemories(session.id)
                loadPinnedFacts(session.id)
                // 加载关联的角色和场景
                session.characterId?.let { characterId ->
                    repository.getCharacterById(characterId)?.let { _selectedCharacter.value = it }
                }
                session.scenarioId?.let { scenarioId ->
                    repository.getScenarioById(scenarioId)?.let { _selectedScenario.value = it }
                }
            }
        }
    }

    fun deleteSession(session: RoleplaySession) {
        viewModelScope.launch {
            try {
                // 与普通会话共用事务删除：取消生成并一并清除消息、会话记忆和分支。
                // 只删除 RoleplaySession 会留下“失去故事身份”的聊天与残留记录。
                AiAssistantApp.instance.repository.deleteConversation(session.conversationId)
                if (_currentSession.value?.id == session.id) {
                    _currentSession.value = null
                }
                _uiState.value = RoleplayUiState.SaveSuccess("会话已删除")
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("删除会话失败: ${e.message}")
            }
        }
    }

    fun renameStory(conversationId: Long, title: String) {
        val normalized = title.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            try {
                AiAssistantApp.instance.repository.updateConversationTitle(conversationId, normalized)
                _uiState.value = RoleplayUiState.SaveSuccess("故事已重命名")
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("重命名失败: ${e.message}")
            }
        }
    }

    fun setStoryPinned(conversationIds: Collection<Long>, pinned: Boolean) {
        viewModelScope.launch {
            try {
                conversationIds.forEach { database.conversationDao().setPinned(it, pinned) }
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("置顶操作失败: ${e.message}")
            }
        }
    }

    fun duplicateStory(conversationId: Long, onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val id = AiAssistantApp.instance.repository.duplicateConversation(conversationId)
                if (id > 0) {
                    _uiState.value = RoleplayUiState.SaveSuccess("已复制故事、消息及设定")
                    onSuccess(id)
                } else {
                    _uiState.value = RoleplayUiState.Error("原故事不存在")
                }
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("复制故事失败: ${e.message}")
            }
        }
    }

    fun hideStories(conversationIds: Collection<Long>) {
        viewModelScope.launch {
            try {
                AiAssistantApp.instance.repository.setConversationsHidden(conversationIds, true)
                _uiState.value = RoleplayUiState.SaveSuccess("故事已隐藏，可在会话的隐藏列表恢复")
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("隐藏故事失败: ${e.message}")
            }
        }
    }

    fun deleteStories(conversationIds: Collection<Long>) {
        viewModelScope.launch {
            try {
                conversationIds.forEach { conversationId ->
                    AiAssistantApp.instance.repository.deleteConversation(conversationId)
                }
                _uiState.value = RoleplayUiState.SaveSuccess("已删除 ${conversationIds.size} 个故事及其对话记录")
            } catch (e: Exception) {
                _uiState.value = RoleplayUiState.Error("删除故事失败: ${e.message}")
            }
        }
    }

    fun setNarrativeMode(mode: NarrativeMode) {
        _narrativeMode.value = mode
        _currentSession.value?.let { session ->
            viewModelScope.launch {
                repository.updateSession(session.copy(narrativeMode = mode.value))
            }
        }
    }

    // ============ 记忆操作 ============

    // v2.7.3 流畅度：同 loadCharacterTags，单订阅 + 切换会话取消旧订阅
    private var memoriesJob: Job? = null

    private fun loadMemories(sessionId: Long) {
        memoriesJob?.cancel()
        memoriesJob = viewModelScope.launch {
            repository.getMemoriesBySession(sessionId).collect { list ->
                _memories.value = list
            }
        }
    }

    private fun loadPinnedFacts(sessionId: Long) {
        viewModelScope.launch {
            _pinnedFacts.value = repository.getPinnedFacts(sessionId)
        }
    }

    fun addMemory(sessionId: Long, type: String, content: String, sourceMessageId: Long? = null) {
        viewModelScope.launch {
            repository.insertMemory(
                RoleplayMemory(
                    sessionId = sessionId,
                    memoryType = type,
                    content = content,
                    sourceMessageId = sourceMessageId
                )
            )
            loadMemories(sessionId)
        }
    }

    fun updateMemory(memory: RoleplayMemory) {
        viewModelScope.launch {
            repository.updateMemory(memory)
            loadMemories(memory.sessionId)
        }
    }

    fun deleteMemory(memory: RoleplayMemory) {
        viewModelScope.launch {
            repository.deleteMemory(memory)
            loadMemories(memory.sessionId)
        }
    }

    fun setMemoryPinned(id: Long, isPinned: Boolean) {
        viewModelScope.launch {
            repository.setMemoryPinned(id, isPinned)
            _currentSession.value?.let { session ->
                loadMemories(session.id)
                loadPinnedFacts(session.id)
            }
        }
    }

    fun addPinnedFact(sessionId: Long, fact: String) {
        viewModelScope.launch {
            repository.addPinnedFact(sessionId, fact)
            loadPinnedFacts(sessionId)
        }
    }

    fun clearSessionMemories(sessionId: Long) {
        viewModelScope.launch {
            repository.clearSessionMemories(sessionId)
            _memories.value = emptyList()
            _pinnedFacts.value = emptyList()
            _plotSummary.value = ""
        }
    }

    // ============ 剧情操作 ============

    fun processPlotAction(action: PlotAction, customInstruction: String? = null) {
        val sessionId = _currentSession.value?.id ?: return
        viewModelScope.launch {
            val instruction = repository.processPlotAction(sessionId, action, customInstruction)
            _uiState.value = RoleplayUiState.PlotInstruction(instruction)
        }
    }

    fun savePlotSummary(summary: String) {
        val sessionId = _currentSession.value?.id ?: return
        viewModelScope.launch {
            repository.savePlotSummary(sessionId, summary)
            _plotSummary.value = summary
        }
    }

    // ============ 上下文组装 ============

    suspend fun assembleContext(
        userMessage: String?,
        globalSystemPrompt: String?,
        includeHistory: Boolean = true,
        maxHistoryMessages: Int = 50
    ): String {
        val sessionId = _currentSession.value?.id ?: return userMessage ?: ""
        return repository.assembleRoleplayContext(
            sessionId = sessionId,
            globalSystemPrompt = globalSystemPrompt,
            userMessage = userMessage,
            includeHistory = includeHistory,
            maxHistoryMessages = maxHistoryMessages
        )
    }

    fun clearUiState() {
        _uiState.value = RoleplayUiState.Idle
    }
}

sealed class RoleplayUiState {
    object Idle : RoleplayUiState()
    object Saving : RoleplayUiState()
    data class SaveSuccess(val message: String) : RoleplayUiState()
    data class Error(val message: String) : RoleplayUiState()
    data class PlotInstruction(val instruction: String) : RoleplayUiState()
}

enum class ConflictAction(val displayName: String) {
    CREATE_COPY("新建副本"),
    OVERWRITE("覆盖原有"),
    MERGE("智能融合")
}
