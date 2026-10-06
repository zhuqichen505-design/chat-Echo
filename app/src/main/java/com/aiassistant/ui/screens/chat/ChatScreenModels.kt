@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.aiassistant.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import com.aiassistant.domain.model.Message

data class BranchSuccessDialogState(
    val newConversationId: Long,
    val branchTitle: String
)

data class DisplayMessageItem(
    val message: Message,
    val groupId: String?,
    val variantInfo: VariantInfo? = null
)

data class ScrollFollowSnapshot(
    val isScrolling: Boolean,
    val lastVisibleIndex: Int,
    val totalItems: Int
) {
    val isAtBottom: Boolean
        get() = totalItems <= 0 || lastVisibleIndex >= totalItems - 2
}

data class VariantInfo(
    val groupId: String,
    val currentIndex: Int,
    val total: Int,
    val availableIndices: List<Int>
)

data class ParsedQuotedMessage(
    val quoteText: String,
    val replyText: String
)

data class CitationInfo(
    val index: Int,
    val title: String,
    val url: String,
    val snippet: String = ""
)
