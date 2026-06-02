package com.codexapp.ui.drawer

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.codexapp.model.ConnectionStatus

@Composable
internal fun ConnectionStatusLine(
    status: ConnectionStatus,
    detail: String
) {
    val statusText = when (status) {
        ConnectionStatus.CONNECTED -> "已连接 Desktop Gateway"
        ConnectionStatus.CONNECTING -> "正在连接 Desktop Gateway"
        ConnectionStatus.ERROR -> detail.ifBlank { "连接异常" }
        ConnectionStatus.DISCONNECTED -> "未连接 Desktop Gateway"
    }
    Text(
        text = statusText,
        color = when (status) {
            ConnectionStatus.CONNECTED -> Color(0xFF059669)
            ConnectionStatus.CONNECTING -> Color(0xFF2563EB)
            ConnectionStatus.ERROR -> Color(0xFFDC2626)
            ConnectionStatus.DISCONNECTED -> Color(0xFFF59E0B)
        },
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
