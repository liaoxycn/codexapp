package com.codexapp.data.gateway

import com.codexapp.model.ComposerChip
import com.codexapp.model.ComposerChipIcon
import com.codexapp.model.ComposerFile
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.GatewayConfig
import com.codexapp.model.GatewayConfigDefaults
import com.codexapp.model.GatewayConfigOption
import com.codexapp.model.GatewayConfigOptions
import com.codexapp.model.MessageRole
import com.codexapp.model.MessageBlock
import com.codexapp.model.OperationalNotice
import com.codexapp.model.SessionConfig
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.StateDiagnostics
import com.codexapp.model.ThreadGroupKind
import com.codexapp.model.ThreadMessage
import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary
import com.codexapp.model.TokenUsageState

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
    tokenUsage = null,
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
    val nextMessages = previous.messages.mergeSnapshotMessages(messages.toThreadMessages(previous.messages))
    if (!previous.shouldAcceptSelectedThreadSnapshot(incomingSelectedThreadId, threads)) {
        return previous.withDeferredSelectionSnapshot(
            threads = threads,
            desktopRestartRequired = desktopRestartRequired,
            operationalNotices = operationalNotices.toOperationalNotices(),
            revision = revision
        )
    }
    val nextDiagnostics = diagnostics.toStateDiagnostics(revision ?: previous.snapshotRevision)
    return previous.copy(
        threads = threads,
        selectedThreadId = incomingSelectedThreadId,
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        messages = nextMessages,
        hasMoreHistory = hasMoreHistory,
        isLoadingOlder = false,
        pendingApproval = pendingApproval,
        chips = chips.toComposerChips(),
        files = files.toComposerFiles(),
        slashCommands = slashCommands,
        cwd = cwd.orEmpty(),
        permissionSummary = permissionSummary.orEmpty(),
        sessionConfig = sessionConfig.toSessionConfig(),
        tokenUsage = tokenUsage?.toTokenUsageState(),
        configOptions = configOptions.toConfigOptions(),
        desktopRestartRequired = desktopRestartRequired,
        operationalNotices = operationalNotices.toOperationalNotices(),
        isGenerating = resolveSelectedThreadGenerating(
            selectedThreadId = incomingSelectedThreadId,
            threads = threads,
            messages = nextMessages,
            snapshotIsGenerating = isGenerating,
            pendingApproval = pendingApproval,
            diagnostics = nextDiagnostics
        ),
        diagnostics = nextDiagnostics,
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
    val nextMessages = if (acceptSelectedSnapshot && "messages" in changedFields) {
        previous.messages.mergeSnapshotMessages(messages.orEmpty().toThreadMessages(previous.messages))
    } else {
        previous.messages
    }
    val patchIsGenerating = if (acceptSelectedSnapshot && "isGenerating" in changedFields) {
        isGenerating == true
    } else {
        previous.isGenerating
    }
    val nextPendingApproval = if (acceptSelectedSnapshot && "pendingApproval" in changedFields) {
        pendingApproval
    } else {
        previous.pendingApproval
    }
    val nextDiagnostics = if ("diagnostics" in changedFields) {
        diagnostics?.toStateDiagnostics(revision) ?: previous.diagnostics.copy(snapshotRevision = revision)
    } else {
        previous.diagnostics.copy(snapshotRevision = revision)
    }

    return previous.copy(
        threads = nextThreads,
        selectedThreadId = if (acceptSelectedSnapshot) nextSelectedThreadId else previous.selectedThreadId,
        pendingSelectionThreadId = if (acceptSelectedSnapshot) null else previous.pendingSelectionThreadId,
        pendingThreadTitle = if (acceptSelectedSnapshot) null else previous.pendingThreadTitle,
        isThreadSwitching = if (acceptSelectedSnapshot) false else previous.isThreadSwitching,
        messages = nextMessages,
        hasMoreHistory = if (acceptSelectedSnapshot && "hasMoreHistory" in changedFields) hasMoreHistory == true else previous.hasMoreHistory,
        isLoadingOlder = if (acceptSelectedSnapshot) false else previous.isLoadingOlder,
        pendingApproval = nextPendingApproval,
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
        tokenUsage = if (acceptSelectedSnapshot && "tokenUsage" in changedFields) {
            tokenUsage?.toTokenUsageState()
        } else {
            previous.tokenUsage
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
        isGenerating = resolveSelectedThreadGenerating(
            selectedThreadId = nextSelectedThreadId,
            threads = nextThreads,
            messages = nextMessages,
            snapshotIsGenerating = patchIsGenerating,
            pendingApproval = nextPendingApproval,
            diagnostics = nextDiagnostics
        ),
        diagnostics = nextDiagnostics,
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

private fun resolveSelectedThreadGenerating(
    selectedThreadId: String,
    threads: List<ThreadSummary>,
    messages: List<ThreadMessage>,
    snapshotIsGenerating: Boolean,
    pendingApproval: String?,
    diagnostics: StateDiagnostics
): Boolean {
    if (pendingApproval != null) {
        return false
    }
    val selectedThreadStatus = threads.firstOrNull { it.id == selectedThreadId }?.status
    if (selectedThreadStatus == ThreadStatus.RUNNING) {
        return true
    }
    if (diagnostics.runningThreadIds.contains(selectedThreadId)) {
        return true
    }
    val diagnosticsTargetsSelected = diagnostics.selectedThreadId.isBlank() ||
        diagnostics.selectedThreadId == selectedThreadId
    if (diagnostics.isGenerating && diagnosticsTargetsSelected) {
        return true
    }
    val diagnosticsHasRuntimeSignal = diagnostics.snapshotRevision > 0L &&
        (
            diagnostics.selectedThreadId.isNotBlank() ||
                diagnostics.actionTraceId.isNotBlank() ||
                diagnostics.actionType.isNotBlank() ||
                diagnostics.actionStatus.isNotBlank() ||
                diagnostics.runningThreadIds.isNotEmpty() ||
                diagnostics.isGenerating
            )
    val diagnosticsProvesSelectedIdle = diagnosticsHasRuntimeSignal &&
        selectedThreadId.isNotBlank() &&
        !diagnostics.runningThreadIds.contains(selectedThreadId) &&
        (!diagnostics.isGenerating || !diagnosticsTargetsSelected) &&
        (selectedThreadStatus == ThreadStatus.IDLE || diagnostics.selectedThreadId == selectedThreadId)
    if (diagnosticsProvesSelectedIdle) {
        return false
    }
    if (snapshotIsGenerating) {
        return true
    }
    return messages.hasOpenAssistantTurn()
}

private fun List<ThreadMessage>.hasOpenAssistantTurn(): Boolean {
    val lastUserIndex = indexOfLast { it.role == MessageRole.USER }
    if (lastUserIndex < 0 || lastUserIndex == lastIndex) {
        return false
    }
    val turnMessages = drop(lastUserIndex + 1)
    if (turnMessages.none { it.role == MessageRole.ASSISTANT }) {
        return false
    }
    return turnMessages.none { it.role == MessageRole.ASSISTANT && it.isFinal && it.hasAssistantTerminalContent() }
}

private fun ThreadMessage.hasAssistantTerminalContent(): Boolean {
    return blocks.any { block ->
        when (block) {
            is MessageBlock.Text -> block.value.isNotBlank()
            is MessageBlock.Code -> block.value.isNotBlank()
            is MessageBlock.Status -> block.value.isNotBlank()
            is MessageBlock.Reasoning -> block.value.isNotBlank()
            is MessageBlock.Commentary -> block.value.isNotBlank()
            is MessageBlock.Plan -> block.value.isNotBlank()
            is MessageBlock.CommandSummary -> block.value.isNotBlank()
            is MessageBlock.CommandMeta -> block.value.isNotBlank()
            is MessageBlock.ToolCall -> block.value.isNotBlank()
            is MessageBlock.WebSearch -> block.value.isNotBlank()
            is MessageBlock.Image -> block.value.isNotBlank()
            is MessageBlock.Collab -> block.value.isNotBlank()
            is MessageBlock.Review -> block.value.isNotBlank()
            is MessageBlock.Hook -> block.value.isNotBlank()
            is MessageBlock.Context -> block.value.isNotBlank()
            is MessageBlock.FileChangeSummary -> block.value.isNotBlank()
            is MessageBlock.FileChangeMeta -> block.value.isNotBlank() || block.path.isNotBlank()
            is MessageBlock.FileChangeDiff -> block.value.isNotBlank()
        }
    }
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

internal fun List<GatewayMessagePayload>.toThreadMessages(
    previousMessages: List<ThreadMessage> = emptyList()
): List<ThreadMessage> {
    val previousById = previousMessages.associateBy(ThreadMessage::id)
    val mapped = map { message ->
        message.toThreadMessage(previousById[message.id])
    }
    return previousMessages.reuseIfSameMessages(mapped)
}

private fun List<ThreadMessage>.mergeSnapshotMessages(
    snapshotMessages: List<ThreadMessage>
): List<ThreadMessage> {
    val merged = when {
        isEmpty() || snapshotMessages.isEmpty() -> snapshotMessages.dedupeDuplicateUserMessagesWithinOpenTurns()
        none { it.isOptimisticUserMessage() } -> snapshotMessages.dedupeDuplicateUserMessagesWithinOpenTurns()
        else -> {
            val snapshotUserTexts = snapshotMessages
                .asSequence()
                .filter { it.role == MessageRole.USER }
                .map { it.normalizedText() }
                .filter { it.isNotBlank() }
                .toSet()
            val carriedOptimisticMessages = filter { message ->
                message.isOptimisticUserMessage() && message.normalizedText() !in snapshotUserTexts
            }
            (snapshotMessages + carriedOptimisticMessages).dedupeDuplicateUserMessagesWithinOpenTurns()
        }
    }
    return reuseIfSameMessages(merged)
}

private fun ThreadMessage.isOptimisticUserMessage(): Boolean {
    return role == MessageRole.USER && id.startsWith("user-")
}

private fun List<ThreadMessage>.dedupeDuplicateUserMessagesWithinOpenTurns(): List<ThreadMessage> {
    val deduped = mutableListOf<ThreadMessage>()
    val userTextIndexInOpenTurn = mutableMapOf<String, Int>()
    for (message in this) {
        if (message.role == MessageRole.USER) {
            val text = message.normalizedText()
            val previousIndex = userTextIndexInOpenTurn[text]
            if (text.isNotBlank() && previousIndex != null) {
                deduped[previousIndex] = message
                continue
            }
            if (text.isNotBlank()) {
                userTextIndexInOpenTurn[text] = deduped.size
            }
            deduped += message
            continue
        }

        deduped += message
        if (message.role == MessageRole.ASSISTANT && message.isFinal && message.hasAssistantTerminalContent()) {
            userTextIndexInOpenTurn.clear()
        }
    }
    return deduped
}

private fun GatewayMessagePayload.toThreadMessage(previous: ThreadMessage?): ThreadMessage {
    val role = role.toMessageRole()
    val blocks = blocks.toMessageBlocks(previous?.blocks)
    val next = ThreadMessage(
        id = id,
        role = role,
        blocks = blocks,
        forkNumTurns = forkNumTurns?.takeIf { it > 0 },
        rollbackNumTurns = rollbackNumTurns?.takeIf { it > 0 },
        durationMs = durationMs?.takeIf { it > 0L },
        isFinal = isFinal
    )
    return if (
        previous != null &&
        previous.role == next.role &&
        previous.blocks === next.blocks &&
        previous.forkNumTurns == next.forkNumTurns &&
        previous.rollbackNumTurns == next.rollbackNumTurns &&
        previous.durationMs == next.durationMs &&
        previous.isFinal == next.isFinal
    ) {
        previous
    } else {
        next
    }
}

private fun List<GatewayBlockPayload>.toMessageBlocks(
    previousBlocks: List<MessageBlock>?
): List<MessageBlock> {
    val mapped = mapIndexed { index, block ->
        block.toMessageBlock(previousBlocks?.getOrNull(index))
    }
    return previousBlocks.reuseIfSameBlocks(mapped)
}

internal fun GatewayBlockPayload.toMessageBlock(previous: MessageBlock? = null): MessageBlock {
    return when (kind) {
        "code" -> previous.takeIf {
            it is MessageBlock.Code &&
                it.language == (language ?: "text") &&
                it.value == value
        } ?: MessageBlock.Code(
            language = language ?: "text",
            value = value
        )

        "status" -> previous.takeIf { it is MessageBlock.Status && it.value == value } ?: MessageBlock.Status(value)
        "reasoning" -> previous.takeIf { it is MessageBlock.Reasoning && it.value == value } ?: MessageBlock.Reasoning(value)
        "commentary" -> previous.takeIf { it is MessageBlock.Commentary && it.value == value } ?: MessageBlock.Commentary(value)
        "plan" -> previous.takeIf { it is MessageBlock.Plan && it.value == value } ?: MessageBlock.Plan(value)
        "commandSummary" -> previous.takeIf { it is MessageBlock.CommandSummary && it.value == value } ?: MessageBlock.CommandSummary(value)
        "commandMeta" -> previous.takeIf { it is MessageBlock.CommandMeta && it.value == value } ?: MessageBlock.CommandMeta(value)
        "toolCall" -> previous.takeIf { it is MessageBlock.ToolCall && it.value == value } ?: MessageBlock.ToolCall(value)
        "webSearch" -> previous.takeIf { it is MessageBlock.WebSearch && it.value == value } ?: MessageBlock.WebSearch(value)
        "image" -> previous.takeIf { it is MessageBlock.Image && it.value == value } ?: MessageBlock.Image(value)
        "collab" -> previous.takeIf { it is MessageBlock.Collab && it.value == value } ?: MessageBlock.Collab(value)
        "review" -> previous.takeIf { it is MessageBlock.Review && it.value == value } ?: MessageBlock.Review(value)
        "hook" -> previous.takeIf { it is MessageBlock.Hook && it.value == value } ?: MessageBlock.Hook(value)
        "context" -> previous.takeIf { it is MessageBlock.Context && it.value == value } ?: MessageBlock.Context(value)
        "fileChangeSummary" -> previous.takeIf { it is MessageBlock.FileChangeSummary && it.value == value } ?: MessageBlock.FileChangeSummary(value)
        "fileChangeMeta" -> previous.takeIf {
            it is MessageBlock.FileChangeMeta && it.value == value && it.path == path.orEmpty()
        } ?: MessageBlock.FileChangeMeta(value, path.orEmpty())
        "fileChangeDiff" -> previous.takeIf { it is MessageBlock.FileChangeDiff && it.value == value } ?: MessageBlock.FileChangeDiff(value)
        else -> previous.takeIf { it is MessageBlock.Text && it.value == value } ?: MessageBlock.Text(value)
    }
}

private fun List<MessageBlock>?.reuseIfSameBlocks(next: List<MessageBlock>): List<MessageBlock> {
    val previous = this ?: return next
    if (previous.size != next.size) {
        return next
    }
    return if (previous.indices.all { previous[it] === next[it] }) previous else next
}

private fun List<ThreadMessage>.reuseIfSameMessages(next: List<ThreadMessage>): List<ThreadMessage> {
    if (size != next.size) {
        return next
    }
    return if (indices.all { this[it] === next[it] }) this else next
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

internal fun GatewayDiagnosticsPayload.toStateDiagnostics(revisionFallback: Long): StateDiagnostics {
    return StateDiagnostics(
        selectedThreadId = selectedThreadId.orEmpty(),
        pendingSelectionThreadId = pendingSelectionThreadId.orEmpty(),
        isGenerating = isGenerating,
        runningThreadIds = runningThreadIds.filter(String::isNotBlank),
        snapshotRevision = snapshotRevision.takeIf { it > 0L } ?: revisionFallback,
        actionTraceId = actionTraceId.orEmpty(),
        actionType = actionType.orEmpty(),
        actionStatus = actionStatus.orEmpty(),
        actionStartedAt = actionStartedAt,
        actionFinishedAt = actionFinishedAt
    )
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

private fun GatewayTokenUsagePayload.toTokenUsageState(): TokenUsageState {
    return TokenUsageState(
        totalTokens = totalTokens.coerceAtLeast(0L),
        inputTokens = inputTokens.coerceAtLeast(0L),
        outputTokens = outputTokens.coerceAtLeast(0L),
        reasoningTokens = reasoningTokens.coerceAtLeast(0L),
        contextPercent = contextPercent?.coerceIn(0, 100)
    )
}

private fun GatewayConfigOptionPayload.toConfigOption(): GatewayConfigOption {
    return GatewayConfigOption(
        label = label.ifBlank { value },
        value = value,
        description = description.orEmpty()
    )
}
