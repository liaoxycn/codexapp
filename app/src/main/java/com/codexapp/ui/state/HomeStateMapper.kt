package com.codexapp.ui.state

import com.codexapp.model.HomeUiState
import com.codexapp.model.AppUpdateState
import com.codexapp.model.ComposerChip
import com.codexapp.model.ComposerChipIcon
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.SessionConfig
import com.codexapp.model.SessionRemoteState

internal fun SessionRemoteState.toHomeState(
    composer: String,
    composerExpanded: Boolean,
    composerFocusRequest: Long,
    isNewThreadDraft: Boolean,
    draftSubmissionInFlight: Boolean,
    isForkingThread: Boolean,
    newThreadDraft: NewThreadDraft,
    appUpdate: AppUpdateState = AppUpdateState()
): HomeUiState = HomeUiState(
    threads = threads,
    selectedThreadId = if (isNewThreadDraft) "" else selectedThreadId,
    pendingSelectionThreadId = if (isNewThreadDraft) null else pendingSelectionThreadId,
    pendingThreadTitle = if (isNewThreadDraft) null else pendingThreadTitle,
    isThreadSwitching = if (isNewThreadDraft) false else isThreadSwitching,
    messages = if (isNewThreadDraft && !draftSubmissionInFlight) emptyList() else messages,
    hasMoreHistory = if (isNewThreadDraft) false else hasMoreHistory,
    isLoadingOlder = if (isNewThreadDraft) false else isLoadingOlder,
    composerText = composer,
    composerFocusRequest = composerFocusRequest,
    isGenerating = if (isNewThreadDraft && !draftSubmissionInFlight) false else isGenerating,
    showComposerDetails = composerExpanded,
    chips = if (isNewThreadDraft) newThreadDraft.toComposerChips() else chips,
    files = if (isNewThreadDraft) {
        val draftCwd = newThreadDraft.cwd.replace('\\', '/').trimEnd('/')
        files.filter { file ->
            draftCwd.isNotBlank() && file.path.replace('\\', '/').startsWith("$draftCwd/")
        }
    } else {
        files
    },
    slashCommands = slashCommands,
    pendingApproval = if (isNewThreadDraft) null else pendingApproval,
    cwd = if (isNewThreadDraft) newThreadDraft.cwd else cwd,
    permissionSummary = if (isNewThreadDraft) newThreadDraft.sandboxMode else permissionSummary,
    sessionConfig = if (isNewThreadDraft) {
        newThreadDraft.toSessionConfig()
    } else {
        sessionConfig
    },
    isForkingThread = isForkingThread,
    connectionStatus = connectionStatus,
    connectionDetail = connectionDetail,
    gatewayConfig = gatewayConfig,
    desktopRestartRequired = desktopRestartRequired,
    operationalNotices = operationalNotices,
    appUpdate = appUpdate,
    isDemoMode = isDemoMode,
    isManualRefreshing = isManualRefreshing,
    isNewThreadDraft = isNewThreadDraft,
    newThreadDraft = newThreadDraft,
    configOptions = configOptions,
    diagnostics = diagnostics.copy(
        selectedThreadId = if (isNewThreadDraft) "" else diagnostics.selectedThreadId,
        pendingSelectionThreadId = if (isNewThreadDraft) "" else diagnostics.pendingSelectionThreadId,
        isGenerating = if (isNewThreadDraft && !draftSubmissionInFlight) false else diagnostics.isGenerating
    )
)

private fun NewThreadDraft.toComposerChips(): List<ComposerChip> {
    return listOfNotNull(
        cwd.takeIf(String::isNotBlank)?.let { path ->
            ComposerChip(label = path.substringAfterLast('/').substringAfterLast('\\'), icon = ComposerChipIcon.FILE, path = path)
        },
        model.takeIf(String::isNotBlank)?.let { ComposerChip(label = it, icon = ComposerChipIcon.CONTEXT) },
        sandboxMode.takeIf(String::isNotBlank)?.let { ComposerChip(label = it, icon = ComposerChipIcon.CONTEXT) }
    )
}

private fun NewThreadDraft.toSessionConfig(): SessionConfig {
    return SessionConfig(
        permissionMode = sandboxMode,
        model = model,
        reasoningEffort = reasoningEffort
    )
}
