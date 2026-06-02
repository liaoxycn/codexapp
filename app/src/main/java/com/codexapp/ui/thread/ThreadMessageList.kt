package com.codexapp.ui.thread

import com.codexapp.ui.message.MessageCard
import com.codexapp.ui.message.toTurnMessageItems
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.HomeUiState
import com.codexapp.model.NewThreadDraft

@Composable
internal fun ThreadMessageList(
    modifier: Modifier = Modifier,
    state: HomeUiState,
    compactMode: Boolean,
    listState: LazyListState,
    contentPadding: PaddingValues,
    isLoadingOlder: Boolean,
    onEditUserMessage: (String, Int) -> Unit,
    onResendUserMessage: (String, Int) -> Unit,
    onForkFromMessage: (Int) -> Unit,
    onNewThreadDraftChange: (NewThreadDraft) -> Unit,
    onApprovePending: () -> Unit,
    onRejectPending: () -> Unit,
) {
    val turnItems = state.messages.toTurnMessageItems(currentTurnRunning = state.isGenerating)
    val centerDraft = state.messages.isEmpty() && state.isNewThreadDraft
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .testTag("thread_message_list"),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = if (centerDraft) {
            Arrangement.Center
        } else {
            Arrangement.spacedBy(if (compactMode) 4.dp else 5.dp)
        }
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
                if (state.isNewThreadDraft) {
                    NewThreadDraftCard(
                        draft = state.newThreadDraft,
                        configOptions = state.configOptions,
                        compactMode = compactMode,
                        onDraftChange = onNewThreadDraftChange
                    )
                } else if (state.isThreadSwitching) {
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
            items = turnItems,
            key = { _, item -> item.message.id }
        ) { index, item ->
            MessageCard(
                messages = turnItems.map { it.message },
                message = item.message,
                processMessages = item.processMessages,
                assistantActionsEnabled = item.assistantActionsEnabled,
                compactMode = compactMode,
                messageIndex = index,
                onEditUserMessage = onEditUserMessage,
                onResendUserMessage = onResendUserMessage,
                onForkFromMessage = onForkFromMessage
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
