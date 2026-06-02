package com.codexapp.ui

import com.codexapp.model.ConnectionStatus
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.ThreadGroupKind
import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary
import com.codexapp.ui.state.LiveRefreshCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRefreshCoordinatorTest {
    @Test
    fun syncDoesNotPollRunningSelectedThread() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = LiveRefreshCoordinator()
        var snapshot = connectedSnapshot("thread-1", ThreadStatus.RUNNING)
        var refreshCount = 0

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

        delay(90L)
        scope.cancel()

        assertEquals(0, refreshCount)
    }

    @Test
    fun syncDoesNotPollWhenSelectionBecomesPending() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = LiveRefreshCoordinator()
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

        assertEquals(0, refreshCount)
    }

    @Test
    fun syncDoesNotStartPollingIdleThread() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = LiveRefreshCoordinator()
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
    fun repeatedSyncDoesNotStartPollingForSameThread() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = LiveRefreshCoordinator()
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

        assertEquals(0, refreshCount)
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
