package com.galaxyalarm

import android.app.Application
import android.os.Build
import com.galaxyalarm.data.db.AppDatabase
import com.galaxyalarm.data.repo.AlarmRepository
import com.galaxyalarm.notify.NotificationHelper
import com.galaxyalarm.reliability.PermissionChecker
import com.galaxyalarm.reliability.ReliabilityChecker
import com.galaxyalarm.reliability.ReliabilityStore
import com.galaxyalarm.reliability.ScheduleHealthWorker
import com.galaxyalarm.scheduler.AlarmScheduler
import com.galaxyalarm.widget.NextAlarmWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 軽量 DI コンテナ。Hilt 不使用でビルドリスクを抑える。 */
class AppContainer(app: Application) {
    val db = AppDatabase.get(app)
    val permissions = PermissionChecker(app)
    val reliabilityStore = ReliabilityStore(app)
    val scheduler = AlarmScheduler(
        context = app,
        groupDao = db.groupDao(),
        alarmDao = db.alarmDao(),
        occurrenceDao = db.occurrenceDao(),
        logDao = db.eventLogDao(),
        permissions = permissions,
    )
    val repository = AlarmRepository(
        groupDao = db.groupDao(),
        alarmDao = db.alarmDao(),
        occurrenceDao = db.occurrenceDao(),
        logDao = db.eventLogDao(),
        scheduler = scheduler,
    )
    val reliabilityChecker = ReliabilityChecker(repository, permissions, reliabilityStore)
}

class AlarmApplication : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        NotificationHelper(this).createChannels()
        // 実行中タイマーを復元(タブ移動・アプリ終了をまたいで生存)。
        runCatching { com.galaxyalarm.timer.TimerController.init(this) }

        // アプリ起動時: 既定グループ確保 → 全再スケジュール → 健全性チェック。
        appScope.launch {
            // プリセット群は「初回・かつグループが1つも無いとき」だけ投入する。
            // 一度削除したプリセットがアプリ更新で復活しないようにフラグで一度きりにする。
            // (ensureDefaultGroup より前に判定すること。既定グループ作成で count が増えるため)
            if (!container.reliabilityStore.presetsSeeded) {
                // 「過去に一度も起動していない=真の初回」のみ投入。
                // lastCheckAt は過去の起動で必ずセットされるので、削除済みでも復活しない。
                if (container.reliabilityStore.lastCheckAt == 0L) {
                    container.repository.ensureImagePresetGroups()
                }
                container.reliabilityStore.presetsSeeded = true
            }
            container.repository.ensureDefaultGroup()
            val fingerprint = Build.FINGERPRINT ?: ""
            val reason = if (
                container.reliabilityStore.lastBuildFingerprint.isNotBlank() &&
                container.reliabilityStore.lastBuildFingerprint != fingerprint
            ) {
                "os-build-changed"
            } else {
                "app-start"
            }
            container.repository.rescheduleAll(reason)
            val report = container.reliabilityChecker.runCheck()
            container.reliabilityStore.lastBuildFingerprint = fingerprint
            if (report.hasCritical) {
                val issues = report.items.filter { !it.ok }.joinToString("、") { it.title }
                NotificationHelper(this@AlarmApplication).showReliabilityWarning(
                    title = "アラーム設定を確認してください",
                    message = "OS更新または権限変更の影響で要対応項目があります: $issues"
                )
            }
            NextAlarmWidgetProvider.refresh(this@AlarmApplication)
        }
        // バックグラウンド定期チェック。
        ScheduleHealthWorker.schedule(this)
    }

    companion object {
        lateinit var instance: AlarmApplication
            private set
    }
}
