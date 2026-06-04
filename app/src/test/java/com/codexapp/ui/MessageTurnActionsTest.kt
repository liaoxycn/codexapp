package com.codexapp.ui

import com.codexapp.model.MessageBlock
import com.codexapp.model.MessageRole
import com.codexapp.model.ThreadMessage
import com.codexapp.ui.message.buildAssistantTurnUiModel
import com.codexapp.ui.message.hasMeaningfulReasoningText
import com.codexapp.ui.message.toTurnMessageItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTurnActionsTest {
    @Test
    fun runningTurnKeepsProcessMessagesVisibleUntilFinalReplyArrives() {
        val messages = listOf(
            user("u1"),
            assistant("status", MessageBlock.Status("运行中")),
            assistant("reasoning", MessageBlock.Reasoning("思考"))
        )

        val items = messages.toTurnMessageItems(currentTurnRunning = true)

        assertEquals(listOf("u1", "u1:assistant-running"), items.map { it.message.id })
        assertEquals(listOf("status", "reasoning"), items.last().processMessages.map { it.id })
        assertEquals("u1:assistant-running", items.last().stableKey)
        assertEquals(false, items.last().preferPlainText)
    }

    @Test
    fun runningTurnShowsThinkingPlaceholderBeforeGatewayProcessArrives() {
        val messages = listOf(user("u1"))

        val items = messages.toTurnMessageItems(currentTurnRunning = true)

        assertEquals(listOf("u1", "u1:assistant-running"), items.map { it.message.id })
        assertEquals(listOf("u1:assistant-running:thinking"), items.last().processMessages.map { it.id })
        assertEquals(true, items.last().assistantTurnRunning)
    }

    @Test
    fun runningTurnKeepsStreamingAssistantTextVisibleUntilTurnStops() {
        val messages = listOf(
            user("u1"),
            system("approval", MessageBlock.Status("审批已允许")),
            assistant("status", MessageBlock.Status("运行中")),
            assistant("reasoning", MessageBlock.Reasoning("思考")),
            assistant("streaming-text", MessageBlock.Text("正在输出，还没有结束"), durationMs = 5_000L)
        )

        val items = messages.toTurnMessageItems(currentTurnRunning = true)

        assertEquals(listOf("u1", "streaming-text"), items.map { it.message.id })
        assertEquals(listOf("approval", "status", "reasoning"), items.last().processMessages.map { it.id })
        assertEquals("u1:assistant-running", items.last().stableKey)
        assertEquals(5_000L, items.last().message.durationMs)
    }

    @Test
    fun runningTurnAbsorbsProcessMessagesThatArriveAfterStreamingText() {
        val messages = listOf(
            user("u1"),
            assistant("streaming-text", MessageBlock.Text("正在输出，还没有结束"), durationMs = 16_000L),
            assistant("search-1", MessageBlock.Status("网页搜索 search: weather Shenzhen")),
            assistant("search-2", MessageBlock.Status("网页搜索 openPage: https://weather.example"))
        )

        val items = messages.toTurnMessageItems(currentTurnRunning = true)

        assertEquals(listOf("u1", "streaming-text"), items.map { it.message.id })
        assertEquals(listOf("search-1", "search-2"), items.last().processMessages.map { it.id })
        assertEquals("u1:assistant-running", items.last().stableKey)
        assertEquals(true, items.last().assistantTurnRunning)
    }

    @Test
    fun runningTurnKeepsSameAssistantKeyWhenTextArrivesAfterThinking() {
        val thinkingOnly = listOf(
            user("u1"),
            assistant("reasoning", MessageBlock.Reasoning("正在思考"))
        ).toTurnMessageItems(currentTurnRunning = true)
        val streamingText = listOf(
            user("u1"),
            assistant("reasoning", MessageBlock.Reasoning("正在思考")),
            assistant("streaming-text", MessageBlock.Text("开始输出"))
        ).toTurnMessageItems(currentTurnRunning = true)

        assertEquals("u1:assistant-running", thinkingOnly.last().stableKey)
        assertEquals("u1:assistant-running", streamingText.last().stableKey)
        assertEquals(listOf("reasoning"), streamingText.last().processMessages.map { it.id })
        assertEquals(false, streamingText.last().preferPlainText)
    }

    @Test
    fun runningTurnDisablesAssistantFinalActionsEvenIfMessageCarriesFinalMetadata() {
        val messages = listOf(
            user("u1"),
            assistant(
                id = "streaming-text",
                block = MessageBlock.Text("正在输出，还没有结束"),
                isFinal = true,
                durationMs = 5_000L,
                forkNumTurns = 2
            )
        )

        val items = messages.toTurnMessageItems(currentTurnRunning = true)

        assertEquals(false, items.last().assistantActionsEnabled)
        assertEquals(5_000L, items.last().message.durationMs)
        assertEquals(2, items.last().message.forkNumTurns)
    }

    @Test
    fun completedTurnMovesProcessMessagesAboveFinalReply() {
        val messages = listOf(
            user("u1"),
            system("approval", MessageBlock.Status("审批已允许")),
            assistant("status", MessageBlock.Status("运行中")),
            assistant("reasoning", MessageBlock.Reasoning("思考")),
            assistant("final", MessageBlock.Text("最终回复"), isFinal = true)
        )

        val items = messages.toTurnMessageItems()

        assertEquals(listOf("u1", "final"), items.map { it.message.id })
        assertEquals(listOf("approval", "status", "reasoning"), items.last().processMessages.map { it.id })
        assertEquals(false, items.last().preferPlainText)
    }

    @Test
    fun semanticProcessBlocksDoNotBecomeFinalReplyText() {
        val messages = listOf(
            user("u1"),
            assistant("commentary", MessageBlock.Commentary("我先检查 gateway mapper。")),
            assistant("plan", MessageBlock.Plan("检查实现并修改")),
            assistant("tool", MessageBlock.ToolCall("正在调用工具 codegraph/explore")),
            ThreadMessage(
                id = "tool-detail",
                role = MessageRole.ASSISTANT,
                blocks = listOf(
                    MessageBlock.ToolCall("已调用工具 codegraph/explore"),
                    MessageBlock.Code(language = "json", value = """{"ok":true}""")
                )
            ),
            assistant("search", MessageBlock.WebSearch("已搜索网页: Codex app-server")),
            assistant("final", MessageBlock.Text("最终回复"), isFinal = true)
        )

        val items = messages.toTurnMessageItems()

        assertEquals(listOf("u1", "final"), items.map { it.message.id })
        assertEquals(listOf("commentary", "plan", "tool", "tool-detail", "search"), items.last().processMessages.map { it.id })
    }

    @Test
    fun reasoningPlaceholderDoesNotCountAsMeaningfulContent() {
        assertFalse(listOf(MessageBlock.Reasoning("正在思考")).hasMeaningfulReasoningText())
        assertTrue(listOf(MessageBlock.Reasoning("先检查 snapshot，再处理 running 收口")).hasMeaningfulReasoningText())
    }

    @Test
    fun completedTurnAbsorbsProcessMessagesThatArriveAfterFinalReply() {
        val messages = listOf(
            user("u1"),
            assistant("final", MessageBlock.Text("最终回复"), isFinal = true, durationMs = 42_000L),
            system("approval", MessageBlock.Status("审批已允许")),
            assistant("search-1", MessageBlock.Status("网页搜索 search: weather Shenzhen")),
            assistant("search-2", MessageBlock.Status("网页搜索 openPage: https://weather.example"))
        )

        val items = messages.toTurnMessageItems()

        assertEquals(listOf("u1", "final"), items.map { it.message.id })
        assertEquals(listOf("approval", "search-1", "search-2"), items.last().processMessages.map { it.id })
        assertEquals(false, items.last().assistantTurnRunning)
    }

    @Test
    fun shellOnlyFinalAssistantStillAnchorsCompletedTurn() {
        val messages = listOf(
            user("u1"),
            system("approval", MessageBlock.Status("审批已允许")),
            ThreadMessage(
                id = "command-final",
                role = MessageRole.ASSISTANT,
                blocks = listOf(
                    MessageBlock.CommandSummary("已运行 1 条命令"),
                    MessageBlock.CommandMeta("结果: 退出码 0"),
                    MessageBlock.Code(language = "shell", value = "12345")
                ),
                isFinal = true,
                durationMs = 2_000L
            )
        )

        val items = messages.toTurnMessageItems()

        assertEquals(listOf("u1", "command-final"), items.map { it.message.id })
        assertEquals(listOf("approval"), items.last().processMessages.map { it.id })
        assertEquals(false, items.last().assistantTurnRunning)
    }

    @Test
    fun assistantTextWithoutFinalFlagStaysExpandedEvenWhenGenerationFlagIsIdle() {
        val messages = listOf(
            user("u1"),
            assistant("status", MessageBlock.Status("运行中")),
            assistant("reasoning", MessageBlock.Reasoning("思考")),
            assistant("streaming-text", MessageBlock.Text("正在输出，还没有结束"))
        )

        val items = messages.toTurnMessageItems(currentTurnRunning = false)

        assertEquals(listOf("u1", "streaming-text"), items.map { it.message.id })
        assertEquals(listOf("status", "reasoning"), items.last().processMessages.map { it.id })
        assertEquals(false, items.last().showAssistantActions)
    }

    @Test
    fun assistantTurnUiModelSeparatesProcessBlocksFromFinalBody() {
        val model = buildAssistantTurnUiModel(
            message = ThreadMessage(
                id = "assistant-final",
                role = MessageRole.ASSISTANT,
                blocks = listOf(
                    MessageBlock.Status("运行中"),
                    MessageBlock.Reasoning("正在分析"),
                    MessageBlock.Text("最终回复")
                ),
                forkNumTurns = 2,
                durationMs = 8_000L,
                isFinal = true
            ),
            processMessages = listOf(
                ThreadMessage(
                    id = "assistant-process",
                    role = MessageRole.ASSISTANT,
                    blocks = listOf(MessageBlock.Status("运行中"))
                )
            ),
            isRunning = false,
            showActions = true,
            enableFinalActions = true,
            preferPlainText = false
        )

        assertEquals(listOf(MessageBlock.Text("最终回复")), model.bodyBlocks)
        assertEquals(2, model.processMessages.size)
        assertEquals("最终回复", model.finalText)
        assertTrue(model.canCopy)
        assertTrue(model.canFork)
        assertTrue(model.showFooterActions)
        assertFalse(model.isRunning)
    }

    @Test
    fun assistantTurnUiModelKeepsFinalActionsDisabledWhileRunning() {
        val model = buildAssistantTurnUiModel(
            message = ThreadMessage(
                id = "assistant-running",
                role = MessageRole.ASSISTANT,
                blocks = listOf(MessageBlock.Text("partial answer")),
                forkNumTurns = 2,
                durationMs = 5_000L,
                isFinal = true
            ),
            processMessages = emptyList(),
            isRunning = true,
            showActions = true,
            enableFinalActions = true,
            preferPlainText = true
        )

        assertFalse(model.canCopy)
        assertFalse(model.canFork)
        assertFalse(model.showFooterActions)
        assertTrue(model.preferPlainText)
    }

    @Test
    fun nextTurnDoesNotAbsorbPreviousProcessMessages() {
        val messages = listOf(
            user("u1"),
            assistant("final-1", MessageBlock.Text("第一轮"), isFinal = true),
            user("u2"),
            assistant("status-2", MessageBlock.Status("第二轮运行中"))
        )

        val items = messages.toTurnMessageItems()

        assertEquals(listOf("u1", "final-1", "u2"), items.map { it.message.id })
    }

    @Test
    fun completedProcessOnlyTurnDoesNotRenderStandaloneAssistantRows() {
        val messages = listOf(
            user("u1"),
            assistant("status", MessageBlock.Status("运行中")),
            assistant("tool", MessageBlock.ToolCall("已调用工具 node_repl/js")),
            assistant("file", MessageBlock.FileChangeSummary("已编辑 bridgeBackend.test.mjs"))
        )

        val items = messages.toTurnMessageItems()

        assertEquals(listOf("u1"), items.map { it.message.id })
    }

    @Test
    fun completedFinalCommandOnlyTurnRendersCommandAssistant() {
        val messages = listOf(
            user("u1"),
            ThreadMessage(
                id = "cmd",
                role = MessageRole.ASSISTANT,
                blocks = listOf(
                    MessageBlock.CommandSummary("已运行命令"),
                    MessageBlock.CommandMeta("命令: pwd"),
                    MessageBlock.Code("shell", "D:\\Projects\\home\\codexapp")
                ),
                durationMs = 3_000L,
                isFinal = true
            )
        )

        val items = messages.toTurnMessageItems()

        assertEquals(listOf("u1", "cmd"), items.map { it.message.id })
        assertEquals(emptyList<ThreadMessage>(), items.last().processMessages)
        assertEquals(3_000L, items.last().message.durationMs)
        assertTrue(items.last().showAssistantActions)
    }

    @Test
    fun completedNonFinalCommandOnlyTurnRendersCommandProcessAnchor() {
        val messages = listOf(
            user("u1"),
            ThreadMessage(
                id = "cmd",
                role = MessageRole.ASSISTANT,
                blocks = listOf(
                    MessageBlock.CommandSummary("已运行命令"),
                    MessageBlock.CommandMeta("命令: pwd"),
                    MessageBlock.Code("shell", "D:\\Projects\\home\\codexapp")
                ),
                durationMs = 3_000L
            )
        )

        val items = messages.toTurnMessageItems()

        assertEquals(listOf("u1", "u1:assistant-command-process"), items.map { it.message.id })
        assertEquals(listOf("cmd"), items.last().processMessages.map { it.id })
        assertEquals(3_000L, items.last().message.durationMs)
        assertFalse(items.last().showAssistantActions)
    }

    @Test
    fun leadingProcessMessagesAreNotRenderedAsStandaloneTurns() {
        val messages = listOf(
            assistant("status", MessageBlock.Status("运行中")),
            assistant("tool", MessageBlock.ToolCall("已调用工具 node_repl/js")),
            user("u1"),
            assistant("final", MessageBlock.Text("最终回复"), isFinal = true)
        )

        val items = messages.toTurnMessageItems()

        assertEquals(listOf("u1", "final"), items.map { it.message.id })
        assertEquals(emptyList<ThreadMessage>(), items.last().processMessages)
    }

    private fun user(id: String) = ThreadMessage(
        id = id,
        role = MessageRole.USER,
        blocks = listOf(MessageBlock.Text("prompt"))
    )

    private fun assistant(
        id: String,
        block: MessageBlock,
        isFinal: Boolean = false,
        durationMs: Long? = null,
        forkNumTurns: Int? = null
    ) = ThreadMessage(
        id = id,
        role = MessageRole.ASSISTANT,
        blocks = listOf(block),
        forkNumTurns = forkNumTurns,
        durationMs = durationMs,
        isFinal = isFinal
    )

    private fun system(
        id: String,
        block: MessageBlock
    ) = ThreadMessage(
        id = id,
        role = MessageRole.SYSTEM,
        blocks = listOf(block)
    )
}
