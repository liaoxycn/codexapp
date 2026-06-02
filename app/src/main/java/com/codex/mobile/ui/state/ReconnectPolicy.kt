package com.codex.mobile.ui.state

import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.ThreadStatus

internal fun shouldReconnect(snapshot: SessionRemoteState, manualDisconnect: Boolean): Boolean {
    return !manualDisconnect &&
        snapshot.gatewayConfig.url.isNotBlank() &&
        snapshot.connectionStatus != ConnectionStatus.CONNECTING
}

internal fun reconnectDelayMs(attempts: Int): Long {
    return ((attempts.coerceAtMost(4) + 1) * 1500L).coerceAtMost(6000L)
}

internal fun shouldPollLiveRefresh(snapshot: SessionRemoteState): Boolean {
    return snapshot.connectionStatus == ConnectionStatus.CONNECTED &&
        snapshot.pendingSelectionThreadId.isNullOrBlank() &&
        snapshot.selectedThreadId.isNotBlank() &&
        selectedThreadNeedsLiveRefresh(snapshot)
}

internal fun shouldContinueLiveRefresh(snapshot: SessionRemoteState, targetThreadId: String): Boolean {
    return snapshot.connectionStatus == ConnectionStatus.CONNECTED &&
        snapshot.pendingSelectionThreadId.isNullOrBlank() &&
        snapshot.selectedThreadId == targetThreadId &&
        selectedThreadNeedsLiveRefresh(snapshot)
}

internal fun selectedThreadNeedsLiveRefresh(snapshot: SessionRemoteState): Boolean {
    if (snapshot.isThreadSwitching) {
        return true
    }
    if (snapshot.isGenerating || snapshot.pendingApproval != null) {
        return snapshot.selectedThreadId.isNotBlank()
    }
    val selectedThread = snapshot.threads.firstOrNull { it.id == snapshot.selectedThreadId } ?: return false
    return selectedThread.status == ThreadStatus.RUNNING || selectedThread.status == ThreadStatus.NEEDS_APPROVAL
}
