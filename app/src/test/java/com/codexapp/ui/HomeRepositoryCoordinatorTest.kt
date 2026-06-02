package com.codexapp.ui

import com.codexapp.data.SessionRepository
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.GatewayConfig
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary
import com.codexapp.ui.state.ComposerSession
import com.codexapp.ui.state.HomeRepositoryCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRepositoryCoordinatorTest {
    @Test
    fun startConnectsSavedGatewayAndObservesSnapshots() {
        runBlocking {
            val repository = FakeSessionRepository(
                initial = SessionRemoteState(
                    gatewayConfig = GatewayConfig(url = "ws://saved-gateway", pairToken = "pair"),
                    connectionStatus = ConnectionStatus.DISCONNECTED
                )
            )
            val scope = CoroutineScope(coroutineContext + Job())
            val composerSession = ComposerSession(initialThreadId = "")
            composerSession.replace("thread-2 draft", "thread-2")
            composerSession.clear()
            val coordinator = HomeRepositoryCoordinator(
                repository = repository,
                scope = scope,
                composerSession = composerSession
            )

            coordinator.start()
            yield()

            assertEquals(
                listOf(GatewayConfig(url = "ws://saved-gateway", pairToken = "pair")),
                repository.connectCalls
            )
            assertEquals("", composerSession.currentText())

            repository.snapshot.value = repository.snapshot.value.copy(selectedThreadId = "thread-2")
            yield()

            assertEquals("thread-2 draft", composerSession.currentText())
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun startIsIdempotent() {
        runBlocking {
            val repository = FakeSessionRepository(
                initial = SessionRemoteState(
                    gatewayConfig = GatewayConfig(url = "ws://saved-gateway")
                )
            )
            val scope = CoroutineScope(coroutineContext + Job())
            val composerSession = ComposerSession(initialThreadId = "")
            val coordinator = HomeRepositoryCoordinator(
                repository = repository,
                scope = scope,
                composerSession = composerSession
            )

            coordinator.start()
            coordinator.start()
            yield()

            assertEquals(1, repository.connectCalls.size)
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun runningSnapshotsDoNotTriggerAppSideAutoRefresh() {
        runBlocking {
            val repository = FakeSessionRepository(
                initial = SessionRemoteState(
                    selectedThreadId = "thread-1",
                    threads = listOf(
                        ThreadSummary(
                            id = "thread-1",
                            title = "项目会话测试",
                            preview = "hello",
                            status = ThreadStatus.RUNNING
                        )
                    ),
                    connectionStatus = ConnectionStatus.CONNECTED
                )
            )
            val scope = CoroutineScope(coroutineContext + Job())
            val coordinator = HomeRepositoryCoordinator(
                repository = repository,
                scope = scope,
                composerSession = ComposerSession(initialThreadId = "thread-1")
            )

            coordinator.start()
            repeat(3) { yield() }

            assertEquals(0, repository.refreshCalls)
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun disconnectedSnapshotSchedulesSingleReconnect() = runBlocking {
        val repository = FakeSessionRepository(
            initial = SessionRemoteState(
                gatewayConfig = GatewayConfig(url = "ws://saved-gateway", pairToken = "pair"),
                connectionStatus = ConnectionStatus.DISCONNECTED
            )
        )
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = HomeRepositoryCoordinator(
            repository = repository,
            scope = scope,
            composerSession = ComposerSession(initialThreadId = "")
        )

        coordinator.start()
        repeat(2) { yield() }
        delay(1_650L)

        assertEquals(
            listOf(
                GatewayConfig(url = "ws://saved-gateway", pairToken = "pair"),
                GatewayConfig(url = "ws://saved-gateway", pairToken = "pair")
            ),
            repository.connectCalls
        )
        scope.cancel()
    }

    @Test
    fun manualDisconnectPreventsAutoReconnect() = runBlocking {
        val repository = FakeSessionRepository(
            initial = SessionRemoteState(
                gatewayConfig = GatewayConfig(url = "ws://saved-gateway", pairToken = "pair"),
                connectionStatus = ConnectionStatus.DISCONNECTED
            )
        )
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = HomeRepositoryCoordinator(
            repository = repository,
            scope = scope,
            composerSession = ComposerSession(initialThreadId = "")
        )

        coordinator.start()
        repeat(2) { yield() }
        coordinator.onManualDisconnect()
        delay(1_650L)

        assertEquals(
            listOf(GatewayConfig(url = "ws://saved-gateway", pairToken = "pair")),
            repository.connectCalls
        )
        scope.cancel()
    }

    @Test
    fun manualConnectCancelsPendingAutoReconnect() = runBlocking {
        val repository = FakeSessionRepository(
            initial = SessionRemoteState(
                gatewayConfig = GatewayConfig(url = "ws://saved-gateway", pairToken = "pair"),
                connectionStatus = ConnectionStatus.DISCONNECTED
            ),
            connectBlocker = CompletableDeferred()
        )
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = HomeRepositoryCoordinator(
            repository = repository,
            scope = scope,
            composerSession = ComposerSession(initialThreadId = "")
        )

        coordinator.start()
        yield()
        coordinator.onManualConnect()
        delay(1_650L)
        repository.connectBlocker?.complete(Unit)
        yield()

        assertEquals(
            listOf(GatewayConfig(url = "ws://saved-gateway", pairToken = "pair")),
            repository.connectCalls
        )
        scope.cancel()
    }

    private class FakeSessionRepository(
        initial: SessionRemoteState,
        val connectBlocker: CompletableDeferred<Unit>? = null
    ) : SessionRepository {
        val snapshot = MutableStateFlow(initial)
        val connectCalls = mutableListOf<GatewayConfig>()
        var refreshCalls = 0

        override val state: StateFlow<SessionRemoteState> = snapshot

        override suspend fun connect(config: GatewayConfig) {
            connectCalls += config
            connectBlocker?.await()
        }

        override suspend fun disconnect() = Unit

        override suspend fun createThread(cwd: String?, draft: NewThreadDraft?) = Unit

        override suspend fun selectThread(id: String) = Unit

        override suspend fun forkThread(id: String, numTurns: Int?) = Unit

        override suspend fun renameThread(id: String, name: String) = Unit

        override suspend fun archiveThread(id: String) = Unit

        override suspend fun unarchiveThread(id: String) = Unit

        override suspend fun refreshThreads() {
            refreshCalls += 1
        }

        override suspend fun loadOlderMessages() = Unit

        override fun markManualRefreshing(refreshing: Boolean) = Unit

        override suspend fun sendPrompt(prompt: String, draft: NewThreadDraft?, newThread: Boolean): Boolean = true

        override suspend fun rollbackThread(numTurns: Int): Boolean = true

        override suspend fun resendPrompt(prompt: String, rollbackNumTurns: Int, draft: NewThreadDraft?): Boolean = true

        override suspend fun stopTurn() = Unit

        override suspend fun approvePending() = Unit

        override suspend fun rejectPending() = Unit

        override suspend fun restartDesktop() = Unit
    }
}
