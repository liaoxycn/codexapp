package com.codexapp.model

import androidx.compose.runtime.Immutable

@Immutable
data class ThreadMessage(
    val id: String,
    val role: MessageRole,
    val blocks: List<MessageBlock>,
    val forkNumTurns: Int? = null,
    val rollbackNumTurns: Int? = null,
    val durationMs: Long? = null,
    val isFinal: Boolean = false
)

@Immutable
sealed interface MessageBlock {
    @Immutable
    data class Text(val value: String) : MessageBlock
    @Immutable
    data class Code(val language: String, val value: String) : MessageBlock
    @Immutable
    data class Status(val value: String) : MessageBlock
    @Immutable
    data class Reasoning(val value: String) : MessageBlock
    @Immutable
    data class Commentary(val value: String) : MessageBlock
    @Immutable
    data class Plan(val value: String) : MessageBlock
    @Immutable
    data class CommandSummary(val value: String) : MessageBlock
    @Immutable
    data class CommandMeta(val value: String) : MessageBlock
    @Immutable
    data class ToolCall(val value: String) : MessageBlock
    @Immutable
    data class WebSearch(val value: String) : MessageBlock
    @Immutable
    data class Image(val value: String) : MessageBlock
    @Immutable
    data class Collab(val value: String) : MessageBlock
    @Immutable
    data class Review(val value: String) : MessageBlock
    @Immutable
    data class Hook(val value: String) : MessageBlock
    @Immutable
    data class Context(val value: String) : MessageBlock
    @Immutable
    data class FileChangeSummary(val value: String) : MessageBlock
    @Immutable
    data class FileChangeMeta(val value: String, val path: String = "") : MessageBlock
    @Immutable
    data class FileChangeDiff(val value: String) : MessageBlock
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
