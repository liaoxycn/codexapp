package com.codex.mobile.data

import com.codex.mobile.data.gateway.GatewayCommandSender
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRepositoryCommandActionsTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun createThreadMarksUnavailableWhenDisconnected() {
        var state = SessionRemoteState(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            connectionDetail = "未连接 gateway"
        )
        val sentMessages = mutableListOf<String>()
        val actions = GatewayRepositoryCommandActions(
            commandSender = GatewayCommandSender(json) { payload ->
                sentMessages += payload
                true
            },
            readState = { state },
            updateState = { transform -> state = transform(state) },
            logDebug = {}
        )

        val accepted = actions.createThread("D:/Projects/home/codexapp")

        assertFalse(accepted)
        assertTrue(sentMessages.isEmpty())
        assertEquals("未连接 gateway，无法新建会话", state.connectionDetail)
    }

    @Test
    fun selectThreadPrefillsSwitchingStateBeforeSending() {
        var state = SessionRemoteState(
            connectionStatus = ConnectionStatus.CONNECTED,
            threads = listOf(
                ThreadSummary(
                    id = "thread-2",
                    title = "项目重构",
                    preview = "",
                    status = ThreadStatus.IDLE
                )
            )
        )
        val sentMessages = mutableListOf<String>()
        val actions = GatewayRepositoryCommandActions(
            commandSender = GatewayCommandSender(json) { payload ->
                sentMessages += payload
                true
            },
            readState = { state },
            updateState = { transform -> state = transform(state) },
            logDebug = {}
        )

        val accepted = actions.selectThread("thread-2")

        assertTrue(accepted)
        assertEquals("thread-2", state.selectedThreadId)
        assertEquals("项目重构", state.pendingThreadTitle)
        assertTrue(state.isThreadSwitching)
        assertTrue(sentMessages.single().contains("\"type\":\"select_thread\""))
    }

    @Test
    fun sendPromptAppendsOptimisticMessagesForConnectedThread() {
        var state = SessionRemoteState(
            connectionStatus = ConnectionStatus.CONNECTED,
            selectedThreadId = "thread-1",
            gatewayConfig = GatewayConfig(url = "ws://10.0.2.2:8765/mobile")
        )
        val sentMessages = mutableListOf<String>()
        val actions = GatewayRepositoryCommandActions(
            commandSender = GatewayCommandSender(json) { payload ->
                sentMessages += payload
                true
            },
            readState = { state },
            updateState = { transform -> state = transform(state) },
            logDebug = {}
        )

        val accepted = actions.sendPrompt("hello codex")

        assertTrue(accepted)
        assertTrue(sentMessages.single().contains("\"type\":\"send_prompt\""))
        assertTrue(sentMessages.single().contains("\"threadId\":\"thread-1\""))
        assertEquals(2, state.messages.size)
        assertTrue(state.isGenerating)
    }

    @Test
    fun threadManagementActionsSendExpectedMessagesWhenConnected() {
        var state = SessionRemoteState(connectionStatus = ConnectionStatus.CONNECTED)
        val sentMessages = mutableListOf<String>()
        val actions = GatewayRepositoryCommandActions(
            commandSender = GatewayCommandSender(json) { payload ->
                sentMessages += payload
                true
            },
            readState = { state },
            updateState = { transform -> state = transform(state) },
            logDebug = {}
        )

        assertTrue(actions.renameThread("thread-1", "  Renamed  "))
        assertTrue(actions.forkThread("thread-2"))
        assertTrue(actions.archiveThread("thread-3"))
        assertTrue(actions.unarchiveThread("thread-4"))

        assertTrue(sentMessages[0].contains("\"type\":\"rename_thread\""))
        assertTrue(sentMessages[0].contains("\"name\":\"Renamed\""))
        assertTrue(sentMessages[1].contains("\"type\":\"fork_thread\""))
        assertTrue(sentMessages[2].contains("\"type\":\"archive_thread\""))
        assertTrue(sentMessages[3].contains("\"type\":\"unarchive_thread\""))
    }

    @Test
    fun renameThreadRejectsBlankNameBeforeSending() {
        var state = SessionRemoteState(connectionStatus = ConnectionStatus.CONNECTED)
        val sentMessages = mutableListOf<String>()
        val actions = GatewayRepositoryCommandActions(
            commandSender = GatewayCommandSender(json) { payload ->
                sentMessages += payload
                true
            },
            readState = { state },
            updateState = { transform -> state = transform(state) },
            logDebug = {}
        )

        val accepted = actions.renameThread("thread-1", "   ")

        assertFalse(accepted)
        assertTrue(sentMessages.isEmpty())
    }

    @Test
    fun sendPromptTurnsSendFailureIntoSystemErrorMessage() {
        var state = SessionRemoteState(
            connectionStatus = ConnectionStatus.CONNECTED,
            gatewayConfig = GatewayConfig(url = "ws://10.0.2.2:8765/mobile")
        )
        val actions = GatewayRepositoryCommandActions(
            commandSender = GatewayCommandSender(json) { false },
            readState = { state },
            updateState = { transform -> state = transform(state) },
            logDebug = {}
        )

        val accepted = actions.sendPrompt("hello codex")

        assertFalse(accepted)
        assertEquals(ConnectionStatus.ERROR, state.connectionStatus)
        assertEquals("发送失败，gateway 连接已断开", state.connectionDetail)
        assertEquals(1, state.messages.size)
        assertFalse(state.isGenerating)
    }
}
