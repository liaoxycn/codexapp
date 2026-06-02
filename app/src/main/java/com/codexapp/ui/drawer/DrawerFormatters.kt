package com.codexapp.ui.drawer

import com.codexapp.model.ThreadSummary

internal fun threadListSortOrder(): Comparator<ThreadSummary> {
    return compareByDescending<ThreadSummary> { it.updatedAt }
        .thenByDescending { it.id }
}

internal fun formatThreadUpdatedAt(
    updatedAt: Long,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val deltaMinutes = ((nowMillis - updatedAt).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        deltaMinutes <= 0 -> "刚刚"
        deltaMinutes < 60 -> "${deltaMinutes}分前"
        deltaMinutes < 24 * 60 -> "${deltaMinutes / 60}小时前"
        deltaMinutes < 48 * 60 -> "昨天"
        deltaMinutes < 7 * 24 * 60 -> "${deltaMinutes / (24 * 60)}天前"
        else -> {
            val days = deltaMinutes / (24 * 60)
            "${days / 7}周前"
        }
    }
}
