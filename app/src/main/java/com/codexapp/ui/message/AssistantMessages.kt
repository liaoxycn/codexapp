package com.codexapp.ui.message

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.model.MessageBlock
import com.codexapp.model.ThreadMessage
import com.codexapp.ui.theme.CodexTheme
import kotlinx.coroutines.delay

@Composable
internal fun AssistantMessage(
    message: ThreadMessage,
    processMessages: List<ThreadMessage>,
    isRunning: Boolean,
    compactMode: Boolean,
    messageIndex: Int,
    showActions: Boolean,
    enableFinalActions: Boolean,
    preferPlainText: Boolean,
    onForkFromMessage: (Int) -> Unit
) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var reasoningExpanded by rememberSaveable(message.id + ":reasoning") { mutableStateOf(false) }
    val context = LocalContext.current
    val turn = remember(
        message,
        processMessages,
        isRunning,
        showActions,
        enableFinalActions,
        preferPlainText
    ) {
        buildAssistantTurnUiModel(
            message = message,
            processMessages = processMessages,
            isRunning = isRunning,
            showActions = showActions,
            enableFinalActions = enableFinalActions,
            preferPlainText = preferPlainText
        )
    }
    val autoExpandProcess = turn.isRunning || (turn.hasProcess && turn.bodyBlocks.isEmpty())
    var processExpanded by rememberSaveable(message.id + ":process") { mutableStateOf(autoExpandProcess) }
    LaunchedEffect(turn.isRunning, turn.hasProcess, message.isFinal, turn.bodyBlocks.isEmpty()) {
        processExpanded = autoExpandProcess
    }
    val processedHeader = rememberAssistantProcessedHeader(
        durationMs = turn.durationMs,
        isRunning = turn.isRunning
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)
        ) {
            AssistantProcessedHeader(
                text = processedHeader,
                expanded = processExpanded,
                hasProcess = turn.hasProcess,
                compactMode = compactMode,
                onToggle = {
                    if (turn.hasProcess) {
                        processExpanded = !processExpanded
                    }
                }
            )

            AssistantProcessSlot(
                messages = turn.processMessages,
                visible = processExpanded && turn.hasProcess,
                compactMode = compactMode
            )

            if (turn.bodyBlocks.isNotEmpty()) {
                StableAssistantBodySlot(
                    blocks = turn.bodyBlocks,
                    expanded = expanded,
                    reasoningExpanded = reasoningExpanded,
                    onToggleExpanded = { expanded = !expanded },
                    onToggleReasoning = { reasoningExpanded = !reasoningExpanded },
                    renderMarkdown = !turn.preferPlainText,
                    compactMode = compactMode,
                    messageId = turn.id,
                    messageIndex = messageIndex
                )
            }

            AssistantTurnFooterActions(
                messageId = turn.id,
                canShowActions = turn.showFooterActions,
                canCopy = turn.canCopy,
                canFork = turn.canFork,
                compactMode = compactMode,
                onCopyFinalText = { context.copyTextToClipboard("Codex 回复", turn.finalText) },
                onForkFromMessage = {
                    if (turn.forkNumTurns != null) {
                        onForkFromMessage(turn.forkNumTurns)
                    }
                }
            )
        }
    }
}

@Composable
private fun AssistantProcessedHeader(
    text: String,
    expanded: Boolean,
    hasProcess: Boolean,
    compactMode: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compactMode) 22.dp else 26.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                .clickable(enabled = hasProcess, onClick = onToggle)
                .semantics {
                    contentDescription = when {
                        !hasProcess -> text
                        expanded -> "$text，收起处理过程"
                        else -> "$text，展开处理过程"
                    }
                }
                .testTag("assistant_processed_header"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compactMode) 11.sp else 12.sp,
                fontWeight = FontWeight.Medium,
            )
            if (hasProcess) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CodexTheme.colors.border)
        )
    }
}

