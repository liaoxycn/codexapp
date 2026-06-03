package com.codexapp.ui.message

import androidx.compose.runtime.Composable
import com.codexapp.model.MessageRole
import com.codexapp.model.ThreadMessage

@Composable
internal fun MessageCard(
    message: ThreadMessage,
    processMessages: List<ThreadMessage>,
    assistantTurnRunning: Boolean,
    showUserActions: Boolean,
    showAssistantActions: Boolean,
    assistantActionsEnabled: Boolean,
    preferPlainText: Boolean,
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
            showActions = showUserActions,
            onEditAndResend = onEditUserMessage,
            onResend = onResendUserMessage
        )
        MessageRole.ASSISTANT -> AssistantMessage(
            message = message,
            processMessages = processMessages,
            isRunning = assistantTurnRunning,
            compactMode = compactMode,
            messageIndex = messageIndex,
            showActions = showAssistantActions,
            enableFinalActions = assistantActionsEnabled,
            preferPlainText = preferPlainText,
            onForkFromMessage = onForkFromMessage
        )
        MessageRole.SYSTEM -> SystemMessage(message, compactMode)
    }
}
