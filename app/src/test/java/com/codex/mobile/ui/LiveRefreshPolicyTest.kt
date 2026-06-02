package com.codex.mobile.ui

import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.ThreadGroupKind
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary
import com.codex.mobile.ui.state.selectedThreadNeedsLiveRefresh
import com.codex.mobile.ui.state.shouldContinueLiveRefresh
import com.codex.mobile.ui.state.shouldPollLiveRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRefreshPolicyTest {
    @Test
    fun doesNotPollIdleThreadAfterSnapshotSync() {
        val snapshot = connectedSnapshot(
            selectedThreadId = "thread-1",
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
