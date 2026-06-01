package com.codex.mobile.ui.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun AssistantMessage(
    message: ThreadMessage,
    compactMode: Boolean,
    messageIndex: Int,
    onCopyMessage: (String) -> Unit
) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var reasoningExpanded by rememberSaveable(message.id + ":reasoning") { mutableStateOf(false) }
    val cards = remember(message.blocks) { deriveAssistantMessageCards(message.blocks) }
    val copyText = remember(message.blocks) { message.copyableText() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)
    ) {
        if (copyText.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { onCopyMessage(copyText) },
                    modifier = Modifier
                        .size(if (compactMode) 28.dp else 32.dp)
                        .semantics { contentDescription = "复制助手消息" }
                        .testTag("assistant_message_copy_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = CodexTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (cards.hasFileChangeCard) {
            FileChangeCard(
                messageId = message.id,
                summary = cards.fileChangeSummary ?: "文件改动",
                entries = cards.fileChangeEntries,
                compactMode = compactMode
            )
        }

        if (cards.hasCommandCard) {
            CommandExecutionCard(
                messageId = message.id,
                blockIndex = messageIndex,
                summary = cards.commandSummary ?: if (cards.commandOutput != null) "命令执行中" else "命令状态",
                metaLines = cards.commandMetaLines,
                outputLanguage = cards.commandOutput?.language ?: "shell",
                outputValue = cards.commandOutput?.value.orEmpty(),
                compactMode = compactMode
            )
        }

        message.blocks.forEach { block ->
            when (block) {
                is MessageBlock.Text -> ExpandableText(
                    text = block.value,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    textColor = CodexTheme.colors.textPrimary,
                    fontSize = if (compactMode) 12.sp else 13.sp,
                    lineHeight = if (compactMode) 17.sp else 19.sp,
                    maxCollapsedLines = if (compactMode) 6 else 8
                )

                is MessageBlock.Code -> if (!block.language.equals("shell", ignoreCase = true)) {
                    CodeBlock(
                        messageId = message.id,
                        blockIndex = messageIndex,
                        language = block.language,
                        value = block.value,
                        compactMode = compactMode
                    )
                }

                is MessageBlock.Status -> InlineStatus(block.value, compactMode)
                is MessageBlock.Reasoning -> ReasoningBlock(
                    text = block.value,
                    expanded = reasoningExpanded,
                    onToggle = { reasoningExpanded = !reasoningExpanded },
                    compactMode = compactMode
                )

                is MessageBlock.CommandSummary,
                is MessageBlock.CommandMeta,
                is MessageBlock.FileChangeSummary,
                is MessageBlock.FileChangeMeta,
                is MessageBlock.FileChangeDiff -> Unit
            }
        }
    }
}
