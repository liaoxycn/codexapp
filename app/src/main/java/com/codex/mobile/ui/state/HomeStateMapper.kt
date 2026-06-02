package com.codex.mobile.ui.state

import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.AppUpdateState
import com.codex.mobile.model.ComposerChip
import com.codex.mobile.model.ComposerChipIcon
import com.codex.mobile.model.NewThreadDraft
import com.codex.mobile.model.SessionConfig
import com.codex.mobile.model.SessionRemoteState

internal fun SessionRemoteState.toHomeState(
    composer: String,
    composerExpanded: Boolean,
    composerFocusRequest: Long,
    isNewThreadDraft: Boolean,
    draftSubmissionInFlight: Boolean,
    newThreadDraft: NewThreadDraft,
    appUpdate: AppUpdateState = AppUpdateState()
): HomeUiState = HomeUiState(
    threads = threads,
    selectedThreadId = if (isNewThreadDraft) "" else selectedThreadId,
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
    configOptions = configOptions
)

private fun NewThreadDraft.toComposerChips(): List<ComposerChip> {
    val projectLabel = cwd.ifBlank { "默认项目" }
    return listOf(
        ComposerChip(label = projectLabel, icon = ComposerChipIcon.FILE, path = cwd.takeIf(String::isNotBlank)),
        ComposerChip(label = model.ifBlank { "默认模型" }, icon = ComposerChipIcon.CONTEXT),
        ComposerChip(label = sandboxMode.ifBlank { "默认权限" }, icon = ComposerChipIcon.CONTEXT)
    )
}

private fun NewThreadDraft.toSessionConfig(): SessionConfig {
    return SessionConfig(
        permissionMode = sandboxMode,
        model = model,
        reasoningEffort = reasoningEffort
    )
}
