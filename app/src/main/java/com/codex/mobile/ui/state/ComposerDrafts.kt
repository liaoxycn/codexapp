package com.codex.mobile.ui.state

internal fun restoreComposerDraft(
    drafts: Map<String, String>,
    threadId: String
): String {
    if (threadId.isBlank()) {
        return ""
    }
    return drafts[threadId].orEmpty()
}

internal fun updateComposerDrafts(
    drafts: Map<String, String>,
    threadId: String,
    text: String
): Map<String, String> {
    if (threadId.isBlank()) {
        return drafts
    }
    return if (text.isBlank()) {
        drafts - threadId
    } else {
        drafts + (threadId to text)
    }
}

internal fun clearComposerDraft(
    drafts: Map<String, String>,
    threadId: String
): Map<String, String> {
    if (threadId.isBlank()) {
        return drafts
    }
    return drafts - threadId
}
