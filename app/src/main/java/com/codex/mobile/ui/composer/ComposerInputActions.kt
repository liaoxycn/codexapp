package com.codex.mobile.ui.composer

import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun ComposerDetailsToggleButton(
    expanded: Boolean,
    onToggleDetails: () -> Unit
) {
    ComposerIconButton(
        onClick = onToggleDetails,
        contentDescription = composerDetailsContentDescription(expanded),
        size = 32.dp,
        shape = RoundedCornerShape(10.dp)
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.Add,
            contentDescription = null,
            tint = CodexTheme.colors.textPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun ComposerSubmitButton(
    isGenerating: Boolean,
    sendEnabled: Boolean,
    onSendNow: () -> Unit,
    onStop: () -> Unit
) {
    if (isGenerating) {
        ComposerIconButton(
            onClick = onStop,
            contentDescription = "停止生成",
            size = 32.dp,
            shape = CircleShape,
            fill = CodexTheme.colors.danger
        ) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
        return
    }

    ComposerIconButton(
        onClick = {
            if (sendEnabled) onSendNow()
        },
        contentDescription = composerSendContentDescription(sendEnabled),
        enabled = sendEnabled,
        size = 32.dp,
        shape = CircleShape,
        fill = composerSendFill(sendEnabled)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = if (!sendEnabled) CodexTheme.colors.textTertiary else Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

internal fun composerDetailsContentDescription(expanded: Boolean): String {
    return if (expanded) "收起输入工具" else "展开输入工具"
}

internal fun composerSendContentDescription(sendEnabled: Boolean): String {
    return if (sendEnabled) "发送消息" else "输入内容后发送"
}

@Composable
internal fun composerSendFill(sendEnabled: Boolean): Color {
    return if (sendEnabled) {
        Color(0xFF111827)
    } else {
        CodexTheme.colors.surfaceSubtle
    }
}
