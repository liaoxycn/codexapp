package com.codex.mobile.ui.state

internal class ComposerActionHandler(
    private val composerSession: ComposerSession,
    private val selectedThreadId: () -> String,
    private val launch: (suspend () -> Unit) -> Unit,
    private val sendPrompt: suspend (String) -> Boolean
) {
    fun updateComposer(text: String) {
        composerSession.replace(text, selectedThreadId())
    }

    fun insertComposerText(text: String) {
        composerSession.append(text, selectedThreadId())
    }

    fun applySlashCommand(command: String) {
        composerSession.applySlashCommand(command, selectedThreadId())
    }

    fun compactContext() {
        composerSession.replace("/compact ", selectedThreadId())
        send()
    }

    fun rollbackLastTurn() {
        composerSession.replace("/rollback ", selectedThreadId())
        send()
    }

    fun clearComposer() {
        composerSession.clear()
    }

    fun insertShellTemplate() {
        composerSession.insertCommandTemplate("! ", selectedThreadId())
    }

    fun replaceComposer(text: String) {
        composerSession.replace(text, selectedThreadId())
    }

    fun resendText(text: String) {
        composerSession.replace(text, selectedThreadId())
        send()
    }

    fun send() {
        val threadId = composerSession.currentThreadId(selectedThreadId())
        val prompt = composerSession.currentText().trim()
        if (prompt.isBlank()) return
        launch {
            val accepted = sendPrompt(prompt)
            if (accepted) {
                composerSession.clearAcceptedDraft(threadId)
            }
        }
    }
}
