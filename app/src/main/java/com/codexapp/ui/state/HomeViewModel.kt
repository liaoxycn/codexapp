package com.codexapp.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codexapp.data.DefaultSessionRepository
import com.codexapp.model.HomeUiState
import com.codexapp.model.NewThreadDraft
import com.codexapp.update.AppUpdateManager
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val delegate = HomeViewModelDelegate(
        repository = DefaultSessionRepository(application.applicationContext),
        scope = viewModelScope,
        appUpdateManager = AppUpdateManager(application.applicationContext)
    )

    val state: StateFlow<HomeUiState> = delegate.state

    fun selectThread(id: String) = delegate.selectThread(id)

    fun createThread(cwd: String? = null) = delegate.createThread(cwd)

    fun updateNewThreadDraft(transform: (NewThreadDraft) -> NewThreadDraft) = delegate.updateNewThreadDraft(transform)

    fun forkThread(id: String, numTurns: Int? = null) = delegate.forkThread(id, numTurns)

    fun renameThread(id: String, name: String) = delegate.renameThread(id, name)

    fun archiveThread(id: String) = delegate.archiveThread(id)

    fun unarchiveThread(id: String) = delegate.unarchiveThread(id)

    fun refreshThreads() = delegate.refreshThreads()

    fun refreshThreadsAnimated() = delegate.refreshThreadsAnimated()

    fun refreshCurrentThreadAnimated() = delegate.refreshCurrentThreadAnimated()

    fun loadOlderMessages() = delegate.loadOlderMessages()

    fun toggleComposerDetails() = delegate.toggleComposerDetails()

    fun closeComposerDetails() = delegate.closeComposerDetails()

    fun updateComposer(text: String) = delegate.updateComposer(text)

    fun insertComposerText(text: String) = delegate.insertComposerText(text)

    fun applySlashCommand(command: String) = delegate.applySlashCommand(command)

    fun compactContext() = delegate.compactContext()

    fun rollbackLastTurn() = delegate.rollbackLastTurn()

    fun clearComposer() = delegate.clearComposer()

    fun insertShellTemplate() = delegate.insertShellTemplate()

    fun replaceComposer(text: String) = delegate.replaceComposer(text)

    fun editAndResendUserMessage(text: String, rollbackNumTurns: Int) =
        delegate.editAndResendUserMessage(text, rollbackNumTurns)

    fun resendUserMessage(text: String, rollbackNumTurns: Int) = delegate.resendUserMessage(text, rollbackNumTurns)

    fun send() = delegate.send()

    fun stopGenerating() = delegate.stopGenerating()

    fun approvePending() = delegate.approvePending()

    fun rejectPending() = delegate.rejectPending()

    fun restartDesktop() = delegate.restartDesktop()

    fun connect(url: String, pairToken: String) = delegate.connect(url, pairToken)

    fun disconnect() = delegate.disconnect()

    fun checkAppUpdate() = delegate.checkAppUpdate()

    fun downloadAppUpdate() = delegate.downloadAppUpdate()
}
