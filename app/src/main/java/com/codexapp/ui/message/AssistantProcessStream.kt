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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
internal fun AssistantProcessStream(
    messages: List<ThreadMessage>,
    compactMode: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 7.dp)
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
    val processRunning = remember(message.blocks) { message.blocks.isRunningProcessMessage() }

    Column(verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp)) {
        if (message.role == MessageRole.SYSTEM) {
            SystemMessage(message, compactMode)
            return@Column
        }

        if (cards.hasFileChangeCard) {
            FileChangeProcessRow(
                messageId = message.id,
                summary = cards.fileChangeSummary ?: "文件改动",
                entries = cards.fileChangeEntries,
                running = processRunning,
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
                running = processRunning,
                compactMode = compactMode
            )
        }

        var blockIndex = 0
        blocksLoop@ while (blockIndex < message.blocks.size) {
            when (val block = message.blocks[blockIndex]) {
                is MessageBlock.Status -> ProcessStatusLine(
                    text = block.value,
                    compactMode = compactMode
                )

                is MessageBlock.Reasoning -> {
                    if (block.value.isThinkingPlaceholder()) {
                        ProcessLine(
                            text = "正在思考",
                            visual = ProcessLineVisual(
                                kind = ProcessLineKind.THINKING,
                                running = true
                            ),
                            compactMode = compactMode
                        )
                    } else {
                        ProcessNarrativeText(
                            text = block.value,
                            visual = ProcessLineVisual(ProcessLineKind.THINKING),
                            compactMode = compactMode
                        )
                    }
                }

                is MessageBlock.Commentary -> ProcessNarrativeText(
                    text = block.value,
                    visual = ProcessLineVisual(ProcessLineKind.COMMENTARY),
                    compactMode = compactMode
                )

                is MessageBlock.Plan -> ProcessLineForBlock(
                    block = block,
                    compactMode = compactMode
                )

                is MessageBlock.ToolCall -> {
                    val detailCode = message.blocks.inlineToolDetailCode(blockIndex)
                    ToolCallProcessRow(
                        messageId = message.id,
                        blockIndex = blockIndex,
                        summary = block.value,
                        detailCode = detailCode,
                        compactMode = compactMode
                    )
                    blockIndex += if (detailCode != null) 2 else 1
                    continue@blocksLoop
                }

                is MessageBlock.WebSearch,
                is MessageBlock.Image,
                is MessageBlock.Collab,
                is MessageBlock.Review,
                is MessageBlock.Hook,
                is MessageBlock.Context,
                is MessageBlock.Text -> ProcessLineForBlock(
                    block = block,
                    compactMode = compactMode
                )

                is MessageBlock.Code -> if (!block.language.equals("shell", ignoreCase = true)) {
                    CodeBlock(
                        messageId = message.id,
                        blockIndex = blockIndex,
                        language = block.language,
                        value = block.value,
                        compactMode = compactMode
                    )
                }

                is MessageBlock.CommandSummary,
                is MessageBlock.CommandMeta,
                is MessageBlock.FileChangeSummary,
                is MessageBlock.FileChangeMeta,
                is MessageBlock.FileChangeDiff -> Unit
            }
            blockIndex += 1
        }
    }
}

@Composable
internal fun ProcessLineForBlock(
    block: MessageBlock,
    compactMode: Boolean
) {
    val text = block.processText() ?: return
    ProcessLine(
        text = text,
        visual = block.toProcessLineVisual(),
        compactMode = compactMode
    )
}

@Composable
internal fun ProcessStatusLine(
    text: String,
    compactMode: Boolean
) {
    ProcessLine(
        text = text,
        visual = inferStatusProcessVisual(text),
        compactMode = compactMode
    )
}

@Composable
private fun ProcessNarrativeText(
    text: String,
    visual: ProcessLineVisual,
    compactMode: Boolean
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compactMode) 1.dp else 2.dp)
    ) {
        ProcessLineLeadingVisual(
            visual = visual,
            compactMode = compactMode
        )
        Spacer(Modifier.width(6.dp))
        MarkdownText(
            text = text,
            expanded = true,
            onToggle = {},
            textColor = CodexTheme.colors.textPrimary,
            fontSize = if (compactMode) 11.sp else 12.sp,
            lineHeight = if (compactMode) 16.sp else 18.sp,
            maxCollapsedLines = Int.MAX_VALUE,
            wrapContent = true,
            modifier = Modifier.weight(1f)
        )
    }
}

internal fun List<MessageBlock>.hasMeaningfulReasoningText(): Boolean {
    return any { block ->
        block is MessageBlock.Reasoning &&
            block.value.trim().isNotBlank() &&
            !block.value.isThinkingPlaceholder()
    }
}

