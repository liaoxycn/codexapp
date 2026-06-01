package com.codex.mobile.ui.state

import com.codex.mobile.data.SessionRepository
import com.codex.mobile.model.HomeUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class HomeViewModelDelegate(
    private val repository: SessionRepository,
    private val scope: CoroutineScope
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
        selectedThreadId = { repository.state.value.selectedThreadId },
        launch = { block -> scope.launch { block() } },
        sendPrompt = repository::sendPrompt
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
    }

    fun selectThread(id: String) {
        repositoryActions.selectThread(id)
    }

    fun createThread(cwd: String? = null) {
        repositoryActions.createThread(cwd)
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

    fun clearComposer() {
        composerActions.clearComposer()
    }

    fun insertGoalTemplate() {
        composerActions.insertGoalTemplate()
    }

    fun insertShellTemplate() {
        composerActions.insertShellTemplate()
    }

    fun replaceComposer(text: String) {
        composerActions.replaceComposer(text)
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

    fun connect(url: String, pairToken: String) {
        repositoryActions.connect(url, pairToken)
    }

    fun disconnect() {
        repositoryActions.disconnect()
    }
}
