package com.codexapp.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.codexapp.model.AppUpdateState
import com.codexapp.model.AppUpdateStatus
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.StateDiagnostics
import com.codexapp.ui.theme.CodexTheme
import kotlinx.coroutines.delay

@Composable
internal fun DrawerHeader(
    connectionStatus: ConnectionStatus,
    connectionDetail: String,
    desktopRestartRequired: Boolean,
    appUpdate: AppUpdateState,
    diagnostics: StateDiagnostics,
    hasRunningThread: Boolean,
    isRefreshing: Boolean,
    onCreateThread: () -> Unit,
    onRefreshThreads: () -> Unit,
    onOpenConnection: () -> Unit,
    onRestartDesktop: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onOpenUpdateReleasePage: () -> Unit,
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
                onOpenReleasePage = onOpenUpdateReleasePage,
            )
            val diagnosticsNowMillis = rememberDiagnosticsClock(diagnostics)
            if (shouldShowDiagnostics(diagnostics, nowMillis = diagnosticsNowMillis)) {
                DiagnosticsRow(diagnostics = diagnostics)
            }
        }
    }
}

@Composable
private fun rememberDiagnosticsClock(diagnostics: StateDiagnostics): Long {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val expireAt = diagnostics.actionFinishedAt
        .takeIf { it > 0L && diagnostics.actionStatus != "failed" && diagnostics.actionType in diagnosticActionTypes }
        ?.plus(5_000L)
        ?: 0L
    LaunchedEffect(expireAt) {
        nowMillis = System.currentTimeMillis()
        if (expireAt > nowMillis) {
            delay(expireAt - nowMillis + 50L)
            nowMillis = System.currentTimeMillis()
        }
    }
    return nowMillis
}

internal fun shouldShowDiagnostics(
    diagnostics: StateDiagnostics,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    val recentAction = diagnostics.actionFinishedAt > 0L &&
        nowMillis - diagnostics.actionFinishedAt in 0L..5_000L
    return diagnostics.isGenerating ||
        diagnostics.pendingSelectionThreadId.isNotBlank() ||
        diagnostics.runningThreadIds.isNotEmpty() ||
        diagnostics.actionStatus == "failed" ||
        (recentAction && diagnostics.actionType in diagnosticActionTypes)
}

private val diagnosticActionTypes = setOf(
    "select_thread",
    "send_prompt",
    "fork_thread",
    "archive_thread",
    "refresh_threads",
    "hello/select_thread"
)

@Composable
private fun DiagnosticsRow(
    diagnostics: StateDiagnostics
) {
    var expanded by remember { mutableStateOf(false) }
    val action = diagnostics.actionType.takeIf(String::isNotBlank)?.let { type ->
        val status = diagnostics.actionStatus.ifBlank { "pending" }
        "$type/$status"
    } ?: "idle"
    val summary = "rev ${diagnostics.snapshotRevision} / run ${diagnostics.runningThreadIds.size} / $action"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .clickable { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = CodexTheme.colors.textSecondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = summary,
                color = CodexTheme.colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CodexTheme.colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        if (expanded) {
            Text(
                text = buildDiagnosticsText(diagnostics),
                color = CodexTheme.colors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

private fun buildDiagnosticsText(diagnostics: StateDiagnostics): String {
    val running = diagnostics.runningThreadIds.take(4).joinToString(", ").ifBlank { "-" }
    val action = listOf(
        diagnostics.actionTraceId.ifBlank { "-" },
        diagnostics.actionType.ifBlank { "-" },
        diagnostics.actionStatus.ifBlank { "-" }
    ).joinToString(" / ")
    return listOf(
        "selected: ${diagnostics.selectedThreadId.ifBlank { "-" }}",
        "pending: ${diagnostics.pendingSelectionThreadId.ifBlank { "-" }}",
        "generating: ${diagnostics.isGenerating}",
        "running: $running",
        "action: $action"
    ).joinToString("\n")
}

@Composable
private fun AppUpdatePrompt(
    state: AppUpdateState,
    onDownload: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    when (state.status) {
        AppUpdateStatus.AVAILABLE -> UpdateNoticeActionsRow(
            icon = Icons.Filled.Download,
            title = "发现新版本 ${state.latestVersion}",
            actions = listOf(
                UpdateNoticeAction(label = "系统下载", onClick = onDownload),
                UpdateNoticeAction(label = "发布页", onClick = onOpenReleasePage)
            )
        )
        AppUpdateStatus.DOWNLOAD_QUEUED -> UpdateNoticeRow(
            icon = Icons.Filled.Download,
            title = state.message.ifBlank { "已交给系统下载器" },
            action = "等待安装",
            loading = false,
            onClick = null
        )
        AppUpdateStatus.RELEASE_PAGE_OPENED -> UpdateNoticeRow(
            icon = Icons.Filled.Download,
            title = state.message.ifBlank { "已打开 GitHub 发布页" },
            action = "已打开",
            loading = false,
            onClick = null
        )
        AppUpdateStatus.ERROR -> {
            if (state.latestVersion.isBlank() && state.downloadUrl.isBlank()) return
            UpdateNoticeRow(
                icon = Icons.Filled.Warning,
                title = state.message.ifBlank { "更新失败" },
                action = "发布页",
                loading = false,
                onClick = onOpenReleasePage
            )
        }
        else -> Unit
    }
}

private data class UpdateNoticeAction(
    val label: String,
    val onClick: (() -> Unit)? = null
)

@Composable
private fun UpdateNoticeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    action: String,
    loading: Boolean,
    onClick: (() -> Unit)?
) {
    val warning = Color(0xFFD97706)
    val actionable = !loading && onClick != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF7ED))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(8.dp))
            .clickable(enabled = actionable, onClick = { onClick?.invoke() })
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
            color = if (actionable) warning else Color(0xFFB45309),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun UpdateNoticeActionsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    actions: List<UpdateNoticeAction>
) {
    val warning = Color(0xFFD97706)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF7ED))
            .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(8.dp))
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
                imageVector = icon,
                contentDescription = null,
                tint = warning,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                color = Color(0xFF9A3412),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Row(
            modifier = Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                val actionable = action.onClick != null
                Text(
                    text = action.label,
                    color = if (actionable) warning else Color(0xFFB45309),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = actionable) { action.onClick?.invoke() },
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
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
