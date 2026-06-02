package com.codexapp.model

data class AppUpdateState(
    val status: AppUpdateStatus = AppUpdateStatus.IDLE,
    val latestVersion: String = "",
    val localVersion: String = "",
    val assetName: String = "",
    val downloadUrl: String = "",
    val releasePageUrl: String = "",
    val totalBytes: Long = 0L,
    val downloadId: Long = 0L,
    val message: String = ""
)

enum class AppUpdateStatus {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOAD_QUEUED,
    RELEASE_PAGE_OPENED,
    UP_TO_DATE,
    ERROR
}
