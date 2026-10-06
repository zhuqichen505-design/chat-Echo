package com.aiassistant.data.repository

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 全局会话生成状态管理器（解决返回首页/其他界面时思考气泡与回复中断或短暂消失的问题）
 * 1. 在 Application 生命周期内持有正在进行的生成流与思考流状态；
 * 2. 无论用户在聊天页、首页、设置页或角色扮演工作室之间如何切换导航，生成任务与流式数据都不会中断；
 * 3. 当用户重新进入聊天界面时，能够无缝挂载现存的生成状态与累计的思考/正文内容，绝不闪烁或空白；
 * 4. 内置原子锁机制，彻底杜绝多次保存或重复报错输出。
 */
object ChatGenerationManager {

    class ActiveSession(
        val conversationId: Long,
        initialModelName: String = "",
        val requestStartTime: Long = System.currentTimeMillis()
    ) {
        val _isGenerating = MutableStateFlow(true)
        val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

        val _currentResponse = MutableStateFlow("")
        val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

        val _currentThinking = MutableStateFlow("")
        val currentThinking: StateFlow<String> = _currentThinking.asStateFlow()

        val _reconnectStatus = MutableStateFlow<String?>(null)
        val reconnectStatus: StateFlow<String?> = _reconnectStatus.asStateFlow()

        val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        val _isConnecting = MutableStateFlow(true)
        val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

        val _callingModel = MutableStateFlow(initialModelName)
        val callingModel: StateFlow<String> = _callingModel.asStateFlow()

        val isMessageSaved = AtomicBoolean(false)
        var generationJob: Job? = null
        var assistantVariantGroupId: String? = null
        var assistantVariantIndex: Int = 1
        var userMessageId: Long? = null
        var directionPhase: Boolean = false
        var selectedReplyDirection: String? = null
        val pendingReplyDirection = MutableStateFlow<com.aiassistant.domain.model.ReplyDirectionPrompt?>(null)

        @Synchronized
        fun answerReplyDirection(decision: com.aiassistant.domain.model.ReplyDirectionDecision) {
            val prompt = pendingReplyDirection.value ?: return
            pendingReplyDirection.value = null
            prompt.onDecision(decision)
        }

        /**
         * v2.6.8 需求 4：上下文回退待确认提示提升到 Application 级会话。
         * 生成过程中用户返回首页/切换其他对话时 ViewModel 会被销毁，此前提示（连同续体）只挂在
         * ViewModel 上，随之一起消失 → 等待应答的请求协程永久挂起，连接与回复卡死。
         * 现在提示挂在会话上：重进会话的新 ViewModel 转发该流继续显示弹窗，用户仍可应答；
         * 配合调用方的超时兜底，任何情况下都不再存在无人应答的永久挂起。
         */
        val _pendingContextFallbackPrompt = MutableStateFlow<com.aiassistant.domain.model.ContextFallbackPromptState?>(null)
        val pendingContextFallbackPrompt: StateFlow<com.aiassistant.domain.model.ContextFallbackPromptState?> =
            _pendingContextFallbackPrompt.asStateFlow()

        /**
         * v2.6.8 需求 4：实时 Key 尝试报错明细同样挂在会话上。
         * 重进会话后明细不再丢失，用户暂停生成时也能把完整 Key 报错记录写进回复正文（避免数据损失）。
         */
        val _keyAttemptErrors = MutableStateFlow<List<String>>(emptyList())
        val keyAttemptErrors: StateFlow<List<String>> = _keyAttemptErrors.asStateFlow()

        /** 应答上下文回退待确认提示：清空会话级状态并唤醒等待中的请求协程 */
        fun answerContextFallbackPrompt(choice: com.aiassistant.domain.model.ContextFallbackChoice) {
            val prompt = _pendingContextFallbackPrompt.value ?: return
            _pendingContextFallbackPrompt.value = null
            prompt.onDecision(choice)
        }

        /**
         * 生成锚点（v2.6.5）：触发本轮生成的用户消息 id 及其 user 分组 id。
         * 流式回复气泡钉在该用户消息之后挂载，位置不随同一位置其他回复（如错误占位、
         * 旧 variant）的删除而移动；重进会话时可由此恢复挂载点。
         */
        var anchorUserMessageId: Long? = null
        var anchorUserGroupId: String? = null

        fun appendResponse(token: String) {
            _isConnecting.value = false
            _currentResponse.update { it + token }
        }

        fun appendThinking(token: String) {
            _isConnecting.value = false
            _currentThinking.update { it + token }
        }

        fun resetBuffer() {
            _currentResponse.value = ""
            _currentThinking.value = ""
        }

        fun setStatus(status: String?) {
            _reconnectStatus.value = status
        }

        fun setError(err: String?) {
            _error.value = err
            _isGenerating.value = false
            _isConnecting.value = false
        }

        // A failed attempt is visible, but is NOT the end of the generation round.
        fun setAttemptError(err: String?) {
            _error.value = err
        }

        fun markFinished() {
            _isGenerating.value = false
            _isConnecting.value = false
            _reconnectStatus.value = null
            // v2.6.8 需求 4：本轮结束时一并清掉会话级待确认提示，避免残留到下一次生成
            _pendingContextFallbackPrompt.value = null
            pendingReplyDirection.value = null
        }
    }

