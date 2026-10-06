package com.aiassistant.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aiassistant.domain.model.TimelineNode
import kotlinx.coroutines.flow.Flow

/**
 * 时间线独立节点 DAO
 */
@Dao
interface TimelineNodeDao {

    @Query("SELECT * FROM timeline_nodes WHERE conversationId = :conversationId ORDER BY orderIndex ASC, id ASC")
    fun getTimelineNodesFlow(conversationId: Long): Flow<List<TimelineNode>>

    @Query("SELECT * FROM timeline_nodes WHERE conversationId = :conversationId ORDER BY orderIndex ASC, id ASC")
    suspend fun getTimelineNodes(conversationId: Long): List<TimelineNode>

    @Query("SELECT * FROM timeline_nodes WHERE id = :id")
    suspend fun getTimelineNodeById(id: Long): TimelineNode?

    @Query("SELECT MAX(orderIndex) FROM timeline_nodes WHERE conversationId = :conversationId")
    suspend fun getMaxOrderIndex(conversationId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineNode(node: TimelineNode): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineNodes(nodes: List<TimelineNode>): List<Long>

    @Update
    suspend fun updateTimelineNode(node: TimelineNode)

    @Delete
    suspend fun deleteTimelineNode(node: TimelineNode)

    @Query("DELETE FROM timeline_nodes WHERE id = :id")
    suspend fun deleteTimelineNodeById(id: Long)

    @Query("DELETE FROM timeline_nodes WHERE conversationId = :conversationId")
    suspend fun deleteTimelineByConversation(conversationId: Long)

    suspend fun clearTimelineByConversation(conversationId: Long) = deleteTimelineByConversation(conversationId)

    @Transaction
    suspend fun replaceTimelineNodes(conversationId: Long, nodes: List<TimelineNode>) {
        deleteTimelineByConversation(conversationId)
        if (nodes.isNotEmpty()) {
            val indexedNodes = nodes.mapIndexed { idx, node ->
                node.copy(id = 0, conversationId = conversationId, orderIndex = idx)
            }
            insertTimelineNodes(indexedNodes)
        }
    }
}
