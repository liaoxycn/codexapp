package com.codex.mobile.model

data class AppUpdateState(
    val status: AppUpdateStatus = AppUpdateStatus.IDLE,
    val latestVersion: String = "",
    val localVersion: String = "",
    val assetName: String = "",
    val downloadUrl: String = "",
    val downloadedApkPath: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String = ""
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

enum class AppUpdateStatus {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    INSTALL_PERMISSION_REQUIRED,
    UP_TO_DATE,
    ERROR
}
