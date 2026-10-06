package com.aiassistant.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiassistant.AiAssistantApp
import com.aiassistant.domain.model.*
import com.aiassistant.utils.TimelineMemoryHelper
import com.aiassistant.utils.TimelineReconcileResult
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineDraftManager
import com.aiassistant.utils.TimelineReconcileDraft
import com.aiassistant.utils.TimelineReconcileCheckpoint
import com.aiassistant.data.repository.AiRepository
import com.aiassistant.data.repository.ChatGenerationManager
import com.aiassistant.data.repository.AutoTimelineUpdateResult
import com.google.gson.Gson
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * v2.6.8 需求 4：上下文回退待确认提示的最长等待时间（5 分钟）。
 * 生成过程中用户返回首页/切换对话时提示会保留在 Application 级会话上，重进会话仍可应答；
 * 若长时间无人应答，则按应用既有默认策略（压缩上下文后重试）放行，
 * 保证「连接与回复」绝不会因为一个无人应答的弹窗而永久挂起。
 */
private const val CONTEXT_FALLBACK_PROMPT_TIMEOUT_MS = 300_000L

class ChatViewModel(private val conversationId: Long) : ViewModel() {
    private val repository = AiAssistantApp.instance.repository
    private val personalizationManager = AiAssistantApp.instance.personalizationManager
    private val gson = Gson()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // 正在回复时的输入排队系统（需求 6）
    private val _messageQueue = MutableStateFlow<List<QueuedMessage>>(emptyList())
    val messageQueue: StateFlow<List<QueuedMessage>> = _messageQueue.asStateFlow()

    private val _isQueuePaused = MutableStateFlow(false)
    val isQueuePaused: StateFlow<Boolean> = _isQueuePaused.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    private val _currentThinking = MutableStateFlow("")
    val currentThinking: StateFlow<String> = _currentThinking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 可用模型列表，包含 API 配置来源，允许在同一对话里跨 API 切换。
    private val _availableModelOptions = MutableStateFlow<List<ChatModelOption>>(emptyList())
    val availableModelOptions: StateFlow<List<ChatModelOption>> = _availableModelOptions.asStateFlow()

    // 当前选择的模型（临时，仅当前对话有效）
    private val _currentModel = MutableStateFlow<String?>(null)
    val currentModel: StateFlow<String?> = _currentModel.asStateFlow()

    private val _currentModelOption = MutableStateFlow<ChatModelOption?>(null)
    val currentModelOption: StateFlow<ChatModelOption?> = _currentModelOption.asStateFlow()

    // 临时设置（仅当前对话有效）
    private val _tempSettings = MutableStateFlow(TempChatSettings())
    val tempSettings: StateFlow<TempChatSettings> = _tempSettings.asStateFlow()

    // 是否使用临时设置
    private val _useTempSettings = MutableStateFlow(true)
    val useTempSettings: StateFlow<Boolean> = _useTempSettings.asStateFlow()

    // 提示词模板列表
    private val _promptTemplates = MutableStateFlow<List<PromptTemplate>>(emptyList())
    val promptTemplates: StateFlow<List<PromptTemplate>> = _promptTemplates.asStateFlow()

    private val _contextUsage = MutableStateFlow(ContextUsageUiState())
    val contextUsage: StateFlow<ContextUsageUiState> = _contextUsage.asStateFlow()

    private val _messageModelMap = MutableStateFlow<Map<Long, String>>(emptyMap())
    val messageModelMap: StateFlow<Map<Long, String>> = _messageModelMap.asStateFlow()
    private val runtimeMessageModelMap = java.util.concurrent.ConcurrentHashMap<Long, String>()

    // 智能记忆提取待确认候选
    private val _pendingMemoryCandidate = MutableStateFlow<com.aiassistant.domain.model.PendingMemoryCandidate?>(null)
    val pendingMemoryCandidate: StateFlow<com.aiassistant.domain.model.PendingMemoryCandidate?> = _pendingMemoryCandidate.asStateFlow()

    // 网络波动与上下文回退提示
    private val _pendingContextFallbackPrompt = MutableStateFlow<com.aiassistant.domain.model.ContextFallbackPromptState?>(null)
    val pendingContextFallbackPrompt: StateFlow<com.aiassistant.domain.model.ContextFallbackPromptState?> = _pendingContextFallbackPrompt.asStateFlow()
    private val _pendingReplyDirection = MutableStateFlow<ReplyDirectionPrompt?>(null)
    val pendingReplyDirection: StateFlow<ReplyDirectionPrompt?> = _pendingReplyDirection.asStateFlow()

    fun answerReplyDirection(prompt: ReplyDirectionPrompt, decision: ReplyDirectionDecision) {
        val session = ChatGenerationManager.getSession(conversationId) ?: return
        if (session.pendingReplyDirection.value === prompt) session.answerReplyDirection(decision)
    }

