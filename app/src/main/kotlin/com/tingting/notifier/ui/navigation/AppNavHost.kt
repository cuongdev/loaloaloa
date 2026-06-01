package com.tingting.notifier.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tingting.notifier.ui.banks.BanksScreen
import com.tingting.notifier.ui.history.HistoryScreen
import com.tingting.notifier.ui.home.HomeScreen
import com.tingting.notifier.ui.onboarding.OnboardingScreen
import com.tingting.notifier.ui.report.ReportScreen
import com.tingting.notifier.ui.settings.ApiSourceScreen
import com.tingting.notifier.ui.settings.ExcludedAppsScreen
import com.tingting.notifier.ui.settings.SettingsScreen
import com.tingting.notifier.ui.troubleshooting.TroubleshootingScreen

@Composable
fun AppNavHost(
    startDestination: String = Routes.HOME,
    navController: NavHostController = rememberNavController(),
    onRequestNotificationAccess: () -> Unit = {},
    onRequestBatteryExemption: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TopLevelDestination.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = backStackEntry?.destination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label,
                                )
                            },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                )
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.REPORT) { ReportScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenExcludedApps = { navController.navigate(Routes.EXCLUDED_APPS) },
                    onOpenBanks = { navController.navigate(Routes.BANKS) },
                    onOpenApiSource = { navController.navigate(Routes.API_SOURCE) },
                    onOpenTroubleshooting = { navController.navigate(Routes.TROUBLESHOOTING) },
                )
            }

            composable(Routes.BANKS) { BanksScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.EXCLUDED_APPS) { ExcludedAppsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.API_SOURCE) { ApiSourceScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.TROUBLESHOOTING) { TroubleshootingScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
