package com.codexapp.debug

import androidx.annotation.VisibleForTesting
import com.codexapp.model.HomeUiState
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.ThreadMessage
import com.codexapp.model.ThreadSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun HomeUiState.toAgentDebugJson(
    messageLimit: Int = 40,
    threadLimit: Int = 50
): JsonObject {
    val selectedThread = threads.firstOrNull { it.id == selectedThreadId }
    return buildJsonObject {
        put("schema", "codexapp.agentDebug.v1")
        put("selectedThreadId", selectedThreadId)
        put("selectedThreadTitle", selectedThread?.title.orEmpty())
        put("selectedThreadStatus", selectedThread?.status?.name?.lowercase().orEmpty())
        put("selectedThreadCwd", selectedThread?.cwd.orEmpty())
        put("pendingSelectionThreadId", pendingSelectionThreadId.orEmpty())
        put("pendingThreadTitle", pendingThreadTitle.orEmpty())
        put("isThreadSwitching", isThreadSwitching)
        put("isGenerating", isGenerating)
        put("isNewThreadDraft", isNewThreadDraft)
        put("isForkingThread", isForkingThread)
        put("connectionStatus", connectionStatus.name.lowercase())
        put("connectionDetail", connectionDetail)
        put("gatewayUrl", gatewayConfig.url)
        put("cwd", cwd)
        put("permissionSummary", permissionSummary)
        put("composerText", composerText)
        put("composerFocusRequest", composerFocusRequest)
        put("showComposerDetails", showComposerDetails)
        put("pendingApproval", pendingApproval.orEmpty())
        put("hasMoreHistory", hasMoreHistory)
        put("isLoadingOlder", isLoadingOlder)
        put("isManualRefreshing", isManualRefreshing)
        put("desktopRestartRequired", desktopRestartRequired)
        put("messageCount", messages.size)
        put("threadCount", threads.size)
        put("chipCount", chips.size)
        put("fileCount", files.size)
        put("slashCommandCount", slashCommands.size)
        put("operationalNoticeCount", operationalNotices.size)
        put("pendingEditResend", buildJsonObject {
            val pending = pendingEditResend
            put("threadId", pending?.threadId.orEmpty())
            put("rollbackNumTurns", pending?.rollbackNumTurns ?: 0)
        })
        put("appUpdate", buildJsonObject {
            put("status", appUpdate.status.name.lowercase())
            put("latestVersion", appUpdate.latestVersion)
            put("localVersion", appUpdate.localVersion)
            put("message", appUpdate.message)
        })
        put("diagnostics", buildJsonObject {
            put("snapshotRevision", diagnostics.snapshotRevision)
            put("selectedThreadId", diagnostics.selectedThreadId)
            put("isGenerating", diagnostics.isGenerating)
            put("runningThreadIds", JsonArray(diagnostics.runningThreadIds.map(::JsonPrimitive)))
            put("actionType", diagnostics.actionType)
            put("actionStatus", diagnostics.actionStatus)
            put("actionTraceId", diagnostics.actionTraceId)
        })
        put("testSummary", messages.toAgentDebugTestSummary(isGenerating))
        put("currentDraft", buildJsonObject {
            put("cwd", newThreadDraft.cwd)
            put("model", newThreadDraft.model)
            put("reasoningEffort", newThreadDraft.reasoningEffort)
            put("permissionMode", newThreadDraft.permissionMode)
            put("permissionLabel", newThreadDraft.permissionLabel)
            put("sandboxMode", newThreadDraft.sandboxMode)
            put("approvalPolicy", newThreadDraft.approvalPolicy)
        })
        put("threads", buildJsonArray {
            threads.take(threadLimit.coerceAtLeast(1)).forEach { thread ->
                add(thread.toAgentDebugJson())
            }
        })
        put("messages", buildJsonArray {
            messages.takeLast(messageLimit.coerceAtLeast(1)).forEach { message ->
                add(message.toAgentDebugJson())
            }
        })
        put("uiHints", buildJsonObject {
            put("messageListTag", "thread_message_list")
            put("processedHeaderTag", "assistant_processed_header")
            put("jumpToBottomTag", "jump_to_bottom_button")
            put("runningFooterText", "正在思考中")
        })
    }
}

