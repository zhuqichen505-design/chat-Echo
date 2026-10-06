package com.aiassistant.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

// 文件夹/分类
@Entity(
    tableName = "folders",
    indices = [Index(value = ["parentId"])]
)
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String = "folder",
    val color: Int = 0,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// API配置
@Entity(tableName = "api_configs")
data class ApiConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val apiKey: String,
    val apiType: String = "openai",
    val modelName: String,
    val availableModels: String? = null,
    val maxTokens: Int = 4096,
    val temperature: Float = 0.95f,
    val topP: Float = 1.0f,
    val topK: Int = 50,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val enableThinking: Boolean = true,
    val thinkingBudget: Int = 1024,
    val thinkingEffort: String = "medium",
    val enableWebSearch: Boolean = false,
    val searchContextSize: String = "medium",
    val stopSequences: String? = null,
    val seed: Int? = null,
    val responseFormat: String? = null,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 具名/备注 API Key 模型（用于多Key区分与命名管理）
data class NamedApiKey(
    val name: String = "",
    val key: String = "",
    val isEnabled: Boolean = true
)

// 对话
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["folderId"])]
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val folderId: Long? = null,
    val apiConfigId: Long,
    val modelName: String,
    val systemPrompt: String? = null,
    val rollingSummary: String? = null,
    val summaryUpdatedMessageId: Long? = null,
    val summaryUpdatedAt: Long? = null,
    val totalTokens: Int = 0,
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
    val tags: String? = null,
    // 对话级别的配置
    val temperature: Float? = null,      // null表示使用API配置默认值
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val enableThinking: Boolean? = null,
    val thinkingEffort: String? = null,
    val enableWebSearch: Boolean? = null,
    val enableSessionMemory: Boolean? = null,
    val enableExternalMemory: Boolean? = null,
    val enableWorldBook: Boolean? = null,
    @ColumnInfo(defaultValue = "0") val enableReplyDirections: Boolean = false,
    @ColumnInfo(defaultValue = "2") val replyDirectionCount: Int = 2,
    val activeWorldBookIds: String? = null,
    val modelAvatarUri: String? = null,
    val currentStoryTime: String? = null,
    val contextWindowTokens: Int? = null, // 会话级独立最大上下文上限（Tokens），null表示跟随模型
    val compressionTier: Int = 0,        // 会话独立压缩档位：0=L0, 1=L1, 2=L2, 3=L3, 4=L4, 5=LC
    val compressionRecentRounds: Int = 8, // L2 滚动摘要档保留的最近轮数 N (4~32)
    val compressionCustomPercent: Int = 30, // LC 自定义比例档保留原文的百分比 (10~90)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 消息
@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId"])]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val thinkingContent: String? = null,
    val attachments: String? = null,
    val variantGroupId: String? = null,
    val variantIndex: Int = 1,
    val tokenCount: Int = 0,
    val thinkingTokens: Int = 0,
    val responseTime: Long = 0,
    val toolCalls: String? = null,
    val translatedThinking: String? = null,
    val isPinned: Boolean = false,
    val isExcluded: Boolean = false,
    val modelName: String? = null,
    val replyDirection: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// 智能工具调用留痕记录
data class ToolCallRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val toolType: String,
    val toolName: String,
    val iconName: String = "AutoAwesome",
    val summary: String,
    val detailContent: String,
    val isSuccess: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

// 文件附件
data class Attachment(
    val uri: String,
    val name: String,
    val mimeType: String,
    val size: Long = 0,
    val base64Data: String? = null,
    val textContent: String? = null,
    val ocrText: String? = null,
    val processingNote: String? = null,
    val payloadMimeType: String? = null
)

// 正在回复时的排队消息模型（需求 6）
data class QueuedMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

