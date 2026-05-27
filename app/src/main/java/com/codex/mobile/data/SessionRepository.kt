package com.codex.mobile.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.codex.mobile.model.ComposerChip
import com.codex.mobile.model.ComposerChipIcon
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

interface SessionRepository {
    val state: StateFlow<SessionRemoteState>

    suspend fun connect(config: GatewayConfig)
    suspend fun disconnect()
    suspend fun createThread()
    suspend fun selectThread(id: String)
    suspend fun refreshThreads()
    suspend fun loadOlderMessages()
    suspend fun sendPrompt(prompt: String): Boolean
    suspend fun stopTurn()
    suspend fun approvePending()
    suspend fun rejectPending()
}

class DefaultSessionRepository(
    context: Context
) : SessionRepository {
    private val tag = "CodexGateway"
    private val settingsStore = GatewaySettingsStore(context.applicationContext)
    private val gatewayClient = GatewayWebSocketClient()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val _state = MutableStateFlow(
        emptyRemoteState(settingsStore.load())
    )

    override val state: StateFlow<SessionRemoteState> = _state.asStateFlow()

    override suspend fun connect(config: GatewayConfig) {
        val normalized = config.copy(url = normalizeUrl(config.url))
        if (normalized.url.isBlank()) {
            _state.update {
                it.copy(
                    gatewayConfig = normalized,
                    connectionStatus = ConnectionStatus.ERROR,
                    connectionDetail = "网关地址不能为空"
                )
            }
            return
        }
        settingsStore.save(normalized)
        _state.update {
            it.copy(
                gatewayConfig = normalized,
                connectionStatus = ConnectionStatus.CONNECTING,
                connectionDetail = "正在连接 ${normalized.url}",
                isDemoMode = true
            )
        }
        gatewayClient.connect(
            config = normalized,
            onOpen = { socket ->
                Log.d(tag, "websocket opened: ${normalized.url}")
                _state.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.CONNECTING,
                        connectionDetail = "已建立连接，正在同步会话",
                        gatewayConfig = normalized
                    )
                }
                val sent = socket.send(
                    json.encodeToString(
                        GatewayHelloMessage.serializer(),
                        GatewayHelloMessage(pairToken = normalized.pairToken.ifBlank { null })
                    )
                )
                Log.d(tag, "hello sent=$sent")
                if (!sent) {
                    _state.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.ERROR,
                            connectionDetail = "hello 发送失败，请重新连接 gateway",
                            isDemoMode = true
                        )
                    }
                }
            },
            onClosed = { reason ->
                Log.d(tag, "websocket closed: $reason")
                _state.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        connectionDetail = if (reason.isBlank()) "desktop gateway 已断开" else reason,
                        isDemoMode = true
                    )
                }
            },
            onFailure = { error ->
                Log.e(tag, "websocket failure: $error")
                _state.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.ERROR,
                        connectionDetail = decorateConnectionError(normalized.url, error),
                        isDemoMode = true
                    )
                }
            },
            onMessage = {
                Log.d(tag, "inbound: $it")
                handleInboundMessage(it)
            }
        )
    }

    override suspend fun disconnect() {
        gatewayClient.disconnect()
        _state.update {
            it.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                connectionDetail = "已断开 desktop gateway",
                isDemoMode = true,
                isGenerating = false,
                pendingApproval = null
            )
        }
    }

    override suspend fun createThread() {
        if (_state.value.connectionStatus == ConnectionStatus.CONNECTED) {
            _state.update { remote ->
                remote.copy(
                    pendingThreadTitle = "新会话",
                    isThreadSwitching = true,
                    selectedThreadId = "",
                    messages = emptyList(),
                    hasMoreHistory = false,
                    pendingApproval = null,
                    isGenerating = false
                )
            }
            gatewayClient.send(
                json.encodeToString(
                    GatewayCreateThreadMessage.serializer(),
                    GatewayCreateThreadMessage()
                )
            )
            return
        }
        markActionUnavailable("未连接 gateway，无法新建会话")
    }

    override suspend fun selectThread(id: String) {
        if (id.isBlank()) return
        if (_state.value.connectionStatus == ConnectionStatus.CONNECTED) {
            val nextTitle = _state.value.threads.firstOrNull { it.id == id }?.title
            _state.update {
                it.copy(
                    selectedThreadId = id,
                    pendingThreadTitle = nextTitle,
                    isThreadSwitching = true,
                    messages = emptyList(),
                    hasMoreHistory = false,
                    pendingApproval = null,
                    isGenerating = false
                )
            }
            gatewayClient.send(
                json.encodeToString(
                    GatewaySelectThreadMessage.serializer(),
                    GatewaySelectThreadMessage(threadId = id)
                )
            )
            return
        }
        markActionUnavailable("未连接 gateway，无法切换会话")
    }

    override suspend fun refreshThreads() {
        if (_state.value.connectionStatus == ConnectionStatus.CONNECTED) {
            gatewayClient.send(
                json.encodeToString(
                    GatewayRefreshThreadsMessage.serializer(),
                    GatewayRefreshThreadsMessage()
                )
            )
            return
        }
        markActionUnavailable("未连接 gateway，无法刷新会话")
    }

    override suspend fun loadOlderMessages() {
        if (_state.value.connectionStatus == ConnectionStatus.CONNECTED) {
            gatewayClient.send(
                json.encodeToString(
                    GatewayLoadOlderMessagesMessage.serializer(),
                    GatewayLoadOlderMessagesMessage()
                )
            )
            return
        }
        markActionUnavailable("未连接 gateway，无法加载历史")
    }

    override suspend fun sendPrompt(prompt: String): Boolean {
        if (prompt.isBlank()) return false
        if (_state.value.connectionStatus == ConnectionStatus.CONNECTING) {
            _state.update {
                it.copy(connectionDetail = "正在同步会话，请稍后再发")
            }
            return false
        }
        if (_state.value.connectionStatus != ConnectionStatus.CONNECTED) {
            markActionUnavailable("未连接 gateway，请先连接后再发送")
            return false
        }
        val sent = gatewayClient.send(
            json.encodeToString(
                GatewaySendPromptMessage.serializer(),
                GatewaySendPromptMessage(text = prompt)
            )
        )
        Log.d(tag, "send_prompt sent=$sent")
        if (!sent) {
            applySendFailure("发送失败，gateway 连接已断开")
            return false
        }
        optimisticAppendPrompt(prompt)
        return true
    }

    override suspend fun stopTurn() {
        if (!_state.value.isGenerating) return
        if (_state.value.connectionStatus == ConnectionStatus.CONNECTED) {
            gatewayClient.send(
                json.encodeToString(
                    GatewayStopTurnMessage.serializer(),
                    GatewayStopTurnMessage()
                )
            )
            return
        }
        markActionUnavailable("未连接 gateway，无法停止")
    }

    override suspend fun approvePending() {
        if (_state.value.connectionStatus == ConnectionStatus.CONNECTED) {
            gatewayClient.send(
                json.encodeToString(
                    GatewayApprovePendingMessage.serializer(),
                    GatewayApprovePendingMessage()
                )
            )
            return
        }
        markActionUnavailable("未连接 gateway，无法审批")
    }

    override suspend fun rejectPending() {
        if (_state.value.connectionStatus == ConnectionStatus.CONNECTED) {
            gatewayClient.send(
                json.encodeToString(
                    GatewayRejectPendingMessage.serializer(),
                    GatewayRejectPendingMessage()
                )
            )
            return
        }
        markActionUnavailable("未连接 gateway，无法审批")
    }

    private fun optimisticAppendPrompt(prompt: String) {
        val userMessage = ThreadMessage(
            id = "user-${System.currentTimeMillis()}",
            role = MessageRole.USER,
            blocks = listOf(MessageBlock.Text(prompt))
        )
        _state.update {
            it.copy(
                isGenerating = true,
                messages = if (it.isGenerating) {
                    it.messages + userMessage
                } else {
                    it.messages + userMessage + ThreadMessage(
                        id = "assistant-pending",
                        role = MessageRole.ASSISTANT,
                        blocks = listOf(MessageBlock.Status("正在生成…"))
                    )
                }
            )
        }
    }

    private fun handleInboundMessage(raw: String) {
        runCatching {
            when (json.decodeFromString(GatewayEnvelope.serializer(), raw).type) {
                "snapshot" -> applySnapshot(
                    json.decodeFromString(GatewaySnapshotMessage.serializer(), raw)
                )

                "status" -> applyStatus(
                    json.decodeFromString(GatewayStatusMessage.serializer(), raw)
                )
            }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    connectionDetail = "网关消息解析失败: ${error.message}",
                    isDemoMode = true
                )
            }
        }
    }

    private fun applySnapshot(snapshot: GatewaySnapshotMessage) {
        val threads = snapshot.threads.map {
            ThreadSummary(
                id = it.id,
                title = it.title,
                preview = it.preview,
                status = it.status.toThreadStatus(),
                updatedAt = it.updatedAt ?: 0L
            )
        }
        val selectedThread = threads.firstOrNull { it.id == snapshot.selectedThreadId }
        val selectedIsRunning = selectedThread?.status == ThreadStatus.RUNNING
        val messages = snapshot.messages.map { message ->
            ThreadMessage(
                id = message.id,
                role = message.role.toMessageRole(),
                blocks = message.blocks.map { block ->
                    when (block.kind) {
                        "code" -> MessageBlock.Code(
                            language = block.language ?: "text",
                            value = block.value
                        )

                        "status" -> MessageBlock.Status(block.value)
                        else -> MessageBlock.Text(block.value)
                    }
                }
            )
        }
        _state.update {
            it.copy(
                threads = threads,
                selectedThreadId = snapshot.selectedThreadId ?: threads.firstOrNull()?.id.orEmpty(),
                pendingThreadTitle = null,
                isThreadSwitching = false,
                messages = messages,
                hasMoreHistory = snapshot.hasMoreHistory,
                pendingApproval = snapshot.pendingApproval,
                chips = snapshot.chips.map { chip ->
                    ComposerChip(
                        label = chip.label,
                        icon = if (chip.icon == "context") ComposerChipIcon.CONTEXT else ComposerChipIcon.FILE
                    )
                },
                slashCommands = snapshot.slashCommands,
                cwd = snapshot.cwd.orEmpty(),
                permissionSummary = snapshot.permissionSummary.orEmpty(),
                isGenerating = snapshot.isGenerating || selectedIsRunning,
                connectionStatus = ConnectionStatus.CONNECTED,
                connectionDetail = if (threads.isEmpty()) "已连接，暂无会话" else "已同步 ${threads.size} 个会话",
                isDemoMode = false
            )
        }
    }

    private fun applyStatus(status: GatewayStatusMessage) {
        _state.update {
            val nextStatus = when (status.status.lowercase()) {
                "connected" -> ConnectionStatus.CONNECTED
                else -> status.status.toConnectionStatus()
            }
            it.copy(
                connectionStatus = nextStatus,
                connectionDetail = status.detail ?: it.connectionDetail,
                isDemoMode = status.status != "connected" && it.isDemoMode
            )
        }
    }

    private fun markActionUnavailable(detail: String) {
        _state.update {
            it.copy(
                connectionDetail = detail,
                isGenerating = false
            )
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) trimmed else "ws://$trimmed"
    }

    private fun applySendFailure(detail: String) {
        _state.update { remote ->
            val pendingMessageRemoved = if (remote.messages.lastOrNull()?.id == "assistant-pending") {
                remote.messages.dropLast(1)
            } else {
                remote.messages
            }
            remote.copy(
                messages = pendingMessageRemoved + ThreadMessage(
                    id = "system-send-failed-${System.currentTimeMillis()}",
                    role = MessageRole.SYSTEM,
                    blocks = listOf(MessageBlock.Status(detail))
                ),
                isGenerating = false,
                connectionStatus = ConnectionStatus.ERROR,
                connectionDetail = detail
            )
        }
    }
}

