package com.codexapp.ui.message

import com.codexapp.model.MessageBlock

internal data class FileChangeEntry(
    val label: String,
    val path: String,
    val diff: String?
)

internal fun buildFileChangeEntries(blocks: List<MessageBlock>): List<FileChangeEntry> {
    val entries = mutableListOf<FileChangeEntry>()
    blocks.forEach { block ->
        when (block) {
            is MessageBlock.FileChangeMeta -> {
                val label = block.value.trim()
                if (label.isNotEmpty()) {
                    entries += FileChangeEntry(label = label, path = block.path.trim(), diff = null)
                }
            }

            is MessageBlock.FileChangeDiff -> {
                val diff = block.value.trim()
                if (diff.isNotEmpty()) {
                    val lastIndex = entries.indexOfLast { it.diff == null }
                    if (lastIndex >= 0) {
                        entries[lastIndex] = entries[lastIndex].copy(diff = diff)
                    } else {
                        entries += FileChangeEntry(label = "文件 diff", path = "", diff = diff)
                    }
                }
            }

            else -> Unit
        }
    }
    return entries
}
