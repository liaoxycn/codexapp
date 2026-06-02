package com.codex.mobile.ui.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.ui.theme.CodexTheme

@Composable
internal fun PullRefreshHint(
    modifier: Modifier = Modifier,
    refreshing: Boolean,
    generating: Boolean,
    progress: Float,
    compactMode: Boolean,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        AnimatedVisibility(
            visible = progress > 0f || refreshing || generating,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val refreshLabel = pullRefreshHintLabel(
                refreshing = refreshing,
                generating = generating,
                progress = progress
            )
            Text(
                text = refreshLabel,
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compactMode) 10.sp else 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(CodexTheme.colors.surface.copy(alpha = 0.92f))
                    .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(999.dp))
                    .testTag("pull_refresh_hint")
                    .semantics { contentDescription = refreshLabel }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

internal fun pullRefreshHintLabel(
    refreshing: Boolean,
    generating: Boolean,
    progress: Float
): String {
    return when {
        refreshing -> "刷新会话中"
        generating -> "会话运行中"
        progress >= 1f -> "松开刷新"
        else -> "继续上滑"
    }
}

@Composable
internal fun HistoryLoadHint(
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = loading,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("load_older_hint")
                .semantics { contentDescription = "加载更早消息" }
                .padding(top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                strokeWidth = 1.8.dp,
                modifier = Modifier.size(12.dp),
                color = CodexTheme.colors.textTertiary
            )
            Spacer(Modifier.size(8.dp, 0.dp))
            Text(
                text = "加载更早消息",
                color = CodexTheme.colors.textTertiary,
                fontSize = 11.sp
            )
        }
    }
}
