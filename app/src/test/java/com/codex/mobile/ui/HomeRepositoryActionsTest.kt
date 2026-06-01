package com.codex.mobile.ui

import com.codex.mobile.data.SessionRepository
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.ui.state.HomeRepositoryActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.startCoroutine

class HomeRepositoryActionsTest {
    @Test
    fun connectTriggersManualHookAndForwardsGatewayConfig() {
        val repository = FakeSessionRepository()
        var manualConnectCalls = 0
        val actions = HomeRepositoryActions(
            repository = repository,
            launch = { block -> runBlockingImmediate(block) },
            onManualConnect = { manualConnectCalls += 1 },
            onManualDisconnect = {},
            runAnimatedRefresh = {}
        )

        actions.connect("ws://gateway", "pair-token")

        assertEquals(1, manualConnectCalls)
        assertEquals(listOf(GatewayConfig(url = "ws://gateway", pairToken = "pair-token")), repository.connectCalls)
    }

    @Test
    fun disconnectAndRefreshUseTheirDedicatedHooks() {
        val repository = FakeSessionRepository()
        var manualDisconnectCalls = 0
        var animatedRefreshCalls = 0
        val actions = HomeRepositoryActions(
            repository = repository,
            launch = { block -> runBlockingImmediate(block) },
            onManualConnect = {},
            onManualDisconnect = { manualDisconnectCalls += 1 },
            runAnimatedRefresh = { animatedRefreshCalls += 1 }
        )

        actions.disconnect()
        actions.refreshCurrentThreadAnimated()

        assertEquals(1, manualDisconnectCalls)
        assertEquals(1, animatedRefreshCalls)
        assertEquals(1, repository.disconnectCalls)
    }

    @Test
    fun repositoryActionsForwardToExpectedMethods() {
        val repository = FakeSessionRepository()
        val actions = HomeRepositoryActions(
            repository = repository,
            launch = { block -> runBlockingImmediate(block) },
            onManualConnect = {},
            onManualDisconnect = {},
            runAnimatedRefresh = {}
        )

        actions.selectThread("thread-1")
        actions.createThread("D:/Projects/Test")
        actions.forkThread("thread-fork")
        actions.renameThread("thread-1", "Renamed")
        actions.archiveThread("thread-2")
        actions.unarchiveThread("thread-3")
        actions.refreshThreads()
        actions.loadOlderMessages()
        actions.stopGenerating()
        actions.approvePending()
        actions.rejectPending()

        assertEquals(listOf("thread-1"), repository.selectCalls)
        assertEquals(listOf("D:/Projects/Test"), repository.createThreadCalls)
        assertEquals(listOf("thread-fork"), repository.forkThreadCalls)
        assertEquals(listOf("thread-1" to "Renamed"), repository.renameThreadCalls)
        assertEquals(listOf("thread-2"), repository.archiveThreadCalls)
        assertEquals(listOf("thread-3"), repository.unarchiveThreadCalls)
        assertEquals(1, repository.refreshThreadsCalls)
        assertEquals(1, repository.loadOlderMessagesCalls)
        assertEquals(1, repository.stopTurnCalls)
        assertEquals(1, repository.approveCalls)
        assertEquals(1, repository.rejectCalls)
    }

    private class FakeSessionRepository : SessionRepository {
        private val snapshot = MutableStateFlow(SessionRemoteState())
        val connectCalls = mutableListOf<GatewayConfig>()
        var disconnectCalls = 0
        val createThreadCalls = mutableListOf<String?>()
        val selectCalls = mutableListOf<String>()
        val forkThreadCalls = mutableListOf<String>()
        val renameThreadCalls = mutableListOf<Pair<String, String>>()
        val archiveThreadCalls = mutableListOf<String>()
        val unarchiveThreadCalls = mutableListOf<String>()
        var refreshThreadsCalls = 0
        var loadOlderMessagesCalls = 0
        var stopTurnCalls = 0
        var approveCalls = 0
        var rejectCalls = 0

        override val state: StateFlow<SessionRemoteState> = snapshot

        override suspend fun connect(config: GatewayConfig) {
            connectCalls += config
        }

        override suspend fun disconnect() {
            disconnectCalls += 1
        }

        override suspend fun createThread(cwd: String?) {
            createThreadCalls += cwd
        }

        override suspend fun selectThread(id: String) {
            selectCalls += id
        }

        override suspend fun forkThread(id: String) {
            forkThreadCalls += id
        }

        override suspend fun renameThread(id: String, name: String) {
            renameThreadCalls += id to name
        }

        override suspend fun archiveThread(id: String) {
            archiveThreadCalls += id
        }

        override suspend fun unarchiveThread(id: String) {
            unarchiveThreadCalls += id
        }

        override suspend fun refreshThreads() {
            refreshThreadsCalls += 1
        }

        override suspend fun loadOlderMessages() {
            loadOlderMessagesCalls += 1
        }

        override fun markManualRefreshing(refreshing: Boolean) = Unit

        override suspend fun sendPrompt(prompt: String): Boolean = true

        override suspend fun stopTurn() {
            stopTurnCalls += 1
        }

        override suspend fun approvePending() {
            approveCalls += 1
        }

        override suspend fun rejectPending() {
            rejectCalls += 1
        }
    }

    private fun runBlockingImmediate(block: suspend () -> Unit) {
        var failure: Throwable? = null
        block.startCoroutine(
            object : kotlin.coroutines.Continuation<Unit> {
                override val context = kotlin.coroutines.EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    failure = result.exceptionOrNull()
                }
            }
        )
        failure?.let { throw it }
    }
}
