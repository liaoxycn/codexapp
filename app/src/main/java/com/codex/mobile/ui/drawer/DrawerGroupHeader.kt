package com.codex.mobile.ui.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun GroupHeader(
    label: String,
    secondaryText: String = "",
    icon: ImageVector? = null,
    compact: Boolean = false,
    expanded: Boolean? = null,
    onCreateThread: (() -> Unit)? = null,
    onToggle: (() -> Unit)? = null
) {
    val groupDescription = groupHeaderDescription(
        label = label,
        expanded = expanded,
        canToggle = onToggle != null
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 3.dp)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = groupDescription }
                .clickable(enabled = onToggle != null) { onToggle?.invoke() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupHeaderContent(
                label = label,
                secondaryText = secondaryText,
                icon = icon,
                compact = compact,
                expanded = expanded
            )
        }
        if (onCreateThread != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .semantics { contentDescription = groupHeaderCreateThreadDescription(label) }
                    .clickable(onClick = onCreateThread),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
