package com.codexapp.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CodeBlockHeader(
    compactMode: Boolean,
    label: String,
    expanded: Boolean,
    shouldCollapse: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compactMode) 9.dp else 10.dp,
                vertical = if (compactMode) 6.dp else 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF374151), RoundedCornerShape(999.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = label,
                color = Color(0xFFE5E7EB),
                fontSize = if (compactMode) 9.sp else 10.sp,
                lineHeight = if (compactMode) 11.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.weight(1f))
        if (shouldCollapse) {
            ExpandCollapseTextButton(
                expanded = expanded,
                expandLabel = "展开",
                collapseLabel = "收起",
                expandDescription = "展开代码块",
                collapseDescription = "收起代码块",
                onToggle = onToggleExpanded
            )
        }
    }
}
