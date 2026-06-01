package com.codex.mobile.ui.composer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codex.mobile.model.HomeUiState

internal data class ComposerControllerLocalState(
    val dismissImeAfterSend: Boolean,
    val slashQuery: String,
    val fileQuery: String,
    val suppressInlineSlashPanel: Boolean,
    val composerFieldValue: TextFieldValue,
    val focusRequester: FocusRequester,
    val inputInteractionSource: MutableInteractionSource,
    val updateDismissImeAfterSend: (Boolean) -> Unit,
    val updateSlashQuery: (String) -> Unit,
    val updateFileQuery: (String) -> Unit,
    val updateSuppressInlineSlashPanel: (Boolean) -> Unit,
    val updateComposerFieldValue: (TextFieldValue) -> Unit,
)

@Composable
internal fun rememberComposerControllerLocalState(
    state: HomeUiState
): ComposerControllerLocalState {
    var dismissImeAfterSend by rememberSaveable { mutableStateOf(false) }
    var slashQuery by rememberSaveable { mutableStateOf("") }
    var fileQuery by rememberSaveable { mutableStateOf("") }
    var suppressInlineSlashPanel by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val inputInteractionSource = remember { MutableInteractionSource() }
    var composerFieldValue by remember(state.selectedThreadId, state.pendingThreadTitle) {
        mutableStateOf(TextFieldValue(state.composerText, TextRange(state.composerText.length)))
    }

    return ComposerControllerLocalState(
        dismissImeAfterSend = dismissImeAfterSend,
        slashQuery = slashQuery,
        fileQuery = fileQuery,
        suppressInlineSlashPanel = suppressInlineSlashPanel,
        composerFieldValue = composerFieldValue,
        focusRequester = focusRequester,
        inputInteractionSource = inputInteractionSource,
        updateDismissImeAfterSend = { dismissImeAfterSend = it },
        updateSlashQuery = { slashQuery = it },
        updateFileQuery = { fileQuery = it },
        updateSuppressInlineSlashPanel = { suppressInlineSlashPanel = it },
        updateComposerFieldValue = { composerFieldValue = it }
    )
}
