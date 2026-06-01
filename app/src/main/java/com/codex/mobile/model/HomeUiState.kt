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
    val slashCommands: List<String>,
    val pendingApproval: String?,
    val cwd: String,
    val permissionSummary: String,
    val connectionStatus: ConnectionStatus,
    val connectionDetail: String,
    val gatewayConfig: GatewayConfig,
    val isDemoMode: Boolean
)
