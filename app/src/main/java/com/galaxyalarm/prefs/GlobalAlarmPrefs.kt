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
        get() = sp.getInt("fadeInStartVolume", 5).coerceIn(5, 50)
        set(v) = sp.edit().putInt("fadeInStartVolume", v.coerceIn(5, 50)).apply()

    private companion object { const val PREFS_NAME = "global_alarm_prefs" }
}
