package com.codexapp.ui

import com.codexapp.ui.thread.shouldTriggerPullRefresh
import com.codexapp.ui.thread.nextEdgePullGestureStart
import com.codexapp.ui.thread.shouldAccumulateEdgePull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadPullRefreshControllerTest {
    @Test
    fun pullRefreshTriggersWhenBottomDragPassesThreshold() {
        assertTrue(
            shouldTriggerPullRefresh(
                isAtBottom = true,
                isGenerating = false,
                isManualRefreshing = false,
                gestureStartedAtBottom = true,
                pullDistance = 100f,
                pullThreshold = 96f,
                refreshTriggered = false
            )
        )
    }

    @Test
    fun pullRefreshDoesNotTriggerWhenBusyOrAlreadyTriggered() {
        assertFalse(
            shouldTriggerPullRefresh(
                isAtBottom = true,
                isGenerating = true,
                isManualRefreshing = false,
                gestureStartedAtBottom = true,
                pullDistance = 200f,
                pullThreshold = 160f,
                refreshTriggered = false
            )
        )
        assertFalse(
            shouldTriggerPullRefresh(
                isAtBottom = true,
                isGenerating = false,
                isManualRefreshing = false,
                gestureStartedAtBottom = true,
                pullDistance = 200f,
                pullThreshold = 160f,
                refreshTriggered = true
            )
        )
    }

    @Test
    fun pullRefreshDoesNotTriggerWhenGestureDidNotStartAtBottom() {
        assertFalse(
            shouldTriggerPullRefresh(
                isAtBottom = true,
                isGenerating = false,
                isManualRefreshing = false,
                gestureStartedAtBottom = false,
                pullDistance = 200f,
                pullThreshold = 160f,
                refreshTriggered = false
            )
        )
    }

    @Test
    fun edgePullKeepsOriginalGestureStartEdge() {
        val startedAwayFromEdge = nextEdgePullGestureStart(
            observed = false,
            startedAtEdge = false,
            isAtEdge = false,
            dragDistance = 12f
        )
        val continuedAtEdge = nextEdgePullGestureStart(
            observed = startedAwayFromEdge.observed,
            startedAtEdge = startedAwayFromEdge.startedAtEdge,
            isAtEdge = true,
            dragDistance = 140f
        )

        assertTrue(continuedAtEdge.observed)
        assertFalse(continuedAtEdge.startedAtEdge)
        assertFalse(
            shouldAccumulateEdgePull(
                isAtEdge = true,
                gestureStartedAtEdge = continuedAtEdge.startedAtEdge
            )
        )
    }

    @Test
    fun edgePullAccumulatesOnlyWhenGestureStartsAtEdge() {
        val startedAtEdge = nextEdgePullGestureStart(
            observed = false,
            startedAtEdge = false,
            isAtEdge = true,
            dragDistance = 12f
        )

        assertTrue(startedAtEdge.observed)
        assertTrue(startedAtEdge.startedAtEdge)
        assertTrue(
            shouldAccumulateEdgePull(
                isAtEdge = true,
                gestureStartedAtEdge = startedAtEdge.startedAtEdge
            )
        )
    }
}
