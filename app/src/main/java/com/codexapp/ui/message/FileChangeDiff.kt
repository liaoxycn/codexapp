package com.codexapp.ui.message

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

internal fun buildDiffAnnotatedString(diff: String): AnnotatedString = buildAnnotatedString {
    val lines = diff.lines()
    lines.forEachIndexed { index, line ->
        withStyle(SpanStyle(color = diffLineColor(line))) {
            append(line)
        }
        if (index < lines.lastIndex) {
            append("\n")
        }
    }
}

internal fun diffLineColor(line: String): Color = when {
    line.startsWith("@@") -> Color(0xFF93C5FD)
    line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff --git") -> Color(0xFF9CA3AF)
    line.startsWith("+") -> Color(0xFF86EFAC)
    line.startsWith("-") -> Color(0xFFFCA5A5)
    else -> Color(0xFFE5E7EB)
}
