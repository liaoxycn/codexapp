package com.codex.mobile.data

import com.codex.mobile.model.SessionRemoteState

internal fun SessionRemoteState.startCreatingThread(): SessionRemoteState {
    return copy(
        pendingThreadTitle = "新会话",
        isThreadSwitching = true,
        selectedThreadId = "",
        messages = emptyList(),
        hasMoreHistory = false,
        isLoadingOlder = false,
        pendingApproval = null,
        isGenerating = false
    )
}

internal fun SessionRemoteState.startSelectingThread(id: String, title: String?): SessionRemoteState {
    return copy(
        selectedThreadId = id,
        pendingThreadTitle = title,
        isThreadSwitching = true,
        messages = emptyList(),
        hasMoreHistory = false,
        isLoadingOlder = false,
        pendingApproval = null,
        isGenerating = false
    )
}

internal fun SessionRemoteState.startLoadingOlderMessages(): SessionRemoteState {
    return copy(isLoadingOlder = true)
}
