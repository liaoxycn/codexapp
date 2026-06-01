package com.codex.mobile.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun Composer(
    state: HomeUiState,
    compactMode: Boolean,
    activePanel: ComposerPanel,
    onActivePanelChange: (ComposerPanel) -> Unit,
    onToggleCompact: () -> Unit,
    onToggleDetails: () -> Unit,
    onCompactContext: () -> Unit,
    onRollbackLastTurn: () -> Unit,
    onChange: (String) -> Unit,
    onInsertText: (String) -> Unit,
    onApplySlashCommand: (String) -> Unit,
    onClearComposer: () -> Unit,
    onInsertShellTemplate: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val controller = rememberComposerController(
        state = state,
        activePanel = activePanel,
        onActivePanelChange = onActivePanelChange,
        onChange = onChange,
        onApplySlashCommand = onApplySlashCommand,
        onSend = onSend,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodexTheme.colors.surface)
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        ComposerDetailsSection(
            state = state,
            compactMode = compactMode,
            activePanel = activePanel,
            slashPanelVisible = controller.slashPanelVisible,
            filteredCommands = controller.filteredCommands,
            trailingToken = controller.trailingToken,
            slashQuery = controller.slashQuery,
            onSlashQueryChange = controller.onSlashQueryChange,
            onFocusComposer = controller.focusComposer,
            onActivePanelChange = onActivePanelChange,
            onToggleCompact = onToggleCompact,
            onCompactContext = onCompactContext,
            onRollbackLastTurn = onRollbackLastTurn,
            onClearComposer = onClearComposer,
            onInsertShellTemplate = onInsertShellTemplate,
            onInsertText = onInsertText,
            onResetInlineSlashPanel = controller.resetInlineSlashPanel,
            onSelectSlashCommand = controller.selectSlashCommand,
        )
        ComposerInputBar(
            state = state,
            compactMode = compactMode,
            composerEnabled = controller.composerEnabled,
            sendEnabled = controller.sendEnabled,
            composerFieldValue = controller.composerFieldValue,
            focusRequester = controller.focusRequester,
            inputInteractionSource = controller.inputInteractionSource,
            onFocusComposer = controller.focusComposer,
            onComposerValueChange = controller.onComposerValueChange,
            onToggleDetails = onToggleDetails,
            onSendNow = controller.sendNow,
            onStop = onStop,
        )
    }
}
