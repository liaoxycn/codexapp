package com.codex.mobile.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.codex.mobile.model.ComposerChip
import com.codex.mobile.model.ComposerChipIcon
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.model.ThreadGroupKind
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary
import com.codex.mobile.ui.drawer.DrawerContent
import com.codex.mobile.ui.composer.Composer
import com.codex.mobile.ui.composer.ComposerPanel
import com.codex.mobile.ui.theme.CodexTheme
import com.codex.mobile.ui.thread.ThreadScreen
import org.junit.Assert.assertEquals
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
                    onEditUserMessage = {},
                    onResendUserMessage = {},
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
                    onEditUserMessage = {},
                    onResendUserMessage = {},
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
                    onEditUserMessage = {},
                    onResendUserMessage = {},
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
                    onEditUserMessage = {},
                    onResendUserMessage = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithTag("thread_message_list").performScrollToIndex(0)
        rule.waitUntil(timeoutMillis = 3_000) { loadCalls > 0 }
    }

    @Test
    fun fileChangeCardShowsFilesAndExpandsDiff() {
        val diff = "diff --git a/app/src/main/App.kt b/app/src/main/App.kt\n-old\n+new"
        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        messages = listOf(
                            ThreadMessage(
                                id = "file-change-1",
                                role = MessageRole.ASSISTANT,
                                blocks = listOf(
                                    MessageBlock.FileChangeSummary("已编辑 1 个文件"),
                                    MessageBlock.FileChangeMeta("已编辑 App.kt", "app/src/main/App.kt"),
                                    MessageBlock.FileChangeDiff(diff)
                                )
                            )
                        )
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = {},
                    onResendUserMessage = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.waitForIdle()
        rule.onNodeWithTag("thread_message_list").performScrollToIndex(0)
        rule.waitForIdle()
        rule.onNodeWithText("已编辑 App.kt").assertExists()
        rule.onNodeWithContentDescription("展开 已编辑 App.kt diff").performClick()
        rule.onNodeWithText("app/src/main/App.kt").assertExists()
        rule.onNodeWithText(diff).assertExists()
    }

    @Test
    fun approvalRejectButtonInvokesRejectCallback() {
        var approveCalls = 0
        var rejectCalls = 0
        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(pendingApproval = "允许执行 shell 命令\n! dir"),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = {},
                    onResendUserMessage = {},
                    onApprovePending = { approveCalls += 1 },
                    onRejectPending = { rejectCalls += 1 }
                )
            }
        }

        rule.onNodeWithTag("approval_reject_button")
            .assertExists()
            .performClick()

        assertEquals(0, approveCalls)
        assertEquals(1, rejectCalls)
    }

    @Test
    fun userMessageMenuSupportsEditAndResend() {
        var editedText: String? = null
        var resentText: String? = null
        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        messages = listOf(
                            ThreadMessage(
                                id = "user-editable",
                                role = MessageRole.USER,
                                blocks = listOf(MessageBlock.Text("please inspect build failure"))
                            )
                        )
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { editedText = it },
                    onResendUserMessage = { resentText = it },
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithTag("user_message_more_user-editable").performClick()
        rule.onNodeWithText("编辑后重发").performClick()
        assertEquals("please inspect build failure", editedText)

        rule.onNodeWithTag("user_message_more_user-editable").performClick()
        rule.onNodeWithText("重发").performClick()
        assertEquals("please inspect build failure", resentText)
    }

    @Test
    fun composerFileChipInsertsMentionPath() {
        var insertedText: String? = null
        rule.setContent {
            CodexTheme {
                Composer(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        showComposerDetails = true,
                        chips = listOf(
                            ComposerChip(
                                label = "app/Main.kt",
                                icon = ComposerChipIcon.FILE,
                                path = "D:/Projects/app/Main.kt"
                            )
                        )
                    ),
                    compactMode = false,
                    activePanel = ComposerPanel.NONE,
                    onActivePanelChange = {},
                    onToggleCompact = {},
                    onToggleDetails = {},
                    onChange = {},
                    onInsertText = { insertedText = it },
                    onApplySlashCommand = {},
                    onClearComposer = {},
                    onSend = {},
                    onStop = {}
                )
            }
        }

        rule.onNodeWithContentDescription("app/Main.kt").performClick()

        assertEquals("@{D:/Projects/app/Main.kt}", insertedText)
    }

    @Test
    fun projectDrawerActionCreatesThreadWithProjectCwd() {
        var createdCwd: String? = null
        rule.setContent {
            CodexTheme {
                DrawerContent(
                    state = sampleDrawerState(),
                    onCreateThread = {},
                    onCreateThreadInProject = { createdCwd = it },
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onForkThread = {},
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onUnarchiveThread = {}
                )
            }
        }

        rule.onNodeWithContentDescription("在 Project A 中开始新会话")
            .assertExists()
            .performClick()

        assertEquals("D:/Projects/Project A", createdCwd)
    }

    @Test
    fun drawerThreadMenuRenamesThread() {
        var renameCall: Pair<String, String>? = null
        rule.setContent {
            CodexTheme {
                DrawerContent(
                    state = sampleDrawerState(),
                    onCreateThread = {},
                    onCreateThreadInProject = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onForkThread = {},
                    onRenameThread = { id, name -> renameCall = id to name },
                    onArchiveThread = {},
                    onUnarchiveThread = {}
                )
            }
        }

        rule.onNodeWithTag("thread_row_more_project-a-1").performClick()
        rule.onNodeWithText("重命名").performClick()
        rule.onNodeWithTag("rename_thread_field").performTextClearance()
        rule.onNodeWithTag("rename_thread_field").performTextInput("Renamed Project")
        rule.onNodeWithTag("rename_thread_confirm").performClick()

        assertEquals("project-a-1" to "Renamed Project", renameCall)
    }

    @Test
    fun drawerThreadMenuForksThread() {
        var forkedThreadId: String? = null
        rule.setContent {
            CodexTheme {
                DrawerContent(
                    state = sampleDrawerState(),
                    onCreateThread = {},
                    onCreateThreadInProject = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onForkThread = { forkedThreadId = it },
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onUnarchiveThread = {}
                )
            }
        }

        rule.onNodeWithTag("thread_row_more_chat-1").performClick()
        rule.onNodeWithText("分叉").performClick()

        assertEquals("chat-1", forkedThreadId)
    }

    @Test
    fun drawerThreadMenuArchivesThread() {
        var archivedThreadId: String? = null
        rule.setContent {
            CodexTheme {
                DrawerContent(
                    state = sampleDrawerState(),
                    onCreateThread = {},
                    onCreateThreadInProject = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onForkThread = {},
                    onRenameThread = { _, _ -> },
                    onArchiveThread = { archivedThreadId = it },
                    onUnarchiveThread = {}
                )
            }
        }

        rule.onNodeWithTag("thread_row_more_chat-1").performClick()
        rule.onAllNodesWithText("归档")[1].performClick()

        assertEquals("chat-1", archivedThreadId)
    }

    @Test
    fun drawerArchivedSectionCanUnarchiveThread() {
        var unarchivedThreadId: String? = null
        rule.setContent {
            CodexTheme {
                DrawerContent(
                    state = sampleDrawerState().copy(
                        threads = sampleDrawerState().threads + ThreadSummary(
                            id = "archived-1",
                            title = "Archived chat",
                            preview = "old work",
                            status = ThreadStatus.IDLE,
                            updatedAt = 500L,
                            archived = true
                        )
                    ),
                    onCreateThread = {},
                    onCreateThreadInProject = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onForkThread = {},
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onUnarchiveThread = { unarchivedThreadId = it }
                )
            }
        }

        rule.onNodeWithText("归档").assertExists()
        rule.onNodeWithText("Archived chat").assertExists()
        rule.onNodeWithTag("thread_row_more_archived-1").performClick()
        rule.onNodeWithText("取消归档").performClick()

        assertEquals("archived-1", unarchivedThreadId)
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

    private fun sampleDrawerState() = HomeUiState(
        threads = listOf(
            ThreadSummary(
                id = "project-a-1",
                title = "Project task",
                preview = "preview",
                status = ThreadStatus.IDLE,
                updatedAt = 2_000L,
                groupKind = ThreadGroupKind.PROJECT,
                groupLabel = "Project A",
                cwd = "D:/Projects/Project A"
            ),
            ThreadSummary(
                id = "chat-1",
                title = "General chat",
                preview = "preview",
                status = ThreadStatus.IDLE,
                updatedAt = 1_000L
            )
        ),
        selectedThreadId = "project-a-1",
        pendingThreadTitle = null,
        isThreadSwitching = false,
        messages = emptyList(),
        hasMoreHistory = false,
        isLoadingOlder = false,
        composerText = "",
        isGenerating = false,
        isManualRefreshing = false,
        showComposerDetails = false,
        chips = emptyList(),
        slashCommands = emptyList(),
        pendingApproval = null,
        cwd = "D:/Projects/Project A",
        permissionSummary = "",
        connectionStatus = ConnectionStatus.CONNECTED,
        connectionDetail = "",
        gatewayConfig = GatewayConfig(),
        isDemoMode = false
    )
}
