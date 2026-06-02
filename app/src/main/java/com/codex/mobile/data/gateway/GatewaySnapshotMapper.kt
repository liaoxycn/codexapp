package com.codex.mobile.data.gateway

import com.codex.mobile.model.ComposerChip
import com.codex.mobile.model.ComposerChipIcon
import com.codex.mobile.model.ComposerFile
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.GatewayConfigDefaults
import com.codex.mobile.model.GatewayConfigOption
import com.codex.mobile.model.GatewayConfigOptions
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.OperationalNotice
import com.codex.mobile.model.SessionConfig
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
    files = emptyList(),
    slashCommands = listOf(
        "/compact  压缩当前上下文",
        "/rollback 回滚最近一轮",
        "! ls      执行 shell 命令"
    ),
    pendingApproval = null,
    cwd = "",
    permissionSummary = "",
    sessionConfig = SessionConfig(),
    configOptions = GatewayConfigOptions(),
    connectionStatus = ConnectionStatus.DISCONNECTED,
    connectionDetail = if (config.url.isBlank()) "未连接 desktop gateway" else "未连接 ${config.url}",
    gatewayConfig = config,
    desktopRestartRequired = false,
    operationalNotices = emptyList(),
    isDemoMode = true
)

internal fun GatewaySnapshotMessage.applyTo(
    previous: SessionRemoteState
): SessionRemoteState {
    val threads = threads.toThreadSummaries()
    val incomingSelectedThreadId = selectedThreadId ?: threads.firstOrNull()?.id.orEmpty()
    if (!previous.shouldAcceptSelectedThreadSnapshot(incomingSelectedThreadId, threads)) {
        return previous.withDeferredSelectionSnapshot(
            threads = threads,
            desktopRestartRequired = desktopRestartRequired,
            operationalNotices = operationalNotices.toOperationalNotices(),
            revision = revision
        )
    }
    return previous.copy(
        threads = threads,
        selectedThreadId = incomingSelectedThreadId,
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        messages = previous.messages.mergeSnapshotMessages(messages.toThreadMessages()),
        hasMoreHistory = hasMoreHistory,
        isLoadingOlder = false,
        pendingApproval = pendingApproval,
        chips = chips.toComposerChips(),
        files = files.toComposerFiles(),
        slashCommands = slashCommands,
        cwd = cwd.orEmpty(),
        permissionSummary = permissionSummary.orEmpty(),
        sessionConfig = sessionConfig.toSessionConfig(),
        configOptions = configOptions.toConfigOptions(),
        desktopRestartRequired = desktopRestartRequired,
        operationalNotices = operationalNotices.toOperationalNotices(),
        isGenerating = isGenerating,
        isManualRefreshing = false,
        connectionStatus = ConnectionStatus.CONNECTED,
        connectionDetail = if (threads.isEmpty()) "已连接，暂无会话" else "已同步 ${threads.size} 个会话",
        isDemoMode = false,
        snapshotRevision = revision ?: previous.snapshotRevision
    )
}

