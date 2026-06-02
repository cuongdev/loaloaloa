package com.loaloaloa

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.loaloaloa.permission.BatteryOptimizationHelper
import com.loaloaloa.permission.NotificationAccessHelper
import com.loaloaloa.ui.navigation.AppNavHost
import com.loaloaloa.ui.navigation.Routes
import com.loaloaloa.ui.theme.LoaLoaLoaTheme
import com.microsoft.clarity.Clarity
import com.microsoft.clarity.ClarityConfig
import com.microsoft.clarity.models.LogLevel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result reflected on resume */ }

    // A pairing App-Link (https://…/pair#<token>) that launched/resumed us; consumed by the relay
    // screen. Compose snapshot state so an onNewIntent while we're already running recomposes.
    private val pendingPairToken = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Microsoft Clarity session recording. Verbose logs in debug, silent in release.
        Clarity.initialize(
            applicationContext,
            ClarityConfig(
                projectId = "x0p6ldkrn0",
                logLevel = if (BuildConfig.DEBUG) LogLevel.Verbose else LogLevel.None,
            ),
        )

        maybeRequestPostNotifications()
        pendingPairToken.value = pairTokenFrom(intent)

        val start = if (NotificationAccessHelper.isGranted(this)) Routes.HOME else Routes.ONBOARDING

        setContent {
            LoaLoaLoaTheme {
                AppNavHost(
                    startDestination = start,
                    onRequestNotificationAccess = { launchSafely { NotificationAccessHelper.settingsIntent() } },
                    onRequestBatteryExemption = { launchSafely { BatteryOptimizationHelper.requestIntent(packageName) } },
                    pairToken = pendingPairToken.value,
                    onPairTokenHandled = { pendingPairToken.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pairTokenFrom(intent)?.let { pendingPairToken.value = it }
    }

    /**
     * The pairing token from an App-Link VIEW intent ("https://…/pair#<token>"), or null. The whole
     * URL is passed through — [com.loaloaloa.relay.RelayPairingCodec] pulls the token out of
     * the fragment, so the secret never has to be in the path/query.
     */
    private fun pairTokenFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        if (!data.path.orEmpty().startsWith("/pair")) return null
        return data.toString()
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
