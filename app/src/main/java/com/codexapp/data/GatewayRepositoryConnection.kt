package com.codexapp.data

import android.util.Log
import com.codexapp.data.gateway.GatewayCommandSender
import com.codexapp.data.gateway.GatewayWebSocketClient
import com.codexapp.data.gateway.decorateConnectionError
import com.codexapp.data.gateway.summarizeInboundForLog
import kotlinx.serialization.json.Json
import com.codexapp.model.GatewayConfig
import com.codexapp.model.SessionRemoteState

internal class GatewayRepositoryConnection(
    private val gatewayClient: GatewayWebSocketClient,
    private val commandSender: GatewayCommandSender,
    private val json: Json,
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
                Log.d(tag, "inbound: ${summarizeInboundForLog(json, raw)}")
                onInboundRawMessage(raw)
            }
        )
    }

    fun disconnect() {
        gatewayClient.disconnect()
        updateState(SessionRemoteState::withManualDisconnect)
    }
}
