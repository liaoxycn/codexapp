package com.codexapp.ui.message

import com.codexapp.model.MessageBlock
import com.codexapp.model.ThreadMessage

internal fun ThreadMessage.revisionKey(): String {
    val contentSize = blocks.sumOf { block: MessageBlock ->
        when (block) {
            is MessageBlock.Text -> block.value.length
            is MessageBlock.Code -> block.language.length + block.value.length
            is MessageBlock.Status -> block.value.length
            is MessageBlock.Reasoning -> block.value.length
            is MessageBlock.Commentary -> block.value.length
            is MessageBlock.Plan -> block.value.length
            is MessageBlock.CommandSummary -> block.value.length
            is MessageBlock.CommandMeta -> block.value.length
            is MessageBlock.ToolCall -> block.value.length
            is MessageBlock.WebSearch -> block.value.length
            is MessageBlock.Image -> block.value.length
            is MessageBlock.Collab -> block.value.length
            is MessageBlock.Review -> block.value.length
            is MessageBlock.Hook -> block.value.length
            is MessageBlock.Context -> block.value.length
            is MessageBlock.FileChangeSummary -> block.value.length
            is MessageBlock.FileChangeMeta -> block.value.length
            is MessageBlock.FileChangeDiff -> block.value.length
        }
    }
    return "$id:${blocks.size}:$contentSize"
}
