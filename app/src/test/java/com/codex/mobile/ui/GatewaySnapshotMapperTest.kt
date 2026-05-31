package com.codex.mobile.ui

import com.codex.mobile.data.gateway.GatewayBlockPayload
import com.codex.mobile.data.gateway.GatewayChipPayload
import com.codex.mobile.data.gateway.GatewaySnapshotMessage
import com.codex.mobile.data.gateway.GatewayStatusMessage
import com.codex.mobile.data.gateway.applyTo
import com.codex.mobile.data.gateway.decodeGatewayInboundMessage
import com.codex.mobile.data.gateway.emptyRemoteState
import com.codex.mobile.model.ComposerChipIcon
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.MessageBlock
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewaySnapshotMapperTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun snapshotApplyToMapsThreadsMessagesAndChips() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile"))
        val snapshot = GatewaySnapshotMessage(
            threads = listOf(
                com.codex.mobile.data.gateway.GatewayThreadPayload(
                    id = "thread-1",
                    title = "项目重构",
                    preview = "preview",
                    subtitle = "subtitle",
                    cwd = "D:/Projects/home/codexapp",
                    status = "running",
                    updatedAt = 123L,
                    groupKind = "project",
                    groupLabel = "codexapp"
                )
            ),
            selectedThreadId = "thread-1",
            messages = listOf(
                com.codex.mobile.data.gateway.GatewayMessagePayload(
                    id = "msg-1",
                    role = "assistant",
                    blocks = listOf(
                        GatewayBlockPayload(kind = "code", value = "println(1)", language = "kotlin"),
                        GatewayBlockPayload(kind = "fileChangeMeta", value = "已编辑 Main.kt", path = "app/src/Main.kt")
                    )
                )
            ),
            chips = listOf(GatewayChipPayload(label = "上下文", icon = "context")),
            slashCommands = listOf("/compact"),
            cwd = "D:/Projects/home/codexapp",
            permissionSummary = "workspace-write",
            isGenerating = true
        )

        val next = snapshot.applyTo(previous)

        assertEquals(ConnectionStatus.CONNECTED, next.connectionStatus)
        assertEquals("thread-1", next.selectedThreadId)
        assertEquals("codexapp", next.threads.single().groupLabel)
        assertEquals(ComposerChipIcon.CONTEXT, next.chips.single().icon)
        assertTrue(next.messages.first().blocks.first() is MessageBlock.Code)
        assertTrue(next.messages.first().blocks.last() is MessageBlock.FileChangeMeta)
    }

    @Test
    fun statusApplyToPreservesDetailFallback() {
        val previous = emptyRemoteState(GatewayConfig()).copy(connectionDetail = "old detail")
        val next = GatewayStatusMessage(status = "error").applyTo(previous)

        assertEquals(ConnectionStatus.ERROR, next.connectionStatus)
        assertEquals("old detail", next.connectionDetail)
    }

    @Test
    fun decodeGatewayInboundMessageDetectsSnapshotEnvelope() {
        val raw = """
            {
              "type": "snapshot",
              "threads": [],
              "messages": []
            }
        """.trimIndent()

        val inbound = decodeGatewayInboundMessage(json, raw)

        assertEquals("Snapshot", inbound::class.simpleName)
    }
}
