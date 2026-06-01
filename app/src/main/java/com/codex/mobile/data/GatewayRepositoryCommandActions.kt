package com.codex.mobile.data

import android.util.Log
import com.codex.mobile.data.gateway.GatewayCommandSender
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.SessionRemoteState

internal class GatewayRepositoryCommandActions(
    private val commandSender: GatewayCommandSender,
    private val readState: () -> SessionRemoteState,
    private val updateState: ((SessionRemoteState) -> SessionRemoteState) -> Unit,
    tag: String = "CodexGateway",
    private val logDebug: (String) -> Unit = { message -> Log.d(tag, message) }
) {
    fun createThread(cwd: String?): Boolean {
        return runConnectedAction(
            unavailableDetail = "未连接 gateway，无法新建会话",
            beforeSend = { updateState(SessionRemoteState::startCreatingThread) }
        ) {
            commandSender.createThread(cwd)
        }
    }

    fun selectThread(id: String): Boolean {
        if (id.isBlank()) return false
        return runConnectedAction(
            unavailableDetail = "未连接 gateway，无法切换会话",
            beforeSend = {
                val nextTitle = readState().threads.firstOrNull { it.id == id }?.title
                updateState { it.startSelectingThread(id, nextTitle) }
            }
        ) {
            commandSender.selectThread(id)
        }
    }

    fun renameThread(id: String, name: String): Boolean {
        val trimmedName = name.trim()
        if (id.isBlank() || trimmedName.isBlank()) return false
        return runConnectedAction("未连接 gateway，无法重命名会话") {
            commandSender.renameThread(id, trimmedName)
        }
    }

    fun forkThread(id: String): Boolean {
        if (id.isBlank()) return false
        return runConnectedAction("未连接 gateway，无法分叉会话") {
            commandSender.forkThread(id)
        }
    }

    fun archiveThread(id: String): Boolean {
        if (id.isBlank()) return false
        return runConnectedAction("未连接 gateway，无法归档会话") {
            commandSender.archiveThread(id)
        }
    }

    fun unarchiveThread(id: String): Boolean {
        if (id.isBlank()) return false
        return runConnectedAction("未连接 gateway，无法恢复会话") {
            commandSender.unarchiveThread(id)
        }
    }

    fun refreshThreads(): Boolean {
        return runConnectedAction("未连接 gateway，无法刷新会话") {
            commandSender.refreshThreads()
        }
    }

    fun loadOlderMessages(): Boolean {
        return runConnectedAction(
            unavailableDetail = "未连接 gateway，无法加载历史",
            beforeSend = { updateState(SessionRemoteState::startLoadingOlderMessages) }
        ) {
            commandSender.loadOlderMessages()
        }
    }

    fun sendPrompt(prompt: String): Boolean {
        if (prompt.isBlank()) return false
        val snapshot = readState()
        val targetThreadId = snapshot.selectedThreadId.ifBlank { null }
        if (snapshot.connectionStatus == ConnectionStatus.CONNECTING) {
            updateState { it.withConnectionDetail("正在同步会话，请稍后再发") }
            return false
        }
        if (snapshot.connectionStatus != ConnectionStatus.CONNECTED) {
            markActionUnavailable("未连接 gateway，请先连接后再发送")
            return false
        }
        val sent = commandSender.sendPrompt(prompt, targetThreadId)
        logDebug("send_prompt sent=$sent")
        if (!sent) {
            updateState { it.withSendFailure("发送失败，gateway 连接已断开") }
            return false
        }
        updateState { it.withOptimisticPrompt(prompt) }
        return true
    }

    fun stopTurn(): Boolean {
        if (!readState().isGenerating) return false
        return runConnectedAction("未连接 gateway，无法停止") {
            commandSender.stopTurn()
        }
    }

    fun approvePending(): Boolean {
        return runConnectedAction("未连接 gateway，无法审批") {
            commandSender.approvePending()
        }
    }

    fun rejectPending(): Boolean {
        return runConnectedAction("未连接 gateway，无法审批") {
            commandSender.rejectPending()
        }
    }

    fun markManualRefreshing(refreshing: Boolean) {
        updateState { it.withManualRefreshing(refreshing) }
    }

    private inline fun runConnectedAction(
        unavailableDetail: String,
        noinline beforeSend: (() -> Unit)? = null,
        send: () -> Boolean
    ): Boolean {
        if (readState().connectionStatus != ConnectionStatus.CONNECTED) {
            markActionUnavailable(unavailableDetail)
            return false
        }
        beforeSend?.invoke()
        return send()
    }

    private fun markActionUnavailable(detail: String) {
        updateState { it.withUnavailableAction(detail) }
    }
}
