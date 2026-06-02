package com.codexapp.data

import android.content.Context
import com.codexapp.data.gateway.GatewayCommandSender
import com.codexapp.data.gateway.GatewaySettingsStore
import com.codexapp.data.gateway.GatewayWebSocketClient
import com.codexapp.data.gateway.emptyRemoteState
import com.codexapp.data.gateway.normalizeGatewayUrl
import com.codexapp.model.GatewayConfig
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.SessionRemoteState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

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
    private val commandSender = GatewayCommandSender(json, gatewayClient::send)
    private val commandMutex = Mutex()
    private val _state = MutableStateFlow(
        emptyRemoteState(settingsStore.load())
    )
    private val commandActions = GatewayRepositoryCommandActions(
        commandSender = commandSender,
        readState = { _state.value },
        updateState = { transform -> _state.update(transform) },
        tag = tag
    )
    private val connection = GatewayRepositoryConnection(
        gatewayClient = gatewayClient,
        commandSender = commandSender,
        json = json,
        readState = { _state.value },
        updateState = { transform -> _state.update(transform) },
        onInboundRawMessage = ::handleInboundMessage
    )

    override val state: StateFlow<SessionRemoteState> = _state.asStateFlow()

    override suspend fun connect(config: GatewayConfig) {
        val normalized = config.copy(url = normalizeGatewayUrl(config.url))
        if (normalized.url.isBlank()) {
            _state.update { it.withBlankGatewayUrl(normalized) }
            return
        }
        settingsStore.save(normalized)
        _state.update { it.withConnectingGateway(normalized) }
        connection.connect(normalized)
    }

    override suspend fun disconnect() {
        connection.disconnect()
    }

    override suspend fun createThread(cwd: String?, draft: NewThreadDraft?): Boolean = commandMutex.withLock {
        commandActions.createThread(cwd, draft)
    }

    override suspend fun selectThread(id: String): Boolean = commandMutex.withLock {
        commandActions.selectThread(id)
    }

    override suspend fun forkThread(id: String, numTurns: Int?): Boolean = commandMutex.withLock {
        commandActions.forkThread(id, numTurns)
    }

    override suspend fun renameThread(id: String, name: String): Boolean = commandMutex.withLock {
        commandActions.renameThread(id, name)
    }

    override suspend fun archiveThread(id: String): Boolean = commandMutex.withLock {
        commandActions.archiveThread(id)
    }

    override suspend fun unarchiveThread(id: String): Boolean = commandMutex.withLock {
        commandActions.unarchiveThread(id)
    }

    override suspend fun refreshThreads(): Boolean = commandMutex.withLock {
        commandActions.refreshThreads()
    }

    override suspend fun loadOlderMessages(): Boolean = commandMutex.withLock {
        commandActions.loadOlderMessages()
    }

    override suspend fun sendPrompt(prompt: String, draft: NewThreadDraft?, newThread: Boolean): Boolean {
        return commandMutex.withLock { commandActions.sendPrompt(prompt, draft, newThread) }
    }

    override suspend fun rollbackThread(numTurns: Int): Boolean {
        return commandMutex.withLock { commandActions.rollbackThread(numTurns) }
    }

    override suspend fun resendPrompt(prompt: String, rollbackNumTurns: Int, draft: NewThreadDraft?): Boolean {
        return commandMutex.withLock { commandActions.resendPrompt(prompt, rollbackNumTurns, draft) }
    }

    override suspend fun stopTurn(): Boolean = commandMutex.withLock {
        commandActions.stopTurn()
    }

    override suspend fun approvePending(): Boolean = commandMutex.withLock {
        commandActions.approvePending()
    }

    override suspend fun rejectPending(): Boolean = commandMutex.withLock {
        commandActions.rejectPending()
    }

    override suspend fun restartDesktop(): Boolean = commandMutex.withLock {
        commandActions.restartDesktop()
    }

    private fun handleInboundMessage(raw: String) {
        var shouldRequestFullSnapshot = false
        _state.update { previous ->
            reduceGatewayInboundState(
                json = json,
                previous = previous,
                raw = raw,
                onSnapshotPatchMismatch = { shouldRequestFullSnapshot = true }
            )
        }
        if (shouldRequestFullSnapshot) {
            commandSender.refreshThreads(forceSnapshot = true)
        }
    }

    override fun markManualRefreshing(refreshing: Boolean) {
        commandActions.markManualRefreshing(refreshing)
    }
}