@Composable
private fun AssistantProcessSlot(
    messages: List<ThreadMessage>,
    visible: Boolean,
    compactMode: Boolean
) {
    if (!visible) {
        return
    }
    AssistantProcessStream(
        messages = messages,
        compactMode = compactMode
    )
}

@Composable
private fun StableAssistantBodySlot(
    blocks: List<MessageBlock>,
    expanded: Boolean,
    reasoningExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleReasoning: () -> Unit,
    renderMarkdown: Boolean,
    compactMode: Boolean,
    messageId: String,
    messageIndex: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = bodySlotMinHeight(compactMode))
            .testTag("assistant_body_slot_$messageId"),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MessageBlock.Text -> ExpandableText(
                    text = block.value,
                    expanded = expanded,
                    onToggle = onToggleExpanded,
                    textColor = CodexTheme.colors.textPrimary,
                    fontSize = if (compactMode) 12.sp else 13.sp,
                    lineHeight = if (compactMode) 17.sp else 19.sp,
                    maxCollapsedLines = if (compactMode) 6 else 8,
                    collapseEnabled = false,
                    parseMarkdown = renderMarkdown
                )

                is MessageBlock.Code -> if (!block.language.equals("shell", ignoreCase = true)) {
                    CodeBlock(
                        messageId = messageId,
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
                    onToggle = onToggleReasoning,
                    compactMode = compactMode
                )

                is MessageBlock.Commentary,
                is MessageBlock.Plan,
                is MessageBlock.ToolCall,
                is MessageBlock.WebSearch,
                is MessageBlock.Image,
                is MessageBlock.Collab,
                is MessageBlock.Review,
                is MessageBlock.Hook,
                is MessageBlock.Context,
                is MessageBlock.CommandSummary,
                is MessageBlock.CommandMeta,
                is MessageBlock.FileChangeSummary,
                is MessageBlock.FileChangeMeta,
                is MessageBlock.FileChangeDiff -> Unit
            }
        }
    }
}

private fun bodySlotMinHeight(compactMode: Boolean) = if (compactMode) 17.dp else 19.dp

@Composable
private fun AssistantTurnFooterActions(
    messageId: String,
    canShowActions: Boolean,
    canCopy: Boolean,
    canFork: Boolean,
    compactMode: Boolean,
    onCopyFinalText: () -> Unit,
    onForkFromMessage: () -> Unit
) {
    if (!canShowActions || (!canCopy && !canFork)) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compactMode) 25.dp else 28.dp)
            .padding(top = if (compactMode) 1.dp else 2.dp)
            .testTag("assistant_turn_footer_$messageId"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canShowActions && canCopy) {
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
        if (canShowActions && canFork) {
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
    }
}

internal fun buildProcessedHeader(durationMs: Long?): String? {
    val duration = durationMs?.takeIf { it > 0L }?.let { formatDuration(it) }
    return duration?.let { "已处理 $it" }
}

internal fun buildProcessHeaderTitle(count: Int): String {
    return "已处理 $count 项"
}

@Composable
private fun rememberAssistantProcessedHeader(durationMs: Long?, isRunning: Boolean): String {
    var localElapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(isRunning, durationMs) {
        if (!isRunning || durationMs != null) {
            return@LaunchedEffect
        }
        localElapsedSeconds = 0L
        while (true) {
            delay(1000L)
            localElapsedSeconds += 1L
        }
    }
    val effectiveMs = durationMs ?: localElapsedSeconds * 1000L
    return "已处理 ${formatDurationAllowZero(effectiveMs)}"
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(1L)
    return formatDurationSeconds(totalSeconds)
}

private fun formatDurationAllowZero(durationMs: Long): String {
    return formatDurationSeconds((durationMs / 1000L).coerceAtLeast(0L))
}

private fun formatDurationSeconds(totalSeconds: Long): String {
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

private fun Context.copyTextToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
