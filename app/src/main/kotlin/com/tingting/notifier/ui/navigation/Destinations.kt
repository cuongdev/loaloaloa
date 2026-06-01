package com.tingting.notifier.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Navigation routes for the whole app. */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val REPORT = "report"
    const val SETTINGS = "settings"

    const val BANKS = "banks"
    const val ONBOARDING = "onboarding"
    const val TROUBLESHOOTING = "troubleshooting"
    const val EXCLUDED_APPS = "excluded_apps"
    const val API_SOURCE = "api_source"
    const val WEBHOOK = "webhook"
}

/** The four bottom-navigation tabs (label + filled/outlined icons). */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Routes.HOME, "Trang chủ", Icons.Filled.Home, Icons.Outlined.Home),
    HISTORY(Routes.HISTORY, "Lịch sử", Icons.Filled.History, Icons.Outlined.History),
    REPORT(Routes.REPORT, "Báo cáo", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    SETTINGS(Routes.SETTINGS, "Cài đặt", Icons.Filled.Settings, Icons.Outlined.Settings),
}
