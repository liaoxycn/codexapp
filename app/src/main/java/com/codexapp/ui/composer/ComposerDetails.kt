package com.codexapp.ui.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexapp.model.ComposerFile
import com.codexapp.model.GatewayConfigOptions
import com.codexapp.model.HomeUiState
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.SessionConfig
import com.codexapp.ui.thread.DraftOption
import com.codexapp.ui.thread.buildConfigDraftOptions
import com.codexapp.ui.thread.buildModelDraftOptions
import com.codexapp.ui.thread.buildPermissionDraftOptions
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
    onDraftChange: (NewThreadDraft) -> Unit,
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
            DraftQuickConfigRow(
                draft = state.composerConfigDraft,
                configOptions = state.configOptions,
                onDraftChange = onDraftChange
            )
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
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
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
}

@Composable
private fun DraftQuickConfigRow(
    draft: NewThreadDraft,
    configOptions: GatewayConfigOptions,
    onDraftChange: (NewThreadDraft) -> Unit,
) {
    val modelOptions = buildModelDraftOptions(draft, configOptions.models)
    val reasoningOptions = buildConfigDraftOptions(configOptions.reasoningEfforts)
    val permissionOptions = buildPermissionDraftOptions(configOptions)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DraftQuickPicker(
            modifier = Modifier.weight(1.2f),
            title = "权限模式",
            options = permissionOptions,
            selected = draft.permissionMode,
            onSelect = { onDraftChange(draft.copy(permissionMode = it)) }
        )
        DraftQuickPicker(
            modifier = Modifier.weight(1f),
            title = "模型",
            options = modelOptions,
            selected = draft.model,
            onSelect = { onDraftChange(draft.copy(model = it)) }
        )
        if (reasoningOptions.isNotEmpty()) {
            DraftQuickPicker(
                modifier = Modifier.weight(0.9f),
                title = "推理",
                options = reasoningOptions,
                selected = draft.reasoningEffort,
                onSelect = { onDraftChange(draft.copy(reasoningEffort = it)) }
            )
        }
    }
}

@Composable
private fun DraftQuickPicker(
    modifier: Modifier = Modifier,
    title: String,
    options: List<DraftOption>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var dropdownWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val selectedOption = options.firstOrNull { it.value == selected } ?: options.firstOrNull()
    val selectedLabel = selectedOption?.label?.ifBlank { selectedOption.value } ?: "读取中"
    val enabled = options.any { it.enabled }

    Box(
        modifier = modifier
            .onGloballyPositioned { dropdownWidthPx = it.size.width }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CodexTheme.colors.surfaceSubtle)
                .border(1.dp, CodexTheme.colors.border.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { expanded = true }
                .semantics { contentDescription = "$title：$selectedLabel" }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = selectedLabel,
                color = if (enabled) CodexTheme.colors.textPrimary else CodexTheme.colors.textTertiary,
                fontSize = ComposerMetaTextSize,
                lineHeight = ComposerMetaLineHeight,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = CodexTheme.colors.textSecondary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = if (dropdownWidthPx > 0) {
                Modifier.width(with(density) { dropdownWidthPx.toDp() })
            } else {
                Modifier
            }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    enabled = option.enabled,
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    }
                ) {
                    Text(
                        text = option.label.ifBlank { option.value.ifBlank { "读取中" } },
                        color = if (option.enabled) CodexTheme.colors.textPrimary else CodexTheme.colors.textTertiary,
                        fontSize = ComposerTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
