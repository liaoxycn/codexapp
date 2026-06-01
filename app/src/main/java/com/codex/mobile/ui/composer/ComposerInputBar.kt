package com.codex.mobile.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun ComposerInputBar(
    state: HomeUiState,
    compactMode: Boolean,
    composerEnabled: Boolean,
    sendEnabled: Boolean,
    composerFieldValue: TextFieldValue,
    focusRequester: FocusRequester,
    inputInteractionSource: MutableInteractionSource,
    onFocusComposer: () -> Unit,
    onComposerValueChange: (TextFieldValue) -> Unit,
    onToggleDetails: () -> Unit,
    onSendNow: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodexTheme.colors.surface)
            .padding(horizontal = 0.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ComposerDetailsToggleButton(
            expanded = state.showComposerDetails,
            onToggleDetails = onToggleDetails
        )
        ComposerInputField(
            value = composerFieldValue,
            compactMode = compactMode,
            composerEnabled = composerEnabled,
            connectionStatus = state.connectionStatus,
            focusRequester = focusRequester,
            interactionSource = inputInteractionSource,
            onFocusComposer = onFocusComposer,
            onValueChange = onComposerValueChange,
            onSendNow = onSendNow
        )
        ComposerSubmitButton(
            isGenerating = state.isGenerating,
            sendEnabled = sendEnabled,
            onSendNow = onSendNow,
            onStop = onStop
        )
    }
}
