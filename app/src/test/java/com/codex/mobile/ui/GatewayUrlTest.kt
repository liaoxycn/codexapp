package com.codex.mobile.ui

import com.codex.mobile.data.gateway.normalizeGatewayUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayUrlTest {
    @Test
    fun normalizeGatewayUrlAddsSchemeAndMobilePath() {
        assertEquals(
            "ws://10.0.2.2:8765/mobile",
            normalizeGatewayUrl("10.0.2.2:8765")
        )
    }

    @Test
    fun normalizeGatewayUrlConvertsHttpScheme() {
        assertEquals(
            "ws://10.0.2.2:8765/mobile",
            normalizeGatewayUrl("http://10.0.2.2:8765")
        )
    }

    @Test
    fun normalizeGatewayUrlKeepsExplicitPath() {
        assertEquals(
            "wss://example.com/custom",
            normalizeGatewayUrl("wss://example.com/custom")
        )
    }
}
