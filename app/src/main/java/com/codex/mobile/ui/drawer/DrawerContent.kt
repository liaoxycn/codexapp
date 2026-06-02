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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary
import com.codex.mobile.ui.theme.CodexTheme

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DrawerContent(
    state: HomeUiState,
    onCreateThread: () -> Unit,
    onCreateThreadInProject: (String) -> Unit,
    onOpenConnection: () -> Unit,
    onRefreshThreads: () -> Unit,
    onSelectThread: (String) -> Unit,
    onRenameThread: (String, String) -> Unit,
    onArchiveThread: (String) -> Unit,
    onUnarchiveThread: (String) -> Unit,
    onRestartDesktop: () -> Unit,
    onDownloadUpdate: () -> Unit = {},
) {
    var renamingThread by remember { mutableStateOf<ThreadSummary?>(null) }
    var confirmingDesktopRestart by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sectionsState = rememberDrawerSectionsState(
        threads = state.threads,
        selectedThreadId = state.selectedThreadId,
    )
    fun clearDrawerInput() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    renamingThread?.let { thread ->
        RenameThreadDialog(
            initialName = thread.title,
            onDismiss = { renamingThread = null },
            onConfirm = { name ->
                onRenameThread(thread.id, name)
            }
        )
    }
    if (confirmingDesktopRestart) {
        RestartDesktopConfirmDialog(
            onDismiss = { confirmingDesktopRestart = false },
            onConfirm = {
                onRestartDesktop()
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
            desktopRestartRequired = state.desktopRestartRequired,
            appUpdate = state.appUpdate,
            hasRunningThread = state.threads.any { it.status == ThreadStatus.RUNNING },
            isRefreshing = state.isManualRefreshing,
            onCreateThread = {
                clearDrawerInput()
                onCreateThread()
            },
            onRefreshThreads = {
                clearDrawerInput()
                onRefreshThreads()
            },
            onOpenConnection = {
                clearDrawerInput()
                onOpenConnection()
            },
            onRestartDesktop = {
                clearDrawerInput()
                confirmingDesktopRestart = true
            },
            onDownloadUpdate = {
                clearDrawerInput()
                onDownloadUpdate()
            },
        )
        Spacer(Modifier.height(10.dp))
        DrawerThreadList(
            selectedThreadId = state.selectedThreadId,
            sections = sectionsState.sections,
            onCreateThreadInProject = { cwd ->
                clearDrawerInput()
                onCreateThreadInProject(cwd)
            },
            onSelectThread = { threadId ->
                clearDrawerInput()
                onSelectThread(threadId)
            },
            onRenameThread = {
                clearDrawerInput()
                renamingThread = it
            },
            onArchiveThread = { threadId ->
                clearDrawerInput()
                onArchiveThread(threadId)
            },
            onUnarchiveThread = { threadId ->
                clearDrawerInput()
                onUnarchiveThread(threadId)
            },
            onToggleProjectGroup = sectionsState.onToggleProjectGroup,
        )
    }
}
