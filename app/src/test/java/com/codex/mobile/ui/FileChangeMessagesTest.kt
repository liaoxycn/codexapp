package com.codex.mobile.ui

import com.codex.mobile.ui.message.fileChangeDiffContentDescription
import com.codex.mobile.ui.message.fileChangeExpansionKey
import org.junit.Assert.assertEquals
import org.junit.Test

class FileChangeMessagesTest {
    @Test
    fun fileChangeExpansionKeyUsesStableMessageAndIndex() {
        assertEquals(
            "msg-1:fileChange:3",
            fileChangeExpansionKey(messageId = "msg-1", index = 3)
        )
    }

    @Test
    fun fileChangeDiffContentDescriptionReflectsExpandedState() {
        assertEquals(
            "展开 src/Main.kt diff",
            fileChangeDiffContentDescription(expanded = false, label = "src/Main.kt")
        )
        assertEquals(
            "收起 src/Main.kt diff",
            fileChangeDiffContentDescription(expanded = true, label = "src/Main.kt")
        )
    }
}
