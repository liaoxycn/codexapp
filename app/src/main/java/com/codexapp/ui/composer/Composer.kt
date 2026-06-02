package com.codexapp.ui.composer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.Text
import com.codexapp.model.HomeUiState
import com.codexapp.model.NewThreadDraft
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun Composer(
    state: HomeUiState,
    compactMode: Boolean,
    activePanel: ComposerPanel,
    onActivePanelChange: (ComposerPanel) -> Unit,
    onToggleDetails: () -> Unit,
    onCloseDetails: () -> Unit,
    onChange: (String) -> Unit,
    onInsertText: (String) -> Unit,
    onApplySlashCommand: (String) -> Unit,
    onDraftChange: (NewThreadDraft) -> Unit,
    onClearComposer: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val controller = rememberComposerController(
        state = state,
        activePanel = activePanel,
        onActivePanelChange = onActivePanelChange,
        onChange = onChange,
        onInsertText = onInsertText,
        onApplySlashCommand = onApplySlashCommand,
        onSend = onSend,
    )

    fun clearComposerFocus() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    BackHandler(enabled = activePanel != ComposerPanel.NONE || state.showComposerDetails) {
        if (activePanel != ComposerPanel.NONE) {
            onActivePanelChange(ComposerPanel.NONE)
            clearComposerFocus()
        } else if (state.showComposerDetails) {
            onCloseDetails()
            clearComposerFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodexTheme.colors.surface)
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        ComposerPendingEditResendHint(state = state)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(CodexTheme.colors.surface)
                .border(1.dp, CodexTheme.colors.border.copy(alpha = 0.92f), RoundedCornerShape(26.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            ComposerDetailsSection(
                state = state,
                activePanel = activePanel,
                slashPanelVisible = controller.slashPanelVisible,
                filePanelVisible = controller.filePanelVisible,
                filteredCommands = controller.filteredCommands,
                trailingToken = controller.trailingToken,
                slashQuery = controller.slashQuery,
                fileQuery = controller.fileQuery,
                onSlashQueryChange = controller.onSlashQueryChange,
                onFileQueryChange = controller.onFileQueryChange,
                onActivePanelChange = onActivePanelChange,
                onClearComposer = onClearComposer,
                onResetInlineSlashPanel = controller.resetInlineSlashPanel,
                onSelectSlashCommand = controller.selectSlashCommand,
                onSelectFile = controller.selectFile,
                onDraftChange = onDraftChange,
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
}

@Composable
private fun ComposerPendingEditResendHint(state: HomeUiState) {
    val pending = state.pendingEditResend ?: return
    Text(
        text = "已进入编辑后重发，下一次发送会回滚最近 ${pending.rollbackNumTurns} 轮后重发",
        color = CodexTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            .testTag("composer_pending_edit_resend_hint")
    )
}
