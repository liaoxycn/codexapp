package com.codex.mobile.ui.composer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.TextFieldValue
import com.codex.mobile.model.HomeUiState

internal data class ComposerController(
    val composerEnabled: Boolean,
    val sendEnabled: Boolean,
    val trailingToken: String,
    val slashPanelVisible: Boolean,
    val filteredCommands: List<String>,
    val slashQuery: String,
    val composerFieldValue: TextFieldValue,
    val focusRequester: FocusRequester,
    val inputInteractionSource: MutableInteractionSource,
    val onSlashQueryChange: (String) -> Unit,
    val focusComposer: () -> Unit,
    val resetInlineSlashPanel: () -> Unit,
    val selectSlashCommand: (String) -> Unit,
    val onComposerValueChange: (TextFieldValue) -> Unit,
    val sendNow: () -> Unit,
)

@Composable
internal fun rememberComposerController(
    state: HomeUiState,
    activePanel: ComposerPanel,
    onActivePanelChange: (ComposerPanel) -> Unit,
    onChange: (String) -> Unit,
    onApplySlashCommand: (String) -> Unit,
    onSend: () -> Unit,
): ComposerController {
    val localState = rememberComposerControllerLocalState(state)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val rootView = LocalView.current
    val trailingToken = extractTrailingComposerToken(state.composerText)
    val slashPanelVisible = shouldShowSlashPanel(
        slashCommands = state.slashCommands,
        activePanel = activePanel,
        suppressInlineSlashPanel = localState.suppressInlineSlashPanel,
        trailingToken = trailingToken
    )
    val filteredCommands = filterSlashCommands(state.slashCommands, localState.slashQuery)
    val composerEnabled = composerControllerEnabled(state.isThreadSwitching)
    val sendEnabled = composerControllerSendEnabled(
        composerEnabled = composerEnabled,
        composerText = state.composerText,
        connectionStatus = state.connectionStatus
    )

    HandleComposerControllerEffects(
        composerText = state.composerText,
        composerFocusRequest = state.composerFocusRequest,
        localState = localState,
        onActivePanelChange = onActivePanelChange,
        focusManager = focusManager,
        rootView = rootView,
        keyboardController = keyboardController
    )

    fun focusComposer(showKeyboard: Boolean = true) {
        localState.focusRequester.requestFocus()
        if (showKeyboard) {
            keyboardController?.show()
        }
    }

    return ComposerController(
        composerEnabled = composerEnabled,
        sendEnabled = sendEnabled,
        trailingToken = trailingToken,
        slashPanelVisible = slashPanelVisible,
        filteredCommands = filteredCommands,
        slashQuery = localState.slashQuery,
        composerFieldValue = localState.composerFieldValue,
        focusRequester = localState.focusRequester,
        inputInteractionSource = localState.inputInteractionSource,
        onSlashQueryChange = localState.updateSlashQuery,
        focusComposer = { focusComposer() },
        resetInlineSlashPanel = {
            localState.updateSlashQuery("")
            localState.updateSuppressInlineSlashPanel(false)
        },
        selectSlashCommand = { command ->
            localState.updateSuppressInlineSlashPanel(false)
            onApplySlashCommand(command)
            onActivePanelChange(ComposerPanel.NONE)
            localState.updateSlashQuery("")
            focusComposer()
        },
        onComposerValueChange = { nextValue ->
            if (composerEnabled) {
                localState.updateComposerFieldValue(nextValue)
                localState.updateSuppressInlineSlashPanel(false)
                onChange(nextValue.text)
                val nextRouting = routeComposerInput(activePanel, nextValue.text)
                onActivePanelChange(nextRouting.nextPanel)
                localState.updateSlashQuery(nextRouting.nextSlashQuery)
            }
        },
        sendNow = {
            if (sendEnabled) {
                localState.updateDismissImeAfterSend(true)
                onSend()
            } else if (shouldClearFocusAfterComposerSendAttempt(sendEnabled, state.connectionStatus)) {
                focusManager.clearFocus(force = true)
            }
        },
    )
}
