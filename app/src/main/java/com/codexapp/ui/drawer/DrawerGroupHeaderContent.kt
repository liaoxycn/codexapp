package com.codexapp.ui.drawer

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun RowScope.GroupHeaderContent(
    label: String,
    secondaryText: String,
    icon: ImageVector?,
    compact: Boolean,
    expanded: Boolean?
) {
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CodexTheme.colors.textTertiary,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp)
        )
        Spacer(Modifier.width(5.dp))
    }

    val titleStyle = groupHeaderTitleStyle(
        compact = compact,
        hasSecondaryText = secondaryText.isNotBlank()
    )
    Text(
        text = label,
        color = CodexTheme.colors.textSecondary,
        fontSize = titleStyle.fontSize,
        lineHeight = titleStyle.lineHeight,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (secondaryText.isNotBlank()) Modifier.widthIn(max = 96.dp) else Modifier.weight(1f),
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )

    if (secondaryText.isNotBlank()) {
        GroupHeaderSecondaryText(secondaryText = secondaryText)
    }

    if (expanded != null) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CodexTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun RowScope.GroupHeaderSecondaryText(
    secondaryText: String
) {
    Spacer(Modifier.width(4.dp))
    Text(
        text = "|",
        color = CodexTheme.colors.textTertiary,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        maxLines = 1,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )
    Spacer(Modifier.width(4.dp))
    Text(
        text = secondaryText,
        color = CodexTheme.colors.textTertiary,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Start,
        modifier = Modifier.weight(1f),
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )
}
