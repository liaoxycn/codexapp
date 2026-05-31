package com.codex.mobile.ui.message

internal data class CodeBlockPresentation(
    val label: String,
    val lineCount: Int,
    val shouldCollapse: Boolean,
    val collapsedHint: String,
    val expandedText: String,
)

internal fun buildCodeBlockPresentation(
    language: String,
    value: String,
): CodeBlockPresentation {
    val isDiff = language.equals("diff", ignoreCase = true)
    val isShell = language.equals("shell", ignoreCase = true)
    val lineCount = value.lineSequence().count().coerceAtLeast(1)
    val shouldCollapse = isShell || isDiff || lineCount > 8
    return CodeBlockPresentation(
        label = codeBlockLabel(language = language, isShell = isShell),
        lineCount = lineCount,
        shouldCollapse = shouldCollapse,
        collapsedHint = collapsedCodeBlockHint(
            isShell = isShell,
            isDiff = isDiff,
            lineCount = lineCount,
        ),
        expandedText = value,
    )
}

internal fun codeBlockLabel(language: String, isShell: Boolean = language.equals("shell", ignoreCase = true)): String {
    return if (isShell) "输出" else language.ifBlank { "code" }
}

internal fun collapsedCodeBlockHint(
    isShell: Boolean,
    isDiff: Boolean,
    lineCount: Int,
): String {
    return when {
        isShell && lineCount <= 1 -> "点击展开查看输出"
        isShell -> "$lineCount 行输出，点击展开查看"
        isDiff -> "点击展开查看 diff"
        else -> "$lineCount 行内容，点击展开查看"
    }
}
