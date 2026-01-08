package com.vibe.terminal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vibe.terminal.ui.home.HomeScreen
import com.vibe.terminal.ui.project.ProjectDetailScreen
import com.vibe.terminal.ui.settings.MachineEditScreen
import com.vibe.terminal.ui.settings.SettingsScreen
import com.vibe.terminal.ui.terminal.TerminalScreen

/**
 * 导航路由定义
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object MachineEdit : Screen("machine_edit?machineId={machineId}") {
        fun createRoute(machineId: String? = null): String {
            return if (machineId != null) {
                "machine_edit?machineId=$machineId"
            } else {
                "machine_edit"
            }
        }
    }
    data object ProjectDetail : Screen("project/{projectId}") {
        fun createRoute(projectId: String): String = "project/$projectId"
    }
    data object Terminal : Screen("terminal/{projectId}") {
        fun createRoute(projectId: String): String = "terminal/$projectId"
    }
}

@Composable
fun VibeNavHost(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToProjectDetail = { projectId ->
                    navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                },
                onNavigateToTerminal = { projectId ->
                    navController.navigate(Screen.Terminal.createRoute(projectId))
                },
                onNavigateToMachineEdit = { machineId ->
                    navController.navigate(Screen.MachineEdit.createRoute(machineId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToMachineEdit = { machineId ->
                    navController.navigate(Screen.MachineEdit.createRoute(machineId))
                }
            )
        }

        composable(
            route = Screen.MachineEdit.route,
            arguments = listOf(
                navArgument("machineId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            MachineEditScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.ProjectDetail.route,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            ProjectDetailScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToTerminal = {
                    navController.navigate(Screen.Terminal.createRoute(projectId))
                }
            )
        }

        composable(
            route = Screen.Terminal.route,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.StringType
                }
            )
        ) {
            TerminalScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
