package com.codexapp.ui

import com.codexapp.model.TokenUsageState
import com.codexapp.ui.common.formatTopBarTokenUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarTokenUsageTest {
    @Test
    fun formatTopBarTokenUsageShowsCompactTotalAndContext() {
        assertEquals(
            "12.3k · 62%",
            formatTopBarTokenUsage(
                TokenUsageState(
                    totalTokens = 12_345L,
                    inputTokens = 10_000L,
                    outputTokens = 2_345L,
                    reasoningTokens = 345L,
                    contextPercent = 62
                )
            )
        )
    }

    @Test
    fun formatTopBarTokenUsageClampsContextAndHandlesSmallCounts() {
        assertEquals(
            "999 · 100%",
            formatTopBarTokenUsage(
                TokenUsageState(
                    totalTokens = 999L,
                    inputTokens = 0L,
                    outputTokens = 0L,
                    reasoningTokens = 0L,
                    contextPercent = 140
                )
            )
        )
    }
}
