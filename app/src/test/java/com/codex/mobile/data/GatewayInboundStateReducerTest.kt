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
    fun reduceGatewayInboundStateTurnsDecodeFailureIntoErrorState() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile"))

        val next = reduceGatewayInboundState(json, previous, "{not-json")

        assertEquals(ConnectionStatus.ERROR, next.connectionStatus)
        assertTrue(next.connectionDetail.startsWith("网关消息解析失败:"))
    }
}
