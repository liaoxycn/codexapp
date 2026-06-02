package com.codex.mobile.data

import com.codex.mobile.data.gateway.summarizeInboundForLog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayInboundLogSummaryTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun snapshotSummaryKeepsOnlyMetadata() {
        val raw = """
            {
              "type": "snapshot",
              "revision": 12,
              "threads": [
                { "id": "thread-secret-1", "title": "Sensitive title", "preview": "Sensitive preview", "status": "running" }
              ],
              "selectedThreadId": "thread-secret-1",
              "messages": [
                { "id": "msg-1", "role": "assistant", "blocks": [{ "kind": "text", "value": "private assistant text" }] }
              ],
              "isGenerating": true,
              "diagnostics": {
                "runningThreadIds": ["thread-secret-1"],
                "actionType": "send_prompt",
                "actionStatus": "running"
              }
            }
        """.trimIndent()

        val summary = summarizeInboundForLog(json, raw)

        assertTrue(summary.contains("type=snapshot"))
        assertTrue(summary.contains("revision=12"))
        assertTrue(summary.contains("threads=1"))
        assertTrue(summary.contains("messages=1"))
        assertTrue(summary.contains("generating=true"))
        assertFalse(summary.contains("Sensitive title"))
        assertFalse(summary.contains("Sensitive preview"))
        assertFalse(summary.contains("private assistant text"))
        assertFalse(summary.contains("thread-secret-1"))
    }

    @Test
    fun statusSummaryDoesNotLogDetailText() {
        val summary = summarizeInboundForLog(
            json,
            """{"type":"status","status":"error","detail":"secret connection detail"}"""
        )

        assertTrue(summary.contains("type=status"))
        assertTrue(summary.contains("status=error"))
        assertTrue(summary.contains("detailLen=24"))
        assertFalse(summary.contains("secret connection detail"))
    }

    @Test
    fun unparseableSummaryDoesNotEchoRawText() {
        val summary = summarizeInboundForLog(json, "{private malformed json")

        assertTrue(summary.contains("type=unparseable"))
        assertFalse(summary.contains("private malformed json"))
    }
}
