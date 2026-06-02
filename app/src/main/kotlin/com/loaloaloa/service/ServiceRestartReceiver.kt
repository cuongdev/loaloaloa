package com.loaloaloa.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.loaloaloa.reliability.ListenerRebinder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Receives the custom restart broadcast scheduled by the listener service's
 * `onTaskRemoved`/`onDestroy` and nudges the listener back alive (spec §9).
 */
@AndroidEntryPoint
class ServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESTART_SERVICE) return
        Timber.d("Restart broadcast received; nudging listener")
        ListenerRebinder.rebind(context)
    }

    companion object {
        const val ACTION_RESTART_SERVICE = "com.loaloaloa.RESTART_SERVICE"
    }
}
