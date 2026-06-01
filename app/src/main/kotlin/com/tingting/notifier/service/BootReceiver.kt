package com.tingting.notifier.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tingting.notifier.data.repository.UserSettingsRepository
import com.tingting.notifier.reliability.ListenerRebinder
import com.tingting.notifier.reliability.WatchdogScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * On device boot, nudge the notification listener alive if the user has the service
 * enabled, and (re)schedule the watchdog (spec §9). Handles both the standard
 * `BOOT_COMPLETED` and the HTC/quick-boot action.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var userSettingsRepository: UserSettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) return

        val pending = goAsync()
        try {
            val enabled = runBlocking { userSettingsRepository.settings.first().enableService }
            if (enabled) {
                Timber.d("Boot: service enabled, nudging listener")
                ListenerRebinder.rebind(context)
                WatchdogScheduler.schedule(context)
            }
        } catch (e: Exception) {
            Timber.w(e, "BootReceiver failed")
        } finally {
            pending.finish()
        }
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
