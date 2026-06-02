package com.codexapp.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.assertCountEquals
import com.codexapp.model.ComposerChip
import com.codexapp.model.ComposerChipIcon
import com.codexapp.model.ComposerFile
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.GatewayConfig
import com.codexapp.model.GatewayConfigOption
import com.codexapp.model.GatewayConfigOptions
import com.codexapp.model.HomeUiState
import com.codexapp.model.AppUpdateState
import com.codexapp.model.AppUpdateStatus
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.OperationalNotice
import com.codexapp.model.PendingEditResendState
import com.codexapp.model.ThreadMessage
import com.codexapp.model.ThreadGroupKind
import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary
import com.codexapp.ui.app.OperationalNoticeOverlay
import com.codexapp.ui.drawer.DrawerContent
import com.codexapp.ui.composer.Composer
import com.codexapp.ui.composer.ComposerPanel
import com.codexapp.ui.theme.CodexTheme
import com.codexapp.ui.thread.ThreadScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThreadScreenVisibilityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun operationalNoticeOverlayShowsToastBubble() {
        rule.setContent {
            CodexTheme {
                OperationalNoticeOverlay(
                    notices = listOf(
                        OperationalNotice(
                            id = "notice-1",
                            text = "MCP 服务已就绪",
                            createdAt = 1L
                        )
                    )
                )
            }
        }

        rule.onNodeWithTag("operational_notice_notice-1").assertIsDisplayed()
        rule.onNodeWithText("MCP 服务已就绪").assertIsDisplayed()
    }

    @Test
    fun operationalNoticeOverlayShowsNoticeDetails() {
        rule.setContent {
            CodexTheme {
                OperationalNoticeOverlay(
                    notices = listOf(
                        OperationalNotice(
                            id = "notice-1",
                            text = "MCP 服务 github: 失败\n权限不足\n请重新登录",
                            createdAt = 1L
                        )
                    )
                )
            }
        }

        rule.waitForIdle()
        rule.onNodeWithTag("operational_notice_notice-1").assertIsDisplayed()
        rule.onNodeWithText("MCP 服务 github: 失败", substring = true).assertIsDisplayed()
        rule.onNodeWithText("权限不足", substring = true).assertIsDisplayed()
        rule.onNodeWithText("请重新登录", substring = true).assertIsDisplayed()
    }

    @Test
    fun operationalNoticeOverlayLimitsVisibleToastCount() {
        rule.setContent {
            CodexTheme {
                OperationalNoticeOverlay(
                    notices = List(5) { index ->
                        OperationalNotice(
                            id = "notice-$index",
                            text = "通知 $index",
                            createdAt = index.toLong()
                        )
                    }
                )
            }
        }

        rule.onAllNodesWithTag("operational_notice")
            .assertCountEquals(3)
    }

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
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
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
    fun jumpToBottomDoesNotOccupyUserMessageActionRail() {
        val state = sampleState(
            hasMoreHistory = false,
            isLoadingOlder = false,
            messageCount = 24
        ).copy(
            messages = sampleState(
                hasMoreHistory = false,
                isLoadingOlder = false,
                messageCount = 24
            ).messages.mapIndexed { index, message ->
                if (message.role == MessageRole.USER) {
                    message.copy(rollbackNumTurns = index + 1)
                } else {
                    message
                }
            }
        )
        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = state,
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.waitForIdle()
        rule.onNodeWithTag("thread_message_list").performScrollToIndex(0)
        rule.waitForIdle()

        val jumpBounds = rule.onNodeWithTag("jump_to_bottom_button").getUnclippedBoundsInRoot()
        val actionBounds = rule.onNodeWithTag("user_message_more_m23").getUnclippedBoundsInRoot()

        assertTrue(jumpBounds.right < actionBounds.left)
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
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
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
    fun connectionBannerShowsSpecificErrorDetail() {
        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        connectionStatus = ConnectionStatus.ERROR,
                        connectionDetail = "网关消息解析失败: bad json"
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithText("连接异常").assertIsDisplayed()
        rule.onNodeWithText("网关消息解析失败: bad json").assertIsDisplayed()
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
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
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
    fun loadsOlderMessagesWhenPulledDownAtTop() {
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
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithTag("thread_message_list").performScrollToIndex(0)
        rule.waitForIdle()
        rule.onNodeWithTag("thread_message_list").performTouchInput {
            swipeDown()
        }
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
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
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
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
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
                                blocks = listOf(MessageBlock.Text("please inspect build failure")),
                                rollbackNumTurns = 1
                            )
                        )
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { text, _ -> editedText = text },
                    onResendUserMessage = { text, _ -> resentText = text },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithTag("user_message_more_user-editable").performClick()
        assertTrue(runCatching { rule.onNodeWithText("复制").assertExists() }.isFailure)

        rule.onNodeWithTag("user_message_more_user-editable").performClick()
        rule.onNodeWithText("编辑后重发").performClick()
        assertEquals("please inspect build failure", editedText)
        rule.mainClock.advanceTimeBy(4_000L)
        rule.waitForIdle()

        rule.onNodeWithTag("user_message_more_user-editable").performClick()
        rule.onNodeWithText("重发").performClick()
        assertEquals("please inspect build failure", resentText)
    }

    @Test
    fun assistantMessageFooterForksFromTurn() {
        var forkNumTurns: Int? = null
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
                                id = "assistant-fork",
                                role = MessageRole.ASSISTANT,
                                blocks = listOf(MessageBlock.Text("branch from this reply")),
                                forkNumTurns = 2,
                                isFinal = true
                            )
                        )
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = { forkNumTurns = it },
                    onNewThreadDraftChange = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithTag("assistant_turn_fork_assistant-fork").performClick()

        assertEquals(2, forkNumTurns)
    }

    @Test
    fun onlyFinalAssistantMessageInTurnShowsForkAction() {
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
                                id = "assistant-1",
                                role = MessageRole.ASSISTANT,
                                blocks = listOf(MessageBlock.Text("first assistant chunk")),
                                forkNumTurns = 1,
                                isFinal = true
                            ),
                            ThreadMessage(
                                id = "assistant-2",
                                role = MessageRole.ASSISTANT,
                                blocks = listOf(MessageBlock.Text("final assistant reply")),
                                forkNumTurns = 2,
                                isFinal = true
                            )
                        )
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.waitForIdle()
        assertTrue(runCatching { rule.onNodeWithTag("assistant_turn_fork_assistant-1").assertExists() }.isFailure)
        rule.onNodeWithTag("assistant_turn_fork_assistant-2").assertExists()
    }

    @Test
    fun runningAssistantTurnDoesNotShowCopyOrForkActions() {
        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        isGenerating = true,
                        messages = listOf(
                            ThreadMessage(
                                id = "user-running",
                                role = MessageRole.USER,
                                blocks = listOf(MessageBlock.Text("prompt"))
                            ),
                            ThreadMessage(
                                id = "assistant-running",
                                role = MessageRole.ASSISTANT,
                                blocks = listOf(MessageBlock.Text("partial answer")),
                                forkNumTurns = 2,
                                durationMs = 5_000L,
                                isFinal = true
                            )
                        )
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithTag("assistant_turn_footer_assistant-running").assertExists()
        rule.onNodeWithText("5s").assertExists()
        assertTrue(runCatching { rule.onNodeWithTag("assistant_turn_copy_assistant-running").assertExists() }.isFailure)
        assertTrue(runCatching { rule.onNodeWithTag("assistant_turn_fork_assistant-running").assertExists() }.isFailure)
    }

    @Test
    fun userMessageMenuDoesNotForkFromTurn() {
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
                                id = "user-no-fork",
                                role = MessageRole.USER,
                                blocks = listOf(MessageBlock.Text("user prompt")),
                                forkNumTurns = 2
                            )
                        )
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        assertTrue(runCatching { rule.onNodeWithTag("user_message_more_user-no-fork").assertExists() }.isFailure)
    }

    @Test
    fun onlyFinalUserMessageInTurnShowsEditAction() {
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
                                id = "user-1",
                                role = MessageRole.USER,
                                blocks = listOf(MessageBlock.Text("first user chunk")),
                                rollbackNumTurns = 1
                            ),
                            ThreadMessage(
                                id = "user-2",
                                role = MessageRole.USER,
                                blocks = listOf(MessageBlock.Text("final user prompt")),
                                rollbackNumTurns = 2
                            )
                        )
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.waitForIdle()
        assertTrue(runCatching { rule.onNodeWithTag("user_message_more_user-1").assertExists() }.isFailure)
        rule.onNodeWithTag("user_message_more_user-2").assertExists()
    }

    @Test
    fun assistantMessageDoesNotShowCopyButton() {
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
                                id = "assistant-copy",
                                role = MessageRole.ASSISTANT,
                                blocks = listOf(MessageBlock.Text("copy this answer"))
                            )
                        )
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = {},
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        assertTrue(runCatching { rule.onNodeWithTag("assistant_message_copy_assistant-copy").assertExists() }.isFailure)
    }

    @Test
    fun composerDetailsDoNotExposeFileChips() {
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
                            ),
                            ComposerChip(
                                label = ".codex/AGENTS.md",
                                icon = ComposerChipIcon.FILE,
                                path = "D:/Projects/app/.codex/AGENTS.md"
                            )
                        )
                    ),
                    compactMode = false,
                    activePanel = ComposerPanel.NONE,
                    onActivePanelChange = {},
                    onToggleDetails = {},
                    onCloseDetails = {},
                    onChange = {},
                    onInsertText = {},
                    onApplySlashCommand = {},
                    onClearComposer = {},
                    onSend = {},
                    onStop = {}
                )
            }
        }

        rule.onNodeWithContentDescription("app/Main.kt").assertDoesNotExist()
        rule.onNodeWithContentDescription(".codex/AGENTS.md").assertDoesNotExist()
    }

    @Test
    fun composerDetailsOnlyExposeAllowedQuickActions() {
        rule.setContent {
            CodexTheme {
                Composer(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(showComposerDetails = true),
                    compactMode = false,
                    activePanel = ComposerPanel.NONE,
                    onActivePanelChange = {},
                    onToggleDetails = {},
                    onCloseDetails = {},
                    onChange = {},
                    onInsertText = {},
                    onApplySlashCommand = {},
                    onClearComposer = {},
                    onSend = {},
                    onStop = {}
                )
            }
        }

        rule.onNodeWithContentDescription("紧凑").assertDoesNotExist()
        rule.onNodeWithContentDescription("常规").assertDoesNotExist()
        rule.onNodeWithContentDescription("压缩").assertDoesNotExist()
        rule.onNodeWithContentDescription("回滚").assertDoesNotExist()
        rule.onNodeWithContentDescription("清空").assertIsDisplayed()
        rule.onNodeWithContentDescription("/命令").assertIsDisplayed()
        rule.onNodeWithContentDescription("文件").assertIsDisplayed()
        rule.onNodeWithContentDescription("Shell").assertDoesNotExist()
    }

    @Test
    fun composerDetailsHidesDefaultProjectPlaceholder() {
        rule.setContent {
            CodexTheme {
                Composer(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        showComposerDetails = true,
                        chips = listOf(ComposerChip("默认项目", ComposerChipIcon.FILE)),
                        isNewThreadDraft = true
                    ),
                    compactMode = false,
                    activePanel = ComposerPanel.NONE,
                    onActivePanelChange = {},
                    onToggleDetails = {},
                    onCloseDetails = {},
                    onChange = {},
                    onInsertText = {},
                    onApplySlashCommand = {},
                    onClearComposer = {},
                    onSend = {},
                    onStop = {}
                )
            }
        }

        rule.onNodeWithContentDescription("默认项目").assertDoesNotExist()
    }

    @Test
    fun newThreadDraftUsesDropdownsWithoutProjectSelector() {
        var updatedDraft: NewThreadDraft? = null
        val draft = NewThreadDraft(
            cwd = "D:/Projects/Project A",
            model = "model-a",
            reasoningEffort = "low"
        )
        val configOptions = GatewayConfigOptions(
            models = listOf(
                GatewayConfigOption(label = "Model A", value = "model-a"),
                GatewayConfigOption(label = "Model B", value = "model-b")
            ),
            reasoningEfforts = listOf(
                GatewayConfigOption(label = "low", value = "low"),
                GatewayConfigOption(label = "high", value = "high")
            )
        )

        rule.setContent {
            MaterialTheme {
                ThreadScreen(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        isNewThreadDraft = true,
                        newThreadDraft = draft,
                        configOptions = configOptions
                    ),
                    compactMode = false,
                    onOpenConnection = {},
                    onRefreshCurrent = {},
                    onLoadOlderMessages = {},
                    onEditUserMessage = { _, _ -> },
                    onResendUserMessage = { _, _ -> },
                    onForkFromMessage = {},
                    onNewThreadDraftChange = { updatedDraft = it },
                    onApprovePending = {},
                    onRejectPending = {}
                )
            }
        }

        rule.onNodeWithText("项目").assertDoesNotExist()
        rule.onNodeWithContentDescription("模型：Model A").performClick()
        rule.onNodeWithContentDescription("选择模型：Model B").performClick()
        assertEquals("model-b", updatedDraft?.model)

        rule.onNodeWithContentDescription("推理：low").performClick()
        rule.onNodeWithContentDescription("选择推理：high").performClick()
        assertEquals("high", updatedDraft?.reasoningEffort)
    }

    @Test
    fun openingSlashPanelDoesNotInsertACommand() {
        var changeCalls = 0
        var appliedCommand: String? = null
        rule.setContent {
            var composerText by remember { mutableStateOf("") }
            var activePanel by remember { mutableStateOf(ComposerPanel.NONE) }
            CodexTheme {
                Composer(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        showComposerDetails = true,
                        composerText = composerText,
                        slashCommands = listOf(
                            "/compact  压缩上下文",
                            "/rollback  回滚上轮",
                            "! ls  运行 shell 命令"
                        )
                    ),
                    compactMode = false,
                    activePanel = activePanel,
                    onActivePanelChange = { activePanel = it },
                    onToggleDetails = {},
                    onCloseDetails = {},
                    onChange = {
                        changeCalls += 1
                        composerText = it
                    },
                    onInsertText = {},
                    onApplySlashCommand = { appliedCommand = it },
                    onClearComposer = { composerText = "" },
                    onSend = {},
                    onStop = {}
                )
            }
        }

        rule.onNodeWithContentDescription("/命令").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("/compact").assertIsDisplayed()
        assertEquals(0, changeCalls)
        assertEquals(null, appliedCommand)
    }

    @Test
    fun fileQuickActionSearchesProjectFilesAndInsertsMention() {
        var inserted: String? = null
        rule.setContent {
            var activePanel by remember { mutableStateOf(ComposerPanel.NONE) }
            CodexTheme {
                Composer(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        showComposerDetails = true,
                        cwd = "D:/Projects/Project A",
                        files = listOf(
                            ComposerFile(
                                label = "src/CodexApp.kt",
                                path = "D:/Projects/Project A/src/CodexApp.kt"
                            ),
                            ComposerFile(
                                label = "README.md",
                                path = "D:/Projects/Project A/README.md"
                            )
                        )
                    ),
                    compactMode = false,
                    activePanel = activePanel,
                    onActivePanelChange = { activePanel = it },
                    onToggleDetails = {},
                    onCloseDetails = {},
                    onChange = {},
                    onInsertText = { inserted = it },
                    onApplySlashCommand = {},
                    onClearComposer = {},
                    onSend = {},
                    onStop = {}
                )
            }
        }

        rule.onNodeWithContentDescription("文件").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("file_picker_search_field").assertExists()
        rule.onNodeWithText("CodexApp.kt").assertIsDisplayed()
        rule.onNodeWithTag("file_picker_search_field").performTextInput("Codex")
        rule.onNodeWithText("CodexApp.kt").performClick()

        assertEquals("@{D:/Projects/Project A/src/CodexApp.kt}", inserted)
    }

    @Test
    fun composerFocusRequestFocusesInputFieldForEditFlow() {
        rule.setContent {
            CodexTheme {
                Composer(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        composerText = "please inspect build failure",
                        composerFocusRequest = 1L
                    ),
                    compactMode = false,
                    activePanel = ComposerPanel.NONE,
                    onActivePanelChange = {},
                    onToggleDetails = {},
                    onCloseDetails = {},
                    onChange = {},
                    onInsertText = {},
                    onApplySlashCommand = {},
                    onClearComposer = {},
                    onSend = {},
                    onStop = {}
                )
            }
        }

        rule.waitForIdle()
        rule.onNodeWithTag("composer_input_field").assertIsFocused()
    }

    @Test
    fun composerShowsPendingEditResendHint() {
        rule.setContent {
            CodexTheme {
                Composer(
                    state = sampleState(
                        hasMoreHistory = false,
                        isLoadingOlder = false,
                        messageCount = 0
                    ).copy(
                        composerText = "please inspect build failure",
                        pendingEditResend = PendingEditResendState(
                            threadId = "t1",
                            rollbackNumTurns = 3
                        )
                    ),
                    compactMode = false,
                    activePanel = ComposerPanel.NONE,
                    onActivePanelChange = {},
                    onToggleDetails = {},
                    onCloseDetails = {},
                    onChange = {},
                    onInsertText = {},
                    onApplySlashCommand = {},
                    onClearComposer = {},
                    onSend = {},
                    onStop = {}
                )
            }
        }

        rule.onNodeWithTag("composer_pending_edit_resend_hint").assertIsDisplayed()
        rule.onNodeWithText("下一次发送会回滚最近 3 轮后重发").assertIsDisplayed()
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
                    onOpenConnection = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onRestartDesktop = {}
                )
            }
        }

        rule.onNodeWithContentDescription("在 Project A 中开始新会话")
            .assertExists()
            .performClick()

        assertEquals("D:/Projects/Project A", createdCwd)
    }

    @Test
    fun drawerHeaderOpensGatewaySettings() {
        var openConnectionCalls = 0
        rule.setContent {
            CodexTheme {
                DrawerContent(
                    state = sampleDrawerState(),
                    onCreateThread = {},
                    onCreateThreadInProject = {},
                    onOpenConnection = { openConnectionCalls += 1 },
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onRestartDesktop = {}
                )
            }
        }

        rule.onNodeWithContentDescription("连接设置").performClick()

        assertEquals(1, openConnectionCalls)
    }

    @Test
    fun appUpdateErrorRowOpensReleasePage() {
        var openReleaseCalls = 0
        rule.setContent {
            CodexTheme {
                DrawerContent(
                    state = sampleDrawerState().copy(
                        appUpdate = AppUpdateState(
                            status = AppUpdateStatus.ERROR,
                            latestVersion = "0.2.18",
                            releasePageUrl = "https://github.com/liaoxycn/codexapp/releases/latest",
                            message = "系统下载器不可用"
                        )
                    ),
                    onCreateThread = {},
                    onCreateThreadInProject = {},
                    onOpenConnection = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onRestartDesktop = {},
                    onOpenUpdateReleasePage = { openReleaseCalls += 1 }
                )
            }
        }

        rule.onNodeWithText("发布页").performClick()
        assertEquals(1, openReleaseCalls)
    }

    @Test
    fun appUpdateQueuedRowDoesNotExposeFakeAction() {
        var openReleaseCalls = 0
        rule.setContent {
            CodexTheme {
                DrawerContent(
                    state = sampleDrawerState().copy(
                        appUpdate = AppUpdateState(
                            status = AppUpdateStatus.DOWNLOAD_QUEUED,
                            latestVersion = "0.2.18",
                            message = "已交给系统下载器"
                        )
                    ),
                    onCreateThread = {},
                    onCreateThreadInProject = {},
                    onOpenConnection = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onRestartDesktop = {},
                    onOpenUpdateReleasePage = { openReleaseCalls += 1 }
                )
            }
        }

        rule.onNodeWithText("等待安装").assertIsDisplayed()
        assertEquals(0, openReleaseCalls)
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
                    onOpenConnection = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onRenameThread = { id, name -> renameCall = id to name },
                    onArchiveThread = {},
                    onRestartDesktop = {}
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
    fun drawerThreadMenuDoesNotForkThread() {
        rule.setContent {
            CodexTheme {
                DrawerContent(
                    state = sampleDrawerState(),
                    onCreateThread = {},
                    onCreateThreadInProject = {},
                    onOpenConnection = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onRestartDesktop = {}
                )
            }
        }

        rule.onNodeWithTag("thread_row_more_chat-1").performClick()

        assertTrue(runCatching { rule.onNodeWithText("分叉").assertExists() }.isFailure)
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
                    onOpenConnection = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onRenameThread = { _, _ -> },
                    onArchiveThread = { archivedThreadId = it },
                    onRestartDesktop = {}
                )
            }
        }

        rule.onNodeWithTag("thread_row_more_chat-1").performClick()
        rule.onAllNodesWithText("归档")[1].performClick()

        assertEquals("chat-1", archivedThreadId)
    }

    @Test
    fun drawerOmitsArchivedThreads() {
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
                    onOpenConnection = {},
                    onRefreshThreads = {},
                    onSelectThread = {},
                    onRenameThread = { _, _ -> },
                    onArchiveThread = {},
                    onRestartDesktop = {}
                )
            }
        }

        rule.onNodeWithText("Archived chat").assertDoesNotExist()
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
        pendingSelectionThreadId = null,
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
        composerFocusRequest = 0L,
        pendingEditResend = null,
        isGenerating = false,
        isManualRefreshing = false,
        showComposerDetails = false,
        chips = listOf(ComposerChip("chip", ComposerChipIcon.FILE)),
        files = emptyList(),
        slashCommands = emptyList(),
        pendingApproval = null,
        cwd = "",
        permissionSummary = "",
        connectionStatus = ConnectionStatus.CONNECTED,
        connectionDetail = "",
        gatewayConfig = GatewayConfig(),
        isDemoMode = true,
        isNewThreadDraft = false,
        newThreadDraft = NewThreadDraft()
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
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        messages = emptyList(),
        hasMoreHistory = false,
        isLoadingOlder = false,
        composerText = "",
        composerFocusRequest = 0L,
        isGenerating = false,
        isManualRefreshing = false,
        showComposerDetails = false,
        chips = emptyList(),
        files = emptyList(),
        slashCommands = emptyList(),
        pendingApproval = null,
        cwd = "D:/Projects/Project A",
        permissionSummary = "",
        connectionStatus = ConnectionStatus.CONNECTED,
        connectionDetail = "",
        gatewayConfig = GatewayConfig(),
        isDemoMode = false,
        isNewThreadDraft = false,
        newThreadDraft = NewThreadDraft()
    )
}