private fun List<ThreadMessage>.toAgentDebugTestSummary(isGenerating: Boolean): JsonObject {
    val assistantMessages = filter { it.role == MessageRole.ASSISTANT }
    val lastMessage = lastOrNull()
    val lastAssistant = assistantMessages.lastOrNull()
    val allBlocks = flatMap { it.blocks }
    val assistantBlocks = assistantMessages.flatMap { it.blocks }
    val lastAssistantProcessBlocks = lastAssistant?.blocks.orEmpty().filter { it.isProcessBlock() }
    return buildJsonObject {
        put("userMessageCount", count { it.role == MessageRole.USER })
        put("systemMessageCount", count { it.role == MessageRole.SYSTEM })
        put("assistantMessageCount", assistantMessages.size)
        put("finalAssistantMessageCount", assistantMessages.count { it.isFinal })
        put("nonFinalAssistantMessageCount", assistantMessages.count { !it.isFinal })
        put("runningAssistantMessageCount", if (isGenerating) assistantMessages.count { !it.isFinal } else 0)
        put("processBlockCount", assistantBlocks.count { it.isProcessBlock() })
        put("reasoningBlockCount", assistantBlocks.count { it is MessageBlock.Reasoning })
        put("commentaryBlockCount", assistantBlocks.count { it is MessageBlock.Commentary })
        put("toolCallBlockCount", assistantBlocks.count { it is MessageBlock.ToolCall })
        put("commandBlockCount", assistantBlocks.count { it is MessageBlock.CommandSummary || it is MessageBlock.CommandMeta })
        put("commandOutputBlockCount", assistantBlocks.count { it.isCommandOutputBlock() })
        put("fileChangeBlockCount", assistantBlocks.count {
            it is MessageBlock.FileChangeSummary ||
                it is MessageBlock.FileChangeMeta ||
                it is MessageBlock.FileChangeDiff
        })
        put("textBlockCount", assistantBlocks.count { it is MessageBlock.Text })
        put("blockKindCounts", allBlocks.toKindCountJson())
        put("lastMessageId", lastMessage?.id.orEmpty())
        put("lastMessageRole", lastMessage?.role?.name?.lowercase().orEmpty())
        put("lastAssistantMessageId", lastAssistant?.id.orEmpty())
        put("lastAssistantIsFinal", lastAssistant?.isFinal ?: false)
        put("lastAssistantDurationMs", lastAssistant?.durationMs ?: 0L)
        put("lastAssistantBlockKinds", JsonArray(lastAssistant?.blocks.orEmpty().map { JsonPrimitive(it.kindName()) }))
        put("lastAssistantHasProcess", lastAssistantProcessBlocks.isNotEmpty())
        put("lastAssistantHasFinalText", lastAssistant?.blocks.orEmpty().any { it is MessageBlock.Text })
        put("lastAssistantProcessBlockCount", lastAssistantProcessBlocks.size)
        put("lastAssistantProcessBlockKinds", JsonArray(lastAssistantProcessBlocks.map { JsonPrimitive(it.kindName()) }))
        put("lastAssistantProcessPreview", lastAssistantProcessBlocks.joinToString(" ") { it.previewText() }.trim().take(500))
        put("lastAssistantPreview", lastAssistant?.blocks.orEmpty().joinToString(" ") { it.previewText() }.trim().take(500))
        put("lastUserPreview", lastOrNull { it.role == MessageRole.USER }?.blocks.orEmpty().joinToString(" ") {
            it.previewText()
        }.trim().take(500))
    }
}

private fun List<MessageBlock>.toKindCountJson(): JsonObject {
    val counts = groupingBy { it.kindName() }.eachCount()
    return buildJsonObject {
        counts.toSortedMap().forEach { (kind, count) ->
            put(kind, count)
        }
    }
}

private fun MessageBlock.isProcessBlock(): Boolean {
    return when (this) {
        is MessageBlock.Status,
        is MessageBlock.Reasoning,
        is MessageBlock.Commentary,
        is MessageBlock.Plan,
        is MessageBlock.CommandSummary,
        is MessageBlock.CommandMeta,
        is MessageBlock.ToolCall,
        is MessageBlock.WebSearch,
        is MessageBlock.Collab,
        is MessageBlock.Review,
        is MessageBlock.Hook,
        is MessageBlock.Context,
        is MessageBlock.FileChangeSummary,
        is MessageBlock.FileChangeMeta,
        is MessageBlock.FileChangeDiff -> true
        is MessageBlock.Code -> isCommandOutputBlock()
        is MessageBlock.Text,
        is MessageBlock.Image -> false
    }
}

