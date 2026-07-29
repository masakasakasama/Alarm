package com.galaxyalarm.update

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.galaxyalarm.BuildConfig

object UpdatePermissionRecovery {
    fun markFullScreenExpected(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_FULL_SCREEN_EXPECTED, true)
            .commit()
    }

    fun shouldOpenFullScreenSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val prefs = prefs(context)
        val handledVersion = prefs.getInt(KEY_HANDLED_VERSION, 0)
        val expected = prefs.getBoolean(KEY_FULL_SCREEN_EXPECTED, true)
        val currentlyAllowed = context
            .getSystemService(NotificationManager::class.java)
            .canUseFullScreenIntent()
        return shouldPromptForFullScreenRecovery(
            sdkInt = Build.VERSION.SDK_INT,
            expected = expected,
            currentlyAllowed = currentlyAllowed,
            handledVersion = handledVersion,
            currentVersion = BuildConfig.VERSION_CODE,
        )
    }

    fun markFullScreenRecoveryHandled(context: Context) {
        prefs(context).edit()
            .putInt(KEY_HANDLED_VERSION, BuildConfig.VERSION_CODE)
            .remove(KEY_FULL_SCREEN_EXPECTED)
            .commit()
    }

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "update_permission_recovery"
    private const val KEY_FULL_SCREEN_EXPECTED = "full_screen_expected"
    private const val KEY_HANDLED_VERSION = "handled_version"
}

internal fun shouldPromptForFullScreenRecovery(
    sdkInt: Int,
    expected: Boolean,
    currentlyAllowed: Boolean,
    handledVersion: Int,
    currentVersion: Int,
): Boolean =
    sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        expected &&
        !currentlyAllowed &&
        handledVersion != currentVersion
