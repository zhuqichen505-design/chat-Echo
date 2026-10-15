package com.aiassistant.ui.screens.roleplay

import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.Conversation
import com.aiassistant.domain.model.NarrativeMode
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.RoleplaySession

/**
 * 角色与创作工作台的列表索引逻辑。
 *
 * 工作台展示的是会话、角色卡和世界观卡的组合，而会话实体本身只保存 id。
 * 把标题和检索规则集中在这里，避免 UI 在每个卡片中重复拼接，也保证搜索不会
 * 因为只输入了角色名、世界观名或叙事模式而漏掉故事。
 */
internal fun roleplaySessionLabel(
    session: RoleplaySession,
    characters: List<CharacterProfile>,
    scenarios: List<RoleplayScenario>
): String {
    val characterNames = session.getParticipatingCharacters(characters
        .filter { it.id in session.getEffectiveCharacterIds() }).map { it.name }
    val scenario = session.getCustomizedScenario(scenarios.firstOrNull { it.id == session.scenarioId })
    return when {
        characterNames.isNotEmpty() && scenario != null ->
            "${characterNames.joinToString("、")} · ${scenario.name}"
        characterNames.isNotEmpty() -> "故事：${characterNames.joinToString("、")}"
        scenario != null -> "世界观：${scenario.name}"
        else -> "故事创作会话 #${session.id}"
    }
}

internal data class StudioStory(
    val session: RoleplaySession,
    val conversation: Conversation,
    val characterNames: String,
    val scenarioName: String
) {
    val title: String get() = conversation.title
}

/** 只展示真实且可见的会话；隐藏故事与临时私密会话不能从创作入口绕过保护。 */
internal fun buildStudioStories(
    sessions: List<RoleplaySession>,
    conversations: List<Conversation>,
    characters: List<CharacterProfile>,
    scenarios: List<RoleplayScenario>,
    query: String = "",
    pinnedOnly: Boolean = false,
    narrativeMode: String? = null
): List<StudioStory> {
    val visibleConversations = conversations.filter {
        !it.tags.orEmpty().contains("hidden", ignoreCase = true) &&
            !it.tags.orEmpty().contains("private", ignoreCase = true)
    }.associateBy { it.id }
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return sessions.mapNotNull { session ->
        val conversation = visibleConversations[session.conversationId] ?: return@mapNotNull null
        if (pinnedOnly && !conversation.isPinned) return@mapNotNull null
        if (narrativeMode != null && session.narrativeMode != narrativeMode) return@mapNotNull null
        val characterNames = session.getParticipatingCharacters(
            characters.filter { it.id in session.getEffectiveCharacterIds() }
        ).joinToString("、") { it.name }
        val scenarioName = session.getCustomizedScenario(scenarios.firstOrNull { it.id == session.scenarioId })?.name.orEmpty()
        val story = StudioStory(session, conversation, characterNames, scenarioName)
        val searchable = listOf(story.title, characterNames, scenarioName, session.currentPlotSummary,
            NarrativeMode.fromValue(session.narrativeMode).displayName, conversation.modelName).joinToString("\n")
        story.takeIf { terms.all { searchable.contains(it, ignoreCase = true) } }
    }.sortedWith(compareByDescending<StudioStory> { it.conversation.isPinned }
        .thenByDescending { it.conversation.updatedAt }
        .thenByDescending { it.session.id })
}

internal fun filterRoleplaySessions(
    sessions: List<RoleplaySession>,
    characters: List<CharacterProfile>,
    scenarios: List<RoleplayScenario>,
    query: String
): List<RoleplaySession> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return sessions
    return sessions.filter { session ->
        val mode = NarrativeMode.fromValue(session.narrativeMode).displayName
        val label = roleplaySessionLabel(session, characters, scenarios)
        listOf(label, session.currentPlotSummary, mode, session.id.toString())
            .any { it.contains(normalized, ignoreCase = true) }
    }
}
