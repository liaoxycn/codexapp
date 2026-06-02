package com.codexapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.model.ThreadStatus
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun ThreadStatusIcon(status: ThreadStatus) {
    Box(
        modifier = Modifier.semantics {
            contentDescription = "当前会话状态：${threadStatusLabel(status)}"
        }
    ) {
        StatusDot(status, size = 8.dp)
    }
}

@Composable
internal fun ThreadStatusText(
    status: ThreadStatus,
    modifier: Modifier = Modifier
) {
    val text = threadStatusLabel(status)
    val color = when (status) {
        ThreadStatus.RUNNING -> Color(0xFF2563EB)
        ThreadStatus.NEEDS_APPROVAL -> Color(0xFFF59E0B)
        ThreadStatus.FAILED -> Color(0xFFDC2626)
        ThreadStatus.IDLE -> CodexTheme.colors.textTertiary
    }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal fun threadStatusLabel(status: ThreadStatus): String = when (status) {
    ThreadStatus.RUNNING -> "运行中"
    ThreadStatus.NEEDS_APPROVAL -> "待审批"
    ThreadStatus.FAILED -> "失败"
    ThreadStatus.IDLE -> "空闲"
}

@Composable
internal fun StatusDot(status: ThreadStatus, size: Dp = 7.dp) {
    val color = threadStatusColor(status)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

internal fun threadStatusColor(status: ThreadStatus): Color {
    return when (status) {
        ThreadStatus.RUNNING -> Color(0xFF2563EB)
        ThreadStatus.IDLE -> Color(0xFF9CA3AF)
        ThreadStatus.NEEDS_APPROVAL -> Color(0xFFF59E0B)
        ThreadStatus.FAILED -> Color(0xFFDC2626)
    }
}
