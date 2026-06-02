package com.codexapp.ui

import com.codexapp.ui.composer.composerDetailsContentDescription
import com.codexapp.ui.composer.composerSendContentDescription
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerInputActionsTest {
    @Test
    fun composerDetailsContentDescriptionReflectsExpandedState() {
        assertEquals("收起输入工具", composerDetailsContentDescription(expanded = true))
        assertEquals("展开输入工具", composerDetailsContentDescription(expanded = false))
    }

    @Test
    fun composerSendContentDescriptionReflectsAvailability() {
        assertEquals("发送消息", composerSendContentDescription(sendEnabled = true))
        assertEquals("输入内容后发送", composerSendContentDescription(sendEnabled = false))
    }
}
