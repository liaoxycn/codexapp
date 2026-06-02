package com.codexapp.model

data class ComposerChip(
    val label: String,
    val icon: ComposerChipIcon,
    val path: String? = null
)

data class ComposerFile(
    val label: String,
    val path: String
)

enum class ComposerChipIcon {
    FILE,
    CONTEXT
}
