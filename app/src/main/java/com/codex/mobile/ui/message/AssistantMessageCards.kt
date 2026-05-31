package com.codex.mobile.ui.message

import com.codex.mobile.model.MessageBlock

internal data class AssistantMessageCards(
    val fileChangeSummary: String?,
    val fileChangeEntries: List<FileChangeEntry>,
    val commandSummary: String?,
    val commandMetaLines: List<String>,
    val commandOutput: MessageBlock.Code?
) {
    val hasFileChangeCard: Boolean = fileChangeSummary != null || fileChangeEntries.isNotEmpty()
    val hasCommandCard: Boolean = commandSummary != null || commandMetaLines.isNotEmpty() || commandOutput != null
}

internal fun deriveAssistantMessageCards(blocks: List<MessageBlock>): AssistantMessageCards {
    return AssistantMessageCards(
        fileChangeSummary = blocks.filterIsInstance<MessageBlock.FileChangeSummary>().firstOrNull()?.value,
        fileChangeEntries = buildFileChangeEntries(blocks),
        commandSummary = blocks.filterIsInstance<MessageBlock.CommandSummary>().firstOrNull()?.value,
        commandMetaLines = blocks.filterIsInstance<MessageBlock.CommandMeta>()
            .mapNotNull { it.value.trim().takeIf(String::isNotEmpty) },
        commandOutput = blocks.filterIsInstance<MessageBlock.Code>()
            .firstOrNull { it.language.equals("shell", ignoreCase = true) }
    )
}
