package com.codexapp.ui.state

import com.codexapp.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class HomeRepositoryCoordinator(
    private val repository: SessionRepository,
    private val scope: CoroutineScope,
    private val composerSession: ComposerSession,
    private val reconnectCoordinator: ReconnectCoordinator = ReconnectCoordinator(),
    private val liveRefreshCoordinator: LiveRefreshCoordinator = LiveRefreshCoordinator()
) {
    private var observeJob: Job? = null
    private var started: Boolean = false

    fun start() {
        if (started) {
            return
        }
        started = true
        connectSavedGatewayIfNeeded()
        observeRepositoryState()
    }

    fun onManualConnect() {
        reconnectCoordinator.onManualConnect()
    }

    fun onManualDisconnect() {
        reconnectCoordinator.onManualDisconnect()
    }

    fun refreshAnimated() {
        liveRefreshCoordinator.refreshAnimated(
            scope = scope,
            setManualRefreshing = repository::markManualRefreshing,
            refresh = { repository.refreshThreads() }
        )
    }

    private fun connectSavedGatewayIfNeeded() {
        val config = repository.state.value.gatewayConfig
        if (config.url.isNotBlank()) {
            scope.launch {
                repository.connect(config)
            }
        }
    }

    private fun observeRepositoryState() {
        observeJob = scope.launch {
            repository.state.collect { snapshot ->
                composerSession.syncSelectedThread(snapshot.selectedThreadId)
                reconnectCoordinator.sync(
                    scope = scope,
                    snapshot = snapshot,
                    currentSnapshot = { repository.state.value },
                    reconnect = { current -> repository.connect(current.gatewayConfig) }
                )
                liveRefreshCoordinator.sync(
                    scope = scope,
                    snapshot = snapshot,
                    currentSnapshot = { repository.state.value },
                    setManualRefreshing = repository::markManualRefreshing,
                    refresh = { repository.refreshThreads() }
                )
            }
        }
    }
}
