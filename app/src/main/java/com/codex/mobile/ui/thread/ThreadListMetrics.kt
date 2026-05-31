package com.codex.mobile.ui.thread

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.HomeUiState

internal data class ThreadListMetrics(
    val composerPadding: Dp,
    val messageItemStartIndex: Int,
    val lastItemIndex: Int,
    val showConnectionBanner: Boolean,
    val showJumpToBottom: Boolean,
    val hasVisibleMessages: Boolean,
)

internal fun calculateThreadListMetrics(
    state: HomeUiState,
    compactMode: Boolean,
    isAtBottom: Boolean,
): ThreadListMetrics {
    val composerPadding = when {
        state.showComposerDetails && compactMode -> 278.dp
        state.showComposerDetails -> 298.dp
        compactMode -> 106.dp
        else -> 114.dp
    }
    val hasVisibleMessages = state.messages.isNotEmpty() && !state.isThreadSwitching
    val messageItemStartIndex = if (state.hasMoreHistory) 1 else 0
    val contentItemCount = (if (state.messages.isEmpty()) 1 else state.messages.size) +
        (if (state.pendingApproval != null) 1 else 0)
    val totalItems = messageItemStartIndex + contentItemCount
    return ThreadListMetrics(
        composerPadding = composerPadding,
        messageItemStartIndex = messageItemStartIndex,
        lastItemIndex = (totalItems - 1).coerceAtLeast(0),
        showConnectionBanner = state.connectionStatus == ConnectionStatus.DISCONNECTED ||
            state.connectionStatus == ConnectionStatus.ERROR,
        showJumpToBottom = hasVisibleMessages && !isAtBottom && !state.showComposerDetails,
        hasVisibleMessages = hasVisibleMessages,
    )
}
