package com.codex.mobile.model

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class GatewayConfig(
    val url: String = "",
    val pairToken: String = ""
)
