package com.codexapp.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun CodeBlock(
    messageId: String,
    blockIndex: Int,
    language: String,
    value: String,
    compactMode: Boolean = false
) {
    val presentation = remember(language, value) {
        buildCodeBlockPresentation(language = language, value = value)
    }
    var expanded by rememberSaveable(messageId + ":code:$blockIndex") {
        mutableStateOf(!presentation.shouldCollapse)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .background(CodexTheme.colors.codeBackground)
    ) {
        CodeBlockHeader(
            compactMode = compactMode,
            label = presentation.label,
            expanded = expanded,
            shouldCollapse = presentation.shouldCollapse,
            onToggleExpanded = { expanded = !expanded },
        )
        Divider(color = Color(0xFF374151))
        if (presentation.shouldCollapse && !expanded) {
            CodeBlockCollapsedHint(
                compactMode = compactMode,
                hint = presentation.collapsedHint,
            )
        } else {
            CodeBlockText(
                compactMode = compactMode,
                text = presentation.expandedText,
            )
        }
    }
}

@Composable
internal fun CommandOutputBlock(
    messageId: String,
    blockIndex: Int,
    language: String,
    value: String,
    compactMode: Boolean
) {
    CodeBlock(
        messageId = messageId,
        blockIndex = blockIndex,
        language = language,
        value = value,
        compactMode = compactMode
    )
}

@Composable
private fun CodeBlockCollapsedHint(
    compactMode: Boolean,
    hint: String,
) {
    Text(
        text = hint,
        modifier = Modifier.padding(
            horizontal = if (compactMode) 9.dp else 10.dp,
            vertical = if (compactMode) 7.dp else 8.dp
        ),
        color = Color(0xFF9CA3AF),
        fontSize = if (compactMode) 10.sp else 11.sp,
        lineHeight = if (compactMode) 14.sp else 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CodeBlockText(
    compactMode: Boolean,
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = if (compactMode) 9.dp else 10.dp,
                vertical = if (compactMode) 7.dp else 8.dp
            ),
        color = Color(0xFFE5E7EB),
        fontSize = if (compactMode) 10.sp else 11.sp,
        lineHeight = if (compactMode) 15.sp else 16.sp,
        overflow = TextOverflow.Clip
    )
}
