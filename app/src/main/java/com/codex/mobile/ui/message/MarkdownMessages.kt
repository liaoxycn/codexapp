package com.codex.mobile.ui.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Composable
internal fun MarkdownText(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    maxCollapsedLines: Int,
    wrapContent: Boolean = false
) {
    val displayText = text.trimEnd()
    val lines = remember(displayText) { parseMarkdownLines(displayText) }
    val shouldCollapse =
        lines.count { it.kind != MarkdownLineKind.EMPTY } > maxCollapsedLines || displayText.length > 180
    val visibleLines = if (shouldCollapse && !expanded) lines.take(maxCollapsedLines) else lines

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                visibleLines.forEach { line ->
                    MarkdownLineItem(
                        line = line,
                        textColor = textColor,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        wrapContent = wrapContent
                    )
                }
            }
        }
        if (shouldCollapse) {
            ExpandCollapseTextButton(
                expanded = expanded,
                expandLabel = "展开",
                collapseLabel = "收起",
                expandDescription = "展开全文",
                collapseDescription = "收起全文",
                onToggle = onToggle
            )
        }
    }
}
