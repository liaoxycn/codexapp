package com.codex.mobile.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun FileDiffBlock(
    value: String,
    compactMode: Boolean
) {
    val styledDiff = remember(value) { buildDiffAnnotatedString(value) }
    Text(
        text = styledDiff,
        modifier = Modifier
            .fillMaxWidth()
            .clip(FileChangeTokens.diffShape(compactMode))
            .background(CodexTheme.colors.codeBackground)
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = if (compactMode) 8.dp else 9.dp,
                vertical = if (compactMode) 5.dp else 6.dp
            ),
        fontSize = if (compactMode) 10.sp else 11.sp,
        lineHeight = if (compactMode) 15.sp else 16.sp,
        fontFamily = FontFamily.Monospace,
        overflow = TextOverflow.Clip
    )
}
