package com.codex.mobile.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun SlashCommandPanel(
    query: String,
    commands: List<String>,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CodexTheme.colors.surface)
            .border(1.dp, CodexTheme.colors.border.copy(alpha = 0.82f), RoundedCornerShape(18.dp))
            .padding(8.dp)
            .heightIn(max = 180.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        PanelHeader(title = "命令", subtitle = "选择要插入的 Codex 操作")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CodexTheme.colors.surfaceSubtle)
                .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = CodexTheme.colors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(7.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = CodexTheme.colors.textPrimary,
                    fontSize = ComposerTextSize,
                    lineHeight = ComposerTextLineHeight,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 28.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isBlank()) {
                            Text(
                                "搜索命令",
                                color = CodexTheme.colors.textTertiary,
                                fontSize = ComposerTextSize,
                                lineHeight = ComposerTextLineHeight
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        if (commands.isEmpty()) {
            SlashCommandRow(command = "没有匹配的命令", supporting = "修改搜索词", icon = Icons.Filled.Search)
        } else {
            commands.forEach { command ->
                val commandText = command.substringBefore("  ").trim()
                SlashCommandRow(
                    command = commandText,
                    supporting = command.substringAfter("  ", ""),
                    icon = iconForSlashCommand(commandText),
                    onClick = { onSelect(command) }
                )
            }
        }
    }
}

@Composable
private fun SlashCommandRow(
    command: String,
    supporting: String = "",
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .defaultMinSize(minHeight = 46.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CodexTheme.colors.textSecondary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = command,
            color = CodexTheme.colors.textPrimary,
            fontSize = ComposerTextSize,
            lineHeight = ComposerTextLineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (supporting.isNotBlank()) {
            Text(
                text = supporting,
                color = CodexTheme.colors.textSecondary,
                fontSize = ComposerTextSize,
                lineHeight = ComposerTextLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PanelHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = CodexTheme.colors.textPrimary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = subtitle,
            color = CodexTheme.colors.textTertiary,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun iconForSlashCommand(command: String): ImageVector {
    val token = command.substringBefore(" ").trim()
    return when {
        token == "/compact" -> Icons.Filled.Archive
        token == "/rollback" -> Icons.Filled.Refresh
        token.startsWith("!") -> Icons.Filled.Edit
        else -> Icons.Filled.Search
    }
}