internal fun List<MessageBlock>.isRunningProcessMessage(): Boolean {
    return any { block ->
        when (block) {
            is MessageBlock.Status -> isRunningStatusText(block.value)
            is MessageBlock.Reasoning -> block.value.isThinkingPlaceholder()
            is MessageBlock.CommandSummary -> isRunningStatusText(block.value)
            is MessageBlock.FileChangeSummary -> isRunningStatusText(block.value)
            is MessageBlock.ToolCall -> isRunningStatusText(block.value)
            is MessageBlock.WebSearch -> isRunningStatusText(block.value)
            is MessageBlock.Image -> isRunningStatusText(block.value)
            is MessageBlock.Collab -> isRunningStatusText(block.value)
            is MessageBlock.Review -> isRunningStatusText(block.value)
            is MessageBlock.Hook -> isRunningStatusText(block.value)
            is MessageBlock.Context -> isRunningStatusText(block.value)
            else -> false
        }
    }
}

@Composable
private fun ToolCallProcessRow(
    messageId: String,
    blockIndex: Int,
    summary: String,
    detailCode: MessageBlock.Code?,
    compactMode: Boolean
) {
    val hasDetails = detailCode != null && detailCode.value.isNotBlank()
    var expanded by rememberSaveable(messageId + ":tool-process:$blockIndex") {
        mutableStateOf(false)
    }
    ProcessExpandableLine(
        visual = ProcessLineVisual(
            kind = ProcessLineKind.TOOL,
            running = isRunningStatusText(summary)
        ),
        text = summary,
        expanded = expanded,
        hasDetails = hasDetails,
        compactMode = compactMode,
        expandDescription = "展开工具详情",
        collapseDescription = "收起工具详情",
        onToggle = { expanded = !expanded }
    ) {
        if (detailCode != null) {
            CodeBlock(
                messageId = messageId,
                blockIndex = blockIndex,
                language = detailCode.language,
                value = detailCode.value,
                compactMode = compactMode
            )
        }
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
    running: Boolean,
    compactMode: Boolean
) {
    val hasDetails = metaLines.isNotEmpty() || outputValue.isNotBlank()
    var expanded by rememberSaveable(messageId + ":command-process:$blockIndex") {
        mutableStateOf(running && hasDetails)
    }
    LaunchedEffect(running, hasDetails, outputValue, metaLines) {
        if (running && hasDetails) {
            expanded = true
        }
    }
    ProcessExpandableLine(
        visual = ProcessLineVisual(
            kind = ProcessLineKind.COMMAND,
            running = running
        ),
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
                lineHeight = if (compactMode) 12.sp else 13.sp
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
    running: Boolean,
    compactMode: Boolean
) {
    val hasDetails = entries.isNotEmpty()
    var expanded by rememberSaveable(messageId + ":file-process") {
        mutableStateOf(running && hasDetails)
    }
    LaunchedEffect(running, hasDetails, entries) {
        if (running && hasDetails) {
            expanded = true
        }
    }
    ProcessExpandableLine(
        visual = ProcessLineVisual(
            kind = ProcessLineKind.FILE,
            running = running
        ),
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
        if (entries.size == 1) {
            FileChangeEntryDetails(
                entry = entries.first(),
                compactMode = compactMode
            )
        } else {
            entries.forEachIndexed { index, entry ->
                FileChangeRow(
                    messageId = messageId,
                    index = index,
                    entry = entry,
                    compactMode = compactMode
                )
            }
        }
    }
}

@Composable
private fun ProcessExpandableLine(
    visual: ProcessLineVisual,
    text: String,
    expanded: Boolean,
    hasDetails: Boolean,
    compactMode: Boolean,
    expandDescription: String,
    collapseDescription: String,
    onToggle: () -> Unit,
    details: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 4.dp)) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
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
                .padding(vertical = if (compactMode) 1.dp else 2.dp)
        ) {
            ProcessLineLeadingVisual(
                visual = visual,
                compactMode = compactMode
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compactMode) 10.sp else 11.sp,
                lineHeight = if (compactMode) 14.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (hasDetails) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
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
                    .padding(start = processContentStartPadding(compactMode)),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 4.dp)
            ) {
                details()
            }
        }
    }
}

@Composable
private fun ProcessLine(
    text: String,
    visual: ProcessLineVisual,
    compactMode: Boolean
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compactMode) 1.dp else 2.dp)
    ) {
        ProcessLineLeadingVisual(
            visual = visual,
            compactMode = compactMode
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = CodexTheme.colors.textSecondary,
            fontSize = if (compactMode) 10.sp else 11.sp,
            lineHeight = if (compactMode) 14.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProcessLineLeadingVisual(
    visual: ProcessLineVisual,
    compactMode: Boolean
) {
    if (visual.running) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (compactMode) 11.dp else 12.dp),
            color = CodexTheme.colors.textTertiary,
            strokeWidth = 1.6.dp
        )
        return
    }
    Icon(
        imageVector = visual.kind.icon,
        contentDescription = null,
        tint = visual.kind.tintColor(),
        modifier = Modifier.size(if (compactMode) 12.dp else 13.dp)
    )
}

