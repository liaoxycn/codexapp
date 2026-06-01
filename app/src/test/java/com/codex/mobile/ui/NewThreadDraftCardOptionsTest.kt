package com.codex.mobile.ui

import com.codex.mobile.model.GatewayConfigOption
import com.codex.mobile.model.NewThreadDraft
import com.codex.mobile.ui.thread.buildConfigDraftOptions
import com.codex.mobile.ui.thread.buildModelDraftOptions
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
}
