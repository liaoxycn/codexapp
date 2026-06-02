package com.codexapp.data

import android.util.Log
import com.codexapp.data.gateway.GatewayCommandSender
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.SessionRemoteState

internal class GatewayRepositoryCommandActions(
    private val commandSender: GatewayCommandSender,
    private val readState: () -> SessionRemoteState,
    private val updateState: ((SessionRemoteState) -> SessionRemoteState) -> Unit,
    tag: String = "CodexGateway",
    private val logDebug: (String) -> Unit = { message -> Log.d(tag, message) }
) {
    fun createThread(cwd: String?, draft: NewThreadDraft? = null): Boolean {
        return runConnectedAction(
            unavailableDetail = "未连接 gateway，无法新建会话",
            beforeSend = { updateState(SessionRemoteState::startCreatingThread) },
            sendFailureDetail = "新建会话失败，gateway 连接已断开"
        ) {
            commandSender.createThread(
                cwd = cwd,
                model = draft?.model,
                reasoningEffort = draft?.reasoningEffort,
                approvalPolicy = draft?.approvalPolicy,
                approvalsReviewer = draft?.approvalsReviewer,
                sandboxMode = draft?.sandboxMode
            )
        }
    }

    fun selectThread(id: String): Boolean {
        if (id.isBlank()) return false
        return runConnectedAction(
            unavailableDetail = "未连接 gateway，无法切换会话",
            beforeSend = {
                val nextTitle = readState().threads.firstOrNull { it.id == id }?.title
                updateState { it.startSelectingThread(id, nextTitle) }
            },
            sendFailureDetail = "切换会话失败，gateway 连接已断开"
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

    fun forkThread(id: String, numTurns: Int? = null): Boolean {
        if (id.isBlank()) return false
        return runConnectedAction("未连接 gateway，无法分叉会话") {
            commandSender.forkThread(id, numTurns)
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
            beforeSend = { updateState(SessionRemoteState::startLoadingOlderMessages) },
            sendFailureDetail = "加载历史失败，gateway 连接已断开"
        ) {
            commandSender.loadOlderMessages()
        }
    }

    fun sendPrompt(prompt: String, draft: NewThreadDraft? = null, newThread: Boolean = false): Boolean {
        if (prompt.isBlank()) return false
        val snapshot = readState()
        val targetThreadId = if (newThread) null else snapshot.selectedThreadId.ifBlank { null }
        if (snapshot.connectionStatus == ConnectionStatus.CONNECTING) {
            updateState { it.withConnectionDetail("正在同步会话，请稍后再发") }
            return false
        }
        if (snapshot.connectionStatus != ConnectionStatus.CONNECTED) {
            markActionUnavailable("未连接 gateway，请先连接后再发送")
            return false
        }
        val sent = commandSender.sendPrompt(
            text = prompt,
            threadId = targetThreadId,
            newThread = newThread,
            cwd = draft?.cwd,
            model = draft?.model,
            reasoningEffort = draft?.reasoningEffort,
            approvalPolicy = draft?.approvalPolicy,
            approvalsReviewer = draft?.approvalsReviewer,
            sandboxMode = draft?.sandboxMode
        )
        logDebug("send_prompt sent=$sent")
        if (!sent) {
            updateState { it.withSendFailure("发送失败，gateway 连接已断开") }
            return false
        }
        updateState {
            if (newThread) {
                it.startCreatingThread().withOptimisticPrompt(prompt)
            } else {
                it.withOptimisticPrompt(prompt)
            }
        }
        return true
    }

    fun rollbackThread(numTurns: Int): Boolean {
        val snapshot = readState()
        val threadId = snapshot.selectedThreadId.ifBlank { return false }
        return runConnectedAction("未连接 gateway，无法回滚会话") {
            commandSender.rollbackThread(threadId, numTurns)
        }
    }

    fun resendPrompt(prompt: String, rollbackNumTurns: Int, draft: NewThreadDraft? = null): Boolean {
        if (prompt.isBlank()) return false
        val snapshot = readState()
        val threadId = snapshot.selectedThreadId.ifBlank { return false }
        if (snapshot.connectionStatus != ConnectionStatus.CONNECTED) {
            markActionUnavailable("未连接 gateway，请先连接后再重发")
            return false
        }
        val sent = commandSender.resendPrompt(
            text = prompt.trim(),
            threadId = threadId,
            rollbackNumTurns = rollbackNumTurns,
            model = draft?.model,
            reasoningEffort = draft?.reasoningEffort,
            approvalPolicy = draft?.approvalPolicy,
            approvalsReviewer = draft?.approvalsReviewer,
            sandboxMode = draft?.sandboxMode
        )
        if (!sent) {
            updateState { it.withSendFailure("重发失败，gateway 连接已断开") }
            return false
        }
        updateState { it.withOptimisticPrompt(prompt.trim()) }
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

    fun restartDesktop(): Boolean {
        return runConnectedAction("未连接 gateway，无法重启桌面端") {
            commandSender.restartDesktop()
        }
    }

    fun markManualRefreshing(refreshing: Boolean) {
        updateState { it.withManualRefreshing(refreshing) }
    }

    private inline fun runConnectedAction(
        unavailableDetail: String,
        noinline beforeSend: (() -> Unit)? = null,
        sendFailureDetail: String = unavailableDetail,
        send: () -> Boolean
    ): Boolean {
        if (readState().connectionStatus != ConnectionStatus.CONNECTED) {
            markActionUnavailable(unavailableDetail)
            return false
        }
        val previous = readState()
        beforeSend?.invoke()
        val sent = send()
        if (!sent) {
            updateState {
                previous.withUnavailableAction(sendFailureDetail)
            }
        }
        return sent
    }

    private fun markActionUnavailable(detail: String) {
        updateState { it.withUnavailableAction(detail) }
    }
}
