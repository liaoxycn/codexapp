package com.codex.mobile.ui.state

import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.SessionRemoteState

internal fun SessionRemoteState.toHomeState(
    composer: String,
    composerExpanded: Boolean,
    composerFocusRequest: Long
): HomeUiState = HomeUiState(
    threads = threads,
    selectedThreadId = selectedThreadId,
    pendingThreadTitle = pendingThreadTitle,
    isThreadSwitching = isThreadSwitching,
    messages = messages,
    hasMoreHistory = hasMoreHistory,
    isLoadingOlder = isLoadingOlder,
    composerText = composer,
    composerFocusRequest = composerFocusRequest,
    isGenerating = isGenerating,
    showComposerDetails = composerExpanded,
    chips = chips,
    slashCommands = slashCommands,
    pendingApproval = pendingApproval,
    cwd = cwd,
    permissionSummary = permissionSummary,
    connectionStatus = connectionStatus,
    connectionDetail = connectionDetail,
    gatewayConfig = gatewayConfig,
    isDemoMode = isDemoMode,
    isManualRefreshing = isManualRefreshing
)
