package com.codexapp.ui.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun FileChangeRow(
    messageId: String,
    index: Int,
    entry: FileChangeEntry,
    compactMode: Boolean
) {
    val hasDiff = !entry.diff.isNullOrBlank()
    var expanded by rememberSaveable(fileChangeExpansionKey(messageId, index)) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 3.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FileChangeTokens.rowShape)
                .then(
                    if (hasDiff) {
                        Modifier.clickable(onClick = { expanded = !expanded })
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 2.dp, vertical = if (compactMode) 2.dp else 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.label,
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compactMode) 10.sp else 11.sp,
                lineHeight = if (compactMode) 13.sp else 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (hasDiff) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = fileChangeDiffContentDescription(expanded, entry.label),
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (expanded && hasDiff) {
            if (entry.path.isNotBlank()) {
                Text(
                    text = entry.path,
                    color = CodexTheme.colors.textTertiary,
                    fontSize = if (compactMode) 9.sp else 10.sp,
                    lineHeight = if (compactMode) 12.sp else 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp)
                )
            }
            FileDiffBlock(
                value = entry.diff.orEmpty(),
                compactMode = compactMode
            )
        }
    }
}

internal fun fileChangeExpansionKey(messageId: String, index: Int): String {
    return "$messageId:fileChange:$index"
}

internal fun fileChangeDiffContentDescription(expanded: Boolean, label: String): String {
    return if (expanded) {
        "收起 $label diff"
    } else {
        "展开 $label diff"
    }
}
