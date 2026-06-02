package com.codexapp.model

data class ThreadMessage(
    val id: String,
    val role: MessageRole,
    val blocks: List<MessageBlock>,
    val forkNumTurns: Int? = null,
    val rollbackNumTurns: Int? = null,
    val durationMs: Long? = null,
    val isFinal: Boolean = false
)

sealed interface MessageBlock {
    data class Text(val value: String) : MessageBlock
    data class Code(val language: String, val value: String) : MessageBlock
    data class Status(val value: String) : MessageBlock
    data class Reasoning(val value: String) : MessageBlock
    data class CommandSummary(val value: String) : MessageBlock
    data class CommandMeta(val value: String) : MessageBlock
    data class FileChangeSummary(val value: String) : MessageBlock
    data class FileChangeMeta(val value: String, val path: String = "") : MessageBlock
    data class FileChangeDiff(val value: String) : MessageBlock
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
