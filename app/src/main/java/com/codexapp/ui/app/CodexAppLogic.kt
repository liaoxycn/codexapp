package com.codexapp.ui.app

import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary

internal data class SelectedThreadChrome(
    val title: String,
    val status: ThreadStatus,
)

internal fun resolveSelectedThreadChrome(
    threads: List<ThreadSummary>,
    selectedThreadId: String,
    isNewThreadDraft: Boolean = false,
    isGenerating: Boolean = false,
    pendingApproval: String? = null,
): SelectedThreadChrome {
    val forcedStatus = when {
        pendingApproval != null -> ThreadStatus.NEEDS_APPROVAL
        isGenerating -> ThreadStatus.RUNNING
        else -> null
    }
    if (isNewThreadDraft) {
        return SelectedThreadChrome(
            title = "新对话",
            status = forcedStatus ?: ThreadStatus.IDLE
        )
    }
    val selectedThread = threads.firstOrNull { it.id == selectedThreadId }
    return SelectedThreadChrome(
        title = selectedThread?.title ?: "Codex",
        status = if (selectedThreadId.isBlank()) {
            forcedStatus ?: ThreadStatus.IDLE
        } else {
            forcedStatus ?: selectedThread?.status ?: ThreadStatus.IDLE
        },
    )
}