internal fun GatewaySnapshotPatchMessage.applyTo(
    previous: SessionRemoteState
): SessionRemoteState {
    if (previous.snapshotRevision != 0L && baseRevision != previous.snapshotRevision) {
        return previous.copy(
            connectionStatus = ConnectionStatus.CONNECTED,
            connectionDetail = "snapshot patch 基线不匹配，正在刷新完整状态",
            isManualRefreshing = true
        )
    }

    val changedFields = changed.toSet()
    val nextThreads = if ("threads" in changedFields) {
        threads.orEmpty().toThreadSummaries()
    } else {
        previous.threads
    }
    val nextSelectedThreadId = if ("selectedThreadId" in changedFields) {
        selectedThreadId ?: nextThreads.firstOrNull()?.id.orEmpty()
    } else {
        previous.selectedThreadId
    }
    val acceptSelectedSnapshot = previous.shouldAcceptSelectedThreadSnapshot(nextSelectedThreadId, nextThreads)

    return previous.copy(
        threads = nextThreads,
        selectedThreadId = if (acceptSelectedSnapshot) nextSelectedThreadId else previous.selectedThreadId,
        pendingSelectionThreadId = if (acceptSelectedSnapshot) null else previous.pendingSelectionThreadId,
        pendingThreadTitle = if (acceptSelectedSnapshot) null else previous.pendingThreadTitle,
        isThreadSwitching = if (acceptSelectedSnapshot) false else previous.isThreadSwitching,
        messages = if (acceptSelectedSnapshot && "messages" in changedFields) {
            previous.messages.mergeSnapshotMessages(messages.orEmpty().toThreadMessages())
        } else {
            previous.messages
        },
        hasMoreHistory = if (acceptSelectedSnapshot && "hasMoreHistory" in changedFields) hasMoreHistory == true else previous.hasMoreHistory,
        isLoadingOlder = if (acceptSelectedSnapshot) false else previous.isLoadingOlder,
        pendingApproval = if (acceptSelectedSnapshot && "pendingApproval" in changedFields) pendingApproval else previous.pendingApproval,
        chips = if (acceptSelectedSnapshot && "chips" in changedFields) chips.orEmpty().toComposerChips() else previous.chips,
        files = if (acceptSelectedSnapshot && "files" in changedFields) files.orEmpty().toComposerFiles() else previous.files,
        slashCommands = if ("slashCommands" in changedFields) slashCommands.orEmpty() else previous.slashCommands,
        cwd = if (acceptSelectedSnapshot && "cwd" in changedFields) cwd.orEmpty() else previous.cwd,
        permissionSummary = if (acceptSelectedSnapshot && "permissionSummary" in changedFields) permissionSummary.orEmpty() else previous.permissionSummary,
        sessionConfig = if (acceptSelectedSnapshot && "sessionConfig" in changedFields) {
            sessionConfig?.toSessionConfig() ?: SessionConfig()
        } else {
            previous.sessionConfig
        },
        configOptions = if ("configOptions" in changedFields) {
            configOptions?.toConfigOptions() ?: GatewayConfigOptions()
        } else {
            previous.configOptions
        },
        desktopRestartRequired = if ("desktopRestartRequired" in changedFields) {
            desktopRestartRequired == true
        } else {
            previous.desktopRestartRequired
        },
        operationalNotices = if ("operationalNotices" in changedFields) {
            operationalNotices.orEmpty().toOperationalNotices()
        } else {
            emptyList()
        },
        isGenerating = if (acceptSelectedSnapshot && "isGenerating" in changedFields) isGenerating == true else previous.isGenerating,
        isManualRefreshing = if (acceptSelectedSnapshot) false else previous.isManualRefreshing,
        connectionStatus = ConnectionStatus.CONNECTED,
        connectionDetail = if (nextThreads.isEmpty()) "已连接，暂无会话" else "已同步 ${nextThreads.size} 个会话",
        isDemoMode = false,
        snapshotRevision = revision
    )
}

private fun SessionRemoteState.shouldAcceptSelectedThreadSnapshot(
    incomingSelectedThreadId: String,
    threads: List<ThreadSummary>
): Boolean {
    val pendingSelection = pendingSelectionThreadId?.takeIf(String::isNotBlank) ?: return true
    return incomingSelectedThreadId == pendingSelection && threads.any { it.id == pendingSelection }
}

private fun SessionRemoteState.withDeferredSelectionSnapshot(
    threads: List<ThreadSummary>,
    desktopRestartRequired: Boolean,
    operationalNotices: List<OperationalNotice>,
    revision: Long?
): SessionRemoteState {
    return copy(
        threads = threads,
        desktopRestartRequired = desktopRestartRequired,
        operationalNotices = operationalNotices,
        connectionStatus = ConnectionStatus.CONNECTED,
        connectionDetail = if (threads.isEmpty()) "已连接，暂无会话" else "已同步 ${threads.size} 个会话",
        isDemoMode = false,
        snapshotRevision = revision ?: snapshotRevision
    )
}

internal fun GatewaySnapshotPatchMessage.isStaleFor(previous: SessionRemoteState): Boolean {
    return previous.snapshotRevision != 0L && baseRevision != previous.snapshotRevision
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
    return filterNot { it.archived == true }.map {
        ThreadSummary(
            id = it.id,
            title = it.title,
            preview = it.subtitle ?: it.preview,
            status = it.status.toThreadStatus(),
            updatedAt = it.updatedAt ?: 0L,
            groupKind = if (it.groupKind == "project") ThreadGroupKind.PROJECT else ThreadGroupKind.CHAT,
            groupLabel = it.groupLabel ?: "普通会话",
            cwd = it.cwd.orEmpty(),
            archived = it.archived == true,
            gitBranch = it.gitBranch.orEmpty(),
            gitSha = it.gitSha.orEmpty()
        )
    }
}

