package com.codex.mobile.data.gateway

import com.codex.mobile.model.ComposerChip
import com.codex.mobile.model.ComposerChipIcon
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.ThreadGroupKind
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary

internal fun String.toThreadStatus(): ThreadStatus = when (this.lowercase()) {
    "running", "inprogress" -> ThreadStatus.RUNNING
    "needs_approval", "approval", "paused" -> ThreadStatus.NEEDS_APPROVAL
    "failed", "error" -> ThreadStatus.FAILED
    else -> ThreadStatus.IDLE
}

internal fun String.toMessageRole(): MessageRole = when (this.lowercase()) {
    "user" -> MessageRole.USER
    "system" -> MessageRole.SYSTEM
    else -> MessageRole.ASSISTANT
}

internal fun String.toConnectionStatus(): ConnectionStatus = when (this.lowercase()) {
    "connected" -> ConnectionStatus.CONNECTED
    "connecting" -> ConnectionStatus.CONNECTING
    "error", "failed" -> ConnectionStatus.ERROR
    else -> ConnectionStatus.DISCONNECTED
}

internal fun emptyRemoteState(
    config: GatewayConfig
): SessionRemoteState = SessionRemoteState(
    threads = emptyList(),
    selectedThreadId = "",
    pendingThreadTitle = null,
    isThreadSwitching = false,
    messages = emptyList(),
    hasMoreHistory = false,
    isLoadingOlder = false,
    isGenerating = false,
    chips = emptyList(),
    slashCommands = listOf(
        "/compact  压缩当前上下文",
        "/goal     设置当前会话目标",
        "! ls      执行 shell 命令"
    ),
    pendingApproval = null,
    cwd = "",
    permissionSummary = "",
    connectionStatus = ConnectionStatus.DISCONNECTED,
    connectionDetail = if (config.url.isBlank()) "未连接 desktop gateway" else "未连接 ${config.url}",
    gatewayConfig = config,
    isDemoMode = true
)

internal fun GatewaySnapshotMessage.applyTo(
    previous: SessionRemoteState
): SessionRemoteState {
    val threads = threads.toThreadSummaries()
    return previous.copy(
        threads = threads,
        selectedThreadId = selectedThreadId ?: threads.firstOrNull()?.id.orEmpty(),
        pendingThreadTitle = null,
        isThreadSwitching = false,
        messages = messages.toThreadMessages(),
        hasMoreHistory = hasMoreHistory,
        isLoadingOlder = false,
        pendingApproval = pendingApproval,
        chips = chips.toComposerChips(),
        slashCommands = slashCommands,
        cwd = cwd.orEmpty(),
        permissionSummary = permissionSummary.orEmpty(),
        isGenerating = isGenerating,
        isManualRefreshing = false,
        connectionStatus = ConnectionStatus.CONNECTED,
        connectionDetail = if (threads.isEmpty()) "已连接，暂无会话" else "已同步 ${threads.size} 个会话",
        isDemoMode = false
    )
}

internal fun GatewayStatusMessage.applyTo(
    previous: SessionRemoteState
): SessionRemoteState {
    val nextStatus = when (status.lowercase()) {
        "connected" -> ConnectionStatus.CONNECTED
        else -> status.toConnectionStatus()
    }
    return previous.copy(
        connectionStatus = nextStatus,
        connectionDetail = detail ?: previous.connectionDetail,
        isDemoMode = status != "connected" && previous.isDemoMode
    )
}

internal fun List<GatewayThreadPayload>.toThreadSummaries(): List<ThreadSummary> {
    return map {
        ThreadSummary(
            id = it.id,
            title = it.title,
            preview = it.subtitle ?: it.preview,
            status = it.status.toThreadStatus(),
            updatedAt = it.updatedAt ?: 0L,
            groupKind = if (it.groupKind == "project") ThreadGroupKind.PROJECT else ThreadGroupKind.CHAT,
            groupLabel = it.groupLabel ?: "普通会话",
            cwd = it.cwd.orEmpty(),
            archived = it.archived == true
        )
    }
}

internal fun List<GatewayMessagePayload>.toThreadMessages(): List<ThreadMessage> {
    return map { message ->
        ThreadMessage(
            id = message.id,
            role = message.role.toMessageRole(),
            blocks = message.blocks.map { it.toMessageBlock() }
        )
    }
}

internal fun List<GatewayChipPayload>.toComposerChips(): List<ComposerChip> {
    return map { chip ->
        ComposerChip(
            label = chip.label,
            icon = if (chip.icon == "context") ComposerChipIcon.CONTEXT else ComposerChipIcon.FILE
        )
    }
}

internal fun GatewayBlockPayload.toMessageBlock(): MessageBlock {
    return when (kind) {
        "code" -> MessageBlock.Code(
            language = language ?: "text",
            value = value
        )

        "status" -> MessageBlock.Status(value)
        "reasoning" -> MessageBlock.Reasoning(value)
        "commandSummary" -> MessageBlock.CommandSummary(value)
        "commandMeta" -> MessageBlock.CommandMeta(value)
        "fileChangeSummary" -> MessageBlock.FileChangeSummary(value)
        "fileChangeMeta" -> MessageBlock.FileChangeMeta(value, path.orEmpty())
        "fileChangeDiff" -> MessageBlock.FileChangeDiff(value)
        else -> MessageBlock.Text(value)
    }
}
