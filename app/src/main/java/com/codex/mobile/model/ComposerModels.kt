package com.codex.mobile.model

data class ComposerChip(
    val label: String,
    val icon: ComposerChipIcon,
    val path: String? = null
)

enum class ComposerChipIcon {
    FILE,
    CONTEXT
}
