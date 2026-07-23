package com.galaxyalarm.reliability

import android.content.Context
import android.os.UserManager

class ReliabilityStore(context: Context) {
    private val sp = context.createDeviceProtectedStorageContext().run {
        if (context.getSystemService(UserManager::class.java).isUserUnlocked) {
            moveSharedPreferencesFrom(context.applicationContext, PREFS_NAME)
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var lastCheckAt: Long
        get() = sp.getLong("lastCheckAt", 0L)
        set(v) = sp.edit().putLong("lastCheckAt", v).apply()

    var lastCheckOk: Boolean
        get() = sp.getBoolean("lastCheckOk", false)
        set(v) = sp.edit().putBoolean("lastCheckOk", v).apply()

    var lastCheckSummary: String
        get() = sp.getString("lastCheckSummary", "未実行") ?: "未実行"
        set(v) = sp.edit().putString("lastCheckSummary", v).apply()

    var lastRepairAt: Long
        get() = sp.getLong("lastRepairAt", 0L)
        set(v) = sp.edit().putLong("lastRepairAt", v).apply()

    var lastRepairResult: String
        get() = sp.getString("lastRepairResult", "未実行") ?: "未実行"
        set(v) = sp.edit().putString("lastRepairResult", v).apply()

    var lastBuildFingerprint: String
        get() = sp.getString("lastBuildFingerprint", "") ?: ""
        set(v) = sp.edit().putString("lastBuildFingerprint", v).apply()

    /** プリセット群(電車内・飛行機など)を初回に一度だけ投入したか。削除したものを復活させないため。 */
    var presetsSeeded: Boolean
        get() = sp.getBoolean("presetsSeeded", false)
        set(v) = sp.edit().putBoolean("presetsSeeded", v).apply()

    var lastSystemEvent: String
        get() = sp.getString("lastSystemEvent", "未受信") ?: "未受信"
        set(v) = sp.edit().putString("lastSystemEvent", v).apply()

    var lastSystemEventAt: Long
        get() = sp.getLong("lastSystemEventAt", 0L)
        set(v) = sp.edit().putLong("lastSystemEventAt", v).apply()

    private companion object { const val PREFS_NAME = "reliability" }
}
