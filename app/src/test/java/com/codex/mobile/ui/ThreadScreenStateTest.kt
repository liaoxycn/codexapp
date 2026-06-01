package com.codex.mobile.ui

import com.codex.mobile.model.ComposerChip
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.model.ThreadSummary
import com.codex.mobile.ui.thread.calculateThreadListMetrics
import com.codex.mobile.ui.thread.restoredHistoryAnchorIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadScreenStateTest {
    @Test
    fun metricsIncludeHistorySpacerAndPendingApproval() {
        val state = homeUiState(
            messages = listOf(message("a"), message("b")),
            hasMoreHistory = true,
            pendingApproval = "审批中"
        )

        val metrics = calculateThreadListMetrics(state, compactMode = false, isAtBottom = false)

        assertEquals(1, metrics.messageItemStartIndex)
        assertEquals(3, metrics.lastItemIndex)
        assertTrue(metrics.showJumpToBottom)
        assertTrue(metrics.hasVisibleMessages)
    }

    @Test
    fun metricsUseComposerDetailPadding() {
        val state = homeUiState(showComposerDetails = true)

        val compactMetrics = calculateThreadListMetrics(state, compactMode = true, isAtBottom = true)
        val regularMetrics = calculateThreadListMetrics(state, compactMode = false, isAtBottom = true)

        assertEquals(278, compactMetrics.composerPadding.value.toInt())
        assertEquals(298, regularMetrics.composerPadding.value.toInt())
        assertFalse(compactMetrics.showJumpToBottom)
    }

    @Test
    fun disconnectedStateShowsConnectionBanner() {
        val state = homeUiState(connectionStatus = ConnectionStatus.ERROR)

        val metrics = calculateThreadListMetrics(state, compactMode = false, isAtBottom = true)

        assertTrue(metrics.showConnectionBanner)
        assertFalse(metrics.showJumpToBottom)
    }

    @Test
    fun historyAnchorRestoreKeepsPreviouslyVisibleMessageAfterOlderItemsPrepend() {
        val messagesAfterLoad = listOf(
            message("msg-8"),
            message("msg-9"),
            message("msg-10"),
            message("msg-11")
        )

        val restoredIndex = restoredHistoryAnchorIndex(
            messages = messagesAfterLoad,
            anchorId = "msg-10",
            hasMoreHistory = true
        )

        assertEquals(3, restoredIndex)
    }

    private fun homeUiState(
        threads: List<ThreadSummary> = emptyList(),
        messages: List<ThreadMessage> = emptyList(),
        hasMoreHistory: Boolean = false,
        pendingApproval: String? = null,
        showComposerDetails: Boolean = false,
        connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
    ) = HomeUiState(
        threads = threads,
        selectedThreadId = "thread-1",
        pendingThreadTitle = null,
        isThreadSwitching = false,
        messages = messages,
        hasMoreHistory = hasMoreHistory,
        isLoadingOlder = false,
        composerText = "",
        composerFocusRequest = 0L,
        isGenerating = false,
        isManualRefreshing = false,
        showComposerDetails = showComposerDetails,
        chips = emptyList<ComposerChip>(),
        slashCommands = emptyList(),
        pendingApproval = pendingApproval,
        cwd = "",
        permissionSummary = "",
        connectionStatus = connectionStatus,
        connectionDetail = "",
        gatewayConfig = GatewayConfig(),
        isDemoMode = false
    )

    private fun message(id: String) = ThreadMessage(
        id = id,
        role = com.codex.mobile.model.MessageRole.ASSISTANT,
        blocks = emptyList()
    )
}
