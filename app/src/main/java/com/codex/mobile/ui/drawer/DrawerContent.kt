package com.codex.mobile.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.ThreadSummary
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun DrawerContent(
    state: HomeUiState,
    onCreateThread: () -> Unit,
    onCreateThreadInProject: (String) -> Unit,
    onRefreshThreads: () -> Unit,
    onSelectThread: (String) -> Unit,
    onRenameThread: (String, String) -> Unit,
    onArchiveThread: (String) -> Unit,
    onUnarchiveThread: (String) -> Unit
) {
    var renamingThread by remember { mutableStateOf<ThreadSummary?>(null) }
    val sectionsState = rememberDrawerSectionsState(
        threads = state.threads,
        selectedThreadId = state.selectedThreadId,
    )

    renamingThread?.let { thread ->
        RenameThreadDialog(
            initialName = thread.title,
            onDismiss = { renamingThread = null },
            onConfirm = { name ->
                onRenameThread(thread.id, name)
                renamingThread = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .width(304.dp)
            .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
            .background(CodexTheme.colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        DrawerHeader(
            connectionStatus = state.connectionStatus,
            connectionDetail = state.connectionDetail,
            onCreateThread = onCreateThread,
            onRefreshThreads = onRefreshThreads,
        )
        Spacer(Modifier.height(10.dp))
        DrawerSearchBar(
            query = sectionsState.query,
            onChange = sectionsState.onQueryChange
        )
        Spacer(Modifier.height(8.dp))
        DrawerThreadList(
            selectedThreadId = state.selectedThreadId,
            sections = sectionsState.sections,
            onCreateThreadInProject = onCreateThreadInProject,
            onSelectThread = onSelectThread,
            onRenameThread = { renamingThread = it },
            onArchiveThread = onArchiveThread,
            onUnarchiveThread = onUnarchiveThread,
            onToggleProjectGroup = sectionsState.onToggleProjectGroup,
        )
    }
}
