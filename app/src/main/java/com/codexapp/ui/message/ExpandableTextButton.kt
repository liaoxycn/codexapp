package com.codexapp.ui.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun ExpandCollapseTextButton(
    expanded: Boolean,
    expandLabel: String,
    collapseLabel: String,
    expandDescription: String,
    collapseDescription: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onToggle)
            .clearAndSetSemantics {
                contentDescription = if (expanded) collapseDescription else expandDescription
            }
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (expanded) collapseLabel else expandLabel,
            color = CodexTheme.colors.textTertiary,
            fontSize = 11.sp
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CodexTheme.colors.textTertiary,
            modifier = Modifier.size(13.dp)
        )
    }
}
