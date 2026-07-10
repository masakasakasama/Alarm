package com.galaxyalarm.reliability

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.galaxyalarm.AlarmApplication
import com.galaxyalarm.notify.NotificationHelper
import java.util.concurrent.TimeUnit

class ScheduleHealthWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AlarmApplication
        val reason = inputData.getString(KEY_REASON) ?: "periodic-health"
        return try {
            // Reassert AlarmManager state periodically. Android does not expose a complete
            // API for enumerating exact alarms, so a DB-only check cannot detect every loss.
            if (app.container.permissions.canScheduleExactAlarms()) {
                app.container.repository.rescheduleAll("health:$reason")
            }
            var report = app.container.reliabilityChecker.runCheck()
            if (report.hasRepairableScheduleProblem && app.container.permissions.canScheduleExactAlarms()) {
                report = app.container.reliabilityChecker.repair(reason)
            }
            if (report.hasCritical) {
                val issues = report.items.filter { !it.ok }.joinToString("、") { it.title }
                NotificationHelper(applicationContext).showReliabilityWarning(
                    title = "アラーム設定を確認してください",
                    message = "OS更新または権限変更の影響で要対応項目があります: $issues"
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "schedule_health"
        private const val IMMEDIATE_NAME = "schedule_health_immediate"
        private const val KEY_REASON = "reason"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ScheduleHealthWorker>(
                3, TimeUnit.HOURS
            ).setInputData(workDataOf(KEY_REASON to "periodic-health")).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun runSoon(context: Context, reason: String) {
            val req = OneTimeWorkRequestBuilder<ScheduleHealthWorker>()
                .setInputData(workDataOf(KEY_REASON to reason))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, req
            )
        }
    }
}
