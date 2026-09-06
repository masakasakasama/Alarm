package com.galaxyalarm.prefs

import android.content.Context
import android.os.UserManager

class GlobalAlarmPrefs(context: Context) {
    private val sp = context.createDeviceProtectedStorageContext().run {
        if (context.getSystemService(UserManager::class.java).isUserUnlocked) {
            moveSharedPreferencesFrom(context.applicationContext, PREFS_NAME)
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var fadeInSeconds: Int
        get() = sp.getInt("fadeInSeconds", 0)
        set(v) = sp.edit().putInt("fadeInSeconds", v).apply()

    var fadeInStartVolume: Int
        get() = sp.getInt("fadeInStartVolume", DEFAULT_FADE_IN_START_VOLUME)
            .coerceIn(MIN_FADE_IN_START_VOLUME, MAX_FADE_IN_START_VOLUME)
        set(v) = sp.edit().putInt(
            "fadeInStartVolume",
            v.coerceIn(MIN_FADE_IN_START_VOLUME, MAX_FADE_IN_START_VOLUME)
        ).apply()

    companion object {
        const val MIN_FADE_IN_START_VOLUME = 0
        const val MAX_FADE_IN_START_VOLUME = 50
        const val DEFAULT_FADE_IN_START_VOLUME = 5
        const val FADE_IN_START_VOLUME_STEP = 5

        private const val PREFS_NAME = "global_alarm_prefs"
    }
}
