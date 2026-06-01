package com.codex.mobile.ui.message

import androidx.compose.runtime.Composable
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.ThreadMessage

@Composable
internal fun MessageCard(
    messages: List<ThreadMessage>,
    message: ThreadMessage,
    processMessages: List<ThreadMessage>,
    compactMode: Boolean,
    messageIndex: Int,
    onEditUserMessage: (String, Int) -> Unit,
    onResendUserMessage: (String, Int) -> Unit,
    onForkFromMessage: (Int) -> Unit
) {
    when (message.role) {
        MessageRole.USER -> UserMessage(
            message = message,
            compactMode = compactMode,
            showActions = messages.isUserTurnActionMessage(messageIndex),
            onEditAndResend = onEditUserMessage,
            onResend = onResendUserMessage
        )
        MessageRole.ASSISTANT -> AssistantMessage(
            message = message,
            processMessages = processMessages,
            compactMode = compactMode,
            messageIndex = messageIndex,
            showActions = messages.isFinalAssistantTurnMessage(messageIndex),
            onForkFromMessage = onForkFromMessage
        )
        MessageRole.SYSTEM -> SystemMessage(message, compactMode)
    }
}
