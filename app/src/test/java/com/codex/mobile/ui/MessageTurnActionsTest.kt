package com.codex.mobile.ui

import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.ui.message.toTurnMessageItems
import org.junit.Assert.assertEquals
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

        assertEquals(listOf("u1", "status", "reasoning"), items.map { it.message.id })
        assertEquals(emptyList<ThreadMessage>(), items.last().processMessages)
    }

    @Test
    fun runningTurnKeepsStreamingAssistantTextVisibleUntilTurnStops() {
        val messages = listOf(
            user("u1"),
            assistant("status", MessageBlock.Status("运行中")),
            assistant("reasoning", MessageBlock.Reasoning("思考")),
            assistant("streaming-text", MessageBlock.Text("正在输出，还没有结束"), durationMs = 5_000L)
        )

        val items = messages.toTurnMessageItems(currentTurnRunning = true)

        assertEquals(listOf("u1", "status", "reasoning", "streaming-text"), items.map { it.message.id })
        assertEquals(emptyList<ThreadMessage>(), items.last().processMessages)
        assertEquals(5_000L, items.last().message.durationMs)
    }

    @Test
    fun completedTurnMovesProcessMessagesAboveFinalReply() {
        val messages = listOf(
            user("u1"),
            assistant("status", MessageBlock.Status("运行中")),
            assistant("reasoning", MessageBlock.Reasoning("思考")),
            assistant("final", MessageBlock.Text("最终回复"), isFinal = true)
        )

        val items = messages.toTurnMessageItems()

        assertEquals(listOf("u1", "final"), items.map { it.message.id })
        assertEquals(listOf("status", "reasoning"), items.last().processMessages.map { it.id })
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

        assertEquals(listOf("u1", "status", "reasoning", "streaming-text"), items.map { it.message.id })
        assertEquals(emptyList<ThreadMessage>(), items.last().processMessages)
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

        assertEquals(listOf("u1", "final-1", "u2", "status-2"), items.map { it.message.id })
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
        durationMs: Long? = null
    ) = ThreadMessage(
        id = id,
        role = MessageRole.ASSISTANT,
        blocks = listOf(block),
        durationMs = durationMs,
        isFinal = isFinal
    )
}
