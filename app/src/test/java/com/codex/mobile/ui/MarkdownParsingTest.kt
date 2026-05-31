package com.codex.mobile.ui

import com.codex.mobile.ui.message.MarkdownLineKind
import com.codex.mobile.ui.message.parseMarkdownLines
import com.codex.mobile.ui.message.renderInlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParsingTest {
    @Test
    fun parsesHeadingsListsQuotesAndCodeBlocks() {
        val lines = parseMarkdownLines(
            """
            # Title
            - item
            > quote
            ```kotlin
            val x = 1
            ```
            """.trimIndent()
        )

        assertEquals(
            listOf(
                MarkdownLineKind.HEADING,
                MarkdownLineKind.LIST_ITEM,
                MarkdownLineKind.QUOTE,
                MarkdownLineKind.CODE,
                MarkdownLineKind.CODE,
                MarkdownLineKind.CODE
            ),
            lines.map { it.kind }
        )
        assertEquals("Title", lines.first().text)
    }

    @Test
    fun rendersInlineMarkdownWithoutControlMarkers() {
        val rendered = renderInlineMarkdown("**bold** and `code` [link](https://example.com)")

        assertEquals("bold and code link", rendered.text)
        assertTrue(rendered.spanStyles.isNotEmpty())
    }
}
