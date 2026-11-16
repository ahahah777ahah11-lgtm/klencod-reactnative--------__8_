package com.example.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class AppUpdateState {
    data object Idle : AppUpdateState()
    data object Checking : AppUpdateState()
    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int,
        val checkedAt: Long,
        val message: String,
    ) : AppUpdateState()

    data class UpdateAvailable(
        val currentVersionName: String,
        val latestVersionName: String,
        val latestVersionCode: Int,
        val releaseNotes: String,
        val downloadUrl: String,
        val checkedAt: Long,
    ) : AppUpdateState()

    data class CheckError(val message: String) : AppUpdateState()
}

data class RemoteUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
)

object AppUpdateChecker {

    fun parseUpdateManifestJson(rawJson: String): Result<RemoteUpdateManifest> {
        return runCatching {
            val obj = JSONObject(rawJson.trim())
            val versionCode = obj.optInt("versionCode", 0)
            val versionName = obj.optString("versionName", "").trim()
            val releaseNotes = obj.optString("releaseNotes", "").trim()
            val downloadUrl = obj.optString("downloadUrl", "").trim()
            require(versionCode > 0 && versionName.isNotBlank()) {
                "تنسيق ملف التحديث غير مكتمل (versionCode / versionName)"
            }
            RemoteUpdateManifest(
                versionCode = versionCode,
                versionName = versionName,
                releaseNotes = releaseNotes.ifBlank { "تحسينات في الأداء وإصلاح الأخطاء" },
                downloadUrl = downloadUrl,
            )
        }
    }

    fun evaluateManifest(
        manifest: RemoteUpdateManifest,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        nowMillis: Long = System.currentTimeMillis(),
    ): AppUpdateState {
        return if (manifest.versionCode > currentVersionCode) {
            AppUpdateState.UpdateAvailable(
                currentVersionName = currentVersionName,
                latestVersionName = manifest.versionName,
                latestVersionCode = manifest.versionCode,
                releaseNotes = manifest.releaseNotes,
                downloadUrl = manifest.downloadUrl,
                checkedAt = nowMillis,
            )
        } else {
            AppUpdateState.UpToDate(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                checkedAt = nowMillis,
                message = "أنت تستخدم أحدث إصدار من التطبيق (v$currentVersionName).",
            )
        }
    }

    suspend fun checkForUpdates(
        manifestUrl: String,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME,
    ): AppUpdateState = withContext(Dispatchers.IO) {
        val cleanUrl = manifestUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext AppUpdateState.UpToDate(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                checkedAt = System.currentTimeMillis(),
                message = "الإصدار الحالي مثبت وجاهز (v$currentVersionName • رقم $currentVersionCode). أضف رابط بيان التحديثات (JSON) لفحص الإصدارات الجديدة عن بُعد.",
            )
        }
        if (!cleanUrl.startsWith("https://", ignoreCase = true) && !cleanUrl.startsWith("http://", ignoreCase = true)) {
            return@withContext AppUpdateState.CheckError("يرجى إدخال رابط تحديث يبدأ بـ https://")
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL(cleanUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext AppUpdateState.CheckError("تعذر الوصول لخادم التحديثات (HTTP $code).")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val manifest = parseUpdateManifestJson(body).getOrElse { err ->
                return@withContext AppUpdateState.CheckError(err.message ?: "تعذر قراءة بيانات التحديث.")
            }
            evaluateManifest(
                manifest = manifest,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
            )
        } catch (e: Throwable) {
            AppUpdateState.CheckError("تعذر الاتصال بخادم التحديثات (تعمل بدون إنترنت أو الخادم غير متاح).")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    fun openDownloadUrl(context: Context, url: String): Boolean {
        val clean = url.trim()
        if (!clean.startsWith("https://", ignoreCase = true) && !clean.startsWith("http://", ignoreCase = true)) {
            return false
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
