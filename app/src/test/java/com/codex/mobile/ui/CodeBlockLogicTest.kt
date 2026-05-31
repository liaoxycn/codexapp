package com.codex.mobile.ui

import com.codex.mobile.ui.message.buildCodeBlockPresentation
import com.codex.mobile.ui.message.codeBlockLabel
import com.codex.mobile.ui.message.collapsedCodeBlockHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlockLogicTest {
    @Test
    fun shellBlocksAlwaysCollapseAndUseOutputLabel() {
        val presentation = buildCodeBlockPresentation(
            language = "shell",
            value = "line1"
        )

        assertTrue(presentation.shouldCollapse)
        assertEquals("输出", presentation.label)
        assertEquals("点击展开查看输出", presentation.collapsedHint)
    }

    @Test
    fun diffBlocksUseDedicatedHint() {
        val presentation = buildCodeBlockPresentation(
            language = "diff",
            value = "+a\n-b"
        )

        assertTrue(presentation.shouldCollapse)
        assertEquals("diff", presentation.label)
        assertEquals("点击展开查看 diff", presentation.collapsedHint)
    }

    @Test
    fun shortRegularCodeBlocksStayExpanded() {
        val presentation = buildCodeBlockPresentation(
            language = "kotlin",
            value = "a\nb\nc"
        )

        assertFalse(presentation.shouldCollapse)
        assertEquals(3, presentation.lineCount)
    }

    @Test
    fun longRegularCodeBlocksCollapseWithLineCountHint() {
        val presentation = buildCodeBlockPresentation(
            language = "",
            value = (1..9).joinToString("\n") { "line$it" }
        )

        assertTrue(presentation.shouldCollapse)
        assertEquals("code", codeBlockLabel(language = ""))
        assertEquals("9 行内容，点击展开查看", collapsedCodeBlockHint(false, false, 9))
    }
}
