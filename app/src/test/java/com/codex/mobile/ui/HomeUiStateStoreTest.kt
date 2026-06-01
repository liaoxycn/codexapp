package com.codex.mobile.ui

import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.ui.state.HomeUiStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateStoreTest {
    @Test
    fun stateCombinesRemoteAndComposerValues() {
        runBlocking {
            val remoteState = MutableStateFlow(
                SessionRemoteState(
                    selectedThreadId = "thread-1",
                    connectionStatus = ConnectionStatus.CONNECTED,
                    connectionDetail = "已连接"
                )
            )
            val composerText = MutableStateFlow("hello codex")
            val scope = CoroutineScope(Dispatchers.Unconfined + Job())
            val store = HomeUiStateStore(
                remoteState = remoteState,
                composerText = composerText,
                scope = scope
            )
            val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) { store.state.collect {} }

            yield()

            assertEquals("thread-1", store.state.value.selectedThreadId)
            assertEquals("hello codex", store.state.value.composerText)
            assertEquals(ConnectionStatus.CONNECTED, store.state.value.connectionStatus)
            assertEquals("已连接", store.state.value.connectionDetail)
            assertFalse(store.state.value.showComposerDetails)
            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun toggleAndCloseComposerDetailsOnlyAffectLocalUiState() {
        runBlocking {
            val remoteState = MutableStateFlow(SessionRemoteState(selectedThreadId = "thread-1"))
            val composerText = MutableStateFlow("")
            val scope = CoroutineScope(Dispatchers.Unconfined + Job())
            val store = HomeUiStateStore(
                remoteState = remoteState,
                composerText = composerText,
                scope = scope
            )
            val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) { store.state.collect {} }

            store.toggleComposerDetails()
            yield()
            assertTrue(store.state.value.showComposerDetails)

            store.closeComposerDetails()
            yield()
            assertFalse(store.state.value.showComposerDetails)
            assertEquals("thread-1", store.state.value.selectedThreadId)
            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
        }
    }
}
