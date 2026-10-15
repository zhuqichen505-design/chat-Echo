package com.aiassistant.domain.model

import com.google.gson.Gson

/** 故事副本的关联规则。负数 ID 只存在 JSON 副本中，不写入 Room 外键。 */
object StorySessionBindings {
    fun normalizeLocalIds(characters: List<CharacterProfile>): List<CharacterProfile> {
        var nextId = minOf(-1L, (characters.minOfOrNull { it.id } ?: 0L) - 1L)
        return characters.map { if (it.id == 0L) it.copy(id = nextId--) else it }
    }

    fun select(
        session: RoleplaySession,
        characters: List<CharacterProfile>,
        selectedIds: Collection<Long>,
        scenarioId: Long?,
        mode: NarrativeMode,
        summary: String
    ): RoleplaySession {
        val ids = selectedIds.distinct()
        val snapshots = normalizeLocalIds(characters)
        return session.copy(
            characterIds = Gson().toJson(ids),
            characterId = ids.firstOrNull { it > 0 },
            customCharacterData = snapshots.takeIf { it.isNotEmpty() }?.let { Gson().toJson(it) },
            scenarioId = scenarioId?.takeIf { it > 0 },
            customScenarioData = if (scenarioId == (session.getCustomizedScenario(null)?.id ?: session.scenarioId)) session.customScenarioData else null,
            narrativeMode = mode.value,
            currentPlotSummary = summary,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun upsertCharacter(session: RoleplaySession, existing: List<CharacterProfile>, edited: CharacterProfile): RoleplaySession {
        val normalized = normalizeLocalIds(existing).toMutableList()
        val incoming = if (edited.id == 0L) edited.copy(id = minOf(-1L, (normalized.minOfOrNull { it.id } ?: 0L) - 1L)) else edited
        val index = normalized.indexOfFirst { it.id == incoming.id }
        if (index < 0) normalized.add(incoming) else normalized[index] = incoming
        val ids = (session.getParticipatingCharacters(existing).map { it.id } + session.getEffectiveCharacterIds() + incoming.id).distinct()
        return session.copy(characterIds = Gson().toJson(ids), characterId = ids.firstOrNull { it > 0 },
            customCharacterData = Gson().toJson(normalized), updatedAt = System.currentTimeMillis())
    }
}
