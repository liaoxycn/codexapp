package com.codex.mobile.ui.message

import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.ThreadMessage

internal data class TurnMessageItem(
    val message: ThreadMessage,
    val processMessages: List<ThreadMessage> = emptyList()
)

internal fun List<ThreadMessage>.toTurnMessageItems(currentTurnRunning: Boolean = false): List<TurnMessageItem> {
    val items = mutableListOf<TurnMessageItem>()
    var index = 0
    while (index < size) {
        val message = this[index]
        if (message.role != MessageRole.USER) {
            items += TurnMessageItem(message)
            index += 1
            continue
        }

        items += TurnMessageItem(message)
        index += 1

        val turnMessages = mutableListOf<ThreadMessage>()
        while (index < size && this[index].role != MessageRole.USER) {
            turnMessages += this[index]
            index += 1
        }
        if (currentTurnRunning && index == size) {
            items += turnMessages.map { TurnMessageItem(it) }
            continue
        }
        val finalAssistantIndex = turnMessages.indexOfLast { it.isFinalAssistantReply() }
        if (finalAssistantIndex < 0) {
            items += turnMessages.map { TurnMessageItem(it) }
        } else {
            val processMessages = turnMessages.take(finalAssistantIndex)
            val finalMessage = turnMessages[finalAssistantIndex]
            items += TurnMessageItem(finalMessage, processMessages)
            items += turnMessages.drop(finalAssistantIndex + 1).map { TurnMessageItem(it) }
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
    return role == MessageRole.ASSISTANT && isFinal && blocks.any { block ->
        block is MessageBlock.Text && block.value.isNotBlank()
    }
}
