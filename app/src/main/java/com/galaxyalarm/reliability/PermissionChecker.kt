package com.galaxyalarm.reliability

import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.Manifest

/**
 * 各種権限・OS状態の確認を一元化する。アラームの信頼性に直結するため、
 * UI/スケジューラ/Worker すべてここを通す。
 */
class PermissionChecker(private val context: Context) {

    private val alarmManager get() =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val powerManager get() =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /** exact alarm を予約できるか。Android 12 未満は常に true。 */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

    /** 通知を出せるか(Android 13+ の POST_NOTIFICATIONS)。 */
    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    /** バッテリー最適化の対象外(=除外済み)か。除外済みが望ましい。 */
    fun isIgnoringBatteryOptimizations(): Boolean =
        powerManager.isIgnoringBatteryOptimizations(context.packageName)

    /** 全画面通知が許可されているか(Android 14+ で制限あり)。 */
    fun canUseFullScreenIntent(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm.canUseFullScreenIntent()
        } else true
}
