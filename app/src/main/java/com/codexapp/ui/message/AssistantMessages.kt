package com.codexapp.ui.message

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.ThreadMessage
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun AssistantMessage(
    message: ThreadMessage,
    processMessages: List<ThreadMessage>,
    compactMode: Boolean,
    messageIndex: Int,
    showActions: Boolean,
    enableFinalActions: Boolean,
    onForkFromMessage: (Int) -> Unit
) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var reasoningExpanded by rememberSaveable(message.id + ":reasoning") { mutableStateOf(false) }
    var processExpanded by rememberSaveable(message.id + ":process") { mutableStateOf(false) }
    val cards = remember(message.blocks) { deriveAssistantMessageCards(message.blocks) }
    val forkNumTurns = message.forkNumTurns
    val finalText = remember(message.blocks) { message.assistantFinalText() }
    val context = LocalContext.current
    val canCopy = showActions && enableFinalActions && message.isFinal && finalText.isNotBlank()
    val canFork = showActions && enableFinalActions && message.isFinal && forkNumTurns != null
    val showFooterActions = showActions && (canCopy || canFork || message.durationMs != null)
    val inlineProcessBlocks = remember(message.blocks, finalText) {
        if (finalText.isBlank()) emptyList() else message.blocks.filter { it is MessageBlock.Reasoning || it is MessageBlock.Status }
    }
    val bodyBlocks = remember(message.blocks, finalText) {
        if (finalText.isBlank()) message.blocks else message.blocks.filterNot { it is MessageBlock.Reasoning || it is MessageBlock.Status }
    }
    val stableProcessMessages = remember(processMessages, inlineProcessBlocks, message.id) {
        if (inlineProcessBlocks.isEmpty()) {
            processMessages
        } else {
            processMessages + ThreadMessage(
                id = "${message.id}:inline-process",
                role = MessageRole.ASSISTANT,
                blocks = inlineProcessBlocks
            )
        }
    }
    val reservesProcessSlot = finalText.isNotBlank() || stableProcessMessages.isNotEmpty()

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)
        ) {
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

            bodyBlocks.forEach { block ->
                when (block) {
                    is MessageBlock.Text -> ExpandableText(
                        text = block.value,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        textColor = CodexTheme.colors.textPrimary,
                        fontSize = if (compactMode) 12.sp else 13.sp,
                        lineHeight = if (compactMode) 17.sp else 19.sp,
                        maxCollapsedLines = if (compactMode) 6 else 8,
                        collapseEnabled = false
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

            if (reservesProcessSlot) {
                StableProcessSlot(
                    messages = stableProcessMessages,
                    expanded = processExpanded,
                    onToggle = { processExpanded = !processExpanded },
                    compactMode = compactMode
                )
            }

            if (showFooterActions) {
                AssistantTurnFooterActions(
                    messageId = message.id,
                    durationMs = message.durationMs,
                    canCopy = canCopy,
                    canFork = canFork,
                    compactMode = compactMode,
                    onCopyFinalText = { context.copyTextToClipboard("Codex 回复", finalText) },
                    onForkFromMessage = {
                        if (forkNumTurns != null) {
                            onForkFromMessage(forkNumTurns)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StableProcessSlot(
    messages: List<ThreadMessage>,
    expanded: Boolean,
    onToggle: () -> Unit,
    compactMode: Boolean
) {
    if (messages.isEmpty()) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(processSlotMinHeight(compactMode))
                .testTag("turn_process_reserved_slot")
        )
        return
    }
    TurnProcessBlock(
        messages = messages,
        expanded = expanded,
        onToggle = onToggle,
        compactMode = compactMode
    )
}

@Composable
private fun TurnProcessBlock(
    messages: List<ThreadMessage>,
    expanded: Boolean,
    onToggle: () -> Unit,
    compactMode: Boolean
) {
    val count = messages.sumOf { it.blocks.size }.coerceAtLeast(messages.size)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                    .clickable(onClick = onToggle)
                    .clearAndSetSemantics { contentDescription = if (expanded) "收起处理过程" else "展开处理过程" }
                    .padding(horizontal = 2.dp, vertical = 3.dp)
                    .testTag("turn_process_toggle")
            ) {
                Text(
                    text = buildProcessHeaderTitle(count),
                    color = CodexTheme.colors.textSecondary,
                    fontSize = if (compactMode) 10.sp else 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (expanded) {
            messages.forEachIndexed { index, processMessage ->
                AssistantProcessMessage(
                    message = processMessage,
                    compactMode = compactMode,
                    messageIndex = index
                )
            }
        }
    }
}

private fun processSlotMinHeight(compactMode: Boolean) = if (compactMode) 20.dp else 22.dp

@Composable
private fun AssistantTurnFooterActions(
    messageId: String,
    durationMs: Long?,
    canCopy: Boolean,
    canFork: Boolean,
    compactMode: Boolean,
    onCopyFinalText: () -> Unit,
    onForkFromMessage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compactMode) 1.dp else 2.dp)
            .testTag("assistant_turn_footer_$messageId"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canCopy) {
            IconButton(
                onClick = onCopyFinalText,
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = "复制文本" }
                    .testTag("assistant_turn_copy_$messageId")
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        if (canFork) {
            IconButton(
                onClick = onForkFromMessage,
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = "从此处分叉" }
                    .testTag("assistant_turn_fork_$messageId")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CallSplit,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        buildAssistantFooterDuration(durationMs)?.let { duration ->
            Text(
                text = duration,
                color = CodexTheme.colors.textTertiary,
                fontSize = if (compactMode) 11.sp else 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

internal fun buildProcessHeaderTitle(count: Int): String {
    return "已处理 $count 项"
}

internal fun buildAssistantFooterDuration(durationMs: Long?): String? {
    val duration = durationMs?.takeIf { it > 0L }?.let { formatDuration(it) }
    return duration
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(1L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

private fun ThreadMessage.assistantFinalText(): String {
    return blocks.filterIsInstance<MessageBlock.Text>().joinToString("\n") { it.value }.trim()
}

private fun Context.copyTextToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun AssistantProcessMessage(
    message: ThreadMessage,
    compactMode: Boolean,
    messageIndex: Int
) {
    val cards = remember(message.blocks) { deriveAssistantMessageCards(message.blocks) }
    var reasoningExpanded by rememberSaveable(message.id + ":process-reasoning") { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)) {
        if (message.role == com.codexapp.model.MessageRole.SYSTEM) {
            SystemMessage(message, compactMode)
            return@Column
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
                is MessageBlock.Status -> InlineStatus(block.value, compactMode)
                is MessageBlock.Reasoning -> ReasoningBlock(
                    text = block.value,
                    expanded = reasoningExpanded,
                    onToggle = { reasoningExpanded = !reasoningExpanded },
                    compactMode = compactMode
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
                is MessageBlock.Text -> InlineStatus(block.value, compactMode)
                is MessageBlock.CommandSummary,
                is MessageBlock.CommandMeta,
                is MessageBlock.FileChangeSummary,
                is MessageBlock.FileChangeMeta,
                is MessageBlock.FileChangeDiff -> Unit
            }
        }
    }
}
