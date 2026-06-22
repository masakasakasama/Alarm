package com.galaxyalarm.prefs

import android.content.Context

class GlobalAlarmPrefs(context: Context) {
    private val sp = context.getSharedPreferences("global_alarm_prefs", Context.MODE_PRIVATE)

    var fadeInSeconds: Int
        get() = sp.getInt("fadeInSeconds", 0)
        set(v) = sp.edit().putInt("fadeInSeconds", v).apply()
}
