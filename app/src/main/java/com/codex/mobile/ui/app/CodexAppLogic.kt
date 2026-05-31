package com.codex.mobile.ui.app

import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary

internal data class SelectedThreadChrome(
    val title: String,
    val status: ThreadStatus,
)

internal fun resolveSelectedThreadChrome(
    threads: List<ThreadSummary>,
    selectedThreadId: String,
): SelectedThreadChrome {
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
