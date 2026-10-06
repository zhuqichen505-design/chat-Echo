package com.aiassistant.data.local

import androidx.room.*
import com.aiassistant.domain.model.*
import kotlinx.coroutines.flow.Flow

// ============ 文件夹 DAO ============
@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, name ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentId IS NULL ORDER BY sortOrder ASC, name ASC")
    fun getRootFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY sortOrder ASC, name ASC")
    fun getSubFolders(parentId: Long): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)

    @Query("UPDATE conversations SET folderId = NULL WHERE folderId = :folderId")
    suspend fun unassignConversationsFromFolder(folderId: Long)

    @Query("SELECT COUNT(*) FROM conversations WHERE folderId = :folderId")
    suspend fun getConversationCount(folderId: Long): Int
}

// ============ API配置 DAO ============
@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM api_configs ORDER BY isDefault DESC, name ASC")
    fun getAllConfigs(): Flow<List<ApiConfig>>

    @Query("SELECT * FROM api_configs WHERE id = :id")
    suspend fun getConfigById(id: Long): ApiConfig?

    @Query("""
        SELECT * FROM api_configs
        WHERE name = :name
          AND provider = :provider
          AND baseUrl = :baseUrl
          AND apiType = :apiType
          AND modelName = :modelName
        LIMIT 1
    """)
    suspend fun getConfigByIdentity(
        name: String,
        provider: String,
        baseUrl: String,
        apiType: String,
        modelName: String
    ): ApiConfig?

    @Query("SELECT * FROM api_configs WHERE isDefault = 1 AND isEnabled = 1 LIMIT 1")
    suspend fun getDefaultConfig(): ApiConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ApiConfig): Long

    @Update
    suspend fun updateConfig(config: ApiConfig)

    @Delete
    suspend fun deleteConfig(config: ApiConfig)

    @Query("UPDATE api_configs SET isDefault = 0")
    suspend fun clearDefaultConfigs()

    @Query("UPDATE api_configs SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultConfig(id: Long)

    @Query("SELECT * FROM api_configs WHERE isEnabled = 1 ORDER BY isDefault DESC, name ASC")
    fun getEnabledConfigs(): Flow<List<ApiConfig>>

    @Query("UPDATE api_configs SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setConfigEnabled(id: Long, isEnabled: Boolean)
}

// ============ 对话 DAO ============
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE tags IS NULL OR (tags NOT LIKE '%hidden%' AND tags NOT LIKE '%private%') ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE folderId IS NULL AND (tags IS NULL OR (tags NOT LIKE '%hidden%' AND tags NOT LIKE '%private%')) ORDER BY isPinned DESC, updatedAt DESC")
    fun getUnfiledConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE isPinned = 1 AND (tags IS NULL OR (tags NOT LIKE '%hidden%' AND tags NOT LIKE '%private%')) ORDER BY updatedAt DESC")
    fun getPinnedConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE folderId = :folderId AND (tags IS NULL OR (tags NOT LIKE '%hidden%' AND tags NOT LIKE '%private%')) ORDER BY isPinned DESC, updatedAt DESC")
    fun getConversationsByFolder(folderId: Long): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE tags LIKE '%hidden%' ORDER BY updatedAt DESC")
    fun getHiddenConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): Conversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)

    @Query("UPDATE conversations SET updatedAt = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET messageCount = :count, totalTokens = :tokens WHERE id = :id")
    suspend fun updateStats(id: Long, count: Int, tokens: Int)

    @Query("UPDATE conversations SET messageCount = (SELECT COUNT(*) FROM messages WHERE conversationId = :id), totalTokens = (SELECT COALESCE(SUM(tokenCount), 0) FROM messages WHERE conversationId = :id), updatedAt = :timestamp WHERE id = :id")
    suspend fun refreshStats(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET folderId = :folderId WHERE id = :conversationId")
    suspend fun moveToFolder(conversationId: Long, folderId: Long?)

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' AND (tags IS NULL OR (tags NOT LIKE '%hidden%' AND tags NOT LIKE '%private%')) ORDER BY updatedAt DESC")
    fun searchConversations(query: String): Flow<List<Conversation>>

    @Query("SELECT title FROM conversations WHERE title LIKE :prefix || '%'")
    suspend fun getTitlesStartingWith(prefix: String): List<String>

    @Query("SELECT * FROM conversations")
    suspend fun getAllConversationsList(): List<Conversation>

    @Query("UPDATE conversations SET tags = :tags, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateTags(id: Long, tags: String?, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE conversations
        SET rollingSummary = :summary,
            summaryUpdatedMessageId = :messageId,
            summaryUpdatedAt = :timestamp,
            updatedAt = :timestamp
        WHERE id = :conversationId
    """)
    suspend fun updateRollingSummary(
        conversationId: Long,
        summary: String?,
        messageId: Long?,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE conversations SET modelAvatarUri = :avatarUri, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateModelAvatarUri(
        id: Long,
        avatarUri: String?,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE conversations SET contextWindowTokens = :tokens, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateContextWindowTokens(
        id: Long,
        tokens: Int?,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE conversations SET compressionTier = :tier, compressionRecentRounds = :recentRounds, compressionCustomPercent = :customPercent, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateCompressionTier(
        id: Long,
        tier: Int,
        recentRounds: Int,
        customPercent: Int,
        timestamp: Long = System.currentTimeMillis()
    )
}

// ============ 消息 DAO ============
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): Message?
    @Query("SELECT m.* FROM messages m INNER JOIN conversations c ON c.id = m.conversationId WHERE (c.tags IS NULL OR (c.tags NOT LIKE '%hidden%' AND c.tags NOT LIKE '%private%')) AND instr(lower(m.content), lower(:query)) > 0 ORDER BY c.updatedAt DESC, m.createdAt ASC LIMIT 200")
    suspend fun searchMessages(query: String): List<Message>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun getMessagesByConversation(conversationId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessagesList(conversationId: Long): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>): List<Long>

    @Update
    suspend fun updateMessage(message: Message)

    @Delete
    suspend fun deleteMessage(message: Message)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt >= :createdAt")
    suspend fun deleteMessagesFrom(conversationId: Long, createdAt: Long)

    @Query("SELECT SUM(tokenCount) FROM messages WHERE conversationId = :conversationId")
    suspend fun getTotalTokens(conversationId: Long): Int?

    @Query("UPDATE messages SET translatedThinking = :translatedThinking WHERE id = :messageId")
    suspend fun updateTranslatedThinking(messageId: Long, translatedThinking: String?)

    @Query("UPDATE messages SET modelName = :modelName WHERE id = :messageId")
    suspend fun updateMessageModelName(messageId: Long, modelName: String)
}

// ============ 使用统计 DAO ============
@Dao
interface UsageStatDao {
    @Query("SELECT * FROM api_usage_stats ORDER BY timestamp DESC")
    fun getAllStats(): Flow<List<ApiUsageStat>>

    @Query("""
        SELECT * FROM api_usage_stats
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    fun getStatsByTimeRange(startTime: Long, endTime: Long): Flow<List<ApiUsageStat>>

    @Query("""
        SELECT * FROM api_usage_stats
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp ASC
    """)
    suspend fun getStatsListByTimeRange(startTime: Long, endTime: Long): List<ApiUsageStat>

    @Query("""
        SELECT * FROM api_usage_stats
        WHERE provider = :provider
        ORDER BY timestamp DESC
    """)
    fun getStatsByProvider(provider: String): Flow<List<ApiUsageStat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStat(stat: ApiUsageStat)

    @Query("SELECT SUM(totalTokens) FROM api_usage_stats WHERE timestamp >= :startTime")
    suspend fun getTotalTokensSince(startTime: Long): Int?

    @Query("SELECT SUM(thinkingTokens) FROM api_usage_stats WHERE timestamp >= :startTime")
    suspend fun getThinkingTokensSince(startTime: Long): Int?

    @Query("SELECT COUNT(*) FROM api_usage_stats WHERE timestamp >= :startTime")
    suspend fun getRequestCountSince(startTime: Long): Int?

    @Query("SELECT AVG(responseTime) FROM api_usage_stats WHERE timestamp >= :startTime AND success = 1")
    suspend fun getAvgResponseTimeSince(startTime: Long): Long?

    @Query("""
        SELECT modelName, provider, SUM(totalTokens) as totalTokens,
               SUM(thinkingTokens) as thinkingTokens, COUNT(*) as requestCount
        FROM api_usage_stats
        WHERE timestamp >= :startTime
        GROUP BY modelName, provider
        ORDER BY totalTokens DESC
    """)
    suspend fun getModelStatsSince(startTime: Long): List<ModelStats>

    @Query("""
        SELECT
            strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') as date,
            SUM(totalTokens) as totalTokens,
            SUM(thinkingTokens) as thinkingTokens,
            COUNT(*) as requestCount,
            AVG(responseTime) as avgResponseTime
        FROM api_usage_stats
        WHERE timestamp >= :startTime
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyStatsSince(startTime: Long): List<DailyStats>

    @Query("""
        SELECT
            modelName,
            provider,
            COALESCE(SUM(inputTokens), 0) as totalInputTokens,
            COALESCE(SUM(outputTokens), 0) as totalOutputTokens,
            COALESCE(SUM(thinkingTokens), 0) as totalThinkingTokens,
            COALESCE(SUM(cachedTokens), 0) as totalCachedTokens,
            COALESCE(SUM(totalTokens), 0) as totalTokens,
            COUNT(*) as requestCount,
            COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0) as successCount,
            COALESCE(CAST(AVG(responseTime) AS INTEGER), 0) as avgResponseTime
        FROM api_usage_stats
        WHERE timestamp >= :startTime
        GROUP BY modelName, provider
        ORDER BY totalTokens DESC
    """)
    suspend fun getModelUsageSummarySince(startTime: Long): List<ModelUsageSummaryResult>

    @Query("SELECT SUM(cachedTokens) FROM api_usage_stats WHERE timestamp >= :startTime")
    suspend fun getCachedTokensSince(startTime: Long): Int?
}

// 用于Room查询的结果类
data class ModelUsageSummaryResult(
    val modelName: String,
    val provider: String,
    val totalInputTokens: Int,
    val totalOutputTokens: Int,
    val totalThinkingTokens: Int,
    val totalCachedTokens: Int,
    val totalTokens: Int,
    val requestCount: Int,
    val successCount: Int,
    val avgResponseTime: Long
)

// ============ 环境变量 DAO ============
@Dao
interface EnvironmentVariableDao {
    @Query("SELECT * FROM environment_variables WHERE environment = :environment ORDER BY name ASC")
    fun getVariablesByEnvironment(environment: String): Flow<List<EnvironmentVariable>>

    @Query("SELECT * FROM environment_variables ORDER BY environment ASC, name ASC")
    fun getAllVariables(): Flow<List<EnvironmentVariable>>

    @Query("SELECT * FROM environment_variables WHERE id = :id")
    suspend fun getVariableById(id: Long): EnvironmentVariable?

    @Query("SELECT * FROM environment_variables WHERE name = :name AND environment = :environment LIMIT 1")
    suspend fun getVariableByName(name: String, environment: String): EnvironmentVariable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariable(variable: EnvironmentVariable): Long

    @Update
    suspend fun updateVariable(variable: EnvironmentVariable)

    @Delete
    suspend fun deleteVariable(variable: EnvironmentVariable)

    @Query("SELECT DISTINCT environment FROM environment_variables ORDER BY environment ASC")
    suspend fun getAllEnvironments(): List<String>
}

// ============ 提示词模板 DAO ============
@Dao
interface PromptTemplateDao {
    @Query("SELECT * FROM prompt_templates ORDER BY isBuiltIn DESC, useCount DESC, name ASC")
    fun getAllTemplates(): Flow<List<PromptTemplate>>

    @Query("SELECT * FROM prompt_templates WHERE category = :category ORDER BY isBuiltIn DESC, useCount DESC, name ASC")
    fun getTemplatesByCategory(category: String): Flow<List<PromptTemplate>>

    @Query("SELECT * FROM prompt_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): PromptTemplate?

    @Query("SELECT * FROM prompt_templates WHERE name = :name LIMIT 1")
    suspend fun getTemplateByName(name: String): PromptTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: PromptTemplate): Long

    @Update
    suspend fun updateTemplate(template: PromptTemplate)

    @Delete
    suspend fun deleteTemplate(template: PromptTemplate)

    @Query("DELETE FROM prompt_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)

    @Query("UPDATE prompt_templates SET useCount = useCount + 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun incrementUseCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT DISTINCT category FROM prompt_templates ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>
}

@Dao
interface MemoryDao {
    @Query("""
        SELECT * FROM memory_items
        WHERE isEnabled = 1
          AND (
              scope IN ('user', 'global')
              OR conversationId = :conversationId
              OR (scope = 'conversation' AND conversationId = :conversationId)
          )
        ORDER BY updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getCandidateMemories(conversationId: Long, limit: Int = 80): List<MemoryItem>

    @Query("SELECT * FROM memory_items ORDER BY isEnabled DESC, updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryItem>>

    @Query("""
        SELECT * FROM memory_items
        WHERE content LIKE '%' || :query || '%'
           OR keywords LIKE '%' || :query || '%'
           OR scope LIKE '%' || :query || '%'
        ORDER BY isEnabled DESC, updatedAt DESC
    """)
    fun searchMemories(query: String): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memory_items WHERE id = :id")
    suspend fun getMemoryById(id: Long): MemoryItem?

    @Query("SELECT * FROM memory_items WHERE sourceMessageId = :sourceMessageId LIMIT 1")
    suspend fun getBySourceMessage(sourceMessageId: Long): MemoryItem?

    @Query("SELECT * FROM memory_items WHERE scope = :scope AND content = :content LIMIT 1")
    suspend fun getByScopeAndContent(scope: String, content: String): MemoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryItem): Long

    @Update
    suspend fun updateMemory(memory: MemoryItem)

    @Delete
    suspend fun deleteMemory(memory: MemoryItem)

    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memory_items")
    suspend fun deleteAllMemories()

    @Query("UPDATE memory_items SET isEnabled = :isEnabled, updatedAt = :timestamp WHERE id = :id")
    suspend fun setMemoryEnabled(id: Long, isEnabled: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE memory_items SET isEnabled = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun disableMemory(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM memory_items WHERE conversationId = :conversationId ORDER BY isEnabled DESC, updatedAt DESC")
    fun getConversationMemoriesFlow(conversationId: Long): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memory_items WHERE conversationId = :conversationId ORDER BY isEnabled DESC, updatedAt DESC")
    suspend fun getConversationMemories(conversationId: Long): List<MemoryItem>

    @Query("DELETE FROM memory_items WHERE conversationId = :conversationId")
    suspend fun deleteConversationMemories(conversationId: Long)

    @Query("SELECT * FROM memory_items WHERE scope IN ('user', 'global') ORDER BY isEnabled DESC, updatedAt DESC")
    fun getGlobalMemoriesFlow(): Flow<List<MemoryItem>>

    @Query("""
        SELECT * FROM memory_items
        WHERE scope IN ('user', 'global')
          AND (content LIKE '%' || :query || '%' OR keywords LIKE '%' || :query || '%')
        ORDER BY isEnabled DESC, updatedAt DESC
    """)
    fun searchGlobalMemories(query: String): Flow<List<MemoryItem>>

    @Query("DELETE FROM memory_items WHERE scope IN ('user', 'global')")
    suspend fun deleteGlobalMemories()
}
