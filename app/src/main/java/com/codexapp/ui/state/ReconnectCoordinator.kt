package com.codexapp.ui.state

import com.codexapp.model.ConnectionStatus
import com.codexapp.model.SessionRemoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ReconnectCoordinator {
    private var reconnectJob: Job? = null
    private var reconnectAttempts: Int = 0
    private var manualDisconnect: Boolean = false

    fun onManualConnect() {
        manualDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
    }

    fun onManualDisconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
    }

    fun sync(
        scope: CoroutineScope,
        snapshot: SessionRemoteState,
        currentSnapshot: () -> SessionRemoteState,
        reconnect: suspend (SessionRemoteState) -> Unit
    ) {
        if (snapshot.connectionStatus == ConnectionStatus.CONNECTED) {
            reconnectAttempts = 0
            reconnectJob?.cancel()
            reconnectJob = null
            return
        }

        if (!shouldReconnect(snapshot, manualDisconnect) || reconnectJob != null) {
            return
        }

        reconnectJob = scope.launch {
            delay(reconnectDelayMs(reconnectAttempts))
            val current = currentSnapshot()
            if (manualDisconnect || current.connectionStatus == ConnectionStatus.CONNECTED) {
                return@launch
            }
            reconnectAttempts += 1
            reconnect(current)
        }.also { job ->
            job.invokeOnCompletion {
                if (reconnectJob === job) {
                    reconnectJob = null
                }
            }
        }
    }
}
