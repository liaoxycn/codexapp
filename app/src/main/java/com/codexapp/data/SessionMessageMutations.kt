package com.codexapp.data

import com.codexapp.model.ConnectionStatus
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.ThreadMessage

internal fun SessionRemoteState.withOptimisticPrompt(
    prompt: String,
    nowMillis: Long = System.currentTimeMillis()
): SessionRemoteState {
    val userMessage = ThreadMessage(
        id = "user-$nowMillis",
        role = MessageRole.USER,
        blocks = listOf(MessageBlock.Text(prompt))
    )
    return copy(
        isGenerating = true,
        messages = messages + userMessage
    )
}

internal fun SessionRemoteState.withSendFailure(
    detail: String,
    nowMillis: Long = System.currentTimeMillis()
): SessionRemoteState {
    val clearedMessages = if (messages.lastOrNull()?.id == "assistant-pending") {
        messages.dropLast(1)
    } else {
        messages
    }
    return copy(
        messages = clearedMessages + ThreadMessage(
            id = "system-send-failed-$nowMillis",
            role = MessageRole.SYSTEM,
            blocks = listOf(MessageBlock.Status(detail))
        ),
        isGenerating = false,
        connectionStatus = ConnectionStatus.ERROR,
        connectionDetail = detail
    )
}
