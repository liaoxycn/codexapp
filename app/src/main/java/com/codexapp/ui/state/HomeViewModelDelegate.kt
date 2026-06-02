package com.codexapp.ui.state

import com.codexapp.data.SessionRepository
import com.codexapp.model.HomeUiState
import com.codexapp.model.AppUpdateStatus
import com.codexapp.model.ConnectionStatus
import com.codexapp.update.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class HomeViewModelDelegate(
    private val repository: SessionRepository,
    private val scope: CoroutineScope,
    private val appUpdateManager: AppUpdateManager
) {
    private val composerSession = ComposerSession(repository.state.value.selectedThreadId)
    private val uiStateStore = HomeUiStateStore(
        remoteState = repository.state,
        composerText = composerSession.text,
        scope = scope
    )
    private val repositoryCoordinator = HomeRepositoryCoordinator(
        repository = repository,
        scope = scope,
        composerSession = composerSession
    )
    private val composerActions = ComposerActionHandler(
        composerSession = composerSession,
        selectedThreadId = { uiStateStore.state.value.selectedThreadId },
        newThreadDraft = { uiStateStore.state.value.newThreadDraft.takeIf { uiStateStore.state.value.isNewThreadDraft } },
        launch = { block -> scope.launch { block() } },
        sendPrompt = repository::sendPrompt,
        resendPrompt = repository::resendPrompt,
        onPromptAccepted = { wasDraft ->
            if (wasDraft) {
                uiStateStore.markDraftSubmissionStarted()
            }
        }
    )
    private val repositoryActions = HomeRepositoryActions(
        repository = repository,
        launch = { block -> scope.launch { block() } },
        onManualConnect = repositoryCoordinator::onManualConnect,
        onManualDisconnect = repositoryCoordinator::onManualDisconnect,
        runAnimatedRefresh = repositoryCoordinator::refreshAnimated
    )

    val state: StateFlow<HomeUiState> = uiStateStore.state

    init {
        repositoryCoordinator.start()
        checkAppUpdateOnStartup()
        scope.launch {
            repository.state.collect { remote ->
                uiStateStore.syncDraftDefaults(remote)
                uiStateStore.syncRemoteSelection(remote)
            }
        }
    }

    fun selectThread(id: String) {
        uiStateStore.exitNewThreadDraft()
        repositoryActions.selectThread(id)
    }

    fun createThread(cwd: String? = null) {
        uiStateStore.startNewThreadDraft(cwd)
    }

    fun forkThread(id: String, numTurns: Int? = null) {
        if (id.isBlank() || uiStateStore.state.value.connectionStatus != ConnectionStatus.CONNECTED) {
            repositoryActions.forkThread(id, numTurns)
            return
        }
        uiStateStore.markForkStarted(id)
        repositoryActions.forkThread(id, numTurns)
        scope.launch {
            delay(12_000L)
            uiStateStore.clearForkIfSource(id)
        }
    }

    fun renameThread(id: String, name: String) {
        repositoryActions.renameThread(id, name)
    }

    fun archiveThread(id: String) {
        uiStateStore.startNewThreadDraft()
        repositoryActions.archiveThread(id)
    }

    fun updateNewThreadDraft(transform: (com.codexapp.model.NewThreadDraft) -> com.codexapp.model.NewThreadDraft) {
        uiStateStore.updateNewThreadDraft(transform)
    }

    fun unarchiveThread(id: String) {
        repositoryActions.unarchiveThread(id)
    }

    fun refreshThreads() {
        repositoryActions.refreshThreads()
    }

    fun refreshThreadsAnimated() {
        refreshCurrentThreadAnimated()
    }

    fun refreshCurrentThreadAnimated() {
        repositoryActions.refreshCurrentThreadAnimated()
    }

    fun loadOlderMessages() {
        repositoryActions.loadOlderMessages()
    }

    fun toggleComposerDetails() {
        uiStateStore.toggleComposerDetails()
    }

    fun closeComposerDetails() {
        uiStateStore.closeComposerDetails()
    }

    fun updateComposer(text: String) {
        composerActions.updateComposer(text)
    }

    fun insertComposerText(text: String) {
        composerActions.insertComposerText(text)
    }

    fun applySlashCommand(command: String) {
        composerActions.applySlashCommand(command)
    }

    fun compactContext() {
        composerActions.compactContext()
    }

    fun rollbackLastTurn() {
        composerActions.rollbackLastTurn()
    }

    fun clearComposer() {
        composerActions.clearComposer()
    }

    fun insertShellTemplate() {
        composerActions.insertShellTemplate()
    }

    fun replaceComposer(text: String) {
        composerActions.replaceComposer(text)
        uiStateStore.requestComposerFocus()
    }

    fun editAndResendUserMessage(text: String, rollbackNumTurns: Int) {
        composerActions.editAndResendText(text, rollbackNumTurns)
        uiStateStore.requestComposerFocus()
    }

    fun resendUserMessage(text: String, rollbackNumTurns: Int) {
        scope.launch {
            repository.resendPrompt(text, rollbackNumTurns)
        }
    }

    fun send() {
        composerActions.send()
    }

    fun stopGenerating() {
        repositoryActions.stopGenerating()
    }

    fun approvePending() {
        repositoryActions.approvePending()
    }

    fun rejectPending() {
        repositoryActions.rejectPending()
    }

    fun restartDesktop() {
        repositoryActions.restartDesktop()
    }

    fun connect(url: String, pairToken: String) {
        repositoryActions.connect(url, pairToken)
    }

    fun disconnect() {
        repositoryActions.disconnect()
    }

    fun checkAppUpdate() {
        scope.launch {
            uiStateStore.updateAppUpdate(
                uiStateStore.state.value.appUpdate.copy(status = AppUpdateStatus.CHECKING, message = "")
            )
            val next = appUpdateManager.checkLatest()
            uiStateStore.updateAppUpdate(next)
        }
    }

    private fun checkAppUpdateOnStartup() {
        if (appUpdateManager.consumeStartupCheck()) {
            checkAppUpdate()
        }
    }

    fun downloadAppUpdate() {
        val current = uiStateStore.state.value.appUpdate
        if (current.status != AppUpdateStatus.AVAILABLE) return
        uiStateStore.updateAppUpdate(appUpdateManager.enqueueSystemDownload(current))
    }
}