    private suspend fun awaitReplyDirection(
        session: ChatGenerationManager.ActiveSession,
        directions: List<ReplyDirection>,
        error: String? = null
    ): ReplyDirectionDecision = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val prompt = ReplyDirectionPrompt(directions, error) { decision ->
            if (continuation.isActive) continuation.resumeWith(Result.success(decision))
        }
        session._isConnecting.value = false
        session.setStatus(if (error == null) "等待选择回复方向" else "回复方向生成失败，等待处理")
        _reconnectStatus.value = session.reconnectStatus.value
        session.pendingReplyDirection.value = prompt
        _pendingReplyDirection.value = prompt
        continuation.invokeOnCancellation {
            session.pendingReplyDirection.value = null
            if (ChatGenerationManager.isCurrentSession(conversationId, session)) _pendingReplyDirection.value = null
        }
    }

    fun handleContextFallbackDecision(choice: com.aiassistant.domain.model.ContextFallbackChoice) {
        val localPrompt = _pendingContextFallbackPrompt.value
        _pendingContextFallbackPrompt.value = null
        // v2.6.8 需求 4：提示真实归属是 Application 级会话（用户离开会话页后重进，新 ViewModel 也能应答），
        // 因此优先由会话应答，确保等待中的请求协程一定会被唤醒，不再永久挂起
        val session = ChatGenerationManager.getSession(conversationId)
        val sessionPrompt = session?.pendingContextFallbackPrompt?.value
        when {
            sessionPrompt != null -> session.answerContextFallbackPrompt(choice)
            localPrompt != null -> localPrompt.onDecision(choice)
        }
    }

    // 思考链翻译状态
    private val _translatingMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    val translatingMessageIds: StateFlow<Set<Long>> = _translatingMessageIds.asStateFlow()

    // 本会话专属记忆列表（仅存放纯净设定与规则）
    val sessionMemories: StateFlow<List<MemoryItem>> = repository.getConversationMemories(conversationId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 本会话独立时间线节点流（单一独立存放时间与关键事件）
    val timelineNodes: StateFlow<List<TimelineNode>> = repository.getTimelineNodesFlow(conversationId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 全量历史时间轴梳理与校对状态
    private val _isReconcilingTimeline = MutableStateFlow(false)
    val isReconcilingTimeline: StateFlow<Boolean> = _isReconcilingTimeline.asStateFlow()

    private val _timelineReconcileResult = MutableStateFlow<TimelineReconcileResult?>(null)
    val timelineReconcileResult: StateFlow<TimelineReconcileResult?> = _timelineReconcileResult.asStateFlow()

    private val _showTimelineReconcileDialog = MutableStateFlow(false)
    val showTimelineReconcileDialog: StateFlow<Boolean> = _showTimelineReconcileDialog.asStateFlow()

    private var timelineReconcileJob: Job? = null
    private val _timelineReconcileProgress = MutableStateFlow<String?>(null)
    val timelineReconcileProgress: StateFlow<String?> = _timelineReconcileProgress.asStateFlow()

    private val _timelineUpdateNotice = MutableStateFlow<String?>(null)
    val timelineUpdateNotice: StateFlow<String?> = _timelineUpdateNotice.asStateFlow()

    // 时间线变动待确认提案（需求 1：自动识别需用户确认）
    val pendingTimelineProposal: StateFlow<AutoTimelineUpdateResult?> =
        ChatGenerationManager.timelineProposals(conversationId).map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.Eagerly,
                ChatGenerationManager.timelineProposals(conversationId).value.firstOrNull())
    val timelineExtractionCount: StateFlow<Int> = ChatGenerationManager.timelineExtractionCount(conversationId)

    // 实时梳理草稿（需求 4：实时可见与断点续梳）
    // v2.7.3 流畅度：草稿/检查点文件读取从构造期主线程迁到 IO 协程异步回填
    // （原先每次进入聊天页都在主线程做 2 次磁盘读 + Gson 解析）
    private val _liveReconcileDraft = MutableStateFlow<TimelineReconcileDraft?>(null)
    val liveReconcileDraft: StateFlow<TimelineReconcileDraft?> = _liveReconcileDraft.asStateFlow()

    // 时间线梳理断点检查点（需求 1：记录上次梳理到的对话节点）
    private val _timelineCheckpoint = MutableStateFlow<TimelineReconcileCheckpoint?>(null)
    val timelineCheckpoint: StateFlow<TimelineReconcileCheckpoint?> = _timelineCheckpoint.asStateFlow()

    private val _showDraftDialog = MutableStateFlow(false)
    val showDraftDialog: StateFlow<Boolean> = _showDraftDialog.asStateFlow()

    fun dismissTimelineUpdateNotice() {
        _timelineUpdateNotice.value = null
    }

    fun dismissTimelineProposal() {
        ChatGenerationManager.dismissTimelineProposal(conversationId, pendingTimelineProposal.value)
    }

    fun openDraftDialog() {
        _showDraftDialog.value = true
    }

    fun closeDraftDialog() {
        _showDraftDialog.value = false
    }

    fun cancelTimelineReconciliation() {
        timelineReconcileJob?.cancel()
        timelineReconcileJob = null
        _isReconcilingTimeline.value = false
        _timelineReconcileProgress.value = null
    }

    private var activeAssistantVariantGroupId: String? = null
    private var activeAssistantVariantIndex: Int = 1

    private var conversation: Conversation? = null
    private var apiConfig: ApiConfig? = null
    private var generationJob: Job? = null
    private var systemPromptSaveJob: Job? = null
    private var isMessageSaved = false
    @Volatile private var isUserStopping = false
    private val currentKeyAttemptErrors = mutableListOf<String>()
    private var isPrivateConversation = false
    private var privateExitHandled = false

    /**
     * 实时 Key 尝试报错明细（需求 v2.7.0-3）：
     * 生成过程中每个 Key 失败即刻入流，流式气泡下方实时渲染错误明细，无需手动暂停后才能看到
     */
    private val _keyAttemptErrors = MutableStateFlow<List<String>>(emptyList())
    val keyAttemptErrors: StateFlow<List<String>> = _keyAttemptErrors.asStateFlow()

    /**
     * 生成锚点（v2.6.5）：触发本轮生成的用户消息 id 及其 user 分组 id。
     * 流式回复气泡钉在该用户消息之后挂载——删除同一位置的其他回复（错误占位、旧 variant）
     * 不会移动正在连接/输出的回复的位置；生成结束后清空。
     */
    private val _generatingAnchor = MutableStateFlow<GeneratingAnchor?>(null)
    val generatingAnchor: StateFlow<GeneratingAnchor?> = _generatingAnchor.asStateFlow()

    private fun applyGeneratingAnchor(userMessageId: Long?, userGroupId: String? = null) {
        if (userMessageId == null || userMessageId <= 0L) {
            _generatingAnchor.value = null
            return
        }
        val group = userGroupId ?: _messages.value.firstOrNull { it.id == userMessageId }
            ?.variantGroupId?.takeIf { it.endsWith("_user") }
        _generatingAnchor.value = GeneratingAnchor(userMessageId = userMessageId, userGroupId = group)
    }

    private fun attachToActiveGenerationSession() {
        val activeSession = ChatGenerationManager.getSession(conversationId) ?: return
        if (activeSession.isGenerating.value) {
            _isGenerating.value = true
            _currentResponse.value = activeSession.currentResponse.value
            _currentThinking.value = activeSession.currentThinking.value
            _reconnectStatus.value = activeSession.reconnectStatus.value
            _error.value = activeSession.error.value
            generationJob = activeSession.generationJob
            // 恢复生成锚点，保证重进会话后流式气泡仍钉在触发本轮的用户消息之后
            applyGeneratingAnchor(activeSession.anchorUserMessageId, activeSession.anchorUserGroupId)
            // v2.6.8 需求 4：恢复会话级的实时 Key 报错明细与上下文回退待确认提示
            // （提示挂在会话上，用户离开后重进仍能应答，等待中的请求不会被永久挂起）
            currentKeyAttemptErrors.clear()
            currentKeyAttemptErrors.addAll(activeSession.keyAttemptErrors.value)
            _keyAttemptErrors.value = activeSession.keyAttemptErrors.value

            viewModelScope.launch {
                activeSession.currentResponse.collect { res ->
                    _currentResponse.value = res
                }
            }
            viewModelScope.launch {
                activeSession.currentThinking.collect { thk ->
                    _currentThinking.value = thk
                }
            }
            viewModelScope.launch {
                activeSession.reconnectStatus.collect { st ->
                    _reconnectStatus.value = st
                }
            }
            viewModelScope.launch {
                activeSession.error.collect { err ->
                    _error.value = err
                }
            }
            viewModelScope.launch {
                activeSession.keyAttemptErrors.collect { errs ->
                    _keyAttemptErrors.value = errs
                }
            }
            viewModelScope.launch {
                activeSession.pendingReplyDirection.collect { _pendingReplyDirection.value = it }
            }
            viewModelScope.launch {
                activeSession.pendingContextFallbackPrompt.collect { prompt ->
                    _pendingContextFallbackPrompt.value = prompt
                }
            }
            viewModelScope.launch {
                activeSession.isGenerating.collect { gen ->
                    _isGenerating.value = gen
                    if (!gen) {
                        loadConversation()
                    }
                }
            }
        }
    }

    private fun observeUsageStatsForModels() {
        viewModelScope.launch {
            repository.getAllUsageStats().collect {
                updateMessageModelMap(_messages.value)
            }
        }
    }

    // 当前模型是否所属配置失效
    private val _isModelConfigInvalid = MutableStateFlow(false)
    val isModelConfigInvalid: StateFlow<Boolean> = _isModelConfigInvalid.asStateFlow()

    // 模型连接与重连状态
    private val _reconnectStatus = MutableStateFlow<String?>(null)
    val reconnectStatus: StateFlow<String?> = _reconnectStatus.asStateFlow()

    private fun loadConversation() {
        viewModelScope.launch {
            conversation = repository.getConversationById(conversationId)
            _timelineCheckpoint.value = withContext(Dispatchers.IO) {
                TimelineDraftManager.getCheckpoint(AiAssistantApp.instance, conversationId)
            }
            conversation?.let { conv ->
                isPrivateConversation = repository.hasConversationTag(conv, "private")
                apiConfig = repository.getApiConfigById(conv.apiConfigId)
                if (apiConfig == null) {
                    _isModelConfigInvalid.value = true
                    val fallback = repository.getDefaultApiConfig()
                        ?: repository.getAllApiConfigs().first().firstOrNull()
                    if (fallback != null) {
                        apiConfig = fallback
                    }
                    _error.value = "当前会话绑定的 API 配置已失效或被删除，请重新选择可用模型"
                } else {
                    _isModelConfigInvalid.value = false
                }

                // 无论原配置是否存在，均加载可用模型列表，保证用户能正常切换模型
                loadAvailableModels()
                // 设置当前模型
                _currentModel.value = conv.modelName
                // 使用对话级别配置，如果没有则使用API配置默认值
                val rpRepoForInit = AiAssistantApp.instance.roleplayRepository
                val rpSessionForInit = rpRepoForInit.getSessionByConversationId(conversationId)
                _tempSettings.value = TempChatSettings(
                    temperature = conv.temperature ?: apiConfig?.temperature ?: 0.95f,
                    maxTokens = conv.maxTokens ?: apiConfig?.maxTokens ?: 8192,
                    topP = conv.topP ?: apiConfig?.topP ?: 1.0f,
                    enableThinking = conv.enableThinking ?: true,
                    thinkingEffort = conv.thinkingEffort ?: apiConfig?.thinkingEffort ?: "high",
                    enableWebSearch = conv.enableWebSearch ?: false,
                    enableSessionMemory = conv.enableSessionMemory ?: false,
                    enableExternalMemory = conv.enableExternalMemory ?: rpSessionForInit?.enableExternalMemory ?: false,
                    enableWorldBook = conv.enableWorldBook ?: rpSessionForInit?.enableWorldBook ?: false,
                    enableReplyDirections = conv.enableReplyDirections,
                    replyDirectionCount = conv.replyDirectionCount.coerceIn(2, 4),
                    activeWorldBookIds = conv.activeWorldBookIds ?: rpSessionForInit?.activeWorldBookIds,
                    contextWindowTokens = conv.contextWindowTokens
                )
                // 如果对话有自定义配置，自动启用临时设置
                _useTempSettings.value = true

                val roleplayRepo = AiAssistantApp.instance.roleplayRepository
                val rpSession = roleplayRepo.getSessionByConversationId(conversationId)
                val charIds = rpSession?.getEffectiveCharacterIds().orEmpty()
                val rpCharacters = if (charIds.isNotEmpty()) {
                    roleplayRepo.getCharactersByIds(charIds)
                } else {
                    listOfNotNull(rpSession?.characterId?.let { roleplayRepo.getCharacterById(it) })
                }
                val rpCharacter = rpCharacters.firstOrNull()
                val rpScenario = rpSession?.scenarioId?.let { roleplayRepo.getScenarioById(it) }
                val narrativeMode = rpSession?.let { NarrativeMode.fromValue(it.narrativeMode) } ?: NarrativeMode.CHARACTER

                _uiState.update {
                    it.copy(
                        conversationTitle = conv.title,
                        modelName = conv.modelName,
                        systemPrompt = conv.systemPrompt,
                        enableThinking = conversation?.enableThinking ?: true,
                        modelAvatarUri = conv.modelAvatarUri,
                        isRoleplay = rpSession != null,
                        roleplaySession = rpSession,
                        roleplayCharacter = rpCharacter,
                        roleplayCharacters = rpCharacters,
                        roleplayScenario = rpScenario,
                        narrativeMode = narrativeMode,
                        currentStoryTime = conv.currentStoryTime
                    )
                }
            }

            // v2.7.3 流畅度：消息订阅收敛为单例 Job——loadConversation 在会话存活期间被多处调用
            // （生成结束、报错保存、会话记忆增删改、时间线操作等 16 处），原先每次都在协程体内
            // 新启一条 getMessages collect 且旧订阅从不取消，同屏 N 条等值订阅重复查询、重复修复写库、
            // 重复估算上下文（越用越卡的残留根因）。同一 ViewModel 的会话流固定，去重后行为与
            // 单份订阅完全等价。
            observeMessages()
        }
    }

    private var messagesCollectJob: Job? = null

    private fun observeMessages() {
        if (messagesCollectJob?.isActive == true) return
        messagesCollectJob = viewModelScope.launch {
            repository.getMessages(conversationId).collect { messageList ->
                _messages.value = messageList
                repairDuplicateVariantIndices(messageList)
                refreshContextUsage()
                updateMessageModelMap(messageList)
            }
        }
    }

    private fun loadAvailableModels() {
        viewModelScope.launch {
            val conv = conversation ?: return@launch
            var currentConfig = apiConfig
            if (currentConfig == null) {
                currentConfig = repository.getDefaultApiConfig()?.takeIf { it.isEnabled }
                    ?: repository.getAllApiConfigs().first().firstOrNull { it.isEnabled }
                if (currentConfig != null) {
                    apiConfig = currentConfig
                }
            }
            val fallbackOption = currentConfig?.takeIf { it.isEnabled }?.let { cfg ->
                ChatModelOption(
                    apiConfigId = cfg.id,
                    configName = cfg.name,
                    provider = cfg.provider,
                    apiType = cfg.apiType,
                    modelName = conv.modelName.ifBlank { cfg.modelName },
                    capability = "auto"
                )
            }
            val visibleOptions = repository.getAllVisibleChatModelOptions()
            val options = (visibleOptions + listOfNotNull(fallbackOption))
                .filter { it.modelName.isNotBlank() }
                .distinctBy { "${it.apiConfigId}:${it.modelName}" }

            _availableModelOptions.value = options
            val selected = options.firstOrNull {
                it.apiConfigId == conv.apiConfigId && it.modelName == conv.modelName
            } ?: options.firstOrNull {
                it.modelName == conv.modelName
            } ?: options.firstOrNull()

            selected?.let { applyCurrentModelOption(it, persist = false) }
        }
    }

    private fun loadPromptTemplates() {
        viewModelScope.launch {
            repository.getAllPromptTemplates().collect { templates ->
                _promptTemplates.value = templates
            }
        }
    }

    // 保存提示词模板
    fun savePromptTemplate(name: String, content: String, description: String? = null, category: String = "general") {
        viewModelScope.launch {
            val template = PromptTemplate(
                name = name,
                content = content,
                description = description,
                category = category
            )
            repository.savePromptTemplate(template)
        }
    }

    // 使用模板
    fun usePromptTemplate(template: PromptTemplate) {
        viewModelScope.launch {
            repository.incrementTemplateUseCount(template.id)
        }
    }

    private fun getPresetModels(apiType: String, provider: String): List<String> {
        return when {
            apiType == "anthropic" -> listOf(
                "claude-opus-4-6",
                "claude-sonnet-4-6",
                "claude-haiku-4-5",
                "claude-3-5-sonnet-20241022",
                "claude-3-5-haiku-20241022"
            )
            provider.contains("DeepSeek", ignoreCase = true) -> listOf(
                "deepseek-flash",
                "deepseek-v4-pro",
                "deepseek-v4-flash",
                "deepseek-chat-v4"
            )
            provider.contains("OpenAI", ignoreCase = true) -> listOf(
                "gpt-6-astra",
                "gpt-6-luna",
                "gpt-6-sol",
                "gpt-5.6",
                "gpt-5.5",
                "gpt-5",
                "gpt-4.1"
            )
            provider.contains("Gemini", ignoreCase = true) || provider.contains("Google", ignoreCase = true) -> listOf(
                "gemini-3.8-flash",
                "gemini-3.5-flash",
                "gemini-3.1-pro"
            )
            provider.contains("MiniMax", ignoreCase = true) -> listOf(
                "MiniMax-M3",
                "MiniMax-M2.7",
                "MiniMax-M2.7-highspeed"
            )
            provider.contains("Kimi", ignoreCase = true) || provider.contains("Moonshot", ignoreCase = true) -> listOf(
                "kimi-k3",
                "kimi-k2.6",
                "moonshot-v1-128k"
            )
            provider.contains("GLM", ignoreCase = true) || provider.contains("Zhipu", ignoreCase = true) ||
                provider.contains("智谱", ignoreCase = true) -> listOf(
                "glm-5.3",
                "glm-5.2",
                "glm-4.7"
            )
            provider.contains("MiMo", ignoreCase = true) || provider.contains("Xiaomi", ignoreCase = true) -> listOf(
                "MiMo-V2.6-Pro",
                "MiMo-V2.6",
                "MiMo-V2.5"
            )
            else -> emptyList()
        }
    }

    // 切换模型和 API 配置（仅当前对话有效）
    fun switchModel(option: ChatModelOption) {
        if (ChatGenerationManager.getSession(conversationId)?.directionPhase == true) stopGeneration()
        applyCurrentModelOption(option, persist = true)
    }

    private fun applyCurrentModelOption(option: ChatModelOption, persist: Boolean) {
        _currentModelOption.value = option
        _currentModel.value = option.modelName
        _uiState.update { it.copy(modelName = option.modelName) }
        viewModelScope.launch {
            if (!persist) {
                apiConfig = repository.getApiConfigById(option.apiConfigId)
                if (!_useTempSettings.value) {
                    apiConfig?.let { cfg ->
                        val current = _tempSettings.value
                        _tempSettings.value = TempChatSettings(
                            temperature = cfg.temperature,
                            maxTokens = cfg.maxTokens,
                            topP = cfg.topP,
                            enableThinking = cfg.enableThinking,
                            thinkingEffort = cfg.thinkingEffort,
                            enableWebSearch = cfg.enableWebSearch,
                            enableSessionMemory = current.enableSessionMemory,
                            enableExternalMemory = current.enableExternalMemory,
                            enableWorldBook = current.enableWorldBook,
                            enableReplyDirections = current.enableReplyDirections,
                            replyDirectionCount = current.replyDirectionCount,
                            activeWorldBookIds = current.activeWorldBookIds,
                            contextWindowTokens = current.contextWindowTokens
                        )
                    }
                }
                refreshContextUsage()
                return@launch
            }
            conversation?.let { conv ->
                val updated = conv.copy(
                    apiConfigId = option.apiConfigId,
                    modelName = option.modelName,
                    updatedAt = System.currentTimeMillis()
                )
                AiAssistantApp.instance.database.conversationDao().updateConversation(updated)
                conversation = updated
                apiConfig = repository.getApiConfigById(option.apiConfigId)
                _isModelConfigInvalid.value = apiConfig == null
                if (apiConfig != null) {
                    _error.value = null
                }
                if (!_useTempSettings.value) {
                    apiConfig?.let { cfg ->
                        val current = _tempSettings.value
                        _tempSettings.value = TempChatSettings(
                            temperature = cfg.temperature,
                            maxTokens = cfg.maxTokens,
                            topP = cfg.topP,
                            enableThinking = cfg.enableThinking,
                            thinkingEffort = cfg.thinkingEffort,
                            enableWebSearch = cfg.enableWebSearch,
                            enableSessionMemory = current.enableSessionMemory,
                            enableExternalMemory = current.enableExternalMemory,
                            enableWorldBook = current.enableWorldBook,
                            enableReplyDirections = current.enableReplyDirections,
                            replyDirectionCount = current.replyDirectionCount,
                            activeWorldBookIds = current.activeWorldBookIds,
                            contextWindowTokens = current.contextWindowTokens
                        )
                    }
                }
            }
            refreshContextUsage()
        }
    }

    // 更新临时设置并保存到对话
    fun updateTempSettings(settings: TempChatSettings) {
        if (ChatGenerationManager.getSession(conversationId)?.directionPhase == true) stopGeneration()
        _tempSettings.value = settings
        _useTempSettings.value = true
        // 保存到对话
        saveConversationSettings(settings)
        refreshContextUsage()
    }

    fun updateChatSettings(settings: TempChatSettings, prompt: String?) {
        if (ChatGenerationManager.getSession(conversationId)?.directionPhase == true) stopGeneration()
        val normalizedPrompt = normalizeSystemPrompt(prompt)
        _tempSettings.value = settings
        _useTempSettings.value = true
        _uiState.update {
            it.copy(
                systemPrompt = normalizedPrompt,
                enableThinking = settings.enableThinking
            )
        }
        saveConversationSettings(settings, normalizedPrompt)
        refreshContextUsage()
    }

    // 启用/禁用临时设置
    fun toggleTempSettings(enabled: Boolean) {
        _useTempSettings.value = enabled
        if (!enabled) {
            // 清除对话级别配置
            saveConversationSettings(null)
        }
        refreshContextUsage()
    }

    fun updateConversationContextLimit(tokens: Int?) {
        val updated = _tempSettings.value.copy(contextWindowTokens = tokens)
        _tempSettings.value = updated
        _useTempSettings.value = true
        saveConversationSettings(updated)
        refreshContextUsage()
    }

    fun refreshContextUsage() {
        viewModelScope.launch {
            val latestConv = repository.getConversationById(conversationId)
            if (latestConv != null) {
                conversation = latestConv
                val dbTokens = latestConv.contextWindowTokens
                if (dbTokens != null && _tempSettings.value.contextWindowTokens != dbTokens) {
                    _tempSettings.update { it.copy(contextWindowTokens = dbTokens) }
                }
            }
            val (modelName, maxTokens, contextOverride) = resolveUsageQueryOverrides()
            val usage = repository.getConversationContextUsage(
                conversationId = conversationId,
                modelNameOverride = modelName,
                maxOutputTokens = maxTokens,
                contextWindowOverrideTokens = contextOverride
            )
            _contextUsage.update {
                it.copy(
                    usage = usage,
                    isCompressing = false,
                    statusMessage = null
                )
            }
        }
    }

    fun compressContextNow(isAuto: Boolean = false) {
        if (_contextUsage.value.isCompressing) return
        viewModelScope.launch {
            val shouldCompress = _contextUsage.value.usage?.canCompress == true
            val initialPercent = (_contextUsage.value.usage?.usagePercent ?: 0f) * 100
            val startMsg = if (isAuto) "🔄 正在自动压缩历史上下文，精简早期对话..." else "🔄 正在压缩上下文，精简历史消息..."
            _contextUsage.update { it.copy(isCompressing = true, statusMessage = startMsg) }
            val modelName = _currentModel.value ?: conversation?.modelName ?: _uiState.value.modelName
            val maxTokens = (_useTempSettings.value)
                .takeIf { it }
                ?.let { _tempSettings.value.maxTokens }
                ?: conversation?.maxTokens
                ?: apiConfig?.maxTokens
            val contextOverride = (_useTempSettings.value)
                .takeIf { it }
                ?.let { _tempSettings.value.contextWindowTokens }
                ?: conversation?.contextWindowTokens
            repository.compressConversationContext(
                conversationId = conversationId,
                modelNameOverride = modelName,
                maxOutputTokens = maxTokens,
                contextWindowOverrideTokens = contextOverride
            ).fold(
                onSuccess = { usage ->
                    conversation = repository.getConversationById(conversationId) ?: conversation
                    val newPercent = usage.usagePercent * 100
                    val freed = (initialPercent - newPercent).coerceAtLeast(0f)
                    val finishMsg = if (freed > 1f) {
                        "✅ 较早历史已成功沉淀为时间线与会话记忆，释放约 ${freed.toInt()}% 空间，最近十几次对话完整保留"
                    } else if (shouldCompress || usage.compressedThroughMessageId != null) {
                        "✅ 较早历史已梳理沉淀为时间线与会话记忆，最近十几次对话完整无损保留"
                    } else {
                        "当前最近十几次对话已处于无损保留状态，无需额外压缩"
                    }
                    _contextUsage.update {
                        it.copy(
                            usage = usage,
                            isCompressing = false,
                            statusMessage = finishMsg
                        )
                    }
                },
                onFailure = { error ->
                    _contextUsage.update {
                        it.copy(
                            isCompressing = false,
                            statusMessage = error.message ?: "压缩失败"
                        )
                    }
                }
            )
        }
    }

    fun clearContextStatusMessage() {
        _contextUsage.update { it.copy(statusMessage = null, pendingAutoTier = null) }
    }

    fun generateRollingSummaryNow() {
        if (_contextUsage.value.isGeneratingSummary) return
        viewModelScope.launch {
            _contextUsage.update { it.copy(isGeneratingSummary = true, statusMessage = "🔄 正在梳理较早历史时间线并沉淀会话记忆...") }
            val modelName = _currentModel.value ?: conversation?.modelName ?: _uiState.value.modelName
            val maxTokens = (_useTempSettings.value)
                .takeIf { it }
                ?.let { _tempSettings.value.maxTokens }
                ?: conversation?.maxTokens
                ?: apiConfig?.maxTokens
            val contextOverride = (_useTempSettings.value)
                .takeIf { it }
                ?.let { _tempSettings.value.contextWindowTokens }
                ?: conversation?.contextWindowTokens
            repository.generateRollingSummaryNow(
                conversationId = conversationId,
                modelNameOverride = modelName,
                maxOutputTokens = maxTokens,
                contextWindowOverrideTokens = contextOverride
            ).fold(
                onSuccess = { usage ->
                    conversation = repository.getConversationById(conversationId) ?: conversation
                    val msg = "✅ 较早历史已成功沉淀为时间线与会话记忆，最近十几次对话完整无损保留"
                    _contextUsage.update {
                        it.copy(
                            usage = usage,
                            isGeneratingSummary = false,
                            statusMessage = msg
                        )
                    }
                },
                onFailure = { error ->
                    _contextUsage.update {
                        it.copy(
                            isGeneratingSummary = false,
                            statusMessage = error.message ?: "提炼时间线与记忆失败"
                        )
                    }
                }
            )
        }
    }

    fun updateRollingSummary(newSummary: String) {
        viewModelScope.launch {
            repository.updateRollingSummary(conversationId, newSummary)
            conversation = repository.getConversationById(conversationId) ?: conversation
            refreshContextUsage()
            _contextUsage.update { it.copy(statusMessage = "✅ 滚动摘要已手动更新并保存") }
        }
    }

    fun clearRollingSummary() {
        viewModelScope.launch {
            repository.clearRollingSummary(conversationId)
            conversation = repository.getConversationById(conversationId) ?: conversation
            refreshContextUsage()
            _contextUsage.update { it.copy(statusMessage = "✅ 滚动摘要已清除") }
        }
    }

    fun getCurrentRollingSummary(): String = conversation?.rollingSummary.orEmpty()

    fun setCompressionTier(
        tier: com.aiassistant.domain.model.CompressionTier,
        recentRounds: Int = 8,
        customPercent: Int? = null
    ) {
        viewModelScope.launch {
            val safeRounds = recentRounds.coerceIn(
                com.aiassistant.domain.model.CompressionTierPolicy.MIN_L2_RECENT_ROUNDS,
                com.aiassistant.domain.model.CompressionTierPolicy.MAX_L2_RECENT_ROUNDS
            )
            val safePercent = (customPercent
                ?: conversation?.compressionCustomPercent
                ?: com.aiassistant.domain.model.CompressionTierPolicy.DEFAULT_CUSTOM_RETAIN_PERCENT)
                .coerceIn(
                    com.aiassistant.domain.model.CompressionTierPolicy.MIN_CUSTOM_RETAIN_PERCENT,
                    com.aiassistant.domain.model.CompressionTierPolicy.MAX_CUSTOM_RETAIN_PERCENT
                )
            repository.updateConversationCompressionTier(conversationId, tier, safeRounds, safePercent)
            conversation = repository.getConversationById(conversationId) ?: conversation

            if (_isGenerating.value) {
                _contextUsage.update {
                    it.copy(statusMessage = "⚙️ 压缩档位已变更为「${tier.displayName}」，将在下一轮请求生效")
                }
            } else {
                refreshContextUsage()
                _contextUsage.update {
                    it.copy(
                        statusMessage = "✅ 已切换至「${tier.displayName}」（${tier.shortDesc}）",
                        pendingAutoTier = null
                    )
                }
            }
        }
    }

    /**
     * 滑杆调整未保存时的实时预览：用覆盖值即时重算压缩快照（含各档位预估 Token），不落库
     */
    fun previewCompressionSettings(recentRounds: Int, customPercent: Int) {
        viewModelScope.launch {
            val (modelName, maxTokens, contextOverride) = resolveUsageQueryOverrides()
            val usage = repository.getConversationContextUsage(
                conversationId = conversationId,
                modelNameOverride = modelName,
                maxOutputTokens = maxTokens,
                contextWindowOverrideTokens = contextOverride,
                l2RoundsOverride = recentRounds,
                customPercentOverride = customPercent
            )
            _contextUsage.update { it.copy(usage = usage) }
        }
    }

    private fun resolveUsageQueryOverrides(): Triple<String?, Int?, Int?> {
        val modelName = _currentModel.value ?: conversation?.modelName ?: _uiState.value.modelName
        val maxTokens = (_useTempSettings.value)
            .takeIf { it }
            ?.let { _tempSettings.value.maxTokens }
            ?: conversation?.maxTokens
            ?: apiConfig?.maxTokens
        val contextOverride = (_useTempSettings.value)
            .takeIf { it }
            ?.let { _tempSettings.value.contextWindowTokens }
            ?: conversation?.contextWindowTokens
        return Triple(modelName, maxTokens, contextOverride)
    }

    fun applyPendingAutoCompression() {
        val target = _contextUsage.value.pendingAutoTier ?: return
        val currentRounds = _contextUsage.value.usage?.compressionRecentRounds ?: 8
        setCompressionTier(target, currentRounds)
    }

    fun dismissPendingAutoCompression() {
        _contextUsage.update {
            it.copy(pendingAutoTier = null, statusMessage = null)
        }
    }

    private fun evaluateAutoCompression() {
        viewModelScope.launch {
            try {
                val personalization = personalizationManager.getSettings()
                if (!personalization.autoCompressionTierEnabled) return@launch

                val usage = _contextUsage.value.usage ?: return@launch
                val percent = usage.usagePercent
                if (_contextUsage.value.isCompressing) return@launch

                val suggested = com.aiassistant.domain.model.CompressionTierPolicy.evaluateAutoUpgrade(
                    currentTier = usage.compressionTier,
                    usagePercent = percent,
                    autoEnabled = true,
                    thresholdL2 = personalization.autoCompressionThresholdL2,
                    thresholdL3 = personalization.autoCompressionThresholdL3,
                    thresholdL4 = personalization.autoCompressionThresholdL4
                ) ?: return@launch

                val banner = "⚠️ 上下文占用已达 ${(percent * 100).toInt()}%，建议升至「${suggested.displayName}」档（${suggested.shortDesc}）"
                _contextUsage.update {
                    it.copy(
                        pendingAutoTier = suggested,
                        statusMessage = banner
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun updateConversationModelAvatar(avatarUri: String?) {
        viewModelScope.launch {
            conversation?.let { conv ->
                val updated = conv.copy(modelAvatarUri = avatarUri)
                conversation = updated
                repository.updateConversationModelAvatar(conv.id, avatarUri)
                _uiState.update { it.copy(modelAvatarUri = avatarUri) }
            }
        }
    }

    /**
     * 仅修改一条消息的内容（用户/助手消息通用）：不删除后续消息、不重新发送、不触发重新生成。
     * 修改后的内容会作为该消息的持久化内容参与后续上下文组装。
     */
    fun updateMessageContent(messageId: Long, newContent: String) {
        if (ChatGenerationManager.getSession(conversationId)?.directionPhase == true) stopGeneration()
        viewModelScope.launch {
            val target = _messages.value.firstOrNull { it.id == messageId } ?: return@launch
            val updated = target.copy(content = newContent)
            repository.updateMessage(updated)
            _messages.update { list ->
                list.map { if (it.id == messageId) updated else it }
            }
            refreshContextUsage()
        }
    }

    // 保存对话级别配置
    private fun saveConversationSettings(
        settings: TempChatSettings?,
        systemPrompt: String? = _uiState.value.systemPrompt
    ) {
        conversation?.let { conv ->
            val updated = conv.copy(
                temperature = settings?.temperature,
                maxTokens = settings?.maxTokens,
                topP = settings?.topP,
                enableThinking = settings?.enableThinking,
                thinkingEffort = settings?.thinkingEffort,
                enableWebSearch = settings?.enableWebSearch,
                enableSessionMemory = settings?.enableSessionMemory ?: conv.enableSessionMemory ?: false,
                enableExternalMemory = settings?.enableExternalMemory ?: conv.enableExternalMemory ?: false,
                enableWorldBook = settings?.enableWorldBook ?: conv.enableWorldBook ?: false,
                enableReplyDirections = settings?.enableReplyDirections ?: conv.enableReplyDirections,
                replyDirectionCount = (settings?.replyDirectionCount ?: conv.replyDirectionCount).coerceIn(2, 4),
                activeWorldBookIds = settings?.activeWorldBookIds ?: conv.activeWorldBookIds,
                contextWindowTokens = settings?.contextWindowTokens,
                systemPrompt = normalizeSystemPrompt(systemPrompt)
            )
            conversation = updated
            systemPromptSaveJob?.cancel()
            systemPromptSaveJob = viewModelScope.launch {
                AiAssistantApp.instance.database.conversationDao().updateConversation(updated)
                // 若当前会话为角色扮演会话，同步保存至角色扮演会话实体
                val rpRepo = AiAssistantApp.instance.roleplayRepository
                val rpSession = _uiState.value.roleplaySession ?: rpRepo.getSessionByConversationId(conversationId)
                if (rpSession != null && settings != null) {
                    val updatedRp = rpSession.copy(
                        enableExternalMemory = settings.enableExternalMemory,
                        enableWorldBook = settings.enableWorldBook,
                        activeWorldBookIds = settings.activeWorldBookIds,
                        updatedAt = System.currentTimeMillis()
                    )
                    rpRepo.updateSession(updatedRp)
                    _uiState.update { it.copy(roleplaySession = updatedRp) }
                }
            }
        }
    }

    fun sendMessage(content: String, attachments: List<Attachment> = emptyList()) {
        if (_isGenerating.value) {
            enqueueMessage(content, attachments)
            return
        }
        sendMessageInternal(content, attachments, saveUserMessage = true)
    }

    fun enqueueMessage(content: String, attachments: List<Attachment> = emptyList()) {
        val trimmed = content.trim()
        if (trimmed.isBlank() && attachments.isEmpty()) return
        _messageQueue.update { it + QueuedMessage(content = trimmed, attachments = attachments) }
    }

    fun toggleQueuePause() {
        _isQueuePaused.update { !it }
        if (!_isQueuePaused.value && !_isGenerating.value && _messageQueue.value.isNotEmpty()) {
            checkAndDispatchQueue()
        }
    }

    fun removeQueuedMessage(id: String) {
        _messageQueue.update { list -> list.filter { it.id != id } }
    }

    fun recallQueuedMessage(id: String): QueuedMessage? {
        val target = _messageQueue.value.firstOrNull { it.id == id }
        if (target != null) {
            _messageQueue.update { list -> list.filter { it.id != id } }
        }
        return target
    }

    fun editQueuedMessage(id: String, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isBlank()) return
        _messageQueue.update { list ->
            list.map { if (it.id == id) it.copy(content = trimmed) else it }
        }
    }

    fun moveQueuedMessage(fromIndex: Int, toIndex: Int) {
        _messageQueue.update { list ->
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return@update list
            val mutable = list.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            mutable
        }
    }

    fun checkAndDispatchQueue() {
        if (_isQueuePaused.value || _isGenerating.value) return
        val next = _messageQueue.value.firstOrNull() ?: return
        _messageQueue.update { it.drop(1) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(200L)
            sendMessage(next.content, next.attachments)
        }
    }

    fun sendEditedMessage(source: Message, content: String, attachments: List<Attachment> = emptyList()) {
        if (_isGenerating.value) return

        viewModelScope.launch {
            val allMessages = repository.getMessagesList(conversationId)
            val sourceIndex = allMessages.indexOfFirst { it.id == source.id }
            if (sourceIndex < 0) {
                sendMessage(content, attachments)
                return@launch
            }

            val turnKey = source.variantGroupId
                ?.substringBeforeLast("_user")
                ?: "turn_${source.id}"
            val userGroupId = "${turnKey}_user"
            val assistantGroupId = "${turnKey}_assistant"

            if (source.variantGroupId == null) {
                AiAssistantApp.instance.database.messageDao().updateMessage(
                    source.copy(variantGroupId = userGroupId, variantIndex = 1)
                )
            }

            val nextAssistant = allMessages
                .drop(sourceIndex + 1)
                .takeWhile { it.role != "user" }
                .firstOrNull { it.role == "assistant" }
            if (nextAssistant != null && nextAssistant.variantGroupId == null) {
                AiAssistantApp.instance.database.messageDao().updateMessage(
                    nextAssistant.copy(variantGroupId = assistantGroupId, variantIndex = 1)
                )
            }

            val nextIndex = (allMessages
                .filter { it.variantGroupId == userGroupId }
                .maxOfOrNull { it.variantIndex } ?: 1) + 1

            sendMessageInternal(
                content = content,
                attachments = attachments,
                saveUserMessage = true,
                userVariantGroupId = userGroupId,
                userVariantIndex = nextIndex,
                assistantVariantGroupId = assistantGroupId,
                assistantVariantIndex = nextIndex
            )
        }
    }

    private fun sendMessageInternal(
        content: String,
        attachments: List<Attachment> = emptyList(),
        saveUserMessage: Boolean,
        userVariantGroupId: String? = null,
        userVariantIndex: Int = 1,
        assistantVariantGroupId: String? = null,
        assistantVariantIndex: Int = 1,
        anchorUserMessageId: Long? = null,
        retryReplyDirection: String? = null
    ) {
        if ((content.isBlank() && attachments.isEmpty()) || _isGenerating.value) return

        val selectedOption = _currentModelOption.value ?: conversation?.let { conv ->
            val cfg = apiConfig
            if (cfg != null) {
                ChatModelOption(
                    apiConfigId = cfg.id,
                    configName = cfg.name,
                    provider = cfg.provider,
                    apiType = cfg.apiType,
                    modelName = conv.modelName.ifBlank { cfg.modelName },
                    capability = "auto"
                )
            } else null
        }

        if (selectedOption == null) {
            _error.value = "API配置不存在，请在设置中配置API"
            return
        }

        // 保存用户消息
        val attachmentsJson = if (attachments.isNotEmpty()) {
            gson.toJson(attachments)
        } else null

        isMessageSaved = false
        isUserStopping = false
        activeAssistantVariantGroupId = assistantVariantGroupId
        activeAssistantVariantIndex = assistantVariantIndex

        // 获取当前选择的模型和设置
        val settings = if (_useTempSettings.value) _tempSettings.value else null
        val currentSystemPrompt = normalizeSystemPrompt(_uiState.value.systemPrompt)

        val session = ChatGenerationManager.startSession(
            conversationId = conversationId,
            modelName = selectedOption.modelName,
            variantGroupId = assistantVariantGroupId,
            variantIndex = assistantVariantIndex
        )
        session.directionPhase = (settings?.enableReplyDirections ?: conversation?.enableReplyDirections ?: false) && retryReplyDirection == null

        // 生成锚点：重新生成（saveUserMessage=false）时由调用方传入触发本轮的用户消息 id，
        // 流式气泡钉在该消息之后，位置不随同一位置其他回复的删除而移动
        if (anchorUserMessageId != null) {
            applyGeneratingAnchor(anchorUserMessageId, userVariantGroupId)
            session.anchorUserMessageId = anchorUserMessageId
            session.anchorUserGroupId = _generatingAnchor.value?.userGroupId
        }

        generationJob = AiAssistantApp.instance.applicationScope.launch {
            session.generationJob = coroutineContext[Job]
            // 重新生成/重发必须是一次全新的连接尝试：启动前取消上一轮可能仍阻塞在
            // HTTP 读取中的残留调用（startSession 只取消了旧 Job，阻塞中的 call 不受影响），
            // 并彻底清空上一轮错误状态，防止旧轮次报错在新一轮开头被原样弹出
            repository.cancelActiveRequest(conversationId, cancelMemory = false)
            _isGenerating.value = true
            _currentResponse.value = ""
            _currentThinking.value = ""
            _error.value = null
            _reconnectStatus.value = null
            currentKeyAttemptErrors.clear()
            _keyAttemptErrors.value = emptyList()
            session._keyAttemptErrors.value = emptyList()
            val currentCallingModel = selectedOption.modelName
            val requestStartTime = System.currentTimeMillis()
            runtimeMessageModelMap[requestStartTime] = currentCallingModel

            val slowTimeoutJob = launch {
                kotlinx.coroutines.delay(120_000L)
                if (_isGenerating.value && !session.directionPhase && _reconnectStatus.value == null) {
                    val slowMsg = "响应耗时较长（已持续 120s+），若为长篇生成或深度推理请耐心稍候，也可随时点击停止..."
                    session.setStatus(slowMsg)
                    _reconnectStatus.value = slowMsg
                }
            }

            var currentUserMsgId: Long? = null
            try {
                if (saveUserMessage) {
                    val userMessage = Message(
                        conversationId = conversationId,
                        role = "user",
                        content = content,
                        attachments = attachmentsJson,
                        variantGroupId = userVariantGroupId,
                        variantIndex = userVariantIndex
                    )
                    val savedMsgId = repository.saveMessage(userMessage)
                    currentUserMsgId = savedMsgId
                    session.userMessageId = savedMsgId
                    // 普通发送/编辑重发：锚点为刚落库的用户消息（分组 id 直接取本次参数）
                    applyGeneratingAnchor(savedMsgId, userVariantGroupId)
                    session.anchorUserMessageId = savedMsgId
                    session.anchorUserGroupId = _generatingAnchor.value?.userGroupId
                }

                val selectedConfig = repository.getDecryptedConfig(selectedOption.apiConfigId)
                    ?: throw Exception("API配置不存在，请重新配置")
                systemPromptSaveJob?.join()
                val effectiveConfig = selectedConfig.copy(modelName = selectedOption.modelName)

                val roleplayRepo = AiAssistantApp.instance.roleplayRepository
                val currentRoleplaySession = _uiState.value.roleplaySession
                val effectiveSystemPrompt = if (currentRoleplaySession != null) {
                    val globalRpPrompt = AiAssistantApp.instance.personalizationManager.getSettings().globalRoleplayPrompt
                    roleplayRepo.assembleRoleplayContext(
                        sessionId = currentRoleplaySession.id,
                        globalSystemPrompt = currentSystemPrompt,
                        userMessage = null, // 设定与上下文纯净解耦，用户消息由 user 角色独立发送
                        globalRoleplayPrompt = globalRpPrompt,
                        queryText = content // 用于动态匹配世界书设定与外置记忆库相关条目
                    )
                } else {
                    currentSystemPrompt
                }

                var requestOptions = ChatRequestOptions(
                    temperature = settings?.temperature,
                    maxTokens = settings?.maxTokens,
                    topP = settings?.topP,
                    enableThinking = settings?.enableThinking ?: conversation?.enableThinking ?: effectiveConfig.enableThinking,
                    thinkingEffort = settings?.thinkingEffort ?: conversation?.thinkingEffort ?: effectiveConfig.thinkingEffort,
                    enableWebSearch = settings?.enableWebSearch,
                    enableSessionMemory = settings?.enableSessionMemory ?: conversation?.enableSessionMemory ?: true,
                    enableExternalMemory = settings?.enableExternalMemory ?: conversation?.enableExternalMemory ?: false,
                    enableWorldBook = settings?.enableWorldBook ?: conversation?.enableWorldBook ?: false,
                    activeWorldBookIds = settings?.activeWorldBookIds ?: conversation?.activeWorldBookIds,
                    overrideSystemPrompt = true,
                    systemPromptOverride = effectiveSystemPrompt,
                    contextWindowOverrideTokens = settings?.contextWindowTokens ?: conversation?.contextWindowTokens
                )

                val directionEnabled = settings?.enableReplyDirections ?: conversation?.enableReplyDirections ?: false
                if (directionEnabled && retryReplyDirection == null) {
                    session.directionPhase = true
                    val count = (settings?.replyDirectionCount ?: conversation?.replyDirectionCount ?: 2).coerceIn(2, 4)
                    var prepared: PreparedDirectionContext? = null
                    while (true) {
                        var directions = emptyList<ReplyDirection>()
                        var directionError: String? = null
                        try {
                            session._isConnecting.value = true
                            session.setStatus("正在生成 $count 个回复方向…")
                            _reconnectStatus.value = session.reconnectStatus.value
                            withContext(Dispatchers.IO) {
                                if (prepared == null) prepared = repository.prepareReplyDirectionContext(
                                    effectiveConfig, conversationId, content, requestOptions, assistantVariantGroupId)
                                directions = repository.generateReplyDirections(effectiveConfig, conversationId, content,
                                    attachments, requestOptions.copy(preparedDirectionContext = prepared), count,
                                    onStatus = { session.setStatus(it); _reconnectStatus.value = it })
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            directionError = e.message ?: "未知错误"
                        }
                        val decision = awaitReplyDirection(session, directions, directionError)
                        _pendingReplyDirection.value = null
                        when (decision.action) {
                            ReplyDirectionAction.RETRY -> continue
                            ReplyDirectionAction.CANCEL -> throw CancellationException("用户取消回复方向选择")
                            else -> {
                                session.selectedReplyDirection = ReplyDirections.instruction(decision, directions)
                                requestOptions = requestOptions.copy(preparedDirectionContext = prepared,
                                    replyDirection = session.selectedReplyDirection)
                                break
                            }
                        }
                    }
                } else if (directionEnabled && retryReplyDirection != null) {
                    session.selectedReplyDirection = retryReplyDirection
                    requestOptions = requestOptions.copy(replyDirection = retryReplyDirection)
                }
                session.directionPhase = false
                session._isConnecting.value = true
                session.setStatus(null)
                _reconnectStatus.value = null

                // 直接在主线程调用，通过withContext切换到IO线程
                withContext(Dispatchers.IO) {
                    repository.sendChatMessageWithConfig(
                        config = effectiveConfig,
                        conversationId = conversationId,
                        userMessage = content,
                        attachments = attachments,
                        options = requestOptions,
                        assistantVariantGroupId = assistantVariantGroupId,
                        assistantVariantIndex = assistantVariantIndex,
                        onToken = { token ->
                            // 上一轮已由新一轮取代时（isCurrentSession=false），其残留回调不得回写 ViewModel 状态，
                            // 否则旧轮次的报错/内容会污染新一轮显示（v2.6.8 需求 4 同款守卫，覆盖流式全程）
                            if (ChatGenerationManager.isCurrentSession(conversationId, session)) {
                                // 连接已恢复（首个正文 token 到达）：清掉过期的实时失败明细，避免成功流式后残留
                                if (token.isNotEmpty() && session.currentResponse.value.isBlank() && _keyAttemptErrors.value.isNotEmpty()) {
                                    _keyAttemptErrors.value = emptyList()
                                    session._keyAttemptErrors.value = emptyList()
                                    session.setAttemptError(null)
                                    _error.value = null
                                }
                                session.appendResponse(token)
                                _currentResponse.value = session.currentResponse.value
                            }
                        },
                        onThinkingToken = { token ->
                            if (ChatGenerationManager.isCurrentSession(conversationId, session)) {
                                // 思考 token 同样代表连接已建立
                                if (token.isNotEmpty() && session.currentThinking.value.isBlank() && _keyAttemptErrors.value.isNotEmpty()) {
                                    _keyAttemptErrors.value = emptyList()
                                    session._keyAttemptErrors.value = emptyList()
                                    session.setAttemptError(null)
                                    _error.value = null
                                }
                                session.appendThinking(token)
                                _currentThinking.value = session.currentThinking.value
                            }
                        },
                        onStatusUpdate = { status ->
                            if (ChatGenerationManager.isCurrentSession(conversationId, session)) {
                                session.setStatus(status)
                                _reconnectStatus.value = status
                            }
                        },
                        onKeyAttemptError = { keyIndex, keyMasked, errorMsg ->
                            // v2.6.8 需求 4：明细同时写入 Application 级会话，重进会话后仍可恢复显示
                            // （用户暂停时也能完整写进回复正文）
                            if (ChatGenerationManager.isCurrentSession(conversationId, session)) {
                                val line = "Key #$keyIndex ($keyMasked)：$errorMsg"
                                currentKeyAttemptErrors.add(line)
                                session._keyAttemptErrors.update { it + line }
                                _keyAttemptErrors.value = session.keyAttemptErrors.value
                                session.setAttemptError(errorMsg)
                                _error.value = errorMsg
                            }
                        },
                        onResetBuffer = {
                            if (ChatGenerationManager.isCurrentSession(conversationId, session)) {
                                session.resetBuffer()
                                _currentResponse.value = ""
                                _currentThinking.value = ""
                            }
                        },
                        onComplete = { replyContent, _, _ ->
                            slowTimeoutJob.cancel()
                            session.isMessageSaved.compareAndSet(false, true)
                            session.markFinished()
                            // v2.6.8 需求 4：按会话身份移除并判定"这一轮是否仍是活跃轮次"——
                            // 用户停止后立刻重发、或同一会话存在两个页面实例时，上一轮的收尾
                            // 不得清空新一轮的流式状态与生成锚点，也不得把新会话从表中误删
                            val isStillCurrentRound = ChatGenerationManager.isCurrentSession(conversationId, session)
                            ChatGenerationManager.removeSession(conversationId, session)
                            if (isStillCurrentRound) {
                                isMessageSaved = true
                                _isGenerating.value = false
                                _generatingAnchor.value = null
                                activeAssistantVariantGroupId = null
                                activeAssistantVariantIndex = 1
                                val savedReply = replyContent.ifBlank { session.currentResponse.value.ifBlank { _currentResponse.value } }
                                _currentResponse.value = ""
                                _currentThinking.value = ""
                                _reconnectStatus.value = null
                                autoNameIfNeeded()
                                // 重新同步会话状态与上下文使用情况（后台降级自动同步）
                                refreshContextUsage()
                                evaluateAutoCompression()
                                evaluateAutoTimelineUpdate(content, savedReply, selectedOption,
                                    settings?.enableSessionMemory ?: conversation?.enableSessionMemory)
                                checkAndDispatchQueue()
                            }
                        },
                        onError = { errorMsg ->
                            slowTimeoutJob.cancel()
                            val isStillCurrentRound = ChatGenerationManager.isCurrentSession(conversationId, session)
                            if (isStillCurrentRound) {
                                _reconnectStatus.value = null
                                refreshContextUsage()
                            }
                            val partialResponse = session.currentResponse.value.trim()
                            val partialThinking = session.currentThinking.value.trim().ifEmpty { null }
                            val hasPartialContent = partialResponse.isNotBlank() || partialThinking != null

                            if (hasPartialContent) {
                                // 需求 3：只要模型已经输出思考或部分回复，不管是否中断/取消/关闭连接，必须 100% 入库保存
                                // （仅限当前轮次：被新一轮取代的旧轮次，其报错/残文不得再写进会话，
                                // 否则表现为"重新生成后旧报错直接弹出"）
                                if (isStillCurrentRound && session.isMessageSaved.compareAndSet(false, true)) {
                                    isMessageSaved = true
                                    session.setError(errorMsg)
                                    saveErrorReply(errorMsg, session, isUserStopping)
                                }
                                session.markFinished()
                                ChatGenerationManager.removeSession(conversationId, session)
                                if (isStillCurrentRound) {
                                    _isGenerating.value = false
                                    _generatingAnchor.value = null
                                    _currentResponse.value = ""
                                    _currentThinking.value = ""
                                }
                            } else if (isUserStopping || errorMsg.contains("Socket closed", ignoreCase = true) || errorMsg.contains("Canceled", ignoreCase = true)) {
                                session.markFinished()
                                ChatGenerationManager.removeSession(conversationId, session)
                                if (isStillCurrentRound) {
                                    _isGenerating.value = false
                                    _generatingAnchor.value = null
                                    _currentResponse.value = ""
                                    _currentThinking.value = ""
                                }
                            } else if (isStillCurrentRound && session.isMessageSaved.compareAndSet(false, true)) {
                                isMessageSaved = true
                                session.setError(errorMsg)
                                saveErrorReply(errorMsg, session, false)
                                session.markFinished()
                                ChatGenerationManager.removeSession(conversationId, session)
                                if (isStillCurrentRound) {
                                    _isGenerating.value = false
                                    _generatingAnchor.value = null
                                    _error.value = errorMsg
                                    _currentResponse.value = ""
                                    _currentThinking.value = ""
                                }
                            }
                        },
                        onContextFallbackPrompt = { reason ->
                            kotlinx.coroutines.withTimeoutOrNull(CONTEXT_FALLBACK_PROMPT_TIMEOUT_MS) {
                                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                                    val promptState = com.aiassistant.domain.model.ContextFallbackPromptState(
                                        conversationId = conversationId,
                                        reason = reason,
                                        onDecision = { choice ->
                                            session._pendingContextFallbackPrompt.value = null
                                            _pendingContextFallbackPrompt.value = null
                                            if (cont.isActive) {
                                                cont.resumeWith(Result.success(choice))
                                            }
                                        }
                                    )
                                    session._pendingContextFallbackPrompt.value = promptState
                                    _pendingContextFallbackPrompt.value = promptState
                                    cont.invokeOnCancellation {
                                        session._pendingContextFallbackPrompt.value = null
                                        _pendingContextFallbackPrompt.value = null
                                    }
                                }
                            } ?: com.aiassistant.domain.model.ContextFallbackChoice.FALLBACK
                        }
                    )
                }
            } catch (e: Exception) {
                slowTimeoutJob.cancel()
                val isStillCurrentRound = ChatGenerationManager.isCurrentSession(conversationId, session)
                if (isStillCurrentRound) {
                    _reconnectStatus.value = null
                }
                val partialResponse = session.currentResponse.value.trim()
                val partialThinking = session.currentThinking.value.trim().ifEmpty { null }
                val hasPartialContent = partialResponse.isNotBlank() || partialThinking != null
                val errorMsg = e.message ?: "未知错误"

                if (hasPartialContent) {
                    // 需求 3：异常分支同样 100% 抢救保存思考与正文（仅限当前轮次，旧轮次残文不得写回会话）
                    if (isStillCurrentRound && session.isMessageSaved.compareAndSet(false, true)) {
                        isMessageSaved = true
                        session.setError(errorMsg)
                        saveErrorReply(errorMsg, session, isUserStopping)
                    }
                    session.markFinished()
                    ChatGenerationManager.removeSession(conversationId, session)
                    if (isStillCurrentRound) {
                        _isGenerating.value = false
                        _generatingAnchor.value = null
                        _currentResponse.value = ""
                        _currentThinking.value = ""
                    }
                    return@launch
                }

                if (isUserStopping || e is CancellationException || e.message?.contains("Socket closed", ignoreCase = true) == true || e.message?.contains("Canceled", ignoreCase = true) == true) {
                    session.markFinished()
                    ChatGenerationManager.removeSession(conversationId, session)
                    if (isStillCurrentRound) {
                        _isGenerating.value = false
                        _generatingAnchor.value = null
                        _currentResponse.value = ""
                        _currentThinking.value = ""
                    }
                    return@launch
                }
                if (isStillCurrentRound) {
                    _isGenerating.value = false
                    _generatingAnchor.value = null
                }
                if (isStillCurrentRound && session.isMessageSaved.compareAndSet(false, true)) {
                    isMessageSaved = true
                    session.setError(errorMsg)
                    saveErrorReply(errorMsg, session, false)
                    session.markFinished()
                    ChatGenerationManager.removeSession(conversationId, session)
                    if (isStillCurrentRound) {
                        _error.value = errorMsg
                        _currentResponse.value = ""
                        _currentThinking.value = ""
                    }
                }
            } finally {
                slowTimeoutJob.cancel()
                session.pendingReplyDirection.value = null
                if (ChatGenerationManager.isCurrentSession(conversationId, session) || ChatGenerationManager.getSession(conversationId) == null) _pendingReplyDirection.value = null
                // Never await memory before the foreground request. One background pass
                // supplies both automatic memory and the review candidate, including on
                // ordinary chat errors; stopped/cancelled rounds do not launch new requests.
                if (!isUserStopping && !session.directionPhase && coroutineContext[Job]?.isActive == true) {
                    evaluateMemoryCandidate(currentUserMsgId, selectedOption,
                        settings?.enableSessionMemory ?: conversation?.enableSessionMemory)
                }
            }
        }
    }

    private fun saveErrorReply(
        errorMsg: String,
        session: ChatGenerationManager.ActiveSession? = null,
        isUserStopping: Boolean = false
    ) {
        val partialResponse = (session?.currentResponse?.value ?: _currentResponse.value).trim()
        val partialThinking = (session?.currentThinking?.value ?: _currentThinking.value).trim().ifEmpty { null }
        val variantGroupId = session?.assistantVariantGroupId ?: activeAssistantVariantGroupId
        val variantIndex = session?.assistantVariantIndex ?: activeAssistantVariantIndex
        val savedModelName = session?.callingModel?.value?.ifBlank { null }
            ?: _currentModel.value?.ifBlank { null }
            ?: conversation?.modelName
        activeAssistantVariantGroupId = null
        activeAssistantVariantIndex = 1
        val expectedMutationEpoch = ChatGenerationManager.mutationEpoch(conversationId)
        AiAssistantApp.instance.applicationScope.launch {
            val content = when {
                partialResponse.isNotBlank() -> {
                    if (isUserStopping) {
                        "$partialResponse\n\n*(回复已被暂停)*"
                    } else {
                        "$partialResponse\n\n*(输出已被中断: $errorMsg)*"
                    }
                }
                !partialThinking.isNullOrBlank() -> {
                    if (isUserStopping) {
                        "*(思考已停止，回复已暂停)*"
                    } else {
                        "*(思考已输出，回复已被中断: $errorMsg)*"
                    }
                }
                else -> {
                    buildString {
                        append("请求失败\n\n")
                        append(errorMsg.trim().ifBlank { "未知错误" })
                        append("\n\n可以检查 API 地址、密钥、模型名称或网络状态后重试。")
                    }
                }
            }
            val message = Message(
                conversationId = conversationId,
                role = "assistant",
                content = content,
                thinkingContent = partialThinking,
                variantGroupId = variantGroupId,
                variantIndex = variantIndex,
                modelName = savedModelName,
                replyDirection = session?.selectedReplyDirection
            )
            repository.saveMessage(message, expectedMutationEpoch)
            withContext(Dispatchers.Main) {
                loadConversation()
            }
        }
    }

    fun stopGeneration() {
        isUserStopping = true
        // v2.6.8 需求 4：待确认提示可能挂在 Application 级会话上（用户离开过再回来），
        // 两处都要应答，避免请求协程继续挂起
        val stopFallbackChoice = com.aiassistant.domain.model.ContextFallbackChoice.IGNORE
        _pendingContextFallbackPrompt.value?.onDecision(stopFallbackChoice)
        _pendingContextFallbackPrompt.value = null
        ChatGenerationManager.getSession(conversationId)?.answerContextFallbackPrompt(stopFallbackChoice)
        repository.cancelActiveRequest(conversationId)
        generationJob?.cancel(CancellationException("用户暂停生成"))
        _isGenerating.value = false
        _generatingAnchor.value = null

        val session = ChatGenerationManager.getSession(conversationId)
        if (session?.directionPhase == true) {
            session.isMessageSaved.set(true)
            session.markFinished()
            ChatGenerationManager.removeSession(conversationId, session)
            _pendingReplyDirection.value = null
            _currentResponse.value = ""
            _currentThinking.value = ""
            _reconnectStatus.value = null
            return
        }
        val responseToSave = (session?.currentResponse?.value ?: _currentResponse.value).trim()
        val thinkingToSave = (session?.currentThinking?.value ?: _currentThinking.value).trim().ifEmpty { null }
        val variantGroupId = session?.assistantVariantGroupId ?: activeAssistantVariantGroupId
        val variantIndex = session?.assistantVariantIndex ?: activeAssistantVariantIndex
        activeAssistantVariantGroupId = null
        activeAssistantVariantIndex = 1

        val connStatus = (session?.reconnectStatus?.value ?: _reconnectStatus.value)?.takeIf { it.isNotBlank() }
        val activeError = (session?.error?.value ?: _error.value)?.takeIf { it.isNotBlank() }
        // v2.6.8 需求 4：Key 尝试报错明细优先取 Application 级会话（重进会话后 ViewModel 本地列表为空，
        // 若只读本地会丢掉全部明细，写进「回复已暂停」正文的连接异常记录也随之丢失）
        val keyAttemptErrors = session?.keyAttemptErrors?.value?.takeIf { it.isNotEmpty() }
            ?: currentKeyAttemptErrors.toList()
        val hasErrors = keyAttemptErrors.isNotEmpty() || connStatus != null || activeError != null

        val shouldSave = session?.isMessageSaved?.compareAndSet(false, true) ?: !isMessageSaved
        if (shouldSave) {
            isMessageSaved = true
            session?.markFinished()
            ChatGenerationManager.removeSession(conversationId)
            val finalContent = buildString {
                when {
                    responseToSave.isNotBlank() -> {
                        append(responseToSave)
                        append("\n\n*(回复已被暂停)*")
                    }
                    thinkingToSave != null -> {
                        append("*(思考已停止，回复已暂停)*")
                    }
                    else -> {
                        append("回复已停止 (用户已暂停)")
                    }
                }

                if (hasErrors) {
                    append("\n\n【连接异常信息记录】：")
                    if (connStatus != null) {
                        append("\n• 当前状态: $connStatus")
                    }
                    if (activeError != null && activeError != connStatus) {
                        append("\n• 报错详情: $activeError")
                    }
                    if (keyAttemptErrors.isNotEmpty()) {
                        append("\n• 尝试的 Key 报错记录:")
                        keyAttemptErrors.forEach { append("\n  - $it") }
                    }
                }
            }.trim()
            val savedModelName = session?.callingModel?.value?.ifBlank { null }
                ?: _currentModel.value?.ifBlank { null }
                ?: conversation?.modelName
            AiAssistantApp.instance.applicationScope.launch {
                val message = Message(
                    conversationId = conversationId,
                    role = "assistant",
                    content = finalContent,
                    thinkingContent = thinkingToSave,
                    variantGroupId = variantGroupId,
                    variantIndex = variantIndex,
                    modelName = savedModelName
                )
                repository.saveMessage(message)
            }

            // 使用统计修正（v2.6.7 需求 5e）：本轮出现过报错/重连后用户主动暂停，
            // 同样属于一次失败的模型调用。此前取消路径完全不写 api_usage_stats，
            // 失败请求缺失导致成功率虚高。随 shouldSave 的 CAS 保证只补记一次。
            if (hasErrors) {
                val failReason = connStatus ?: activeError
                    ?: keyAttemptErrors.lastOrNull() ?: "连接异常"
                val statOption = _currentModelOption.value
                AiAssistantApp.instance.applicationScope.launch {
                    runCatching {
                        AiAssistantApp.instance.database.usageStatDao().insertStat(
                            ApiUsageStat(
                                apiConfigId = statOption?.apiConfigId ?: apiConfig?.id ?: 0L,
                                provider = statOption?.provider ?: apiConfig?.provider ?: "unknown",
                                modelName = statOption?.modelName
                                    ?: _currentModel.value?.ifBlank { null }
                                    ?: conversation?.modelName
                                    ?: "unknown",
                                success = false,
                                errorMessage = "用户暂停生成（此前连接报错：${failReason.take(160)}）"
                            )
                        )
                    }
                }
            }
        }
        _currentResponse.value = ""
        _currentThinking.value = ""
    }

    fun translateMessageThinking(message: Message) {
        val thinking = message.thinkingContent
        if (thinking.isNullOrBlank()) return
        if (_translatingMessageIds.value.contains(message.id)) return

        viewModelScope.launch {
            _translatingMessageIds.update { it + message.id }
            try {
                val settings = AiAssistantApp.instance.personalizationManager.getSettings()
                val targetConfigId = if (settings.thinkingTranslationApiConfigId > 0L) {
                    settings.thinkingTranslationApiConfigId
                } else {
                    _currentModelOption.value?.apiConfigId ?: conversation?.apiConfigId ?: 0L
                }
                val targetModel = if (settings.thinkingTranslationModel.isNotBlank()) {
                    settings.thinkingTranslationModel
                } else {
                    _currentModel.value ?: conversation?.modelName.orEmpty()
                }

                val result = repository.translateThinkingContent(
                    thinkingText = thinking,
                    targetApiConfigId = targetConfigId,
                    targetModelName = targetModel
                )
                result.onSuccess { translated ->
                    repository.updateTranslatedThinking(message.id, translated)
                }.onFailure { e ->
                    _error.value = "思考链翻译失败: ${e.message}"
                }
            } catch (e: Exception) {
                _error.value = "思考链翻译异常: ${e.message}"
            } finally {
                _translatingMessageIds.update { it - message.id }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    // 自动校准并修复数据库中历史可能存在的重复分支序号
    private fun repairDuplicateVariantIndices(messageList: List<Message>) {
        val grouped = messageList.filter { !it.variantGroupId.isNullOrBlank() }.groupBy { it.variantGroupId!! }
        grouped.forEach { (_, groupMsgs) ->
            if (groupMsgs.size > 1 && groupMsgs.map { it.variantIndex }.distinct().size < groupMsgs.size) {
                val sorted = groupMsgs.sortedWith(compareBy<Message> { it.variantIndex }.thenBy { it.createdAt }.thenBy { it.id })
                viewModelScope.launch(Dispatchers.IO) {
                    sorted.forEachIndexed { idx, msg ->
                        val expected = idx + 1
                        if (msg.variantIndex != expected) {
                            AiAssistantApp.instance.database.messageDao().updateMessage(
                                msg.copy(variantIndex = expected)
                            )
                        }
                    }
                }
            }
        }
    }

    // 重新生成AI回复（支持传入指定目标消息或自动定位最后一条回复）
    fun regenerateLastMessage(targetAssistant: Message? = null) {
        if (_isGenerating.value) return

        viewModelScope.launch {
            val messages = repository.getMessagesList(conversationId)
            if (messages.isEmpty()) return@launch

            // 优先使用传入的目标消息，或者找到最后一条有效AI消息
            val lastAssistantMessage = targetAssistant?.let { target ->
                messages.firstOrNull { it.id == target.id } ?: target
            } ?: messages.lastOrNull { it.role == "assistant" } ?: return@launch

            val isError = AiRepository.isErrorPlaceholderMessage(lastAssistantMessage.content)
            if (isError) {
                // 如果最后一条是错误占位消息，重新生成时直接删除该错误消息，避免污染会话记录与多分支
                repository.deleteMessage(lastAssistantMessage)
            }
            val lastUserMessage = messages.lastOrNull { it.role == "user" && it.createdAt < lastAssistantMessage.createdAt }
                ?: messages.lastOrNull { it.role == "user" }
            if (lastUserMessage != null) {
                val groupId = if (isError) {
                    lastAssistantMessage.variantGroupId
                } else {
                    lastAssistantMessage.variantGroupId ?: "reply_${lastAssistantMessage.id}"
                }
                if (!isError && lastAssistantMessage.variantGroupId == null) {
                    AiAssistantApp.instance.database.messageDao().updateMessage(
                        lastAssistantMessage.copy(variantGroupId = groupId, variantIndex = 1)
                    )
                }

                // 计算下一个 variantIndex：必须严格自增，杜绝序号冲突
                val nextIndex = if (groupId != null) {
                    if (isError) {
                        lastAssistantMessage.variantIndex.coerceAtLeast(1)
                    } else {
                        val groupVariants = messages.filter { it.variantGroupId == groupId || it.id == lastAssistantMessage.id }
                        val maxExistingIndex = groupVariants.maxOfOrNull { it.variantIndex } ?: 1
                        maxOf(groupVariants.size, maxExistingIndex) + 1
                    }
                } else 1

                sendMessageInternal(
                    content = lastUserMessage.content,
                    saveUserMessage = false,
                    assistantVariantGroupId = groupId,
                    assistantVariantIndex = nextIndex,
                    anchorUserMessageId = lastUserMessage.id,
                    retryReplyDirection = if (isError) lastAssistantMessage.replyDirection else null
                )
            }
        }
    }

    // 删除单条消息
    // v2.6.5 修正：删除消息绝不影响进行中的生成（v2.6.4 曾误改为删除即取消生成，导致正在
    // 连接/输出的回复直接消失）；删除同一位置的过去回复时，流式回复的挂载位置由
    // 生成锚点（触发本轮的用户消息）钉住，与被删消息无关，详见 ChatScreen 挂载判定。
    fun deleteMessage(message: Message) {
        if (ChatGenerationManager.getSession(conversationId)?.directionPhase == true) stopGeneration()
        viewModelScope.launch {
            repository.deleteMessage(message)
        }
    }

    fun deleteMessagesFrom(message: Message) {
        if (ChatGenerationManager.getSession(conversationId)?.directionPhase == true) stopGeneration()
        viewModelScope.launch {
            repository.deleteMessagesFrom(conversationId, message.createdAt)
        }
    }

    // 重命名对话标题
    fun renameConversation(newTitle: String) {
        viewModelScope.launch {
            conversation?.let { conv ->
                val updated = conv.copy(title = newTitle)
                AiAssistantApp.instance.database.conversationDao().updateConversation(updated)
                conversation = updated
                _uiState.update { it.copy(conversationTitle = newTitle) }
            }
        }
    }

    // 自动生成标题（优先使用配置的独立自动命名模型）
    fun generateAutoTitle() {
        viewModelScope.launch {
            val generated = repository.generateConversationTitle(conversationId)
            if (generated != null) {
                renameConversation(generated)
            } else {
                val messages = repository.getMessagesList(conversationId)
                val firstUserMessage = messages.firstOrNull { it.role == "user" }
                if (firstUserMessage != null) {
                    val title = generateTitleFromContent(firstUserMessage.content)
                    renameConversation(title)
                }
            }
        }
    }

    private fun generateTitleFromContent(content: String): String {
        // 简单的标题生成逻辑
        val cleanContent = content.trim()
        return when {
            cleanContent.length <= 20 -> cleanContent
            cleanContent.contains("\n") -> cleanContent.substringBefore("\n").take(20) + "..."
            else -> cleanContent.take(20) + "..."
        }
    }

    private fun autoNameIfNeeded() {
        AiAssistantApp.instance.applicationScope.launch {
            val latestConversation = repository.getConversationById(conversationId) ?: return@launch
            if (latestConversation.title != "新对话" && latestConversation.title.isNotBlank()) {
                return@launch
            }

            val generatedTitle = repository.generateConversationTitle(conversationId)
            val fallbackTitle = repository.getMessagesList(conversationId)
                .firstOrNull { it.role == "user" }
                ?.content
                ?.let { generateTitleFromContent(it) }
            val title = generatedTitle ?: fallbackTitle ?: return@launch
            val updated = latestConversation.copy(title = title)
            AiAssistantApp.instance.database.conversationDao().updateConversation(updated)
            conversation = updated
            _uiState.update { it.copy(conversationTitle = title) }
        }
    }

    fun updateSystemPrompt(prompt: String?) {
        val normalizedPrompt = normalizeSystemPrompt(prompt)
        conversation?.let { conv ->
            val updated = conv.copy(systemPrompt = normalizedPrompt)
            conversation = updated
            _uiState.update { it.copy(systemPrompt = normalizedPrompt) }
            systemPromptSaveJob?.cancel()
            systemPromptSaveJob = viewModelScope.launch {
                AiAssistantApp.instance.database.conversationDao().updateConversation(updated)
            }
        }
    }

    private fun normalizeSystemPrompt(prompt: String?): String? {
        return prompt?.trim()?.ifBlank { null }
    }

    // 创建会话分支：基于 Room 事务批量落库、继承活跃模型与全部参数、深度克隆角色扮演与记忆设定
    fun createBranch(
        messageId: Long,
        sourceMessages: List<Message>? = null,
        onComplete: (Long, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val effectiveOption = _currentModelOption.value
                val rawMessages = if (!sourceMessages.isNullOrEmpty()) {
                    sourceMessages
                } else {
                    repository.getMessagesList(conversationId)
                }

                val (newConversationId, branchTitle) = repository.createBranchConversation(
                    parentId = conversationId,
                    branchMessageId = messageId,
                    sourceMessages = rawMessages,
                    activeApiConfigId = effectiveOption?.apiConfigId,
                    activeModelName = effectiveOption?.modelName
                )

                withContext(Dispatchers.Main) {
                    onComplete(newConversationId, branchTitle)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "创建分支对话失败", e)
                withContext(Dispatchers.Main) {
                    _error.value = "创建分支失败: ${e.message}"
                }
            }
        }
    }

    fun createBranch(
        messageId: Long,
        sourceMessages: List<Message>? = null,
        onCompleteLegacy: (Long) -> Unit
    ) {
        createBranch(messageId, sourceMessages) { newId, _ -> onCompleteLegacy(newId) }
    }

    fun acceptPendingMemory(scope: String, customContent: String? = null) {
        val candidate = _pendingMemoryCandidate.value ?: return
        val finalContent = customContent?.trim()?.ifBlank { null } ?: candidate.distilledContent
        viewModelScope.launch {
            val targetScope = if (scope == "session" || scope == "conversation") "conversation" else "user"
            repository.saveConfirmedMemory(
                content = finalContent,
                scope = targetScope,
                conversationId = if (targetScope == "conversation") candidate.conversationId else null,
                sourceMessageId = candidate.sourceMessageId
            )
            _pendingMemoryCandidate.value = null
        }
    }

    fun dismissPendingMemory() {
        _pendingMemoryCandidate.value = null
    }

    fun addSessionMemory(content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.addConversationMemory(conversationId, trimmed)
            loadConversation()
        }
    }

    fun updateSessionMemory(memory: MemoryItem) {
        viewModelScope.launch {
            repository.updateMemory(memory.copy(updatedAt = System.currentTimeMillis()))
            loadConversation()
        }
    }

    fun toggleSessionMemory(id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.setMemoryEnabled(id, isEnabled)
            loadConversation()
        }
    }

    fun deleteSessionMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
            loadConversation()
        }
    }

    fun clearSessionMemories() {
        viewModelScope.launch {
            repository.clearConversationMemories(conversationId)
            loadConversation()
        }
    }

    fun startTimelineReconciliation(startFromDraft: Boolean = false, fromCheckpoint: Boolean = false) {
        if (_isReconcilingTimeline.value) return
        _isReconcilingTimeline.value = true
        _timelineReconcileProgress.value = when {
            startFromDraft -> "正在读取先前进度继续梳理..."
            fromCheckpoint -> "正在结合原有时间线，梳理后续新对话..."
            else -> "准备分析对话历史..."
        }
        timelineReconcileJob?.cancel()
        timelineReconcileJob = viewModelScope.launch {
            try {
                val activeCfgId = apiConfig?.id ?: _currentModelOption.value?.apiConfigId
                val activeModel = _currentModel.value?.ifBlank { null } ?: _currentModelOption.value?.modelName.orEmpty()
                val draft = if (startFromDraft) {
                    withContext(Dispatchers.IO) {
                        TimelineDraftManager.getDraft(AiAssistantApp.instance, conversationId)
                    }
                } else null
                val checkpoint = if (fromCheckpoint) {
                    _timelineCheckpoint.value ?: withContext(Dispatchers.IO) {
                        TimelineDraftManager.getCheckpoint(AiAssistantApp.instance, conversationId)
                    }
                } else null
                val existingNodes = if (fromCheckpoint) timelineNodes.value else null
                val result = repository.reconcileConversationTimeline(
                    conversationId = conversationId,
                    activeConfigId = activeCfgId,
                    activeModelName = activeModel,
                    startFromDraft = draft,
                    reconcileCheckpoint = checkpoint,
                    existingTimelineNodes = existingNodes,
                    onProgress = { step, total, detail ->
                        _timelineReconcileProgress.value = if (total > 1) "[$step/$total] $detail" else detail
                    },
                    onIntermediateResult = { intermediateDraft ->
                        TimelineDraftManager.saveDraft(AiAssistantApp.instance, intermediateDraft)
                        _liveReconcileDraft.value = intermediateDraft
                    }
                )
                _timelineReconcileResult.value = result
                _showTimelineReconcileDialog.value = true
            } catch (e: CancellationException) {
                // 用户主动取消，当前进度已实时留存在本地草稿文件
                Log.d("ChatViewModel", "时间线梳理已暂停/取消，中间结果已保存草稿")
            } catch (e: Exception) {
                _timelineReconcileResult.value = TimelineReconcileResult(
                    currentStoryTime = "未确定",
                    events = mutableListOf(),
                    extractionSource = "LOCAL_FALLBACK",
                    extractionErrorMessage = e.message ?: "提炼请求异常"
                )
                _showTimelineReconcileDialog.value = true
            } finally {
                _isReconcilingTimeline.value = false
                _timelineReconcileProgress.value = null
                timelineReconcileJob = null
            }
        }
    }

    private fun evaluateAutoTimelineUpdate(
        userMsg: String, assistantReply: String, selectedOption: ChatModelOption, sessionMemoryEnabled: Boolean?
    ) {
        val epoch = ChatGenerationManager.mutationEpoch(conversationId)
        ChatGenerationManager.beginTimelineExtraction(conversationId)
        AiAssistantApp.instance.applicationScope.launch(Dispatchers.IO) {
            try {
                val result = repository.evaluateAndAutoUpdateTimeline(
                    conversationId = conversationId,
                    userMessage = userMsg,
                    assistantReply = assistantReply,
                    activeConfigId = selectedOption.apiConfigId,
                    activeModelName = selectedOption.modelName,
                    sessionMemoryEnabled = sessionMemoryEnabled
                )
                if (result != null && epoch == ChatGenerationManager.mutationEpoch(conversationId)) {
                    // 需求 1：自动识别到的时间和事件放入待确认提案，由用户在界面交互确认后再应用
                    ChatGenerationManager.offerTimelineProposal(conversationId, result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ChatViewModel", "自动更新时间线后台任务异常: ${e.message}")
                if (epoch == ChatGenerationManager.mutationEpoch(conversationId)) {
                    ChatGenerationManager.offerTimelineProposal(conversationId, AutoTimelineUpdateResult(
                        null, null, "本轮提取未完成", extractionErrorMessage = "本轮提取失败，请检查模型配置或手动梳理"))
                }
            } finally {
                ChatGenerationManager.endTimelineExtraction(conversationId)
            }
        }
    }

    private fun evaluateMemoryCandidate(
        messageId: Long?,
        selectedOption: ChatModelOption,
        sessionMemoryEnabled: Boolean?
    ) {
        if (_uiState.value.roleplaySession == null && messageId != null) {
            AiAssistantApp.instance.applicationScope.launch(Dispatchers.IO) {
                try {
                    val candidate = repository.processSavedMessageMemory(
                        messageId = messageId,
                        activeConfigId = selectedOption.apiConfigId,
                        activeModelName = selectedOption.modelName,
                        sessionMemoryEnabled = sessionMemoryEnabled
                    )
                    if (candidate != null && sessionMemoryEnabled != false) {
                        _pendingMemoryCandidate.value = candidate
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("ChatViewModel", "后台提取记忆候选异常: ${e.message}")
                }
            }
        }
    }

    fun applyTimelineProposal(proposal: AutoTimelineUpdateResult) {
        val pending = pendingTimelineProposal.value
        viewModelScope.launch {
            try {
                repository.applyAutoTimelineProposal(conversationId, proposal)
                ChatGenerationManager.dismissTimelineProposal(conversationId, pending)
                _timelineUpdateNotice.value = "已保存确认的时间线与设定"
                loadConversation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ChatViewModel", "应用时间线提案失败: ${e.message}", e)
                _timelineUpdateNotice.value = "保存失败，待确认内容已保留，请重试"
            }
        }
    }

    fun dismissTimelineReconcileDialog() {
        _showTimelineReconcileDialog.value = false
        _timelineReconcileResult.value = null
    }

    fun getSavedTimelineDraft(): TimelineReconcileDraft? {
        return TimelineDraftManager.getDraft(AiAssistantApp.instance, conversationId)
    }

    fun hasTimelineDraft(): Boolean {
        return TimelineDraftManager.hasDraft(AiAssistantApp.instance, conversationId)
    }

    fun clearTimelineDraft() {
        TimelineDraftManager.clearDraft(AiAssistantApp.instance, conversationId)
        _liveReconcileDraft.value = null
    }

    fun openSavedDraftForReview() {
        // 草稿文件可能损坏（显式 null / 未知枚举）：无害化后展示，彻底坏则静默忽略，绝不闪退
        val draft = try {
            TimelineDraftManager.sanitizeDraft(getSavedTimelineDraft() ?: _liveReconcileDraft.value) ?: return
        } catch (e: Exception) {
            Log.w("ChatViewModel", "打开时间线草稿失败，已忽略损坏草稿: ${e.message}")
            return
        }
        _timelineReconcileResult.value = TimelineReconcileResult(
            currentStoryTime = draft.currentStoryTime,
            events = draft.events.toMutableList(),
            atemporalSettings = draft.atemporalSettings.toMutableList(),
            extractionSource = "SAVED_DRAFT"
        )
        _showTimelineReconcileDialog.value = true
    }

    fun openLiveDraftForReview() {
        val draft = try {
            TimelineDraftManager.sanitizeDraft(_liveReconcileDraft.value ?: getSavedTimelineDraft()) ?: return
        } catch (e: Exception) {
            Log.w("ChatViewModel", "打开实时梳理快照失败，已忽略损坏草稿: ${e.message}")
            return
        }
        _timelineReconcileResult.value = TimelineReconcileResult(
            currentStoryTime = draft.currentStoryTime,
            events = draft.events.toMutableList(),
            atemporalSettings = draft.atemporalSettings.toMutableList(),
            extractionSource = "LIVE_PROGRESS"
        )
        _showTimelineReconcileDialog.value = true
    }

    fun applySavedDraft() {
        val draft = try {
            TimelineDraftManager.sanitizeDraft(getSavedTimelineDraft()) ?: return
        } catch (e: Exception) {
            Log.w("ChatViewModel", "应用时间线草稿失败，已忽略损坏草稿: ${e.message}")
            return
        }
        applyReconciledTimeline(
            currentStoryTime = draft.currentStoryTime,
            events = draft.events,
            confirmedSettings = draft.atemporalSettings
        )
    }

    fun applyReconciledTimeline(
        currentStoryTime: String,
        events: List<TimelineEventItem>,
        confirmedSettings: List<AtemporalSettingItem> = emptyList()
    ) {
        viewModelScope.launch {
            // 1. 故事时间独立存入 Conversation 表
            val cleanStoryTime = currentStoryTime.trim().takeIf { it.isNotBlank() && it != "未确定" }
            repository.updateStoryTime(conversationId, cleanStoryTime)

            // 2. 时间与事件独立存入 timeline_nodes 表，保留明确时序与分类
            val nodesToSave = events.mapIndexed { idx, ev ->
                TimelineNode(
                    conversationId = conversationId,
                    timeTag = ev.timeTag.trim(),
                    event = ev.content.trim(),
                    category = ev.category.key,
                    orderIndex = idx,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
            repository.replaceTimelineNodes(conversationId, nodesToSave)

            // 3. 清理之前误存放在 memory_items 中的时间线碎片，但完整保留用户自定义的纯净设定与规则
            try {
                val oldMemories = repository.getConversationMemoriesList(conversationId)
                for (oldMem in oldMemories) {
                    val trimmed = oldMem.content.trim()
                    if (trimmed.startsWith("【当前故事时间】：") || trimmed.startsWith("当前故事时间：") || TimelineMemoryHelper.isExplicitTimelineEvent(trimmed)) {
                        repository.deleteMemory(oldMem.id)
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatViewModel", "清理旧时间线记忆缓存失败: ${e.message}")
            }

            // 4. 需求 8：展示的世界观与固有设定 100% 真正保存进记忆中（在会话专属记忆与角色扮演记忆中立即可见并生效）
            val roleplayRepo = AiAssistantApp.instance.roleplayRepository
            val currentRpSession = _uiState.value.roleplaySession
            for (setting in confirmedSettings) {
                if (setting.isSelected && setting.content.isNotBlank()) {
                    val formatted = "【${setting.category}】${setting.content.trim()}"
                    // 无论 targetScope 是 session 还是 global，都向当前会话专属记忆存入一条（带 conversationId），确保在当前会话的“会话专属记忆与规则”列表中 100% 立即可见！
                    repository.addConversationMemory(conversationId, formatted)
                    // 若用户明确选择 global，额外存入一条 user 级全局记忆，让跨会话生效
                    if (setting.targetScope == "global") {
                        repository.addUserMemory(formatted)
                    }
                    // 若当前处于角色扮演/剧情创作会话，同时写入角色扮演专属记忆表 (RoleplayMemory) 并标记为已固定，确保在角色扮演上下文组装中立即生效
                    if (currentRpSession != null) {
                        try {
                            roleplayRepo.insertMemory(
                                RoleplayMemory(
                                    sessionId = currentRpSession.id,
                                    memoryType = "fact",
                                    content = formatted,
                                    isPinned = true
                                )
                            )
                        } catch (e: Exception) {
                            Log.w("ChatViewModel", "保存角色扮演专属记忆失败: ${e.message}")
                        }
                    }
                }
            }

            // 确保当前会话的会话记忆功能处于开启状态，使刚存入的设定立即可用
            conversation?.let { conv ->
                if (conv.enableSessionMemory != true) {
                    repository.updateConversation(conv.copy(enableSessionMemory = true))
                }
            }

            // 记录时间线水线检查点 (Checkpoint)
            try {
                val allMsgs = _messages.value
                    .filter { !it.isExcluded && it.role != "system" && it.content.isNotBlank() }
                    .sortedBy { it.createdAt }
                val lastProcessedId = _timelineReconcileResult.value?.lastProcessedMessageId
                    ?: _liveReconcileDraft.value?.lastProcessedMessageId
                    ?: allMsgs.lastOrNull()?.id ?: 0L
                val lastIdx = allMsgs.indexOfFirst { it.id == lastProcessedId }.takeIf { it >= 0 }?.let { it + 1 } ?: allMsgs.size
                val checkpoint = TimelineReconcileCheckpoint(
                    conversationId = conversationId,
                    lastReconciledMessageId = lastProcessedId,
                    lastReconciledMessageIndex = lastIdx,
                    totalMessageCountAtReconciliation = allMsgs.size,
                    storyTimeAtReconciliation = cleanStoryTime,
                    nodeCountAtReconciliation = nodesToSave.size,
                    timestamp = System.currentTimeMillis()
                )
                TimelineDraftManager.saveCheckpoint(AiAssistantApp.instance, checkpoint)
                _timelineCheckpoint.value = checkpoint
            } catch (e: Exception) {
                Log.w("ChatViewModel", "记录时间线水线检查点失败: ${e.message}")
            }

            // 清理已应用的草稿
            TimelineDraftManager.clearDraft(AiAssistantApp.instance, conversationId)
            _liveReconcileDraft.value = null

            dismissTimelineReconcileDialog()
            loadConversation()
        }
    }

    // -------------------------------------------------------------
    // 用户对独立时间线的编辑与管理操作接口（满足需求 1）
    // -------------------------------------------------------------
    fun updateCurrentStoryTime(newTime: String?) {
        viewModelScope.launch {
            repository.updateStoryTime(conversationId, newTime?.trim()?.takeIf { it.isNotBlank() && it != "未确定" })
            loadConversation()
        }
    }

    fun addTimelineNode(timeTag: String, event: String, category: String = TimelineCategory.PLOT_EVENT.key) {
        viewModelScope.launch {
            val currentNodes = timelineNodes.value
            val nextOrder = (currentNodes.maxOfOrNull { it.orderIndex } ?: 0) + 1
            repository.addTimelineNode(
                TimelineNode(
                    conversationId = conversationId,
                    timeTag = timeTag.trim(),
                    event = event.trim(),
                    category = category,
                    orderIndex = nextOrder,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateTimelineNode(node: TimelineNode) {
        viewModelScope.launch {
            repository.updateTimelineNode(node.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteTimelineNode(nodeId: Long) {
        viewModelScope.launch {
            repository.deleteTimelineNodeById(nodeId)
        }
    }

    fun clearTimeline() {
        viewModelScope.launch {
            repository.clearTimeline(conversationId)
            repository.updateStoryTime(conversationId, null)
            TimelineDraftManager.clearDraft(AiAssistantApp.instance, conversationId)
            TimelineDraftManager.clearCheckpoint(AiAssistantApp.instance, conversationId)
            _liveReconcileDraft.value = null
            _timelineCheckpoint.value = null
            loadConversation()
        }
    }

    fun clearTimelineCheckpoint() {
        TimelineDraftManager.clearCheckpoint(AiAssistantApp.instance, conversationId)
        _timelineCheckpoint.value = null
    }

    fun getNewMessagesCountSinceCheckpoint(): Int {
        val checkpoint = _timelineCheckpoint.value ?: return 0
        val allMsgs = _messages.value.filter { !it.isExcluded && it.role != "system" && it.content.isNotBlank() }
        return allMsgs.count { it.id > checkpoint.lastReconciledMessageId }
    }

    fun convertToRoleplay(
        charName: String? = null,
        charIdentity: String? = null,
        charPersonality: String? = null,
        scenarioName: String? = null,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val db = AiAssistantApp.instance.database
            val converter = com.aiassistant.utils.ConversationConverter(
                conversationDao = db.conversationDao(),
                messageDao = db.messageDao(),
                roleplaySessionDao = db.roleplaySessionDao(),
                characterProfileDao = db.characterProfileDao(),
                roleplayScenarioDao = db.roleplayScenarioDao()
            )
            val sessionId = converter.convertToRoleplay(
                conversationId = conversationId,
                charName = charName,
                charIdentity = charIdentity,
                charPersonality = charPersonality,
                scenarioName = scenarioName
            )
            loadConversation()
            onSuccess(sessionId)
        }
    }

    fun convertToNormal(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val db = AiAssistantApp.instance.database
            val converter = com.aiassistant.utils.ConversationConverter(
                conversationDao = db.conversationDao(),
                messageDao = db.messageDao(),
                roleplaySessionDao = db.roleplaySessionDao(),
                characterProfileDao = db.characterProfileDao(),
                roleplayScenarioDao = db.roleplayScenarioDao()
            )
            val success = converter.convertToNormal(conversationId)
            if (success) {
                loadConversation()
                onSuccess()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isPrivateConversation) {
            destroyPrivateConversation()
        } else {
            // generationJob运行在Application级作用域中，退出页面后会继续完成并保存回复。
            autoNameIfNeeded()
        }
    }

    fun leaveConversation(onComplete: () -> Unit) {
        if (isPrivateConversation) {
            destroyPrivateConversation()
        } else {
            autoNameIfNeeded()
        }
        onComplete()
    }

    private fun destroyPrivateConversation() {
        if (privateExitHandled) return
        privateExitHandled = true
        repository.cancelActiveRequest(conversationId)
        generationJob?.cancel(CancellationException("隐私对话退出"))
        AiAssistantApp.instance.applicationScope.launch {
            repository.destroyPrivateConversation(conversationId)
        }
    }

    fun sendPlotAction(action: PlotAction, customInstruction: String? = null) {
        val session = _uiState.value.roleplaySession ?: return
        when (action) {
            PlotAction.REGENERATE -> {
                regenerateLastMessage()
            }
            PlotAction.ROLLBACK -> {
                viewModelScope.launch {
                    val messages = repository.getMessagesList(conversationId)
                    val lastAssistant = messages.lastOrNull { it.role == "assistant" }
                    if (lastAssistant != null) {
                        repository.deleteMessage(lastAssistant)
                    }
                }
            }
            else -> {
                viewModelScope.launch {
                    val instruction = AiAssistantApp.instance.roleplayRepository.processPlotAction(session.id, action, customInstruction)
                    sendMessage(instruction)
                }
            }
        }
    }

    fun triggerCharacterOpening() {
        val session = _uiState.value.roleplaySession ?: return
        val character = _uiState.value.roleplayCharacter
        val scenario = _uiState.value.roleplayScenario
        viewModelScope.launch {
            val greeting = character?.greeting?.trim()
            if (!greeting.isNullOrBlank()) {
                val assistantMsg = Message(
                    conversationId = conversationId,
                    role = "assistant",
                    content = greeting,
                    tokenCount = com.aiassistant.data.repository.AiRepository.estimateTokenCount(greeting),
                    modelName = _currentModel.value?.ifBlank { null } ?: conversation?.modelName
                )
                repository.saveMessage(assistantMsg)
            } else {
                val charName = character?.name ?: "角色"
                val scenarioDesc = scenario?.let { "当前场景为【${it.name}】（${it.location.ifBlank { "" }} ${it.environment.ifBlank { "" }}）。" }.orEmpty()
                val openingPrompt = "【导演开场指令】${scenarioDesc}请以【$charName】的身份，根据角色设定与当前场景世界观，开启故事的第一幕，展现角色当前的动作、神态与首句对话，为故事奠定氛围并留出互动切入点。"
                sendMessage(openingPrompt)
            }
        }
    }

    fun togglePinMessage(message: Message) {
        viewModelScope.launch {
            val updated = message.copy(isPinned = !message.isPinned)
            repository.updateMessage(updated)
        }
    }

    fun toggleExcludeMessage(message: Message) {
        if (ChatGenerationManager.getSession(conversationId)?.directionPhase == true) stopGeneration()
        viewModelScope.launch {
            val updated = message.copy(isExcluded = !message.isExcluded)
            repository.updateMessage(updated)
            refreshContextUsage()
        }
    }

    fun updateNarrativeMode(mode: NarrativeMode) {
        val session = _uiState.value.roleplaySession ?: return
        viewModelScope.launch {
            AiAssistantApp.instance.roleplayRepository.updateSession(session.copy(narrativeMode = mode.value))
            _uiState.update { it.copy(narrativeMode = mode) }
        }
    }

    fun appendAndMergeStoryBundle(
        newCharacters: List<CharacterProfile>,
        newScenario: RoleplayScenario?,
        resolutionMap: Map<String, com.aiassistant.ui.screens.roleplay.ConflictAction>,
        onComplete: (String) -> Unit
    ) {
        val session = _uiState.value.roleplaySession ?: return
        viewModelScope.launch {
            try {
                val roleplayRepo = AiAssistantApp.instance.roleplayRepository
                val existingAllChars = roleplayRepo.getAllCharacters().first()
                val currentIds = session.getEffectiveCharacterIds().toMutableList()

                newCharacters.forEach { incoming ->
                    val existing = existingAllChars.firstOrNull { it.name.trim() == incoming.name.trim() }
                    val action = resolutionMap[incoming.name.trim()] ?: com.aiassistant.ui.screens.roleplay.ConflictAction.MERGE

                    if (existing != null) {
                        when (action) {
                            com.aiassistant.ui.screens.roleplay.ConflictAction.CREATE_COPY -> {
                                val copyChar = incoming.copy(name = "${incoming.name} (副本)")
                                val newId = roleplayRepo.insertCharacter(copyChar)
                                if (newId !in currentIds) currentIds.add(newId)
                            }
                            com.aiassistant.ui.screens.roleplay.ConflictAction.OVERWRITE -> {
                                val updated = incoming.copy(id = existing.id, isFavorite = existing.isFavorite)
                                roleplayRepo.updateCharacter(updated)
                                if (existing.id !in currentIds) currentIds.add(existing.id)
                            }
                            com.aiassistant.ui.screens.roleplay.ConflictAction.MERGE -> {
                                val merged = com.aiassistant.utils.RoleplaySmartAnalyzer.mergeCharacters(existing, incoming)
                                roleplayRepo.updateCharacter(merged)
                                if (existing.id !in currentIds) currentIds.add(existing.id)
                            }
                        }
                    } else {
                        val newId = roleplayRepo.insertCharacter(incoming)
                        if (newId !in currentIds) currentIds.add(newId)
                    }
                }

                var finalScenarioId = session.scenarioId
                if (newScenario != null) {
                    val currentSc = session.scenarioId?.let { roleplayRepo.getScenarioById(it) }
                    if (currentSc != null) {
                        val mergedSc = com.aiassistant.utils.RoleplaySmartAnalyzer.mergeScenarios(currentSc, newScenario)
                        roleplayRepo.updateScenario(mergedSc)
                    } else {
                        finalScenarioId = roleplayRepo.insertScenario(newScenario)
                    }
                }

                val updatedCharIdsJson = com.google.gson.Gson().toJson(currentIds)
                val updatedSession = session.copy(
                    characterId = currentIds.firstOrNull(),
                    characterIds = updatedCharIdsJson,
                    scenarioId = finalScenarioId
                )
                roleplayRepo.updateSession(updatedSession)

                val updatedChars = roleplayRepo.getCharactersByIds(currentIds)
                val updatedSc = finalScenarioId?.let { roleplayRepo.getScenarioById(it) }

                _uiState.update {
                    it.copy(
                        roleplaySession = updatedSession,
                        roleplayCharacter = updatedChars.firstOrNull(),
                        roleplayCharacters = updatedChars,
                        roleplayScenario = updatedSc
                    )
                }

                onComplete("已成功追加/融合 ${newCharacters.size} 位角色" + (if (newScenario != null) "与世界观设定" else ""))
            } catch (e: Exception) {
                onComplete("追加融合失败: ${e.message}")
            }
        }
    }

    fun updateStorySessionContext(
        characterIds: List<Long>,
        scenarioId: Long?,
        narrativeMode: NarrativeMode,
        plotSummary: String
    ) {
        viewModelScope.launch {
            val roleplayRepo = AiAssistantApp.instance.roleplayRepository
            val currentSession = _uiState.value.roleplaySession ?: roleplayRepo.getSessionByConversationId(conversationId) ?: return@launch
            val charIdsJson = com.google.gson.Gson().toJson(characterIds)
            val updated = currentSession.copy(
                characterIds = charIdsJson,
                characterId = characterIds.firstOrNull(),
                scenarioId = scenarioId,
                narrativeMode = narrativeMode.value,
                currentPlotSummary = plotSummary,
                updatedAt = System.currentTimeMillis()
            )
            roleplayRepo.updateSession(updated)

            val rpCharacters = roleplayRepo.getEffectiveCharactersForSession(updated)
            val rpScenario = roleplayRepo.getEffectiveScenarioForSession(updated)
            _uiState.update {
                it.copy(
                    roleplaySession = updated,
                    roleplayCharacter = rpCharacters.firstOrNull(),
                    roleplayCharacters = rpCharacters,
                    roleplayScenario = rpScenario,
                    narrativeMode = narrativeMode
                )
            }
        }
    }

    fun saveLocalCharacterOverride(editedCharacter: CharacterProfile) {
        viewModelScope.launch {
            val roleplayRepo = AiAssistantApp.instance.roleplayRepository
            val currentSession = _uiState.value.roleplaySession ?: roleplayRepo.getSessionByConversationId(conversationId) ?: return@launch

            // 如果是全新创建的角色 (id <= 0)，先同步持久化进全局 Room 数据库以分配真实主键 ID
            val finalCharacter = if (editedCharacter.id <= 0L) {
                val newId = roleplayRepo.insertCharacter(editedCharacter)
                editedCharacter.copy(id = newId)
            } else {
                editedCharacter
            }

            val baseCharacters = _uiState.value.roleplayCharacters
            val currentCustomized = currentSession.getCustomizedCharacters(baseCharacters).toMutableList()
            val existingIndex = currentCustomized.indexOfFirst { (it.id == finalCharacter.id && it.id > 0) || it.name == finalCharacter.name }
            if (existingIndex >= 0) {
                currentCustomized[existingIndex] = finalCharacter
            } else {
                currentCustomized.add(finalCharacter)
            }

            // 确保角色 ID 加入到本故事会话的登场角色列表中
            val sessionCharIds = currentSession.getEffectiveCharacterIds().toMutableList()
            if (!sessionCharIds.contains(finalCharacter.id)) {
                sessionCharIds.add(finalCharacter.id)
            }

            val jsonStr = com.google.gson.Gson().toJson(currentCustomized)
            val updatedSession = currentSession.copy(
                characterIds = com.google.gson.Gson().toJson(sessionCharIds),
                customCharacterData = jsonStr,
                updatedAt = System.currentTimeMillis()
            )
            roleplayRepo.updateSession(updatedSession)
            val effectiveChars = roleplayRepo.getEffectiveCharactersForSession(updatedSession)
            _uiState.update {
                it.copy(
                    roleplaySession = updatedSession,
                    roleplayCharacter = effectiveChars.firstOrNull(),
                    roleplayCharacters = effectiveChars
                )
            }
        }
    }

    fun saveLocalScenarioOverride(editedScenario: RoleplayScenario) {
        viewModelScope.launch {
            val roleplayRepo = AiAssistantApp.instance.roleplayRepository
            val currentSession = _uiState.value.roleplaySession ?: roleplayRepo.getSessionByConversationId(conversationId) ?: return@launch

            // 如果是全新创建的世界观 (id <= 0)，先同步持久化进全局 Room 数据库以分配真实主键 ID
            val finalScenario = if (editedScenario.id <= 0L) {
                val newId = roleplayRepo.insertScenario(editedScenario)
                editedScenario.copy(id = newId)
            } else {
                editedScenario
            }

            val jsonStr = com.google.gson.Gson().toJson(finalScenario)
            val updatedSession = currentSession.copy(
                scenarioId = finalScenario.id,
                customScenarioData = jsonStr,
                updatedAt = System.currentTimeMillis()
            )
            roleplayRepo.updateSession(updatedSession)
            val effectiveScenario = roleplayRepo.getEffectiveScenarioForSession(updatedSession)
            _uiState.update {
                it.copy(
                    roleplaySession = updatedSession,
                    roleplayScenario = effectiveScenario
                )
            }
        }
    }

    fun analyzeAndProposeSettingFromInput(
        text: String,
        onProgress: (String) -> Unit = {},
        onNoProposal: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val currentConfig = apiConfig ?: repository.getDefaultApiConfig() ?: return@launch
                val modelToUse = _currentModel.value ?: currentConfig.modelName
                withContext(Dispatchers.Main) { onProgress("正在结合故事已有设定进行精准分析...") }
                val currentChars = _uiState.value.roleplayCharacters
                val currentScenario = _uiState.value.roleplayScenario
                val proposal = com.aiassistant.utils.RoleplaySmartAnalyzer.analyzeStoryInputForProposal(
                    context = AiAssistantApp.instance,
                    rawText = text,
                    existingCharacters = currentChars,
                    existingScenario = currentScenario,
                    repository = repository,
                    preferredConfig = currentConfig,
                    selectedModel = modelToUse,
                    onProgress = { msg ->
                        viewModelScope.launch(Dispatchers.Main) {
                            onProgress(msg)
                        }
                    }
                )
                if (proposal.hasAnyUpdates) {
                    _uiState.update {
                        it.copy(
                            suggestedProposal = ProposedSettingBundle(
                                updatedCharacters = proposal.updatedCharacters,
                                newCharacters = proposal.newCharacters,
                                scenarioUpdate = proposal.scenarioUpdate,
                                summary = proposal.summaryReport
                            )
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) { onNoProposal() }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "识别失败") }
            }
        }
    }

    fun dismissProposedSetting() {
        _uiState.update { it.copy(suggestedProposal = null) }
    }

    fun applyProposedSetting(characters: List<CharacterProfile>, scenario: RoleplayScenario?) {
        viewModelScope.launch {
            val roleplayRepo = AiAssistantApp.instance.roleplayRepository
            val currentSession = _uiState.value.roleplaySession ?: roleplayRepo.getSessionByConversationId(conversationId) ?: return@launch

            // 1. 如果有新增/更新角色，存入局部角色列表
            var updatedSession = currentSession
            if (characters.isNotEmpty()) {
                val baseChars = _uiState.value.roleplayCharacters
                val currentCustomized = currentSession.getCustomizedCharacters(baseChars).toMutableList()
                characters.forEach { newChar ->
                    val idx = currentCustomized.indexOfFirst { (it.id > 0 && it.id == newChar.id) || it.name == newChar.name }
                    if (idx >= 0) {
                        currentCustomized[idx] = newChar
                    } else {
                        currentCustomized.add(newChar)
                    }
                }
                val jsonStr = com.google.gson.Gson().toJson(currentCustomized)
                updatedSession = updatedSession.copy(
                    customCharacterData = jsonStr,
                    updatedAt = System.currentTimeMillis()
                )
            }

            // 2. 如果有更新世界观，存入局部世界观
            if (scenario != null) {
                val jsonStr = com.google.gson.Gson().toJson(scenario)
                updatedSession = updatedSession.copy(
                    customScenarioData = jsonStr,
                    updatedAt = System.currentTimeMillis()
                )
            }

            roleplayRepo.updateSession(updatedSession)
            val effectiveChars = roleplayRepo.getEffectiveCharactersForSession(updatedSession)
            val effectiveScenario = roleplayRepo.getEffectiveScenarioForSession(updatedSession)
            _uiState.update {
                it.copy(
                    roleplaySession = updatedSession,
                    roleplayCharacter = effectiveChars.firstOrNull(),
                    roleplayCharacters = effectiveChars,
                    roleplayScenario = effectiveScenario,
                    suggestedProposal = null
                )
            }
        }
    }

    fun addNewLocalCharacter(newChar: CharacterProfile) {
        viewModelScope.launch {
            val roleplayRepo = AiAssistantApp.instance.roleplayRepository
            val currentSession = _uiState.value.roleplaySession ?: roleplayRepo.getSessionByConversationId(conversationId) ?: return@launch

            val baseChars = _uiState.value.roleplayCharacters
            val currentCustomized = currentSession.getCustomizedCharacters(baseChars).toMutableList()
            val idx = currentCustomized.indexOfFirst { (it.id > 0 && it.id == newChar.id) || it.name == newChar.name }
            if (idx >= 0) {
                currentCustomized[idx] = newChar
            } else {
                currentCustomized.add(newChar)
            }
            val jsonStr = com.google.gson.Gson().toJson(currentCustomized)
            val updatedSession = currentSession.copy(
                customCharacterData = jsonStr,
                updatedAt = System.currentTimeMillis()
            )
            roleplayRepo.updateSession(updatedSession)
            val effectiveChars = roleplayRepo.getEffectiveCharactersForSession(updatedSession)
            _uiState.update {
                it.copy(
                    roleplaySession = updatedSession,
                    roleplayCharacter = effectiveChars.firstOrNull(),
                    roleplayCharacters = effectiveChars
                )
            }
        }
    }

    fun updateLocalCharacter(updatedChar: CharacterProfile) {
        addNewLocalCharacter(updatedChar)
    }

    fun deleteLocalCharacter(character: CharacterProfile) {
        viewModelScope.launch {
            val roleplayRepo = AiAssistantApp.instance.roleplayRepository
            val currentSession = _uiState.value.roleplaySession ?: roleplayRepo.getSessionByConversationId(conversationId) ?: return@launch

            val charIds = currentSession.getEffectiveCharacterIds().filterNot { it == character.id }
            val charIdsJson = com.google.gson.Gson().toJson(charIds)

            val baseChars = _uiState.value.roleplayCharacters
            val currentCustomized = currentSession.getCustomizedCharacters(baseChars).filterNot {
                (it.id > 0 && it.id == character.id) || it.name == character.name
            }
            val customJson = if (currentCustomized.isNotEmpty()) {
                com.google.gson.Gson().toJson(currentCustomized)
            } else null

            val updatedSession = currentSession.copy(
                characterIds = charIdsJson,
                customCharacterData = customJson,
                updatedAt = System.currentTimeMillis()
            )
            roleplayRepo.updateSession(updatedSession)
            val effectiveChars = roleplayRepo.getEffectiveCharactersForSession(updatedSession)
            _uiState.update {
                it.copy(
                    roleplaySession = updatedSession,
                    roleplayCharacter = effectiveChars.firstOrNull(),
                    roleplayCharacters = effectiveChars
                )
            }
        }
    }

    fun saveLocalScenario(scenario: RoleplayScenario) {
        viewModelScope.launch {
            val roleplayRepo = AiAssistantApp.instance.roleplayRepository
            val currentSession = _uiState.value.roleplaySession ?: roleplayRepo.getSessionByConversationId(conversationId) ?: return@launch

            val jsonStr = com.google.gson.Gson().toJson(scenario)
            val updatedSession = currentSession.copy(
                scenarioId = if (scenario.id > 0) scenario.id else currentSession.scenarioId,
                customScenarioData = jsonStr,
                updatedAt = System.currentTimeMillis()
            )
            roleplayRepo.updateSession(updatedSession)
            val effectiveScenario = roleplayRepo.getEffectiveScenarioForSession(updatedSession)
            _uiState.update {
                it.copy(
                    roleplaySession = updatedSession,
                    roleplayScenario = effectiveScenario
                )
            }
        }
    }

    fun deleteLocalScenario() {
        viewModelScope.launch {
            val roleplayRepo = AiAssistantApp.instance.roleplayRepository
            val currentSession = _uiState.value.roleplaySession ?: roleplayRepo.getSessionByConversationId(conversationId) ?: return@launch

            val updatedSession = currentSession.copy(
                scenarioId = null,
                customScenarioData = null,
                updatedAt = System.currentTimeMillis()
            )
            roleplayRepo.updateSession(updatedSession)
            _uiState.update {
                it.copy(
                    roleplaySession = updatedSession,
                    roleplayScenario = null
                )
            }
        }
    }

    fun syncCharacterToDatabase(character: CharacterProfile, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val roleplayRepo = AiAssistantApp.instance.roleplayRepository
                val savedChar = roleplayRepo.syncCharacterToDatabase(character)
                withContext(Dispatchers.Main) {
                    onDone(true, "已将【${savedChar.name}】同步保存至角色库")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onDone(false, "同步失败: ${e.message}")
                }
            }
        }
    }

    fun syncScenarioToDatabase(scenario: RoleplayScenario, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val roleplayRepo = AiAssistantApp.instance.roleplayRepository
                val savedSc = roleplayRepo.syncScenarioToDatabase(scenario)
                withContext(Dispatchers.Main) {
                    onDone(true, "已将【${savedSc.name}】同步保存至世界观库")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onDone(false, "同步失败: ${e.message}")
                }
            }
        }
    }

    fun syncCharacterFromDatabase(characterId: Long, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val roleplayRepo = AiAssistantApp.instance.roleplayRepository
                val currentSession = _uiState.value.roleplaySession ?: roleplayRepo.getSessionByConversationId(conversationId) ?: return@launch
                val updatedSession = roleplayRepo.syncCharacterFromDatabase(currentSession, characterId)
                val effectiveChars = roleplayRepo.getEffectiveCharactersForSession(updatedSession)
                _uiState.update {
                    it.copy(
                        roleplaySession = updatedSession,
                        roleplayCharacter = effectiveChars.firstOrNull(),
                        roleplayCharacters = effectiveChars
                    )
                }
                withContext(Dispatchers.Main) {
                    onDone(true, "已从数据库重新加载角色最新设定")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onDone(false, "同步失败: ${e.message}")
                }
            }
        }
    }

    fun syncScenarioFromDatabase(scenarioId: Long, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val roleplayRepo = AiAssistantApp.instance.roleplayRepository
                val currentSession = _uiState.value.roleplaySession ?: roleplayRepo.getSessionByConversationId(conversationId) ?: return@launch
                val effectiveSc = roleplayRepo.syncScenarioFromDatabase(currentSession, scenarioId)
                _uiState.update {
                    it.copy(
                        roleplaySession = currentSession.copy(scenarioId = scenarioId, customScenarioData = null),
                        roleplayScenario = effectiveSc
                    )
                }
                withContext(Dispatchers.Main) {
                    onDone(true, "已从数据库重新加载世界观最新设定")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onDone(false, "同步失败: ${e.message}")
                }
            }
        }
    }

    fun summarizeAndExtractMemories(onSuccess: (String) -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val roleplaySession = _uiState.value.roleplaySession ?: AiAssistantApp.instance.roleplayRepository.getSessionByConversationId(conversationId)
                if (roleplaySession == null) {
                    withContext(Dispatchers.Main) { onError("当前不是故事会话") }
                    return@launch
                }
                val allMessages = repository.getMessagesList(conversationId)
                if (allMessages.isEmpty()) {
                    withContext(Dispatchers.Main) { onError("暂无剧情记录可提炼") }
                    return@launch
                }
                val transcript = allMessages.takeLast(30).joinToString("\n") { m ->
                    val role = if (m.role == "user") "【导演/用户】" else "【模型剧情】"
                    "$role: ${m.content}"
                }
                val prompt = """
                    请分析以下故事剧本对白与情节发展：
                    $transcript

                    请完成两项任务：
                    1. 生成一段简明扼要、连贯的中文【当前剧情摘要】（不超过 200 字）；
                    2. 提取 2~4 条不可违背的【关键事实或既定设定】（每条一句话）。

                    请严格输出合法 JSON，格式如下：
                    {
                      "plotSummary": "剧情摘要文本...",
                      "extractedFacts": [
                        "事实1...",
                        "事实2..."
                      ]
                    }
                """.trimIndent()

                val config = apiConfig ?: repository.getDefaultApiConfig()
                if (config == null) {
                    withContext(Dispatchers.Main) { onError("未找到可用的 API 配置") }
                    return@launch
                }
                val responseText = withContext(Dispatchers.IO) {
                    repository.executeQuickCompletion(config, prompt, maxTokens = 1024).orEmpty()
                }
                val jsonStart = responseText.indexOf('{')
                val jsonEnd = responseText.lastIndexOf('}')
                val jsonStr = if (jsonStart >= 0 && jsonEnd > jsonStart) responseText.substring(jsonStart, jsonEnd + 1) else null

                if (jsonStr != null) {
                    val parsed = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                    val newSummary = parsed.get("plotSummary")?.asString.orEmpty()
                    val factsArray = parsed.get("extractedFacts")?.asJsonArray

                    if (newSummary.isNotBlank()) {
                        AiAssistantApp.instance.roleplayRepository.savePlotSummary(roleplaySession.id, newSummary)
                    }
                    factsArray?.forEach { el ->
                        val factStr = el.asString.trim()
                        if (factStr.isNotBlank()) {
                            AiAssistantApp.instance.roleplayRepository.addPinnedFact(roleplaySession.id, factStr)
                        }
                    }
                    val updatedSession = AiAssistantApp.instance.roleplayRepository.getSessionById(roleplaySession.id)
                    _uiState.update { it.copy(roleplaySession = updatedSession) }
                    withContext(Dispatchers.Main) {
                        onSuccess("已成功提炼剧情摘要与 ${factsArray?.size() ?: 0} 条关键事实！")
                    }
                } else {
                    AiAssistantApp.instance.roleplayRepository.savePlotSummary(roleplaySession.id, responseText.take(200))
                    val updatedSession = AiAssistantApp.instance.roleplayRepository.getSessionById(roleplaySession.id)
                    _uiState.update { it.copy(roleplaySession = updatedSession) }
                    withContext(Dispatchers.Main) {
                        onSuccess("已保存剧情摘要")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "提炼失败")
                }
            }
        }
    }

    private val hasBackfilledHistoricalModelNames = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun updateMessageModelMap(messageList: List<Message>) {
        val assistantMsgs = messageList.filter { it.role == "assistant" }
        if (assistantMsgs.isEmpty()) return

        val currentMap = _messageModelMap.value
        // Fast-path 快速检查：如果所有 assistant 消息都已经在 currentMap 中，或者都有 modelName，无需全量查库
        val needResolution = assistantMsgs.filter { msg ->
            msg.modelName.isNullOrBlank() && !currentMap.containsKey(msg.id) && !currentMap.containsKey(msg.createdAt)
        }

        if (needResolution.isEmpty()) {
            // 没有需要反查的旧消息，尝试一次性静默后台回填（受 AtomicBoolean 保护，全局仅执行一次，绝不在 Flow 监听中无限循环）
            triggerHistoricalModelNameBackfillOnce(assistantMsgs)
            return
        }

        viewModelScope.launch {
            val stats = repository.getUsageStatsListByTimeRange(0, System.currentTimeMillis())
            val defaultModel = conversation?.modelName.orEmpty()
            val newMap = _messageModelMap.value.toMutableMap()
            var changed = false

            assistantMsgs.forEach { msg ->
                val model = msg.modelName?.ifBlank { null }
                    ?: newMap[msg.id]
                    ?: newMap[msg.createdAt]
                    ?: stats.filter {
                        Math.abs(it.timestamp - msg.createdAt) < 15000L ||
                        (it.responseTime > 0 && it.responseTime == msg.responseTime)
                    }.minByOrNull { Math.abs(it.timestamp - msg.createdAt) }?.modelName?.ifBlank { null }
                    ?: runtimeMessageModelMap[msg.createdAt]
                    ?: defaultModel.ifBlank { null }

                if (!model.isNullOrBlank()) {
                    if (newMap[msg.id] != model || newMap[msg.createdAt] != model) {
                        newMap[msg.id] = model
                        newMap[msg.createdAt] = model
                        changed = true
                    }
                }
            }
            if (changed) {
                _messageModelMap.value = newMap
            }

            // 内存映射建立完毕后，尝试触发一次性静默后台持久化回填
            triggerHistoricalModelNameBackfillOnce(assistantMsgs)
        }
    }

    private fun triggerHistoricalModelNameBackfillOnce(assistantMsgs: List<Message>) {
        // 使用原子布尔确保每个 ChatViewModel 生命周期内至多执行一次回填，彻底杜绝 Flow 级联死循环
        if (!hasBackfilledHistoricalModelNames.compareAndSet(false, true)) return

        val msgsToBackfill = assistantMsgs.filter { it.modelName.isNullOrBlank() && it.id > 0 }
        if (msgsToBackfill.isEmpty()) return

        // 在独立后台协程中轻量静默写入，不阻塞主线程与 Flow 收集器
        viewModelScope.launch(Dispatchers.IO) {
            val currentMap = _messageModelMap.value
            val defaultModel = conversation?.modelName.orEmpty()
            msgsToBackfill.forEach { msg ->
                val model = currentMap[msg.id]
                    ?: currentMap[msg.createdAt]
                    ?: runtimeMessageModelMap[msg.createdAt]
                    ?: defaultModel.ifBlank { null }
                if (!model.isNullOrBlank()) {
                    try {
                        repository.updateMessageModelName(msg.id, model)
                    } catch (e: Exception) {
                        // 忽略偶发的并发写入异常，不影响会话使用
                    }
                }
            }
        }
    }

    init {
        // Keep after every field: Main.immediate collectors may execute during construction.
        loadConversation()
        loadPromptTemplates()
        observeUsageStatsForModels()
        attachToActiveGenerationSession()
        viewModelScope.launch(Dispatchers.IO) {
            _liveReconcileDraft.value = TimelineDraftManager.getDraft(AiAssistantApp.instance, conversationId)
            _timelineCheckpoint.value = TimelineDraftManager.getCheckpoint(AiAssistantApp.instance, conversationId)
        }
    }

    companion object {
        private val conversationDrafts = java.util.concurrent.ConcurrentHashMap<Long, String>()
        fun getDraft(conversationId: Long): String = conversationDrafts[conversationId].orEmpty()
        fun saveDraft(conversationId: Long, text: String) {
            if (text.isBlank()) conversationDrafts.remove(conversationId)
            else conversationDrafts[conversationId] = text
        }

        fun factory(conversationId: Long): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(conversationId) as T
                }
            }
        }
    }
}

