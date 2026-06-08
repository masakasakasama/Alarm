package com.galaxyalarm.reliability

import android.content.Context
import android.os.Build

/**
 * 信頼性関連の小さな状態(最終チェック時刻・修復結果)を SharedPreferences で保持。
 * Receiver/Worker から同期的に読み書きできるよう、あえて軽量実装にする。
 * Direct Boot(再起動直後)でも読めるようデバイス暗号化ストレージを使う。
 */
class ReliabilityStore(context: Context) {
    private val storageCtx =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            context.applicationContext.createDeviceProtectedStorageContext()
        else context.applicationContext
    private val sp = storageCtx.getSharedPreferences("reliability", Context.MODE_PRIVATE)

    var lastCheckAt: Long
        get() = sp.getLong("lastCheckAt", 0L)
        set(v) = sp.edit().putLong("lastCheckAt", v).apply()

    var lastCheckOk: Boolean
        get() = sp.getBoolean("lastCheckOk", false)
        set(v) = sp.edit().putBoolean("lastCheckOk", v).apply()

    var lastCheckSummary: String
        get() = sp.getString("lastCheckSummary", "未実施") ?: "未実施"
        set(v) = sp.edit().putString("lastCheckSummary", v).apply()

    var lastRepairAt: Long
        get() = sp.getLong("lastRepairAt", 0L)
        set(v) = sp.edit().putLong("lastRepairAt", v).apply()

    var lastRepairResult: String
        get() = sp.getString("lastRepairResult", "未実施") ?: "未実施"
        set(v) = sp.edit().putString("lastRepairResult", v).apply()
}
