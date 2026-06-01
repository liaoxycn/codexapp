package com.codex.mobile.ui

import com.codex.mobile.ui.state.ComposerActionHandler
import com.codex.mobile.ui.state.ComposerSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.startCoroutine

class ComposerActionHandlerTest {
    @Test
    fun sendTrimsPromptAndClearsAcceptedDraft() {
        val session = ComposerSession("thread-1")
        val sentPrompts = mutableListOf<String>()
        val handler = ComposerActionHandler(
            composerSession = session,
            selectedThreadId = { "thread-1" },
            launch = { block -> runBlockingImmediate(block) },
            sendPrompt = { prompt ->
                sentPrompts += prompt
                true
            }
        )

        handler.updateComposer("  hello world  ")
        handler.send()

        assertEquals(listOf("hello world"), sentPrompts)
        assertEquals("", session.currentText())
    }

    @Test
    fun sendKeepsDraftWhenPromptRejected() {
        val session = ComposerSession("thread-1")
        val handler = ComposerActionHandler(
            composerSession = session,
            selectedThreadId = { "thread-1" },
            launch = { block -> runBlockingImmediate(block) },
            sendPrompt = { false }
        )

        handler.updateComposer("draft")
        handler.send()

        assertEquals("draft", session.currentText())
    }

    @Test
    fun compactContextWritesCommandAndSubmitsIt() {
        val session = ComposerSession("thread-1")
        val sentPrompts = mutableListOf<String>()
        val handler = ComposerActionHandler(
            composerSession = session,
            selectedThreadId = { "thread-1" },
            launch = { block -> runBlockingImmediate(block) },
            sendPrompt = { prompt ->
                sentPrompts += prompt
                true
            }
        )

        handler.compactContext()

        assertEquals(listOf("/compact"), sentPrompts)
        assertEquals("", session.currentText())
    }

    @Test
    fun rollbackLastTurnWritesCommandAndSubmitsIt() {
        val session = ComposerSession("thread-1")
        val sentPrompts = mutableListOf<String>()
        val handler = ComposerActionHandler(
            composerSession = session,
            selectedThreadId = { "thread-1" },
            launch = { block -> runBlockingImmediate(block) },
            sendPrompt = { prompt ->
                sentPrompts += prompt
                true
            }
        )

        handler.rollbackLastTurn()

        assertEquals(listOf("/rollback"), sentPrompts)
        assertEquals("", session.currentText())
    }

    @Test
    fun resendTextReplacesDraftAndSubmitsImmediately() {
        val session = ComposerSession("thread-1")
        val sentPrompts = mutableListOf<String>()
        val handler = ComposerActionHandler(
            composerSession = session,
            selectedThreadId = { "thread-1" },
            launch = { block -> runBlockingImmediate(block) },
            sendPrompt = { prompt ->
                sentPrompts += prompt
                true
            }
        )

        handler.updateComposer("old draft")
        handler.resendText(" previous prompt ")

        assertEquals(listOf("previous prompt"), sentPrompts)
        assertEquals("", session.currentText())
    }

    @Test
    fun insertsShellTemplate() {
        val session = ComposerSession("thread-1")
        val handler = ComposerActionHandler(
            composerSession = session,
            selectedThreadId = { "thread-1" },
            launch = { block -> runBlockingImmediate(block) },
            sendPrompt = { true }
        )

        handler.insertShellTemplate()

        assertEquals("! ", session.currentText())
    }

    @Test
    fun applySlashCommandReplacesTrailingCommandToken() {
        val session = ComposerSession("thread-1")
        val handler = ComposerActionHandler(
            composerSession = session,
            selectedThreadId = { "thread-1" },
            launch = { block -> runBlockingImmediate(block) },
            sendPrompt = { true }
        )

        handler.updateComposer("请执行 /com")
        handler.applySlashCommand("/compact")

        assertTrue(session.currentText().endsWith("/compact "))
    }

    private fun runBlockingImmediate(block: suspend () -> Unit) {
        var failure: Throwable? = null
        block.startCoroutine(
            object : kotlin.coroutines.Continuation<Unit> {
                override val context = kotlin.coroutines.EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    failure = result.exceptionOrNull()
                }
            }
        )
        failure?.let { throw it }
    }
}