private class GatewaySettingsStore(
    context: Context
) {
    private val prefs = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)
    private val defaultUrl = defaultGatewayUrl()

    fun load(): GatewayConfig {
        val savedUrl = prefs.getString("url", null)?.trim().orEmpty()
        return GatewayConfig(
            url = savedUrl.ifBlank { defaultUrl },
            pairToken = prefs.getString("pairToken", "") ?: ""
        )
    }

    fun save(config: GatewayConfig) {
        prefs.edit()
            .putString("url", config.url)
            .putString("pairToken", config.pairToken)
            .apply()
    }
}

private class GatewayWebSocketClient {
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null

    fun connect(
        config: GatewayConfig,
        onOpen: (WebSocket) -> Unit,
        onClosed: (String) -> Unit,
        onFailure: (String) -> Unit,
        onMessage: (String) -> Unit
    ) {
        disconnect()
        val request = Request.Builder()
            .url(config.url)
            .build()
        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    this@GatewayWebSocketClient.webSocket = webSocket
                    onOpen(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    onMessage(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onClosed(reason)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onFailure(t.message ?: "连接失败")
                }
            }
        )
    }

    fun send(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "client disconnect")
        webSocket = null
    }
}

private fun decorateConnectionError(url: String, error: String): String {
    val base = error.ifBlank { "连接失败" }
    if (!isProbablyEmulator()) {
        return base
    }
    if (url.contains("10.0.2.2")) {
        return base
    }
    return "$base，模拟器建议改用 ws://10.0.2.2:8765/mobile"
}

