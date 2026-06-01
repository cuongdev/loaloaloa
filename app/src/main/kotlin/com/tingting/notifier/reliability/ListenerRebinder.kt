package com.tingting.notifier.reliability

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import com.tingting.notifier.service.TransferNotificationListenerService
import timber.log.Timber

/**
 * Forces Android to rebind the [TransferNotificationListenerService] when its
 * connection has gone stale (spec §9, §14). The reliable trick is to toggle the
 * listener component's enabled state via [PackageManager.setComponentEnabledSetting]
 * — disabling then re-enabling makes the platform rebuild the listener connection —
 * and, on API 24+, to additionally call [NotificationListenerService.requestRebind].
 *
 * All framework glue; never throws — failures are logged and swallowed so the
 * watchdog/boot path is resilient.
 */
object ListenerRebinder {

    /** Disable+re-enable the listener component and request a rebind. Safe to call repeatedly. */
    fun rebind(context: Context) {
        val component = ComponentName(context, TransferNotificationListenerService::class.java)
        toggleComponent(context, component)
        requestRebind(component)
    }

    private fun toggleComponent(context: Context, component: ComponentName) {
        val pm = context.packageManager
        try {
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            Timber.d("Toggled listener component to force rebind")
        } catch (e: Exception) {
            Timber.w(e, "Failed to toggle listener component")
        }
    }

    private fun requestRebind(component: ComponentName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                NotificationListenerService.requestRebind(component)
                Timber.d("Requested listener rebind")
            } catch (e: Exception) {
                Timber.w(e, "requestRebind failed")
            }
        }
    }
}
