package com.tingting.notifier

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tingting.notifier.permission.BatteryOptimizationHelper
import com.tingting.notifier.permission.NotificationAccessHelper
import com.tingting.notifier.ui.navigation.AppNavHost
import com.tingting.notifier.ui.navigation.Routes
import com.tingting.notifier.ui.theme.TingTingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result reflected on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        maybeRequestPostNotifications()

        val start = if (NotificationAccessHelper.isGranted(this)) Routes.HOME else Routes.ONBOARDING

        setContent {
            TingTingTheme {
                AppNavHost(
                    startDestination = start,
                    onRequestNotificationAccess = { launchSafely { NotificationAccessHelper.settingsIntent() } },
                    onRequestBatteryExemption = { launchSafely { BatteryOptimizationHelper.requestIntent(packageName) } },
                )
            }
        }
    }

    private fun maybeRequestPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private inline fun launchSafely(provider: () -> android.content.Intent) {
        try {
            startActivity(provider())
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Không mở được cài đặt hệ thống", Toast.LENGTH_SHORT).show()
        }
    }
}
