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
        cwd: String = ""
    ) = ThreadSummary(
        id = id,
        title = title,
        preview = preview,
        status = ThreadStatus.IDLE,
        updatedAt = updatedAt,
        groupKind = groupKind,
        groupLabel = groupLabel,
        cwd = cwd
    )
}
