package com.codex.mobile.ui

import com.codex.mobile.model.ThreadGroupKind
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary
import com.codex.mobile.ui.drawer.buildDrawerThreadSections
import com.codex.mobile.ui.drawer.newlyDiscoveredProjectGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerThreadSectionsTest {
    @Test
    fun filtersThreadsByQueryAcrossTitleAndPreview() {
        val sections = buildDrawerThreadSections(
            threads = listOf(
                summary("chat-1", title = "Alpha", preview = "hello"),
                summary("chat-2", title = "Beta", preview = "match preview"),
                summary("project-1", title = "Gamma", preview = "project body", groupKind = ThreadGroupKind.PROJECT, groupLabel = "项目A")
            ),
            selectedThreadId = "",
            query = "match",
            expandedProjectGroups = emptySet()
        )

        assertEquals(listOf("chat-2"), sections.chatThreads.map { it.id })
        assertTrue(sections.projectGroups.isEmpty())
        assertTrue(sections.archivedThreads.isEmpty())
    }

    @Test
    fun filtersThreadsByProjectPathAndGitMetadata() {
        val sections = buildDrawerThreadSections(
            threads = listOf(
                summary("chat", title = "Alpha"),
                summary(
                    "project-cwd",
                    title = "Beta",
                    groupKind = ThreadGroupKind.PROJECT,
                    groupLabel = "Home App",
                    cwd = "D:/Projects/home/codexapp"
                ),
                summary(
                    "project-git",
                    title = "Gamma",
                    groupKind = ThreadGroupKind.PROJECT,
                    groupLabel = "Mobile",
                    gitBranch = "feature/mobile-shell",
                    gitSha = "abc1234"
                )
            ),
            selectedThreadId = "",
            query = "mobile-shell",
            expandedProjectGroups = emptySet()
        )

        assertEquals(listOf("Mobile"), sections.projectGroups.map { it.label })
        assertEquals(listOf("project-git"), sections.projectGroups.single().threads.map { it.id })
        assertTrue(sections.chatThreads.isEmpty())
    }

    @Test
    fun filtersThreadsByProjectGroupAndCwd() {
        val groupSections = buildDrawerThreadSections(
            threads = listOf(
                summary("chat", title = "Alpha"),
                summary(
                    "project-label",
                    title = "Beta",
                    groupKind = ThreadGroupKind.PROJECT,
                    groupLabel = "Codex Desktop",
                    cwd = "D:/Projects/home/codexapp"
                )
            ),
            selectedThreadId = "",
            query = "desktop",
            expandedProjectGroups = emptySet()
        )
        val cwdSections = buildDrawerThreadSections(
            threads = listOf(
                summary("chat", title = "Alpha"),
                summary(
                    "project-cwd",
                    title = "Beta",
                    groupKind = ThreadGroupKind.PROJECT,
                    groupLabel = "Mobile",
                    cwd = "D:/Projects/home/codexapp"
                )
            ),
            selectedThreadId = "",
            query = "codexapp",
            expandedProjectGroups = emptySet()
        )

        assertEquals(listOf("project-label"), groupSections.projectGroups.single().threads.map { it.id })
        assertEquals(listOf("project-cwd"), cwdSections.projectGroups.single().threads.map { it.id })
    }

    @Test
    fun ordersProjectGroupsByLatestUpdatedAt() {
        val sections = buildDrawerThreadSections(
            threads = listOf(
                summary("p1-old", updatedAt = 1_000L, groupKind = ThreadGroupKind.PROJECT, groupLabel = "项目A"),
                summary("p2-new", updatedAt = 9_000L, groupKind = ThreadGroupKind.PROJECT, groupLabel = "项目B"),
                summary("p1-new", updatedAt = 5_000L, groupKind = ThreadGroupKind.PROJECT, groupLabel = "项目A")
            ),
            selectedThreadId = "",
            query = "",
            expandedProjectGroups = emptySet()
        )

        assertEquals(listOf("项目B", "项目A"), sections.projectGroups.map { it.label })
    }

    @Test
    fun searchPrioritizesProjectAndTitleMatchesBeforePreviewMatches() {
        val sections = buildDrawerThreadSections(
            threads = listOf(
                summary(
                    "current-preview-hit",
                    updatedAt = 9_000L,
                    title = "持续优化",
                    preview = "继续测试 md2html 抽屉搜索",
                    groupKind = ThreadGroupKind.PROJECT,
                    groupLabel = "codexapp"
                ),
                summary(
                    "target-project",
                    updatedAt = 1_000L,
                    title = "项目会话测试",
                    preview = "Hello",
                    groupKind = ThreadGroupKind.PROJECT,
                    groupLabel = "md2html",
                    cwd = "D:/Data/Documents/md2html"
                )
            ),
            selectedThreadId = "current-preview-hit",
            query = "md2html",
            expandedProjectGroups = emptySet()
        )

        assertEquals(listOf("md2html", "codexapp"), sections.projectGroups.map { it.label })
        assertEquals("target-project", sections.projectGroups.first().threads.single().id)
    }

    @Test
    fun searchPrioritizesTitleMatchesWithinChatThreads() {
        val sections = buildDrawerThreadSections(
            threads = listOf(
                summary("preview-hit", updatedAt = 9_000L, title = "Recent", preview = "mentions weather"),
                summary("title-hit", updatedAt = 1_000L, title = "Weather report", preview = "older")
            ),
            selectedThreadId = "",
            query = "weather",
            expandedProjectGroups = emptySet()
        )

        assertEquals(listOf("title-hit", "preview-hit"), sections.chatThreads.map { it.id })
    }

    @Test
    fun keepsCurrentProjectExpandedEvenWhenNotInExpandedSet() {
        val sections = buildDrawerThreadSections(
            threads = listOf(
                summary("project-1", updatedAt = 3_000L, groupKind = ThreadGroupKind.PROJECT, groupLabel = "项目A")
            ),
            selectedThreadId = "project-1",
            query = "",
            expandedProjectGroups = emptySet()
        )

        assertTrue(sections.projectGroups.single().isExpanded)
        assertTrue(sections.projectGroups.single().isCurrentProject)
    }

    @Test
    fun separatesArchivedThreadsFromActiveSections() {
        val sections = buildDrawerThreadSections(
            threads = listOf(
                summary("active-chat", updatedAt = 1_000L),
                summary("archived-chat", updatedAt = 2_000L, archived = true),
                summary("archived-project", updatedAt = 3_000L, groupKind = ThreadGroupKind.PROJECT, groupLabel = "项目A", archived = true)
            ),
            selectedThreadId = "",
            query = "",
            expandedProjectGroups = emptySet()
        )

        assertEquals(listOf("active-chat"), sections.chatThreads.map { it.id })
        assertTrue(sections.projectGroups.isEmpty())
        assertEquals(listOf("archived-project", "archived-chat"), sections.archivedThreads.map { it.id })
    }

    @Test
    fun findsOnlyNewProjectGroupsForAutoExpand() {
        val newGroups = newlyDiscoveredProjectGroups(
            knownGroups = setOf("项目A"),
            orderedGroups = listOf("项目A", "项目B", "项目C")
        )

        assertEquals(setOf("项目B", "项目C"), newGroups)
        assertFalse(newGroups.contains("项目A"))
    }

    private fun summary(
        id: String,
        updatedAt: Long = 1_000L,
        title: String = id,
        preview: String = "preview",
        groupKind: ThreadGroupKind = ThreadGroupKind.CHAT,
        groupLabel: String = "普通会话",
        cwd: String = "",
        archived: Boolean = false,
        gitBranch: String = "",
        gitSha: String = ""
    ) = ThreadSummary(
        id = id,
        title = title,
        preview = preview,
        status = ThreadStatus.IDLE,
        updatedAt = updatedAt,
        groupKind = groupKind,
        groupLabel = groupLabel,
        cwd = cwd,
        archived = archived,
        gitBranch = gitBranch,
        gitSha = gitSha
    )
}