data class ProposedSettingBundle(
    val updatedCharacters: List<com.aiassistant.utils.ProposedCharacterUpdate> = emptyList(),
    val newCharacters: List<com.aiassistant.utils.ProposedCharacterUpdate> = emptyList(),
    val scenarioUpdate: com.aiassistant.utils.ProposedScenarioUpdate? = null,
    val summary: String = ""
) {
    val allCharacters: List<CharacterProfile>
        get() = updatedCharacters.map { it.character } + newCharacters.map { it.character }

    val scenario: RoleplayScenario?
        get() = scenarioUpdate?.scenario
}

data class ChatUiState(
    val conversationTitle: String = "新对话",
    val modelName: String = "",
    val systemPrompt: String? = null,
    val enableThinking: Boolean = true,
    val isLoading: Boolean = false,
    val modelAvatarUri: String? = null,
    val isRoleplay: Boolean = false,
    val roleplaySession: RoleplaySession? = null,
    val roleplayCharacter: CharacterProfile? = null,
    val roleplayCharacters: List<CharacterProfile> = emptyList(),
    val roleplayScenario: RoleplayScenario? = null,
    val narrativeMode: NarrativeMode = NarrativeMode.CHARACTER,
    val suggestedProposal: ProposedSettingBundle? = null,
    val currentStoryTime: String? = null
)

