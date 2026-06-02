package com.codexapp.ui

import com.codexapp.model.ConnectionStatus
import com.codexapp.model.ComposerFile
import com.codexapp.ui.composer.ComposerPanel
import com.codexapp.ui.composer.buildComposerFileTree
import com.codexapp.ui.composer.composerFileDisplay
import com.codexapp.ui.composer.composerFileMention
import com.codexapp.ui.composer.composerPlaceholder
import com.codexapp.ui.composer.extractTrailingComposerToken
import com.codexapp.ui.composer.filterComposerFiles
import com.codexapp.ui.composer.isExcludedComposerFile
import com.codexapp.ui.composer.filterSlashCommands
import com.codexapp.ui.composer.routeComposerInput
import com.codexapp.ui.composer.shouldShowSlashPanel
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
            "/rollback  回滚上轮",
            "! ls  运行 shell 命令"
        )

        assertEquals(listOf("/rollback  回滚上轮"), filterSlashCommands(commands, "rollback"))
        assertEquals(listOf("! ls  运行 shell 命令"), filterSlashCommands(commands, "shell"))
    }

    @Test
    fun filtersComposerFilesByLabelOrPath() {
        val files = listOf(
            ComposerFile("src/CodexApp.kt", "D:/Projects/App/src/CodexApp.kt"),
            ComposerFile("README.md", "D:/Projects/App/README.md")
        )

        assertEquals(listOf(files.first()), filterComposerFiles(files, "codex"))
        assertEquals(listOf(files.last()), filterComposerFiles(files, "readme"))
    }

    @Test
    fun displaysComposerFilesAsTreeWithRootSlash() {
        val files = listOf(
            ComposerFile("src/ui/FilePickerPanel.kt", "D:/Projects/home/codexapp/src/ui/FilePickerPanel.kt"),
            ComposerFile("README.md", "D:/Projects/home/codexapp/README.md"),
            ComposerFile("docs/MOBILE_GATEWAY_FLOW_PROGRESS.md", "D:/Projects/home/codexapp/docs/MOBILE_GATEWAY_FLOW_PROGRESS.md")
        )

        val tree = buildComposerFileTree(files)

        val root = tree.single() as com.codexapp.ui.composer.ComposerFileTreeNode.Directory
        assertEquals("/", root.name)
        assertEquals(listOf("docs", "src"), root.children.filterIsInstance<com.codexapp.ui.composer.ComposerFileTreeNode.Directory>().map { it.name })
        assertEquals(listOf("README.md"), root.children.filterIsInstance<com.codexapp.ui.composer.ComposerFileTreeNode.File>().map { it.display.name })
    }

    @Test
    fun displaysComposerFilesRelativeToProjectCwdAsTree() {
        val tree = buildComposerFileTree(
            files = listOf(
                ComposerFile(
                    label = "D:/Projects/home/codexapp/app/src/Main.kt",
                    path = "D:/Projects/home/codexapp/app/src/Main.kt"
                )
            ),
            projectCwd = "D:/Projects/home/codexapp"
        )

        val root = tree.single() as com.codexapp.ui.composer.ComposerFileTreeNode.Directory
        val appDir = root.children.filterIsInstance<com.codexapp.ui.composer.ComposerFileTreeNode.Directory>().first { it.name == "app" }
        val srcDir = appDir.children.filterIsInstance<com.codexapp.ui.composer.ComposerFileTreeNode.Directory>().first { it.name == "src" }
        val file = srcDir.children.filterIsInstance<com.codexapp.ui.composer.ComposerFileTreeNode.File>().single().display

        assertEquals("Main.kt", file.name)
        assertEquals("app/src", file.directory)
        assertEquals(2, file.depth)
    }

    @Test
    fun fallsBackToRelativeDisplayForAbsolutePathLabels() {
        val display = composerFileDisplay(
            ComposerFile(
                label = "D:\\Projects\\home\\codexapp\\app\\src\\Main.kt",
                path = "D:\\Projects\\home\\codexapp\\app\\src\\Main.kt"
            )
        )

        assertEquals("Main.kt", display.name)
        assertEquals("app/src", display.directory)
        assertEquals(2, display.depth)
    }

    @Test
    fun excludesKnownIgnoredFilesFromPicker() {
        assertTrue(
            isExcludedComposerFile(
                ComposerFile(".git/config", "D:/Projects/home/codexapp/.git/config")
            )
        )
        assertTrue(
            isExcludedComposerFile(
                ComposerFile("node_modules/pkg/index.js", "D:/Projects/home/codexapp/node_modules/pkg/index.js")
            )
        )
        assertFalse(
            isExcludedComposerFile(
                ComposerFile("src/Main.kt", "D:/Projects/home/codexapp/src/Main.kt")
            )
        )
    }

    @Test
    fun buildsFileMentionText() {
        assertEquals("@{D:/Projects/App/src/Main.kt}", composerFileMention(" D:/Projects/App/src/Main.kt "))
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
