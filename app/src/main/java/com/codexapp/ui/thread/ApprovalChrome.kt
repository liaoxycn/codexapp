package com.codexapp.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexapp.ui.theme.CodexTheme
import kotlinx.coroutines.delay

@Composable
internal fun ApprovalCard(
    text: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    compactMode: Boolean
) {
    var pendingAction by remember(text) { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingAction) {
        if (pendingAction != null) {
            delay(3500L)
            pendingAction = null
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFFBEB))
            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
            .padding(if (compactMode) 10.dp else 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "待审批",
                color = CodexTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compactMode) 12.sp else 13.sp,
                lineHeight = if (compactMode) 16.sp else 17.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            color = CodexTheme.colors.textPrimary,
            fontSize = if (compactMode) 12.sp else 13.sp,
            lineHeight = if (compactMode) 17.sp else 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(if (compactMode) 4.dp else 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 6.dp)) {
            ActionPill(
                label = "允许",
                filled = true,
                testTag = "approval_allow_button",
                loading = pendingAction == "allow",
                enabled = pendingAction == null,
                onClick = {
                    pendingAction = "allow"
                    onApprove()
                }
            )
            ActionPill(
                label = "拒绝",
                filled = false,
                testTag = "approval_reject_button",
                loading = pendingAction == "reject",
                enabled = pendingAction == null,
                onClick = {
                    pendingAction = "reject"
                    onReject()
                }
            )
        }
    }
}

@Composable
internal fun ActionPill(
    label: String,
    filled: Boolean,
    testTag: String,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = if (filled) Color.White else CodexTheme.colors.textPrimary
    Box(
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (filled) {
                    CodexTheme.colors.textPrimary.copy(alpha = if (enabled) 1f else 0.72f)
                } else {
                    CodexTheme.colors.surface
                }
            )
            .border(
                width = if (filled) 0.dp else 1.dp,
                color = CodexTheme.colors.border,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 12.dp, vertical = if (filled) 7.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}
