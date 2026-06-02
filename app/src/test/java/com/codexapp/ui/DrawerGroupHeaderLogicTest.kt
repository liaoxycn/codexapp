package com.codexapp.ui

import com.codexapp.ui.drawer.groupHeaderCreateThreadDescription
import com.codexapp.ui.drawer.groupHeaderDescription
import com.codexapp.ui.drawer.groupHeaderTitleStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerGroupHeaderLogicTest {
    @Test
    fun groupHeaderDescriptionReflectsExpandedAndToggleState() {
        assertEquals(
            "当前项目：codexapp，已展开",
            groupHeaderDescription(label = "codexapp", expanded = true, canToggle = false)
        )
        assertEquals(
            "收起项目：codexapp",
            groupHeaderDescription(label = "codexapp", expanded = true, canToggle = true)
        )
        assertEquals(
            "展开项目：codexapp",
            groupHeaderDescription(label = "codexapp", expanded = false, canToggle = true)
        )
        assertEquals(
            "项目：codexapp",
            groupHeaderDescription(label = "codexapp", expanded = null, canToggle = false)
        )
    }

    @Test
    fun groupHeaderCreateThreadDescriptionIncludesProjectName() {
        assertEquals(
            "在 codexapp 中开始新会话",
            groupHeaderCreateThreadDescription("codexapp")
        )
    }

    @Test
    fun groupHeaderTitleStyleMatchesCompactAndSecondaryStates() {
        assertEquals(14f, groupHeaderTitleStyle(compact = false, hasSecondaryText = true).fontSize.value)
        assertEquals(12f, groupHeaderTitleStyle(compact = true, hasSecondaryText = false).fontSize.value)
        assertEquals(13f, groupHeaderTitleStyle(compact = false, hasSecondaryText = false).fontSize.value)
    }
}
