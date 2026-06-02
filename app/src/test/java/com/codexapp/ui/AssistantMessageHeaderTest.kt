package com.codexapp.ui

import com.codexapp.ui.message.buildProcessHeaderTitle
import com.codexapp.ui.message.buildProcessedHeader
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantMessageHeaderTest {
    @Test
    fun processHeaderShowsCountWithoutDurationWhenMissing() {
        assertEquals("已处理 6 项", buildProcessHeaderTitle(6))
    }

    @Test
    fun processedHeaderShowsDurationWhenPresent() {
        assertEquals("已处理 1m 1s", buildProcessedHeader(61_000L))
    }

    @Test
    fun processedHeaderShowsShortRunningDuration() {
        assertEquals("已处理 5s", buildProcessedHeader(5_000L))
    }

    @Test
    fun processedHeaderHidesDurationWhenMissing() {
        assertEquals(null, buildProcessedHeader(null))
    }
}