// API使用统计
@Entity(tableName = "api_usage_stats")
data class ApiUsageStat(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val apiConfigId: Long,
    val provider: String,
    val modelName: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val thinkingTokens: Int = 0,
    val totalTokens: Int = 0,
    val cachedTokens: Int = 0,
    val responseTime: Long = 0,
    val success: Boolean = true,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// 模型使用统计汇总
data class ModelUsageSummary(
    val modelName: String,
    val provider: String,
    val totalInputTokens: Int,
    val totalOutputTokens: Int,
    val totalThinkingTokens: Int,
    val totalCachedTokens: Int,
    val totalTokens: Int,
    val requestCount: Int,
    val successCount: Int,
    val avgResponseTime: Long,
    val cacheHitRate: Float = 0f
)

// 环境变量
@Entity(tableName = "environment_variables")
data class EnvironmentVariable(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val value: String,
    val description: String? = null,
    val environment: String = "default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 系统提示词模板
@Entity(tableName = "prompt_templates")
data class PromptTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val content: String,
    val description: String? = null,
    val category: String = "general",
    val variables: String? = null,
    val isBuiltIn: Boolean = false,
    val useCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 跨对话可复用的用户偏好或项目背景；私密对话不会写入这里。
@Entity(
    tableName = "memory_items",
    indices = [
        Index(value = ["scope", "conversationId"]),
        Index(value = ["sourceMessageId"]),
        Index(value = ["updatedAt"])
    ]
)
data class MemoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scope: String = "user",
    val conversationId: Long? = null,
    val content: String,
    val keywords: String? = null,
    val sourceMessageId: Long? = null,
    val confidence: Float = 0.6f,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// 待用户确认的智能提炼记忆候选
data class PendingMemoryCandidate(
    val id: String = java.util.UUID.randomUUID().toString(),
    val distilledContent: String,
    val originalSnippet: String,
    val suggestedScope: String = "user", // "user" (长期记忆) 或 "conversation" (会话记忆)
    val conversationId: Long,
    val sourceMessageId: Long? = null,
    val category: String = "PREFERENCE"   // "PREFERENCE", "FACT", "PROJECT"
)

// 时间线独立节点实体
@Entity(
    tableName = "timeline_nodes",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["orderIndex"]),
        Index(value = ["updatedAt"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TimelineNode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val timeTag: String,                  // 明确的时间节点，如 "第 1 天·清晨"、"两周过后"
    @ColumnInfo(name = "eventContent")
    val event: String,                    // 关键事件内容
    val category: String = "PLOT_EVENT",  // 分类：PLOT_EVENT/RULE_CONSTRAINT/CHARACTER_SETTING/WORLD_SETTING
    val orderIndex: Int = 0,              // 明确的时间顺序索引
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    @get:Ignore
    val eventContent: String get() = event
}

// 会话分支
@Entity(
    tableName = "conversation_branches",
    indices = [Index(value = ["parentConversationId"])]
)
data class ConversationBranch(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val parentConversationId: Long,    // 父对话ID
    val branchMessageId: Long,         // 分支点消息ID
    val childConversationId: Long,     // 子对话ID
    val createdAt: Long = System.currentTimeMillis()
)

// 用户选择的模型配置
@Entity(
    tableName = "selected_models",
    indices = [Index(value = ["apiConfigId"])]
)
data class SelectedModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val apiConfigId: Long,             // 关联的API配置ID
    val modelName: String,             // 模型名称
    val displayName: String? = null,   // 显示名称
    val isEnabled: Boolean = true,     // 是否启用
    val capability: String = "auto",   // auto/text/multimodal
    val sortOrder: Int = 0,            // 排序顺序
    val contextWindowTokens: Int? = null, // 自定义上下文窗口大小 (Tokens)
    val supportsTools: Boolean = true,    // 是否支持工具/函数调用
    val supportsVision: Boolean = false,  // 是否支持视觉多模态
    val supportsThinking: Boolean = true, // 是否支持深度思考/Reasoning
    val supportsWebSearch: Boolean = true, // 是否支持联网搜索
    val createdAt: Long = System.currentTimeMillis()
)

data class ModelCustomSettings(
    val contextWindowTokens: Int? = null,
    val supportsTools: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsThinking: Boolean = true,
    val supportsWebSearch: Boolean = true
)


data class ChatModelOption(
    val apiConfigId: Long,
    val configName: String,
    val provider: String,
    val apiType: String,
    val modelName: String,
    val capability: String = "auto",
    val contextWindowTokens: Int? = null,
    val supportsTools: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsThinking: Boolean = true,
    val supportsWebSearch: Boolean = true,
    val baseUrl: String = "",
    val reasoningCapability: ReasoningCapabilityEvidence? = null
)

// 单次对话请求覆盖项。为null时不向API发送该参数，交给模型/服务端默认值处理。
data class ChatRequestOptions(
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val enableThinking: Boolean? = null,
    val thinkingEffort: String? = null,
    val enableWebSearch: Boolean? = null,
    val overrideSystemPrompt: Boolean = false,
    val systemPromptOverride: String? = null,
    val contextWindowOverrideTokens: Int? = null,
    val enableSessionMemory: Boolean? = null,
    val enableExternalMemory: Boolean? = null,
    val enableWorldBook: Boolean? = null,
    val activeWorldBookIds: String? = null,
    val preparedDirectionContext: PreparedDirectionContext? = null,
    val directionPlanningCount: Int? = null,
    val replyDirection: String? = null
)

data class ConversationContextUsage(
    val contextWindowTokens: Int = 0,
    val modelDefaultContextTokens: Int = 0,
    val promptBudgetTokens: Int = 0,
    val estimatedInputTokens: Int = 0,
    val usagePercent: Float = 0f,
    val recentMessageCount: Int = 0,
    val olderMessageCount: Int = 0,
    val recentTokens: Int = 0,
    val summaryTokens: Int = 0,
    val memoryTokens: Int = 0,
    val memoryItemCount: Int = 0,
    val hasRollingSummary: Boolean = false,
    val summaryUpdatedAt: Long? = null,
    val compressedThroughMessageId: Long? = null,
    val canCompress: Boolean = false,
    val compressionTier: CompressionTier = CompressionTier.L0,
    val compressionRecentRounds: Int = 8,
    val compressionCustomPercent: Int = 30,
    val tierPreviews: List<TierCompressionPreview> = emptyList()
)

// ============ 网络异常上下文回退决策 ============

enum class ContextFallbackChoice {
    FALLBACK,            // 回退
    IGNORE,              // 忽略
    PERMANENTLY_IGNORE   // （当前对话）永久忽略
}

data class ContextFallbackPromptState(
    val conversationId: Long,
    val reason: String,
    val onDecision: (ContextFallbackChoice) -> Unit
)

// ============ API 请求/响应格式 ============

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float? = null,
    val max_tokens: Int? = null,
    val max_completion_tokens: Int? = null,
    val top_p: Float? = null,
    val top_k: Int? = null,
    val stream: Boolean = false,
    val frequency_penalty: Float? = null,
    val presence_penalty: Float? = null,
    val stop: List<String>? = null,
    val seed: Int? = null,
    val response_format: ResponseFormat? = null,
    val stream_options: StreamOptions? = null,
    val web_search_options: WebSearchOptions? = null,
    val enable_search: Boolean? = null,
    val web_search: Boolean? = null,
    val search_context_size: String? = null,
    val enable_thinking: Boolean? = null,
    val thinking_budget: Int? = null,
    val thinking_effort: String? = null,
    val reasoning_effort: String? = null,
    val thinking: AnthropicThinking? = null
)

data class StreamOptions(
    val include_usage: Boolean = true
)

data class ResponseFormat(
    val type: String
)

data class WebSearchOptions(
    val search_context_size: String = "medium"
)

data class ChatMessage(
    val role: String,
    val content: Any
)

data class ContentPart(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

data class ImageUrl(
    val url: String,
    val detail: String = "auto"
)

// DeepSeek 思考模式请求
data class DeepSeekChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float? = null,
    val max_tokens: Int? = null,
    val top_p: Float? = null,
    val stream: Boolean = false,
    val frequency_penalty: Float? = null,
    val presence_penalty: Float? = null,
    val stop: List<String>? = null,
    val enable_thinking: Boolean? = null,
    val thinking_budget: Int? = null
)

// OpenAI API 响应格式
data class ChatCompletionResponse(
    val id: String?,
    val choices: List<Choice>?,
    val usage: Usage?,
    val error: ApiError?
)

data class Choice(
    val index: Int?,
    val message: MessageData?,
    val finish_reason: String?
)

data class MessageData(
    val role: String?,
    val content: String?,
    val reasoning_content: String? = null
)

data class Usage(
    val prompt_tokens: Int?,
    val completion_tokens: Int?,
    val total_tokens: Int?,
    val prompt_tokens_details: PromptTokensDetails? = null,
    val completion_tokens_details: CompletionTokensDetails? = null,
    val prompt_cache_hit_tokens: Int? = null,
    val prompt_cache_miss_tokens: Int? = null,
    val cached_tokens: Int? = null,
    val cache_read_input_tokens: Int? = null,
    val cache_creation_input_tokens: Int? = null,
    val cached_content_token_count: Int? = null
)

data class PromptTokensDetails(
    val cached_tokens: Int? = null,
    val cached_content_token_count: Int? = null,
    val cache_read_input_tokens: Int? = null
)

data class CompletionTokensDetails(
    val reasoning_tokens: Int? = null
)

data class ApiError(
    val message: String?,
    val type: String?,
    val code: String?
)

// 流式响应
data class ChatCompletionChunk(
    val id: String?,
    val choices: List<ChunkChoice>?,
    val usage: Usage?
)

data class ChunkChoice(
    val index: Int?,
    val delta: Delta?,
    val finish_reason: String?
)

data class Delta(
    val role: String?,
    val content: String?,
    val reasoning_content: String? = null,
    val reasoning_content_camel: String? = null,
    val reasoning: String? = null,
    val reasoningContent: String? = null,
    val thinking: String? = null,
    val thinking_content: String? = null,
    val thought: String? = null
)

// 统计数据
data class DailyStats(
    val date: String,
    val totalTokens: Int,
    val thinkingTokens: Int,
    val requestCount: Int,
    val avgResponseTime: Long
)

data class ModelStats(
    val modelName: String,
    val provider: String,
    val totalTokens: Int,
    val thinkingTokens: Int,
    val requestCount: Int
)

// 模型列表响应
data class ModelsResponse(
    val data: List<ModelInfo>?
)

data class ModelInfo(
    val id: String,
    val name: String?,
    val owned_by: String?,
    val context_length: Int? = null,
    val context_window: Int? = null,
    val max_context_length: Int? = null,
    val max_context_window: Int? = null,
    val max_input_tokens: Int? = null,
    val input_token_limit: Int? = null,
    val contextLength: Int? = null,
    val contextWindow: Int? = null,
    val maxContextLength: Int? = null,
    val maxContextWindow: Int? = null
)

// Anthropic API 格式
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val max_tokens: Int,
    val system: String? = null,
    val temperature: Float? = null,
    val top_p: Float? = null,
    val top_k: Int? = null,
    val stream: Boolean = false,
    val stop_sequences: List<String>? = null,
    val thinking: AnthropicThinking? = null,
    val output_config: AnthropicOutputConfig? = null
)

