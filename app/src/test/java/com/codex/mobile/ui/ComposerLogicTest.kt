package com.codex.mobile.ui

import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.ui.composer.ComposerPanel
import com.codex.mobile.ui.composer.composerPlaceholder
import com.codex.mobile.ui.composer.extractTrailingComposerToken
import com.codex.mobile.ui.composer.filterSlashCommands
import com.codex.mobile.ui.composer.routeComposerInput
import com.codex.mobile.ui.composer.shouldShowSlashPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerLogicTest {
    @Test
    fun extractsTrailingTokenFromComposerText() {
        assertEquals("/compact", extractTrailingComposerToken("hello /compact"))
        assertEquals("!ls", extractTrailingComposerToken("run\n!ls"))
        assertEquals("", extractTrailingComposerToken("   "))
    }

    @Test
    fun showsSlashPanelOnlyWhenApplicable() {
        assertTrue(
            shouldShowSlashPanel(
                slashCommands = listOf("/compact  压缩上下文"),
                activePanel = ComposerPanel.NONE,
                suppressInlineSlashPanel = false,
                trailingToken = "/compact"
            )
        )
        assertFalse(
            shouldShowSlashPanel(
                slashCommands = emptyList(),
                activePanel = ComposerPanel.SLASH,
                suppressInlineSlashPanel = false,
                trailingToken = "/compact"
            )
        )
    }

    @Test
    fun filtersSlashCommandsByKeywordAndLabel() {
        val commands = listOf(
            "/compact  压缩上下文",
            "/goal  设置目标",
            "! ls  运行 shell 命令"
        )

        assertEquals(listOf("/goal  设置目标"), filterSlashCommands(commands, "goal"))
        assertEquals(listOf("! ls  运行 shell 命令"), filterSlashCommands(commands, "shell"))
    }

    @Test
    fun routesComposerInputToSlashPanelOnlyForSlashLikeTokens() {
        val slashRouting = routeComposerInput(ComposerPanel.NONE, "hello /compact")
        assertEquals(ComposerPanel.SLASH, slashRouting.nextPanel)
        assertEquals("/compact", slashRouting.nextSlashQuery)

        val plainRouting = routeComposerInput(ComposerPanel.SLASH, "hello world")
        assertEquals(ComposerPanel.NONE, plainRouting.nextPanel)
        assertEquals("", plainRouting.nextSlashQuery)
    }

    @Test
    fun resolvesComposerPlaceholderFromConnectionState() {
        assertEquals("回复 Codex", composerPlaceholder(true, ConnectionStatus.CONNECTED))
        assertEquals("正在连接…", composerPlaceholder(true, ConnectionStatus.CONNECTING))
        assertEquals("未连接", composerPlaceholder(true, ConnectionStatus.ERROR))
        assertEquals("正在切换会话…", composerPlaceholder(false, ConnectionStatus.CONNECTED))
    }
}
