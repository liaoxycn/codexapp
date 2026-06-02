package com.codex.mobile.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.model.AppUpdateState
import com.codex.mobile.model.AppUpdateStatus
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun DrawerHeader(
    connectionStatus: ConnectionStatus,
    connectionDetail: String,
    desktopRestartRequired: Boolean,
    appUpdate: AppUpdateState,
    hasRunningThread: Boolean,
    isRefreshing: Boolean,
    onCreateThread: () -> Unit,
    onRefreshThreads: () -> Unit,
    onOpenConnection: () -> Unit,
    onRestartDesktop: () -> Unit,
    onDownloadUpdate: () -> Unit,
) {
    val shouldShowRestartPrompt = desktopRestartRequired && !hasRunningThread
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "会话",
                color = CodexTheme.colors.textPrimary,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DrawerHeaderAction(
                    icon = Icons.Filled.Settings,
                    contentDescription = "连接设置",
                    onClick = onOpenConnection
                )
                DrawerHeaderAction(
                    icon = Icons.Filled.Add,
                    contentDescription = "新建会话",
                    onClick = onCreateThread
                )
                DrawerHeaderAction(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "刷新会话",
                    loading = isRefreshing,
                    onClick = onRefreshThreads
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ConnectionStatusLine(
                status = connectionStatus,
                detail = connectionDetail
            )
            if (shouldShowRestartPrompt) {
                RestartDesktopPrompt(onClick = onRestartDesktop)
            }
            AppUpdatePrompt(
                state = appUpdate,
                onDownload = onDownloadUpdate,
            )
        }
    }
}

@Composable
private fun AppUpdatePrompt(
    state: AppUpdateState,
    onDownload: () -> Unit,
) {
    when (state.status) {
        AppUpdateStatus.AVAILABLE -> UpdateNoticeRow(
            icon = Icons.Filled.Download,
            title = "发现新版本 ${state.latestVersion}",
            action = "系统下载",
            loading = false,
            onClick = onDownload
        )
        AppUpdateStatus.DOWNLOAD_QUEUED -> UpdateNoticeRow(
            icon = Icons.Filled.Download,
            title = state.message.ifBlank { "已交给系统下载器" },
            action = "看通知",
            loading = false,
            onClick = {}
        )
        AppUpdateStatus.ERROR -> {
            if (state.latestVersion.isBlank() && state.downloadUrl.isBlank()) return
            UpdateNoticeRow(
                icon = Icons.Filled.Warning,
                title = state.message.ifBlank { "更新失败" },
                action = "重试",
                loading = false,
                onClick = onDownload
            )
        }
        else -> Unit
    }
}

@Composable
private fun UpdateNoticeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    action: String,
    loading: Boolean,
    onClick: () -> Unit
) {
    val warning = Color(0xFFD97706)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF7ED))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(8.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = warning,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = warning,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = title,
                color = Color(0xFF9A3412),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Text(
            text = action,
            color = warning,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun RestartDesktopPrompt(
    onClick: () -> Unit
) {
    val warning = Color(0xFFD97706)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF7ED))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = warning,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "桌面端待同步",
                color = Color(0xFF9A3412),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Text(
            text = "立即重启",
            color = warning,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}
