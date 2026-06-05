package com.codexapp.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.codexapp.model.ConnectionStatus
import com.codexapp.model.GatewayConfig
import com.codexapp.model.HomeUiState
import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.NewThreadDraft
import com.codexapp.model.ThreadMessage
import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary
import com.codexapp.ui.theme.CodexTheme
import com.codexapp.ui.thread.ThreadScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MessageFlowScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun capturesRunningProcessFlow() {
        renderMessageFlow(
            state = screenshotState(
                isGenerating = true,
                messages = listOf(
                    user("u-running", "继续优化消息流"),
                    assistant("status-running", MessageBlock.Status("正在读取项目上下文")),
                    assistant("reasoning-running", MessageBlock.Reasoning("我会先检查消息分组，再调整过程流排版。")),
                    assistant("tool-running", MessageBlock.ToolCall("mcp工具调用: codegraph/explore")),
                    assistant(
                        id = "streaming-final",
                        block = MessageBlock.Text("正在整理改动，还没有结束。")
                    )
                )
            )
        )

        rule.onNodeWithText("已处理 0s", substring = true).assertIsDisplayed()
        assertMessageStartsBelowSurfaceTop("继续优化消息流")
        rule.onNodeWithText("正在思考中").assertIsDisplayed()
        assertTextAppearsBelow("正在整理改动", "正在思考中")
        rule.onNodeWithText("mcp工具调用", substring = true).assertIsDisplayed()
        assertNodeNotDisplayedByDescription("复制文本")
        assertNodeNotDisplayedByDescription("从此处分叉")
        captureScreenshot("message-flow-running")
    }

    @Test
    fun capturesCompletedFinalMarkdownFlow() {
        renderMessageFlow(
            state = screenshotState(
                messages = listOf(
                    user("u-final", "总结这次改动"),
                    assistant("commentary-final", MessageBlock.Commentary("我先收口运行态，再检查按钮条件。")),
                    assistant("file-final", MessageBlock.FileChangeSummary("已编辑 1 个文件")),
                    ThreadMessage(
                        id = "file-final-meta",
                        role = MessageRole.ASSISTANT,
                        blocks = listOf(
                            MessageBlock.FileChangeMeta("已编辑 AssistantMessages.kt", "app/src/main/java/com/codexapp/ui/message/AssistantMessages.kt"),
                            MessageBlock.FileChangeDiff("+ canFork = finalText.isNotBlank()")
                        )
                    ),
                    ThreadMessage(
                        id = "assistant-final",
                        role = MessageRole.ASSISTANT,
                        blocks = listOf(
                            MessageBlock.Text(
                                """
                                **结论**

                                - running 时不显示复制和分叉
                                - 完成后保留最终回复动作
                                """.trimIndent()
                            )
                        ),
                        forkNumTurns = 2,
                        durationMs = 8_000L,
                        isFinal = true
                    )
                )
            )
        )

        rule.onNodeWithText("已处理 8s", substring = true).assertIsDisplayed()
        assertMessageStartsBelowSurfaceTop("总结这次改动")
        rule.onNodeWithText("结论", substring = true).assertIsDisplayed()
        rule.onNodeWithText("running 时不显示复制和分叉", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("复制文本").assertIsDisplayed()
        rule.onNodeWithContentDescription("从此处分叉").assertIsDisplayed()
        captureScreenshot("message-flow-completed-final")
    }

    @Test
    fun capturesExpandedProcessOnlyFlow() {
        renderMessageFlow(
            state = screenshotState(
                isGenerating = true,
                messages = listOf(
                    user("u-process", "只运行命令，不输出最终正文"),
                    ThreadMessage(
                        id = "assistant-process-only",
                        role = MessageRole.ASSISTANT,
                        blocks = listOf(
                            MessageBlock.CommandSummary("正在运行命令"),
                            MessageBlock.CommandMeta("命令: .\\gradlew.bat compileDebugKotlin"),
                            MessageBlock.Code("shell", "Compiling Kotlin...")
                        )
                    )
                )
            )
        )

        rule.onNodeWithText("已处理 0s", substring = true).assertIsDisplayed()
        assertMessageStartsBelowSurfaceTop("只运行命令")
        rule.onNodeWithText("正在运行命令").assertIsDisplayed()
        rule.onNodeWithText("命令: .\\gradlew.bat compileDebugKotlin", substring = true).assertIsDisplayed()
        rule.onNodeWithText("正在思考中").assertIsDisplayed()
        assertNodeNotDisplayedByDescription("复制文本")
        assertNodeNotDisplayedByDescription("从此处分叉")
        captureScreenshot("message-flow-process-only-expanded")
    }

    @Test
    fun capturesToolFileCommandProcessFlow() {
        renderMessageFlow(
            state = screenshotState(
                messages = listOf(
                    user("u-tools", "展示完整过程类型"),
                    assistant("commentary-tools", MessageBlock.Commentary("我会先读文件，再调用工具并运行命令。")),
                    ThreadMessage(
                        id = "tool-detail",
                        role = MessageRole.ASSISTANT,
                        blocks = listOf(
                            MessageBlock.ToolCall("mcp工具调用: node_repl/js"),
                            MessageBlock.Code("json", """{"ok":true,"title":"Inspect UI"}""")
                        )
                    ),
                    ThreadMessage(
                        id = "file-change",
                        role = MessageRole.ASSISTANT,
                        blocks = listOf(
                            MessageBlock.FileChangeSummary("已编辑 2 个文件"),
                            MessageBlock.FileChangeMeta("已编辑 AssistantProcessStream.kt", "app/src/main/java/com/codexapp/ui/message/AssistantProcessStream.kt"),
                            MessageBlock.FileChangeMeta("已编辑 MessageFlowScreenshotTest.kt", "app/src/androidTest/java/com/codexapp/ui/MessageFlowScreenshotTest.kt")
                        )
                    ),
                    ThreadMessage(
                        id = "command",
                        role = MessageRole.ASSISTANT,
                        blocks = listOf(
                            MessageBlock.CommandSummary("已运行命令"),
                            MessageBlock.CommandMeta("命令: node scripts/dev-run.mjs"),
                            MessageBlock.Code("shell", "all steps done")
                        )
                    ),
                    assistant(
                        id = "assistant-tools-final",
                        block = MessageBlock.Text("最终完成。"),
                        forkNumTurns = 2,
                        durationMs = 11_000L,
                        isFinal = true
                    )
                )
            )
        )

        rule.onNodeWithTag("assistant_processed_header").performClick()
        rule.waitForIdle()
        assertMessageStartsBelowSurfaceTop("展示完整过程类型")
        rule.onNodeWithText("mcp工具调用", substring = true).assertIsDisplayed()
        rule.onNodeWithText("已编辑 2 个文件").assertIsDisplayed()
        rule.onNodeWithText("已运行命令").assertIsDisplayed()
        captureScreenshot("message-flow-tool-file-command")
    }

    private fun renderMessageFlow(state: HomeUiState) {
        rule.setContent {
            CodexTheme {
                Box(
                    modifier = Modifier
                        .size(width = 390.dp, height = 760.dp)
                        .background(CodexTheme.colors.background)
                        .padding(top = 24.dp)
                        .testTag("message_flow_screenshot_surface")
                ) {
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
        }
        rule.waitForIdle()
    }

    private fun assertMessageStartsBelowSurfaceTop(text: String) {
        val bounds = rule.onNodeWithText(text, substring = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected screenshot message to start below the top safe area: $text",
            bounds.top >= 18.dp
        )
    }

    private fun assertTextAppearsBelow(anchorText: String, lowerText: String) {
        val anchorBounds = rule.onNodeWithText(anchorText, substring = true)
            .getUnclippedBoundsInRoot()
        val lowerBounds = rule.onNodeWithText(lowerText, substring = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected '$lowerText' to appear below '$anchorText'",
            lowerBounds.top >= anchorBounds.bottom
        )
    }

    private fun captureScreenshot(name: String) {
        val bitmap = rule.onNodeWithTag("message_flow_screenshot_surface")
            .captureToImage()
            .asAndroidBitmap()
        PlatformTestStorageRegistry.getInstance()
            .openOutputFile("message-flow-screenshots/$name.png")
            .use { output ->
                assertTrue(
                    "Expected screenshot to be encoded: $name",
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                )
        }
    }

    private fun assertNodeNotDisplayedByDescription(description: String) {
        assertTrue(
            "Expected node to be absent or hidden: $description",
            runCatching {
                rule.onNodeWithContentDescription(description).assertIsDisplayed()
            }.isFailure
        )
    }

    private fun screenshotState(
        messages: List<ThreadMessage>,
        isGenerating: Boolean = false
    ) = HomeUiState(
        threads = listOf(
            ThreadSummary(
                id = "screenshot-thread",
                title = "截图回归",
                preview = "message flow",
                status = if (isGenerating) ThreadStatus.RUNNING else ThreadStatus.IDLE
            )
        ),
        selectedThreadId = "screenshot-thread",
        pendingSelectionThreadId = null,
        pendingThreadTitle = null,
        isThreadSwitching = false,
        messages = messages,
        hasMoreHistory = false,
        isLoadingOlder = false,
        composerText = "",
        composerFocusRequest = 0L,
        pendingEditResend = null,
        isGenerating = isGenerating,
        isManualRefreshing = false,
        showComposerDetails = false,
        chips = emptyList(),
        files = emptyList(),
        slashCommands = emptyList(),
        pendingApproval = null,
        cwd = "D:/Projects/home/codexapp",
        permissionSummary = "danger-full-access",
        connectionStatus = ConnectionStatus.CONNECTED,
        connectionDetail = "",
        gatewayConfig = GatewayConfig(),
        isDemoMode = true,
        isNewThreadDraft = false,
        newThreadDraft = NewThreadDraft()
    )

    private fun user(id: String, text: String) = ThreadMessage(
        id = id,
        role = MessageRole.USER,
        blocks = listOf(MessageBlock.Text(text)),
        rollbackNumTurns = 1
    )

    private fun assistant(
        id: String,
        block: MessageBlock,
        forkNumTurns: Int? = null,
        durationMs: Long? = null,
        isFinal: Boolean = false
    ) = ThreadMessage(
        id = id,
        role = MessageRole.ASSISTANT,
        blocks = listOf(block),
        forkNumTurns = forkNumTurns,
        durationMs = durationMs,
        isFinal = isFinal
    )
}
