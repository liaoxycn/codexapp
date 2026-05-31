package com.codex.mobile.model

data class ComposerChip(
    val label: String,
    val icon: ComposerChipIcon
)

enum class ComposerChipIcon {
    FILE,
    CONTEXT
}
