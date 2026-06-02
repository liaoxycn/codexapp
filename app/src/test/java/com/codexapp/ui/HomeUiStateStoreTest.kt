package com.codexapp.ui

import com.codexapp.model.ConnectionStatus
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.ThreadMessage
import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary
import com.codexapp.ui.state.HomeUiStateStore
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

            assertEquals("", store.state.value.selectedThreadId)
            assertEquals("hello codex", store.state.value.composerText)
            assertEquals(ConnectionStatus.CONNECTED, store.state.value.connectionStatus)
            assertEquals("已连接", store.state.value.connectionDetail)
            assertEquals(0L, store.state.value.composerFocusRequest)
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
            assertEquals("", store.state.value.selectedThreadId)
            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun requestComposerFocusIncrementsFocusRequest() {
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

            store.requestComposerFocus()
            yield()

            assertEquals(1L, store.state.value.composerFocusRequest)
            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun exitAndStartNewThreadDraftSwitchVisibleSelection() {
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

            store.exitNewThreadDraft()
            yield()
            assertEquals("thread-1", store.state.value.selectedThreadId)

            store.startNewThreadDraft()
            yield()
            assertEquals("", store.state.value.selectedThreadId)
            assertTrue(store.state.value.isNewThreadDraft)
            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun draftSubmissionKeepsDraftScreenUntilRemoteThreadArrives() {
        runBlocking {
            val remoteState = MutableStateFlow(
                SessionRemoteState(
                    selectedThreadId = "thread-old",
                    messages = listOf(message("old")),
                    isGenerating = false
                )
            )
            val composerText = MutableStateFlow("")
            val scope = CoroutineScope(Dispatchers.Unconfined + Job())
            val store = HomeUiStateStore(
                remoteState = remoteState,
                composerText = composerText,
                scope = scope
            )
            val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) { store.state.collect {} }

            store.markDraftSubmissionStarted()
            remoteState.value = remoteState.value.copy(
                selectedThreadId = "",
                pendingThreadTitle = "新会话",
                isThreadSwitching = true,
                messages = listOf(message("user-new"), message("assistant-pending")),
                isGenerating = true
            )
            yield()

            assertTrue(store.state.value.isNewThreadDraft)
            assertEquals("", store.state.value.selectedThreadId)
            assertEquals(listOf("user-new", "assistant-pending"), store.state.value.messages.map { it.id })
            assertTrue(store.state.value.isGenerating)

            remoteState.value = remoteState.value.copy(
                selectedThreadId = "thread-new",
                pendingThreadTitle = null,
                isThreadSwitching = false
            )
            store.syncRemoteSelection(remoteState.value)
            yield()

            assertFalse(store.state.value.isNewThreadDraft)
            assertEquals("thread-new", store.state.value.selectedThreadId)
            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun forkPendingClearsOnlyAfterNewSelectedThreadAppearsInCatalog() {
        runBlocking {
            val remoteState = MutableStateFlow(
                SessionRemoteState(
                    selectedThreadId = "thread-source",
                    threads = listOf(summary("thread-source")),
                    connectionStatus = ConnectionStatus.CONNECTED
                )
            )
            val composerText = MutableStateFlow("")
            val scope = CoroutineScope(Dispatchers.Unconfined + Job())
            val store = HomeUiStateStore(
                remoteState = remoteState,
                composerText = composerText,
                scope = scope
            )
            val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) { store.state.collect {} }

            store.exitNewThreadDraft()
            store.markForkStarted("thread-source")
            yield()

            assertTrue(store.state.value.isForkingThread)

            remoteState.value = remoteState.value.copy(selectedThreadId = "thread-fork")
            store.syncRemoteSelection(remoteState.value)
            yield()

            assertTrue(store.state.value.isForkingThread)

            remoteState.value = remoteState.value.copy(
                selectedThreadId = "thread-fork",
                threads = listOf(summary("thread-source"), summary("thread-fork"))
            )
            store.syncRemoteSelection(remoteState.value)
            yield()

            assertFalse(store.state.value.isForkingThread)
            assertEquals("thread-fork", store.state.value.selectedThreadId)
            collector.cancel()
            scope.coroutineContext[Job]?.cancel()
        }
    }

    private fun message(id: String): ThreadMessage {
        return ThreadMessage(
            id = id,
            role = MessageRole.USER,
            blocks = listOf(MessageBlock.Text(id))
        )
    }

    private fun summary(id: String): ThreadSummary {
        return ThreadSummary(
            id = id,
            title = id,
            preview = "",
            status = ThreadStatus.IDLE,
            updatedAt = 1L
        )
    }
}
