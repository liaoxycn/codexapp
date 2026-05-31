package com.codex.mobile.ui.state

import com.codex.mobile.model.SessionRemoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class LiveRefreshCoordinator {
    private var liveRefreshJob: Job? = null
    private var liveRefreshTargetId: String? = null
    private var manualRefreshJob: Job? = null

    fun refreshAnimated(
        scope: CoroutineScope,
        setManualRefreshing: (Boolean) -> Unit,
        refresh: suspend () -> Unit
    ) {
        if (manualRefreshJob?.isActive == true) {
            return
        }
        manualRefreshJob = scope.launch {
            setManualRefreshing(true)
            try {
                refresh()
                delay(650)
            } finally {
                setManualRefreshing(false)
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (manualRefreshJob === job) {
                    manualRefreshJob = null
                }
            }
        }
    }

    fun sync(
        scope: CoroutineScope,
        snapshot: SessionRemoteState,
        currentSnapshot: () -> SessionRemoteState,
        setManualRefreshing: (Boolean) -> Unit,
        refresh: suspend () -> Unit
    ) {
        val targetId = snapshot.selectedThreadId.takeIf { it.isNotBlank() }
        if (!shouldPollLiveRefresh(snapshot) || targetId == null) {
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
        liveRefreshJob = scope.launch {
            while (true) {
                delay(1200)
                val current = currentSnapshot()
                if (!shouldContinueLiveRefresh(current, targetId)) {
                    break
                }
                if (!current.isManualRefreshing) {
                    setManualRefreshing(true)
                }
                refresh()
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (liveRefreshJob === job) {
                    liveRefreshJob = null
                    if (liveRefreshTargetId == targetId) {
                        liveRefreshTargetId = null
                    }
                }
            }
        }
    }
}
