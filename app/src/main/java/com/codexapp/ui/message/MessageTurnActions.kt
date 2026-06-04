package com.codexapp.ui.message

import androidx.compose.runtime.Immutable
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.ThreadMessage

@Immutable
internal data class TurnMessageItem(
    val message: ThreadMessage,
    val processMessages: List<ThreadMessage> = emptyList(),
    val assistantTurnRunning: Boolean = false,
    val assistantActionsEnabled: Boolean = true,
    val showUserActions: Boolean = false,
    val showAssistantActions: Boolean = false,
    val preferPlainText: Boolean = false,
    val stableKey: String = message.id
) {
    val contentType: String
        get() = when {
            message.role == MessageRole.USER -> "user"
            processMessages.isNotEmpty() -> "assistant-turn"
            else -> message.role.name.lowercase()
        }
}

internal fun List<ThreadMessage>.toTurnMessageItems(currentTurnRunning: Boolean = false): List<TurnMessageItem> {
    val items = mutableListOf<TurnMessageItem>()
    var index = 0
    while (index < size) {
        val message = this[index]
        if (message.role != MessageRole.USER) {
            if (message.shouldAttachToAssistantProcessStream()) {
                index += 1
                continue
            }
            items += TurnMessageItem(
                message = message,
                showAssistantActions = message.role == MessageRole.ASSISTANT && isFinalAssistantTurnMessage(index)
            )
            index += 1
            continue
        }

        val userMessage = message
        items += TurnMessageItem(
            message = userMessage,
            showUserActions = isUserTurnActionMessage(index)
        )
        index += 1

        val turnMessages = mutableListOf<ThreadMessage>()
        while (index < size && this[index].role != MessageRole.USER) {
            turnMessages += this[index]
            index += 1
        }
        if (currentTurnRunning && index == size) {
            val runningAssistantKey = "${userMessage.id}:assistant-running"
            val streamingAssistantIndex = turnMessages.indexOfLast { it.isAssistantReplyText() }
            if (streamingAssistantIndex < 0) {
                items += TurnMessageItem(
                    message = ThreadMessage(
                        id = runningAssistantKey,
                        role = MessageRole.ASSISTANT,
                        blocks = emptyList()
                    ),
                    processMessages = turnMessages.ifEmpty { listOf(runningThinkingMessage(runningAssistantKey)) },
                    assistantTurnRunning = true,
                    assistantActionsEnabled = false,
                    showAssistantActions = true,
                    stableKey = runningAssistantKey
                )
            } else {
                val streamingMessage = turnMessages[streamingAssistantIndex]
                val processMessages = turnMessages.filterIndexed { turnIndex, turnMessage ->
                    turnIndex != streamingAssistantIndex && turnMessage.shouldAttachToAssistantProcessStream()
                }
                val trailingMessages = turnMessages.drop(streamingAssistantIndex + 1)
                    .filterNot { it.shouldAttachToAssistantProcessStream() }
                items += TurnMessageItem(
                    message = streamingMessage,
                    processMessages = processMessages,
                    assistantTurnRunning = true,
                    assistantActionsEnabled = false,
                    showAssistantActions = true,
                    stableKey = runningAssistantKey
                )
                items += trailingMessages
                    .map {
                        TurnMessageItem(
                            message = it,
                            assistantActionsEnabled = false,
                            showAssistantActions = it.role == MessageRole.ASSISTANT
                        )
                    }
            }
            continue
        }
        val finalAssistantIndex = turnMessages.indexOfLast { it.isFinalAssistantReply() }
        if (finalAssistantIndex < 0) {
            val fallbackAssistantIndex = turnMessages.indexOfLast { it.isAssistantReplyText() }
            if (fallbackAssistantIndex < 0) {
                val processMessages = turnMessages.filter { it.shouldAttachToAssistantProcessStream() }
                if (processMessages.hasCommandProcessMessage()) {
                    items += TurnMessageItem(
                        message = ThreadMessage(
                            id = "${userMessage.id}:assistant-command-process",
                            role = MessageRole.ASSISTANT,
                            blocks = emptyList(),
                            durationMs = processMessages.lastKnownDurationMs()
                        ),
                        processMessages = processMessages,
                        assistantActionsEnabled = false,
                        showAssistantActions = false
                    )
                } else {
                    items += turnMessages
                        .filterNot { it.shouldAttachToAssistantProcessStream() }
                        .mapIndexed { turnIndex, turnMessage ->
                            val absoluteIndex = index - turnMessages.size + turnIndex
                            TurnMessageItem(
                                message = turnMessage,
                                showAssistantActions = turnMessage.role == MessageRole.ASSISTANT && isFinalAssistantTurnMessage(absoluteIndex)
                            )
                        }
                }
            } else {
                val fallbackMessage = turnMessages[fallbackAssistantIndex]
                val processMessages = turnMessages.filterIndexed { turnIndex, turnMessage ->
                    turnIndex != fallbackAssistantIndex && turnMessage.shouldAttachToAssistantProcessStream()
                }
                val trailingMessages = turnMessages.drop(fallbackAssistantIndex + 1)
                    .filterNot { it.shouldAttachToAssistantProcessStream() }
                items += TurnMessageItem(
                    message = fallbackMessage,
                    processMessages = processMessages,
                    assistantActionsEnabled = false,
                    showAssistantActions = false
                )
                items += trailingMessages.mapIndexed { trailingIndex, turnMessage ->
                    val absoluteIndex = index - turnMessages.size + fallbackAssistantIndex + 1 + trailingIndex
                    TurnMessageItem(
                        message = turnMessage,
                        showAssistantActions = turnMessage.role == MessageRole.ASSISTANT && isFinalAssistantTurnMessage(absoluteIndex)
                    )
                }
            }
        } else {
            val finalMessage = turnMessages[finalAssistantIndex]
            val processMessages = turnMessages.filterIndexed { turnIndex, turnMessage ->
                turnIndex != finalAssistantIndex && turnMessage.shouldAttachToAssistantProcessStream()
            }
            val trailingMessages = turnMessages.drop(finalAssistantIndex + 1)
                .filterNot { it.shouldAttachToAssistantProcessStream() }
            items += TurnMessageItem(
                message = finalMessage,
                processMessages = processMessages,
                showAssistantActions = true
            )
            items += trailingMessages.mapIndexed { trailingIndex, turnMessage ->
                val absoluteIndex = index - turnMessages.size + finalAssistantIndex + 1 + trailingIndex
                TurnMessageItem(
                    message = turnMessage,
                    showAssistantActions = turnMessage.role == MessageRole.ASSISTANT && isFinalAssistantTurnMessage(absoluteIndex)
                )
            }
        }
    }
    return items
}

