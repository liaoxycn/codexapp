package com.codexapp.ui.message

import androidx.compose.runtime.Composable
import com.codexapp.model.MessageBlock
import com.codexapp.model.ThreadMessage

@Composable
internal fun SystemMessage(message: ThreadMessage, compactMode: Boolean) {
    val block = message.blocks.firstNotNullOfOrNull { candidate ->
        when (candidate) {
            is MessageBlock.Status,
            is MessageBlock.Review,
            is MessageBlock.Hook,
            is MessageBlock.Context,
            is MessageBlock.ToolCall,
            is MessageBlock.WebSearch,
            is MessageBlock.Image,
            is MessageBlock.Collab,
            is MessageBlock.Plan,
            is MessageBlock.Text -> candidate

            is MessageBlock.Commentary,
            is MessageBlock.Reasoning,
            is MessageBlock.Code,
            is MessageBlock.CommandSummary,
            is MessageBlock.CommandMeta,
            is MessageBlock.FileChangeSummary,
            is MessageBlock.FileChangeMeta,
            is MessageBlock.FileChangeDiff -> null
        }
    } ?: return

    ProcessLineForBlock(
        block = block,
        compactMode = compactMode
    )
}

@Composable
internal fun InlineStatus(text: String, compactMode: Boolean = false) {
    ProcessStatusLine(
        text = text,
        compactMode = compactMode
    )
}

internal fun isRunningStatusText(text: String): Boolean {
    return text.startsWith("正在") ||
        text.contains("进行中", ignoreCase = true) ||
        text.contains("inProgress", ignoreCase = true) ||
        text.contains("running", ignoreCase = true) ||
        text.contains("started", ignoreCase = true)
}
