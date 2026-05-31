package com.codex.mobile.ui.message

import androidx.compose.runtime.Composable
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.ThreadMessage

@Composable
internal fun MessageCard(message: ThreadMessage, compactMode: Boolean, messageIndex: Int) {
    when (message.role) {
        MessageRole.USER -> UserMessage(message, compactMode)
        MessageRole.ASSISTANT -> AssistantMessage(message, compactMode, messageIndex)
        MessageRole.SYSTEM -> SystemMessage(message, compactMode)
    }
}
