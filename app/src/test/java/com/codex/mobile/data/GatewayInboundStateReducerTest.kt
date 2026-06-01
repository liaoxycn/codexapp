package com.codex.mobile.data

import com.codex.mobile.data.gateway.emptyRemoteState
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayInboundStateReducerTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun reduceGatewayInboundStateAppliesSnapshotPayload() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile"))
        val raw = """
            {
              "type": "snapshot",
              "threads": [
                {
                  "id": "thread-1",
                  "title": "项目重构",
                  "preview": "preview",
                  "subtitle": "subtitle",
                  "status": "running",
                  "updatedAt": 123,
                  "groupKind": "project",
                  "groupLabel": "codexapp",
                  "cwd": "D:/Projects/home/codexapp"
                }
              ],
              "selectedThreadId": "thread-1",
              "messages": [],
              "chips": [],
              "slashCommands": [],
              "cwd": "D:/Projects/home/codexapp",
              "permissionSummary": "workspace-write",
              "isGenerating": true
            }
        """.trimIndent()

        val next = reduceGatewayInboundState(json, previous, raw)

        assertEquals(ConnectionStatus.CONNECTED, next.connectionStatus)
        assertEquals("thread-1", next.selectedThreadId)
        assertEquals("codexapp", next.threads.single().groupLabel)
        assertTrue(next.isGenerating)
    }

    @Test
    fun reduceGatewayInboundStateAppliesSnapshotPatchPayload() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile"))
        val baseline = reduceGatewayInboundState(
            json,
            previous,
            """
                {
                  "type": "snapshot",
                  "revision": 1,
                  "threads": [],
                  "messages": [],
                  "chips": [],
                  "slashCommands": ["/compact"],
                  "cwd": "D:/Projects/home/codexapp",
                  "permissionSummary": "workspace-write",
                  "isGenerating": false
                }
            """.trimIndent()
        )
        val raw = """
            {
              "type": "snapshot_patch",
              "baseRevision": 1,
              "revision": 2,
              "changed": ["messages", "isGenerating"],
              "messages": [
                {
                  "id": "msg-1",
                  "role": "assistant",
                  "blocks": [
                    { "kind": "status", "value": "思考中" }
                  ]
                }
              ],
              "isGenerating": true
            }
        """.trimIndent()

        val next = reduceGatewayInboundState(json, baseline, raw)

        assertEquals(ConnectionStatus.CONNECTED, next.connectionStatus)
        assertEquals(2L, next.snapshotRevision)
        assertEquals("/compact", next.slashCommands.single())
        assertEquals("msg-1", next.messages.single().id)
        assertTrue(next.isGenerating)
    }

    @Test
    fun reduceGatewayInboundStateRejectsStaleSnapshotPatchPayload() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile"))
            .copy(snapshotRevision = 5L)
        val raw = """
            {
              "type": "snapshot_patch",
              "baseRevision": 4,
              "revision": 6,
              "changed": ["isGenerating"],
              "isGenerating": true
            }
        """.trimIndent()

        val next = reduceGatewayInboundState(json, previous, raw)

        assertEquals(ConnectionStatus.ERROR, next.connectionStatus)
        assertEquals(5L, next.snapshotRevision)
        assertTrue(next.connectionDetail.contains("基线不匹配"))
    }

    @Test
    fun reduceGatewayInboundStateTurnsDecodeFailureIntoErrorState() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile"))

        val next = reduceGatewayInboundState(json, previous, "{not-json")

        assertEquals(ConnectionStatus.ERROR, next.connectionStatus)
        assertTrue(next.connectionDetail.startsWith("网关消息解析失败:"))
    }
}
