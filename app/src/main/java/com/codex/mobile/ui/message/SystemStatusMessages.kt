package com.codex.mobile.ui.message

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun SystemMessage(message: ThreadMessage, compactMode: Boolean) {
    val text = message.blocks.filterIsInstance<MessageBlock.Status>().firstOrNull()?.value ?: return
    InlineStatus(text, compactMode)
}

@Composable
internal fun InlineStatus(text: String, compactMode: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = CodexTheme.colors.textTertiary,
            modifier = Modifier.size(if (compactMode) 12.dp else 13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = CodexTheme.colors.textSecondary,
            fontSize = if (compactMode) 10.sp else 11.sp,
            lineHeight = if (compactMode) 14.sp else 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
