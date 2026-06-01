package com.codex.mobile.ui

import com.codex.mobile.data.SessionRepository
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.ui.state.ComposerSession
import com.codex.mobile.ui.state.HomeRepositoryCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    private class FakeSessionRepository(
        initial: SessionRemoteState
    ) : SessionRepository {
        val snapshot = MutableStateFlow(initial)
        val connectCalls = mutableListOf<GatewayConfig>()

        override val state: StateFlow<SessionRemoteState> = snapshot

        override suspend fun connect(config: GatewayConfig) {
            connectCalls += config
        }

        override suspend fun disconnect() = Unit

        override suspend fun createThread(cwd: String?) = Unit

        override suspend fun selectThread(id: String) = Unit

        override suspend fun forkThread(id: String) = Unit

        override suspend fun renameThread(id: String, name: String) = Unit

        override suspend fun archiveThread(id: String) = Unit

        override suspend fun unarchiveThread(id: String) = Unit

        override suspend fun refreshThreads() = Unit

        override suspend fun loadOlderMessages() = Unit

        override fun markManualRefreshing(refreshing: Boolean) = Unit

        override suspend fun sendPrompt(prompt: String): Boolean = true

        override suspend fun stopTurn() = Unit

        override suspend fun approvePending() = Unit

        override suspend fun rejectPending() = Unit
    }
}
