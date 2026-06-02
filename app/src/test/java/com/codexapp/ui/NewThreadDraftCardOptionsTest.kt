package com.codexapp.ui

import com.codexapp.model.GatewayConfigOption
import com.codexapp.model.GatewayConfigOptions
import com.codexapp.model.NewThreadDraft
import com.codexapp.ui.thread.buildConfigDraftOptions
import com.codexapp.ui.thread.buildModelDraftOptions
import com.codexapp.ui.thread.buildPermissionDraftOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NewThreadDraftCardOptionsTest {
    @Test
    fun modelOptionsKeepEveryGatewayModel() {
        val models = (1..8).map { index ->
            GatewayConfigOption(label = "Model $index", value = "model-$index")
        }

        val options = buildModelDraftOptions(NewThreadDraft(), models)

        assertEquals((1..8).map { "model-$it" }, options.map { it.value })
    }

    @Test
    fun emptyModelOptionsShowDisabledLoadingPlaceholder() {
        val options = buildModelDraftOptions(NewThreadDraft(), emptyList())

        assertEquals("读取中", options.single().label)
        assertEquals("", options.single().value)
        assertFalse(options.single().enabled)
    }

    @Test
    fun configOptionsDropBlankValuesOnly() {
        val options = buildConfigDraftOptions(
            listOf(
                GatewayConfigOption(label = "low", value = "low"),
                GatewayConfigOption(label = "blank", value = ""),
                GatewayConfigOption(label = "", value = "high")
            )
        )

        assertEquals(listOf("low", "high"), options.map { it.value })
        assertEquals(listOf("low", "high"), options.map { it.label })
    }

    @Test
    fun permissionOptionsOnlyExposeSupportedSandboxPresets() {
        val options = buildPermissionDraftOptions(
            GatewayConfigOptions(
                sandboxModes = listOf(
                    GatewayConfigOption(label = "workspace-write", value = "workspace-write"),
                    GatewayConfigOption(label = "danger-full-access", value = "danger-full-access")
                )
            )
        )

        assertEquals(
            listOf("默认权限", "自动审查", "完全访问权限"),
            options.map { it.label }
        )
        assertEquals(
            listOf("default", "auto-review", "full-access"),
            options.map { it.value }
        )
    }
}
