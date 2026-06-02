package com.codexapp.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun GatewayDialogStatus(
    isConnected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isConnected) Color(0xFF059669) else Color(0xFFF59E0B))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = gatewayDialogStatusText(isConnected),
            color = CodexTheme.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
internal fun GatewayDialogHelperText() {
    Text(
        text = "移动端只负责连接与展示；账号、Key、MCP、Skill 均由桌面端处理。",
        color = CodexTheme.colors.textSecondary,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )
}

internal fun gatewayDialogStatusText(isConnected: Boolean): String {
    return if (isConnected) {
        "当前已连接，移动端只负责转发与展示"
    } else {
        "填写 Desktop Gateway 地址后连接"
    }
}
