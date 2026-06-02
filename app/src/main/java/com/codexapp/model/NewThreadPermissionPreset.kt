package com.codexapp.model

data class NewThreadPermissionPreset(
    val value: String,
    val label: String,
    val approvalPolicy: String,
    val approvalsReviewer: String,
    val sandboxMode: String
)

object NewThreadPermissionPresets {
    val Default = NewThreadPermissionPreset(
        value = "default",
        label = "默认权限",
        approvalPolicy = "on-request",
        approvalsReviewer = "user",
        sandboxMode = "workspace-write"
    )

    val AutoReview = NewThreadPermissionPreset(
        value = "auto-review",
        label = "自动审查",
        approvalPolicy = "on-failure",
        approvalsReviewer = "auto_review",
        sandboxMode = "workspace-write"
    )

    val FullAccess = NewThreadPermissionPreset(
        value = "full-access",
        label = "完全访问权限",
        approvalPolicy = "never",
        approvalsReviewer = "user",
        sandboxMode = "danger-full-access"
    )

    val all: List<NewThreadPermissionPreset> = listOf(
        Default,
        AutoReview,
        FullAccess
    )
}

fun newThreadPermissionPreset(value: String): NewThreadPermissionPreset {
    return NewThreadPermissionPresets.all.firstOrNull { it.value == value }
        ?: NewThreadPermissionPresets.FullAccess
}

fun resolveSupportedNewThreadPermissionMode(
    requested: String,
    availableSandboxModes: Collection<String>
): String {
    val normalizedSandboxModes = availableSandboxModes
        .map(String::trim)
        .filter(String::isNotBlank)
        .toSet()
    val requestedPreset = newThreadPermissionPreset(requested)
    if (normalizedSandboxModes.isEmpty() || normalizedSandboxModes.contains(requestedPreset.sandboxMode)) {
        return requestedPreset.value
    }
    return NewThreadPermissionPresets.all.firstOrNull { preset ->
        normalizedSandboxModes.contains(preset.sandboxMode)
    }?.value ?: requestedPreset.value
}
