package com.galaxyalarm.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestTag: String,
    val isNewer: Boolean,
    val releaseUrl: String,
    val apkUrl: String?,
)

/**
 * GitHub Releases の最新タグを確認するだけの軽量チェッカー。
 * APIキー不要(公開リポジトリの REST v3)。スマホ単体で APK をDLできる URL を返す。
 */
object UpdateChecker {
    const val REPO = "masakasakasama/alarm"
    private const val TAG = "UpdateChecker"
    private const val GITHUB_BASE = "https://github.com"

    suspend fun check(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        checkApi(currentVersionName) ?: checkLatestReleaseRedirect(currentVersionName)
    }

    private fun checkApi(currentVersionName: String): UpdateInfo? {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest")
            .openConnection() as HttpURLConnection).apply {
            configure(currentVersionName)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub API HTTP ${conn.responseCode}; using release-page fallback")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            if (tag.isBlank()) return null
            val htmlUrl = json.optString("html_url")
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url"); break
                    }
                }
            }
            UpdateInfo(
                latestTag = tag,
                isNewer = isVersionNewer(currentVersionName, tag),
                releaseUrl = htmlUrl.ifBlank { releasePageUrl(tag) },
                apkUrl = apkUrl ?: apkDownloadUrl(tag),
            )
        } catch (e: Exception) {
            Log.w(TAG, "GitHub API update check failed; using release-page fallback", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun checkLatestReleaseRedirect(currentVersionName: String): UpdateInfo? {
        val conn = (URL("$GITHUB_BASE/$REPO/releases/latest")
            .openConnection() as HttpURLConnection).apply {
            configure(currentVersionName)
            instanceFollowRedirects = true
        }
        return try {
            if (conn.responseCode !in 200..399) {
                Log.w(TAG, "Latest release page HTTP ${conn.responseCode}")
                return null
            }
            val releaseUrl = conn.url.toString()
            val tag = releaseTagFromUrl(releaseUrl) ?: return null
            UpdateInfo(
                latestTag = tag,
                isNewer = isVersionNewer(currentVersionName, tag),
                releaseUrl = releaseUrl,
                apkUrl = apkDownloadUrl(tag),
            )
        } catch (e: Exception) {
            Log.w(TAG, "release-page fallback failed", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun HttpURLConnection.configure(currentVersionName: String) {
        requestMethod = "GET"
        setRequestProperty("User-Agent", "GalaxyAlarm/$currentVersionName")
        setRequestProperty("Cache-Control", "no-cache")
        connectTimeout = 8_000
        readTimeout = 8_000
    }

    private fun releasePageUrl(tag: String) = "$GITHUB_BASE/$REPO/releases/tag/$tag"

    private fun apkDownloadUrl(tag: String) =
        "$GITHUB_BASE/$REPO/releases/download/$tag/GalaxyAlarm-$tag.apk"
}

internal fun releaseTagFromUrl(url: String): String? {
    val marker = "/releases/tag/"
    val markerIndex = url.indexOf(marker)
    if (markerIndex < 0) return null
    return url.substring(markerIndex + marker.length)
        .substringBefore('?')
        .substringBefore('#')
        .trim('/')
        .takeIf { it.isNotBlank() }
}

internal fun isVersionNewer(current: String, tag: String): Boolean {
    fun numbers(value: String) = value.removePrefix("v").split(".", "-")
        .mapNotNull { it.toIntOrNull() }
    val currentParts = numbers(current)
    val targetParts = numbers(tag)
    for (index in 0 until maxOf(currentParts.size, targetParts.size)) {
        val currentPart = currentParts.getOrElse(index) { 0 }
        val targetPart = targetParts.getOrElse(index) { 0 }
        if (targetPart != currentPart) return targetPart > currentPart
    }
    return false
}
