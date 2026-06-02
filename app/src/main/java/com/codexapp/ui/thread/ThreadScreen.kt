package com.codexapp.ui.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.codexapp.model.HomeUiState
import com.codexapp.model.NewThreadDraft
import com.codexapp.ui.theme.CodexTheme
import kotlinx.coroutines.launch

@Composable
internal fun ThreadScreen(
    modifier: Modifier = Modifier,
    state: HomeUiState,
    compactMode: Boolean,
    onOpenConnection: () -> Unit,
    onRefreshCurrent: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onEditUserMessage: (String, Int) -> Unit,
    onResendUserMessage: (String, Int) -> Unit,
    onForkFromMessage: (Int) -> Unit,
    onNewThreadDraftChange: (NewThreadDraft) -> Unit,
    onApprovePending: () -> Unit,
    onRejectPending: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val controller = rememberThreadListController(
        state = state,
        compactMode = compactMode,
        onRefreshCurrent = onRefreshCurrent,
        onLoadOlderMessages = onLoadOlderMessages,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CodexTheme.colors.background)
            .nestedScroll(controller.nestedScrollConnection)
    ) {
        ThreadMessageList(
            state = state,
            compactMode = compactMode,
            listState = controller.listState,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = if (controller.metrics.showConnectionBanner) 58.dp else 2.dp,
                bottom = controller.metrics.composerPadding + 28.dp
            ),
            isLoadingOlder = controller.isLoadingOlder,
            onEditUserMessage = onEditUserMessage,
            onResendUserMessage = onResendUserMessage,
            onForkFromMessage = onForkFromMessage,
            onNewThreadDraftChange = onNewThreadDraftChange,
            onApprovePending = onApprovePending,
            onRejectPending = onRejectPending,
        )
        if (controller.metrics.showConnectionBanner) {
            ConnectionBanner(
                state = state,
                compact = compactMode,
                onOpenConnection = onOpenConnection,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        if (controller.showPullHint) {
            PullRefreshHint(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = controller.metrics.composerPadding + 8.dp)
                    .offset(y = (-((controller.pullProgress * 12f).coerceAtMost(12f))).dp),
                refreshing = state.isManualRefreshing,
                generating = state.isGenerating,
                progress = controller.pullProgress,
                compactMode = compactMode
            )
        }
        AnimatedVisibility(
            visible = controller.metrics.showJumpToBottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = controller.metrics.composerPadding + 18.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            JumpToBottomButton(
                onClick = {
                    if (state.messages.isNotEmpty()) {
                        scope.launch {
                            controller.listState.animateScrollToItem(controller.metrics.lastItemIndex)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun JumpToBottomButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CodexTheme.colors.surface.copy(alpha = 0.96f))
            .border(1.dp, CodexTheme.colors.border, CircleShape)
            .testTag("jump_to_bottom_button")
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "滚到底部" }
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = CodexTheme.colors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
