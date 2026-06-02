package com.codexapp.ui.state

import com.codexapp.model.NewThreadDraft
import com.codexapp.model.PendingEditResendState

internal class ComposerActionHandler(
    private val composerSession: ComposerSession,
    private val selectedThreadId: () -> String,
    private val newThreadDraft: () -> NewThreadDraft?,
    private val launch: (suspend () -> Unit) -> Unit,
    private val sendPrompt: suspend (String, NewThreadDraft?) -> Boolean,
    private val resendPrompt: suspend (String, Int) -> Boolean = { _, _ -> false },
    private val onPromptAccepted: (Boolean) -> Unit = {}
) {
    private var pendingEditResend: PendingEditResend? = null

    fun pendingEditResendState(): PendingEditResendState? {
        val pending = pendingEditResend ?: return null
        return PendingEditResendState(
            threadId = pending.threadId,
            rollbackNumTurns = pending.rollbackNumTurns
        )
    }

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
        pendingEditResend = null
        composerSession.clear()
    }

    fun insertShellTemplate() {
        composerSession.insertCommandTemplate("! ", selectedThreadId())
    }

    fun replaceComposer(text: String) {
        pendingEditResend = null
        composerSession.replace(text, selectedThreadId())
    }

    fun editAndResendText(text: String, rollbackNumTurns: Int) {
        val threadId = selectedThreadId()
        if (threadId.isBlank()) {
            return
        }
        pendingEditResend = PendingEditResend(threadId = threadId, rollbackNumTurns = rollbackNumTurns.coerceAtLeast(1))
        composerSession.replace(text, threadId)
    }

    fun resendText(text: String) {
        pendingEditResend = null
        composerSession.replace(text, selectedThreadId())
        send()
    }

    fun send() {
        val threadId = composerSession.currentThreadId(selectedThreadId())
        val prompt = composerSession.currentText().trim()
        if (prompt.isBlank()) return
        launch {
            val pending = pendingEditResend?.takeIf { it.threadId == threadId }
            val draft = if (pending == null) newThreadDraft() else null
            val accepted = if (pending != null) {
                resendPrompt(prompt, pending.rollbackNumTurns)
            } else {
                sendPrompt(prompt, draft)
            }
            if (accepted) {
                if (pending != null) {
                    pendingEditResend = null
                }
                composerSession.clearAcceptedDraft(threadId)
                onPromptAccepted(draft != null)
            }
        }
    }

    private data class PendingEditResend(
        val threadId: String,
        val rollbackNumTurns: Int
    )
}
