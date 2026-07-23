package com.galaxyalarm

import android.app.Application
import android.app.ActivityManager
import android.os.Build
import android.os.UserManager
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
    @Volatile private var appContainer: AppContainer? = null
    private var coreStarted = false
    private var unlockedStarted = false

    val container: AppContainer
        get() = containerOrNull() ?: error("Alarm data is unavailable before first unlock")

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationHelper(this).also {
            it.createChannels()
            it.cancelObsoleteNextAlarmStatus()
        }
        initializeAvailable()
    }

    /** Called again from USER_UNLOCKED because Application.onCreate is not repeated. */
    fun initializeAvailable() {
        val current = containerOrNull() ?: return
        val unlocked = getSystemService(UserManager::class.java).isUserUnlocked
        val startCore: Boolean
        val startUnlocked: Boolean
        synchronized(this) {
            startCore = !coreStarted
            if (startCore) coreStarted = true
            startUnlocked = unlocked && !unlockedStarted
            if (startUnlocked) unlockedStarted = true
        }

        if (startUnlocked) {
            runCatching { com.galaxyalarm.timer.TimerController.init(this) }
            ScheduleHealthWorker.schedule(this)
        }
        if (!startCore && !startUnlocked) return

        appScope.launch {
            if (startUnlocked) {
                if (!current.reliabilityStore.presetsSeeded) {
                    if (current.reliabilityStore.lastCheckAt == 0L) {
                        current.repository.ensureImagePresetGroups()
                    }
                    current.reliabilityStore.presetsSeeded = true
                }
                current.repository.ensureDefaultGroup()
            }

            val fingerprint = Build.FINGERPRINT ?: ""
            val wasForceStopped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessStartReasons(1)
                    .firstOrNull()
                    ?.wasForceStopped() == true
            } else false
            val reason = if (wasForceStopped) {
                "force-stop-recovery"
            } else if (
                current.reliabilityStore.lastBuildFingerprint.isNotBlank() &&
                current.reliabilityStore.lastBuildFingerprint != fingerprint
            ) "os-build-changed" else if (unlocked) "app-start" else "locked-boot-start"
            current.repository.rescheduleAll(reason)

            if (unlocked) {
                val report = current.reliabilityChecker.runCheck()
                current.reliabilityStore.lastBuildFingerprint = fingerprint
                if (report.hasCritical) {
                    val issues = report.items.filter { !it.ok }.joinToString("、") { it.title }
                    NotificationHelper(this@AlarmApplication).showReliabilityWarning(
                        title = "アラーム設定を確認してください",
                        message = "OS更新または権限変更の影響で要対応項目があります: $issues"
                    )
                }
                NextAlarmWidgetProvider.refresh(this@AlarmApplication)
            }
        }
    }

    fun containerOrNull(): AppContainer? {
        appContainer?.let { return it }
        if (!AppDatabase.canOpen(this)) return null
        return synchronized(this) {
            appContainer ?: AppContainer(this).also { appContainer = it }
        }
    }

    companion object {
        lateinit var instance: AlarmApplication
            private set
    }
}
