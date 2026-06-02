package com.codexapp.ui.message

import androidx.compose.runtime.Composable
import com.codexapp.model.MessageRole
import com.codexapp.model.ThreadMessage

@Composable
internal fun MessageCard(
    messages: List<ThreadMessage>,
    message: ThreadMessage,
    processMessages: List<ThreadMessage>,
    assistantActionsEnabled: Boolean,
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
            enableFinalActions = assistantActionsEnabled,
            onForkFromMessage = onForkFromMessage
        )
        MessageRole.SYSTEM -> SystemMessage(message, compactMode)
    }
}
