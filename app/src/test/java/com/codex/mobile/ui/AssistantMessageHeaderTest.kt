package com.codex.mobile.ui

import com.codex.mobile.ui.message.buildProcessHeaderTitle
import com.codex.mobile.ui.message.buildAssistantFooterDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantMessageHeaderTest {
    @Test
    fun processHeaderShowsCountWithoutDurationWhenMissing() {
        assertEquals("已处理 6 项", buildProcessHeaderTitle(6))
    }

    @Test
    fun assistantFooterShowsDurationWhenPresent() {
        assertEquals("1m 1s", buildAssistantFooterDuration(61_000L))
    }

    @Test
    fun assistantFooterShowsShortRunningDuration() {
        assertEquals("5s", buildAssistantFooterDuration(5_000L))
    }

    @Test
    fun assistantFooterHidesDurationWhenMissing() {
        assertEquals(null, buildAssistantFooterDuration(null))
    }
}
