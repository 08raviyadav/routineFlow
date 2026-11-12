package com.thor.routineflow.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.ui.screens.DiagnosticsScreen
import com.thor.routineflow.ui.screens.HistoryScreen
import com.thor.routineflow.ui.screens.HomeScreen
import com.thor.routineflow.ui.screens.ModesScreen
import com.thor.routineflow.ui.screens.PermissionWizardScreen
import com.thor.routineflow.ui.screens.RoutineEditorScreen
import com.thor.routineflow.ui.screens.SettingsScreen
import com.thor.routineflow.ui.screens.TemplatesScreen

object Routes {
    const val HOME = "home"
    const val MODES = "modes"
    const val TEMPLATES = "templates"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SETTINGS_WITH_IMPORT = "settings?importUri={importUri}"
    const val DIAGNOSTICS = "diagnostics"
    const val WIZARD = "wizard"
    const val EDITOR = "editor/{routineId}"

    fun editor(routineId: String) = "editor/$routineId"
    fun settingsWithImport(importUri: String) = "settings?importUri=${Uri.encode(importUri)}"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home") { Icon(Icons.Filled.Home, contentDescription = null) },
    BottomTab(Routes.MODES, "Modes") { Icon(Icons.Filled.Bedtime, contentDescription = null) },
    BottomTab(Routes.TEMPLATES, "Templates") { Icon(Icons.Filled.Dashboard, contentDescription = null) },
    BottomTab(Routes.HISTORY, "History") { Icon(Icons.Filled.History, contentDescription = null) },
    BottomTab(Routes.SETTINGS, "Settings") { Icon(Icons.Filled.Settings, contentDescription = null) }
)

/**
 * The app's single navigation graph: five bottom tabs plus the editor,
 * diagnostics and permission-wizard destinations. First-run users land on the
 * wizard; everyone else lands on Home.
 */
@Composable
fun RoutineFlowNav(
    container: AppContainer,
    pendingImportUri: String?,
    onImportConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val onboardingDone by container.preferences.onboardingDone.collectAsState(initial = null)

    // Wait for the onboarding flag before choosing a start destination.
    if (onboardingDone == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (onboardingDone == true) Routes.HOME else Routes.WIZARD

    // A backup file shared into the app routes to Settings with its Uri.
    LaunchedEffect(pendingImportUri) {
        if (pendingImportUri != null) {
            navController.navigate(Routes.settingsWithImport(pendingImportUri)) {
                launchSingleTop = true
            }
            onImportConsumed()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.HOME ||
        currentRoute == Routes.MODES ||
        currentRoute == Routes.TEMPLATES ||
        currentRoute == Routes.HISTORY ||
        currentRoute == Routes.SETTINGS_WITH_IMPORT

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route ||
                            (tab.route == Routes.SETTINGS && currentRoute == Routes.SETTINGS_WITH_IMPORT)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(navController, tab.route) },
                            icon = tab.icon,
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onNavigateToEditor = { id -> navController.navigate(Routes.editor(id)) },
                    onNavigateToTemplates = { navigateToTab(navController, Routes.TEMPLATES) },
                    onNavigateToModes = { navigateToTab(navController, Routes.MODES) }
                )
            }
            composable(Routes.MODES) {
                ModesScreen(container = container)
            }
            composable(Routes.TEMPLATES) {
                TemplatesScreen(
                    container = container,
                    onOpenEditor = { id -> navController.navigate(Routes.editor(id)) }
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(container = container)
            }
            composable(
                route = Routes.SETTINGS_WITH_IMPORT,
                arguments = listOf(
                    navArgument("importUri") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                SettingsScreen(
                    container = container,
                    importUri = backStackEntry.arguments?.getString("importUri"),
                    onNavigateToDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    onNavigateToWizard = { navController.navigate(Routes.WIZARD) }
                )
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument("routineId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val routineId = backStackEntry.arguments?.getString("routineId") ?: "new"
                RoutineEditorScreen(
                    container = container,
                    routineId = routineId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onNavigateToWizard = { navController.navigate(Routes.WIZARD) }
                )
            }
            composable(Routes.WIZARD) {
                PermissionWizardScreen(
                    container = container,
                    onFinish = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.WIZARD) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

private fun navigateToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
