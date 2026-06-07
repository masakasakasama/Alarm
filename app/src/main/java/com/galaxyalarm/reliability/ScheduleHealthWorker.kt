package com.galaxyalarm.reliability

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.galaxyalarm.AlarmApplication
import java.util.concurrent.TimeUnit

/**
 * バックグラウンドで定期的にスケジュール健全性を確認し、
 * 予約欠落があれば自動修復する。
 */
class ScheduleHealthWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AlarmApplication
        return try {
            val report = app.container.reliabilityChecker.runCheck()
            // 未来予約の欠落があれば自動修復。
            val missing = report.items.firstOrNull { it.title.contains("未来予約") }
            if (missing != null && !missing.ok) {
                app.container.reliabilityChecker.repair()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "schedule_health"
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ScheduleHealthWorker>(
                3, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }
}
