package com.codex.mobile.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun GatewayDialog(
    config: GatewayConfig,
    isConnected: Boolean,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    val state = rememberGatewayDialogState(config)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "连接 Desktop Gateway",
                color = CodexTheme.colors.textPrimary,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GatewayDialogStatus(isConnected = isConnected)
                GatewayDialogFields(state = state)
                GatewayDialogHelperText()
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(state.url, state.pairToken) },
                enabled = state.canConnect,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Text("连接", fontSize = 15.sp, lineHeight = 19.sp)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isConnected) {
                    TextButton(
                        onClick = onDisconnect,
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text("断开连接", color = CodexTheme.colors.danger, fontSize = 14.sp)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Text("取消", color = CodexTheme.colors.textPrimary, fontSize = 14.sp)
                }
            }
        }
    )
}
