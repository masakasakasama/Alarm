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
    // 自分のリポジトリに合わせて変更。
    const val REPO = "masakasakasama/alarm"
    private const val TAG = "UpdateChecker"

    suspend fun check(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$REPO/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "HTTP ${conn.responseCode}")
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
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
                isNewer = isNewer(currentVersionName, tag),
                releaseUrl = htmlUrl,
                apkUrl = apkUrl
            )
        } catch (e: Exception) {
            Log.w(TAG, "update check failed: ${e.message}")
            null
        }
    }

    private fun isNewer(current: String, tag: String): Boolean {
        fun nums(s: String) = s.removePrefix("v").split(".", "-")
            .mapNotNull { it.toIntOrNull() }
        val c = nums(current); val t = nums(tag)
        for (i in 0 until maxOf(c.size, t.size)) {
            val cv = c.getOrElse(i) { 0 }; val tv = t.getOrElse(i) { 0 }
            if (tv != cv) return tv > cv
        }
        return false
    }
}
