package com.galaxyalarm.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
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

        installDownloadedApk(context, apk)
    }

    internal fun installDownloadedApk(context: Context, apk: File) {
        require(apk.isFile && apk.length() > 0L) { "downloaded APK is empty" }
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                grantFullScreenPermissionForUpdate()
            }
        }

        UpdatePermissionRecovery.markFullScreenExpected(context)
        val sessionId = packageInstaller.createSession(params)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0L, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val statusIntent = Intent(context, UpdateInstallReceiver::class.java)
                    .putExtra(UpdateInstallReceiver.EXTRA_SESSION_ID, sessionId)
                val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
                session.commit(
                    PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        statusIntent,
                        pendingFlags,
                    ).intentSender
                )
            }
        } catch (error: Exception) {
            runCatching { packageInstaller.abandonSession(sessionId) }
            throw error
        }
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun PackageInstaller.SessionParams.grantFullScreenPermissionForUpdate() {
    setPermissionState(
        Manifest.permission.USE_FULL_SCREEN_INTENT,
        PackageInstaller.SessionParams.PERMISSION_STATE_GRANTED,
    )
}

internal fun fullScreenPermissionStateForUpdate(sdkInt: Int): Int? =
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PackageInstaller.SessionParams.PERMISSION_STATE_GRANTED
    } else {
        null
    }
