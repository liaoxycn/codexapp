package com.codexapp.ui.state

import com.codexapp.model.ConnectionStatus
import com.codexapp.model.SessionRemoteState

internal fun shouldReconnect(snapshot: SessionRemoteState, manualDisconnect: Boolean): Boolean {
    return !manualDisconnect &&
        snapshot.gatewayConfig.url.isNotBlank() &&
        snapshot.connectionStatus != ConnectionStatus.CONNECTING
}

internal fun reconnectDelayMs(attempts: Int): Long {
    return ((attempts.coerceAtMost(4) + 1) * 1500L).coerceAtMost(6000L)
}
