package com.codex.mobile.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.codex.mobile.model.ComposerChip
import com.codex.mobile.model.ComposerChipIcon
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThreadScreenVisibilityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsJumpToBottomWhenMessagesArePresentAndNotAtBottom() {
        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 24
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.waitForIdle()
        rule.onNodeWithTag("thread_message_list").performScrollToIndex(0)
        rule.waitForIdle()
        rule.onNodeWithTag("jump_to_bottom_button").assertIsDisplayed()
    }

    @Test
    fun showsOlderHintWhenHistoryIsLoading() {
        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = sampleState(
                        hasMoreHistory = true,
                        isLoadingOlder = true,
                        messageCount = 1
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithTag("thread_message_list").performScrollToIndex(0)
        rule.waitForIdle()
        rule.onNodeWithTag("load_older_hint").assertExists()
    }

    @Test
    fun hidesOlderHintWhenHistoryIsNotLoading() {
        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = sampleState(
                        hasMoreHistory = true,
                        isLoadingOlder = false,
                        messageCount = 1
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithTag("thread_message_list").performScrollToIndex(0)
        rule.waitForIdle()
        assertTrue(
            runCatching {
                rule.onNodeWithTag("load_older_hint").assertExists()
            }.isFailure
        )
    }

    @Test
    fun loadsOlderMessagesWhenScrolledToTop() {
        var loadCalls = 0
        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = sampleState(
                        hasMoreHistory = true,
                        isLoadingOlder = false,
                        messageCount = 24
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = { loadCalls += 1 },
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithTag("thread_message_list").performScrollToIndex(0)
        rule.waitUntil(timeoutMillis = 3_000) { loadCalls > 0 }
    }

    private fun sampleState(
        hasMoreHistory: Boolean,
        isLoadingOlder: Boolean,
        messageCount: Int
    ) = HomeUiState(
        threads = listOf(
            ThreadSummary(
                id = "t1",
                title = "Thread",
                preview = "preview",
                status = ThreadStatus.IDLE
            )
        ),
        selectedThreadId = "t1",
        pendingThreadTitle = null,
        isThreadSwitching = false,
        messages = List(messageCount) { index ->
            ThreadMessage(
                id = "m${index + 1}",
                role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                blocks = listOf(MessageBlock.Text("hello ${index + 1}"))
            )
        },
        hasMoreHistory = hasMoreHistory,
        isLoadingOlder = isLoadingOlder,
        composerText = "",
        isGenerating = false,
        isManualRefreshing = false,
        showComposerDetails = false,
        chips = listOf(ComposerChip("chip", ComposerChipIcon.FILE)),
        slashCommands = emptyList(),
        pendingApproval = null,
        cwd = "",
        permissionSummary = "",
        connectionStatus = ConnectionStatus.CONNECTED,
        connectionDetail = "",
        gatewayConfig = GatewayConfig(),
        isDemoMode = true
    )
}
