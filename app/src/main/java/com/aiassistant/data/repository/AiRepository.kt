package com.aiassistant.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.aiassistant.data.local.*
import com.aiassistant.data.remote.RetrofitClient
import com.aiassistant.data.repository.helpers.*
import com.aiassistant.domain.model.*
import com.aiassistant.tools.EnrichedPromptResult
import com.aiassistant.utils.CryptoManager
import com.aiassistant.utils.FileUtils
import com.aiassistant.utils.PersonalizationManager
import com.aiassistant.utils.SmartMemoryExtractor
import com.aiassistant.utils.TavilySearchManager
import com.aiassistant.utils.TimelineMemoryHelper
import com.aiassistant.utils.TimelineReconcileResult
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.toTimelineEventItem
import com.aiassistant.utils.TimelineCategory
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.AdvancedMemoryEngine
import com.aiassistant.utils.TimelineReconcileDraft
import com.aiassistant.utils.TimelineReconcileCheckpoint
import com.aiassistant.utils.TimelineDraftManager
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Job
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

class ApiException(val statusCode: Int, message: String, val errorBody: String? = null) : Exception(message)

data class KeyAttemptFailure(
    val keyIndex: Int,
    val keyMasked: String,
    val errorMessage: String,
    val isTimeout: Boolean = false
)

data class AutoTimelineUpdateResult(
    val updatedStoryTime: String?,
    val newEvent: TimelineEventItem?,
    val summaryNotice: String,
    val action: String = "APPEND", // "APPEND" | "UPDATE" | "TIME_ONLY" | "SETTINGS_ONLY"
    val targetNodeId: Long? = null,
    val previousEventContent: String? = null,
    val atemporalSettings: List<AtemporalSettingItem> = emptyList(),
    val extractionErrorMessage: String? = null
)

