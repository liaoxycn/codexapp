package com.codex.mobile.data.gateway

import android.content.Context
import com.codex.mobile.model.GatewayConfig

internal class GatewaySettingsStore(
    context: Context
) {
    private val prefs = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)
    private val defaultUrl = defaultGatewayUrl()

    fun load(): GatewayConfig {
        val savedUrl = prefs.getString("url", null)?.trim().orEmpty()
        return GatewayConfig(
            url = savedUrl.ifBlank { defaultUrl },
            pairToken = prefs.getString("pairToken", "") ?: ""
        )
    }

    fun save(config: GatewayConfig) {
        prefs.edit()
            .putString("url", config.url)
            .putString("pairToken", config.pairToken)
            .apply()
    }
}
