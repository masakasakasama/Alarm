package com.galaxyalarm.reliability

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import android.Manifest
import com.galaxyalarm.data.db.AppDatabase
import com.galaxyalarm.receiver.AlarmReceiver
import com.galaxyalarm.receiver.SystemEventReceiver
import com.galaxyalarm.ring.AlarmRingActivity
import com.galaxyalarm.service.AlarmService

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

    fun supportsDirectBoot(): Boolean =
        AppDatabase.isInDeviceProtectedStorage(context) &&
            receiverDirectBootAware(AlarmReceiver::class.java) &&
            receiverDirectBootAware(SystemEventReceiver::class.java) &&
            serviceDirectBootAware(AlarmService::class.java) &&
            activityDirectBootAware(AlarmRingActivity::class.java)

    @Suppress("DEPRECATION")
    private fun receiverDirectBootAware(clazz: Class<*>): Boolean = runCatching {
        val component = ComponentName(context, clazz)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getReceiverInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else context.packageManager.getReceiverInfo(component, 0)
        info.directBootAware
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun serviceDirectBootAware(clazz: Class<*>): Boolean = runCatching {
        val component = ComponentName(context, clazz)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else context.packageManager.getServiceInfo(component, 0)
        info.directBootAware
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun activityDirectBootAware(clazz: Class<*>): Boolean = runCatching {
        val component = ComponentName(context, clazz)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else context.packageManager.getActivityInfo(component, 0)
        info.directBootAware
    }.getOrDefault(false)
}
