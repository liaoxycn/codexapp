package com.codex.mobile.ui

import com.codex.mobile.model.NewThreadDraft
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
        val handler = handlerFor(
            session = session,
            sendPrompt = { prompt, _ ->
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
        val handler = handlerFor(
            session = session,
            sendPrompt = { _, _ -> false }
        )

        handler.updateComposer("draft")
        handler.send()

        assertEquals("draft", session.currentText())
    }

    @Test
    fun compactContextWritesCommandAndSubmitsIt() {
        val session = ComposerSession("thread-1")
        val sentPrompts = mutableListOf<String>()
        val handler = handlerFor(
            session = session,
            sendPrompt = { prompt, _ ->
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
        val handler = handlerFor(
            session = session,
            sendPrompt = { prompt, _ ->
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
        val handler = handlerFor(
            session = session,
            sendPrompt = { prompt, _ ->
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
    fun editAndResendOnlyPrefillsUntilUserSends() {
        val session = ComposerSession("thread-1")
        val sentPrompts = mutableListOf<String>()
        val resentPrompts = mutableListOf<Pair<String, Int>>()
        val handler = handlerFor(
            session = session,
            sendPrompt = { prompt, _ ->
                sentPrompts += prompt
                true
            },
            resendPrompt = { prompt, rollbackNumTurns ->
                resentPrompts += prompt to rollbackNumTurns
                true
            }
        )

        handler.updateComposer("old draft")
        handler.editAndResendText(" previous prompt ", 3)

        assertEquals(" previous prompt ", session.currentText())
        assertEquals(emptyList<String>(), sentPrompts)
        assertEquals(emptyList<Pair<String, Int>>(), resentPrompts)

        handler.send()

        assertEquals(listOf("previous prompt" to 3), resentPrompts)
        assertEquals(emptyList<String>(), sentPrompts)
        assertEquals("", session.currentText())
    }

    @Test
    fun editAndResendKeepsDraftWhenResendRejected() {
        val session = ComposerSession("thread-1")
        val handler = handlerFor(
            session = session,
            resendPrompt = { _, _ -> false }
        )

        handler.editAndResendText("editable", 2)
        handler.send()

        assertEquals("editable", session.currentText())
    }

    @Test
    fun insertsShellTemplate() {
        val session = ComposerSession("thread-1")
        val handler = handlerFor(session)

        handler.insertShellTemplate()

        assertEquals("! ", session.currentText())
    }

    @Test
    fun applySlashCommandReplacesTrailingCommandToken() {
        val session = ComposerSession("thread-1")
        val handler = handlerFor(session)

        handler.updateComposer("请执行 /com")
        handler.applySlashCommand("/compact")

        assertTrue(session.currentText().endsWith("/compact "))
    }

    @Test
    fun acceptedDraftPromptNotifiesNewThreadCreation() {
        val session = ComposerSession("")
        var acceptedDraft = false
        val handler = handlerFor(
            session = session,
            selectedThreadId = { "" },
            newThreadDraft = { NewThreadDraft(cwd = "D:/Projects/App") },
            sendPrompt = { _, draft -> draft != null },
            onPromptAccepted = { acceptedDraft = it }
        )

        handler.updateComposer("start")
        handler.send()

        assertTrue(acceptedDraft)
        assertEquals("", session.currentText())
    }

    private fun handlerFor(
        session: ComposerSession,
        selectedThreadId: () -> String = { "thread-1" },
        newThreadDraft: () -> NewThreadDraft? = { null },
        sendPrompt: suspend (String, NewThreadDraft?) -> Boolean = { _, _ -> true },
        resendPrompt: suspend (String, Int) -> Boolean = { _, _ -> true },
        onPromptAccepted: (Boolean) -> Unit = {}
    ): ComposerActionHandler {
        return ComposerActionHandler(
            composerSession = session,
            selectedThreadId = selectedThreadId,
            newThreadDraft = newThreadDraft,
            launch = { block -> runBlockingImmediate(block) },
            sendPrompt = sendPrompt,
            resendPrompt = resendPrompt,
            onPromptAccepted = onPromptAccepted
        )
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
