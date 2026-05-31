package com.codex.mobile.ui

import com.codex.mobile.ui.app.gatewayDialogStatusText
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayDialogStatusTest {
    @Test
    fun gatewayDialogStatusTextShowsConnectedCopy() {
        assertEquals(
            "当前已连接，移动端只负责转发与展示",
            gatewayDialogStatusText(isConnected = true)
        )
    }

    @Test
    fun gatewayDialogStatusTextShowsDisconnectedCopy() {
        assertEquals(
            "填写 Desktop Gateway 地址后连接",
            gatewayDialogStatusText(isConnected = false)
        )
    }
}
