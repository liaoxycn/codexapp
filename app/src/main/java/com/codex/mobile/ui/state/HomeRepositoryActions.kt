package com.codex.mobile.ui.state

import com.codex.mobile.data.SessionRepository
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.NewThreadDraft

internal class HomeRepositoryActions(
    private val repository: SessionRepository,
    private val launch: (suspend () -> Unit) -> Unit,
    private val onManualConnect: () -> Unit,
    private val onManualDisconnect: () -> Unit,
    private val runAnimatedRefresh: () -> Unit,
) {
    fun selectThread(id: String) {
        launch { repository.selectThread(id) }
    }

    fun createThread(cwd: String? = null, draft: NewThreadDraft? = null) {
        launch { repository.createThread(cwd, draft) }
    }

    fun forkThread(id: String, numTurns: Int? = null) {
        launch { repository.forkThread(id, numTurns) }
    }

    fun renameThread(id: String, name: String) {
        launch { repository.renameThread(id, name) }
    }

    fun archiveThread(id: String) {
        launch { repository.archiveThread(id) }
    }

    fun unarchiveThread(id: String) {
        launch { repository.unarchiveThread(id) }
    }

    fun refreshThreads() {
        launch { repository.refreshThreads() }
    }

    fun refreshCurrentThreadAnimated() {
        runAnimatedRefresh()
    }

    fun loadOlderMessages() {
        launch { repository.loadOlderMessages() }
    }

    fun stopGenerating() {
        launch { repository.stopTurn() }
    }

    fun approvePending() {
        launch { repository.approvePending() }
    }

    fun rejectPending() {
        launch { repository.rejectPending() }
    }

    fun restartDesktop() {
        launch { repository.restartDesktop() }
    }

    fun connect(url: String, pairToken: String) {
        onManualConnect()
        launch {
            repository.connect(
                GatewayConfig(
                    url = url,
                    pairToken = pairToken
                )
            )
        }
    }

    fun disconnect() {
        onManualDisconnect()
        launch { repository.disconnect() }
    }
}
