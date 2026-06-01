package com.codex.mobile.ui.thread

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.codex.mobile.model.HomeUiState

@Composable
internal fun HandleThreadHistoryPaging(
    state: HomeUiState,
    listState: LazyListState,
    metrics: ThreadListMetrics,
    isAtTop: Boolean,
    onLoadOlderMessages: () -> Unit,
) {
    var topLoadArmed by rememberSaveable(state.selectedThreadId) { mutableStateOf(false) }
    var topLoadAnchorId by rememberSaveable(state.selectedThreadId) { mutableStateOf<String?>(null) }
    var topLoadAnchorOffset by rememberSaveable(state.selectedThreadId) { mutableIntStateOf(0) }

    LaunchedEffect(
        state.selectedThreadId,
        isAtTop,
        state.hasMoreHistory,
        state.isLoadingOlder,
        state.isThreadSwitching,
        state.messages.size,
        listState.isScrollInProgress
    ) {
        if (!state.hasMoreHistory || state.isThreadSwitching || state.messages.isEmpty()) {
            topLoadArmed = false
            topLoadAnchorId = null
            return@LaunchedEffect
        }
        if (!isAtTop) {
            topLoadArmed = true
            return@LaunchedEffect
        }
        if (!(isAtTop &&
                topLoadArmed &&
                !state.isLoadingOlder &&
                !listState.isScrollInProgress)
        ) {
            return@LaunchedEffect
        }
        val anchorInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.index >= metrics.messageItemStartIndex &&
                info.index < metrics.messageItemStartIndex + state.messages.size
        }
        if (anchorInfo != null) {
            val messageIndex = anchorInfo.index - metrics.messageItemStartIndex
            if (messageIndex in state.messages.indices) {
                topLoadAnchorId = state.messages[messageIndex].id
                topLoadAnchorOffset = anchorInfo.offset
            }
        } else {
            topLoadAnchorId = null
        }
        topLoadArmed = false
        onLoadOlderMessages()
    }

    LaunchedEffect(state.isLoadingOlder, state.messages.size, state.hasMoreHistory, state.selectedThreadId) {
        if (state.isLoadingOlder) return@LaunchedEffect
        val anchorId = topLoadAnchorId ?: return@LaunchedEffect
        val restoredIndex = restoredHistoryAnchorIndex(
            messages = state.messages,
            anchorId = anchorId,
            hasMoreHistory = state.hasMoreHistory
        )
        if (restoredIndex != null) {
            listState.scrollToItem(restoredIndex, topLoadAnchorOffset)
        }
        topLoadAnchorId = null
    }
}

internal fun restoredHistoryAnchorIndex(
    messages: List<com.codex.mobile.model.ThreadMessage>,
    anchorId: String,
    hasMoreHistory: Boolean
): Int? {
    val anchorIndex = messages.indexOfFirst { it.id == anchorId }
    if (anchorIndex < 0 || messages.isEmpty()) {
        return null
    }
    val restoredStartIndex = if (hasMoreHistory) 1 else 0
    return restoredStartIndex + anchorIndex
}
