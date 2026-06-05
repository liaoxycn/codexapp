package com.codexapp.ui

import com.codexapp.data.startCreatingThread
import com.codexapp.data.startSelectingThread
import com.codexapp.data.withConnectionFailure
import com.codexapp.data.withArchivedThreadLocally
import com.codexapp.data.withDisconnectedGateway
import com.codexapp.data.withInboundDecodeFailure
import com.codexapp.data.withManualDisconnect
import com.codexapp.data.withOptimisticPrompt
import com.codexapp.data.withSendFailure
import com.codexapp.data.withUnavailableAction
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.StateDiagnostics
import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRemoteMutationsTest {
    @Test
    fun startCreatingThreadClearsSelectedConversationState() {
        val next = SessionRemoteState(
            selectedThreadId = "thread-1",
            pendingApproval = "approval",
            isGenerating = true,
            hasMoreHistory = true,
            isLoadingOlder = true,
            messages = listOf(message("user-1", MessageRole.USER))
        ).startCreatingThread()

        assertEquals("", next.selectedThreadId)
        assertEquals("新会话", next.pendingThreadTitle)
        assertTrue(next.isThreadSwitching)
        assertTrue(next.messages.isEmpty())
        assertFalse(next.hasMoreHistory)
        assertFalse(next.isLoadingOlder)
        assertNull(next.pendingApproval)
        assertFalse(next.isGenerating)
    }

    @Test
    fun startSelectingThreadPreservesResolvedTitle() {
        val next = SessionRemoteState().startSelectingThread("thread-2", "标题")

        assertEquals("", next.selectedThreadId)
        assertEquals("thread-2", next.pendingSelectionThreadId)
        assertEquals("标题", next.pendingThreadTitle)
        assertTrue(next.isThreadSwitching)
    }

    @Test
    fun localArchiveClearsSelectedConversationState() {
        val next = SessionRemoteState(
            selectedThreadId = "thread-1",
            pendingSelectionThreadId = "thread-2",
            pendingThreadTitle = "切换目标",
            isThreadSwitching = true,
            threads = listOf(
                thread("thread-1"),
                thread("thread-2")
            ),
            messages = listOf(message("user-1", MessageRole.USER)),
            hasMoreHistory = true,
            isLoadingOlder = true,
            isGenerating = true,
            pendingApproval = "approval"
        ).withArchivedThreadLocally("thread-1")

        assertEquals("", next.selectedThreadId)
        assertEquals(listOf("thread-2"), next.threads.map { it.id })
        assertNull(next.pendingSelectionThreadId)
        assertNull(next.pendingThreadTitle)
        assertFalse(next.isThreadSwitching)
        assertTrue(next.messages.isEmpty())
        assertFalse(next.hasMoreHistory)
        assertFalse(next.isLoadingOlder)
        assertFalse(next.isGenerating)
        assertNull(next.pendingApproval)
    }

    @Test
    fun localArchiveKeepsSelectedConversationWhenArchivingAnotherThread() {
        val next = SessionRemoteState(
            selectedThreadId = "thread-1",
            threads = listOf(
                thread("thread-1"),
                thread("thread-2")
            ),
            messages = listOf(message("user-1", MessageRole.USER))
        ).withArchivedThreadLocally("thread-2")

        assertEquals("thread-1", next.selectedThreadId)
        assertEquals(listOf("thread-1"), next.threads.map { it.id })
        assertEquals(listOf("user-1"), next.messages.map { it.id })
    }

    @Test
    fun optimisticPromptAppendsUserMessageOnlyWhenIdle() {
        val next = SessionRemoteState().withOptimisticPrompt("hello", nowMillis = 42L)

        assertEquals(1, next.messages.size)
        assertEquals("user-42", next.messages[0].id)
        assertEquals(MessageRole.USER, next.messages[0].role)
        assertTrue(next.isGenerating)
    }

    @Test
    fun optimisticPromptSkipsDuplicatePlaceholderWhenAlreadyGenerating() {
        val next = SessionRemoteState(
            isGenerating = true,
            messages = listOf(message("assistant-live", MessageRole.ASSISTANT))
        ).withOptimisticPrompt("hello", nowMillis = 42L)

        assertEquals(2, next.messages.size)
        assertEquals("assistant-live", next.messages[0].id)
        assertEquals("user-42", next.messages[1].id)
    }

    @Test
    fun sendFailureRemovesPendingPlaceholderAndAddsSystemMessage() {
        val next = SessionRemoteState(
            isGenerating = true,
            messages = listOf(
                message("user-1", MessageRole.USER),
                ThreadMessage(
                    id = "assistant-pending",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(MessageBlock.Status("正在生成…"))
                )
            )
        ).withSendFailure("发送失败", nowMillis = 100L)

        assertEquals(listOf("user-1", "system-send-failed-100"), next.messages.map { it.id })
        assertEquals(ConnectionStatus.ERROR, next.connectionStatus)
        assertEquals("发送失败", next.connectionDetail)
        assertFalse(next.isGenerating)
    }

    @Test
    fun unavailableActionClearsTransientOperationFlags() {
        val next = SessionRemoteState(
            pendingThreadTitle = "新会话",
            isThreadSwitching = true,
            isLoadingOlder = true,
            isManualRefreshing = true,
            isGenerating = true
        ).withUnavailableAction("连接断开")

        assertNull(next.pendingThreadTitle)
        assertFalse(next.isThreadSwitching)
        assertFalse(next.isLoadingOlder)
        assertFalse(next.isManualRefreshing)
        assertFalse(next.isGenerating)
        assertEquals("连接断开", next.connectionDetail)
    }

    @Test
    fun gatewayDisconnectClearsTransientRunStateButKeepsRealSessionMode() {
        val next = SessionRemoteState(
            connectionStatus = ConnectionStatus.CONNECTED,
            isDemoMode = false,
            pendingSelectionThreadId = "thread-2",
            pendingThreadTitle = "切换目标",
            isThreadSwitching = true,
            isLoadingOlder = true,
            isManualRefreshing = true,
            isGenerating = true,
            pendingApproval = "允许执行命令？",
            diagnostics = busyDiagnostics(),
            messages = listOf(message("user-1", MessageRole.USER))
        ).withDisconnectedGateway("")

        assertEquals(ConnectionStatus.DISCONNECTED, next.connectionStatus)
        assertEquals("desktop gateway 已断开", next.connectionDetail)
        assertFalse(next.isDemoMode)
        assertFalse(next.isGenerating)
        assertNull(next.pendingApproval)
        assertClearedConnectionTransients(next)
        assertEquals(listOf("user-1"), next.messages.map { it.id })
    }

    @Test
    fun gatewayDisconnectKeepsGeneratingWhenSelectedThreadIsStillRunning() {
        val next = SessionRemoteState(
            connectionStatus = ConnectionStatus.CONNECTED,
            isDemoMode = false,
            selectedThreadId = "thread-1",
            threads = listOf(thread("thread-1", ThreadStatus.RUNNING)),
            isGenerating = true,
            diagnostics = busyDiagnostics(),
            messages = listOf(
                message("user-1", MessageRole.USER),
                ThreadMessage(
                    id = "assistant-running",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(MessageBlock.CommandSummary("正在运行 ping"))
                )
            )
        ).withManualDisconnect()

        assertEquals(ConnectionStatus.DISCONNECTED, next.connectionStatus)
        assertTrue(next.isGenerating)
        assertTrue(next.diagnostics.isGenerating)
        assertEquals(listOf("thread-1"), next.diagnostics.runningThreadIds)
        assertEquals(ThreadStatus.RUNNING, next.threads.first().status)
        assertEquals(listOf("user-1", "assistant-running"), next.messages.map { it.id })
    }

    @Test
    fun gatewayFailureClearsTransientRunStateButKeepsRealSessionMode() {
        val next = SessionRemoteState(
            connectionStatus = ConnectionStatus.CONNECTED,
            isDemoMode = false,
            pendingSelectionThreadId = "thread-2",
            pendingThreadTitle = "切换目标",
            isThreadSwitching = true,
            isLoadingOlder = true,
            isManualRefreshing = true,
            isGenerating = true,
            pendingApproval = "允许执行命令？",
            diagnostics = busyDiagnostics()
        ).withConnectionFailure("连接失败")

        assertEquals(ConnectionStatus.ERROR, next.connectionStatus)
        assertEquals("连接失败", next.connectionDetail)
        assertFalse(next.isDemoMode)
        assertFalse(next.isGenerating)
        assertNull(next.pendingApproval)
        assertClearedConnectionTransients(next)
    }

    @Test
    fun manualDisconnectClearsTransientConnectionState() {
        val next = switchingGeneratingState().withManualDisconnect()

        assertEquals(ConnectionStatus.DISCONNECTED, next.connectionStatus)
        assertEquals("已断开 desktop gateway", next.connectionDetail)
        assertTrue(next.isDemoMode)
        assertFalse(next.isGenerating)
        assertNull(next.pendingApproval)
        assertClearedConnectionTransients(next)
    }

    @Test
    fun inboundDecodeFailureClearsTransientConnectionState() {
        val next = switchingGeneratingState().withInboundDecodeFailure("bad json")

        assertEquals(ConnectionStatus.ERROR, next.connectionStatus)
        assertEquals("网关消息解析失败: bad json", next.connectionDetail)
        assertFalse(next.isDemoMode)
        assertFalse(next.isGenerating)
        assertNull(next.pendingApproval)
        assertClearedConnectionTransients(next)
    }

    private fun switchingGeneratingState(): SessionRemoteState {
        return SessionRemoteState(
            connectionStatus = ConnectionStatus.CONNECTED,
            isDemoMode = false,
            pendingSelectionThreadId = "thread-2",
            pendingThreadTitle = "切换目标",
            isThreadSwitching = true,
            isLoadingOlder = true,
            isManualRefreshing = true,
            isGenerating = true,
            pendingApproval = "允许执行命令？",
            diagnostics = busyDiagnostics()
        )
    }

    private fun assertClearedConnectionTransients(state: SessionRemoteState) {
        assertNull(state.pendingSelectionThreadId)
        assertNull(state.pendingThreadTitle)
        assertFalse(state.isThreadSwitching)
        assertFalse(state.isLoadingOlder)
        assertFalse(state.isManualRefreshing)
        assertFalse(state.diagnostics.isGenerating)
        assertTrue(state.diagnostics.runningThreadIds.isEmpty())
        assertEquals("", state.diagnostics.pendingSelectionThreadId)
        assertEquals("", state.diagnostics.actionTraceId)
        assertEquals("", state.diagnostics.actionType)
        assertEquals("", state.diagnostics.actionStatus)
        assertEquals(0L, state.diagnostics.actionStartedAt)
        assertEquals(0L, state.diagnostics.actionFinishedAt)
    }

    private fun busyDiagnostics() = StateDiagnostics(
        selectedThreadId = "thread-1",
        pendingSelectionThreadId = "thread-2",
        isGenerating = true,
        runningThreadIds = listOf("thread-1"),
        snapshotRevision = 7L,
        actionTraceId = "trace-7",
        actionType = "refresh_threads",
        actionStatus = "succeeded",
        actionStartedAt = 100L,
        actionFinishedAt = 180L
    )

    private fun message(id: String, role: MessageRole): ThreadMessage {
        return ThreadMessage(
            id = id,
            role = role,
            blocks = listOf(MessageBlock.Text(id))
        )
    }

    private fun thread(
        id: String,
        status: ThreadStatus = ThreadStatus.IDLE
    ) = com.codexapp.model.ThreadSummary(
        id = id,
        title = id,
        preview = "",
        status = status
    )
}
