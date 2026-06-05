package com.codexapp.data

import com.codexapp.data.gateway.GatewayCommandSender
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.GatewayConfig
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        assertEquals("", state.selectedThreadId)
        assertNull(state.pendingSelectionThreadId)
        assertNull(state.pendingThreadTitle)
        assertFalse(state.isThreadSwitching)
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
        assertEquals(1, state.messages.size)
        assertTrue(state.isGenerating)
    }

    @Test
    fun sendPromptForNewDraftOmitsExistingThreadAndSendsDraftOptions() {
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

        val accepted = actions.sendPrompt(
            "hello new",
            NewThreadDraft(
                cwd = "D:/Projects/App",
                model = "gpt-5",
                reasoningEffort = "high",
                permissionMode = "default"
            ),
            newThread = true
        )

        assertTrue(accepted)
        assertTrue(sentMessages.single().contains("\"newThread\":true"))
        assertFalse(sentMessages.single().contains("\"threadId\":\"thread-1\""))
        assertTrue(sentMessages.single().contains("\"cwd\":\"D:/Projects/App\""))
        assertTrue(sentMessages.single().contains("\"approvalPolicy\":\"on-request\""))
        assertTrue(sentMessages.single().contains("\"approvalsReviewer\":\"user\""))
        assertTrue(sentMessages.single().contains("\"sandboxMode\":\"workspace-write\""))
        assertEquals(1, state.messages.size)
        assertEquals("", state.selectedThreadId)
        assertEquals("新会话", state.pendingThreadTitle)
        assertTrue(state.isThreadSwitching)
        assertTrue(state.isGenerating)
    }

    @Test
    fun sendPromptForDefaultNewDraftDoesNotSendProjectCwd() {
        var state = SessionRemoteState(
            connectionStatus = ConnectionStatus.CONNECTED,
            selectedThreadId = "thread-project",
            cwd = "D:/Projects/SelectedProject",
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

        val accepted = actions.sendPrompt(
            "hello ordinary chat",
            NewThreadDraft(model = "gpt-5", reasoningEffort = "high"),
            newThread = true
        )

        assertTrue(accepted)
        assertTrue(sentMessages.single().contains("\"newThread\":true"))
        assertFalse(sentMessages.single().contains("\"threadId\":\"thread-project\""))
        assertFalse(sentMessages.single().contains("\"cwd\""))
        assertTrue(sentMessages.single().contains("\"approvalPolicy\":\"never\""))
        assertTrue(sentMessages.single().contains("\"approvalsReviewer\":\"user\""))
        assertTrue(sentMessages.single().contains("\"sandboxMode\":\"danger-full-access\""))
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
        assertTrue(actions.forkThread("thread-2", 2))
        assertTrue(actions.archiveThread("thread-3"))
        assertTrue(actions.unarchiveThread("thread-4"))
        assertTrue(actions.restartDesktop())

        assertTrue(sentMessages[0].contains("\"type\":\"rename_thread\""))
        assertTrue(sentMessages[0].contains("\"name\":\"Renamed\""))
        assertTrue(sentMessages[1].contains("\"type\":\"fork_thread\""))
        assertTrue(sentMessages[1].contains("\"numTurns\":2"))
        assertTrue(sentMessages[2].contains("\"type\":\"archive_thread\""))
        assertTrue(sentMessages[3].contains("\"type\":\"unarchive_thread\""))
        assertTrue(sentMessages[4].contains("\"type\":\"restart_desktop\""))
    }

    @Test
    fun archiveCurrentThreadClearsLocalSelectedThreadAfterSend() {
        var state = SessionRemoteState(
            connectionStatus = ConnectionStatus.CONNECTED,
            selectedThreadId = "thread-1",
            threads = listOf(
                ThreadSummary(
                    id = "thread-1",
                    title = "Archived",
                    preview = "",
                    status = ThreadStatus.IDLE
                )
            ),
            messages = listOf(
                com.codexapp.model.ThreadMessage(
                    id = "user-1",
                    role = com.codexapp.model.MessageRole.USER,
                    blocks = listOf(com.codexapp.model.MessageBlock.Text("hello"))
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

        val accepted = actions.archiveThread("thread-1")

        assertTrue(accepted)
        assertTrue(sentMessages.single().contains("\"type\":\"archive_thread\""))
        assertEquals("", state.selectedThreadId)
        assertTrue(state.threads.isEmpty())
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isGenerating)
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

    @Test
    fun createThreadClearsSwitchingStateWhenSendFails() {
        var state = SessionRemoteState(
            connectionStatus = ConnectionStatus.CONNECTED,
            selectedThreadId = "thread-1"
        )
        val actions = GatewayRepositoryCommandActions(
            commandSender = GatewayCommandSender(json) { false },
            readState = { state },
            updateState = { transform -> state = transform(state) },
            logDebug = {}
        )

        val accepted = actions.createThread("D:/Projects/home/codexapp")

        assertFalse(accepted)
        assertEquals("thread-1", state.selectedThreadId)
        assertNull(state.pendingThreadTitle)
        assertFalse(state.isThreadSwitching)
        assertEquals("新建会话失败，gateway 连接已断开", state.connectionDetail)
    }

    @Test
    fun loadOlderMessagesClearsLoadingFlagWhenSendFails() {
        var state = SessionRemoteState(connectionStatus = ConnectionStatus.CONNECTED)
        val actions = GatewayRepositoryCommandActions(
            commandSender = GatewayCommandSender(json) { false },
            readState = { state },
            updateState = { transform -> state = transform(state) },
            logDebug = {}
        )

        val accepted = actions.loadOlderMessages()

        assertFalse(accepted)
        assertFalse(state.isLoadingOlder)
        assertEquals("加载历史失败，gateway 连接已断开", state.connectionDetail)
    }

    @Test
    fun sessionRepositorySerializesGatewayCommands() = runBlocking {
        val releaseFirst = CountDownLatch(1)
        val firstEntered = CountDownLatch(1)
        val sendOrder = mutableListOf<String>()
        val mutex = Mutex()
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val commandSender = GatewayCommandSender(json) { payload ->
            sendOrder += payload
            if (sendOrder.size == 1) {
                firstEntered.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
            true
        }

        val first = async(Dispatchers.Default) {
            mutex.withLock { commandSender.selectThread("thread-1") }
        }
        assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
        val second = async(Dispatchers.Default) {
            mutex.withLock { commandSender.archiveThread("thread-2") }
        }

        assertFalse(second.isCompleted)
        releaseFirst.countDown()
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(2, sendOrder.size)
    }
}
