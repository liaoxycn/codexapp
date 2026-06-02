package com.codexapp.ui.app

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.codexapp.model.HomeUiState
import com.codexapp.ui.composer.ComposerPanel
import com.codexapp.ui.state.HomeViewModel
import kotlinx.coroutines.launch
import android.app.Activity
import android.widget.Toast

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
    val closeDrawer: () -> Unit,
    val onComposerPanelChange: (ComposerPanel) -> Unit,
)

@Composable
internal fun rememberCodexAppController(
    state: HomeUiState,
    viewModel: HomeViewModel,
): CodexAppController {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showGatewayDialog by rememberSaveable { mutableStateOf(false) }
    var compactMode by rememberSaveable { mutableStateOf(true) }
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
            (context as? Activity)?.finish()
        } else {
            lastBackPressAt = now
            Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
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
        },
        disconnectGateway = {
            viewModel.disconnect()
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
        closeDrawer = {
            scope.launch { drawerState.close() }
        },
        onComposerPanelChange = { composerPanel = it },
    )
}