class AiRepository(
    private val folderDao: FolderDao,
    private val apiConfigDao: ApiConfigDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val usageStatDao: UsageStatDao,
    private val environmentVariableDao: EnvironmentVariableDao,
    private val promptTemplateDao: PromptTemplateDao,
    private val memoryDao: MemoryDao,
    private val conversationBranchDao: ConversationBranchDao,
    private val selectedModelDao: SelectedModelDao,
    private val cryptoManager: CryptoManager,
    private val personalizationManager: PersonalizationManager,
    private val tavilySearchManager: TavilySearchManager,
    private val echoToolHub: com.aiassistant.tools.EchoToolHub? = null,
    private val worldBookDao: WorldBookDao? = null,
    private val timelineNodeDao: TimelineNodeDao? = null,
    private val firstTokenTimeoutMillis: Long = FirstTokenDeadline.DEFAULT_TIMEOUT_MILLIS
) {
    private val gson = Gson()
    private val tag = "AiRepository"
    private val activeStreamingCalls = ConcurrentHashMap<Long, Call>()
    private val activeMemoryCalls = ConcurrentHashMap<retrofit2.Call<*>, Long>()
    private val activeMemoryJobs = ConcurrentHashMap<Long, Pair<Long, Job>>()
    private val modelContextWindowCache = ConcurrentHashMap<String, Int>()

    companion object {
        const val UNCOMPRESSED_RECENT_MESSAGE_COUNT = 16 // 最近十几次对话不做压缩，原汁原味无损保留（涵盖8~10轮对话）
        const val SUMMARY_BUDGET_RATIO = 0.0f // 彻底废除滚动摘要预算占用
        const val MEMORY_BUDGET_RATIO = 0.12f // 充裕预算分配给会话记忆与时间线系统
        const val SYSTEM_PROMPT_TOKEN_RESERVE = 900
        const val MIN_RECENT_CONTEXT_TOKENS = 16_000

        const val MIN_SUMMARY_SOURCE_MESSAGES = 16
        const val MIN_SUMMARY_SOURCE_TOKENS = 8_000
        const val SUMMARY_PROMPT_MIN_TOKENS = 300
        const val SUMMARY_PROMPT_MAX_TOKENS = 3_000
        const val SUMMARY_COMPLETION_MIN_TOKENS = 512
        const val SUMMARY_COMPLETION_MAX_TOKENS = 4_096
        const val SUMMARY_TRANSCRIPT_MESSAGE_LIMIT = 80
        const val SUMMARY_TRANSCRIPT_HEAD_COUNT = 0 // 彻底废弃头部跳跃切片，防止开场剧情与近期断层拼接
        const val SUMMARY_TRANSCRIPT_CHAR_LIMIT = 10_000
        const val EXTRACTIVE_SUMMARY_MESSAGE_LIMIT = 12
        const val EXTRACTIVE_SUMMARY_CHAR_LIMIT = 2_000

        const val MEMORY_CAPTURE_FRESHNESS_MS = 10 * 60 * 1000L
        const val MEMORY_CAPTURE_CONFIDENCE = 0.72f
        const val MEMORY_RELEVANCE_THRESHOLD = 0.45f
        const val CONVERSATION_MEMORY_BOOST = 0.45f
        const val USER_MEMORY_BOOST = 0.35f
        const val MEMORY_CONFIDENCE_WEIGHT = 0.18f
        const val MEMORY_TERM_OVERLAP_WEIGHT = 0.32f
        const val MEMORY_RECENCY_WEIGHT = 0.12f
        const val MEMORY_RECENCY_WINDOW_MS = 14f * 24f * 60f * 60f * 1000f
        const val DEFAULT_UNKNOWN_CONTEXT_WINDOW_TOKENS = 256_000
        const val CONTEXT_OVERFLOW_RETRY_WINDOW_TOKENS = 200_000


        fun parseNamedApiKeys(rawKey: String?): List<NamedApiKey> = ApiKeysHelper.parseNamedApiKeys(rawKey)
        fun formatNamedApiKeys(keys: List<NamedApiKey>): String = ApiKeysHelper.formatNamedApiKeys(keys)
        fun parseApiKeys(rawKey: String?): List<String> = ApiKeysHelper.parseApiKeys(rawKey)

        fun isMainlyEnglish(text: String?): Boolean = TokenEstimationHelper.isMainlyEnglish(text)
        fun normalizeThinkingEffort(effort: String?, providerType: String = "openai", modelName: String? = null): String =
            TokenEstimationHelper.normalizeThinkingEffort(effort, providerType, modelName)
        fun thinkingBudgetForEffort(effort: String?, configuredBudget: Int): Int = TokenEstimationHelper.thinkingBudgetForEffort(effort, configuredBudget)
        fun isTimeoutException(e: Throwable): Boolean = NetworkExceptionClassifier.isTimeoutException(e)
        fun estimateTokenCount(text: String): Int = TokenEstimationHelper.estimateTokenCount(text)
        fun estimateContentTokenCount(content: Any?): Int = TokenEstimationHelper.estimateContentTokenCount(content)
        fun extractContextWindowFromError(message: String): Int? = TokenEstimationHelper.extractContextWindowFromError(message)

        fun sanitizeGeneratedTitle(rawTitle: String?): String? = ConversationTitleHelper.sanitizeGeneratedTitle(rawTitle)
        fun sanitizeSummaryCompletion(rawText: String?): String? = ConversationTitleHelper.sanitizeSummaryCompletion(rawText)
        fun isSummarySubstantiallyComplete(summary: String?): Boolean = ConversationTitleHelper.isSummarySubstantiallyComplete(summary)
        fun generateDuplicateTitle(originalTitle: String): String = ConversationTitleHelper.generateDuplicateTitle(originalTitle)
        fun isRequestCancellation(error: Throwable): Boolean = NetworkExceptionClassifier.isRequestCancellation(error)
        fun isNetworkFluctuationException(e: Throwable): Boolean = NetworkExceptionClassifier.isNetworkFluctuationException(e)

        fun parseOpenAiStreamLine(rawLine: String, gson: com.google.gson.Gson = com.google.gson.Gson()): OpenAiStreamChunkResult? =
            OpenAiStreamChunkParser.parseOpenAiStreamLine(rawLine, gson)

        fun extractRootBaseTitle(rawTitle: String): String = ConversationTitleHelper.extractRootBaseTitle(rawTitle)
        fun calculateNextBranchTitle(rootBaseTitle: String, existingTitles: List<String>): String =
            ConversationTitleHelper.calculateNextBranchTitle(rootBaseTitle, existingTitles)

        fun formatWorldBookPrompt(entries: List<WorldBookEntry>): String = ChatContextAssemblyHelper.formatWorldBookPrompt(entries)
        fun compactMessageForHistory(content: String, limit: Int = Int.MAX_VALUE): String =
            TokenEstimationHelper.compactMessageForHistory(content, limit)
        fun isErrorPlaceholderMessage(content: String?): Boolean = ChatContextAssemblyHelper.isErrorPlaceholderMessage(content)
        fun isRoleplayConversation(conversation: Conversation?): Boolean = ChatContextAssemblyHelper.isRoleplayConversation(conversation)
        fun formatRoleplaySystemPrompt(
            customPrompt: String?,
            memoryBlock: String? = null,
            worldBookBlock: String? = null
        ): String? = ChatContextAssemblyHelper.formatRoleplaySystemPrompt(customPrompt, memoryBlock, worldBookBlock)

        fun hasConversationTag(conversation: Conversation?, tag: String): Boolean =
            ChatContextAssemblyHelper.hasConversationTag(conversation, tag)

        fun normalizeChatMessagesRoleAlternation(rawMessages: List<ChatMessage>): List<ChatMessage> =
            ChatContextAssemblyHelper.normalizeChatMessagesRoleAlternation(rawMessages)

        fun resolveActiveContextMessages(
            usableMessages: List<Message>,
            compressedThrough: Long,
            recentBudget: Int
        ): ActiveContextResolution = ChatContextAssemblyHelper.resolveActiveContextMessages(usableMessages, compressedThrough, recentBudget)
    }

    fun cancelActiveRequest(conversationId: Long, cancelMemory: Boolean = true) {
        activeStreamingCalls.remove(conversationId)?.cancel()
        if (cancelMemory) {
            activeMemoryJobs.values.forEach { (id, job) -> if (id == conversationId) job.cancel(CancellationException("记忆提炼已取消")) }
            activeMemoryCalls.forEach { (call, id) -> if (id == conversationId) call.cancel() }
        }
    }

    private fun isRequestCancellation(error: Throwable): Boolean {
        if (error is CancellationException) return true
        if (error.message.equals("Canceled", ignoreCase = true)) return true
        return error.cause?.let(::isRequestCancellation) == true
    }

    private fun isContextLimitError(error: Throwable): Boolean {
        val messages = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null) {
            current.message?.let(messages::add)
            current = current.cause
        }
        val message = messages.joinToString(" ").lowercase()
        if (message.isBlank()) return false
        val hasTokenOrContext = listOf("token", "context", "上下文", "长度", "prompt", "input").any { it in message }
        val hasOverflow = listOf(
            "exceed",
            "exceeded",
            "too long",
            "too many",
            "maximum",
            "max",
            "length",
            "context_length_exceeded",
            "reduce",
            "超出",
            "超过",
            "过长"
        ).any { it in message }
        return hasTokenOrContext && hasOverflow
    }

    // ============ 文件夹相关 ============

    fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFolders()

    fun getRootFolders(): Flow<List<Folder>> = folderDao.getRootFolders()

    fun getSubFolders(parentId: Long): Flow<List<Folder>> = folderDao.getSubFolders(parentId)

    suspend fun getFolderById(id: Long): Folder? = folderDao.getFolderById(id)

    suspend fun createFolder(name: String, parentId: Long? = null, icon: String = "folder", color: Int = 0): Long {
        val folder = Folder(
            name = name,
            parentId = parentId,
            icon = icon,
            color = color
        )
        return folderDao.insertFolder(folder)
    }

    suspend fun updateFolder(folder: Folder) {
        folderDao.updateFolder(folder.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteFolder(id: Long, moveConversationsToRoot: Boolean = true) {
        if (moveConversationsToRoot) {
            folderDao.unassignConversationsFromFolder(id)
        }
        folderDao.deleteFolderById(id)
    }

    suspend fun getConversationCountInFolder(folderId: Long): Int {
        return folderDao.getConversationCount(folderId)
    }

    // ============ API配置相关 ============

    private val apiConfigOrderTrigger = MutableStateFlow(0)

    fun getAllApiConfigs(): Flow<List<ApiConfig>> = combine(
        apiConfigDao.getAllConfigs(),
        apiConfigOrderTrigger
    ) { configs: List<ApiConfig>, _: Int ->
        val order = personalizationManager.getApiConfigOrder()
        if (order.isEmpty()) {
            configs
        } else {
            val orderMap = order.withIndex().associate { it.value to it.index }
            configs.sortedWith(
                compareBy<ApiConfig> { orderMap[it.id] ?: Int.MAX_VALUE }
                    .thenByDescending { it.isDefault }
                    .thenBy { it.name }
            )
        }
    }

    fun saveApiConfigOrder(order: List<Long>) {
        personalizationManager.saveApiConfigOrder(order)
        apiConfigOrderTrigger.value += 1
    }

    fun isContextFallbackPermanentlyIgnored(conversationId: Long): Boolean {
        return personalizationManager.isContextFallbackPermanentlyIgnored(conversationId)
    }

    fun setContextFallbackPermanentlyIgnored(conversationId: Long, ignored: Boolean) {
        personalizationManager.setContextFallbackPermanentlyIgnored(conversationId, ignored)
    }

    suspend fun getApiConfigById(id: Long): ApiConfig? = apiConfigDao.getConfigById(id)

    suspend fun getDefaultApiConfig(): ApiConfig? {
        val defaultCfg = apiConfigDao.getDefaultConfig()
        if (defaultCfg != null && defaultCfg.isEnabled) return defaultCfg
        return apiConfigDao.getEnabledConfigs().first().firstOrNull()
    }

    fun getEnabledApiConfigs(): Flow<List<ApiConfig>> = apiConfigDao.getEnabledConfigs()

    suspend fun setApiConfigEnabled(id: Long, isEnabled: Boolean) {
        apiConfigDao.setConfigEnabled(id, isEnabled)
    }

    suspend fun saveApiConfig(config: ApiConfig): Long {
        // 检查apiKey是否已经加密（以 enc:v1: 标头为准）
        val apiKeyToSave = if (isAlreadyEncrypted(config.apiKey)) {
            // 如果key已经带有 enc:v1: 密文标头，保持原密文
            config.apiKey
        } else {
            // 否则加密并打上 enc:v1: 标头
            cryptoManager.encrypt(config.apiKey)
        }

        // 自动补全URL
        val normalizedConfig = config.copy(apiKey = apiKeyToSave)
        val finalConfig = normalizeUrl(normalizedConfig)

        return if (config.id == 0L) {
            val existing = apiConfigDao.getConfigByIdentity(
                name = finalConfig.name,
                provider = finalConfig.provider,
                baseUrl = finalConfig.baseUrl,
                apiType = finalConfig.apiType,
                modelName = finalConfig.modelName
            )
            if (existing != null) {
                apiConfigDao.updateConfig(
                    finalConfig.copy(
                        id = existing.id,
                        isDefault = existing.isDefault,
                        createdAt = existing.createdAt
                    )
                )
                existing.id
            } else {
                apiConfigDao.insertConfig(finalConfig)
            }
        } else {
            apiConfigDao.updateConfig(finalConfig)
            config.id
        }
    }

    // 自动补全URL
    private fun normalizeUrl(config: ApiConfig): ApiConfig {
        var baseUrl = config.baseUrl.trim()

        // 确保以http://或https://开头
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://$baseUrl"
        }

        // 移除末尾的斜杠
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.dropLast(1)
        }

        // 根据API类型自动补全路径
        when (config.apiType) {
            "anthropic" -> {
                // Anthropic API
                if (!baseUrl.endsWith("/v1") && !baseUrl.endsWith("/anthropic")) {
                    baseUrl = "$baseUrl/v1"
                }
            }
            "openai" -> {
                // OpenAI兼容格式
                if (!baseUrl.endsWith("/v1")) {
                    baseUrl = "$baseUrl/v1"
                }
            }
        }

        return config.copy(baseUrl = baseUrl)
    }

    // 检查是否已经加密
    private fun isAlreadyEncrypted(value: String): Boolean {
        return cryptoManager.isEncrypted(value)
    }

    suspend fun deleteApiConfig(config: ApiConfig) = apiConfigDao.deleteConfig(config)

    suspend fun setDefaultConfig(id: Long) {
        apiConfigDao.clearDefaultConfigs()
        apiConfigDao.setDefaultConfig(id)
    }

    suspend fun getDecryptedConfig(id: Long): ApiConfig? {
        val config = apiConfigDao.getConfigById(id) ?: return null
        return try {
            val decryptedKey = cryptoManager.decrypt(config.apiKey)
            if (!cryptoManager.isEncrypted(config.apiKey) && config.apiKey.isNotBlank()) {
                val newEncryptedKey = cryptoManager.encrypt(decryptedKey)
                apiConfigDao.updateConfig(config.copy(apiKey = newEncryptedKey))
            }
            config.copy(apiKey = decryptedKey)
        } catch (e: Exception) {
            Log.e(tag, "解密API Key失败", e)
            config // 返回原始配置，让调用者处理
        }
    }

    // 获取可用模型列表
    suspend fun fetchAvailableModels(configId: Long): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val config = getDecryptedConfig(configId)
                    ?: return@withContext Result.failure(Exception("API配置不存在"))

                val models = fetchModelsFromEndpoint(
                    baseUrl = normalizeApiBaseUrl(config.baseUrl, config.apiType),
                    apiKey = config.apiKey,
                    apiType = config.apiType
                )
                val cleanedModels = sanitizeModelNames(models)
                if (cleanedModels.isEmpty()) {
                    Result.failure(Exception("该API未返回模型列表，请手动输入模型名称"))
                } else {
                    Result.success(cleanedModels)
                }
            } catch (e: Exception) {
                Log.e(tag, "获取模型列表失败", e)
                if (e is com.aiassistant.data.remote.HttpAccessDeniedException) return@withContext Result.failure(e)
                // 返回预设模型列表作为备选
                val config = getApiConfigById(configId)
                val presetModels = config?.let { getPresetModels(it.apiType, it.provider) } ?: emptyList()
                if (presetModels.isNotEmpty()) {
                    Result.success(sanitizeModelNames(presetModels))
                } else {
                    Result.failure(Exception("网络错误，请检查API地址是否正确"))
                }
            }
        }
    }

    // 直接获取模型列表（不保存配置，支持多Key轮询尝试）
    suspend fun fetchAvailableModelsDirect(baseUrl: String, apiKey: String, apiType: String = "openai"): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            val allKeys = parseApiKeys(apiKey).ifEmpty { listOf(apiKey) }
            var lastException: Exception? = null

            for (key in allKeys) {
                try {
                    val normalizedBaseUrl = normalizeApiBaseUrl(baseUrl, apiType)
                    val models = fetchModelsFromEndpoint(normalizedBaseUrl, key, apiType)
                    val cleanedModels = sanitizeModelNames(models)
                    if (cleanedModels.isNotEmpty()) {
                        return@withContext Result.success(cleanedModels)
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Key获取模型列表失败: ${e.message}，尝试下一Key")
                    lastException = e
                    if (e is com.aiassistant.data.remote.HttpAccessDeniedException) return@withContext Result.failure(e)
                }
            }
            Result.failure(lastException ?: Exception("未能获取到模型列表，请手动输入模型名称"))
        }
    }

    private fun normalizeApiBaseUrl(rawBaseUrl: String, apiType: String): String {
        var baseUrl = rawBaseUrl.trim()
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://$baseUrl"
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.dropLast(1)
        }
        return when (apiType) {
            "anthropic" -> {
                if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/anthropic")) baseUrl else "$baseUrl/v1"
            }
            else -> {
                if (baseUrl.endsWith("/v1")) baseUrl else "$baseUrl/v1"
            }
        }
    }

    private fun fetchModelsFromEndpoint(baseUrl: String, apiKey: String, apiType: String): List<String> {
        val headers = if (apiType == "anthropic") {
            mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01"
            )
        } else {
            mapOf("Authorization" to RetrofitClient.formatApiKey(apiKey))
        }

        RetrofitClient.getJson(baseUrl, "models", headers).use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = bodyText.ifBlank { response.message }
                throw Exception("获取模型列表失败 (${response.code}): $detail")
            }

            val dynamicModels = parseModelNamesAndCacheContextWindows(bodyText, baseUrl, apiType)
            if (dynamicModels.isNotEmpty()) return dynamicModels

            val body = gson.fromJson(bodyText, ModelsResponse::class.java)
            return body?.data
                ?.onEach { cacheModelContextWindow(it) }
                ?.mapNotNull { sanitizeModelName(it.id.ifBlank { it.name.orEmpty() }) }
                ?.distinct()
                ?.sorted()
                .orEmpty()
        }
    }

    private fun parseModelNamesAndCacheContextWindows(bodyText: String, baseUrl: String, apiType: String): List<String> {
        return runCatching {
            val root = JsonParser.parseString(bodyText).asJsonObject
            val modelElements = when {
                root.get("data")?.isJsonArray == true -> root.getAsJsonArray("data")
                root.get("models")?.isJsonArray == true -> root.getAsJsonArray("models")
                root.get("model")?.isJsonArray == true -> root.getAsJsonArray("model")
                else -> return@runCatching emptyList()
            }

            modelElements.mapNotNull { element ->
                val modelObject = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val modelName = firstString(modelObject, "id", "name", "model", "model_name")
                    ?.let(::sanitizeModelName)
                    ?: return@mapNotNull null
                val aliases = listOfNotNull(
                    modelName,
                    firstString(modelObject, "id")?.let(::sanitizeModelName),
                    firstString(modelObject, "name")?.let(::sanitizeModelName)
                ).distinct()
                aliases.forEach { alias ->
                    ReasoningCapabilityCatalog.put(baseUrl, apiType, alias, ReasoningCapabilityEvidence.parse(modelObject))
                }
                extractContextWindowFromModelJson(modelObject)?.let { limit ->
                    aliases.forEach { alias ->
                        modelContextWindowCache[alias.lowercase()] = limit
                    }
                }
                modelName
            }.distinct().sorted()
        }.getOrElse {
            Log.w(tag, "动态解析模型列表失败", it)
            emptyList()
        }
    }

    private fun firstString(obj: JsonObject, vararg names: String): String? {
        return names.firstNotNullOfOrNull { name ->
            obj.get(name)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        }
    }

    private fun extractContextWindowFromModelJson(obj: JsonObject): Int? {
        val directKeys = listOf(
            "context_length",
            "context_window",
            "max_context_length",
            "max_context_window",
            "max_model_len",
            "max_model_length",
            "max_sequence_length",
            "max_position_embeddings",
            "max_input_tokens",
            "input_token_limit",
            "n_ctx",
            "contextLength",
            "contextWindow",
            "maxContextLength",
            "maxContextWindow",
            "maxModelLen",
            "maxInputTokens"
        )
        directKeys.firstNotNullOfOrNull { key ->
            parseContextWindowValue(obj.get(key))
        }?.let { return it }

        val nestedKeys = listOf("metadata", "limits", "capabilities", "model_info", "config", "parameters")
        return nestedKeys.firstNotNullOfOrNull { key ->
            obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.let(::extractContextWindowFromModelJson)
        }
    }

    private fun parseContextWindowValue(value: JsonElement?): Int? {
        if (value == null || value.isJsonNull) return null
        val parsed = when {
            value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> parseContextWindowFromText(value.asString)
            else -> null
        }
        return parsed?.takeIf { it >= 4_000 }?.coerceIn(4_000, 2_000_000)
    }

    private fun cacheModelContextWindow(modelInfo: ModelInfo) {
        val limit = listOfNotNull(
            modelInfo.context_length,
            modelInfo.context_window,
            modelInfo.max_context_length,
            modelInfo.max_context_window,
            modelInfo.max_input_tokens,
            modelInfo.input_token_limit,
            modelInfo.contextLength,
            modelInfo.contextWindow,
            modelInfo.maxContextLength,
            modelInfo.maxContextWindow
        ).firstOrNull { it > 0 } ?: return

        listOf(modelInfo.id, modelInfo.name.orEmpty())
            .mapNotNull(::sanitizeModelName)
            .forEach { modelName ->
                modelContextWindowCache[modelName.lowercase()] = limit.coerceIn(4_000, 2_000_000)
            }
    }

    private fun sanitizeModelNames(models: List<String>): List<String> {
        return models.mapNotNull { sanitizeModelName(it) }.distinct()
    }

    private fun sanitizeModelName(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        val blockedEdges = setOf('"', '“', '”', '\'', '`', ']', '[', '\\')
        if (value.first() in blockedEdges || value.last() in blockedEdges) return null
        if (value.any { it.isISOControl() }) return null
        return value
    }

    // 获取预设模型列表（对齐 2026-09 主流；实际可用以服务端 /models 为准）
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

    // ============ 对话相关 ============

    fun getAllConversations(): Flow<List<Conversation>> = conversationDao.getAllConversations()

    fun getUnfiledConversations(): Flow<List<Conversation>> = conversationDao.getUnfiledConversations()

    fun getPinnedConversations(): Flow<List<Conversation>> = conversationDao.getPinnedConversations()

    fun getHiddenConversations(): Flow<List<Conversation>> = conversationDao.getHiddenConversations()

    fun getConversationsByFolder(folderId: Long): Flow<List<Conversation>> =
        conversationDao.getConversationsByFolder(folderId)

    fun searchConversations(query: String): Flow<List<Conversation>> =
        conversationDao.searchConversations(query)

    suspend fun getConversationById(id: Long): Conversation? =
        conversationDao.getConversationById(id)

    suspend fun createConversation(
        title: String,
        apiConfigId: Long,
        modelName: String,
        folderId: Long? = null,
        systemPrompt: String? = null,
        tags: String? = null
    ): Long {
        val config = apiConfigDao.getConfigById(apiConfigId)
        val conversation = Conversation(
            title = title,
            folderId = folderId,
            apiConfigId = apiConfigId,
            modelName = modelName,
            temperature = config?.temperature ?: 0.95f,
            maxTokens = config?.maxTokens?.takeIf { it != 50000 } ?: 4096,
            topP = config?.topP ?: 1.0f,
            enableThinking = true,
            thinkingEffort = config?.thinkingEffort ?: "high",
            enableWebSearch = false,
            systemPrompt = systemPrompt,
            tags = tags
        )
        return conversationDao.insertConversation(conversation)
    }

    suspend fun resolveDefaultModelName(config: ApiConfig): String = withContext(Dispatchers.IO) {
        val savedModels = selectedModelDao.getModelsByConfig(config.id).first()
            .filter { sanitizeModelName(it.modelName) != null }
        val enabledModels = savedModels.filter { it.isEnabled }
        val configDefault = sanitizeModelName(config.modelName)

        enabledModels.firstOrNull { it.modelName == configDefault }?.modelName
            ?: savedModels.firstOrNull { it.modelName == configDefault }?.modelName
            ?: enabledModels.firstOrNull()?.modelName
            ?: savedModels.firstOrNull()?.modelName
            ?: configDefault
            ?: config.modelName
    }

    suspend fun deleteConversation(id: Long) {
        ChatGenerationManager.cancelSession(id)
        cancelActiveRequest(id)
        val db = com.aiassistant.AiAssistantApp.instance.database
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL("UPDATE memory_items SET sourceMessageId=NULL WHERE sourceMessageId IN (SELECT id FROM messages WHERE conversationId=?)", arrayOf(id))
            db.openHelper.writableDatabase.execSQL("UPDATE roleplay_memories SET sourceMessageId=NULL WHERE sourceMessageId IN (SELECT id FROM messages WHERE conversationId=?)", arrayOf(id))
            messageDao.deleteMessagesByConversation(id)
            memoryDao.deleteConversationMemories(id)
            db.openHelper.writableDatabase.execSQL("DELETE FROM conversation_branches WHERE parentConversationId = ? OR childConversationId = ?", arrayOf(id, id))
            conversationDao.deleteConversationById(id)
        }
    }

    suspend fun destroyPrivateConversation(id: Long) {
        deleteConversation(id)
    }

    suspend fun setConversationHidden(conversationId: Long, hidden: Boolean) {
        val conversation = conversationDao.getConversationById(conversationId) ?: return
        conversationDao.updateTags(conversationId, updateTag(conversation.tags, "hidden", hidden))
    }

    suspend fun setConversationsHidden(conversationIds: Collection<Long>, hidden: Boolean) {
        conversationIds.forEach { setConversationHidden(it, hidden) }
    }

    fun isRoleplayConversation(conversation: Conversation?): Boolean =
        Companion.isRoleplayConversation(conversation)

    fun hasConversationTag(conversation: Conversation?, tag: String): Boolean =
        Companion.hasConversationTag(conversation, tag)

    fun updateTag(rawTags: String?, tag: String, enabled: Boolean): String? {
        val tags = rawTags
            ?.split(',', ';', '|', ' ')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toMutableSet()
            ?: mutableSetOf()
        if (enabled) {
            tags += tag
        } else {
            tags.removeAll { it.equals(tag, ignoreCase = true) }
        }
        return tags.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    suspend fun moveToFolder(conversationId: Long, folderId: Long?) {
        conversationDao.moveToFolder(conversationId, folderId)
    }

    suspend fun setPinned(conversationId: Long, isPinned: Boolean) {
        conversationDao.setPinned(conversationId, isPinned)
    }

    suspend fun updateConversationTitle(conversationId: Long, title: String) {
        val conv = conversationDao.getConversationById(conversationId) ?: return
        conversationDao.updateConversation(conv.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateConversation(conversation: Conversation) {
        conversationDao.updateConversation(conversation)
    }

    suspend fun updateConversationContextWindowTokens(conversationId: Long, tokens: Int?) = withContext(Dispatchers.IO) {
        conversationDao.updateContextWindowTokens(conversationId, tokens, System.currentTimeMillis())
    }

    // ============ 模型长记忆相关 ============

    fun getAllMemories(): Flow<List<MemoryItem>> = memoryDao.getAllMemories()

    fun getGlobalMemories(): Flow<List<MemoryItem>> = memoryDao.getGlobalMemoriesFlow()

    fun searchGlobalMemories(query: String): Flow<List<MemoryItem>> = memoryDao.searchGlobalMemories(query)

    suspend fun clearGlobalMemories() = withContext(Dispatchers.IO) {
        memoryDao.deleteGlobalMemories()
    }

    fun getConversationMemories(conversationId: Long): Flow<List<MemoryItem>> =
        memoryDao.getConversationMemoriesFlow(conversationId)

    suspend fun getConversationMemoriesList(conversationId: Long): List<MemoryItem> =
        memoryDao.getConversationMemories(conversationId)

    suspend fun addConversationMemory(conversationId: Long, content: String): Long {
        val now = System.currentTimeMillis()
        val trimmed = content.trim()
        val item = MemoryItem(
            scope = "conversation",
            conversationId = conversationId,
            content = trimmed,
            keywords = tokenizeForMemory(trimmed).take(18).joinToString(",").ifBlank { null },
            confidence = 1.0f,
            isEnabled = true,
            createdAt = now,
            updatedAt = now
        )
        return memoryDao.insertMemory(item)
    }

    suspend fun addUserMemory(content: String): Long {
        val now = System.currentTimeMillis()
        val trimmed = content.trim()
        val item = MemoryItem(
            scope = "user",
            conversationId = null,
            content = trimmed,
            keywords = tokenizeForMemory(trimmed).take(18).joinToString(",").ifBlank { null },
            confidence = 1.0f,
            isEnabled = true,
            createdAt = now,
            updatedAt = now
        )
        return memoryDao.insertMemory(item)
    }

    suspend fun clearConversationMemories(conversationId: Long) {
        memoryDao.deleteConversationMemories(conversationId)
    }

    fun searchMemories(query: String): Flow<List<MemoryItem>> = memoryDao.searchMemories(query)

    suspend fun getMemoryById(id: Long): MemoryItem? = memoryDao.getMemoryById(id)

    suspend fun insertMemory(memory: MemoryItem): Long = memoryDao.insertMemory(memory)

    suspend fun updateMemory(memory: MemoryItem) = memoryDao.updateMemory(memory)

    suspend fun deleteMemory(id: Long) = memoryDao.deleteMemoryById(id)

    suspend fun deleteMemory(memory: MemoryItem) = deleteMemory(memory.id)

    suspend fun deleteAllMemories() = memoryDao.deleteAllMemories()

    suspend fun clearAllMemories() = deleteAllMemories()

    suspend fun saveMemory(memory: MemoryItem): Long {
        return if (memory.id > 0L) {
            updateMemory(memory)
            memory.id
        } else {
            insertMemory(memory)
        }
    }

    suspend fun setMemoryEnabled(id: Long, isEnabled: Boolean) = memoryDao.setMemoryEnabled(id, isEnabled)

    suspend fun saveConfirmedMemory(
        content: String,
        scope: String,
        conversationId: Long?,
        sourceMessageId: Long? = null
    ): Long {
        val now = System.currentTimeMillis()
        val item = MemoryItem(
            scope = scope,
            conversationId = if (scope == "conversation") conversationId else null,
            content = content,
            keywords = tokenizeForMemory(content).take(18).joinToString(","),
            sourceMessageId = sourceMessageId,
            confidence = 1.0f,
            isEnabled = true,
            createdAt = now,
            updatedAt = now
        )
        return memoryDao.insertMemory(item)
    }

    // ============ 世界书 (World Book / Lorebook) 相关 ============
    val worldBookDaoInstance: WorldBookDao? get() = worldBookDao

    fun getAllWorldBooks(): Flow<List<WorldBook>> =
        worldBookDao?.getAllBooks() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getAllWorldBooksList(): List<WorldBook> =
        worldBookDao?.getAllBooksList() ?: emptyList()

    suspend fun getWorldBookById(id: Long): WorldBook? =
        worldBookDao?.getBookById(id)

    suspend fun insertWorldBook(book: WorldBook): Long =
        worldBookDao?.insertBook(book) ?: 0L

    suspend fun updateWorldBook(book: WorldBook) {
        worldBookDao?.updateBook(book)
    }

    suspend fun deleteWorldBook(id: Long) {
        worldBookDao?.deleteBookById(id)
    }

    suspend fun setWorldBookEnabled(id: Long, isEnabled: Boolean) {
        worldBookDao?.setBookEnabled(id, isEnabled)
    }

    fun getWorldBookEntries(bookId: Long): Flow<List<WorldBookEntry>> =
        worldBookDao?.getEntriesForBook(bookId) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getWorldBookEntriesList(bookId: Long): List<WorldBookEntry> =
        worldBookDao?.getEntriesForBookList(bookId) ?: emptyList()

    suspend fun getWorldBookEntryById(id: Long): WorldBookEntry? =
        worldBookDao?.getEntryById(id)

    suspend fun insertWorldBookEntry(entry: WorldBookEntry): Long =
        worldBookDao?.insertEntry(entry) ?: 0L

    suspend fun updateWorldBookEntry(entry: WorldBookEntry) {
        worldBookDao?.updateEntry(entry)
    }

    suspend fun deleteWorldBookEntry(id: Long) {
        worldBookDao?.deleteEntryById(id)
    }

    suspend fun setWorldBookEntryEnabled(id: Long, isEnabled: Boolean) {
        worldBookDao?.setEntryEnabled(id, isEnabled)
    }

    fun searchWorldBookEntries(query: String): Flow<List<WorldBookEntry>> =
        worldBookDao?.searchEntries(query) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun matchWorldBookEntries(
        text: String,
        activeBookIds: List<Long>? = null,
        maxTokenBudget: Int = 1500
    ): List<WorldBookEntry> {
        val dao = worldBookDao ?: return emptyList()
        val candidateEntries = if (!activeBookIds.isNullOrEmpty()) {
            dao.getActiveEntriesForBooks(activeBookIds)
        } else {
            dao.getActiveEntriesFromEnabledBooks()
        }
        if (candidateEntries.isEmpty()) return emptyList()

        val matched = candidateEntries.filter { it.matchesText(text) }
        val sorted = matched.sortedWith(
            compareByDescending<WorldBookEntry> { it.priority }
                .thenByDescending { it.updatedAt }
        )

        val results = mutableListOf<WorldBookEntry>()
        var estimatedTokens = 0
        for (entry in sorted) {
            val cost = (entry.name.length + entry.content.length) / 2 + 10
            if (results.isNotEmpty() && estimatedTokens + cost > maxTokenBudget) break
            results.add(entry)
            estimatedTokens += cost
        }
        return results
    }

    // ============ 消息相关 ============

    fun getMessages(conversationId: Long): Flow<List<Message>> =
        messageDao.getMessagesByConversation(conversationId)

    suspend fun getMessagesList(conversationId: Long): List<Message> =
        messageDao.getMessagesList(conversationId)

    suspend fun getConversationContextUsage(
        conversationId: Long,
        modelNameOverride: String? = null,
        maxOutputTokens: Int? = null,
        contextWindowOverrideTokens: Int? = null,
        l2RoundsOverride: Int? = null,
        customPercentOverride: Int? = null
    ): ConversationContextUsage = withContext(Dispatchers.IO) {
        val conversation = getConversationById(conversationId) ?: return@withContext ConversationContextUsage()
        val messages = getMessagesList(conversationId).collapseVariantsForHistory()
        val modelName = modelNameOverride
            ?.takeIf { it.isNotBlank() }
            ?: conversation.modelName

        // 预览覆盖：滑杆调整未保存时，用覆盖值即时重算快照（不落库）
        val effectiveConversation = if (l2RoundsOverride != null || customPercentOverride != null) {
            conversation.copy(
                compressionRecentRounds = l2RoundsOverride ?: conversation.compressionRecentRounds,
                compressionCustomPercent = customPercentOverride ?: conversation.compressionCustomPercent
            )
        } else {
            conversation
        }

        buildContextUsageSnapshot(
            conversation = effectiveConversation,
            messages = messages,
            modelName = modelName,
            maxOutputTokens = maxOutputTokens,
            contextWindowOverrideTokens = contextWindowOverrideTokens ?: conversation.contextWindowTokens
        )
    }

    suspend fun compressConversationContext(
        conversationId: Long,
        modelNameOverride: String? = null,
        maxOutputTokens: Int? = null,
        contextWindowOverrideTokens: Int? = null
    ): Result<ConversationContextUsage> = withContext(Dispatchers.IO) {
        runCatching {
            val conversation = getConversationById(conversationId)
                ?: throw IllegalStateException("对话不存在")
            val config = getDecryptedConfig(conversation.apiConfigId)
                ?: throw IllegalStateException("API配置不存在")
            val messages = getMessagesList(conversationId).collapseVariantsForHistory()
            val modelName = modelNameOverride
                ?.takeIf { it.isNotBlank() }
                ?: resolveRequestModel(config.copy(modelName = conversation.modelName), resolveChatRequestOptions(config, null))

            val snapshot = buildContextUsageSnapshot(
                conversation = conversation,
                messages = messages,
                modelName = modelName,
                maxOutputTokens = maxOutputTokens,
                contextWindowOverrideTokens = contextWindowOverrideTokens ?: conversation.contextWindowTokens
            )
            val usableMessages = messages.filter { message ->
                (message.role == "user" || message.role == "assistant") &&
                    message.content.isNotBlank() &&
                    !message.isExcluded &&
                    !isErrorPlaceholderMessage(message.content)
            }
            if (usableMessages.size <= UNCOMPRESSED_RECENT_MESSAGE_COUNT) {
                // 当前对话处于最近十几次（16条）未压缩无损保留窗口，无需压缩
                return@runCatching snapshot
            }

            // 超出最近 16 条无损保留窗口的较早历史对话，全部作为提炼压缩的目标
            val olderMessages = usableMessages.dropLast(UNCOMPRESSED_RECENT_MESSAGE_COUNT)

            if (olderMessages.isNotEmpty()) {
                // 1. 将较早对话梳理并沉淀为时间线节点 (作为时空演变压缩成果)
                try {
                    val localResult = fallbackLocalTimelineScan(olderMessages)
                    if (localResult.events.isNotEmpty()) {
                        val currentNodes = timelineNodeDao?.getTimelineNodes(conversationId).orEmpty()
                        val newNodes = localResult.events.mapIndexedNotNull { idx, item ->
                            val cleanEvent = item.content.trim()
                            if (cleanEvent.isBlank() || currentNodes.any { it.event == cleanEvent }) null
                            else {
                                TimelineNode(
                                    conversationId = conversationId,
                                    timeTag = item.timeTag.ifBlank { "早期剧情" },
                                    event = cleanEvent,
                                    category = item.category.name,
                                    orderIndex = currentNodes.size + idx
                                )
                            }
                        }
                        if (newNodes.isNotEmpty()) {
                            timelineNodeDao?.insertTimelineNodes(newNodes)
                        }
                    }
                    if (localResult.currentStoryTime.isNotBlank() && localResult.currentStoryTime != "未确定") {
                        if (conversation.currentStoryTime.isNullOrBlank() || conversation.currentStoryTime == "未确定") {
                            conversationDao.updateConversation(conversation.copy(currentStoryTime = localResult.currentStoryTime))
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(tag, "Failed to persist timeline nodes during context compression: ${e.message}")
                }

                // 2. 将较早对话的核心事实提炼为会话专属记忆 (作为设定事实压缩成果)
                try {
                    val extractive = AdvancedMemoryEngine.generateExtractiveStructuredSummary(olderMessages)
                    val candidateFacts = (extractive.coreConstraints + extractive.openItems).map { it.trim() }.filter { it.isNotBlank() }
                    val existingMemories = memoryDao.getCandidateMemories(conversationId).map { it.content.trim() }.toSet()
                    val memoriesToInsert: List<MemoryItem> = candidateFacts.filter { fact: String ->
                        !existingMemories.contains(fact)
                    }.take(8).map { fact: String ->
                        MemoryItem(
                            conversationId = conversationId,
                            content = fact,
                            scope = "conversation",
                            confidence = 0.85f,
                            isEnabled = true
                        )
                    }
                    for (item: MemoryItem in memoriesToInsert) {
                        memoryDao.insertMemory(item)
                    }
                } catch (e: Throwable) {
                    Log.w(tag, "Failed to persist memory items during context compression: ${e.message}")
                }

                // 3. 记录压缩截断水线，并彻底清空旧的 rollingSummary (置为 null)，杜绝陈旧摘要锁死时间点
                val lastOlderMessageId = olderMessages.last().id
                conversationDao.updateRollingSummary(
                    conversationId = conversationId,
                    summary = null,
                    messageId = lastOlderMessageId,
                    timestamp = System.currentTimeMillis()
                )
            }

            val refreshedConversation = getConversationById(conversationId) ?: conversation
            buildContextUsageSnapshot(
                conversation = refreshedConversation,
                messages = messages,
                modelName = modelName,
                maxOutputTokens = maxOutputTokens,
                contextWindowOverrideTokens = contextWindowOverrideTokens ?: refreshedConversation.contextWindowTokens
            )
        }
    }

    /**
     * 按需生成/重新生成滚动摘要（由用户在界面显式触发）：
     * 不受后台自动归约 16 条的高门槛限制，只要会话有 >= 2 条有效对话，即可根据当前对话提炼前序摘要并入库。
     */
    suspend fun generateRollingSummaryNow(
        conversationId: Long,
        modelNameOverride: String? = null,
        maxOutputTokens: Int? = null,
        contextWindowOverrideTokens: Int? = null
    ): Result<ConversationContextUsage> = withContext(Dispatchers.IO) {
        // 废除滚动摘要：主动整理统一委托至 compressConversationContext，执行时间线梳理与会话记忆沉淀，最近十几次对话无损保留
        compressConversationContext(
            conversationId = conversationId,
            modelNameOverride = modelNameOverride,
            maxOutputTokens = maxOutputTokens,
            contextWindowOverrideTokens = contextWindowOverrideTokens
        )
    }

    suspend fun updateRollingSummary(
        conversationId: Long,
        newSummary: String
    ) = withContext(Dispatchers.IO) {
        val trimmed = newSummary.trim()
        val summaryToSave = if (trimmed.isBlank()) null else trimmed
        conversationDao.updateRollingSummary(
            conversationId = conversationId,
            summary = summaryToSave,
            messageId = if (summaryToSave != null) System.currentTimeMillis() else 0L
        )
    }

    suspend fun clearRollingSummary(conversationId: Long) = withContext(Dispatchers.IO) {
        conversationDao.updateRollingSummary(
            conversationId = conversationId,
            summary = null,
            messageId = 0L
        )
    }

    suspend fun updateConversationModelAvatar(conversationId: Long, avatarUri: String?) = withContext(Dispatchers.IO) {
        conversationDao.updateModelAvatarUri(conversationId, avatarUri)
    }

    suspend fun updateConversationCompressionTier(
        conversationId: Long,
        tier: com.aiassistant.domain.model.CompressionTier,
        recentRounds: Int,
        customPercent: Int = com.aiassistant.domain.model.CompressionTierPolicy.DEFAULT_CUSTOM_RETAIN_PERCENT
    ) = withContext(Dispatchers.IO) {
        conversationDao.updateCompressionTier(
            id = conversationId,
            tier = tier.level,
            recentRounds = recentRounds.coerceIn(
                com.aiassistant.domain.model.CompressionTierPolicy.MIN_L2_RECENT_ROUNDS,
                com.aiassistant.domain.model.CompressionTierPolicy.MAX_L2_RECENT_ROUNDS
            ),
            customPercent = customPercent.coerceIn(
                com.aiassistant.domain.model.CompressionTierPolicy.MIN_CUSTOM_RETAIN_PERCENT,
                com.aiassistant.domain.model.CompressionTierPolicy.MAX_CUSTOM_RETAIN_PERCENT
            )
        )
    }

    suspend fun saveMessage(message: Message, expectedMutationEpoch: Long? = null): Long {
        currentCoroutineContext().ensureActive()
        val id = com.aiassistant.AiAssistantApp.instance.database.withTransaction {
            if (expectedMutationEpoch != null && expectedMutationEpoch != ChatGenerationManager.mutationEpoch(message.conversationId)) throw CancellationException("对话已删除或恢复，丢弃旧轮次回复")
            check(conversationDao.getConversationById(message.conversationId) != null) { "对话已删除，停止保存回复" }
            val inserted = messageDao.insertMessage(message)
            updateConversationStats(message.conversationId)
            inserted
        }
        // Saving must never make a model request. Memory is processed once after the
        // foreground chat finishes, using the same distilled result for storage and review.
        return id
    }

    suspend fun updateMessage(message: Message) {
        com.aiassistant.AiAssistantApp.instance.database.withTransaction {
            messageDao.updateMessage(message)
            updateConversationStats(message.conversationId)
        }
    }

    suspend fun updateTranslatedThinking(messageId: Long, translatedThinking: String?) {
        messageDao.updateTranslatedThinking(messageId, translatedThinking)
    }

    suspend fun updateMessageModelName(messageId: Long, modelName: String) {
        messageDao.updateMessageModelName(messageId, modelName)
    }

    suspend fun deleteMessage(message: Message) {
        messageDao.deleteMessage(message)
        updateConversationStats(message.conversationId)
    }

    suspend fun deleteMessagesFrom(conversationId: Long, createdAt: Long) {
        messageDao.deleteMessagesFrom(conversationId, createdAt)
        updateConversationStats(conversationId)
    }

    private suspend fun updateConversationStats(conversationId: Long) {
        conversationDao.refreshStats(conversationId)
    }

    suspend fun searchMessages(query: String): List<Message> = messageDao.searchMessages(query)

    // ============ AI API调用 ============

    suspend fun sendChatMessage(
        configId: Long,
        conversationId: Long,
        userMessage: String,
        attachments: List<Attachment> = emptyList(),
        onToken: (String) -> Unit,
        onThinkingToken: (String) -> Unit = {},
        onComplete: (String, String?, Any?) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val config = getDecryptedConfig(configId)
                    ?: throw Exception("API配置不存在")
                if (!config.isEnabled) {
                    throw Exception("当前 API 配置已关闭，请在设置中开启后再使用")
                }

                // All public chat entry points share the same retry/deadline policy.
                dispatchChatMessageWithConfig(config, conversationId, userMessage, attachments,
                    null, null, 1, onToken, onThinkingToken, onComplete = onComplete)
            } catch (e: Exception) {
                if (isRequestCancellation(e)) throw CancellationException("请求已取消", e)
                Log.e(tag, "发送消息失败", e)
                onError(e.message ?: "未知错误")

                // 记录失败统计
                try {
                    val failedConfig = getApiConfigById(configId)
                    failedConfig?.let { cfg ->
                        val stat = ApiUsageStat(
                            apiConfigId = configId,
                            provider = cfg.provider,
                            modelName = cfg.modelName,
                            success = false,
                            errorMessage = e.message
                        )
                        usageStatDao.insertStat(stat)
                    }
                } catch (statEx: Exception) {
                    Log.e(tag, "记录失败统计异常", statEx)
                }
            }
        }
    }

    suspend fun prepareReplyDirectionContext(
        config: ApiConfig,
        conversationId: Long,
        userMessage: String,
        options: ChatRequestOptions,
        assistantVariantGroupId: String? = null
    ): PreparedDirectionContext {
        val conversation = getConversationById(conversationId)
        val effective = resolveChatRequestOptions(config, options)
        val history = getMessagesList(conversationId)
            .filter { assistantVariantGroupId.isNullOrBlank() || it.variantGroupId != assistantVariantGroupId }
            .dropLastCurrentUserMessage(userMessage)
        val bundle = buildContextBundle(conversation, config, history, resolveRequestModel(config, effective),
            effective.maxTokens, effective.contextWindowOverrideTokens, userMessage, effective)
        val roleplay = isRoleplayConversation(conversation)
        val prompt = resolveSystemPromptWithPriority(conversation, effective, roleplay)
        val lore = if (effective.enableWorldBook == true && userMessage.isNotBlank()) {
            val ids = effective.activeWorldBookIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
            matchWorldBookEntries(userMessage, ids).takeIf { it.isNotEmpty() }?.let {
                "<world_book_lore>\n${formatWorldBookPrompt(it)}\n</world_book_lore>"
            }
        } else null
        val system = buildEffectiveSystemPrompt(prompt.first, bundle.summary, bundle.memoryBlock,
            effective, prompt.second, roleplay, lore)
        val enriched = enrichUserMessageWithWebSearch(userMessage, effective)
        return PreparedDirectionContext(system, bundle.recentMessages, enriched.enrichedPrompt, enriched.toolCalls)
    }

    suspend fun generateReplyDirections(
        config: ApiConfig,
        conversationId: Long,
        userMessage: String,
        attachments: List<Attachment>,
        options: ChatRequestOptions,
        count: Int,
        onStatus: (String) -> Unit = {}
    ): List<ReplyDirection> {
        requireNotNull(options.preparedDirectionContext)
        var result: String? = null
        // Reuse both protocol parsers, deadlines and configured retry policy, but never persist planner output.
        dispatchChatMessageWithConfig(config.copy(responseFormat = null, stopSequences = null), conversationId,
            userMessage, attachments, options.copy(directionPlanningCount = count.coerceIn(2, 4),
                enableThinking = false, enableWebSearch = false, maxTokens = 2048, replyDirection = null), null, 1,
            onToken = {}, onThinkingToken = {}, onStatusUpdate = onStatus,
            onKeyAttemptError = { _, _, error -> onStatus("生成方向失败：$error") },
            onComplete = { text, _, _ -> result = text })
        return ReplyDirections.parse(result ?: error("模型未返回回复方向"), count)
    }

    private fun directionUserPrompt(prompt: String, options: ChatRequestOptions): String =
        options.directionPlanningCount?.let { "$prompt\n\n${ReplyDirections.planningInstruction(it)}" } ?: prompt

    private fun directionSystemPrompt(prompt: String?, options: ChatRequestOptions): String? =
        options.replyDirection?.let { listOfNotNull(prompt, it).joinToString("\n\n") } ?: prompt

    // 使用自定义配置发送消息（支持临时切换模型和参数）
    suspend fun sendChatMessageWithConfig(
        config: ApiConfig,
        conversationId: Long,
        userMessage: String,
        attachments: List<Attachment> = emptyList(),
        options: ChatRequestOptions? = null,
        assistantVariantGroupId: String? = null,
        assistantVariantIndex: Int = 1,
        onToken: (String) -> Unit,
        onThinkingToken: (String) -> Unit = {},
        onStatusUpdate: ((String) -> Unit)? = null,
        onKeyAttemptError: ((keyIndex: Int, keyMasked: String, errorMsg: String) -> Unit)? = null,
        onResetBuffer: (() -> Unit)? = null,
        onComplete: (String, String?, Any?) -> Unit,
        onError: (String) -> Unit,
        onContextFallbackPrompt: (suspend (reason: String) -> ContextFallbackChoice)? = null
    ) {
        try {
            dispatchChatMessageWithConfig(
                config = config,
                conversationId = conversationId,
                userMessage = userMessage,
                attachments = attachments,
                options = options,
                assistantVariantGroupId = assistantVariantGroupId,
                assistantVariantIndex = assistantVariantIndex,
                onToken = onToken,
                onThinkingToken = onThinkingToken,
                onStatusUpdate = onStatusUpdate,
                onKeyAttemptError = onKeyAttemptError,
                onResetBuffer = onResetBuffer,
                onComplete = onComplete
            )
        } catch (e: Exception) {
            if (isRequestCancellation(e)) throw CancellationException("请求已取消", e)
            val retryPolicy = personalizationManager.getRetryPolicy()
            // Empty responses use only their configured retry budget, never a second
            // hidden compression/retry round after that budget is exhausted.
            if (retryPolicy.enabled && isContextLimitError(e)) {
                if (personalizationManager.isContextFallbackPermanentlyIgnored(conversationId)) {
                    Log.i(tag, "会话 $conversationId 已永久忽略上下文回退，保全完整上下文")
                } else {
                    val decision = onContextFallbackPrompt?.invoke(e.message ?: "网络异常或空响应")
                        ?: ContextFallbackChoice.FALLBACK
                    when (decision) {
                        ContextFallbackChoice.FALLBACK -> {
                            if (retryWithCompressedContext(
                                    config = config,
                                    conversationId = conversationId,
                                    userMessage = userMessage,
                                    attachments = attachments,
                                    options = options,
                                    assistantVariantGroupId = assistantVariantGroupId,
                                    assistantVariantIndex = assistantVariantIndex,
                                    onToken = onToken,
                                    onThinkingToken = onThinkingToken,
                                    onKeyAttemptError = onKeyAttemptError,
                                    onResetBuffer = onResetBuffer,
                                    onComplete = onComplete,
                                    originalError = e
                                )
                            ) {
                                return
                            }
                        }
                        ContextFallbackChoice.IGNORE -> {
                            Log.i(tag, "用户选择忽略本次上下文回退，保持完整上下文不变")
                        }
                        ContextFallbackChoice.PERMANENTLY_IGNORE -> {
                            Log.i(tag, "用户选择永久忽略当前会话的上下文回退")
                            personalizationManager.setContextFallbackPermanentlyIgnored(conversationId, true)
                        }
                    }
                }
            }
            Log.e(tag, "发送消息失败", e)
            onError(e.message ?: "未知错误")

            // 记录失败统计
            try {
                val stat = ApiUsageStat(
                    apiConfigId = config.id,
                    provider = config.provider,
                    modelName = config.modelName,
                    success = false,
                    errorMessage = e.message
                )
                usageStatDao.insertStat(stat)
            } catch (statEx: Exception) {
                Log.e(tag, "记录失败统计异常", statEx)
            }
        }
    }

    private suspend fun dispatchChatMessageWithConfig(
        config: ApiConfig,
        conversationId: Long,
        userMessage: String,
        attachments: List<Attachment>,
        options: ChatRequestOptions?,
        assistantVariantGroupId: String?,
        assistantVariantIndex: Int,
        onToken: (String) -> Unit,
        onThinkingToken: (String) -> Unit,
        onStatusUpdate: ((String) -> Unit)? = null,
        onKeyAttemptError: ((keyIndex: Int, keyMasked: String, errorMsg: String) -> Unit)? = null,
        onResetBuffer: (() -> Unit)? = null,
        onComplete: (String, String?, Any?) -> Unit
    ) {
        if (!config.isEnabled) {
            throw Exception("当前 API 配置已关闭，请在设置中开启后重试")
        }
        val historyMessages = getMessagesList(conversationId)
        val allKeys = parseApiKeys(config.apiKey).let {
            if (personalizationManager.isBackupKeyFallbackEnabled()) it else it.take(1)
        }
        if (allKeys.isEmpty()) {
            throw Exception("当前 API 未配置已启用的 Key，请在 API 配置中开启至少一个 Key")
        }
        var lastException: Exception? = null
        var hasEmittedTokens = false
        val keyFailures = mutableListOf<KeyAttemptFailure>()
        val retryPolicy = personalizationManager.getRetryPolicy()

        val wrappedOnToken: (String) -> Unit = { token ->
            if (token.isNotEmpty()) hasEmittedTokens = true
            onToken(token)
        }
        val wrappedOnThinkingToken: (String) -> Unit = { token ->
            if (token.isNotEmpty()) hasEmittedTokens = true
            onThinkingToken(token)
        }

        for ((keyIndex, currentKey) in allKeys.withIndex()) {
            val keyConfig = config.copy(apiKey = currentKey)
            val keyMasked = maskApiKeyForDisplay(currentKey)
            var attempt = 0
            while (true) {
                val deadline = FirstTokenDeadline(firstTokenTimeoutMillis)
                try {
                    when (keyConfig.apiType) {
                        "anthropic" -> sendAnthropicMessage(
                            keyConfig, conversationId, historyMessages, userMessage, attachments,
                            options, assistantVariantGroupId, assistantVariantIndex,
                            { deadline.received(it); wrappedOnToken(it) }, { deadline.received(it); wrappedOnThinkingToken(it) }, onComplete, deadline
                        )
                        else -> sendOpenAIMessage(
                            keyConfig, conversationId, historyMessages, userMessage, attachments,
                            options, assistantVariantGroupId, assistantVariantIndex,
                            { deadline.received(it); wrappedOnToken(it) }, { deadline.received(it); wrappedOnThinkingToken(it) }, onComplete, deadline
                        )
                    }
                    return // 请求成功完成
                } catch (rawError: Exception) {
                    val e = deadline.failure(rawError)
                    deadline.close()
                    currentCoroutineContext().ensureActive()
                    if (isRequestCancellation(e)) throw e
                    lastException = e
                    if (e is com.aiassistant.data.remote.HttpAccessDeniedException) throw e

                    // 保护策略：如果已经收到部分内容，坚决不自动重试，避免向用户重复输出
                    if (hasEmittedTokens) {
                        Log.w(tag, "Key[$keyIndex] 已向用户输出部分内容，根据保护策略不再自动重试以避免重复输出: ${e.message}")
                        val cleanErrMsg = e.message?.trim()?.ifBlank { "连接中断" } ?: "连接中断"
                        onKeyAttemptError?.invoke(keyIndex + 1, keyMasked, cleanErrMsg)
                        throw e
                    }

                    // 只有在完全没有收到任何内容时才自动重试
                    val errorType = com.aiassistant.domain.model.RetryErrorType.classify(e)
                    val maxTimeoutAttempts = retryPolicy.rule(errorType).maxRetries
                    onKeyAttemptError?.invoke(keyIndex + 1, keyMasked, "第 ${attempt + 1} 次请求失败：${e.message ?: "未知异常"}")
                    if (retryPolicy.canRetry(e, attempt)) {
                        attempt++
                        val exMsg = e.message?.trim()?.take(180)?.ifBlank { null }
                        val delayMs = retryPolicy.delayMillis(attempt - 1)
                        val retryText = if (!exMsg.isNullOrBlank()) {
                            "${errorType.label} ($exMsg)，正在尝试重新连接 ($attempt/$maxTimeoutAttempts)..."
                        } else {
                            "${errorType.label}，正在尝试重新连接 ($attempt/$maxTimeoutAttempts)..."
                        }
                        Log.w(tag, "Key[$keyIndex] $retryText (退避等待 ${delayMs}ms) - 异常: ${e.javaClass.simpleName}: ${e.message}")
                        onStatusUpdate?.invoke(retryText)
                        kotlinx.coroutines.delay(delayMs)
                        continue
                    } else {
                        // 客户端参数/模型错误(400, 404, 422)或服务端错误(401, 403, 429, 500)
                        val cleanErrMsg = e.message?.trim()?.ifBlank { "未知异常" } ?: "未知异常"
                        val failure = KeyAttemptFailure(
                            keyIndex = keyIndex + 1,
                            keyMasked = keyMasked,
                            errorMessage = cleanErrMsg,
                            isTimeout = com.aiassistant.data.repository.helpers.NetworkExceptionClassifier.isTimeoutException(e)
                        )
                        keyFailures.add(failure)

                        if (keyIndex + 1 < allKeys.size) {
                            val nextIdx = keyIndex + 2
                            val failText = "当前 Key 异常 ($cleanErrMsg)，正在自动尝试备用 Key ($nextIdx/${allKeys.size})..."
                            Log.w(tag, "Key[$keyIndex] 请求报错: $cleanErrMsg，自动尝试备用 Key")
                            onStatusUpdate?.invoke(failText)
                            break
                        } else {
                            val failText = "Key[${keyIndex + 1}] 请求报错: $cleanErrMsg"
                            Log.w(tag, failText)
                            onStatusUpdate?.invoke(failText)
                            break
                        }
                    }
                } finally {
                    deadline.close()
                }
            }
        }

        // 所有 Key 都尝试失败：汇总所有 Key 的具体报错原因，完整展示
        if (keyFailures.isNotEmpty()) {
            if (allKeys.size > 1) {
                val compositeMessage = buildString {
                    append("所有 API Key 均请求失败 (共尝试 ${allKeys.size} 个 Key)：\n")
                    keyFailures.forEach { failure ->
                        append("• Key #${failure.keyIndex} (${failure.keyMasked})：${failure.errorMessage}\n")
                    }
                    append("\n建议检查 API 地址、网络连接或对应 Key 的额度与可用状态。")
                }.trim()
                throw Exception(compositeMessage)
            } else {
                throw Exception(keyFailures.first().errorMessage)
            }
        }

        throw lastException ?: Exception("所有 API Key 均连接失败或报错")
    }

    private fun maskApiKeyForDisplay(key: String): String {
        val trimmed = key.trim()
        return if (trimmed.length > 8) {
            "..." + trimmed.takeLast(4)
        } else {
            "尾号" + trimmed.takeLast(2)
        }
    }

    private suspend fun retryWithCompressedContext(
        config: ApiConfig,
        conversationId: Long,
        userMessage: String,
        attachments: List<Attachment>,
        options: ChatRequestOptions?,
        assistantVariantGroupId: String?,
        assistantVariantIndex: Int,
        onToken: (String) -> Unit,
        onThinkingToken: (String) -> Unit,
        onKeyAttemptError: ((keyIndex: Int, keyMasked: String, errorMsg: String) -> Unit)? = null,
        onResetBuffer: (() -> Unit)? = null,
        onComplete: (String, String?, Any?) -> Unit,
        originalError: Exception
    ): Boolean {
        val retryWindow = extractContextWindowFromError(originalError.message.orEmpty())
            ?: CONTEXT_OVERFLOW_RETRY_WINDOW_TOKENS
        val effectiveOptions = resolveChatRequestOptions(config, options)
        val requestModel = resolveRequestModel(config, effectiveOptions)

        // 核心改造：降级仅对当前对话生效，绝对不写入全局缓存与 SelectedModel，杜绝污染全局及其他会话
        runCatching {
            conversationDao.updateContextWindowTokens(conversationId, retryWindow, System.currentTimeMillis())
        }

        val safeRetryOutput = maxOf(options?.maxTokens ?: 4096, 4096).coerceIn(4096, 16384)

        runCatching {
            compressConversationContext(
                conversationId = conversationId,
                modelNameOverride = requestModel,
                maxOutputTokens = safeRetryOutput,
                contextWindowOverrideTokens = retryWindow
            )
        }.onFailure {
            Log.w(tag, "上下文超限后自动压缩失败，仍尝试缩小窗口重试", it)
        }

        val retryOptions = (options ?: ChatRequestOptions()).copy(
            contextWindowOverrideTokens = retryWindow,
            preparedDirectionContext = null,
            maxTokens = safeRetryOutput
        )
        return runCatching {
            dispatchChatMessageWithConfig(
                config = config,
                conversationId = conversationId,
                userMessage = userMessage,
                attachments = attachments,
                options = retryOptions,
                assistantVariantGroupId = assistantVariantGroupId,
                assistantVariantIndex = assistantVariantIndex,
                onToken = onToken,
                onThinkingToken = onThinkingToken,
                onKeyAttemptError = onKeyAttemptError,
                onResetBuffer = onResetBuffer,
                onComplete = onComplete
            )
        }.onFailure {
            Log.e(tag, "上下文压缩重试仍失败", it)
        }.isSuccess
    }

    // OpenAI格式发送消息
    private suspend fun sendOpenAIMessage(
        config: ApiConfig,
        conversationId: Long,
        historyMessages: List<Message>,
        userMessage: String,
        attachments: List<Attachment>,
        options: ChatRequestOptions? = null,
        assistantVariantGroupId: String? = null,
        assistantVariantIndex: Int = 1,
        onToken: (String) -> Unit,
        onThinkingToken: (String) -> Unit,
        onComplete: (String, String?, Any?) -> Unit,
        firstTokenDeadline: FirstTokenDeadline
    ) {
        val contextMessages = historyMessages
            .filter { msg ->
                assistantVariantGroupId.isNullOrBlank() || msg.variantGroupId != assistantVariantGroupId
            }
            .dropLastCurrentUserMessage(userMessage)
        val conversation = getConversationById(conversationId)
        val effectiveOptions = resolveChatRequestOptions(config, options)
        val requestModel = resolveRequestModel(config, effectiveOptions)
        val contextBundle = effectiveOptions.preparedDirectionContext?.let { ContextBundle(null, null, it.recentMessages) } ?: buildContextBundle(
            conversation = conversation,
            config = config,
            messages = contextMessages,
            modelName = requestModel,
            maxOutputTokens = effectiveOptions.maxTokens,
            contextWindowOverrideTokens = effectiveOptions.contextWindowOverrideTokens,
            currentUserMessage = userMessage,
            options = effectiveOptions
        )
        val enrichedResult = effectiveOptions.preparedDirectionContext?.let { EnrichedPromptResult(it.enrichedPrompt, it.toolCalls) }
            ?: enrichUserMessageWithWebSearch(userMessage, effectiveOptions)
        val enrichedUserMessage = directionUserPrompt(enrichedResult.enrichedPrompt, effectiveOptions)
        val toolCalls = enrichedResult.toolCalls
        val toolCallsJson = if (toolCalls.isNotEmpty()) gson.toJson(toolCalls) else null
        val chatMessages = mutableListOf<ChatMessage>()

        val isRoleplayConv = isRoleplayConversation(conversation)
        val promptResolution = resolveSystemPromptWithPriority(conversation, effectiveOptions, isRoleplayConv)

        val worldBookBlock = if (effectiveOptions.preparedDirectionContext == null && effectiveOptions.enableWorldBook == true && userMessage.isNotBlank()) {
            val bookIds = effectiveOptions.activeWorldBookIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
            val matchedEntries = matchWorldBookEntries(userMessage, bookIds)
            if (matchedEntries.isNotEmpty()) {
                "<world_book_lore>\n${formatWorldBookPrompt(matchedEntries)}\n</world_book_lore>"
            } else null
        } else null

        directionSystemPrompt(effectiveOptions.preparedDirectionContext?.systemPrompt ?: buildEffectiveSystemPrompt(
            customPrompt = promptResolution.first,
            olderSummary = contextBundle.summary,
            memoryBlock = contextBundle.memoryBlock,
            options = effectiveOptions,
            isConversationSpecific = promptResolution.second,
            isRoleplay = isRoleplayConv,
            worldBookBlock = worldBookBlock
        ), effectiveOptions)?.let {
            chatMessages.add(ChatMessage(role = "system", content = it))
        }

        contextBundle.recentMessages.forEach { msg ->
            if ((msg.role == "user" || msg.role == "assistant") && !isErrorPlaceholderMessage(msg.content)) {
                chatMessages.add(ChatMessage(role = msg.role, content = compactMessageForHistory(msg.content)))
            }
        }

        // 构建当前用户消息（支持多模态）：彻底废除将系统提示词伪装为 [System Override Directive] 注入用户消息的旧机制，
        // 彻底根除由此引发的 Gemini/OpenAI 提示注入/越狱安全检测拦截导致的 500 empty response
        val userContent = buildUserMessage(enrichedUserMessage, attachments)
        chatMessages.add(ChatMessage(role = "user", content = userContent))

        val normalizedChatMessages = normalizeChatMessagesRoleAlternation(chatMessages)

        // 创建请求 - OpenAI格式不发送top_k
        val searchIsReady = echoToolHub?.let {
            when (it.getSearchEngine()) {
                com.aiassistant.tools.search.SearchEngineType.EXA -> true
                com.aiassistant.tools.search.SearchEngineType.TAVILY -> tavilySearchManager.isReady()
                com.aiassistant.tools.search.SearchEngineType.MWMBL -> it.mwmblSearchEngine.isReady()
                com.aiassistant.tools.search.SearchEngineType.SEARXNG -> it.searxngSearchEngine.isReady()
            }
        } ?: tavilySearchManager.isReady()

        val providerToggles = buildOpenAiProviderToggles(
            config = config,
            options = effectiveOptions,
            allowNativeWebSearch = !searchIsReady
        )
        // 2026-09 厂商适配：按模型名识别 GPT/MiniMax/Kimi/DeepSeek/Gemini/GLM/MiMo，不再为 o 系列做专项分支
        val reasoningEvidence = ReasoningCapabilityCatalog.get(config.baseUrl, config.apiType, requestModel)
        val vendorPolicy = ReasoningControls.policy(requestModel, reasoningEvidence)
        val capability = com.aiassistant.domain.model.ModelCapabilityEngine.evaluateModel(requestModel)

        val rawConfiguredMax = effectiveOptions.maxTokens ?: config.maxTokens
        // 兼容历史遗留 50000 默认值：自动纠偏降级为 8192，杜绝上游网关报错
        val configuredMax = if (rawConfiguredMax == 50000) 8192 else (rawConfiguredMax.takeIf { it > 0 } ?: 8192)
        val isReasoningOrThinkingModel = capability.supportsThinking ||
            requestModel.contains("deepseek-v4", ignoreCase = true) ||
            requestModel.contains("deepseek-flash", ignoreCase = true) ||
            (effectiveOptions.enableThinking == true || config.enableThinking || vendorPolicy.alwaysThinking)
        val providerMaxOutput = vendorPolicy.maxOutputTokensCap.coerceIn(1_024, 1_048_576)
        // 输出Token安全预算：按厂商上限收敛，思考模型预留充足预算防截断
        val safeMaxTokens = when {
            vendorPolicy.usesMaxCompletionTokens -> maxOf(configuredMax, 8192)
                .coerceIn(4096, minOf(vendorPolicy.defaultMaxOutputTokens.coerceAtLeast(8192), providerMaxOutput))
            isReasoningOrThinkingModel -> maxOf(configuredMax, 8192)
                .coerceIn(8192, minOf(64_000, providerMaxOutput))
            else -> configuredMax.coerceIn(1024, minOf(32_768, providerMaxOutput))
        }

        val reasoningSelection = ReasoningControls.selected(requestModel, "openai", effectiveOptions.enableThinking ?: config.enableThinking, effectiveOptions.thinkingEffort, reasoningEvidence)
        val omitTemperature = vendorPolicy.temperaturePolicy == com.aiassistant.domain.model.TemperaturePolicy.FIXED_ONE ||
            (vendorPolicy.temperaturePolicy == com.aiassistant.domain.model.TemperaturePolicy.OMIT_WHEN_THINKING &&
                reasoningSelection.enabled)
        val omitSamplingExtras = vendorPolicy.omitsTopPAndPenalties || omitTemperature
        val mappedEffort = com.aiassistant.domain.model.ModelVendorProfiles
            .mapThinkingGear(effectiveOptions.thinkingEffort, vendorPolicy)

        val request = ChatCompletionRequest(
            model = requestModel,
            messages = normalizedChatMessages,
            temperature = if (omitTemperature) null else requestTemperature(config, effectiveOptions),
            max_tokens = if (capability.usesMaxCompletionTokens) null else safeMaxTokens,
            max_completion_tokens = if (capability.usesMaxCompletionTokens) safeMaxTokens else null,
            top_p = if (omitSamplingExtras) null else effectiveOptions.topP,
            top_k = if (providerToggles.includeTopK && !omitSamplingExtras) config.topK else null,
            stream = true,
            frequency_penalty = if (omitSamplingExtras) null else config.frequencyPenalty.takeIf { it != 0.0f },
            presence_penalty = if (omitSamplingExtras) null else config.presencePenalty.takeIf { it != 0.0f },
            stop = parseStopSequences(config.stopSequences),
            seed = config.seed,
            response_format = config.responseFormat?.let { ResponseFormat(it) },
            stream_options = StreamOptions(include_usage = true),
            web_search_options = if (providerToggles.includeOpenAiSearchOptions) WebSearchOptions(
                search_context_size = config.searchContextSize
            ) else null,
            enable_search = if (providerToggles.includeGenericSearch) true else null,
            web_search = if (providerToggles.includeGenericSearch) true else null,
            search_context_size = if (providerToggles.includeGenericSearch) config.searchContextSize else null,
            enable_thinking = if (providerToggles.includeEnableThinking && reasoningEvidence?.supportsThinking != false) reasoningSelection.enabled else null,
            thinking_budget = if (providerToggles.includeThinkingBudget) thinkingBudgetForEffort(effectiveOptions.thinkingEffort, config.thinkingBudget) else null,
            thinking_effort = if (providerToggles.includeThinkingEffort) mappedEffort else null,
            reasoning_effort = if (vendorPolicy.usesReasoningEffort) ReasoningControls.wireChatEffort(requestModel, "openai", reasoningSelection.enabled, reasoningSelection.value, reasoningEvidence) else null,
            thinking = ReasoningControls.wireThinkingType(requestModel, "openai", reasoningSelection.enabled, reasoningEvidence)?.let { AnthropicThinking(type = it) }
        )

        val auth = RetrofitClient.formatApiKey(config.apiKey)

        // 发送流式请求
        val startTime = System.currentTimeMillis()
        var activeCall: Call? = null
        try {
            val response = RetrofitClient.postJson(
                baseUrl = config.baseUrl,
                path = "chat/completions",
                headers = mapOf(
                    "Authorization" to auth,
                    "Accept" to "text/event-stream",
                    "Cache-Control" to "no-cache"
                ),
                json = gson.toJson(request),
                onCallCreated = { call ->
                    activeCall = call
                    activeStreamingCalls[conversationId] = call
                    firstTokenDeadline.start(call)
                }
            )

            response.use { okResponse ->
            if (okResponse.isSuccessful) {
                val responseBody = okResponse.body
                    ?: throw Exception("响应体为空")

                val contentBuilder = StringBuilder()
                val thinkingBuilder = StringBuilder()
                var isInThinkTag = false
                var totalTokens = 0
                var inputTokens = 0
                var outputTokens = 0
                var thinkingTokens = 0
                var cachedTokens = 0

                var hasReceivedDone = false
                var lastFinishReason: String? = null
                var streamReadException: Exception? = null
                val jsonAccumulator = StringBuilder()

                // 健壮读取 SSE / NDJSON 流：兼容 BOM、缺失[DONE]、缺失finish_reason、空行、注释及纯JSON
                try {
                    responseBody.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                        while (true) {
                            val nextLine = try {
                                reader.readLine()
                            } catch (ioEx: Exception) {
                                if (isRequestCancellation(ioEx)) throw ioEx
                                streamReadException = ioEx
                                Log.w(tag, "流式网络读取中断 (${ioEx.javaClass.simpleName}): ${ioEx.message}")
                                null
                            } ?: break

                            var lineStr = nextLine
                            if (lineStr.startsWith("\uFEFF")) {
                                lineStr = lineStr.removePrefix("\uFEFF")
                            }
                            lineStr = lineStr.trim()
                            if (lineStr.isEmpty() || lineStr.startsWith(":")) continue

                            // 兼容多行换行缩进的完整 JSON 返回（支持 data: {...} 与纯 NDJSON）
                            val rawDataPart = if (lineStr.startsWith("data:", ignoreCase = true)) lineStr.substring(5).trim() else lineStr
                            val isPartialJsonStart = (rawDataPart.startsWith("{") && !rawDataPart.endsWith("}"))
                            if (jsonAccumulator.isNotEmpty() || isPartialJsonStart) {
                                jsonAccumulator.append(rawDataPart).append("\n")
                                if (rawDataPart.endsWith("}") || rawDataPart == "}") {
                                    lineStr = jsonAccumulator.toString().trim()
                                    jsonAccumulator.clear()
                                } else {
                                    continue
                                }
                            }

                            val chunkResult = parseOpenAiStreamLine(lineStr, gson) ?: continue

                            if (chunkResult.isDone) {
                                hasReceivedDone = true
                                break
                            }

                            // 检测流式返回的内联错误
                            if (!chunkResult.inlineErrorMessage.isNullOrBlank()) {
                                if (contentBuilder.isNotEmpty() || thinkingBuilder.isNotEmpty()) {
                                    Log.w(tag, "流式接收过程中遇到内联错误: ${chunkResult.inlineErrorMessage}，但已接收到部分内容，保留已收到内容平稳完成")
                                    break
                                } else {
                                    throw ApiException(400, "API流式返回错误: ${chunkResult.inlineErrorMessage}")
                                }
                            }

                            if (!chunkResult.finishReason.isNullOrBlank()) {
                                lastFinishReason = chunkResult.finishReason
                            }

                            chunkResult.thinkingDelta?.let { thinking ->
                                thinkingBuilder.append(thinking)
                                onThinkingToken(thinking)
                            }

                            chunkResult.contentDelta?.let { content ->
                                if (isInThinkTag) {
                                    if (content.contains("</think>")) {
                                        val parts = content.split("</think>", limit = 2)
                                        val inside = parts[0]
                                        val after = parts.getOrNull(1).orEmpty()
                                        if (inside.isNotEmpty()) {
                                            thinkingBuilder.append(inside)
                                            onThinkingToken(inside)
                                        }
                                        isInThinkTag = false
                                        if (after.isNotEmpty()) {
                                            contentBuilder.append(after)
                                            onToken(after)
                                        }
                                    } else {
                                        thinkingBuilder.append(content)
                                        onThinkingToken(content)
                                    }
                                } else if (content.contains("<think>")) {
                                    val parts = content.split("<think>", limit = 2)
                                    val before = parts[0]
                                    val insideAndAfter = parts.getOrNull(1).orEmpty()
                                    if (before.isNotEmpty()) {
                                        contentBuilder.append(before)
                                        onToken(before)
                                    }
                                    if (insideAndAfter.contains("</think>")) {
                                        val subParts = insideAndAfter.split("</think>", limit = 2)
                                        val inside = subParts[0]
                                        val after = subParts.getOrNull(1).orEmpty()
                                        if (inside.isNotEmpty()) {
                                            thinkingBuilder.append(inside)
                                            onThinkingToken(inside)
                                        }
                                        isInThinkTag = false
                                        if (after.isNotEmpty()) {
                                            contentBuilder.append(after)
                                            onToken(after)
                                        }
                                    } else {
                                        isInThinkTag = true
                                        if (insideAndAfter.isNotEmpty()) {
                                            thinkingBuilder.append(insideAndAfter)
                                            onThinkingToken(insideAndAfter)
                                        }
                                    }
                                } else {
                                    contentBuilder.append(content)
                                    onToken(content)
                                }
                            }

                            chunkResult.usage?.let { usage ->
                                inputTokens = usage.prompt_tokens ?: inputTokens
                                val rawCompletionTokens = usage.completion_tokens ?: outputTokens
                                thinkingTokens = usage.completion_tokens_details?.reasoning_tokens ?: thinkingTokens
                                outputTokens = (rawCompletionTokens - thinkingTokens).coerceAtLeast(0)
                                val parsedCached = extractCachedTokensFromUsage(usage)
                                if (parsedCached > 0) {
                                    cachedTokens = parsedCached
                                }
                                totalTokens = usage.total_tokens ?: (inputTokens + outputTokens + thinkingTokens)
                            }
                        }
                    }
                } catch (streamEx: Exception) {
                    if (isRequestCancellation(streamEx)) throw streamEx
                    streamReadException = streamEx
                    Log.w(tag, "流式解析外层异常: ${streamEx.javaClass.simpleName}: ${streamEx.message}")
                }

                val responseTime = System.currentTimeMillis() - startTime
                var fullContent = contentBuilder.toString()
                var fullThinking = thinkingBuilder.toString().ifEmpty { null }

                // 兜底提取：若思考内容未被流式单独捕获但文本中带有 <think> 标签，将其分离并持久化到 thinkingContent
                if (fullThinking == null && fullContent.contains("<think>", ignoreCase = true)) {
                    val thinkRegex = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.IGNORE_CASE)
                    val match = thinkRegex.find(fullContent)
                    if (match != null) {
                        val extracted = match.groupValues[1].trim()
                        if (extracted.isNotEmpty()) {
                            fullThinking = extracted
                        }
                        fullContent = fullContent.replace(match.value, "").trim()
                    }
                }

                // 需求 5：若模型完成了深度思考但未输出正文内容（如 Token 耗尽或提前中断），提供明确友好说明，避免界面空白或抛出 500 异常
                if (fullContent.isBlank() && !fullThinking.isNullOrBlank()) {
                    val fallbackMsg = "*(思考已完成，但模型未输出正文内容，可能由于输出 Token 达到上限或被提前截断)*"
                    fullContent = fallbackMsg
                    onToken(fallbackMsg)
                }

                val hasReceivedContent = fullContent.isNotBlank() || !fullThinking.isNullOrBlank()

                // 需求 2：只在“完全没有收到任何 delta.content”（且无思考、无工具调用）时，才判定为失败
                if (!hasReceivedContent && toolCalls.isEmpty()) {
                    streamReadException?.let { throw it }
                    throw ApiException(500, "模型回复内容为空 (empty response detected)，未收到任何有效的文本或思考内容")
                }

                // 检测是否由于达到最大 Token 上限被截断（向用户输出明确友好说明与引导）
                val isTruncatedByLength = lastFinishReason == "length" || lastFinishReason == "max_tokens"
                if (isTruncatedByLength && fullContent.isNotBlank() && !fullContent.contains("已达单次最大 Token")) {
                    val truncationHint = "\n\n*(已达到单次最大 Token 输出上限并截断，可在输入框输入“继续”或在设置中调大单次最大 Token)*"
                    fullContent += truncationHint
                    onToken(truncationHint)
                }

                // 检测是否流式网络异常中断（非正常结束，向用户明确展示中断信息并支持重试）
                val currentStreamEx = streamReadException
                if (currentStreamEx != null && !hasReceivedDone && (lastFinishReason.isNullOrBlank() || lastFinishReason == "null")) {
                    val interruptMsg = currentStreamEx.message?.take(80)?.ifBlank { "网络连接断开" } ?: "网络连接断开"
                    val interruptNotice = "\n\n[输出已被中断: 网络连接异常断开 ($interruptMsg)]"
                    fullContent += interruptNotice
                    onToken(interruptNotice)
                }

                // 需求 3：如果流结束时缺少 finish_reason 或 [DONE]，不要抛异常，只记录 warning 并给结果标记 finished: false
                val isFinished = (hasReceivedDone || !lastFinishReason.isNullOrBlank()) && streamReadException == null
                if (!isFinished) {
                    Log.w(tag, "流式响应结束但缺少 finish_reason 或 [DONE] (finished: false, finishReason: $lastFinishReason, hasDone: $hasReceivedDone, streamException: ${streamReadException?.message}), 内容正常保留输出 (${fullContent.length} 字符)")
                }

                // v2.6.4 真实性：API 已给出 reasoning_tokens 时 output 已扣除思考量直接使用；
                // 否则思考量为估算值，需从 completion_tokens（含思考文本）中扣除，避免总量双重计入
                val apiReportedThinking = thinkingTokens > 0
                val finalThinkingTokens = if (apiReportedThinking) thinkingTokens else estimateTokenCount(fullThinking.orEmpty())
                val finalOutputTokens = when {
                    outputTokens <= 0 -> estimateTokenCount(fullContent)
                    apiReportedThinking -> outputTokens
                    else -> (outputTokens - finalThinkingTokens).coerceAtLeast(0)
                }
                val finalInputTokens = inputTokens.takeIf { it > 0 }
                    ?: chatMessages.sumOf { estimateTokenCount(it.content.toString()) }
                val finalTotalTokens = totalTokens.takeIf { it > 0 }
                    ?: (finalInputTokens + finalOutputTokens + finalThinkingTokens)

                // 保存助手消息
                val assistantMsg = Message(
                    conversationId = conversationId,
                    role = "assistant",
                    content = fullContent,
                    thinkingContent = fullThinking,
                    variantGroupId = assistantVariantGroupId,
                    variantIndex = assistantVariantIndex,
                    tokenCount = finalTotalTokens,
                    thinkingTokens = finalThinkingTokens,
                    responseTime = responseTime,
                    toolCalls = toolCallsJson,
                    modelName = requestModel,
                    replyDirection = effectiveOptions.replyDirection
                )
                if (effectiveOptions.directionPlanningCount == null) saveMessage(assistantMsg)

                // 记录使用统计
                val stat = ApiUsageStat(
                    apiConfigId = config.id,
                    provider = config.provider,
                    modelName = requestModel,
                    inputTokens = finalInputTokens,
                    outputTokens = finalOutputTokens,
                    thinkingTokens = finalThinkingTokens,
                    totalTokens = finalTotalTokens,
                    cachedTokens = cachedTokens,
                    responseTime = responseTime,
                    success = true
                )
                try {
                    usageStatDao.insertStat(stat)
                } catch (statEx: Exception) {
                    Log.e(tag, "记录成功统计异常", statEx)
                }

                onComplete(fullContent, fullThinking, toolCalls)
            } else {
                val errorBody = okResponse.body?.string() ?: "未知错误"
                val errorMsg = parseApiErrorMessage(errorBody)
                val statusCode = okResponse.code
                throw ApiException(statusCode, "API错误 ($statusCode): $errorMsg", errorBody)
            }
        }
        } finally {
            activeCall?.let { activeStreamingCalls.remove(conversationId, it) }
        }
    }

    // Anthropic格式发送消息
    private suspend fun sendAnthropicMessage(
        config: ApiConfig,
        conversationId: Long,
        historyMessages: List<Message>,
        userMessage: String,
        attachments: List<Attachment>,
        options: ChatRequestOptions? = null,
        assistantVariantGroupId: String? = null,
        assistantVariantIndex: Int = 1,
        onToken: (String) -> Unit,
        onThinkingToken: (String) -> Unit,
        onComplete: (String, String?, Any?) -> Unit,
        firstTokenDeadline: FirstTokenDeadline
    ) {
        val contextMessages = historyMessages
            .filter { msg ->
                assistantVariantGroupId.isNullOrBlank() || msg.variantGroupId != assistantVariantGroupId
            }
            .dropLastCurrentUserMessage(userMessage)

        // 获取系统提示
        val conversation = getConversationById(conversationId)
        val effectiveOptions = resolveChatRequestOptions(config, options)
        val requestModel = resolveRequestModel(config, effectiveOptions)
        val contextBundle = effectiveOptions.preparedDirectionContext?.let { ContextBundle(null, null, it.recentMessages) } ?: buildContextBundle(
            conversation = conversation,
            config = config,
            messages = contextMessages,
            modelName = requestModel,
            maxOutputTokens = effectiveOptions.maxTokens,
            contextWindowOverrideTokens = effectiveOptions.contextWindowOverrideTokens,
            currentUserMessage = userMessage,
            options = effectiveOptions
        )
        val isRoleplayConv = isRoleplayConversation(conversation)
        val promptResolution = resolveSystemPromptWithPriority(conversation, effectiveOptions, isRoleplayConv)

        val worldBookBlock = if (effectiveOptions.preparedDirectionContext == null && effectiveOptions.enableWorldBook == true && userMessage.isNotBlank()) {
            val bookIds = effectiveOptions.activeWorldBookIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
            val matchedEntries = matchWorldBookEntries(userMessage, bookIds)
            if (matchedEntries.isNotEmpty()) {
                "<world_book_lore>\n${formatWorldBookPrompt(matchedEntries)}\n</world_book_lore>"
            } else null
        } else null

        val systemPrompt = directionSystemPrompt(effectiveOptions.preparedDirectionContext?.systemPrompt ?: buildEffectiveSystemPrompt(
            customPrompt = promptResolution.first,
            olderSummary = contextBundle.summary,
            memoryBlock = contextBundle.memoryBlock,
            options = effectiveOptions,
            isConversationSpecific = promptResolution.second,
            isRoleplay = isRoleplayConv,
            worldBookBlock = worldBookBlock
        ), effectiveOptions)
        val anthropicMessages = mutableListOf<AnthropicMessage>()

        // 添加历史消息
        contextBundle.recentMessages.dropWhile { it.role == "assistant" }.forEach { msg ->
            if (!isErrorPlaceholderMessage(msg.content)) {
                when (msg.role) {
                    "user" -> addAnthropicHistoryMessage(anthropicMessages, "user", compactMessageForHistory(msg.content))
                    "assistant" -> addAnthropicHistoryMessage(anthropicMessages, "assistant", compactMessageForHistory(msg.content))
                }
            }
        }

        // 构建当前用户消息（支持多模态）
        val enrichedResult = effectiveOptions.preparedDirectionContext?.let { EnrichedPromptResult(it.enrichedPrompt, it.toolCalls) }
            ?: enrichUserMessageWithWebSearch(userMessage, effectiveOptions)
        val enrichedUserMessage = directionUserPrompt(enrichedResult.enrichedPrompt, effectiveOptions)
        val toolCalls = enrichedResult.toolCalls
        val toolCallsJson = if (toolCalls.isNotEmpty()) gson.toJson(toolCalls) else null
        val userContent = buildAnthropicUserMessage(enrichedUserMessage, attachments)
        if (userContent is String) {
            addAnthropicHistoryMessage(anthropicMessages, "user", userContent)
        } else {
            anthropicMessages.add(AnthropicMessage(role = "user", content = userContent))
        }

        val reasoningEvidence = ReasoningCapabilityCatalog.get(config.baseUrl, config.apiType, requestModel)
        val reasoningPolicy = ReasoningControls.policy(requestModel, reasoningEvidence)
        val thinkingEnabled = reasoningPolicy.alwaysThinking || (effectiveOptions.enableThinking ?: config.enableThinking)
        val reasoningSelection = ReasoningControls.selected(requestModel, "anthropic", thinkingEnabled, effectiveOptions.thinkingEffort, reasoningEvidence)
        val thinkingType = ReasoningControls.wireThinkingType(requestModel, "anthropic", thinkingEnabled, reasoningEvidence)
        val adaptive = thinkingType == "adaptive"
        val thinkingBudget = if (thinkingEnabled && thinkingType == "enabled") {
            thinkingBudgetForEffort(reasoningSelection.value, config.thinkingBudget).coerceAtMost(60000)
        } else null
        val rawConfiguredMaxTokens = effectiveOptions.maxTokens ?: config.maxTokens
        val configuredMaxTokens = if (rawConfiguredMaxTokens == 50000) 4096 else rawConfiguredMaxTokens
        // Anthropic 协议要求 max_tokens 必须大于 budget_tokens，且保留充裕正文额度
        val safeRequestMaxTokens = maxOf(configuredMaxTokens, (thinkingBudget ?: 0) + 4096).coerceIn(4096, 64000)

        // 创建请求 - Anthropic格式支持top_k
        val request = AnthropicRequest(
            model = requestModel,
            messages = anthropicMessages,
            max_tokens = safeRequestMaxTokens,
            system = systemPrompt,
            temperature = if (adaptive || thinkingEnabled) null else requestTemperature(config, effectiveOptions),
            top_p = if (adaptive || thinkingEnabled) null else effectiveOptions.topP,
            top_k = if (!adaptive && !thinkingEnabled && config.topK != 50) config.topK else null,
            stream = true,
            stop_sequences = parseStopSequences(config.stopSequences),
            output_config = ReasoningControls.wireChatEffort(requestModel, "anthropic", thinkingEnabled, effectiveOptions.thinkingEffort, reasoningEvidence)?.let { AnthropicOutputConfig(it) },
            thinking = thinkingType?.let { AnthropicThinking(type = it, budget_tokens = thinkingBudget) }
        )

        // 发送流式请求
        val startTime = System.currentTimeMillis()
        var activeCall: Call? = null
        try {
            val response = RetrofitClient.postJson(
                baseUrl = config.baseUrl,
                path = "messages",
                headers = mapOf(
                    "x-api-key" to config.apiKey,
                    "anthropic-version" to "2023-06-01",
                    "Accept" to "text/event-stream",
                    "Cache-Control" to "no-cache"
                ),
                json = gson.toJson(request),
                onCallCreated = { call ->
                    activeCall = call
                    activeStreamingCalls[conversationId] = call
                    firstTokenDeadline.start(call)
                }
            )

            response.use { okResponse ->
            if (okResponse.isSuccessful) {
                val responseBody = okResponse.body
                    ?: throw Exception("响应体为空")

                val contentBuilder = StringBuilder()
                val thinkingBuilder = StringBuilder()
                var totalTokens = 0
                var inputTokens = 0
                var outputTokens = 0
                var cachedTokens = 0

                var anthropicStreamReadException: Exception? = null

                // 读取SSE流
                try {
                    responseBody.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                        while (true) {
                            val nextLine = try {
                                reader.readLine()
                            } catch (ioEx: Exception) {
                                if (isRequestCancellation(ioEx)) throw ioEx
                                anthropicStreamReadException = ioEx
                                Log.w(tag, "Anthropic流式读取遇到IO中断 (${ioEx.javaClass.simpleName}): ${ioEx.message}")
                                null
                            } ?: break

                            var lineStr = nextLine
                            if (lineStr.startsWith("\uFEFF")) {
                                lineStr = lineStr.removePrefix("\uFEFF")
                            }
                            lineStr = lineStr.trim()
                            if (lineStr.isEmpty() || lineStr.startsWith(":") || lineStr.startsWith("event: ")) continue

                            if (lineStr.startsWith("data:")) {
                                val data = lineStr.removePrefix("data:").trimStart()
                                if (data == "[DONE]") break

                                try {
                                    val event = gson.fromJson(data, AnthropicStreamEvent::class.java)

                                    when (event.type) {
                                        "message_start" -> {
                                            event.message?.usage?.let { usage ->
                                                // v2.6.4 真实性：Anthropic 的 input_tokens 不含缓存读写部分，
                                                // 与 OpenAI（prompt_tokens 含缓存）对齐，将缓存读/写并入输入口径，
                                                // 修复缓存命中率可能超过 100% 与总量偏低的问题；命中量仅计 cache_read
                                                inputTokens = (usage.input_tokens ?: 0) +
                                                    (usage.cache_read_input_tokens ?: 0) +
                                                    (usage.cache_creation_input_tokens ?: 0)
                                                cachedTokens = usage.cache_read_input_tokens ?: 0
                                            }
                                        }
                                        "content_block_delta" -> {
                                            // 处理文本内容
                                            event.delta?.text?.let { text ->
                                                contentBuilder.append(text)
                                                onToken(text)
                                            }
                                            // 处理思考内容
                                            event.delta?.thinking?.let { thinking ->
                                                thinkingBuilder.append(thinking)
                                                onThinkingToken(thinking)
                                            }
                                        }
                                        "message_delta" -> {
                                            event.usage?.let { usage ->
                                                // 与 message_start 同口径：输入含缓存读/写，命中量仅计 cache_read
                                                val recomputedInput = (usage.input_tokens ?: 0) +
                                                    (usage.cache_read_input_tokens ?: 0) +
                                                    (usage.cache_creation_input_tokens ?: 0)
                                                if (recomputedInput > 0) {
                                                    inputTokens = recomputedInput
                                                }
                                                outputTokens = usage.output_tokens ?: outputTokens
                                                cachedTokens = usage.cache_read_input_tokens ?: cachedTokens
                                                totalTokens = inputTokens + outputTokens
                                            }
                                        }
                                        "error" -> {
                                            val errDetail = event.error?.message ?: "Anthropic流式返回未知错误"
                                            if (contentBuilder.isNotEmpty() || thinkingBuilder.isNotEmpty()) {
                                                Log.w(tag, "Anthropic流式输出过程中遇到错误: $errDetail，保留已输出内容平稳完成")
                                                break
                                            } else {
                                                throw ApiException(400, "Anthropic错误: $errDetail", data)
                                            }
                                        }
                                    }
                                } catch (e: ApiException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.w(tag, "解析Anthropic chunk失败: $data", e)
                                }
                            }
                        }
                    }
                } catch (streamEx: Exception) {
                    if (isRequestCancellation(streamEx)) throw streamEx
                    anthropicStreamReadException = streamEx
                    Log.w(tag, "Anthropic流式读取外层异常: ${streamEx.javaClass.simpleName}: ${streamEx.message}")
                }

                val responseTime = System.currentTimeMillis() - startTime
                var fullContent = contentBuilder.toString()
                var fullThinking = thinkingBuilder.toString().ifEmpty { null }

                if (fullThinking == null && fullContent.contains("<think>", ignoreCase = true)) {
                    val thinkRegex = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.IGNORE_CASE)
                    val match = thinkRegex.find(fullContent)
                    if (match != null) {
                        val extracted = match.groupValues[1].trim()
                        if (extracted.isNotEmpty()) {
                            fullThinking = extracted
                        }
                        fullContent = fullContent.replace(match.value, "").trim()
                    }
                }

                // 需求 5：若模型完成了深度思考但未输出正文内容，提供明确友好说明
                if (fullContent.isBlank() && !fullThinking.isNullOrBlank()) {
                    val fallbackMsg = "*(思考已完成，但模型未输出正文内容，可能由于输出 Token 达到上限或被提前截断)*"
                    fullContent = fallbackMsg
                    onToken(fallbackMsg)
                }

                // 检测是否流式网络异常中断
                val currentAnthropicEx = anthropicStreamReadException
                if (currentAnthropicEx != null && fullContent.isNotBlank() && !fullContent.contains("[输出已被中断")) {
                    val interruptMsg = currentAnthropicEx.message?.take(80)?.ifBlank { "网络连接断开" } ?: "网络连接断开"
                    val interruptNotice = "\n\n[输出已被中断: 网络连接异常断开 ($interruptMsg)]"
                    fullContent += interruptNotice
                    onToken(interruptNotice)
                }

                val hasReceivedContent = fullContent.isNotBlank() || !fullThinking.isNullOrBlank()

                if (!hasReceivedContent && toolCalls.isEmpty()) {
                    anthropicStreamReadException?.let { throw it }
                    throw ApiException(500, "Anthropic 模型回复内容为空 (empty response detected)，可能触发限制或API Key异常")
                }
                // v2.6.4 真实性：Anthropic 的 output_tokens 已包含 thinking，而思考量此处为估算值——
                // 从输出中扣除估算思考量，避免「输出 + 思考」双重计入总量；total 保持 API 真实值
                val estimatedThinkingTokens = estimateTokenCount(fullThinking.orEmpty())
                val finalThinkingTokens = estimatedThinkingTokens
                val finalOutputTokens = when {
                    outputTokens <= 0 -> estimateTokenCount(fullContent)
                    estimatedThinkingTokens > 0 -> (outputTokens - estimatedThinkingTokens).coerceAtLeast(0)
                    else -> outputTokens
                }
                val finalInputTokens = inputTokens.takeIf { it > 0 }
                    ?: anthropicMessages.sumOf { estimateTokenCount(it.content.toString()) }
                val finalTotalTokens = totalTokens.takeIf { it > 0 }
                    ?: (finalInputTokens + finalOutputTokens + finalThinkingTokens)

                // 保存助手消息
                val assistantMsg = Message(
                    conversationId = conversationId,
                    role = "assistant",
                    content = fullContent,
                    thinkingContent = fullThinking,
                    variantGroupId = assistantVariantGroupId,
                    variantIndex = assistantVariantIndex,
                    tokenCount = finalTotalTokens,
                    thinkingTokens = finalThinkingTokens,
                    responseTime = responseTime,
                    toolCalls = toolCallsJson,
                    modelName = requestModel,
                    replyDirection = effectiveOptions.replyDirection
                )
                if (effectiveOptions.directionPlanningCount == null) saveMessage(assistantMsg)

                // 记录使用统计
                val stat = ApiUsageStat(
                    apiConfigId = config.id,
                    provider = config.provider,
                    modelName = requestModel,
                    inputTokens = finalInputTokens,
                    outputTokens = finalOutputTokens,
                    thinkingTokens = finalThinkingTokens,
                    totalTokens = finalTotalTokens,
                    cachedTokens = cachedTokens,
                    responseTime = responseTime,
                    success = true
                )
                try {
                    usageStatDao.insertStat(stat)
                } catch (statEx: Exception) {
                    Log.e(tag, "记录成功统计异常", statEx)
                }

                onComplete(fullContent, fullThinking, toolCalls)
            } else {
                val errorBody = okResponse.body?.string() ?: "未知错误"
                val errorMsg = parseApiErrorMessage(errorBody)
                val statusCode = okResponse.code
                throw ApiException(statusCode, "API错误 ($statusCode): $errorMsg", errorBody)
            }
        }
        } finally {
            activeCall?.let { activeStreamingCalls.remove(conversationId, it) }
        }
    }

    private data class OpenAiProviderToggles(
        val includeTopK: Boolean,
        val includeGenericSearch: Boolean,
        val includeOpenAiSearchOptions: Boolean,
        val includeEnableThinking: Boolean,
        val includeThinkingBudget: Boolean,
        val includeThinkingEffort: Boolean,
        val includeReasoningEffort: Boolean
    )

    private fun resolveChatRequestOptions(
        config: ApiConfig,
        overrides: ChatRequestOptions?
    ): ChatRequestOptions {
        return ChatRequestOptions(
            temperature = (overrides?.temperature ?: config.temperature).coerceIn(0f, temperatureMaxForConfig(config)),
            maxTokens = overrides?.maxTokens ?: config.maxTokens,
            topP = (overrides?.topP ?: config.topP).coerceIn(0f, 1f),
            enableThinking = overrides?.enableThinking ?: config.enableThinking,
            thinkingEffort = normalizeThinkingEffort(overrides?.thinkingEffort ?: config.thinkingEffort, config),
            enableWebSearch = overrides?.enableWebSearch ?: config.enableWebSearch,
            enableSessionMemory = overrides?.enableSessionMemory,
            enableExternalMemory = overrides?.enableExternalMemory,
            enableWorldBook = overrides?.enableWorldBook,
            activeWorldBookIds = overrides?.activeWorldBookIds,
            overrideSystemPrompt = overrides?.overrideSystemPrompt == true,
            systemPromptOverride = overrides?.systemPromptOverride,
            contextWindowOverrideTokens = overrides?.contextWindowOverrideTokens,
            preparedDirectionContext = overrides?.preparedDirectionContext,
            directionPlanningCount = overrides?.directionPlanningCount,
            replyDirection = overrides?.replyDirection
        )
    }

    fun resolveSystemPromptWithPriority(
        conversation: Conversation?,
        options: ChatRequestOptions,
        isRoleplay: Boolean = isRoleplayConversation(conversation)
    ): Pair<String?, Boolean> {
        if (options.overrideSystemPrompt && !options.systemPromptOverride.isNullOrBlank()) {
            return Pair(options.systemPromptOverride, true)
        }
        if (isRoleplay) {
            // 角色扮演拥有完全独立的上下文，不读取普通全局提示词
            return Pair(conversation?.systemPrompt, true)
        }
        val convPrompt = conversation?.systemPrompt?.trim()
        if (!convPrompt.isNullOrBlank()) {
            // 对话本身有系统提示词 -> 全局提示词 100% 0作用
            return Pair(convPrompt, true)
        }
        // 对话本身无系统提示词 -> 自动继承使用全局提示词作为兜底
        val globalPrompt = personalizationManager.getSettings().globalSystemPrompt.trim()
        return if (globalPrompt.isNotBlank()) {
            Pair(globalPrompt, false)
        } else {
            Pair(null, false)
        }
    }

    private fun effectiveSystemPrompt(
        conversation: Conversation?,
        options: ChatRequestOptions
    ): String? {
        return resolveSystemPromptWithPriority(conversation, options).first
    }

    private fun normalizeThinkingEffort(effort: String?, config: ApiConfig): String {
        val evidence = ReasoningCapabilityCatalog.get(config.baseUrl, config.apiType, config.modelName)
        return ReasoningControls.selected(config.modelName, config.apiType, true, effort, evidence).value
    }

    private fun thinkingBudgetForEffort(effort: String?, configuredBudget: Int): Int {
        effort?.takeIf { it.startsWith("budget:") }?.removePrefix("budget:")?.toIntOrNull()?.let { return it.coerceIn(1024, 60000) }
        val base = configuredBudget.coerceIn(1024, 32768)
        return when (effort?.lowercase()) {
            "low" -> (base / 2).coerceIn(1024, 32768)
            "max", "ultra" -> 32768
            "high" -> (base * 2).coerceIn(1024, 32768)
            else -> base
        }
    }

    private fun isDeepSeekConfig(config: ApiConfig): Boolean {
        val identity = listOf(config.provider, config.baseUrl, config.modelName).joinToString(" ").lowercase()
        return "deepseek" in identity
    }

    private fun isMiMoConfig(config: ApiConfig): Boolean {
        val identity = listOf(config.provider, config.baseUrl, config.modelName).joinToString(" ").lowercase()
        return "mimo" in identity || "xiaomi" in identity
    }

    private fun temperatureMaxForConfig(config: ApiConfig): Float {
        return if (isDeepSeekConfig(config)) 2f else 1f
    }

    private fun requestTemperature(config: ApiConfig, options: ChatRequestOptions): Float? {
        val identity = listOf(config.provider, config.baseUrl, config.modelName).joinToString(" ").lowercase()
        val policy = ReasoningControls.policy(config.modelName, ReasoningCapabilityCatalog.get(config.baseUrl, config.apiType, config.modelName))
        val isAnthropic = config.apiType == "anthropic" || policy.vendor == com.aiassistant.domain.model.ModelVendor.CLAUDE ||
            config.provider.equals("anthropic", ignoreCase = true) || "anthropic" in identity || "claude" in identity

        return when (policy.temperaturePolicy) {
            // Kimi K3 / GLM-5.3：官方固定 temperature=1.0，不建议显式传入
            com.aiassistant.domain.model.TemperaturePolicy.FIXED_ONE -> null
            // 现代 OpenAI 推理风格：思考时省略 temperature
            com.aiassistant.domain.model.TemperaturePolicy.OMIT_WHEN_THINKING ->
                if (options.enableThinking == true || policy.alwaysThinking) null
                else options.temperature?.coerceIn(0f, temperatureMaxForConfig(config))
            // Anthropic：思考开启时强制 1.0
            com.aiassistant.domain.model.TemperaturePolicy.FORCE_ONE_WHEN_THINKING ->
                if (options.enableThinking == true || isAnthropic) 1.0f
                else options.temperature?.coerceIn(0f, temperatureMaxForConfig(config))
            com.aiassistant.domain.model.TemperaturePolicy.ALLOWED ->
                if (options.enableThinking == true && !isAnthropic) null
                else if (isAnthropic && options.enableThinking == true) 1.0f
                else options.temperature?.coerceIn(0f, temperatureMaxForConfig(config))
        }
    }

    private fun resolveRequestModel(config: ApiConfig, options: ChatRequestOptions): String {
        return config.modelName
    }

    fun parseApiErrorMessage(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "未知错误"
        return runCatching {
            val json = JsonParser.parseString(errorBody).asJsonObject
            val rawMsg = when {
                json.has("error") -> {
                    val errorElem = json.get("error")
                    when {
                        errorElem.isJsonObject -> {
                            val errObj = errorElem.asJsonObject
                            errObj.get("message")?.asString
                                ?: errObj.get("msg")?.asString
                                ?: errObj.toString()
                        }
                        errorElem.isJsonPrimitive -> errorElem.asString
                        else -> errorElem.toString()
                    }
                }
                json.has("message") -> json.get("message").asString
                json.has("detail") -> {
                    val detail = json.get("detail")
                    if (detail.isJsonPrimitive) detail.asString else detail.toString()
                }
                json.has("msg") -> json.get("msg").asString
                else -> errorBody
            }
            if (rawMsg.contains("empty response detected", ignoreCase = true)) {
                "$rawMsg (上游模型未返回有效流式内容，通常由敏感词拦截、思考预算耗尽截断或网关格式校验引起)"
            } else {
                rawMsg
            }
        }.getOrDefault(errorBody)
    }

    private fun buildOpenAiProviderToggles(
        config: ApiConfig,
        options: ChatRequestOptions?,
        allowNativeWebSearch: Boolean = true
    ): OpenAiProviderToggles {
        val identity = listOf(config.provider, config.baseUrl, config.modelName)
            .joinToString(" ")
            .lowercase()
        val wantsSearch = options?.enableWebSearch == true && allowNativeWebSearch
        val isDeepSeek = "deepseek" in identity
        val isMiMo = "mimo" in identity || "xiaomi" in identity
        val isOpenAi = "openai" in identity || "api.openai.com" in identity
        val isSiliconFlow = "siliconflow" in identity

        return OpenAiProviderToggles(
            includeTopK = options != null && isMiMo,
            includeGenericSearch = wantsSearch && !isDeepSeek && !isOpenAi,
            includeOpenAiSearchOptions = wantsSearch && isOpenAi,
            // 绝不盲目发送非标准属性 enable_thinking / thinking_budget，避免第三方中转网关解析异常与 1024 Token 截断导致的空回复 (empty response detected)
            includeEnableThinking = options?.enableThinking != null && isSiliconFlow,
            includeThinkingBudget = false,
            includeThinkingEffort = false,
            // 标准 OpenAI 与主流中转网关协议统一使用 reasoning_effort 控制思考强度
            includeReasoningEffort = options?.enableThinking == true
        )
    }

    private data class ContextBundle(
        val summary: String?,
        val memoryBlock: String?,
        val recentMessages: List<Message>
    )

    private suspend fun buildContextUsageSnapshot(
        conversation: Conversation,
        messages: List<Message>,
        modelName: String,
        maxOutputTokens: Int? = null,
        contextWindowOverrideTokens: Int? = null
    ): ConversationContextUsage {
        val usableMessages = messages.filter { message ->
            (message.role == "user" || message.role == "assistant") &&
                message.content.isNotBlank() &&
                !message.isExcluded &&
                !isErrorPlaceholderMessage(message.content)
        }

        val effectiveContextOverride = contextWindowOverrideTokens
            ?: conversation.contextWindowTokens?.let {
                if (it == 32_000 || it == 32_768) 200_000 else it
            }
            ?: runCatching {
                selectedModelDao.getModelsByConfig(conversation.apiConfigId).first()
                    .firstOrNull { it.modelName.equals(modelName, ignoreCase = true) }
                    ?.contextWindowTokens
            }.getOrNull()

        val modelDefaultTokens = estimateModelContextWindowTokens(modelName)
        val contextWindow = effectiveContextOverride
            ?.coerceIn(4_000, 2_000_000)
            ?: modelDefaultTokens
        val promptBudget = estimatePromptBudgetTokens(modelName, maxOutputTokens, effectiveContextOverride)
        val memoryBudget = (promptBudget * MEMORY_BUDGET_RATIO).toInt().coerceIn(400, 2_400)
        val recentBudget = (
            promptBudget - memoryBudget - SYSTEM_PROMPT_TOKEN_RESERVE
        ).coerceAtLeast(MIN_RECENT_CONTEXT_TOKENS)

        val resolution = resolveActiveContextMessages(
            usableMessages = usableMessages,
            compressedThrough = conversation.summaryUpdatedMessageId ?: 0L,
            recentBudget = recentBudget
        )

        val latestUserMessage = usableMessages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val memoryBlock = buildRelevantMemoryBlock(conversation, latestUserMessage, memoryBudget)
        val memoryTokens = memoryBlock?.let(::estimateTokenCount) ?: 0
        val memoryItemCount = memoryDao.getCandidateMemories(conversation.id).size

        val currentTier = com.aiassistant.domain.model.CompressionTier.fromLevel(conversation.compressionTier)
        val l2Rounds = (conversation.compressionRecentRounds).coerceIn(
            com.aiassistant.domain.model.CompressionTierPolicy.MIN_L2_RECENT_ROUNDS,
            com.aiassistant.domain.model.CompressionTierPolicy.MAX_L2_RECENT_ROUNDS
        )
        val customPercent = (conversation.compressionCustomPercent).coerceIn(
            com.aiassistant.domain.model.CompressionTierPolicy.MIN_CUSTOM_RETAIN_PERCENT,
            com.aiassistant.domain.model.CompressionTierPolicy.MAX_CUSTOM_RETAIN_PERCENT
        )

        // 基础 L0 组装结果
        val l0Res = ChatContextAssemblyHelper.assembleTieredContextMessages(
            tier = com.aiassistant.domain.model.CompressionTier.L0,
            usableMessages = usableMessages,
            recentBudget = recentBudget,
            l2RecentRounds = l2Rounds,
            existingRollingSummary = conversation.rollingSummary,
            customRetainPercent = customPercent
        )
        val baselineTokens = (
            SYSTEM_PROMPT_TOKEN_RESERVE +
                l0Res.recentTokens +
                memoryTokens.coerceAtMost(memoryBudget)
        ).coerceAtLeast(0)

        // 计算各档位的对比预览快照
        val previews = com.aiassistant.domain.model.CompressionTier.values().map { t ->
            val res = if (t == com.aiassistant.domain.model.CompressionTier.L0) l0Res else {
                ChatContextAssemblyHelper.assembleTieredContextMessages(
                    tier = t,
                    usableMessages = usableMessages,
                    recentBudget = recentBudget,
                    l2RecentRounds = l2Rounds,
                    existingRollingSummary = conversation.rollingSummary,
                    customRetainPercent = customPercent
                )
            }
            val tierTokens = (
                SYSTEM_PROMPT_TOKEN_RESERVE +
                    res.recentTokens +
                    memoryTokens.coerceAtMost(memoryBudget)
            ).coerceAtLeast(0)
            val tokensSaved = (baselineTokens - tierTokens).coerceAtLeast(0)
            val savingsPercent = if (baselineTokens > 0) (tokensSaved / baselineTokens.toFloat()).coerceIn(0f, 1f) else 0f
            com.aiassistant.domain.model.TierCompressionPreview(
                tier = t,
                estimatedTokens = tierTokens,
                baselineTokens = baselineTokens,
                tokensSaved = tokensSaved,
                savingsPercent = savingsPercent,
                retainedRoundsDesc = com.aiassistant.domain.model.CompressionTierPolicy.getRetainedRoundsDesc(t, l2Rounds, customPercent),
                lossNote = t.detailLossNote
            )
        }

        val currentPreview = previews.firstOrNull { it.tier == currentTier } ?: previews.first()
        val estimatedInputTokens = currentPreview.estimatedTokens
        val calculatedUsagePercent = (estimatedInputTokens / promptBudget.toFloat()).coerceIn(0f, 1f)

        val activeRes = when (currentTier) {
            com.aiassistant.domain.model.CompressionTier.L0 -> l0Res
            else -> ChatContextAssemblyHelper.assembleTieredContextMessages(
                tier = currentTier,
                usableMessages = usableMessages,
                recentBudget = recentBudget,
                l2RecentRounds = l2Rounds,
                existingRollingSummary = conversation.rollingSummary,
                customRetainPercent = customPercent
            )
        }

        return ConversationContextUsage(
            contextWindowTokens = contextWindow,
            modelDefaultContextTokens = modelDefaultTokens,
            promptBudgetTokens = promptBudget,
            estimatedInputTokens = estimatedInputTokens,
            usagePercent = calculatedUsagePercent,
            recentMessageCount = activeRes.activeMessages.size,
            olderMessageCount = activeRes.uncompressedOlderCount,
            recentTokens = activeRes.recentTokens,
            summaryTokens = if (activeRes.injectedSummary != null) TokenEstimationHelper.estimateTokenCount(activeRes.injectedSummary) else 0,
            memoryTokens = memoryTokens,
            memoryItemCount = memoryItemCount,
            hasRollingSummary = !conversation.rollingSummary.isNullOrBlank(),
            summaryUpdatedAt = conversation.summaryUpdatedAt,
            compressedThroughMessageId = conversation.summaryUpdatedMessageId,
            canCompress = activeRes.canCompress,
            compressionTier = currentTier,
            compressionRecentRounds = l2Rounds,
            compressionCustomPercent = customPercent,
            tierPreviews = previews
        )
    }


    private suspend fun buildContextBundle(
        conversation: Conversation?,
        config: ApiConfig,
        messages: List<Message>,
        modelName: String,
        maxOutputTokens: Int?,
        contextWindowOverrideTokens: Int? = null,
        currentUserMessage: String,
        options: ChatRequestOptions? = null
    ): ContextBundle {
        val usableMessages = messages.filter { message ->
            (message.role == "user" || message.role == "assistant") &&
                message.content.isNotBlank() &&
                !message.isExcluded &&
                !isErrorPlaceholderMessage(message.content)
        }

        val effectiveContextOverride = contextWindowOverrideTokens
            ?: conversation?.contextWindowTokens?.let {
                if (it == 32_000 || it == 32_768) 200_000 else it
            }
            ?: runCatching {
                conversation?.let { conv ->
                    selectedModelDao.getModelsByConfig(conv.apiConfigId).first()
                        .firstOrNull { it.modelName.equals(modelName, ignoreCase = true) }
                        ?.contextWindowTokens
                }
            }.getOrNull()

        val promptBudget = estimatePromptBudgetTokens(
            modelName = modelName,
            maxOutputTokens = maxOutputTokens,
            contextWindowOverrideTokens = effectiveContextOverride
        )
        val memoryBudget = (promptBudget * MEMORY_BUDGET_RATIO).toInt().coerceIn(400, 2_400)
        val recentBudget = (
            promptBudget - memoryBudget - SYSTEM_PROMPT_TOKEN_RESERVE
        ).coerceAtLeast(MIN_RECENT_CONTEXT_TOKENS)

        // 上下文组装：过往历史压缩成果由会话专属记忆与独立时间线系统 (memoryBlock) 完整承载
        val memoryBlock = conversation?.let {
            buildRelevantMemoryBlock(it, currentUserMessage, memoryBudget, options)
        }

        var activeTier = com.aiassistant.domain.model.CompressionTier.fromLevel(conversation?.compressionTier)
        val l2Rounds = (conversation?.compressionRecentRounds ?: com.aiassistant.domain.model.CompressionTierPolicy.DEFAULT_L2_RECENT_ROUNDS).coerceIn(
            com.aiassistant.domain.model.CompressionTierPolicy.MIN_L2_RECENT_ROUNDS,
            com.aiassistant.domain.model.CompressionTierPolicy.MAX_L2_RECENT_ROUNDS
        )
        val customRetainPercent = (conversation?.compressionCustomPercent ?: com.aiassistant.domain.model.CompressionTierPolicy.DEFAULT_CUSTOM_RETAIN_PERCENT).coerceIn(
            com.aiassistant.domain.model.CompressionTierPolicy.MIN_CUSTOM_RETAIN_PERCENT,
            com.aiassistant.domain.model.CompressionTierPolicy.MAX_CUSTOM_RETAIN_PERCENT
        )

        val structuredSummary = if (
            activeTier == com.aiassistant.domain.model.CompressionTier.L2 ||
            activeTier == com.aiassistant.domain.model.CompressionTier.L3 ||
            activeTier == com.aiassistant.domain.model.CompressionTier.LC
        ) {
            conversation?.rollingSummary?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    com.aiassistant.utils.AdvancedMemoryEngine.generateExtractiveStructuredSummary(usableMessages).toPromptBlock()
                }.getOrNull()
        } else null

        var tieredResult = ChatContextAssemblyHelper.assembleTieredContextMessages(
            tier = activeTier,
            usableMessages = usableMessages,
            recentBudget = recentBudget,
            l2RecentRounds = l2Rounds,
            existingRollingSummary = conversation?.rollingSummary,
            structuredSummary = structuredSummary,
            customRetainPercent = customRetainPercent
        )

        // 溢出安全回退：若当前档位组装后仍超 promptBudget 上限，自动降一档，直到 L4
        val memoryTokensCost = memoryBlock?.let(::estimateTokenCount) ?: 0
        var totalEstimated = SYSTEM_PROMPT_TOKEN_RESERVE + tieredResult.recentTokens + memoryTokensCost
        while (totalEstimated > promptBudget && activeTier != com.aiassistant.domain.model.CompressionTier.L4) {
            val fallback = com.aiassistant.domain.model.CompressionTierPolicy.fallbackOnContextOverflow(activeTier, totalEstimated, promptBudget)
                ?: break
            activeTier = fallback
            tieredResult = ChatContextAssemblyHelper.assembleTieredContextMessages(
                tier = activeTier,
                usableMessages = usableMessages,
                recentBudget = recentBudget,
                l2RecentRounds = l2Rounds,
                existingRollingSummary = conversation?.rollingSummary,
                structuredSummary = structuredSummary,
                customRetainPercent = customRetainPercent
            )
            totalEstimated = SYSTEM_PROMPT_TOKEN_RESERVE + tieredResult.recentTokens + memoryTokensCost
        }

        return ContextBundle(
            summary = tieredResult.injectedSummary,
            memoryBlock = memoryBlock,
            recentMessages = tieredResult.activeMessages
        )
    }

    private fun estimatePromptBudgetTokens(
        modelName: String,
        maxOutputTokens: Int?,
        contextWindowOverrideTokens: Int? = null
    ): Int {
        val contextWindow = contextWindowOverrideTokens
            ?.coerceIn(4_000, 4_000_000)
            ?: estimateModelContextWindowTokens(modelName)
        // 2026-09：DeepSeek V4 最大输出 384K、GPT-6 Astra 128K，输出预留不再被 8K 死锁
        val capabilityReserveCap = com.aiassistant.domain.model.ModelCapabilityEngine
            .evaluateModel(modelName)
            .maxOutputTokens
            .coerceIn(2_048, 64_000)
        val maxAllowedOutputReserve = (contextWindow * 0.25f).toInt()
            .coerceAtLeast(1_024)
            .coerceAtMost(capabilityReserveCap)
        val outputReserve = (maxOutputTokens ?: 4_096).coerceIn(512, maxAllowedOutputReserve)
        return (contextWindow - outputReserve - 1_024)
            .coerceAtLeast(3_000)
            .coerceAtMost((contextWindow - 512).coerceAtLeast(3_000))
    }

    private fun estimateModelContextWindowTokens(modelName: String): Int {
        val name = modelName.lowercase()

        val explicitLimit = parseContextWindowFromText(name)
        if (explicitLimit != null) {
            return explicitLimit.coerceIn(4_000, 2_000_000)
        }

        modelContextWindowCache[name]
            ?: modelContextWindowCache.entries.firstOrNull { (cachedName, _) ->
                cachedName == name ||
                    name.endsWith("/$cachedName") ||
                    cachedName.endsWith("/$name")
            }?.value
            ?.coerceIn(4_000, 2_000_000)
            ?.takeIf { it >= 128_000 }
            ?.let { return it }

        return com.aiassistant.domain.model.ModelCapabilityEngine.evaluateModel(modelName).contextWindowTokens
    }

    private fun parseContextWindowFromText(value: String): Int? {
        val normalized = value.lowercase()
        Regex("""(?<!\d)(\d+(?:\.\d+)?)\s*(m|k)\b""")
            .findAll(normalized)
            .mapNotNull { match ->
                val number = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val multiplier = if (match.groupValues[2] == "m") 1_000_000 else 1_000
                (number * multiplier).toInt()
            }
            .filter { it in 4_000..2_000_000 }
            .maxOrNull()
            ?.let { return it }

        // 避免误匹配年份数字（如 2024、2025、202405）
        return Regex("""(?<!\d)(?:ctx|context|window|tokens?)?[-_]?([1-9]\d{4,6})(?!\d)""")
            .findAll(normalized)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 16_000..2_000_000 }
            .maxOrNull()
    }

    private suspend fun resolveLatestTimelineAnchor(conversationId: Long): String? {
        val conversation = conversationDao.getConversationById(conversationId) ?: return null
        val timelineNodes = runCatching { timelineNodeDao?.getTimelineNodes(conversationId) }.getOrNull().orEmpty()
        val sortedNodes = timelineNodes.sortedWith(compareBy<TimelineNode> { it.orderIndex }.thenBy { it.createdAt })
        val latestNode = sortedNodes.lastOrNull()
        val currentStoryTime = conversation.currentStoryTime?.takeIf { it.isNotBlank() && it != "未确定" && it != "未知" }

        return when {
            latestNode != null && !currentStoryTime.isNullOrBlank() -> {
                "【$currentStoryTime】${if (latestNode.timeTag.isNotBlank()) "[${latestNode.timeTag}] " else ""}${latestNode.event}"
            }
            latestNode != null -> {
                "${if (latestNode.timeTag.isNotBlank()) "[${latestNode.timeTag}] " else ""}${latestNode.event}"
            }
            !currentStoryTime.isNullOrBlank() -> {
                "【$currentStoryTime】"
            }
            else -> {
                val candidateMemories = runCatching { memoryDao.getCandidateMemories(conversationId) }.getOrNull().orEmpty()
                candidateMemories.firstOrNull { it.content.trim().startsWith("【当前故事时间】：") || it.content.trim().startsWith("当前故事时间：") }
                    ?.content?.substringAfter("：")?.trim()?.takeIf { it.isNotBlank() }
            }
        }
    }

    private suspend fun resolveExistingPreferencesAndConstraints(conversationId: Long): List<String> {
        val results = mutableListOf<String>()
        val candidateMemories = runCatching { memoryDao.getCandidateMemories(conversationId) }.getOrNull().orEmpty()
        for (m in candidateMemories) {
            val trimmed = m.content.trim()
            if (trimmed.isBlank()) continue
            val lower = trimmed.lowercase(java.util.Locale.ROOT)
            if (lower.contains("偏好") || lower.contains("约束") || lower.contains("准则") ||
                lower.contains("习惯") || lower.contains("禁忌") || lower.contains("禁止") ||
                lower.contains("必须") || lower.contains("不要") || lower.contains("规则") ||
                m.scope == "user" || m.scope == "global"
            ) {
                results.add(trimmed)
            }
        }
        val timelineNodes = timelineNodeDao?.let { runCatching { it.getTimelineNodes(conversationId) }.getOrNull() }.orEmpty()
        for (node in timelineNodes) {
            val category = TimelineCategory.fromKey(node.category)
            if (category == TimelineCategory.RULE_CONSTRAINT ||
                category == TimelineCategory.ATEMPORAL_SETTING ||
                category == TimelineCategory.CHARACTER_SETTING ||
                category == TimelineCategory.WORLD_SETTING
            ) {
                val content = node.eventContent.trim()
                if (content.isNotBlank() && !results.contains(content)) {
                    results.add(content)
                }
            }
        }
        return results.take(12)
    }

    private suspend fun ensureRollingSummary(
        conversation: Conversation?,
        config: ApiConfig,
        modelName: String,
        olderMessages: List<Message>,
        tokenBudget: Int
    ): String? {
        // 彻底废除滚动摘要：过往历史由时间线梳理与会话记忆承载，不再生成长文本滚动摘要
        return null
    }

    private suspend fun generateRollingSummary(
        config: ApiConfig,
        modelName: String,
        existingSummary: String?,
        pendingMessages: List<Message>,
        tokenBudget: Int,
        latestTimelineAnchor: String? = null,
        existingPreferencesAndConstraints: List<String>? = null
    ): String? {
        val transcript = buildSummaryTranscript(pendingMessages, maxMessages = SUMMARY_TRANSCRIPT_MESSAGE_LIMIT)
        val prompt = AdvancedMemoryEngine.buildStructuredSummaryPrompt(
            existingSummary = existingSummary,
            transcript = transcript,
            tokenBudget = tokenBudget.coerceIn(SUMMARY_PROMPT_MIN_TOKENS, SUMMARY_PROMPT_MAX_TOKENS),
            latestTimelineAnchor = latestTimelineAnchor,
            existingPreferencesAndConstraints = existingPreferencesAndConstraints
        )
        // 内部滚动摘要调用统一使用保守预算（与 2026 厂商输出上限无关，仅控制摘要生成长度）：
        // 8,192（Anthropic Haiku 为 4,096）足以容纳完整摘要，并对仅支持旧上限的第三方网关保持兼容。
        val isHaiku = modelName.contains("haiku", ignoreCase = true)
        val completionTokens = when {
            config.apiType == "anthropic" -> if (isHaiku) 4096 else 8192
            else -> 8192
        }

        val normalizedUrl = normalizeApiBaseUrl(config.baseUrl, config.apiType)
        val allKeys = parseApiKeys(config.apiKey).ifEmpty { listOf(config.apiKey) }.let {
            if (personalizationManager.isBackupKeyFallbackEnabled()) it else it.take(1)
        }
        var lastException: Exception? = null
        val isModernOpenAiStyle = com.aiassistant.domain.model.ModelCapabilityEngine
            .isModernOpenAiReasoningStyle(modelName) ||
            com.aiassistant.domain.model.ModelVendorProfiles.detectVendor(modelName).let {
                it == com.aiassistant.domain.model.ModelVendor.KIMI ||
                    it == com.aiassistant.domain.model.ModelVendor.GLM ||
                    it == com.aiassistant.domain.model.ModelVendor.GPT ||
                    it == com.aiassistant.domain.model.ModelVendor.MINIMAX
            }

        for (key in allKeys) {
            val cleanKey = key.removePrefix("Bearer ").trim()
            val retryPolicy = personalizationManager.getRetryPolicy()
            for (attempt in 1..(com.aiassistant.domain.model.RetryPolicy.MAX_RETRIES + 1)) {
                try {
                    if (config.apiType == "anthropic") {
                        val request = AnthropicRequest(
                            model = modelName,
                            messages = listOf(AnthropicMessage(role = "user", content = prompt)),
                            max_tokens = completionTokens,
                            temperature = null
                        )
                        val response = RetrofitClient.getAnalysisService(normalizedUrl)
                            .anthropicMessages(apiKey = cleanKey, request = request)
                            .execute()
                        if (!response.isSuccessful) {
                            val errBody = response.errorBody()?.string()?.take(300).orEmpty()
                            throw Exception("HTTP ${response.code()}: $errBody")
                        }
                        val text = response.body()?.content?.firstOrNull { it.type == "text" }?.text
                            ?: response.body()?.content?.firstOrNull()?.text
                        val sanitized = sanitizeSummaryCompletion(text)
                        if (!sanitized.isNullOrBlank() && isSummarySubstantiallyComplete(sanitized)) return sanitized
                    } else {
                        // 优先尝试标准非流式请求
                        val request = ChatCompletionRequest(
                            model = modelName,
                            messages = listOf(ChatMessage(role = "user", content = prompt)),
                            temperature = null,
                            max_tokens = if (isModernOpenAiStyle) null else completionTokens,
                            max_completion_tokens = if (isModernOpenAiStyle) completionTokens else null,
                            stream = false
                        )
                        val response = RetrofitClient.getAnalysisService(normalizedUrl)
                            .chatCompletion(RetrofitClient.formatApiKey(cleanKey), request)
                            .execute()
                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body?.error != null) {
                                throw Exception(body.error.message ?: "OpenAI API 返回错误")
                            }
                            val choice = body?.choices?.firstOrNull()
                            val text = choice?.message?.content?.ifBlank { null }
                                ?: choice?.message?.reasoning_content?.ifBlank { null }
                            val sanitized = sanitizeSummaryCompletion(text)
                            if (!sanitized.isNullOrBlank() && isSummarySubstantiallyComplete(sanitized)) return sanitized
                        } else {
                            val errBody = response.errorBody()?.string()?.take(300).orEmpty()
                            if (response.code() !in setOf(400, 405, 415, 501)) throw Exception("HTTP ${response.code()}: $errBody")
                            Log.w(tag, "generateRollingSummary 非流式 HTTP ${response.code()}: $errBody，尝试流式通道备用")
                        }

                        // 非流式未返回或网关仅支持流式时，自动启用流式保底通道
                        val streamResult = executeStreamingCompletion(
                            config = config.copy(baseUrl = normalizedUrl, modelName = modelName),
                            key = cleanKey,
                            prompt = prompt,
                            maxTokens = completionTokens
                        )
                        val sanitized = sanitizeSummaryCompletion(streamResult)
                        if (!sanitized.isNullOrBlank() && isSummarySubstantiallyComplete(sanitized)) {
                            return sanitized
                        }
                    }
                    throw Exception("模型未返回有效的完整摘要")
                } catch (e: Exception) {
                    lastException = e
                    Log.w(tag, "generateRollingSummary Key报错或请求异常 (attempt $attempt): ${e.message}")
                    if (isRequestCancellation(e)) throw e
                    if (e is com.aiassistant.data.remote.HttpAccessDeniedException) throw e
                    if (retryPolicy.canRetry(e, attempt - 1)) {
                        kotlinx.coroutines.delay(retryPolicy.delayMillis(attempt - 1))
                        continue
                    }
                    break
                }
            }
        }
        if (lastException != null) {
            Log.w(tag, "generateRollingSummary 所有Key均尝试失败: ${lastException.message}")
        }
        return null
    }

    private fun buildSummaryTranscript(messages: List<Message>, maxMessages: Int): String {
        // 先进行智能信息密度提纯 (Loss-Aware Pre-pruning)，剥离无意义口语废话
        val pruned = AdvancedMemoryEngine.pruneLowInformationTurns(messages)
        val candidateMessages = if (pruned.isNotEmpty()) pruned else messages
        // 彻底根除断层跳跃切片：严禁提取开场几条与末尾几条进行强行拼凑（防止模型将最初开场与最新事件错误因果连接）
        // 严格提取单一连续的近期对话切片，保持因果与时间线连贯
        val selectedMessages = if (candidateMessages.size > maxMessages) {
            candidateMessages.takeLast(maxMessages)
        } else {
            candidateMessages
        }
        return selectedMessages.joinToString("\n") { message ->
            val role = if (message.role == "user") "用户" else "助手"
            "$role: ${compactMessageForHistory(message.content, SUMMARY_TRANSCRIPT_CHAR_LIMIT)}"
        }
    }

    private fun buildExtractiveConversationSummary(
        messages: List<Message>,
        tokenBudget: Int,
        existingPreferencesAndConstraints: List<String>? = null
    ): String? {
        if (messages.isEmpty()) return null
        val recentMessages = if (messages.size > 30) messages.takeLast(30) else messages
        val structured = AdvancedMemoryEngine.generateExtractiveStructuredSummary(
            recentMessages,
            tokenBudget,
            existingPreferencesAndConstraints
        )
        val block = structured.toPromptBlock()
        if (block.isBlank()) return null
        return compactTextToTokenBudget(block, tokenBudget)
    }

    fun buildEffectiveSystemPrompt(
        customPrompt: String?,
        olderSummary: String?,
        memoryBlock: String?,
        options: ChatRequestOptions?,
        isConversationSpecific: Boolean = true,
        isRoleplay: Boolean = false,
        worldBookBlock: String? = null
    ): String? {
        if (isRoleplay) {
            // 角色扮演/剧情创作：以角色设定与场景为核心，隔绝通用 AI 助手套话，同时有机装载会话记忆、时间线与世界书
            return formatRoleplaySystemPrompt(customPrompt, memoryBlock, worldBookBlock)
        }

        val basePrompt = """
            你是一个可靠、清晰的 AI 助手。
            - 优先回答用户最新的问题，同时结合本轮对话上下文。
            - 默认使用用户当前消息的语言回答。
            - 引用文件、图片或 OCR 内容时，尽量说明来自哪个附件。
            - 不确定的信息要直接说明不确定，不要编造。
            - 用户明确指定格式、语气、称谓要求或步骤时，优先遵守用户要求。严禁违背任何已设定的称谓禁忌或负向约束。
        """.trimIndent()

        val personalizationPart = personalizationManager.buildPrompt()?.let {
            "【用户个性化习惯偏好（通用背景引导）】\n$it\n（注：当个性化偏好与下方的提示词发生任何冲突时，严格以下方的提示词为最高准则。）"
        }

        val promptPart = customPrompt?.takeIf { it.isNotBlank() }?.let {
            if (isConversationSpecific) {
                "【会话独享系统提示词（已覆盖全局提示词，最高指令）】\n${it.trim()}"
            } else {
                "【全局默认系统提示词（当前会话未设置专属提示词）】\n${it.trim()}"
            }
        }

        val summaryPart = olderSummary?.takeIf { it.isNotBlank() }?.let {
            "【历史对话前序背景与摘要】：\n${it.trim()}\n（注：以上为超出近期上下文窗口的早期对话摘要，仅供参考背景，当前对话以此后的近期消息为准。）"
        }

        return listOfNotNull(
            basePrompt,
            personalizationPart,
            buildRuntimeFeaturePrompt(options),
            promptPart,
            summaryPart,
            memoryBlock,
            worldBookBlock
        ).joinToString("\n\n").ifBlank { null }
    }

    private fun buildRuntimeFeaturePrompt(options: ChatRequestOptions?): String? {
        if (options == null) return null
        val notes = mutableListOf<String>()
        if (options.enableThinking == true) {
            notes += "本轮已开启思考模式。请进行更充分的推理；如果 API 返回可见思考内容，应将其作为思考过程流式输出。思考强度：${options.thinkingEffort ?: "medium"}。"
        }
        if (options.enableWebSearch == true) {
            notes += "本轮已开启智能搜索/联网。若消息中包含联网搜索资料，请优先基于资料回答，并在末尾列出来源；若资料不足，请明确说明不确定。"
        }
        return notes.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private suspend fun enrichUserMessageWithWebSearch(
        userMessage: String,
        options: ChatRequestOptions
    ): EnrichedPromptResult {
        val hub = echoToolHub
        if (hub != null) {
            return hub.enrichUserPrompt(userMessage, options)
        }

        if (options.enableWebSearch != true) return EnrichedPromptResult(userMessage, emptyList())

        val settings = tavilySearchManager.getSettings()
        return if (!settings.enabled || settings.apiKey.isBlank()) {
            val prompt = buildString {
                append(userMessage)
                append("\n\n[联网搜索状态]\n")
                append("用户已开启联网搜索，但 Tavily 未启用或 API Key 为空。请明确说明本轮未能成功联网，不要假装读取了实时网页。")
            }
            EnrichedPromptResult(prompt, emptyList())
        } else {
            tavilySearchManager.search(userMessage).fold(
                onSuccess = { bundle ->
                    val prompt = buildString {
                        append(bundle.toPromptBlock())
                        append("\n\n用户原始问题：\n")
                        append(userMessage)
                    }
                    val record = ToolCallRecord(
                        toolType = "WEB_SEARCH",
                        toolName = "Tavily 联网搜索",
                        iconName = "Search",
                        summary = "已检索到 ${bundle.results.size} 条网络网页资料",
                        detailContent = bundle.toPromptBlock(),
                        isSuccess = true
                    )
                    EnrichedPromptResult(prompt, listOf(record))
                },
                onFailure = { error ->
                    val prompt = buildString {
                        append(userMessage)
                        append("\n\n[联网搜索状态]\n")
                        append("Tavily 搜索失败：")
                        append(error.message ?: "未知错误")
                        append("\n请明确说明本轮未能成功联网，并基于已有上下文谨慎回答。")
                    }
                    EnrichedPromptResult(prompt, emptyList())
                }
            )
        }
    }


    private fun compactTextToTokenBudget(text: String, tokenBudget: Int): String {
        val normalized = text.trim()
        val safeBudget = maxOf(tokenBudget, 4_000)
        if (estimateTokenCount(normalized) <= safeBudget) return normalized

        var charLimit = (safeBudget * 2.4f).toInt().coerceAtLeast(1_000)
        while (charLimit > 1_000) {
            val candidate = normalized.take(charLimit)
            val complete = AdvancedMemoryEngine.extractCompleteSentence(candidate, charLimit)
            if (estimateTokenCount(complete) <= safeBudget) {
                return complete
            }
            charLimit = (charLimit * 0.9f).toInt()
        }
        return AdvancedMemoryEngine.extractCompleteSentence(normalized.take(charLimit), charLimit)
    }

    suspend fun processSavedMessageMemory(
        messageId: Long,
        activeConfigId: Long? = null,
        activeModelName: String? = null,
        sessionMemoryEnabled: Boolean? = null
    ): PendingMemoryCandidate? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val message = messageDao.getMessageById(messageId) ?: return@withContext null
        val registration = message.conversationId to currentCoroutineContext()[Job]!!
        if (activeMemoryJobs.putIfAbsent(messageId, registration) != null) return@withContext null
        try {
            processMessageMemory(message, activeConfigId, activeModelName, sessionMemoryEnabled)
        } finally {
            activeMemoryJobs.remove(messageId, registration)
        }
    }

    private suspend fun processMessageMemory(
        message: Message,
        activeConfigId: Long?,
        activeModelName: String?,
        sessionMemoryEnabled: Boolean?
    ): PendingMemoryCandidate? = withContext(Dispatchers.IO) {
        if (message.role != "user" || message.content.isBlank()) return@withContext null
        // Called only for the saved message of this round. A long foreground reply must
        // not make its source expire before background extraction can start.
        if (memoryDao.getBySourceMessage(message.id) != null) return@withContext null

        val conversation = conversationDao.getConversationById(message.conversationId) ?: return@withContext null
        val mutationEpoch = ChatGenerationManager.mutationEpoch(message.conversationId)
        // 严格隔离：角色扮演/故事创作会话不写入全局长期记忆，防止小说情节与角色设定污染全局用户偏好
        if (isRoleplayConversation(conversation)) return@withContext null
        if (!personalizationManager.getSettings().autoMemoryEnabled &&
            (sessionMemoryEnabled ?: conversation.enableSessionMemory) == false
        ) return@withContext null

        val candidate = extractMemoryCandidate(
            content = message.content,
            conversationId = message.conversationId,
            messageId = message.id,
            activeConfigId = activeConfigId,
            activeModelName = activeModelName
        ) ?: return@withContext null
        currentCoroutineContext().ensureActive()

        val memoryContent = candidate.distilledContent
        val scope = candidate.suggestedScope
        val scopedConversationId = if (scope == "conversation") message.conversationId else null
        val keywords = tokenizeForMemory(memoryContent).take(18).joinToString(",")
        val accepted = com.aiassistant.AiAssistantApp.instance.database.withTransaction {
            val latestConversation = conversationDao.getConversationById(message.conversationId) ?: return@withTransaction false
            if (mutationEpoch != ChatGenerationManager.mutationEpoch(message.conversationId) ||
                messageDao.getMessageById(message.id)?.content != message.content || isRoleplayConversation(latestConversation)
            ) return@withTransaction false
            // Recheck switches after the model returns; a disabled scope must not be saved or offered.
            val switchedOffDuringExtraction = conversation.enableSessionMemory != false && latestConversation.enableSessionMemory == false
            val canReview = (sessionMemoryEnabled ?: latestConversation.enableSessionMemory) != false && !switchedOffDuringExtraction
            if (scope in listOf("user", "global") && !personalizationManager.getSettings().autoMemoryEnabled) return@withTransaction canReview
            if (scope == "conversation" && !canReview) return@withTransaction false
            val existing = memoryDao.getByScopeAndContent(scope, memoryContent)
                ?.takeIf { scopedConversationId == null || it.conversationId == scopedConversationId }
            val now = System.currentTimeMillis()

            // 仅自动保存经 SmartMemoryExtractor 或辅助模型提炼的高置信度偏好或项目背景，彻底杜绝噪音
            if (existing != null) {
                memoryDao.updateMemory(
                    existing.copy(
                        confidence = maxOf(existing.confidence, MEMORY_CAPTURE_CONFIDENCE),
                        keywords = keywords.ifBlank { existing.keywords },
                        updatedAt = now
                    )
                )
                return@withTransaction true
            }

            // 冲突检测与版本更替：检测是否有与当前事实发生排他性属性冲突的旧记忆
            val existingCandidates = memoryDao.getCandidateMemories(message.conversationId)
                .filter { it.scope == scope && (scopedConversationId == null || it.conversationId == scopedConversationId) }
            val conflictedItem = existingCandidates.firstOrNull { AdvancedMemoryEngine.detectConflict(memoryContent, it) }
            if (conflictedItem != null) {
                memoryDao.updateMemory(
                    conflictedItem.copy(
                        content = memoryContent,
                        keywords = keywords.ifBlank { conflictedItem.keywords },
                        confidence = maxOf(conflictedItem.confidence, MEMORY_CAPTURE_CONFIDENCE),
                        sourceMessageId = message.id,
                        updatedAt = now
                    )
                )
                return@withTransaction true
            }

            memoryDao.insertMemory(
                MemoryItem(
                    scope = scope,
                    conversationId = scopedConversationId,
                    content = memoryContent,
                    keywords = keywords.ifBlank { null },
                    sourceMessageId = message.id,
                    confidence = MEMORY_CAPTURE_CONFIDENCE,
                    createdAt = now,
                    updatedAt = now
                )
            )
            true
        }
        candidate.takeIf { accepted }
    }

    suspend fun extractMemoryCandidate(
        content: String,
        conversationId: Long = 0L,
        messageId: Long? = null,
        activeConfigId: Long? = null,
        activeModelName: String? = null
    ): PendingMemoryCandidate? = withContext(Dispatchers.IO) {
        val settings = personalizationManager.getSettings()
        val triedModels = mutableSetOf<Pair<Long, String>>()
        // 1. 如果启用了辅助模型，尝试使用指定的辅助模型进行提炼
        if (settings.auxiliaryMemoryEnabled && settings.auxiliaryMemoryApiConfigId > 0L) {
            val auxiliaryModel = settings.auxiliaryMemoryModel.trim().ifBlank {
                getDecryptedConfig(settings.auxiliaryMemoryApiConfigId)?.modelName.orEmpty()
            }
            triedModels += settings.auxiliaryMemoryApiConfigId to auxiliaryModel
            try {
                val candidate = extractMemoryWithAuxiliaryModel(
                    content = content,
                    conversationId = conversationId,
                    messageId = messageId,
                    apiConfigId = settings.auxiliaryMemoryApiConfigId,
                    modelName = settings.auxiliaryMemoryModel,
                    customPrompt = settings.auxiliaryMemoryPrompt
                )
                if (candidate != null) {
                    return@withContext candidate
                }
            } catch (e: Exception) {
                if (isRequestCancellation(e)) throw CancellationException("记忆提炼已取消", e)
                Log.w(tag, "辅助模型提取记忆失败: ${e.message}")
            }
        }

        // 2. 让模型真正参与智能提取（需求 2）：优先调度当前会话正在使用的活动模型/会话绑定模型
        val conversation = if (conversationId > 0L) conversationDao.getConversationById(conversationId) else null
        val configPair = resolveTimelineAnalysisConfig(activeConfigId, activeModelName, conversation, preferAuxiliaryModel = false)
        if (configPair != null && triedModels.add(configPair.first.id to configPair.second.trim())) {
            try {
                val candidate = extractMemoryWithAuxiliaryModel(
                    content = content,
                    conversationId = conversationId,
                    messageId = messageId,
                    apiConfigId = configPair.first.id,
                    modelName = configPair.second,
                    customPrompt = settings.auxiliaryMemoryPrompt
                )
                if (candidate != null) {
                    return@withContext candidate
                }
            } catch (e: Exception) {
                if (isRequestCancellation(e)) throw CancellationException("记忆提炼已取消", e)
                Log.w(tag, "会话模型智能提取记忆异常，平滑降级为本地规则: ${e.message}")
            }
        }

        // 3. 本地纯规则提取兜底（当模型不可用、超时、报错或离线时安全平滑生效）
        currentCoroutineContext().ensureActive()
        SmartMemoryExtractor.extractCandidate(
            content = content,
            conversationId = conversationId,
            messageId = messageId
        )
    }

    private suspend fun extractMemoryWithAuxiliaryModel(
        content: String,
        conversationId: Long,
        messageId: Long?,
        apiConfigId: Long,
        modelName: String,
        customPrompt: String
    ): PendingMemoryCandidate? = withContext(Dispatchers.IO) {
        val rawConfig = getDecryptedConfig(apiConfigId) ?: return@withContext null
        if (!rawConfig.isEnabled) return@withContext null
        val targetModel = modelName.trim().ifBlank { rawConfig.modelName }
        val config = rawConfig.copy(modelName = targetModel)

        val promptTemplate = customPrompt.trim().ifBlank {
            PersonalizationManager.DEFAULT_AUXILIARY_MEMORY_PROMPT
        }
        val prompt = """
            $promptTemplate

            【待识别内容】
            $content
        """.trimIndent()

        val responseText = try {
            if (config.apiType == "anthropic") {
                generateAnthropicMemoryExtraction(config, prompt, conversationId)
            } else {
                generateOpenAIMemoryExtraction(config, prompt, conversationId)
            }
        } catch (e: Exception) {
            if (isRequestCancellation(e)) throw CancellationException("记忆提炼已取消", e)
            Log.w(tag, "调用辅助模型API提取记忆异常: ${e.message}")
            null
        }

        if (responseText.isNullOrBlank()) return@withContext null
        // 彻底剥离思考标签，防止思考过程或草稿污染记忆事实
        val stripped = TimelineMemoryHelper.stripThinkingTags(responseText)
        val cleaned = stripped.trim().removePrefix("```").removeSuffix("```").trim()
        if (cleaned.contains("IGNORE", ignoreCase = true) || cleaned.length < 3) {
            return@withContext null
        }

        // 过滤开场白、客套话与空白，获取首条有效记忆内容
        val validLines = cleaned.lines().map { it.trim() }.filter {
            it.isNotBlank() && !it.startsWith("<") && !it.startsWith("好的") && !it.startsWith("以下是") && !it.startsWith("提炼结果")
        }
        val rawDistilled = validLines.firstOrNull()?.trim() ?: return@withContext null
        val (finalContent, category) = SmartMemoryExtractor.refineMemoryContent(rawDistilled)
        if (finalContent.isBlank()) return@withContext null

        PendingMemoryCandidate(
            distilledContent = finalContent,
            originalSnippet = content.take(80),
            suggestedScope = if (SmartMemoryExtractor.isConversationScoped(content)) "conversation" else "user",
            conversationId = conversationId,
            sourceMessageId = messageId,
            category = category
        )
    }

    private suspend fun <T> awaitMemoryCall(call: retrofit2.Call<T>, conversationId: Long): retrofit2.Response<T> {
        currentCoroutineContext().ensureActive()
        activeMemoryCalls[call] = conversationId
        try {
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : retrofit2.Callback<T> {
                    override fun onResponse(call: retrofit2.Call<T>, response: retrofit2.Response<T>) {
                        if (continuation.isActive) continuation.resume(response) else response.errorBody()?.close()
                    }
                    override fun onFailure(call: retrofit2.Call<T>, error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(
                            if (call.isCanceled) CancellationException("记忆提炼已取消", error) else error
                        )
                    }
                })
            }
        } finally {
            activeMemoryCalls.remove(call)
        }
    }

    private suspend fun generateOpenAIMemoryExtraction(config: ApiConfig, prompt: String, conversationId: Long): String? {
        val allKeys = parseApiKeys(config.apiKey).ifEmpty { listOf(config.apiKey) }.let {
            if (personalizationManager.isBackupKeyFallbackEnabled()) it else it.take(1)
        }
        var lastException: Exception? = null
        val retryPolicy = personalizationManager.getRetryPolicy()
        for (key in allKeys) {
            for (attempt in 0..com.aiassistant.domain.model.RetryPolicy.MAX_RETRIES) {
                try {
                    currentCoroutineContext().ensureActive()
                    val completionLimit = ReasoningControls.policy(config.modelName,
                        ReasoningCapabilityCatalog.get(config.baseUrl, config.apiType, config.modelName)).usesMaxCompletionTokens
                    val request = ChatCompletionRequest(
                        model = config.modelName,
                        messages = listOf(ChatMessage(role = "user", content = prompt)),
                        temperature = null,
                        max_tokens = if (completionLimit) null else 4096,
                        max_completion_tokens = if (completionLimit) 4096 else null,
                        stream = false
                    )
                    val response = awaitMemoryCall(RetrofitClient.getService(normalizeApiBaseUrl(config.baseUrl, config.apiType))
                        .chatCompletion(RetrofitClient.formatApiKey(key), request), conversationId)
                    if (!response.isSuccessful) {
                        val err = response.errorBody()?.string()?.take(200).orEmpty()
                        throw Exception("HTTP ${response.code()}: $err")
                    }
                    val choice = response.body()?.choices?.firstOrNull()
                    val text = choice?.message?.content?.ifBlank { null }
                        ?: choice?.message?.reasoning_content?.ifBlank { null }
                    if (!text.isNullOrBlank()) return text
                    throw Exception("模型未返回有效内容")
                } catch (e: Exception) {
                    if (isRequestCancellation(e)) throw e
                    lastException = e
                    if (e is com.aiassistant.data.remote.HttpAccessDeniedException) throw e
                    Log.w(tag, "辅助模型提取(OpenAI) Key报错: ${e.message}")
                    if (retryPolicy.canRetry(e, attempt)) {
                        kotlinx.coroutines.delay(retryPolicy.delayMillis(attempt))
                        continue
                    }
                    break
                }
            }
        }
        if (lastException != null) throw lastException
        return null
    }

    private suspend fun generateAnthropicMemoryExtraction(config: ApiConfig, prompt: String, conversationId: Long): String? {
        val allKeys = parseApiKeys(config.apiKey).ifEmpty { listOf(config.apiKey) }.let {
            if (personalizationManager.isBackupKeyFallbackEnabled()) it else it.take(1)
        }
        var lastException: Exception? = null
        val retryPolicy = personalizationManager.getRetryPolicy()
        for (key in allKeys) {
            for (attempt in 0..com.aiassistant.domain.model.RetryPolicy.MAX_RETRIES) {
                try {
                    currentCoroutineContext().ensureActive()
                    val request = AnthropicRequest(
                        model = config.modelName,
                        messages = listOf(AnthropicMessage(role = "user", content = prompt)),
                        max_tokens = 4096,
                        temperature = null
                    )
                    val response = awaitMemoryCall(RetrofitClient.getService(normalizeApiBaseUrl(config.baseUrl, config.apiType))
                        .anthropicMessages(apiKey = key.removePrefix("Bearer ").trim(), request = request), conversationId)
                    if (!response.isSuccessful) {
                        val err = response.errorBody()?.string()?.take(200).orEmpty()
                        throw Exception("HTTP ${response.code()}: $err")
                    }
                    val text = response.body()?.content?.firstOrNull { it.type == "text" }?.text
                    if (!text.isNullOrBlank()) return text
                    throw Exception("模型未返回有效内容")
                } catch (e: Exception) {
                    if (isRequestCancellation(e)) throw e
                    lastException = e
                    Log.w(tag, "辅助模型提取(Anthropic) Key报错: ${e.message}")
                    if (e is com.aiassistant.data.remote.HttpAccessDeniedException) throw e
                    if (retryPolicy.canRetry(e, attempt)) {
                        kotlinx.coroutines.delay(retryPolicy.delayMillis(attempt))
                        continue
                    }
                    break
                }
            }
        }
        if (lastException != null) throw lastException
        return null
    }

    suspend fun testAuxiliaryMemoryExtraction(
        apiConfigId: Long,
        modelName: String,
        testText: String,
        customPrompt: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rawConfig = getDecryptedConfig(apiConfigId)
                ?: return@withContext Result.failure(Exception("API配置不存在，请重新选择配置"))
            val targetModel = modelName.trim().ifBlank { rawConfig.modelName }
            val config = rawConfig.copy(modelName = targetModel)

            val promptTemplate = customPrompt.trim().ifBlank {
                PersonalizationManager.DEFAULT_AUXILIARY_MEMORY_PROMPT
            }
            val prompt = """
                $promptTemplate

                【待识别内容】
                $testText
            """.trimIndent()

            val raw = if (config.apiType == "anthropic") {
                generateAnthropicMemoryExtraction(config, prompt, 0L)
            } else {
                generateOpenAIMemoryExtraction(config, prompt, 0L)
            }

            if (raw.isNullOrBlank()) {
                val localFallback = SmartMemoryExtractor.extractCandidate(testText, 0L, null)
                if (localFallback != null) {
                    Result.success("【辅助模型返回空，自动降级为本地规则提炼】：\n${localFallback.distilledContent}")
                } else {
                    Result.success("【辅助模型与本地规则均判定该内容无需成为记忆（输出 IGNORE）】")
                }
            } else if (raw.contains("IGNORE", ignoreCase = true)) {
                Result.success("【辅助模型判定无需记录】：$raw")
            } else {
                Result.success("【辅助模型成功提取记忆】：\n$raw")
            }
        } catch (e: Exception) {
            if (isRequestCancellation(e)) throw CancellationException("记忆提炼已取消", e)
            val localFallback = SmartMemoryExtractor.extractCandidate(testText, 0L, null)
            val fallbackMsg = if (localFallback != null) {
                "\n\n[自动安全降级] 本地规则成功兜底提炼出记忆：\n${localFallback.distilledContent}"
            } else {
                "\n\n[自动安全降级] 本地规则兜底运行正常（内容无需入库）"
            }
            Result.failure(Exception("辅助模型调用报错: ${e.message}$fallbackMsg", e))
        }
    }

    /**
     * 读取会话全量历史，调用模型对时间推进轴与日常记忆进行提取、消灭相对时间词并校对推断
     */
    /**
     * 解析时间线梳理与自动更新所使用的 API 配置与模型
     */
    private suspend fun resolveTimelineAnalysisConfig(
        activeConfigId: Long?,
        activeModelName: String?,
        conversation: Conversation?,
        preferAuxiliaryModel: Boolean = true
    ): Pair<ApiConfig, String>? {
        val settings = personalizationManager.getSettings()
        var rawConfig: ApiConfig? = null
        var targetModel: String = ""

        // 1. 优先使用专属【辅助记忆提炼模型】（若开启且已配置）
        if (preferAuxiliaryModel && settings.auxiliaryMemoryEnabled && settings.auxiliaryMemoryApiConfigId > 0L && settings.auxiliaryMemoryModel.isNotBlank()) {
            rawConfig = getDecryptedConfig(settings.auxiliaryMemoryApiConfigId)
            targetModel = settings.auxiliaryMemoryModel
            Log.d(tag, "时间轴分析：优先调度辅助模型 [${targetModel}] (configId=${settings.auxiliaryMemoryApiConfigId})")
        }

        // 2. 若未启用辅助模型，依次寻找：活动配置 -> 会话绑定配置 -> 全局默认配置 -> 首个有效配置
        if (rawConfig == null || targetModel.isBlank()) {
            val candidateConfigId = activeConfigId?.takeIf { it > 0L }
                ?: conversation?.apiConfigId?.takeIf { it > 0L }
                ?: getDefaultApiConfig()?.id
                ?: getAllApiConfigs().first().firstOrNull()?.id
                ?: 0L

            if (candidateConfigId > 0L) {
                rawConfig = getDecryptedConfig(candidateConfigId)
            }
            if (rawConfig == null) {
                rawConfig = getDefaultApiConfig()?.let { getDecryptedConfig(it.id) }
                    ?: getAllApiConfigs().first().firstOrNull()?.let { getDecryptedConfig(it.id) }
            }

            targetModel = activeModelName?.ifBlank { null }
                ?: conversation?.modelName?.ifBlank { null }
                ?: rawConfig?.modelName.orEmpty()
        }

        if (rawConfig != null && targetModel.isBlank()) {
            targetModel = try {
                selectedModelDao.getEnabledModelsByConfig(rawConfig.id).first().firstOrNull()?.modelName.orEmpty()
            } catch (e: Exception) {
                ""
            }.ifBlank { rawConfig.modelName }
        }

        return if (rawConfig != null && targetModel.isNotBlank()) {
            Pair(rawConfig, targetModel)
        } else null
    }

    /**
     * 单段消息切片的时间线与设定深度提炼
     */
    private suspend fun analyzeTimelineChunk(
        messagesChunk: List<Message>,
        config: ApiConfig,
        targetModel: String,
        fallbackCurrentTime: String?
    ): TimelineReconcileResult {
        val totalChars = messagesChunk.sumOf { it.content.length }
        val messagesToAnalyze = if (totalChars <= 50000) {
            messagesChunk
        } else {
            val head = messagesChunk.take(20)
            val headChars = head.sumOf { it.content.length }
            val tailBudget = 45000 - headChars
            val tail = mutableListOf<Message>()
            var acc = 0
            for (msg in messagesChunk.drop(20).reversed()) {
                if (acc + msg.content.length > tailBudget) break
                tail.add(msg)
                acc += msg.content.length
            }
            (head + tail.reversed()).sortedBy { it.createdAt }
        }

        val formattedHistory = messagesToAnalyze.joinToString("\n") { msg ->
            val contentTrimmed = msg.content.trim()
            if (msg.role == "user") {
                if (TimelineMemoryHelper.isPureDirectorInstruction(contentTrimmed)) {
                    "【编剧写作指导/导演要求】: $contentTrimmed"
                } else {
                    "[用户发言/动作]: $contentTrimmed"
                }
            } else {
                "[助手演出的剧情正文]: $contentTrimmed"
            }
        }

        val prompt = """
            你是一个专业的小说时间线、剧情推进与常驻设定深度提炼专家。
            请通读以下对话记录切片，梳理出故事内部真实的【单向推进叙事时间轴（In-Story Timeline）】、【在各时间点确立的规则与设定】以及【与时间无关的全局角色与世界固有常驻设定（Atemporal Settings）】。

            【特别指导核心准则】：
            1.【时间锚点极致敏感与精准捕捉法则（文学叙事时空深度挖掘）】：
               - 必须以极高敏感度嗅探剧情中所有的显式与隐式时间过渡，严禁遗漏任何细微的时序跃迁与暗线推移！
               - 包含但不限于：
                 ① 显式天数与时段：如 [第1天·清晨]、[第2天·晌午]、[第3天·黄昏]、[第4天·子时]；
                 ② 相对与自然时间跨度：如 [两周过后]、[半个月后]、[三日后·微雨]、[数月后·初冬]、[三年后·重逢]、[次日拂晓]；
                 ③ 季节轮替与阶段节气：如 [暑假开始]、[新学期伊始]、[深秋初雪]、[除夕之夜]、[惊蛰过后]；
                 ④ 篇章转折与时空锚点：如 [回忆·五年前]、[转折之夜]、[决战前夕]、[破晓时刻]；
               - 敏感捕捉文字中潜藏的暗线时间推移（如“聊到了掌灯时分”、“不知不觉窗外泛白”、“大雪封山已过七日”），将其提炼为定位精准的规范时间标签！
               - 时序单向单调递增：剧情正文中若前文已是第2天，后文描写“第二天/次日/又过了一天”，必须合理推断累进为第3天；遇到“两周过后”等跨度词时，自然承接并推进入内部递增序列。

            2.【全方位剧情里程碑事件精炼提炼（极简短句，拒绝流水账与截断）】：
               - 每一条时间线事件必须是【极简凝练、句意完整的剧情事实单句（12~25字）】！
                 格式：主体在何处完成了什么关键事实（例：于临江楼商定行动路线；在山谷击退黑衣人伏击）。
               - 句意必须独立完整，禁止冗余废话，坚决拒绝半截腰斩的残句！
               - 【同一场景/事件高度凝练合并铁律】：
                 当某一件事被描写的很详细、用很多轮对话展开细节时（例如一次聚餐、一场战斗、一次商讨筹划、一次出行），必须整体提炼为 1 条简洁的高层次总结事件！
                 绝对禁止按对话拆分成零碎子动作（严禁分别记录“点餐”、“讨论细节”、“吃完离开”等），同一场景只记录 1 条最终里程碑总结！

            3.【多维固有与常驻设定深度提炼与去重（覆盖 6 大核心维度）】：
               - 敏锐从角色言行、反应、对话及背景中，捕捉具有长久约束力的核心原子事实（每条 8~25 字明确规则事实）：
                 ① 角色特质与心结；② 习惯偏好与小动作；③ 生理特征与禁忌；④ 世界规则与法则限制；⑤ 人际羁绊与誓言契约；⑥ 专属信物与特殊器物。
               - 严禁原句抄录大段抒情，必须凝练为规则属性的原子设定事实，且严禁输出表述相似、含义重复的多条设定！

            4.【严禁截取或抄录用户输入，指令与正文严格解耦（最高铁律）】：
               - 无论是用户的写作指导指令（如“接下来让他们在车站相遇”），还是角色扮演中用户的台词或动作，【严禁直接照抄用户的发言、指令或原句作为事件】！
               - 时间线事件必须是【第三人称客观发生的故事剧情事实】（依据助手演出的情节概括，如“两人在车站碰面达成协议”，绝不可记为“接下来让他们相遇”或“我说走吧”）；
               - 若用户发言是导演指导，必须完全忽略该指令文本，仅依据助手正文中实际演出的情节事实提炼！

            5.【同一事件/连续场景归并与防虚假跨天铁律（绝不允许把一件事拆成多天）】：
               - 现实中一件事情（例如一顿聚餐、一次长谈、一场战斗、一次旅途同车）通常由多轮对话连续进行演进；
               - 严禁将同一个连续场景或同一件事中的各轮次发言错误切分成多天或多顿饭！
               - 除非剧情正文中明确描写了“次日/到了第二天/过了一周”或发生明确的时空跳跃，否则整个进餐、讨论、同游过程属于同一个连续时间节点（如：[第1天·傍晚]），必须合并为一个完整的剧情事件条目，严禁生成“第1天吃饭”、“第2天吃饭”、“第3天吃饭”！
               - 严禁非正向推移的时间跨度暴跳：若剧情出现“两年前”、“这两天”、“数日前”，这属于回忆或口头提及，绝不是故事主线向前推进了两年，严禁将时间标签跃迁为两年前或暴跳 730 天！

            6.【事件与设定界限分明，严禁两头重复（消除跨界一致）】：
               - 时间线事件记录特定时间发生的【动态行动与转折】；固有设定只记录【长期静态规则与属性】；
               - 严禁同一个事实既出现在事件列表、又出现在设定列表！已作为事件发生的，设定中绝不重复收录！

            请严格按照以下 JSON 格式输出，杜绝任何额外客套或解释：
            ```json
            {
              "currentStoryTime": "故事当前停留在的时间节点，如：第3天·夜晚、两周过后、暑假开始等",
              "timelineEvents": [
                {
                  "timeTag": "第1天·上午",
                  "category": "PLOT_EVENT",
                  "content": "两人在车站碰面并达成同行契约（客观完整的剧情里程碑总结）"
                }
              ],
              "atemporalSettings": [
                {
                  "category": "角色特质",
                  "content": "极度重视契约承诺，一旦立誓绝不反悔",
                  "targetScope": "session"
                }
              ]
            }
            ```

            【历史对话记录】：
            $formattedHistory
        """.trimIndent()

        val cfg = config.copy(modelName = targetModel)
        var responseText: String? = null
        var modelException: Exception? = null

        try {
            responseText = if (cfg.apiType == "anthropic") {
                generateAnthropicTimelineAnalysis(cfg, prompt)
            } else {
                generateOpenAITimelineAnalysis(cfg, prompt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            modelException = e
            Log.e(tag, "调用大模型分析时间线切片异常: ${e.message}", e)
        }

        val userMessages = messagesChunk.filter { it.role == "user" }.map { it.content }

        if (!responseText.isNullOrBlank()) {
            val parsedResult = TimelineMemoryHelper.parseModelOutput(responseText, fallbackCurrentTime = fallbackCurrentTime)
            if (parsedResult.events.isNotEmpty() || parsedResult.atemporalSettings.isNotEmpty()) {
                parsedResult.extractionSource = "AI_MODEL"
                parsedResult.modelUsed = targetModel
                return TimelineMemoryHelper.consolidateFinalReconcileResult(parsedResult, userMessages)
            }
        }

        val fallback = fallbackLocalTimelineScan(messagesChunk)
        if (fallback.currentStoryTime.isBlank() || fallback.currentStoryTime == "未确定") {
            fallback.currentStoryTime = fallbackCurrentTime?.takeIf { it.isNotBlank() && it != "未确定" } ?: "第 1 天·起始"
        }
        fallback.extractionSource = "LOCAL_FALLBACK"
        fallback.modelUsed = targetModel
        fallback.extractionErrorMessage = modelException?.message ?: "模型返回解析内容为空"
        return TimelineMemoryHelper.consolidateFinalReconcileResult(fallback, userMessages)
    }

    /**
     * 读取会话全量历史，调用分段提炼 (Map) + 单调时序汇总 (Reduce) 架构对时间推进轴与日常记忆进行提炼
     * 解决超长对话梳理效果差、耗时长且无法取消的问题
     */
    suspend fun reconcileConversationTimeline(
        conversationId: Long,
        activeConfigId: Long? = null,
        activeModelName: String? = null,
        startFromDraft: TimelineReconcileDraft? = null,
        reconcileCheckpoint: TimelineReconcileCheckpoint? = null,
        existingTimelineNodes: List<TimelineNode>? = null,
        onProgress: ((step: Int, total: Int, detail: String) -> Unit)? = null,
        onIntermediateResult: ((TimelineReconcileDraft) -> Unit)? = null
    ): TimelineReconcileResult = withContext(Dispatchers.IO) {
        val allMessages = messageDao.getMessagesList(conversationId)
            .filter { !it.isExcluded && it.role != "system" && it.content.isNotBlank() }
            .sortedBy { it.createdAt }

        val baselineEvents = existingTimelineNodes?.map { node ->
            TimelineEventItem(
                timeTag = node.timeTag,
                content = node.event,
                category = TimelineCategory.fromKey(node.category)
            )
        } ?: emptyList()

        val messages = if (startFromDraft != null && startFromDraft.lastProcessedMessageId > 0L) {
            allMessages.filter { it.id > startFromDraft.lastProcessedMessageId }
        } else if (reconcileCheckpoint != null && reconcileCheckpoint.lastReconciledMessageId > 0L) {
            allMessages.filter { it.id > reconcileCheckpoint.lastReconciledMessageId }
        } else {
            allMessages
        }

        val conversation = conversationDao.getConversationById(conversationId)
        val existingStoryTime = conversation?.currentStoryTime?.takeIf { it.isNotBlank() && it != "未确定" }
            ?: memoryDao.getCandidateMemories(conversationId)
                .firstOrNull { it.content.startsWith("【当前故事时间】：") || it.content.startsWith("当前故事时间：") }
                ?.content?.substringAfter("：")?.trim()

        if (messages.isEmpty() && startFromDraft != null && startFromDraft.events.isNotEmpty()) {
            return@withContext TimelineReconcileResult(
                currentStoryTime = startFromDraft.currentStoryTime,
                events = startFromDraft.events.toMutableList(),
                atemporalSettings = startFromDraft.atemporalSettings.toMutableList(),
                extractionSource = "AI_MODEL",
                lastProcessedMessageId = allMessages.lastOrNull()?.id ?: 0L,
                totalProcessedMessages = allMessages.size
            )
        }

        if (messages.isEmpty() && reconcileCheckpoint != null && baselineEvents.isNotEmpty()) {
            return@withContext TimelineReconcileResult(
                currentStoryTime = existingStoryTime ?: reconcileCheckpoint.storyTimeAtReconciliation ?: "未确定",
                events = baselineEvents.toMutableList(),
                atemporalSettings = mutableListOf(),
                extractionSource = "AI_MODEL",
                lastProcessedMessageId = allMessages.lastOrNull()?.id ?: 0L,
                totalProcessedMessages = allMessages.size
            )
        }

        if (allMessages.isEmpty()) {
            return@withContext TimelineReconcileResult(
                currentStoryTime = "未确定",
                events = mutableListOf(),
                extractionSource = "AI_MODEL",
                lastProcessedMessageId = 0L,
                totalProcessedMessages = 0
            )
        }

        val configPair = resolveTimelineAnalysisConfig(activeConfigId, activeModelName, conversation)

        if (configPair == null) {
            val fallback = fallbackLocalTimelineScan(messages)
            if (fallback.currentStoryTime.isBlank() || fallback.currentStoryTime == "未确定") {
                fallback.currentStoryTime = existingStoryTime?.takeIf { it.isNotBlank() && it != "未确定" } ?: "第 1 天·起始"
            }
            if (baselineEvents.isNotEmpty()) {
                val combined = (baselineEvents + fallback.events).distinctBy { it.content }
                fallback.events.clear()
                fallback.events.addAll(combined)
            }
            fallback.extractionSource = "LOCAL_FALLBACK"
            fallback.extractionErrorMessage = "未找到可用的 API 配置或模型"
            fallback.lastProcessedMessageId = allMessages.lastOrNull()?.id ?: 0L
            fallback.totalProcessedMessages = allMessages.size
            return@withContext fallback
        }

        val (config, targetModel) = configPair
        val userMessages = allMessages.filter { it.role == "user" }.map { it.content }

        // 分段切片：如果消息数 <= 25，直接执行单段分析；如果 > 25，按每 20~25 条切片分段梳理后汇总 (Map-Reduce)
        val chunks = TimelineMemoryHelper.chunkMessagesForAnalysis(messages, chunkSize = 25, overlap = 3)
        if (chunks.size <= 1) {
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(1, 1, if (baselineEvents.isNotEmpty()) "正在结合原有时间线梳理后续对话..." else "正在梳理时间线与核心设定...")
            val singleResult = analyzeTimelineChunk(messages, config, targetModel, existingStoryTime)
            val mergedEvents = if (startFromDraft != null) {
                (startFromDraft.events + singleResult.events).distinctBy { it.content }
            } else singleResult.events
            val mergedSettings = if (startFromDraft != null) {
                (startFromDraft.atemporalSettings + singleResult.atemporalSettings).distinctBy { it.content }
            } else singleResult.atemporalSettings
            val combinedResult = singleResult.copy(
                events = mergedEvents.toMutableList(),
                atemporalSettings = mergedSettings.toMutableList()
            )
            val consolidated = if (baselineEvents.isNotEmpty() || combinedResult.events.size > 2) {
                consolidateTimelineWithModel(combinedResult, config, targetModel, userMessages, baselineEvents)
            } else {
                TimelineMemoryHelper.consolidateFinalReconcileResult(combinedResult, userMessages)
            }
            consolidated.lastProcessedMessageId = allMessages.lastOrNull()?.id ?: 0L
            consolidated.totalProcessedMessages = allMessages.size
            val draft = TimelineReconcileDraft(
                conversationId = conversationId,
                lastProcessedMessageId = allMessages.lastOrNull()?.id ?: 0L,
                currentStoryTime = consolidated.currentStoryTime,
                events = consolidated.events,
                atemporalSettings = consolidated.atemporalSettings,
                isCompleted = true,
                totalChunks = 1,
                processedChunks = 1
            )
            onIntermediateResult?.invoke(draft)
            return@withContext consolidated
        }

        // 多段 Map 阶段
        val aggregatedEvents = mutableListOf<TimelineEventItem>()
        val aggregatedSettings = mutableListOf<AtemporalSettingItem>()
        var latestStoryTime: String = existingStoryTime ?: "第 1 天·起始"

        if (startFromDraft != null) {
            aggregatedEvents.addAll(startFromDraft.events)
            aggregatedSettings.addAll(startFromDraft.atemporalSettings)
            if (startFromDraft.currentStoryTime.isNotBlank() && startFromDraft.currentStoryTime != "未确定") {
                latestStoryTime = startFromDraft.currentStoryTime
            }
        }

        for ((idx, chunk) in chunks.withIndex()) {
            currentCoroutineContext().ensureActive()
            val step = idx + 1
            val progressMsg = if (baselineEvents.isNotEmpty()) {
                "正在梳理后续第 $step/${chunks.size} 阶段对话..."
            } else {
                "正在梳理第 $step/${chunks.size} 阶段对话..."
            }
            onProgress?.invoke(step, chunks.size + 1, progressMsg)

            try {
                val chunkResult = analyzeTimelineChunk(chunk, config, targetModel, latestStoryTime)
                if (chunkResult.currentStoryTime.isNotBlank() && chunkResult.currentStoryTime != "未确定") {
                    latestStoryTime = chunkResult.currentStoryTime
                }
                for (event in chunkResult.events) {
                    val updated = TimelineMemoryHelper.mergeOrAppendEvent(aggregatedEvents, event)
                    aggregatedEvents.clear()
                    aggregatedEvents.addAll(updated)
                }
                for (setting in chunkResult.atemporalSettings) {
                    if (aggregatedSettings.none { it.content == setting.content }) {
                        aggregatedSettings.add(setting)
                    }
                }
                val chunkLastMsgId = chunk.lastOrNull()?.id ?: 0L
                val intermediateDraft = TimelineReconcileDraft(
                    conversationId = conversationId,
                    lastProcessedMessageId = chunkLastMsgId,
                    currentStoryTime = latestStoryTime,
                    events = aggregatedEvents.toList(),
                    atemporalSettings = aggregatedSettings.toList(),
                    isCompleted = false,
                    totalChunks = chunks.size,
                    processedChunks = step
                )
                onIntermediateResult?.invoke(intermediateDraft)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "分段 $step 梳理发生局部异常，继续处理后续分段: ${e.message}")
            }
        }

        currentCoroutineContext().ensureActive()
        onProgress?.invoke(chunks.size + 1, chunks.size + 1, if (baselineEvents.isNotEmpty()) "正在结合原有时间线整体汇总与去重优化..." else "正在汇总各阶段时间线与去重合并...")

        val finalEvents = TimelineMemoryHelper.normalizeMonotonicTimeline(aggregatedEvents)
        val resolvedStoryTime = if (latestStoryTime.isNotBlank() && latestStoryTime != "未确定") {
            latestStoryTime
        } else {
            TimelineMemoryHelper.inferCurrentStoryTime(finalEvents, existingStoryTime)
        }

        val intermediateResult = TimelineReconcileResult(
            currentStoryTime = resolvedStoryTime,
            events = finalEvents.toMutableList(),
            atemporalSettings = aggregatedSettings,
            extractionSource = "AI_MODEL",
            modelUsed = targetModel
        )

        // 执行最后的整体汇总 Pass (Global Consolidation Pass)
        val finalResult = consolidateTimelineWithModel(intermediateResult, config, targetModel, userMessages, baselineEvents)
        finalResult.lastProcessedMessageId = allMessages.lastOrNull()?.id ?: 0L
        finalResult.totalProcessedMessages = allMessages.size
        val finalDraft = TimelineReconcileDraft(
            conversationId = conversationId,
            lastProcessedMessageId = allMessages.lastOrNull()?.id ?: 0L,
            currentStoryTime = finalResult.currentStoryTime,
            events = finalResult.events,
            atemporalSettings = finalResult.atemporalSettings,
            isCompleted = true,
            totalChunks = chunks.size,
            processedChunks = chunks.size
        )
        onIntermediateResult?.invoke(finalDraft)
        finalResult
    }

    /**
     * 全局整体汇总 Pass（解决问题 1、问题 2 与问题 3）
     * 针对分段提炼产生的类似事件（同一件事被多次记录）、极其相似的设定以及事件与设定的跨界重复进行模型智能汇总与去重压缩
     * 支持传入 baselineEvents，实现结合既有时间线对后续增量事件进行全局融合与深化
     */
    private suspend fun consolidateTimelineWithModel(
        rawResult: TimelineReconcileResult,
        config: ApiConfig,
        targetModel: String,
        userMessages: Collection<String> = emptyList(),
        baselineEvents: List<TimelineEventItem> = emptyList()
    ): TimelineReconcileResult {
        if (baselineEvents.isEmpty() && rawResult.events.size <= 2 && rawResult.atemporalSettings.size <= 2) {
            return TimelineMemoryHelper.consolidateFinalReconcileResult(rawResult, userMessages)
        }

        val baselinePromptSection = if (baselineEvents.isNotEmpty()) {
            """
            【此前已确立的时间线（时序基准）】：
            ${baselineEvents.joinToString("\n") { "- [${it.timeTag}] 【${it.category.displayName}】${it.content}" }}

            【后续新对话提炼出的增量事件与改动】：
            """.trimIndent()
        } else {
            "【初步事件列表】：\n"
        }

        val prompt = if (baselineEvents.isNotEmpty()) {
            """
            你是一个专业的小说时间线与常驻设定全局统筹专家。
            本次任务是【结合此前已确立的时间线，对后续新剧情进行整体梳理、深化与去重融合】：
            当前故事停留在：${rawResult.currentStoryTime}

            $baselinePromptSection
            ${rawResult.events.joinToString("\n") { "- [${it.timeTag}] 【${it.category.displayName}】${it.content}" }}

            【初步设定列表】：
            ${rawResult.atemporalSettings.joinToString("\n") { "- 【${it.category}】${it.content}" }}

            请对上述内容进行【结合原有时间线的整体统筹、去重与深化优化】：
            1.【继承时序基准】：【此前已确立的时间线】是已经发生并确认的时序基准。请确保基准时间线中的核心历史事件在因果与时序上得到保全与延续。
            2.【吸收与深化】：后续新增事件（包括在此期间自动记录的事件节点），请精准确定其时间标签、归纳事件内容，并顺畅衔接在基准时间线之后。
            3.【同一事件合并与补充】：如果后续对话是对之前某个事件的补充、反转、完结或延伸，必须在原有事件基础上进行深化合并或改写补充，避免同一事件被多次重复记录！
            4.【设定去重与本质提炼】：提取后续对话中新明确的世界观规则、角色固有设定，消除与事件列表的冗余！
            5.【按时序排列】：确保输出的全部事件按时间发展严格单调递增排列。

            请严格输出以下 JSON：
            ```json
            {
              "currentStoryTime": "${rawResult.currentStoryTime.ifBlank { "第 1 天·起始" }}",
              "timelineEvents": [
                {
                  "timeTag": "时间标签",
                  "category": "PLOT_EVENT",
                  "content": "精简凝练的完整事实（12~25字，拒绝截断）"
                }
              ],
              "atemporalSettings": [
                {
                  "category": "角色核心特质",
                  "content": "精简规则事实（8~25字）",
                  "targetScope": "session"
                }
              ]
            }
            ```
            """.trimIndent()
        } else {
            """
            你是一个专业的小说时间线与常驻设定全局终审专家。
            以下是从全篇长对话中分段提炼出的初步时间线事件和设定列表：
            当前故事停留在：${rawResult.currentStoryTime}

            【初步事件列表】：
            ${rawResult.events.joinToString("\n") { "- [${it.timeTag}] 【${it.category.displayName}】${it.content}" }}

            【初步设定列表】：
            ${rawResult.atemporalSettings.joinToString("\n") { "- 【${it.category}】${it.content}" }}

            请对上述列表进行【最后的整体汇总、去重与浓缩优化】：
            1.【同一事件合并与精炼（拒绝流水账与截断）】：
               - 若某一件事被拆分为了多个细分子事件（如同一顿饭记录了点餐、交谈、吃完等多条，或同一场战斗记录了多次交手，或同一场景反复对话），必须彻底合并为 1 条精炼的最终里程碑总结（12~25字）！
               - 必须是句意完整的主谓宾单句，严禁出现半句腰斩！严禁保留微观动作或高度相似的同一事件！
            2.【设定去重与本质提炼】：
               - 若有多个表述相近、含义重叠的设定，必须合并为 1 条最核心精炼的规则（8~25字），消除所有冗余！
            3.【跨界去重消歧（核心铁律）】：
               - 若某个事实已经在【初步事件列表】中作为特定时空的动态事件记录（例如在某天结识、前往某处、答应某事），【严禁在设定列表中重复记录该事件】！
               - 设定列表只保留长期的静态法则、生理禁忌与常态属性，坚决消除事件与设定两头并存、本质一致的冗余！
            4.【按时序排列】：确保事件按时间发展严格单调递增排列。

            请严格输出以下 JSON：
            ```json
            {
              "currentStoryTime": "${rawResult.currentStoryTime.ifBlank { "第 1 天·起始" }}",
              "timelineEvents": [
                {
                  "timeTag": "时间标签",
                  "category": "PLOT_EVENT",
                  "content": "精简凝练的完整事实（12~25字，拒绝截断）"
                }
              ],
              "atemporalSettings": [
                {
                  "category": "角色核心特质",
                  "content": "精简规则事实（8~25字）",
                  "targetScope": "session"
                }
              ]
            }
            ```
            """.trimIndent()
        }

        try {
            val cfg = config.copy(modelName = targetModel)
            val responseText = if (cfg.apiType == "anthropic") {
                generateAnthropicTimelineAnalysis(cfg, prompt)
            } else {
                generateOpenAITimelineAnalysis(cfg, prompt)
            }
            if (!responseText.isNullOrBlank()) {
                val parsed = TimelineMemoryHelper.parseModelOutput(responseText, rawResult.currentStoryTime)
                if (parsed.events.isNotEmpty() || parsed.atemporalSettings.isNotEmpty()) {
                    parsed.extractionSource = "AI_MODEL"
                    parsed.modelUsed = targetModel
                    return TimelineMemoryHelper.consolidateFinalReconcileResult(parsed, userMessages)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "大模型全局终审汇总异常，使用本地算法去重汇总: ${e.message}")
        }

        val fallbackResult = if (baselineEvents.isNotEmpty()) {
            val combined = (baselineEvents + rawResult.events).distinctBy { it.content }
            rawResult.copy(events = combined.toMutableList())
        } else {
            rawResult
        }
        return TimelineMemoryHelper.consolidateFinalReconcileResult(fallbackResult, userMessages)
    }

    /**
     * 对话结束后模型自动根据当前对话判断是否需要更新时间线（轻量增量评估）
     * 解决“时间线只能手动启动”、“缺少对时间线自动提取”、“错误地停留在当前时空”等痛点
     */
    suspend fun evaluateAndAutoUpdateTimeline(
        conversationId: Long,
        userMessage: String,
        assistantReply: String,
        activeConfigId: Long? = null,
        activeModelName: String? = null,
        sessionMemoryEnabled: Boolean? = null
    ): AutoTimelineUpdateResult? = withContext(Dispatchers.IO) {
        if (userMessage.isBlank() && assistantReply.isBlank()) return@withContext null

        val conversation = conversationDao.getConversationById(conversationId) ?: return@withContext null

        val isRoleplay = hasConversationTag(conversation, "roleplay") || hasConversationTag(conversation, "story")
        val currentTimelineNodes = timelineNodeDao?.getTimelineNodes(conversationId) ?: emptyList()

        // 核心规范：时间线功能与会话记忆联动，或只要存在时空节点/时间线数据即自动开启
        val effectiveMemoryEnabled = sessionMemoryEnabled ?: conversation.enableSessionMemory
        val isSessionMemoryEnabled = effectiveMemoryEnabled == true || (isRoleplay && effectiveMemoryEnabled != false)
        val hasActiveTimeline = !conversation.currentStoryTime.isNullOrBlank() || currentTimelineNodes.isNotEmpty()
        if (!isSessionMemoryEnabled && !hasActiveTimeline) {
            return@withContext null
        }

        val totalLength = userMessage.length + assistantReply.length
        if (totalLength < 8) return@withContext null

        val existingStoryTime = conversation.currentStoryTime?.takeIf { it.isNotBlank() && it != "未确定" }
            ?: memoryDao.getCandidateMemories(conversationId)
                .firstOrNull { it.content.startsWith("【当前故事时间】：") || it.content.startsWith("当前故事时间：") }
                ?.content?.substringAfter("：")?.trim() ?: "第 1 天·起始"

        val recentNodes = currentTimelineNodes.takeLast(4)
        val recentNodesContext = if (recentNodes.isEmpty()) {
            "（暂无先前已记录的事件）"
        } else {
            recentNodes.joinToString("\n") { node ->
                "- [ID: ${node.id}] [${node.timeTag}] 【${TimelineCategory.fromKey(node.category).displayName}】${node.event}"
            }
        }

        val configPair = resolveTimelineAnalysisConfig(activeConfigId, activeModelName, conversation)
        var modelStoryTime: String? = null
        var newEvent: TimelineEventItem? = null
        var parsedAction: String = "APPEND"
        var parsedTargetNodeId: Long? = null
        var newSettings = emptyList<AtemporalSettingItem>()
        var extractionError: String? = if (configPair == null) "未找到可用的提取模型配置" else null
        val existingSettings = memoryDao.getConversationMemories(conversationId)

        if (configPair != null) {
            val (config, targetModel) = configPair

            val prompt = """
                你是一个专业的故事时间线与常驻设定增量提取引擎。
                已知当前故事停留在时间节点：【$existingStoryTime】。

                【已记录的时间线最近事件列表】：
                $recentNodesContext

                【已确认的会话常驻设定（不要重复提取）】：
                ${existingSettings.joinToString("\n") { it.content }.take(6000)}

                以下是最新的一轮对话交互：
                [用户发言/指令]: ${userMessage.take(4000)}
                [助手剧情正文]: ${if (assistantReply.length <= 24000) assistantReply else assistantReply.take(12000) + "\n（超长正文中段省略）\n" + assistantReply.takeLast(12000)}

                请深度理解正文对话，敏锐判断并智能提取：
                1.【时间流逝与时空防篡改铁律（核心）】：
                   - 对话中角色活动是否发生变化或结束？时间是否有自然向前推移？
                   - 【默认守时与单步推进铁律（核心）】：时间默认保持原时段不变，宁可输出 NO_UPDATE，也绝不可提前推进！
                     单轮至多顺延一个相邻时段（清晨→上午→中午→下午→傍晚→入夜→深夜）；若未描写明确的时间流逝却输出跨越多个时段的新时间，属于违规推进，必须改为保持原时间或 NO_UPDATE。
                   - 【防虚假篡改铁律（核心）】：若当前故事时间设定为早上/清晨/上午/白天【$existingStoryTime】，严禁在正文没有描写数小时大跨度时间流逝（如夕阳西下、夜幕降临、漫长的一天过去）的情况下，擅自将时间篡改为晚上/夜间！
                     角色之间在此处的常规交谈、问候早餐、商讨计划、回忆昨晚往事或规划今晚安排，均属于当前早晨/上午时段内的活动，故事时间必须坚定保持在原早晨/上午时段或平滑顺延（如早晨推至上午、上午推至中午），绝对禁止违背设定直接篡改为晚上！
                   - 仅当正文明确描写剧情告一段落、场景转移或明确描写日内时段流转（如由清晨行至正午、由午后聊至傍晚、待到夜幕降临）时，才推断并输出推进后的精确故事时间；
                   - 若本轮对话依然在同一时段同一场景紧密对话，活动尚未有显著时段推进时，必须保持原时间。
                2.【多轮事件修改补充 vs 新增事件（拒绝流水账重复，核心铁律）】：
                   - 现实中一件事情往往由多轮对话连续进行（如同一场交谈、同一顿饭、同一场战斗、同一个场景的活动、同一个任务的前后进展）；
                   - 如果当前对话属于过往已记录事件（见上述【已记录的时间线最近事件列表】）的延续、细节补充、深入推进或同一事件的收尾，【严禁新增独立重复事件】！
                     只有新事件与目标节点明确属于同一时间点才设置 "action": "UPDATE"，并在 "targetNodeId" 中填入该事件对应的数值 ID，在 "newEvent" 中给出【融合旧事件与新进展后的单条完整新描述】（12~28字完整单句）。时间点不同或不确定，即使人物、场景或内容相似也必须 APPEND，禁止用后续时段总结覆盖早期事件！
                   - 仅当真正发生了不同场景、不同时段、不同性质的全新独立重大事件时，才设置 "action": "APPEND"，此时 "targetNodeId" 设为 null。
                3.【同一场景归并与防虚假跨天铁律】：
                   - 严禁将同一个连续场景或同一件事（如一顿饭、一次促膝长谈、一场战斗）错误拆分成多天多顿饭！
                   - 若对话中出现“两年前”、“这两天”、“数日前”，这属于回忆或提及，绝不可当成故事推进并跃迁两年！
                4.【严禁直接截取或搬运用户输入】：
                   - 依据助手正文中实际演出的情节事实提炼，严禁出现“用户”、“AI”、“助手”等元词汇。

                注意：
                1. 独立提取正文中新增、明确成立的常驻角色特质、习惯偏好、生理禁忌、世界规则与人际羁绊，写入 atemporalSettings；临时动作、事件和导演指令不算设定，不得记录为现实用户的全局偏好。没有时间推进也可以提取事件或设定。
                2. 仅当时间、事件、常驻设定三者均无变化时输出 NO_UPDATE；没有新事件时 newEvent 为 null，没有新设定时 atemporalSettings 为 []。无时间变化时 newStoryTime 为 null。
                3. 若有变化，请输出以下纯 JSON：
                ```json
                {
                  "newStoryTime": "推移后的故事时间节点",
                  "action": "UPDATE 或 APPEND",
                  "targetNodeId": 123,
                  "newEvent": {
                    "timeTag": "事件发生的具体时间标签，如：第 1 天·黄昏、第 2 天·清晨",
                    "category": "PLOT_EVENT",
                    "content": "精简完整的客观事实（12~28字，拒绝截断与元词汇）"
                  },
                  "atemporalSettings": [{"category": "世界规则", "content": "正文中新确认的长期规则"}]
                }
                ```
            """.trimIndent()

            var analysisText = try {
                val cfg = config.copy(modelName = targetModel)
                if (cfg.apiType == "anthropic") {
                    generateAnthropicTimelineAnalysis(cfg, prompt, conversationId)
                } else {
                    generateOpenAITimelineAnalysis(cfg, prompt, conversationId)
                }
            } catch (e: Exception) {
                if (isRequestCancellation(e)) throw CancellationException("本轮提取已取消", e)
                extractionError = "模型提取请求失败，请检查模型配置、网络权限或服务端日志"
                Log.w(tag, "自动评估时间线更新异常: ${e.message}")
                null
            }

            // 模型未按格式输出（无 JSON 且无无变化表述）时追加一次严格格式重试：仅一次，
            // 取消与 HTTP 错误不重试；JSON 形似但非法时不重试（模型已在尝试结构化输出，重试意义不大）。
            if (!analysisText.isNullOrBlank()) {
                val firstStripped = TimelineMemoryHelper.stripThinkingTags(analysisText)
                if (Regex("""\{[\s\S]*\}""").find(firstStripped) == null &&
                    !TimelineMemoryHelper.isNoChangeLikeResponse(firstStripped)
                ) {
                    currentCoroutineContext().ensureActive()
                    Log.d(tag, "增量提取追加严格格式重试")
                    analysisText = try {
                        val strictPrompt = "$prompt\n\n注意：只输出纯 JSON 或 NO_UPDATE，不要输出任何解释文字。"
                        val cfg = config.copy(modelName = targetModel)
                        if (cfg.apiType == "anthropic") {
                            generateAnthropicTimelineAnalysis(cfg, strictPrompt, conversationId)
                        } else {
                            generateOpenAITimelineAnalysis(cfg, strictPrompt, conversationId)
                        }
                    } catch (e: Exception) {
                        if (isRequestCancellation(e)) throw CancellationException("本轮提取已取消", e)
                        Log.w(tag, "增量提取严格格式重试失败: ${e.message}")
                        analysisText
                    }
                }
            }

            // 增量提取结果判定（顺序不可调换）：
            // 1. 先找 JSON：模型按指令返回结构化结果时优先解析，不受正文措辞影响；
            // 2. 无 JSON 但有自然语言无变化表述（NO_UPDATE 或中文“无变化/没有推进”等）时静默无提案；
            // 3. 既无 JSON 又无无变化表述时才提示格式错误（另记日志前 200 字备查）。
            if (!analysisText.isNullOrBlank()) {
                val stripped = TimelineMemoryHelper.stripThinkingTags(analysisText)
                val jsonMatcher = Regex("""\{[\s\S]*\}""").find(stripped)
                if (jsonMatcher != null) {
                    try {
                        val jsonObj = JsonParser.parseString(jsonMatcher.value).asJsonObject
                        modelStoryTime = jsonObj.get("newStoryTime")?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotBlank() && it != "未确定" && it != "未知" }
                        val rawAction = jsonObj.get("action")?.takeIf { !it.isJsonNull }?.asString?.trim()?.uppercase()
                        if (rawAction == "UPDATE" || rawAction == "APPEND") {
                            parsedAction = rawAction
                        }
                        parsedTargetNodeId = jsonObj.get("targetNodeId")?.takeIf { !it.isJsonNull }?.asLong
                        val newEventObj = jsonObj.get("newEvent")?.takeIf { it.isJsonObject }?.asJsonObject
                        if (newEventObj != null) {
                            val tag = newEventObj.get("timeTag")?.asString?.trim().orEmpty()
                            val content = newEventObj.get("content")?.asString?.trim().orEmpty()
                            val cat = TimelineCategory.fromKey(newEventObj.get("category")?.asString)
                            if (content.isNotBlank() && !TimelineMemoryHelper.isInvalidOrUserInstructionEvent(content, listOf(userMessage))) {
                                val cleanContent = TimelineMemoryHelper.compactSentenceKeepComplete(content, 28)
                                if (cleanContent.isNotBlank() && !TimelineMemoryHelper.isInvalidOrUserInstructionEvent(cleanContent, listOf(userMessage))) {
                                    newEvent = TimelineEventItem(timeTag = tag, content = cleanContent, category = cat)
                                }
                            }
                        }
                        newSettings = TimelineMemoryHelper.parseModelOutput(jsonMatcher.value, existingStoryTime)
                            .atemporalSettings.filter { setting ->
                                setting.content.isNotBlank() &&
                                    !TimelineMemoryHelper.isInvalidOrUserInstructionEvent(setting.content, listOf(userMessage)) &&
                                    existingSettings.none { it.content.substringAfter("】", it.content).trim() == setting.content.trim() } &&
                                    setting.content.trim() != newEvent?.content?.trim()
                            }.distinctBy { it.content.trim() }.map { it.copy(targetScope = "session") }
                    } catch (_: Exception) {
                        extractionError = "模型提取结果格式无效，请重试或手动梳理"
                    }
                } else if (TimelineMemoryHelper.isNoChangeLikeResponse(stripped)) {
                    Log.d(tag, "增量提取模型判定本轮无变化，保持静默")
                } else {
                    extractionError = "模型 $targetModel 未返回有效的提取 JSON，请重试或手动梳理"
                    Log.w(tag, "增量提取无 JSON 且无无变化表述，前200字: ${stripped.take(200)}")
                }
            } else if (extractionError == null) {
                extractionError = "模型未返回提取内容，请重试或手动梳理"
            }
        }

        // 防大模型幻觉篡改：若原故事时间为早晨/白天，模型擅自推断为“晚上/入夜/夜间”，且助手正文中无夕阳/黑夜真实描写，坚决拒绝篡改
        val isOriginalMorningOrDay = existingStoryTime?.let {
            it.contains("清晨") || it.contains("早晨") || it.contains("早上") || it.contains("上午") || it.contains("晨") || it.contains("白天")
        } ?: false
        val isModelOutputNight = modelStoryTime?.let {
            it.contains("晚") || it.contains("夜") || it.contains("暮")
        } ?: false
        if (isOriginalMorningOrDay && isModelOutputNight) {
            val hasEveningTransition = assistantReply.contains("夕阳西下") || assistantReply.contains("夜幕降临") ||
                assistantReply.contains("直到晚上") || assistantReply.contains("天色彻底黑了下来") || assistantReply.contains("转眼到了晚上")
            if (!hasEveningTransition) {
                Log.w(tag, "拦截到大模型试图在无时移描写的情况下将早晨/白天篡改为夜晚: $modelStoryTime，保持早晨/顺延时段")
                modelStoryTime = null
            }
        }

        // 防大模型幻觉篡改②（防过度推进）：同日内跨多个时段的跳跃式推进（如上午→傍晚），
        // 且正文无明确时间流逝描写时坚决拒绝，早晨的事还在发生就不允许被推进到晚上
        if (modelStoryTime != null &&
            TimelineMemoryHelper.isUnreasonableStoryTimeJump(existingStoryTime, modelStoryTime, assistantReply)
        ) {
            Log.w(tag, "拦截到同日跨多时段的跳跃式时间推进（正文缺乏时间流逝描写）: $modelStoryTime，保持原时间")
            modelStoryTime = null
        }

        // 本地启发式推演时空推进兜底（防止大模型漏判或网络未响应导致错误停滞在当前时空）
        val localAdvancedTime = TimelineMemoryHelper.detectAutoStoryTimeAdvancement(existingStoryTime, userMessage, assistantReply)
        val resolvedNewStoryTime = modelStoryTime ?: localAdvancedTime

        val isStoryTimeChanged = !resolvedNewStoryTime.isNullOrBlank() && resolvedNewStoryTime != existingStoryTime
        val hasEvent = newEvent != null && newEvent.content.isNotBlank() && !TimelineMemoryHelper.isInvalidOrUserInstructionEvent(newEvent.content, listOf(userMessage))

        currentCoroutineContext().ensureActive()
        if (conversationDao.getConversationById(conversationId) == null) return@withContext null
        if (!isStoryTimeChanged && !hasEvent && newSettings.isEmpty() && extractionError == null) return@withContext null

        // 匹配修改目标节点
        var resolvedTargetNode: TimelineNode? = null
        if (hasEvent) {
            val eventTime = newEvent!!.timeTag.ifBlank { resolvedNewStoryTime ?: existingStoryTime.orEmpty() }
            val existingNodes = (timelineNodeDao?.getTimelineNodes(conversationId) ?: emptyList()).filter {
                TimelineMemoryHelper.isSameTimelineEventTime(it.timeTag, eventTime)
            }
            if (parsedAction == "UPDATE") {
                resolvedTargetNode = if (parsedTargetNodeId != null && parsedTargetNodeId > 0L) {
                    existingNodes.firstOrNull { it.id == parsedTargetNodeId }
                } else null
                if (resolvedTargetNode == null) {
                    resolvedTargetNode = existingNodes.takeLast(4).firstOrNull { node ->
                        val cleanNode = node.event.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
                        val cleanIncoming = newEvent!!.content.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
                        cleanNode.contains(cleanIncoming) || cleanIncoming.contains(cleanNode) ||
                        (cleanNode.length >= 6 && cleanIncoming.length >= 6 && cleanNode.take(6) == cleanIncoming.take(6))
                    }
                }
            } else {
                // 如果是 APPEND 但与最后一条高度重叠，智能转为 UPDATE
                resolvedTargetNode = existingNodes.takeLast(2).firstOrNull { node ->
                    val cleanNode = node.event.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
                    val cleanIncoming = newEvent!!.content.replace(Regex("""[，。！？、\s\[\]【】"”'’]"""), "")
                    cleanNode.contains(cleanIncoming) || cleanIncoming.contains(cleanNode) ||
                    (cleanNode.length >= 8 && cleanIncoming.length >= 8 && cleanNode.take(8) == cleanIncoming.take(8))
                }
            }
        }

        val finalAction = if (resolvedTargetNode != null) "UPDATE" else if (hasEvent) "APPEND" else if (newSettings.isNotEmpty()) "SETTINGS_ONLY" else "TIME_ONLY"

        val notice = buildString {
            append("本轮时间线与设定提取结果")
            if (isStoryTimeChanged && !resolvedNewStoryTime.isNullOrBlank()) {
                append(" · 故事时间【$resolvedNewStoryTime】")
            }
            if (hasEvent && newEvent != null) {
                if (finalAction == "UPDATE" && resolvedTargetNode != null) {
                    append(" · 补充更新事件：${newEvent.content.take(22)}")
                } else {
                    append(" · 新增事件：${newEvent.content.take(22)}")
                }
            }
            if (newSettings.isNotEmpty()) append(" · 新设定 ${newSettings.size} 条")
        }

        return@withContext AutoTimelineUpdateResult(
            updatedStoryTime = if (isStoryTimeChanged) resolvedNewStoryTime else null,
            newEvent = if (hasEvent) newEvent else null,
            summaryNotice = notice,
            action = finalAction,
            targetNodeId = resolvedTargetNode?.id,
            previousEventContent = resolvedTargetNode?.event,
            atemporalSettings = newSettings,
            extractionErrorMessage = extractionError
        )
    }

    /** Apply only reviewed incremental facts; never replace the full timeline or save global story facts. */
    suspend fun applyAutoTimelineProposal(conversationId: Long, proposal: AutoTimelineUpdateResult) = withContext(Dispatchers.IO) {
        val db = com.aiassistant.AiAssistantApp.instance.database
        db.withTransaction {
            val conversation = conversationDao.getConversationById(conversationId) ?: return@withTransaction
            proposal.updatedStoryTime?.trim()?.takeIf { it.isNotBlank() }?.let { updateStoryTime(conversationId, it) }
            proposal.newEvent?.takeIf { it.content.isNotBlank() }?.let { event ->
                val dao = timelineNodeDao ?: error("时间线存储不可用")
                val nodes = dao.getTimelineNodes(conversationId)
                val eventTime = event.timeTag.ifBlank { proposal.updatedStoryTime ?: conversation.currentStoryTime ?: "未确定" }
                val existing = if (proposal.action == "UPDATE") nodes.firstOrNull {
                    it.id == proposal.targetNodeId && TimelineMemoryHelper.isSameTimelineEventTime(it.timeTag, eventTime)
                } else null
                if (existing != null) {
                    dao.updateTimelineNode(existing.copy(timeTag = event.timeTag.ifBlank { existing.timeTag },
                        event = event.content.trim(), category = event.category.key, updatedAt = System.currentTimeMillis()))
                } else if (nodes.none { it.event == event.content.trim() && it.timeTag == eventTime }) {
                    dao.insertTimelineNode(TimelineNode(conversationId = conversationId,
                        timeTag = eventTime,
                        event = event.content.trim(), category = event.category.key,
                        orderIndex = (nodes.maxOfOrNull { it.orderIndex } ?: -1) + 1))
                }
            }
            val session = db.roleplaySessionDao().getSessionByConversationId(conversationId)
            for (setting in proposal.atemporalSettings.filter { it.isSelected && it.content.isNotBlank() }) {
                val formatted = "【${setting.category}】${setting.content.trim()}"
                if (memoryDao.getConversationMemories(conversationId).none { it.content == formatted }) {
                    addConversationMemory(conversationId, formatted)
                }
                if (session != null && db.roleplayMemoryDao().getMemoriesListBySession(session.id).none { it.content == formatted }) {
                    db.roleplayMemoryDao().insertMemory(RoleplayMemory(sessionId = session.id,
                        memoryType = "fact", content = formatted, isPinned = true))
                }
            }
        }
    }

    private suspend fun generateOpenAITimelineAnalysis(config: ApiConfig, prompt: String, conversationId: Long = 0L): String? {
        val normalizedUrl = normalizeApiBaseUrl(config.baseUrl, config.apiType)
        val completionLimit = ReasoningControls.policy(config.modelName,
            ReasoningCapabilityCatalog.get(config.baseUrl, config.apiType, config.modelName)).usesMaxCompletionTokens
        val request = ChatCompletionRequest(
            model = config.modelName,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            temperature = null,
            max_tokens = if (completionLimit) null else 8192,
            max_completion_tokens = if (completionLimit) 8192 else null,
            stream = false
        )
        val allKeys = parseApiKeys(config.apiKey).ifEmpty { listOf(config.apiKey) }.let {
            if (personalizationManager.isBackupKeyFallbackEnabled()) it else it.take(1)
        }
        var lastException: Exception? = null

        for (key in allKeys) {
            val retryPolicy = personalizationManager.getRetryPolicy()
            for (attempt in 1..(com.aiassistant.domain.model.RetryPolicy.MAX_RETRIES + 1)) {
                try {
                    val response = awaitMemoryCall(RetrofitClient.getAnalysisService(normalizedUrl)
                        .chatCompletion(RetrofitClient.formatApiKey(key), request), conversationId)
                    if (!response.isSuccessful) {
                        val errBody = response.errorBody()?.string()?.take(300).orEmpty()
                        throw Exception("HTTP ${response.code()}: $errBody")
                    }
                    val body = response.body()
                    if (body?.error != null) {
                        throw Exception(body.error.message ?: "OpenAI API 返回错误")
                    }
                    val choice = body?.choices?.firstOrNull()
                    val content = choice?.message?.content?.ifBlank { null }
                        ?: choice?.message?.reasoning_content?.ifBlank { null }
                    if (!content.isNullOrBlank()) return content
                    throw Exception("模型未返回有效内容")
                } catch (e: Exception) {
                    lastException = e
                    Log.w(tag, "时间线分析 (OpenAI) Key报错或请求异常 (attempt $attempt): ${e.message}")
                    if (isRequestCancellation(e)) throw e
                    if (e is com.aiassistant.data.remote.HttpAccessDeniedException) throw e
                    if (retryPolicy.canRetry(e, attempt - 1)) {
                        kotlinx.coroutines.delay(retryPolicy.delayMillis(attempt - 1))
                        continue
                    }
                    break // 尝试下一个 Key
                }
            }
        }
        throw lastException ?: Exception("未能获取大模型有效输出")
    }

    private suspend fun generateAnthropicTimelineAnalysis(config: ApiConfig, prompt: String, conversationId: Long = 0L): String? {
        val normalizedUrl = normalizeApiBaseUrl(config.baseUrl, config.apiType)
        val request = AnthropicRequest(
            model = config.modelName,
            messages = listOf(AnthropicMessage(role = "user", content = prompt)),
            max_tokens = 8192,
            temperature = null
        )
        val allKeys = parseApiKeys(config.apiKey).ifEmpty { listOf(config.apiKey) }.let {
            if (personalizationManager.isBackupKeyFallbackEnabled()) it else it.take(1)
        }
        var lastException: Exception? = null

        for (key in allKeys) {
            val retryPolicy = personalizationManager.getRetryPolicy()
            for (attempt in 1..(com.aiassistant.domain.model.RetryPolicy.MAX_RETRIES + 1)) {
                try {
                    val response = awaitMemoryCall(RetrofitClient.getAnalysisService(normalizedUrl)
                        .anthropicMessages(
                            apiKey = key.removePrefix("Bearer ").trim(),
                            request = request
                        ), conversationId)
                    if (!response.isSuccessful) {
                        val errBody = response.errorBody()?.string()?.take(300).orEmpty()
                        throw Exception("HTTP ${response.code()}: $errBody")
                    }
                    val content = response.body()?.content?.firstOrNull { it.type == "text" }?.text
                    if (!content.isNullOrBlank()) return content
                    throw Exception("模型未返回有效内容")
                } catch (e: Exception) {
                    lastException = e
                    Log.w(tag, "时间线分析 (Anthropic) Key报错或请求异常 (attempt $attempt): ${e.message}")
                    if (isRequestCancellation(e)) throw e
                    if (e is com.aiassistant.data.remote.HttpAccessDeniedException) throw e
                    if (retryPolicy.canRetry(e, attempt - 1)) {
                        kotlinx.coroutines.delay(retryPolicy.delayMillis(attempt - 1))
                        continue
                    }
                    break // 尝试下一个 Key
                }
            }
        }
        throw lastException ?: Exception("未能获取大模型有效输出")
    }

    private fun fallbackLocalTimelineScan(messages: List<Message>): TimelineReconcileResult {
        val events = mutableListOf<TimelineEventItem>()
        val atemporalSettings = mutableListOf<AtemporalSettingItem>()
        var inferredCurrentTime = "未确定"
        val dayPattern = java.util.regex.Pattern.compile("""(?:第\s*(\d+)\s*天|DAY\s*(\d+))""", java.util.regex.Pattern.CASE_INSENSITIVE)

        var currentTrackedDay = 1
        var hasSeenEventsOnCurrentDay = false

        for (index in messages.indices) {
            val msg = messages[index]
            val content = msg.content.trim()
            if (content.isBlank()) continue

            // 1. 过滤纯用户导演指令（绝不将其原样塞入事件流）
            if (TimelineMemoryHelper.isPureDirectorInstruction(content)) {
                // 如果指令里提到天数推进
                val dMatch = dayPattern.matcher(content)
                if (dMatch.find()) {
                    val d = (dMatch.group(1) ?: dMatch.group(2))?.toIntOrNull() ?: currentTrackedDay
                    if (d > currentTrackedDay) {
                        currentTrackedDay = d
                        hasSeenEventsOnCurrentDay = false
                    }
                }
                val jumpDays = TimelineMemoryHelper.estimateTimeSpanJumpDays(content)
                if (jumpDays > 0) {
                    currentTrackedDay += jumpDays
                    hasSeenEventsOnCurrentDay = false
                }

                // 寻找紧随其后的助手正文，尝试提炼正文事实
                val nextAssistant = messages.getOrNull(index + 1)?.takeIf { it.role == "assistant" }
                if (nextAssistant != null && nextAssistant.content.isNotBlank()) {
                    val summary = nextAssistant.content.lines().firstOrNull { l ->
                        l.length in 10..120 && !l.startsWith("[") && !l.startsWith("【")
                    }?.trim()
                    if (!summary.isNullOrBlank()) {
                        val timeTag = when {
                            content.contains("两周") -> "两周过后"
                            content.contains("暑假") -> "暑假开始"
                            content.contains("一个月") || content.contains("次月") -> "一个月后"
                            content.contains("半年") -> "半年后"
                            content.contains("一年") -> "一年后"
                            else -> "第 $currentTrackedDay 天·剧情展开"
                        }
                        if (events.none { it.content == summary }) {
                            events.add(
                                TimelineEventItem(
                                    timeTag = timeTag,
                                    content = summary,
                                    category = TimelineCategory.PLOT_EVENT
                                )
                            )
                            hasSeenEventsOnCurrentDay = true
                        }
                    }
                }
                continue
            }

            // 2. 时序累进状态机：处理显式天数、相对次日与自然时间跨度
            val matcher = dayPattern.matcher(content)
            val spanJump = TimelineMemoryHelper.estimateTimeSpanJumpDays(content)
            if (spanJump > 0) {
                currentTrackedDay += spanJump
                hasSeenEventsOnCurrentDay = false
            } else if (matcher.find()) {
                val d = (matcher.group(1) ?: matcher.group(2))?.toIntOrNull() ?: currentTrackedDay
                if (d > currentTrackedDay) {
                    currentTrackedDay = d
                    hasSeenEventsOnCurrentDay = false
                } else if (d <= currentTrackedDay && hasSeenEventsOnCurrentDay && (content.contains("第二天") || content.contains("次日") || content.contains("翌日"))) {
                    // 关键累进：若已在第 2 天及以上，文本再次出现“第二天”，表示次日剧情，必须累加天数！
                    currentTrackedDay++
                    hasSeenEventsOnCurrentDay = false
                }
            } else if (content.contains("第二天") || content.contains("次日") || content.contains("翌日") || content.contains("又过了一天") || content.contains("隔天")) {
                if (hasSeenEventsOnCurrentDay || currentTrackedDay >= 1) {
                    currentTrackedDay++
                    hasSeenEventsOnCurrentDay = false
                }
            }

            // 3. 敏锐挖掘与时间无关的 6 维多维常驻设定 (atemporalSettings)
            // ① 生理禁忌
            val physiologicalKeywords = listOf("过敏", "畏寒", "怕冷", "旧伤", "夜盲", "不沾酒", "不能喝酒", "弱点", "体虚", "旧疾", "残疾", "毒素")
            // ② 习惯偏好
            val habitKeywords = listOf("习惯", "偏好", "喜欢喝", "喜欢吃", "总是会", "经常在", "随身带着", "习惯于", "嗜好", "口癖", "小动作")
            // ③ 身份过往与角色特质
            val identityKeywords = listOf("真实身份", "其实是", "隐姓埋名", "秘密", "身世", "曾经历过", "来自", "执念", "心结", "底线", "阵营")
            // ④ 世界规则与法则约束
            val ruleKeywords = listOf("禁止", "必须遵守", "世界规则", "法则", "结界", "严禁", "代价", "禁令", "宵禁", "律法", "禁区", "反噬")
            // ⑤ 人际羁绊与誓言契约
            val bondKeywords = listOf("保护", "绝不原谅", "唯一信任", "誓言", "承诺", "约定", "底线", "生死契约", "血契")
            // ⑥ 专属信物与特殊器物
            val itemKeywords = listOf("信物", "佩戴", "随身器物", "徽章", "玉佩", "戒指", "佩剑", "魔杖", "纹章", "刻印", "法器")

            val matchedCat = when {
                ruleKeywords.any { content.contains(it) } -> "世界规则"
                physiologicalKeywords.any { content.contains(it) } -> "生理禁忌"
                habitKeywords.any { content.contains(it) } -> "习惯偏好"
                identityKeywords.any { content.contains(it) } -> "角色特质"
                bondKeywords.any { content.contains(it) } -> "人际羁绊"
                itemKeywords.any { content.contains(it) } -> "专属信物"
                else -> null
            }

            if (matchedCat != null) {
                val candidateLine = content.lines().firstOrNull { l ->
                    listOf(physiologicalKeywords, habitKeywords, identityKeywords, ruleKeywords, bondKeywords, itemKeywords)
                        .flatten().any { l.contains(it) }
                }?.trim()

                // 严控设定提炼质量：长篇文学抒情长句不当作规则设定
                if (candidateLine != null && candidateLine.length <= 45 &&
                    !candidateLine.contains("目光") && !candidateLine.contains("神情") &&
                    !candidateLine.contains("微皱") && !candidateLine.contains("叹息")
                ) {
                    val cleanLine = candidateLine.replace("“", "").replace("”", "").replace("\"", "").trim()
                    if (cleanLine.length in 4..38 && atemporalSettings.none { it.content == cleanLine }) {
                        atemporalSettings.add(
                            AtemporalSettingItem(
                                category = matchedCat,
                                content = cleanLine,
                                isSelected = true,
                                targetScope = if (matchedCat == "世界规则") "global" else "session"
                            )
                        )
                    }
                }
            }

            // 4. 提取显式时间线事件或剧情转折正文（仅限 assistant 正文，杜绝提取用户输入）
            if (msg.role == "assistant" && (content.startsWith("[") || content.startsWith("【"))) {
                val item = TimelineMemoryHelper.parseContentToEvent(content)
                val cleanContent = TimelineMemoryHelper.compactSentenceKeepComplete(item.content, 28)
                val cleanItem = item.copy(content = cleanContent)
                if (cleanItem.content.isNotBlank() && cleanItem.timeTag.isNotBlank() && events.none { it.content == cleanItem.content }) {
                    events.add(cleanItem)
                    hasSeenEventsOnCurrentDay = true
                }
            } else if (msg.role == "assistant" && content.length in 12..250 &&
                listOf("前往", "来到", "决定", "相遇", "发现", "答应", "拒绝", "战斗", "救下", "商量", "告别", "突破", "坦白", "重逢").any { content.contains(it) }) {
                val timeTag = "第 $currentTrackedDay 天·剧情节点"
                val rawSummary = content.lines().firstOrNull { l ->
                    listOf("前往", "来到", "决定", "相遇", "发现", "答应", "拒绝", "战斗", "救下", "商量", "告别", "突破", "坦白", "重逢").any { l.contains(it) }
                }?.trim() ?: content
                val cleanSummary = TimelineMemoryHelper.compactSentenceKeepComplete(rawSummary, 28)

                if (cleanSummary.isNotBlank() && events.none { it.content == cleanSummary }) {
                    events.add(
                        TimelineEventItem(
                            timeTag = timeTag,
                            content = cleanSummary,
                            category = TimelineCategory.PLOT_EVENT
                        )
                    )
                    hasSeenEventsOnCurrentDay = true
                }
            }
        }

        val normalized = TimelineMemoryHelper.normalizeMonotonicTimeline(events)
        if (currentTrackedDay > 1 || normalized.isNotEmpty()) {
            inferredCurrentTime = TimelineMemoryHelper.inferCurrentStoryTime(normalized).ifBlank { "第 $currentTrackedDay 天" }
        }

        return TimelineReconcileResult(
            currentStoryTime = inferredCurrentTime,
            events = normalized.toMutableList(),
            atemporalSettings = atemporalSettings
        )
    }

    private suspend fun buildRelevantMemoryBlock(
        conversation: Conversation,
        currentUserMessage: String,
        tokenBudget: Int,
        options: ChatRequestOptions? = null
    ): String? {
        val isRoleplay = isRoleplayConversation(conversation)
        // 角色扮演会话中严格隔离跨会话全局长期记忆，防止外部工作/代码等日常偏好污染小说剧情
        val extMemoryEnabled = if (isRoleplay) false else (options?.enableExternalMemory ?: conversation.enableExternalMemory ?: false)

        val candidates = memoryDao.getCandidateMemories(conversation.id).filter { it.isEnabled }
        if (candidates.isEmpty()) return null

        // 会话专属记忆在所有对话（包括普通对话、私密对话、角色扮演）中只要开启均生效
        val sessionMemories = if (options?.enableSessionMemory == false) {
            emptyList()
        } else {
            candidates.filter { it.scope == "conversation" && it.conversationId == conversation.id }
        }
        val longTermMemories = if (extMemoryEnabled) {
            candidates.filter { it.scope == "user" }
        } else {
            emptyList()
        }

        fun isDirectiveOrConstraint(memory: MemoryItem): Boolean {
            val lower = memory.content.lowercase(java.util.Locale.ROOT)
            val constraintMarkers = listOf(
                "行为约束", "用户偏好", "约束", "规则", "规范",
                "不允许", "禁止", "切勿", "不要", "避免", "严禁", "不许", "不得", "不能",
                "必须", "始终", "永远", "称呼", "叫我", "自称", "身份", "尊称", "规矩",
                "雷区", "禁忌", "别叫", "不要叫", "格式要求", "不准", "特助", "老板"
            )
            return constraintMarkers.any { lower.contains(it) }
        }

        val (sessionDirectives, normalSessionMemories) = sessionMemories.partition { isDirectiveOrConstraint(it) }
        val (longTermDirectives, normalLongTermMemories) = longTermMemories.partition { isDirectiveOrConstraint(it) }
        val allDirectives = (sessionDirectives + longTermDirectives).distinctBy { it.content.trim() }

        val blocks = mutableListOf<String>()

        // 1. 核心行为准则与绝对约束（100% 无条件注入，最高约束级别，解决需求 4c）
        if (allDirectives.isNotEmpty()) {
            val directiveLines = allDirectives.map { "- [绝对准则] ${SmartMemoryExtractor.sanitizeMetaLanguage(it.content.trim())}" }.joinToString("\n")
            blocks += """
                【核心行为准则与绝对约束（最高优先级，必须严格无条件遵守）】
                以下是用户已确认并生效的核心行为准则、称谓规范与输出禁令。在任何对话与输出中均拥有最高绝对效力，必须无条件执行：
                $directiveLines
                【执行铁律】：凡涉及上述禁止性称谓、措辞或行为（例如严禁使用“老板”、“特助”等任何称谓），在任何回复中绝对严禁出现，哪怕未被显式提醒也必须绝对回避！
            """.trimIndent()
        }

        // 2. 独立时间线注入 (<session_timeline>)
        val timelineNodes = try {
            timelineNodeDao?.getTimelineNodes(conversation.id) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val currentStoryTime = conversation.currentStoryTime?.takeIf { it.isNotBlank() && it != "未确定" }
            ?: normalSessionMemories.firstOrNull { it.content.trim().startsWith("【当前故事时间】：") || it.content.trim().startsWith("当前故事时间：") }
                ?.content?.substringAfter("：")?.trim()

        val shouldInjectTimelineContext = isRoleplay || (currentStoryTime != null) || timelineNodes.isNotEmpty()
        if (shouldInjectTimelineContext && (currentStoryTime != null || timelineNodes.isNotEmpty())) {
            val timelineContext = TimelineMemoryHelper.buildTimelineNodesPromptContext(nodes = timelineNodes, currentStoryTime = currentStoryTime)
            blocks += timelineContext
        }

        // 3. 独立会话专属设定与规则注入 (<session_memory>)，彻底过滤掉旧的时间线数据
        val cleanSessionSettings = normalSessionMemories
            .filter { mem ->
                val trimmed = mem.content.trim()
                !trimmed.startsWith("【当前故事时间】：") &&
                !trimmed.startsWith("当前故事时间：") &&
                !TimelineMemoryHelper.isExplicitTimelineEvent(trimmed)
            }
            .map { SmartMemoryExtractor.sanitizeMetaLanguage(it.content.trim()) }

        if (cleanSessionSettings.isNotEmpty()) {
            val lines = cleanSessionSettings.joinToString("\n") { "- $it" }
            blocks += "<session_memory>\n【当前会话专属设定与规则】：\n$lines\n</session_memory>"
        }

        if (normalLongTermMemories.isNotEmpty()) {
            val queryTerms = tokenizeForMemory(currentUserMessage)
            val queryEntities = extractEntitiesFromQuery(currentUserMessage)
            val ranked = normalLongTermMemories
                .map { it to scoreMemory(it, queryTerms, queryEntities, conversation.id) }
                .filter { (_, score) -> score >= 0.22f }
                .sortedWith(compareByDescending<Pair<MemoryItem, Float>> { it.second }
                    .thenByDescending { it.first.updatedAt })

            val lines = mutableListOf<String>()
            var usedTokens = 0
            for ((memory, _) in ranked) {
                val line = "- ${memory.content}"
                val cost = estimateTokenCount(line) + 8
                if (lines.isNotEmpty() && usedTokens + cost > tokenBudget) break
                lines += line
                usedTokens += cost
            }
            if (lines.isNotEmpty()) {
                blocks += "<cross_session_long_term_memory>\n【跨会话长期背景与偏好参考（全局用户画像）】\n${lines.joinToString("\n")}\n</cross_session_long_term_memory>"
            }
        }

        if (blocks.isEmpty()) return null
        val memoryBody = blocks.joinToString("\n\n")
        return "<system_memory_context>\n$memoryBody\n\n【记忆与约束执行指引】：请严格优先执行上述核心行为准则与绝对约束；其余记忆、时间线与事实供你在构思方案和回答时自然参考与遵循，在未被用户明确询问时，无需机械复述这些条目。\n</system_memory_context>"
    }

    private fun scoreMemory(
        memory: MemoryItem,
        queryTerms: Set<String>,
        queryEntities: Set<String>,
        conversationId: Long
    ): Float {
        return AdvancedMemoryEngine.calculateHybridScore(
            memory = memory,
            queryTerms = queryTerms,
            queryEntities = queryEntities,
            conversationId = conversationId
        )
    }

    private fun extractEntitiesFromQuery(text: String): Set<String> {
        val entities = mutableSetOf<String>()
        Regex("""[“"《『【]([^“”"》』】]{2,20})[”"》』】]""").findAll(text).forEach {
            entities.add(it.groupValues[1].trim())
        }
        Regex("""\b[A-Z][a-zA-Z0-9_\-]{2,25}\b""").findAll(text).forEach {
            entities.add(it.value.trim())
        }
        return entities
    }

    private fun tokenizeForMemory(text: String): Set<String> {
        val lower = text.lowercase()
        val result = linkedSetOf<String>()
        Regex("[a-z0-9_\\-]{3,}").findAll(lower).forEach { result += it.value }
        Regex("[\\u4E00-\\u9FFF]{2,}").findAll(lower).forEach { match ->
            val value = match.value
            if (value.length <= 12) result += value
            value.windowed(2).forEach { result += it }
            if (value.length >= 3) value.windowed(3).forEach { result += it }
        }
        return result.take(120).toSet()
    }

    private fun addAnthropicHistoryMessage(
        messages: MutableList<AnthropicMessage>,
        role: String,
        content: String
    ) {
        if (content.isBlank()) return
        val last = messages.lastOrNull()
        if (last?.role == role && last.content is String) {
            messages[messages.lastIndex] = last.copy(content = "${last.content}\n\n$content")
        } else {
            messages.add(AnthropicMessage(role = role, content = content))
        }
    }

    // 构建OpenAI格式的用户消息（支持多模态）
    private fun buildUserMessage(text: String, attachments: List<Attachment>): Any {
        if (attachments.isEmpty()) {
            return text
        }

        val contentParts = mutableListOf<ContentPart>()

        // 构建完整的文本内容（包含文件内容）
        val fullText = StringBuilder()
        if (text.isNotBlank()) {
            fullText.append(text)
        }

        // 添加文本文件内容
        val textFiles = attachments.filter { it.textContent != null }
        if (textFiles.isNotEmpty()) {
            if (fullText.isNotEmpty()) fullText.append("\n\n")
            fullText.append("--- 附件内容 ---\n")
            textFiles.forEach { attachment ->
                fullText.append("\n文件: ${attachment.name}\n")
                fullText.append("```\n")
                fullText.append(attachment.textContent)
                fullText.append("\n```\n")
            }
        }

        // 添加文本部分
        if (fullText.isNotEmpty()) {
            contentParts.add(ContentPart(type = "text", text = fullText.toString()))
        }

        // 添加图片附件（多模态）
        attachments.filter { it.base64Data != null && FileUtils.isImage(it.mimeType, it.name) }.forEach { attachment ->
            contentParts.add(
                ContentPart(
                    type = "image_url",
                    image_url = ImageUrl(url = "data:${attachment.payloadMimeType ?: if (attachment.base64Data?.startsWith("/9j/") == true) "image/jpeg" else attachment.mimeType};base64,${attachment.base64Data}")
                )
            )
        }

        // 如果只有文本内容，直接返回字符串
        if (contentParts.size == 1 && contentParts[0].type == "text") {
            return contentParts[0].text ?: text
        }

        return contentParts
    }

    // 构建Anthropic格式的用户消息（支持多模态）
    private fun buildAnthropicUserMessage(text: String, attachments: List<Attachment>): Any {
        if (attachments.isEmpty()) {
            return text
        }

        val contentParts = mutableListOf<AnthropicContent>()

        // 构建完整的文本内容（包含文件内容）
        val fullText = StringBuilder()
        if (text.isNotBlank()) {
            fullText.append(text)
        }

        // 添加文本文件内容
        val textFiles = attachments.filter { it.textContent != null }
        if (textFiles.isNotEmpty()) {
            if (fullText.isNotEmpty()) fullText.append("\n\n")
            fullText.append("--- 附件内容 ---\n")
            textFiles.forEach { attachment ->
                fullText.append("\n文件: ${attachment.name}\n")
                fullText.append("```\n")
                fullText.append(attachment.textContent)
                fullText.append("\n```\n")
            }
        }

        // 添加文本部分
        if (fullText.isNotEmpty()) {
            contentParts.add(AnthropicContent(type = "text", text = fullText.toString()))
        }

        // 添加图片附件（多模态）
        attachments.filter { it.base64Data != null && FileUtils.isImage(it.mimeType, it.name) }.forEach { attachment ->
            contentParts.add(
                AnthropicContent(
                    type = "image",
                    source = AnthropicImageSource(
                        type = "base64",
                        media_type = attachment.payloadMimeType ?: if (attachment.base64Data?.startsWith("/9j/") == true) "image/jpeg" else attachment.mimeType,
                        data = attachment.base64Data!!
                    )
                )
            )
        }

        return contentParts
    }

    private fun parseStopSequences(json: String?): List<String>? {
        if (json.isNullOrBlank()) return null
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    private fun List<Message>.dropLastCurrentUserMessage(userMessage: String): List<Message> {
        val collapsed = collapseVariantsForHistory()
        val last = collapsed.lastOrNull()
        return if (last?.role == "user" && last.content == userMessage) {
            collapsed.dropLast(1)
        } else {
            collapsed
        }
    }

    private fun List<Message>.collapseVariantsForHistory(): List<Message> {
        val groups = filter { !it.variantGroupId.isNullOrBlank() }
            .groupBy { it.variantGroupId!! }
        val consumedGroups = mutableSetOf<String>()
        val result = mutableListOf<Message>()

        forEach { message ->
            val groupId = message.variantGroupId
            if (groupId.isNullOrBlank()) {
                result += message
                return@forEach
            }
            if (!consumedGroups.add(groupId)) return@forEach

            val selected = groups[groupId]
                .orEmpty()
                .maxWithOrNull(compareBy<Message> { it.variantIndex }.thenBy { it.createdAt })
                ?: message
            result += selected
        }

        return result
    }

    suspend fun generateConversationTitle(conversationId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val settings = personalizationManager.getSettings()
            if (!settings.autoNameEnabled) {
                return@withContext null
            }

            val conversation = getConversationById(conversationId) ?: return@withContext null

            // API 配置选择：若设置了独立自动命名配置则优先使用，否则使用当前对话的配置
            val targetConfigId = if (settings.autoNameApiConfigId > 0L) settings.autoNameApiConfigId else conversation.apiConfigId
            val rawConfig = getDecryptedConfig(targetConfigId) ?: getDecryptedConfig(conversation.apiConfigId) ?: return@withContext null

            // 模型选择：若设置了指定自动命名模型则覆盖配置模型，否则沿用配置模型
            val targetModel = settings.autoNameModel.trim().ifBlank { rawConfig.modelName }
            val config = rawConfig.copy(modelName = targetModel)

            val messages = getMessagesList(conversationId).take(8)
            if (messages.isEmpty()) return@withContext null

            val transcript = messages.joinToString("\n") { message ->
                val role = when (message.role) {
                    "user" -> "用户"
                    "assistant" -> "助手"
                    else -> message.role
                }
                "$role: ${message.content.take(500)}"
            }

            val customPrompt = settings.autoNamePrompt.trim().ifBlank {
                "请根据下面这段对话，生成一个简短精炼的中文标题。\n要求：严格在12个字以内，禁止使用任何标点符号、书名号或引号，禁止包含'标题'、'关于'等前缀废话，只直接输出最终标题。"
            }
            val prompt = """
                $customPrompt

                【对话内容】
                $transcript
            """.trimIndent()

            val title = if (config.apiType == "anthropic") {
                generateAnthropicTitle(config, prompt)
            } else {
                generateOpenAITitle(config, prompt)
            }

            sanitizeGeneratedTitle(title)
        } catch (e: Exception) {
            Log.w(tag, "自动生成标题失败", e)
            null
        }
    }

    suspend fun testAutoNaming(
        apiConfigId: Long,
        modelName: String,
        testText: String,
        customPrompt: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rawConfig = getDecryptedConfig(apiConfigId) ?: return@withContext Result.failure(Exception("API配置不存在"))
            val targetModel = modelName.trim().ifBlank { rawConfig.modelName }
            val config = rawConfig.copy(modelName = targetModel)
            val promptTemplate = customPrompt.trim().ifBlank {
                "请根据下面这段用户发言或对话，生成一个简短精炼的中文标题。\n要求：严格在12个字以内，禁止使用任何标点符号、书名号或引号，禁止包含'标题'、'关于'等前缀废话，只直接输出最终标题。"
            }
            val prompt = """
                $promptTemplate

                【对话内容】
                用户: ${testText.take(500)}
            """.trimIndent()
            val raw = if (config.apiType == "anthropic") {
                generateAnthropicTitle(config, prompt)
            } else {
                generateOpenAITitle(config, prompt)
            }
            val sanitized = sanitizeGeneratedTitle(raw)
            if (sanitized.isNullOrBlank()) {
                Result.failure(Exception("未能生成有效标题，原始返回: ${raw ?: "空"}"))
            } else {
                Result.success(sanitized)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun generateOpenAITitle(config: ApiConfig, prompt: String): String? {
        val request = ChatCompletionRequest(
            model = config.modelName,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            temperature = null,
            max_tokens = 2048,
            max_completion_tokens = 2048,
            stream = false
        )
        val response = RetrofitClient.getService(config.baseUrl)
            .chatCompletion(RetrofitClient.formatApiKey(config.apiKey), request)
            .execute()
        if (!response.isSuccessful) return null
        return response.body()?.choices?.firstOrNull()?.message?.content
    }

    private suspend fun generateAnthropicTitle(config: ApiConfig, prompt: String): String? {
        val request = AnthropicRequest(
            model = config.modelName,
            messages = listOf(AnthropicMessage(role = "user", content = prompt)),
            max_tokens = 2048,
            temperature = null
        )
        val response = RetrofitClient.getService(config.baseUrl)
            .anthropicMessages(apiKey = config.apiKey, request = request)
            .execute()
        if (!response.isSuccessful) return null
        return response.body()?.content?.firstOrNull()?.text
    }

    suspend fun executeStreamingCompletion(
        config: ApiConfig,
        key: String,
        prompt: String,
        maxTokens: Int
    ): String? {
        return try {
            val request = ChatCompletionRequest(
                model = config.modelName,
                messages = listOf(ChatMessage(role = "user", content = prompt)),
                temperature = null,
                max_tokens = maxTokens,
                max_completion_tokens = maxTokens,
                stream = true
            )
            val auth = RetrofitClient.formatApiKey(key)
            val response = RetrofitClient.postJson(
                baseUrl = config.baseUrl,
                path = "chat/completions",
                headers = mapOf(
                    "Authorization" to auth,
                    "Accept" to "text/event-stream",
                    "Cache-Control" to "no-cache"
                ),
                json = gson.toJson(request)
            )
            response.use { okResponse ->
                if (!okResponse.isSuccessful) return@use null
                val body = okResponse.body ?: return@use null
                val reader = body.charStream().buffered()
                val contentBuilder = StringBuilder()
                val thinkingBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val lineStr = line?.trim().orEmpty()
                    if (lineStr.isBlank()) continue
                    val chunk = parseOpenAiStreamLine(lineStr, gson) ?: continue
                    chunk.contentDelta?.let { contentBuilder.append(it) }
                    chunk.thinkingDelta?.let { thinkingBuilder.append(it) }
                }
                val res = contentBuilder.toString().trim()
                if (res.isNotBlank()) res else thinkingBuilder.toString().trim().ifBlank { null }
            }
        } catch (e: Exception) {
            Log.w(tag, "executeStreamingCompletion 流式备用异常: ${e.message}")
            null
        }
    }

    suspend fun executeQuickCompletionWithResult(
        config: ApiConfig,
        prompt: String,
        maxTokens: Int = 1000
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isEnabled) {
            return@withContext Result.failure(Exception("当前 API 配置已关闭，请在设置中开启后重试"))
        }
        val normalizedUrl = normalizeApiBaseUrl(config.baseUrl, config.apiType)
        val allKeys = parseApiKeys(config.apiKey).let {
            if (personalizationManager.isBackupKeyFallbackEnabled()) it else it.take(1)
        }
        if (allKeys.isEmpty()) {
            return@withContext Result.failure(Exception("当前 API 未配置已启用的 Key，请在 API 配置中开启至少一个 Key"))
        }
        var lastException: Exception? = null

        for (key in allKeys) {
            val cleanKey = key.removePrefix("Bearer ").trim()
            val retryPolicy = personalizationManager.getRetryPolicy()
            for (attempt in 1..(com.aiassistant.domain.model.RetryPolicy.MAX_RETRIES + 1)) {
                try {
                    if (config.apiType == "anthropic") {
                        val request = AnthropicRequest(
                            model = config.modelName,
                            messages = listOf(AnthropicMessage(role = "user", content = prompt)),
                            max_tokens = maxTokens,
                            temperature = null
                        )
                        val response = RetrofitClient.getAnalysisService(normalizedUrl)
                            .anthropicMessages(apiKey = cleanKey, request = request)
                            .execute()
                        if (!response.isSuccessful) {
                            val errBody = response.errorBody()?.string()?.take(300).orEmpty()
                            throw Exception("HTTP ${response.code()}: $errBody")
                        }
                        val text = response.body()?.content?.firstOrNull { it.type == "text" }?.text
                            ?: response.body()?.content?.firstOrNull()?.text
                        if (!text.isNullOrBlank()) {
                            return@withContext Result.success(text.trim())
                        }
                    } else {
                        // 优先尝试标准非流式请求
                        val request = ChatCompletionRequest(
                            model = config.modelName,
                            messages = listOf(ChatMessage(role = "user", content = prompt)),
                            temperature = null,
                            max_tokens = maxTokens,
                            stream = false
                        )
                        val response = RetrofitClient.getAnalysisService(normalizedUrl)
                            .chatCompletion(RetrofitClient.formatApiKey(cleanKey), request)
                            .execute()
                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body?.error != null) {
                                throw Exception(body.error.message ?: "OpenAI API 返回错误")
                            }
                            val choice = body?.choices?.firstOrNull()
                            val text = choice?.message?.content?.ifBlank { null }
                                ?: choice?.message?.reasoning_content?.ifBlank { null }
                            if (!text.isNullOrBlank()) {
                                return@withContext Result.success(text.trim())
                            }
                        } else {
                            val errBody = response.errorBody()?.string()?.take(300).orEmpty()
                            if (response.code() !in setOf(400, 405, 415, 501)) throw Exception("HTTP ${response.code()}: $errBody")
                            Log.w(tag, "executeQuickCompletion 非流式 HTTP ${response.code()}: $errBody，尝试流式通道备用")
                        }

                        // 非流式未返回或网关仅支持流式时，自动启用流式保底通道
                        val streamResult = executeStreamingCompletion(
                            config = config.copy(baseUrl = normalizedUrl),
                            key = cleanKey,
                            prompt = prompt,
                            maxTokens = maxTokens
                        )
                        if (!streamResult.isNullOrBlank()) {
                            return@withContext Result.success(streamResult.trim())
                        }
                    }
                    throw Exception("模型未返回有效内容")
                } catch (e: Exception) {
                    lastException = e
                    Log.w(tag, "executeQuickCompletion Key报错 (attempt $attempt): ${e.message}")
                    if (isRequestCancellation(e)) throw e
                    if (e is com.aiassistant.data.remote.HttpAccessDeniedException) return@withContext Result.failure(e)
                    if (retryPolicy.canRetry(e, attempt - 1)) {
                        kotlinx.coroutines.delay(retryPolicy.delayMillis(attempt - 1))
                        continue
                    }
                    break // 尝试下一个 Key
                }
            }
        }
        Result.failure(lastException ?: Exception("模型未返回有效输出内容"))
    }

    suspend fun executeQuickCompletion(config: ApiConfig, prompt: String, maxTokens: Int = 1000): String? =
        executeQuickCompletionWithResult(config, prompt, maxTokens).getOrNull()

    suspend fun translateThinkingContent(
        thinkingText: String,
        targetApiConfigId: Long = 0L,
        targetModelName: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (thinkingText.isBlank()) {
                return@withContext Result.failure(Exception("待翻译内容为空"))
            }

            val rawConfig = (if (targetApiConfigId > 0L) getDecryptedConfig(targetApiConfigId) else null)
                ?: getDefaultApiConfig()?.let { getDecryptedConfig(it.id) }
                ?: getAllApiConfigs().first().firstOrNull()?.let { getDecryptedConfig(it.id) }
                ?: return@withContext Result.failure(Exception("未找到可用的 API 配置用于翻译"))

            val targetModel = targetModelName.trim().ifBlank { rawConfig.modelName }
            val effectiveConfig = rawConfig.copy(modelName = targetModel)

            val prompt = """
                请将以下 AI 模型的深度思考过程（Chain-of-Thought / 思维链）翻译为流畅、自然、符合中文表达习惯的简体中文。
                要求：
                1. 完整保留原有的思考逻辑、推理步骤、数学推导、代码标记和技术术语；
                2. 保持原有的思考语气（如自言自语、第一人称分析等）；
                3. 绝对不要添加任何额外的开场白、解释说明、翻译备注或结语，只直接输出翻译后的思考链正文。

                【待翻译思考过程】
                $thinkingText
            """.trimIndent()

            val maxOut = (thinkingText.length * 2).coerceIn(1024, 4096)
            executeQuickCompletionWithResult(effectiveConfig, prompt, maxTokens = maxOut)
        } catch (e: Exception) {
            Log.e(tag, "思考链翻译异常", e)
            Result.failure(e)
        }
    }

    // ============ 统计相关 ============

    fun getAllUsageStats(): Flow<List<ApiUsageStat>> = usageStatDao.getAllStats()

    fun getUsageStatsByTimeRange(startTime: Long, endTime: Long): Flow<List<ApiUsageStat>> =
        usageStatDao.getStatsByTimeRange(startTime, endTime)

    suspend fun getUsageStatsListByTimeRange(startTime: Long, endTime: Long): List<ApiUsageStat> =
        usageStatDao.getStatsListByTimeRange(startTime, endTime)

    suspend fun getDailyStats(days: Int = 30): List<DailyStats> {
        val startTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        return usageStatDao.getDailyStatsSince(startTime)
    }

    suspend fun getModelStats(days: Int = 30): List<ModelStats> {
        val startTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        return usageStatDao.getModelStatsSince(startTime)
    }

    suspend fun getTotalTokens(days: Int = 30): Int {
        val startTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        return usageStatDao.getTotalTokensSince(startTime) ?: 0
    }

    suspend fun getThinkingTokens(days: Int = 30): Int {
        val startTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        return usageStatDao.getThinkingTokensSince(startTime) ?: 0
    }

    suspend fun getRequestCount(days: Int = 30): Int {
        val startTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        return usageStatDao.getRequestCountSince(startTime) ?: 0
    }

    suspend fun getAvgResponseTime(days: Int = 30): Long {
        val startTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        return usageStatDao.getAvgResponseTimeSince(startTime) ?: 0
    }

    // ============ 环境变量相关 ============

    fun getEnvironmentVariables(environment: String = "default"): Flow<List<EnvironmentVariable>> =
        environmentVariableDao.getVariablesByEnvironment(environment)

    fun getAllEnvironmentVariables(): Flow<List<EnvironmentVariable>> =
        environmentVariableDao.getAllVariables()

    suspend fun saveEnvironmentVariable(variable: EnvironmentVariable): Long {
        val encrypted = variable.copy(value = cryptoManager.encrypt(variable.value))
        return if (variable.id == 0L) {
            environmentVariableDao.insertVariable(encrypted)
        } else {
            environmentVariableDao.updateVariable(encrypted)
            variable.id
        }
    }

    suspend fun deleteEnvironmentVariable(variable: EnvironmentVariable) =
        environmentVariableDao.deleteVariable(variable)

    suspend fun getDecryptedVariable(name: String, environment: String): String? {
        val variable = environmentVariableDao.getVariableByName(name, environment) ?: return null
        return try {
            cryptoManager.decrypt(variable.value)
        } catch (e: Exception) {
            Log.e(tag, "解密环境变量失败", e)
            null
        }
    }

    suspend fun getAllEnvironments(): List<String> =
        environmentVariableDao.getAllEnvironments()

    // 替换字符串中的环境变量引用 {{VAR_NAME}}
    suspend fun resolveEnvironmentVariables(text: String, environment: String = "default"): String {
        var resolved = text
        val regex = "\\{\\{(.+?)\\}\\}".toRegex()
        regex.findAll(text).forEach { match ->
            val varName = match.groupValues[1]
            val value = getDecryptedVariable(varName, environment)
            if (value != null) {
                resolved = resolved.replace(match.value, value)
            }
        }
        return resolved
    }

    // ============ 提示词模板相关 ============

    fun getAllPromptTemplates(): Flow<List<PromptTemplate>> = promptTemplateDao.getAllTemplates()

    fun getPromptTemplatesByCategory(category: String): Flow<List<PromptTemplate>> =
        promptTemplateDao.getTemplatesByCategory(category)

    suspend fun getPromptTemplateById(id: Long): PromptTemplate? =
        promptTemplateDao.getTemplateById(id)

    suspend fun savePromptTemplate(template: PromptTemplate): Long {
        return if (template.id == 0L) {
            promptTemplateDao.insertTemplate(template)
        } else {
            promptTemplateDao.updateTemplate(template)
            template.id
        }
    }

    suspend fun deletePromptTemplate(template: PromptTemplate) =
        promptTemplateDao.deleteTemplate(template)

    suspend fun incrementTemplateUseCount(id: Long) =
        promptTemplateDao.incrementUseCount(id)

    suspend fun getAllTemplateCategories(): List<String> =
        promptTemplateDao.getAllCategories()

    // ============ 会话分支相关 ============

    fun getConversationBranches(parentId: Long): Flow<List<ConversationBranch>> =
        conversationBranchDao.getBranchesByParent(parentId)

    suspend fun createBranch(parentId: Long, branchMessageId: Long, childId: Long): Long {
        val branch = ConversationBranch(
            parentConversationId = parentId,
            branchMessageId = branchMessageId,
            childConversationId = childId
        )
        return conversationBranchDao.insertBranch(branch)
    }

    /**
     * 事务级完整创建会话分支：原子批量落库消息、继承活跃配置与会话设定、克隆角色扮演Session与记忆
     */
    suspend fun createBranchConversation(
        parentId: Long,
        branchMessageId: Long,
        sourceMessages: List<Message>,
        activeApiConfigId: Long? = null,
        activeModelName: String? = null
    ): Pair<Long, String> = withContext(Dispatchers.IO) {
        val originalConv = conversationDao.getConversationById(parentId)
            ?: throw IllegalStateException("原会话不存在: $parentId")

        val isParentHidden = hasConversationTag(originalConv, "hidden") ||
            originalConv.tags?.contains("hidden") == true
        val branchTags = if (isParentHidden) {
            updateTag(originalConv.tags, "hidden", true)
        } else {
            originalConv.tags
        }

        // 计算全局唯一、优雅单调自增的分支标题（彻底解决 XX(分支1) 重复问题，需求 1）
        val rootBaseTitle = extractRootBaseTitle(originalConv.title)
        val existingTitles = conversationDao.getTitlesStartingWith(rootBaseTitle)
        val branchTitle = calculateNextBranchTitle(rootBaseTitle, existingTitles)

        val effectiveApiConfigId = activeApiConfigId ?: originalConv.apiConfigId
        val effectiveModelName = (activeModelName?.takeIf { it.isNotBlank() }) ?: originalConv.modelName

        val db = com.aiassistant.AiAssistantApp.instance.database

        val newConversationId = db.withTransaction {
            // 1. 创建新会话实体（完整继承原会话的所有高级参数、滚动摘要、提示词与配置）
            val newConversation = Conversation(
                title = branchTitle,
                folderId = originalConv.folderId,
                apiConfigId = effectiveApiConfigId,
                modelName = effectiveModelName,
                systemPrompt = originalConv.systemPrompt,
                rollingSummary = originalConv.rollingSummary,
                summaryUpdatedMessageId = null,
                summaryUpdatedAt = originalConv.summaryUpdatedAt,
                totalTokens = 0,
                messageCount = 0,
                isPinned = false,
                tags = branchTags,
                temperature = originalConv.temperature,
                maxTokens = originalConv.maxTokens,
                topP = originalConv.topP,
                enableThinking = originalConv.enableThinking,
                thinkingEffort = originalConv.thinkingEffort,
                enableWebSearch = originalConv.enableWebSearch,
                enableSessionMemory = originalConv.enableSessionMemory,
                enableReplyDirections = originalConv.enableReplyDirections,
                replyDirectionCount = originalConv.replyDirectionCount,
                contextWindowTokens = originalConv.contextWindowTokens,
                currentStoryTime = originalConv.currentStoryTime,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newId = conversationDao.insertConversation(newConversation)

            // 2. 准备待复制的消息序列：完整保留纳入分支历史的单条消息所有历史版本（需求 7）
            val allParentMessages = messageDao.getMessagesList(parentId)
            val targetGroupIds = mutableSetOf<String>()
            val targetStandaloneIds = mutableSetOf<Long>()

            if (!sourceMessages.isNullOrEmpty()) {
                val targetIdx = sourceMessages.indexOfFirst { it.id == branchMessageId }
                val effectiveSlice = if (targetIdx >= 0) sourceMessages.subList(0, targetIdx + 1) else sourceMessages
                for (msg in effectiveSlice) {
                    if (!msg.variantGroupId.isNullOrBlank()) {
                        targetGroupIds.add(msg.variantGroupId!!)
                    } else {
                        targetStandaloneIds.add(msg.id)
                    }
                }
            } else {
                val branchMsg = allParentMessages.firstOrNull { it.id == branchMessageId }
                val maxTime = branchMsg?.createdAt ?: Long.MAX_VALUE
                for (msg in allParentMessages) {
                    if (msg.createdAt <= maxTime) {
                        if (!msg.variantGroupId.isNullOrBlank()) {
                            targetGroupIds.add(msg.variantGroupId!!)
                        } else {
                            targetStandaloneIds.add(msg.id)
                        }
                    }
                }
            }

            val messagesToCopy = allParentMessages.filter { msg ->
                (!msg.variantGroupId.isNullOrBlank() && msg.variantGroupId in targetGroupIds) ||
                (msg.id in targetStandaloneIds)
            }.sortedWith(compareBy<Message> { it.createdAt }.thenBy { it.variantIndex })

            val groupIdMapping = mutableMapOf<String, String>()
            targetGroupIds.forEach { oldGroup ->
                groupIdMapping[oldGroup] = "${oldGroup}_b${newId}"
            }

            val baseTime = System.currentTimeMillis() - (messagesToCopy.size * 1000L)
            val preparedMessages = messagesToCopy.mapIndexed { index, msg ->
                val newGroupId = msg.variantGroupId?.let { groupIdMapping[it] ?: "${it}_b${newId}" }
                msg.copy(
                    id = 0,
                    conversationId = newId,
                    variantGroupId = newGroupId,
                    variantIndex = msg.variantIndex,
                    createdAt = baseTime + (index * 1000L)
                )
            }

            // 批量一次性原子落库
            if (preparedMessages.isNotEmpty()) {
                messageDao.insertMessages(preparedMessages)
                val totalTokens = preparedMessages.sumOf { it.tokenCount }
                conversationDao.updateStats(newId, preparedMessages.size, totalTokens)
            }

            // 3. 继承隐藏属性确保生效
            if (isParentHidden) {
                conversationDao.updateTags(newId, branchTags)
            }

            // 4. 深度克隆 Roleplay 剧情会话与记忆
            try {
                val roleplayRepo = com.aiassistant.AiAssistantApp.instance.roleplayRepository
                val rpSession = roleplayRepo.getSessionByConversationId(parentId)
                if (rpSession != null) {
                    val clonedSessionId = roleplayRepo.insertSession(
                        rpSession.copy(
                            id = 0,
                            conversationId = newId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    val sessionMemories = roleplayRepo.getMemoriesListBySession(rpSession.id)
                    sessionMemories.forEach { mem ->
                        roleplayRepo.insertMemory(
                            mem.copy(
                                id = 0,
                                sessionId = clonedSessionId
                            )
                        )
                    }
                }
            } catch (rpEx: Exception) {
                Log.e(tag, "克隆角色扮演会话异常", rpEx)
            }

            // 5. 复制普通会话专属记忆
            try {
                val convMemories = memoryDao.getConversationMemories(parentId)
                convMemories.forEach { mem ->
                    memoryDao.insertMemory(
                        mem.copy(
                            id = 0,
                            conversationId = newId
                        )
                    )
                }
            } catch (memEx: Exception) {
                Log.e(tag, "克隆会话专属记忆异常", memEx)
            }

            // 5.5 克隆时间线节点
            try {
                val nodes = timelineNodeDao?.getTimelineNodes(parentId) ?: emptyList()
                if (nodes.isNotEmpty()) {
                    val clonedNodes = nodes.map { it.copy(id = 0, conversationId = newId, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()) }
                    timelineNodeDao?.insertTimelineNodes(clonedNodes)
                }
            } catch (tlEx: Exception) {
                Log.e(tag, "克隆时间线节点异常", tlEx)
            }

            // 6. 记录分支关联表
            val branch = ConversationBranch(
                parentConversationId = parentId,
                branchMessageId = branchMessageId,
                childConversationId = newId
            )
            conversationBranchDao.insertBranch(branch)

            newId
        }

        Pair(newConversationId, branchTitle)
    }

    suspend fun getBranchByChild(childId: Long): ConversationBranch? =
        conversationBranchDao.getBranchByChild(childId)

    suspend fun deleteBranch(branch: ConversationBranch) =
        conversationBranchDao.deleteBranch(branch)

    /**
     * 完整复制整个对话生成新对话：
     * 包含全量消息流、模型与高级配置、会话专属记忆、角色卡与场景世界观关联及剧情专属记忆，并严格继承隐藏属性
     */
    suspend fun duplicateConversation(conversationId: Long): Long = withContext(Dispatchers.IO) {
        val originalConv = conversationDao.getConversationById(conversationId)
            ?: return@withContext -1L

        val isParentHidden = hasConversationTag(originalConv, "hidden") ||
            originalConv.tags?.contains("hidden") == true
        val duplicatedTags = if (isParentHidden) {
            updateTag(originalConv.tags, "hidden", true)
        } else {
            originalConv.tags
        }

        val duplicateTitle = generateDuplicateTitle(originalConv.title)
        val originalMessages = messageDao.getMessagesList(conversationId)

        val db = try {
            com.aiassistant.AiAssistantApp.instance.database
        } catch (e: Exception) {
            null
        }

        val performDuplicate = suspend {
            val now = System.currentTimeMillis()
            val newConversation = originalConv.copy(
                id = 0L,
                title = duplicateTitle,
                isPinned = false,
                tags = duplicatedTags,
                createdAt = now,
                updatedAt = now
            )
            val newId = conversationDao.insertConversation(newConversation)

            if (originalMessages.isNotEmpty()) {
                val baseTime = now - (originalMessages.size * 1000L)
                val preparedMessages = originalMessages.mapIndexed { index, msg ->
                    msg.copy(
                        id = 0L,
                        conversationId = newId,
                        createdAt = baseTime + (index * 1000L)
                    )
                }
                messageDao.insertMessages(preparedMessages)
                val totalTokens = preparedMessages.sumOf { it.tokenCount }
                conversationDao.updateStats(newId, preparedMessages.size, totalTokens)
            }

            if (isParentHidden) {
                conversationDao.updateTags(newId, duplicatedTags)
            }

            // 深度克隆 Roleplay 剧情会话与记忆
            try {
                val roleplayRepo = com.aiassistant.AiAssistantApp.instance.roleplayRepository
                val rpSession = roleplayRepo.getSessionByConversationId(conversationId)
                if (rpSession != null) {
                    val clonedSessionId = roleplayRepo.insertSession(
                        rpSession.copy(
                            id = 0L,
                            conversationId = newId,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    val sessionMemories = roleplayRepo.getMemoriesListBySession(rpSession.id)
                    sessionMemories.forEach { mem ->
                        roleplayRepo.insertMemory(
                            mem.copy(
                                id = 0L,
                                sessionId = clonedSessionId,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                }
            } catch (rpEx: Exception) {
                Log.e(tag, "克隆角色扮演会话异常", rpEx)
            }

            // 复制普通会话专属记忆
            try {
                val convMemories = memoryDao.getConversationMemories(conversationId)
                convMemories.forEach { mem ->
                    memoryDao.insertMemory(
                        mem.copy(
                            id = 0L,
                            conversationId = newId,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            } catch (memEx: Exception) {
                Log.e(tag, "克隆会话专属记忆异常", memEx)
            }

            // 复制时间线节点
            try {
                val nodes = timelineNodeDao?.getTimelineNodes(conversationId) ?: emptyList()
                if (nodes.isNotEmpty()) {
                    val clonedNodes = nodes.map { it.copy(id = 0L, conversationId = newId, createdAt = now, updatedAt = now) }
                    timelineNodeDao?.insertTimelineNodes(clonedNodes)
                }
            } catch (tlEx: Exception) {
                Log.e(tag, "克隆时间线节点异常", tlEx)
            }

            newId
        }

        if (db != null) {
            db.withTransaction { performDuplicate() }
        } else {
            performDuplicate()
        }
    }

    // ============ 选择的模型相关 ============

    fun getSelectedModels(apiConfigId: Long): Flow<List<SelectedModel>> =
        selectedModelDao.getModelsByConfig(apiConfigId)

    fun getEnabledModels(apiConfigId: Long): Flow<List<SelectedModel>> =
        selectedModelDao.getEnabledModelsByConfig(apiConfigId)

    suspend fun getAllVisibleChatModelOptions(): List<ChatModelOption> = withContext(Dispatchers.IO) {
        val configs = apiConfigDao.getAllConfigs().first().filter { it.isEnabled }
        configs.flatMap { config ->
            val savedModels = selectedModelDao.getModelsByConfig(config.id).first()
            val selectedModels = savedModels.filter { it.isEnabled }
            val options = when {
                selectedModels.isNotEmpty() -> selectedModels.map {
                    ChatModelOption(
                        apiConfigId = config.id,
                        configName = config.name,
                        provider = config.provider,
                        apiType = config.apiType,
                        modelName = it.modelName,
                        capability = it.capability,
                        contextWindowTokens = it.contextWindowTokens
                    )
                }
                savedModels.isNotEmpty() -> savedModels.map {
                    ChatModelOption(
                        apiConfigId = config.id,
                        configName = config.name,
                        provider = config.provider,
                        apiType = config.apiType,
                        modelName = it.modelName,
                        capability = it.capability,
                        contextWindowTokens = it.contextWindowTokens
                    )
                }
                else -> {
                    val modelNames = parseSavedModelNames(config.availableModels)
                        .ifEmpty { listOf(config.modelName) }
                    sanitizeModelNames(modelNames).map { model ->
                        ChatModelOption(
                            apiConfigId = config.id,
                            configName = config.name,
                            provider = config.provider,
                            apiType = config.apiType,
                            modelName = model,
                            capability = "auto"
                        )
                    }
                }
            }
            options.map { option -> option.copy(
                baseUrl = config.baseUrl,
                reasoningCapability = ReasoningCapabilityCatalog.get(config.baseUrl, config.apiType, option.modelName)
            ) }
        }.distinctBy { "${it.apiConfigId}:${it.modelName}" }
    }

    private fun parseSavedModelNames(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type).orEmpty()
        } catch (_: Exception) {
            raw.split(",")
        }
    }

    suspend fun saveSelectedModel(model: SelectedModel): Long {
        return if (model.id == 0L) {
            selectedModelDao.insertModel(model)
        } else {
            selectedModelDao.updateModel(model)
            model.id
        }
    }

    suspend fun saveSelectedModels(models: List<SelectedModel>) =
        selectedModelDao.insertModels(models)

    suspend fun replaceSelectedModels(
        apiConfigId: Long,
        modelNames: List<String>,
        enabledModelNames: Set<String>,
        modelCapabilities: Map<String, String> = emptyMap(),
        modelSettings: Map<String, ModelCustomSettings> = emptyMap()
    ) {
        selectedModelDao.deleteModelsByConfig(apiConfigId)
        selectedModelDao.insertModels(
            sanitizeModelNames(modelNames).mapIndexed { index, modelName ->
                val custom = modelSettings[modelName]
                SelectedModel(
                    apiConfigId = apiConfigId,
                    modelName = modelName,
                    isEnabled = enabledModelNames.contains(modelName),
                    capability = modelCapabilities[modelName] ?: "auto",
                    sortOrder = index,
                    contextWindowTokens = custom?.contextWindowTokens,
                    supportsTools = custom?.supportsTools ?: true,
                    supportsVision = custom?.supportsVision ?: false,
                    supportsThinking = custom?.supportsThinking ?: true,
                    supportsWebSearch = custom?.supportsWebSearch ?: true
                )
            }
        )
    }

    suspend fun deleteSelectedModel(model: SelectedModel) =
        selectedModelDao.deleteModel(model)

    suspend fun setModelEnabled(id: Long, isEnabled: Boolean) =
        selectedModelDao.setModelEnabled(id, isEnabled)

    // ============ 使用统计增强 ============

    suspend fun getModelUsageSummary(days: Int = 30): List<ModelUsageSummary> {
        val startTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        return getModelUsageSummarySince(startTime)
    }

    suspend fun getModelUsageSummarySince(startTime: Long): List<ModelUsageSummary> {
        val results = usageStatDao.getModelUsageSummarySince(startTime)
        return results.map { result ->
            ModelUsageSummary(
                modelName = result.modelName,
                provider = result.provider,
                totalInputTokens = result.totalInputTokens,
                totalOutputTokens = result.totalOutputTokens,
                totalThinkingTokens = result.totalThinkingTokens,
                totalCachedTokens = result.totalCachedTokens,
                totalTokens = result.totalTokens,
                requestCount = result.requestCount,
                successCount = result.successCount,
                avgResponseTime = result.avgResponseTime,
                cacheHitRate = if (result.totalInputTokens > 0) {
                    (result.totalCachedTokens.toFloat() / result.totalInputTokens).coerceIn(0f, 1f)
                } else 0f
            )
        }
    }

    suspend fun getCachedTokens(days: Int = 30): Int {
        val startTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        return usageStatDao.getCachedTokensSince(startTime) ?: 0
    }

    private fun extractCachedTokensFromUsage(usage: Usage?): Int {
        if (usage == null) return 0
        return usage.prompt_tokens_details?.cached_tokens
            ?: usage.prompt_tokens_details?.cached_content_token_count
            ?: usage.prompt_tokens_details?.cache_read_input_tokens
            ?: usage.prompt_cache_hit_tokens
            ?: usage.cached_tokens
            ?: usage.cache_read_input_tokens
            ?: usage.cached_content_token_count
            ?: 0
    }

    // -------------------------------------------------------------
    // 时间线专属数据访问与操作（彻底解耦于普通记忆，提供单一独立存储）
    // -------------------------------------------------------------
    fun getTimelineNodesFlow(conversationId: Long): Flow<List<TimelineNode>> =
        timelineNodeDao?.getTimelineNodesFlow(conversationId) ?: flowOf(emptyList())

    suspend fun getTimelineNodes(conversationId: Long): List<TimelineNode> =
        timelineNodeDao?.getTimelineNodes(conversationId) ?: emptyList()

    suspend fun addTimelineNode(node: TimelineNode): Long =
        timelineNodeDao?.insertTimelineNode(node) ?: -1L

    suspend fun updateTimelineNode(node: TimelineNode) =
        timelineNodeDao?.updateTimelineNode(node)

    suspend fun deleteTimelineNode(node: TimelineNode) =
        timelineNodeDao?.deleteTimelineNode(node)

    suspend fun deleteTimelineNodeById(id: Long) =
        timelineNodeDao?.deleteTimelineNodeById(id)

    suspend fun clearTimeline(conversationId: Long) =
        timelineNodeDao?.clearTimelineByConversation(conversationId)

    suspend fun replaceTimelineNodes(conversationId: Long, nodes: List<TimelineNode>) =
        timelineNodeDao?.replaceTimelineNodes(conversationId, nodes)

    suspend fun updateStoryTime(conversationId: Long, storyTime: String?) = withContext(Dispatchers.IO) {
        val conv = conversationDao.getConversationById(conversationId) ?: return@withContext
        conversationDao.updateConversation(conv.copy(currentStoryTime = storyTime, updatedAt = System.currentTimeMillis()))
    }
}
