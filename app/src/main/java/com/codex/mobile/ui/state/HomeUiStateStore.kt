package com.codex.mobile.ui.state

import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.SessionRemoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal class HomeUiStateStore(
    remoteState: StateFlow<SessionRemoteState>,
    composerText: StateFlow<String>,
    scope: CoroutineScope
) {
    private val showComposerDetails = MutableStateFlow(false)
    private val composerFocusRequest = MutableStateFlow(0L)

    val state: StateFlow<HomeUiState> = combine(
        remoteState,
        composerText,
        showComposerDetails,
        composerFocusRequest
    ) { remote, text, expanded, focusRequest ->
        remote.toHomeState(
            composer = text,
            composerExpanded = expanded,
            composerFocusRequest = focusRequest
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = remoteState.value.toHomeState(
            composer = composerText.value,
            composerExpanded = showComposerDetails.value,
            composerFocusRequest = composerFocusRequest.value
        )
    )

    fun toggleComposerDetails() {
        showComposerDetails.update { !it }
    }

    fun closeComposerDetails() {
        showComposerDetails.value = false
    }

    fun requestComposerFocus() {
        composerFocusRequest.update { it + 1L }
    }
}
