package com.codexapp.data

import com.codexapp.model.ConnectionStatus
import com.codexapp.model.StateDiagnostics
import com.codexapp.model.GatewayConfig
import com.codexapp.model.SessionRemoteState

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
    return copy(
        connectionStatus = ConnectionStatus.DISCONNECTED,
        connectionDetail = if (reason.isBlank()) "desktop gateway 已断开" else reason,
        isDemoMode = false,
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        isLoadingOlder = false,
        isManualRefreshing = false,
        isGenerating = false,
        pendingApproval = null,
        diagnostics = diagnostics.clearedAfterConnectionLoss()
    )
}

internal fun SessionRemoteState.withConnectionFailure(detail: String): SessionRemoteState {
    return copy(
        connectionStatus = ConnectionStatus.ERROR,
        connectionDetail = detail,
        isDemoMode = false,
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        isLoadingOlder = false,
        isManualRefreshing = false,
        isGenerating = false,
        pendingApproval = null,
        diagnostics = diagnostics.clearedAfterConnectionLoss()
    )
}

internal fun SessionRemoteState.withManualDisconnect(): SessionRemoteState {
    return copy(
        connectionStatus = ConnectionStatus.DISCONNECTED,
        connectionDetail = "已断开 desktop gateway",
        isDemoMode = true,
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        isLoadingOlder = false,
        isManualRefreshing = false,
        isGenerating = false,
        pendingApproval = null,
        diagnostics = diagnostics.clearedAfterConnectionLoss()
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
    return copy(
        connectionStatus = ConnectionStatus.ERROR,
        connectionDetail = "网关消息解析失败: $message",
        isDemoMode = false,
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        isLoadingOlder = false,
        isManualRefreshing = false,
        isGenerating = false,
        pendingApproval = null,
        diagnostics = diagnostics.clearedAfterConnectionLoss()
    )
}

private fun StateDiagnostics.clearedAfterConnectionLoss(): StateDiagnostics {
    return copy(
        pendingSelectionThreadId = "",
        isGenerating = false,
        runningThreadIds = emptyList(),
        actionTraceId = "",
        actionType = "",
        actionStatus = "",
        actionStartedAt = 0L,
        actionFinishedAt = 0L
    )
}
