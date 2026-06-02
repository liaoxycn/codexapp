package com.codexapp.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun GatewayDialogFields(
    state: GatewayDialogState
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GatewayDialogTextField(
            value = state.url,
            onValueChange = { state.url = it },
            label = "WebSocket 地址",
            placeholder = "ws://10.0.2.2:8765/mobile"
        )
        GatewayDialogTextField(
            value = state.pairToken,
            onValueChange = { state.pairToken = it },
            label = "配对码 / token",
            placeholder = "可选"
        )
    }
}

@Composable
private fun GatewayDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 13.sp) },
        placeholder = { Text(placeholder, fontSize = 14.sp) },
        textStyle = TextStyle(
            color = CodexTheme.colors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 18.sp
        ),
        singleLine = true
    )
}
