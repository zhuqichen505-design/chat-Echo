package com.aiassistant.data.local

import androidx.room.*
import com.aiassistant.domain.model.ConversationBranch
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationBranchDao {
    @Query("SELECT * FROM conversation_branches WHERE parentConversationId = :parentId ORDER BY createdAt DESC")
    fun getBranchesByParent(parentId: Long): Flow<List<ConversationBranch>>

    @Query("SELECT * FROM conversation_branches WHERE childConversationId = :childId LIMIT 1")
    suspend fun getBranchByChild(childId: Long): ConversationBranch?

    @Query("SELECT * FROM conversation_branches WHERE branchMessageId = :messageId LIMIT 1")
    suspend fun getBranchByMessage(messageId: Long): ConversationBranch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranch(branch: ConversationBranch): Long

    @Delete
    suspend fun deleteBranch(branch: ConversationBranch)

    @Query("DELETE FROM conversation_branches WHERE parentConversationId = :parentId")
    suspend fun deleteBranchesByParent(parentId: Long)
}
