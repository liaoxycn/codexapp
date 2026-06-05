package com.codexapp.ui

import com.codexapp.debug.toAgentDebugJson
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.GatewayConfig
import com.codexapp.model.HomeUiState
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.ThreadMessage
import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentDebugStateTest {
    @Test
    fun stateSummaryExposesAgentDebugFields() {
        val state = HomeUiState(
            threads = listOf(
                ThreadSummary(
                    id = "thread-1",
                    title = "Agent debug",
                    preview = "preview",
                    status = ThreadStatus.RUNNING,
                    cwd = "D:/Projects/App"
                )
            ),
            selectedThreadId = "thread-1",
            pendingSelectionThreadId = null,
            pendingThreadTitle = null,
            isThreadSwitching = false,
            messages = listOf(
                ThreadMessage(
                    id = "system-1",
                    role = MessageRole.SYSTEM,
                    blocks = listOf(MessageBlock.Text("目标已清除"))
                ),
                ThreadMessage(
                    id = "assistant-1",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(
                        MessageBlock.Commentary("正在检查状态"),
                        MessageBlock.FileChangeMeta("已编辑 Main.kt", "app/src/Main.kt")
                    ),
                    durationMs = 2_000L,
                    isFinal = true
                )
            ),
            hasMoreHistory = false,
            isLoadingOlder = false,
            composerText = "hello",
            composerFocusRequest = 0L,
            isGenerating = true,
            isManualRefreshing = false,
            showComposerDetails = false,
            chips = emptyList(),
            files = emptyList(),
            slashCommands = emptyList(),
            pendingApproval = "允许命令？",
            cwd = "D:/Projects/App",
            permissionSummary = "danger-full-access",
            connectionStatus = ConnectionStatus.CONNECTED,
            connectionDetail = "已连接",
            gatewayConfig = GatewayConfig(url = "ws://10.0.2.2:8765/mobile"),
            isDemoMode = false,
            isNewThreadDraft = false,
            newThreadDraft = NewThreadDraft(model = "gpt-5.5", reasoningEffort = "high")
        )

        val json = state.toAgentDebugJson(messageLimit = 5, threadLimit = 5)
        val message = json.getValue("messages").jsonArray[1].jsonObject
        val block = message.getValue("blocks").jsonArray[1].jsonObject
        val summary = json.getValue("testSummary").jsonObject

        assertEquals("codexapp.agentDebug.v1", json.getValue("schema").jsonPrimitive.content)
        assertEquals("thread-1", json.getValue("selectedThreadId").jsonPrimitive.content)
        assertEquals("Agent debug", json.getValue("selectedThreadTitle").jsonPrimitive.content)
        assertEquals("running", json.getValue("selectedThreadStatus").jsonPrimitive.content)
        assertEquals("D:/Projects/App", json.getValue("selectedThreadCwd").jsonPrimitive.content)
        assertEquals("true", json.getValue("isGenerating").jsonPrimitive.content)
        assertEquals("允许命令？", json.getValue("pendingApproval").jsonPrimitive.content)
        assertEquals("assistant", message.getValue("role").jsonPrimitive.content)
        assertEquals("fileChangeMeta", block.getValue("kind").jsonPrimitive.content)
        assertEquals("app/src/Main.kt", block.getValue("path").jsonPrimitive.content)
        assertEquals("1", summary.getValue("systemMessageCount").jsonPrimitive.content)
        assertEquals("1", summary.getValue("assistantMessageCount").jsonPrimitive.content)
        assertEquals("2", summary.getValue("processBlockCount").jsonPrimitive.content)
        assertEquals("1", summary.getValue("commentaryBlockCount").jsonPrimitive.content)
        assertEquals("1", summary.getValue("fileChangeBlockCount").jsonPrimitive.content)
        assertEquals(
            "1",
            summary.getValue("blockKindCounts").jsonObject.getValue("fileChangeMeta").jsonPrimitive.content
        )
        assertEquals("thread_message_list", json.getValue("uiHints").jsonObject.getValue("messageListTag").jsonPrimitive.content)
    }

    @Test
    fun stateSummaryExposesProcessDetailsForAgentAssertions() {
        val state = HomeUiState(
            threads = listOf(
                ThreadSummary(
                    id = "thread-2",
                    title = "Agent process",
                    preview = "preview",
                    status = ThreadStatus.IDLE
                )
            ),
            selectedThreadId = "thread-2",
            pendingSelectionThreadId = null,
            pendingThreadTitle = null,
            isThreadSwitching = false,
            messages = listOf(
                ThreadMessage(
                    id = "assistant-2",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(
                        MessageBlock.CommandSummary("ran command"),
                        MessageBlock.CommandMeta("exit code 0"),
                        MessageBlock.Code("shell", "agent output"),
                        MessageBlock.Text("final text")
                    ),
                    isFinal = false
                )
            ),
            hasMoreHistory = false,
            isLoadingOlder = false,
            composerText = "",
            composerFocusRequest = 0L,
            isGenerating = false,
            isManualRefreshing = false,
            showComposerDetails = false,
            chips = emptyList(),
            files = emptyList(),
            slashCommands = emptyList(),
            pendingApproval = null,
            cwd = "",
            permissionSummary = "",
            connectionStatus = ConnectionStatus.CONNECTED,
            connectionDetail = "",
            gatewayConfig = GatewayConfig(url = "ws://10.0.2.2:8765/mobile"),
            isDemoMode = false,
            isNewThreadDraft = false,
            newThreadDraft = NewThreadDraft()
        )

        val json = state.toAgentDebugJson(messageLimit = 5, threadLimit = 5)
        val message = json.getValue("messages").jsonArray.single().jsonObject
        val summary = json.getValue("testSummary").jsonObject

        assertEquals("ws://10.0.2.2:8765/mobile", json.getValue("gatewayUrl").jsonPrimitive.content)
        assertEquals("3", message.getValue("processBlockCount").jsonPrimitive.content)
        assertEquals("1", summary.getValue("nonFinalAssistantMessageCount").jsonPrimitive.content)
        assertEquals("0", summary.getValue("runningAssistantMessageCount").jsonPrimitive.content)
        assertEquals("3", summary.getValue("processBlockCount").jsonPrimitive.content)
        assertEquals("1", summary.getValue("commandOutputBlockCount").jsonPrimitive.content)
        assertEquals("true", summary.getValue("lastAssistantHasProcess").jsonPrimitive.content)
        assertEquals("true", summary.getValue("lastAssistantHasFinalText").jsonPrimitive.content)
        assertEquals("3", summary.getValue("lastAssistantProcessBlockCount").jsonPrimitive.content)
        assertEquals("commandSummary", summary.getValue("lastAssistantProcessBlockKinds").jsonArray[0].jsonPrimitive.content)
        assertEquals("code", summary.getValue("lastAssistantProcessBlockKinds").jsonArray[2].jsonPrimitive.content)
        assertEquals("ran command exit code 0 agent output", summary.getValue("lastAssistantProcessPreview").jsonPrimitive.content)
    }
}
