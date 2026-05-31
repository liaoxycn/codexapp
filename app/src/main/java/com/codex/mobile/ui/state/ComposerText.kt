package com.codex.mobile.ui.state

internal fun appendComposerText(current: String, text: String): String {
    return when {
        current.isBlank() -> text
        current.endsWith(" ") || text.startsWith(" ") -> current + text
        else -> "$current $text"
    }
}

internal fun applySlashCommandText(current: String, command: String): String {
    val trimmedEnd = current.trimEnd()
    val trailingWhitespace = current.drop(trimmedEnd.length)
    val lastSeparator = trimmedEnd.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
    val trailingToken = if (lastSeparator >= 0) trimmedEnd.substring(lastSeparator + 1) else trimmedEnd

    return if (trailingToken.startsWith("/") || trailingToken.startsWith("!")) {
        val prefix = if (lastSeparator >= 0) trimmedEnd.substring(0, lastSeparator + 1) else ""
        prefix + command + if (trailingWhitespace.isEmpty()) " " else trailingWhitespace
    } else {
        when {
            trimmedEnd.isBlank() -> "$command "
            trimmedEnd.endsWith(" ") -> "$trimmedEnd$command "
            else -> "$trimmedEnd $command "
        }
    }
}

internal fun insertCommandTemplate(current: String, command: String): String {
    val trimmed = current.trim()
    return if (trimmed.isBlank()) command else "$trimmed\n$command"
}
