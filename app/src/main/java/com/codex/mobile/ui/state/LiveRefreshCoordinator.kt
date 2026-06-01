package com.codex.mobile.ui.state

import com.codex.mobile.model.SessionRemoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class LiveRefreshCoordinator {
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
        @Suppress("UNUSED_EXPRESSION")
        listOf(scope, snapshot, currentSnapshot, setManualRefreshing, refresh)
    }
}
