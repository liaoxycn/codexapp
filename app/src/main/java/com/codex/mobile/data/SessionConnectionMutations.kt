package com.codex.mobile.data

import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.SessionRemoteState

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
        isDemoMode = true
    )
}

internal fun SessionRemoteState.withConnectionFailure(detail: String): SessionRemoteState {
    return copy(
        connectionStatus = ConnectionStatus.ERROR,
        connectionDetail = detail,
        isDemoMode = true
    )
}

internal fun SessionRemoteState.withManualDisconnect(): SessionRemoteState {
    return copy(
        connectionStatus = ConnectionStatus.DISCONNECTED,
        connectionDetail = "已断开 desktop gateway",
        isDemoMode = true,
        isGenerating = false,
        pendingApproval = null
    )
}

internal fun SessionRemoteState.withConnectionDetail(detail: String): SessionRemoteState {
    return copy(connectionDetail = detail)
}

internal fun SessionRemoteState.withUnavailableAction(detail: String): SessionRemoteState {
    return copy(
        connectionDetail = detail,
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
        isDemoMode = true
    )
}
