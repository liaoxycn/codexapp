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
    val pullThreshold = remember(pullVelocity) {
        val speedBoost = (pullVelocity / 900f).coerceIn(0f, 0.35f)
        (160f * (1f - speedBoost)).coerceIn(110f, 160f)
    }
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
                if (available.y < 0f) {
                    val now = SystemClock.uptimeMillis()
                    val elapsed = (now - lastPullSampleAt).coerceAtLeast(1L).toFloat()
                    pullVelocity = ((-available.y) / elapsed) * 1000f
                    lastPullSampleAt = now
                    pullDistance = (pullDistance - available.y).coerceAtMost(260f)
                    pullGestureTick += 1
                } else if (available.y > 0f && pullDistance > 0f) {
                    pullDistance = 0f
                    pullVelocity = 0f
                    pullGestureTick += 1
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (isAtBottom &&
                    !isGenerating &&
                    !isManualRefreshing &&
                    pullDistance >= pullThreshold
                ) {
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