internal fun List<GatewayMessagePayload>.toThreadMessages(): List<ThreadMessage> {
    return map { message ->
        ThreadMessage(
            id = message.id,
            role = message.role.toMessageRole(),
            blocks = message.blocks.map { it.toMessageBlock() },
            forkNumTurns = message.forkNumTurns?.takeIf { it > 0 },
            rollbackNumTurns = message.rollbackNumTurns?.takeIf { it > 0 },
            durationMs = message.durationMs?.takeIf { it > 0L },
            isFinal = message.isFinal
        )
    }
}

private fun List<ThreadMessage>.mergeSnapshotMessages(
    snapshotMessages: List<ThreadMessage>
): List<ThreadMessage> {
    if (isEmpty() || snapshotMessages.isEmpty()) {
        return snapshotMessages.dedupeAdjacentDuplicateUserMessages()
    }
    if (none { it.isOptimisticUserMessage() }) {
        return snapshotMessages.dedupeAdjacentDuplicateUserMessages()
    }
    val snapshotUserTexts = snapshotMessages
        .asSequence()
        .filter { it.role == MessageRole.USER }
        .map { it.normalizedText() }
        .filter { it.isNotBlank() }
        .toSet()
    val carriedOptimisticMessages = filter { message ->
        message.isOptimisticUserMessage() && message.normalizedText() !in snapshotUserTexts
    }
    return (carriedOptimisticMessages + snapshotMessages).dedupeAdjacentDuplicateUserMessages()
}

private fun ThreadMessage.isOptimisticUserMessage(): Boolean {
    return role == MessageRole.USER && id.startsWith("user-")
}

private fun List<ThreadMessage>.dedupeAdjacentDuplicateUserMessages(): List<ThreadMessage> {
    val deduped = mutableListOf<ThreadMessage>()
    for (message in this) {
        val previous = deduped.lastOrNull()
        if (
            previous != null &&
            previous.role == MessageRole.USER &&
            message.role == MessageRole.USER &&
            previous.normalizedText() == message.normalizedText()
        ) {
            deduped[deduped.lastIndex] = message
        } else {
            deduped += message
        }
    }
    return deduped
}

private fun ThreadMessage.normalizedText(): String {
    return blocks
        .filterIsInstance<MessageBlock.Text>()
        .joinToString("\n") { it.value.trim() }
        .trim()
}

internal fun List<GatewayChipPayload>.toComposerChips(): List<ComposerChip> {
    return map { chip ->
        ComposerChip(
            label = chip.label,
            icon = if (chip.icon == "context") ComposerChipIcon.CONTEXT else ComposerChipIcon.FILE,
            path = chip.path
        )
    }
}

internal fun List<GatewayFilePayload>.toComposerFiles(): List<ComposerFile> {
    return mapNotNull { file ->
        val label = file.label.trim()
        val path = file.path.trim()
        if (label.isBlank() || path.isBlank()) {
            null
        } else {
            ComposerFile(label = label, path = path)
        }
    }
}

internal fun List<GatewayOperationalNoticePayload>.toOperationalNotices(): List<OperationalNotice> {
    return mapNotNull { notice ->
        val id = notice.id.trim()
        val text = notice.text.trim()
        if (id.isBlank() || text.isBlank()) {
            null
        } else {
            OperationalNotice(id = id, text = text, createdAt = notice.createdAt)
        }
    }
}

internal fun GatewayConfigOptionsPayload.toConfigOptions(): GatewayConfigOptions {
    return GatewayConfigOptions(
        models = models.map { it.toConfigOption() },
        reasoningEfforts = reasoningEfforts.map { it.toConfigOption() },
        sandboxModes = sandboxModes.map { it.toConfigOption() },
        defaults = GatewayConfigDefaults(
            model = defaults.model.orEmpty(),
            reasoningEffort = defaults.reasoningEffort.orEmpty(),
            sandboxMode = defaults.sandboxMode.orEmpty()
        )
    )
}

internal fun GatewaySessionConfigPayload.toSessionConfig(): SessionConfig {
    return SessionConfig(
        permissionMode = permissionMode.orEmpty(),
        provider = provider.orEmpty(),
        model = model.orEmpty(),
        reasoningEffort = reasoningEffort.orEmpty()
    )
}

private fun GatewayConfigOptionPayload.toConfigOption(): GatewayConfigOption {
    return GatewayConfigOption(
        label = label.ifBlank { value },
        value = value,
        description = description.orEmpty()
    )
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
