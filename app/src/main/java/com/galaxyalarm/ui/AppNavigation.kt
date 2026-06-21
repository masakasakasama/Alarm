package com.galaxyalarm.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.galaxyalarm.ui.clock.StopwatchScreen
import com.galaxyalarm.ui.clock.TimerScreen
import com.galaxyalarm.ui.edit.EditAlarmScreen
import com.galaxyalarm.ui.groups.GroupsScreen
import com.galaxyalarm.ui.log.EventLogScreen
import com.galaxyalarm.ui.reliability.ReliabilityScreen
import com.galaxyalarm.ui.settings.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

// Galaxy純正時計を参考にした下タブ。世界時計はアラームタブ内、信頼性は設定内に集約。
private val tabs = listOf(
    Tab("alarms", "アラーム", Icons.Filled.Alarm),
    Tab("groups", "グループ", Icons.Filled.Folder),
    Tab("timer", "タイマー", Icons.Filled.HourglassBottom),
    Tab("stopwatch", "ストップウォッチ", Icons.Filled.Timer),
    Tab("settings", "設定", Icons.Filled.Settings),
)

@Composable
fun AppNavigation(editAlarmRequest: Long? = null) {
    val navController = rememberNavController()
    val mainVm: MainViewModel = viewModel()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "alarms"
    val selectedRoute = when {
        currentRoute.startsWith("alarms") -> "alarms"
        currentRoute.startsWith("edit") -> "alarms"
        currentRoute == "log" -> "settings"
        currentRoute == "reliability" -> "settings"
        else -> currentRoute
    }

    LaunchedEffect(editAlarmRequest) {
        editAlarmRequest?.let { id ->
            navController.navigate("edit/$id") {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedRoute == tab.route,
                        onClick = {
                            if (selectedRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "alarms",
            modifier = Modifier.padding(padding)
        ) {
            composable("alarms") {
                AlarmsScreen(
                    vm = mainVm,
                    onAddAlarm = { navController.navigate("edit/0") },
                    onEditAlarm = { id -> navController.navigate("edit/$id") },
                    onOpenGroup = { id -> navController.navigate("alarms/group/$id") }
                )
            }
            composable("alarms/group/{groupId}") { entry ->
                val groupId = entry.arguments?.getString("groupId")?.toLongOrNull() ?: 0L
                AlarmsScreen(
                    vm = mainVm,
                    groupId = groupId,
                    // グループ詳細からの追加は、そのグループを初期所属にする。
                    onAddAlarm = { navController.navigate("edit/0/$groupId") },
                    onEditAlarm = { id -> navController.navigate("edit/$id") },
                    onOpenGroup = { id -> navController.navigate("alarms/group/$id") }
                )
            }
            composable("groups") {
                GroupsScreen(
                    vm = mainVm,
                    onOpenGroup = { id -> navController.navigate("alarms/group/$id") }
                )
            }
            composable("timer") { TimerScreen() }
            composable("stopwatch") { StopwatchScreen() }
            composable("settings") {
                SettingsScreen(
                    vm = mainVm,
                    onOpenLog = { navController.navigate("log") },
                    onOpenReliability = { navController.navigate("reliability") }
                )
            }
            composable("reliability") {
                ReliabilityScreen(mainVm, onOpenLog = { navController.navigate("log") })
            }
            composable("log") { EventLogScreen(mainVm, onBack = { navController.popBackStack() }) }
            composable("edit/{alarmId}") { entry ->
                val id = entry.arguments?.getString("alarmId")?.toLongOrNull() ?: 0L
                EditAlarmScreen(alarmId = id, onDone = { navController.popBackStack() })
            }
            composable("edit/{alarmId}/{groupId}") { entry ->
                val id = entry.arguments?.getString("alarmId")?.toLongOrNull() ?: 0L
                val gid = entry.arguments?.getString("groupId")?.toLongOrNull() ?: 0L
                EditAlarmScreen(alarmId = id, groupId = gid, onDone = { navController.popBackStack() })
            }
        }
    }
}
