package com.codexapp.ui

import com.codexapp.model.ConnectionStatus
import com.codexapp.ui.composer.composerControllerEnabled
import com.codexapp.ui.composer.composerControllerSendEnabled
import com.codexapp.ui.composer.shouldClearFocusAfterComposerSendAttempt
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
