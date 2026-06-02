package com.codexapp.ui.common
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.model.ThreadStatus
import com.codexapp.model.TokenUsageState
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun TopBar(
    title: String,
    status: ThreadStatus,
    tokenUsage: TokenUsageState? = null,
    onOpenDrawer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodexTheme.colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderIconButton(
            icon = Icons.Filled.Menu,
            contentDescription = "打开抽屉",
            onClick = onOpenDrawer
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    color = CodexTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThreadStatusIcon(status)
                    Text(
                        text = threadStatusLabel(status),
                        color = CodexTheme.colors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
            }
        }
        TopBarTokenUsage(tokenUsage)
    }
}

@Composable
private fun TopBarTokenUsage(tokenUsage: TokenUsageState?) {
    if (tokenUsage == null) {
        Spacer(Modifier.size(42.dp))
        return
    }
    Box(
        modifier = Modifier
            .height(34.dp)
            .widthIn(min = 42.dp, max = 82.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border.copy(alpha = 0.66f), RoundedCornerShape(17.dp))
            .padding(horizontal = 9.dp)
            .semantics { contentDescription = "Token 用量 ${formatTopBarTokenUsage(tokenUsage)}" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatTopBarTokenUsage(tokenUsage),
            color = CodexTheme.colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

internal fun formatTopBarTokenUsage(tokenUsage: TokenUsageState): String {
    val total = formatCompactTokenCount(tokenUsage.totalTokens)
    val context = tokenUsage.contextPercent?.let { "${it.coerceIn(0, 100)}%" }
    return listOfNotNull(total, context).joinToString(" · ")
}

private fun formatCompactTokenCount(value: Long): String {
    val safeValue = value.coerceAtLeast(0L)
    return when {
        safeValue < 1_000L -> safeValue.toString()
        safeValue < 1_000_000L -> formatOneDecimalUnit(safeValue, 1_000L, "k")
        else -> formatOneDecimalUnit(safeValue, 1_000_000L, "m")
    }
}

private fun formatOneDecimalUnit(value: Long, unit: Long, suffix: String): String {
    val whole = value / unit
    val tenth = (value % unit) / (unit / 10L)
    return if (tenth == 0L) "$whole$suffix" else "$whole.$tenth$suffix"
}

@Composable
internal fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border.copy(alpha = 0.66f), CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CodexTheme.colors.textPrimary,
            modifier = Modifier.size(19.dp)
        )
    }
}
