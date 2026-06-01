package com.codex.mobile.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.ui.theme.CodexTheme
import kotlinx.coroutines.delay

@Composable
internal fun UserMessage(
    message: ThreadMessage,
    compactMode: Boolean,
    showActions: Boolean,
    onEditAndResend: (String, Int) -> Unit,
    onResend: (String, Int) -> Unit
) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var menuExpanded by rememberSaveable(message.id + ":menu") { mutableStateOf(false) }
    var pendingAction by rememberSaveable(message.id + ":pending") { mutableStateOf<String?>(null) }
    val messageText = message.userEditableText()
    val rollbackNumTurns = message.rollbackNumTurns
    LaunchedEffect(pendingAction) {
        if (pendingAction != null) {
            delay(3500L)
            pendingAction = null
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (showActions && messageText.isNotBlank() && rollbackNumTurns != null) 22.dp else 0.dp)
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(if (compactMode) 14.dp else 16.dp))
                .background(CodexTheme.colors.userBubble)
                .padding(horizontal = if (compactMode) 10.dp else 11.dp, vertical = if (compactMode) 8.dp else 9.dp)
        ) {
            message.blocks.forEach { block ->
                if (block is MessageBlock.Text) {
                    MarkdownText(
                        text = block.value,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        textColor = CodexTheme.colors.userBubbleText,
                        fontSize = if (compactMode) 12.sp else 13.sp,
                        lineHeight = if (compactMode) 17.sp else 18.sp,
                        maxCollapsedLines = if (compactMode) 4 else 5,
                        wrapContent = true
                    )
                }
            }
        }
        if (showActions && messageText.isNotBlank() && rollbackNumTurns != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
            ) {
                IconButton(
                    onClick = { if (pendingAction == null) menuExpanded = true },
                    enabled = pendingAction == null,
                    modifier = Modifier
                        .size(24.dp)
                        .semantics { contentDescription = "用户消息操作" }
                        .testTag("user_message_more_${message.id}")
                ) {
                    if (pendingAction != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = CodexTheme.colors.textSecondary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = null,
                            tint = CodexTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        onClick = {
                            menuExpanded = false
                            onEditAndResend(messageText, rollbackNumTurns)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = CodexTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("编辑后重发")
                    }
                    DropdownMenuItem(
                        onClick = {
                            menuExpanded = false
                            pendingAction = "resend"
                            onResend(messageText, rollbackNumTurns)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = CodexTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("重发")
                    }
                }
            }
        }
    }
}

internal fun ThreadMessage.userEditableText(): String {
    return blocks.filterIsInstance<MessageBlock.Text>().joinToString("\n") { it.value }.trim()
}
