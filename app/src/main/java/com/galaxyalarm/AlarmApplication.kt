package com.galaxyalarm

import android.app.Application
import android.os.UserManager
import com.galaxyalarm.data.db.AppDatabase
import com.galaxyalarm.data.repo.AlarmRepository
import com.galaxyalarm.notify.NotificationHelper
import com.galaxyalarm.reliability.PermissionChecker
import com.galaxyalarm.reliability.ReliabilityChecker
import com.galaxyalarm.reliability.ReliabilityStore
import com.galaxyalarm.reliability.ScheduleHealthWorker
import com.galaxyalarm.scheduler.AlarmScheduler
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
        runCatching { NotificationHelper(this).createChannels() }

        // アプリ起動時(Direct Boot=再起動直後の未解除状態を含む): 全再スケジュール。
        // DB はデバイス暗号化ストレージなので未解除でも読める。例外で絶対に落とさない。
        appScope.launch {
            runCatching {
                container.repository.ensureDefaultGroup()
                container.repository.rescheduleAll("app-start")
                container.reliabilityChecker.runCheck()
            }
        }

        // WorkManager はロック解除後(CEストレージ利用可)のみ初期化する。
        // Direct Boot 中に呼ぶと例外になり得るため、未解除時はスキップ。
        val unlocked = getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        if (unlocked) {
            runCatching { ScheduleHealthWorker.schedule(this) }
        }
    }

    companion object {
        lateinit var instance: AlarmApplication
            private set
    }
}
