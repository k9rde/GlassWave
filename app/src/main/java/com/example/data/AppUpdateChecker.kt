package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val latestVersion: String,
    val updateMessage: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

object AppUpdateChecker {
    private const val STATUS_URL = "https://k9rde.github.io/GlassWave/status.json"
    private const val DEFAULT_APK_URL = "https://k9rde.github.io/GlassWave/glasswave.apk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$STATUS_URL?t=${System.currentTimeMillis()}")
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("تعذر الاتصال بخادم التحديثات"))
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val latestVersion = json.optString("latestVersion", BuildConfig.VERSION_NAME).trim()
                val updateMessage = json.optString("updateMessage", "تتوفر نسخة جديدة من التطبيق مع تحسينات ومزايا جديدة.").trim()
                val downloadUrl = json.optString("downloadUrl", DEFAULT_APK_URL).ifBlank { DEFAULT_APK_URL }
                val isNewer = compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0
                Result.success(
                    AppUpdateInfo(
                        latestVersion = latestVersion,
                        updateMessage = updateMessage,
                        downloadUrl = downloadUrl,
                        isUpdateAvailable = isNewer
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
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
