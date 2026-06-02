package com.codex.mobile.ui.thread

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay

internal data class ThreadPullRefreshController(
    val nestedScrollConnection: NestedScrollConnection,
    val pullProgress: Float,
    val showPullHint: Boolean,
)

@Composable
internal fun rememberThreadPullRefreshController(
    selectedThreadId: String,
    isGenerating: Boolean,
    isManualRefreshing: Boolean,
    isAtBottom: Boolean,
    onRefreshCurrent: () -> Unit,
): ThreadPullRefreshController {
    var pullDistance by rememberSaveable(selectedThreadId) { mutableFloatStateOf(0f) }
    var pullVelocity by rememberSaveable(selectedThreadId) { mutableFloatStateOf(0f) }
    var lastPullSampleAt by rememberSaveable(selectedThreadId) { mutableStateOf(0L) }
    var pullHintVisibleUntil by rememberSaveable(selectedThreadId) { mutableStateOf(0L) }
    var pullGestureTick by rememberSaveable(selectedThreadId) { mutableIntStateOf(0) }
    var refreshTriggered by rememberSaveable(selectedThreadId) { mutableStateOf(false) }
    val pullThreshold = remember { 96f }
    val rawProgress = (pullDistance / pullThreshold).coerceIn(0f, 1f)
    val pullProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(stiffness = 420f),
        label = "pull-progress"
    )
    val showPullHint = pullDistance > 0f ||
        isManualRefreshing ||
        SystemClock.uptimeMillis() < pullHintVisibleUntil
    val nestedScrollConnection = remember(
        selectedThreadId,
        isGenerating,
        isManualRefreshing,
        isAtBottom,
        pullThreshold
    ) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (isGenerating || isManualRefreshing) {
                    return Offset.Zero
                }
                if (!isAtBottom) {
                    if (pullDistance != 0f) {
                        pullDistance = 0f
                        pullVelocity = 0f
                    }
                    return Offset.Zero
                }
                val upwardDrag = (-consumed.y).coerceAtLeast(-available.y).coerceAtLeast(0f)
                if (upwardDrag > 0f) {
                    val now = SystemClock.uptimeMillis()
                    val elapsed = (now - lastPullSampleAt).coerceAtLeast(1L).toFloat()
                    pullVelocity = (upwardDrag / elapsed) * 1000f
                    lastPullSampleAt = now
                    pullDistance = (pullDistance + upwardDrag).coerceAtMost(220f)
                    pullGestureTick += 1
                    if (shouldTriggerPullRefresh(
                            isAtBottom = isAtBottom,
                            isGenerating = isGenerating,
                            isManualRefreshing = isManualRefreshing,
                            pullDistance = pullDistance,
                            pullThreshold = pullThreshold,
                            refreshTriggered = refreshTriggered
                        )
                    ) {
                        refreshTriggered = true
                        pullHintVisibleUntil = SystemClock.uptimeMillis() + 700L
                        onRefreshCurrent()
                        pullDistance = 0f
                        pullVelocity = 0f
                    }
                } else if ((consumed.y > 0f || available.y > 0f) && pullDistance > 0f) {
                    pullDistance = 0f
                    pullVelocity = 0f
                    refreshTriggered = false
                    pullGestureTick += 1
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (shouldTriggerPullRefresh(
                        isAtBottom = isAtBottom,
                        isGenerating = isGenerating,
                        isManualRefreshing = isManualRefreshing,
                        pullDistance = pullDistance,
                        pullThreshold = pullThreshold,
                        refreshTriggered = refreshTriggered
                    )
                ) {
                    refreshTriggered = true
                    pullHintVisibleUntil = SystemClock.uptimeMillis() + 700L
                    onRefreshCurrent()
                    pullDistance = 0f
                    pullVelocity = 0f
                    return Velocity.Zero
                }
                pullVelocity = 0f
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(refreshTriggered, selectedThreadId) {
        if (refreshTriggered) {
            delay(900L)
            refreshTriggered = false
        }
    }

    LaunchedEffect(pullGestureTick, selectedThreadId) {
        if (pullDistance <= 0f || isManualRefreshing || isGenerating) return@LaunchedEffect
        val expectedTick = pullGestureTick
        delay(900L)
        if (expectedTick == pullGestureTick &&
            !isManualRefreshing &&
            !isGenerating &&
            pullDistance > 0f
        ) {
            pullDistance = 0f
            pullVelocity = 0f
        }
    }

    return ThreadPullRefreshController(
        nestedScrollConnection = nestedScrollConnection,
        pullProgress = pullProgress,
        showPullHint = showPullHint,
    )
}

internal fun shouldTriggerPullRefresh(
    isAtBottom: Boolean,
    isGenerating: Boolean,
    isManualRefreshing: Boolean,
    pullDistance: Float,
    pullThreshold: Float,
    refreshTriggered: Boolean
): Boolean {
    return isAtBottom &&
        !isGenerating &&
        !isManualRefreshing &&
        !refreshTriggered &&
        pullDistance >= pullThreshold
}
