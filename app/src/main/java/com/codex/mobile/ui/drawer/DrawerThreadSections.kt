package com.codex.mobile.ui.drawer

import com.codex.mobile.model.ThreadGroupKind
import com.codex.mobile.model.ThreadSummary

internal data class DrawerProjectGroup(
    val label: String,
    val cwd: String,
    val threads: List<ThreadSummary>,
    val isCurrentProject: Boolean,
    val isExpanded: Boolean
)

internal data class DrawerThreadSections(
    val projectGroups: List<DrawerProjectGroup>,
    val chatThreads: List<ThreadSummary>
)

internal fun buildDrawerThreadSections(
    threads: List<ThreadSummary>,
    selectedThreadId: String,
    query: String,
    expandedProjectGroups: Set<String>
): DrawerThreadSections {
    val normalizedQuery = query.trim()
    val activeThreads = threads.filter { thread ->
        normalizedQuery.isBlank() ||
            thread.title.contains(normalizedQuery, ignoreCase = true) ||
            thread.preview.contains(normalizedQuery, ignoreCase = true)
    }
    val selectedThread = threads.firstOrNull { it.id == selectedThreadId }
    val currentProjectGroupLabel = selectedThread
        ?.takeIf { it.groupKind == ThreadGroupKind.PROJECT && it.groupLabel.isNotBlank() }
        ?.groupLabel
    val projectThreads = activeThreads.filter { it.groupKind == ThreadGroupKind.PROJECT && it.groupLabel.isNotBlank() }
    val chatThreads = activeThreads.filter { it.groupKind != ThreadGroupKind.PROJECT || it.groupLabel.isBlank() }
    val groupedProjectThreads = projectThreads
        .sortedWith(threadListSortOrder())
        .groupBy(ThreadSummary::groupLabel)
        .filterValues(List<ThreadSummary>::isNotEmpty)
    val orderedProjectGroups = orderProjectGroups(groupedProjectThreads)
    return DrawerThreadSections(
        projectGroups = orderedProjectGroups.map { label ->
            val groupedThreads = groupedProjectThreads[label].orEmpty()
            DrawerProjectGroup(
                label = label,
                cwd = groupedThreads.firstOrNull { it.cwd.isNotBlank() }?.cwd.orEmpty(),
                threads = groupedThreads,
                isCurrentProject = label == currentProjectGroupLabel,
                isExpanded = label == currentProjectGroupLabel || expandedProjectGroups.contains(label)
            )
        },
        chatThreads = chatThreads
    )
}

internal fun orderProjectGroups(
    groupedProjectThreads: Map<String, List<ThreadSummary>>
): List<String> {
    return groupedProjectThreads.keys.sortedWith(
        compareByDescending<String> { label ->
            groupedProjectThreads[label].orEmpty().maxOfOrNull(ThreadSummary::updatedAt) ?: 0L
        }.thenBy { it }
    )
}

internal fun newlyDiscoveredProjectGroups(
    knownGroups: Set<String>,
    orderedGroups: List<String>
): Set<String> {
    return orderedGroups.filterNot(knownGroups::contains).toSet()
}
