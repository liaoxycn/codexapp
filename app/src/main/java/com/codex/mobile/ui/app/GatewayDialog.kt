package com.codex.mobile.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.ui.theme.CodexTheme
import kotlinx.coroutines.delay

@Composable
internal fun GatewayDialog(
    config: GatewayConfig,
    isConnected: Boolean,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    val state = rememberGatewayDialogState(config)
    var pendingAction by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingAction) {
        if (pendingAction != null) {
            delay(3500L)
            pendingAction = null
        }
    }
    LaunchedEffect(isConnected) {
        if (isConnected && pendingAction == "connect") {
            onDismiss()
        } else if (!isConnected && pendingAction == "disconnect") {
            onDismiss()
        }
        pendingAction = null
    }

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
                onClick = {
                    pendingAction = "connect"
                    onConnect(state.url, state.pairToken)
                },
                enabled = state.canConnect && pendingAction == null,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                if (pendingAction == "connect") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = CodexTheme.colors.surface,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("连接中", fontSize = 15.sp, lineHeight = 19.sp)
                } else {
                    Text("连接", fontSize = 15.sp, lineHeight = 19.sp)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isConnected) {
                    TextButton(
                        onClick = {
                            pendingAction = "disconnect"
                            onDisconnect()
                        },
                        enabled = pendingAction == null,
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        if (pendingAction == "disconnect") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = CodexTheme.colors.danger,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("断开中", color = CodexTheme.colors.danger, fontSize = 14.sp)
                        } else {
                            Text("断开连接", color = CodexTheme.colors.danger, fontSize = 14.sp)
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = pendingAction == null,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Text("取消", color = CodexTheme.colors.textPrimary, fontSize = 14.sp)
                }
            }
        }
    )
}
