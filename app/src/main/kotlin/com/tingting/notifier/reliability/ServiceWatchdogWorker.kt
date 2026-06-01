package com.tingting.notifier.reliability

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tingting.notifier.data.repository.UserSettingsRepository
import com.tingting.notifier.permission.NotificationAccessHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Periodic reliability watchdog (spec §9). On each run it reads the current settings
 * and notification-access state, asks the pure [WatchdogDecision] whether to act, and
 * if so nudges the listener alive via [ListenerRebinder]. Framework glue — the only
 * decision logic lives in (unit-tested) [WatchdogDecision].
 */
@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val userSettingsRepository: UserSettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = userSettingsRepository.settings.first()
            val accessGranted = NotificationAccessHelper.isGranted(applicationContext)
            if (WatchdogDecision.shouldRebind(settings.enableService, accessGranted)) {
                Timber.d("Watchdog nudging listener alive")
                ListenerRebinder.rebind(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "Watchdog run failed; will retry")
            Result.retry()
        }
    }
}
