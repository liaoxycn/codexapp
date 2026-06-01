package com.codex.mobile.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.codex.mobile.model.GatewayConfig

@Stable
internal class GatewayDialogState(
    initialUrl: String,
    initialPairToken: String
) {
    var url by mutableStateOf(initialUrl)
    var pairToken by mutableStateOf(initialPairToken)

    val canConnect: Boolean
        get() = url.isNotBlank()
}

@Composable
internal fun rememberGatewayDialogState(config: GatewayConfig): GatewayDialogState {
    return remember(config.url, config.pairToken) {
        GatewayDialogState(
            initialUrl = config.url,
            initialPairToken = config.pairToken
        )
    }
}
