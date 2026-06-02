package com.codexapp.ui.state

import com.codexapp.model.HomeUiState
import com.codexapp.model.AppUpdateState
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.PendingEditResendState
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.resolveSupportedNewThreadPermissionMode
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
    private val pendingThreadSelectionId = MutableStateFlow<String?>(null)
    private val pendingThreadTitle = MutableStateFlow<String?>(null)
    private val pendingArchive = MutableStateFlow<PendingArchiveState?>(null)
    private val pendingEditResend = MutableStateFlow<PendingEditResendState?>(null)
    private val newThreadDraft = MutableStateFlow(NewThreadDraft())
    private val currentThreadConfigDrafts = MutableStateFlow<Map<String, NewThreadDraft>>(emptyMap())
    private val appUpdate = MutableStateFlow(AppUpdateState())

    private data class HomeLocalState(
        val showComposerDetails: Boolean,
        val composerFocusRequest: Long,
        val isNewThreadDraft: Boolean,
        val draftSubmissionInFlight: Boolean,
        val forkingThreadId: String?,
        val pendingThreadSelectionId: String?,
        val pendingThreadTitle: String?,
        val pendingEditResend: PendingEditResendState?,
        val newThreadDraft: NewThreadDraft,
        val currentThreadConfigDrafts: Map<String, NewThreadDraft>,
        val appUpdate: AppUpdateState
    )

    private data class DraftLocalState(
        val isNewThreadDraft: Boolean,
        val draftSubmissionInFlight: Boolean,
        val forkingThreadId: String?,
        val pendingThreadSelectionId: String?,
        val pendingThreadTitle: String?,
        val pendingEditResend: PendingEditResendState?,
        val newThreadDraft: NewThreadDraft,
        val currentThreadConfigDrafts: Map<String, NewThreadDraft>,
        val appUpdate: AppUpdateState
    )

    private data class PendingSelectionLocalState(
        val threadId: String?,
        val title: String?
    )

    private data class EditResendDraftState(
        val pendingEditResend: PendingEditResendState?,
        val newThreadDraft: NewThreadDraft,
        val currentThreadConfigDrafts: Map<String, NewThreadDraft>,
        val appUpdate: AppUpdateState
    )

    private data class PendingArchiveState(
        val threadId: String,
        val diagnosticsBaselineAt: Long
    )

    private val editResendDraftState = combine(
        pendingEditResend,
        newThreadDraft,
        currentThreadConfigDrafts,
        appUpdate
    ) { editResend, draft, currentDrafts, update ->
        EditResendDraftState(
            pendingEditResend = editResend,
            newThreadDraft = draft,
            currentThreadConfigDrafts = currentDrafts,
            appUpdate = update
        )
    }

    private val pendingSelectionLocalState = combine(
        pendingThreadSelectionId,
        pendingThreadTitle
    ) { threadId, title ->
        PendingSelectionLocalState(threadId = threadId, title = title)
    }

    private val draftLocalState = combine(
        isNewThreadDraft,
        draftSubmissionInFlight,
        forkingThreadId,
        pendingSelectionLocalState,
        editResendDraftState
    ) { draftMode, submittingDraft, forkingId, pendingSelection, editResendState ->
        DraftLocalState(
            isNewThreadDraft = draftMode,
            draftSubmissionInFlight = submittingDraft,
            forkingThreadId = forkingId,
            pendingThreadSelectionId = pendingSelection.threadId,
            pendingThreadTitle = pendingSelection.title,
            pendingEditResend = editResendState.pendingEditResend,
            newThreadDraft = editResendState.newThreadDraft,
            currentThreadConfigDrafts = editResendState.currentThreadConfigDrafts,
            appUpdate = editResendState.appUpdate
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
            pendingThreadSelectionId = draftState.pendingThreadSelectionId,
            pendingThreadTitle = draftState.pendingThreadTitle,
            pendingEditResend = draftState.pendingEditResend,
            newThreadDraft = draftState.newThreadDraft,
            currentThreadConfigDrafts = draftState.currentThreadConfigDrafts,
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
            pendingEditResend = local.pendingEditResend,
            isNewThreadDraft = local.isNewThreadDraft,
            draftSubmissionInFlight = local.draftSubmissionInFlight,
            isForkingThread = local.forkingThreadId != null,
            pendingSelectionThreadId = local.pendingThreadSelectionId,
            pendingThreadTitle = local.pendingThreadTitle,
            newThreadDraft = local.newThreadDraft,
            composerConfigDraftOverride = if (!local.isNewThreadDraft && remote.selectedThreadId.isNotBlank()) {
                local.currentThreadConfigDrafts[remote.selectedThreadId]
            } else {
                null
            },
            appUpdate = local.appUpdate
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = remoteState.value.toHomeState(
            composer = composerText.value,
            composerExpanded = showComposerDetails.value,
            composerFocusRequest = composerFocusRequest.value,
            pendingEditResend = pendingEditResend.value,
            isNewThreadDraft = isNewThreadDraft.value,
            draftSubmissionInFlight = draftSubmissionInFlight.value,
            isForkingThread = forkingThreadId.value != null,
            pendingSelectionThreadId = pendingThreadSelectionId.value,
            pendingThreadTitle = pendingThreadTitle.value,
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

    fun setPendingEditResend(update: PendingEditResendState?) {
        pendingEditResend.value = update
    }

    fun startNewThreadDraft(cwd: String? = null) {
        newThreadDraft.update { draft ->
            draft.copy(cwd = cwd?.trim().orEmpty())
        }
        draftSubmissionInFlight.value = false
        isNewThreadDraft.value = true
        pendingThreadSelectionId.value = null
        pendingThreadTitle.value = null
        pendingArchive.value = null
        requestComposerFocus()
    }

    fun exitNewThreadDraft() {
        draftSubmissionInFlight.value = false
        isNewThreadDraft.value = false
        pendingThreadSelectionId.value = null
        pendingThreadTitle.value = null
        pendingArchive.value = null
    }

    fun markDraftSubmissionStarted() {
        draftSubmissionInFlight.value = true
    }

    fun markThreadSelectionStarted(threadId: String, title: String?) {
        pendingThreadSelectionId.value = threadId.takeIf(String::isNotBlank)
        pendingThreadTitle.value = title
    }

    fun markArchiveStarted(threadId: String, remote: SessionRemoteState) {
        pendingArchive.value = threadId.takeIf(String::isNotBlank)?.let { id ->
            PendingArchiveState(
                threadId = id,
                diagnosticsBaselineAt = maxOf(
                    remote.diagnostics.actionStartedAt,
                    remote.diagnostics.actionFinishedAt
                )
            )
        }
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
        if (remote.connectionStatus != ConnectionStatus.CONNECTED) {
            pendingThreadSelectionId.value = null
            pendingThreadTitle.value = null
            pendingArchive.value = null
        }
        val pendingArchiveState = pendingArchive.value
        if (pendingArchiveState != null && remote.diagnostics.actionType == "archive_thread") {
            val actionStartedAfterPending = remote.diagnostics.actionStartedAt >
                pendingArchiveState.diagnosticsBaselineAt
            when (remote.diagnostics.actionStatus) {
                "succeeded" -> if (actionStartedAfterPending) {
                    pendingArchive.value = null
                    startNewThreadDraft()
                    return
                }
                "failed" -> if (actionStartedAfterPending) {
                    pendingArchive.value = null
                }
            }
        }
        val pendingSelectionId = pendingThreadSelectionId.value
        if (
            pendingSelectionId != null &&
            remote.selectedThreadId.isNotBlank() &&
            remote.selectedThreadId == pendingSelectionId
        ) {
            pendingThreadSelectionId.value = null
            pendingThreadTitle.value = null
            exitNewThreadDraft()
        } else if (draftSubmissionInFlight.value && remote.selectedThreadId.isNotBlank()) {
            pendingThreadTitle.value = null
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

    fun updateCurrentThreadConfig(remote: SessionRemoteState, transform: (NewThreadDraft) -> NewThreadDraft) {
        val threadId = remote.selectedThreadId.ifBlank { return }
        val base = state.value.composerConfigDraft.copy(cwd = remote.cwd)
        currentThreadConfigDrafts.update { drafts ->
            drafts + (threadId to transform(base))
        }
    }

    fun updateAppUpdate(update: AppUpdateState) {
        appUpdate.value = update
    }

    fun syncDraftDefaults(remote: SessionRemoteState) {
        newThreadDraft.update { draft ->
            draft.copy(
                model = draft.model.ifBlank { remote.configOptions.defaults.model },
                reasoningEffort = draft.reasoningEffort.ifBlank { remote.configOptions.defaults.reasoningEffort },
                permissionMode = resolveSupportedNewThreadPermissionMode(
                    requested = draft.permissionMode,
                    availableSandboxModes = remote.configOptions.sandboxModes.map { it.value }
                )
            )
        }
        currentThreadConfigDrafts.update { drafts ->
            val sandboxModes = remote.configOptions.sandboxModes.map { it.value }
            drafts.mapValues { (_, draft) ->
                draft.copy(
                    model = draft.model.ifBlank { remote.configOptions.defaults.model },
                    reasoningEffort = draft.reasoningEffort.ifBlank { remote.configOptions.defaults.reasoningEffort },
                    permissionMode = resolveSupportedNewThreadPermissionMode(
                        requested = draft.permissionMode,
                        availableSandboxModes = sandboxModes
                    )
                )
            }
        }
    }
}
