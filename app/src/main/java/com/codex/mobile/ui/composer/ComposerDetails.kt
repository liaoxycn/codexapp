package com.codex.mobile.ui.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun ComposerDetailsSection(
    state: HomeUiState,
    compactMode: Boolean,
    activePanel: ComposerPanel,
    slashPanelVisible: Boolean,
    filteredCommands: List<String>,
    trailingToken: String,
    slashQuery: String,
    onSlashQueryChange: (String) -> Unit,
    onFocusComposer: () -> Unit,
    onActivePanelChange: (ComposerPanel) -> Unit,
    onClearComposer: () -> Unit,
    onInsertText: (String) -> Unit,
    onResetInlineSlashPanel: () -> Unit,
    onSelectSlashCommand: (String) -> Unit,
) {
    AnimatedVisibility(visible = state.showComposerDetails) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(top = 3.dp, bottom = 3.dp)
        ) {
            Divider(color = CodexTheme.colors.border, thickness = 1.dp)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MiniAction("清空", Icons.Filled.Delete) {
                    onActivePanelChange(ComposerPanel.NONE)
                    onResetInlineSlashPanel()
                    onClearComposer()
                }
                MiniAction("/命令", Icons.Filled.Search) {
                    val opening = activePanel != ComposerPanel.SLASH
                    onResetInlineSlashPanel()
                    onActivePanelChange(if (opening) ComposerPanel.SLASH else ComposerPanel.NONE)
                    if (opening) {
                        onSlashQueryChange(
                            if (trailingToken.startsWith("/") || trailingToken.startsWith("!")) trailingToken else ""
                        )
                        onFocusComposer()
                    }
                }
                state.chips
                    .filter { it.icon == com.codex.mobile.model.ComposerChipIcon.FILE }
                    .forEach { chip ->
                        MiniAction(chip.label, Icons.AutoMirrored.Filled.InsertDriveFile) {
                            onInsertText("@{${chip.path ?: chip.label}}")
                            onFocusComposer()
                        }
                    }
            }
            if (state.cwd.isNotBlank() || state.permissionSummary.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.cwd.isNotBlank()) {
                        Text(
                            text = state.cwd,
                            color = CodexTheme.colors.textSecondary,
                            fontSize = if (compactMode) 10.sp else 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (state.permissionSummary.isNotBlank()) {
                        Text(
                            text = state.permissionSummary,
                            color = CodexTheme.colors.textTertiary,
                            fontSize = if (compactMode) 10.sp else 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            AnimatedVisibility(visible = slashPanelVisible) {
                SlashCommandPanel(
                    query = slashQuery,
                    commands = filteredCommands,
                    onQueryChange = onSlashQueryChange,
                    onSelect = { command ->
                        onSelectSlashCommand(command.substringBefore("  ").trim())
                    }
                )
            }
        }
    }

    if (state.showComposerDetails) {
        Divider(color = CodexTheme.colors.border, thickness = 1.dp)
    }
}
