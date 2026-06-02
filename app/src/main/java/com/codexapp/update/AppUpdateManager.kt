package com.codexapp.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.codexapp.model.AppUpdateState
import com.codexapp.model.AppUpdateStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

internal class AppUpdateManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun consumeStartupCheck(): Boolean = AppUpdateStartupGate.consume()

    suspend fun checkLatest(): AppUpdateState = withContext(Dispatchers.IO) {
        val localVersion = context.localVersionName()
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppUpdateState(
                        status = AppUpdateStatus.ERROR,
                        localVersion = localVersion,
                        message = "检查更新失败：HTTP ${response.code}"
                    )
                }
                val body = response.body?.string().orEmpty()
                val release = json.decodeFromString(GitHubRelease.serializer(), body)
                val latestVersion = release.tagName.trim().removePrefix("v")
                val releasePageUrl = release.htmlUrl.ifBlank { LATEST_RELEASE_PAGE_URL }
                val releaseNotes = release.body.trim()
                val apkAsset = release.assets.firstOrNull { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true) && asset.browserDownloadUrl.isNotBlank()
                }
                when {
                    latestVersion.isBlank() -> AppUpdateState(
                        status = AppUpdateStatus.ERROR,
                        localVersion = localVersion,
                        message = "最新版本号为空"
                    )
                    compareVersions(latestVersion, localVersion) <= 0 -> AppUpdateState(
                        status = AppUpdateStatus.UP_TO_DATE,
                        latestVersion = latestVersion,
                        localVersion = localVersion,
                        releaseNotes = releaseNotes
                    )
                    apkAsset == null -> AppUpdateState(
                        status = AppUpdateStatus.ERROR,
                        latestVersion = latestVersion,
                        localVersion = localVersion,
                        releasePageUrl = releasePageUrl,
                        releaseNotes = releaseNotes,
                        message = "最新 release 未找到 APK"
                    )
                    else -> AppUpdateState(
                        status = AppUpdateStatus.AVAILABLE,
                        latestVersion = latestVersion,
                        localVersion = localVersion,
                        assetName = apkAsset.name,
                        downloadUrl = apkAsset.browserDownloadUrl,
                        releasePageUrl = releasePageUrl,
                        releaseNotes = releaseNotes,
                        totalBytes = apkAsset.size
                    )
                }
            }
        }.getOrElse { error ->
            AppUpdateState(
                status = AppUpdateStatus.ERROR,
                localVersion = localVersion,
                message = error.message ?: "检查更新失败"
            )
        }
    }

    fun enqueueSystemDownload(available: AppUpdateState): AppUpdateState {
        if (available.downloadUrl.isBlank()) {
            return openReleasePage(available, "下载地址为空")
        }
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: return openReleasePage(available, "系统下载器不可用")
        return runCatching {
            val safeName = safeApkName(available)
            val request = DownloadManager.Request(Uri.parse(available.downloadUrl))
                .setTitle("codexapp ${available.latestVersion}")
                .setDescription(safeName)
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val id = manager.enqueue(request)
            available.copy(
                status = AppUpdateStatus.DOWNLOAD_QUEUED,
                downloadId = id,
                message = "已交给系统下载器"
            )
        }.getOrElse { error ->
            openReleasePage(available, error.message ?: "启动系统下载失败")
        }
    }

    fun openReleasePage(
        state: AppUpdateState,
        reason: String = "已打开发布页"
    ): AppUpdateState {
        val pageUrl = state.releasePageUrl.ifBlank { LATEST_RELEASE_PAGE_URL }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            state.copy(
                status = AppUpdateStatus.RELEASE_PAGE_OPENED,
                releasePageUrl = pageUrl,
                message = reason
            )
        }.getOrElse { error ->
            state.copy(
                status = AppUpdateStatus.ERROR,
                releasePageUrl = pageUrl,
                message = error.message ?: "无法打开发布页"
            )
        }
    }

    private fun safeApkName(state: AppUpdateState): String {
        val name = state.assetName.ifBlank { "codexapp-${state.latestVersion}.apk" }
        return name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/liaoxycn/codexapp/releases/latest"
        const val LATEST_RELEASE_PAGE_URL = "https://github.com/liaoxycn/codexapp/releases/latest"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

internal object AppUpdateStartupGate {
    private val pending = AtomicBoolean(true)

    fun consume(): Boolean = pending.compareAndSet(true, false)

    fun resetForTest() {
        pending.set(true)
    }
}

private fun Context.localVersionName(): String {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }.orEmpty()
    }.getOrDefault("")
}

internal fun compareVersions(remote: String, local: String): Int {
    val remoteParts = remote.versionParts()
    val localParts = local.versionParts()
    val size = maxOf(remoteParts.size, localParts.size)
    for (index in 0 until size) {
        val left = remoteParts.getOrElse(index) { 0 }
        val right = localParts.getOrElse(index) { 0 }
        if (left != right) return left.compareTo(right)
    }
    return 0
}

private fun String.versionParts(): List<Int> {
    return trim()
        .removePrefix("v")
        .split('.', '-', '_')
        .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
    @SerialName("html_url")
    val htmlUrl: String = "",
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = ""
)
