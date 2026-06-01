package com.codex.mobile.update

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.codex.mobile.model.AppUpdateState
import com.codex.mobile.model.AppUpdateStatus
import java.io.File
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
                        localVersion = localVersion
                    )
                    apkAsset == null -> AppUpdateState(
                        status = AppUpdateStatus.ERROR,
                        latestVersion = latestVersion,
                        localVersion = localVersion,
                        message = "最新 release 未找到 APK"
                    )
                    else -> AppUpdateState(
                        status = AppUpdateStatus.AVAILABLE,
                        latestVersion = latestVersion,
                        localVersion = localVersion,
                        assetName = apkAsset.name,
                        downloadUrl = apkAsset.browserDownloadUrl,
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

    suspend fun download(
        available: AppUpdateState,
        onProgress: (AppUpdateState) -> Unit
    ): AppUpdateState = withContext(Dispatchers.IO) {
        if (available.downloadUrl.isBlank()) {
            return@withContext available.copy(status = AppUpdateStatus.ERROR, message = "下载地址为空")
        }
        val started = available.copy(status = AppUpdateStatus.DOWNLOADING, downloadedBytes = 0L)
        onProgress(started)
        runCatching {
            val request = Request.Builder().url(available.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext available.copy(
                        status = AppUpdateStatus.ERROR,
                        message = "下载失败：HTTP ${response.code}"
                    )
                }
                val body = response.body ?: return@withContext available.copy(
                    status = AppUpdateStatus.ERROR,
                    message = "下载内容为空"
                )
                val total = body.contentLength().takeIf { it > 0L } ?: available.totalBytes
                val apkFile = File(context.getExternalFilesDir("updates") ?: context.cacheDir, safeApkName(available))
                apkFile.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(
                                available.copy(
                                    status = AppUpdateStatus.DOWNLOADING,
                                    downloadedBytes = downloaded,
                                    totalBytes = total
                                )
                            )
                        }
                    }
                }
                available.copy(
                    status = AppUpdateStatus.READY_TO_INSTALL,
                    downloadedApkPath = apkFile.absolutePath,
                    downloadedBytes = apkFile.length(),
                    totalBytes = total
                )
            }
        }.getOrElse { error ->
            available.copy(status = AppUpdateStatus.ERROR, message = error.message ?: "下载失败")
        }
    }

    fun install(state: AppUpdateState): AppUpdateState {
        val apkFile = File(state.downloadedApkPath)
        if (!apkFile.exists()) {
            return state.copy(status = AppUpdateStatus.ERROR, message = "APK 文件不存在")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return state.copy(
                status = AppUpdateStatus.INSTALL_PERMISSION_REQUIRED,
                message = "请允许安装未知应用后再次点击安装"
            )
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(intent)
            state
        } catch (error: ActivityNotFoundException) {
            state.copy(status = AppUpdateStatus.ERROR, message = "未找到系统安装器")
        }
    }

    private fun safeApkName(state: AppUpdateState): String {
        val name = state.assetName.ifBlank { "codex-mobile-${state.latestVersion}.apk" }
        return name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/liaoxycn/CodexMobileApp/releases/latest"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
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
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = ""
)
