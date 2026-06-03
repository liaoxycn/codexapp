package com.codexapp.ui.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.ThreadMessage
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun AssistantProcessStream(
    messages: List<ThreadMessage>,
    compactMode: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)
    ) {
        messages.forEachIndexed { index, message ->
            AssistantProcessMessage(
                message = message,
                compactMode = compactMode,
                messageIndex = index
            )
        }
    }
}

@Composable
private fun AssistantProcessMessage(
    message: ThreadMessage,
    compactMode: Boolean,
    messageIndex: Int
) {
    val cards = remember(message.blocks) { deriveAssistantMessageCards(message.blocks) }
    val hasReasoningText = remember(message.blocks) { message.blocks.hasMeaningfulReasoningText() }
    var reasoningExpanded by rememberSaveable(message.id + ":process-reasoning") {
        mutableStateOf(hasReasoningText)
    }
    LaunchedEffect(hasReasoningText) {
        if (hasReasoningText) {
            reasoningExpanded = true
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)) {
        if (message.role == MessageRole.SYSTEM) {
            SystemMessage(message, compactMode)
            return@Column
        }
        if (cards.hasFileChangeCard) {
            FileChangeProcessRow(
                messageId = message.id,
                summary = cards.fileChangeSummary ?: "文件改动",
                entries = cards.fileChangeEntries,
                compactMode = compactMode
            )
        }
        if (cards.hasCommandCard) {
            CommandProcessRow(
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
                is MessageBlock.Commentary -> CommentaryProcessText(block.value, compactMode)
                is MessageBlock.Plan -> InlineStatus(block.value, compactMode)
                is MessageBlock.ToolCall -> InlineStatus(block.value, compactMode)
                is MessageBlock.WebSearch -> InlineStatus(block.value, compactMode)
                is MessageBlock.Image -> InlineStatus(block.value, compactMode)
                is MessageBlock.Collab -> InlineStatus(block.value, compactMode)
                is MessageBlock.Review -> InlineStatus(block.value, compactMode)
                is MessageBlock.Hook -> InlineStatus(block.value, compactMode)
                is MessageBlock.Context -> InlineStatus(block.value, compactMode)
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

@Composable
private fun CommentaryProcessText(text: String, compactMode: Boolean) {
    MarkdownText(
        text = text,
        expanded = true,
        onToggle = {},
        textColor = CodexTheme.colors.textPrimary,
        fontSize = if (compactMode) 11.sp else 12.sp,
        lineHeight = if (compactMode) 16.sp else 18.sp,
        maxCollapsedLines = Int.MAX_VALUE,
        wrapContent = true
    )
}

internal fun List<MessageBlock>.hasMeaningfulReasoningText(): Boolean {
    return any { block ->
        block is MessageBlock.Reasoning &&
            block.value.trim().isNotBlank() &&
            block.value.trim() != "正在思考" &&
            block.value.trim() != "思考中"
    }
}

@Composable
private fun CommandProcessRow(
    messageId: String,
    blockIndex: Int,
    summary: String,
    metaLines: List<String>,
    outputLanguage: String,
    outputValue: String,
    compactMode: Boolean
) {
    val hasDetails = metaLines.isNotEmpty() || outputValue.isNotBlank()
    var expanded by rememberSaveable(messageId + ":command-process:$blockIndex") { mutableStateOf(false) }
    ProcessExpandableLine(
        icon = ProcessLineIcon.COMMAND,
        text = summary,
        expanded = expanded,
        hasDetails = hasDetails,
        compactMode = compactMode,
        expandDescription = "展开命令详情",
        collapseDescription = "收起命令详情",
        onToggle = { expanded = !expanded }
    ) {
        metaLines.forEach { metaLine ->
            Text(
                text = metaLine,
                color = CodexTheme.colors.textTertiary,
                fontSize = if (compactMode) 9.sp else 10.sp,
                lineHeight = if (compactMode) 12.sp else 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = processDetailsStartPadding(compactMode))
            )
        }
        if (outputValue.isNotBlank()) {
            CommandOutputBlock(
                messageId = messageId,
                blockIndex = blockIndex,
                language = outputLanguage,
                value = outputValue,
                compactMode = compactMode
            )
        }
    }
}

@Composable
private fun FileChangeProcessRow(
    messageId: String,
    summary: String,
    entries: List<FileChangeEntry>,
    compactMode: Boolean
) {
    val hasDetails = entries.isNotEmpty()
    var expanded by rememberSaveable(messageId + ":file-process") { mutableStateOf(false) }
    ProcessExpandableLine(
        icon = ProcessLineIcon.FILE,
        text = when {
            entries.isEmpty() -> summary
            entries.size == 1 -> entries.first().label
            else -> summary
        },
        expanded = expanded,
        hasDetails = hasDetails,
        compactMode = compactMode,
        expandDescription = if (entries.size == 1) {
            fileChangeDiffContentDescription(false, entries.first().label)
        } else {
            "展开文件改动"
        },
        collapseDescription = if (entries.size == 1) {
            fileChangeDiffContentDescription(true, entries.first().label)
        } else {
            "收起文件改动"
        },
        onToggle = { expanded = !expanded }
    ) {
        entries.forEachIndexed { index, entry ->
            FileChangeRow(
                messageId = messageId,
                index = index,
                entry = entry,
                compactMode = compactMode,
                forceExpanded = entries.size == 1
            )
        }
    }
}

@Composable
private fun ProcessExpandableLine(
    icon: ProcessLineIcon,
    text: String,
    expanded: Boolean,
    hasDetails: Boolean,
    compactMode: Boolean,
    expandDescription: String,
    collapseDescription: String,
    onToggle: () -> Unit,
    details: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .then(
                    if (hasDetails) {
                        Modifier
                            .clickable(onClick = onToggle)
                            .semantics {
                                contentDescription = if (expanded) collapseDescription else expandDescription
                            }
                    } else {
                        Modifier.semantics { contentDescription = text }
                    }
                )
                .padding(horizontal = 2.dp, vertical = if (compactMode) 2.dp else 3.dp)
        ) {
            Icon(
                imageVector = when (icon) {
                    ProcessLineIcon.COMMAND -> Icons.Filled.Code
                    ProcessLineIcon.FILE -> Icons.Filled.Description
                },
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
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (hasDetails) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (expanded && hasDetails) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = processDetailsStartPadding(compactMode)),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 3.dp)
            ) {
                details()
            }
        }
    }
}

private enum class ProcessLineIcon {
    COMMAND,
    FILE
}

private fun processDetailsStartPadding(compactMode: Boolean) = if (compactMode) 17.dp else 18.dp
