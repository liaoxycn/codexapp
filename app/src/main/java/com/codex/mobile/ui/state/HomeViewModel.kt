package com.codex.mobile.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codex.mobile.data.DefaultSessionRepository
import com.codex.mobile.model.HomeUiState
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val delegate = HomeViewModelDelegate(
        repository = DefaultSessionRepository(application.applicationContext),
        scope = viewModelScope
    )

    val state: StateFlow<HomeUiState> = delegate.state

    fun selectThread(id: String) = delegate.selectThread(id)

    fun createThread(cwd: String? = null) = delegate.createThread(cwd)

    fun forkThread(id: String) = delegate.forkThread(id)

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

    fun clearComposer() = delegate.clearComposer()

    fun insertGoalTemplate() = delegate.insertGoalTemplate()

    fun insertShellTemplate() = delegate.insertShellTemplate()

    fun replaceComposer(text: String) = delegate.replaceComposer(text)

    fun resendUserMessage(text: String) = delegate.resendUserMessage(text)

    fun send() = delegate.send()

    fun stopGenerating() = delegate.stopGenerating()

    fun approvePending() = delegate.approvePending()

    fun rejectPending() = delegate.rejectPending()

    fun connect(url: String, pairToken: String) = delegate.connect(url, pairToken)

    fun disconnect() = delegate.disconnect()
}
