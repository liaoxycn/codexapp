package com.codexapp.ui

import com.codexapp.data.gateway.GatewayBlockPayload
import com.codexapp.data.gateway.GatewayChipPayload
import com.codexapp.data.gateway.GatewayFilePayload
import com.codexapp.data.gateway.GatewayDiagnosticsPayload
import com.codexapp.data.gateway.GatewayOperationalNoticePayload
import com.codexapp.data.gateway.GatewaySessionConfigPayload
import com.codexapp.data.gateway.GatewaySnapshotMessage
import com.codexapp.data.gateway.GatewaySnapshotPatchMessage
import com.codexapp.data.gateway.GatewayStatusMessage
import com.codexapp.data.gateway.GatewayTokenUsagePayload
import com.codexapp.data.gateway.applyTo
import com.codexapp.data.gateway.decodeGatewayInboundMessage
import com.codexapp.data.gateway.emptyRemoteState
import com.codexapp.model.ComposerChipIcon
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.GatewayConfig
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.ThreadMessage
import com.codexapp.model.ThreadStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
                com.codexapp.data.gateway.GatewayThreadPayload(
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
                com.codexapp.data.gateway.GatewayThreadPayload(
                    id = "thread-archived",
                    title = "旧会话",
                    preview = "archived",
                    status = "idle",
                    archived = true
                )
            ),
            selectedThreadId = "thread-1",
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
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
                GatewayOperationalNoticePayload(
                    id = "mcp-startup-playwright",
                    text = "MCP 服务 playwright: 已就绪\n等待连接",
                    createdAt = 1234L
                )
            ),
            diagnostics = GatewayDiagnosticsPayload(
                selectedThreadId = "thread-1",
                pendingSelectionThreadId = "thread-2",
                isGenerating = true,
                runningThreadIds = listOf("thread-1", "thread-3"),
                snapshotRevision = 7L,
                actionTraceId = "trace-7",
                actionType = "send_prompt",
                actionStatus = "succeeded",
                actionStartedAt = 100L,
                actionFinishedAt = 180L
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
            tokenUsage = GatewayTokenUsagePayload(
                totalTokens = 12345L,
                inputTokens = 10000L,
                outputTokens = 2345L,
                reasoningTokens = 345L,
                contextPercent = 62
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
        assertEquals("MCP 服务 playwright: 已就绪\n等待连接", next.operationalNotices.single().text)
        assertEquals(1234L, next.operationalNotices.single().createdAt)
        assertEquals("workspace-write", next.sessionConfig.permissionMode)
        assertEquals("openai", next.sessionConfig.provider)
        assertEquals("gpt-5", next.sessionConfig.model)
        assertEquals("high", next.sessionConfig.reasoningEffort)
        assertEquals(12345L, next.tokenUsage?.totalTokens)
        assertEquals(10000L, next.tokenUsage?.inputTokens)
        assertEquals(2345L, next.tokenUsage?.outputTokens)
        assertEquals(345L, next.tokenUsage?.reasoningTokens)
        assertEquals(62, next.tokenUsage?.contextPercent)
        assertTrue(next.desktopRestartRequired)
        assertEquals("trace-7", next.diagnostics.actionTraceId)
        assertEquals("send_prompt", next.diagnostics.actionType)
        assertEquals(listOf("thread-1", "thread-3"), next.diagnostics.runningThreadIds)
        assertEquals(7L, next.diagnostics.snapshotRevision)
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
            files = listOf(com.codexapp.model.ComposerFile("README.md", "D:/Projects/home/codexapp/README.md")),
            cwd = "D:/Projects/home/codexapp",
            sessionConfig = com.codexapp.model.SessionConfig(provider = "openai", model = "gpt-5")
        )
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 7L,
            revision = 8L,
            changed = listOf(
                "isGenerating",
                "permissionSummary",
                "sessionConfig",
                "tokenUsage",
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
            tokenUsage = GatewayTokenUsagePayload(
                totalTokens = 2222L,
                inputTokens = 2000L,
                outputTokens = 222L,
                reasoningTokens = 12L,
                contextPercent = 140
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
        assertEquals(2222L, next.tokenUsage?.totalTokens)
        assertEquals(100, next.tokenUsage?.contextPercent)
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
                com.codexapp.data.gateway.GatewayMessagePayload(
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
    fun snapshotPatchAppendsUnconfirmedOptimisticUserMessageAfterHistory() {
        val previous = SessionRemoteState(
            snapshotRevision = 2L,
            messages = listOf(
                ThreadMessage(
                    id = "old-user",
                    role = MessageRole.USER,
                    blocks = listOf(MessageBlock.Text("old prompt"))
                ),
                ThreadMessage(
                    id = "old-assistant",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(MessageBlock.Text("old answer")),
                    isFinal = true
                ),
                ThreadMessage(
                    id = "user-optimistic",
                    role = MessageRole.USER,
                    blocks = listOf(MessageBlock.Text("new prompt"))
                )
            )
        )
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 2L,
            revision = 3L,
            changed = listOf("messages"),
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "old-user",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "old prompt"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "old-assistant",
                    role = "assistant",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "old answer")),
                    isFinal = true
                )
            )
        )

        val next = patch.applyTo(previous)

        assertEquals(listOf("old-user", "old-assistant", "user-optimistic"), next.messages.map { it.id })
    }

    @Test
    fun snapshotPatchKeepsGeneratingWhenSelectedAssistantTurnIsNotFinal() {
        val previous = SessionRemoteState(
            snapshotRevision = 10L,
            selectedThreadId = "thread-1",
            threads = listOf(
                com.codexapp.model.ThreadSummary(
                    id = "thread-1",
                    title = "持续优化",
                    preview = "",
                    status = ThreadStatus.IDLE
                )
            ),
            isGenerating = true,
            messages = listOf(
                ThreadMessage(
                    id = "user-1",
                    role = MessageRole.USER,
                    blocks = listOf(MessageBlock.Text("prompt"))
                ),
                ThreadMessage(
                    id = "assistant-live",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(MessageBlock.Text("partial"))
                )
            )
        )
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 10L,
            revision = 11L,
            changed = listOf("threads", "isGenerating"),
            threads = listOf(
                com.codexapp.data.gateway.GatewayThreadPayload(
                    id = "thread-1",
                    title = "持续优化",
                    preview = "",
                    status = "idle"
                )
            ),
            isGenerating = false
        )

        val next = patch.applyTo(previous)

        assertTrue(next.isGenerating)
    }

    @Test
    fun snapshotPatchAllowsIdleAfterFinalAssistantReplyArrives() {
        val previous = SessionRemoteState(
            snapshotRevision = 11L,
            selectedThreadId = "thread-1",
            isGenerating = true,
            messages = listOf(
                ThreadMessage(
                    id = "user-1",
                    role = MessageRole.USER,
                    blocks = listOf(MessageBlock.Text("prompt"))
                ),
                ThreadMessage(
                    id = "assistant-live",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(MessageBlock.Text("partial"))
                )
            )
        )
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 11L,
            revision = 12L,
            changed = listOf("messages", "isGenerating"),
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "user-1",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "prompt"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "assistant-final",
                    role = "assistant",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "done")),
                    isFinal = true
                )
            ),
            isGenerating = false
        )

        val next = patch.applyTo(previous)

        assertEquals(false, next.isGenerating)
    }

    @Test
    fun snapshotPatchAllowsIdleAfterShellOnlyFinalAssistantArrives() {
        val previous = SessionRemoteState(
            snapshotRevision = 12L,
            selectedThreadId = "thread-1",
            threads = listOf(
                com.codexapp.model.ThreadSummary(
                    id = "thread-1",
                    title = "Codex 会话",
                    preview = "",
                    status = ThreadStatus.IDLE
                )
            ),
            isGenerating = true,
            messages = listOf(
                ThreadMessage(
                    id = "user-1",
                    role = MessageRole.USER,
                    blocks = listOf(MessageBlock.Text("!powershell -NoProfile -Command Get-Random"))
                ),
                ThreadMessage(
                    id = "assistant-live",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(MessageBlock.Reasoning("正在思考"))
                )
            )
        )
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 12L,
            revision = 13L,
            changed = listOf("messages", "isGenerating"),
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "user-1",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "!powershell -NoProfile -Command Get-Random"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "approval-1",
                    role = "system",
                    blocks = listOf(GatewayBlockPayload(kind = "status", value = "审批已允许"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "assistant-final",
                    role = "assistant",
                    isFinal = true,
                    blocks = listOf(
                        GatewayBlockPayload(kind = "commandSummary", value = "已运行 1 条命令"),
                        GatewayBlockPayload(kind = "commandMeta", value = "结果: 退出码 0"),
                        GatewayBlockPayload(kind = "code", language = "shell", value = "34590525")
                    )
                )
            ),
            isGenerating = false
        )

        val next = patch.applyTo(previous)

        assertFalse(next.isGenerating)
        assertEquals(listOf("user-1", "approval-1", "assistant-final"), next.messages.map { it.id })
    }

    @Test
    fun snapshotPatchClearsStaleGeneratingWhenDiagnosticsAreIdle() {
        val previous = SessionRemoteState(
            snapshotRevision = 13L,
            selectedThreadId = "thread-1",
            threads = listOf(
                com.codexapp.model.ThreadSummary(
                    id = "thread-1",
                    title = "stop flow",
                    preview = "",
                    status = ThreadStatus.IDLE
                )
            ),
            isGenerating = true,
            messages = listOf(
                ThreadMessage(
                    id = "user-1",
                    role = MessageRole.USER,
                    blocks = listOf(MessageBlock.Text("stop me"))
                )
            )
        )
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 13L,
            revision = 14L,
            changed = listOf("diagnostics"),
            diagnostics = GatewayDiagnosticsPayload(
                selectedThreadId = "thread-1",
                isGenerating = false,
                runningThreadIds = emptyList(),
                snapshotRevision = 14L,
                actionType = "stop_turn",
                actionStatus = "succeeded"
            )
        )

        val next = patch.applyTo(previous)

        assertFalse(next.isGenerating)
        assertFalse(next.diagnostics.isGenerating)
        assertEquals(emptyList<String>(), next.diagnostics.runningThreadIds)
    }

    @Test
    fun snapshotPatchClearsSelectedGeneratingWhenOnlyAnotherThreadRuns() {
        val previous = SessionRemoteState(
            snapshotRevision = 21L,
            selectedThreadId = "thread-selected",
            threads = listOf(
                com.codexapp.model.ThreadSummary(
                    id = "thread-selected",
                    title = "selected",
                    preview = "",
                    status = ThreadStatus.IDLE
                ),
                com.codexapp.model.ThreadSummary(
                    id = "thread-other",
                    title = "other",
                    preview = "",
                    status = ThreadStatus.RUNNING
                )
            ),
            isGenerating = true,
            messages = listOf(
                ThreadMessage(
                    id = "user-1",
                    role = MessageRole.USER,
                    blocks = listOf(MessageBlock.Text("stop me"))
                ),
                ThreadMessage(
                    id = "assistant-live",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(MessageBlock.Reasoning("正在思考"))
                )
            )
        )
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 21L,
            revision = 22L,
            changed = listOf("diagnostics"),
            diagnostics = GatewayDiagnosticsPayload(
                selectedThreadId = "thread-selected",
                isGenerating = false,
                runningThreadIds = listOf("thread-other"),
                snapshotRevision = 22L,
                actionType = "stop_turn",
                actionStatus = "succeeded"
            )
        )

        val next = patch.applyTo(previous)

        assertFalse(next.isGenerating)
        assertEquals(listOf("thread-other"), next.diagnostics.runningThreadIds)
    }

    @Test
    fun snapshotClearsStaleGeneratingWhenDiagnosticsAreIdle() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile"))
        val snapshot = GatewaySnapshotMessage(
            revision = 15L,
            selectedThreadId = "thread-1",
            threads = listOf(
                com.codexapp.data.gateway.GatewayThreadPayload(
                    id = "thread-1",
                    title = "stale generating",
                    preview = "",
                    status = "idle"
                )
            ),
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "user-1",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "stop me"))
                )
            ),
            diagnostics = GatewayDiagnosticsPayload(
                selectedThreadId = "thread-1",
                isGenerating = false,
                runningThreadIds = emptyList(),
                snapshotRevision = 15L,
                actionType = "stop_turn",
                actionStatus = "succeeded"
            ),
            isGenerating = true
        )

        val next = snapshot.applyTo(previous)

        assertFalse(next.isGenerating)
    }

    @Test
    fun snapshotClearsSelectedGeneratingWhenOnlyAnotherThreadRuns() {
        val previous = emptyRemoteState(GatewayConfig(url = "ws://10.0.2.2:8765/mobile"))
        val snapshot = GatewaySnapshotMessage(
            revision = 23L,
            selectedThreadId = "thread-selected",
            threads = listOf(
                com.codexapp.data.gateway.GatewayThreadPayload(
                    id = "thread-selected",
                    title = "selected",
                    preview = "",
                    status = "idle"
                ),
                com.codexapp.data.gateway.GatewayThreadPayload(
                    id = "thread-other",
                    title = "other",
                    preview = "",
                    status = "running"
                )
            ),
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "user-1",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "stop me"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "assistant-live",
                    role = "assistant",
                    blocks = listOf(GatewayBlockPayload(kind = "reasoning", value = "正在思考"))
                )
            ),
            diagnostics = GatewayDiagnosticsPayload(
                selectedThreadId = "thread-selected",
                isGenerating = false,
                runningThreadIds = listOf("thread-other"),
                snapshotRevision = 23L,
                actionType = "stop_turn",
                actionStatus = "succeeded"
            ),
            isGenerating = true
        )

        val next = snapshot.applyTo(previous)

        assertFalse(next.isGenerating)
        assertEquals(listOf("thread-other"), next.diagnostics.runningThreadIds)
    }

    @Test
    fun snapshotPatchCollapsesAdjacentDuplicateUserMessages() {
        val previous = SessionRemoteState(snapshotRevision = 1L)
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 1L,
            revision = 2L,
            changed = listOf("messages"),
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "user-optimistic-like",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "same prompt"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "user-real",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "same prompt"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
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
    fun snapshotPatchCollapsesDuplicateUserMessagesInsideOpenTurn() {
        val previous = SessionRemoteState(snapshotRevision = 1L)
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 1L,
            revision = 2L,
            changed = listOf("messages"),
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "optimistic-or-stream-user",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "same prompt"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "assistant-running",
                    role = "assistant",
                    blocks = listOf(GatewayBlockPayload(kind = "commentary", value = "streaming"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "real-user",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "same prompt"))
                )
            )
        )

        val next = patch.applyTo(previous)

        assertEquals(listOf("real-user", "assistant-running"), next.messages.map { it.id })
    }

    @Test
    fun snapshotPatchKeepsSameUserTextAcrossCompletedTurns() {
        val previous = SessionRemoteState(snapshotRevision = 1L)
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 1L,
            revision = 2L,
            changed = listOf("messages"),
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "user-a",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "repeat prompt"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "assistant-a",
                    role = "assistant",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "done")),
                    isFinal = true
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "user-b",
                    role = "user",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "repeat prompt"))
                )
            )
        )

        val next = patch.applyTo(previous)

        assertEquals(listOf("user-a", "assistant-a", "user-b"), next.messages.map { it.id })
    }

    @Test
    fun snapshotPatchReusesUnchangedMessageInstances() {
        val unchanged = ThreadMessage(
            id = "assistant-stable",
            role = MessageRole.ASSISTANT,
            blocks = listOf(MessageBlock.Text("stable"))
        )
        val previous = SessionRemoteState(
            snapshotRevision = 5L,
            messages = listOf(
                unchanged,
                ThreadMessage(
                    id = "assistant-live",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(MessageBlock.Text("old"))
                )
            )
        )
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 5L,
            revision = 6L,
            changed = listOf("messages"),
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "assistant-stable",
                    role = "assistant",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "stable"))
                ),
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "assistant-live",
                    role = "assistant",
                    blocks = listOf(GatewayBlockPayload(kind = "text", value = "new"))
                )
            )
        )

        val next = patch.applyTo(previous)

        assertSame(unchanged, next.messages.first())
        assertEquals("new", (next.messages.last().blocks.single() as MessageBlock.Text).value)
    }

    @Test
    fun semanticGatewayBlocksMapToProcessMessageBlocks() {
        val previous = SessionRemoteState(snapshotRevision = 1L)
        val patch = GatewaySnapshotPatchMessage(
            baseRevision = 1L,
            revision = 2L,
            changed = listOf("messages"),
            messages = listOf(
                com.codexapp.data.gateway.GatewayMessagePayload(
                    id = "assistant-process",
                    role = "assistant",
                    blocks = listOf(
                        GatewayBlockPayload(kind = "plan", value = "计划"),
                        GatewayBlockPayload(kind = "commentary", value = "过程自然语言"),
                        GatewayBlockPayload(kind = "toolCall", value = "工具"),
                        GatewayBlockPayload(kind = "webSearch", value = "搜索"),
                        GatewayBlockPayload(kind = "image", value = "图片"),
                        GatewayBlockPayload(kind = "collab", value = "协作"),
                        GatewayBlockPayload(kind = "review", value = "审查"),
                        GatewayBlockPayload(kind = "hook", value = "Hook"),
                        GatewayBlockPayload(kind = "context", value = "上下文")
                    )
                )
            )
        )

        val blocks = patch.applyTo(previous).messages.single().blocks

        assertTrue(blocks[0] is MessageBlock.Plan)
        assertTrue(blocks[1] is MessageBlock.Commentary)
        assertTrue(blocks[2] is MessageBlock.ToolCall)
        assertTrue(blocks[3] is MessageBlock.WebSearch)
        assertTrue(blocks[4] is MessageBlock.Image)
        assertTrue(blocks[5] is MessageBlock.Collab)
        assertTrue(blocks[6] is MessageBlock.Review)
        assertTrue(blocks[7] is MessageBlock.Hook)
        assertTrue(blocks[8] is MessageBlock.Context)
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
