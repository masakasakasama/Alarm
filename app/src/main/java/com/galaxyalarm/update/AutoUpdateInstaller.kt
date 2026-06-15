package com.galaxyalarm.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AutoUpdateInstaller {
    suspend fun downloadAndOpen(context: Context, info: UpdateInfo): Unit = withContext(Dispatchers.IO) {
        val apkUrl = info.apkUrl ?: return@withContext
        val safeTag = info.latestTag.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "GalaxyAlarm-$safeTag.apk")

        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        if (conn.responseCode !in 200..299) error("download failed: HTTP ${conn.responseCode}")
        conn.inputStream.use { input ->
            apk.outputStream().use { output -> input.copyTo(output) }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
