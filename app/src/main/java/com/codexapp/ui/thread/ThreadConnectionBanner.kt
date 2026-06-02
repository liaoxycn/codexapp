package com.codexapp.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.HomeUiState
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun ConnectionBanner(
    state: HomeUiState,
    compact: Boolean,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when (state.connectionStatus) {
        ConnectionStatus.CONNECTED -> "已连接 Desktop Gateway"
        ConnectionStatus.CONNECTING -> "正在连接 Desktop Gateway"
        ConnectionStatus.ERROR -> "连接异常"
        ConnectionStatus.DISCONNECTED -> "未连接 Desktop Gateway"
    }
    val detailText = connectionBannerDetailText(state)
    val statusColor = when (state.connectionStatus) {
        ConnectionStatus.CONNECTED -> Color(0xFF059669)
        ConnectionStatus.CONNECTING -> Color(0xFF2563EB)
        ConnectionStatus.ERROR -> Color(0xFFDC2626)
        ConnectionStatus.DISCONNECTED -> Color(0xFFF59E0B)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (compact) {
                    Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                } else {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(CodexTheme.colors.surface)
                        .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                }
            )
            .semantics { contentDescription = "连接状态：$statusText" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (detailText == null) 0.dp else 3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 6.dp else 8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                Text(
                    text = statusText,
                    color = CodexTheme.colors.textPrimary,
                    fontSize = if (compact) 9.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.isGenerating) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "运行中",
                        color = Color(0xFF2563EB),
                        fontSize = if (compact) 8.sp else 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            if (detailText != null) {
                Text(
                    text = detailText,
                    color = CodexTheme.colors.textSecondary,
                    fontSize = if (compact) 8.sp else 10.sp,
                    lineHeight = if (compact) 11.sp else 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = if (compact) 12.dp else 16.dp)
                )
            }
        }
        if (state.connectionStatus == ConnectionStatus.DISCONNECTED || state.connectionStatus == ConnectionStatus.ERROR) {
            Spacer(Modifier.width(if (compact) 6.dp else 12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(CodexTheme.colors.surfaceSubtle)
                    .clickable(onClick = onOpenConnection)
                    .clearAndSetSemantics { contentDescription = "打开连接设置" }
                    .defaultMinSize(minHeight = if (compact) 32.dp else 36.dp)
                    .padding(horizontal = if (compact) 9.dp else 11.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "连接",
                    color = CodexTheme.colors.textPrimary,
                    fontSize = if (compact) 10.sp else 11.sp,
                    lineHeight = if (compact) 13.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
    }
}

internal fun connectionBannerDetailText(state: HomeUiState): String? {
    return when (state.connectionStatus) {
        ConnectionStatus.ERROR -> state.connectionDetail.takeIf { it.isNotBlank() && it != "连接异常" }
        ConnectionStatus.DISCONNECTED -> state.connectionDetail.takeIf {
            it.isNotBlank() &&
                it != "未连接 gateway" &&
                it != "未连接 desktop gateway" &&
                !it.startsWith("未连接 ")
        }
        else -> null
    }
}
