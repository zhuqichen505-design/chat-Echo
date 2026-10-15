package com.aiassistant.ui.screens.chat.story

import com.aiassistant.domain.model.MemoryItem
import com.aiassistant.domain.model.NarrativeMode
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.domain.model.TimelineNode
import com.aiassistant.ui.screens.chat.TempChatSettings
import com.aiassistant.ui.screens.chat.mergeChatSettingsDraft
import com.aiassistant.utils.TimelineReconcileCheckpoint

/** 同一会话的工作区；不另建一份故事记忆或时间线。 */
data class StoryKnowledgeState(
    val memories: List<MemoryItem> = emptyList(),
    val timeline: List<TimelineNode> = emptyList(),
    val currentTime: String? = null,
    val isExtracting: Boolean = false,
    val extractionProgress: String? = null,
    val hasDraft: Boolean = false,
    val checkpoint: TimelineReconcileCheckpoint? = null,
    val newMessageCount: Int = 0
)

data class StoryKnowledgeActions(
    val addMemory: (String) -> Unit,
    val updateMemory: (MemoryItem) -> Unit,
    val toggleMemory: (Long, Boolean) -> Unit,
    val deleteMemory: (Long) -> Unit,
    val clearMemories: () -> Unit,
    val updateTime: (String?) -> Unit,
    val addEvent: (String, String, String) -> Unit,
    val updateEvent: (TimelineNode) -> Unit,
    val deleteEvent: (Long) -> Unit,
    val clearTimeline: () -> Unit,
    val extractAll: () -> Unit,
    val continueExtraction: () -> Unit,
    val extractNew: () -> Unit,
    val reviewDraft: () -> Unit,
    val clearDraft: () -> Unit,
    val cancelExtraction: () -> Unit
)

internal data class StoryWorkspaceDraft(
    val characterIds: Set<Long>,
    val scenarioId: Long?,
    val mode: NarrativeMode,
    val summary: String,
    val instructions: String,
    val settings: TempChatSettings
) {
    companion object {
        fun from(session: RoleplaySession, prompt: String?, settings: TempChatSettings) =
            StoryWorkspaceDraft((session.getEffectiveCharacterIds() + session.getParticipatingCharacters(emptyList()).map { it.id }).toSet(), session.getCustomizedScenario(null)?.id ?: session.scenarioId,
                NarrativeMode.fromValue(session.narrativeMode), session.currentPlotSummary,
                prompt.orEmpty(), settings)
    }
}

/** 本地编辑过的字段优先；后台提取、增删人物和切换模型只刷新未编辑字段。 */
internal fun mergeStoryWorkspaceDraft(
    local: StoryWorkspaceDraft,
    previous: StoryWorkspaceDraft,
    incoming: StoryWorkspaceDraft
): StoryWorkspaceDraft = StoryWorkspaceDraft(
    characterIds = (local.characterIds + (incoming.characterIds - previous.characterIds)) -
        (previous.characterIds - incoming.characterIds),
    scenarioId = if (local.scenarioId == previous.scenarioId) incoming.scenarioId else local.scenarioId,
    mode = if (local.mode == previous.mode) incoming.mode else local.mode,
    summary = if (local.summary == previous.summary) incoming.summary else local.summary,
    instructions = if (local.instructions == previous.instructions) incoming.instructions else local.instructions,
    settings = mergeChatSettingsDraft(local.settings, previous.settings, incoming.settings)
)
