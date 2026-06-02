package com.codexapp.ui.drawer

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal fun groupHeaderDescription(
    label: String,
    expanded: Boolean?,
    canToggle: Boolean
): String {
    return when {
        expanded == true && !canToggle -> "当前项目：$label，已展开"
        expanded == true -> "收起项目：$label"
        expanded == false -> "展开项目：$label"
        else -> "项目：$label"
    }
}

internal fun groupHeaderCreateThreadDescription(label: String): String {
    return "在 $label 中开始新会话"
}

internal data class GroupHeaderTitleStyle(
    val fontSize: TextUnit,
    val lineHeight: TextUnit
)

internal fun groupHeaderTitleStyle(
    compact: Boolean,
    hasSecondaryText: Boolean
): GroupHeaderTitleStyle {
    return when {
        hasSecondaryText -> GroupHeaderTitleStyle(fontSize = 14.sp, lineHeight = 17.sp)
        compact -> GroupHeaderTitleStyle(fontSize = 12.sp, lineHeight = 15.sp)
        else -> GroupHeaderTitleStyle(fontSize = 13.sp, lineHeight = 16.sp)
    }
}
