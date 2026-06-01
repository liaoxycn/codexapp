package com.codex.mobile.model

data class ThreadSummary(
    val id: String,
    val title: String,
    val preview: String,
    val status: ThreadStatus,
    val updatedAt: Long = 0L,
    val groupKind: ThreadGroupKind = ThreadGroupKind.CHAT,
    val groupLabel: String = "普通会话",
    val cwd: String = "",
    val archived: Boolean = false,
    val gitBranch: String = "",
    val gitSha: String = ""
)

enum class ThreadGroupKind {
    PROJECT,
    CHAT
}

enum class ThreadStatus {
    RUNNING,
    IDLE,
    NEEDS_APPROVAL,
    FAILED
}