data class AnthropicThinking(
    val type: String = "enabled",
    val budget_tokens: Int? = null
)

data class AnthropicOutputConfig(val effort: String)

data class AnthropicMessage(
    val role: String,
    val content: Any
)

data class AnthropicContent(
    val type: String,
    val text: String? = null,
    val source: AnthropicImageSource? = null
)

data class AnthropicImageSource(
    val type: String = "base64",
    val media_type: String,
    val data: String
)

data class AnthropicResponse(
    val id: String?,
    val type: String?,
    val role: String?,
    val content: List<AnthropicResponseContent>?,
    val model: String?,
    val stop_reason: String?,
    val usage: AnthropicUsage?
)

data class AnthropicResponseContent(
    val type: String?,
    val text: String?
)

data class AnthropicUsage(
    val input_tokens: Int?,
    val output_tokens: Int?,
    val cache_read_input_tokens: Int? = null,
    val cache_creation_input_tokens: Int? = null
)

data class AnthropicStreamEvent(
    val type: String?,
    val delta: AnthropicDelta?,
    val usage: AnthropicUsage?,
    val message: AnthropicMessageObject? = null,
    val error: AnthropicErrorDetail? = null
)

data class AnthropicMessageObject(
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val model: String? = null,
    val usage: AnthropicUsage? = null
)

data class AnthropicDelta(
    val type: String?,
    val text: String?,
    val thinking: String?,
    val stop_reason: String?
)

data class AnthropicError(
    val type: String?,
    val error: AnthropicErrorDetail?
)

data class AnthropicErrorDetail(
    val type: String?,
    val message: String?
)
