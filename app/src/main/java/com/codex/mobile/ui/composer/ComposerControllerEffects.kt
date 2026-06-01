package com.codex.mobile.ui.composer

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

@Composable
internal fun HandleComposerControllerEffects(
    composerText: String,
    composerFocusRequest: Long,
    localState: ComposerControllerLocalState,
    onActivePanelChange: (ComposerPanel) -> Unit,
    focusManager: FocusManager,
    rootView: View,
    keyboardController: SoftwareKeyboardController?
) {
    LaunchedEffect(localState.dismissImeAfterSend, composerText) {
        if (localState.dismissImeAfterSend && composerText.isBlank()) {
            onActivePanelChange(ComposerPanel.NONE)
            localState.updateSlashQuery("")
            localState.updateFileQuery("")
            localState.updateSuppressInlineSlashPanel(false)
            focusManager.clearFocus(force = true)
            rootView.clearFocus()
            keyboardController?.hide()
            localState.updateDismissImeAfterSend(false)
        }
    }

    LaunchedEffect(composerText) {
        if (localState.composerFieldValue.text != composerText) {
            localState.updateComposerFieldValue(
                TextFieldValue(composerText, TextRange(composerText.length))
            )
        }
        if (composerText.isBlank()) {
            localState.updateSuppressInlineSlashPanel(false)
        }
    }

    LaunchedEffect(composerFocusRequest) {
        if (composerFocusRequest > 0L) {
            localState.focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
}
