package com.codexapp.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codexapp.model.ConnectionStatus
import com.codexapp.ui.common.TopBar
import com.codexapp.ui.composer.Composer
import com.codexapp.ui.drawer.DrawerContent
import com.codexapp.ui.state.HomeViewModel
import com.codexapp.ui.thread.ThreadScreen
import com.codexapp.ui.theme.CodexTheme

@Composable
fun CodexApp(
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val controller = rememberCodexAppController(
        state = state,
        viewModel = viewModel,
    )
    val selectedThreadChrome = resolveSelectedThreadChrome(
        threads = state.threads,
        selectedThreadId = state.selectedThreadId,
        isNewThreadDraft = state.isNewThreadDraft,
    )
    var pendingForkNumTurns by remember { mutableStateOf<Int?>(null) }
    BackHandler(enabled = true, onBack = controller.handleBackPress)

    if (controller.showGatewayDialog) {
        GatewayDialog(
            config = state.gatewayConfig,
            isConnected = state.connectionStatus == ConnectionStatus.CONNECTED,
            onDismiss = controller.dismissGatewayDialog,
            onConnect = controller.connectGateway,
            onDisconnect = controller.disconnectGateway,
        )
    }
    pendingForkNumTurns?.let { numTurns ->
        ForkThreadConfirmDialog(
            onDismiss = { pendingForkNumTurns = null },
            onConfirm = {
                pendingForkNumTurns = null
                if (!state.isForkingThread) {
                    viewModel.forkThread(state.selectedThreadId, numTurns)
                }
            }
        )
    }

    ModalDrawer(
        drawerState = controller.drawerState,
        drawerContent = {
            DrawerContent(
                state = state,
                onCreateThread = controller.createThread,
                onCreateThreadInProject = controller.createThreadInProject,
                onOpenConnection = controller.openGatewayDialog,
                onRefreshThreads = viewModel::refreshThreadsAnimated,
                onSelectThread = controller.selectThread,
                onRenameThread = { id, name ->
                    viewModel.renameThread(id, name)
                    controller.closeDrawer()
                },
                onArchiveThread = { id ->
                    viewModel.archiveThread(id)
                    controller.closeDrawer()
                },
                onRestartDesktop = viewModel::restartDesktop,
                onDownloadUpdate = viewModel::downloadAppUpdate,
                onOpenUpdateReleasePage = viewModel::openAppUpdateReleasePage,
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CodexTheme.colors.background),
                topBar = {
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        TopBar(
                            title = selectedThreadChrome.title,
                            status = selectedThreadChrome.status,
                            onOpenDrawer = controller.openDrawer,
                        )
                    }
                },
                bottomBar = {
                    Composer(
                        state = state,
                        compactMode = controller.compactMode,
                        activePanel = controller.composerPanel,
                        onActivePanelChange = controller.onComposerPanelChange,
                        onToggleDetails = viewModel::toggleComposerDetails,
                        onCloseDetails = viewModel::closeComposerDetails,
                        onChange = viewModel::updateComposer,
                        onInsertText = viewModel::insertComposerText,
                        onApplySlashCommand = viewModel::applySlashCommand,
                        onDraftChange = { draft -> viewModel.updateNewThreadDraft { draft } },
                        onClearComposer = viewModel::clearComposer,
                        onSend = viewModel::send,
                        onStop = viewModel::stopGenerating
                    )
                },
                backgroundColor = CodexTheme.colors.background
            ) { padding ->
                ThreadScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    compactMode = controller.compactMode,
                    onOpenConnection = controller.openGatewayDialog,
                    onRefreshCurrent = viewModel::refreshCurrentThreadAnimated,
                    onLoadOlderMessages = viewModel::loadOlderMessages,
                    onEditUserMessage = viewModel::editAndResendUserMessage,
                    onResendUserMessage = viewModel::resendUserMessage,
                    onForkFromMessage = { numTurns ->
                        pendingForkNumTurns = numTurns
                    },
                    onNewThreadDraftChange = { draft -> viewModel.updateNewThreadDraft { draft } },
                    onApprovePending = viewModel::approvePending,
                    onRejectPending = viewModel::rejectPending
                )
            }
            OperationalNoticeOverlay(notices = state.operationalNotices)
            val globalLoadingText = resolveGlobalLoadingText(state)
            GlobalLoadingOverlay(
                visible = globalLoadingText != null,
                text = globalLoadingText.orEmpty()
            )
        }
    }
}
