package com.codex.mobile.ui.message

import androidx.compose.runtime.Composable
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.ThreadMessage

@Composable
internal fun MessageCard(
    message: ThreadMessage,
    compactMode: Boolean,
    messageIndex: Int,
    onEditUserMessage: (String) -> Unit,
    onResendUserMessage: (String) -> Unit
) {
    when (message.role) {
        MessageRole.USER -> UserMessage(
            message = message,
            compactMode = compactMode,
            onEditAndResend = onEditUserMessage,
            onResend = onResendUserMessage
        )
        MessageRole.ASSISTANT -> AssistantMessage(message, compactMode, messageIndex)
        MessageRole.SYSTEM -> SystemMessage(message, compactMode)
    }
}
