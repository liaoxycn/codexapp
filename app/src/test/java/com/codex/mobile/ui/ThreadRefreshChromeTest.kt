package com.codex.mobile.ui

import com.codex.mobile.ui.thread.pullRefreshHintLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadRefreshChromeTest {
    @Test
    fun pullRefreshHintDistinguishesGeneratingFromManualRefresh() {
        assertEquals(
            "刷新会话中",
            pullRefreshHintLabel(refreshing = true, generating = true, progress = 0f)
        )
        assertEquals(
            "会话运行中",
            pullRefreshHintLabel(refreshing = false, generating = true, progress = 0.5f)
        )
    }

    @Test
    fun pullRefreshHintShowsGestureProgress() {
        assertEquals(
            "继续上滑",
            pullRefreshHintLabel(refreshing = false, generating = false, progress = 0.5f)
        )
        assertEquals(
            "松开刷新",
            pullRefreshHintLabel(refreshing = false, generating = false, progress = 1f)
        )
    }
}
