package com.codexapp.ui

import com.codexapp.model.StateDiagnostics
import com.codexapp.ui.drawer.shouldShowDiagnostics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerDiagnosticsTest {
    @Test
    fun hidesDiagnosticsForPlainIdleState() {
        assertFalse(shouldShowDiagnostics(StateDiagnostics(snapshotRevision = 4L), nowMillis = 10_000L))
    }

    @Test
    fun hidesDiagnosticsForOldSuccessfulAction() {
        assertFalse(
            shouldShowDiagnostics(
                StateDiagnostics(
                    actionType = "select_thread",
                    actionStatus = "succeeded",
                    actionFinishedAt = 1_000L
                ),
                nowMillis = 7_000L
            )
        )
    }

    @Test
    fun showsDiagnosticsForRunningOrPendingState() {
        assertTrue(shouldShowDiagnostics(StateDiagnostics(isGenerating = true), nowMillis = 10_000L))
        assertTrue(shouldShowDiagnostics(StateDiagnostics(runningThreadIds = listOf("thread-1")), nowMillis = 10_000L))
        assertTrue(shouldShowDiagnostics(StateDiagnostics(pendingSelectionThreadId = "thread-2"), nowMillis = 10_000L))
    }

    @Test
    fun showsDiagnosticsForImportantRecentActions() {
        assertTrue(
            shouldShowDiagnostics(
                StateDiagnostics(actionType = "select_thread", actionStatus = "succeeded", actionFinishedAt = 9_500L),
                nowMillis = 10_000L
            )
        )
        assertTrue(
            shouldShowDiagnostics(
                StateDiagnostics(actionType = "refresh_threads", actionStatus = "succeeded", actionFinishedAt = 9_500L),
                nowMillis = 10_000L
            )
        )
        assertTrue(shouldShowDiagnostics(StateDiagnostics(actionType = "unknown", actionStatus = "failed"), nowMillis = 10_000L))
    }
}
