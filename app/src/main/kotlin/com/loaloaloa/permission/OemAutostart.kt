package com.loaloaloa.permission

import android.content.ComponentName
import android.content.Intent

/**
 * Maps a device manufacturer (`android.os.Build.MANUFACTURER`) to the vendor
 * "autostart" / "background app management" settings screen, where the user must
 * allow-list the app so the OEM ROM does not kill the background listener
 * (spec §9, §14). There is no public API for this; the [ComponentName]s are
 * hard-coded vendor knowledge gathered from common ROMs.
 *
 * [componentFor] is the PURE, case-insensitive lookup (package, class) and is
 * unit-tested. [autostartIntent] is the thin Android wrapper that turns it into an
 * [Intent], or null for unknown manufacturers (the UI then falls back to generic
 * battery-optimization guidance).
 */
object OemAutostart {

    /**
     * @return `(packageName, className)` of the OEM autostart settings activity for
     *   [manufacturer], or null if the manufacturer is unknown. Case-insensitive and
     *   whitespace-trimmed.
     */
    fun componentFor(manufacturer: String): Pair<String, String>? {
        return when (manufacturer.trim().lowercase()) {
            "xiaomi", "redmi", "poco" ->
                "com.miui.securitycenter" to
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
            "oppo", "realme" ->
                "com.coloros.safecenter" to
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            "vivo" ->
                "com.vivo.permissionmanager" to
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            "huawei", "honor" ->
                "com.huawei.systemmanager" to
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            "samsung" ->
                "com.samsung.android.lool" to
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
            "letv" ->
                "com.letv.android.letvsafe" to
                    "com.letv.android.letvsafe.AutobootManageActivity"
            "asus" ->
                "com.asus.mobilemanager" to
                    "com.asus.mobilemanager.MainActivity"
            else -> null
        }
    }

    /**
     * @return an [Intent] opening the OEM autostart settings screen for
     *   [manufacturer], or null when unknown. Callers should `try/catch`
     *   `ActivityNotFoundException` since the activity may be absent on a given ROM
     *   version.
     */
    fun autostartIntent(manufacturer: String): Intent? {
        val (pkg, cls) = componentFor(manufacturer) ?: return null
        return Intent().apply { component = ComponentName(pkg, cls) }
    }
}
