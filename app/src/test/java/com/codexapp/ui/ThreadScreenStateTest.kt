package com.codexapp.ui

import com.codexapp.model.ComposerChip
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.GatewayConfig
import com.codexapp.model.HomeUiState
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.ThreadMessage
import com.codexapp.model.ThreadSummary
import com.codexapp.ui.thread.calculateThreadListMetrics
import com.codexapp.ui.thread.connectionBannerDetailText
import com.codexapp.ui.thread.restoredHistoryAnchorIndex
import com.codexapp.ui.thread.shouldTriggerHistoryLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun metricsUseSmallBottomPaddingBecauseScaffoldAlreadyInsetsComposer() {
        val state = homeUiState(showComposerDetails = true)

        val compactMetrics = calculateThreadListMetrics(state, compactMode = true, isAtBottom = true)
        val regularMetrics = calculateThreadListMetrics(state, compactMode = false, isAtBottom = true)

        assertEquals(8, compactMetrics.composerPadding.value.toInt())
        assertEquals(10, regularMetrics.composerPadding.value.toInt())
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
    fun connectionBannerShowsDisconnectReasonWhenAvailable() {
        val state = homeUiState(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            connectionDetail = "desktop gateway 已断开"
        )

        assertEquals("desktop gateway 已断开", connectionBannerDetailText(state))
    }

    @Test
    fun connectionBannerHidesGenericDisconnectedDetail() {
        val state = homeUiState(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            connectionDetail = "未连接 desktop gateway"
        )

        assertNull(connectionBannerDetailText(state))
    }

    @Test
    fun connectionBannerShowsErrorDetailWhenAvailable() {
        val state = homeUiState(
            connectionStatus = ConnectionStatus.ERROR,
            connectionDetail = "网关消息解析失败: bad json"
        )

        assertEquals("网关消息解析失败: bad json", connectionBannerDetailText(state))
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

    @Test
    fun historyLoadRequiresTopPullPastThreshold() {
        assertTrue(
            shouldTriggerHistoryLoad(
                isAtTop = true,
                hasMoreHistory = true,
                isLoadingOlder = false,
                isThreadSwitching = false,
                hasMessages = true,
                gestureStartedAtTop = true,
                pullDistance = 92f,
                pullThreshold = 88f,
                loadArmed = true
            )
        )
    }

    @Test
    fun historyLoadDoesNotTriggerWhenNotAtTopOrBusy() {
        assertFalse(
            shouldTriggerHistoryLoad(
                isAtTop = false,
                hasMoreHistory = true,
                isLoadingOlder = false,
                isThreadSwitching = false,
                hasMessages = true,
                gestureStartedAtTop = true,
                pullDistance = 160f,
                pullThreshold = 120f,
                loadArmed = true
            )
        )
        assertFalse(
            shouldTriggerHistoryLoad(
                isAtTop = true,
                hasMoreHistory = true,
                isLoadingOlder = true,
                isThreadSwitching = false,
                hasMessages = true,
                gestureStartedAtTop = true,
                pullDistance = 160f,
                pullThreshold = 120f,
                loadArmed = true
            )
        )
    }

    @Test
    fun historyLoadDoesNotTriggerWhenGestureDidNotStartAtTop() {
        assertFalse(
            shouldTriggerHistoryLoad(
                isAtTop = true,
                hasMoreHistory = true,
                isLoadingOlder = false,
                isThreadSwitching = false,
                hasMessages = true,
                gestureStartedAtTop = false,
                pullDistance = 160f,
                pullThreshold = 120f,
                loadArmed = true
            )
        )
    }

    private fun homeUiState(
        threads: List<ThreadSummary> = emptyList(),
        messages: List<ThreadMessage> = emptyList(),
        hasMoreHistory: Boolean = false,
        pendingApproval: String? = null,
        showComposerDetails: Boolean = false,
        connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
        connectionDetail: String = "",
    ) = HomeUiState(
        threads = threads,
        selectedThreadId = "thread-1",
        pendingSelectionThreadId = null,
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
        files = emptyList(),
        slashCommands = emptyList(),
        pendingApproval = pendingApproval,
        cwd = "",
        permissionSummary = "",
        connectionStatus = connectionStatus,
        connectionDetail = connectionDetail,
        gatewayConfig = GatewayConfig(),
        desktopRestartRequired = false,
        isDemoMode = false,
        isNewThreadDraft = false,
        newThreadDraft = NewThreadDraft()
    )

    private fun message(id: String) = ThreadMessage(
        id = id,
        role = com.codexapp.model.MessageRole.ASSISTANT,
        blocks = emptyList()
    )
}
