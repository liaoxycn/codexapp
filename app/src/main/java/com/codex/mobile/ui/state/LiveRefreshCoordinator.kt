package com.codex.mobile.ui.state

import com.codex.mobile.model.SessionRemoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class LiveRefreshCoordinator(
    private val liveRefreshIntervalMs: Long = LIVE_REFRESH_INTERVAL_MS
) {
    private var manualRefreshJob: Job? = null
    private var liveRefreshJob: Job? = null
    private var liveRefreshThreadId: String? = null

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
        setManualRefreshing
        if (!shouldPollLiveRefresh(snapshot)) {
            stopLiveRefresh()
            return
        }
        val targetThreadId = snapshot.selectedThreadId
        if (liveRefreshJob?.isActive == true && liveRefreshThreadId == targetThreadId) {
            return
        }
        stopLiveRefresh()
        liveRefreshThreadId = targetThreadId
        liveRefreshJob = scope.launch {
            delay(liveRefreshIntervalMs)
            while (shouldContinueLiveRefresh(currentSnapshot(), targetThreadId)) {
                refresh()
                delay(liveRefreshIntervalMs)
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (liveRefreshJob === job) {
                    liveRefreshJob = null
                    liveRefreshThreadId = null
                }
            }
        }
    }

    private fun stopLiveRefresh() {
        liveRefreshJob?.cancel()
        liveRefreshJob = null
        liveRefreshThreadId = null
    }

    private companion object {
        const val LIVE_REFRESH_INTERVAL_MS = 2_500L
    }
}
