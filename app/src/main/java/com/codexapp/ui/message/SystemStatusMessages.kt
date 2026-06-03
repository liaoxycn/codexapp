package com.codexapp.ui.message

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.model.MessageBlock
import com.codexapp.model.ThreadMessage
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun SystemMessage(message: ThreadMessage, compactMode: Boolean) {
    val text = message.blocks.firstNotNullOfOrNull { block ->
        when (block) {
            is MessageBlock.Status -> block.value
            is MessageBlock.Review -> block.value
            is MessageBlock.Hook -> block.value
            is MessageBlock.Context -> block.value
            is MessageBlock.ToolCall -> block.value
            is MessageBlock.WebSearch -> block.value
            is MessageBlock.Image -> block.value
            is MessageBlock.Collab -> block.value
            is MessageBlock.Plan -> block.value
            is MessageBlock.Commentary,
            is MessageBlock.Reasoning,
            is MessageBlock.Text,
            is MessageBlock.Code,
            is MessageBlock.CommandSummary,
            is MessageBlock.CommandMeta,
            is MessageBlock.FileChangeSummary,
            is MessageBlock.FileChangeMeta,
            is MessageBlock.FileChangeDiff -> null
        }
    } ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        InlineStatus(text, compactMode)
    }
}

@Composable
internal fun InlineStatus(text: String, compactMode: Boolean = false) {
    val running = isRunningStatusText(text)
    val searched = text.startsWith("正在搜索网页") || text.startsWith("已搜索网页")
    val completed = text.startsWith("已")
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (running) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compactMode) 11.dp else 12.dp),
                color = CodexTheme.colors.textTertiary,
                strokeWidth = 1.6.dp
            )
        } else {
            Icon(
                imageVector = when {
                    searched -> Icons.Filled.Search
                    completed -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.Info
                },
                contentDescription = null,
                tint = CodexTheme.colors.textTertiary,
                modifier = Modifier.size(if (compactMode) 12.dp else 13.dp)
            )
        }
        Spacer(Modifier.width(5.dp))
        SelectionContainer {
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
}

private fun isRunningStatusText(text: String): Boolean {
    return text.startsWith("正在") ||
        text.contains("进行中", ignoreCase = true) ||
        text.contains("inProgress", ignoreCase = true) ||
        text.contains("running", ignoreCase = true)
}
