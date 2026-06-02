package com.codexapp.ui.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.model.AppUpdateState
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun AppUpdateConfirmDialog(
    state: AppUpdateState,
    onDismiss: () -> Unit,
    onDownloadWithSystem: () -> Unit,
    onOpenReleasePage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "发现新版本 ${state.latestVersion}",
                color = CodexTheme.colors.textPrimary,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = buildUpdatePrompt(state),
                    color = CodexTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = state.releaseNotes.toDialogReleaseNotes(),
                    color = CodexTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onDownloadWithSystem()
                },
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Text("系统下载器下载", fontSize = 15.sp, lineHeight = 19.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onOpenReleasePage()
                },
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Text("浏览器打开 GitHub", color = CodexTheme.colors.textPrimary, fontSize = 14.sp)
            }
        }
    )
}

private fun buildUpdatePrompt(state: AppUpdateState): String {
    val local = state.localVersion.ifBlank { "当前版本" }
    val latest = state.latestVersion.ifBlank { "最新版本" }
    return "当前版本 $local，可更新到 $latest。请选择下载 APK 的方式。"
}

private fun String.toDialogReleaseNotes(): String {
    val normalized = lineSequence()
        .map { it.trim().trimStart('#', '*', '-', ' ') }
        .filter(String::isNotBlank)
        .take(8)
        .joinToString("\n")
    return normalized.ifBlank { "新版更新说明暂未提供，请打开 GitHub 发布页查看详情。" }
}
