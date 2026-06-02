package com.codexapp.ui.state

import com.codexapp.model.HomeUiState
import com.codexapp.model.AppUpdateState
import com.codexapp.model.ComposerChip
import com.codexapp.model.ComposerChipIcon
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.PendingEditResendState
import com.codexapp.model.SessionConfig
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.resolveSupportedNewThreadPermissionMode

internal fun SessionRemoteState.toHomeState(
    composer: String,
    composerExpanded: Boolean,
    composerFocusRequest: Long,
    pendingEditResend: PendingEditResendState?,
    isNewThreadDraft: Boolean,
    draftSubmissionInFlight: Boolean,
    isForkingThread: Boolean,
    newThreadDraft: NewThreadDraft,
    composerConfigDraftOverride: NewThreadDraft? = null,
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
    pendingEditResend = pendingEditResend,
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
    permissionSummary = if (isNewThreadDraft) newThreadDraft.permissionLabel else permissionSummary,
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
    composerConfigDraft = resolveComposerConfigDraft(
        isNewThreadDraft = isNewThreadDraft,
        newThreadDraft = newThreadDraft,
        overrideDraft = composerConfigDraftOverride,
        sessionConfig = sessionConfig,
        cwd = cwd,
        configOptions = configOptions
    ),
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
        permissionLabel.takeIf(String::isNotBlank)?.let { ComposerChip(label = it, icon = ComposerChipIcon.CONTEXT) }
    )
}

private fun NewThreadDraft.toSessionConfig(): SessionConfig {
    return SessionConfig(
        permissionMode = permissionLabel,
        model = model,
        reasoningEffort = reasoningEffort
    )
}

private fun resolveComposerConfigDraft(
    isNewThreadDraft: Boolean,
    newThreadDraft: NewThreadDraft,
    overrideDraft: NewThreadDraft?,
    sessionConfig: SessionConfig,
    cwd: String,
    configOptions: com.codexapp.model.GatewayConfigOptions
): NewThreadDraft {
    if (isNewThreadDraft) {
        return newThreadDraft
    }
    if (overrideDraft != null) {
        return overrideDraft
    }
    return NewThreadDraft(
        cwd = cwd,
        model = sessionConfig.model.ifBlank { configOptions.defaults.model },
        reasoningEffort = sessionConfig.reasoningEffort.ifBlank { configOptions.defaults.reasoningEffort },
        permissionMode = resolveSupportedNewThreadPermissionMode(
            requested = sessionConfig.permissionMode.toPermissionDraftValue(),
            availableSandboxModes = configOptions.sandboxModes.map { it.value }
        )
    )
}

private fun String.toPermissionDraftValue(): String {
    return when (trim()) {
        "danger-full-access", "完全访问权限" -> "full-access"
        "workspace-write", "workspace-write+net", "默认权限" -> "default"
        "auto-review", "自动审查" -> "auto-review"
        else -> "full-access"
    }
}
