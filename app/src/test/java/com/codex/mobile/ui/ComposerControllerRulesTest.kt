package com.codex.mobile.ui

import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.ui.composer.composerControllerEnabled
import com.codex.mobile.ui.composer.composerControllerSendEnabled
import com.codex.mobile.ui.composer.shouldClearFocusAfterComposerSendAttempt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerControllerRulesTest {
    @Test
    fun composerControllerEnabledDependsOnThreadSwitchingState() {
        assertTrue(composerControllerEnabled(isThreadSwitching = false))
        assertFalse(composerControllerEnabled(isThreadSwitching = true))
    }

    @Test
    fun composerControllerSendEnabledRequiresConnectedNonBlankComposer() {
        assertTrue(
            composerControllerSendEnabled(
                composerEnabled = true,
                composerText = "hello",
                connectionStatus = ConnectionStatus.CONNECTED
            )
        )
        assertFalse(
            composerControllerSendEnabled(
                composerEnabled = true,
                composerText = "",
                connectionStatus = ConnectionStatus.CONNECTED
            )
        )
        assertFalse(
            composerControllerSendEnabled(
                composerEnabled = false,
                composerText = "hello",
                connectionStatus = ConnectionStatus.CONNECTED
            )
        )
        assertFalse(
            composerControllerSendEnabled(
                composerEnabled = true,
                composerText = "hello",
                connectionStatus = ConnectionStatus.CONNECTING
            )
        )
    }

    @Test
    fun shouldClearFocusAfterComposerSendAttemptOnlyWhenDisconnectedAndNotSendable() {
        assertTrue(
            shouldClearFocusAfterComposerSendAttempt(
                sendEnabled = false,
                connectionStatus = ConnectionStatus.ERROR
            )
        )
        assertFalse(
            shouldClearFocusAfterComposerSendAttempt(
                sendEnabled = true,
                connectionStatus = ConnectionStatus.CONNECTED
            )
        )
        assertFalse(
            shouldClearFocusAfterComposerSendAttempt(
                sendEnabled = false,
                connectionStatus = ConnectionStatus.CONNECTED
            )
        )
    }
}
