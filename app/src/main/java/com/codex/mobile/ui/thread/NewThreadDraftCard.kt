package com.codex.mobile.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.model.GatewayConfigOption
import com.codex.mobile.model.GatewayConfigOptions
import com.codex.mobile.model.NewThreadDraft
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun NewThreadDraftCard(
    draft: NewThreadDraft,
    configOptions: GatewayConfigOptions,
    compactMode: Boolean,
    onDraftChange: (NewThreadDraft) -> Unit
) {
    val modelOptions = buildModelDraftOptions(draft, configOptions.models)
    val reasoningOptions = buildConfigDraftOptions(configOptions.reasoningEfforts)
    val sandboxOptions = buildConfigDraftOptions(configOptions.sandboxModes)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp)
            .testTag("new_thread_draft_card"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DraftHero(compactMode = compactMode)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DraftConfigPicker(
                modifier = Modifier.weight(1f),
                title = "模型",
                icon = Icons.Filled.AutoFixHigh,
                options = modelOptions,
                selected = draft.model,
                compactMode = compactMode,
                onSelect = { model -> onDraftChange(draft.copy(model = model)) }
            )
            if (reasoningOptions.isNotEmpty()) {
                DraftConfigPicker(
                    modifier = Modifier.weight(1f),
                    title = "推理",
                    icon = Icons.Filled.Bolt,
                    options = reasoningOptions,
                    selected = draft.reasoningEffort,
                    compactMode = compactMode,
                    onSelect = { effort -> onDraftChange(draft.copy(reasoningEffort = effort)) }
                )
            }
        }
        if (sandboxOptions.isNotEmpty()) {
            DraftPermissionStrip(
                options = sandboxOptions,
                selected = draft.sandboxMode,
                compactMode = compactMode,
                onSelect = { sandbox -> onDraftChange(draft.copy(sandboxMode = sandbox)) }
            )
        }
    }
}

@Composable
private fun DraftHero(compactMode: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(CodexTheme.colors.textPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.NorthEast,
                    contentDescription = null,
                    tint = CodexTheme.colors.userBubbleText,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "从这里开始",
                    color = CodexTheme.colors.textPrimary,
                    fontSize = if (compactMode) 22.sp else 24.sp,
                    lineHeight = if (compactMode) 26.sp else 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = "第一条消息发送后才会创建真实会话",
                    color = CodexTheme.colors.textSecondary,
                    fontSize = if (compactMode) 12.sp else 13.sp,
                    lineHeight = if (compactMode) 16.sp else 17.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
    }
}

internal data class DraftOption(
    val label: String,
    val value: String,
    val enabled: Boolean = true
)

@Composable
private fun DraftConfigPicker(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    options: List<DraftOption>,
    selected: String,
    compactMode: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var dropdownWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val selectedOption = options.firstOrNull { it.value == selected } ?: options.firstOrNull()
    val selectedLabel = selectedOption?.label?.ifBlank { selectedOption.value } ?: "读取中"
    val enabled = options.any { it.enabled }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates -> dropdownWidthPx = coordinates.size.width }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.dp, CodexTheme.colors.border.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
                .clickable(enabled = enabled) { expanded = true }
                .semantics { contentDescription = "$title：$selectedLabel" }
                .padding(horizontal = 12.dp, vertical = if (compactMode) 10.dp else 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = title,
                    color = CodexTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedLabel,
                    color = if (enabled) CodexTheme.colors.textPrimary else CodexTheme.colors.textTertiary,
                    fontSize = if (compactMode) 13.sp else 14.sp,
                    lineHeight = if (compactMode) 17.sp else 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = CodexTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = if (dropdownWidthPx > 0) {
                Modifier.width(with(density) { dropdownWidthPx.toDp() })
            } else {
                Modifier
            }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    enabled = option.enabled,
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "选择$title：${option.label.ifBlank { option.value }}"
                    }
                ) {
                    Text(
                        text = option.label.ifBlank { option.value.ifBlank { "读取中" } },
                        color = if (option.enabled) CodexTheme.colors.textPrimary else CodexTheme.colors.textTertiary,
                        fontSize = if (compactMode) 12.sp else 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftPermissionStrip(
    options: List<DraftOption>,
    selected: String,
    compactMode: Boolean,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = CodexTheme.colors.textTertiary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "权限",
                color = CodexTheme.colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            options.forEach { option ->
                DraftChip(
                    label = option.label,
                    selected = option.value == selected,
                    enabled = option.enabled,
                    compactMode = compactMode,
                    onClick = {
                        if (option.enabled) {
                            onSelect(option.value)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DraftChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    compactMode: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label.ifBlank { "默认" },
        color = when {
            selected -> CodexTheme.colors.userBubbleText
            enabled -> CodexTheme.colors.textPrimary
            else -> CodexTheme.colors.textTertiary
        },
        fontSize = if (compactMode) 11.sp else 12.sp,
        lineHeight = if (compactMode) 14.sp else 15.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(min = 54.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) CodexTheme.colors.textPrimary else Color(0xFFF7F7F8))
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = CodexTheme.colors.border,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label.ifBlank { "默认权限" } }
            .padding(horizontal = 11.dp, vertical = if (compactMode) 7.dp else 8.dp)
    )
}

internal fun buildModelDraftOptions(draft: NewThreadDraft, options: List<GatewayConfigOption>): List<DraftOption> {
    return buildConfigDraftOptions(options).ifEmpty {
        listOfNotNull(draft.model.takeIf { it.isNotBlank() }?.let { DraftOption(it, it) })
            .ifEmpty { listOf(DraftOption("读取中", "", enabled = false)) }
    }
}

internal fun buildConfigDraftOptions(options: List<GatewayConfigOption>): List<DraftOption> {
    return options
        .filter { it.value.isNotBlank() }
        .map { DraftOption(it.label.ifBlank { it.value }, it.value) }
}
