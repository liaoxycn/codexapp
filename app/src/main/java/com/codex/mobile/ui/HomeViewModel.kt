package com.codex.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codex.mobile.data.DefaultSessionRepository
import com.codex.mobile.data.SessionRepository
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.ThreadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository: SessionRepository = DefaultSessionRepository(application.applicationContext)
    private val composerText = MutableStateFlow("")
    private val composerDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val showComposerDetails = MutableStateFlow(false)
    private var lastSelectedThreadId: String = repository.state.value.selectedThreadId
    private var liveRefreshJob: Job? = null
    private var liveRefreshTargetId: String? = null
    private var manualRefreshJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts: Int = 0
    private var manualDisconnect: Boolean = false

    init {
        val config = repository.state.value.gatewayConfig
        if (config.url.isNotBlank()) {
            viewModelScope.launch {
                repository.connect(config)
            }
        }
        viewModelScope.launch {
            repository.state.collect { snapshot ->
                val threadId = snapshot.selectedThreadId
                if (threadId.isNotBlank() && threadId != lastSelectedThreadId) {
                    lastSelectedThreadId = threadId
                    composerText.value = composerDrafts.value[threadId].orEmpty()
                }
                updateReconnectPolicy(snapshot)
                syncLiveRefresh(snapshot)
            }
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        repository.state,
        composerText,
        showComposerDetails
    ) { remote, text, expanded ->
        remote.toHomeState(
            composer = text,
            composerExpanded = expanded
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.state.value.toHomeState(
            composer = "",
            composerExpanded = true
        )
    )

    fun selectThread(id: String) {
        viewModelScope.launch {
            repository.selectThread(id)
        }
    }

    fun createThread() {
        viewModelScope.launch {
            repository.createThread()
        }
    }

    fun refreshThreads() {
        viewModelScope.launch {
            repository.refreshThreads()
        }
    }

    fun refreshThreadsAnimated() {
        refreshCurrentThreadAnimated()
    }

    fun refreshCurrentThreadAnimated() {
        if (manualRefreshJob?.isActive == true) return
        manualRefreshJob = viewModelScope.launch {
            repository.markManualRefreshing(true)
            try {
                repository.refreshThreads()
                delay(650)
            } finally {
                repository.markManualRefreshing(false)
            }
        }
    }

    fun loadOlderMessages() {
        viewModelScope.launch {
            repository.loadOlderMessages()
        }
    }

    fun toggleComposerDetails() {
        showComposerDetails.update { !it }
    }

    fun closeComposerDetails() {
        showComposerDetails.value = false
    }

    fun updateComposer(text: String) {
        setComposer(text)
    }

    fun insertComposerText(text: String) {
        val current = composerText.value
        setComposer(
            when {
            current.isBlank() -> text
            current.endsWith(" ") || text.startsWith(" ") -> current + text
            else -> "$current $text"
            }
        )
    }

    fun applySlashCommand(command: String) {
        val current = composerText.value
        val trimmedEnd = current.trimEnd()
        val trailingWhitespace = current.drop(trimmedEnd.length)
        val lastSeparator = trimmedEnd.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
        val trailingToken = if (lastSeparator >= 0) trimmedEnd.substring(lastSeparator + 1) else trimmedEnd

        setComposer(
            if (trailingToken.startsWith("/") || trailingToken.startsWith("!")) {
                val prefix = if (lastSeparator >= 0) trimmedEnd.substring(0, lastSeparator + 1) else ""
                prefix + command + if (trailingWhitespace.isEmpty()) " " else trailingWhitespace
            } else {
                when {
                    trimmedEnd.isBlank() -> "$command "
                    trimmedEnd.endsWith(" ") -> "$trimmedEnd$command "
                    else -> "$trimmedEnd $command "
                }
            }
        )
    }

    fun compactContext() {
        setComposer("/compact ")
        send()
    }

    fun clearComposer() {
        setComposer("")
    }

    fun insertGoalTemplate() {
        val current = composerText.value.trim()
        setComposer(
            if (current.isBlank()) {
                "/goal "
            } else {
                "$current\n/goal "
            }
        )
    }

    fun insertShellTemplate() {
        val current = composerText.value.trim()
        setComposer(
            if (current.isBlank()) {
                "! "
            } else {
                "$current\n! "
            }
        )
    }

    fun replaceComposer(text: String) {
        setComposer(text)
    }

    fun send() {
        val threadId = currentThreadId()
        val prompt = composerText.value.trim()
        if (prompt.isBlank()) return
        viewModelScope.launch {
            val accepted = repository.sendPrompt(prompt)
            if (accepted) {
                composerText.value = ""
                if (threadId.isNotBlank()) {
                    composerDrafts.update { drafts -> drafts - threadId }
                }
            }
        }
    }

    fun stopGenerating() {
        viewModelScope.launch {
            repository.stopTurn()
        }
    }

    fun approvePending() {
        viewModelScope.launch {
            repository.approvePending()
        }
    }

    fun rejectPending() {
        viewModelScope.launch {
            repository.rejectPending()
        }
    }

    fun connect(url: String, pairToken: String) {
        manualDisconnect = false
        viewModelScope.launch {
            repository.connect(
                GatewayConfig(
                    url = url,
                    pairToken = pairToken
                )
            )
        }
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        viewModelScope.launch {
            repository.disconnect()
        }
    }

    private fun setComposer(text: String) {
        composerText.value = text
        val threadId = currentThreadId()
        if (threadId.isBlank()) {
            return
        }
        composerDrafts.update { drafts ->
            if (text.isBlank()) {
                drafts - threadId
            } else {
                drafts + (threadId to text)
            }
        }
    }

    private fun currentThreadId(): String {
        val selected = repository.state.value.selectedThreadId
        return if (selected.isNotBlank()) selected else lastSelectedThreadId
    }

    private fun updateReconnectPolicy(snapshot: SessionRemoteState) {
        if (snapshot.connectionStatus == com.codex.mobile.model.ConnectionStatus.CONNECTED) {
            reconnectAttempts = 0
            reconnectJob?.cancel()
            reconnectJob = null
            return
        }

        val shouldReconnect =
            !manualDisconnect &&
                snapshot.gatewayConfig.url.isNotBlank() &&
                snapshot.connectionStatus != com.codex.mobile.model.ConnectionStatus.CONNECTING

        if (!shouldReconnect || reconnectJob != null) {
            return
        }

        reconnectJob = viewModelScope.launch {
            val delayMs = ((reconnectAttempts.coerceAtMost(4) + 1) * 1500L).coerceAtMost(6000L)
            delay(delayMs)
            val current = repository.state.value
            if (manualDisconnect || current.connectionStatus == com.codex.mobile.model.ConnectionStatus.CONNECTED) {
                return@launch
            }
            reconnectAttempts += 1
            repository.connect(current.gatewayConfig)
        }.also { job ->
            job.invokeOnCompletion {
                if (reconnectJob === job) {
                    reconnectJob = null
                }
            }
        }
    }

    private fun syncLiveRefresh(snapshot: SessionRemoteState) {
        val shouldPoll =
            snapshot.connectionStatus == com.codex.mobile.model.ConnectionStatus.CONNECTED &&
                snapshot.selectedThreadId.isNotBlank() &&
                (
                    snapshot.isGenerating ||
                    snapshot.pendingApproval != null ||
                    snapshot.isThreadSwitching
                    )
        val targetId = snapshot.selectedThreadId.takeIf { it.isNotBlank() }

        if (!shouldPoll || targetId == null) {
            liveRefreshJob?.cancel()
            liveRefreshJob = null
            liveRefreshTargetId = null
            return
        }

        if (liveRefreshJob != null && liveRefreshTargetId == targetId) {
            return
        }

        liveRefreshJob?.cancel()
        liveRefreshTargetId = targetId
        liveRefreshJob = viewModelScope.launch {
            while (true) {
                delay(1200)
                val current = repository.state.value
                val stillRunning =
                    current.connectionStatus == com.codex.mobile.model.ConnectionStatus.CONNECTED &&
                        current.selectedThreadId == targetId &&
                        (
                            current.isGenerating ||
                                current.pendingApproval != null ||
                                current.isThreadSwitching
                )
                if (!stillRunning) {
                    break
                }
                if (!current.isManualRefreshing) {
                    repository.markManualRefreshing(true)
                }
                repository.refreshThreads()
            }
        }
    }
}

private fun SessionRemoteState.toHomeState(
    composer: String,
    composerExpanded: Boolean
): HomeUiState = HomeUiState(
    threads = threads,
    selectedThreadId = selectedThreadId,
    pendingThreadTitle = pendingThreadTitle,
    isThreadSwitching = isThreadSwitching,
    messages = messages,
    hasMoreHistory = hasMoreHistory,
    isLoadingOlder = isLoadingOlder,
    composerText = composer,
    isGenerating = isGenerating,
    showComposerDetails = composerExpanded,
    chips = chips,
    slashCommands = slashCommands,
    pendingApproval = pendingApproval,
    cwd = cwd,
    permissionSummary = permissionSummary,
    connectionStatus = connectionStatus,
    connectionDetail = connectionDetail,
    gatewayConfig = gatewayConfig,
    isDemoMode = isDemoMode,
    isManualRefreshing = isManualRefreshing
)
