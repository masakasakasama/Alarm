package com.galaxyalarm.reliability

import com.galaxyalarm.data.repo.AlarmRepository
import com.galaxyalarm.scheduler.NextTriggerCalculator

/** 1項目のチェック結果。 */
data class CheckItem(val title: String, val ok: Boolean, val detail: String)

/** 信頼性チェック画面に出す総合レポート。 */
data class ReliabilityReport(
    val items: List<CheckItem>,
    val lastCheckAt: Long,
    val lastRepairAt: Long,
    val lastRepairResult: String,
    val lastEventLogSummary: String,
) {
    val allOk: Boolean get() = items.all { it.ok }
    val hasCritical: Boolean get() = items.any { !it.ok && it.critical() }
    private fun CheckItem.critical(): Boolean =
        title.contains("exact") || title.contains("正確") || title.contains("予約")
}

/**
 * すべての信頼性項目を検査し、結果を ReliabilityStore に記録する。
 */
class ReliabilityChecker(
    private val repo: AlarmRepository,
    private val permissions: PermissionChecker,
    private val store: ReliabilityStore,
) {
    suspend fun buildReport(): ReliabilityReport {
        val items = mutableListOf<CheckItem>()

        val exactOk = permissions.canScheduleExactAlarms()
        items += CheckItem(
            "正確なアラーム権限 (exact alarm)", exactOk,
            if (exactOk) "許可済み" else "未許可。設定から許可してください"
        )
        val notifOk = permissions.hasNotificationPermission()
        items += CheckItem(
            "通知権限", notifOk,
            if (notifOk) "許可済み" else "未許可。鳴動表示が出ない可能性"
        )
        val battOk = permissions.isIgnoringBatteryOptimizations()
        items += CheckItem(
            "バッテリー最適化の除外", battOk,
            if (battOk) "除外済み" else "未除外。Dozeで遅延の恐れ"
        )
        val fsOk = permissions.canUseFullScreenIntent()
        items += CheckItem(
            "全画面通知", fsOk,
            if (fsOk) "許可済み" else "未許可。ロック画面に全画面表示できない恐れ"
        )

        // 起動時/更新後/TZ/時刻変更への対応はマニフェスト宣言で常に有効。
        items += CheckItem("起動時 再スケジュール対応", true, "BOOT_COMPLETED を受信")
        items += CheckItem("アプリ更新後 再スケジュール対応", true, "MY_PACKAGE_REPLACED を受信")
        items += CheckItem("タイムゾーン変更対応", true, "TIMEZONE_CHANGED を受信")
        items += CheckItem("時刻変更対応", true, "TIME_SET を受信")

        // 有効アラームに未来の予約があるか。
        val alarms = repo.getAlarms()
        val groups = repo.getGroups().associateBy { it.id }
        val scheduled = repo.getAllScheduled()
        val now = System.currentTimeMillis()
        val enabledAlarms = alarms.filter { a ->
            a.enabled && groups[a.groupId]?.enabled == true
        }
        val scheduledAlarmIds = scheduled.filter { it.triggerAtMillis > now }
            .map { it.alarmId }.toSet()
        val missing = enabledAlarms.filter { it.id !in scheduledAlarmIds }
        items += CheckItem(
            "有効アラームの未来予約", missing.isEmpty(),
            if (missing.isEmpty()) "${enabledAlarms.size}件すべて予約済み"
            else "${missing.size}件 予約欠落。修復が必要"
        )

        // requestCode の重複。
        val rcs = scheduled.map { it.requestCode }
        val dupOk = rcs.size == rcs.toSet().size
        items += CheckItem(
            "PendingIntent requestCode 重複なし", dupOk,
            if (dupOk) "重複なし (${rcs.size}件)" else "重複検出!"
        )

        // 次回鳴動時刻の整合(再計算と予約のズレが大きくないか)。
        var consistencyOk = true
        for (occ in scheduled.filter { it.triggerAtMillis > now && it.snoozeCount == 0 }) {
            val a = alarms.firstOrNull { it.id == occ.alarmId } ?: continue
            val recomputed = NextTriggerCalculator.nextTrigger(a.hour, a.minute, a.weekdaysMask)
            // スヌーズ以外で、予約が再計算より大幅に過去ならズレとみなす。
            if (occ.triggerAtMillis < recomputed - 60_000) consistencyOk = false
        }
        items += CheckItem(
            "次回鳴動時刻の整合", consistencyOk,
            if (consistencyOk) "整合" else "ズレあり。修復推奨"
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

    /** チェックを実行し、結果概要を保存。UI からもバックグラウンドからも呼ぶ。 */
    suspend fun runCheck(): ReliabilityReport {
        val report = buildReport()
        store.lastCheckAt = System.currentTimeMillis()
        store.lastCheckOk = report.allOk
        store.lastCheckSummary =
            if (report.allOk) "問題なし" else report.items.filter { !it.ok }
                .joinToString("、") { it.title }
        return report
    }

    /** 手動/自動の「スケジュール修復」。 */
    suspend fun repair(): ReliabilityReport {
        repo.rescheduleAll("manual-repair")
        store.lastRepairAt = System.currentTimeMillis()
        val report = runCheck()
        store.lastRepairResult = if (report.allOk) "修復成功・問題なし" else "修復実行・一部要対応"
        return report
    }
}
