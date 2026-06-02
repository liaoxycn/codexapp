package com.codexapp.ui.state

import com.codexapp.data.SessionRepository
import com.codexapp.model.GatewayConfig
import com.codexapp.model.NewThreadDraft

internal class HomeRepositoryActions(
    private val repository: SessionRepository,
    private val launch: (suspend () -> Unit) -> Unit,
    private val onManualConnect: () -> Unit,
    private val onManualDisconnect: () -> Unit,
    private val runAnimatedRefresh: () -> Unit,
) {
    fun selectThread(id: String, onComplete: (Boolean) -> Unit = {}) {
        launch { onComplete(repository.selectThread(id)) }
    }

    fun createThread(cwd: String? = null, draft: NewThreadDraft? = null, onComplete: (Boolean) -> Unit = {}) {
        launch { onComplete(repository.createThread(cwd, draft)) }
    }

    fun forkThread(id: String, numTurns: Int? = null, onComplete: (Boolean) -> Unit = {}) {
        launch { onComplete(repository.forkThread(id, numTurns)) }
    }

    fun renameThread(id: String, name: String, onComplete: (Boolean) -> Unit = {}) {
        launch { onComplete(repository.renameThread(id, name)) }
    }

    fun archiveThread(id: String, onComplete: (Boolean) -> Unit = {}) {
        launch { onComplete(repository.archiveThread(id)) }
    }

    fun unarchiveThread(id: String, onComplete: (Boolean) -> Unit = {}) {
        launch { onComplete(repository.unarchiveThread(id)) }
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
