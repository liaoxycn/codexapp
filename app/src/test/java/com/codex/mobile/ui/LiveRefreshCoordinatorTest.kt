package com.codex.mobile.ui

import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.ThreadGroupKind
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary
import com.codex.mobile.ui.state.LiveRefreshCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRefreshCoordinatorTest {
    @Test
    fun syncPollsRunningSelectedThreadUntilItBecomesIdle() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = LiveRefreshCoordinator(liveRefreshIntervalMs = 20L)
        var snapshot = connectedSnapshot("thread-1", ThreadStatus.RUNNING)
        var refreshCount = 0

        coordinator.sync(
            scope = scope,
            snapshot = snapshot,
            currentSnapshot = { snapshot },
            setManualRefreshing = {},
            refresh = {
                refreshCount += 1
                if (refreshCount == 2) {
                    snapshot = connectedSnapshot("thread-1", ThreadStatus.IDLE)
                }
            }
        )

        delay(90L)
        scope.cancel()

        assertEquals(2, refreshCount)
    }

    @Test
    fun syncStopsPollingWhenSelectionBecomesPending() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = LiveRefreshCoordinator(liveRefreshIntervalMs = 20L)
        var snapshot = connectedSnapshot("thread-1", ThreadStatus.RUNNING)
        var refreshCount = 0

        coordinator.sync(
            scope = scope,
            snapshot = snapshot,
            currentSnapshot = { snapshot },
            setManualRefreshing = {},
            refresh = {
                refreshCount += 1
                snapshot = connectedSnapshot(
                    selectedThreadId = "thread-1",
                    status = ThreadStatus.RUNNING,
                    pendingSelectionThreadId = "thread-2"
                )
            }
        )

        delay(90L)
        scope.cancel()

        assertEquals(1, refreshCount)
    }

    @Test
    fun syncDoesNotStartPollingIdleThread() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = LiveRefreshCoordinator(liveRefreshIntervalMs = 20L)
        val snapshot = connectedSnapshot("thread-1", ThreadStatus.IDLE)
        var refreshCount = 0

        coordinator.sync(
            scope = scope,
            snapshot = snapshot,
            currentSnapshot = { snapshot },
            setManualRefreshing = {},
            refresh = { refreshCount += 1 }
        )

        delay(60L)
        scope.cancel()

        assertEquals(0, refreshCount)
    }

    @Test
    fun syncDoesNotRestartExistingPollForSameThread() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = LiveRefreshCoordinator(liveRefreshIntervalMs = 30L)
        var snapshot = connectedSnapshot("thread-1", ThreadStatus.RUNNING)
        var refreshCount = 0

        repeat(3) {
            coordinator.sync(
                scope = scope,
                snapshot = snapshot,
                currentSnapshot = { snapshot },
                setManualRefreshing = {},
                refresh = {
                    refreshCount += 1
                    snapshot = connectedSnapshot("thread-1", ThreadStatus.IDLE)
                }
            )
        }

        delay(80L)
        scope.cancel()

        assertEquals(1, refreshCount)
    }

    private fun connectedSnapshot(
        selectedThreadId: String,
        status: ThreadStatus,
        pendingSelectionThreadId: String? = null
    ): SessionRemoteState {
        return SessionRemoteState(
            threads = listOf(summary(selectedThreadId, status)),
            selectedThreadId = selectedThreadId,
            pendingSelectionThreadId = pendingSelectionThreadId,
            isThreadSwitching = pendingSelectionThreadId != null,
            connectionStatus = ConnectionStatus.CONNECTED
        )
    }

    private fun summary(id: String, status: ThreadStatus) = ThreadSummary(
        id = id,
        title = id,
        preview = "preview",
        status = status,
        groupKind = ThreadGroupKind.CHAT,
        groupLabel = "普通会话"
    )
}
