package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val latestVersion: String,
    val updateMessage: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

data class DownloadProgress(
    val percent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long
)

enum class ApkInstallResult {
    LAUNCHED_INSTALLER,
    NEEDS_UNKNOWN_SOURCES_PERMISSION,
    FAILED
}

object AppUpdateChecker {
    private const val STATUS_URL = "https://k9rde.github.io/GlassWave/status.json"
    private const val FALLBACK_STATUS_URL = "https://raw.githubusercontent.com/k9rde/GlassWave/main/docs/status.json"
    private const val DEFAULT_APK_URL = "https://k9rde.github.io/GlassWave/glasswave.apk"
    private const val FALLBACK_APK_URL = "https://github.com/k9rde/GlassWave/raw/main/docs/glasswave.apk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun fetchStatusJson(url: String): AppUpdateInfo? {
        val request = Request.Builder()
            .url("$url?t=${System.currentTimeMillis()}")
            .header("Cache-Control", "no-cache")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@use null
            val json = JSONObject(body)
            val latestVersion = json.optString("latestVersion", BuildConfig.VERSION_NAME).trim()
            val updateMessage = json.optString("updateMessage", "تتوفر نسخة جديدة من التطبيق مع تحسينات ومزايا جديدة.").trim()
            val downloadUrl = json.optString("downloadUrl", DEFAULT_APK_URL).ifBlank { DEFAULT_APK_URL }
            val isNewer = compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0
            AppUpdateInfo(
                latestVersion = latestVersion,
                updateMessage = updateMessage,
                downloadUrl = downloadUrl,
                isUpdateAvailable = isNewer
            )
        }
    }

    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val primary = runCatching { fetchStatusJson(STATUS_URL) }.getOrNull()
            val fallback = if (primary == null || !primary.isUpdateAvailable) {
                runCatching { fetchStatusJson(FALLBACK_STATUS_URL) }.getOrNull()
            } else {
                null
            }
            val chosen = when {
                primary != null && fallback != null -> {
                    if (compareVersions(fallback.latestVersion, primary.latestVersion) > 0) {
                        fallback.copy(
                            downloadUrl = if (fallback.downloadUrl == DEFAULT_APK_URL) FALLBACK_APK_URL else fallback.downloadUrl
                        )
                    } else {
                        primary
                    }
                }
                primary != null -> primary
                fallback != null -> fallback.copy(
                    downloadUrl = if (fallback.downloadUrl == DEFAULT_APK_URL) FALLBACK_APK_URL else fallback.downloadUrl
                )
                else -> return@withContext Result.failure(IllegalStateException("تعذر الاتصال بخادم التحديثات"))
            }
            Result.success(chosen)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadUpdateApk(
        context: Context,
        url: String,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val tempFile = File(updatesDir, "glasswave-update.apk.part")
            val finalFile = File(updatesDir, "glasswave-update.apk")
            if (tempFile.exists()) tempFile.delete()

            val candidateUrls = buildList {
                add(url)
                if (url != FALLBACK_APK_URL) add(FALLBACK_APK_URL)
            }
            var lastErrorMessage = "تعذر تنزيل التحديث"

            for (targetUrl in candidateUrls) {
                if (tempFile.exists()) tempFile.delete()
                val separator = if (targetUrl.contains("?")) "&" else "?"
                val request = Request.Builder()
                    .url("$targetUrl${separator}t=${System.currentTimeMillis()}")
                    .header("Cache-Control", "no-cache")
                    .header("Accept-Encoding", "identity")
                    .build()

                val downloadedResult = runCatching {
                    downloadClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            lastErrorMessage = "فشل تنزيل التحديث (${response.code})"
                            return@use null
                        }
                        val body = response.body ?: run {
                            lastErrorMessage = "ملف التحديث غير متوفر"
                            return@use null
                        }
                        val totalBytes = body.contentLength()
                        var downloaded = 0L
                        var lastReportedPct = -1
                        var lastReportTime = 0L

                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    downloaded += read

                                    val now = System.currentTimeMillis()
                                    val pct = if (totalBytes > 0) {
                                        ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    } else {
                                        // Smooth fallback estimate (~30 MB) when server omits Content-Length
                                        ((downloaded * 100L) / (30L * 1024L * 1024L)).toInt().coerceIn(1, 95)
                                    }
                                    if (pct != lastReportedPct || (now - lastReportTime) >= 200L) {
                                        lastReportedPct = pct
                                        lastReportTime = now
                                        onProgress(DownloadProgress(pct, downloaded, totalBytes))
                                    }
                                }
                                output.flush()
                            }
                        }
                        if (downloaded <= 0L) {
                            lastErrorMessage = "تم تنزيل ملف فارغ"
                            return@use null
                        }
                        onProgress(DownloadProgress(100, downloaded, if (totalBytes > 0) totalBytes else downloaded))
                        if (finalFile.exists()) finalFile.delete()
                        if (!tempFile.renameTo(finalFile)) {
                            tempFile.copyTo(finalFile, overwrite = true)
                            tempFile.delete()
                        }
                        finalFile
                    }
                }
                val file = downloadedResult.getOrNull()
                if (file != null && file.exists()) {
                    return@withContext Result.success(file)
                }
                downloadedResult.exceptionOrNull()?.message?.let { msg ->
                    if (msg.isNotBlank()) lastErrorMessage = msg
                }
            }
            Result.failure(IllegalStateException(lastErrorMessage))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File): ApkInstallResult {
        if (!apkFile.exists()) return ApkInstallResult.FAILED
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return ApkInstallResult.NEEDS_UNKNOWN_SOURCES_PERMISSION
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            ApkInstallResult.LAUNCHED_INSTALLER
        } catch (_: Exception) {
            ApkInstallResult.FAILED
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
