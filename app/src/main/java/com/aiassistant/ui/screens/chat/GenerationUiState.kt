package com.aiassistant.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

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
    Cancelled    // 手动取消（需数据层结构化信号，当前派生不可达，占位）
}

object GenerationUiStateRules {

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
