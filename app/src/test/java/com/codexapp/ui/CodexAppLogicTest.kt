package com.codexapp.ui

import com.codexapp.model.ThreadStatus
import com.codexapp.model.ThreadSummary
import com.codexapp.ui.app.resolveSelectedThreadChrome
import org.junit.Assert.assertEquals
import org.junit.Test

class CodexAppLogicTest {
    @Test
    fun returnsIdleChromeForBlankSelection() {
        val chrome = resolveSelectedThreadChrome(
            threads = emptyList(),
            selectedThreadId = ""
        )

        assertEquals("Codex", chrome.title)
        assertEquals(ThreadStatus.IDLE, chrome.status)
    }

    @Test
    fun returnsSelectedThreadTitleAndStatus() {
        val chrome = resolveSelectedThreadChrome(
            threads = listOf(
                ThreadSummary(
                    id = "thread-1",
                    title = "项目重构",
                    preview = "",
                    updatedAt = 1L,
                    status = ThreadStatus.RUNNING,
                    cwd = "",
                    groupKind = com.codexapp.model.ThreadGroupKind.CHAT,
                    groupLabel = "普通会话",
                    archived = false
                )
            ),
            selectedThreadId = "thread-1"
        )

        assertEquals("项目重构", chrome.title)
        assertEquals(ThreadStatus.RUNNING, chrome.status)
    }
}
