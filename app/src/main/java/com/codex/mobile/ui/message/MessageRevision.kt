package com.codex.mobile.ui.message

import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.ThreadMessage

internal fun ThreadMessage.revisionKey(): String {
    val contentSize = blocks.sumOf { block: MessageBlock ->
        when (block) {
            is MessageBlock.Text -> block.value.length
            is MessageBlock.Code -> block.language.length + block.value.length
            is MessageBlock.Status -> block.value.length
            is MessageBlock.Reasoning -> block.value.length
            is MessageBlock.CommandSummary -> block.value.length
            is MessageBlock.CommandMeta -> block.value.length
            is MessageBlock.FileChangeSummary -> block.value.length
            is MessageBlock.FileChangeMeta -> block.value.length
            is MessageBlock.FileChangeDiff -> block.value.length
        }
    }
    return "$id:${blocks.size}:$contentSize"
}
