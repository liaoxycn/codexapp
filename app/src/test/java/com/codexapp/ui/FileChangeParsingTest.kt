package com.codexapp.ui

import androidx.compose.ui.graphics.Color
import com.codexapp.model.MessageBlock
import com.codexapp.ui.message.FileChangeEntry
import com.codexapp.ui.message.buildDiffAnnotatedString
import com.codexapp.ui.message.buildFileChangeEntries
import com.codexapp.ui.message.diffLineColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileChangeParsingTest {
    @Test
    fun buildFileChangeEntriesPairsMetaWithNextDiff() {
        val entries = buildFileChangeEntries(
            listOf(
                MessageBlock.FileChangeMeta("已编辑 Main.kt", "app/src/Main.kt"),
                MessageBlock.FileChangeDiff("@@\n-old\n+new")
            )
        )

        assertEquals(1, entries.size)
        assertEquals("已编辑 Main.kt", entries.single().label)
        assertEquals("app/src/Main.kt", entries.single().path)
        assertEquals("@@\n-old\n+new", entries.single().diff)
    }

    @Test
    fun buildFileChangeEntriesCreatesFallbackEntryForOrphanDiff() {
        val entries = buildFileChangeEntries(
            listOf(MessageBlock.FileChangeDiff("+new"))
        )

        assertEquals(1, entries.size)
        assertEquals("文件 diff", entries.single().label)
        assertEquals("", entries.single().path)
        assertEquals("+new", entries.single().diff)
    }

    @Test
    fun buildFileChangeEntriesSkipsBlankMetaAndDiff() {
        val entries = buildFileChangeEntries(
            listOf(
                MessageBlock.FileChangeMeta("   ", "app/src/Main.kt"),
                MessageBlock.FileChangeDiff("   ")
            )
        )

        assertEquals(emptyList<FileChangeEntry>(), entries)
    }

    @Test
    fun diffLineColorMatchesExpectedSemanticColors() {
        assertEquals(Color(0xFF93C5FD), diffLineColor("@@ section"))
        assertEquals(Color(0xFF9CA3AF), diffLineColor("diff --git a b"))
        assertEquals(Color(0xFF86EFAC), diffLineColor("+added"))
        assertEquals(Color(0xFFFCA5A5), diffLineColor("-removed"))
        assertEquals(Color(0xFFE5E7EB), diffLineColor(" context"))
    }

    @Test
    fun buildDiffAnnotatedStringPreservesLineOrder() {
        val diff = "diff --git a b\n-old\n+new"
        val annotated = buildDiffAnnotatedString(diff)

        assertEquals(diff, annotated.text)
        assertEquals(3, annotated.spanStyles.size)
        assertNull(annotated.paragraphStyles.firstOrNull())
    }
}
