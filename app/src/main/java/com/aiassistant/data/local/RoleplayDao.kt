package com.aiassistant.data.local

import androidx.room.*
import com.aiassistant.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 角色卡 DAO
 */
@Dao
interface CharacterProfileDao {
    @Query("SELECT * FROM character_profiles ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllCharacters(): Flow<List<CharacterProfile>>

    @Query("SELECT * FROM character_profiles WHERE id = :id")
    suspend fun getCharacterById(id: Long): CharacterProfile?

    @Query("SELECT * FROM character_profiles WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteCharacters(): Flow<List<CharacterProfile>>

    @Query("SELECT * FROM character_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultCharacter(): CharacterProfile?

    @Query("SELECT * FROM character_profiles WHERE name LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    fun searchCharacters(query: String): Flow<List<CharacterProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterProfile): Long

    @Update
    suspend fun updateCharacter(character: CharacterProfile)

    @Delete
    suspend fun deleteCharacter(character: CharacterProfile)

    @Query("DELETE FROM character_profiles WHERE id = :id")
    suspend fun deleteCharacterById(id: Long)

    @Query("UPDATE character_profiles SET isFavorite = :isFavorite, updatedAt = :timestamp WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE character_profiles SET isDefault = 0")
    suspend fun clearDefaultCharacters()

    @Query("UPDATE character_profiles SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultCharacter(id: Long)

    @Query("SELECT COUNT(*) FROM character_profiles")
    suspend fun getCharacterCount(): Int
}

/**
 * 场景卡 DAO
 */
@Dao
interface RoleplayScenarioDao {
    @Query("SELECT * FROM roleplay_scenarios ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllScenarios(): Flow<List<RoleplayScenario>>

    @Query("SELECT * FROM roleplay_scenarios WHERE id = :id")
    suspend fun getScenarioById(id: Long): RoleplayScenario?

    @Query("SELECT * FROM roleplay_scenarios WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteScenarios(): Flow<List<RoleplayScenario>>

    @Query("SELECT * FROM roleplay_scenarios WHERE name LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    fun searchScenarios(query: String): Flow<List<RoleplayScenario>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenario(scenario: RoleplayScenario): Long

    @Update
    suspend fun updateScenario(scenario: RoleplayScenario)

    @Delete
    suspend fun deleteScenario(scenario: RoleplayScenario)

    @Query("DELETE FROM roleplay_scenarios WHERE id = :id")
    suspend fun deleteScenarioById(id: Long)

    @Query("UPDATE roleplay_scenarios SET isFavorite = :isFavorite, updatedAt = :timestamp WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM roleplay_scenarios")
    suspend fun getScenarioCount(): Int
}

/**
 * 角色扮演会话 DAO
 */
@Dao
interface RoleplaySessionDao {
    @Query("SELECT * FROM roleplay_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<RoleplaySession>>

    @Query("SELECT * FROM roleplay_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): RoleplaySession?

    @Query("SELECT * FROM roleplay_sessions WHERE conversationId = :conversationId")
    suspend fun getSessionByConversationId(conversationId: Long): RoleplaySession?

    @Query("SELECT * FROM roleplay_sessions WHERE characterId = :characterId ORDER BY updatedAt DESC")
    fun getSessionsByCharacter(characterId: Long): Flow<List<RoleplaySession>>

    @Query("SELECT * FROM roleplay_sessions WHERE scenarioId = :scenarioId ORDER BY updatedAt DESC")
    fun getSessionsByScenario(scenarioId: Long): Flow<List<RoleplaySession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RoleplaySession): Long

    @Update
    suspend fun updateSession(session: RoleplaySession)

    @Delete
    suspend fun deleteSession(session: RoleplaySession)

    @Query("DELETE FROM roleplay_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("""
        UPDATE roleplay_sessions
        SET currentPlotSummary = :summary,
            pinnedFacts = :pinnedFacts,
            updatedAt = :timestamp
        WHERE id = :id
    """)
    suspend fun updateMemoryState(
        id: Long,
        summary: String,
        pinnedFacts: String?,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("SELECT COUNT(*) FROM roleplay_sessions")
    suspend fun getSessionCount(): Int
}

/**
 * 角色扮演记忆 DAO
 */
@Dao
interface RoleplayMemoryDao {
    @Query("SELECT * FROM roleplay_memories WHERE sessionId = :sessionId ORDER BY isPinned DESC, updatedAt DESC")
    fun getMemoriesBySession(sessionId: Long): Flow<List<RoleplayMemory>>

    @Query("SELECT * FROM roleplay_memories WHERE sessionId = :sessionId ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getMemoriesListBySession(sessionId: Long): List<RoleplayMemory>

    @Query("SELECT * FROM roleplay_memories WHERE sessionId = :sessionId AND memoryType = :type ORDER BY updatedAt DESC")
    fun getMemoriesByType(sessionId: Long, type: String): Flow<List<RoleplayMemory>>

    @Query("SELECT * FROM roleplay_memories WHERE sessionId = :sessionId AND isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinnedMemories(sessionId: Long): Flow<List<RoleplayMemory>>

    @Query("SELECT * FROM roleplay_memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): RoleplayMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: RoleplayMemory): Long

    @Update
    suspend fun updateMemory(memory: RoleplayMemory)

    @Delete
    suspend fun deleteMemory(memory: RoleplayMemory)

    @Query("DELETE FROM roleplay_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM roleplay_memories WHERE sessionId = :sessionId")
    suspend fun deleteAllMemories(sessionId: Long)

    @Query("UPDATE roleplay_memories SET isPinned = :isPinned, updatedAt = :timestamp WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM roleplay_memories WHERE sessionId = :sessionId AND isPinned = 1 AND memoryType = 'fact' ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getPinnedFacts(sessionId: Long, limit: Int = 20): List<RoleplayMemory>

    @Query("SELECT * FROM roleplay_memories WHERE sessionId = :sessionId AND memoryType = 'summary' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestSummary(sessionId: Long): RoleplayMemory?
}

/**
 * 角色标签 DAO
 */
@Dao
interface CharacterTagDao {
    @Query("SELECT * FROM character_tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<CharacterTag>>

    @Query("SELECT * FROM character_tags WHERE id = :id")
    suspend fun getTagById(id: Long): CharacterTag?

    @Query("SELECT * FROM character_tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): CharacterTag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: CharacterTag): Long

    @Delete
    suspend fun deleteTag(tag: CharacterTag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: CharacterTagCrossRef)

    @Delete
    suspend fun deleteCrossRef(crossRef: CharacterTagCrossRef)

    @Query("DELETE FROM character_tag_cross_ref WHERE characterId = :characterId")
    suspend fun deleteAllCrossRefsForCharacter(characterId: Long)

    @Query("""
        SELECT ct.* FROM character_tags ct
        INNER JOIN character_tag_cross_ref ref ON ct.id = ref.tagId
        WHERE ref.characterId = :characterId
        ORDER BY ct.name ASC
    """)
    fun getTagsForCharacter(characterId: Long): Flow<List<CharacterTag>>

    @Query("""
        SELECT cp.* FROM character_profiles cp
        INNER JOIN character_tag_cross_ref ref ON cp.id = ref.characterId
        WHERE ref.tagId = :tagId
        ORDER BY cp.name ASC
    """)
    fun getCharactersForTag(tagId: Long): Flow<List<CharacterProfile>>
}
