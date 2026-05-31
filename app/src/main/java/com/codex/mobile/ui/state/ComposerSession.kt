package com.codex.mobile.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ComposerSession(
    initialThreadId: String = ""
) {
    private val composerText = MutableStateFlow("")
    private val composerDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private var lastSelectedThreadId: String = initialThreadId

    val text: StateFlow<String> = composerText.asStateFlow()

    fun currentThreadId(selectedThreadId: String): String {
        return if (selectedThreadId.isNotBlank()) selectedThreadId else lastSelectedThreadId
    }

    fun syncSelectedThread(threadId: String) {
        if (threadId.isBlank() || threadId == lastSelectedThreadId) {
            return
        }
        lastSelectedThreadId = threadId
        composerText.value = restoreComposerDraft(composerDrafts.value, threadId)
    }

    fun replace(text: String, selectedThreadId: String) {
        composerText.value = text
        val threadId = currentThreadId(selectedThreadId)
        if (threadId.isBlank()) {
            return
        }
        composerDrafts.update { drafts -> updateComposerDrafts(drafts, threadId, text) }
    }

    fun append(text: String, selectedThreadId: String) {
        replace(appendComposerText(composerText.value, text), selectedThreadId)
    }

    fun applySlashCommand(command: String, selectedThreadId: String) {
        replace(applySlashCommandText(composerText.value, command), selectedThreadId)
    }

    fun insertCommandTemplate(command: String, selectedThreadId: String) {
        replace(com.codex.mobile.ui.state.insertCommandTemplate(composerText.value, command), selectedThreadId)
    }

    fun clear() {
        composerText.value = ""
    }

    fun clearAcceptedDraft(threadId: String) {
        composerText.value = ""
        composerDrafts.update { drafts -> clearComposerDraft(drafts, threadId) }
    }

    fun currentText(): String = composerText.value
}
