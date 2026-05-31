package com.codex.mobile.ui.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.codex.mobile.model.ThreadSummary

internal data class DrawerSectionsState(
    val query: String,
    val sections: DrawerThreadSections,
    val onQueryChange: (String) -> Unit,
    val onToggleProjectGroup: (DrawerProjectGroup) -> Unit,
)

@Composable
internal fun rememberDrawerSectionsState(
    threads: List<ThreadSummary>,
    selectedThreadId: String,
): DrawerSectionsState {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedProjectGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    var discoveredProjectGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    val sections = remember(threads, selectedThreadId, query, expandedProjectGroups) {
        buildDrawerThreadSections(
            threads = threads,
            selectedThreadId = selectedThreadId,
            query = query,
            expandedProjectGroups = expandedProjectGroups
        )
    }

    LaunchedEffect(sections.projectGroups.map { it.label }) {
        val newGroups = newlyDiscoveredProjectGroups(
            knownGroups = discoveredProjectGroups,
            orderedGroups = sections.projectGroups.map { it.label }
        )
        if (newGroups.isNotEmpty()) {
            expandedProjectGroups = expandedProjectGroups + newGroups
            discoveredProjectGroups = discoveredProjectGroups + newGroups
        }
    }

    return DrawerSectionsState(
        query = query,
        sections = sections,
        onQueryChange = { query = it },
        onToggleProjectGroup = { group ->
            if (!group.isCurrentProject) {
                expandedProjectGroups = if (group.isExpanded) {
                    expandedProjectGroups - group.label
                } else {
                    expandedProjectGroups + group.label
                }
            }
        },
    )
}