private data class ProcessLineVisual(
    val kind: ProcessLineKind,
    val running: Boolean = false
)

private enum class ProcessLineKind(val icon: ImageVector) {
    THINKING(Icons.Filled.Info),
    COMMENTARY(Icons.Filled.Bolt),
    COMMAND(Icons.Filled.Code),
    FILE(Icons.Filled.Description),
    PLAN(Icons.Filled.AutoFixHigh),
    TOOL(Icons.Filled.Bolt),
    SEARCH(Icons.Filled.Search),
    IMAGE(Icons.Filled.Info),
    COLLAB(Icons.Filled.Bolt),
    REVIEW(Icons.Filled.Warning),
    HOOK(Icons.Filled.Bolt),
    CONTEXT(Icons.Filled.Archive),
    INFO(Icons.Filled.Info),
    SUCCESS(Icons.Filled.CheckCircle),
    WARNING(Icons.Filled.Warning)
}

@Composable
private fun ProcessLineKind.tintColor(): Color {
    return when (this) {
        ProcessLineKind.WARNING -> CodexTheme.colors.danger
        else -> CodexTheme.colors.textTertiary
    }
}

private fun MessageBlock.toProcessLineVisual(): ProcessLineVisual {
    return when (this) {
        is MessageBlock.Status -> inferStatusProcessVisual(value)
        is MessageBlock.Plan -> ProcessLineVisual(ProcessLineKind.PLAN, isRunningStatusText(value))
        is MessageBlock.ToolCall -> ProcessLineVisual(ProcessLineKind.TOOL, isRunningStatusText(value))
        is MessageBlock.WebSearch -> ProcessLineVisual(ProcessLineKind.SEARCH, isRunningStatusText(value))
        is MessageBlock.Image -> ProcessLineVisual(ProcessLineKind.IMAGE, isRunningStatusText(value))
        is MessageBlock.Collab -> ProcessLineVisual(ProcessLineKind.COLLAB, isRunningStatusText(value))
        is MessageBlock.Review -> ProcessLineVisual(ProcessLineKind.REVIEW, isRunningStatusText(value))
        is MessageBlock.Hook -> ProcessLineVisual(ProcessLineKind.HOOK, isRunningStatusText(value))
        is MessageBlock.Context -> ProcessLineVisual(ProcessLineKind.CONTEXT, isRunningStatusText(value))
        is MessageBlock.Text -> inferStatusProcessVisual(value)
        else -> ProcessLineVisual(ProcessLineKind.INFO)
    }
}

private fun inferStatusProcessVisual(text: String): ProcessLineVisual {
    if (isRunningStatusText(text)) {
        return ProcessLineVisual(
            kind = when {
                text.contains("搜索", ignoreCase = true) -> ProcessLineKind.SEARCH
                text.contains("review", ignoreCase = true) -> ProcessLineKind.REVIEW
                else -> ProcessLineKind.INFO
            },
            running = true
        )
    }
    return when {
        text.contains("失败") || text.contains("error", ignoreCase = true) -> {
            ProcessLineVisual(ProcessLineKind.WARNING)
        }

        text.contains("搜索", ignoreCase = true) -> {
            ProcessLineVisual(ProcessLineKind.SEARCH)
        }

        text.startsWith("已") || text.contains("completed", ignoreCase = true) -> {
            ProcessLineVisual(ProcessLineKind.SUCCESS)
        }

        else -> ProcessLineVisual(ProcessLineKind.INFO)
    }
}

private fun MessageBlock.processText(): String? {
    return when (this) {
        is MessageBlock.Status -> value
        is MessageBlock.Plan -> value
        is MessageBlock.ToolCall -> value
        is MessageBlock.WebSearch -> value
        is MessageBlock.Image -> value
        is MessageBlock.Collab -> value
        is MessageBlock.Review -> value
        is MessageBlock.Hook -> value
        is MessageBlock.Context -> value
        is MessageBlock.Text -> value
        else -> null
    }?.trim()?.takeIf(String::isNotEmpty)
}

private fun List<MessageBlock>.inlineToolDetailCode(index: Int): MessageBlock.Code? {
    val next = getOrNull(index + 1) as? MessageBlock.Code ?: return null
    return next.takeUnless { it.language.equals("shell", ignoreCase = true) }
}

private fun String.isThinkingPlaceholder(): Boolean {
    val normalized = trim()
    return normalized.isBlank() ||
        normalized == "正在思考" ||
        normalized == "思考中"
}

private fun processContentStartPadding(compactMode: Boolean) = if (compactMode) 18.dp else 19.dp
