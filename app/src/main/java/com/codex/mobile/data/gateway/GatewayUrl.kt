package com.codex.mobile.data.gateway

import android.os.Build
import java.net.URI

internal fun decorateConnectionError(url: String, error: String): String {
    val base = error.ifBlank { "连接失败" }
    if (!isProbablyEmulator()) {
        return base
    }
    if (url.contains("10.0.2.2")) {
        return base
    }
    return "$base，模拟器建议改用 ws://10.0.2.2:8765/mobile"
}

internal fun defaultGatewayUrl(): String = if (isProbablyEmulator()) {
    "ws://10.0.2.2:8765/mobile"
} else {
    "ws://192.168.31.97:8765/mobile"
}

internal fun normalizeGatewayUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return ""

    val withScheme = when {
        trimmed.startsWith("ws://", ignoreCase = true) ||
            trimmed.startsWith("wss://", ignoreCase = true) -> trimmed

        trimmed.startsWith("http://", ignoreCase = true) -> "ws://${trimmed.substringAfter("://")}"
        trimmed.startsWith("https://", ignoreCase = true) -> "wss://${trimmed.substringAfter("://")}"
        else -> "ws://$trimmed"
    }

    return runCatching {
        val parsed = URI(withScheme)
        val normalizedPath = parsed.rawPath
            ?.takeIf { it.isNotBlank() && it != "/" }
            ?: "/mobile"
        URI(
            parsed.scheme?.lowercase(),
            parsed.rawUserInfo,
            parsed.host,
            parsed.port,
            normalizedPath,
            parsed.rawQuery,
            parsed.rawFragment
        ).toString()
    }.getOrElse { withScheme }
}

internal fun isProbablyEmulator(): Boolean {
    return Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
        Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
        Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
        Build.PRODUCT.contains("sdk", ignoreCase = true)
}

