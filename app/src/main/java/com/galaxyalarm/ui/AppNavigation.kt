package com.galaxyalarm.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.galaxyalarm.ui.alarms.AlarmsScreen
import com.galaxyalarm.ui.edit.EditAlarmScreen
import com.galaxyalarm.ui.groups.GroupsScreen
import com.galaxyalarm.ui.home.HomeScreen
import com.galaxyalarm.ui.log.EventLogScreen
import com.galaxyalarm.ui.reliability.ReliabilityScreen
import com.galaxyalarm.ui.settings.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "ホーム", Icons.Filled.Home),
    Tab("groups", "グループ", Icons.Filled.Folder),
    Tab("alarms", "アラーム", Icons.Filled.Alarm),
    Tab("reliability", "信頼性", Icons.Filled.HealthAndSafety),
    Tab("settings", "設定", Icons.Filled.Settings),
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val mainVm: MainViewModel = viewModel()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(mainVm,
                    onOpenReliability = { navController.navigate("reliability") },
                    onOpenAlarms = { navController.navigate("alarms") },
                    onAddAlarm = { navController.navigate("edit/0") })
            }
            composable("groups") { GroupsScreen(mainVm) }
            composable("alarms") {
                AlarmsScreen(mainVm,
                    onAddAlarm = { navController.navigate("edit/0") },
                    onEditAlarm = { id -> navController.navigate("edit/$id") })
            }
            composable("reliability") {
                ReliabilityScreen(mainVm, onOpenLog = { navController.navigate("log") })
            }
            composable("settings") {
                SettingsScreen(mainVm, onOpenLog = { navController.navigate("log") })
            }
            composable("log") { EventLogScreen(mainVm, onBack = { navController.popBackStack() }) }
            composable("edit/{alarmId}") { entry ->
                val id = entry.arguments?.getString("alarmId")?.toLongOrNull() ?: 0L
                EditAlarmScreen(alarmId = id, onDone = { navController.popBackStack() })
            }
        }
    }
}
