package com.codexapp.ui.message

internal enum class MarkdownLineKind {
    PARAGRAPH,
    HEADING,
    LIST_ITEM,
    QUOTE,
    CODE,
    EMPTY
}

internal data class MarkdownLine(
    val kind: MarkdownLineKind,
    val text: String,
    val indent: Int = 0
)

internal fun parseMarkdownLines(text: String): List<MarkdownLine> {
    val normalized = text.replace("\r\n", "\n").trimEnd()
    if (normalized.isBlank()) {
        return listOf(MarkdownLine(MarkdownLineKind.EMPTY, ""))
    }

    val result = mutableListOf<MarkdownLine>()
    var inCodeBlock = false
    normalized.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        if (line.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            return@forEach
        }
        if (inCodeBlock) {
            result += MarkdownLine(MarkdownLineKind.CODE, line)
            return@forEach
        }

        when {
            line.isBlank() -> result += MarkdownLine(MarkdownLineKind.EMPTY, "")
            line.startsWith("#") -> result += MarkdownLine(
                kind = MarkdownLineKind.HEADING,
                text = line.trimStart('#').trim(),
                indent = line.takeWhile { it == '#' }.length
            )

            line.startsWith("- ") || line.startsWith("* ") -> result += MarkdownLine(
                kind = MarkdownLineKind.LIST_ITEM,
                text = line.drop(2).trim()
            )

            line.matches(Regex("""\d+\.\s+.+""")) -> result += MarkdownLine(
                kind = MarkdownLineKind.LIST_ITEM,
                text = line.substringAfter('.').trim()
            )

            line.startsWith("> ") -> result += MarkdownLine(
                kind = MarkdownLineKind.QUOTE,
                text = line.drop(2).trim()
            )

            else -> result += MarkdownLine(MarkdownLineKind.PARAGRAPH, line)
        }
    }
    return result
}
