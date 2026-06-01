package com.codex.mobile.ui.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun DrawerHeader(
    connectionStatus: ConnectionStatus,
    connectionDetail: String,
    onCreateThread: () -> Unit,
    onRefreshThreads: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "会话",
                color = CodexTheme.colors.textPrimary,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            ConnectionStatusLine(
                status = connectionStatus,
                detail = connectionDetail
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DrawerHeaderAction(
                icon = Icons.Filled.Add,
                contentDescription = "新建会话",
                onClick = onCreateThread
            )
            DrawerHeaderAction(
                icon = Icons.Filled.Refresh,
                contentDescription = "刷新会话",
                onClick = onRefreshThreads
            )
        }
    }
}
