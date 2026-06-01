package com.codex.mobile.data

import android.util.Log
import com.codex.mobile.data.gateway.GatewayCommandSender
import com.codex.mobile.data.gateway.GatewayWebSocketClient
import com.codex.mobile.data.gateway.decorateConnectionError
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.SessionRemoteState

internal class GatewayRepositoryConnection(
    private val gatewayClient: GatewayWebSocketClient,
    private val commandSender: GatewayCommandSender,
    private val readState: () -> SessionRemoteState,
    private val updateState: ((SessionRemoteState) -> SessionRemoteState) -> Unit,
    private val onInboundRawMessage: (String) -> Unit,
    private val tag: String = "CodexGateway",
) {
    fun connect(config: GatewayConfig) {
        gatewayClient.connect(
            config = config,
            onOpen = {
                Log.d(tag, "websocket opened: ${config.url}")
                updateState { it.withOpenedGateway(config) }
                val sent = commandSender.sendHello(
                    pairToken = config.pairToken,
                    selectedThreadId = readState().selectedThreadId
                )
                Log.d(tag, "hello sent=$sent")
                if (!sent) {
                    updateState(SessionRemoteState::withHelloSendFailure)
                }
            },
            onClosed = { reason ->
                Log.d(tag, "websocket closed: $reason")
                updateState { it.withDisconnectedGateway(reason) }
            },
            onFailure = { error ->
                Log.e(tag, "websocket failure: $error")
                updateState { it.withConnectionFailure(decorateConnectionError(config.url, error)) }
            },
            onMessage = { raw ->
                Log.d(tag, "inbound: $raw")
                onInboundRawMessage(raw)
            }
        )
    }

    fun disconnect() {
        gatewayClient.disconnect()
        updateState(SessionRemoteState::withManualDisconnect)
    }
}
