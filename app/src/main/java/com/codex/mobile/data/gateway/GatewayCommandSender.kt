package com.codex.mobile.data.gateway

import kotlinx.serialization.json.Json

internal class GatewayCommandSender(
    private val json: Json,
    private val sendText: (String) -> Boolean
) {
    fun sendHello(pairToken: String): Boolean {
        return send(
            GatewayHelloMessage(
                pairToken = pairToken.ifBlank { null }
            )
        )
    }

    fun createThread(cwd: String?): Boolean {
        return send(
            GatewayCreateThreadMessage(
                cwd = cwd?.takeIf { it.isNotBlank() }
            )
        )
    }

    fun selectThread(threadId: String): Boolean {
        return send(
            GatewaySelectThreadMessage(threadId = threadId)
        )
    }

    fun refreshThreads(): Boolean {
        return send(GatewayRefreshThreadsMessage())
    }

    fun loadOlderMessages(): Boolean {
        return send(GatewayLoadOlderMessagesMessage())
    }

    fun sendPrompt(text: String, threadId: String?): Boolean {
        return send(
            GatewaySendPromptMessage(
                text = text,
                threadId = threadId
            )
        )
    }

    fun stopTurn(): Boolean {
        return send(GatewayStopTurnMessage())
    }

    fun approvePending(): Boolean {
        return send(GatewayApprovePendingMessage())
    }

    fun rejectPending(): Boolean {
        return send(GatewayRejectPendingMessage())
    }

    private fun send(message: GatewayHelloMessage): Boolean {
        return sendText(json.encodeToString(GatewayHelloMessage.serializer(), message))
    }

    private fun send(message: GatewayCreateThreadMessage): Boolean {
        return sendText(json.encodeToString(GatewayCreateThreadMessage.serializer(), message))
    }

    private fun send(message: GatewaySelectThreadMessage): Boolean {
        return sendText(json.encodeToString(GatewaySelectThreadMessage.serializer(), message))
    }

    private fun send(message: GatewayRefreshThreadsMessage): Boolean {
        return sendText(json.encodeToString(GatewayRefreshThreadsMessage.serializer(), message))
    }

    private fun send(message: GatewayLoadOlderMessagesMessage): Boolean {
        return sendText(json.encodeToString(GatewayLoadOlderMessagesMessage.serializer(), message))
    }

    private fun send(message: GatewaySendPromptMessage): Boolean {
        return sendText(json.encodeToString(GatewaySendPromptMessage.serializer(), message))
    }

    private fun send(message: GatewayStopTurnMessage): Boolean {
        return sendText(json.encodeToString(GatewayStopTurnMessage.serializer(), message))
    }

    private fun send(message: GatewayApprovePendingMessage): Boolean {
        return sendText(json.encodeToString(GatewayApprovePendingMessage.serializer(), message))
    }

    private fun send(message: GatewayRejectPendingMessage): Boolean {
        return sendText(json.encodeToString(GatewayRejectPendingMessage.serializer(), message))
    }
}
