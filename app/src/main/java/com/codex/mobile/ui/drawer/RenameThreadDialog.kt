package com.codex.mobile.ui.drawer

import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.codex.mobile.ui.theme.CodexTheme
import kotlinx.coroutines.delay

@Composable
internal fun RenameThreadDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var saving by remember(initialName) { mutableStateOf(false) }
    val trimmedName = name.trim()
    LaunchedEffect(saving) {
        if (saving) {
            delay(650L)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("名称") },
                modifier = Modifier.testTag("rename_thread_field")
            )
        },
        confirmButton = {
            Button(
                enabled = trimmedName.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    onConfirm(trimmedName)
                },
                modifier = Modifier.testTag("rename_thread_confirm")
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = CodexTheme.colors.surface,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("保存中")
                } else {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !saving,
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
}
