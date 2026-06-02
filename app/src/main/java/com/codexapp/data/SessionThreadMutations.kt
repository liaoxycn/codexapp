package com.codexapp.data

import com.codexapp.model.SessionRemoteState

internal fun SessionRemoteState.startCreatingThread(): SessionRemoteState {
    return copy(
        pendingSelectionThreadId = null,
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
        pendingSelectionThreadId = id,
        pendingThreadTitle = title,
        isThreadSwitching = true,
        isLoadingOlder = false,
        pendingApproval = null,
        isGenerating = false
    )
}

internal fun SessionRemoteState.startLoadingOlderMessages(): SessionRemoteState {
    return copy(isLoadingOlder = true)
}
