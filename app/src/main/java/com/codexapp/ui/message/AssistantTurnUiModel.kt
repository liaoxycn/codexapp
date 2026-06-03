package com.codexapp.ui.message

import androidx.compose.runtime.Immutable
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.ThreadMessage

@Immutable
internal data class AssistantTurnUiModel(
    val id: String,
    val bodyBlocks: List<MessageBlock>,
    val processMessages: List<ThreadMessage>,
    val finalText: String,
    val forkNumTurns: Int?,
    val durationMs: Long?,
    val isRunning: Boolean,
    val canCopy: Boolean,
    val canFork: Boolean,
    val preferPlainText: Boolean
) {
    val hasProcess: Boolean
        get() = processMessages.isNotEmpty()

    val showFooterActions: Boolean
        get() = canCopy || canFork
}

internal fun buildAssistantTurnUiModel(
    message: ThreadMessage,
    processMessages: List<ThreadMessage>,
    isRunning: Boolean,
    showActions: Boolean,
    enableFinalActions: Boolean,
    preferPlainText: Boolean
): AssistantTurnUiModel {
    val inlineProcessBlocks = message.blocks.filter { it.isAssistantProcessBlock() }
    val bodyBlocks = message.blocks.filterNot { it.isAssistantProcessBlock() }
    val mergedProcessMessages = if (inlineProcessBlocks.isEmpty()) {
        processMessages
    } else {
        processMessages + ThreadMessage(
            id = "${message.id}:inline-process",
            role = MessageRole.ASSISTANT,
            blocks = inlineProcessBlocks
        )
    }
    val finalText = bodyBlocks.filterIsInstance<MessageBlock.Text>()
        .joinToString("\n") { it.value }
        .trim()
    val canUseFinalActions = showActions && enableFinalActions && !isRunning && message.isFinal
    val canCopy = canUseFinalActions && finalText.isNotBlank()
    val canFork = canUseFinalActions && message.forkNumTurns != null
    return AssistantTurnUiModel(
        id = message.id,
        bodyBlocks = bodyBlocks,
        processMessages = mergedProcessMessages,
        finalText = finalText,
        forkNumTurns = message.forkNumTurns,
        durationMs = message.durationMs,
        isRunning = isRunning,
        canCopy = canCopy,
        canFork = canFork,
        preferPlainText = preferPlainText
    )
}

internal fun MessageBlock.isAssistantProcessBlock(): Boolean {
    return when (this) {
        is MessageBlock.Reasoning,
        is MessageBlock.Status,
        is MessageBlock.CommandSummary,
        is MessageBlock.CommandMeta,
        is MessageBlock.FileChangeSummary,
        is MessageBlock.FileChangeMeta,
        is MessageBlock.FileChangeDiff -> true
        is MessageBlock.Code -> language.equals("shell", ignoreCase = true)
        is MessageBlock.Text -> false
    }
}