private fun MessageBlock.isCommandOutputBlock(): Boolean {
    return this is MessageBlock.Code && language.equals("shell", ignoreCase = true)
}

private fun ThreadSummary.toAgentDebugJson(): JsonObject {
    return buildJsonObject {
        put("id", id)
        put("title", title)
        put("preview", preview)
        put("status", status.name.lowercase())
        put("updatedAt", updatedAt)
        put("groupKind", groupKind.name.lowercase())
        put("groupLabel", groupLabel)
        put("cwd", cwd)
        put("archived", archived)
        put("gitBranch", gitBranch)
        put("gitSha", gitSha)
    }
}

private fun ThreadMessage.toAgentDebugJson(): JsonObject {
    return buildJsonObject {
        put("id", id)
        put("role", role.name.lowercase())
        put("forkNumTurns", forkNumTurns ?: 0)
        put("rollbackNumTurns", rollbackNumTurns ?: 0)
        put("durationMs", durationMs ?: 0L)
        put("isFinal", isFinal)
        put("textPreview", blocks.joinToString(" ") { it.previewText() }.trim().take(240))
        put("processBlockCount", blocks.count { it.isProcessBlock() })
        put("processBlockKinds", JsonArray(blocks.filter { it.isProcessBlock() }.map { JsonPrimitive(it.kindName()) }))
        put("blocks", buildJsonArray {
            blocks.forEach { block ->
                add(block.toAgentDebugJson())
            }
        })
    }
}

private fun MessageBlock.toAgentDebugJson(): JsonObject {
    return buildJsonObject {
        put("kind", kindName())
        put("value", previewText())
        if (this@toAgentDebugJson is MessageBlock.Code) {
            put("language", language)
        }
        if (this@toAgentDebugJson is MessageBlock.FileChangeMeta) {
            put("path", path)
        }
    }
}

private fun MessageBlock.kindName(): String {
    return when (this) {
        is MessageBlock.Text -> "text"
        is MessageBlock.Code -> "code"
        is MessageBlock.Status -> "status"
        is MessageBlock.Reasoning -> "reasoning"
        is MessageBlock.Commentary -> "commentary"
        is MessageBlock.Plan -> "plan"
        is MessageBlock.CommandSummary -> "commandSummary"
        is MessageBlock.CommandMeta -> "commandMeta"
        is MessageBlock.ToolCall -> "toolCall"
        is MessageBlock.WebSearch -> "webSearch"
        is MessageBlock.Image -> "image"
        is MessageBlock.Collab -> "collab"
        is MessageBlock.Review -> "review"
        is MessageBlock.Hook -> "hook"
        is MessageBlock.Context -> "context"
        is MessageBlock.FileChangeSummary -> "fileChangeSummary"
        is MessageBlock.FileChangeMeta -> "fileChangeMeta"
        is MessageBlock.FileChangeDiff -> "fileChangeDiff"
    }
}

private fun MessageBlock.previewText(): String {
    val raw = when (this) {
        is MessageBlock.Text -> value
        is MessageBlock.Code -> value
        is MessageBlock.Status -> value
        is MessageBlock.Reasoning -> value
        is MessageBlock.Commentary -> value
        is MessageBlock.Plan -> value
        is MessageBlock.CommandSummary -> value
        is MessageBlock.CommandMeta -> value
        is MessageBlock.ToolCall -> value
        is MessageBlock.WebSearch -> value
        is MessageBlock.Image -> value
        is MessageBlock.Collab -> value
        is MessageBlock.Review -> value
        is MessageBlock.Hook -> value
        is MessageBlock.Context -> value
        is MessageBlock.FileChangeSummary -> value
        is MessageBlock.FileChangeMeta -> value.ifBlank { path }
        is MessageBlock.FileChangeDiff -> value
    }
    return raw.replace(Regex("\\s+"), " ").trim().take(500)
}
