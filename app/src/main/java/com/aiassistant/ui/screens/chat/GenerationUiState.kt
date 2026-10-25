package com.aiassistant.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aiassistant.domain.model.Message
import com.aiassistant.ui.components.EchoModelMarkState

/**
 * 生成状态机总线（方案 P0-4）
 * 把散落在 isConnecting/isThinkingActive/isGenerating/reconnectStatus 的隐式状态推导收敛为单一枚举，
 * 所有动效（脉冲环/文案交叉/光标/落定）由状态机驱动而非字符串匹配。
 *
 * 行为等价迁移约束：错误文案匹配规则（contains 异常/报错/失败/错误/Error/HTTP）
 * 与 isErrorMessage() 内容判定原样保留，判定结果不变；
 * 后续由数据层输出结构化错误码后替换（属另一任务）。
 */
enum class GenerationUiState {
    Idle,        // 无生成进行
    Connecting,  // 已发出请求，等待响应
    Reconnecting,// 断线重连中（reconnectStatus 非空且非错误）
    ChoosingDirection, // 等待用户选择，非网络连接阶段
    Thinking,    // 思考块流式输出中，正文未开始
    Streaming,   // 正文流式输出中
    Settling,    // 落定过渡（UI 一次性动画态，由落定动画本地驱动，派生函数不产出）
    Failed,      // 错误（重连文案命中错误词，或消息内容为错误报告）
    Cancelled    // 手动停止，保留已生成正文与思考
}

object GenerationUiStateRules {

    fun stoppedReplyContent(content: String, pendingErrors: List<String> = emptyList()): String {
        val body = content.trim().let { if (it.isEmpty()) "生成已停止" else "$it\n\n*(生成已停止)*" }
        val errors = pendingErrors.map(String::trim).filter(String::isNotBlank).distinct()
        return if (errors.isEmpty()) body else body + "\n\n【连接异常信息记录】\n" +
            errors.joinToString("\n") { "• $it" }
    }

    /** 停止状态与错误报告独立展示，连接详情仍可查看和复制。 */
    fun stoppedReplyErrorReport(content: String): String? = if (isStoppedContent(content)) {
        content.substringAfter("\n\n【连接异常信息记录】", "").trim().removePrefix("：").trim().ifBlank { null }
    } else null

    /** 仅识别生成器写入的独立停止正文或尾标，兼容旧版保存格式。 */
    fun isStoppedContent(content: String): Boolean {
        val body = content.trim()
        if (body == "生成已停止" || body.startsWith("生成已停止\n\n【连接异常信息记录】") || body == "回复已停止 (用户已暂停)" ||
            body.startsWith("回复已停止 (用户已暂停)\n\n")) return true
        return body.substringBefore("\n\n【连接异常信息记录】").trimEnd().substringAfterLast("\n\n") in setOf(
            "*(生成已停止)*", "*(回复已被暂停)*", "*(思考已停止，回复已暂停)*")
    }

    /** 胶囊统一展示停止状态；正文仅保留真实部分回复，兼容旧版追加的连接记录。 */
    fun stoppedReplyDisplayBody(content: String): String {
        if (!isStoppedContent(content)) return content
        val body = content.trim()
        if (body == "生成已停止" || body.startsWith("生成已停止\n\n【连接异常信息记录】") || body == "回复已停止 (用户已暂停)" ||
            body.startsWith("回复已停止 (用户已暂停)\n\n")) return ""
        val reply = body.substringBefore("\n\n【连接异常信息记录】").trimEnd()
        return reply.substringBeforeLast("\n\n", missingDelimiterValue = "").trimEnd()
    }

    /** 只识别生成器写入的独立终止尾标，不因正文提到“暂停”就误判。 */
    fun isInterruptedContent(content: String): Boolean {
        val body = content.trim()
        if (!body.endsWith(")*")) return false
        val lastBlock = body.substringAfterLast("\n\n")
        return lastBlock.startsWith("*(输出已被中断: ") ||
            lastBlock.startsWith("*(思考已输出，回复已被中断: ")
    }

    fun modelMarkHasSuccessfulBody(content: String): Boolean {
        val body = content.trim()
        if (body.isEmpty() || isStoppedContent(body) || isErrorMessage(body)) return false
        return !isInterruptedContent(body)
    }

    fun modelMarkState(state: GenerationUiState, completed: Boolean): EchoModelMarkState = when (state) {
        GenerationUiState.Connecting, GenerationUiState.Reconnecting -> EchoModelMarkState.Connecting
        GenerationUiState.Thinking -> EchoModelMarkState.Thinking
        GenerationUiState.Streaming -> EchoModelMarkState.Replying
        GenerationUiState.Cancelled -> EchoModelMarkState.Paused
        GenerationUiState.Failed -> EchoModelMarkState.Failed
        GenerationUiState.Idle, GenerationUiState.Settling ->
            if (completed) EchoModelMarkState.Completed else EchoModelMarkState.Neutral
        else -> EchoModelMarkState.Neutral
    }

    /** Only the latest successful assistant may idle; historical, failed and decision rows never run a ticker. */
    fun modelMarkAllowsIdle(state: GenerationUiState, isLatestAssistant: Boolean, content: String): Boolean =
        isLatestAssistant && (state == GenerationUiState.Idle || state == GenerationUiState.Settling) &&
            modelMarkHasSuccessfulBody(content)

