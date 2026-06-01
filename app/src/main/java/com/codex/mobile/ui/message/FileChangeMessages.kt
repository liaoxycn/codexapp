package com.codex.mobile.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.theme.CodexTheme

internal object FileChangeTokens {
    val rowShape = RoundedCornerShape(8.dp)

    fun cardShape(compactMode: Boolean): RoundedCornerShape {
        return RoundedCornerShape(if (compactMode) 10.dp else 12.dp)
    }

    fun diffShape(compactMode: Boolean): RoundedCornerShape {
        return RoundedCornerShape(if (compactMode) 8.dp else 10.dp)
    }
}

@Composable
internal fun FileChangeCard(
    messageId: String,
    summary: String,
    entries: List<FileChangeEntry>,
    compactMode: Boolean
) {
    val cardShape = FileChangeTokens.cardShape(compactMode)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border, cardShape)
            .padding(horizontal = if (compactMode) 7.dp else 8.dp, vertical = if (compactMode) 2.dp else 3.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        if (entries.isEmpty()) {
            Text(
                text = summary,
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compactMode) 10.sp else 11.sp,
                lineHeight = if (compactMode) 13.sp else 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            entries.forEachIndexed { index, entry ->
                FileChangeRow(
                    messageId = messageId,
                    index = index,
                    entry = entry,
                    compactMode = compactMode
                )
            }
        }
    }
}
