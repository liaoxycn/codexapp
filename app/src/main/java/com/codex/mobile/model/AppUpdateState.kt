package com.codex.mobile.model

data class AppUpdateState(
    val status: AppUpdateStatus = AppUpdateStatus.IDLE,
    val latestVersion: String = "",
    val localVersion: String = "",
    val assetName: String = "",
    val downloadUrl: String = "",
    val totalBytes: Long = 0L,
    val downloadId: Long = 0L,
    val message: String = ""
)

enum class AppUpdateStatus {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOAD_QUEUED,
    UP_TO_DATE,
    ERROR
}
