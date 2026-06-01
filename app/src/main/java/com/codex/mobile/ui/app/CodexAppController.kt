package com.codex.mobile.ui.app

import androidx.compose.material.DrawerValue
import androidx.compose.material.DrawerState
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.ui.composer.ComposerPanel
import com.codex.mobile.ui.state.HomeViewModel
import kotlinx.coroutines.launch

internal data class CodexAppController(
    val drawerState: DrawerState,
    val showGatewayDialog: Boolean,
    val compactMode: Boolean,
    val composerPanel: ComposerPanel,
    val handleBackPress: () -> Unit,
    val openGatewayDialog: () -> Unit,
    val dismissGatewayDialog: () -> Unit,
    val connectGateway: (String, String) -> Unit,
    val disconnectGateway: () -> Unit,
    val openDrawer: () -> Unit,
    val createThread: () -> Unit,
    val createThreadInProject: (String) -> Unit,
    val selectThread: (String) -> Unit,
    val onComposerPanelChange: (ComposerPanel) -> Unit,
    val toggleCompactMode: () -> Unit,
)

@Composable
internal fun rememberCodexAppController(
    state: HomeUiState,
    viewModel: HomeViewModel,
): CodexAppController {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showGatewayDialog by rememberSaveable { mutableStateOf(false) }
    var compactMode by rememberSaveable { mutableStateOf(false) }
    var composerPanel by rememberSaveable { mutableStateOf(ComposerPanel.NONE) }
    var lastBackPressAt by rememberSaveable { mutableStateOf(0L) }

    fun dismissComposerChrome() {
        composerPanel = ComposerPanel.NONE
        if (state.showComposerDetails) {
            viewModel.closeComposerDetails()
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun handleBackPress() {
        if (showGatewayDialog) {
            showGatewayDialog = false
            return
        }
        if (composerPanel != ComposerPanel.NONE) {
            composerPanel = ComposerPanel.NONE
            return
        }
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
            return
        }
        if (state.showComposerDetails) {
            viewModel.closeComposerDetails()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastBackPressAt < 1800L) {
            android.os.Process.killProcess(android.os.Process.myPid())
        } else {
            lastBackPressAt = now
        }
    }

    return CodexAppController(
        drawerState = drawerState,
        showGatewayDialog = showGatewayDialog,
        compactMode = compactMode,
        composerPanel = composerPanel,
        handleBackPress = ::handleBackPress,
        openGatewayDialog = { showGatewayDialog = true },
        dismissGatewayDialog = { showGatewayDialog = false },
        connectGateway = { url, pairToken ->
            viewModel.connect(url, pairToken)
            showGatewayDialog = false
        },
        disconnectGateway = {
            viewModel.disconnect()
            showGatewayDialog = false
        },
        openDrawer = {
            dismissComposerChrome()
            scope.launch { drawerState.open() }
        },
        createThread = {
            viewModel.createThread()
            scope.launch { drawerState.close() }
        },
        createThreadInProject = { cwd ->
            viewModel.createThread(cwd)
            scope.launch { drawerState.close() }
        },
        selectThread = { threadId ->
            viewModel.selectThread(threadId)
            scope.launch { drawerState.close() }
        },
        onComposerPanelChange = { composerPanel = it },
        toggleCompactMode = { compactMode = !compactMode },
    )
}
