package com.tingting.notifier.permission

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.tingting.notifier.service.TransferNotificationListenerService

/**
 * Notification-listener access checks and the deep-link to grant it (spec §11).
 * Reads the `enabled_notification_listeners` secure setting — a colon-separated list
 * of flattened component names — and looks for our listener component.
 */
object NotificationAccessHelper {

    /** @return true when the user has granted notification access to our listener. */
    fun isGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val component = ComponentName(context, TransferNotificationListenerService::class.java)
        val flattened = component.flattenToString()
        val flattenedShort = component.flattenToShortString()
        return flat.split(':').any { entry ->
            entry == flattened || entry == flattenedShort
        }
    }

    /** @return the system Notification-Access settings screen. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
