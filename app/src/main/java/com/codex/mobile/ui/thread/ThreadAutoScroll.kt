package com.codex.mobile.ui.thread

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.codex.mobile.model.HomeUiState

@Composable
internal fun HandleThreadAutoScroll(
    state: HomeUiState,
    listState: LazyListState,
    metrics: ThreadListMetrics,
    lastMessageRevision: String?,
    isAtBottom: Boolean,
) {
    val userWasAtBottom = rememberSaveable(state.selectedThreadId) { mutableStateOf(true) }

    LaunchedEffect(isAtBottom) {
        userWasAtBottom.value = isAtBottom
    }

    LaunchedEffect(
        lastMessageRevision,
        state.pendingApproval,
        state.selectedThreadId,
        state.isGenerating,
        state.isManualRefreshing,
        state.isLoadingOlder
    ) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        if (state.isManualRefreshing || state.isLoadingOlder || state.pendingApproval != null) return@LaunchedEffect
        if (!userWasAtBottom.value) return@LaunchedEffect
        if (state.isGenerating) {
            listState.scrollToItem(metrics.lastItemIndex)
        } else {
            listState.animateScrollToItem(metrics.lastItemIndex)
        }
    }
}
