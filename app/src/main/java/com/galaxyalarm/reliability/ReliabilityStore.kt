package com.galaxyalarm.reliability

import android.content.Context

class ReliabilityStore(context: Context) {
    private val sp = context.getSharedPreferences("reliability", Context.MODE_PRIVATE)

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
}
