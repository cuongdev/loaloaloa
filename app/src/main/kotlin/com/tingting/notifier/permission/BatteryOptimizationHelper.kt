package com.tingting.notifier.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimization exemption checks and request Intent (spec §9, §11). Being
 * exempt greatly improves background-listener survival on stock Android.
 */
object BatteryOptimizationHelper {

    /** @return true when the app is already exempt from battery optimizations. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * @return an Intent asking the user to exempt [packageName] from battery
     *   optimizations. Uses the direct request action; the system shows a system
     *   dialog. Requires the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission
     *   (declared in the manifest).
     */
    fun requestIntent(packageName: String): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
}
