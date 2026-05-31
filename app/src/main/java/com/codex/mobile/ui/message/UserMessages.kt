package com.codex.mobile.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun UserMessage(message: ThreadMessage, compactMode: Boolean) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            modifier = Modifier
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
    }
}
