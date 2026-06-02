package com.codexapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class CodexColors(
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val danger: Color,
    val userBubble: Color,
    val userBubbleText: Color,
    val codeBackground: Color
)

private val LightCodexColors = CodexColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFF7F7F8),
    border = Color(0xFFE5E7EB),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF6B7280),
    textTertiary = Color(0xFF9CA3AF),
    accent = Color(0xFF111111),
    danger = Color(0xFFDC2626),
    userBubble = Color(0xFF111111),
    userBubbleText = Color(0xFFFFFFFF),
    codeBackground = Color(0xFF1F2937)
)

private val LocalCodexColors = staticCompositionLocalOf { LightCodexColors }

object CodexTheme {
    val colors: CodexColors
        @Composable
        get() = LocalCodexColors.current
}

@Composable
fun CodexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = LightCodexColors
    MaterialTheme(
        colors = materialColorScheme(colors),
        typography = codexTypography(),
        content = {
            CompositionLocalProvider(
                LocalCodexColors provides colors,
                content = content
            )
        }
    )
}

private fun materialColorScheme(colors: CodexColors): Colors =
    lightColors(
        background = colors.background,
        surface = colors.surface,
        primary = colors.accent,
        onPrimary = colors.userBubbleText,
        onSurface = colors.textPrimary,
        onBackground = colors.textPrimary,
        error = colors.danger
    )

private fun codexTypography(): Typography = Typography()