    private val sessions = ConcurrentHashMap<Long, ActiveSession>()
    private val mutationEpochs = ConcurrentHashMap<Long, Long>()
    private val timelineProposals = ConcurrentHashMap<Long, MutableStateFlow<List<AutoTimelineUpdateResult>>>()
    private val timelineExtractions = ConcurrentHashMap<Long, MutableStateFlow<Int>>()

    fun timelineExtractionCount(conversationId: Long): StateFlow<Int> = extractionState(conversationId).asStateFlow()
    private fun extractionState(conversationId: Long): MutableStateFlow<Int> =
        timelineExtractions.computeIfAbsent(conversationId) { MutableStateFlow(0) }
    fun beginTimelineExtraction(conversationId: Long) { extractionState(conversationId).update { it + 1 } }
    fun endTimelineExtraction(conversationId: Long) { extractionState(conversationId).update { (it - 1).coerceAtLeast(0) } }

    fun timelineProposals(conversationId: Long): StateFlow<List<AutoTimelineUpdateResult>> =
        proposalState(conversationId).asStateFlow()

    private fun proposalState(conversationId: Long): MutableStateFlow<List<AutoTimelineUpdateResult>> =
        timelineProposals.computeIfAbsent(conversationId) { MutableStateFlow(emptyList()) }

    fun offerTimelineProposal(conversationId: Long, proposal: AutoTimelineUpdateResult) {
        proposalState(conversationId).update { it + proposal }
    }

    fun dismissTimelineProposal(conversationId: Long, proposal: AutoTimelineUpdateResult?) {
        proposalState(conversationId).update { pending -> pending.filterNot { it === proposal } }
    }
    fun mutationEpoch(conversationId: Long): Long = mutationEpochs[conversationId] ?: 0L

    fun getSession(conversationId: Long): ActiveSession? {
        return sessions[conversationId]
    }

    fun isGenerating(conversationId: Long): Boolean {
        return sessions[conversationId]?.isGenerating?.value == true
    }

    fun startSession(
        conversationId: Long,
        modelName: String,
        variantGroupId: String? = null,
        variantIndex: Int = 1
    ): ActiveSession {
        // 如果先前已有正在运行的旧 Session，先取消其 Job，防止两个生成任务并存冲突
        sessions[conversationId]?.generationJob?.cancel()

        val newSession = ActiveSession(
            conversationId = conversationId,
            initialModelName = modelName
        ).apply {
            assistantVariantGroupId = variantGroupId
            assistantVariantIndex = variantIndex
        }
        sessions[conversationId] = newSession
        return newSession
    }

    fun removeSession(conversationId: Long) {
        sessions.remove(conversationId)
    }

    fun cancelSession(conversationId: Long) {
        mutationEpochs.merge(conversationId, 1L) { old, increment -> old + increment }
        timelineProposals[conversationId]?.value = emptyList()
        sessions.remove(conversationId)?.let { session ->
            session.isMessageSaved.set(true)
            session.markFinished()
            session.generationJob?.cancel()
        }
    }

    /**
     * v2.6.8 需求 4：当前会话是否仍是 [session]。
     * 生成收尾（完成/报错/取消）时用它判定"这一轮是否还是活跃轮次"——
     * 用户停止后立刻重发、或同一会话存在两个页面实例时，上一轮的收尾不得回写 ViewModel 状态
     * （否则会把新一轮的生成标志、生成锚点、流式缓冲清空，表现为"新回复被打断/消失"）。
     */
    fun isCurrentSession(conversationId: Long, session: ActiveSession): Boolean =
        sessions[conversationId] === session

    /**
     * v2.6.8 需求 4：仅当 [session] 仍是当前会话时才移除（ConcurrentHashMap 的 CAS 语义），
     * 避免过期生成的收尾把后来者（新一轮生成）从表中误删，导致重进会话读不到进行中的生成。
     */
    fun removeSession(conversationId: Long, session: ActiveSession) {
        sessions.remove(conversationId, session)
    }

    /**
     * 原子标记消息已持久化保存，保证报错回复或正常回复在任何情况下仅保存一次
     */
    fun tryMarkMessageSaved(conversationId: Long): Boolean {
        val session = sessions[conversationId] ?: return false
        return session.isMessageSaved.compareAndSet(false, true)
    }
}
