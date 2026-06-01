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
    val chatThreads: List<ThreadSummary>,
    val archivedThreads: List<ThreadSummary>
)

internal fun buildDrawerThreadSections(
    threads: List<ThreadSummary>,
    selectedThreadId: String,
    query: String,
    expandedProjectGroups: Set<String>
): DrawerThreadSections {
    val normalizedQuery = query.trim()
    val matchingThreads = threads.filter { thread ->
        thread.matchesDrawerQuery(normalizedQuery)
    }
    val activeThreads = matchingThreads.filterNot(ThreadSummary::archived)
    val listSortOrder = drawerThreadSortOrder(normalizedQuery)
    val archivedThreads = matchingThreads.filter(ThreadSummary::archived).sortedWith(listSortOrder)
    val selectedThread = threads.firstOrNull { it.id == selectedThreadId }
    val currentProjectGroupLabel = selectedThread
        ?.takeIf { it.groupKind == ThreadGroupKind.PROJECT && it.groupLabel.isNotBlank() }
        ?.groupLabel
    val projectThreads = activeThreads.filter { it.groupKind == ThreadGroupKind.PROJECT && it.groupLabel.isNotBlank() }
    val chatThreads = activeThreads
        .filter { it.groupKind != ThreadGroupKind.PROJECT || it.groupLabel.isBlank() }
        .sortedWith(listSortOrder)
    val groupedProjectThreads = projectThreads
        .sortedWith(listSortOrder)
        .groupBy(ThreadSummary::groupLabel)
        .filterValues(List<ThreadSummary>::isNotEmpty)
    val orderedProjectGroups = orderProjectGroups(groupedProjectThreads, normalizedQuery)
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
        chatThreads = chatThreads,
        archivedThreads = archivedThreads
    )
}

internal fun orderProjectGroups(
    groupedProjectThreads: Map<String, List<ThreadSummary>>,
    query: String = ""
): List<String> {
    return groupedProjectThreads.keys.sortedWith(
        compareByDescending<String> { label ->
            groupedProjectThreads[label].orEmpty().maxOfOrNull { it.drawerQueryRank(query) } ?: 0
        }.thenByDescending { label ->
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

private fun ThreadSummary.matchesDrawerQuery(query: String): Boolean {
    return query.isBlank() ||
        listOf(title, preview, groupLabel, cwd, gitBranch, gitSha)
            .any { it.contains(query, ignoreCase = true) }
}

private fun drawerThreadSortOrder(query: String): Comparator<ThreadSummary> {
    if (query.isBlank()) {
        return threadListSortOrder()
    }
    return compareByDescending<ThreadSummary> { it.drawerQueryRank(query) }
        .thenByDescending { it.updatedAt }
        .thenByDescending { it.id }
}

private fun ThreadSummary.drawerQueryRank(query: String): Int {
    if (query.isBlank()) {
        return 0
    }
    return when {
        title.contains(query, ignoreCase = true) -> 6
        groupLabel.contains(query, ignoreCase = true) -> 5
        cwd.contains(query, ignoreCase = true) -> 4
        gitBranch.contains(query, ignoreCase = true) -> 3
        gitSha.contains(query, ignoreCase = true) -> 2
        preview.contains(query, ignoreCase = true) -> 1
        else -> 0
    }
}
