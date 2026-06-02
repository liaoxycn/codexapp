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
): SelectedThreadChrome {
    if (isNewThreadDraft) {
        return SelectedThreadChrome(
            title = "新对话",
            status = ThreadStatus.IDLE
        )
    }
    val selectedThread = threads.firstOrNull { it.id == selectedThreadId }
    return SelectedThreadChrome(
        title = selectedThread?.title ?: "Codex",
        status = if (selectedThreadId.isBlank()) {
            ThreadStatus.IDLE
        } else {
            selectedThread?.status ?: ThreadStatus.IDLE
        },
    )
}
