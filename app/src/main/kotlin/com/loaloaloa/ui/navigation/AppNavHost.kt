package com.loaloaloa.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.loaloaloa.data.model.AppMode
import com.loaloaloa.ui.banks.BanksScreen
import com.loaloaloa.ui.debug.DebugScreen
import com.loaloaloa.ui.history.HistoryScreen
import com.loaloaloa.ui.home.HomeScreen
import com.loaloaloa.ui.mode.AppModeViewModel
import com.loaloaloa.ui.mode.ModePickerScreen
import com.loaloaloa.ui.mode.RootModeState
import com.loaloaloa.ui.onboarding.OnboardingScreen
import com.loaloaloa.ui.report.ReportScreen
import com.loaloaloa.ui.shift.ShiftScreen
import com.loaloaloa.ui.settings.ApiSourceScreen
import com.loaloaloa.ui.settings.ExcludedAppsScreen
import com.loaloaloa.ui.settings.RelaySettingsScreen
import com.loaloaloa.ui.settings.SettingsScreen
import com.loaloaloa.ui.settings.WebhookScreen
import com.loaloaloa.ui.staff.StaffShell
import com.loaloaloa.ui.staff.StaffTtsScreen
import com.loaloaloa.ui.troubleshooting.TroubleshootingScreen

@Composable
fun AppRoot(
    notifAccessGranted: Boolean,
    onRequestNotificationAccess: () -> Unit = {},
    onRequestBatteryExemption: () -> Unit = {},
    pairToken: String? = null,
    onPairTokenHandled: () -> Unit = {},
    modeViewModel: AppModeViewModel = hiltViewModel(),
) {
    val state by modeViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { modeViewModel.ensureMigrated(notifAccessGranted) }

    // A pairing deep-link on a not-yet-chosen install means this is a staff device.
    LaunchedEffect(pairToken, state) {
        val ready = state as? RootModeState.Ready
        if (pairToken != null && ready?.appMode == AppMode.UNSET) {
            modeViewModel.chooseStaff()
        }
    }

    when (val s = state) {
        RootModeState.Loading ->
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

        is RootModeState.Ready -> when (s.appMode) {
            AppMode.UNSET ->
                ModePickerScreen(
                    onChooseShop = modeViewModel::chooseShop,
                    onChooseStaff = modeViewModel::chooseStaff,
                )

            AppMode.STAFF -> {
                val staffNav = rememberNavController()
                NavHost(navController = staffNav, startDestination = Routes.STAFF) {
                    composable(Routes.STAFF) {
                        StaffShell(
                            onOpenTts = { staffNav.navigate(Routes.STAFF_TTS) },
                            onOpenShift = { staffNav.navigate(Routes.SHIFT) },
                            onRequestBatteryExemption = onRequestBatteryExemption,
                            pairToken = pairToken,
                            onPairTokenHandled = onPairTokenHandled,
                        )
                    }
                    composable(Routes.STAFF_TTS) {
                        StaffTtsScreen(onBack = { staffNav.popBackStack() })
                    }
                    composable(Routes.SHIFT) {
                        ShiftScreen(onBack = { staffNav.popBackStack() })
                    }
                }
            }

            AppMode.SHOP_OWNER ->
                AppNavHost(
                    startDestination = if (notifAccessGranted) Routes.HOME else Routes.ONBOARDING,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                    pairToken = pairToken,
                    onPairTokenHandled = onPairTokenHandled,
                    onSwitchMode = modeViewModel::toPicker,
                )
        }
    }
}

@Composable
fun AppNavHost(
    startDestination: String = Routes.HOME,
    navController: NavHostController = rememberNavController(),
    onRequestNotificationAccess: () -> Unit = {},
    onRequestBatteryExemption: () -> Unit = {},
    pairToken: String? = null,
    onPairTokenHandled: () -> Unit = {},
    onSwitchMode: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TopLevelDestination.entries.map { it.route }

    // An incoming pairing App-Link (https://…/pair#<token>) routes the employee straight to the
    // relay screen, which applies the token. Re-runs when a new link arrives via onNewIntent.
    LaunchedEffect(pairToken) {
        if (pairToken != null) {
            navController.navigate(Routes.RELAY) { launchSingleTop = true }
        }
    }

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
                    onOpenShift = { navController.navigate(Routes.SHIFT) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.REPORT) { ReportScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenExcludedApps = { navController.navigate(Routes.EXCLUDED_APPS) },
                    onOpenBanks = { navController.navigate(Routes.BANKS) },
                    onOpenApiSource = { navController.navigate(Routes.API_SOURCE) },
                    onOpenWebhook = { navController.navigate(Routes.WEBHOOK) },
                    onOpenRelay = { navController.navigate(Routes.RELAY) },
                    onOpenTroubleshooting = { navController.navigate(Routes.TROUBLESHOOTING) },
                    onOpenDebug = { navController.navigate(Routes.DEBUG) },
                    onSwitchMode = onSwitchMode,
                )
            }

            composable(Routes.BANKS) { BanksScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.EXCLUDED_APPS) { ExcludedAppsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.API_SOURCE) { ApiSourceScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.WEBHOOK) { WebhookScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.RELAY) {
                RelaySettingsScreen(
                    onBack = { navController.popBackStack() },
                    incomingToken = pairToken,
                    onIncomingTokenHandled = onPairTokenHandled,
                )
            }
            composable(Routes.DEBUG) { DebugScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SHIFT) { ShiftScreen(onBack = { navController.popBackStack() }) }
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
