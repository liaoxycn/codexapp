package com.codex.mobile.ui.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun DrawerThreadList(
    selectedThreadId: String,
    sections: DrawerThreadSections,
    onCreateThreadInProject: (String) -> Unit,
    onSelectThread: (String) -> Unit,
    onToggleProjectGroup: (DrawerProjectGroup) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            SectionHeader(text = "项目", startPadding = 10.dp)
        }
        if (sections.projectGroups.isEmpty()) {
            item {
                DrawerEmptyState("暂无项目会话")
            }
        } else {
            sections.projectGroups.forEach { group ->
                item(key = "group-${group.label}") {
                    GroupHeader(
                        label = group.label,
                        secondaryText = group.cwd,
                        icon = Icons.Filled.Folder,
                        compact = true,
                        expanded = group.isExpanded,
                        onCreateThread = group.cwd.takeIf(String::isNotBlank)?.let { cwd ->
                            { onCreateThreadInProject(cwd) }
                        },
                        onToggle = if (group.isCurrentProject) null else {
                            { onToggleProjectGroup(group) }
                        }
                    )
                }
                if (group.isExpanded) {
                    items(
                        items = group.threads,
                        key = { thread -> thread.id }
                    ) { thread ->
                        ThreadRow(
                            summary = thread,
                            selected = thread.id == selectedThreadId,
                            indentLevel = 1,
                            onClick = { onSelectThread(thread.id) }
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(3.dp))
            SectionHeader(text = "会话", startPadding = 10.dp)
        }
        if (sections.chatThreads.isEmpty()) {
            item {
                DrawerEmptyState("暂无普通会话")
            }
        } else {
            items(
                items = sections.chatThreads,
                key = { thread -> thread.id }
            ) { thread ->
                ThreadRow(
                    summary = thread,
                    selected = thread.id == selectedThreadId,
                    indentLevel = 0,
                    onClick = { onSelectThread(thread.id) }
                )
            }
        }
    }
}

@Composable
private fun DrawerEmptyState(text: String) {
    Text(
        text = text,
        color = CodexTheme.colors.textSecondary,
        fontSize = 11.sp
    )
}
