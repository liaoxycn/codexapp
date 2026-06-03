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
            if (message.role == MessageRole.ASSISTANT && message.blocks.any { it.isAssistantProcessBlock() }) {
                items += TurnMessageItem(
                    message = message,
                    showAssistantActions = isFinalAssistantTurnMessage(index)
                )
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
                if (turnMessages.isNotEmpty()) {
                    items += TurnMessageItem(
                        message = ThreadMessage(
                            id = runningAssistantKey,
                            role = MessageRole.ASSISTANT,
                            blocks = emptyList()
                        ),
                        processMessages = turnMessages,
                        assistantTurnRunning = true,
                        assistantActionsEnabled = false,
                        showAssistantActions = true,
                        preferPlainText = true,
                        stableKey = runningAssistantKey
                    )
                }
            } else {
                val processMessages = turnMessages.take(streamingAssistantIndex)
                val streamingMessage = turnMessages[streamingAssistantIndex]
                items += TurnMessageItem(
                    message = streamingMessage,
                    processMessages = processMessages,
                    assistantTurnRunning = true,
                    assistantActionsEnabled = false,
                    showAssistantActions = true,
                    preferPlainText = true,
                    stableKey = runningAssistantKey
                )
                items += turnMessages.drop(streamingAssistantIndex + 1)
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
            items += turnMessages.mapIndexed { turnIndex, turnMessage ->
                val absoluteIndex = index - turnMessages.size + turnIndex
                TurnMessageItem(
                    message = turnMessage,
                    showAssistantActions = turnMessage.role == MessageRole.ASSISTANT && isFinalAssistantTurnMessage(absoluteIndex)
                )
            }
        } else {
            val processMessages = turnMessages.take(finalAssistantIndex)
            val finalMessage = turnMessages[finalAssistantIndex]
            items += TurnMessageItem(
                message = finalMessage,
                processMessages = processMessages,
                showAssistantActions = true
            )
            items += turnMessages.drop(finalAssistantIndex + 1).mapIndexed { trailingIndex, turnMessage ->
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
    return role == MessageRole.ASSISTANT && isFinal && isAssistantReplyText()
}

private fun ThreadMessage.isAssistantReplyText(): Boolean {
    return role == MessageRole.ASSISTANT && blocks.any { block ->
        block is MessageBlock.Text && block.value.isNotBlank()
    }
}
