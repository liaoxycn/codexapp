package com.codexapp.ui.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexapp.model.ComposerFile
import com.codexapp.model.HomeUiState
import com.codexapp.model.SessionConfig
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun ComposerDetailsSection(
    state: HomeUiState,
    activePanel: ComposerPanel,
    slashPanelVisible: Boolean,
    filePanelVisible: Boolean,
    filteredCommands: List<String>,
    trailingToken: String,
    slashQuery: String,
    fileQuery: String,
    onSlashQueryChange: (String) -> Unit,
    onFileQueryChange: (String) -> Unit,
    onActivePanelChange: (ComposerPanel) -> Unit,
    onClearComposer: () -> Unit,
    onResetInlineSlashPanel: () -> Unit,
    onSelectSlashCommand: (String) -> Unit,
    onSelectFile: (ComposerFile) -> Unit,
) {
    AnimatedVisibility(visible = state.showComposerDetails) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CodexTheme.colors.surfaceSubtle)
                    .border(1.dp, CodexTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 5.dp, vertical = 5.dp),
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
                    }
                }
                MiniAction("文件", Icons.Filled.Description) {
                    val opening = activePanel != ComposerPanel.FILE
                    onResetInlineSlashPanel()
                    onActivePanelChange(if (opening) ComposerPanel.FILE else ComposerPanel.NONE)
                    if (opening) {
                        onFileQueryChange("")
                    }
                }
            }
            val configItems = rememberSessionConfigItems(state.sessionConfig)
            if (configItems.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = configItems.joinToString("  ·  "),
                        color = CodexTheme.colors.textSecondary,
                        fontSize = ComposerMetaTextSize,
                        lineHeight = ComposerMetaLineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
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
            AnimatedVisibility(visible = filePanelVisible) {
                FilePickerPanel(
                    query = fileQuery,
                    files = state.files,
                    hasProject = state.cwd.isNotBlank(),
                    projectCwd = state.cwd,
                    onQueryChange = onFileQueryChange,
                    onSelect = onSelectFile
                )
            }
        }
    }
}

private fun rememberSessionConfigItems(config: SessionConfig): List<String> {
    return listOfNotNull(
        config.permissionMode.takeIf(String::isNotBlank),
        config.provider.takeIf(String::isNotBlank),
        config.model.takeIf(String::isNotBlank),
        config.reasoningEffort.takeIf(String::isNotBlank)
    )
}
