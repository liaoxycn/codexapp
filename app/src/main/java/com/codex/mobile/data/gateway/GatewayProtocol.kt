package com.codex.mobile.data.gateway

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
    val cwd: String? = null
)

@Serializable
internal data class GatewayForkThreadMessage(
    val type: String = "fork_thread",
    val threadId: String
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
    val type: String = "refresh_threads"
)

@Serializable
internal data class GatewayLoadOlderMessagesMessage(
    val type: String = "load_older_messages"
)

@Serializable
internal data class GatewaySendPromptMessage(
    val type: String = "send_prompt",
    val text: String,
    val threadId: String? = null
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
    val slashCommands: List<String> = emptyList(),
    val cwd: String? = null,
    val permissionSummary: String? = null,
    val isGenerating: Boolean = false
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
    val slashCommands: List<String>? = null,
    val cwd: String? = null,
    val permissionSummary: String? = null,
    val isGenerating: Boolean? = null
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
    val archived: Boolean? = null
)

@Serializable
internal data class GatewayMessagePayload(
    val id: String,
    val role: String,
    val blocks: List<GatewayBlockPayload> = emptyList()
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

