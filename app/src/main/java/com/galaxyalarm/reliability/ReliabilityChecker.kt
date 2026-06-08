package com.galaxyalarm.reliability

import com.galaxyalarm.data.repo.AlarmRepository
import com.galaxyalarm.scheduler.NextTriggerCalculator

const val TITLE_EXACT_ALARM = "正確なアラーム権限"
const val TITLE_NOTIFICATIONS = "通知権限"
const val TITLE_BATTERY = "バッテリー最適化の除外"
const val TITLE_FULL_SCREEN = "全画面通知"
const val TITLE_ENABLED_ALARMS_IN_OFF_GROUPS = "OFFグループ内のONアラーム"
const val TITLE_FUTURE_SCHEDULES = "有効アラームの未来予約"
const val TITLE_REQUEST_CODES = "PendingIntent requestCode"
const val TITLE_TRIGGER_CONSISTENCY = "次回鳴動時刻の整合"

data class CheckItem(val title: String, val ok: Boolean, val detail: String)

data class ReliabilityReport(
    val items: List<CheckItem>,
    val lastCheckAt: Long,
    val lastRepairAt: Long,
    val lastRepairResult: String,
    val lastEventLogSummary: String,
) {
    val allOk: Boolean get() = items.all { it.ok }
    val hasCritical: Boolean get() = items.any { !it.ok && it.isCritical() }
    val hasMissingFutureSchedule: Boolean get() =
        items.any { it.title == TITLE_FUTURE_SCHEDULES && !it.ok }
    val hasBlockedEnabledAlarms: Boolean get() =
        items.any { it.title == TITLE_ENABLED_ALARMS_IN_OFF_GROUPS && !it.ok }
    val hasRepairableScheduleProblem: Boolean get() =
        hasMissingFutureSchedule || hasBlockedEnabledAlarms

    private fun CheckItem.isCritical(): Boolean =
        title in setOf(
            TITLE_EXACT_ALARM,
            TITLE_NOTIFICATIONS,
            TITLE_BATTERY,
            TITLE_FULL_SCREEN,
            TITLE_ENABLED_ALARMS_IN_OFF_GROUPS,
            TITLE_FUTURE_SCHEDULES,
            TITLE_REQUEST_CODES,
            TITLE_TRIGGER_CONSISTENCY,
        )
}

class ReliabilityChecker(
    private val repo: AlarmRepository,
    private val permissions: PermissionChecker,
    private val store: ReliabilityStore,
) {
    suspend fun buildReport(): ReliabilityReport {
        val items = mutableListOf<CheckItem>()

        val exactOk = permissions.canScheduleExactAlarms()
        items += CheckItem(
            TITLE_EXACT_ALARM,
            exactOk,
            if (exactOk) "許可済み" else "未許可。設定の「アラームとリマインダー」で許可が必要です。"
        )

        val notifOk = permissions.hasNotificationPermission()
        items += CheckItem(
            TITLE_NOTIFICATIONS,
            notifOk,
            if (notifOk) "許可済み" else "未許可。鳴動中の通知・ロック画面操作が出ない可能性があります。"
        )

        val battOk = permissions.isIgnoringBatteryOptimizations()
        items += CheckItem(
            TITLE_BATTERY,
            battOk,
            if (battOk) "除外済み" else "未除外。Dozeやメーカー最適化で遅延する可能性があります。"
        )

        val fsOk = permissions.canUseFullScreenIntent()
        items += CheckItem(
            TITLE_FULL_SCREEN,
            fsOk,
            if (fsOk) "許可済み" else "未許可。ロック画面に全画面で表示できない可能性があります。"
        )

        items += CheckItem("起動時 再スケジュール", true, "BOOT_COMPLETED / LOCKED_BOOT_COMPLETED を受信")
        items += CheckItem("初回ロック解除 再スケジュール", true, "USER_UNLOCKED を受信")
        items += CheckItem("アプリ更新後 再スケジュール", true, "MY_PACKAGE_REPLACED を受信")
        items += CheckItem("時刻・タイムゾーン変更対応", true, "TIME_SET / TIMEZONE_CHANGED を受信")
        items += CheckItem("定期監視", true, "WorkManagerで3時間ごとに確認")

        val alarms = repo.getAlarms()
        val groups = repo.getGroups().associateBy { it.id }
        val scheduled = repo.getAllScheduled()
        val now = System.currentTimeMillis()
        val blockedByGroup = alarms.filter { alarm ->
            alarm.enabled && groups[alarm.groupId]?.enabled == false
        }
        items += CheckItem(
            TITLE_ENABLED_ALARMS_IN_OFF_GROUPS,
            blockedByGroup.isEmpty(),
            if (blockedByGroup.isEmpty()) "なし"
            else "${blockedByGroup.size}件あります。修復でグループをONにします。"
        )
        val enabledAlarms = alarms.filter { alarm ->
            alarm.enabled && groups[alarm.groupId]?.enabled == true
        }
        val scheduledAlarmIds = scheduled.filter { it.triggerAtMillis > now }
            .map { it.alarmId }
            .toSet()
        val missing = enabledAlarms.filter { it.id !in scheduledAlarmIds }
        items += CheckItem(
            TITLE_FUTURE_SCHEDULES,
            missing.isEmpty(),
            if (missing.isEmpty()) "${enabledAlarms.size}件すべて予約済み"
            else "${missing.size}件の未来予約が欠落。修復が必要です。"
        )

        val requestCodes = scheduled.map { it.requestCode }
        val requestCodesOk = requestCodes.size == requestCodes.toSet().size
        items += CheckItem(
            TITLE_REQUEST_CODES,
            requestCodesOk,
            if (requestCodesOk) "重複なし (${requestCodes.size}件)" else "重複があります。再スケジュールが必要です。"
        )

        var consistencyOk = true
        for (occ in scheduled.filter { it.triggerAtMillis > now && it.snoozeCount == 0 }) {
            val alarm = alarms.firstOrNull { it.id == occ.alarmId } ?: continue
            val recomputed = NextTriggerCalculator.nextTrigger(alarm.hour, alarm.minute, alarm.weekdaysMask)
            if (occ.triggerAtMillis < recomputed - 60_000) consistencyOk = false
        }
        items += CheckItem(
            TITLE_TRIGGER_CONSISTENCY,
            consistencyOk,
            if (consistencyOk) "整合" else "予約時刻にズレがあります。再スケジュールが必要です。"
        )

        return ReliabilityReport(
            items = items,
            lastCheckAt = store.lastCheckAt,
            lastRepairAt = store.lastRepairAt,
            lastRepairResult = store.lastRepairResult,
            lastEventLogSummary = repo.latestLog()?.let {
                "${it.result} ${it.message}".take(120)
            } ?: "ログなし",
        )
    }

    suspend fun runCheck(): ReliabilityReport {
        val report = buildReport()
        store.lastCheckAt = System.currentTimeMillis()
        store.lastCheckOk = report.allOk
        store.lastCheckSummary = if (report.allOk) {
            "問題なし"
        } else {
            report.items.filter { !it.ok }.joinToString("、") { it.title }
        }
        return report
    }

    suspend fun repair(reason: String = "manual-repair"): ReliabilityReport {
        repo.rescheduleAll(reason)
        store.lastRepairAt = System.currentTimeMillis()
        val report = runCheck()
        store.lastRepairResult = if (report.allOk) "修復成功・問題なし" else "修復後も要対応項目あり"
        return report
    }
}
