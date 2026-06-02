package com.codexapp.ui.state

import com.codexapp.data.SessionRepository
import com.codexapp.model.HomeUiState
import com.codexapp.model.AppUpdateStatus
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
        isNewThreadDraft = { uiStateStore.state.value.isNewThreadDraft },
        newThreadDraft = { uiStateStore.state.value.newThreadDraft.takeIf { uiStateStore.state.value.isNewThreadDraft } },
        composerConfigDraft = {
            uiStateStore.state.value.composerConfigDraft.takeIf {
                uiStateStore.state.value.isNewThreadDraft || uiStateStore.state.value.selectedThreadId.isNotBlank()
            }
        },
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

    fun selectThread(id: String, onComplete: (Boolean) -> Unit = {}) {
        repositoryActions.selectThread(id) { sent ->
            if (sent) {
                val title = repository.state.value.threads.firstOrNull { it.id == id }?.title
                uiStateStore.markThreadSelectionStarted(id, title)
                if (repository.state.value.selectedThreadId == id) {
                    uiStateStore.syncRemoteSelection(repository.state.value)
                }
            }
            onComplete(sent)
        }
    }

    fun createThread(cwd: String? = null, onComplete: (Boolean) -> Unit = {}) {
        uiStateStore.startNewThreadDraft(cwd)
        onComplete(true)
    }

    fun forkThread(id: String, numTurns: Int? = null) {
        repositoryActions.forkThread(id, numTurns) { sent ->
            if (sent) {
                uiStateStore.markForkStarted(id)
                scope.launch {
                    delay(12_000L)
                    uiStateStore.clearForkIfSource(id)
                }
            }
        }
    }

    fun renameThread(id: String, name: String) {
        repositoryActions.renameThread(id, name)
    }

    fun archiveThread(id: String, onComplete: (Boolean) -> Unit = {}) {
        val beforeSend = repository.state.value
        repositoryActions.archiveThread(id) { sent ->
            if (sent) {
                uiStateStore.markArchiveStarted(id, beforeSend)
                uiStateStore.syncRemoteSelection(repository.state.value)
            }
            onComplete(sent)
        }
    }

    fun updateNewThreadDraft(transform: (com.codexapp.model.NewThreadDraft) -> com.codexapp.model.NewThreadDraft) {
        uiStateStore.updateNewThreadDraft(transform)
    }

    fun updateCurrentThreadConfig(transform: (com.codexapp.model.NewThreadDraft) -> com.codexapp.model.NewThreadDraft) {
        uiStateStore.updateCurrentThreadConfig(repository.state.value, transform)
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
        uiStateStore.setPendingEditResend(composerActions.pendingEditResendState())
    }

    fun insertShellTemplate() {
        composerActions.insertShellTemplate()
    }

    fun replaceComposer(text: String) {
        composerActions.replaceComposer(text)
        uiStateStore.setPendingEditResend(composerActions.pendingEditResendState())
        uiStateStore.requestComposerFocus()
    }

    fun editAndResendUserMessage(text: String, rollbackNumTurns: Int) {
        composerActions.editAndResendText(text, rollbackNumTurns)
        uiStateStore.setPendingEditResend(composerActions.pendingEditResendState())
        uiStateStore.requestComposerFocus()
    }

    fun resendUserMessage(text: String, rollbackNumTurns: Int) {
        scope.launch {
            repository.resendPrompt(
                text,
                rollbackNumTurns,
                uiStateStore.state.value.composerConfigDraft.takeIf { !uiStateStore.state.value.isNewThreadDraft }
            )
        }
    }

    fun send() {
        composerActions.send()
        uiStateStore.setPendingEditResend(composerActions.pendingEditResendState())
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

    fun openAppUpdateReleasePage() {
        val current = uiStateStore.state.value.appUpdate
        if (current.releasePageUrl.isBlank() && current.latestVersion.isBlank()) return
        uiStateStore.updateAppUpdate(appUpdateManager.openReleasePage(current, "已打开 GitHub 发布页"))
    }
}
