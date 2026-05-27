package com.codex.mobile.model

data class ThreadSummary(
    val id: String,
    val title: String,
    val preview: String,
    val status: ThreadStatus,
    val updatedAt: Long = 0L
)

data class ThreadMessage(
    val id: String,
    val role: MessageRole,
    val blocks: List<MessageBlock>
)

sealed interface MessageBlock {
    data class Text(val value: String) : MessageBlock
    data class Code(val language: String, val value: String) : MessageBlock
    data class Status(val value: String) : MessageBlock
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class ThreadStatus {
    RUNNING,
    IDLE,
    NEEDS_APPROVAL,
    FAILED
}

data class ComposerChip(
    val label: String,
    val icon: ComposerChipIcon
)

enum class ComposerChipIcon {
    FILE,
    CONTEXT
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class GatewayConfig(
    val url: String = "",
    val pairToken: String = ""
)

data class SessionRemoteState(
    val threads: List<ThreadSummary> = emptyList(),
    val selectedThreadId: String = "",
    val pendingThreadTitle: String? = null,
    val isThreadSwitching: Boolean = false,
    val messages: List<ThreadMessage> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val isGenerating: Boolean = false,
    val chips: List<ComposerChip> = emptyList(),
    val slashCommands: List<String> = emptyList(),
    val pendingApproval: String? = null,
    val cwd: String = "",
    val permissionSummary: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionDetail: String = "未连接 gateway",
    val gatewayConfig: GatewayConfig = GatewayConfig(),
    val isDemoMode: Boolean = true
)

data class HomeUiState(
    val threads: List<ThreadSummary>,
    val selectedThreadId: String,
    val pendingThreadTitle: String?,
    val isThreadSwitching: Boolean,
    val messages: List<ThreadMessage>,
    val hasMoreHistory: Boolean,
    val composerText: String,
    val isGenerating: Boolean,
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
