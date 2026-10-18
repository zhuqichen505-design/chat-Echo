package com.aiassistant.ui.screens.chat

/** 接收外部模型更新时，只更新用户尚未编辑的字段。 */
internal fun mergeChatSettingsDraft(
    local: TempChatSettings,
    previous: TempChatSettings,
    incoming: TempChatSettings
): TempChatSettings = TempChatSettings(
    temperature = if (local.temperature == previous.temperature) incoming.temperature else local.temperature,
    maxTokens = if (local.maxTokens == previous.maxTokens) incoming.maxTokens else local.maxTokens,
    topP = if (local.topP == previous.topP) incoming.topP else local.topP,
    enableThinking = if (local.enableThinking == previous.enableThinking) incoming.enableThinking else local.enableThinking,
    thinkingEffort = if (local.thinkingEffort == previous.thinkingEffort) incoming.thinkingEffort else local.thinkingEffort,
    enableWebSearch = if (local.enableWebSearch == previous.enableWebSearch) incoming.enableWebSearch else local.enableWebSearch,
    enableSessionMemory = if (local.enableSessionMemory == previous.enableSessionMemory) incoming.enableSessionMemory else local.enableSessionMemory,
    enableTimeline = if (local.enableTimeline == previous.enableTimeline) incoming.enableTimeline else local.enableTimeline,
    enableExternalMemory = if (local.enableExternalMemory == previous.enableExternalMemory) incoming.enableExternalMemory else local.enableExternalMemory,
    enableWorldBook = if (local.enableWorldBook == previous.enableWorldBook) incoming.enableWorldBook else local.enableWorldBook,
    enableReplyDirections = if (local.enableReplyDirections == previous.enableReplyDirections) incoming.enableReplyDirections else local.enableReplyDirections,
    replyDirectionCount = if (local.replyDirectionCount == previous.replyDirectionCount) incoming.replyDirectionCount else local.replyDirectionCount,
    activeWorldBookIds = if (local.activeWorldBookIds == previous.activeWorldBookIds) incoming.activeWorldBookIds else local.activeWorldBookIds,
    contextWindowTokens = if (local.contextWindowTokens == previous.contextWindowTokens) incoming.contextWindowTokens else local.contextWindowTokens
)
