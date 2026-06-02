package com.codexapp.ui

import com.codexapp.ui.thread.shouldTriggerPullRefresh
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
                pullDistance = 200f,
                pullThreshold = 160f,
                refreshTriggered = true
            )
        )
    }
}
