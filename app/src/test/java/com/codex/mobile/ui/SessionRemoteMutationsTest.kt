package com.codex.mobile.ui

import com.codex.mobile.data.startCreatingThread
import com.codex.mobile.data.startSelectingThread
import com.codex.mobile.data.withOptimisticPrompt
import com.codex.mobile.data.withSendFailure
import com.codex.mobile.data.withUnavailableAction
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.ThreadMessage
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

        assertEquals("thread-2", next.selectedThreadId)
        assertEquals("标题", next.pendingThreadTitle)
        assertTrue(next.isThreadSwitching)
    }

    @Test
    fun optimisticPromptAppendsPlaceholderOnlyWhenIdle() {
        val next = SessionRemoteState().withOptimisticPrompt("hello", nowMillis = 42L)

        assertEquals(2, next.messages.size)
        assertEquals("user-42", next.messages[0].id)
        assertEquals(MessageRole.ASSISTANT, next.messages[1].role)
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

    private fun message(id: String, role: MessageRole): ThreadMessage {
        return ThreadMessage(
            id = id,
            role = role,
            blocks = listOf(MessageBlock.Text(id))
        )
    }
}
