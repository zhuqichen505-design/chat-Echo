@file:OptIn(ExperimentalMaterial3Api::class)

package com.aiassistant.ui.screens.chat

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.aiassistant.domain.model.ChatModelOption
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.NarrativeMode
import com.aiassistant.domain.model.PlotAction
import com.aiassistant.domain.model.PromptTemplate
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.domain.model.RoleplaySession
import com.aiassistant.ui.screens.roleplay.ConflictAction
import com.aiassistant.utils.AtemporalSettingItem
import com.aiassistant.utils.TimelineEventItem
import com.aiassistant.utils.TimelineReconcileResult
import dev.chrisbanes.haze.HazeState

/**
 * 剧情与角色扮演相关对话框门面（向后兼容保留，实际实现拆分至 com.aiassistant.ui.screens.chat.story 子包）
 */

@Composable
fun StoryUnifiedSettingsDialog(
    hazeState: HazeState,
    session: RoleplaySession,
    characters: List<CharacterProfile>,
    allCharacters: List<CharacterProfile>,
    scenario: RoleplayScenario?,
    allScenarios: List<RoleplayScenario>,
    narrativeMode: NarrativeMode,
    currentOption: ChatModelOption?,
    fallbackModel: String,
    availableOptions: List<ChatModelOption>,
    tempSettings: TempChatSettings,
    currentPrompt: String?,
    templates: List<PromptTemplate>,
    onDismiss: () -> Unit,
    onSaveAll: (
        selectedCharIds: List<Long>,
        selectedScenarioId: Long?,
        mode: NarrativeMode,
        plotSummary: String,
        newSettings: TempChatSettings,
        newPrompt: String?
    ) -> Unit,
    onPlotAction: (PlotAction, String?) -> Unit,
    onSummarizeMemories: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onOpenSmartAppend: () -> Unit,
    onConvertToNormal: () -> Unit = {},
    onSaveLocalCharacter: (CharacterProfile) -> Unit = {},
    onDeleteLocalCharacter: (CharacterProfile) -> Unit = {},
    onSaveLocalScenario: (RoleplayScenario) -> Unit = {},
    onDeleteLocalScenario: () -> Unit = {},
    onModelSelected: (ChatModelOption) -> Unit,
    onSavePromptTemplate: (String, String) -> Unit,
    onModelAvatarChanged: () -> Unit
) {
    com.aiassistant.ui.screens.chat.story.StoryUnifiedSettingsDialog(
        hazeState = hazeState,
        session = session,
        characters = characters,
        allCharacters = allCharacters,
        scenario = scenario,
        allScenarios = allScenarios,
        narrativeMode = narrativeMode,
        currentOption = currentOption,
        fallbackModel = fallbackModel,
        availableOptions = availableOptions,
        tempSettings = tempSettings,
        currentPrompt = currentPrompt,
        templates = templates,
        onDismiss = onDismiss,
        onSaveAll = onSaveAll,
        onPlotAction = onPlotAction,
        onSummarizeMemories = onSummarizeMemories,
        onNavigateToMemory = onNavigateToMemory,
        onOpenSmartAppend = onOpenSmartAppend,
        onConvertToNormal = onConvertToNormal,
        onSaveLocalCharacter = onSaveLocalCharacter,
        onDeleteLocalCharacter = onDeleteLocalCharacter,
        onSaveLocalScenario = onSaveLocalScenario,
        onDeleteLocalScenario = onDeleteLocalScenario,
        onModelSelected = onModelSelected,
        onSavePromptTemplate = onSavePromptTemplate,
        onModelAvatarChanged = onModelAvatarChanged
    )
}

@Composable
fun EditableSettingProposalDialog(
    hazeState: HazeState,
    proposal: ProposedSettingBundle,
    onDismiss: () -> Unit,
    onApply: (List<CharacterProfile>, RoleplayScenario?) -> Unit
) {
    com.aiassistant.ui.screens.chat.story.EditableSettingProposalDialog(
        hazeState = hazeState,
        proposal = proposal,
        onDismiss = onDismiss,
        onApply = onApply
    )
}

@Composable
fun ActionChip(text: String, onClick: () -> Unit) {
    com.aiassistant.ui.screens.chat.story.ActionChip(text = text, onClick = onClick)
}

@Composable
fun SmartAppendStoryDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onAppendAndMerge: (List<CharacterProfile>, RoleplayScenario?, Map<String, ConflictAction>) -> Unit
) {
    com.aiassistant.ui.screens.chat.story.SmartAppendStoryDialog(
        hazeState = hazeState,
        onDismiss = onDismiss,
        onAppendAndMerge = onAppendAndMerge
    )
}

@Composable
fun TimelineReconcileDialog(
    hazeState: HazeState,
    initialResult: TimelineReconcileResult,
    onDismiss: () -> Unit,
    onApply: (String, List<TimelineEventItem>, List<AtemporalSettingItem>) -> Unit
) {
    com.aiassistant.ui.screens.chat.story.TimelineReconcileDialog(
        hazeState = hazeState,
        initialResult = initialResult,
        onDismiss = onDismiss,
        onApply = onApply
    )
}
