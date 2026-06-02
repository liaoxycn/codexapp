package com.codex.mobile.ui

import com.codex.mobile.data.gateway.GatewayBlockPayload
import com.codex.mobile.data.gateway.GatewayChipPayload
import com.codex.mobile.data.gateway.GatewayFilePayload
import com.codex.mobile.data.gateway.GatewayOperationalNoticePayload
import com.codex.mobile.data.gateway.GatewaySessionConfigPayload
import com.codex.mobile.data.gateway.GatewaySnapshotMessage
import com.codex.mobile.data.gateway.GatewaySnapshotPatchMessage
import com.codex.mobile.data.gateway.GatewayStatusMessage
import com.codex.mobile.data.gateway.applyTo
import com.codex.mobile.data.gateway.decodeGatewayInboundMessage
import com.codex.mobile.data.gateway.emptyRemoteState
import com.codex.mobile.model.ComposerChipIcon
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.ThreadMessage
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
            revision = 7,
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
                    groupLabel = "codexapp",
                    gitBranch = "feature/mobile-shell",
                    gitSha = "1234567"
                ),
                com.codex.mobile.data.gateway.GatewayThreadPayload(
                    id = "thread-archived",
                    title = "旧会话",
                    preview = "archived",
                    status = "idle",
                    archived = true
                )
            ),
            selectedThreadId = "thread-1",
            messages = listOf(
                com.codex.mobile.data.gateway.GatewayMessagePayload(
                    id = "msg-1",
                    role = "assistant",
                    forkNumTurns = 2,
                    rollbackNumTurns = 3,
                    durationMs = 61_000L,
                    isFinal = true,
                    blocks = listOf(
                        GatewayBlockPayload(kind = "code", value = "println(1)", language = "kotlin"),
                        GatewayBlockPayload(kind = "fileChangeMeta", value = "已编辑 Main.kt", path = "app/src/Main.kt")
                    )
                )
            ),
            chips = listOf(GatewayChipPayload(label = "app/Main.kt", icon = "file", path = "D:/Projects/app/Main.kt")),
            files = listOf(GatewayFilePayload(label = "app/src/Main.kt", path = "D:/Projects/app/app/src/Main.kt")),
            operationalNotices = listOf(
                GatewayOperationalNoticePayload(id = "mcp-startup-playwright", text = "MCP 服务 playwright: 已就绪", createdAt = 1234L)
            ),
            slashCommands = listOf("/compact"),
            cwd = "D:/Projects/home/codexapp",
            permissionSummary = "workspace-write",
            sessionConfig = GatewaySessionConfigPayload(
                permissionMode = "workspace-write",
                provider = "openai",
                model = "gpt-5",
                reasoningEffort = "high"
            ),
            desktopRestartRequired = true,
            isGenerating = true
        )

        val next = snapshot.applyTo(previous)

        assertEquals(ConnectionStatus.CONNECTED, next.connectionStatus)
        assertEquals(7L, next.snapshotRevision)
        assertEquals("thread-1", next.selectedThreadId)
        assertEquals("codexapp", next.threads.single().groupLabel)
        assertEquals("feature/mobile-shell", next.threads.single().gitBranch)
        assertEquals("1234567", next.threads.single().gitSha)
        assertEquals(ComposerChipIcon.FILE, next.chips.single().icon)
        assertEquals("D:/Projects/app/Main.kt", next.chips.single().path)
        assertEquals("app/src/Main.kt", next.files.single().label)
        assertEquals("D:/Projects/app/app/src/Main.kt", next.files.single().path)
        assertEquals("mcp-startup-playwright", next.operationalNotices.single().id)
        assertEquals("MCP 服务 playwright: 已就绪", next.operationalNotices.single().text)
        assertEquals(1234L, next.operationalNotices.single().createdAt)
        assertEquals("workspace-write", next.sessionConfig.permissionMode)
        assertEquals("openai", next.sessionConfig.provider)
        assertEquals("gpt-5", next.sessionConfig.model)
        assertEquals("high", next.sessionConfig.reasoningEffort)
        assertTrue(next.desktopRestartRequired)
        assertEquals(2, next.messages.first().forkNumTurns)
        assertEquals(3, next.messages.first().rollbackNumTurns)
        assertEquals(61_000L, next.messages.first().durationMs)
        assertTrue(next.messages.first().isFinal)
        assertTrue(next.messages.first().blocks.first() is MessageBlock.Code)
        assertTrue(next.messages.first().blocks.last() is MessageBlock.FileChangeMeta)
    }

    @Test
    fun snapshotPatchApplyToUpdatesOnlyChangedFields() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile")).copy(
            snapshotRevision = 7L,
            slashCommands = listOf("/compact"),
            files = listOf(com.codex.mobile.model.ComposerFile("README.md", "D:/Projects/home/codexapp/README.md")),
            cwd = "D:/Projects/home/codexapp",
            sessionConfig = com.codex.mobile.model.SessionConfig(provider = "openai", model = "gpt-5")
        )
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 7L,
            revision = 8L,
            changed = listOf(
                "isGenerating",
                "permissionSummary",
                "sessionConfig",
                "desktopRestartRequired",
                "operationalNotices"
            ),
            isGenerating = true,
            permissionSummary = "danger-full-access",
            sessionConfig = GatewaySessionConfigPayload(
                permissionMode = "danger-full-access",
                provider = "openai",
                model = "gpt-5.1",
                reasoningEffort = "medium"
            ),
            operationalNotices = listOf(
                GatewayOperationalNoticePayload(id = "account-updated", text = "账号状态已更新: chatgpt · pro", createdAt = 9L)
            ),
            desktopRestartRequired = true
        )

        val next = patch.applyTo(previous)

        assertEquals(ConnectionStatus.CONNECTED, next.connectionStatus)
        assertEquals(8L, next.snapshotRevision)
        assertTrue(next.isGenerating)
        assertEquals("danger-full-access", next.permissionSummary)
        assertEquals("danger-full-access", next.sessionConfig.permissionMode)
        assertEquals("openai", next.sessionConfig.provider)
        assertEquals("gpt-5.1", next.sessionConfig.model)
        assertEquals("medium", next.sessionConfig.reasoningEffort)
        assertEquals("account-updated", next.operationalNotices.single().id)
        assertTrue(next.desktopRestartRequired)
        assertEquals("README.md", next.files.single().label)
        assertEquals(listOf("/compact"), next.slashCommands)
        assertEquals("D:/Projects/home/codexapp", next.cwd)
    }

    @Test
    fun snapshotPatchDropsReplacedOptimisticUserMessage() {
        val previous = SessionRemoteState(
            snapshotRevision = 1L,
            messages = listOf(
                ThreadMessage(
                    id = "user-42",
                    role = MessageRole.USER,
                    blocks = listOf(MessageBlock.Text("hello"))
                ),
                ThreadMessage(
                    id = "assistant-pending",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(MessageBlock.Status("正在生成…"))
                )
            )
        )
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 1L,
            revision = 2L,
            changed = listOf("messages"),
            messages = listOf(
                com.codex.mobile.data.gateway.GatewayMessagePayload(
                    id = "turn-user-real",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "hello"))
                )
            )
        )

        val next = patch.applyTo(previous)

        assertEquals(listOf("turn-user-real"), next.messages.map { it.id })
    }

    @Test
    fun snapshotPatchCollapsesAdjacentDuplicateUserMessages() {
        val previous = SessionRemoteState(snapshotRevision = 1L)
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 1L,
            revision = 2L,
            changed = listOf("messages"),
            messages = listOf(
                com.codex.mobile.data.gateway.GatewayMessagePayload(
                    id = "user-optimistic-like",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "same prompt"))
                ),
                com.codex.mobile.data.gateway.GatewayMessagePayload(
                    id = "user-real",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "same prompt"))
                ),
                com.codex.mobile.data.gateway.GatewayMessagePayload(
                    id = "assistant-live",
                    role = "assistant",
                    blocks = listOf(GatewayBlockPayload(kind = "status", value = "思考中"))
                )
            )
        )

        val next = patch.applyTo(previous)

        assertEquals(listOf("user-real", "assistant-live"), next.messages.map { it.id })
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

    @Test
    fun decodeGatewayInboundMessageDetectsSnapshotPatchEnvelope() {
        val raw = """
            {
              "type": "snapshot_patch",
              "baseRevision": 1,
              "revision": 2,
              "changed": ["isGenerating"],
              "isGenerating": true
            }
        """.trimIndent()

        val inbound = decodeGatewayInboundMessage(json, raw)

        assertEquals("SnapshotPatch", inbound::class.simpleName)
    }
}
