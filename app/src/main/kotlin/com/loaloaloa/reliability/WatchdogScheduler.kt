package com.loaloaloa.reliability

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the [ServiceWatchdogWorker] as unique periodic work (spec §9). 15 minutes
 * is the WorkManager minimum period. Idempotent: re-scheduling keeps the existing work
 * so app launches / boots don't reset the cadence.
 */
object WatchdogScheduler {

    private const val UNIQUE_WORK_NAME = "loaloaloa_service_watchdog"
    private const val INTERVAL_MINUTES = 15L

    /** Enqueue the periodic watchdog if not already scheduled. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
            INTERVAL_MINUTES, TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
