package com.codex.mobile.data

import com.codex.mobile.data.gateway.emptyRemoteState
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.ThreadMessage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun pendingThreadSelectionIgnoresOldThreadSnapshotUntilTargetArrives() {
        val previous = SessionRemoteState(
            selectedThreadId = "thread-old",
            pendingSelectionThreadId = "thread-new",
            pendingThreadTitle = "新会话",
            isThreadSwitching = true,
            messages = listOf(message("old-local")),
            connectionStatus = ConnectionStatus.CONNECTED,
            gatewayConfig = GatewayConfig(url = "ws://10.0.2.2:8765/mobile")
        )
        val oldRaw = """
            {
              "type": "snapshot",
              "revision": 7,
              "threads": [
                { "id": "thread-old", "title": "旧会话", "preview": "", "status": "idle" },
                { "id": "thread-new", "title": "新会话", "preview": "", "status": "idle" }
              ],
              "selectedThreadId": "thread-old",
              "messages": [
                { "id": "old-remote", "role": "assistant", "blocks": [{ "kind": "text", "value": "old" }] }
              ],
              "chips": [],
              "slashCommands": [],
              "isGenerating": false
            }
        """.trimIndent()

        val stillSwitching = reduceGatewayInboundState(json, previous, oldRaw)

        assertEquals("thread-old", stillSwitching.selectedThreadId)
        assertEquals("thread-new", stillSwitching.pendingSelectionThreadId)
        assertTrue(stillSwitching.isThreadSwitching)
        assertEquals(listOf("old-local"), stillSwitching.messages.map { it.id })

        val targetRaw = """
            {
              "type": "snapshot",
              "revision": 8,
              "threads": [
                { "id": "thread-old", "title": "旧会话", "preview": "", "status": "idle" },
                { "id": "thread-new", "title": "新会话", "preview": "", "status": "idle" }
              ],
              "selectedThreadId": "thread-new",
              "messages": [
                { "id": "new-remote", "role": "assistant", "blocks": [{ "kind": "text", "value": "new" }] }
              ],
              "chips": [],
              "slashCommands": [],
              "isGenerating": false
            }
        """.trimIndent()

        val switched = reduceGatewayInboundState(json, stillSwitching, targetRaw)

        assertEquals("thread-new", switched.selectedThreadId)
        assertEquals(null, switched.pendingSelectionThreadId)
        assertFalse(switched.isThreadSwitching)
        assertEquals(listOf("new-remote"), switched.messages.map { it.id })
    }

    @Test
    fun reduceGatewayInboundStateRejectsStaleSnapshotPatchPayload() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile"))
            .copy(snapshotRevision = 5L)
        var mismatchCallbacks = 0
        val raw = """
            {
              "type": "snapshot_patch",
              "baseRevision": 4,
              "revision": 6,
              "changed": ["isGenerating"],
              "isGenerating": true
            }
        """.trimIndent()

        val next = reduceGatewayInboundState(
            json = json,
            previous = previous,
            raw = raw,
            onSnapshotPatchMismatch = { mismatchCallbacks += 1 }
        )

        assertEquals(ConnectionStatus.CONNECTED, next.connectionStatus)
        assertEquals(5L, next.snapshotRevision)
        assertTrue(next.isManualRefreshing)
        assertEquals(1, mismatchCallbacks)
        assertTrue(next.connectionDetail.contains("正在刷新完整状态"))
    }

    @Test
    fun reduceGatewayInboundStateTurnsDecodeFailureIntoErrorState() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile"))

        val next = reduceGatewayInboundState(json, previous, "{not-json")

        assertEquals(ConnectionStatus.ERROR, next.connectionStatus)
        assertTrue(next.connectionDetail.startsWith("网关消息解析失败:"))
    }

    private fun message(id: String): ThreadMessage {
        return ThreadMessage(
            id = id,
            role = MessageRole.ASSISTANT,
            blocks = listOf(MessageBlock.Text(id))
        )
    }
}
