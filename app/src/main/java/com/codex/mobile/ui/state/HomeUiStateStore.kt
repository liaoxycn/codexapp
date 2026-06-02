package com.codex.mobile.ui.state

import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.AppUpdateState
import com.codex.mobile.model.NewThreadDraft
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
    private val isNewThreadDraft = MutableStateFlow(true)
    private val draftSubmissionInFlight = MutableStateFlow(false)
    private val forkingThreadId = MutableStateFlow<String?>(null)
    private val newThreadDraft = MutableStateFlow(NewThreadDraft())
    private val appUpdate = MutableStateFlow(AppUpdateState())

    private data class HomeLocalState(
        val showComposerDetails: Boolean,
        val composerFocusRequest: Long,
        val isNewThreadDraft: Boolean,
        val draftSubmissionInFlight: Boolean,
        val forkingThreadId: String?,
        val newThreadDraft: NewThreadDraft,
        val appUpdate: AppUpdateState
    )

    private data class DraftLocalState(
        val isNewThreadDraft: Boolean,
        val draftSubmissionInFlight: Boolean,
        val forkingThreadId: String?,
        val newThreadDraft: NewThreadDraft,
        val appUpdate: AppUpdateState
    )

    private val draftLocalState = combine(
        isNewThreadDraft,
        draftSubmissionInFlight,
        forkingThreadId,
        newThreadDraft,
        appUpdate
    ) { draftMode, submittingDraft, forkingId, draft, update ->
        DraftLocalState(
            isNewThreadDraft = draftMode,
            draftSubmissionInFlight = submittingDraft,
            forkingThreadId = forkingId,
            newThreadDraft = draft,
            appUpdate = update
        )
    }

    private val localState = combine(
        showComposerDetails,
        composerFocusRequest,
        draftLocalState
    ) { expanded, focusRequest, draftState ->
        HomeLocalState(
            showComposerDetails = expanded,
            composerFocusRequest = focusRequest,
            isNewThreadDraft = draftState.isNewThreadDraft,
            draftSubmissionInFlight = draftState.draftSubmissionInFlight,
            forkingThreadId = draftState.forkingThreadId,
            newThreadDraft = draftState.newThreadDraft,
            appUpdate = draftState.appUpdate
        )
    }

    val state: StateFlow<HomeUiState> = combine(
        remoteState,
        composerText,
        localState
    ) { remote, text, local ->
        remote.toHomeState(
            composer = text,
            composerExpanded = local.showComposerDetails,
            composerFocusRequest = local.composerFocusRequest,
            isNewThreadDraft = local.isNewThreadDraft,
            draftSubmissionInFlight = local.draftSubmissionInFlight,
            isForkingThread = local.forkingThreadId != null,
            newThreadDraft = local.newThreadDraft,
            appUpdate = local.appUpdate
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = remoteState.value.toHomeState(
            composer = composerText.value,
            composerExpanded = showComposerDetails.value,
            composerFocusRequest = composerFocusRequest.value,
            isNewThreadDraft = isNewThreadDraft.value,
            draftSubmissionInFlight = draftSubmissionInFlight.value,
            isForkingThread = forkingThreadId.value != null,
            newThreadDraft = newThreadDraft.value,
            appUpdate = appUpdate.value
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

    fun startNewThreadDraft(cwd: String? = null) {
        newThreadDraft.update { draft ->
            draft.copy(cwd = cwd?.trim().orEmpty())
        }
        draftSubmissionInFlight.value = false
        isNewThreadDraft.value = true
        requestComposerFocus()
    }

    fun exitNewThreadDraft() {
        draftSubmissionInFlight.value = false
        isNewThreadDraft.value = false
    }

    fun markDraftSubmissionStarted() {
        draftSubmissionInFlight.value = true
    }

    fun markForkStarted(sourceThreadId: String) {
        forkingThreadId.value = sourceThreadId.takeIf(String::isNotBlank)
    }

    fun clearForkIfSource(sourceThreadId: String) {
        if (forkingThreadId.value == sourceThreadId) {
            forkingThreadId.value = null
        }
    }

    fun syncRemoteSelection(remote: SessionRemoteState) {
        if (draftSubmissionInFlight.value && remote.selectedThreadId.isNotBlank()) {
            exitNewThreadDraft()
        }
        val sourceThreadId = forkingThreadId.value
        if (
            sourceThreadId != null &&
            remote.selectedThreadId.isNotBlank() &&
            remote.selectedThreadId != sourceThreadId &&
            remote.threads.any { it.id == remote.selectedThreadId }
        ) {
            forkingThreadId.value = null
            exitNewThreadDraft()
        }
    }

    fun updateNewThreadDraft(transform: (NewThreadDraft) -> NewThreadDraft) {
        newThreadDraft.update(transform)
    }

    fun updateAppUpdate(update: AppUpdateState) {
        appUpdate.value = update
    }

    fun syncDraftDefaults(remote: SessionRemoteState) {
        newThreadDraft.update { draft ->
            draft.copy(
                model = draft.model.ifBlank { remote.configOptions.defaults.model },
                reasoningEffort = draft.reasoningEffort.ifBlank { remote.configOptions.defaults.reasoningEffort },
                sandboxMode = draft.sandboxMode.ifBlank { remote.configOptions.defaults.sandboxMode }
            )
        }
    }
}
