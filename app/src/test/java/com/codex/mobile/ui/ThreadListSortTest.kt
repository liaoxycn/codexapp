package com.codex.mobile.ui

import com.codex.mobile.model.ThreadGroupKind
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadListSortTest {
    @Test
    fun sortsByUpdatedAtDescending() {
        val threads = listOf(
            summary("a", 1_000L),
            summary("b", 3_000L),
            summary("c", 2_000L)
        )

        val sorted = threads.sortedWith(threadListSortOrder())

        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun groupsByLatestThreadTime() {
        val threads = listOf(
            summary("p1-old", 1_000L, ThreadGroupKind.PROJECT, "项目A"),
            summary("chat-1", 6_000L, ThreadGroupKind.CHAT, "普通会话"),
            summary("p2-new", 9_000L, ThreadGroupKind.PROJECT, "项目B")
        )

        val grouped = threads
            .sortedWith(threadListSortOrder())
            .groupBy {
                if (it.groupKind == ThreadGroupKind.PROJECT && it.groupLabel.isNotBlank()) it.groupLabel else "普通会话"
            }
            .keys
            .sortedWith(
                compareByDescending<String> { groupLabel ->
                    threads
                        .filter { thread ->
                            if (groupLabel == "普通会话") {
                                thread.groupKind != ThreadGroupKind.PROJECT || thread.groupLabel.isBlank()
                            } else {
                                thread.groupKind == ThreadGroupKind.PROJECT && thread.groupLabel == groupLabel
                            }
                        }
                        .maxOfOrNull { it.updatedAt } ?: 0L
                }.thenBy { groupLabel -> if (groupLabel == "普通会话") 1 else 0 }
                    .thenBy { it }
            )

        assertEquals(listOf("项目B", "普通会话", "项目A"), grouped)
    }

    @Test
    fun formatsRelativeUpdatedTimeForRecentItems() {
        assertEquals("刚刚", formatThreadUpdatedAt(1_000L, nowMillis = 1_000L))
        assertEquals("5分前", formatThreadUpdatedAt(1_000L, nowMillis = 301_000L))
        assertEquals("2小时前", formatThreadUpdatedAt(1_000L, nowMillis = 7_201_000L))
    }

    private fun summary(
        id: String,
        updatedAt: Long,
        groupKind: ThreadGroupKind = ThreadGroupKind.CHAT,
        groupLabel: String = "普通会话"
    ) = ThreadSummary(
        id = id,
        title = id,
        preview = "preview",
        status = ThreadStatus.IDLE,
        updatedAt = updatedAt,
        groupKind = groupKind,
        groupLabel = groupLabel
    )
}
