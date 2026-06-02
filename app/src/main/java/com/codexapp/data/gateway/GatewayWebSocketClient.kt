package com.codexapp.data.gateway

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.codexapp.model.GatewayConfig
import java.util.concurrent.TimeUnit

internal class GatewayWebSocketClient {
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private var activeConnectionId: Long = 0

    fun connect(
        config: GatewayConfig,
        onOpen: (WebSocket) -> Unit,
        onClosed: (String) -> Unit,
        onFailure: (String) -> Unit,
        onMessage: (String) -> Unit
    ) {
        disconnect()
        val connectionId = ++activeConnectionId
        val request = Request.Builder()
            .url(config.url)
            .build()
        val socket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!isActive(connectionId, webSocket)) {
                        webSocket.close(1000, "stale connection")
                        return
                    }
                    onOpen(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (isActive(connectionId, webSocket)) {
                        onMessage(text)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!isActive(connectionId, webSocket)) {
                        return
                    }
                    this@GatewayWebSocketClient.webSocket = null
                    onClosed(reason)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (isActive(connectionId, webSocket)) {
                        webSocket.close(code, reason)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!isActive(connectionId, webSocket)) {
                        return
                    }
                    this@GatewayWebSocketClient.webSocket = null
                    onFailure(t.message ?: "连接失败")
                }
            }
        )
        webSocket = socket
    }

    fun send(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }

    fun disconnect() {
        activeConnectionId += 1
        webSocket?.close(1000, "client disconnect")
        webSocket = null
    }

    private fun isActive(connectionId: Long, socket: WebSocket): Boolean {
        return activeConnectionId == connectionId && webSocket === socket
    }
}

