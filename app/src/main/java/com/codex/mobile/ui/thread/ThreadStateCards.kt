package com.codex.mobile.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun EmptyThreadCard(
    connected: Boolean,
    hasThreads: Boolean
) {
    val title = when {
        !connected -> "连接 Desktop Gateway 后开始"
        hasThreads -> "当前会话暂无消息"
        else -> "暂无会话"
    }
    val detail = when {
        !connected -> "本 app 只显示 Desktop Gateway 真实会话数据。"
        hasThreads -> "从下方输入区发送第一条消息。"
        else -> "点右上角新建，或在侧边栏选择已有会话。"
    }
    ThreadStateCard(
        icon = if (connected) Icons.Filled.Info else Icons.Filled.ErrorOutline,
        title = title,
        detail = detail
    )
}

@Composable
internal fun ThreadSwitchingCard(pendingTitle: String?) {
    val title = pendingTitle?.takeIf { it.isNotBlank() } ?: "会话"
    ThreadStateCard(
        icon = Icons.Filled.HourglassEmpty,
        title = "正在切换到 $title",
        detail = "等待 Desktop Gateway 同步真实内容…"
    )
}

@Composable
private fun ThreadStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = CodexTheme.colors.textTertiary, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            color = CodexTheme.colors.textPrimary,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Text(
            text = detail,
            color = CodexTheme.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}
