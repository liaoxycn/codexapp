package com.codexapp.data

import com.codexapp.model.ConnectionStatus
import com.codexapp.model.StateDiagnostics
import com.codexapp.model.GatewayConfig
import com.codexapp.model.SessionRemoteState
import com.codexapp.model.ThreadStatus

internal fun SessionRemoteState.withBlankGatewayUrl(config: GatewayConfig): SessionRemoteState {
    return copy(
        gatewayConfig = config,
        connectionStatus = ConnectionStatus.ERROR,
        connectionDetail = "网关地址不能为空"
    )
}

internal fun SessionRemoteState.withConnectingGateway(config: GatewayConfig): SessionRemoteState {
    return copy(
        gatewayConfig = config,
        connectionStatus = ConnectionStatus.CONNECTING,
        connectionDetail = "正在连接 ${config.url}",
        isDemoMode = true
    )
}

internal fun SessionRemoteState.withOpenedGateway(config: GatewayConfig): SessionRemoteState {
    return copy(
        connectionStatus = ConnectionStatus.CONNECTING,
        connectionDetail = "已建立连接，正在同步会话",
        gatewayConfig = config
    )
}

internal fun SessionRemoteState.withHelloSendFailure(): SessionRemoteState {
    return copy(
        connectionStatus = ConnectionStatus.ERROR,
        connectionDetail = "hello 发送失败，请重新连接 gateway",
        isDemoMode = true
    )
}

internal fun SessionRemoteState.withDisconnectedGateway(reason: String): SessionRemoteState {
    val keepGenerating = shouldKeepSelectedGeneratingAfterConnectionLoss()
    return copy(
        connectionStatus = ConnectionStatus.DISCONNECTED,
        connectionDetail = if (reason.isBlank()) "desktop gateway 已断开" else reason,
        isDemoMode = false,
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        isLoadingOlder = false,
        isManualRefreshing = false,
        isGenerating = keepGenerating,
        pendingApproval = null,
        diagnostics = diagnostics.clearedAfterConnectionLoss(
            keepGenerating = keepGenerating,
            selectedThreadId = selectedThreadId
        )
    )
}

internal fun SessionRemoteState.withConnectionFailure(detail: String): SessionRemoteState {
    val keepGenerating = shouldKeepSelectedGeneratingAfterConnectionLoss()
    return copy(
        connectionStatus = ConnectionStatus.ERROR,
        connectionDetail = detail,
        isDemoMode = false,
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        isLoadingOlder = false,
        isManualRefreshing = false,
        isGenerating = keepGenerating,
        pendingApproval = null,
        diagnostics = diagnostics.clearedAfterConnectionLoss(
            keepGenerating = keepGenerating,
            selectedThreadId = selectedThreadId
        )
    )
}

internal fun SessionRemoteState.withManualDisconnect(): SessionRemoteState {
    val keepGenerating = shouldKeepSelectedGeneratingAfterConnectionLoss()
    return copy(
        connectionStatus = ConnectionStatus.DISCONNECTED,
        connectionDetail = "已断开 desktop gateway",
        isDemoMode = true,
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        isLoadingOlder = false,
        isManualRefreshing = false,
        isGenerating = keepGenerating,
        pendingApproval = null,
        diagnostics = diagnostics.clearedAfterConnectionLoss(
            keepGenerating = keepGenerating,
            selectedThreadId = selectedThreadId
        )
    )
}

internal fun SessionRemoteState.withConnectionDetail(detail: String): SessionRemoteState {
    return copy(connectionDetail = detail)
}

internal fun SessionRemoteState.withUnavailableAction(detail: String): SessionRemoteState {
    return copy(
        connectionDetail = detail,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        isLoadingOlder = false,
        isManualRefreshing = false,
        isGenerating = false
    )
}

internal fun SessionRemoteState.withManualRefreshing(refreshing: Boolean): SessionRemoteState {
    return copy(isManualRefreshing = refreshing)
}

internal fun SessionRemoteState.withInboundDecodeFailure(message: String?): SessionRemoteState {
    val keepGenerating = shouldKeepSelectedGeneratingAfterConnectionLoss()
    return copy(
        connectionStatus = ConnectionStatus.ERROR,
        connectionDetail = "网关消息解析失败: $message",
        isDemoMode = false,
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        isLoadingOlder = false,
        isManualRefreshing = false,
        isGenerating = keepGenerating,
        pendingApproval = null,
        diagnostics = diagnostics.clearedAfterConnectionLoss(
            keepGenerating = keepGenerating,
            selectedThreadId = selectedThreadId
        )
    )
}

private fun SessionRemoteState.shouldKeepSelectedGeneratingAfterConnectionLoss(): Boolean {
    if (selectedThreadId.isBlank()) {
        return false
    }
    val selectedThreadRunning = threads.any { thread ->
        thread.id == selectedThreadId && thread.status == ThreadStatus.RUNNING
    }
    val diagnosticsTargetsSelected = diagnostics.selectedThreadId.isBlank() ||
        diagnostics.selectedThreadId == selectedThreadId
    return selectedThreadRunning ||
        diagnostics.runningThreadIds.contains(selectedThreadId) ||
        (diagnostics.isGenerating && diagnosticsTargetsSelected)
}

private fun StateDiagnostics.clearedAfterConnectionLoss(
    keepGenerating: Boolean,
    selectedThreadId: String
): StateDiagnostics {
    return copy(
        pendingSelectionThreadId = "",
        isGenerating = keepGenerating,
        runningThreadIds = if (keepGenerating && selectedThreadId.isNotBlank()) {
            listOf(selectedThreadId)
        } else {
            emptyList()
        },
        actionTraceId = "",
        actionType = "",
        actionStatus = "",
        actionStartedAt = 0L,
        actionFinishedAt = 0L
    )
}