    fun modelMarkCompletionObserved(
        previous: GenerationUiState?,
        current: GenerationUiState,
        content: String,
        completionSignal: Boolean = false
    ): Boolean = (current == GenerationUiState.Idle || current == GenerationUiState.Settling) &&
        (completionSignal || previous == GenerationUiState.Thinking || previous == GenerationUiState.Streaming) &&
        modelMarkHasSuccessfulBody(content)

    /** 模型标识只在正在生成时运动；等待用户决策、失败和结束均保持静态。 */
    fun animateModelMark(state: GenerationUiState): Boolean = when (state) {
        GenerationUiState.Connecting, GenerationUiState.Reconnecting,
        GenerationUiState.Thinking, GenerationUiState.Streaming -> true
        else -> false
    }

    /** 隐藏思考气泡后保留必要操作状态；正文开始后不再显示旧重试提示。 */
    fun statusWithoutThinking(state: GenerationUiState, reconnectStatus: String?, contentEmpty: Boolean): String? = when (state) {
        GenerationUiState.Connecting -> "正在连接…"
        GenerationUiState.Reconnecting -> reconnectStatus?.takeIf { it.isNotBlank() } ?: "正在重新连接…"
        GenerationUiState.ChoosingDirection -> reconnectStatus?.takeIf { it.isNotBlank() } ?: "等待选择回复方向"
        GenerationUiState.Thinking -> "正在生成回复…"
        GenerationUiState.Failed -> if (contentEmpty) reconnectStatus?.takeIf { it.isNotBlank() } ?: "模型请求异常" else "模型请求异常"
        GenerationUiState.Cancelled -> "生成已停止"
        GenerationUiState.Idle -> if (contentEmpty) "未返回回复正文" else null
        GenerationUiState.Streaming, GenerationUiState.Settling -> null
    }

    /**
     * 错误文案匹配——行为等价迁移自 ChatMessageComponents 状态胶囊的 contains 规则
     * （原作用于 reconnectStatus 非空时的 capsuleText，capsuleText 此时即 reconnectStatus）
     */
    fun isErrorText(text: String): Boolean =
        text.contains("异常") ||
            text.contains("报错") ||
            text.contains("失败") ||
            text.contains("错误") ||
            text.contains("Error", ignoreCase = true) ||
            text.contains("HTTP", ignoreCase = true)

    /**
     * 派生生成状态。入参均为消息气泡已有的原始信号，O(1) 纯函数。
     *
     * @param isGenerating 生成进行中
     * @param content 正文内容（流式中为已产出文本）
     * @param hasThinking 存在思考内容或思考 token
     * @param reconnectStatus 重连状态文本（null=无重连）
     * @param contentIsError 消息内容命中 isErrorMessage()（错误报告包裹逻辑，v2.5.7 红框全包裹）
     */
    fun derive(
        isGenerating: Boolean,
        content: String,
        hasThinking: Boolean,
        reconnectStatus: String?,
        contentIsError: Boolean = false
    ): GenerationUiState = when {
        !isGenerating && isStoppedContent(content) -> GenerationUiState.Cancelled
        !isGenerating && isInterruptedContent(content) -> GenerationUiState.Failed
        contentIsError -> GenerationUiState.Failed
        isGenerating && reconnectStatus in listOf("等待选择回复方向", "回复方向生成失败，等待处理") -> GenerationUiState.ChoosingDirection
        isGenerating && content.isBlank() && !hasThinking -> {
            if (reconnectStatus != null) {
                if (reconnectStatus.contains("正在尝试重新连接") || reconnectStatus.contains("正在自动尝试备用 Key")) {
                    GenerationUiState.Reconnecting
                } else if (isErrorText(reconnectStatus)) GenerationUiState.Failed else GenerationUiState.Reconnecting
            } else {
                GenerationUiState.Connecting
            }
        }
        isGenerating && hasThinking && content.isBlank() -> GenerationUiState.Thinking
        isGenerating -> GenerationUiState.Streaming
        else -> GenerationUiState.Idle
    }
}

/**
 * 生成结束只能落定本轮新增或真正更新的助手消息。重生成可能更新原 id，故比较正文；
 * 取消且没有新消息时不得拿上一轮回复补一个“完成”动画。
 */
internal fun settledModelMarkMessageId(
    before: Map<Long, String>,
    after: List<Message>,
    activeVariantGroupId: String?
): Long = after.lastOrNull { message ->
    message.role == "assistant" && message.id > 0L &&
        (activeVariantGroupId == null || message.variantGroupId == activeVariantGroupId) &&
        (!before.containsKey(message.id) || before[message.id] != message.content)
}?.takeIf { GenerationUiStateRules.modelMarkHasSuccessfulBody(it.content) }?.id ?: 0L

/**
 * 组合期派生：从原始信号得到 GenerationUiState。
 * 纯重组期计算，无副作用；P0-1/P0-2/P0-3 的动效均消费该枚举而非裸布尔/字符串。
 */
@Composable
fun rememberGenerationUiState(
    isGenerating: Boolean,
    content: String,
    hasThinking: Boolean,
    reconnectStatus: String?,
    contentIsError: Boolean
): GenerationUiState {
    return remember(isGenerating, content, hasThinking, reconnectStatus, contentIsError) {
        GenerationUiStateRules.derive(isGenerating, content, hasThinking, reconnectStatus, contentIsError)
    }
}
