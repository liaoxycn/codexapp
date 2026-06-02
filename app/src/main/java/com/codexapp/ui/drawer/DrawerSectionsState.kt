package com.codexapp.ui.drawer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.codexapp.model.ThreadSummary

internal data class DrawerSectionsState(
    val sections: DrawerThreadSections,
    val onToggleProjectGroup: (DrawerProjectGroup) -> Unit,
)

@Composable
internal fun rememberDrawerSectionsState(
    threads: List<ThreadSummary>,
    selectedThreadId: String,
): DrawerSectionsState {
    var expandedProjectGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    var discoveredProjectGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    val sections = remember(threads, selectedThreadId, expandedProjectGroups) {
        buildDrawerThreadSections(
            threads = threads,
            selectedThreadId = selectedThreadId,
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
        sections = sections,
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