internal fun List<ThreadMessage>.isUserTurnActionMessage(index: Int): Boolean {
    val message = getOrNull(index) ?: return false
    if (message.role != MessageRole.USER) return false
    return nextVisibleRole(index) != MessageRole.USER
}

internal fun List<ThreadMessage>.isFinalAssistantTurnMessage(index: Int): Boolean {
    val message = getOrNull(index) ?: return false
    if (message.role != MessageRole.ASSISTANT) return false
    return nextVisibleRole(index) != MessageRole.ASSISTANT
}

private fun List<ThreadMessage>.nextVisibleRole(index: Int): MessageRole? {
    for (nextIndex in index + 1 until size) {
        val role = this[nextIndex].role
        if (role != MessageRole.SYSTEM) {
            return role
        }
    }
    return null
}

internal fun ThreadMessage.isFinalAssistantReply(): Boolean {
    return role == MessageRole.ASSISTANT && isFinal && hasAssistantTerminalContent()
}

private fun ThreadMessage.isAssistantReplyText(): Boolean {
    return role == MessageRole.ASSISTANT && blocks.any { block ->
        block is MessageBlock.Text && block.value.isNotBlank()
    }
}

private fun ThreadMessage.hasAssistantTerminalContent(): Boolean {
    return role == MessageRole.ASSISTANT && blocks.any { block ->
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

private fun ThreadMessage.isAssistantProcessMessage(): Boolean {
    return role == MessageRole.ASSISTANT &&
        blocks.isNotEmpty() &&
        blocks.all { it.isAssistantProcessBlock() }
}

private fun ThreadMessage.shouldAttachToAssistantProcessStream(): Boolean {
    return when (role) {
        MessageRole.SYSTEM -> true
        MessageRole.ASSISTANT -> isAssistantProcessMessage()
        MessageRole.USER -> false
    }
}

private fun List<ThreadMessage>.hasCommandProcessMessage(): Boolean {
    return any { message ->
        message.role == MessageRole.ASSISTANT &&
            message.blocks.any { block ->
                block is MessageBlock.CommandSummary ||
                    block is MessageBlock.CommandMeta ||
                    (block is MessageBlock.Code && block.language.equals("shell", ignoreCase = true))
            }
    }
}

private fun List<ThreadMessage>.lastKnownDurationMs(): Long? {
    return asReversed().firstNotNullOfOrNull { it.durationMs }
}

private fun runningThinkingMessage(runningAssistantKey: String): ThreadMessage {
    return ThreadMessage(
        id = "$runningAssistantKey:thinking",
        role = MessageRole.ASSISTANT,
        blocks = listOf(MessageBlock.Reasoning("正在思考"))
    )
}
