package com.codex.mobile.ui.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.theme.CodexTheme
import kotlinx.coroutines.delay

@Composable
internal fun RestartDesktopConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var restarting by remember { mutableStateOf(false) }
    LaunchedEffect(restarting) {
        if (restarting) {
            delay(900L)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!restarting) onDismiss() },
        title = {
            Text(
                text = "重启 Codex Desktop？",
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
                    text = "移动端已有会话变更，重启桌面端后 Desktop 会重新加载会话列表。",
                    color = CodexTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = "警告：这会关闭并重新打开 Codex Desktop。未完成的桌面端输入、弹窗或本地操作可能被打断。",
                    color = CodexTheme.colors.danger,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !restarting,
                onClick = {
                    restarting = true
                    onConfirm()
                },
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                if (restarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = CodexTheme.colors.surface,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("重启中", fontSize = 15.sp, lineHeight = 19.sp)
                } else {
                    Text("确认重启", fontSize = 15.sp, lineHeight = 19.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !restarting,
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Text("取消", color = CodexTheme.colors.textPrimary, fontSize = 14.sp)
            }
        }
    )
}
