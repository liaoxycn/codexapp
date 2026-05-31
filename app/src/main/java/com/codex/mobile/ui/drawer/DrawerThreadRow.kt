package com.codex.mobile.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.model.ThreadSummary
import com.codex.mobile.ui.common.StatusDot
import com.codex.mobile.ui.common.ThreadStatusText
import com.codex.mobile.ui.common.threadStatusLabel
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun ThreadRow(
    summary: ThreadSummary,
    selected: Boolean,
    indentLevel: Int,
    onClick: () -> Unit
) {
    val startPadding = 10.dp + (indentLevel * 8).dp
    val updatedLabel = if (summary.updatedAt > 0L) formatThreadUpdatedAt(summary.updatedAt) else "无更新时间"
    val rowDescription = "会话：${summary.title}，状态：${threadStatusLabel(summary.status)}，更新：$updatedLabel"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) CodexTheme.colors.surfaceSubtle else Color.Transparent)
            .semantics { contentDescription = rowDescription }
            .clickable(onClick = onClick)
            .padding(start = startPadding, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CodexTheme.colors.textTertiary)
                )
            }
        }
        StatusDot(summary.status)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.title,
                color = CodexTheme.colors.textPrimary,
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary.preview.ifBlank { summary.title },
                color = CodexTheme.colors.textSecondary,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.width(62.dp),
            horizontalAlignment = Alignment.End
        ) {
            ThreadStatusText(
                status = summary.status,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(2.dp))
            if (summary.updatedAt > 0L) {
                Text(
                    text = formatThreadUpdatedAt(summary.updatedAt),
                    modifier = Modifier.fillMaxWidth(),
                    color = CodexTheme.colors.textTertiary,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
