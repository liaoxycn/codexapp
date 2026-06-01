package com.codex.mobile.model

data class SessionRemoteState(
    val threads: List<ThreadSummary> = emptyList(),
    val selectedThreadId: String = "",
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
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionDetail: String = "未连接 gateway",
    val gatewayConfig: GatewayConfig = GatewayConfig(),
    val desktopRestartRequired: Boolean = false,
    val operationalNotices: List<OperationalNotice> = emptyList(),
    val isDemoMode: Boolean = true,
    val snapshotRevision: Long = 0L,
    val configOptions: GatewayConfigOptions = GatewayConfigOptions()
)

data class OperationalNotice(
    val id: String,
    val text: String,
    val createdAt: Long = 0L
)
