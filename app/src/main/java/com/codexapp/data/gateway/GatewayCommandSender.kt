package com.codexapp.data.gateway

import kotlinx.serialization.json.Json

internal class GatewayCommandSender(
    private val json: Json,
    private val sendText: (String) -> Boolean
) {
    fun sendHello(pairToken: String, selectedThreadId: String? = null): Boolean {
        return send(
            GatewayHelloMessage(
                pairToken = pairToken.ifBlank { null },
                selectedThreadId = selectedThreadId?.takeIf { it.isNotBlank() }
            )
        )
    }

    fun createThread(
        cwd: String?,
        model: String? = null,
        reasoningEffort: String? = null,
        sandboxMode: String? = null
    ): Boolean {
        return send(
            GatewayCreateThreadMessage(
                cwd = cwd?.takeIf { it.isNotBlank() },
                model = model?.takeIf { it.isNotBlank() },
                reasoningEffort = reasoningEffort?.takeIf { it.isNotBlank() },
                sandboxMode = sandboxMode?.takeIf { it.isNotBlank() }
            )
        )
    }

    fun selectThread(threadId: String): Boolean {
        return send(
            GatewaySelectThreadMessage(threadId = threadId)
        )
    }

    fun forkThread(threadId: String, numTurns: Int? = null): Boolean {
        return send(GatewayForkThreadMessage(threadId = threadId, numTurns = numTurns?.takeIf { it > 0 }))
    }

    fun renameThread(threadId: String, name: String): Boolean {
        return send(GatewayRenameThreadMessage(threadId = threadId, name = name))
    }

    fun archiveThread(threadId: String): Boolean {
        return send(GatewayArchiveThreadMessage(threadId = threadId))
    }

    fun unarchiveThread(threadId: String): Boolean {
        return send(GatewayUnarchiveThreadMessage(threadId = threadId))
    }

    fun refreshThreads(forceSnapshot: Boolean = false): Boolean {
        return send(GatewayRefreshThreadsMessage(forceSnapshot = forceSnapshot))
    }

    fun loadOlderMessages(): Boolean {
        return send(GatewayLoadOlderMessagesMessage())
    }

    fun sendPrompt(
        text: String,
        threadId: String?,
        newThread: Boolean = false,
        cwd: String? = null,
        model: String? = null,
        reasoningEffort: String? = null,
        sandboxMode: String? = null
    ): Boolean {
        return send(
            GatewaySendPromptMessage(
                text = text,
                threadId = threadId,
                newThread = newThread,
                cwd = cwd?.takeIf { it.isNotBlank() },
                model = model?.takeIf { it.isNotBlank() },
                reasoningEffort = reasoningEffort?.takeIf { it.isNotBlank() },
                sandboxMode = sandboxMode?.takeIf { it.isNotBlank() }
            )
        )
    }

    fun rollbackThread(threadId: String?, numTurns: Int): Boolean {
        return send(
            GatewayRollbackThreadMessage(
                threadId = threadId?.takeIf { it.isNotBlank() },
                numTurns = numTurns.coerceAtLeast(1)
            )
        )
    }

    fun resendPrompt(text: String, threadId: String?, rollbackNumTurns: Int): Boolean {
        return send(
            GatewayResendPromptMessage(
                text = text,
                threadId = threadId?.takeIf { it.isNotBlank() },
                rollbackNumTurns = rollbackNumTurns.coerceAtLeast(1)
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

    fun restartDesktop(): Boolean {
        return send(GatewayRestartDesktopMessage())
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

    private fun send(message: GatewayForkThreadMessage): Boolean {
        return sendText(json.encodeToString(GatewayForkThreadMessage.serializer(), message))
    }

    private fun send(message: GatewayRenameThreadMessage): Boolean {
        return sendText(json.encodeToString(GatewayRenameThreadMessage.serializer(), message))
    }

    private fun send(message: GatewayArchiveThreadMessage): Boolean {
        return sendText(json.encodeToString(GatewayArchiveThreadMessage.serializer(), message))
    }

    private fun send(message: GatewayUnarchiveThreadMessage): Boolean {
        return sendText(json.encodeToString(GatewayUnarchiveThreadMessage.serializer(), message))
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

    private fun send(message: GatewayRollbackThreadMessage): Boolean {
        return sendText(json.encodeToString(GatewayRollbackThreadMessage.serializer(), message))
    }

    private fun send(message: GatewayResendPromptMessage): Boolean {
        return sendText(json.encodeToString(GatewayResendPromptMessage.serializer(), message))
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

    private fun send(message: GatewayRestartDesktopMessage): Boolean {
        return sendText(json.encodeToString(GatewayRestartDesktopMessage.serializer(), message))
    }
}