data class ContextUsageUiState(
    val usage: ConversationContextUsage? = null,
    val isCompressing: Boolean = false,
    val isGeneratingSummary: Boolean = false,
    val statusMessage: String? = null,
    val pendingAutoTier: com.aiassistant.domain.model.CompressionTier? = null
)

// 临时聊天设置（仅当前对话有效）
data class TempChatSettings(
    val temperature: Float = 0.95f,
    val maxTokens: Int = 4096,
    val topP: Float = 1.0f,
    val enableThinking: Boolean = true,
    val thinkingEffort: String = "high",
    val enableWebSearch: Boolean = false,
    val enableSessionMemory: Boolean = false,
    val enableExternalMemory: Boolean = false,
    val enableWorldBook: Boolean = false,
    val enableReplyDirections: Boolean = false,
    val replyDirectionCount: Int = 2,
    val activeWorldBookIds: String? = null,
    val contextWindowTokens: Int? = null
)

/**
 * 生成锚点（v2.6.5）：流式回复气泡的稳定挂载参照。
 * [userMessageId] 为触发本轮生成的用户消息 id；[userGroupId] 为该消息所属的 user 分组
 * （编辑重发场景，如 turn_x_user），供气泡在分组选中项变化时仍能命中挂载位置。
 */
data class GeneratingAnchor(
    val userMessageId: Long,
    val userGroupId: String? = null
)