private fun defaultGatewayUrl(): String = if (isProbablyEmulator()) {
    "ws://10.0.2.2:8765/mobile"
} else {
    "ws://192.168.31.97:8765/mobile"
}

private fun isProbablyEmulator(): Boolean {
    return Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
        Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
        Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
        Build.PRODUCT.contains("sdk", ignoreCase = true)
}

@Serializable
private data class GatewayEnvelope(
    val type: String
)

@Serializable
private data class GatewayHelloMessage(
    val type: String = "hello",
    val client: String = "android-shell",
    val version: String = "0.2.0",
    val pairToken: String? = null
)

@Serializable
private data class GatewaySelectThreadMessage(
    val type: String = "select_thread",
    val threadId: String
)

@Serializable
private data class GatewayCreateThreadMessage(
    val type: String = "create_thread"
)

@Serializable
private data class GatewayRefreshThreadsMessage(
    val type: String = "refresh_threads"
)

@Serializable
private data class GatewayLoadOlderMessagesMessage(
    val type: String = "load_older_messages"
)

@Serializable
private data class GatewaySendPromptMessage(
    val type: String = "send_prompt",
    val text: String
)

@Serializable
private data class GatewayStopTurnMessage(
    val type: String = "stop_turn"
)

@Serializable
private data class GatewayApprovePendingMessage(
    val type: String = "approve_pending"
)

