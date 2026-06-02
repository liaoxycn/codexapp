package com.codexapp.model

data class HomeUiState(
    val threads: List<ThreadSummary>,
    val selectedThreadId: String,
    val pendingSelectionThreadId: String?,
    val pendingThreadTitle: String?,
    val isThreadSwitching: Boolean,
    val messages: List<ThreadMessage>,
    val hasMoreHistory: Boolean,
    val isLoadingOlder: Boolean,
    val composerText: String,
    val composerFocusRequest: Long,
    val pendingEditResend: PendingEditResendState? = null,
    val isGenerating: Boolean,
    val isManualRefreshing: Boolean,
    val showComposerDetails: Boolean,
    val chips: List<ComposerChip>,
    val files: List<ComposerFile>,
    val slashCommands: List<String>,
    val pendingApproval: String?,
    val cwd: String,
    val permissionSummary: String,
    val sessionConfig: SessionConfig = SessionConfig(),
    val tokenUsage: TokenUsageState? = null,
    val isForkingThread: Boolean = false,
    val connectionStatus: ConnectionStatus,
    val connectionDetail: String,
    val gatewayConfig: GatewayConfig,
    val desktopRestartRequired: Boolean = false,
    val operationalNotices: List<OperationalNotice> = emptyList(),
    val appUpdate: AppUpdateState = AppUpdateState(),
    val isDemoMode: Boolean,
    val isNewThreadDraft: Boolean,
    val newThreadDraft: NewThreadDraft,
    val composerConfigDraft: NewThreadDraft = newThreadDraft,
    val configOptions: GatewayConfigOptions = GatewayConfigOptions(),
    val diagnostics: StateDiagnostics = StateDiagnostics()
)

data class NewThreadDraft(
    val cwd: String = "",
    val model: String = "",
    val reasoningEffort: String = "",
    val permissionMode: String = NewThreadPermissionPresets.FullAccess.value
) {
    val permissionPreset: NewThreadPermissionPreset
        get() = newThreadPermissionPreset(permissionMode)

    val permissionLabel: String
        get() = permissionPreset.label

    val approvalPolicy: String
        get() = permissionPreset.approvalPolicy

    val approvalsReviewer: String
        get() = permissionPreset.approvalsReviewer

    val sandboxMode: String
        get() = permissionPreset.sandboxMode
}

data class SessionConfig(
    val permissionMode: String = "",
    val provider: String = "",
    val model: String = "",
    val reasoningEffort: String = ""
)

data class GatewayConfigOptions(
    val models: List<GatewayConfigOption> = emptyList(),
    val reasoningEfforts: List<GatewayConfigOption> = emptyList(),
    val sandboxModes: List<GatewayConfigOption> = emptyList(),
    val defaults: GatewayConfigDefaults = GatewayConfigDefaults()
)

data class GatewayConfigOption(
    val label: String,
    val value: String,
    val description: String = ""
)

data class GatewayConfigDefaults(
    val model: String = "",
    val reasoningEffort: String = "",
    val sandboxMode: String = ""
)

data class PendingEditResendState(
    val threadId: String,
    val rollbackNumTurns: Int
)
