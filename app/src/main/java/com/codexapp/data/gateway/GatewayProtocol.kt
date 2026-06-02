package com.codexapp.data.gateway

import kotlinx.serialization.Serializable

@Serializable
internal data class GatewayEnvelope(
    val type: String
)

@Serializable
internal data class GatewayHelloMessage(
    val type: String = "hello",
    val client: String = "android-shell",
    val version: String = "0.2.0",
    val pairToken: String? = null,
    val selectedThreadId: String? = null,
    val capabilities: List<String> = listOf("snapshot_patch")
)

@Serializable
internal data class GatewaySelectThreadMessage(
    val type: String = "select_thread",
    val threadId: String
)

@Serializable
internal data class GatewayCreateThreadMessage(
    val type: String = "create_thread",
    val cwd: String? = null,
    val model: String? = null,
    val reasoningEffort: String? = null,
    val approvalPolicy: String? = null,
    val approvalsReviewer: String? = null,
    val sandboxMode: String? = null
)

@Serializable
internal data class GatewayForkThreadMessage(
    val type: String = "fork_thread",
    val threadId: String,
    val numTurns: Int? = null
)

@Serializable
internal data class GatewayRenameThreadMessage(
    val type: String = "rename_thread",
    val threadId: String,
    val name: String
)

@Serializable
internal data class GatewayArchiveThreadMessage(
    val type: String = "archive_thread",
    val threadId: String
)

@Serializable
internal data class GatewayUnarchiveThreadMessage(
    val type: String = "unarchive_thread",
    val threadId: String
)

@Serializable
internal data class GatewayRefreshThreadsMessage(
    val type: String = "refresh_threads",
    val forceSnapshot: Boolean = false
)

@Serializable
internal data class GatewayLoadOlderMessagesMessage(
    val type: String = "load_older_messages"
)

@Serializable
internal data class GatewaySendPromptMessage(
    val type: String = "send_prompt",
    val text: String,
    val threadId: String? = null,
    val newThread: Boolean = false,
    val cwd: String? = null,
    val model: String? = null,
    val reasoningEffort: String? = null,
    val approvalPolicy: String? = null,
    val approvalsReviewer: String? = null,
    val sandboxMode: String? = null
)

@Serializable
internal data class GatewayRollbackThreadMessage(
    val type: String = "rollback_thread",
    val threadId: String? = null,
    val numTurns: Int
)

@Serializable
internal data class GatewayResendPromptMessage(
    val type: String = "resend_prompt",
    val text: String,
    val threadId: String? = null,
    val rollbackNumTurns: Int,
    val model: String? = null,
    val reasoningEffort: String? = null,
    val approvalPolicy: String? = null,
    val approvalsReviewer: String? = null,
    val sandboxMode: String? = null
)

@Serializable
internal data class GatewayStopTurnMessage(
    val type: String = "stop_turn"
)

@Serializable
internal data class GatewayApprovePendingMessage(
    val type: String = "approve_pending"
)

@Serializable
internal data class GatewayRejectPendingMessage(
    val type: String = "reject_pending"
)

@Serializable
internal data class GatewayRestartDesktopMessage(
    val type: String = "restart_desktop"
)

@Serializable
internal data class GatewayStatusMessage(
    val type: String = "status",
    val status: String,
    val detail: String? = null
)

@Serializable
internal data class GatewaySnapshotMessage(
    val type: String = "snapshot",
    val revision: Long? = null,
    val threads: List<GatewayThreadPayload> = emptyList(),
    val selectedThreadId: String? = null,
    val messages: List<GatewayMessagePayload> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val pendingApproval: String? = null,
    val chips: List<GatewayChipPayload> = emptyList(),
    val files: List<GatewayFilePayload> = emptyList(),
    val slashCommands: List<String> = emptyList(),
    val cwd: String? = null,
    val permissionSummary: String? = null,
    val sessionConfig: GatewaySessionConfigPayload = GatewaySessionConfigPayload(),
    val configOptions: GatewayConfigOptionsPayload = GatewayConfigOptionsPayload(),
    val operationalNotices: List<GatewayOperationalNoticePayload> = emptyList(),
    val desktopRestartRequired: Boolean = false,
    val tokenUsage: GatewayTokenUsagePayload? = null,
    val isGenerating: Boolean = false,
    val diagnostics: GatewayDiagnosticsPayload = GatewayDiagnosticsPayload()
)

