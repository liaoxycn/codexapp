package com.codexapp.ui.message

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun CommandExecutionCard(
    messageId: String,
    blockIndex: Int,
    summary: String,
    metaLines: List<String>,
    outputLanguage: String,
    outputValue: String,
    compactMode: Boolean
) {
    val hasOutput = outputValue.isNotBlank()
    val hasDetails = metaLines.isNotEmpty() || hasOutput
    var detailsExpanded by rememberSaveable(messageId + ":command:$blockIndex") { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .padding(horizontal = if (compactMode) 8.dp else 9.dp, vertical = if (compactMode) 5.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (hasDetails) {
                Modifier
                    .clickable(onClick = { detailsExpanded = !detailsExpanded })
                    .clearAndSetSemantics { contentDescription = if (detailsExpanded) "收起命令详情" else "展开命令详情" }
            } else {
                Modifier
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = CodexTheme.colors.textTertiary,
                modifier = Modifier.size(if (compactMode) 13.dp else 15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = summary,
                color = CodexTheme.colors.textPrimary,
                fontSize = if (compactMode) 11.sp else 12.sp,
                lineHeight = if (compactMode) 14.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hasDetails) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (detailsExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (detailsExpanded) {
            metaLines.forEach { metaLine ->
                MarkdownText(
                    text = metaLine,
                    expanded = true,
                    onToggle = {},
                    textColor = CodexTheme.colors.textTertiary,
                    fontSize = if (compactMode) 9.sp else 10.sp,
                    lineHeight = if (compactMode) 12.sp else 13.sp,
                    maxCollapsedLines = Int.MAX_VALUE,
                    wrapContent = false
                )
            }
        }
        if (detailsExpanded && hasOutput) {
            CommandOutputBlock(
                messageId = messageId,
                blockIndex = blockIndex,
                language = outputLanguage,
                value = outputValue,
                compactMode = compactMode
            )
        }
    }
}