@Serializable
private data class GatewayRejectPendingMessage(
    val type: String = "reject_pending"
)

@Serializable
private data class GatewayStatusMessage(
    val type: String = "status",
    val status: String,
    val detail: String? = null
)

@Serializable
private data class GatewaySnapshotMessage(
    val type: String = "snapshot",
    val threads: List<GatewayThreadPayload> = emptyList(),
    val selectedThreadId: String? = null,
    val messages: List<GatewayMessagePayload> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val pendingApproval: String? = null,
    val chips: List<GatewayChipPayload> = emptyList(),
    val slashCommands: List<String> = emptyList(),
    val cwd: String? = null,
    val permissionSummary: String? = null,
    val isGenerating: Boolean = false
)

@Serializable
private data class GatewayThreadPayload(
    val id: String,
    val title: String,
    val preview: String,
    val status: String,
    val updatedAt: Long? = null
)

@Serializable
private data class GatewayMessagePayload(
    val id: String,
    val role: String,
    val blocks: List<GatewayBlockPayload> = emptyList()
)

@Serializable
private data class GatewayBlockPayload(
    val kind: String,
    val value: String,
    val language: String? = null
)

@Serializable
private data class GatewayChipPayload(
    val label: String,
    val icon: String
)

private fun String.toThreadStatus(): ThreadStatus = when (this.lowercase()) {
    "running", "inprogress" -> ThreadStatus.RUNNING
    "needs_approval", "approval", "paused" -> ThreadStatus.NEEDS_APPROVAL
    "failed", "error" -> ThreadStatus.FAILED
    else -> ThreadStatus.IDLE
}

private fun String.toMessageRole(): MessageRole = when (this.lowercase()) {
    "user" -> MessageRole.USER
    "system" -> MessageRole.SYSTEM
    else -> MessageRole.ASSISTANT
}

private fun String.toConnectionStatus(): ConnectionStatus = when (this.lowercase()) {
    "connected" -> ConnectionStatus.CONNECTED
    "connecting" -> ConnectionStatus.CONNECTING
    "error", "failed" -> ConnectionStatus.ERROR
    else -> ConnectionStatus.DISCONNECTED
}

private fun emptyRemoteState(
    config: GatewayConfig
): SessionRemoteState = SessionRemoteState(
    threads = emptyList(),
    selectedThreadId = "",
    pendingThreadTitle = null,
    isThreadSwitching = false,
    messages = emptyList(),
    hasMoreHistory = false,
    isGenerating = false,
    chips = emptyList(),
    slashCommands = listOf(
        "/compact  压缩当前上下文",
        "/goal     设置当前会话目标",
        "! ls      执行 shell 命令"
    ),
    pendingApproval = null,
    cwd = "",
    permissionSummary = "",
    connectionStatus = ConnectionStatus.DISCONNECTED,
    connectionDetail = if (config.url.isBlank()) "未连接 desktop gateway" else "未连接 ${config.url}",
    gatewayConfig = config,
    isDemoMode = true
)
