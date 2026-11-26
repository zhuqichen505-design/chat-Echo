package com.aiassistant.data.local

import androidx.room.*
import com.aiassistant.domain.model.ConversationSummaryChunk
import com.aiassistant.domain.model.VectorEmbeddingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryChunkDao {
    @Query("SELECT * FROM conversation_summary_chunks WHERE conversationId = :conversationId ORDER BY chunkIndex ASC, id ASC")
    fun getChunksFlow(conversationId: Long): Flow<List<ConversationSummaryChunk>>

    @Query("SELECT * FROM conversation_summary_chunks WHERE conversationId = :conversationId ORDER BY chunkIndex ASC, id ASC")
    suspend fun getChunksList(conversationId: Long): List<ConversationSummaryChunk>

    @Query("SELECT * FROM conversation_summary_chunks WHERE id = :id")
    suspend fun getChunkById(id: Long): ConversationSummaryChunk?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: ConversationSummaryChunk): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<ConversationSummaryChunk>)

    @Update
    suspend fun updateChunk(chunk: ConversationSummaryChunk)

    @Query("UPDATE conversation_summary_chunks SET isPinned = :isPinned, updatedAt = :timestamp WHERE id = :id")
    suspend fun setChunkPinned(id: Long, isPinned: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversation_summary_chunks WHERE id = :id")
    suspend fun deleteChunkById(id: Long)

    @Query("DELETE FROM conversation_summary_chunks WHERE conversationId = :conversationId")
    suspend fun deleteConversationChunks(conversationId: Long)

    @Query("DELETE FROM conversation_summary_chunks WHERE conversationId = :conversationId AND chunkIndex >= :fromIndex")
    suspend fun deleteChunksFromIndex(conversationId: Long, fromIndex: Int)
}

@Dao
interface VectorEmbeddingDao {
    @Query("SELECT * FROM vector_embeddings WHERE targetType = :targetType AND embeddingSpaceId = :spaceId")
    suspend fun getByTypeAndSpace(targetType: String, spaceId: String): List<VectorEmbeddingEntity>

    @Query("SELECT * FROM vector_embeddings WHERE targetType = :targetType AND conversationId = :conversationId AND embeddingSpaceId = :spaceId")
    suspend fun getByTypeConversationAndSpace(targetType: String, conversationId: Long, spaceId: String): List<VectorEmbeddingEntity>

    @Query("SELECT * FROM vector_embeddings WHERE targetType = :targetType AND targetId IN (:targetIds) AND embeddingSpaceId = :spaceId")
    suspend fun getByIdsAndSpace(targetType: String, targetIds: List<Long>, spaceId: String): List<VectorEmbeddingEntity>

    @Query("SELECT COUNT(*) FROM vector_embeddings WHERE targetType = :targetType AND embeddingSpaceId = :spaceId")
    suspend fun countByTypeAndSpace(targetType: String, spaceId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VectorEmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<VectorEmbeddingEntity>)

    @Query("DELETE FROM vector_embeddings WHERE targetType = :targetType AND targetId = :targetId")
    suspend fun deleteTarget(targetType: String, targetId: Long)

    @Query("DELETE FROM vector_embeddings WHERE targetType = :targetType AND targetId IN (:targetIds)")
    suspend fun deleteTargets(targetType: String, targetIds: List<Long>)

    @Query("DELETE FROM vector_embeddings WHERE targetType = :targetType AND conversationId = :conversationId")
    suspend fun deleteByTypeAndConversation(targetType: String, conversationId: Long)

    @Query("DELETE FROM vector_embeddings WHERE embeddingSpaceId != :currentSpaceId")
    suspend fun purgeStaleSpaces(currentSpaceId: String)

    @Query("DELETE FROM vector_embeddings")
    suspend fun deleteAll()
}
