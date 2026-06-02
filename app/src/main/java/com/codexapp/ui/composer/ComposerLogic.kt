package com.codexapp.ui.composer

import com.codexapp.model.ConnectionStatus

internal fun extractTrailingComposerToken(text: String): String {
    val normalizedComposer = text.trimEnd()
    val lastSeparator = normalizedComposer.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
    return if (lastSeparator >= 0) normalizedComposer.substring(lastSeparator + 1) else normalizedComposer
}

internal data class ComposerInputRouting(
    val nextPanel: ComposerPanel,
    val nextSlashQuery: String,
)

internal fun routeComposerInput(
    activePanel: ComposerPanel,
    text: String,
): ComposerInputRouting {
    val token = extractTrailingComposerToken(text)
    return if (token.startsWith("/") || token.startsWith("!")) {
        ComposerInputRouting(
            nextPanel = ComposerPanel.SLASH,
            nextSlashQuery = token,
        )
    } else {
        ComposerInputRouting(
            nextPanel = if (activePanel == ComposerPanel.SLASH) ComposerPanel.NONE else activePanel,
            nextSlashQuery = "",
        )
    }
}

internal fun shouldShowSlashPanel(
    slashCommands: List<String>,
    activePanel: ComposerPanel,
    suppressInlineSlashPanel: Boolean,
    trailingToken: String
): Boolean {
    if (slashCommands.isEmpty()) {
        return false
    }
    return activePanel == ComposerPanel.SLASH ||
        (!suppressInlineSlashPanel && (trailingToken.startsWith("/") || trailingToken.startsWith("!")))
}

internal fun filterSlashCommands(commands: List<String>, slashQuery: String): List<String> {
    return commands.filter { command ->
        val keyword = command.substringBefore("  ").trim()
        slashQuery.isBlank() ||
            command.contains(slashQuery, ignoreCase = true) ||
            keyword.contains(slashQuery, ignoreCase = true)
    }
}

internal fun composerFileMention(path: String): String {
    return "@{${path.trim()}}"
}

internal fun composerPlaceholder(
    composerEnabled: Boolean,
    connectionStatus: ConnectionStatus
): String {
    return when {
        !composerEnabled -> "正在切换会话…"
        connectionStatus == ConnectionStatus.CONNECTED -> "回复 Codex"
        connectionStatus == ConnectionStatus.CONNECTING -> "正在连接…"
        else -> "未连接"
    }
}
