package com.codex.mobile.ui.thread

import com.codex.mobile.ui.message.MessageCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.HomeUiState

@Composable
internal fun ThreadMessageList(
    modifier: Modifier = Modifier,
    state: HomeUiState,
    compactMode: Boolean,
    listState: LazyListState,
    contentPadding: PaddingValues,
    isLoadingOlder: Boolean,
    onEditUserMessage: (String) -> Unit,
    onResendUserMessage: (String) -> Unit,
    onCopyMessage: (String) -> Unit,
    onApprovePending: () -> Unit,
    onRejectPending: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("thread_message_list"),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 5.dp)
    ) {
        if (state.hasMoreHistory) {
            item {
                HistoryLoadHint(
                    loading = isLoadingOlder,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }

        if (state.messages.isEmpty()) {
            item {
                if (state.isThreadSwitching) {
                    ThreadSwitchingCard(state.pendingThreadTitle)
                } else {
                    EmptyThreadCard(
                        connected = state.connectionStatus == ConnectionStatus.CONNECTED,
                        hasThreads = state.threads.isNotEmpty()
                    )
                }
            }
        }

        itemsIndexed(
            items = state.messages,
            key = { _, message -> message.id }
        ) { index, message ->
            MessageCard(
                message = message,
                compactMode = compactMode,
                messageIndex = index,
                onEditUserMessage = onEditUserMessage,
                onResendUserMessage = onResendUserMessage,
                onCopyMessage = onCopyMessage
            )
        }

        if (state.pendingApproval != null) {
            item {
                ApprovalCard(
                    text = state.pendingApproval,
                    onApprove = onApprovePending,
                    onReject = onRejectPending,
                    compactMode = compactMode
                )
            }
        }
    }
}
