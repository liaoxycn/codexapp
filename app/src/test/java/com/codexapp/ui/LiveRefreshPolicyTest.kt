package com.codexapp.ui

import com.codexapp.model.ConnectionStatus
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.ThreadGroupKind
import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary
import com.codexapp.ui.state.selectedThreadNeedsLiveRefresh
import com.codexapp.ui.state.shouldContinueLiveRefresh
import com.codexapp.ui.state.shouldPollLiveRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRefreshPolicyTest {
    @Test
    fun keepsPollingWhenSelectedThreadHasTopLevelGeneratingSignal() {
        val snapshot = connectedSnapshot(
            selectedThreadId = "thread-1",
            threads = listOf(summary("thread-1", ThreadStatus.IDLE)),
            isGenerating = true,
            pendingApproval = "stale approval"
        )

        assertTrue(selectedThreadNeedsLiveRefresh(snapshot))
        assertTrue(shouldPollLiveRefresh(snapshot))
        assertTrue(shouldContinueLiveRefresh(snapshot, "thread-1"))
    }

    @Test
    fun doesNotPollTopLevelGeneratingWithoutSelectedThread() {
        val snapshot = connectedSnapshot(
            selectedThreadId = "",
            threads = listOf(summary("thread-1", ThreadStatus.IDLE)),
            isGenerating = true,
            pendingApproval = "stale approval"
        )

        assertFalse(selectedThreadNeedsLiveRefresh(snapshot))
        assertFalse(shouldPollLiveRefresh(snapshot))
        assertFalse(shouldContinueLiveRefresh(snapshot, "thread-1"))
    }

    @Test
    fun pollsRunningThread() {
        val snapshot = connectedSnapshot(
            selectedThreadId = "thread-1",
            threads = listOf(summary("thread-1", ThreadStatus.RUNNING))
        )

        assertTrue(selectedThreadNeedsLiveRefresh(snapshot))
        assertTrue(shouldPollLiveRefresh(snapshot))
        assertTrue(shouldContinueLiveRefresh(snapshot, "thread-1"))
    }

    @Test
    fun doesNotPollOldThreadWhileSelectionIsPending() {
        val snapshot = connectedSnapshot(
            selectedThreadId = "thread-1",
            threads = listOf(summary("thread-1", ThreadStatus.IDLE)),
            pendingSelectionThreadId = "thread-2",
            isThreadSwitching = true
        )

        assertTrue(selectedThreadNeedsLiveRefresh(snapshot))
        assertFalse(shouldPollLiveRefresh(snapshot))
        assertFalse(shouldContinueLiveRefresh(snapshot, "thread-1"))
    }

    private fun connectedSnapshot(
        selectedThreadId: String,
        threads: List<ThreadSummary>,
        isGenerating: Boolean = false,
        pendingApproval: String? = null,
        pendingSelectionThreadId: String? = null,
        isThreadSwitching: Boolean = false
    ) = SessionRemoteState(
        threads = threads,
        selectedThreadId = selectedThreadId,
        pendingSelectionThreadId = pendingSelectionThreadId,
        isGenerating = isGenerating,
        pendingApproval = pendingApproval,
        isThreadSwitching = isThreadSwitching,
        connectionStatus = ConnectionStatus.CONNECTED
    )

    private fun summary(id: String, status: ThreadStatus) = ThreadSummary(
        id = id,
        title = id,
        preview = "preview",
        status = status,
        groupKind = ThreadGroupKind.CHAT,
        groupLabel = "普通会话"
    )
}
