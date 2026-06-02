package com.codexapp.ui.composer

import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codexapp.ui.theme.CodexTheme
import kotlinx.coroutines.delay

@Composable
internal fun ComposerDetailsToggleButton(
    expanded: Boolean,
    onToggleDetails: () -> Unit
) {
    ComposerIconButton(
        onClick = onToggleDetails,
        contentDescription = composerDetailsContentDescription(expanded),
        size = 40.dp,
        shape = CircleShape,
        fill = CodexTheme.colors.surfaceSubtle
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.Add,
            contentDescription = null,
            tint = CodexTheme.colors.textPrimary.copy(alpha = if (expanded) 1f else ContentAlpha.high),
            modifier = Modifier.size(19.dp)
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
    var sending by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            sending = false
        }
        if (!isGenerating) {
            stopping = false
        }
    }
    LaunchedEffect(sendEnabled) {
        if (!sendEnabled) {
            sending = false
        }
    }
    LaunchedEffect(sending) {
        if (sending) {
            delay(2500L)
            sending = false
        }
    }
    LaunchedEffect(stopping) {
        if (stopping) {
            delay(3500L)
            stopping = false
        }
    }
    if (isGenerating) {
        ComposerIconButton(
            onClick = {
                stopping = true
                onStop()
            },
            contentDescription = "停止生成",
            enabled = !stopping,
            size = 40.dp,
            shape = CircleShape,
            fill = CodexTheme.colors.danger
        ) {
            if (stopping) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        return
    }

    ComposerIconButton(
        onClick = {
            if (sendEnabled) {
                sending = true
                onSendNow()
            }
        },
        contentDescription = composerSendContentDescription(sendEnabled),
        enabled = sendEnabled && !sending,
        size = 40.dp,
        shape = CircleShape,
        fill = composerSendFill(sendEnabled)
    ) {
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = if (!sendEnabled) CodexTheme.colors.textTertiary else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
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
