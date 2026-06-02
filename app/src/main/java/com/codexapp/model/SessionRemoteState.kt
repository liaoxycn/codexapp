package com.codexapp.model

data class SessionRemoteState(
    val threads: List<ThreadSummary> = emptyList(),
    val selectedThreadId: String = "",
    val pendingSelectionThreadId: String? = null,
    val pendingThreadTitle: String? = null,
    val isThreadSwitching: Boolean = false,
    val messages: List<ThreadMessage> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val isGenerating: Boolean = false,
    val isManualRefreshing: Boolean = false,
    val chips: List<ComposerChip> = emptyList(),
    val files: List<ComposerFile> = emptyList(),
    val slashCommands: List<String> = emptyList(),
    val pendingApproval: String? = null,
    val cwd: String = "",
    val permissionSummary: String = "",
    val sessionConfig: SessionConfig = SessionConfig(),
    val tokenUsage: TokenUsageState? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionDetail: String = "未连接 gateway",
    val gatewayConfig: GatewayConfig = GatewayConfig(),
    val desktopRestartRequired: Boolean = false,
    val operationalNotices: List<OperationalNotice> = emptyList(),
    val isDemoMode: Boolean = true,
    val snapshotRevision: Long = 0L,
    val configOptions: GatewayConfigOptions = GatewayConfigOptions(),
    val diagnostics: StateDiagnostics = StateDiagnostics()
)

data class TokenUsageState(
    val totalTokens: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val contextPercent: Int? = null
)

data class OperationalNotice(
    val id: String,
    val text: String,
    val createdAt: Long = 0L
)

data class StateDiagnostics(
    val selectedThreadId: String = "",
    val pendingSelectionThreadId: String = "",
    val isGenerating: Boolean = false,
    val runningThreadIds: List<String> = emptyList(),
    val snapshotRevision: Long = 0L,
    val actionTraceId: String = "",
    val actionType: String = "",
    val actionStatus: String = "",
    val actionStartedAt: Long = 0L,
    val actionFinishedAt: Long = 0L
)
