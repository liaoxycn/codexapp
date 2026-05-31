package com.codex.mobile.ui

import com.codex.mobile.ui.composer.composerDetailsContentDescription
import com.codex.mobile.ui.composer.composerSendContentDescription
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
