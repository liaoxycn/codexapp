package com.codex.mobile.ui.thread

import com.codex.mobile.ui.message.revisionKey

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.codex.mobile.model.HomeUiState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection

internal data class ThreadListController(
    val listState: LazyListState,
    val nestedScrollConnection: NestedScrollConnection,
    val pullProgress: Float,
    val showPullHint: Boolean,
    val isLoadingOlder: Boolean,
    val metrics: ThreadListMetrics,
)

@Composable
internal fun rememberThreadListController(
    state: HomeUiState,
    compactMode: Boolean,
    onRefreshCurrent: () -> Unit,
    onLoadOlderMessages: () -> Unit,
): ThreadListController {
    val listState = rememberLazyListState()
    val lastMessageRevision = state.messages.lastOrNull()?.revisionKey()
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val isAtBottom by remember {
        derivedStateOf {
            listState.layoutInfo.totalItemsCount == 0 || !listState.canScrollForward
        }
    }
    val metrics = calculateThreadListMetrics(state, compactMode, isAtBottom)
    val pullRefresh = rememberThreadPullRefreshController(
        selectedThreadId = state.selectedThreadId,
        isGenerating = state.isGenerating,
        isManualRefreshing = state.isManualRefreshing,
        isAtBottom = isAtBottom,
        onRefreshCurrent = onRefreshCurrent,
    )

    HandleThreadAutoScroll(
        state = state,
        listState = listState,
        metrics = metrics,
        lastMessageRevision = lastMessageRevision,
        isAtBottom = isAtBottom,
    )
    HandleThreadHistoryPaging(
        state = state,
        listState = listState,
        metrics = metrics,
        isAtTop = isAtTop,
        onLoadOlderMessages = onLoadOlderMessages,
    )

    return ThreadListController(
        listState = listState,
        nestedScrollConnection = pullRefresh.nestedScrollConnection,
        pullProgress = pullRefresh.pullProgress,
        showPullHint = pullRefresh.showPullHint,
        isLoadingOlder = state.isLoadingOlder,
        metrics = metrics,
    )
}
