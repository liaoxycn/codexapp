package com.codex.mobile.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun ReasoningBlock(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    compactMode: Boolean
) {
    val displayText = text.trimEnd()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .padding(horizontal = if (compactMode) 9.dp else 10.dp, vertical = if (compactMode) 7.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onToggle)
                .clearAndSetSemantics { contentDescription = if (expanded) "收起思考详情" else "展开思考详情" }
                .padding(horizontal = 2.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (expanded) "思考详情" else "思考中",
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compactMode) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CodexTheme.colors.textTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
        if (expanded) {
            Text(
                text = displayText,
                color = CodexTheme.colors.textPrimary,
                fontSize = if (compactMode) 11.sp else 12.sp,
                lineHeight = if (compactMode) 15.sp else 17.sp
            )
        }
    }
}

@Composable
internal fun ExpandableText(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    maxCollapsedLines: Int
) {
    val displayText = text.trimEnd()
    val lines = remember(displayText) { parseMarkdownLines(displayText) }
    val shouldCollapse = lines.size > maxCollapsedLines || displayText.length > 140
    val visibleLines = if (shouldCollapse && !expanded) lines.take(maxCollapsedLines) else lines
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        visibleLines.forEach { line ->
            MarkdownLineItem(
                line = line,
                textColor = textColor,
                fontSize = fontSize,
                lineHeight = lineHeight
            )
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
