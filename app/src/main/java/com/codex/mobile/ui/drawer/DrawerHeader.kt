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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

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
    onInstallUpdate: () -> Unit,
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
                onInstall = onInstallUpdate
            )
        }
    }
}

@Composable
private fun AppUpdatePrompt(
    state: AppUpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    when (state.status) {
        AppUpdateStatus.AVAILABLE -> UpdateNoticeRow(
            icon = Icons.Filled.Download,
            title = "发现新版本 ${state.latestVersion}",
            action = "下载更新",
            loading = false,
            progress = null,
            onClick = onDownload
        )
        AppUpdateStatus.DOWNLOADING -> UpdateNoticeRow(
            icon = Icons.Filled.Download,
            title = downloadTitle(state),
            action = "${(state.progress * 100).toInt()}%",
            loading = true,
            progress = state.progress.takeIf { state.totalBytes > 0L },
            onClick = {}
        )
        AppUpdateStatus.READY_TO_INSTALL,
        AppUpdateStatus.INSTALL_PERMISSION_REQUIRED -> UpdateNoticeRow(
            icon = Icons.Filled.Download,
            title = if (state.status == AppUpdateStatus.INSTALL_PERMISSION_REQUIRED) {
                state.message.ifBlank { "需要允许安装未知应用" }
            } else {
                "新版本已下载"
            },
            action = "立即安装",
            loading = false,
            progress = 1f,
            onClick = onInstall
        )
        AppUpdateStatus.ERROR -> {
            if (state.latestVersion.isBlank() && state.downloadUrl.isBlank()) return
            UpdateNoticeRow(
                icon = Icons.Filled.Warning,
                title = state.message.ifBlank { "更新失败" },
                action = "重试",
                loading = false,
                progress = null,
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
    progress: Float?,
    onClick: () -> Unit
) {
    val warning = Color(0xFFD97706)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF7ED))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(8.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
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
        if (progress != null) {
            androidx.compose.material.LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = warning,
                backgroundColor = Color(0xFFFED7AA)
            )
        }
    }
}

@Composable
private fun RestartDesktopPrompt(
    onClick: () -> Unit
) {
    var restarting by remember { mutableStateOf(false) }
    LaunchedEffect(restarting) {
        if (restarting) {
            delay(2500L)
            restarting = false
        }
    }
    val warning = Color(0xFFD97706)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF7ED))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(8.dp))
            .clickable(enabled = !restarting) {
                restarting = true
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (restarting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = warning,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = warning,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = if (restarting) "正在重启桌面端" else "桌面端待同步",
                color = Color(0xFF9A3412),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Text(
            text = if (restarting) "处理中" else "立即重启",
            color = warning,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

private fun downloadTitle(state: AppUpdateState): String {
    return if (state.totalBytes > 0L) {
        "正在下载 ${formatBytes(state.downloadedBytes)}/${formatBytes(state.totalBytes)}"
    } else {
        "正在下载新版本"
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024f / 1024f
    return if (mb >= 1f) {
        String.format("%.1f MB", mb)
    } else {
        "${bytes / 1024L} KB"
    }
}
