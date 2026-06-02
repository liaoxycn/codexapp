package com.codexapp.ui.thread

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.codexapp.model.HomeUiState

internal data class ThreadHistoryPagingController(
    val nestedScrollConnection: NestedScrollConnection
)

@Composable
internal fun rememberThreadHistoryPagingController(
    state: HomeUiState,
    listState: LazyListState,
    metrics: ThreadListMetrics,
    isAtTop: Boolean,
    onLoadOlderMessages: () -> Unit,
): ThreadHistoryPagingController {
    var topLoadArmed by rememberSaveable(state.selectedThreadId) { mutableStateOf(false) }
    var topPullDistance by rememberSaveable(state.selectedThreadId) { mutableFloatStateOf(0f) }
    var topLoadAnchorId by rememberSaveable(state.selectedThreadId) { mutableStateOf<String?>(null) }
    var topLoadAnchorOffset by rememberSaveable(state.selectedThreadId) { mutableIntStateOf(0) }
    val topPullThreshold = 88f

    fun resetTopPull() {
        topPullDistance = 0f
        topLoadArmed = false
    }

    fun captureHistoryAnchor() {
        val anchorInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.index >= metrics.messageItemStartIndex &&
                info.index < metrics.messageItemStartIndex + state.messages.size
        }
        if (anchorInfo != null) {
            val messageIndex = anchorInfo.index - metrics.messageItemStartIndex
            if (messageIndex in state.messages.indices) {
                topLoadAnchorId = state.messages[messageIndex].id
                topLoadAnchorOffset = anchorInfo.offset
                return
            }
        }
        topLoadAnchorId = null
    }

    val nestedScrollConnection = remember(
        state.selectedThreadId,
        isAtTop,
        state.hasMoreHistory,
        state.isLoadingOlder,
        state.isThreadSwitching,
        state.messages.size,
        topPullThreshold
    ) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!state.hasMoreHistory || state.isThreadSwitching || state.messages.isEmpty() || state.isLoadingOlder) {
                    resetTopPull()
                    return Offset.Zero
                }
                if (!isAtTop) {
                    resetTopPull()
                    return Offset.Zero
                }
                val downwardDrag = consumed.y.coerceAtLeast(available.y).coerceAtLeast(0f)
                if (downwardDrag > 0f) {
                    topPullDistance = (topPullDistance + downwardDrag).coerceAtMost(220f)
                    topLoadArmed = true
                    if (shouldTriggerHistoryLoad(
                            isAtTop = isAtTop,
                            hasMoreHistory = state.hasMoreHistory,
                            isLoadingOlder = state.isLoadingOlder,
                            isThreadSwitching = state.isThreadSwitching,
                            hasMessages = state.messages.isNotEmpty(),
                            pullDistance = topPullDistance,
                            pullThreshold = topPullThreshold,
                            loadArmed = topLoadArmed
                        )
                    ) {
                        captureHistoryAnchor()
                        resetTopPull()
                        onLoadOlderMessages()
                    }
                } else if ((consumed.y < 0f || available.y < 0f) && topPullDistance > 0f) {
                    resetTopPull()
                }
                return Offset.Zero
            }
        }
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

    return ThreadHistoryPagingController(nestedScrollConnection = nestedScrollConnection)
}

internal fun restoredHistoryAnchorIndex(
    messages: List<com.codexapp.model.ThreadMessage>,
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

internal fun shouldTriggerHistoryLoad(
    isAtTop: Boolean,
    hasMoreHistory: Boolean,
    isLoadingOlder: Boolean,
    isThreadSwitching: Boolean,
    hasMessages: Boolean,
    pullDistance: Float,
    pullThreshold: Float,
    loadArmed: Boolean
): Boolean {
    return isAtTop &&
        hasMoreHistory &&
        !isLoadingOlder &&
        !isThreadSwitching &&
        hasMessages &&
        loadArmed &&
        pullDistance >= pullThreshold
}