@Serializable
internal data class GatewaySnapshotPatchMessage(
    val type: String = "snapshot_patch",
    val baseRevision: Long,
    val revision: Long,
    val changed: List<String> = emptyList(),
    val threads: List<GatewayThreadPayload>? = null,
    val selectedThreadId: String? = null,
    val messages: List<GatewayMessagePayload>? = null,
    val hasMoreHistory: Boolean? = null,
    val pendingApproval: String? = null,
    val chips: List<GatewayChipPayload>? = null,
    val files: List<GatewayFilePayload>? = null,
    val slashCommands: List<String>? = null,
    val cwd: String? = null,
    val permissionSummary: String? = null,
    val sessionConfig: GatewaySessionConfigPayload? = null,
    val configOptions: GatewayConfigOptionsPayload? = null,
    val operationalNotices: List<GatewayOperationalNoticePayload>? = null,
    val desktopRestartRequired: Boolean? = null,
    val tokenUsage: GatewayTokenUsagePayload? = null,
    val isGenerating: Boolean? = null,
    val diagnostics: GatewayDiagnosticsPayload? = null
)

@Serializable
internal data class GatewayTokenUsagePayload(
    val totalTokens: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val contextPercent: Int? = null
)

@Serializable
internal data class GatewayOperationalNoticePayload(
    val id: String,
    val text: String,
    val createdAt: Long = 0L
)

@Serializable
internal data class GatewayDiagnosticsPayload(
    val selectedThreadId: String? = null,
    val pendingSelectionThreadId: String? = null,
    val isGenerating: Boolean = false,
    val runningThreadIds: List<String> = emptyList(),
    val snapshotRevision: Long = 0L,
    val actionTraceId: String? = null,
    val actionType: String? = null,
    val actionStatus: String? = null,
    val actionStartedAt: Long = 0L,
    val actionFinishedAt: Long = 0L
)

@Serializable
internal data class GatewaySessionConfigPayload(
    val permissionMode: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val reasoningEffort: String? = null
)

@Serializable
internal data class GatewayConfigOptionsPayload(
    val models: List<GatewayConfigOptionPayload> = emptyList(),
    val reasoningEfforts: List<GatewayConfigOptionPayload> = emptyList(),
    val sandboxModes: List<GatewayConfigOptionPayload> = emptyList(),
    val defaults: GatewayConfigDefaultsPayload = GatewayConfigDefaultsPayload()
)

@Serializable
internal data class GatewayConfigOptionPayload(
    val label: String,
    val value: String,
    val description: String? = null
)

@Serializable
internal data class GatewayConfigDefaultsPayload(
    val model: String? = null,
    val reasoningEffort: String? = null,
    val sandboxMode: String? = null
)

@Serializable
internal data class GatewayThreadPayload(
    val id: String,
    val title: String,
    val preview: String,
    val subtitle: String? = null,
    val cwd: String? = null,
    val status: String,
    val updatedAt: Long? = null,
    val groupKind: String? = null,
    val groupLabel: String? = null,
    val archived: Boolean? = null,
    val gitBranch: String? = null,
    val gitSha: String? = null
)

@Serializable
internal data class GatewayMessagePayload(
    val id: String,
    val role: String,
    val blocks: List<GatewayBlockPayload> = emptyList(),
    val forkNumTurns: Int? = null,
    val rollbackNumTurns: Int? = null,
    val durationMs: Long? = null,
    val isFinal: Boolean = false
)

@Serializable
internal data class GatewayBlockPayload(
    val kind: String,
    val value: String,
    val language: String? = null,
    val path: String? = null
)

@Serializable
internal data class GatewayChipPayload(
    val label: String,
    val icon: String,
    val path: String? = null
)

@Serializable
internal data class GatewayFilePayload(
    val label: String,
    val path: String
)

