package com.codexapp.ui.thread

import com.codexapp.ui.message.revisionKey

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.codexapp.model.HomeUiState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity

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
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 24
        }
    }
    val isAtBottom by remember {
        derivedStateOf {
            listState.layoutInfo.totalItemsCount == 0 || !listState.canScrollForward ||
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == listState.layoutInfo.totalItemsCount - 1
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
    val historyPaging = rememberThreadHistoryPagingController(
        state = state,
        listState = listState,
        metrics = metrics,
        isAtTop = isAtTop,
        onLoadOlderMessages = onLoadOlderMessages,
    )
    val nestedScrollConnection = remember(historyPaging.nestedScrollConnection, pullRefresh.nestedScrollConnection) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): Offset {
                historyPaging.nestedScrollConnection.onPostScroll(consumed, available, source)
                pullRefresh.nestedScrollConnection.onPostScroll(consumed, available, source)
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                historyPaging.nestedScrollConnection.onPostFling(consumed, available)
                pullRefresh.nestedScrollConnection.onPostFling(consumed, available)
                return Velocity.Zero
            }
        }
    }

    HandleThreadAutoScroll(
        state = state,
        listState = listState,
        metrics = metrics,
        lastMessageRevision = lastMessageRevision,
        isAtBottom = isAtBottom,
    )
    return ThreadListController(
        listState = listState,
        nestedScrollConnection = nestedScrollConnection,
        pullProgress = pullRefresh.pullProgress,
        showPullHint = pullRefresh.showPullHint,
        isLoadingOlder = state.isLoadingOlder,
        metrics = metrics,
    )
}
