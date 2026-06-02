package com.codexapp.ui

import com.codexapp.model.MessageBlock
import com.codexapp.ui.message.deriveAssistantMessageCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMessageCardsTest {
    @Test
    fun deriveAssistantMessageCardsCollectsFileAndCommandCards() {
        val cards = deriveAssistantMessageCards(
            listOf(
                MessageBlock.FileChangeSummary("已编辑 1 个文件"),
                MessageBlock.FileChangeMeta("已编辑 Main.kt", "app/src/Main.kt"),
                MessageBlock.FileChangeDiff("+new"),
                MessageBlock.CommandSummary("执行命令"),
                MessageBlock.CommandMeta("cwd: D:/Projects/home/codexapp"),
                MessageBlock.Code(language = "shell", value = "ok")
            )
        )

        assertTrue(cards.hasFileChangeCard)
        assertTrue(cards.hasCommandCard)
        assertEquals("已编辑 1 个文件", cards.fileChangeSummary)
        assertEquals("已编辑 Main.kt", cards.fileChangeEntries.single().label)
        assertEquals("cwd: D:/Projects/home/codexapp", cards.commandMetaLines.single())
        assertNotNull(cards.commandOutput)
    }

    @Test
    fun deriveAssistantMessageCardsOmitsBlankCommandMeta() {
        val cards = deriveAssistantMessageCards(
            listOf(
                MessageBlock.CommandMeta("   "),
                MessageBlock.Text("hello")
            )
        )

        assertEquals(emptyList<String>(), cards.commandMetaLines)
        assertFalse(cards.hasFileChangeCard)
        assertFalse(cards.hasCommandCard)
    }
}
