package com.codexapp.ui.composer

import com.codexapp.model.ConnectionStatus

internal fun composerControllerEnabled(
    isThreadSwitching: Boolean
): Boolean {
    return !isThreadSwitching
}

internal fun composerControllerSendEnabled(
    composerEnabled: Boolean,
    composerText: String,
    connectionStatus: ConnectionStatus
): Boolean {
    return composerEnabled &&
        composerText.isNotBlank() &&
        connectionStatus == ConnectionStatus.CONNECTED
}

internal fun shouldClearFocusAfterComposerSendAttempt(
    sendEnabled: Boolean,
    connectionStatus: ConnectionStatus
): Boolean {
    return !sendEnabled && connectionStatus != ConnectionStatus.CONNECTED
}
