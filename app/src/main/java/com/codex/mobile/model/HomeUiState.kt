package com.codex.mobile.model

data class HomeUiState(
    val threads: List<ThreadSummary>,
    val selectedThreadId: String,
    val pendingThreadTitle: String?,
    val isThreadSwitching: Boolean,
    val messages: List<ThreadMessage>,
    val hasMoreHistory: Boolean,
    val isLoadingOlder: Boolean,
    val composerText: String,
    val composerFocusRequest: Long,
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
    val connectionStatus: ConnectionStatus,
    val connectionDetail: String,
    val gatewayConfig: GatewayConfig,
    val desktopRestartRequired: Boolean = false,
    val operationalNotices: List<OperationalNotice> = emptyList(),
    val appUpdate: AppUpdateState = AppUpdateState(),
    val isDemoMode: Boolean,
    val isNewThreadDraft: Boolean,
    val newThreadDraft: NewThreadDraft,
    val configOptions: GatewayConfigOptions = GatewayConfigOptions()
)

data class NewThreadDraft(
    val cwd: String = "",
    val model: String = "",
    val reasoningEffort: String = "",
    val sandboxMode: String = ""
)

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
