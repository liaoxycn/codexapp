package com.codexapp.ui.app

import com.codexapp.model.AppUpdateState
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.GatewayConfig
import com.codexapp.model.GatewayConfigOptions
import com.codexapp.model.HomeUiState
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.SessionConfig
import com.codexapp.model.StateDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalLoadingOverlayStateTest {
    @Test
    fun archiveActionShowsArchiveLoading() {
        val state = baseState(
            diagnostics = StateDiagnostics(
                actionType = "archive_thread",
                actionStatus = "started"
            )
        )

        assertEquals("正在归档中", resolveGlobalLoadingText(state))
    }

    @Test
    fun renameActionShowsRenameLoading() {
        val state = baseState(
            diagnostics = StateDiagnostics(
                actionType = "rename_thread",
                actionStatus = "started"
            )
        )

        assertEquals("正在重命名中", resolveGlobalLoadingText(state))
    }

    @Test
    fun refreshActionShowsRefreshLoading() {
        val state = baseState(
            diagnostics = StateDiagnostics(
                actionType = "refresh_threads",
                actionStatus = "started"
            )
        )

        assertEquals("正在刷新中", resolveGlobalLoadingText(state))
    }

    @Test
    fun pendingSelectionShowsSwitchLoading() {
        val state = baseState(pendingSelectionThreadId = "thread-2")

        assertEquals("正在切换会话", resolveGlobalLoadingText(state))
    }

    @Test
    fun forkingTakesPriorityOverTrace() {
        val state = baseState(
            isForkingThread = true,
            diagnostics = StateDiagnostics(
                actionType = "archive_thread",
                actionStatus = "started"
            )
        )

        assertEquals("正在生成分叉会话", resolveGlobalLoadingText(state))
    }

    @Test
    fun completedActionDoesNotShowLoading() {
        val state = baseState(
            diagnostics = StateDiagnostics(
                actionType = "archive_thread",
                actionStatus = "succeeded"
            )
        )

        assertEquals(null, resolveGlobalLoadingText(state))
    }

    private fun baseState(
        pendingSelectionThreadId: String? = null,
        isForkingThread: Boolean = false,
        diagnostics: StateDiagnostics = StateDiagnostics()
    ): HomeUiState {
        return HomeUiState(
            threads = emptyList(),
            selectedThreadId = "thread-1",
            pendingSelectionThreadId = pendingSelectionThreadId,
            pendingThreadTitle = null,
            isThreadSwitching = false,
            messages = emptyList(),
            hasMoreHistory = false,
            isLoadingOlder = false,
            composerText = "",
            composerFocusRequest = 0L,
            isGenerating = false,
            isManualRefreshing = false,
            showComposerDetails = false,
            chips = emptyList(),
            files = emptyList(),
            slashCommands = emptyList(),
            pendingApproval = null,
            cwd = "",
            permissionSummary = "",
            sessionConfig = SessionConfig(),
            isForkingThread = isForkingThread,
            connectionStatus = ConnectionStatus.CONNECTED,
            connectionDetail = "已连接",
            gatewayConfig = GatewayConfig(),
            desktopRestartRequired = false,
            operationalNotices = emptyList(),
            appUpdate = AppUpdateState(),
            isDemoMode = false,
            isNewThreadDraft = false,
            newThreadDraft = NewThreadDraft(),
            configOptions = GatewayConfigOptions(),
            diagnostics = diagnostics
        )
    }
}
